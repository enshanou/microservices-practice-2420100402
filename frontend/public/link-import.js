export function createLinkImport({ api, formError, createCourse, onQueued, generation }) {
  const form = document.querySelector('#link-form');
  const dialog = document.querySelector('#link-dialog');
  const input = document.querySelector('#link-url');
  const title = document.querySelector('#link-video-title');
  const info = document.querySelector('#link-preview');
  const lookup = document.querySelector('#link-lookup');
  const start = document.querySelector('#link-start');
  const picker = document.querySelector('#link-episode');
  const scope = document.querySelector('#link-scope');
  const one = document.querySelector('#link-scope-one');
  const all = document.querySelector('#link-scope-all');
  const episodeRow = document.querySelector('#link-episode-row');
  const error = document.querySelector('#link-error');
  let preview, previewedText, timer, ticket = 0;

  function duration(seconds) {
    if (!seconds) return '时长未知';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor(seconds / 60) % 60;
    const s = seconds % 60;
    return h ? `${h} 小时 ${m} 分钟` : `${m}:${String(s).padStart(2, '0')}`;
  }

  function updateChoice() {
    if (!preview) return;
    const selected = preview.episodes.find(episode => episode.index === Number(picker.value));
    const batch = all.checked && preview.episodes.length > 1;
    episodeRow.hidden = batch || preview.episodes.length < 2;
    const knownSeconds = preview.episodes.reduce((sum, episode) => sum + episode.seconds, 0);
    const tooLong = preview.episodes.filter(episode => episode.seconds > 3600).length;
    const note = batch && tooLong ? `。其中 ${tooLong} 集超过 60 分钟，将列为未处理` : '';
    document.querySelector('#link-preview-status').textContent = batch
      ? `${preview.episodes.length} 集 · ${duration(knownSeconds)}${note}`
      : selected ? `第 ${selected.index} 集 · ${duration(selected.seconds)}` : '请选一集';
    start.textContent = batch ? `开始转写全部 ${preview.episodes.length} 集` : '开始转写这一集';
    start.disabled = batch ? tooLong === preview.episodes.length : !selected || selected.seconds > 3600;
    if (!batch && selected?.seconds > 3600) {
      document.querySelector('#link-preview-status').textContent += ' · 超过 60 分钟';
    }
  }

  function shareTitle(text) {
    const prefix = text.match(/^(.*?)https:\/\//s)?.[1]?.trim() ?? '';
    const name = prefix.startsWith('【') && prefix.endsWith('】') ? prefix.slice(1, -1) : prefix;
    return name.trim().slice(0, 200);
  }

  async function identify() {
    clearTimeout(timer);
    const text = input.value.trim();
    if (!text) {
      formError('#link-error', new Error('请粘贴支持的平台视频链接或整段分享文字'));
      return;
    }
    const request = ++ticket;
    lookup.disabled = true;
    lookup.textContent = '正在识别…';
    start.disabled = true;
    error.hidden = true;
    try {
      const data = await api('/api/tasks/video/preview', {
        method: 'POST', body: JSON.stringify({ shared: text })
      });
      if (request !== ticket) return;
      preview = data;
      previewedText = text;
      title.value = title.value.trim() || data.title;
      picker.replaceChildren(...data.episodes.map(episode =>
        new Option(`第 ${episode.index} 集 · ${episode.title} · ${duration(episode.seconds)}`, episode.index)
      ));
      picker.value = String(data.selectedIndex);
      scope.hidden = data.episodes.length < 2;
      info.hidden = false;
      updateChoice();
    } catch (err) {
      if (request === ticket) formError('#link-error', err);
    } finally {
      if (request === ticket) {
        lookup.disabled = false;
        lookup.textContent = '识别链接';
      }
    }
  }

  function open(course) {
    clearTimeout(timer);
    ticket++;
    preview = previewedText = null;
    form.reset();
    generation.fields(form.querySelector('.generation-host'));
    form.dataset.courseId = course?.id ?? '';
    document.querySelector('#link-course').textContent = course?.title ?? '根据链接新建课程';
    error.hidden = info.hidden = true;
    lookup.textContent = '识别链接';
    lookup.disabled = false;
    input.disabled = false;
    start.disabled = true;
    dialog.showModal();
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    ticket++;
    preview = previewedText = null;
    info.hidden = true;
    start.disabled = true;
    lookup.disabled = false;
    lookup.textContent = '识别链接';
    error.hidden = true;
    title.value = shareTitle(input.value);
    if (/https:\/\/(?:(?:www\.|m\.)?bilibili\.com\/video\/|b23\.tv\/|(?:www\.|m\.)?youtube\.com\/|youtu\.be\/|(?:www\.|player\.)?vimeo\.com\/)[\w]/i.test(input.value)) {
      timer = setTimeout(identify, 400);
    }
  });
  lookup.addEventListener('click', identify);
  one.addEventListener('change', updateChoice);
  all.addEventListener('change', updateChoice);
  picker.addEventListener('change', updateChoice);
  dialog.addEventListener('close', () => {
    clearTimeout(timer);
    ticket++;
  });

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!preview || previewedText !== input.value.trim() || start.disabled) return;
    const batch = all.checked && preview.episodes.length > 1;
    const selected = preview.episodes.find(episode => episode.index === Number(picker.value));
    const name = title.value.trim() || (batch ? preview.collectionTitle : preview.title);
    const source = batch ? preview.url : selected.url;
    let courseId = form.dataset.courseId;
    const formState = ticket;
    const label = start.textContent;
    start.textContent = '正在提交…';
    start.disabled = lookup.disabled = input.disabled = true;
    error.hidden = true;
    try {
      if (!courseId) {
        const course = await createCourse(name, source);
        if (formState !== ticket) return;
        courseId = course.id;
        form.dataset.courseId = courseId;
        document.querySelector('#link-course').textContent = course.title;
      }
      await api(batch ? '/api/tasks/video/collections' : '/api/tasks/video/link', {
        method: 'POST',
        body: JSON.stringify({ ...batch ? { courseId, shared: previewedText, title: name } :
          { courseId, url: selected.url, title: preview.episodes.length > 1 ? selected.title : name }, options: generation.options(form) })
      });
      if (formState === ticket) {
        dialog.close();
        onQueued(courseId, batch);
      }
    } catch (err) {
      if (formState === ticket) formError('#link-error', err);
    } finally {
      if (formState === ticket) {
        input.disabled = lookup.disabled = false;
        start.textContent = label;
        updateChoice();
      }
    }
  });
  return { open };
}
