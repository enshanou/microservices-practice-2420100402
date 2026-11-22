import { createNotebookTools } from './notebook.js';

export function createStudy({ api, notify, escapeHTML: html, onRegenerate, generation, canSendNotion, onOpen = () => {} }) {
  const notebook = createNotebookTools(html);
  const view = document.querySelector('#study-view');
  const byId = id => document.getElementById(id);
  const panel = byId('study-panel');
  const video = byId('study-video');
  const player = byId('study-player');
  const position = byId('study-position');
  const editButton = byId('study-edit');
  const regenerate = byId('study-regenerate');
  let id, task, course, note, draft, pendingSeek, locatedTime;
  let savedSelection = null, saving = false;
  let tab = 'notes', raw = false;
  let selectedText = '', question = null;
  const pet = byId('pet-dialog');
  const petButton = byId('pet-trigger');
  const notionButton = byId('study-notion');
  const notionLink = byId('study-notion-result');
  const exportMenu = view.querySelector('.study-export');
  const petModes = [byId('pet-rephrase'), byId('pet-example')];

  function message(text) {
    const node = byId('study-message');
    node.textContent = text;
    node.hidden = !text;
  }

  function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const stamp = value => String(value).padStart(2, '0');
    return minutes >= 60
      ? `${stamp(Math.floor(minutes / 60))}:${stamp(minutes % 60)}:${stamp(seconds % 60)}`
      : `${stamp(minutes)}:${stamp(seconds % 60)}`;
  }

  function timeLink(seconds) {
    if (!Number.isInteger(seconds) || seconds < 0) return '';
    const time = formatTime(seconds);
    return note.videoTaskId
      ? `<button class="time-link" type="button" data-seek="${seconds}" aria-label="跳到 ${time}">${time}</button>`
      : `<span class="time-link">${time}</span>`;
  }

  function showLocation() {
    position.hidden = locatedTime == null;
    position.textContent = locatedTime == null ? '' : '定位 ' + formatTime(locatedTime);
    for (const button of panel.querySelectorAll('[data-seek]')) {
      if (Number(button.dataset.seek) === locatedTime) button.setAttribute('aria-current', 'location');
      else button.removeAttribute('aria-current');
    }
  }

  function seek(seconds) {
    if (video.error) {
      notify('这个文件无法在当前浏览器内播放。可以下载原视频，按时间定位。', true);
    } else if (!video.readyState) {
      pendingSeek = seconds;
    } else {
      video.currentTime = Number.isFinite(video.duration) ? Math.min(seconds, video.duration) : seconds;
      locatedTime = seconds;
      showLocation();
      if (window.matchMedia('(max-width: 680px)').matches) player.scrollIntoView({ block: 'nearest' });
    }
  }

  function transcriptView() {
    const help = note.reviewed
      ? '按段落阅读。标有「待回看」的段落可点时间核对，也可以切换到原始识别。'
      : '这份旧结果已整理成段落。点「重新校对并生成」可生成一份带术语复核的新结果。';
    return `<div class="transcript-toolbar"><p class="study-help">${help}</p>
      <button class="secondary" type="button" data-toggle-raw>${raw ? '查看校对稿' : '对照原始识别'}</button></div>
      ${raw ? `<pre class="note-transcript">${html(note.transcript)}</pre>` :
        `<div class="transcript-paragraphs">${note.paragraphs.map(paragraph => `<article class="transcript-paragraph">
          <div class="paragraph-meta">${timeLink(paragraph.startSeconds)}${note.reviewed && paragraph.needsReview ? '<span class="review-mark">待回看</span>' : ''}</div>
          <p>${html(paragraph.text)}</p></article>`).join('')}</div>`}`;
  }

  const activeContent = () => tab === 'map' ? note.mindMap : note.content;

  function render() {
    for (const button of view.querySelectorAll('[data-study-tab]')) {
      button.setAttribute('aria-pressed', String(button.dataset.studyTab === tab));
      button.disabled = !!draft;
    }
    editButton.hidden = tab === 'transcript' || !!draft;
    editButton.textContent = tab === 'map' ? '编辑导图' : '编辑正文';
    regenerate.disabled = !!draft;
    savedSelection = null;
    selectedText = '';
    byId('pet-hint').textContent = '选中一段文字，请桌宠换个说法或举例。';
    petButton.hidden = tab !== 'notes';
    byId('pet-hint').hidden = tab !== 'notes';
    exportMenu.hidden = !!draft;
    panel.innerHTML = tab === 'transcript' ? transcriptView() : notebook.renderNotebook(
      draft ?? notebook.draftFromNote(activeContent()), {
        tab, editing: !!draft, courseTitle: course.title, timeLink, hasVideo: !!note.videoTaskId
      }
    );
    showLocation();
  }

