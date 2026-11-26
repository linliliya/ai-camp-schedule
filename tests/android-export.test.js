const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { exportData, target } = require('../scripts/export-android-data');

test('Android packaged content exactly matches website SOP and public config', () => {
  const data = exportData();
  assert.deepEqual(JSON.parse(fs.readFileSync(target, 'utf8')), data);
  assert.equal(data.config.program, 'ai');
  assert.ok(data.config.key.startsWith('sb_publishable_'));
  assert.equal(data.prep.length, 3);
  assert.ok(data.prep.every(t => t.idWeek === 'open' && t.follow));
  for (const task of [...data.prep, ...data.open, ...data.rest, ...data.regular, ...Object.values(data.extras).flat(), ...Object.values(data.exam).flat(), ...data.closing, ...data.tracking]) {
    for (const key of task.links || (task.link ? [task.link] : [])) assert.ok(data.links[key], key);
  }
});
