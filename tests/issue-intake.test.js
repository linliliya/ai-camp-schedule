const test = require('node:test');
const assert = require('node:assert/strict');
const intake = require('../issue-intake.js');
test('repository validation accepts personal GitHub repositories and rejects other origins and path injection', () => {
  assert.equal(intake.repository('https://github.com/me/sop-inbox.git'), 'me/sop-inbox');
  assert.equal(intake.repository('me/sop-inbox'), 'me/sop-inbox');
  for (const value of ['javascript:alert(1)', 'https://evil.example/me/repo', 'https://github.com@evil.example/a/b', 'https://github.com:444/a/b', 'a/..', 'a/b/../../x', 'https://github.com/a/b/tree/main']) assert.throws(() => intake.repository(value));
});
test('Chinese Markdown is encoded without executing HTML or losing the full body', () => {
  const body = '# 第三周话术\n\n<script>alert(1)</script>\n课程链接：https://example.com?a=1&b=2';
  const result = intake.compose('someone/inbox', body);
  const url = new URL(result.url);
  assert.equal(url.origin, 'https://github.com');
  assert.equal(url.pathname, '/someone/inbox/issues/new');
  assert.equal(url.searchParams.get('body'), body);
  assert.equal(url.searchParams.get('title'), '第三周话术');
});
test('large notes use explicit copy flow instead of an oversized URL or silently truncated body', () => {
  const result = intake.compose('someone/inbox', '标题\n' + '中文'.repeat(2000));
  assert.equal(result.long, true); assert.ok(result.url.length < 7500);
  assert.equal(new URL(result.url).searchParams.has('body'), false);
  assert.throws(() => intake.compose('someone/inbox', '  \n'));
});
