# 网页与安卓 1.7.0 本地迭代验收

日期：2026 年 10 月 28 日。用户已确认计划、四类话术、允许带待填项复制，并要求持续执行、疑问按推荐处理。

## 已落实范围

- 网页继续使用原站“提交 Issue”入口与原提交页。新增本浏览器多份草稿、跳转记录和可选的链接关联，不重做日程主界面。
- App 增加草稿列表与提交记录。升级迁移旧稿、图片、绑定、标识和未知提交状态；多个稿件独立，成功只清理当前稿，退出账号前保护全部未提交稿。
- 记录区分“已打开 GitHub”“本人已关联”和 App 收到的成功回执。App 可分页读取当前授权仓库 Issue，排除 PR；open / closed 不映射成 SOP 已完成。
- 网站点击任务标题打开轻量详情；App 复用任务详情。19 条话术对应 76 个稳定任务键，包含四类场景。来源、版本、整理说明与复制正文分开，待填项允许复制并提示补齐。
- 两端均可从任务详情另存带来源的草稿，原稿保留。
- 周三整理、逐条回填及本批待同步飞书清单规范已完善，见 `docs/weekly-sop-processing.md`。不加入网站 / App 自动入库，也不代用户改飞书。

## 数据与内容

网页旧单稿存储保留，新格式独立版本化。支持 Web Locks 的浏览器把本功能读改写串行化，并校验存储版本；其他窗口更改后不覆盖当前输入，可将当前稿另存。旧数据损坏、存储空间不足等情况保留原数据和当前输入；不支持 Web Locks 的旧浏览器只有版本冲突检测，应使用单标签编辑。

Android 新快照原子保存，单稿迁移不改变上传路径与未知结果标记。草稿、记录绑定账号 / 仓库；切回已知仓库的旧稿恢复其目标，不自动改投。没有增加手机 Projects 管理权限。

用户桌面课程提醒原文件未改动，项目保留逐字节相同的参考副本。原有 28 个 SOP 正文 / 附件按基线 SHA-256 核验全部未变；维护规范增加本次操作说明。课程话术与旧 SOP 差异见 `docs/task-scripts-notes.md`，其中第 12 周两种提交截止口径保留提示，没有改排期。

第 3 / 11 周推广文案为通用整理稿，金额、权益和入口不编造；第 12 周仍是结营直播后。网页和 APK 的话术由同一脚本导出并检查一致性，完整 SOP 和学生个人资料未被打入客户端。

## 检查与证据

证据目录：`artifacts/android/next-iteration/`。

- 网站单元 / 集成测试 34 项，0 失败：`web-unit-final.log`。包含旧排期、完成状态、资料入口及新多稿 / 链接规则。
- Android 单元测试 40 项，0 失败；lint 无问题；签名 release 构建成功：`build-delivery.log`。
- 网页真实浏览器桌面 / 390px 窄屏三轮交互：`web-check.log`。覆盖迁移、切稿、重载、待确认 / 关联记录、真实同时编辑冲突恢复、模拟写入失败保留输入、从实际任务入口打开详情、仅复制正文及来源另存。日程端使用隔离后端，不写实际团队数据。
- Android 专用模拟器检查：`native-check.log`，原 1.6.2 图片草稿准备在 `legacy-preparation.log` 和 `legacy/`。首次升级检查确认图片与正文保留，后续重复检查切稿、重开、历史配置入口、话术复制和任务来源另存；没有创建真实 Issue。
- 大字体 / 320dp 的附加截图与检查保存在 `native-large/`、`native-large.log`。复制反馈放在按钮相邻处，避免长正文或大字体下全局反馈落在屏外。
- 话术资源与 Android 排期导出检查通过；既有第 3 / 11 周周三、第 12 周周日直播后的任务键和规则没有改变。

## 视觉复核

沿用原有蓝灰、白色、浅灰和 MiSans 的工作工具风格；未开新风格方向。网页新增面板与原入口配色一致，App 使用原控件。

按 Oil UI 存量界面规范做一轮独立评审，不打分。处理三项反馈：空历史增加直接配置入口及明确空状态；只读 / 离线 / 同步中的完成按钮使用具体状态与锁形图标；网页提交动作在动画稳定后核验可点状态及主色，避免用过渡色截图判断禁用。合并 App 相同话术的对象说明，删去网页推广整理备注中的重复提醒。

