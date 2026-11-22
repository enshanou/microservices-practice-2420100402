// 本机网页入口：提供静态文件，把浏览器的同源请求转发给原有三个服务。
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createAccess, RequestError, bodyBytes, bodyJson, sendJson } from './access.mjs';
import { exportNote } from './export-note.mjs';

const root = path.dirname(fileURLToPath(import.meta.url));
const files = { '/': ['index.html', 'text/html'], '/index.html': ['index.html', 'text/html'],
  '/app.js': ['app.js', 'text/javascript'], '/study.js': ['study.js', 'text/javascript'], '/notebook.js': ['notebook.js', 'text/javascript'], '/link-import.js': ['link-import.js', 'text/javascript'], '/generation.js': ['generation.js', 'text/javascript'], '/styles.css': ['styles.css', 'text/css'] };
const json = (res, status, error) => {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify({ error }));
};
export function createApp(backends = {
  users: process.env.WORKBENCH_USER_URL || 'http://127.0.0.1:18081',
  courses: process.env.WORKBENCH_COURSE_URL || 'http://127.0.0.1:18082',
  tasks: process.env.WORKBENCH_TASK_URL || 'http://127.0.0.1:18083'
}, settings = {}) {
  const access = createAccess(settings);
  const handle = async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (files[url.pathname] && ['GET', 'HEAD'].includes(req.method)) {
      try {
        const [name, type] = files[url.pathname];
        const body = await readFile(path.join(root, 'public', name));
        res.writeHead(200, { 'Content-Type': `${type}; charset=utf-8`, 'Cache-Control': 'no-cache',
          'X-Content-Type-Options': 'nosniff',
          'Content-Security-Policy': "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'" });
        res.end(req.method === 'HEAD' ? undefined : body);
      } catch { json(res, 500, '页面资源读取失败'); }
      return;
    }
    access.validateOrigin(req);
    if (await access.route(req, res, url.pathname)) return;
    const { account } = access.session(req);
    const api = url.pathname.match(/^\/api\/(users|courses|tasks)(?:\/|$)/);
    const health = url.pathname.match(/^\/api\/health\/(users|courses|tasks)$/);
    if (!api && !health) return json(res, 404, '请求地址不存在');
    const media = /^\/api\/tasks\/video\/[a-f0-9-]{36}\/media$/.test(url.pathname);
    const deleteCourse = req.method === 'DELETE' && /^\/api\/courses\/[a-f0-9-]{36}$/.test(url.pathname);
    const methodAllowed = ['GET', 'POST', 'PATCH'].includes(req.method) || deleteCourse || (media && req.method === 'HEAD');
    if (!methodAllowed || (health && req.method !== 'GET'))
      return json(res, 405, '此操作不支持该请求方式');
    if (api && access.enabled && !account) return json(res, 401, '请先登录');
    if (api && access.enabled && url.search) return json(res, 400, '请求地址不能包含查询参数');
    if (/^\/api\/tasks\/video\/(?:claim|[^/]+\/(?:file|source-file|heartbeat|transcript|failure))$/.test(url.pathname)) {
      return json(res, 404, '请求地址不存在');
    }
    const upstreamJson = async pathname => {
      const key = pathname.split('/')[2];
      let response;
      try { response = await fetch(new URL(pathname, backends[key]), { redirect: 'error', signal: AbortSignal.timeout(5000) }); }
      catch { throw new RequestError(503, '业务服务暂时无法连接'); }
      if (!response.ok) throw new RequestError(response.status === 404 ? 404 : 503, '无法读取关联资料');
      return response.json();
    };
    const catalog = req.method === 'POST' && ['/api/tasks/video/preview', '/api/tasks/video/collections'].includes(url.pathname);
    const exportMatch = url.pathname.match(/^\/api\/tasks\/notes\/([a-f0-9-]{36})\/(export\.md|export\.txt|notion)$/);
    const key = (api || health)[1];
    const target = new URL(health ? '/actuator/health' : url.pathname + url.search, backends[key]);
    const subtitle = req.method === 'POST' && /^\/api\/courses\/[^/]+\/transcript$/.test(url.pathname);
    const noteEdit = req.method === 'PATCH' && /^\/api\/tasks\/notes\/[a-f0-9-]{36}(?:\/mind-map)?$/.test(url.pathname);
    const bodyLimit = noteEdit ? 2 * 1024 * 1024 : subtitle ? 400000 : 16384;
    const forward = (headers, body) => {
      const upstream = http.request(target, { method: req.method,
        headers: { 'Accept': 'application/json', ...headers } }, response => {
        const outgoing = { 'Content-Type': response.headers['content-type'] || 'application/json', 'Cache-Control': 'no-store' };
        if (media) {
          for (const name of ['accept-ranges', 'content-range', 'content-length']) {
            if (response.headers[name] !== undefined) outgoing[name] = response.headers[name];
          }
        }
        res.writeHead(response.statusCode, outgoing);
        response.on('error', () => res.destroy());
        response.pipe(res);
      });
      upstream.setTimeout(body === null ? 120000 : catalog ? 28000 : url.pathname.endsWith('/explain') ? 95000 : 6000, () => upstream.destroy(new Error('timeout')));
      upstream.on('error', error => {
        if (!res.headersSent) json(res, error.message === 'timeout' ? 504 : 503,
          error.message === 'timeout' ? '服务响应超时，请稍后重试' : '业务服务暂时无法连接，请确认服务已启动');
        else res.destroy();
      });
      res.on('close', () => { if (!res.writableEnded) upstream.destroy(); });
      req.on('aborted', () => upstream.destroy());
      if (body === null) req.pipe(upstream);
      else upstream.end(body);
    };
    if (media && ['GET', 'HEAD'].includes(req.method)) {
      await access.authorize(account, req.method, url.pathname, null, upstreamJson);
      forward(req.headers.range ? { Range: req.headers.range } : {}, Buffer.alloc(0));
      return;
    }
    if (req.method === 'POST' && (url.pathname === '/api/tasks/video' ||
        /^\/api\/tasks\/video\/upload\/[a-f0-9-]{36}$/.test(url.pathname))) {
      await access.authorize(account, req.method, url.pathname, null, upstreamJson);
      const length = Number(req.headers['content-length']);
      const type = req.headers['content-type'] || '';
      if (!type.startsWith('multipart/form-data;')) return json(res, 400, '请选择视频文件');
      if (!Number.isSafeInteger(length) || length <= 0 || length > 1025 * 1024 * 1024)
        return json(res, 413, '视频上传超过大小限制');
      forward({ 'Content-Type': type, 'Content-Length': length }, null);
      return;
    }
    let body = await bodyBytes(req, bodyLimit);
    const scoped = health ? undefined : await access.authorize(account, req.method, url.pathname, body, upstreamJson);
    if (scoped !== undefined) return sendJson(res, 200, scoped);
    if (exportMatch) return exportNote(req, res, upstreamJson, exportMatch[1], exportMatch[2], access.notion(account));
    if (access.enabled && body.length) body = Buffer.from(JSON.stringify(bodyJson(body)));
    forward(body.length ? { 'Content-Type': 'application/json', 'Content-Length': body.length } : {}, body);
  };
  return http.createServer({ requestTimeout: 600000 }, (req, res) => {
    handle(req, res).catch(error => {
      if (res.headersSent) res.destroy();
      else json(res, error.status ?? 500, error.status ? error.message : '工作台暂时无法处理请求');
    });
  });
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 18090);
  const settingsFile = process.env.WORKBENCH_SETTINGS_FILE || path.join(root, '.workbench-settings.json');
  const contents = await readFile(settingsFile, 'utf8').catch(error => {
    if (error.code === 'ENOENT' && !process.env.WORKBENCH_SETTINGS_FILE) return '{}';
    throw error;
  });
  const settings = JSON.parse(contents.replace(/^\uFEFF/, ''));
  const host = process.env.WORKBENCH_BIND || '127.0.0.1';
  if (host !== '127.0.0.1' && (!settings.requireLogin || !settings.publicOrigin)) {
    throw new Error('Non-loopback deployment requires login and publicOrigin');
  }
  const server = createApp(undefined, settings);
  server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? `端口 ${port} 已被占用` : error.message); process.exitCode = 1; });
  server.listen(port, host, () => console.log(`CourseLens 工作台：http://127.0.0.1:${port}/`));
}
