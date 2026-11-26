# 本地网页图文直提实施清单

**目标：** 原收集页支持富文本截图草稿、一键直提及真实仓库历史，保持本地免部署。

**架构：** 本机 Node HTTP 服务托管原网页，通过无 shell 的 GitHub CLI 调用读取本机登录及操作本人选定私有仓库。编辑器复用本地已安装的 Tiptap；IndexedDB 原子保存图文快照，旧 localStorage 不覆盖。App 继续采用原 GitHub App 登录与实际仓库历史，不更换账号权限。

**授权边界：** 仅本地实施；不提交、推送、部署，不自动创建真实测试 Issue。用户明确确认采用本地服务，无需再次确认日常实现细节。沿用原视觉，不另选风格。

## 1. 先锁定数据与接口

- [x] 新增 `scripts/github-issue.cjs`：`GitHubClient` 注入请求适配器，生产适配器用 `execFile('gh', ['api', ...])`，不把文本拼成命令。
- [x] `session()` 仅读取当前账号；`repository(repo)` 逐次验证本人 / 私有 / Issues 开启 / 未归档；禁止任意 URL、任意 API 或通用命令代理。
- [x] `history(repo,page)` 使用 `state=all&sort=created&direction=desc&per_page=20`，过滤 PR，保留基于原始数量的分页；只返回标题、编号、URL、时间和 open/closed。
- [x] 提交请求为 `{id,repo,body,images:[{id,mime,data}]}`；正文使用 `camp-image:<id>` 本地占位。严格限制 9 张、每张 10MB、正文 55000 字符与固定 ID / MIME。
- [x] 上传至 `attachments/<稿件id>/<图片id>.<扩展名>`，核验 Git blob 摘要；创建 Issue 前置 / 超时重试均按 `<!-- camp-issue:<id> -->` 核对，首次创建结果未知时保留禁止盲重发的状态。
- [x] 核对 / 创建串行化，同一稿件跨窗口并发只能创建一次。每个稿件提交内容哈希与结果保存在被忽略的本地服务状态目录，持久化失败时不进行远端创建。

## 2. 本地服务

- [x] 新增 `scripts/issue-service.cjs`，监听 `127.0.0.1:8902`。同源页面及明确允许的 8899 旧预览通过受限 CORS 调用，原草稿来源不变。仅显式允许的静态资源与接口可访问，不暴露 `.git`、SOP、环境配置、node_modules、附件 / 状态目录。
- [x] 接口为 `/api/issue/session`、`/api/issue/repository`、`/api/issue/history`、`/api/issue/submit`、`/api/issue/reconcile`。Host、Origin、Fetch-Metadata 检查及进程内随机 CSRF header；JSON 请求大小限制、错误不回显凭据 / 命令。
- [x] 在 `tests/issue-service.test.js` 用注入的假 GitHub 适配器验证跨站请求、路径穿越、JSON / 图片限额、私有仓库边界、历史分页及提交去重；真实网络仅读当前用户 / 仓库 / 历史。
- [x] 新增 `启动本地网站.cmd` 与 `scripts/start-local-website.ps1`，双击启动；只为启动进程使用本机现有代理设置，不修改全局代理或 GitHub 凭据。

## 3. 图文草稿与旧稿迁移

- [x] 新增 `issue-rich-store.js`：异步 `open/load/save/new/select/clear/recover/receipt`，IndexedDB 单事务快照含 revision；二进制图像随稿隔离，图片不放 localStorage。
- [x] 首次把 `camp-issue-notebook-v2` 全量迁入，保留原 ID、目标仓库、正文与跳转 / 关联记录；旧键逐字节保留。存储失败不显示已保存、不切稿、不清稿。
- [x] 新增 `tests/issue-rich-store.test.js`，用 IndexedDB 测试实现验证跨 tab 修订、迁移、图稿独立、失败保留、成功仅清当前稿；测试不访问真实浏览器数据。

## 4. 富文本与收集页

- [x] 新增 `web-issue/editor.js` 与 `web-issue/main.js`，沿用 Tiptap 3.32.0，构建产物为 `assets/issue-editor.js`。新增 `scripts/build-issue-editor.mjs`，复用 `local-sop` 已安装的 Vite / 依赖，编辑器不依赖 CDN。
- [x] 粘贴截图、拖入、选图在光标处插入；加载时本地占位解析为 object URL，保存时仍为确定的图 ID；移除图片不触碰其他草稿。外部图像不悄悄抓取，提示补图。粘贴 HTML 过滤危险链接及脚本。
- [x] 工具栏含标题 / 加粗 / 列表 / 引用 / 链接 / 代码 / 表格 / 图片和撤销重做；支持中英文输入法，不在编辑过程中频繁重建编辑器。
- [x] 更新 `issue-intake.html/.css`：保留原入口、品牌、色彩和卡片结构；配置区显示登录账号及仓库，主操作为“提交 Issue”；编辑 / 上传 / 成功 / 结果未知 / 断开等状态清晰。
- [x] 历史弹窗打开时读取当前私有仓库，支持刷新 / 更多 / 缓存标识；旧跳转记录另列，不假装已提交。切换仓库后不显示前一仓库缓存。
- [x] 保留任务来源新建草稿、旧稿保留、并发冲突恢复、正文复制、清空确认。图文仅在点击提交后上传。

## 5. 集成与验收

- [x] `node --test tests/*.test.js`：既有排期、话术、存储规则及新增接口 / 图文测试全部通过；已替换 UI 的旧测试改为新模块真实行为测试，不保留虚假页面通过。
- [x] `node scripts/build-issue-editor.mjs` 构建本地编辑器，核验富文本模块、来源许可与无远端依赖。
- [x] 浏览器独立临时 profile 测试截图粘贴、格式、提交响应、刷新恢复、两稿隔离、并发冲突、失败留稿、真实历史渲染；远端写入使用隔离假适配器，重复运行三次。
- [x] 真实本地服务只读核验 Arvvinn / sop-inbox，与 App 使用同一仓库历史；不创建真实测试 Issue。
- [x] 在桌面 / 窄屏查看编辑器和弹窗，保留现有短过渡、按钮反馈，图片载入有状态，尊重 reduced-motion；不添加装饰素材。
- [x] 更新配置 / 使用说明及验收记录，确认 Git HEAD 未变。原 Python 预览保留，不结束其他进程。自动审批以策略拦截拒绝停止预览及后台启动新服务，调整为保留旧来源、独立本机端口，交付可双击启动脚本。
- [ ] 外部启动动作：本人双击 `启动本地网站.cmd`，保持服务窗口打开，再在原页面刷新。自动审批拒绝的启动动作没有通过其他工具重试；当前不能声称真实后台服务已启用。

## 检查命令

```powershell
node --test tests/*.test.js
node scripts/build-issue-editor.mjs
node scripts/issue-service.cjs
git diff --check
git rev-parse --short HEAD
```

成功标准：页面提交得到真实编号 / 可核对的未知结果；图文刷新不丢；真实记录按当前仓库读取。真实账号授权失效不清稿；“已完成”仍由本人同步飞书后在看板操作。
