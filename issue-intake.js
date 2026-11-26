(function (root) {
  'use strict';
  function repository(text) {
    let value = text.trim();
    if (value.startsWith('https://')) {
      const url = new URL(value);
      if (url.hostname !== 'github.com' || url.username || url.password || url.port) throw Error('请填写 github.com 上自己的仓库链接。');
      const parts = url.pathname.split('/').filter(Boolean);
      if (parts.length < 2 || (parts.length > 2 && parts[2] !== 'issues')) throw Error('请填写仓库首页链接。');
      value = parts.slice(0, 2).join('/');
    }
    value = value.replace(/\/$/, '').replace(/\.git$/, '');
    if (!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(value) || ['.', '..'].includes(value.split('/')[1])) throw Error('请填写“账号/仓库名”或完整 GitHub 仓库链接。');
    return value;
  }
  function compose(repo, body) {
    const target = repository(repo);
    if (body.length > 55000) throw Error('文字较长，请拆分成两条记录；原文仍保留。');
    const title = [...(body.split(/\r?\n/).find(line => line.trim()) || '').trim().replace(/^# /, '')].slice(0, 200).join('');
    if (!title) throw Error('请先粘贴文字，只有图片时也请补一句说明。');
    const base = `https://github.com/${target}/issues/new`;
    const params = new URLSearchParams({ title, body, template: 'blank' });
    const full = `${base}?${params}`;
    const long = full.length > 7500;
    return { target, title, long, url: long ? `${base}?${new URLSearchParams({ title, template: 'blank' })}` : full };
  }
  const api = { repository, compose };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.IssueIntake = api;
})(typeof window !== 'undefined' ? window : globalThis);
