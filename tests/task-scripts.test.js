const test=require('node:test'),assert=require('node:assert/strict');
const {build}=require('../scripts/export-task-scripts.js'),api=require('../task-scripts.js');
test('all twelve course scripts remain available by course number',()=>{
  const data=build();
  for(let week=1;week<=12;week++){
    const [row]=api.forId('course-w'+week,data);
    assert.match(row.source.file,/课程提醒话术汇总/);
  }
  assert.ok(api.forKey('open|all|向已购学员发送开营安排与预习资料',data).some(row=>row.id==='course-w1'));
  assert.equal(api.forKey('week-11|1|提醒学员启动毕业设计',data)[0].id,'graduation');
});
test('referral weeks and Sunday closing remain distinct and do not promise stale rewards',()=>{
  const data=build();for(const w of[3,11])assert.equal(api.forKey(`week-${w}|3|发布老带新话术`,data)[0].id,w===3?'referral':'referral-w11');
  const closing=api.forKey('week-12|0|群发优惠券与推广返现说明',data)[0];assert.equal(closing.id,'referral-closing');assert.doesNotMatch(closing.body,/300|200|下午好/);assert.ok(api.pending(closing.body).length>0);
});
test('unknown variables are visible while copy text stays separate from notes',()=>{
  const entry=build().scripts.find(s=>s.id==='homework');assert.ok(api.pending(entry.body).includes('【作业截止日期】'));assert.doesNotMatch(entry.body,/shrcnzR8|小橘老师/);
  assert.deepEqual(api.forKey('missing',build()),[]);assert.equal(build().scripts.filter(s=>s.category==='课程发布').length,13);
});
test('exam scripts use current-camp placeholders and attach to existing workflow without changing schedule',()=>{
  const data=build(),exam=data.scripts.filter(s=>s.category==='考试通知');assert.equal(exam.length,5);
  for(const row of exam)assert.doesNotMatch(row.body,/8月31日|10月15日|shrcn|9:00|14:00|第7周周五|下午版：|收到证书后/);
  assert.equal(api.forKey('week-6|5|发布本期工信部证书报考信息收集问卷',data)[0].id,'exam-registration');
  assert.equal(api.forKey('week-7|4|提醒报考学员完成实名认证',data)[0].id,'exam-identity');
});
test('ten growth variants rotate from week two, while collection reminders cover each week',()=>{
  const data=build();assert.equal(data.scripts.length,48);assert.equal(data.scripts.filter(s=>s.category==='成长值公布').length,10);
  assert.deepEqual(api.forKey('week-1|1|公布上周成长值榜单',data),[]);
  for(let w=2;w<=12;w++){const rows=api.forKey(`week-${w}|1|公布上周成长值榜单`,data);assert.equal(rows.length,10);assert.equal(rows[0].id,'growth-'+((w-2)%10+1));for(const row of rows){assert.doesNotMatch(row.body,/本周|^第[1-6]组/m);assert.deepEqual(api.pending(row.body),[]);}}
  for(let w=1;w<=12;w++)assert.equal(api.forKey(`week-${w}|5|开启本周答疑问卷收集`,data)[0].id,'question-collect');
});
test('render uses relative deadlines across camps and leaves links blank',()=>{
  const data=build(),get=id=>data.scripts.find(s=>s.id===id),context={week:6,start:'2026-12-28',examStart:'2027-01-11'};
  const body=api.render(get('homework'),context);assert.match(body.body,/第6周/);assert.match(body.body,/本周日晚24:00/);assert.doesNotMatch(body.body,/https?:|【本周答疑问卷链接】/);assert.ok(body.blankFields.includes('【本周答疑问卷链接】'));
  assert.match(api.render(get('exam-registration'),context).body,/截止时间以本期报名通知为准/);
  assert.match(api.render(get('course-w6'),context).body,/本周六晚 20:00/);
  const identity=api.render(get('exam-identity'),context);assert.match(identity.body,/审核中/);assert.ok(!identity.blankFields.includes('【本期实名认证截止日期及时间】'));assert.doesNotMatch(identity.body,/2026年|2027年|https?:/);
  const morning=api.render(get('exam-morning'),context);assert.ok(morning.blankFields.includes('【上午场开考时间】'));assert.doesNotMatch(morning.body,/9:00|14:00|【上午场/);
});

test('new scripts map to opening, survey, deferral and the ceremony with current-period variables',()=>{
 const data=build(),get=id=>data.scripts.find(row=>row.id===id);
 assert.match(get('graduation').body,/第 13 周周三 23:59/);
 assert.match(get('course-w11').body,/第 13 周周三 23:59/);
 assert.match(get('course-w12').body,/第 13 周周三 23:59/);
 assert.doesNotMatch(get('course-w12').body,/本周务必完成毕业项目提交|本周日 24:00/);
 assert.equal(api.forKey('week-12|1|群发私聊学员成果调研问卷，收集成果与反馈',data)[0].id,'outcome-survey');
 assert.equal(get('outcome-survey').audience,'学员群');
 assert.match(api.render(get('outcome-survey')).body,/shrcnhnPCnecliH1Qaj7XixIP4f/);
 assert.match(api.render(get('outcome-followup')).body,/shrcnhnPCnecliH1Qaj7XixIP4f/);
 assert.match(get('graduation-collect').body,/第13周周三晚上23:59/);
 assert.match(get('guest-invite').body,/5分钟/);
 assert.equal(api.forKey('week-12|5|发送嘉宾邀请并确认展示安排',data)[0].id,'guest-invite');
 assert.equal(api.forKey('week-6|all|核对考试意向并标记延期学员',data)[0].id,'exam-deferral');
 assert.match(get('referral-closing').body,/退款.*返现需退回/);
 for(const row of data.scripts){
  const rendered=api.render(row,{week:12,start:'2026-12-28',examStart:'2027-01-11'});
  assert.doesNotMatch(rendered.body,/\d{4}年\d+月\d+日|11 ?月 ?29 ?日|11月1日|19:30|16:00|7 位|第5位/);
 }
});
