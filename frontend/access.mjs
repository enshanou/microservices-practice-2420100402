import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const derive = promisify(scrypt);
const id = '[a-f0-9-]{36}';
const maxAge = 8 * 60 * 60;
const cookieName = 'courselens_session';
const courseRoute = new RegExp('^/api/courses/(' + id + ')(?:/transcript)?$');
const taskRoute = new RegExp('^/api/tasks/(?:notes/|video/)?(' + id
  + ')(?:/(?:cancel|status|mind-map|regenerate|retry|explain|export\\.md|export\\.txt|notion|media))?$');
const collectionRoute = new RegExp('^/api/tasks/video/collections/(' + id + ')/(?:stop|episodes/[0-9]+/retry)$');
const uploadRoute = new RegExp('^/api/tasks/video/upload/(' + id + ')$');
const listPaths = new Set(['/api/courses', '/api/courses/transcripts', '/api/tasks', '/api/tasks/notes',
  '/api/tasks/video', '/api/tasks/video/collections']);
const createPaths = new Set(['/api/courses', '/api/tasks', '/api/tasks/notes',
  '/api/tasks/video/link', '/api/tasks/video/collections']);

export class RequestError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export async function bodyBytes(req, limit) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size <= limit) chunks.push(chunk);
  }
  if (size > limit) throw new RequestError(413, '请求内容过长');
  return Buffer.concat(chunks);
}

export function bodyJson(bytes) {
  try { return JSON.parse(bytes.toString('utf8')); }
  catch { throw new RequestError(400, '请求正文必须为 JSON'); }
}

export function sendJson(res, status, value, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
  res.end(JSON.stringify(value));
}

