const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
function exportData() {
  const context = vm.createContext({ window: {} });
  vm.runInContext(fs.readFileSync(path.join(root, 'data.js'), 'utf8') + '\n' +
    'globalThis.sop = {links:LINKS,prep:SOP_PREP,open:SOP_OPEN,rest:SOP_REST,regular:SOP_REGULAR,extras:SOP_EXTRAS,exam:SOP_EXAM,closing:SOP_CLOSING,tracking:SOP_TRACKING};', context);
  vm.runInContext(fs.readFileSync(path.join(root, 'config.js'), 'utf8'), context);
  return JSON.parse(JSON.stringify({ schema: 1, config: context.window.SCHEDULE_CONFIG, ...context.sop }));
}
const target = path.join(root, 'android/app/src/main/assets/schedule.json');
if (require.main === module) {
  const child = require('node:child_process').spawnSync(process.execPath, [path.join(__dirname,'export-task-scripts.js'), ...(process.argv.includes('--check')?['--check']:[])], {stdio:'inherit'});
  if(child.status!==0) process.exit(child.status||1);
  const text = JSON.stringify(exportData(), null, 2) + '\n';
  if (process.argv.includes('--check')) {
    if (!fs.existsSync(target) || fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n') !== text) {
      console.error('Android SOP content is stale. Run node scripts/export-android-data.js');
      process.exitCode = 1;
    } else console.log('Android SOP content matches data.js and config.js.');
  } else {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, text);
    console.log('Exported Android SOP content.');
  }
}
module.exports = { exportData, target };
