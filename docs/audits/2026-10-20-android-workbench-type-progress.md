# Android 1.4.0：工作台、MiSans / Lora 与完成进度

## 界面与字体

按用户批准的高对比工作台方案，使用白色卡片、浅灰背景、深色正文和灰蓝主色。逾期截止时间使用深红、完成使用绿色，阶段标签保留柔和配色。任务标题 15sp、说明 13sp、元信息至少 12sp，任务卡片圆角 14dp。导航保留 Haze 背景模糊，加强白色遮罩保证标签易读。

从小米和华为官网下载 MiSans / HarmonyOS Sans，实际比较中文及数字后，选择 MiSans Regular / Medium。使用官方原始 OTF，不裁剪、改名或改字重；替换原有 Noto 派生子集。用户追加喜欢 Claude 的英文气质，采用独立授权的 Lora 点缀 Today 标题和百分比，并非声称使用 Claude 官网专用字体。

参考：[MiSans 官方下载](https://hyperos.mi.com/font/zh/download/)、[完整许可](https://hyperos.mi.com/font-download/MiSans%E5%AD%97%E4%BD%93%E7%9F%A5%E8%AF%86%E4%BA%A7%E6%9D%83%E8%AE%B8%E5%8F%AF%E5%8D%8F%E8%AE%AE.pdf)、[HarmonyOS 官方资源](https://developer.huawei.com/consumer/cn/design/resource)、[Lora 官方项目](https://github.com/cyrealtype/Lora-Cyrillic)、[Anthropic 公开字体建议](https://github.com/anthropics/skills/blob/main/skills/brand-guidelines/SKILL.md)。后者包含 Lora，仅用作风格参考，未直接套用其品牌方案。

MiSans 包 SHA-256：`b6aa1fc827035922612df8edf36e5609bca1c5441e25cd57572204569b7b81d9`。

- Regular：`8e9caa6f34f27c6baad1ecf0058cc13e1801efff698bc23e6e0b095d9a9ed9cb`
- Medium：`f9239c75fa0a661b788916250c6d4ad0d52d8a643f1ce2a9142f6588d4d2cb64`
- Lora：`822a6621ccbe8d97d20ac88c1c41f5615c9c2c202eaa75f272cd452aac6475a7`，固定上游提交 `403b1a66ca2e79f81d749c5299559e168591a4df`。

APK 内保留 MiSans 官方完整许可 PDF、版权和提取文本、Lora OFL，设置中声明使用 MiSans，并提供「字体与组件许可」。字体离线随包提供。原始完整字体使安装包增加至约 13 MB，没有运行时下载字体。

## 进度修复

旧控件固定为 78dp 圆环，百分比和「完成」组成两行，但分别改字号后仍继承同一默认行高。1.5 倍字体时，三位数字碰到环线，数字文字框中心比外框中心偏上 45.5px。新增原生回归先在旧代码失败，证据为 `baseline-progress.txt` 和 `progress-before.png`。

使用 `TodaySummary` 替换旧彩色大卡片和圆环：紧凑摘要、独立居中的百分比、完成/逾期/跟进数量和细横向进度条。百分比胶囊按内容扩展，显式行高、去除字体额外 padding，保证放大字体时仍居中。100% 使用绿色完整状态；无任务显示「—」，避免误报全部完成。整数计算百分比，避免 29/100 经浮点截断误成 28%。

新增设备检查覆盖 29%、99%、100%、空日程，以及 1.5 倍字体下 100% 的水平/垂直中心误差不超过 1.5px。快照依旧来自界面即时状态，未确认状态不写入提醒缓存。

## 连续勾选回归中发现的缓存竞争

诊断记录显示，两项云端确认均成功、无权限或网络错误，但缓存及 UI 只有第一项。系统日志出现 `snapshot.json.new` 重命名失败。不同 LocalStore 实例的实例锁不能互斥同一文件；[AtomicFile 官方文档](https://developer.android.com/reference/android/util/AtomicFile)要求调用方自行同步，读者可能清理另一个写者尚未完成的临时文件。

添加两个存储实例并发读写的设备回归，旧代码稳定失败。快照读、写和退出清理共享一个进程锁后通过。原先偶发失败的双任务慢网络回归额外连续运行 3 次均通过，保持即时勾选及异步网络保存。

## 验证证据

- `android/build.ps1`：网站 19 项、安卓单元测试 19 项通过，lint `No issues found.`，R8 release 成功。
- 原生设备检查 20 项通过（包含夹具隔离、共享缓存竞争、完成控件 5 项、营期表单 2 项、慢网络/权限/冲突 5 项、云端 6 项）。另有 3 次连续慢网络复核通过。
- `verify-android-background.ps1`：10 项集成检查通过，应用进程退出后实际 AlarmManager 通知发出。
- 调试版 `verify-android-ui.ps1` 通过：周历、日期保留、筛选、详情及设置。截图保存前检查本应用包名和崩溃缓冲区。
- 最终签名 release 的同一 UI 检查通过。1.3.0 覆盖升级 1.4.0，保留演示状态及提前 30 分钟偏好，验证后恢复 15 分钟。
- 最终 release 在 1.3 倍系统字体下可读，中文/英文标题、营期页和底部导航显示正常；设置中的 MiSans/Lora 声明与许可弹窗可见。测试后恢复系统字体 1.0。
- 14 组文字及控件颜色对比检查通过；其中检查的文字组合最低 4.8:1，待办圆圈另按控件 3:1 验证。此项是指定颜色组合检查，未声称覆盖所有透明叠加与屏幕情形。
- `node scripts/export-android-data.js --check`、`git diff --check` 通过。

使用独立的 CampSchedule_API35 模拟器，夹具期间暂停恢复接收器并取消后台任务；不写入生产营期。证据保存在被 Git 忽略的 `artifacts/android/workbench/`。尚未连接小米真机，不宣称真机帧率、锁屏或触感已验证。

## 安装包

`artifacts/android/camp-schedule-1.4.0.apk`，13,064,497 字节；包名 `cn.aurange.schedule`，versionCode 6，最低 API 26，target API 35。

SHA-256：`e8054d49750940dd9bec95e63e987902e86f3e177f518958e5c0a79ce729aed4`。

`apksigner` 校验通过，v2 签名证书与旧版一致：`bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`。签名凭据不进入仓库。