沿用原有焦点、按压和页面短过渡，不新增装饰动画。主执行者还发现大字体复制结果可能处于屏外，改为卡片内即时反馈并增加无障碍播报。

## 仍需外部条件的部分

GitHub CLI 实际读取 Projects 返回缺少 `read:project`，因此没有创建看板、没有声称读取了实时看板列状态。当前仓库 Issues 可正常承担收集入口；本人完成官方 `project` 授权后，可复用现有配置脚本建立三列。该限制不是文件权限或代码故障，不能用手机仓库令牌替代。

GitHub 的真实个人授权和真机网络仍由手机本人使用；本次不把模拟器演示和网络隔离测试说成真人提交成功。网站没有部署或发布。飞书同步、真实批次整理和最终完成确认也没有代用户执行。

## 交付

- 安装包：`artifacts/android/camp-schedule-1.7.0.apk`，versionCode 13。原签名覆盖安装；最终校验值以 `artifacts/android/SHA256SUMS.txt` 为准。
- APK SHA-256：`61ecd1265682e19cbd47b534405c283625adfe31152fda1fb1ab2788373c6387`；签名证书 SHA-256：`bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`。
- 本地网页入口：`http://127.0.0.1:8899/ai-camp-schedule/issue-intake.html`。
- 内容差异清单：`docs/task-scripts-notes.md`。
- 周三流程：`docs/weekly-sop-processing.md`。

源码没有提交或推送，Git HEAD 保持 8e47476。可选的全文搜索、云端草稿同步、自动替换营期变量和导出备份没有纳入本次实现。

## 浏览器并发依据

[HTML Storage 标准](https://html.spec.whatwg.org/multipage/webstorage.html#the-localstorage-attribute) 明确不保证跨执行环境的本地存储锁。本次因此结合 [Web Locks](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API) 串行与版本冲突检测，未仅凭两次 localStorage 调用声称原子比较更新。

## 后续：Projects 授权与看板配置（2026-10-28）

本人完成新的设备流授权后，CLI 返回 `Authentication complete`，实际 Projects 读取成功。现有其他空项目 #1 保留未动；复用已审阅脚本新建私有 Project #2，随后通过官方 API 配置并回读核验：

- 地址：维护者的私有项目页面，标题 `SOP Inbox - sop-inbox`，`public=false`。
- 关联仓库 `owner/sop-inbox`；视图“周三 SOP 整理”为 `BOARD_LAYOUT`，列字段为 `Status`。
- Status 选项依次“待处理、待确认、已完成”，描述明确已完成需人工同步飞书。
- 将本人既有测试 Issue #1 加入项目并设为“待处理”；Issue 仍为 OPEN，没有新建 / 修改正文 / 评论 / 关闭 Issue。
- `Item closed`、`Pull request merged`、`Pull request linked to issue`、`Auto-close issue` 经回读均 `enabled=false`。未改动其他项目的规则。

当前 GraphQL schema 提供工作流查询及删除，没有创建 / 更新工作流配置的 mutation；现有工具不能操作本人已登录浏览器。自动加入仓库新 Issue 与加入后初始化待处理仍未启用，已在说明中列为待完成的两个网页操作，不能声称手机 → 看板自动流转已验收。官方步骤见 [自动加入](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically) 及 [内置自动化](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-built-in-automations)。

本次只配置 GitHub 项目与更新本地说明，未修改客户端源代码、原 SOP、APK 或排期；未提交、推送、部署。核验快照见 `artifacts/github-project/configuration-2026-10-28.json`。

### 本人完成工作流设置后的复核

本人提供两项工作流开启截图，随后按提醒将自动加入筛选确认成 `is:issue`，回复“OK了”。重新通过 API 读取私有 Project #2：`Auto-add to project`、`Item added to project` 均 `enabled=true`；自动完成 / 自动关闭的四项规则仍 `enabled=false`。初始状态“待处理”有本人截图依据，筛选有本人完成确认；公开 API 不返回过滤条件与动作参数，未声称这些参数也由 API 独立核验。

现有 Issue #1 仍为 OPEN、待处理，项目仍为私有 Status 看板。没有创建额外 Issue、修改 Issue 内容或触发关闭以测试。两项工作流配置依赖已解除，新提交自动流转的端到端结果留待本人下一次正常提交核对。复核快照为 `artifacts/github-project/workflows-enabled-2026-10-28.json`。本地说明同步更新，源码仍未提交、推送或部署。
