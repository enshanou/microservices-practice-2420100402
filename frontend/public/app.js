import { createLinkImport } from './link-import.js';
import { createStudy } from './study.js';
import { createGeneration } from './generation.js';

const $ = selector => document.querySelector(selector);
const icon = name => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
const escapeHTML = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const statusNames = { PENDING:'待处理', RUNNING:'处理中', SUCCEEDED:'已完成', FAILED:'失败' };
const state = { users:[], courses:[], tasks:[], transcripts:[], notes:[], videos:[], collections:[], videoAvailable:false, linkAvailable:false, model:{configured:false}, selected:'', filter:'all', taskCourse:null, errors:{}, loaded:{}, loading:false };
const generation = createGeneration(() => state.model.providers ?? []);
let session = { enabled: false };
const active = task => ['PENDING','RUNNING'].includes(task.status);
const currentUser = () => state.users.find(u => u.id === state.selected);
const myCourses = () => state.courses.filter(c => c.ownerId === state.selected);
const myTasks = () => { const ids = new Set(myCourses().map(c => c.id)); return state.tasks.filter(t => ids.has(t.courseId)); };
let toastTimer;
function notify(message, error = false) {
  const toast = $('#toast'); toast.textContent = message; toast.classList.toggle('error', error); toast.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.hidden = true; }, error ? 8000 : 4500);
}
async function api(path, options = {}) {
  let response;
  const multipart = options.body instanceof FormData;
  const catalogRequest = path === '/api/tasks/video/preview' || (path === '/api/tasks/video/collections' && options.method === 'POST');
  const headers = multipart ? {} : {'Content-Type':'application/json'};
  const longRequest = /\/(?:explain|notion)$/.test(path);
  try { response = await fetch(path, { ...options, headers, signal:AbortSignal.timeout(multipart ? 600000 : longRequest ? 105000 : catalogRequest ? 30000 : 15000) }); }
  catch { throw new Error('暂时无法连接工作台服务，请检查启动状态后重试'); }
  if (response.status === 401 && session.userId && path !== '/api/session/login') { location.reload(); }
  if (response.status === 204) return null;
  let data;
  try { data = await response.json(); } catch { throw new Error('服务返回了无法识别的响应，请稍后刷新'); }
  if (!response.ok) {
    const messages = { 400:'提交的信息不符合要求，请检查填写内容', 404:'关联的用户或课程不存在，请刷新后重试',
      409:'这门课程已有待处理或处理中的任务，请先查看任务记录',
      503:'依赖服务暂时不可用，本次操作未成功，请确认服务恢复后重试', 504:'服务响应超时，请稍后刷新确认结果' };
    const noteRequest = /\/transcript(?:s)?$|\/(?:notes|video)(?:\/|$)/.test(path);
    const detail = noteRequest && data.error !== 'Dependency resource does not exist' ? data.error : '';
    throw new Error(`${detail || messages[response.status] || data.error || '操作失败'}（HTTP ${response.status}）`);
  }
  return data;
}
function saveSelection(id) { state.selected = id; try { localStorage.setItem('courselens-user', id); } catch {} }
function renderUsers() {
  const select = $('#user-select');
  select.innerHTML = state.users.length ? state.users.map(u => `<option value="${escapeHTML(u.id)}">${escapeHTML(u.displayName)}</option>`).join('') : '<option value="">暂无用户，请点击右侧 ＋ 创建</option>';
  select.value = state.selected; select.disabled = session.enabled || !state.users.length;
  $('#new-course').disabled = !currentUser() || !!state.errors.users || !!state.errors.courses;
  $('#import-link').disabled = $('#new-course').disabled || !state.linkAvailable;
}
function badge(status) { return `<span class="status ${escapeHTML(status)}">${escapeHTML(statusNames[status] || status)}</span>`; }
function empty(title, description, action = '') { return `<div class="empty-state">${icon('book')}<h3>${escapeHTML(title)}</h3><p>${escapeHTML(description)}</p>${action}</div>`; }
function render() {
  const courses = myCourses(), tasks = myTasks();
  const courseDataKnown = state.loaded.courses && state.loaded.users;
  $('#stat-courses').textContent = courseDataKnown ? courses.length : '—';
  $('#stat-active').textContent = courseDataKnown && state.loaded.tasks ? tasks.filter(active).length : '—';
  $('#stat-completed').textContent = courseDataKnown && state.loaded.tasks ? tasks.filter(t=>t.status==='SUCCEEDED').length : '—';
  $('#nav-course-count').textContent = courseDataKnown ? courses.length : '—';
  $('#nav-task-count').textContent = courseDataKnown && state.loaded.tasks ? tasks.length : '—';
  $('#course-count').textContent = courseDataKnown ? courses.length : '—';
  const errorLabels = {users:'用户',courses:'课程',tasks:'任务',transcripts:'字幕',notes:'笔记',videos:'视频',collections:'合集'};
  const failures = Object.keys(state.errors).filter(k => state.errors[k]);
  const warning = $('#connection-warning'); warning.hidden = !failures.length;
  warning.textContent = failures.map(k=>errorLabels[k]).join('、') + '服务读取失败。已读取的数据会暂时保留，请恢复服务后刷新；相关写入操作已暂停。';
  renderUsers(); renderCourses(courses, tasks); renderTasks(courses, tasks);
}
function renderCourses(courses, tasks) {
  const grid = $('#course-grid');
  if (!state.loaded.users && state.errors.users) { grid.innerHTML = empty('暂时无法读取用户','请确认用户服务已启动，然后点击刷新。'); return; }
  if (!state.users.length && state.loaded.users) { grid.innerHTML = empty('先创建你的第一个用户','课程需要关联用户。创建后，就可以开始登记课程资料。','<button class="primary" data-action="new-user">新建用户</button>'); return; }
  if (!state.loaded.courses) { grid.innerHTML = empty(state.errors.courses ? '课程服务暂时无法连接' : '正在读取课程',state.errors.courses ? '请确认课程服务启动完成，再点击刷新。' : '请稍候…'); return; }
  const query = $('#course-search').value.trim().toLowerCase();
  const filtered = courses.filter(course => {
    const hasActive = tasks.some(t=>t.courseId === course.id && active(t)) || state.collections.some(batch => batch.courseId === course.id && batch.active);
    return `${course.title} ${course.sourceReference}`.toLowerCase().includes(query) && (state.filter==='all' || state.filter==='active' && hasActive || state.filter==='idle' && !hasActive);
  });
  if (!filtered.length) {
    grid.innerHTML = empty(courses.length ? '没有找到匹配的课程' : '第一门课程，从这里开始', courses.length ? '换一个关键词，或切换课程筛选条件。' : '登记课程名称和资料引用，为它建立一份可追踪的任务记录。', courses.length ? '' : '<button class="primary" data-action="new-course">'+icon('plus')+'新建课程</button>');
    return;
  }
  grid.innerHTML = filtered.map((course, index) => card(course, index, tasks)).join('');
}
function card(course, index, tasks) {
  const id = escapeHTML(course.id);
  const related = tasks.filter(task => task.courseId === course.id);
  const running = related.find(active);
  const collection = state.collections.find(item => item.courseId === course.id);
  const batch = collection?.active ? collection : state.collections.find(item => item.courseId === course.id && item.active);
  const busy = !!running || !!batch;
  const transcript = state.transcripts.find(item => item.courseId === course.id);
  const latest = state.notes.find(note => note.ready && related.some(task => task.id === note.taskId));
  const disabled = state.errors.tasks || state.errors.courses || !state.loaded.tasks;
  const canUpload = !disabled && !busy && state.videoAvailable;
  const canLink = canUpload && state.linkAvailable;
  const canGenerate = !disabled && !busy && !state.errors.transcripts && state.model.configured;
  const noteButton = latest ? `<button class="primary" data-note="${escapeHTML(latest.taskId)}">打开学习页</button>` : '';
  const videoButton = `<button class="primary" data-video="${id}" ${canUpload ? '' : 'disabled'}>上传视频</button>`;
  const linkButton = `<button class="secondary" data-link="${id}" ${canLink ? '' : 'disabled'}
    title="${canLink ? '粘贴 B 站视频链接' : '本机链接转写暂不可用'}">链接转写</button>`;
  const generateButton = `<button class="primary" data-generate="${id}" ${canGenerate ? '' : 'disabled'}>生成笔记</button>`;
  const status = batch ? '<span class="status RUNNING">合集转写中</span>' : running ? badge(running.status) : `<span class="status">${latest ? '笔记已就绪' : '可上传资料'}</span>`;
  let mainAction = videoButton;
  let description = canUpload ? '上传视频或粘贴 B 站链接，自动转写并生成笔记。' : '视频转写暂不可用，请检查服务状态。';
  if (latest) {
    mainAction = noteButton;
    description = '边看视频边阅读、定位和编辑笔记。';
  } else if (transcript) {
    mainAction = generateButton;
    description = canGenerate ? '字幕已保存，可以生成笔记和导图。' : '笔记生成暂不可用，请检查模型和服务状态。';
  }
  if (running) {
    const automatic = state.notes.some(item => item.taskId === running.id) || state.videos.some(item => item.taskId === running.id);
    mainAction = `<button class="primary" data-history="${id}">${automatic ? '查看处理进度' : '查看手动任务'}</button>`;
    description = automatic ? '后台正在处理，完成后可打开学习页。' : '手动任务占用处理名额，可在任务详情中取消。';
  }
  if (batch) {
    mainAction = `<button class="primary" data-history="${id}">查看合集进度</button>`;
    description = '分集按顺序转写，可先查看已经完成的分集。';
  } else if (collection && !running) {
    mainAction = `<button class="primary" data-history="${id}">查看合集</button>`;
    description = '在合集列表选择一集继续学习。';
  }
  const showLinkBesideVideo = !latest && !transcript && !busy;
  const extras = [
    (busy || collection) && latest ? `<button class="text-button" data-note="${escapeHTML(latest.taskId)}">打开已有笔记</button>` : '',
    !busy && (latest || transcript) && canUpload ? `<button class="text-button" data-video="${id}">上传新视频</button>` : '',
    latest && transcript && canGenerate ? `<button class="text-button" data-generate="${id}">用字幕生成笔记</button>` : '',
    !showLinkBesideVideo && !busy ? linkButton : '',
    `<button class="text-button" data-upload="${id}" ${state.errors.courses ? 'disabled' : ''}>${transcript ? '更换字幕' : '上传字幕'}</button>`,
    !busy ? `<button class="text-button" data-history="${id}">查看任务记录</button>` : '',
    `<button class="text-button danger-text" data-delete-course="${id}" ${state.errors.courses ? 'disabled' : ''}>删除课程</button>`
  ].join('');
  return `<article class="course-card">
    <div class="course-body">
      <div class="course-top"><span class="course-icon cover-${index % 3}">${icon('book')}</span>${status}</div>
      <h3>${escapeHTML(course.title)}</h3>
      <p class="source-reference" title="${escapeHTML(course.sourceReference)}">${escapeHTML(course.sourceReference)}</p>
      <p class="course-description">${description}</p>
      <div class="course-actions">${mainAction}${showLinkBesideVideo ? linkButton : ''}</div>
      <details class="course-more"><summary>更多操作</summary><div>${extras}</div></details>
    </div></article>`;
}
function renderCollections(courses, tasks) {
  const container = $('#collections-panel');
  const opened = new Set([...container.querySelectorAll('details[open]')].map(item => item.dataset.collection));
  const shown = state.collections.filter(batch =>
    courses.some(course => course.id === batch.courseId) && (!state.taskCourse || batch.courseId === state.taskCourse)
  );
  container.hidden = !shown.length;
  const byId = new Map(tasks.map(task => [task.id, task]));
  const ready = new Set(state.notes.filter(note => note.ready).map(note => note.taskId));
  container.innerHTML = shown.map(batch => {
    const done = batch.entries.filter(entry => byId.get(entry.taskId)?.status === 'SUCCEEDED').length;
    const failed = batch.entries.filter(entry => entry.problem || byId.get(entry.taskId)?.status === 'FAILED').length;
    const label = batch.active ? '转写队列运行中' : batch.stopped ? '已停止排队' : '队列已结束';
    const id = escapeHTML(batch.id);
    const rows = batch.entries.map(entry => {
      const task = byId.get(entry.taskId);
      const name = escapeHTML(entry.episode.title);
      const status = task ? statusNames[task.status] : entry.problem ? '未处理' : batch.stopped ? '已停止' : '等待排队';
      const problem = task?.failureReason ?? entry.problem;
      const retry = !batch.active && task?.status === "FAILED"
        ? `<button class="text-button" data-retry-episode="${id}/${entry.episode.index}">重试此集</button>` : "";
      const button = ready.has(entry.taskId)
        ? `<button class="text-button task-open" data-note="${escapeHTML(entry.taskId)}">打开学习页</button>`
        : task ? `<button class="text-button" data-task="${escapeHTML(entry.taskId)}">详情</button>` : '';
      return `<li><span class="collection-episode-index">${entry.episode.index}</span>
        <div class="collection-episode-title">${name}${problem ? `<small>${escapeHTML(problem)}</small>` : ''}</div>
        <span class="status ${escapeHTML(task?.status ?? '')}">${status}</span><div class="task-actions">${button}${retry}</div></li>`;
    }).join('');
    return `<details class="collection-card" data-collection="${id}" ${opened.has(batch.id) ? 'open' : ''}>
      <summary>
        <span class="collection-summary-icon">${icon('list')}</span>
        <span class="collection-summary-copy">
          <strong>${escapeHTML(batch.title)}</strong>
          <small>${label} · 已完成 ${done} / ${batch.entries.length} 集${failed ? ` · ${failed} 集需查看` : ''}</small>
          <progress max="${batch.entries.length}" value="${done}" aria-label="合集已完成分集"></progress>
        </span>
      </summary>
      <div class="collection-body">
      ${batch.active ? `<button class="text-button danger-text" data-stop-collection="${id}">停止排队</button>` : ''}
      <ol>${rows}</ol></div></details>`;
  }).join('');
}
async function stopCollection(id, button) {
  if (!window.confirm('停止排队后，已经开始的分集仍会完成。确定停止剩余分集吗？')) return;
  button.disabled = true;
  try {
    await api(`/api/tasks/video/collections/${id}/stop`, { method: 'POST' });
    notify('已停止后续分集排队。');
    await loadData();
  } catch (error) {
    notify(error.message, true);
  } finally {
    button.disabled = false;
  }
}
function renderTasks(courses, tasks) {
  renderCollections(courses, tasks);
  const status = $('#task-filter').value;
  const filtered = tasks.filter(t=>(status==='all'||t.status===status)&&(!state.taskCourse||t.courseId===state.taskCourse));
  $('#task-count').textContent = state.loaded.tasks ? filtered.length : '—';
  const courseFilter = $('#task-course-filter'); courseFilter.hidden = !state.taskCourse;
  courseFilter.querySelector('span').textContent = state.taskCourse ? '课程：'+(courses.find(c=>c.id===state.taskCourse)?.title || '当前课程') : '';
  if (!state.loaded.tasks) { $('#task-table').innerHTML = empty('暂时没有可展示的任务数据','请确认任务服务正常运行，然后点击刷新。'); return; }
  if (!filtered.length) { $('#task-table').innerHTML = empty('这里还没有任务记录','在课程资料中选择一门课程，提交它的第一个处理任务。'); return; }
  $('#task-table').innerHTML = `<div class="table-wrap"><table><thead><tr><th>课程 / 任务编号</th><th>状态</th><th>处理说明</th><th>操作</th></tr></thead><tbody>${filtered.map(task=>{
    const course = courses.find(c=>c.id===task.courseId);
    const note = state.notes.find(item => item.taskId === task.id);
    const video = state.videos.find(item => item.taskId === task.id);
    const pending = note ? '等待校对与生成' : video?.sourceUrl ? '等待获取链接视频' : video ? '等待本机转写' : '手动任务，可在详情中取消';
    const processing = note ? '正在校对并生成笔记' : video?.sourceUrl ? '获取链接视频并转写中' : video ? '提取音频与转写中' : '处理中';
    const explanation = task.failureReason || ({ PENDING:pending,RUNNING:processing,SUCCEEDED:note?'笔记已生成':'已完成',FAILED:'处理失败' }[task.status] || '');
    const action = `<div class="task-actions">
      ${note?.ready ? `<button class="text-button task-open" data-note="${escapeHTML(task.id)}">打开学习页</button>` : ''}
      <button class="text-button" data-task="${escapeHTML(task.id)}">任务详情</button></div>`;
    return `<tr><td><div class="task-title" title="${escapeHTML(course?.title)}">${escapeHTML(course?.title || '课程不可见')}</div><span class="task-id">${escapeHTML(task.id.slice(0,8))}…</span></td><td>${badge(task.status)}</td><td><div class="failure-text" title="${escapeHTML(explanation)}">${escapeHTML(explanation)}</div></td><td>${action}</td></tr>`;
  }).join('')}</tbody></table></div>`;
}
function route() {
  const match = location.hash.match(/^#\/study\/([a-f0-9-]{36})$/);
  if (study.editing() && match?.[1] !== study.taskId() && !window.confirm('有未保存的修改，确定离开学习页吗？')) {
    history.replaceState(null, '', `#/study/${study.taskId()}`);
    return;
  }
  $('#study-view').hidden = !match;
  $('#overview').hidden = !!match;
  if (match) {
    $('#breadcrumb-page').textContent = '学习页';
    for (const link of document.querySelectorAll('[data-nav]')) link.classList.toggle('active', link.dataset.nav === 'tasks');
    study.open(match[1]);
    window.scrollTo(0, 0);
    return;
  }
  study.close();
  const view = location.hash.startsWith('#/tasks') ? 'tasks' : 'courses';
  $('#courses-view').hidden = view !== 'courses';
  $('#tasks-view').hidden = view !== 'tasks';
  for (const link of document.querySelectorAll('[data-nav]')) link.classList.toggle('active', link.dataset.nav === view);
  $('#breadcrumb-page').textContent = view === 'courses' ? '课程资料' : '任务记录';
  $('#page-index').textContent = view === 'courses' ? '学习手帐 · 01' : '学习手帐 · 02';
  $('#page-title').textContent = view === 'courses' ? '课程资料' : '任务记录';
  $('#page-subtitle').textContent = view === 'courses'
    ? '新建课程，上传视频、粘贴 B 站链接或添加字幕。完成后打开学习页。'
    : '查看转写与生成进度，或打开已完成的课程笔记。';
  $('#course-heading-actions').hidden = view !== 'courses';
}
async function health() {
  await Promise.all(['users','courses','tasks'].map(async key=>{
    const item = $(`#health-${key}`);
    try { const result=await api(`/api/health/${key}`); const up=result.status==='UP'; item.classList.toggle('up',up);item.classList.toggle('down',!up);item.title=up?'应用健康：UP':'应用未就绪'; }
    catch { item.classList.remove('up');item.classList.add('down');item.title='健康接口不可用'; }
  }));
}
async function loadData() {
  if (state.loading) return;
  state.loading=true;
  const buttons = [...document.querySelectorAll('.refresh-data')]; buttons.forEach(b=>b.disabled=true);
  try {
    const resources = {
      users:'/api/users', courses:'/api/courses', tasks:'/api/tasks',
      transcripts:'/api/courses/transcripts', notes:'/api/tasks/notes', videos:'/api/tasks/video', collections:'/api/tasks/video/collections'
    };
    const modelConfig = api('/api/tasks/notes/config')
      .then(config => { state.model = config; })
      .catch(() => { state.model = {configured:false}; });
    const videoConfig = api('/api/tasks/video/config')
      .then(config => {
        state.videoAvailable = config.available;
        state.linkAvailable = config.linkAvailable;
      })
      .catch(() => { state.videoAvailable = state.linkAvailable = false; });
    const lists = Object.entries(resources).map(async ([key, url])=>{
      try { const data=await api(url); if(!Array.isArray(data))throw new Error('列表格式错误'); state[key]=data;state.loaded[key]=true;state.errors[key]=false; }
      catch { state.errors[key]=true; }
    });
    await Promise.all([...lists, modelConfig, videoConfig]);
    if(!state.users.some(u=>u.id===state.selected)) {
      let saved='';try{saved=localStorage.getItem('courselens-user')||'';}catch{}
      saveSelection(state.users.find(u=>u.id===saved)?.id || state.users[0]?.id || '');
    }
    render();
    $('#updated-at').textContent = Object.values(state.errors).some(Boolean) ? '部分数据读取失败' : '最近同步 '+new Date().toLocaleTimeString('zh-CN',{hour12:false});
  } finally { state.loading=false;buttons.forEach(b=>b.disabled=false); }
  await health();
}
function openCourse() {
  if (!currentUser()) { openUser(); return; }
  if(state.errors.users||state.errors.courses) {notify('请先恢复用户和课程服务，再登记课程',true);return;}
  $('#course-form').reset();$('#course-error').hidden=true;
  $('#course-owner').textContent=currentUser().displayName;
  $('#course-dialog').showModal();
}
function openUser() { $('#user-form').reset();$('#user-error').hidden=true;$('#user-dialog').showModal(); }
function formError(id,error) { const item=$(id);item.textContent=error.message;item.hidden=false; }
function showTask(id) {
  const task = myTasks().find(item => item.id === id);
  if (!task) return;
  const course = myCourses().find(item => item.id === task.courseId);
  const note = state.notes.find(item => item.taskId === id);
  const video = state.videos.find(item => item.taskId === id);
  const processing = note ? '校对与生成笔记' : video?.sourceUrl ? '获取链接视频并转写' : video ? '提取音频与转写' : '手动更新状态';
  const fields = [['课程', course?.title], ['任务类型', video?.sourceUrl ? '链接视频转写' : video ? '视频转写与笔记' : note ? '笔记生成' : '手动任务'],
    ['当前状态', statusNames[task.status]], ['处理说明', task.failureReason || (active(task) ? processing : '已结束')], ['任务编号', id]];
  $('#task-detail').innerHTML = `<dl>${fields.map(([name, value]) =>
    `<div class="detail-row"><dt>${escapeHTML(name)}</dt><dd>${escapeHTML(value)}</dd></div>`).join('')}</dl>`;
  const manual = !note && !video && !state.errors.notes && !state.errors.videos && active(task);
  const inCollection = state.collections.some(batch => batch.entries.some(entry => entry.taskId === id));
  const retry = !inCollection && task.status === 'FAILED' && (note || video);
  $('#task-detail-actions').innerHTML = note?.ready
    ? `<button class="primary" data-note="${escapeHTML(id)}">打开学习页</button>`
    : manual ? `<button class="secondary danger-text" data-cancel-manual="${escapeHTML(id)}">取消手动任务</button>` : retry ? `<button class="secondary" data-retry-task="/api/tasks/${note ? 'notes' : 'video'}/${escapeHTML(id)}/retry">重试</button>` : '';
  const dialog = $('#task-dialog');
  dialog.dataset.taskId = id;
  if (!dialog.open) dialog.showModal();
}
async function retryTask(path, button) {
  button.disabled = true;
  try {
    await api(path, { method: 'POST' });
    $('#task-dialog').close();
    notify('重试任务已提交。');
    await loadData();
  } catch (error) {
    notify(error.message, true);
  } finally { button.disabled = false; }
}
async function cancelManual(id, button) {
  button.disabled = true;
  try {
    await api(`/api/tasks/${id}/cancel`, { method: 'POST' });
    $('#task-dialog').close();
    notify('手动任务已取消，可以返回课程上传视频。');
    await loadData();
  } catch (error) {
    notify(error.message, true);
  } finally {
    button.disabled = false;
  }
}
function openDelete(courseId) {
  const course = myCourses().find(item => item.id === courseId);
  if (!course) return;
  $('#delete-course-form').dataset.courseId = courseId;
  $('#delete-course-name').textContent = course.title;
  $('#delete-course-error').hidden = true;
  $('#delete-course-dialog').showModal();
}
async function deleteCourse(event) {
  event.preventDefault();
  const courseId = event.currentTarget.dataset.courseId;
  const button = event.submitter;
  button.disabled = true;
  $('#delete-course-error').hidden = true;
  try {
    await api(`/api/courses/${courseId}`, { method: 'DELETE' });
    state.courses = state.courses.filter(course => course.id !== courseId);
    if (state.taskCourse === courseId) state.taskCourse = null;
    $('#delete-course-dialog').close();
    render();
    notify('课程已从工作台移除。');
    await loadData();
  } catch (error) {
    formError('#delete-course-error', error);
  } finally {
    button.disabled = false;
  }
}
$('#delete-course-form').addEventListener('submit',deleteCourse);
$('#user-select').addEventListener('change',e=>{saveSelection(e.target.value);state.taskCourse=null;render();});
$('#import-link').addEventListener('click', () => linkImport.open());
$('#new-user').addEventListener('click',openUser);$('#new-course').addEventListener('click',openCourse);
document.querySelectorAll('.refresh-data').forEach(button=>button.addEventListener('click',loadData));
$('#course-search').addEventListener('input',()=>renderCourses(myCourses(),myTasks()));
$('#task-filter').addEventListener('change',()=>renderTasks(myCourses(),myTasks()));
$('#clear-task-course').addEventListener('click',()=>{state.taskCourse=null;render();});
document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{state.filter=button.dataset.filter;document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b===button));renderCourses(myCourses(),myTasks());}));
document.addEventListener('click',event=>{
  const button=event.target.closest('button');if(!button)return;
  const menu = button.closest('.course-more');
  if (menu) menu.open = false;
  if(button.classList.contains('close-dialog'))button.closest('dialog').close();
  if(button.dataset.action==='new-course')openCourse();if(button.dataset.action==='new-user')openUser();
  if (button.dataset.cancelManual) cancelManual(button.dataset.cancelManual,button);
  if (button.dataset.deleteCourse) openDelete(button.dataset.deleteCourse);
  if(button.dataset.upload)openUpload(button.dataset.upload);
  if(button.dataset.generate)generateNote(button.dataset.generate,button);
  if (button.dataset.note) {
    button.closest('dialog')?.close();
    location.hash = '/study/' + button.dataset.note;
  }
  if(button.dataset.video)openVideo(button.dataset.video);
  if (button.dataset.link) linkImport.open(myCourses().find(course => course.id === button.dataset.link));
  if (button.dataset.retryEpisode) {
    const [batch, episode] = button.dataset.retryEpisode.split('/');
    retryTask(`/api/tasks/video/collections/${batch}/episodes/${episode}/retry`, button);
  }
  if (button.dataset.retryTask) retryTask(button.dataset.retryTask, button);
  if (button.dataset.stopCollection) stopCollection(button.dataset.stopCollection, button);
  if(button.dataset.history){state.taskCourse=button.dataset.history;$('#task-filter').value='all';location.hash='/tasks';render();}
  if(button.dataset.task)showTask(button.dataset.task);
});
$('#user-form').addEventListener('submit',async event=>{
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]');
  const name=$('#user-name').value.trim();if(!name){formError('#user-error',new Error('请填写显示名称'));return;}
  button.disabled=true;
  try {const user=await api('/api/users',{method:'POST',body:JSON.stringify({displayName:name})});state.users.push(user);state.loaded.users=true;state.errors.users=false;saveSelection(user.id);state.taskCourse=null;$('#user-dialog').close();render();notify('用户已创建，现在可以登记课程');await loadData();}
  catch(error){formError('#user-error',error);}finally{button.disabled=false;}
});
$('#course-form').addEventListener('submit',async event=>{
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]');
  const title=$('#course-title').value.trim(),sourceReference=$('#course-source').value.trim(),ownerId=state.selected;
  if(!title||!sourceReference){formError('#course-error',new Error('请填写课程名称和资料引用'));return;}
  if(!currentUser()){formError('#course-error',new Error('请先选择用户'));return;}
  button.disabled=true;
  try {await api('/api/courses',{method:'POST',body:JSON.stringify({ownerId,title,sourceReference})});$('#course-dialog').close();state.filter='all';$('#course-search').value='';document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b.dataset.filter==='all'));location.hash='/courses';notify('课程已保存，可以为它提交任务');await loadData();}
  catch(error){formError('#course-error',error);}finally{button.disabled=false;}
});
function openUpload(courseId) {
  const course = myCourses().find(item => item.id === courseId);
  if (!course) return;
  const form = $('#upload-form');
  form.reset();
  form.dataset.courseId = courseId;
  $('#upload-course').textContent = course.title;
  $('#upload-error').hidden = true;
  $('#upload-dialog').showModal();
}

