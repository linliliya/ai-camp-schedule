const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');
const {IDBFactory}=require('fake-indexeddb');
function scope(seed={}){const values=new Map(Object.entries(seed)),storage={getItem:key=>values.get(key)??null,setItem:(k,v)=>values.set(k,v)};const root={indexedDB:new IDBFactory(),crypto:require('node:crypto').webcrypto,localStorage:storage};root.window=root;const context=vm.createContext({...root,window:root,structuredClone,Blob,console});vm.runInContext(fs.readFileSync(require.resolve('../issue-rich-store.js'),'utf8'),context);return {Store:root.IssueRichStore.Store,storage};}
test('rich migration preserves old bytes and image blobs survive switching and reopen',async()=>{
  const old=JSON.stringify({schema:2,repo:'me/inbox',activeId:'old-draft',drafts:[{id:'old-draft',repo:'me/inbox',body:'旧稿',created:'2026-10-27',updated:'2026-10-27'}],records:[]});const {Store,storage}=scope({'camp-issue-notebook-v2':old});const a=await Store.open(storage);
  const d=a.current;d.images=[{id:'image-1',blob:new Blob(['image'])}];await a.save(d);await a.newDraft();const second=a.current;second.body='第二稿';await a.save(second);await a.select('old-draft');
  assert.equal(await a.current.images[0].blob.text(),'image');a.close();const b=await Store.open(storage);assert.equal(b.current.body,'旧稿');assert.equal(storage.getItem('camp-issue-notebook-v2'),old);b.close();
});
test('IndexedDB serializes conflicting tabs and recovery keeps both versions',async()=>{
  const {Store,storage}=scope();const a=await Store.open(storage),b=await Store.open(storage);const first=a.current;first.body='窗口一';await a.save(first);const second=b.current;second.body='窗口二';await assert.rejects(b.save(second),/其他窗口/);await b.recover(second);assert.deepEqual(b.drafts.map(d=>d.body).sort(),['窗口一','窗口二'].sort());a.close();b.close();
});
test('success clears only current draft, preserves other images, and rejects mismatched receipts',async()=>{
  const {Store,storage}=scope();const a=await Store.open(storage);await a.setRepo('me/inbox');let d=a.current;d.body='保留稿';d.images=[{id:'image-1',blob:new Blob(['photo'])}];await a.save(d);await a.newDraft();d=a.current;d.body='提交稿';await a.save(d);
  await assert.rejects(a.receipt({repo:'other/inbox',number:1,url:'https://github.com/other/inbox/issues/1'},d.id),/不匹配/);
  await a.receipt({repo:'me/inbox',number:2,url:'https://github.com/me/inbox/issues/2'},d.id);assert.equal(a.drafts.length,1);assert.equal(await a.drafts[0].images[0].blob.text(),'photo');assert.equal(a.state.records[0].number,2);a.close();
});
test('invalid old data is not overwritten or replaced with an empty draft',async()=>{
  const {Store,storage}=scope({'camp-issue-notebook-v2':'{broken'});await assert.rejects(Store.open(storage));assert.equal(storage.getItem('camp-issue-notebook-v2'),'{broken');
});
test('import merges preserve the current draft and unknown-result IDs, conflicts are atomic',async()=>{
  const {Store,storage}=scope(),a=await Store.open(storage);let d=a.current;d.body='当前稿';await a.save(d);const original=a.current.id;
  const imported={id:'imported-draft',repo:'me/inbox',body:'待核对稿',images:[],uncertain:true,created:'2026-10-28',updated:'2026-10-28'};await a.mergeDrafts([imported]);assert.equal(a.current.id,original);assert.equal(a.drafts.find(d=>d.id===imported.id).uncertain,true);
  await assert.rejects(a.mergeDrafts([{...imported,body:'冲突'}]),/变化/);assert.equal(a.current.id,original);assert.equal(a.drafts.find(d=>d.id===imported.id).body,'待核对稿');a.close();
});
test('failed IndexedDB writes neither change active draft nor claim durable success',async()=>{
  const {Store,storage}=scope();const a=await Store.open(storage);const d=a.current;d.body='原稿';await a.save(d);const oldId=a.current.id,transaction=a.db.transaction.bind(a.db);
  a.db.transaction=(...args)=>{const tx=transaction(...args),objectStore=tx.objectStore.bind(tx);tx.objectStore=(...names)=>{const table=objectStore(...names);table.put=()=>{throw Error('quota');};return table;};return tx;};
  const edited=a.current;edited.body='必须保留的输入';await assert.rejects(a.save(edited));await assert.rejects(a.newDraft());assert.equal(a.current.id,oldId);assert.equal(a.current.body,'原稿');assert.equal(edited.body,'必须保留的输入');a.close();
});
