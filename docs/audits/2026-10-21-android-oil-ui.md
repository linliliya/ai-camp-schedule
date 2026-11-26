# Android 1.5.0：Oil UI 精修与网站隔离核对

## 范围与依据

用户要求使用 `https://github.com/oil-oil/dsh-oil-ui` 优化手机端，并询问此前网页修改是否使用分支、正式网站是否受影响。

该仓库是 DeepSeek Harness 插件，不是 Android 组件库。按其官方 README 指向，使用 Skill Installer 安装宿主中立的上游 [oil-ui](https://github.com/oil-oil/oil-ui)，读取 0.23.8 的 SKILL、视觉语言、动效、素材及视觉评审协议。当前继续已获用户认可的高对比工作台方向，按其“已选方向，从步骤 2 进入”路径执行。没有安装 DeepSeek Harness，没有新增 App 运行依赖、WebView、网络字体或付费服务。

## Git 与正式网站

- 开始时工作区干净，处于 `codex/sop-source-review`，提交 `7526681`。此前网页修正及后来的 Android 提交都在这一条开发分支上，并非各自独立。
- 本次创建 `codex/android-oil-ui`，保留旧分支和 1.4.0 APK；本次提交的差异仅包含 Android 界面、版本和文档。分支基于已有开发版本，历史仍含较早的网页修正；将来若向 main 合并，需要按实际发布范围检查完整差异，不能仅凭分支名称推断只含 Android。
- `git ls-remote --heads origin` 返回唯一 `main`，指向 `2988761eda1e768c362cce2fa6bca01193b492f5`。本次没有 push、合并或部署。
- 实际只读获取 `https://internal.aurange.cn/ai-camp-schedule/` 的入口、`data.js`、`config.js`、`cloud.js`，四个文件均与 `origin/main` 字节一致，均不同于本地开发版本。因此，检查时正式站点未包含这些本地网页/Android 开发提交。证据：`artifacts/android/oil-ui/website-live-check.json`。
- 从 Android 开发起点 `d8984d9` 至本次基线，网页前端文件没有后续变化；本次再次逐个核对所有非 Android、非 docs 的已跟踪文件，内容与 `7526681` 一致。
- App 与网站共用生产营期及完成记录，真实账号修改数据会互相同步。UI 分支不能隔离这类预期的数据共享。本轮只用演示或假网络夹具，无生产营期写入。

## 界面与动效

北极星：打开 App 能迅速读到要做的事。保留 MiSans、少量 Lora、深色正文、蓝色动作、语义状态色、圆圈完成和底部 Haze 导航。

进度与周历合为一个白色规划区，去掉内外重复描边。同组任务改为连续白色行与缩进分隔线；标题前置，描述居中，营期/阶段和时间放在底部。营期日程复用同一语法。逾期依靠明确的分组和红色截止时间传达，不再为每条任务描红框。

筛选器使用同一块滑动选中底板，弹簧阻尼 .9、刚度 500，文字颜色 160ms 过渡。圆圈完成、180/200ms 勾选与填充、320ms 延后归组及列表位移动效沿用现有实现，未引入页面级等待或入场延迟。动效状态通过实际操作核对；没有测量小米真机帧率，也没有把静态截图当作流畅度测量。

窄屏大字体首次检查发现原统计长句使“项”单独换行，改用 FlowRow 保持每项统计短语整体排布。100% 的独立居中容器、空日程“—”及可访问语义保留。

## 独立视觉评审

按 Oil UI“存量已有界面润色评审”进行一轮隔离评审，不评分。评审者仅接收实际截图、产品任务、固定色板和约束，不接收源码及制作过程。

保留项：标题前置、信息分层、连续列表、圆圈、周历和导航。主要意见是首屏任务出现偏晚、筛选与分组纵向占用偏多，并要求核对导航底部收尾。

据此把 Today 状态操作并入标题行，日期/英文/日期选择放第二行；顶部预留从 60dp 改为 8dp，分组及筛选间距相应收紧。内联状态不再从包含自身的滚动源采样，使用普通白色 Surface；底部导航及其他页面的状态浮层继续使用 Haze。正常字体首屏第三条任务的信息可完整显示在导航上方。滚动到底的实际核对确认页尾及最后任务能完全移出导航遮挡区。

删除了重复的“我的日程”标题、逐条任务描边和重复进度/日历边框。没有删除任务名称、说明、归属、时间或实际操作。没有新增插画或装饰英文。

## 验证

- 最终 `android/build.ps1`：网页测试 19 项、Android 单元测试 19 项通过；lint `No issues found.`；R8 签名 release 成功。`release-build-final.log`。
- 设备夹具检查 18 项通过：完成控件 5、营期创建 2、慢网络/失败恢复/权限/冲突 5、云端协议 6。夹具隔离检查另 1 项通过；没有调用真实账号写入。`device-tests.txt`。
- 最终布局的 UI 操作脚本连续 3 次通过：原生周历、跨周滑动、跨页保留日期、待办/完成筛选、任务详情及设置。`ui-final-1.log` 至 `ui-final-3.log`。
- 实际查看 1080x2400 常规布局及 900x2000 / 字体 1.3x 的 Today、筛选、营期与设置。最终统计布局及紧凑标题再次取图检查，字号与显示尺寸已恢复默认。
- 最终签名 release 从 1.4.0 覆盖升级，保留演示状态及提前 30 分钟偏好，验证后恢复 15 分钟。Today、营期、列表末端可达、页尾位于导航之上，无本应用崩溃。`upgrade.log`、`release-crashes.txt`。
- `node scripts/export-android-data.js --check`、`git diff --check` 通过，网页代码与数据库脚本保持本次基线内容。

截图均在确认 `cn.aurange.schedule/.MainActivity` 前台与 UI 包名后保存。独立模拟器为 CampSchedule_API35，未使用其他应用的画面。证据位于被 Git 忽略的 `artifacts/android/oil-ui/`，交付截图为 `today-release.png`、`camps-release.png` 和 `list-end-release.png`。未连接小米真机；未在本轮重新测量原有后台通知或飞书账号权限。

## APK

`artifacts/android/camp-schedule-1.5.0.apk`，13,064,497 字节，versionCode 7，Android 8.0+。

SHA-256：`f613bcd14b4f5ac8761b64ca0a84171c4089846c5856be3e2556c7fc48ee60a2`。

`apksigner verify` 通过，v2 证书 SHA-256 为 `bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`，与旧 APK 一致。签名凭据不入库。
