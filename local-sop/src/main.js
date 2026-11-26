import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { Markdown } from '@tiptap/markdown';
import { TableKit } from '@tiptap/extension-table';
import Image from '@tiptap/extension-image';
import { createIcons, BookOpen, Search, Plus, ChevronRight, ChevronDown, FileText, Inbox, FolderOpen, Menu, X, Pencil, Check, Copy, Download, History, ArrowLeft, Bold, Italic, List, ListOrdered, Quote, Code, Link, Table, Undo2, Redo2, Save, MoreHorizontal, PanelLeftClose, FileCode2, AlertCircle, RotateCcw } from 'lucide';
import './style.css';

const icons = { BookOpen, Search, Plus, ChevronRight, ChevronDown, FileText, Inbox, FolderOpen, Menu, X, Pencil, Check, Copy, Download, History, ArrowLeft, Bold, Italic, List, ListOrdered, Quote, Code, Link, Table, Undo2, Redo2, Save, MoreHorizontal, PanelLeftClose, FileCode2, AlertCircle, RotateCcw };
const $ = selector => document.querySelector(selector);
const icon = name => `<i data-lucide="${name}" aria-hidden="true"></i>`;
const esc = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const state = { documents: [], doc: null, editor: null, editing: false, source: false, dirty: false, busy: false, scope: '', load: 0, editVersion: '', baseRich: '', history: [] };
const time = value => new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(value * 1000);
const paintIcons = () => createIcons({ icons, attrs: { 'stroke-width': 1.7 } });

