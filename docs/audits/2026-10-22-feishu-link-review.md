# 飞书入口核对：AI 编程与旧训练营资料

状态：用户最终确认只替换第 5 个「批改文档模板」，其余 15 个入口保留。Android 1.5.1 已完成构建及包内核对；网站一行修改已提交 PR #3，当前账号只读，等待仓库所有者合并发布。

## 范围

用户最初反馈 App 中部分链接仍打开 Python 资料，并提出授权飞书账号。后续用户直接提供替换地址，明确要求网站和 App 都更新，因此本轮范围包含网站和 Android。飞书原文不改，不发送消息。

最初盘点时 Git 分支为 `codex/android-oil-ui`，起点 `9bbe11e`，开始时工作区干净。`data.js` 的链接与 Android 内置 `schedule.json` 一致；用户随后提供了准确替换映射，最终在 `codex/ai-grading-template-link-android` 上实施。

## 本地证据清单

| 链接键 | 当前入口 | 本地 AI 编程导出资料中的证据 | 仍需确认 |
| --- | --- | --- | --- |
| duxue | 督学 SOP | 工作 SOP 引用 AI编程班主任督学SOP，目标 `OL6zw9FhsiUO56kVQtfcUfIfnjh` | 在线标题、内容及权限 |
| dayi | 答疑 SOP | 工作 SOP 引用 AI编程学员答疑SOP，目标 `WgnMwexTQiomETkx3z2cnNJ8nac` | 在线标题、内容及权限 |
| jiangshi | 辅助讲师 SOP | 工作 SOP 引用 AI编程讲师社群与直播答疑SOP，目标 `ZlUnwQYtXiaxrPknpjCc4qZCntb` | 在线标题、内容及权限 |
| anli | 好评案例收集 | 工作 SOP 引用 AI编程好评反馈和优秀案例，目标 `KizCdjpy0oiMSoxZALCcGvCUnVX` | 在线标题、内容及权限 |
| kaoshi | 考试常见问题 | 工作 SOP 引用 Vibe Coding 工信部考试常见问题，目标 `E8Uzdz1OWoq9pex0lnmcsSfPnHb` | 在线是否仍适用 |
| pyq | 朋友圈公式 | 引用名为“【全员】朋友圈运营手册”，不能仅凭通用名称判断属于 Python | 是否通用资料，或已有 AI 专用替代 |
| pigai | 批改文档模板 | 引用名为“Week 1 作业批改”，未从本地文件获得目标正文 | 所属课程与 AI 编程模板 |
| zhibo | 直播重建说明 | 入口是飞书妙记 | 所属课程与有效 AI 编程说明 |
| yunpan | 云空间案例 | 入口是飞书文件夹 | 是通用示例还是往期实际云空间 |
| tijiao | 作业提交指南示例 | 工作 SOP 明确说明“每期都重新做，因为每期它的链接也是不一样的” | 需要本营期实际入口，不能把旧示例当作统一地址 |
| fenxiang | 分享稿案例 | 引用名为“Vincent 对话”，目标正文不在本地导出范围 | 是可复用案例还是必须换为 AI 案例 |
| top5 | 名单表格示例 | 引用名为“第五期结营学员信息” | AI 编程对应表格或当期表格 |
| jiangpin | 奖品信息问卷 | 引用名为“结营获奖信息收集问卷” | 所属课程、营期及有效问卷 |
| biye | 毕业作品问卷示例 | 引用名为“第一期｜XX小队毕业作品提交与展示意愿征集”，原 SOP 要求按期/小队创建 | 需要当期各小队实际入口 |
| shiming | 实名认证操作流程（腾讯文档） | 属于外部考试操作资料，需与 AI 编程考试章节交叉核对 | 是否通用操作或 Python 旧版本 |
| kaoshiliucheng | 考试操作流程（腾讯文档） | 属于外部考试操作资料，需与 AI 编程考试章节交叉核对 | 是否适用当前 Vibe Coding 考试 |

前四个入口在此前 SOP 修正中已替换成本地 AI 编程导出资料引用的地址；这不等于本轮已经在线确认其正文。用户看到哪些具体入口仍是 Python，目前尚未逐个确认。考试说明提及以前参加过 Python 考试是正常背景描述，不应据此把整篇 Vibe Coding 文档误判为 Python 资料。

## 接入尝试与限制

### 用户最终确认的映射

