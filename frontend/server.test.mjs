import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from './server.mjs';

test('same-origin proxy serves the app and preserves real API semantics', async t => {
  const seen = [];
  const upstream = http.createServer((req,res) => {
    if (req.url.endsWith('/media')) {
      assert.equal(req.headers.range, 'bytes=4-7');
      res.writeHead(206, { 'Content-Type': 'video/mp4', 'Accept-Ranges': 'bytes',
        'Content-Range': 'bytes 4-7/10', 'Content-Length': 4 });
      res.end('play');
      return;
    }
    const chunks=[];
    req.on('data',chunk=>chunks.push(chunk));
    req.on('end',()=>{
      seen.push({method:req.method,url:req.url,body:Buffer.concat(chunks).toString()});
      res.setHeader('Content-Type','application/json');
      const status=req.url.includes('conflict') ? 409 : req.url.includes('missing') ? 404 : req.method==='POST'?201:200;
      res.writeHead(status);
      res.end(JSON.stringify(req.url==='/actuator/health'?{status:'UP'}:status===409?{error:'Duplicate or conflicting data'}:{id:'real-response',title:'课程'}));
    });
  });
  await new Promise(resolve=>upstream.listen(0,'127.0.0.1',resolve));
  const url=`http://127.0.0.1:${upstream.address().port}`;
  const proxy=createApp({users:url,courses:url,tasks:url});
  await new Promise(resolve=>proxy.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${proxy.address().port}`;
  t.after(()=>{proxy.closeAllConnections();proxy.close();upstream.closeAllConnections();upstream.close();});
  await t.test('static entry and content security policy',async()=>{
    const res=await fetch(base);assert.equal(res.status,200);assert.match(await res.text(),/课览 CourseLens/);
    assert.match(res.headers.get('content-security-policy'),/connect-src 'self'/);
    assert.equal((await fetch(base+'/.env')).status,404);
    assert.equal((await fetch(base+'/server.mjs')).status,404);
  });
  await t.test('POST forwards UTF-8 body and preserves 201',async()=>{
    const body=JSON.stringify({ownerId:'u',title:'微服务课程',sourceReference:'imports/课程.srt'});
    const res=await fetch(base+'/api/courses',{method:'POST',body});assert.equal(res.status,201);
    assert.deepEqual(seen.at(-1),{method:'POST',url:'/api/courses',body});
    assert.equal((await res.json()).id,'real-response');
  });
  await t.test('404 and duplicate 409 are preserved',async()=>{
    assert.equal((await fetch(base+'/api/courses/missing')).status,404);
    const res=await fetch(base+'/api/tasks/conflict',{method:'POST',body:'{}'});
    assert.equal(res.status,409);assert.equal((await res.json()).error,'Duplicate or conflicting data');
  });
  await t.test('video playback forwards the byte range', async () => {
    const response = await fetch(base + '/api/tasks/video/11111111-1111-1111-1111-111111111111/media',
      { headers: { Range: 'bytes=4-7' } });
    assert.equal(response.status, 206);
    assert.equal(response.headers.get('content-type'), 'video/mp4');
    assert.equal(response.headers.get('content-range'), 'bytes 4-7/10');
    assert.equal(await response.text(), 'play');
  });
  await t.test('health route reaches the existing actuator',async()=>{
    const res=await fetch(base+'/api/health/tasks');assert.equal((await res.json()).status,'UP');assert.equal(seen.at(-1).url,'/actuator/health');
  });
  await t.test('unsupported methods and oversized request rejected',async()=>{
    assert.equal((await fetch(base+'/api/users',{method:'DELETE'})).status,405);
    assert.equal((await fetch(base+'/api/users',{method:'POST',body:'x'.repeat(17000)})).status,413);
  });
  await t.test('upstream offline is an explicit 503, not fake success',async()=>{
    upstream.closeAllConnections();await new Promise(resolve=>upstream.close(resolve));
    const res=await fetch(base+'/api/tasks');assert.equal(res.status,503);assert.match((await res.json()).error,/无法连接/);
  });
});

test('authenticated users can only see, edit, stream and export their own material', async t => {
  const { scryptSync } = await import('node:crypto');
  const own = '11111111-1111-1111-1111-111111111111';
  const other = '22222222-2222-2222-2222-222222222222';
  const item = '33333333-3333-3333-3333-333333333333';
  const privateItem = '44444444-4444-4444-4444-444444444444';
  const salt = 'a'.repeat(32);
  const password = 'fixture-password';
  const courses = [{ id: own, ownerId: own, title: '微服务' }, { id: other, ownerId: other, title: '其他资料' }];
  const tasks = [{ id: item, courseId: own }, { id: privateItem, courseId: other }];
  const note = { content: { summary: '已保存的摘要', sections: [{ title: '服务发现', points: ['示例'], startSeconds: 62 }] } };
  const upstream = http.createServer((req, res) => {
    const pathname = req.url;
    const data = pathname === '/api/courses' ? courses : pathname === '/api/tasks' ? tasks :
      pathname === '/api/tasks/notes' ? tasks.map(task => ({ taskId: task.id })) :
      pathname.startsWith('/api/courses/') ? courses.find(course => pathname.endsWith(course.id)) :
      pathname === '/api/tasks/notes/' + item ? note : tasks.find(task => pathname.endsWith(task.id));
    res.writeHead(data ? 200 : 404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(data ?? {}));
  });
  await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve));
  const url = 'http://127.0.0.1:' + upstream.address().port;
  const proxy = createApp({ users: url, courses: url, tasks: url }, {
    accounts: { alice: { userId: own, salt, passwordHash: scryptSync(password, Buffer.from(salt, 'hex'), 64).toString('hex') } }
  });
  await new Promise(resolve => proxy.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + proxy.address().port;
  t.after(() => { proxy.closeAllConnections(); proxy.close(); upstream.closeAllConnections(); upstream.close(); });
  assert.equal((await fetch(base + '/api/tasks')).status, 401);
  const login = await fetch(base + '/api/session/login', { method: 'POST', body: JSON.stringify({ username: 'alice', password }) });
  assert.equal(login.status, 200);
  const session = login.headers.get('set-cookie');
  assert.match(session, /HttpOnly; SameSite=Strict/);
  const cookie = session.split(';')[0];
  const ask = (pathname, init = {}) => fetch(base + pathname, { ...init, headers: { cookie, ...init.headers } });
  assert.deepEqual(await (await ask('/api/courses')).json(), courses.slice(0, 1));
  assert.deepEqual(await (await ask('/api/tasks/notes')).json(), [{ taskId: item }]);
  assert.equal((await ask('/api/tasks/notes/' + privateItem, { method: 'PATCH', body: '{}' })).status, 404);
  assert.equal((await ask('/api/tasks/video/' + privateItem + '/media')).status, 404);
  assert.equal((await ask('/api/tasks/video/' + privateItem + '/media', { method: 'HEAD' })).status, 404);
  assert.equal((await ask('/api/tasks/video/claim', { method: 'POST' })).status, 404);
  assert.equal((await ask('/api/tasks/video/upload/' + other, { method: 'POST', body: new FormData() })).status, 404);
  assert.equal((await ask('/api/courses', { method: 'POST', body: JSON.stringify({ ownerId: other }) })).status, 403);
  assert.equal((await ask('/api/courses', { method: 'POST', body: '{}', headers: { origin: 'https://example.invalid' } })).status, 403);
  assert.equal((await ask('/api/tasks/notes/' + privateItem + '/export.md')).status, 404);
  const exported = await ask('/api/tasks/notes/' + item + '/export.md');
  assert.equal(exported.status, 200);
  assert.match(exported.headers.get('content-disposition'), /attachment/);
  assert.match(await exported.text(), /# 微服务[\s\S]*已保存的摘要[\s\S]*## 服务发现 · 00:01:02[\s\S]*- 示例/);
  assert.equal((await ask('/api/session/logout', { method: 'POST' })).status, 200);
  assert.equal((await ask('/api/courses')).status, 401);
});

test('Notion creates a page from the saved note using server-side credentials', async () => {
  const { exportNote } = await import('./export-note.mjs');
  let sent;
  const result = { writeHead(code) { this.code = code; }, end(body) { this.body = JSON.parse(body); } };
  const pageId = '11111111111111111111111111111111';
  await exportNote({ method: 'POST' }, result, async path => {
    if (path.startsWith('/api/tasks/notes/')) return { content: { summary: '已保存', sections: [] } };
    if (path.startsWith('/api/tasks/')) return { courseId: 'fixture' };
    return { title: '课览' };
  }, 'fixture', 'notion', { pageId, token: 'fake-token' }, async (url, init) => {
    sent = JSON.parse(init.body);
    assert.equal(url, 'https://api.notion.com/v1/pages');
    assert.equal(init.headers.Authorization, 'Bearer fake-token');
    return new Response(JSON.stringify({ url: 'https://www.notion.so/fixture' }), { status: 200 });
  });
  assert.equal(result.code, 201);
  assert.equal(sent.parent.page_id, pageId);
  assert.equal(sent.children[0].paragraph.rich_text[0].text.content, '已保存');
  assert.equal(result.body.url, 'https://www.notion.so/fixture');
});
