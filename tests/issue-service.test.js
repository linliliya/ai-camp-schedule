const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');
const {GitHubClient,Journal,Problem,validate}=require('../scripts/github-issue.cjs');
const {createService}=require('../scripts/issue-service.cjs');
const png='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=';
function setup(t,overrides={}){
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'camp-issue-test-'));t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const calls=[],issues=[],uploads=new Map();
  const request=async(method,endpoint,body)=>{
    calls.push({method,endpoint,body});const special=await overrides.request?.(method,endpoint,body);if(special!==undefined)return special;
    if(endpoint==='/user')return {id:1,login:'me'};
    if(endpoint==='/repos/me/inbox')return {full_name:'me/inbox',owner:{login:'me'},private:true,has_issues:true,default_branch:'main'};
    if(endpoint.includes('/contents/')){if(method==='GET'){if(uploads.has(endpoint))return uploads.get(endpoint);throw new Problem('missing',404);}const bytes=Buffer.from(body.content,'base64');uploads.set(endpoint,{sha:require('node:crypto').createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex')});return {};}
    if(method==='POST'){if(overrides.failCreate)throw Error('network timeout');const issue={number:issues.length+1,html_url:`https://github.com/me/inbox/issues/${issues.length+1}`,title:body.title,body:body.body,state:'open',created_at:'2026-10-28T00:00:00Z'};issues.unshift(issue);return issue;}
    if(endpoint.includes('/issues?'))return issues;
    throw Error('Unexpected request '+endpoint);
  };
  return {client:new GitHubClient({request,journal:new Journal(directory)}),calls,issues,uploads};
}
const draft=()=>({id:'draft-test-1234',repo:'me/inbox',body:'课程截图\n\n![截图](camp-image:image-test-1234)',images:[{id:'image-test-1234',mime:'image/png',data:png}]});
test('upload preserves inline image position and concurrent retries create one issue',async t=>{
  const {client,calls}=setup(t);const [a,b]=await Promise.all([client.submit(draft()),client.submit(draft())]);
  assert.equal(a.receipt.number,b.receipt.number);assert.equal(calls.filter(c=>c.method==='POST').length,1);
  const body=calls.find(c=>c.method==='POST').body.body;assert.match(body,/\.\.\/blob\/main\/attachments\/draft-test-1234\/image-test-1234.png\?raw=true/);assert.match(body,/<!-- camp-issue:draft-test-1234 -->/);
});
test('unknown create result requires explicit reconciliation before retry',async t=>{
  const options={failCreate:true}, {client,calls}=setup(t,options);await assert.rejects(client.submit(draft()),e=>e.uncertain===true);
  await assert.rejects(client.submit(draft()),e=>e.uncertain===true&&/核对/.test(e.message));
  assert.equal(calls.filter(c=>c.method==='POST').length,1);const result=await client.reconcile('me/inbox','draft-test-1234');assert.equal(result.canRetry,true);
  options.failCreate=false;await client.submit(draft());assert.equal(calls.filter(c=>c.method==='POST').length,2);assert.equal(calls.filter(c=>c.method==='PUT').length,1,'original attachment is checked by Git blob SHA, not uploaded twice');
});
test('only personal private issue repositories and valid original images are accepted',async t=>{
  const {client}=setup(t,{request:async(method,endpoint)=>endpoint==='/repos/me/inbox'?{owner:{login:'someone'},private:true,has_issues:true}:undefined});
  await assert.rejects(client.repository('me/inbox'),/本人/);assert.throws(()=>validate({...draft(),repo:'me/../else'}));
  assert.throws(()=>validate({...draft(),images:[{id:'image-test-1234',mime:'image/png',data:Buffer.from('<svg onload=alert(1)>').toString('base64')}]}),/有效/);
  assert.throws(()=>validate({...draft(),images:[]}),/图片缺失/);
});
test('large screenshots validate without nested-regex failure and changed successful drafts cannot be silently cleared',async t=>{
  const large=Buffer.alloc(2*1024*1024);Buffer.from([137,80,78,71,13,10,26,10]).copy(large);const input=draft();input.images[0].data=large.toString('base64');assert.equal(validate(input).images[0].bytes.length,large.length);
  const {client}=setup(t);await client.submit(draft());await assert.rejects(client.submit({...draft(),body:'改后的内容\n\n![图](camp-image:image-test-1234)'}),e=>e.uncertain&&/已修改/.test(e.message));
});
test('history excludes PRs and pagination uses raw count',async t=>{
  const {client}=setup(t,{request:async(method,endpoint)=>endpoint.includes('per_page=20')?Array.from({length:20},(_,i)=>({number:i+1,html_url:`https://github.com/me/inbox/issues/${i+1}`,pull_request:{}})):undefined});
  const page=await client.history('me/inbox');assert.deepEqual(page.entries,[]);assert.equal(page.nextPage,2);
});
test('local service blocks foreign origins, missing CSRF, traversal and private files',async t=>{
  const {client}=setup(t),server=createService({client,port:0});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const base=`http://127.0.0.1:${server.address().port}`;
  const session=await (await fetch(base+'/api/issue/session')).json();assert.equal(session.login,'me');assert.match(session.csrf,/^[a-f0-9]{64}$/);
  assert.equal((await fetch(base+'/api/issue/history?repo=me/inbox')).status,403);
  assert.equal((await fetch(base+'/api/issue/session',{headers:{Origin:'https://evil.example'}})).status,403);
  assert.equal((await fetch(base+'/.git/config')).status,404);assert.equal((await fetch(base+'/scripts/github-issue.cjs')).status,404);
  assert.equal((await fetch(base+'/api/issue/history?repo=me/inbox',{headers:{'X-Camp-CSRF':session.csrf}})).status,200);
  assert.equal((await fetch(base+'/api/issue/submit',{method:'POST',headers:{'X-Camp-CSRF':session.csrf,'Content-Type':'text/plain'},body:'anything'})).status,415);
});
test('only the explicitly trusted old preview origin receives CORS access',async t=>{
  const {client}=setup(t),server=createService({client,port:0,siteOrigins:['http://127.0.0.1:8899']});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const base=`http://127.0.0.1:${server.address().port}`;
  const good=await fetch(base+'/api/issue/session',{headers:{Origin:'http://127.0.0.1:8899'}});assert.equal(good.status,200);assert.equal(good.headers.get('access-control-allow-origin'),'http://127.0.0.1:8899');
  const options=await fetch(base+'/api/issue/submit',{method:'OPTIONS',headers:{Origin:'http://127.0.0.1:8899','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'content-type, x-camp-csrf'}});assert.equal(options.status,204);
  const bad=await fetch(base+'/api/issue/session',{headers:{Origin:'http://127.0.0.1:9999'}});assert.equal(bad.status,403);assert.equal(bad.headers.get('access-control-allow-origin'),null);
});
