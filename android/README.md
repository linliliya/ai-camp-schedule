# 营期日程 · 安卓原生版

1.10.0 可在「添加营期」或「编辑营期」设置考试周。第1周课程提前开放，考前提醒下一周课程，考试周发备考话术，之后恢复同周课程。团队同步需要先在 Supabase 应用项目的 `setup.sql`。

1.9.1 将三项结营后回访合入首页的「持续跟进」，到期后出现，勾选完成后移出待办。

1.9.0 将成果调研移至第13周周一，首页新增独立“长期追踪”板块，按结营日加两周、两个自然月、四个自然月展示回访日期与完成状态，支持查看和复制对应话术。两周后的群问卷保留用户指定入口，两个自然月私聊高活跃学员，四个自然月追踪之前回访过的学员。回访到期后进入每日汇总，并持续保留到完成。话术共48条。

1.8.0 按2026-11-17确认的安排延长至第13周周日结营。第12周群发成果调研与毕业作品问卷，第13周周一、周三催交，周三23:59截止，周四选展示成员，周五发嘉宾邀请（每人5分钟），周五或周六测试彩排。典礼时刻由本期老师安排，可在详情设单次本机提醒。第3、11周周三老带新使用独立原文，第13周由直播后福利通知承担；金额和入口按本期填写，保留退款后返现退回说明。话术增至45条，两端统一通用日期表达。第13周沿用现有云端任务标识，移动的任务继续承接历史勾选。

1.7.4 话术采用通用表述，保留“本周六晚 20:00”“本周日 24:00”等常规安排。报名与实名认证截止时间以本期通知为准。本任务、全部话术及收藏统一使用此规则，复制前请核对本期安排并补齐链接等留空信息。

1.7.3 在话术子菜单增加搜索、“本任务 / 全部 / 收藏”。搜索覆盖 35 条标题 / 分类 / 正文；收藏按话术 ID 保存在本机，不保存旧日期。跨周选择课程 / 考试通知按该话术关联的教学周推导，显示对应营期 / 周次。网页新增图文草稿 JSON 导出 / 导入（含原图），手机本轮没有新增草稿文件备份入口。

1.7.2 按用户确认取消链接配置方案：话术打开时按该任务所属营期 / 教学周及休息周推导日期，链接与未确定的信息留空；子菜单及复制反馈提示“建议人工二次核实”。考试实际场次、实名截止以本期通知核对，不用提醒日期冒充。旧话术资源及任务标识保持兼容。

1.7.1 增加固定 SOP 话术，总计 35 条（课程提醒 13、考试通知 5、每周提醒 4、成长值 10 套，以及保留的毕业设计 1、老带新 2）。打开任务详情，点“查看话术”进入子菜单，再选择版本、展开和复制；第一层不铺开话术列表。网页采用同一内容和下级菜单。成长值从第 2 周周一起公布上周榜单，海报 / 名单由本人核对，考试旧日期与场次保留为本期待填项。

使用 Kotlin / Jetpack Compose / Material 3，支持 Android 8.0 及以上。1.5.0 沿用高对比的工作台界面：白色卡片、浅灰背景、深色正文、灰蓝主色和明确的状态色，阶段标签保留柔和点缀。中文使用 MiSans，少量英文与百分比数字使用 Lora 衬线字体。按 Oil UI 方法精修：进度摘要与周历合为一个规划区，同组任务使用连续列表与缩进分隔线，任务标题优先于营期信息，筛选器共享滑动选中底板。任务标题 15sp、说明 13sp，勾选保持 48dp 点击范围。100% 单独居中，空日程显示「—」。

浮动底部导航与同步胶囊使用实时背景毛玻璃，并加强遮罩以保持文字清楚。保留滑动周历和 Lucide 图标。勾选即时反馈，带轻回弹、圆环扩散和触感，任务淡出后列表自动补位。保存转到后台，同一任务保存时防重复提交，其他任务可继续勾选；失败时恢复已同步记录并提示。未确认状态只用于界面，提醒仍以已保存记录为准。本地快照的读、写、清除共享进程锁，避免不同存储实例同时操作 AtomicFile 时丢失已确认记录。

在「营期 → 添加营期」填写期数、选择开营日期，可选第 1～12 周的假期休息周，保存后自动生成日程并安排本地提醒，与网站共用团队排期。开营日期通常为周日，也允许其他日期。重复期数会提示；网络未能确认保存时，先刷新并核对是否已有该期，避免重复添加。演示模式和离线缓存保持只读。

## 随手记与个人 GitHub

