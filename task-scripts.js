(function(root){
  'use strict';
  function forKey(key,data=root.TASK_SCRIPTS_DATA){if(!data)return[];const ids=data.mapping[key]||[];return ids.map(id=>data.scripts.find(s=>s.id===id)).filter(Boolean);}
  function forId(id,data=root.TASK_SCRIPTS_DATA){return data?.scripts.filter(row=>row.id===id)||[];}
  const fixed=new Set(['【学习成果调研问卷】','【电科云】','【上周学习打卡排行榜·重磅出炉】','【重磅发布！上周学习王者是谁？】','【上周排名战报！你上榜了吗？】']);
  function pending(body){return [...new Set(body.match(/【[^【】\n]+】|\{\{[^}\n]+\}\}/g)||[])].filter(value=>!/^【第\d+周/.test(value)&&!fixed.has(value));}
  function render(row,context={}){
    let body = row.body;
    if (context.week) body = body.replaceAll('【周次】', String(context.week));
    body = body.replaceAll('本周日（【作业截止日期】）', '本周日')
      .replaceAll('【作业截止日期】', '本周日')
      .replaceAll('截止：【报名截止日期】中午12点', '截止时间以本期报名通知为准')
      .replaceAll('【本期实名认证截止日期及时间】', '以本期通知为准');
    const blankFields=pending(body);for(const field of blankFields)body=body.replaceAll(field,'');
    if (!['outcome-survey','outcome-followup'].includes(row.id)) {
    if (!['outcome-survey','outcome-followup'].includes(row.id)) {
    const links=body.match(/https?:\/\/[^\s]+/g)||[];if(links.length)blankFields.push('相关资料链接');body=body.replace(/https?:\/\/[^\s]+/g,'');
    }
    }
    return {...row,body,blankFields:[...new Set(blankFields)]};
  }
  function search(rows,query='',favorites=null){const words=query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);return rows.filter(row=>(!favorites||favorites.includes(row.id))&&words.every(word=>[row.title,row.category,row.body].join(' ').toLocaleLowerCase().replace(/\s+/g,'').includes(word)));}
  class Favorites{
    constructor(storage){this.storage=storage;this.key='camp-script-favorites-v1';this.reload();}
    reload(){const value=JSON.parse(this.storage.getItem(this.key)||'[]');if(!Array.isArray(value)||value.some(id=>typeof id!=='string'))throw Error('收藏未能读取，原数据保留。');this.ids=[...new Set(value)];return this.ids;}
    toggle(id){this.reload();const next=this.ids.includes(id)?this.ids.filter(value=>value!==id):[...this.ids,id];try{this.storage.setItem(this.key,JSON.stringify(next));}catch{throw Error('收藏未能保存，请稍后重试。');}this.ids=next;return this.ids;}
  }
  const api={forKey,forId,pending,render,search,Favorites};if(typeof module!=='undefined'&&module.exports)module.exports=api;root.TaskScripts=api;
})(typeof window!=='undefined'?window:globalThis);
