import {makeEditor} from './editor.js';
import {exportDrafts,importDrafts,additions} from './archive.js';
const $=id=>document.getElementById(id);
const prefix=location.pathname.startsWith('/ai-camp-schedule/')?'/ai-camp-schedule':'';
const apiBase=['http://127.0.0.1:8899','http://localhost:8899'].includes(location.origin)?'http://127.0.0.1:8902/api/issue/':prefix+'/api/issue/';
let store,current,rich,editorDraft='',pool=[],session=null,target=null,busy=false,dirty=false,saveError=false,queue=Promise.resolve(),historyBusy=false,historyRows=[],nextPage=null,checked='',editVersion=0;
const date=value=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});
function notify(text){$('intake-notice').textContent=text;$('intake-notice').hidden=!text;}
function problem(error){saveError=true;$('draft-status').textContent='草稿未能保存，请保留当前输入';$('recover-draft').hidden=false;notify(error.message||'操作未完成，请保留原文。');update();}
function enqueue(action){const job=queue.then(action);queue=job.catch(()=>{});return job;}
async function api(route,{method='GET',body}={}){
  let response;try{response=await fetch(apiBase+route,{method,credentials:'same-origin',headers:{...(session?{'X-Camp-CSRF':session.csrf}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(method==='POST'?240000:90000)});}catch{const error=Error(method==='POST'?'连接中断，请先核对提交结果，图文草稿仍保留。':'本机服务未连接。请双击项目中的“启动本地网站”，再检查连接。');error.uncertain=method==='POST';throw error;}
  let data;try{data=await response.json();}catch{const error=Error(method==='POST'?'提交回执未能读取，请先核对结果；图文草稿仍保留。':'本机服务未连接。请使用“启动本地网站”打开，图文草稿仍可保留。');error.uncertain=method==='POST';throw error;}
  if(!response.ok){const error=Error(data.error||'GitHub 操作未完成。');error.uncertain=data.uncertain;throw error;}return data;
}
function update(){
  if(!current)return;
  $('draft-count').textContent=store?.drafts.length||0;$('image-count').textContent=`${current.images.length}/9 · 每张不超过 10 MB`;$('character-count').textContent=`${rich?.editor.getText().length||0} 字`;
  $('submit-issue').disabled=busy||!target||target.repo.toLowerCase()!==current.repo.toLowerCase()||!current.body.trim()||saveError||current.uncertain;
  $('submit-issue').textContent=busy?'正在处理…':'提交 Issue';$('reconcile').hidden=!current.uncertain;$('reconcile').disabled=busy||!session;
  for(const id of ['open-drafts','save-repo','clear-draft','add-images','connect-local'])$(id).disabled=busy;
  for(const id of ['export-drafts','import-drafts','new-draft'])$(id).disabled=busy;
  $('issue-repo').disabled=busy;if(rich&&rich.editor.isEditable!==(!busy&&!current.uncertain))rich.editor.setEditable(!busy&&!current.uncertain,false);
  for(const button of document.querySelectorAll('.editor-toolbar button,.editor-toolbar select'))button.disabled=busy||current.uncertain;
  $('add-images').disabled=busy||current.uncertain;
  $('submit-hint').textContent=current.uncertain?'上次提交结果尚未确认，请先核对；暂时锁定本稿内容。':busy?'正在上传图文并确认结果，请保留此页面。':target?`图文将提交到 ${target.repo}。`:'草稿留在本机，连接并保存仓库后即可直接提交。';
}
function save(){
  if(!store||!current)return Promise.reject(Error('草稿未准备好。'));
  const snapshot=structuredClone(current),version=editVersion;return enqueue(async()=>{await store.save(snapshot);if(version===editVersion)dirty=false;saveError=false;$('draft-status').textContent=dirty?'正在保存图文草稿…':'图文草稿已保存在本机';update();});
}
function changed(){editVersion++;current.body=rich.body();current.images=rich.images(pool);dirty=true;$('draft-status').textContent='正在保存图文草稿…';update();save().catch(problem);}
function installEditor(){
  rich?.destroy();$('rich-editor').replaceChildren();rich=makeEditor($('rich-editor'),{body:current.body,images:pool,onUpdate:changed,onFiles:files,onWarning:notify});editorDraft=current.id;
  rich.editor.on('selectionUpdate',()=>{for(const button of document.querySelectorAll('[data-command]'))button.setAttribute('aria-pressed',String(rich.editor.isActive(button.dataset.command)));});
}
function load(){current=store.current;pool=[...current.images];if(editorDraft!==current.id)installEditor();else rich.set(current.body,pool);$('issue-repo').value=store.repo;dirty=false;saveError=false;$('draft-status').textContent='图文草稿已保存在本机';$('recover-draft').hidden=true;update();}
async function transition(action){if(busy)return;busy=true;update();try{await save();await enqueue(action);load();}catch(error){problem(error);}finally{busy=false;update();}}
async function connect(){
  $('connection-state').textContent='正在检查本机 GitHub…';session=null;target=null;update();
  try{session=await api('session');$('connection-state').textContent=`已连接 · ${session.login}`;
    if(store.repo){target=await api('repository?'+new URLSearchParams({repo:store.repo}));$('connection-state').textContent=`已连接 · ${session.login} · ${target.repo}`;}
  }catch(error){$('connection-state').textContent=error.message;}
  $('repository-settings').open=!target;
  update();
}
async function files(selected){
  if(busy||current.uncertain)return;
  try{
    const rows=[...selected];if(current.images.length+rows.length>9)throw Error('每条记录最多 9 张原图，可以另记一条。');
    for(const file of rows)if(!['image/png','image/jpeg','image/webp','image/gif'].includes(file.type)||file.size<1||file.size>10000000)throw Error('请选择 PNG、JPG、WebP 或 GIF，每张不超过 10 MB。');
    const added=rows.map(file=>({id:crypto.randomUUID(),name:file.name||'截图',mime:file.type,blob:file}));pool.push(...added);rich.insert(added);await queue;
  }catch(error){notify(error.message);}
}
function listItem(title,meta,url){const item=document.createElement('article');item.className='library-item';const strong=document.createElement('strong');strong.textContent=title;const text=document.createElement('span');text.textContent=meta;item.append(strong,text);if(url&&/^https:\/\/github\.com\/[A-Za-z0-9-]+\/[A-Za-z0-9_.-]+\/issues\/[1-9][0-9]*$/.test(url)){const a=document.createElement('a');a.href=url;a.textContent='查看 Issue ↗';a.target='_blank';a.rel='noopener noreferrer';item.append(a);}return item;}
function renderDrafts(){
  $('draft-list').replaceChildren();for(const draft of store.drafts){const item=listItem(window.IssueRichStore.title(draft.body),`${date(draft.updated)} · ${draft.images.length} 张图 · ${draft.repo||'未选择仓库'}${draft.uncertain?' · 结果待核对':''}${draft.id===current.id?' · 当前稿':''}`);const button=document.createElement('button');button.type='button';button.className='draft-select';button.append(...item.childNodes);button.onclick=()=>transition(async()=>{await store.select(draft.id);$('draft-library').close();target=null;}).then(connect);$('draft-list').append(button);}
  if(!$('draft-list').children.length)$('draft-list').textContent='还没有其他草稿，直接记下一条吧。';
}
function renderHistory(){
  $('record-list').replaceChildren();
  const seen=new Set();for(const row of historyRows){if(seen.has(row.number))continue;seen.add(row.number);$('record-list').append(listItem(`#${row.number} ${row.title}`,`${date(row.created)} · ${row.state==='closed'?'GitHub 已关闭':'GitHub 开放中'}`,row.url));}
  if(!historyRows.length)$('record-list').textContent='当前仓库暂无已读取的提交记录。';
  $('more-records').hidden=!nextPage;$('more-records').disabled=historyBusy;$('refresh-records').disabled=historyBusy;
  $('record-state').textContent=historyBusy?'正在读取 GitHub…':checked?`最近刷新：${date(checked)}（北京时间）`:'尚未读取 GitHub；下方如有记录，为本机缓存。';
}
async function history(reset=true){
  if(historyBusy||!current.repo)return;const repo=current.repo;
  if(reset){const cache=store.state.history[repo.toLowerCase()];historyRows=cache?.entries||store.state.records.filter(r=>r.state==='submitted'&&r.repo.toLowerCase()===repo.toLowerCase());nextPage=null;checked='';}
  historyBusy=true;renderHistory();
  try{if(!session)throw Error('尚未连接本机 GitHub。');const result=await api('history?'+new URLSearchParams({repo,page:reset?1:nextPage}));if(repo!==current.repo)return;
    historyRows=reset?result.entries:[...historyRows,...result.entries];nextPage=result.nextPage;checked=result.checked;
    await enqueue(()=>store.cache(repo,{entries:historyRows,checked,nextPage}));
  }catch(error){$('record-library-notice').textContent=error.message+' 已保留现有缓存。';$('record-library-notice').hidden=false;}finally{historyBusy=false;renderHistory();}
}
async function finish(receipt){const submitted=current.id;await enqueue(()=>store.receipt(receipt,submitted));load();notify(`已提交 Issue #${receipt.number}。其他草稿保留。`);const a=document.createElement('a');a.textContent=' 查看记录 ↗';a.href=receipt.url;a.target='_blank';a.rel='noopener noreferrer';$('intake-notice').append(a);}
async function base64(blob){return new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.onerror=()=>reject(Error('本机图片未能读取，原稿保留。'));reader.readAsDataURL(blob);});}
async function submit(){
  if(busy)return;busy=true;update();
  try{
    await save();if($('issue-repo').value.trim().toLowerCase()!==current.repo.toLowerCase())throw Error('仓库输入已变化，请先保存仓库。');
    if(!target||target.repo.toLowerCase()!==current.repo.toLowerCase()||!session)throw Error('请先检查连接并保存仓库。');
    const images=[];for(const row of current.images)images.push({id:row.id,mime:row.mime,data:await base64(row.blob)});
    // Durable unknown-result state comes before the network write, including browser closure.
    current.uncertain=true;await save();update();
    const result=await api('submit',{method:'POST',body:{id:current.id,repo:current.repo,body:current.body,images}});try{await finish(result.receipt);}catch(error){error.uncertain=true;throw error;}
  }catch(error){if(!error.uncertain){current.uncertain=false;try{await save();}catch(failure){problem(failure);}}else{current.uncertain=true;try{await save();}catch(failure){problem(failure);}}notify(error.message);}
  finally{busy=false;update();}
}
async function reconcile(){if(busy)return;busy=true;update();try{const result=await api('reconcile',{method:'POST',body:{repo:current.repo,id:current.id}});if(result.receipt)await finish(result.receipt);else{current.uncertain=false;await save();notify('已完整核对，未找到这份稿件。可以再次点击提交。');}}catch(error){notify(error.message);}finally{busy=false;update();}}
async function start(){
  try{
    store=await window.IssueRichStore.Store.open();current=store.current;pool=[...current.images];
    load();
    $('issue-form').onsubmit=event=>event.preventDefault();$('connect-local').onclick=connect;
    $('save-repo').onclick=async()=>{if(busy)return;try{const value=$('issue-repo').value.trim();if(!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(value))throw Error('请填写账号/仓库名。');if(!session)await connect();if(!session)throw Error('请先连接本机 GitHub。');const checkedTarget=await api('repository?'+new URLSearchParams({repo:value}));await transition(()=>store.setRepo(checkedTarget.repo));if(store.repo.toLowerCase()===checkedTarget.repo.toLowerCase()){target=checkedTarget;$('connection-state').textContent=`已连接 · ${session.login} · ${target.repo}`;$('repository-settings').open=false;notify(`提交到 ${target.repo}`);update();}}catch(error){notify(error.message);}};
    $('add-images').onclick=()=>$('image-files').click();$('image-files').onchange=()=>{void files($('image-files').files);$('image-files').value='';};
    $('submit-issue').onclick=submit;$('reconcile').onclick=reconcile;
    $('copy-body').onclick=async()=>{try{await navigator.clipboard.writeText(current.body);notify(current.images.length?'正文已复制；截图仍在本机草稿，提交时会一同上传。':'正文已复制。');}catch{notify('剪贴板未能写入，请在编辑区选中文字复制。');}};
    $('open-drafts').onclick=async()=>{try{await save();renderDrafts();$('draft-library').showModal();}catch(error){problem(error);}};
    $('close-drafts').onclick=()=>$('draft-library').close();$('new-draft').onclick=()=>transition(async()=>{await store.newDraft();$('draft-library').close();});
    $('export-drafts').onclick=async()=>{if(busy)return;busy=true;update();try{await save();const text=await exportDrafts(store.drafts),url=URL.createObjectURL(new Blob([text],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='营期草稿-'+new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'})+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);$('draft-library-notice').textContent='图文备份已生成，请妥善保存下载文件。';$('draft-library-notice').hidden=false;}catch(error){$('draft-library-notice').textContent=error.message;$('draft-library-notice').hidden=false;}finally{busy=false;update();}};
    $('import-drafts').onclick=()=>$('archive-file').click();$('archive-file').onchange=async()=>{const file=$('archive-file').files[0];$('archive-file').value='';if(!file||busy)return;busy=true;update();try{if(file.size>150000000)throw Error('备份超过 150 MB，请核对文件。');await save();const decoded=await importDrafts(await file.text()),rows=await additions(store.state.drafts,decoded);await enqueue(()=>store.mergeDrafts(rows));load();target=null;await connect();renderDrafts();$('draft-library-notice').textContent=`已导入 ${rows.length} 份草稿，跳过 ${decoded.length-rows.length} 份重复稿。没有提交到 GitHub。`;$('draft-library-notice').hidden=false;}catch(error){$('draft-library-notice').textContent=error.message+' 当前草稿仍保留。';$('draft-library-notice').hidden=false;}finally{busy=false;update();}};
    $('clear-draft').onclick=()=>$('confirm-clear').showModal();$('cancel-clear').onclick=()=>$('confirm-clear').close();$('confirm-clear-button').onclick=()=>transition(async()=>{await store.clear();$('confirm-clear').close();notify('当前稿已清空，其他草稿保留。');});
    $('recover-draft').onclick=async()=>{if(busy)return;busy=true;update();try{const value=structuredClone(current);await enqueue(()=>store.recover(value));load();notify('当前输入已另存新稿，其他窗口的内容保留。');}catch(error){problem(error);}finally{busy=false;update();}};
    $('open-records').onclick=()=>{
      $('records-repo').textContent=current.repo||'尚未选择仓库';$('all-records').hidden=!current.repo;
      $('record-library').showModal();$('record-library-notice').hidden=true;$('old-record-list').replaceChildren();
      const legacy=store.state.records.filter(row=>row.state!=='submitted'&&row.repo.toLowerCase()===current.repo.toLowerCase());$('old-records').hidden=!legacy.length;
      for(const row of legacy)$('old-record-list').append(listItem(row.title,`${date(row.created)} · ${row.state==='linked'?'本人关联，未核验':'已打开 GitHub，待核对'}`,row.state==='linked'?row.url:undefined));
      $('all-records').href=`https://github.com/${current.repo}/issues`;void history(true);
    };
    $('close-records').onclick=()=>$('record-library').close();$('refresh-records').onclick=()=>history(true);$('more-records').onclick=()=>history(false);
    $('block-type').onchange=()=>{const value=$('block-type').value;const chain=rich.editor.chain().focus();(value==='paragraph'?chain.setParagraph():chain.toggleHeading({level:Number(value)})).run();};
    for(const button of document.querySelectorAll('[data-command]'))button.onclick=()=>{const command=button.dataset.command,chain=rich.editor.chain().focus();if(command==='link'){$('link-url').value=rich.editor.getAttributes('link').href||'';$('link-error').textContent='';$('link-dialog').showModal();return;}
      const methods={bold:'toggleBold',italic:'toggleItalic',bulletList:'toggleBulletList',orderedList:'toggleOrderedList',blockquote:'toggleBlockquote',codeBlock:'toggleCodeBlock',undo:'undo',redo:'redo'};if(command==='table')chain.insertTable({rows:3,cols:2,withHeaderRow:true}).run();else chain[methods[command]]().run();};
    $('cancel-link').onclick=()=>$('link-dialog').close();$('save-link').onclick=()=>{const value=$('link-url').value.trim();try{const url=new URL(value);if(!['https:','http:'].includes(url.protocol))throw Error();rich.editor.chain().focus().extendMarkRange('link').setLink({href:url.href}).run();$('link-dialog').close();}catch{$('link-error').textContent='请填写 http 或 https 链接。';}};
    window.addEventListener('beforeunload',event=>{if(dirty||saveError||busy){event.preventDefault();event.returnValue='';}});
    const task=new URLSearchParams(location.search).get('task');if(task&&task.length<=4000){await store.newDraft();current=store.current;current.body=task;await store.save(current);load();historyReplace();}
    await connect();document.documentElement.dataset.intakeReady='true';
  }catch(error){$('rich-editor').textContent='图文草稿未能打开，原数据未覆盖。';$('draft-status').textContent='未能读取草稿';notify(error.message);}
}
function historyReplace(){window.history.replaceState(null,'',location.pathname);}
void start();
