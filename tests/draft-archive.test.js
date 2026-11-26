const test=require('node:test'),assert=require('node:assert/strict');const {exportDrafts,importDrafts,additions}=require('../web-issue/archive.js');
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=','base64');
const draft=()=>({id:'draft-test-1234',repo:'me/inbox',body:'保留原图\n![截图](camp-image:image-test-1234)',created:'2026-10-28T00:00:00Z',updated:'2026-10-28T00:00:00Z',uncertain:true,images:[{id:'image-test-1234',name:'截图.png',mime:'image/png',blob:new Blob([png],{type:'image/png'})}]});
test('self-contained archive preserves image bytes, stable paths and unknown-result state',async()=>{
  const row=draft(),text=await exportDrafts([row]),decoded=await importDrafts(text);assert.equal(decoded[0].id,row.id);assert.equal(decoded[0].uncertain,true);assert.equal(decoded[0].repo,row.repo);assert.equal(decoded[0].body,row.body);assert.deepEqual(Buffer.from(await decoded[0].images[0].blob.arrayBuffer()),png);
  assert.equal((await additions([row],decoded)).length,0);assert.equal((await additions([],decoded)).length,1);await assert.rejects(additions([{...row,body:'另一个版本'}],decoded),/不同内容/);
});
test('corrupted images, missing attachments and untrusted formats are rejected before merging',async()=>{
  const value=JSON.parse(await exportDrafts([draft()]));value.drafts[0].images[0].sha256='broken';await assert.rejects(importDrafts(JSON.stringify(value)),/校验失败/);
  const absent=JSON.parse(await exportDrafts([draft()]));absent.drafts[0].images=[];await assert.rejects(importDrafts(JSON.stringify(absent)),/截图缺失/);
  await assert.rejects(importDrafts('{broken'));await assert.rejects(importDrafts(JSON.stringify({format:'other',version:1,drafts:[]})));
});