$('#app').innerHTML = `
  <aside class="sidebar" id="sidebar" aria-label="知识库目录">
    <div class="sidebar-top"><button class="book-brand" id="home"><span class="book-letter">A</span><span>AI 编程训练营<small>本地 SOP 知识库</small></span></button><button class="icon-button sidebar-close" aria-label="收起目录">${icon('panel-left-close')}</button></div>
    <button class="search-trigger" id="search-open">${icon('search')}<span>搜索文档或话术</span><kbd>Ctrl K</kbd></button>
    <nav class="collections" aria-label="文档分类"><button data-scope="" class="active">${icon('book-open')}全部 SOP</button><button data-scope="收件箱/">${icon('inbox')}收件箱<span id="inbox-count"></span></button><button data-scope="维护与入库/">${icon('folder-open')}维护与入库</button></nav>
    <div class="tree-heading"><span>目录</span><button class="icon-button" id="new-doc" title="新建文档" aria-label="新建文档">${icon('plus')}</button></div>
    <nav id="document-tree" class="document-tree" aria-label="SOP 文档"></nav>
    <div class="sidebar-footer"><span class="local-dot"></span>保存在这台电脑<span id="doc-count"></span></div>
  </aside>
  <button class="sidebar-scrim" id="sidebar-scrim" aria-label="关闭目录"></button>
  <main class="workspace">
    <header class="topbar"><div class="breadcrumbs"><button class="icon-button mobile-menu" aria-label="打开目录">${icon('menu')}</button><span class="crumb-root">本地知识库</span>${icon('chevron-right')}<span id="crumb-title">SOP</span></div><div class="top-actions"><span id="save-status" class="save-status">${icon('check')}本地文档</span><button class="button" id="copy-doc" title="复制带格式的正文，可手动粘贴到飞书">${icon('copy')}<span>复制正文</span></button><button class="button primary" id="edit-doc">${icon('pencil')}<span>编辑</span></button><button class="icon-button" id="more-open" aria-label="更多文档操作">${icon('more-horizontal')}</button></div></header>
    <div id="notice" class="notice" hidden></div>
    <div id="edit-toolbar" class="edit-toolbar" hidden><div class="format-actions"><select id="block-type" aria-label="段落格式"><option value="paragraph">正文</option><option value="1">标题 1</option><option value="2">标题 2</option><option value="3">标题 3</option></select><span class="tool-divider"></span><button class="icon-button" data-command="bold" aria-label="加粗">${icon('bold')}</button><button class="icon-button" data-command="italic" aria-label="斜体">${icon('italic')}</button><button class="icon-button" data-command="bulletList" aria-label="无序列表">${icon('list')}</button><button class="icon-button" data-command="orderedList" aria-label="有序列表">${icon('list-ordered')}</button><button class="icon-button" data-command="blockquote" aria-label="引用话术">${icon('quote')}</button><button class="icon-button" data-command="codeBlock" aria-label="代码块">${icon('code')}</button><button class="icon-button" data-command="link" aria-label="插入链接">${icon('link')}</button><button class="icon-button" data-command="table" aria-label="插入表格">${icon('table')}</button><span class="tool-divider"></span><button class="icon-button" data-command="undo" aria-label="撤销">${icon('undo-2')}</button><button class="icon-button" data-command="redo" aria-label="重做">${icon('redo-2')}</button></div><div class="edit-actions"><button class="button subtle" id="source-toggle">${icon('file-code-2')}<span>Markdown</span></button><button class="button subtle" id="cancel-edit">取消</button><button class="button primary" id="save-doc">${icon('save')}保存</button></div></div>
    <div id="reading-area" class="reading-area"><article class="document"><div class="article-header" id="article-header"><div class="doc-symbol">${icon('file-text')}</div><h1 id="document-title">正在打开知识库…</h1><div class="doc-meta"><span class="author-mark">A</span><span>本地 SOP</span><span class="meta-divider"></span><span id="doc-modified"></span><span id="doc-size"></span></div></div><div id="editor" class="document-body"></div><textarea id="markdown-source" class="markdown-source" aria-label="Markdown 原文" spellcheck="false" hidden></textarea><footer class="document-footer" id="document-footer">原文保留在本地文件夹，AI 可以直接读取和整理。<a href="/licenses/misans.pdf" target="_blank" rel="noreferrer">MiSans 字体许可</a></footer></article><aside id="outline" class="outline" aria-label="文档大纲"><span class="outline-label">本页目录</span><nav id="outline-links"></nav></aside></div>
    <section id="collection-page" class="collection-page" hidden></section>
  </main>
  <div id="more-menu" class="popover" hidden><button id="history-open">${icon('history')}修改历史</button><button id="export-doc">${icon('download')}导出 Markdown</button><button id="copy-path">${icon('file-code-2')}复制本地文件路径</button></div>
  <dialog id="search-dialog" class="search-dialog"><div class="search-input-row">${icon('search')}<input id="search-input" placeholder="搜索标题、步骤或话术…" aria-label="搜索 SOP 内容" autocomplete="off"><button class="icon-button dialog-close" aria-label="关闭搜索">${icon('x')}</button></div><div class="search-hint">全文检索 · 输入关键词，例如“课程更新”“周六”“老带新”</div><div id="search-results" class="search-results"></div></dialog>
  <dialog id="new-dialog" class="small-dialog"><form id="new-form"><div class="dialog-heading"><h2>新建文档</h2><button type="button" class="icon-button dialog-close" aria-label="关闭新建">${icon('x')}</button></div><label>文档标题<input id="new-title" required maxlength="100" placeholder="例如：第 3 周老带新话术"></label><label>保存位置<select id="new-folder"><option value="">SOP 目录</option><option value="收件箱">收件箱 · 待整理内容</option><option value="维护与入库">维护与入库</option></select></label><p class="form-note" id="new-error">创建后即可编辑，内容会保存为本地 Markdown 文档。</p><div class="dialog-actions"><button type="button" class="button dialog-close">取消</button><button class="button primary" type="submit">创建文档</button></div></form></dialog>
  <dialog id="history-dialog" class="history-dialog"><div class="dialog-heading"><div><h2>修改历史</h2><p>每次保存前，保留原文以便恢复</p></div><button class="icon-button dialog-close" aria-label="关闭历史">${icon('x')}</button></div><div id="history-items"></div><div id="revision-panel" hidden><h3 id="revision-title"></h3><pre id="revision-content"></pre><button id="restore-version" class="button primary">恢复到此版本</button></div></dialog>
  <dialog id="conflict-dialog" class="conflict-dialog"><div class="dialog-heading"><div><h2>文件已有新的修改</h2><p>当前草稿已保留，可以复制需要的段落，再合并到最新原文。</p></div><button class="icon-button dialog-close" aria-label="关闭对比">${icon('x')}</button></div><div class="compare-columns"><label>我的草稿<textarea id="my-draft" readonly></textarea></label><label>文件中的最新内容<textarea id="disk-content" readonly></textarea></label></div><div class="dialog-actions"><button class="button" id="copy-draft">复制我的草稿</button><button class="button primary" id="load-latest">查看最新文档</button></div></dialog>
  <div id="toast" class="toast" role="status" hidden></div>`;

