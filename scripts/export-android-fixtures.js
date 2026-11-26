// Derive parity fixtures from the actual website functions, not a second JS implementation.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const helpers = html.slice(html.indexOf('const DAY_MS ='), html.indexOf('// Team records'));
const model = html.slice(html.indexOf('function campWeeks(camp)'), html.indexOf('function pendingSection()'));
const key = html.match(/function taskKey\(w, task\)\{[^\n]+/)[0];
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(root, 'data.js'), 'utf8') + '\n' + helpers + '\n' + model + '\n' + key + `
function iso(d) {return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');}
globalThis.fixture = [];
for (const openDate of ['2026-10-25','2027-03-31','2028-03-31','2030-01-06','2026-10-19','2026-10-20']) {
  for (const skips of [[],[1],[7,12],[1,2,3,4,5,6,7,8,9,10,11,12]]) {
    for (const examWeek of [1,2,6,7,12]) {
      const camp = {term:1,openDate,skips,examWeek};
      fixture.push({openDate,skips,examWeek,phases:campWeeks(camp).map(w=>({key:w.key,type:w.type,start:iso(w.mon),end:iso(w.sun),courseScript:w.courseScript,keys:weekTasks(w).map(t=>taskKey(w,t))}))});
    }
  }
}`, context);
const target = process.argv[2];
if (!target) throw new Error('Pass a destination for generated web parity fixtures.');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, JSON.stringify(context.fixture));
console.log(`Generated ${context.fixture.length} website model parity cases.`);