$('#upload-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('[type=submit]');
  const file = $('#subtitle-file').files[0];
  if (!file) return;
  if (file.size > 65536) {
    formError('#upload-error', new Error('文件不能超过 64 KB'));
    return;
  }
  button.disabled = true;
  $('#upload-error').hidden = true;
  try {
    let content;
    try { content = new TextDecoder('utf-8', {fatal:true}).decode(await file.arrayBuffer()); }
    catch { throw new Error('请将字幕保存为 UTF-8 编码后重新上传'); }
    const info = await api(`/api/courses/${form.dataset.courseId}/transcript`, {
      method:'POST', body:JSON.stringify({fileName:file.name, content})
    });
    state.transcripts = state.transcripts.filter(item => item.courseId !== info.courseId);
    state.transcripts.push(info);
    $('#upload-dialog').close();
    render();
    notify('字幕已保存，可以生成笔记');
    await loadData();
  } catch (error) {
    formError('#upload-error', error);
  } finally {
    button.disabled = false;
  }
});

async function generateNote(courseId, button) {
  const options = await generation.choose();
  if (!options) return;
  button.disabled = true;
  try {
    await api('/api/tasks/notes', {method:'POST', body:JSON.stringify({courseId, options})});
    state.taskCourse = courseId;
    $('#task-filter').value = 'all';
    location.hash = '/tasks';
    notify('已提交笔记生成任务，完成后可查看');
  } catch (error) {
    notify(error.message, true);
  } finally {
    await loadData();
    if (button.isConnected) button.disabled = false;
  }
}

