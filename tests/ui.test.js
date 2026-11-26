const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const { JSDOM }=require(process.env.JSDOM_MODULE || 'jsdom');
const root=path.resolve(__dirname,'..');
const tick=()=>new Promise(resolve=>setTimeout(resolve,10));
const copy=x=>JSON.parse(JSON.stringify(x));
function server(){
 let camps=[],tasks=[];let sequence=0;
 return {calls:[], async rpc(name,p){
  this.calls.push([name,copy(p)]);
  const active=()=>camps.filter(c=>c.program===p.p_program);
  if(name==='schedule_snapshot')return {data:copy({camps:active(),tasks:tasks.filter(t=>active().some(c=>c.id===t.camp_id))})};
  const conflict=()=>({error:{code:'P0001',message:'schedule_conflict'}});
  if(name==='save_schedule_camp'){
   if(!p.p_id){const c={id:'camp-'+(++sequence),program:p.p_program,term:p.p_term,open_date:p.p_open_date,skips:p.p_skips,exam_week:p.p_exam_week ?? 7,version:1};camps.push(c);return {data:copy(c)};}
   const c=active().find(c=>c.id===p.p_id);if(!c || c.version!==p.p_version)return conflict();
   Object.assign(c,{term:p.p_term,open_date:p.p_open_date,skips:p.p_skips,exam_week:p.p_exam_week ?? c.exam_week,version:c.version+1});return {data:copy(c)};
  }
  if(name==='set_schedule_task'){
   let task=tasks.find(t=>t.camp_id===p.p_camp_id&&t.task_key===p.p_task_key);
   if((task?.version||0)!==p.p_version)return conflict();
   if(!task){task={camp_id:p.p_camp_id,task_key:p.p_task_key,version:0};tasks.push(task);}
   Object.assign(task,{completed:p.p_completed,version:task.version+1});return {data:copy(task)};
  }
  if(name==='import_schedule_camps'){
   const inserted=[],skipped=[];
   for(const c of p.p_camps){if(active().some(x=>x.term===c.term)){skipped.push(c.term);continue;}
    const saved=(await this.rpc('save_schedule_camp',{...p,p_id:null,p_term:c.term,p_open_date:c.open_date,p_skips:c.skips,p_exam_week:c.exam_week})).data;
    for(const key of c.completed_tasks)tasks.push({camp_id:saved.id,task_key:key,completed:true,version:1});inserted.push(c.term);
   }
   return {data:{inserted,skipped}};
  }
  throw new Error('Unexpected RPC '+name);
 }};
}
async function windowFor(backend,{loggedIn=true,legacy=false,legacyDone={}}={}){
 const html=fs.readFileSync(path.join(root,'index.html'),'utf8');
 const dom=new JSDOM(html,{url:'https://internal.aurange.cn/test/#9:2030-02-03',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window;w.alert=()=>{};w.confirm=()=>true;w.HTMLElement.prototype.scrollIntoView=function(){};
 w.eval(fs.readFileSync(path.join(root,'config.js'),'utf8'));
 const prefix=w.SCHEDULE_CONFIG.program==='ai'?'ai':'crawler';
 if(legacy){
  w.localStorage.setItem(prefix+'-camps-v1',JSON.stringify([{term:8,openDate:'2030-01-06',skips:[]}]));
  w.localStorage.setItem(prefix+'-camps-done-v1',JSON.stringify(legacyDone));
 }
 const session=loggedIn?{user:{id:'member',email:'member@example.test'}}:null;
 const client={rpc:(name,p)=>backend.rpc(name,p),auth:{getSession:async()=>({data:{session}}),onAuthStateChange:()=>({data:{subscription:{unsubscribe(){}}}})},
  channel:()=>({on(){return this},subscribe(){return this}}),removeChannel(){}};
 w.supabase={createClient:()=>client};w.eval(fs.readFileSync(path.join(root,'cloud.js'),'utf8'));
 for(const source of ['task-scripts-data.js','task-scripts.js']) w.eval(fs.readFileSync(path.join(root,source),'utf8'));
 const sop=fs.readFileSync(path.join(root,'data.js'),'utf8');
 const inline=[...w.document.scripts].filter(s=>!s.src).map(s=>s.textContent).join('\n');
 w.eval(sop+'\n'+inline+'\nwindow.__test={get cloud(){return cloud}, get legacy(){return legacy}};');await tick();
 return {w,close(){w.__test.cloud.stop();w.close();}};
}

test('two independent pages share camps and completion without URL or local data overwrites',async()=>{
 const backend=server(),first=await windowFor(backend,{legacy:true}),second=await windowFor(backend);
 try{
  const a=first.w,b=second.w;
  assert.equal(a.__test.cloud.state.camps.length,0);
  assert.equal(backend.calls.filter(([name])=>name!=='schedule_snapshot').length,0,'old links never silently write to cloud');
  const key=a.SCHEDULE_CONFIG.program==='ai'?'ai-camps-v1':'crawler-camps-v1';const before=a.localStorage.getItem(key);
  a.openForm(null);a.document.getElementById('f-term').value='1';a.document.getElementById('f-date').value='2030-01-06';await a.saveForm(null);
  await b.__test.cloud.refresh();assert.match(b.document.getElementById('root').textContent,/第 1 期/);
  const firstCheckbox=a.document.querySelector('.ckb');assert.ok(firstCheckbox);await a.toggleDone(Number(firstCheckbox.getAttribute('onclick').match(/\d+/)[0]));await b.__test.cloud.refresh();
  assert.equal(b.document.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  a.openForm(1);b.openForm(1);
  b.document.getElementById('f-date').value='2030-01-13';await b.saveForm(1);await a.__test.cloud.refresh();
  assert.equal(a.document.getElementById('f-date').value,'2030-01-06','background sync preserves the open draft');
  await a.saveForm(1);assert.match(a.document.getElementById('notice').textContent,/其他人/);
  assert.ok(a.document.getElementById('f-date'),'conflict keeps draft visible');
  a.closeForm();a.openImport('local');await a.importLegacy('local');await b.__test.cloud.refresh();
  assert.equal(b.__test.cloud.state.camps.length,2);assert.equal(a.localStorage.getItem(key),before);
  assert.equal(a.__test.cloud.state.camps.some(c=>c.term===9),false,'shared URL still requires explicit import');
  assert.equal(a.__test.cloud.state.camps.find(c=>c.term===1).openDate,'2030-01-13');
 }finally{first.close();second.close();}
});

test('logged out visitors do not fetch or render team data',async()=>{
 const backend=server(),page=await windowFor(backend,{loggedIn:false,legacy:true});
 try{
  assert.ok(page.w.document.getElementById('login-email'));
  assert.equal(backend.calls.length,0);assert.equal(page.w.document.querySelector('.camp'),null);
  assert.equal(page.w.document.querySelector('.sop-pending'),null);
 }finally{page.close();}
});

async function createCamp(backend,skips=[]){
 const result=await backend.rpc('save_schedule_camp',{p_program:'ai',p_id:null,p_term:1,p_open_date:'2030-01-06',p_skips:skips});
 return result.data;
}
function showPhase(w,term,key){
 const camp=w.__test.cloud.state.camps.find(item=>item.term===term);
 const weeks=w.campWeeks(camp);
 const index=weeks.findIndex(week=>week.key===key);
 assert.ok(index>=0,`phase ${key} exists`);w.setView(term,index);
}

test('graduation extends to week thirteen with a flexible ceremony time and preserved completion',async()=>{
 const backend=server();const camp=await createCamp(backend,[3,8]);const page=await windowFor(backend);
 try{
  const w=page.w,weeks=w.campWeeks({term:1,openDate:'2030-01-06',skips:[3,8]});
  const course=weeks.find(week=>week.n===12),closing=weeks.find(week=>week.n===13);
  assert.equal(w.fmtS(course.mon),'4/8');assert.equal(w.fmtS(closing.mon),'4/15');
  const tasks=week=>w.weekTasks(week);
  assert.ok(tasks(course).some(t=>t.t==='发布课程及打卡等更新提醒'));
  assert.ok(tasks(closing).some(t=>t.t==='群内发布成果调研问卷，收集成果与反馈'));
  assert.ok(tasks(course).some(t=>t.t==='发送毕业作品提交与展示意愿问卷'));
  assert.ok(!tasks(closing).some(t=>t.t==='发布课程及打卡等更新提醒'));
  const deadline=tasks(closing).find(t=>t.t==='截止收集毕业作品与展示意愿');
  assert.equal(deadline.day,3);assert.equal(deadline.time,'23:59前');
  assert.equal(w.fmtS(w.addD(closing.mon,2)),'4/17');
  const selection=tasks(closing).find(t=>t.t==='整理作品，与队长确定展示成员');
  assert.equal(selection.day,4);
  assert.equal(w.taskKey(closing,selection),'week-12|5|每队评选并公布 1–2 位毕业展示代表');
  const ceremony=tasks(closing).find(t=>t.t==='结营典礼直播');
  assert.equal(ceremony.day,0);assert.equal(ceremony.time,undefined);
  assert.ok(tasks(closing).some(t=>t.t==='发送嘉宾邀请并确认展示安排'&&t.day===5&&t.d.includes('5分钟')));
  assert.ok(tasks(closing).some(t=>t.t==='测试直播并组织嘉宾彩排'&&t.time==='周五或周六'));
  const reminders=tasks(closing).filter(t=>t.t==='再次提醒学员提交毕业作品');
  assert.deepEqual(copy(reminders.map(t=>t.day)),[1,3]);
  assert.ok(!tasks(closing).some(t=>/文字分享|对话版/.test(t.t)));
  const keys=weeks.flatMap(week=>tasks(week).map(t=>w.taskKey(week,t)));
  assert.equal(new Set(keys).size,keys.length);
  const oldKey='week-12|0|结营典礼直播';
  await w.__test.cloud.setTask(camp.id+'|'+oldKey,true);
  showPhase(w,1,'week-13');
  const rendered=[...w.document.querySelectorAll('#camp-1 .task')].find(el=>el.querySelector('.task-title-action').textContent==='结营典礼直播');
  assert.equal(rendered.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  assert.equal(rendered.querySelector('.task-time'),null);
  await w.__test.cloud.setTask(camp.id+'|'+oldKey,false);
  const today=w.document.createElement('div');
  today.innerHTML=w.todaySection([{camp:{term:1},weeks,pos:weeks.indexOf(closing)}],closing.sun);
  assert.ok(today.textContent.indexOf('结营典礼直播')<today.textContent.indexOf('各小队代表进行毕业作品展示'));
  assert.ok(today.textContent.indexOf('各小队代表进行毕业作品展示')<today.textContent.indexOf('群发优惠券与推广返现说明'));
 }finally{page.close();}
});

test('renamed SOP tasks retain cloud completion and update the existing task key',async()=>{
 const backend=server(),camp=await createCamp(backend);
 const cases=[
  {week:2,day:5,old:'收集好评/成果案例 3 条至案例文档',title:'收集好评/成果案例 5 条至案例文档'},
  {week:12,viewKey:'week-13',day:'all',old:'邮寄实物奖励及发放虚拟奖励',title:'按本期方案发放结营奖励'},
  {week:2,day:6,old:'结束答疑问卷收集，整理后发给答疑老师',title:'截止答疑问卷收集，整理新增问题并同步老师'},
  {week:0,key:'open',day:6,old:'鹅圈子新建小队并把成员移入对应小队',title:'鹅圈子新建小队并把成员移入对应小队'},
  {key:'open',viewKey:'prep',day:'all',old:'创建本期课程',title:'建立本期小鹅通圈子与课程'},
  {key:'open',viewKey:'prep',day:'all',old:'更新本期云空间配套资料',title:'建立本期群资料与云空间'},
 ];
 for(const item of cases){
  await backend.rpc('set_schedule_task',{p_program:'ai',p_camp_id:camp.id,p_task_key:`${item.key || 'week-'+item.week}|${item.day}|${item.old}`,p_version:0,p_completed:true});
 }
 const page=await windowFor(backend);
 try{
  const w=page.w;
  for(const item of cases){
   showPhase(w,1,item.viewKey || item.key || 'week-'+item.week);
   const task=[...w.document.querySelectorAll('#camp-1 .task')].find(el=>el.querySelector('.task-t').textContent.includes(item.title));
   assert.ok(task,`renders updated task: ${item.title}`);
   const button=task.querySelector('.ckb');assert.equal(button.getAttribute('aria-pressed'),'true');
   if(item.day===6 && item.key==='open') assert.match(task.closest('.daygrp').querySelector('.day-hdr').textContent,/周日/,'circle setup moves to opening Sunday');
   await w.toggleDone(Number(button.getAttribute('onclick').match(/\d+/)[0]));
   const write=backend.calls.filter(([name])=>name==='set_schedule_task').at(-1)[1];
   assert.equal(write.p_task_key,`${item.key || 'week-'+item.week}|${item.day}|${item.old}`);
   assert.equal(write.p_version,1);assert.equal(write.p_completed,false);
  }
  const snapshot=await backend.rpc('schedule_snapshot',{p_program:'ai'});
  assert.equal(snapshot.data.tasks.length,cases.length,'content and day changes never create a second completion record');
 }finally{page.close();}
});

test('legacy import keeps renamed SOP task completions and the original browser backup',async()=>{
 const legacyDone={
  '8|第2周|5|收集好评/成果案例 3 条至案例文档':1,
  '8|第12周|all|邮寄实物奖励及发放虚拟奖励':1,
  '8|开营周|6|鹅圈子新建小队并把成员移入对应小队':1,
  '8|第2周|6|结束答疑问卷收集，整理后发给答疑老师':1,
  '8|开营周|all|创建本期课程':1,
  '8|开营周|all|更新本期云空间配套资料':1,
 };
 const page=await windowFor(server(),{legacy:true,legacyDone});
 try{
  const w=page.w,before=w.localStorage.getItem('ai-camps-done-v1');
  const entry=w.legacyEntries('local')[0];
  assert.deepEqual(copy(entry.completed_tasks).sort(),[
   'week-12|all|邮寄实物奖励及发放虚拟奖励',
   'week-2|5|收集好评/成果案例 3 条至案例文档',
   'open|6|鹅圈子新建小队并把成员移入对应小队',
   'week-2|6|结束答疑问卷收集，整理后发给答疑老师',
   'open|all|创建本期课程',
   'open|all|更新本期云空间配套资料',
  ].sort());
  await w.importLegacy('local');
  showPhase(w,8,'week-2');
  const task=[...w.document.querySelectorAll('#camp-8 .task')].find(el=>el.textContent.includes('收集好评/成果案例 5 条至案例文档'));
  assert.equal(task.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  const cutoff=[...w.document.querySelectorAll('#camp-8 .task')].find(el=>el.textContent.includes('截止答疑问卷收集，整理新增问题并同步老师'));
  assert.equal(cutoff.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  showPhase(w,8,'prep');
  for(const title of ['建立本期小鹅通圈子与课程','建立本期群资料与云空间']){
   const moved=[...w.document.querySelectorAll('#camp-8 .task')].find(el=>el.textContent.includes(title));
   assert.equal(moved.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  }
  showPhase(w,8,'open');
  const circle=[...w.document.querySelectorAll('#camp-8 .task')].find(el=>el.textContent.includes('鹅圈子新建小队并把成员移入对应小队'));
  assert.equal(circle.querySelector('.ckb').getAttribute('aria-pressed'),'true');
  assert.match(circle.closest('.daygrp').querySelector('.day-hdr').textContent,/周日/);
  assert.equal(w.localStorage.getItem('ai-camps-done-v1'),before);
 }finally{page.close();}
});

test('per-camp enrollment preparation resolves the last global pending item',async()=>{
 const page=await windowFor(server());
 try{
  const w=page.w,pending=w.document.querySelector('.sop-pending');
  assert.equal(pending,null,'no empty or resolved pending notice is shown');
  const weeks=w.campWeeks({term:1,openDate:'2030-01-06',skips:[3]});
  assert.equal(weeks.filter(week=>week.type==='exam').length,1);
  assert.equal(weeks.find(week=>week.type==='exam').n,7);
  const tasks=weeks.flatMap(week=>w.weekTasks(week));
  assert.ok(tasks.some(task=>/考试|实名认证|报考信息/.test(task.t)));
  assert.equal(tasks.some(task=>/8月31日|10月15日/.test(JSON.stringify(task))),false,'old date examples never become recurring deadlines');
  const referral=tasks.find(task=>task.t==='群发优惠券与推广返现说明');
  assert.equal(referral.day,0);assert.equal(referral.time,'直播后');
  assert.match(referral.d,/300/);assert.match(referral.d,/200/);assert.match(referral.d,/退款/);
  assert.equal(tasks.filter(task=>/推广返现说明/.test(task.t)).length,1,'closing retains its one original post-live referral');
  const award=tasks.find(task=>task.id==='邮寄实物奖励及发放虚拟奖励');
  assert.equal(award.t,'按本期方案发放结营奖励');
  assert.doesNotMatch(award.d,/只发虚拟|是否恢复/);
 }finally{page.close();}
});

test('referral milestones use Wednesdays in weeks 3 and 11 while closing stays after the live ceremony',async()=>{
 const backend=server();await createCamp(backend,[3,8]);const page=await windowFor(backend);
 try{
  const w=page.w;
  for(const scenario of [
   {openDate:'2030-01-06',skips:[],dates:['1/23','3/20','4/7']},
   {openDate:'2030-01-06',skips:[3,8],dates:['1/30','4/3','4/21']},
   {openDate:'2030-01-13',skips:[],dates:['1/30','3/27','4/14']},
  ]){
   const weeks=w.campWeeks({term:1,...scenario});
   const referrals=weeks.flatMap(week=>w.weekTasks(week)
    .filter(task=>task.t==='发布老带新话术'||task.t==='群发优惠券与推广返现说明').map(task=>({week,task})));
   assert.deepEqual(copy(referrals.map(({week})=>week.n)),[3,11,13]);
   referrals.forEach(({week,task},index)=>{
    assert.equal(task.day,index===2?0:3);
    assert.equal(w.fmtS(w.addD(week.mon,(task.day+6)%7)),scenario.dates[index]);
    assert.equal(task.time,index===2?'直播后':undefined);
    assert.equal(w.weekTasks(week).filter(t=>t.t===task.t).length,1);
   });
   const closing=referrals[2];
   assert.equal(w.taskKey(closing.week,closing.task),'week-12|0|群发优惠券与推广返现说明');
  }
  for(const [week,date] of [[3,'1/30'],[11,'4/3']]){
   showPhase(w,1,'week-'+week);
   const task=[...w.document.querySelectorAll('#camp-1 .task')].find(el=>el.querySelector('.task-t').textContent==='发布老带新话术');
   assert.ok(task);
   assert.ok(task.closest('.daygrp').querySelector('.day-hdr').textContent.includes('周三 '+date));
   assert.equal(task.querySelector('.task-time'),null,'no specific posting time was requested');
  }
 }finally{page.close();}
});

test('confirmed weekly handoff and deadlines distinguish teacher access from final question collection',async()=>{
 const backend=server();await createCamp(backend);const page=await windowFor(backend);
 try{
  const w=page.w;showPhase(w,1,'week-2');
  const task=title=>[...w.document.querySelectorAll('#camp-1 .task')].find(el=>el.querySelector('.task-t').textContent.includes(title));
  const handoff=task('问卷创建后立即把入口发给答疑老师');
  assert.ok(handoff);assert.match(handoff.closest('.daygrp').textContent,/周五/);
  assert.equal(handoff.querySelector('.task-time').textContent,'创建后立即');
  const reminder=task('提醒答疑问卷即将截止');
  assert.match(reminder.closest('.daygrp').querySelector('.day-hdr').textContent,/周六/);
  assert.equal(reminder.querySelector('.task-time').textContent,'14:00前');
  const cutoff=task('截止答疑问卷收集，整理新增问题并同步老师');
  assert.equal(cutoff.querySelector('.task-time').textContent,'14:00');
  assert.match(cutoff.textContent,/创建后已交给老师/);
  const live=task('本周讲师直播答疑');
  assert.match(live.closest('.daygrp').querySelector('.day-hdr').textContent,/周六/);
  assert.equal(live.querySelector('.task-time').textContent,'20:00');
  const homework=task('提醒本周作业提交截止');
  assert.match(homework.closest('.daygrp').querySelector('.day-hdr').textContent,/周日/);
  assert.equal(homework.querySelector('.task-time').textContent,'24:00');
  assert.match(homework.textContent,/周一00:00/);
 }finally{page.close();}
});

test('preparation starts one calendar month before opening and preserves teaching dates',async()=>{
 const page=await windowFor(server());
 try{
  const w=page.w;
  for(const [open,start] of [['2030-01-06','2029-12-06'],['2028-03-31','2028-02-29'],['2027-03-31','2027-02-28'],['2030-06-03','2030-05-03']]){
   const weeks=w.campWeeks({term:1,openDate:open,skips:[]}),prep=weeks[0],opening=weeks[1];
   assert.equal(prep.key,'prep');assert.equal(+prep.mon,+w.parseD(start));
   assert.equal(opening.key,'open');assert.equal(opening.mon.getDay(),1);
   assert.equal(+prep.sun,+w.addD(opening.mon,-1));
   assert.equal(w.campPos(weeks,w.addD(prep.mon,-1)),-1);
   assert.equal(w.campPos(weeks,prep.mon),0);
   assert.equal(w.campPos(weeks,prep.sun),0);
   assert.equal(w.campPos(weeks,opening.mon),1);
   const first=weeks.find(week=>week.n===1);
   assert.equal(+first.mon,+w.addD(opening.mon,7));
   assert.equal(w.campPos(weeks,first.mon),weeks.indexOf(first));
   assert.equal(w.campPos(weeks,w.addD(weeks.at(-1).sun,1)),weeks.length);
  }
  const weeks=w.campWeeks({term:1,openDate:'2030-01-06',skips:[3,8]});
  const el=w.document.createElement('div');el.innerHTML=w.timeline([{camp:{term:1},weeks,pos:0}],weeks[0].mon);
  const total=w.dayDiff(weeks[0].mon,w.addD(weeks.at(-1).sun,1));
  const expected=w.dayDiff(weeks[0].mon,w.addD(weeks[0].sun,1))/total*100;
  assert.ok(Math.abs(parseFloat(el.querySelector('.tl-seg.prep').style.width)-expected)<0.002,'long preparation segment uses its actual duration');
 }finally{page.close();}
});

test('today pointer stays on its calendar day across morning and evening',async()=>{
 const page=await windowFor(server());
 try{
  const w=page.w,camps=[{term:3,openDate:'2026-09-27',skips:[]},{term:4,openDate:'2026-11-01',skips:[]}];
  const data=camps.map(camp=>({camp,weeks:w.campWeeks(camp),pos:0}));
  const marker=date=>{
   const el=w.document.createElement('div');el.innerHTML=w.timeline(data,date);
   return el.querySelector('.tl-today');
  };
  const positions=[0,6,11,12,18,23].map(hour=>marker(new w.Date(2026,9,19,hour,30)).style.left);
  assert.equal(new Set(positions).size,1,'changing only the hour must not move today to tomorrow');
  assert.notEqual(marker(new w.Date(2026,9,20,0,30)).style.left,positions[0],'the pointer advances on the next calendar day');
  const start=data[0].weeks[0].mon,end=w.addD(data.at(-1).weeks.at(-1).sun,1);
  assert.equal(marker(w.addD(start,-1)),null);
  assert.equal(marker(end),null);
 }finally{page.close();}
});

test('three per-camp preparation tasks persist from the preparation start until completed',async()=>{
 const backend=server(),camp=await createCamp(backend),page=await windowFor(backend);
 try{
  const w=page.w,weeks=w.campWeeks({term:1,openDate:'2030-01-06',skips:[]}),prep=weeks.find(week=>week.key==='prep');
  assert.ok(prep);showPhase(w,1,'prep');
  const titles=['新建本期入学档案问卷','建立本期小鹅通圈子与课程','建立本期群资料与云空间'];
  assert.equal(w.document.querySelectorAll('#camp-1 .task').length,3);
  for(const title of titles)assert.match(w.document.querySelector('#camp-1').textContent,new RegExp(title));
  assert.equal(w.document.querySelectorAll('#camp-1 a[href*="/share/base/form/"]').length,0,'old enrollment forms are no longer presented as a shared entry');
  const today=date=>{
   const el=w.document.createElement('div');el.innerHTML=w.todaySection([{camp:{term:1},weeks,pos:w.campPos(weeks,date)}],date);return el;
  };
  assert.doesNotMatch(today(w.addD(prep.mon,-1)).textContent,/新建本期入学档案问卷/);
  assert.equal(today(prep.mon).querySelectorAll('.followsec .task').length,3);
  showPhase(w,1,'open');
  assert.equal([...w.document.querySelectorAll('#camp-1 .task-t')].some(el=>titles.some(title=>el.textContent.includes(title))),false,'preparation tasks are not duplicated in opening week');
  showPhase(w,1,'week-1');
  assert.equal(w.document.querySelectorAll('#camp-1 a[href*="/share/base/form/"]').length,0);
  assert.match(w.document.querySelector('#camp-1').textContent,/本期.*入学档案/);
  for(const task of w.weekTasks(prep))await w.__test.cloud.setTask(camp.id+'|'+w.taskKey(prep,task),true);
  assert.doesNotMatch(today(weeks.find(week=>week.n===1).mon).textContent,/新建本期入学档案问卷|建立本期小鹅通圈子与课程|建立本期群资料与云空间/);
 }finally{page.close();}
});

test('exam workflow follows teaching weeks and certificates remain pending only after the exam window',async()=>{
 const backend=server(),camp=await createCamp(backend,[3,7]),page=await windowFor(backend);
 try{
  const w=page.w,weeks=w.campWeeks({term:1,openDate:'2030-01-06',skips:[3,7]});
  const week=n=>weeks.find(item=>item.n===n);
  const task=title=>[...w.document.querySelectorAll('#camp-1 .task')].find(el=>el.querySelector('.task-t').textContent.includes(title));
  assert.equal(w.fmtS(week(5).mon),'2/11');
  w.setView(1,weeks.indexOf(week(5)));assert.ok(task('联系考试老师预约本期考试'));
  w.setView(1,weeks.indexOf(week(6)));
  assert.match(task('发布本期工信部证书报考信息收集问卷').closest('.daygrp').textContent,/周五 2\/22/);
  const exam=week(7);assert.equal(w.fmtS(exam.mon),'3/4');assert.equal(exam.type,'exam');
  w.setView(1,weeks.indexOf(exam));
  const deadline=task('提醒报考信息填写截止');
  assert.match(deadline.closest('.daygrp').querySelector('.day-hdr').textContent,/周一 3\/4/);
  assert.equal(deadline.querySelector('.task-time').textContent,'12:00');
  const transfer=task('向考试工作人员移交本期报名信息');
  assert.match(transfer.closest('.daygrp').querySelector('.day-hdr').textContent,/周二 3\/5/);
  assert.equal(transfer.querySelector('.task-time').textContent,'12:00前');
  assert.match(task('提醒报考学员完成实名认证').closest('.daygrp').querySelector('.day-hdr').textContent,/周四 3\/7/);
  assert.match(task('核对本期考试日期与场次，发送考前提醒').textContent,/本期预约/);
  const cert=w.weekTasks(exam).find(item=>item.follow && /考试证书/.test(item.t));
  assert.ok(cert);assert.equal(cert.day,0);
  const today=(date,pos)=>{
   const el=w.document.createElement('div');el.innerHTML=w.todaySection([{camp:{term:1},weeks,pos}],date);return el;
  };
  assert.doesNotMatch(today(w.addD(exam.mon,5),weeks.indexOf(exam)).textContent,/跟进考试证书/,'certificate followup starts on Sunday, not at the beginning of the week');
  const sunday=today(exam.sun,weeks.indexOf(exam));
  assert.equal([...sunday.querySelectorAll('.task-t')].filter(el=>el.textContent.includes(cert.t)).length,1);
  assert.match(sunday.querySelector('.followsec').textContent,/收到证书后/);
  const later=today(w.addD(weeks.at(-1).sun,7),weeks.length);
  assert.match(later.querySelector('.followsec').textContent,/跟进考试证书/,'unfinished certificate work survives the end of camp');
  const past=w.document.createElement('div');
  past.innerHTML=w.weekPanel(1,weeks,weeks.indexOf(exam),weeks.indexOf(week(8)),week(8).mon);
  const pastCert=[...past.querySelectorAll('.task')].find(el=>el.textContent.includes(cert.t));
  assert.equal(pastCert.querySelector('.task-late'),null,'receipt-dependent followup is not labeled overdue');
  await w.__test.cloud.setTask(camp.id+'|'+w.taskKey(exam,cert),true);
  assert.doesNotMatch(today(w.addD(weeks.at(-1).sun,7),weeks.length).textContent,/跟进考试证书/,'completion removes the followup');
 }finally{page.close();}
});

test('long term tracking uses ceremony anniversaries outside the timeline and persists completion',async()=>{
 const backend=server();const camp=await createCamp(backend);const page=await windowFor(backend);
 try{
  const w=page.w;
  for(const setup of [{openDate:'2030-01-06',skips:[]},{openDate:'2028-10-01',skips:[]},{openDate:'2030-01-06',skips:[3,8]}]){
   const current={term:1,...setup},weeks=w.campWeeks(current),closing=weeks.at(-1).sun;
   assert.equal(weeks.at(-1).n,13);
   const tracking=w.trackingTasks(current);
   assert.equal(tracking.length,3);
   assert.equal(w.dayDiff(closing,tracking[0].w.mon),14);
   assert.ok(tracking.every(entry=>!weeks.some(week=>week.key===entry.w.key)));
   if(setup.openDate==='2028-10-01'){
    assert.equal(w.fmtS(closing),'12/31');assert.equal(w.fmtS(tracking[1].w.mon),'2/28');assert.equal(w.fmtS(tracking[2].w.mon),'4/30');
   }
  }
  const current=w.__test.cloud.state.camps[0],weeks=w.campWeeks(current);
  const entry=w.trackingTasks(current)[0];
  const showToday=date=>{
   const el=w.document.createElement('div');
   el.innerHTML=w.todaySection([{camp:current,weeks,pos:w.campPos(weeks,date)}],date);
   return el;
  };
  assert.doesNotMatch(showToday(w.addD(entry.w.mon,-1)).textContent,new RegExp(entry.task.t));
  const onDate=showToday(entry.w.mon).querySelector('.followsec');
  assert.match(onDate.textContent,/持续跟进 · 完成后勾掉/);
  assert.match(onDate.textContent,new RegExp(entry.task.t));
  assert.match(onDate.textContent,/跟进考试证书/);
  const key=w.taskKey(entry.w,entry.task);
  await w.__test.cloud.setTask(camp.id+'|'+key,true);
  const later=showToday(w.addD(weeks.at(-1).sun,180)).querySelector('.followsec');
  assert.doesNotMatch(later.textContent,new RegExp(entry.task.t));
  assert.match(later.textContent,/初次回访高活跃学员/);
  assert.match(later.textContent,/追踪已回访学员的实践成果/);
  assert.equal(w.document.querySelector('#long-term-tracking'),null);
 }finally{page.close();}
});


test('each camp can edit its exam week and keeps course notices and saved exam tasks aligned',async()=>{
 const backend=server(),camp=await createCamp(backend,[6]),first=await windowFor(backend),second=await windowFor(backend);
 try{
  const w=first.w;
  const key='week-7|4|提醒报考学员完成实名认证';
  await w.__test.cloud.setTask(camp.id+'|'+key,true);
  w.openForm(1);
  assert.equal(w.document.querySelector('#f-exam-week').value,'7');
  w.document.querySelector('#f-exam-week').value='6';
  await w.saveForm(1);
  await second.w.__test.cloud.refresh();
  const saved=second.w.__test.cloud.state.camps[0];
  assert.equal(saved.examWeek,6);
  const weeks=w.campWeeks(saved);
  const course=week=>{
   const phase=weeks.find(p=>p.n===week);
   const task=w.weekTasks(phase).find(t=>t.t==='发布课程及打卡等更新提醒');
   return w.scriptsForTask(phase,task)[0].id;
  };
  assert.deepEqual([1,3,5,6,7,8].map(course),['course-w2','course-w4','course-w6','course-exam','course-w7','course-w8']);
  const exam=weeks.find(p=>p.type==='exam');
  assert.equal(weeks[weeks.indexOf(exam)-1].key,'rest-6');
  assert.ok(w.weekTasks(weeks.find(p=>p.n===4)).some(t=>/联系考试老师/.test(t.t)));
  assert.ok(w.weekTasks(weeks.find(p=>p.n===5)).some(t=>/发布本期工信部/.test(t.t)));
  const identity=w.weekTasks(exam).find(t=>/提醒报考学员完成实名认证/.test(t.t));
  assert.equal(w.taskKey(exam,identity),key);
  assert.equal(second.w.__test.cloud.state.tasks[camp.id+'|'+key].completed,true);
  const later=w.campWeeks({...saved,examWeek:7});
  assert.equal(later.find(p=>p.n===6).courseScript,'course-w7');
  assert.equal(later.find(p=>p.n===7).courseScript,'course-exam');
  assert.equal(later.find(p=>p.n===8).courseScript,'course-w8');
 }finally{first.close();second.close();}
});
