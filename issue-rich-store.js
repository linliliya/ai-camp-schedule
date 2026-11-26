(function(root){
  'use strict';
  const DB='camp-issue-rich-v1',KEY='notebook',clone=value=>structuredClone(value),now=()=>new Date().toISOString(),uid=()=>root.crypto.randomUUID();
  const fresh=repo=>({id:uid(),repo,body:'',images:[],created:now(),updated:now(),uncertain:false});
  const meaningful=d=>d.body.trim()||d.images.length;
  function validate(book){
    if(!book||book.schema!==1||!Array.isArray(book.drafts)||!Array.isArray(book.records)||typeof book.repo!=='string'||!book.drafts.some(d=>d.id===book.activeId))throw Error('图文草稿结构异常，已保留原数据。');
    const ids=new Set();
    for(const d of book.drafts){if(typeof d.id!=='string'||ids.has(d.id)||typeof d.body!=='string'||typeof d.repo!=='string'||!Array.isArray(d.images))throw Error('草稿结构异常，已停止覆盖。');ids.add(d.id);}
    return book;
  }
  function migrated(storage){
    const raw=storage.getItem('camp-issue-notebook-v2');let old;
    if(raw!==null){old=JSON.parse(raw);if(old.schema!==2||!Array.isArray(old.drafts)||!Array.isArray(old.records)||!old.drafts.some(d=>d.id===old.activeId))throw Error('旧草稿未能读取，未覆盖原数据。');}
    else{const legacy=JSON.parse(storage.getItem('camp-issue-web-v1')||'{}');if((legacy.body!==undefined&&typeof legacy.body!=='string')||(legacy.repo!==undefined&&typeof legacy.repo!=='string'))throw Error('旧草稿格式异常。');const d=fresh(legacy.repo||'');d.body=legacy.body||'';old={repo:d.repo,activeId:d.id,drafts:[d],records:[]};}
    return validate({schema:1,revision:uid(),repo:old.repo||'',activeId:old.activeId,drafts:old.drafts.map(d=>({...d,images:[],uncertain:false})),records:old.records,history:{}});
  }
  function title(body){return [...(body.split(/\r?\n/).find(line=>line.trim()&&!/^!\[.*\]\(/.test(line.trim()))||'截图草稿').replace(/^#{1,6}\s+/,'').replace(/\*\*/g,'')].slice(0,200).join('');}
  class Store{
    static open(storage=root.localStorage){return new Promise((resolve,reject)=>{
      const req=root.indexedDB.open(DB,1);req.onupgradeneeded=()=>req.result.createObjectStore('state');req.onerror=()=>reject(Error('浏览器未能打开图文草稿，请保留原文。'));req.onblocked=()=>reject(Error('请关闭其他旧版收集页面后重试。'));
      req.onsuccess=async()=>{const store=new Store(req.result,storage);try{await store.initialize();resolve(store);}catch(error){store.db.close();reject(error);}};
    });}
    constructor(db,storage){this.db=db;this.storage=storage;}
    initialize(){return new Promise((resolve,reject)=>{const tx=this.db.transaction('state','readwrite'),table=tx.objectStore('state'),read=table.get(KEY);let next;
      read.onsuccess=()=>{try{next=read.result?validate(read.result):migrated(this.storage);if(!read.result)table.put(next,KEY);}catch(error){tx.abort();reject(error);}};
      tx.oncomplete=()=>{this.state=clone(next);resolve();};tx.onabort=tx.onerror=()=>reject(Error('图文草稿未能读取，未覆盖原数据。'));
    });}
    get current(){return clone(this.state.drafts.find(d=>d.id===this.state.activeId));}
    get repo(){return this.current.repo||this.state.repo;}
    get drafts(){return clone(this.state.drafts.filter(meaningful).sort((a,b)=>b.updated.localeCompare(a.updated)));}
    async reload(){await this.initialize();return this.current;}
    change(action){return new Promise((resolve,reject)=>{
      const tx=this.db.transaction('state','readwrite'),table=tx.objectStore('state'),read=table.get(KEY);let next,cause;
      read.onsuccess=()=>{try{const disk=validate(read.result);if(disk.revision!==this.state.revision)throw Error('其他窗口已修改草稿。当前输入保留，可另存为新稿。');next=clone(disk);action(next);next.drafts=next.drafts.filter(d=>d.id===next.activeId||meaningful(d));next.revision=uid();validate(next);table.put(next,KEY);}catch(error){cause=error;tx.abort();}};
      tx.oncomplete=()=>{this.state=clone(next);resolve(this.current);};tx.onabort=tx.onerror=()=>reject(cause||Error('草稿未能保存，请保留当前输入。'));
    });}
    save(value){return this.change(book=>{const d=book.drafts.find(d=>d.id===book.activeId);if(value.id!==d.id)throw Error('当前草稿已切换，已停止覆盖。');Object.assign(d,clone(value),{updated:now()});});}
    setRepo(repo){return this.change(book=>{const d=book.drafts.find(d=>d.id===book.activeId);if(meaningful(d)&&d.repo&&d.repo.toLowerCase()!==repo.toLowerCase())throw Error('请先提交或主动清空当前草稿，再更换仓库。');d.repo=repo;book.repo=repo;});}
    newDraft(){return this.change(book=>{const d=fresh(this.repo);book.drafts.push(d);book.activeId=d.id;});}
    select(id){return this.change(book=>{if(!book.drafts.some(d=>d.id===id))throw Error('这份草稿已不存在。');book.activeId=id;});}
    clear(){return this.change(book=>{book.drafts=book.drafts.filter(d=>d.id!==book.activeId);const d=fresh(this.repo);book.drafts.push(d);book.activeId=d.id;});}
    async recover(value){if(value.uncertain)throw Error('这份稿件的提交结果尚未确认，请先核对后再另存，避免重复提交。');await this.reload();return this.change(book=>{const d={...clone(value),id:uid(),created:now(),updated:now(),uncertain:false};book.drafts.push(d);book.activeId=d.id;});}
    receipt(receipt,draftId){return this.change(book=>{
      const current=book.drafts.find(d=>d.id===book.activeId);if(current.id!==draftId)throw Error('原稿已切换，回执已返回，请在记录中核对。');
      if(receipt.repo.toLowerCase()!==current.repo.toLowerCase()||receipt.url!==`https://github.com/${receipt.repo}/issues/${receipt.number}`||!Number.isSafeInteger(receipt.number)||receipt.number<=0)throw Error('回执不匹配，已保留草稿。');
      book.records=book.records.filter(r=>!(r.state==='submitted'&&r.repo.toLowerCase()===receipt.repo.toLowerCase()&&r.number===receipt.number));book.records.push({...receipt,state:'submitted',draftId});
      book.drafts=book.drafts.filter(d=>d.id!==draftId);const d=fresh(receipt.repo);book.drafts.push(d);book.activeId=d.id;
    });}
    cache(repo,value){return this.change(book=>{book.history[repo.toLowerCase()]=clone(value);});}
    mergeDrafts(rows){return this.change(book=>{const ids=new Set(book.drafts.map(d=>d.id));if(rows.some(d=>ids.has(d.id)))throw Error('草稿已变化，请重新检查后导入。');const active=book.drafts.find(d=>d.id===book.activeId);book.drafts.push(...clone(rows));if(!meaningful(active)&&rows.length)book.activeId=rows[0].id;});}
    close(){this.db.close();}
  }
  root.IssueRichStore={Store,title,DB};if(typeof module!=='undefined'&&module.exports)module.exports={migrated,title,validate};
})(typeof window!=='undefined'?window:globalThis);