function openVideo(courseId) {
  const course = myCourses().find(item => item.id === courseId);
  if (!course) return;
  const form = $('#video-form');
  form.reset();
  form.dataset.courseId = courseId;
  generation.fields(form.querySelector('.generation-host'));
  $('#video-course').textContent = course.title;
  $('#video-error').hidden = true;
  $('#video-dialog').showModal();
}

$('#video-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const file = $('#video-file').files[0];
  if (!file) return;
  if (file.size > 1024 * 1024 * 1024) {
    formError('#video-error', new Error('视频不能超过 1 GB'));
    return;
  }
  const button = form.querySelector('[type=submit]');
  const body = new FormData();
  for (const [name, value] of Object.entries(generation.options(form))) body.append(name, String(value));
  body.append('file', file);
  button.disabled = true;
  button.textContent = '正在上传…';
  $('#video-error').hidden = true;
  try {
    await api(`/api/tasks/video/upload/${form.dataset.courseId}`, {method:'POST', body});
    state.taskCourse = form.dataset.courseId;
    $('#task-filter').value = 'all';
    $('#video-dialog').close();
    location.hash = '/tasks';
    notify('视频已保存，等待本机转写');
    await loadData();
  } catch (error) {
    formError('#video-error', error);
  } finally {
    button.disabled = false;
    button.textContent = '上传并生成笔记';
  }
});

