export function createNotebookTools(html) {
  const inkNames = {
    underline: '下划线', wave: '波浪线', yellow: '黄色高亮',
    mint: '绿色高亮', sky: '蓝色高亮', rose: '粉色高亮'
  };
  const colors = { default: '默认', mint: '绿色', sky: '蓝色', yellow: '黄色', rose: '粉色', lavender: '紫色' };
  const inkKinds = Object.keys(inkNames);
  const highlights = ['yellow', 'mint', 'sky', 'rose'];
  const block = (text = '', marks = []) => ({ text, marks });

  function draftFromNote({ summary, sections, appearance }) {
    const marks = appearance?.marks ?? {};
    return {
      summary: block(summary, marks.summary),
      sections: sections.map((section, i) => ({
        title: block(section.title, marks[`${i}.title`]),
        points: section.points.map((text, j) => block(text, marks[`${i}.point.${j}`])),
        startSeconds: section.startSeconds,
        color: appearance?.branchColors?.[i] ?? 'default'
      }))
    };
  }

  function formatText({ text, marks }) {
    const events = new Map();
    const active = new Map();
    for (const mark of marks) {
      if (!Object.hasOwn(inkNames, mark.ink) || mark.start < 0 || mark.end > text.length || mark.start >= mark.end) continue;
      for (const [offset, delta] of [[mark.start, 1], [mark.end, -1]]) {
        if (!events.has(offset)) events.set(offset, []);
        events.get(offset).push([mark.ink, delta]);
      }
    }
    let output = '', previous = 0;
    const append = end => {
      if (end === previous) return;
      const value = html(text.slice(previous, end));
      const inks = inkKinds.filter(ink => active.get(ink) > 0);
      output += inks.length
        ? `<span data-note-ink="${inks.join(' ')}" class="${inks.map(ink => `ink-${ink}`).join(' ')}">${value}</span>`
        : value;
      previous = end;
    };
    for (const offset of [...events.keys()].sort((a, b) => a - b)) {
      append(offset);
      for (const [ink, delta] of events.get(offset)) active.set(ink, (active.get(ink) ?? 0) + delta);
    }
    append(text.length);
    return output;
  }

  function editable(enabled, label, field) {
    return enabled
      ? ` contenteditable="plaintext-only" spellcheck="false" data-note-block="${field}" role="textbox" aria-label="${label}"${field === 'summary' ? ' aria-multiline="true"' : ''}`
      : '';
  }

  function stationery(isMap) {
    return `<div class="note-stationery">
      <div class="stationery-heading"><strong>文具栏</strong><span id="stationery-hint" role="status">选中文字后添加标注</span></div>
      <div class="stationery-inks" role="group" aria-label="文字标注">
        ${inkKinds.map(ink => `<button class="ink-button ink-${ink}" type="button" data-ink="${ink}"
          aria-label="${inkNames[ink]}" title="${inkNames[ink]}">${inkNames[ink]}</button>`).join('')}
        <button class="ink-button" type="button" data-ink="clear">清除标注</button>
      </div>
      <div class="stationery-actions">
        <button class="secondary" type="button" data-append-section>${isMap ? '＋ 添加分支' : '＋ 添加章节'}</button>
        <button class="secondary" type="button" data-discard-edit>取消</button>
        <button class="primary" type="button" data-save-inline>保存修改</button>
      </div>
      <p class="form-error" id="study-save-error" role="alert" hidden></p>
    </div>`;
  }

  function renderNotebook(note, { tab, editing, courseTitle, timeLink, hasVideo }) {
    const isMap = tab === 'map';
    const branches = note.sections.map((section, i) => {
      const color = Object.hasOwn(colors, section.color) ? section.color : 'default';
      const position = editing
        ? `<label class="inline-position">定位（秒）<input data-inline-time type="number" min="0" max="3600" step="1" value="${section.startSeconds ?? ''}"></label>`
        : timeLink(section.startSeconds);
      return `<section class="${isMap ? 'map-branch' : 'note-section'}${editing ? ' inline-editing' : ''}" data-inline-section="${i}" data-branch-color="${color}">
        <div class="section-heading"><h3${editable(editing, '章节标题', 'title')}>${formatText(section.title)}</h3>${position}</div>
        <ul>${section.points.map((point, j) => `<li><span${editable(editing, `要点 ${j + 1}`, 'point')}>${formatText(point)}</span>
          ${editing && section.points.length > 1 ? `<button type="button" class="text-button danger-text point-remove" data-remove-point="${j}" aria-label="删除要点 ${j + 1}">×</button>` : ''}</li>`).join('')}</ul>
        ${editing ? `<div class="inline-branch-actions">
          <button class="text-button" type="button" data-append-point>＋ 要点</button>
          ${hasVideo ? '<button class="text-button" type="button" data-use-position>使用视频当前时间</button>' : ''}
          ${isMap ? `<label>分支颜色 <select data-inline-color aria-label="分支颜色">${Object.entries(colors).map(([value, label]) =>
            `<option value="${value}"${color === value ? ' selected' : ''}>${label}</option>`).join('')}</select></label>` : ''}
          ${note.sections.length > 1 ? `<button class="text-button danger-text" type="button" data-remove-section>${isMap ? '删除分支' : '删除章节'}</button>` : ''}
        </div>` : ''}</section>`;
    }).join('');
    if (isMap) {
      return `${editing ? stationery(true) : '<p class="study-help">导图是精简提纲，独立保存。点「编辑导图」可以修改分支或用颜色标记重点。</p>'}
        <div class="study-map" aria-label="课程主题和要点">
          <div class="map-root"><small>课程主题</small><strong>${html(courseTitle)}</strong></div>
          <div class="map-branches">${branches}</div>
        </div>`;
    }
    return `${editing ? stationery(false) : ''}
      <section class="note-summary${editing ? ' inline-editing' : ''}"><h3>课程摘要</h3>
        <p${editable(editing, '课程摘要', 'summary')}>${formatText(note.summary)}</p></section>${branches}`;
  }

  function collect(element) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    const byInk = new Map();
    let text = '';
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const start = text.length;
      text += node.data;
      const span = node.parentElement.closest('[data-note-ink]');
      if (text.length === start || !span || !element.contains(span)) continue;
      for (const ink of span.dataset.noteInk.split(' ')) {
        if (!Object.hasOwn(inkNames, ink)) continue;
        if (!byInk.has(ink)) byInk.set(ink, []);
        const series = byInk.get(ink);
        const last = series.at(-1);
        if (last?.end === start) last.end = text.length;
        else series.push({ start, end: text.length, ink });
      }
    }
    return block(text, [...byInk.values()].flat());
  }

  function readDraft(panel, current) {
    const summary = panel.querySelector('[data-note-block="summary"]');
    return {
      summary: summary ? collect(summary) : current.summary,
      sections: [...panel.querySelectorAll('[data-inline-section]')].map(section => {
        const time = section.querySelector('[data-inline-time]');
        return {
          title: collect(section.querySelector('[data-note-block="title"]')),
          points: [...section.querySelectorAll('[data-note-block="point"]')].map(collect),
          startSeconds: time.validity.badInput ? NaN : time.value === '' ? null : Number(time.value),
          color: section.dataset.branchColor
        };
      })
    };
  }

  function normalized(field) {
    const from = field.text.length - field.text.trimStart().length;
    const to = field.text.trimEnd().length;
    return block(field.text.slice(from, to), field.marks
      .filter(mark => Math.max(mark.start, from) < Math.min(mark.end, to))
      .map(mark => ({ start: Math.max(mark.start, from) - from, end: Math.min(mark.end, to) - from, ink: mark.ink })));
  }

  function revision(draft, version, isMap) {
    const marks = {};
    const take = (key, value) => {
      if (value.marks.length) marks[key] = value.marks;
      return value.text;
    };
    const sections = draft.sections.map((section, i) => ({
      title: take(`${i}.title`, normalized(section.title)),
      points: section.points.map(normalized).filter(point => point.text).map((point, j) => take(`${i}.point.${j}`, point)),
      startSeconds: section.startSeconds
    }));
    const summary = take('summary', normalized(draft.summary));
    if ((!isMap && (!summary || summary.length > 60000)) || !sections.length || sections.length > 600 ||
        sections.some(section => !section.title || section.title.length > 200 || !section.points.length ||
          section.points.length > 25 || section.points.some(text => text.length > 1200) ||
          (section.startSeconds !== null && (!Number.isInteger(section.startSeconds) ||
            section.startSeconds < 0 || section.startSeconds > 3600)))) {
      throw new Error('请填写摘要、每个标题和至少一条要点。定位时间请填 0–3600 秒，可留空。');
    }
    return { version, ...(isMap ? {} : { summary }), sections,
      appearance: { marks, branchColors: draft.sections.map(section => section.color) } };
  }

  function selection(panel) {
    const current = window.getSelection();
    if (!current?.rangeCount || current.isCollapsed) return null;
    const range = current.getRangeAt(0);
    const container = node => (node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement)?.closest('[data-note-block]');
    const field = container(range.startContainer);
    if (!field || !panel.contains(field) || field !== container(range.endContainer)) return null;
    const offset = (node, count) => {
      const prefix = document.createRange();
      prefix.selectNodeContents(field);
      prefix.setEnd(node, count);
      return prefix.toString().length;
    };
    const start = offset(range.startContainer, range.startOffset);
    const end = offset(range.endContainer, range.endOffset);
    return end > start ? { field, start, end } : null;
  }

  function setSelection(element, start, end) {
    const point = position => {
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (position <= node.length) return [node, position];
        position -= node.length;
      }
      return [element, element.childNodes.length];
    };
    const range = document.createRange();
    range.setStart(...point(start));
    range.setEnd(...point(end));
    element.focus();
    const current = window.getSelection();
    current.removeAllRanges();
    current.addRange(range);
  }

  function applyInk(panel, target, ink) {
    if (!target?.field.isConnected || !panel.contains(target.field)) return false;
    const { field, start, end } = target;
    const item = collect(field);
    if (end > item.text.length) return false;
    const replacements = ink === 'clear' ? inkKinds : highlights.includes(ink) ? highlights : ['underline', 'wave'];
    const toggleOff = item.marks.some(mark => mark.ink === ink && mark.start <= start && mark.end >= end);
    const result = [];
    for (const mark of item.marks) {
      if (!replacements.includes(mark.ink) || mark.end <= start || mark.start >= end) result.push(mark);
      else {
        if (mark.start < start) result.push({ ...mark, end: start });
        if (mark.end > end) result.push({ ...mark, start: end });
      }
    }
    if (ink !== 'clear' && !toggleOff) result.push({ start, end, ink });
    field.innerHTML = formatText(block(item.text, result));
    setSelection(field, start, end);
    return true;
  }

  function insertText(field, text) {
    const current = window.getSelection();
    if (!current?.rangeCount) return;
    const range = current.getRangeAt(0);
    if (!field.contains(range.startContainer) || !field.contains(range.endContainer)) return;
    range.deleteContents();
    const node = document.createTextNode(text);
    range.insertNode(node);
    range.setStartAfter(node);
    range.collapse(true);
    current.removeAllRanges();
    current.addRange(range);
  }

  function blankSection() {
    return { title: block(), points: [block()], startSeconds: null, color: 'default' };
  }
  return { block, draftFromNote, renderNotebook, readDraft, revision, selection, applyInk, insertText, blankSection };
}
