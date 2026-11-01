"""Standard-library HTTP checks. Run via smoke-test.sh or Verify-Week06.ps1."""
import json, sys, time
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

OUT=Path('/work/docs/homework/week-06/evidence')
OUT.mkdir(parents=True,exist_ok=True)
phase=sys.argv[1] if len(sys.argv)>1 else 'core'
log=[]
def call(service,path,method='GET',body=None,expected=200):
    url=f'http://{service}-service:8080{path}'
    req=Request(url, data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json'})
    try:
        with urlopen(req,timeout=10) as r: code,raw=r.status,r.read().decode()
    except HTTPError as e: code,raw=e.code,e.read().decode()
    line=f'{method} {url}\nrequest={json.dumps(body)}\nHTTP {code} {raw}'
    print(line,flush=True); log.append(line)
    assert code==expected,f'Expected {expected}, got {code}'
    return json.loads(raw) if raw else None
def count(service,path): return len(call(service,path))
def unchanged(service,path,before):
    after=count(service,path)
    line=f'DATA {service}: before={before}, after={after}; unchanged={before==after}'
    print(line,flush=True); log.append(line); assert before==after
def healthy():
    for service in ['user','course','task']:
        for attempt in range(90):
            try:
                with urlopen(f'http://{service}-service:8080/actuator/health',timeout=3) as r:
                    if r.status==200 and json.load(r)['status']=='UP': break
            except (URLError,TimeoutError,ConnectionError): pass
            time.sleep(2)
        else: raise RuntimeError(f'{service} not ready')
        result=call(service,'/actuator/health'); assert result['status']=='UP'

try:
    if phase=='core':
        healthy()
        u0=count('user','/api/users'); c0=count('course','/api/courses'); t0=count('task','/api/tasks')
        u=call('user','/api/users','POST',{'displayName':'2420100402 Week06'},201)
        c=call('course','/api/courses','POST',{'ownerId':u['id'],'title':'Week06 CourseLens','sourceReference':'imports/week06.srt'},201)
        t=call('task','/api/tasks','POST',{'courseId':c['id']},201)
        call('task','/api/tasks','POST',{'courseId':c['id']},409)
        call('task',f"/api/tasks/{t['id']}/status",'PATCH',{'status':'RUNNING'})
        done=call('task',f"/api/tasks/{t['id']}/status",'PATCH',{'status':'SUCCEEDED'})
        assert done['status']=='SUCCEEDED' and done['activeCourseId'] is None
        for svc,path,before in [('user','/api/users',u0),('course','/api/courses',c0),('task','/api/tasks',t0)]:
            after=count(svc,path); assert after==before+1
            line=f'DATA {svc}: before={before}, after={after}; delta=1'; print(line); log.append(line)
        missing='00000000-0000-0000-0000-000000000000'
        cb=count('course','/api/courses')
        call('course','/api/courses','POST',{'ownerId':missing,'title':'Rejected','sourceReference':'missing.srt'},404)
        unchanged('course','/api/courses',cb)
        tb=count('task','/api/tasks')
        call('task','/api/tasks','POST',{'courseId':missing},404)
        unchanged('task','/api/tasks',tb)
        (OUT/'context.json').write_text(json.dumps({'userId':u['id'],'courseId':c['id'],'taskId':t['id']}),encoding='utf-8')
    elif phase=='unavailable':
        ctx=json.loads((OUT/'context.json').read_text())
        before=count('task','/api/tasks')
        call('task','/api/tasks','POST',{'courseId':ctx['courseId']},503)
        unchanged('task','/api/tasks',before)
    elif phase=='recovered':
        healthy()
        ctx=json.loads((OUT/'context.json').read_text())
        call('user',f"/api/users/{ctx['userId']}")
        call('course',f"/api/courses/{ctx['courseId']}")
        task=call('task',f"/api/tasks/{ctx['taskId']}")
        assert task['status']=='SUCCEEDED'
    else: raise ValueError(phase)
    print(f'PASS {phase}',flush=True); log.append(f'PASS {phase}')
finally:
    (OUT/f'{phase}.txt').write_text('\n\n'.join(log)+'\n',encoding='utf-8')
