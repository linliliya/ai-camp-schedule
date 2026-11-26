import test, { before, after, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright-core';

const app = fileURLToPath(new URL('..', import.meta.url));
const stateRoot = resolve(app, '.state');
const original = '# 入库流程验证\n\n## 课程更新\n\n第3周和第11周周三发布话术，结营仍在直播后。\n\n> 请核对本期课程与问卷链接。\n\n| 周次 | 安排 |\n| --- | --- |\n| 3 | 周三 |\n\n![资料图片](图片和附件/test.png)\n';
let directory, root, server, browser, base, context, page;
const docURL = () => '/api/document?path=' + encodeURIComponent('入库流程验证.md');
async function waitRead() { await page.waitForFunction(() => document.querySelector('#document-title')?.textContent === '入库流程验证' && !document.querySelector('#reading-area').hidden); }
async function sourceEdit(value) { await page.locator('#edit-doc').click(); await page.locator('#source-toggle').click(); await page.locator('#markdown-source').fill(value); }
async function save() { const response = page.waitForResponse(r => r.url().endsWith('/api/save') && r.request().method() === 'POST'); await page.locator('#save-doc').click(); return response; }

before(async () => {
  mkdirSync(stateRoot, { recursive: true }); directory = mkdtempSync(join(stateRoot, 'ui-check-'));
  assert.ok(resolve(directory).startsWith(stateRoot + sep));
  root = join(directory, 'docs'); mkdirSync(join(root, '图片和附件'), { recursive: true });
  writeFileSync(join(root, '图片和附件', 'test.png'), Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64'));
  writeFileSync(join(root, '来源导航.md'), '# 来源导航\n\n[打开对应原文](入库流程验证.md#L5)\n');
  server = spawn('python', ['-X', 'utf8', join(app, 'server.py'), '--port', '0', '--root', root, '--state', join(directory, 'history')], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  base = await new Promise((resolveReady, reject) => { const timer = setTimeout(() => reject(new Error('Fixture server did not start')), 10000); server.once('error', reject); server.stdout.on('data', data => { const match = data.toString().match(/http:\/\/127\.0\.0\.1:\d+/); if (match) { clearTimeout(timer); resolveReady(match[0]); } }); });
  const chrome = [process.env.LOCAL_SOP_CHROME, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p => p && existsSync(p));
  assert.ok(chrome, 'Install Chrome/Edge or set LOCAL_SOP_CHROME');
  browser = await chromium.launch({ executablePath: chrome, headless: true });
}, { timeout: 20000 });
after(async () => { await browser?.close(); server?.kill(); if (directory) { assert.ok(resolve(directory).startsWith(stateRoot + sep)); await new Promise(r => setTimeout(r, 300)); rmSync(directory, { recursive: true, force: true }); } });
beforeEach(async () => { writeFileSync(join(root, '入库流程验证.md'), original); context = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); page = await context.newPage(); await page.goto(base); await waitRead(); });
afterEach(async () => { await context?.close(); });

test('reader renders headings, table and local attachment; Chinese search opens the document', async () => {
  assert.equal(await page.locator('#editor table').count(), 1);
  assert.equal(await page.locator('#editor blockquote').count(), 1);
  await page.waitForFunction(() => [...document.querySelectorAll('#editor img')].every(img => img.complete && img.naturalWidth > 0));
  await page.keyboard.press('Control+k'); await page.locator('#search-input').fill('直播后');
  await page.locator('.search-result').first().waitFor(); await page.keyboard.press('Enter');
  await page.waitForFunction(() => !document.querySelector('#search-dialog').open);
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), original);
});

test('Markdown editing saves to disk and survives page reload', async () => {
  const updated = original + '\n新的确认内容。\n'; await sourceEdit(updated);
  assert.equal((await save()).status(), 200); await page.locator('#edit-doc').waitFor({ state: 'visible' });
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), updated);
  await page.reload(); await waitRead(); assert.ok((await page.locator('#editor').textContent()).includes('新的确认内容。'));
});

test('outline navigation waits for delayed local images so the requested heading stays in view', async () => {
  writeFileSync(join(root, '入库流程验证.md'), '# 入库流程验证\n\n![资料图片](图片和附件/test.png)\n\n## 目标章节\n\n' + '内容段落。\n\n'.repeat(100));
  await page.route('**/api/asset?*', async route => { await new Promise(resolve => setTimeout(resolve, 400)); await route.continue(); });
  await page.reload(); await waitRead(); await page.addStyleTag({ content: '.tiptap img { width: 500px; }' });
  await page.locator('#outline-links a').filter({ hasText: '目标章节' }).click();
  await page.waitForFunction(() => {
    const heading = [...document.querySelectorAll('#editor h2')].find(node => node.textContent === '目标章节');
    const top = heading?.getBoundingClientRect().top;
    return [...document.querySelectorAll('#editor img')].every(image => image.complete && image.naturalWidth > 0) && top >= 60 && top < 110;
  });
});

