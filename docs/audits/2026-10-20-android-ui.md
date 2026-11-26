# 安卓 1.1.0 界面改版验证

日期：2026-10-20（Asia/Shanghai）。用户已确认暖白与靛蓝的推荐方案并授权实现。

## 范围与组件

重做登录、今日、营期、设置和任务详情。增加可滑动周历、完成进度环、任务分组与筛选、营期切换卡片及 Lucide 线性图标。日期、筛选与页面滚动在切换底部导航时保留；演示预览保持明确标记、只读，不写入云端。

使用 [Kizitonwose Calendar 2.6.1](https://github.com/kizitonwose/Calendar/tree/2.6.1) 的原生 Compose 周历；Kotlin 与 Compose 编译插件统一为 2.0.21，Compose 运行库为 1.7.6。早期试用的 Calendar 2.5.2 引用了运行库已移除的 `SnapPositionInLayout`，在模拟器进入演示页时复现崩溃；升级到 2.6.1 后以实际页面启动与跨周滑动检查回归。截图流程会检查前台应用包名、UI 层级包名及 crash buffer，避免误截模拟器里其他应用。

[Lucide](https://github.com/lucide-icons/lucide) 图标按提交 `02eb9161b57917ccaa262f1bf070507cddde3b08` 下载，由 `scripts/sync-lucide-icons.ps1` 转成 VectorDrawable，R8 和资源裁剪移除未使用资源。Calendar MIT、Lucide ISC / 原 Feather MIT 许可均随 APK 打包，在设置中可查看。

排期引擎、任务键、账号权限、云端 RPC 和本地提醒逻辑未改。网站源代码未修改。

## 已运行验证

- `android/build.ps1`：网站 19 项通过；安卓单元测试 16 项通过，含新增 3 项分组和筛选测试；`lintDebug` 无问题；R8 release 构建成功。
- `node scripts/export-android-data.js --check`：SOP 和公开配置与网站一致。
- `scripts/verify-android-background.ps1`：7 项设备集成测试通过；进程退出后实际系统闹钟发出通知。涵盖加密缓存、退出清理、已完成抑制、通知打开真实 Activity、权限/令牌/离线及版本冲突、退出与同步竞争。使用本地替身传输，未写入线上数据。
- `scripts/verify-android-ui.ps1`：演示页面和周历实际渲染、滑动一周、切换页面后保留日期、回到今天、完成/待办过滤、详情底部操作、设置提醒对话框通过；保存经过包名检查的截图。
- 手工模拟器检查：日期选择器跨月选择 11 月 5 日，页面日期和周历一致；返回今天；切换第二个营期及开营阶段。登录、今日、营期、详情和设置截图已目视检查。
- 在 360dp 宽度、1.3 倍字体下检查今日、营期和设置，文字及导航无重叠，内容可滚动；测试后恢复模拟器尺寸与字体。设置中的开源许可可正常打开。
- 在模拟器重新安装原 1.0.0 release，进入演示并将提前提醒设为 30 分钟，再用 `adb install -r` 覆盖到 1.1.0，安装成功，演示状态与 30 分钟设置均保留。随后恢复默认 15 分钟。最终 release 的首页、营期与周历滑动正常，crash buffer 无应用崩溃；最终截图为 `today-release.png` 和 `camps-release.png`。

证据保存在被 Git 忽略的 `artifacts/android/ui-redesign/`，设备和后台通知记录在 `artifacts/android/`。

## 安装包与边界

包名 `cn.aurange.schedule`，版本 1.1.0 / versionCode 2，Android 8.0 及以上，target SDK 35。release 未启用 debuggable，保留 ARM64、ARM32、x86、x86_64 支持。

`apksigner verify --verbose --print-certs` 通过 APK Signature Scheme v2。证书 SHA-256 与 1.0.0 相同：`bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`。签名密钥和凭据不进入仓库。

APK：`artifacts/android/camp-schedule-1.1.0.apk`，1,594,694 字节。SHA-256：`1d241f049b5c4f6595a7e8d2b8ae3fd42b6e49637c22a542595407fc4cb774cc`。

未连接真实小米手机，未使用真实账号，模拟器也未安装飞书。HyperOS / MIUI 锁屏、休眠、重启与真实账号、真实飞书文档权限仍需在手机验收。云端改动成功同步后才会反映到本机，不承诺后台实时推送。