const linkImport = createLinkImport({
  api, formError, generation,
  createCourse(title, sourceReference) {
    return api('/api/courses', {
      method: 'POST', body: JSON.stringify({ ownerId: state.selected, title, sourceReference })
    });
  },
  onQueued(courseId, batch) {
    state.taskCourse = courseId;
    $('#task-filter').value = 'all';
    location.hash = '/tasks';
    notify(batch ? '合集已加入队列，会按分集依次处理。' : '链接已提交，等待获取视频。');
    loadData();
  }
});

const study = createStudy({
  api, notify, escapeHTML, generation, canSendNotion: () => session.notion,
  onOpen(course) {
    saveSelection(course.ownerId);
    state.taskCourse = course.id;
    render();
  },
  onRegenerate(courseId) {
    const course = state.courses.find(item => item.id === courseId);
    if (course) saveSelection(course.ownerId);
    state.taskCourse = courseId;
    $('#task-filter').value = 'all';
    location.hash = '/tasks';
    notify('已提交重新校对，旧笔记仍可打开。');
    loadData();
  }
});

const loginDialog = $('#login-dialog');
loginDialog.addEventListener('cancel', event => event.preventDefault());
$('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('[type=submit]');
  button.disabled = true;
  try {
    await api('/api/session/login', { method: 'POST', body: JSON.stringify({
      username: $('#login-name').value.trim(), password: $('#login-password').value
    }) });
    location.reload();
  } catch (error) {
    formError('#login-error', error);
  } finally {
    $('#login-password').value = '';
    button.disabled = false;
  }
});
$('#logout').addEventListener('click', async () => {
  try {
    await api('/api/session/logout', { method: 'POST' });
    location.reload();
  } catch (error) { notify(error.message, true); }
});

async function start() {
  try {
    session = await api('/api/session');
    if (session.enabled && !session.userId) {
      loginDialog.showModal();
      return;
    }
    $('#logout').hidden = !session.enabled;
    $('#new-user').hidden = session.enabled;
    if (session.userId) saveSelection(session.userId);
    window.addEventListener('hashchange', route);
    await loadData();
    route();
  } catch (error) { notify(error.message, true); }
}
start();

setInterval(() => {
  if (session.enabled && !session.userId) return;
  if (document.hidden || document.querySelector('dialog[open]:not(#task-dialog), .course-more[open]')) return;
  const detail = $('#task-dialog');
  if (detail.open && detail.querySelector('button:disabled')) return;
  loadData().then(() => {
    if (detail.open && !detail.querySelector('button:disabled')) showTask(detail.dataset.taskId);
  });
}, 10000);