仅将 `pigai`（批改文档模板）更新为用户提供的 [AI 编程批改文档](https://n6fo0mbcz6.feishu.cn/wiki/Rw60wziDIiXSbRk0B49cmkh1nyd?fromScene=spaceOverview&create_from=create_doc_to_wiki)。用户明确表示“剩下的都没问题”，保留其他 15 个入口。本次根据用户给定的对应关系替换，不再需要登录飞书核实映射，也不声称已在线阅读目标文档。

以下保留之前接入核对的历史，不构成本次替换的阻塞。

用户提供的 AI 编程知识库首页：[AI 编程知识库](https://n6fo0mbcz6.feishu.cn/wiki/ZgSAwtqziiaZrzkC3uGceYRsnvf?fromScene=spaceOverview)。后续核对以此为入口，不混用其他课程知识库。

用户补充的 [AI 编程 SOP 目录](https://n6fo0mbcz6.feishu.cn/wiki/WZ93w2ka7iUng9kIIcrcE6lwnHe?fromScene=spaceOverview)作为主要范围。首页的未认证读取实际重定向到 `accounts.feishu.cn/accounts/page/login`，确认链接本身并未授予账号访问。

- 插件目录搜索 `Feishu Lark 飞书` 未返回结果，不代表所有目录均不存在集成。
- 未认证的网页读取无法获取所试私有文档。
- 启动一个全新隔离配置、仅监听本机的自动化浏览器被自动审批拦截；工具返回 `blocked by policy`，没有更具体原因。没有改用其他命令绕过，也没有读取用户现有浏览器登录资料。
- 未获得任何账号密码、验证码、Cookie 或访问令牌；未成功建立飞书账号连接。

本轮最终采用用户直接提供的文档映射完成替换，无需继续登录或重新导出 SOP。未猜测文档 token，未把某一期问卷替换为另一过往营期示例。

## 网站提交与发布状态

网站分支 `codex/ai-grading-template-link-web` 在隔离工作树中从 `origin/main` 的 `2988761` 创建，只修改 `data.js` 一行。提交为 `c874544`，经 fork 推送后创建 [PR #3](https://github.com/linliliya/ai-camp-schedule/pull/3)。没有把开发分支里此前的 UI、SOP 或 Android 历史一并带入网站 PR。

该工作树的 `npm test` 9 项通过，与 `origin/main` 的完整文件比对确认仅替换用户指定 URL，`git diff --check` 通过。PR 可合并、状态 OPEN；当前 GitHub 账号 `Arvvinn` 对上游仓库的权限为 `pull: true, push: false, admin: false`，无法替所有者合并到 main 或触发原发布仓库的工作流。

最后只读检查正式网站 `data.js`，批改模板仍是旧 URL；不能声称网站已上线更新。仓库所有者合并该 PR 后，才会进入现有网站发布流程。证据：`artifacts/android/link-update/website-tests.log`、`live-site.json`。

## Android 1.5.1 验证与交付

网站本地源 `data.js` 和通过既有导出脚本生成的 `android/app/src/main/assets/schedule.json` 同步使用用户给出的完整 URL，保留 `fromScene`、`create_from` 参数。版本递增为 1.5.1 / versionCode 8；任务标识及所有排期、账号配置不变。

`android/build.ps1` 的网站测试 19 项、Android 单元测试 19 项通过，lint `No issues found.`，R8 签名 release 构建成功。导出一致性检查及 `git diff --check` 通过。此修改为固定 URL 更新，不新增镜像实现的单元测试，也不需要重新操作团队真实数据。

直接解包对比 1.5.0 和 1.5.1 的 `assets/schedule.json`：两者均为 16 个链接，只有 `pigai` 变化；其余 15 个入口与全部任务/配置完全相同。最终 APK 的资源也与当前源资源一致。`aapt dump badging` 确认包名 `cn.aurange.schedule`、versionCode 8、versionName 1.5.1。未在线登录验证私有文档正文；替换依据为用户明确提供的映射。

APK：`artifacts/android/camp-schedule-1.5.1.apk`，13,064,541 字节。

SHA-256：`966a9b6c44d1bede2892416e318289fb7ec9a2750e0d3d88f6b6e81f685c8123`。

`apksigner verify` 通过，v2 签名证书 SHA-256 为 `bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`，与 1.5.0 相同；可用于覆盖安装。没有改动或提交签名凭据。

构建、包内检查及签名证据：`artifacts/android/link-update/build.log`、`apk-check.json`、`signature.txt`。本轮没有重新进行真机、通知或 UI 动画验证。