1.7.0 支持多份本机草稿与提交记录，升级保留旧稿和图片。点击“草稿”可切换或另记一条；提交成功只清理当前稿。记录可刷新当前授权仓库的真实 Issue，GitHub 开关状态与入库进度分别看待。任务详情增加四类话术、只复制正文和待填项提示，也可另存一条包含任务来源的草稿。话术来源和差异见 `docs/task-scripts-notes.md`；Projects 看板尚需电脑完成权限配置，手机没有索要看板管理权限。

1.6.2 提供底部“随手记”，不登录营期账号也可记录文字、选择最多 9 张原图并保留草稿。通过 GitHub 设备授权连接个人账号，只提交到本人选择的已授权私有仓库。管理员现已注册 GitHub App 并核对 Device Flow 已开启；修复包内置公开应用编号，免去连接前的匿名查询。底部导航改为图标在上、标签在下，窄屏和大字体时保持单行。软件不部署授权服务器，也不使用电脑 CLI 的令牌。设置步骤见 [GitHub 收集说明](../docs/github-issue-setup.md)。

1.6.2 重绘蓝灰色折角日程卡启动图标，支持自适应蒙版与单色主题；随手记改用 Lucide 便签笔图标。输入框移除标签和占位话术，提交按钮紧跟图片区域，输入法显示时隐藏浮动导航，表单预留导航空间以避免遮挡。

网页端打开官方提交页添加图片，安卓则在 App 内直接上传原图和创建 issue。结果不明时先核对，正常失败保留全部草稿。日程团队账号与个人 GitHub 相互独立。

## 安装到小米手机

1.7.0 保留第 3、11 周周三的「发布老带新话术」，不设置具体时刻，进入每日汇总；第 12 周仍保留周日结营直播后的原提醒。延续 1.5.1 已确认的 AI 编程批改文档入口。将 `artifacts/android/camp-schedule-1.7.0.apk` 发到手机，点击安装；如系统提示，允许当前文件管理器安装应用。安装后用网站相同的邮箱与密码登录。也可以点击「先看看应用」浏览明确标记的只读示例，示例不会写入团队数据库。

在「设置」中开启通知和精准提醒，发送一条测试通知。小米手机在自启动管理中开启「营期日程」，并根据实际需要将应用省电策略设为「无限制」。系统入口因 MIUI / HyperOS 版本而异，应用内提供设置入口和手工路径。

默认北京时间每天 09:00 汇总未完成事项，有明确时刻的任务提前 15 分钟提醒，可修改。周日 24:00 按周一 00:00 处理；“上午”“直播前”等模糊时间不自动猜测，进入每日汇总。在任务详情可设一次性的本机提醒，该设置覆盖本任务的默认时间提醒，不影响团队排期。

任务资料优先交给已安装的飞书打开；飞书未安装或未能处理链接时交给浏览器。文档仍需飞书账号具有访问权限。

已同步的排期和提醒支持离线使用。后台同步由系统安排，云端改期、他人完成任务或权限变动，在成功同步后反映到本机；不能保证关闭应用时立即获知。打开应用可手动刷新。离线不允许修改共享完成状态。系统强行停止应用后需要重新打开，重启设备后会恢复调度。

## 构建

需要 Node.js 24+、JDK 17 或 21、Android SDK Platform 35。设置 `JAVA_HOME` 和 `ANDROID_HOME`；Gradle Wrapper 会下载 Gradle 8.11.1。

```powershell
npm ci
.\android\build.ps1
```

