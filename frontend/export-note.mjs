import { RequestError, sendJson } from './access.mjs';

const stamp = seconds => Number.isInteger(seconds) && seconds >= 0
  ? [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60].map(n => String(n).padStart(2, '0')).join(':') : '';
const escapeMarkdown = text => String(text).replace(/[\\&<>!()[\]_*~#|]/g, char => '\\' + char);
const onOneLine = text => String(text).replace(/\s+/g, ' ').trim();

export function noteText(title, content, markdown) {
  const display = markdown ? escapeMarkdown : String;
  const lines = [markdown ? '# ' + display(onOneLine(title)) : onOneLine(title), '', display(content.summary), ''];
  for (const section of content.sections) {
    const time = stamp(section.startSeconds);
    lines.push((markdown ? '## ' : '') + display(onOneLine(section.title)) + (time ? ' · ' + time : ''), '');
    for (const point of section.points) lines.push('- ' + display(onOneLine(point)));
    lines.push('');
  }
  return lines.join('\n');
}

const rich = value => (String(value).match(/[\s\S]{1,1000}/gu) ?? []).map(content => ({ type: 'text', text: { content } }));
const block = (type, value) => ({ object: 'block', type, [type]: { rich_text: rich(value) } });

export async function exportNote(req, res, upstream, taskId, action, notion, fetcher = fetch) {
  if (action === 'notion' && req.method !== 'POST' || action !== 'notion' && req.method !== 'GET') {
    throw new RequestError(405, '此操作不支持该请求方式');
  }
  if (action === 'notion' && !notion) throw new RequestError(503, '请先配置此账号的 Notion 连接');
  const [task, note] = await Promise.all([upstream('/api/tasks/' + taskId), upstream('/api/tasks/notes/' + taskId)]);
  const course = await upstream('/api/courses/' + task.courseId);
  const title = course.title;
  if (action !== 'notion') {
    const markdown = action === 'export.md';
    const extension = markdown ? 'md' : 'txt';
    const name = encodeURIComponent(onOneLine(title).replace(/[\\/:*?"<>|\u0000-\u001f]/g, '-')).replace(/['()*]/g, c =>
      '%' + c.charCodeAt(0).toString(16).toUpperCase());
    res.writeHead(200, { 'Content-Type': (markdown ? 'text/markdown' : 'text/plain') + '; charset=utf-8',
      'Content-Disposition': "attachment; filename=\"courselens." + extension + "\"; filename*=UTF-8''" + name + '.' + extension,
      'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    res.end(noteText(title, note.content, markdown));
    return;
  }
  const children = [block('paragraph', note.content.summary), ...note.content.sections.flatMap(section => {
    const time = stamp(section.startSeconds);
    return [block('heading_2', section.title + (time ? ' · ' + time : '')),
      ...section.points.map(point => block('bulleted_list_item', point))];
  })];
  const payload = JSON.stringify({ parent: { type: 'page_id', page_id: notion.pageId },
    properties: { title: { type: 'title', title: rich(title) } }, children });
  if (children.length > 100 || Buffer.byteLength(payload) > 450_000) {
    throw new RequestError(413, '这份笔记超出单次发送限制。请下载 Markdown 后在 Notion 中导入。');
  }
  let response;
  try {
    response = await fetcher('https://api.notion.com/v1/pages', {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(25_000),
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + notion.token, 'Notion-Version': '2026-03-11' },
      body: payload
    });
  } catch {
    throw new RequestError(504, '未收到 Notion 的响应。请先查看目标页下是否已生成笔记。');
  }
  if (!response.ok) throw new RequestError(502, 'Notion 返回 HTTP ' + response.status + '，请检查目标页连接及写入权限');
  const page = await response.json();
  sendJson(res, 201, { url: page.url });
}
