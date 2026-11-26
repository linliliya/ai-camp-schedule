const test = require('node:test');
const assert = require('node:assert/strict');
const { Store, KEY } = require('../issue-notebook.js');
function storage(seed = {}) { const data = new Map(Object.entries(seed)); return { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,v) }; }
test('legacy note migrates without changing the old bytes and survives draft switches', () => {
  const old = JSON.stringify({repo:'me/inbox',body:'旧话术'}), disk = storage({'camp-issue-web-v1':old});
  const a = new Store(disk); const first = a.current.id;
  a.saveBody('更新后的话术'); a.newDraft(); a.saveBody('第二条'); a.select(first);
  assert.equal(a.current.body,'更新后的话术'); assert.equal(a.drafts.length,2);
  assert.equal(disk.getItem('camp-issue-web-v1'),old);
  assert.equal(new Store(disk).current.id,first);
});
test('failed durable save cannot switch or remove the current draft', () => {
  const disk=storage(), a=new Store(disk); a.saveBody('必须保留'); const before=disk.getItem(KEY);
  disk.setItem=()=>{throw Error('full');}; assert.throws(()=>a.newDraft());
  assert.equal(a.current.body,'必须保留'); assert.equal(a.drafts.length,1); assert.equal(disk.getItem(KEY),before);
});
test('another tab cannot silently overwrite a newer revision and can recover separately', () => {
  const disk=storage(), a=new Store(disk); a.saveBody('原文'); const b=new Store(disk);
  a.saveBody('窗口一修改'); assert.throws(()=>b.saveBody('窗口二修改'),/其他窗口/);
  b.recover('窗口二修改',''); const c=new Store(disk);
  assert.deepEqual(c.drafts.map(d=>d.body).sort(),['窗口一修改','窗口二修改'].sort());
});
test('opening GitHub is pending, linking validates repository and does not delete the draft', () => {
  const a=new Store(storage()); a.setRepo('me/inbox'); a.saveBody('标题\n正文'); a.opened();
  assert.equal(a.records[0].state,'opened'); assert.throws(()=>a.link('https://github.com/other/inbox/issues/3'));
  assert.throws(()=>a.link('https://github.com/me/inbox/pull/3')); assert.throws(()=>a.link('https://evil.example/me/inbox/issues/3'));
  a.link('https://github.com/me/inbox/issues/3#issuecomment-123');
  assert.equal(a.records[0].state,'linked'); assert.equal(a.records[0].url,'https://github.com/me/inbox/issues/3');
  assert.equal(a.current.body,'标题\n正文');
});
test('corrupt existing data is never replaced by a fresh empty notebook', () => {
  const disk=storage({[KEY]:'{broken'}); assert.throws(()=>new Store(disk)); assert.equal(disk.getItem(KEY),'{broken');
});
test('switching repositories on a new draft never retargets a saved draft',()=>{
  const a=new Store(storage());a.setRepo('me/first');a.saveBody('第一仓库');const first=a.current.id;
  a.newDraft();a.setRepo('me/second');a.saveBody('第二仓库');a.select(first);
  assert.equal(a.repo,'me/first');assert.equal(a.current.body,'第一仓库');assert.throws(()=>a.setRepo('me/second'));
});
