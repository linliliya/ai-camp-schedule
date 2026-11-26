(function(root) {
  'use strict';
  const KEY='camp-issue-notebook-v2', LEGACY='camp-issue-web-v1';
  const clone=v=>JSON.parse(JSON.stringify(v));
  const id=()=>root.crypto?.randomUUID?.() || Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);
  const now=()=>new Date().toISOString();
  const draft=(repo='')=>({id:id(),repo,body:'',created:now(),updated:now()});
  const title=body=>[...(body.split(/\r?\n/).find(x=>x.trim())||'未命名草稿').trim().replace(/^# /,'')].slice(0,200).join('');
  function validate(s) {
    if(s.schema!==2 || !Array.isArray(s.drafts) || !Array.isArray(s.records) || typeof s.repo!=='string' || !s.drafts.some(d=>d.id===s.activeId)) throw Error('旧草稿结构异常，已停止写入，请保留原文。');
    const ids=new Set();
    for(const d of s.drafts) {
      if(typeof d.id!=='string'||ids.has(d.id)||typeof d.body!=='string'||typeof d.repo!=='string'||typeof d.updated!=='string') throw Error('旧草稿结构异常，已停止写入。');
      ids.add(d.id);
    }
    for(const r of s.records) {
      if(typeof r.repo!=='string'||typeof r.title!=='string'||typeof r.created!=='string'||!['opened','linked'].includes(r.state)) throw Error('旧记录结构异常，已停止写入。');
      if(r.state==='linked' && (!Number.isSafeInteger(r.number)||r.number<=0||r.url!==`https://github.com/${r.repo}/issues/${r.number}`||!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(r.repo))) throw Error('旧记录链接异常，已停止写入。');
    }
    return s;
  }
  class Store {
    constructor(storage) { this.storage=storage; this.reload(); }
    reload() {
      const raw=this.storage.getItem(KEY);
      if(raw!==null) this.state=validate(JSON.parse(raw));
      else {
        const old=JSON.parse(this.storage.getItem(LEGACY)||'{}');
        if((old.body!==undefined&&typeof old.body!=='string')||(old.repo!==undefined&&typeof old.repo!=='string')) throw Error('旧稿未能读取，未覆盖原文。');
        const first=draft(old.repo||''); first.body=old.body||'';
        this.state={schema:2,revision:null,repo:old.repo||'',activeId:first.id,drafts:[first],records:[]};
      }
      return this.current;
    }
    get current() { return clone(this.state.drafts.find(d=>d.id===this.state.activeId)); }
    get repo() { return this.current.repo||this.state.repo; }
    get drafts() { return clone(this.state.drafts.filter(d=>d.body.trim()).sort((a,b)=>b.updated.localeCompare(a.updated))); }
    get records() { return clone(this.state.records.filter(r=>r.repo.toLowerCase()===this.repo.toLowerCase()).sort((a,b)=>b.created.localeCompare(a.created))); }
    change(action) {
      const disk=this.storage.getItem(KEY), revision=disk===null?null:validate(JSON.parse(disk)).revision;
      if(revision!==this.state.revision) throw Error('其他窗口已修改草稿。原文仍在输入框，请重新载入或保留为新草稿。');
      const next=clone(this.state); action(next); next.revision=id();
      next.drafts=next.drafts.filter(d=>d.id===next.activeId||d.body.trim());
      validate(next); this.storage.setItem(KEY,JSON.stringify(next)); this.state=next;
      return this.current;
    }
    saveBody(body) { return this.change(s=>{const d=s.drafts.find(x=>x.id===s.activeId);d.body=body;d.updated=now();}); }
    setRepo(repo) {
      if(!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(repo)) throw Error('请先填写有效仓库。');
      return this.change(s=>{const d=s.drafts.find(x=>x.id===s.activeId);if(d.body.trim()&&d.repo&&d.repo.toLowerCase()!==repo.toLowerCase()) throw Error('请先提交或主动清空当前草稿，再更换仓库。');s.repo=repo;d.repo=repo;});
    }
    newDraft() { return this.change(s=>{const fresh=draft(this.repo);s.drafts.push(fresh);s.activeId=fresh.id;}); }
    select(identifier) { return this.change(s=>{if(!s.drafts.some(d=>d.id===identifier)) throw Error('这份草稿已不存在。');s.activeId=identifier;}); }
    clear() { return this.change(s=>{const fresh=draft(this.repo);s.drafts=s.drafts.filter(d=>d.id!==s.activeId);s.drafts.push(fresh);s.activeId=fresh.id;}); }
    opened() {
      if(!this.current.body.trim()||!this.repo) throw Error('请先保存仓库并填写正文。');
      return this.change(s=>{
        const d=s.drafts.find(x=>x.id===s.activeId), existing=s.records.find(r=>r.draftId===d.id);
        if(existing?.state==='linked') return;
        const row={id:d.id,draftId:d.id,repo:this.repo,title:title(d.body),created:now(),state:'opened',url:'',number:0};
        s.records=s.records.filter(r=>r.draftId!==d.id);s.records.push(row);
      });
    }
    link(link) {
      const url=new URL(link), parts=url.pathname.split('/').filter(Boolean);
      if(url.protocol!=='https:'||url.hostname!=='github.com'||url.port||url.username||url.password||parts.length!==4||parts[2]!=='issues'||!/^[1-9]\d*$/.test(parts[3])||parts.slice(0,2).join('/').toLowerCase()!==this.repo.toLowerCase()) throw Error('请粘贴当前仓库的 GitHub Issue 链接。');
      if(!Number.isSafeInteger(Number(parts[3]))) throw Error('Issue 编号过长，请核对链接。');
      if(!this.current.body.trim()) throw Error('请先打开对应草稿，再关联记录。');
      return this.change(s=>{
        const d=s.drafts.find(x=>x.id===s.activeId), row={id:d.id,draftId:d.id,repo:this.repo,title:title(d.body),created:now(),state:'linked',url:`https://github.com/${this.repo}/issues/${parts[3]}`,number:Number(parts[3])};
        s.records=s.records.filter(r=>r.draftId!==d.id);s.records.push(row);
      });
    }
    recover(body,repo) {
      this.reload();
      return this.change(s=>{const fresh=draft(repo);fresh.body=body;s.drafts.push(fresh);s.activeId=fresh.id;});
    }
  }
  const api={Store,KEY,title}; if(typeof module!=='undefined'&&module.exports)module.exports=api;root.IssueNotebook=api;
})(typeof window!=='undefined'?window:globalThis);