async function api(route, payload) {
  let response;
  try { response = await fetch(route, payload === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }); }
  catch { throw new Error('本地服务暂时无法连接，草稿已保留。请重新启动后再保存。'); }
  const data = await response.json();
  if (!response.ok) throw Object.assign(new Error(data.error || '操作失败，请重试。'), { status: response.status, data });
  return data;
}
let toastTimer;
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 3500); }
function draftKey() { return `local-sop.draft:${state.root}:${state.doc?.path}`; }
function storedDraft() { try { return JSON.parse(localStorage.getItem(draftKey()) || 'null'); } catch { return null; } }
function clearDraft(key = draftKey()) { try { localStorage.removeItem(key); } catch { /* Saving files does not depend on browser storage. */ } }
function content() { return state.source ? $('#markdown-source').value : state.editor.getMarkdown(); }
function storeDraft() {
  if (!state.doc || !state.editing) return;
  try { if (state.dirty) localStorage.setItem(draftKey(), JSON.stringify({ content: content(), version: state.editVersion })); else localStorage.removeItem(draftKey()); } catch { toast('浏览器草稿空间不足，请及时保存文档。'); }
}
function notice(message, buttons = []) {
  const node = $('#notice'); node.replaceChildren(); node.hidden = !message;
  if (!message) return;
  const text = document.createElement('span'); text.textContent = message; node.append(text);
  for (const [label, action] of buttons) { const button = document.createElement('button'); button.className = 'button small'; button.textContent = label; button.onclick = action; node.append(button); }
}
function saveStatus() {
  $('#save-status').innerHTML = `${icon(state.dirty ? 'pencil' : 'check')}${state.busy ? '正在保存…' : state.dirty ? '草稿未保存' : '已保存到本地'}`;
  $('#save-status').classList.toggle('unsaved', state.dirty);
  $('#save-doc').disabled = state.busy || !state.dirty;
  $('#cancel-edit').disabled = state.busy; $('#source-toggle').disabled = state.busy; $('#edit-doc').disabled = state.busy;
  $('#markdown-source').disabled = state.busy; $('#block-type').disabled = state.busy;
  document.querySelectorAll('[data-command]').forEach(button => { button.disabled = state.busy; });
  if (state.editor && state.editor.isEditable !== (state.editing && !state.busy)) state.editor.setEditable(state.editing && !state.busy, false);
  paintIcons();
}
function assetURL(src) {
  if (/^https?:\/\//i.test(src) || /^data:image\//i.test(src)) return src;
  if (/^[a-z][a-z\d+.-]*:/i.test(src) || src.startsWith('//')) return '';
  const parent = (state.doc?.path || '').split('/').slice(0, -1).map(encodeURIComponent).join('/');
  const path = decodeURIComponent(new URL(src, `http://local/${parent ? parent + '/' : ''}`).pathname.slice(1));
  return '/api/asset?path=' + encodeURIComponent(path);
}
const LocalImage = Image.extend({ renderHTML({ HTMLAttributes }) { return ['img', { ...HTMLAttributes, src: assetURL(HTMLAttributes.src || ''), loading: 'lazy' }]; } });
function makeEditor(markdown, editable) {
  state.editor?.destroy();
  $('#editor').replaceChildren();
  state.editor = new Editor({
    element: $('#editor'), extensions: [StarterKit.configure({ link: { openOnClick: false, HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' } } }), Markdown, TableKit.configure({ table: { resizable: false } }), LocalImage],
    content: markdown, contentType: 'markdown', editable,
    editorProps: { attributes: { 'aria-label': editable ? 'SOP 正文编辑区' : 'SOP 正文', spellcheck: 'false' } },
    onUpdate: () => { if (state.editing && !state.source) { state.dirty = state.editor.getMarkdown() !== state.baseRich; storeDraft(); saveStatus(); } },
    onSelectionUpdate: () => updateToolbar(),
  });
  state.baseRich = state.editor.getMarkdown();
}
function readBody(markdown) { return markdown.replace(/^\uFEFF?#[ \t]+[^\r\n]+\r?\n/, '').trimStart(); }
let contentScrollRequest = 0;
async function scrollToContent(target, options) {
  if (!target) return;
  const request = ++contentScrollRequest;
  // Local images above a heading must have their final size before scrolling.
  const images = [...$('#editor').querySelectorAll('img')];
  images.forEach(image => { image.loading = 'eager'; });
  await Promise.all(images.map(image => image.decode().catch(() => {})));
  if (request === contentScrollRequest && target.isConnected) target.scrollIntoView(options);
}
function updateOutline() {
  const nav = $('#outline-links'); nav.replaceChildren();
  $('#outline').hidden = state.editing;
  if (state.editing) return;
  const headings = [...$('#editor').querySelectorAll('h1,h2,h3,h4')].filter(heading => heading.textContent.trim());
  headings.forEach((heading, index) => {
    heading.id = 'section-' + index;
    const link = document.createElement('a'); link.textContent = heading.textContent; link.href = '#' + heading.id;
    link.style.setProperty('--depth', Math.min(Number(heading.tagName[1]) - 1, 2));
    link.onclick = event => { event.preventDefault(); scrollToContent(heading, { behavior: 'smooth', block: 'start' }); nav.querySelectorAll('a').forEach(a => a.classList.toggle('active', a === link)); };
    nav.append(link);
  });
  if (!headings.length) nav.innerHTML = '<p>这篇文档还没有小标题</p>';
}
function renderTree() {
  const docs = state.documents.filter(d => !state.scope || d.path.startsWith(state.scope));
  $('#document-tree').innerHTML = docs.map(d => `<button class="tree-item ${d.path === state.doc?.path ? 'active' : ''}" data-path="${esc(d.path)}" title="${esc(d.title)}">${icon('file-text')}<span>${esc(d.title)}</span></button>`).join('') || '<p class="tree-empty">这个目录还是空的</p>';
  $('#document-tree').querySelectorAll('[data-path]').forEach(button => { button.onclick = () => openDocument(button.dataset.path); });
  $('.collections').querySelectorAll('[data-scope]').forEach(button => button.classList.toggle('active', button.dataset.scope === state.scope));
  $('#doc-count').textContent = `${state.documents.length} 篇`;
  const count = state.documents.filter(d => d.path.startsWith('收件箱/')).length;
  $('#inbox-count').textContent = count || '';
  paintIcons();
}
async function refreshDocuments() {
  const result = await api('/api/documents'); state.documents = result.documents; state.root = result.root; renderTree();
}
function switchEditUI(editing) {
  $('#edit-toolbar').hidden = !editing; $('#article-header').hidden = editing; $('#document-footer').hidden = editing;
  $('#edit-doc').hidden = editing; $('#copy-doc').hidden = editing; $('#more-open').disabled = editing;
  $('#reading-area').classList.toggle('is-editing', editing);
  $('#markdown-source').hidden = !editing || !state.source; $('#editor').hidden = editing && state.source;
  $('#source-toggle').classList.toggle('selected', state.source);
}
async function openDocument(path, { force = false, focusText = '', line = 0 } = {}) {
  if (!force && state.dirty && !confirm('当前修改尚未保存。草稿已保留在本机，仍要切换文档吗？')) return;
  storeDraft();
  const own = ++state.load;
  try {
    const doc = await api('/api/document?path=' + encodeURIComponent(path)); if (own !== state.load) return;
    if (state.scope && !path.startsWith(state.scope)) state.scope = '';
    state.doc = doc; state.editing = false; state.source = false; state.dirty = false;
    $('#collection-page').hidden = true; $('#reading-area').hidden = false;
    $('#document-title').textContent = doc.title; $('#crumb-title').textContent = doc.title;
    $('#doc-modified').textContent = '修改于 ' + time(doc.modified); $('#doc-size').textContent = `${doc.size.toLocaleString()} 字`;
    switchEditUI(false); makeEditor(readBody(doc.content), false); updateOutline(); renderTree(); saveStatus();
    history.replaceState(null, '', '#doc=' + encodeURIComponent(path));
    $('#reading-area').scrollTop = 0; document.body.classList.remove('sidebar-open');
    const draft = storedDraft();
    if (draft) notice('这篇文档有一份未保存的本机草稿。', [['恢复草稿', () => recoverDraft(draft)], ['丢弃草稿', () => { clearDraft(); notice(''); }]]); else notice('');
    if (line) focusText = (doc.content.split(/\r?\n/)[line - 1] || '').replace(/^[#>*\-\s]+/, '').replace(/[*_`\\]/g, '').slice(0, 18);
    if (focusText) scrollToContent([...$('#editor').querySelectorAll('h2,h3,p,li')].find(node => node.textContent.includes(focusText)), { block: 'center' });
  } catch (error) { toast(error.message); }
}
function startEditing(initial = state.doc?.content, version = state.doc?.version) {
  if (!state.doc) return;
  notice(''); state.editing = true; state.source = false; state.editVersion = version;
  makeEditor(state.doc.content, true);
  if (initial !== state.doc.content) state.editor.commands.setContent(initial, { contentType: 'markdown' });
  state.dirty = initial !== state.doc.content; switchEditUI(true); updateOutline(); saveStatus();
  state.editor.commands.focus('end');
}
function recoverDraft(draft) {
  if (draft.version !== state.doc.version) return showConflict(state.doc, draft.content);
  startEditing(draft.content, draft.version); state.dirty = true; saveStatus();
}
async function saveDocument() {
  if (!state.editing || state.busy || !state.dirty) return;
  const draft = content(); const path = state.doc.path; const title = state.doc.title;
  const version = state.editVersion; const savedKey = draftKey(); const view = state.load;
  storeDraft(); state.busy = true; saveStatus();
  try {
    const doc = await api('/api/save', { path, content: draft, version });
    await refreshDocuments(); clearDraft(savedKey);
    if (state.load === view && state.doc?.path === path) { state.dirty = false; state.editing = false; await openDocument(doc.path, { force: true }); }
    toast(`已保存「${title}」`);
  } catch (error) {
    if (state.doc?.path === path && state.load === view) {
      if (error.status === 409 && error.data.current) showConflict(error.data.current, draft); else notice(error.message);
    }
    toast(error.message);
  } finally { state.busy = false; saveStatus(); }
}
function showConflict(current, draft = content()) {
  $('#my-draft').value = draft; $('#disk-content').value = current.content;
  $('#conflict-dialog').showModal();
  $('#load-latest').onclick = async () => { $('#conflict-dialog').close(); state.dirty = false; state.editing = false; await openDocument(current.path, { force: true }); };
}
async function copyText(text) { try { await navigator.clipboard.writeText(text); toast('已复制'); } catch { toast('复制受浏览器限制，可选中正文后按 Ctrl+C。'); } }
function updateToolbar() {
  if (!state.editor || !state.editing) return;
  document.querySelectorAll('[data-command]').forEach(button => button.classList.toggle('selected', state.editor.isActive(button.dataset.command)));
  const heading = [1, 2, 3].find(level => state.editor.isActive('heading', { level }));
  $('#block-type').value = heading || 'paragraph';
}
function openNew(folder = state.scope.replace(/\/$/, '')) {
  $('#new-title').value = ''; $('#new-folder').value = folder || ''; $('#new-error').textContent = '创建后即可编辑，内容会保存为本地 Markdown 文档。';
  $('#new-dialog').showModal(); $('#new-title').focus();
}
async function showCollection(scope) {
  if (state.dirty && !confirm('当前有未保存的修改。保留草稿并切换目录吗？')) return;
  storeDraft(); state.load++; state.scope = scope; state.dirty = false; state.editing = false; state.editor?.destroy(); state.editor = null; state.doc = null;
  document.body.classList.remove('sidebar-open');
  $('#reading-area').hidden = true; $('#edit-toolbar').hidden = true; $('#collection-page').hidden = false; $('#more-open').disabled = true; $('#edit-doc').hidden = true; $('#copy-doc').hidden = true;
  notice(''); await refreshDocuments();
  const docs = state.documents.filter(d => !scope || d.path.startsWith(scope));
  const title = scope.startsWith('收件箱') ? '收件箱' : scope ? '维护与入库' : '全部 SOP'; $('#crumb-title').textContent = title;
  $('#collection-page').innerHTML = `<div class="collection-heading"><span class="eyebrow">AI 编程训练营</span><h1>${title}</h1><p>${scope.startsWith('收件箱') ? '先记下零散想法或新话术，再交给 AI 整理到合适的 SOP。' : scope ? '规范、差异和待确认事项，集中放在这里核对。' : '把流程、经验和话术留在本地，随时查阅与更新。'}</p><button class="button primary" id="collection-new">${icon('plus')}${scope.startsWith('收件箱') ? '记一条新内容' : '新建文档'}</button></div><div class="collection-list">${docs.map(d => `<button data-path="${esc(d.path)}">${icon('file-text')}<span><strong>${esc(d.title)}</strong><small>${esc(d.path)}</small></span><time>${time(d.modified)}</time>${icon('chevron-right')}</button>`).join('') || `<div class="empty-state">${icon('inbox')}<h2>还没有${scope.startsWith('收件箱') ? '待整理内容' : '文档'}</h2><p>从第一条记录开始，内容会保存为你可以直接交给 AI 的本地文件。</p></div>`}</div>`;
  $('#collection-new').onclick = () => openNew(); $('#collection-page').querySelectorAll('[data-path]').forEach(button => button.onclick = () => openDocument(button.dataset.path)); paintIcons();
}

$('#edit-doc').onclick = () => startEditing(); $('#save-doc').onclick = saveDocument;
$('#cancel-edit').onclick = () => { storeDraft(); state.dirty = false; state.editing = false; openDocument(state.doc.path, { force: true }); };
$('#source-toggle').onclick = () => {
  const value = content(); const baseline = state.baseRich; state.source = !state.source;
  if (state.source) $('#markdown-source').value = value; else { makeEditor(value, true); state.baseRich = baseline; }
  switchEditUI(true); updateToolbar();
};
$('#markdown-source').oninput = () => { state.dirty = $('#markdown-source').value !== state.doc.content; storeDraft(); saveStatus(); };
$('#block-type').onchange = event => { if (state.source) return; const chain = state.editor.chain().focus(); event.target.value === 'paragraph' ? chain.setParagraph().run() : chain.toggleHeading({ level: Number(event.target.value) }).run(); };
document.querySelectorAll('[data-command]').forEach(button => button.onclick = () => {
  if (state.source) return toast('切回排版编辑后可以使用格式工具。');
  const chain = state.editor.chain().focus(); const command = button.dataset.command;
  if (command === 'link') { const href = prompt('输入链接地址', state.editor.getAttributes('link').href || 'https://'); if (href === null) return; if (!href.trim()) chain.unsetLink().run(); else if (/^https?:\/\//i.test(href)) chain.setLink({ href }).run(); else toast('请输入 http 或 https 链接。'); }
  else if (command === 'table') chain.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
  else if (command === 'undo' || command === 'redo') chain[command]().run(); else chain['toggle' + command[0].toUpperCase() + command.slice(1)]().run();
  updateToolbar();
});
$('#new-doc').onclick = () => openNew();
$('.collections').querySelectorAll('[data-scope]').forEach(button => button.onclick = () => showCollection(button.dataset.scope));
$('#home').onclick = () => showCollection('');
$('#new-form').onsubmit = async event => {
  event.preventDefault(); const button = event.submitter; button.disabled = true;
  try { const doc = await api('/api/create', { title: $('#new-title').value, folder: $('#new-folder').value }); $('#new-dialog').close(); await refreshDocuments(); await openDocument(doc.path); startEditing(); }
  catch (error) { $('#new-error').textContent = error.message; } finally { button.disabled = false; }
};
document.querySelectorAll('.dialog-close').forEach(button => button.onclick = () => button.closest('dialog').close());
document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('click', event => { if (event.target === dialog) { const box = dialog.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close(); } }));
$('#more-open').onclick = () => { $('#more-menu').hidden = !$('#more-menu').hidden; };
document.addEventListener('click', event => { if (!event.target.closest('#more-menu,#more-open')) $('#more-menu').hidden = true; });
$('#export-doc').onclick = () => { if (!state.doc) return; const blob = new Blob([state.doc.content], { type: 'text/markdown;charset=utf-8' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = state.doc.title + '.md'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); };
$('#copy-path').onclick = () => state.doc && copyText(state.root + '\\' + state.doc.path.replaceAll('/', '\\'));
$('#copy-doc').onclick = async () => {
  if (!state.doc || !state.editor) return;
  const html = `<h1>${esc(state.doc.title)}</h1>${state.editor.getHTML()}`;
  try { if (navigator.clipboard.write && window.ClipboardItem) { await navigator.clipboard.write([new ClipboardItem({ 'text/html': new Blob([html], { type: 'text/html' }), 'text/plain': new Blob([state.doc.content], { type: 'text/plain' }) })]); toast('已复制正文，可手动粘贴到飞书'); } else await copyText(state.doc.content); } catch { await copyText(state.doc.content); }
};
$('#copy-draft').onclick = () => copyText($('#my-draft').value);
$('#history-open').onclick = async () => {
  $('#more-menu').hidden = true; $('#history-dialog').showModal(); $('#revision-panel').hidden = true;
  try {
    state.history = (await api('/api/history?path=' + encodeURIComponent(state.doc.path))).versions;
    $('#history-items').innerHTML = state.history.map(v => `<button data-revision="${v.id}">${icon('history')}<span>${time(v.modified)}<small>${v.size.toLocaleString()} 字节</small></span>${icon('chevron-right')}</button>`).join('') || '<div class="empty-history">还没有保存前的历史版本。<br>首次修改并保存后，原文会留在这里。</div>';
    $('#history-items').querySelectorAll('[data-revision]').forEach(button => button.onclick = async () => { const value = await api('/api/revision?path=' + encodeURIComponent(state.doc.path) + '&revision=' + button.dataset.revision); $('#revision-content').textContent = value.content; $('#revision-title').textContent = '版本预览'; $('#revision-panel').hidden = false; $('#restore-version').dataset.revision = button.dataset.revision; }); paintIcons();
  } catch (error) { $('#history-items').textContent = error.message; }
};
$('#restore-version').onclick = async event => {
  if (!confirm('恢复后当前内容也会保留一份历史，确认恢复此版本吗？')) return;
  event.currentTarget.disabled = true;
  try { const doc = await api('/api/restore', { path: state.doc.path, version: state.doc.version, revision: event.currentTarget.dataset.revision }); $('#history-dialog').close(); await refreshDocuments(); await openDocument(doc.path, { force: true }); toast('已恢复，恢复前的内容也已留存'); }
  catch (error) { toast(error.message); } finally { event.currentTarget.disabled = false; }
};
$('#search-open').onclick = () => { $('#search-dialog').showModal(); $('#search-input').focus(); };
let searchTimer, searchSequence = 0;
$('#search-input').oninput = () => { clearTimeout(searchTimer); const query = $('#search-input').value.trim(); const sequence = ++searchSequence; if (!query) { $('#search-results').replaceChildren(); return; } searchTimer = setTimeout(async () => {
  try { const { results } = await api('/api/search?q=' + encodeURIComponent(query)); if (sequence !== searchSequence) return;
    $('#search-results').innerHTML = results.map(result => `<button class="search-result" data-path="${esc(result.path)}" data-hit="${esc(query.split(/\s+/)[0])}"><strong>${icon('file-text')}${esc(result.title)}</strong>${result.hits.map(hit => `<p><small>第 ${hit.line} 行</small>${esc(hit.text)}</p>`).join('')}</button>`).join('') || '<div class="empty-history">没有找到相关内容，试试更短的关键词。</div>';
    $('#search-results').querySelectorAll('[data-path]').forEach(button => button.onclick = () => { $('#search-dialog').close(); openDocument(button.dataset.path, { focusText: button.dataset.hit }); }); paintIcons();
  } catch (error) { $('#search-results').textContent = error.message; }
  }, 180); };
$('#search-input').onkeydown = event => { if (event.key === 'Enter') { const first = $('#search-results .search-result'); if (first) { event.preventDefault(); first.click(); } } };
$('.mobile-menu').onclick = () => document.body.classList.add('sidebar-open');
$('.sidebar-close').onclick = $('#sidebar-scrim').onclick = () => document.body.classList.remove('sidebar-open');
$('#editor').addEventListener('click', event => {
  if (state.editing) return;
  const anchor = event.target.closest('a'); if (!anchor) return;
  const href = anchor.getAttribute('href') || '';
  if (/^https?:\/\//i.test(href)) { event.preventDefault(); window.open(href, '_blank', 'noopener,noreferrer'); }
  else if (!href.startsWith('#')) {
    event.preventDefault();
    if (/\.md(?:#|$)/i.test(href)) {
      const parent = state.doc.path.split('/').slice(0, -1).map(encodeURIComponent).join('/');
      const target = new URL(href, `http://local/${parent ? parent + '/' : ''}`);
      const path = decodeURIComponent(target.pathname.slice(1)); const fragment = decodeURIComponent(target.hash.slice(1));
      openDocument(path, /^L\d+$/.test(fragment) ? { line: Number(fragment.slice(1)) } : { focusText: fragment });
    } else { const url = assetURL(href); if (url) window.open(url, '_blank', 'noopener,noreferrer'); }
  }
});
document.addEventListener('keydown', event => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); $('#search-open').click(); } if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') { event.preventDefault(); if (state.editing) saveDocument(); } });
window.addEventListener('beforeunload', event => { if (state.dirty) { storeDraft(); event.preventDefault(); event.returnValue = ''; } });
setInterval(async () => { if (!state.doc || state.busy || document.hidden) return; try { await refreshDocuments(); const current = state.documents.find(d => d.path === state.doc.path); if (current && current.version !== state.doc.version) { if (state.editing) notice('原文已有外部修改。草稿已保留，保存时会提示核对最新版本。'); else { await openDocument(current.path, { force: true }); toast('已读取最新的本地修改'); } } } catch { /* The current document and draft remain available when the server is stopped. */ } }, 5000);

paintIcons();
try {
  await refreshDocuments();
  const path = new URLSearchParams(location.hash.slice(1)).get('doc');
  const first = state.documents.find(d => d.path === path) || state.documents.find(d => d.title === 'AI编程班主任工作SOP') || state.documents[0];
  if (first) await openDocument(first.path); else await showCollection('');
} catch (error) { $('#document-title').textContent = '暂时无法打开本地知识库'; notice(error.message); }
