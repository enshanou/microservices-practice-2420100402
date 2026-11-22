import { createHmac, timingSafeEqual } from 'node:crypto';
import { createServer, request as sendRequest } from 'node:http';

const secret = Buffer.from(process.env.JWT_SECRET ?? '', 'base64url');
if (secret.length < 32 || secret.toString('base64url') !== process.env.JWT_SECRET) {
  throw new Error('JWT_SECRET must contain at least 32 Base64URL-encoded random bytes.');
}

const userService = new URL(process.env.USER_SERVICE_URL);
const routes = [
  ['/api/users', userService, 'ADMIN'],
  ['/api/courses', new URL(process.env.COURSE_SERVICE_URL)],
  ['/api/tasks', new URL(process.env.TASK_SERVICE_URL)],
];
const canonicalPath = /^\/(?:[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*)?$/;
const methods = new Set(['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS']);
const hopHeaders = [
  'connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
  'proxy-connection', 'te', 'trailer', 'transfer-encoding', 'upgrade',
];

function bearerClaims(authorization) {
  const parts = /^Bearer ([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]{43})$/i.exec(authorization ?? '');
  if (!parts) return null;
  const [, header, payload, signature] = parts;
  const expected = createHmac('sha256', secret).update(`${header}.${payload}`).digest();
  const supplied = Buffer.from(signature, 'base64url');
  if (supplied.toString('base64url') !== signature || !timingSafeEqual(expected, supplied)) return null;

  try {
    const metadata = JSON.parse(Buffer.from(header, 'base64url'));
    const claims = JSON.parse(Buffer.from(payload, 'base64url'));
    const now = Math.floor(Date.now() / 1000);
    if (metadata.alg !== 'HS256' || metadata.typ !== 'JWT' ||
        claims.iss !== 'courselens-user-service' || claims.aud !== 'courselens-gateway' ||
        typeof claims.sub !== 'string' || !/^[a-z0-9_-]{1,64}$/.test(claims.sub) ||
        !['USER', 'ADMIN'].includes(claims.role) ||
        !Number.isSafeInteger(claims.iat) || claims.iat > now + 5 ||
        !Number.isSafeInteger(claims.exp) || claims.exp <= now ||
        claims.exp <= claims.iat || claims.exp - claims.iat > 900) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}

function endToEndHeaders(source) {
  const headers = { ...source };
  const namedByConnection = (headers.connection ?? '').split(',').map(name => name.trim().toLowerCase());
  for (const name of [...hopHeaders, ...namedByConnection]) delete headers[name];
  return headers;
}

function reply(response, status, body, headers = {}) {
  response.writeHead(status, { 'content-type': 'application/json', ...headers });
  response.end(JSON.stringify(body));
}

function forward(incoming, response, target, claims) {
  const headers = endToEndHeaders(incoming.headers);
  for (const name of ['host', 'authorization', 'forwarded', 'expect']) delete headers[name];
  for (const name of Object.keys(headers)) {
    const normalized = name.replaceAll('_', '-');
    if (normalized.startsWith('x-auth-') || normalized.startsWith('x-forwarded-')) delete headers[name];
  }
  if (claims) {
    headers['x-auth-user'] = claims.sub;
    headers['x-auth-role'] = claims.role;
  }

  const upstream = sendRequest({
    hostname: target.hostname,
    port: target.port || 80,
    method: incoming.method,
    path: incoming.url,
    headers,
  }, result => {
    response.writeHead(result.statusCode, endToEndHeaders(result.headers));
    result.on('error', error => response.destroy(error));
    response.on('close', () => result.destroy());
    result.pipe(response);
  });
  upstream.setTimeout(120_000, () => upstream.destroy(new Error('Upstream timeout')));
  upstream.on('error', () => {
    if (response.headersSent) response.destroy();
    else reply(response, 502, { error: 'Upstream service unavailable' });
  });
  incoming.on('error', error => upstream.destroy(error));
  response.on('close', () => upstream.destroy());
  incoming.pipe(upstream);
}

const server = createServer((incoming, response) => {
  const { method, url } = incoming;
  const path = url.split('?', 1)[0];
  if (!canonicalPath.test(path) || url.includes('#')) {
    return reply(response, 400, { error: 'Invalid request path' });
  }
  if (!methods.has(method)) return reply(response, 405, { error: 'Method not allowed' });
  if (method === 'GET' && path === '/api/health') return reply(response, 200, { status: 'UP' });
  if (method === 'POST' && path === '/api/auth/login') {
    return forward(incoming, response, userService, null);
  }

  const route = routes.find(([prefix]) => path === prefix || path.startsWith(`${prefix}/`));
  const target = method === 'GET' && path === '/api/auth/me' ? userService : route?.[1];
  if (!target) return reply(response, 404, { error: 'Route not found' });

  const claims = bearerClaims(incoming.headers.authorization);
  if (!claims) {
    return reply(response, 401, { error: 'Missing or invalid bearer token' }, { 'www-authenticate': 'Bearer' });
  }
  const requiredRole = route?.[2] ??
    (route?.[0] === '/api/courses' && method === 'DELETE' ? 'ADMIN' : null);
  if (requiredRole && claims.role !== requiredRole) {
    return reply(response, 403, { error: 'Administrator access required' });
  }
  return forward(incoming, response, target, claims);
});

server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => {
  console.log('CourseLens gateway listening');
});
