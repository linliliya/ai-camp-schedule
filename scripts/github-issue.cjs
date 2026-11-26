'use strict';
const {execFile}=require('node:child_process');
const {createHash}=require('node:crypto');
const fs=require('node:fs');
const path=require('node:path');
class Problem extends Error { constructor(message,status=400,uncertain=false){super(message);this.status=status;this.uncertain=uncertain;} }
const validId=value=>typeof value==='string'&&/^[A-Za-z0-9-]{8,100}$/.test(value);
function repoName(value){if(typeof value!=='string'||!(/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/).test(value)||['.','..'].includes(value.split('/')[1]))throw new Problem('请填写有效的账号/仓库名。');return value;}
function imageExtension(mime,bytes){
  if(mime==='image/png'&&bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))return 'png';
  if(mime==='image/jpeg'&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'jpg';
  if(mime==='image/gif'&&/^GIF8[79]a$/.test(bytes.subarray(0,6).toString()))return 'gif';
  if(mime==='image/webp'&&bytes.subarray(0,4).toString()==='RIFF'&&bytes.subarray(8,12).toString()==='WEBP')return 'webp';
  throw new Problem('请使用有效的 PNG、JPG、WebP 或 GIF 图片。');
}
function validate(input){
  if(!input||!validId(input.id)||typeof input.body!=='string'||input.body.length>55000||!Array.isArray(input.images)||input.images.length>9)throw new Problem('稿件格式或大小不符合要求，请保留草稿后检查。');
  const repo=repoName(input.repo),ids=new Set();
  const images=input.images.map(image=>{
    if(!image||!validId(image.id)||ids.has(image.id)||typeof image.data!=='string'||image.data.length>13333336||image.data.length%4!==0||!/^[A-Za-z0-9+/]*={0,2}$/.test(image.data))throw new Problem('图片格式、标识或大小不符合要求。');
    ids.add(image.id);const bytes=Buffer.from(image.data,'base64');
    if(bytes.length<1||bytes.length>10000000)throw new Problem('每张原图不能超过 10 MB。');
    return {...image,bytes,extension:imageExtension(image.mime,bytes)};
  });
  for(const match of input.body.matchAll(/camp-image:([A-Za-z0-9-]+)/g))if(!ids.has(match[1]))throw new Problem('草稿有图片缺失，请补齐后提交。');
  for(const image of images)if(!input.body.includes(`camp-image:${image.id}`))throw new Problem('存在未插入正文的图片，请重新打开草稿。');
  const title=[...(input.body.split(/\r?\n/).find(line=>line.trim()&&!/^!\[.*\]\(/.test(line.trim()))||'').trim().replace(/^#{1,6}\s+/,'').replace(/\*\*/g,'')].slice(0,200).join('');
  if(!title)throw new Problem('请为截图补一句文字说明。');
  return {id:input.id,repo,body:input.body,images,title};
}
function receipt(issue,repo){
  if(!issue||!Number.isSafeInteger(issue.number)||issue.number<=0||issue.html_url!==`https://github.com/${repo}/issues/${issue.number}`)throw new Problem('GitHub 回执未能确认，请先核对结果。',502,true);
  return {number:issue.number,url:issue.html_url,title:issue.title||'',created:issue.created_at||new Date().toISOString(),repo,state:issue.state||'open'};
}
function ghRequest(method,endpoint,payload){
  return new Promise((resolve,reject)=>{
    const args=['api',endpoint,'--method',method,'--hostname','github.com','-H','Accept: application/vnd.github+json'];
    if(payload!==undefined)args.push('--input','-');
    const child=execFile('gh',args,{windowsHide:true,timeout:120000,maxBuffer:24*1024*1024,env:{...process.env,GH_PROMPT_DISABLED:'1',GIT_TERMINAL_PROMPT:'0'}},(error,stdout,stderr)=>{
      if(error){const status=Number(stderr?.match(/HTTP (\d{3})/)?.[1])||502;reject(new Problem(status===401?'电脑 GitHub 登录已过期，请在终端重新登录。':status===404?'GitHub 中没有找到目标内容，请检查仓库权限。':status===403?'GitHub 暂时拒绝访问，请检查权限或稍后重试。':'GitHub 请求未完成，草稿已保留，请稍后重试。',status,method==='POST'));return;}
      try{resolve(stdout.trim()?JSON.parse(stdout):null);}catch{reject(new Problem('GitHub 返回格式异常，请先核对提交结果。',502,method==='POST'));}
    });
    child.stdin.on('error',()=>{});child.stdin.end(payload===undefined?undefined:JSON.stringify(payload));
  });
}
class Journal {
  constructor(directory){this.directory=directory;fs.mkdirSync(directory,{recursive:true});}
  file(key){return path.join(this.directory,createHash('sha256').update(key).digest('hex')+'.json');}
  get(key){const file=this.file(key);if(!fs.existsSync(file))return null;try{return JSON.parse(fs.readFileSync(file,'utf8'));}catch{throw new Problem('本机提交凭证未能读取，已停止重发。',503,true);}}
  set(key,value){const file=this.file(key),temp=file+'.tmp';fs.writeFileSync(temp,JSON.stringify(value),{mode:0o600});fs.renameSync(temp,file);}
}
class GitHubClient {
  constructor({request=ghRequest,journal}={}){this.request=request;this.journal=journal;this.queues=new Map();}
  async session(){const me=await this.request('GET','/user');if(!me||typeof me.login!=='string'||!Number.isSafeInteger(me.id))throw new Problem('请先在电脑终端执行 gh auth login。',401);return {login:me.login,id:me.id};}
  async repository(name){
    name=repoName(name);const me=await this.session(),repo=await this.request('GET',`/repos/${name}`);
    if(repo.owner?.login?.toLowerCase()!==me.login.toLowerCase()||repo.private!==true||!repo.has_issues||repo.archived||repo.disabled)throw new Problem('请选择当前账号本人拥有、已开启 Issues 且未归档的私有仓库。',403);
    return {repo:repo.full_name,branch:repo.default_branch||'main',login:me.login,accountId:me.id};
  }
  async history(name,page=1){
    if(!Number.isSafeInteger(page)||page<1||page>10000)throw new Problem('分页参数不正确。');
    const target=await this.repository(name),rows=await this.request('GET',`/repos/${target.repo}/issues?state=all&sort=created&direction=desc&per_page=20&page=${page}`);
    if(!Array.isArray(rows))throw new Problem('GitHub 记录格式异常。',502);
    return {entries:rows.filter(row=>!row.pull_request).map(row=>receipt(row,target.repo)),nextPage:rows.length===20?page+1:null,repo:target.repo,login:target.login,checked:new Date().toISOString()};
  }
  async find(target,id){
    for(let page=1;page<=100;page++){
      const rows=await this.request('GET',`/repos/${target.repo}/issues?state=all&sort=created&direction=desc&per_page=100&page=${page}`);
      if(!Array.isArray(rows))throw new Problem('核对结果格式异常，请保留草稿。',502,true);
      const found=rows.find(row=>!row.pull_request&&typeof row.body==='string'&&row.body.includes(`<!-- camp-issue:${id} -->`));
      if(found)return receipt(found,target.repo);if(rows.length<100)return null;
    }
    throw new Problem('记录较多，尚未完整核对，请保留草稿到 GitHub 检查。',409,true);
  }
  async exclusive(key,action){const prior=this.queues.get(key)||Promise.resolve();const next=prior.catch(()=>{}).then(action);this.queues.set(key,next);try{return await next;}finally{if(this.queues.get(key)===next)this.queues.delete(key);}}
  async reconcile(name,id){
    if(!validId(id))throw new Problem('草稿标识无效。');const target=await this.repository(name),key=`${target.accountId}:${target.repo.toLowerCase()}:${id}`;
    return this.exclusive(key,async()=>{
      const old=this.journal.get(key);if(old?.receipt)return {receipt:old.receipt};
      const found=await this.find(target,id);
      if(found)this.journal.set(key,{...old,receipt:found,state:'success'});
      else if(old)this.journal.set(key,{...old,state:'retry'});
      return {receipt:found,canRetry:!found};
    });
  }
  async submit(input){
    const draft=validate(input),target=await this.repository(draft.repo),key=`${target.accountId}:${target.repo.toLowerCase()}:${draft.id}`;
    return this.exclusive(key,async()=>{
      const fingerprint=createHash('sha256').update(JSON.stringify({body:draft.body,images:draft.images.map(i=>({id:i.id,mime:i.mime,hash:createHash('sha256').update(i.bytes).digest('hex')}))})).digest('hex');
      const old=this.journal.get(key);
      if(old?.receipt){if(old.fingerprint&&old.fingerprint!==fingerprint)throw new Problem('此稿件已有成功回执，但当前内容已修改。请先核对原提交并另记修改内容。',409,true);return {receipt:old.receipt};}
      if(old?.state==='creating')throw new Problem('上次提交结果尚未确认，请先点“核对提交结果”。',409,true);
      if(old?.fingerprint&&old.fingerprint!==fingerprint)throw new Problem('这份稿件已有提交尝试。请保留当前稿，另存新稿后提交修改后的内容。',409);
      const previous=await this.find(target,draft.id);
      if(previous){this.journal.set(key,{fingerprint,state:'success',receipt:previous});return {receipt:previous};}
      this.journal.set(key,{fingerprint,state:'uploading'});
      let body=draft.body;
      for(const image of draft.images){
        const file=`attachments/${draft.id}/${image.id}.${image.extension}`,endpoint=`/repos/${target.repo}/contents/${file}`;
        let existing;try{existing=await this.request('GET',endpoint);}catch(error){if(error.status!==404)throw error;}
        const sha=createHash('sha1').update(`blob ${image.bytes.length}\0`).update(image.bytes).digest('hex');
        if(existing){if(existing.sha!==sha)throw new Problem('同名附件内容不同，已保留草稿，请另存新稿。',409);}
        else await this.request('PUT',endpoint,{message:`Add issue attachment ${draft.id}`,content:image.data});
        body=body.replaceAll(`camp-image:${image.id}`,`../blob/${encodeURIComponent(target.branch)}/${file}?raw=true`);
      }
      this.journal.set(key,{fingerprint,state:'creating'});
      try{
        const result=await this.request('POST',`/repos/${target.repo}/issues`,{title:draft.title,body:body.trimEnd()+`\n\n<!-- camp-issue:${draft.id} -->`});
        const saved=receipt(result,target.repo);this.journal.set(key,{fingerprint,state:'success',receipt:saved});return {receipt:saved};
      }catch(error){
        if(error.status>=400&&error.status<500&&!error.uncertain)this.journal.set(key,{fingerprint,state:'retry'});
        else error.uncertain=true;
        throw error;
      }
    });
  }
}
module.exports={GitHubClient,Journal,Problem,repoName,validate,imageExtension,ghRequest};