test('local source links navigate inside the knowledge base instead of downloading Markdown as an attachment', async () => {
  await page.locator('[data-path="来源导航.md"]').click();
  await page.waitForFunction(() => document.querySelector('#document-title')?.textContent === '来源导航');
  await page.getByRole('link', { name: '打开对应原文' }).click();
  await waitRead();
  assert.ok(page.url().includes(encodeURIComponent('入库流程验证.md')));
});

test('rich text editing retains table, quote and relative image link', async () => {
  await page.locator('#edit-doc').click(); await page.locator('.tiptap').click();
  await page.keyboard.press('Control+End'); await page.keyboard.press('Enter'); await page.keyboard.type('新增一段执行说明。');
  assert.equal((await save()).status(), 200); await page.locator('#edit-doc').waitFor({ state: 'visible' });
  const text = readFileSync(join(root, '入库流程验证.md'), 'utf8');
  assert.ok(text.includes('新增一段执行说明。')); assert.ok(text.includes('|')); assert.ok(text.includes('>')); assert.ok(text.includes('图片和附件/test.png')); assert.ok(!text.includes('/api/asset'));
});

test('failed save preserves editor input and recovers the local draft after reload', async () => {
  const draft = original + '\n不能丢失的草稿。\n';
  await page.route('**/api/save', route => route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ error: '保存失败测试' }) }));
  await sourceEdit(draft); assert.equal((await save()).status(), 500);
  assert.equal(await page.locator('#markdown-source').inputValue(), draft);
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), original);
  await page.reload(); await waitRead(); await page.getByRole('button', { name: '恢复草稿', exact: true }).click();
  assert.ok((await page.locator('#editor').textContent()).includes('不能丢失的草稿。'));
});

test('external AI edit produces a conflict and never overwrites the changed file', async () => {
  const draft = original + '\n我正在编辑。\n'; await sourceEdit(draft);
  const external = '# AI 更新\n\n文件中的新规则。\n'; writeFileSync(join(root, '入库流程验证.md'), external);
  assert.equal((await save()).status(), 409); await page.locator('#conflict-dialog').waitFor({ state: 'visible' });
  assert.equal(await page.locator('#my-draft').inputValue(), draft);
  assert.equal(await page.locator('#disk-content').inputValue(), external);
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), external);
});

test('connection failure leaves the editable draft available with a local restart instruction', async () => {
  const draft = original + '\n服务断开时保留的草稿。'; await sourceEdit(draft);
  await page.route('**/api/save', route => route.abort('connectionfailed'));
  await page.locator('#save-doc').click();
  await page.waitForFunction(() => document.querySelector('#notice')?.textContent.includes('重新启动'));
  assert.equal(await page.locator('#markdown-source').inputValue(), draft);
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), original);
});

test('finishing a save never navigates away from another document the user has opened', async () => {
  let release, entered;
  const gate = new Promise(resolve => { release = resolve; }); const started = new Promise(resolve => { entered = resolve; });
  await page.route('**/api/save', async route => { entered(); await gate; await route.continue(); });
  const updated = original + '\n切换文档时保存。'; await sourceEdit(updated);
  await page.locator('#save-doc').click(); await started;
  page.once('dialog', dialog => dialog.accept()); await page.locator('[data-path="来源导航.md"]').click();
  await page.waitForFunction(() => document.querySelector('#document-title')?.textContent === '来源导航');
  const saved = page.waitForResponse(r => r.url().endsWith('/api/save')); release(); assert.equal((await saved).status(), 200);
  await page.waitForFunction(() => !document.querySelector('#edit-doc').disabled);
  assert.equal(await page.locator('#document-title').textContent(), '来源导航');
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), updated);
});

test('history preview restores a previous version through the real interface', async () => {
  await sourceEdit(original + '\n历史恢复测试。\n'); assert.equal((await save()).status(), 200);
  await page.locator('#edit-doc').waitFor({ state: 'visible' }); await page.locator('#more-open').click(); await page.locator('#history-open').click();
  await page.locator('#history-items [data-revision]').first().click(); await page.locator('#revision-panel').waitFor({ state: 'visible' });
  page.once('dialog', dialog => dialog.accept());
  const restored = page.waitForResponse(r => r.url().endsWith('/api/restore')); await page.locator('#restore-version').click(); assert.equal((await restored).status(), 200);
  await page.waitForFunction(() => !document.querySelector('#history-dialog').open);
  assert.equal(readFileSync(join(root, '入库流程验证.md'), 'utf8'), original);
});

test('mobile navigation creates a real inbox document without horizontal overflow', async () => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.mobile-menu').click();
  await page.locator('[data-scope="收件箱/"]').click(); await page.locator('#collection-new').click();
  await page.locator('#new-title').fill('手机收件箱记录'); await page.locator('#new-form button[type=submit]').click();
  await page.locator('#source-toggle').waitFor({ state: 'visible' }); await page.locator('#source-toggle').click();
  const body = '# 手机收件箱记录\n\n请 AI 帮我整理的内容。'; await page.locator('#markdown-source').fill(body);
  assert.equal((await save()).status(), 200); await page.locator('#edit-doc').waitFor({ state: 'visible' });
  assert.equal(readFileSync(join(root, '收件箱', '手机收件箱记录.md'), 'utf8'), body);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
});