脚本导出最新 `data.js` / `config.js`，运行网站测试、安卓单元测试与 lint，构建启用 R8 优化的 release APK 并生成 SHA-256。首次在本机生成个人安装签名，后续重复使用；密钥和凭据位于 `%USERPROFILE%\.android\camp-schedule-signing\`，引用配置位于被 Git 忽略的 `android/signing.properties`。请妥善备份整个签名目录，后续覆盖更新必须使用相同签名。凭据不可提交仓库或发送给其他人。

其他系统也可配置相同内容的 `android/signing.properties` 后运行 `./gradlew -p android testDebugUnitTest lintDebug assembleRelease`。Properties 文件含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 四项。未配置签名时，使用 `assembleDebug` 构建调试版，不能覆盖已安装的 release 版。

```powershell
node scripts/export-android-data.js --check
.\android\gradlew.bat -p android testDebugUnitTest lintDebug assembleDebug
adb install -r artifacts/android/camp-schedule-1.7.0.apk
```

修改 SOP 后需要重新打包 APK；营期开营日期、休息周和完成状态通过现有云端同步，无需重新打包。应用不包含 service role key，不需要新增后台服务或推送平台。

覆盖安装新版前请在 `android/app/build.gradle` 递增 `versionCode`，并保留相同的签名。前台每 30 秒检查一次团队排期，后台通过系统调度定期同步。

## 模拟器集成验证

```powershell
.\android\gradlew.bat -p android assembleDebug assembleDebugAndroidTest
.\scripts\verify-android-background.ps1 -Device emulator-5554
```

此脚本仅允许 Android 模拟器，使用明确的测试夹具并清空测试应用账号；不会写入线上数据。它检查加密缓存、退出清理、权限撤销、登录过期、版本冲突、退出与同步竞争、通知点击路由，并在应用进程退出后等待实际系统闹钟通知。不得在存有真实账号数据的手机上运行这些测试夹具。

安装调试版或 release 版后，可运行 `./scripts/verify-android-ui.ps1 -Device emulator-5554` 检查演示页面、周历滑动、日期保留、筛选、详情与设置；应先退出真实账号。脚本只接受模拟器，截图前会检查前台包名、UI 层级与崩溃日志，结果保存在 `artifacts/android/ui-redesign/`。

## 文件分工与组件来源

- `scripts/export-android-data.js` 将网站内容导出为 `app/src/main/assets/schedule.json`，网站测试防止内容漂移。
- `ScheduleEngine.kt` 与 `ReminderPlan.kt` 负责日期、稳定任务键、跟进和提醒计算。
- `ScheduleRepository.kt` 使用 Supabase Auth、`my_app_access` 和现有版本化排期 RPC；`LocalStore.kt` 使用应用私有缓存和 Android Keystore AES-GCM 加密会话，退出时清除缓存与待发提醒。
- `ReminderScheduler.kt` 使用 AlarmManager；`SyncWorker.kt` 使用 WorkManager 做可延迟的联网同步。接收器恢复重启、升级和时间变化后的提醒。
- `*Screen.kt` 与 `TaskDetail.kt` 使用 Material 3 原生组件和惰性列表。周历采用 [Kizitonwose Calendar 2.6.1](https://github.com/kizitonwose/Calendar)，MIT 许可。图标采用 [Lucide](https://github.com/lucide-icons/lucide) 官方 SVG 的固定提交，由 `scripts/sync-lucide-icons.ps1` 转为原生 VectorDrawable，采用 ISC / 原 Feather MIT 许可。两者许可随 APK 打包，可在「设置 → 开源许可」查看。Kotlin 和 Compose 编译插件统一为 2.0.21；Compose 运行库为 1.7.6。Jetpack 组件遵循 [AndroidX Apache 2.0 许可](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)。
- `GlassSurface.kt` 使用 [Haze 1.1.1](https://github.com/chrisbanes/haze/tree/1.1.1)，Apache 2.0 许可。屏幕只录制一个共享背景层，玻璃控件单独取样；模糊输入缩至 0.66，正文不参与模糊，静止时没有持续运行动画。该版本遵循库的默认平台兼容策略，旧系统使用柔和底色回退。选用匹配 Kotlin 2.0.21 / Compose 1.7 的版本，避免为视觉效果升级整个编译工具链。纯 `Modifier.blur` 会模糊控件自身，不能直接作为背景毛玻璃。
- 中文字体为[小米官方 MiSans](https://hyperos.mi.com/font/zh/download/) 的原始 Regular/Medium OTF 文件，不裁剪、不修改字体。官网字体包 SHA-256 为 `b6aa1fc827035922612df8edf36e5609bca1c5441e25cd57572204569b7b81d9`。英文点缀和百分比使用 [Lora](https://github.com/cyrealtype/Lora-Cyrillic)，OFL 1.1 许可，固定源提交 `403b1a66ca2e79f81d749c5299559e168591a4df`。其气质参考 Claude，但不声称是 Claude 官网的专用字体；Lora 也列于 [Anthropic 的公开品牌建议](https://github.com/anthropics/skills/blob/main/skills/brand-guidelines/SKILL.md)。字体随 APK 离线提供，启动时无需下载。用 `python scripts/prepare-android-font.py`（需 fonttools、pypdf）下载原始文件及许可；MiSans 官方完整许可 PDF 与提取文本、Lora OFL 随 APK 打包，可从「设置 → 字体与组件许可」阅读。设置中保留 MiSans 使用声明。

本地构建、模拟器验证结果与尚需真机检查的项目记录在 `docs/audits/2026-10-19-android.md`、`docs/audits/2026-10-20-android-ui.md`、`docs/audits/2026-10-20-android-completion.md` 和 `docs/audits/2026-10-20-android-responsive-completion.md`。