export function createAccess(settings) {
  const accounts = new Map(Object.entries(settings.accounts ?? {}));
  const enabled = settings.requireLogin === true || accounts.size > 0;
  for (const account of accounts.values()) {
    if (!new RegExp('^' + id + '$').test(account.userId) || !/^[0-9a-f]{32}$/.test(account.salt)
        || !/^[0-9a-f]{128}$/.test(account.passwordHash)) throw new Error('Invalid workbench account configuration');
  }
  if (enabled && !accounts.size) throw new Error('Login requires at least one configured account');
  const sessions = new Map();
  let attemptWindow = 0, attempts = 0;
  const publicOrigin = settings.publicOrigin ? new URL(settings.publicOrigin) : null;
  if (publicOrigin && (publicOrigin.origin !== settings.publicOrigin ||
      !(publicOrigin.protocol === 'https:' || /^(127\.0\.0\.1|localhost)$/.test(publicOrigin.hostname)))) {
    throw new Error('Use an HTTPS publicOrigin, or an HTTP loopback address');
  }
  const secure = publicOrigin?.protocol === 'https:' ? '; Secure' : '';
  const cookie = (value, seconds) =>
    cookieName + '=' + value + '; Path=/; HttpOnly; SameSite=Strict; Max-Age=' + seconds + secure;

  function session(req) {
    const token = req.headers.cookie?.split(';').map(part => part.trim())
      .find(part => part.startsWith(cookieName + '='))?.slice(cookieName.length + 1);
    const state = sessions.get(token);
    if (!state || state.expiry <= Date.now()) return { token, account: null };
    return { token, account: state.account };
  }

  function validateOrigin(req) {
    if (!/^(?:127\.0\.0\.1|localhost|\[::1\])(?::[0-9]+)?$/.test(req.headers.host ?? '')
        && req.headers.host !== publicOrigin?.host) throw new RequestError(403, '请求地址无效');
    if (['GET', 'HEAD'].includes(req.method)) return;
    const origin = settings.publicOrigin || ('http://' + req.headers.host);
    if (['cross-site', 'same-site'].includes(req.headers['sec-fetch-site'])
        || (req.headers.origin && req.headers.origin !== origin)) {
      throw new RequestError(403, '请从工作台页面提交操作');
    }
  }

  async function route(req, res, pathname) {
    if (pathname === '/api/session' && req.method === 'GET') {
      const { account } = session(req);
      sendJson(res, 200, { enabled, userId: account?.userId ?? null, notion: !!notion(account) });
      return true;
    }
    if (pathname === '/api/session/login' && req.method === 'POST' && enabled) {
      const now = Date.now();
      if (now - attemptWindow >= 60_000) { attempts = 0; attemptWindow = now; }
      if (++attempts > 30) throw new RequestError(429, '登录尝试过多，请一分钟后再试');
      const { username, password } = bodyJson(await bodyBytes(req, 4096)) ?? {};
      const account = typeof username === 'string' ? accounts.get(username) : null;
      if (typeof password !== 'string' || password.length > 256) throw new RequestError(400, '请输入有效的密码');
      const hash = await derive(password, Buffer.from(account?.salt ?? '0'.repeat(32), 'hex'), 64);
      if (!account || !timingSafeEqual(hash, Buffer.from(account.passwordHash, 'hex'))) {
        throw new RequestError(401, '账号或密码错误');
      }
      for (const [token, state] of sessions) if (state.expiry <= now) sessions.delete(token);
      sessions.delete(session(req).token);
      const token = randomBytes(32).toString('hex');
      sessions.set(token, { account, expiry: now + maxAge * 1000 });
      sendJson(res, 200, { userId: account.userId }, { 'Set-Cookie': cookie(token, maxAge) });
      return true;
    }
    if (pathname === '/api/session/logout' && req.method === 'POST') {
      sessions.delete(session(req).token);
      sendJson(res, 200, {}, { 'Set-Cookie': cookie('', 0) });
      return true;
    }
    return false;
  }

  function notion(account) {
    const config = enabled ? account?.notion : settings.notion;
    return typeof config?.token === 'string' && config.token && /^[a-f0-9-]{32,36}$/i.test(config.pageId) ? config : null;
  }

  async function authorize(account, method, path, bytes, upstream) {
    if (!enabled) return;
    if (!account) throw new RequestError(401, '请先登录');
    const own = async courseId => {
      if (typeof courseId !== 'string' || !new RegExp('^' + id + '$').test(courseId)
          || (await upstream('/api/courses/' + courseId)).ownerId !== account.userId) {
        throw new RequestError(404, '课程或学习资料不存在');
      }
    };
    if (method === 'GET' && path === '/api/users') return [await upstream('/api/users/' + account.userId)];
    if (method === 'GET' && listPaths.has(path)) {
      const owned = new Set((await upstream('/api/courses')).filter(row => row.ownerId === account.userId).map(row => row.id));
      if (path === '/api/courses') return (await upstream(path)).filter(row => row.ownerId === account.userId);
      const rows = await upstream(path);
      if (path === '/api/tasks/notes' || path === '/api/tasks/video') {
        const tasks = new Set((await upstream('/api/tasks')).filter(row => owned.has(row.courseId)).map(row => row.id));
        return rows.filter(row => tasks.has(row.taskId));
      }
      return rows.filter(row => owned.has(row.courseId));
    }
    if (method === 'GET' && ['/api/tasks/notes/config', '/api/tasks/video/config'].includes(path)) return;
    if (method === 'POST' && path === '/api/tasks/video/preview') return;
    if (method === 'POST' && createPaths.has(path)) {
      const body = bodyJson(bytes);
      if (path !== '/api/courses') return own(body?.courseId);
      if (body?.ownerId === account.userId) return;
      throw new RequestError(403, '课程只能登记到自己的账号');
    }
    const course = path.match(courseRoute) || path.match(uploadRoute);
    if (course) return own(course[1]);
    const task = path.match(taskRoute);
    if (task) return own((await upstream('/api/tasks/' + task[1])).courseId);
    const collection = path.match(collectionRoute);
    if (collection) {
      const row = (await upstream('/api/tasks/video/collections')).find(item => item.id === collection[1]);
      return own(row?.courseId);
    }
    throw new RequestError(404, '请求地址不存在');
  }
  return { enabled, session, validateOrigin, route, authorize, notion };
}