function editStructure(button) {
  draft = notebook.readDraft(panel, draft);
  const node = button.closest('[data-inline-section]');
  const index = node ? Number(node.dataset.inlineSection) : null;
  const data = button.dataset;
  let focus;
  if ('appendSection' in data) {
    if (draft.sections.length >= 600) return;
    draft.sections.push(notebook.blankSection());
    focus = `[data-inline-section="${draft.sections.length - 1}"] [data-note-block="title"]`;
  } else if ('removeSection' in data) {
    draft.sections.splice(index, 1);
  } else if ('appendPoint' in data) {
    if (draft.sections[index].points.length >= 25) return;
    draft.sections[index].points.push(notebook.block());
    focus = `[data-inline-section="${index}"] li:last-child [data-note-block="point"]`;
  } else {
    draft.sections[index].points.splice(Number(data.removePoint), 1);
  }
  render();
  if (focus) panel.querySelector(focus)?.focus();
}

async function saveInline(button) {
  if (!draft || saving) return;
  const errorNode = byId('study-save-error');
  errorNode.hidden = true;
  const isMap = tab === 'map';
  let revision;
  try {
    draft = notebook.readDraft(panel, draft);
    revision = notebook.revision(draft, isMap ? note.mapVersion : note.version, isMap);
  } catch (error) {
    errorNode.textContent = error.message;
    errorNode.hidden = false;
    return;
  }
  const editingId = id;
  saving = true;
  panel.inert = true;
  button.textContent = '保存中…';
  try {
    const updated = await api(`/api/tasks/notes/${editingId}${isMap ? '/mind-map' : ''}`, {
      method: 'PATCH', body: JSON.stringify(revision)
    });
    if (id !== editingId) return;
    note = updated;
    draft = null;
    panel.inert = false;
    render();
    notify(isMap ? '导图已保存。' : '笔记已保存。');
    editButton.focus();
  } catch (error) {
    if (id !== editingId) return;
    errorNode.textContent = error.message;
    errorNode.hidden = false;
  } finally {
    if (id === editingId) {
      saving = false;
      panel.inert = false;
      button.textContent = '保存修改';
    }
  }
}

  async function open(taskId) {
    if (id === taskId) return;
    close();
    id = taskId;
    byId('study-title').textContent = '学习页';
    byId('study-meta').textContent = '';
    message('正在读取学习资料…');
    try {
      const [loadedTask, loadedNote] = await Promise.all([
        api(`/api/tasks/${taskId}`), api(`/api/tasks/notes/${taskId}`)
      ]);
      const loadedCourse = await api(`/api/courses/${loadedTask.courseId}`);
      if (id !== taskId) return;
      task = loadedTask;
      note = loadedNote;
      course = loadedCourse;
      onOpen(course);
      byId('study-title').textContent = course.title;
      byId('study-meta').textContent = note.videoTaskId ? note.model : `${note.fileName} · ${note.model}`;
      byId('study-video-title').textContent = note.fileName;
      const sourceLink = byId('study-original-link');
      sourceLink.hidden = !note.sourceUrl;
      if (note.sourceUrl) sourceLink.href = note.sourceUrl;
      const hasVideo = !!note.videoTaskId;
      player.hidden = !hasVideo;
      byId('study-layout').classList.toggle('without-video', !hasVideo);
      if (hasVideo) {
        const source = `/api/tasks/video/${note.videoTaskId}/media`;
        const download = byId('study-download');
        download.href = source;
        download.download = note.fileName;
        video.src = source;
      }
      const endpoint = '/api/tasks/notes/' + id;
      byId('study-export-md').href = endpoint + '/export.md';
      byId('study-export-txt').href = endpoint + '/export.txt';
      notionButton.hidden = !canSendNotion();
      byId('study-layout').hidden = false;
      regenerate.hidden = false;
      message('');
      render();
    } catch (error) {
      if (id === taskId) {
        id = null;
        message(error.message);
      }
    }
  }

  function close() {
    if (pet.open) pet.close();
    selectedText = '';
    question = null;
    exportMenu.open = false;
    notionLink.hidden = true;
    notionLink.removeAttribute('href');
    if (video.hasAttribute('src')) {
      video.pause();
      video.removeAttribute('src');
      video.load();
    }
    id = task = course = note = draft = pendingSeek = savedSelection = locatedTime = null;
    position.hidden = true;
    saving = false;
    panel.inert = false;
    byId('study-original-link').hidden = true;
    tab = 'notes';
    raw = false;
    byId('study-layout').hidden = true;
    byId('study-player-error').hidden = true;
    regenerate.hidden = true;
    regenerate.disabled = false;
    panel.replaceChildren();
  }

  video.addEventListener('loadedmetadata', () => {
    if (pendingSeek !== null) {
      seek(pendingSeek);
      pendingSeek = null;
    }
  });
  video.addEventListener('error', () => {
    if (!video.hasAttribute('src')) return;
    const error = byId('study-player-error');
    error.textContent = '当前浏览器无法播放此文件。可下载原视频，按右侧时间标签定位。';
    error.hidden = false;
  });
  view.addEventListener('click', async event => {
    const button = event.target.closest('button');
    if (!button || !note) return;
    const data = button.dataset;
    if (data.studyTab) {
      tab = data.studyTab;
      render();
    } else if ('toggleRaw' in data) {
      raw = !raw;
      render();
    } else if (data.seek !== undefined) {
      seek(Number(data.seek));
    } else if (button === editButton) {
      draft = notebook.draftFromNote(activeContent());
      render();
      panel.querySelector('[data-note-block]')?.focus();
    } else if ('discardEdit' in data) {
      draft = null;
      render();
      editButton.focus();
    } else if ('saveInline' in data) {
      await saveInline(button);
    } else if (data.ink) {
      const selected = notebook.selection(panel) ?? savedSelection;
      const changed = notebook.applyInk(panel, selected, data.ink);
      byId('stationery-hint').textContent = changed ? '标注已添加到正文，记得保存' : '请先在同一段文字内选中内容';
    } else if ('appendSection' in data || 'removeSection' in data || 'appendPoint' in data || data.removePoint !== undefined) {
      editStructure(button);
    } else if ('usePosition' in data) {
      const field = button.closest('[data-inline-section]').querySelector('[data-inline-time]');
      field.value = Math.min(3600, Math.floor(video.currentTime));
    } else if (button === petButton) {
      if (!selectedText) return notify('先选中想了解的一段笔记。');
      if (selectedText.length > 2400) return notify('请选择 2400 字以内的一段。', true);
      question = { id, text: selectedText };
      byId('pet-excerpt').textContent = selectedText;
      byId('pet-answer').textContent = '';
      petModes.forEach(mode => mode.disabled = false);
      pet.showModal();
    } else if (button === notionButton) {
      if (!window.confirm('将当前已保存的笔记发送到配置的 Notion 页面下？')) return;
      const sourceId = id;
      button.disabled = true;
      try {
        const result = await api('/api/tasks/notes/' + sourceId + '/notion', { method: 'POST' });
        if (id === sourceId && /^https:\/\//.test(result.url)) {
          notionLink.href = result.url;
          notionLink.hidden = false;
          notify('笔记已发送到 Notion。');
        }
      } catch (error) { notify(error.message, true); }
      finally { button.disabled = false; }
    } else if (button === regenerate) {
      const sourceId = id;
      const options = await generation.choose(note.options);
      if (!options || id !== sourceId) return;
      button.disabled = true;
      try {
        await api(`/api/tasks/notes/${sourceId}/regenerate`, { method: 'POST', body: JSON.stringify(options) });
        if (id === sourceId) onRegenerate(task.courseId);
      } catch (error) {
        notify(error.message, true);
      } finally {
        button.disabled = !!draft;
      }
    }
  });
  async function ask(mode) {
    const current = question;
    if (!current) return;
    petModes.forEach(button => button.disabled = true);
    const answer = byId('pet-answer');
    answer.textContent = '桌宠正在读这段笔记…';
    try {
      const result = await api('/api/tasks/notes/' + current.id + '/explain', {
        method: 'POST', body: JSON.stringify({ text: current.text, mode })
      });
      if (question === current && pet.open) answer.textContent = result.answer;
    } catch (error) {
      if (question === current && pet.open) answer.textContent = error.message;
    } finally {
      if (question === current) petModes.forEach(button => button.disabled = false);
    }
  }
  petModes[0].addEventListener('click', () => ask('rephrase'));
  petModes[1].addEventListener('click', () => ask('example'));
  pet.addEventListener('close', () => { if (!pet.open) question = null; });
  petButton.addEventListener('mousedown', event => event.preventDefault());
  document.addEventListener('selectionchange', () => {
    const range = window.getSelection();
    if (tab === 'notes' && range && !range.isCollapsed && panel.contains(range.anchorNode) && panel.contains(range.focusNode)) {
      selectedText = range.toString().trim();
      byId('pet-hint').textContent = selectedText ? '已选中笔记，点击「问问桌宠」。' : '选中一段文字，请桌宠换个说法或举例。';
    } else if (document.activeElement !== petButton && !pet.open) {
      selectedText = '';
      byId('pet-hint').textContent = '选中一段文字，请桌宠换个说法或举例。';
    }
    if (!draft) return;
    const selected = notebook.selection(panel);
    if (selected) savedSelection = selected;
    else if (document.activeElement?.matches('[data-note-block]')) savedSelection = null;
  });
  panel.addEventListener('mousedown', event => {
    if (event.target.closest('[data-ink]')) event.preventDefault();
  });
  panel.addEventListener('change', event => {
    if (event.target.matches('[data-inline-color]')) {
      event.target.closest('[data-inline-section]').dataset.branchColor = event.target.value;
    }
  });
  panel.addEventListener('input', event => {
    if (event.target.matches('[data-note-block]')) savedSelection = null;
  });
  panel.addEventListener('beforeinput', event => {
    const field = event.target.closest('[data-note-block]');
    if (!field || !['insertParagraph', 'insertLineBreak'].includes(event.inputType)) return;
    event.preventDefault();
    if (field.dataset.noteBlock === 'summary') notebook.insertText(field, '\n');
  });
  panel.addEventListener('paste', event => {
    const field = event.target.closest('[data-note-block]');
    if (!field) return;
    event.preventDefault();
    const value = event.clipboardData.getData('text/plain').replace(/\r\n?/g, '\n');
    notebook.insertText(field, field.dataset.noteBlock === 'summary' ? value : value.replace(/\n/g, ' '));
  });
  panel.addEventListener('drop', event => {
    if (event.target.closest('[data-note-block]')) event.preventDefault();
  });
  window.addEventListener('beforeunload', event => {
    if (draft) {
      event.preventDefault();
      event.returnValue = '';
    }
  });
  return { open, close, editing: () => !!draft, taskId: () => id };
}
