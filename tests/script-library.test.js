const test=require('node:test'),assert=require('node:assert/strict');const {search,Favorites}=require('../task-scripts.js');
test('script search matches category/body and favorite IDs are durable without storing dated bodies',()=>{
  const data=new Map(),storage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};let favorites=new Favorites(storage);favorites.toggle('exam-morning');favorites=new Favorites(storage);
  const rows=[{id:'exam-morning',title:'上午场',category:'考试通知',body:'人工核对场次'},{id:'homework',title:'周末提醒',category:'作业',body:'截止'}];
  assert.equal(search(rows,'考试 核对',favorites.ids)[0].id,'exam-morning');assert.equal(search(rows,'作业').length,1);assert.equal(search(rows,'无匹配').length,0);assert.equal(data.get(favorites.key),'["exam-morning"]');
  assert.equal(search([{id:'course-w12',title:'第 12 周课程更新',category:'课程',body:''}],'第12周').length,1);
  storage.setItem=()=>{throw Error('full');};assert.throws(()=>favorites.toggle('homework'));assert.deepEqual(favorites.ids,['exam-morning']);
});
