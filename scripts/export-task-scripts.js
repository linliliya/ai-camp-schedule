const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname,'..');
const coursePath='content-sources/AI编程班课程提醒话术汇总.md';
const studyPath='AI编程SOP/AI编程班主任督学SOP/AI编程班主任督学SOP.md';
const workPath='AI编程SOP/AI编程班主任工作SOP/AI编程班主任工作SOP.md';
const rulesPath='AI编程SOP/维护与入库/本地SOP维护规范.md';
const read=p=>fs.readFileSync(path.join(root,p),'utf8').replace(/\r\n/g,'\n');
const source=p=>({file:p,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex')});
const plain=text=>text.replace(/\*\*(.*?)\*\*/gs,'$1').replace(/\\([.+_*[\]{}()\-!>])/g,'$1').trim();
function readCourseScripts() {
  const course=read(coursePath), parts=[...course.matchAll(/^## (.+)$/gm)], scripts=[];
  parts.forEach((match,index)=>{
    const section=match[1], raw=course.slice(match.index+match[0].length,parts[index+1]?.index??course.length).replace(/\n---\s*$/,'').trim();
    const week=section.match(/第\s*(\d+)\s*周课程更新/), exam=section.includes('考试周'), graduation=section.includes('毕业设计单独提醒');
    if(!week&&!exam&&!graduation)throw Error('未识别的课程话术章节：'+section);
    const id=week?'course-w'+week[1]:exam?'course-exam':'graduation';
    const notes=[];
    if(id==='course-exam')notes.push('仅用于本期确定的考试流程；考试日期与场次以个人预约信息为准。');
    if(id==='course-w12')notes.push('毕业作品最终收集截止为第13周周三23:59；典礼在第13周周日，具体时刻按本期通知。');
    scripts.push({id,title:section.replace(/^T04-\d+\s*/,''),category:graduation?'毕业设计':'课程发布',audience:'学员群',body:plain(raw),notes,source:{...source(coursePath),section}});
  });
  if(scripts.length!==14)throw Error('课程话术应包含 14 条，实际 '+scripts.length);
  return scripts;
}

function liveReminderBody(course) {
  const liveLine=course.body.split('\n').find(line=>line.includes('本周六晚 20:00'));
  if(!liveLine)throw Error('未找到已确认的直播提醒原句');
  return liveLine+'\n\n本周直播入口：【本周直播链接】';
}

function appendSopScripts(scripts) {
  const study=read(studyPath), marker='- 每周作业及打卡提醒', start=study.indexOf(marker), end=study.indexOf('- 问卷截止提醒',start);
  if(start<0||end<0)throw Error('未找到作业提醒原文');
  const homework=plain(study.slice(start+marker.length,end).split('\n').map(x=>x.replace(/^ {4}/,'')).join('\n').replace(/https:\/\/n6fo0mbcz6[^\s]+/g,'【本周答疑问卷链接】').replace(/第X周/g,'第【周次】周').replace(/X月X日/g,'【作业截止日期】').replace(/小橘老师/g,'【答疑讲师姓名】').replace(/\n[ \t]*\n(?:[ \t]*\n)+/g,'\n\n'));
  scripts.push({id:'homework',title:'本周作业与答疑问卷提醒',category:'作业 / 督学',audience:'学员群',body:homework,
    notes:['原文的周次、日期、讲师和往期问卷入口已改成待填项；未带入学员个人信息。'],source:{...source(studyPath),section:'每周作业及打卡提醒'}});
  scripts.push({id:'live',title:'周六答疑直播提醒',category:'直播答疑',audience:'学员群',body:liveReminderBody(scripts.find(s=>s.id==='course-w1')),notes:['直播提醒原句来自第 1 周课程话术，入口留待填写；不沿用旧 SOP 的“十点 / 八点”错字。'],source:{...source(coursePath),section:'T04-001 第 1 周课程更新'}});
  scripts.push({id:'question-deadline',title:'答疑问卷截止提醒',category:'直播答疑',audience:'学员群',body:'同学们好，本周答疑问卷将于周六 14:00 截止。\n\n有希望在答疑直播中讨论的问题，请及时填写：【本周答疑问卷链接】\n\n周六晚 20:00 的答疑直播见！',notes:['根据已确认的时间规则整理；旧 SOP 这段问卷提醒时间有冲突，未照搬。'],source:{...source(rulesPath),section:'当前已确认的时间规则'}});
  const questionStart=homework.indexOf('本周答疑问卷开启收集');
  if(questionStart<0)throw Error('未找到问卷收集原句');
  scripts.push({id:'question-collect',title:'本周答疑问卷收集通知',category:'直播答疑',audience:'学员群',body:homework.slice(questionStart),notes:['截取作业提醒中的问卷收集原文；本周入口和讲师姓名需补齐。'],source:{...source(studyPath),section:'每周作业及打卡提醒 / 答疑问卷收集'}});
  const work=read(workPath);
  const between=(start,end)=>{const a=work.indexOf(start),b=work.indexOf(end,a+start.length);if(a<0||b<0)throw Error('未找到考试原文：'+start);return plain(work.slice(a,b).replace(/^> ?/gm,'').replace(/\n[ \t]*>[ \t]*/g,'\n').replace(/\n\s*\n(?:\s*\n)+/g,'\n\n')).replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g,'$1：$2').trim();};
  const examNotes=['原文的历史日期、报名表、考试时刻及测评岗位改为本期待填项；发送前核对本期预约，不改变排期。'];
  const examRows=[
    ['exam-registration','报考信息收集',between('📢Vibe Coding工信部证书考试信息收集','# 开营第7周').replace('8月31日中午12点（一般是周一中午）','【报名截止日期】中午12点').replace(/https:\/\/n6fo0mbcz6\.feishu\.cn\/share\/base\/form\/[^\s]+/g,'【本期报考信息表单】')],
    ['exam-deadline','报名截止提醒',between('📢 Vibe Coding工信部证书考试报名提醒','**周二**').replace(/https:\/\/n6fo0mbcz6\.feishu\.cn\/share\/base\/form\/[^\s]+/g,'【本期报考信息表单】')],
    ['exam-identity','实名认证提醒',between('📢 工信部考试｜实名认证提醒','第7周周五').replace('本周四10月15日，23:00 前','【本期实名认证截止日期及时间】')],
    ['exam-morning','上午场考试提醒',between('📢 今日考试提醒','下午版：').replace(/9:00/g,'【上午场开考时间】').replace(/9点考试/g,'【上午场开考时间】考试').replace(/《智能体应用工程师—Vibe Coding 软件开发》/g,'【本期测评岗位】')],
    ['exam-afternoon','下午场考试提醒',between('📢 下午场考试提醒','- 收到证书后').replace('14:00','【下午场开考时间】').replace(/《智能体应用工程师—Vibe Coding 软件开发》/g,'【本期测评岗位】')],
  ];
  for(const [id,title,body] of examRows)scripts.push({id,title,category:'考试通知',audience:'报考学员',body,notes:examNotes,source:{...source(workPath),section:'工信部证书考试 / '+title}});
  const growthStart=study.indexOf('## 成长值榜单话术模版'),growthEnd=study.indexOf('## 结果呈现与学习闭环',growthStart),growth=study.slice(growthStart,growthEnd);
  const growthParts=[...growth.matchAll(/^(?:([1-4])\.[ \t]*|第([1-6])组([^\n]*))$/gm)];
  if(growthParts.length!==10)throw Error('成长值应有 10 套原文，实际 '+growthParts.length);
  growthParts.forEach((match,i)=>{
    const id='growth-'+(i+1);
    const raw=growth.slice(match.index+match[0].length,growthParts[i+1]?.index??growth.length).replace(/\n---\s*$/,'').trim();
    scripts.push({id,title:match[1]?'榜单公布 · 版本 '+match[1]:'榜单公布 · '+(match[3]?.replace(/[（）]/g,'').trim()||'积极鼓励型'),category:'成长值公布',audience:'学员群',body:plain(raw).replace(/本周/g,'上周'),notes:['周一公布上周榜单，发送时配上本期实际海报；不自动生成学员名单或分数。原有版本的“本周”统一为“上周”，SOP 原文件未改。'],source:{...source(studyPath),section:'成长值榜单话术模版 / '+match[0].trim()}});
  });
  // Extract copyable bodies from the confirmed operational SOP.
  function addFromSection(id, title, category, audience, section, notes = []) {
    const heading = work.indexOf(section);
    if (heading < 0) throw Error('未找到话术章节：' + section);
    const bodyStart = work.indexOf('```text\n', heading);
    const bodyEnd = work.indexOf('\n```', bodyStart + 8);
    if (bodyStart < 0 || bodyEnd < 0) throw Error('未找到话术正文：' + section);
    scripts.push({id, title, category, audience, body: work.slice(bodyStart + 8, bodyEnd).trim(), notes,
      source: {...source(workPath), section}});
  }
  addFromSection('referral', '第3周老带新推荐', '老带新', '学员群', '### 第3周周三');
  addFromSection('referral-w11', '第11周老带新推荐', '老带新', '学员群', '### 第11周周三');
  addFromSection('referral-closing', '结营后福利与推荐说明', '老带新', '学员群', '### 结营直播后',
    ['第13周结营直播后使用。500元券、好友减200元、推荐返300元为当前参考，金额、有效期与条件按本期填写。退款后返现需退回。']);
  addFromSection('opening-notice', '开营安排与预习资料通知', '开营通知', '已购学员私聊', '### 开营直播前');
  addFromSection('graduation-collect', '毕业作品收集与展示意愿征集', '毕业设计', '学员群', '发布各小队毕业作品与展示意愿问卷，询问作品收集进度。',
    ['第12周周一发布，最终截止为第13周周三23:59。']);
  addFromSection('outcome-survey', '学习成果调研群通知', '成果调研', '学员群', '### 第13周成果调研');
  addFromSection('graduation-monday', '周一毕业作品提交提醒', '毕业设计', '学员群', '### 周一\n\n再次提醒学员提交毕业作品');
  addFromSection('graduation-wednesday', '周三毕业作品截止提醒', '毕业设计', '学员群', '### 周三\n\n再次提醒学员提交毕业作品');
  addFromSection('guest-invite', '结营展示嘉宾邀请', '结营典礼', '展示学员私聊', '### 周五\n\n逐一发送嘉宾邀请');
  addFromSection('rehearsal', '测试直播与彩排通知', '结营典礼', '展示嘉宾', '### 周五或周六');
  addFromSection('closing-preview', '结营典礼群预告', '结营典礼', '学员群', '### 周日\n\n上午发送本期典礼预告');
  addFromSection('outcome-followup', '结营两周后的成果调研', '长期追踪', '学员群', '### 结营后两周群问卷');
  addFromSection('followup-two-months', '两个月初次回访', '长期追踪', '高活跃学员私聊', '### 结营后2个月初次回访');
  addFromSection('followup-four-months', '四个月成果追踪', '长期追踪', '之前回访过的学员私聊', '### 结营后4个月成果追踪');
  const deferral = work.match(/参考话术：\s*\n\s*> (没关系[^\n]+)/);
  if (!deferral) throw Error('未找到考试延期回复');
  scripts.push({id: 'exam-deferral', title: '本期无法参加考试的回复', category: '考试延期', audience: '延期学员私聊',
    body: plain(deferral[1]), notes: ['及时打“等待下一期考试”等标签。下次报名覆盖当前期和上一期群，上一期群发普通消息。'],
    source: {...source(workPath), section: '补考SOP'}});
  const identity = scripts.find(row => row.id === 'exam-identity');
  identity.body = identity.body.replace('⏰ 实名认证截止时间', '提交审核后，请将页面滑到最上方，确认出现“审核中”，核对是否已成功提交。\n\n⏰ 实名认证截止时间');
}

function build() {
  const scripts = readCourseScripts();
  const localSopFiles = [studyPath, workPath, rulesPath];
  if (localSopFiles.every(file => fs.existsSync(path.join(root, file)))) {
    appendSopScripts(scripts);
  } else {
    // Public checkouts use the published runtime copy for scripts sourced from the local SOP.
    const courseIds = new Set(scripts.map(row => row.id));
    const { scripts: published } = JSON.parse(read('task-scripts.json'));
    for (const row of published) {
      if (courseIds.has(row.id)) continue;
      scripts.push(row.id === 'live' ? {
        ...row, body: liveReminderBody(scripts.find(course => course.id === 'course-w1')),
        source: { ...row.source, ...source(coursePath) },
      } : row);
    }
  }
  const growthIds = scripts.filter(row => row.category === '成长值公布').map(row => row.id);
  const context=vm.createContext({});vm.runInContext(read('data.js')+';globalThis.source={open:SOP_OPEN,regular:SOP_REGULAR,extras:SOP_EXTRAS,exam:SOP_EXAM,closing:SOP_CLOSING,tracking:SOP_TRACKING};',context);
  const mapping={};
  const put=(week,t,ids)=>{const key=`${t.idWeek||'week-'+week}|${t.idDay??t.day}|${t.id||t.t}`;mapping[key]=[...(mapping[key]||[]),...ids];};
  for(let w=1;w<=12;w++){
    for(const t of context.source.regular){
      const key=t.id||t.t;
      if(['提醒本周作业截止时间','提醒本周作业提交截止'].includes(key))put(w,t,['homework']);
      if(key==='本周讲师直播答疑')put(w,t,['live']);
      if(key==='提醒答疑问卷即将截止')put(w,t,['question-deadline']);
      if(key==='开启本周答疑问卷收集')put(w,t,['question-collect']);
      if(key==='公布上周成长值榜单'&&w>1){const offset=(w-2)%growthIds.length;put(w,t,[...growthIds.slice(offset),...growthIds.slice(0,offset)]);}
      if(key==='日常：督学 / 答疑 / 辅助讲师直播答疑')put(w,t,['homework','live']);
    }
    for(const t of context.source.extras[w]||[]){
      if(t.t==='发布老带新话术')put(w,t,[w===11?'referral-w11':'referral']);if(t.t==='提醒学员启动毕业设计')put(w,t,['graduation']);
      if(t.t==='群内发布成果调研问卷，收集成果与反馈')put(w,t,['outcome-survey']);
      if(t.t==='发送毕业作品提交与展示意愿问卷')put(w,t,['graduation-collect']);

    }

  }
  const examMapping={'核对考试意向并标记延期学员':['exam-deferral'],'发布本期工信部证书报考信息收集问卷':['exam-registration'],'提醒报考信息填写截止':['exam-deadline'],'提醒报考学员完成实名认证':['exam-identity'],'核对本期考试日期与场次，发送考前提醒':['exam-morning','exam-afternoon'],'提醒尚未参加本期考试的学员':['exam-morning','exam-afternoon']};
  for (const task of Object.values(context.source.exam).flat()) {
    if (examMapping[task.t]) put(0, task, examMapping[task.t]);
  }
  for (const task of context.source.open) {
    if (task.t === '向已购学员发送开营安排与预习资料') {
      mapping[`open|${task.day}|${task.t}`] = ['opening-notice', 'course-w1'];
    }
  }
  const closingMapping = {
    '群内发布成果调研问卷，收集成果与反馈': 'outcome-survey',
    '再次提醒学员提交毕业作品': {1: 'graduation-monday', 3: 'graduation-wednesday'},
    '截止收集毕业作品与展示意愿': 'graduation-wednesday',
    '发送嘉宾邀请并确认展示安排': 'guest-invite',
    '测试直播并组织嘉宾彩排': 'rehearsal',
    '发送结营典礼预告': 'closing-preview',
    '群发优惠券与推广返现说明': 'referral-closing'
  };
  for (const task of context.source.closing) {
    const entry = closingMapping[task.t];
    const id = typeof entry === 'object' ? entry[task.day] : entry;
    if (id) put(13, task, [id]);
  }
  const trackingIds = ['outcome-followup', 'followup-two-months', 'followup-four-months'];
  context.source.tracking.forEach((task, index) => put(13, task, [trackingIds[index]]));
  for(const ids of Object.values(mapping))for(const id of ids)if(!scripts.some(s=>s.id===id))throw Error('未找到话术 '+id);
  return {schema:1,version:'2026-11-19.2',scripts,mapping};
}
const targets=['task-scripts.json','android/app/src/main/assets/task-scripts.json','task-scripts-data.js'];
if(require.main===module){
  const value=build(),json=JSON.stringify(value,null,2)+'\n';
  targets.forEach(target=>{const expected=target.endsWith('.js')?'window.TASK_SCRIPTS_DATA='+JSON.stringify(value)+';\n':json;const full=path.join(root,target);if(process.argv.includes('--check')){if(!fs.existsSync(full)||fs.readFileSync(full,'utf8').replace(/\r\n/g,'\n')!==expected)throw Error('话术资源需要更新：'+target);}else fs.writeFileSync(full,expected);});
  console.log(`Task scripts: ${value.scripts.length} entries, ${Object.keys(value.mapping).length} stable task keys; ${process.argv.includes('--check')?'verified':'exported'}.`);
}
module.exports={build,targets};
