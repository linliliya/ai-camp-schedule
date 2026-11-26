# Android 1.3.0：莫兰迪界面与添加营期

## 最终行为

原生安卓界面使用鼠尾草绿、灰蓝、暖米白及少量灰紫/陶土阶段色。移除左上角品牌图标和「营期日程」栏，页面标题保留。内置 Schedule Sans SC（基于 Noto Sans SC 的静态子集），统一中文字体与字重，日期选择器和系统时间控件使用中文上下文。

底部导航和右上同步胶囊使用 Haze 1.1.1 实时取样屏幕背景，前景文字和图标保持清楚。共享一个源层，模糊输入比例 0.66，半径 22dp；只有交互和滚动时更新。底部选中胶囊通过弹簧移动，位置读取放在布局阶段。列表保留随系统导航栏高度变化的末尾空间。对旧系统遵循 Haze 默认回退策略。

原生「营期 → 添加营期」支持期数 1～10000、必选开营日期（含年份，2000～2100）及可选休息周 1～12。周日为提示，允许其他日期，与网站一致。复用 `save_schedule_camp`、`my_app_access`，不新增数据库接口。服务端确认后立即将返回营期缓存，避免因二次刷新失败导致重复添加；按既有引擎生成筹备、开营、课程和休息阶段，更新 Today 与 AlarmManager 排程。成功后选择新营期并滚动到对应卡片。失败保留表单；期数冲突给出说明，网络未确认时提示刷新核对。演示与离线只读。

## 调研与取舍

- [Haze 官方项目](https://github.com/chrisbanes/haze)、[1.1.1 用法](https://github.com/chrisbanes/haze/blob/1.1.1/docs/usage.md)、[该版本依赖](https://github.com/chrisbanes/haze/blob/1.1.1/gradle/libs.versions.toml)：匹配 Kotlin 2.0.21 / Compose 1.7，采用 source/child 背景取样，避免为视觉效果升级整个工具链。渐进模糊有额外开销，此处采用均匀模糊和轻边缘高光。
- [Android RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)：直接给控件设置 blur 会影响其内容；自行管理背景捕获层也可实现，但增加坐标、裁剪和平台兼容维护。采用开源组件的共享背景层。
- [Noto Sans SC](https://github.com/google/fonts/tree/main/ofl/notosanssc) 与 OFL 1.1 许可：离线嵌入常规/半粗两种字重，GB2312 常用字加所有内置 UI/SOP 字符，其他字符由系统回退。子集另命名，附完整许可；来源及 SHA-256 固定检查见 `scripts/prepare-android-font.py`。

## 验证

- `android/build.ps1`：网站 19 项、安卓单元测试 19 项通过，lint 为 `No issues found.`，R8 release 成功。
- `CampCreationTest`、`CloudIntegrationTest`、`CompletionLatencyTest`、`CompletionControlTest` 最终 16 项设备测试通过。覆盖真实原生日期表单、休息周、必填校验、防重复提交、新营期选中和缓存、Today/提醒计划、重复期数、断网、访问撤销，以及原有完成交互与慢网络即时反馈。测试传输为本地替身，不向生产创建营期。
- `verify-android-background.ps1`：9 项集成检查通过；实际应用进程退出后，AlarmManager 通知正常发出。
- `verify-android-ui.ps1` 在最终签名 release 通过：周历切换、日期跨页保留、筛选、详情、设置和受包名保护的截图。无应用崩溃。
- 1.2.0 覆盖安装 1.3.0，演示状态和提前 30 分钟偏好保留，验证后恢复 15 分钟。
- 最终 release 静置 5 秒，`dumpsys gfxinfo` 新增渲染帧为 0。该结果仅证明该模拟器场景没有持续空转绘制，不表示小米真机帧率。
- 1.3 倍系统字体下页面标题及三个导航项均可读；最后一项内容可以完整滚至浮动导航上方。测试后恢复系统字体 1.0。
- `node scripts/export-android-data.js --check` 与 `git diff --check` 通过。网站和排期公式保持。

设备为独立 `CampSchedule_API35` 模拟器，假账号期间暂停恢复接收器并取消 WorkManager。首轮表单检查误读了文字子节点的 selected 属性，改为验证控件父节点的 checked 属性；最终 16 项通过。中间一次慢网络设备测试出现超时，后续完整回归通过；未据此宣称真机性能或消除所有设备波动。

构建、设备检查、升级和布局证据保存在被 Git 忽略的 `artifacts/android/morandi/`。未连接真实小米手机，真实触感、锁屏、自启动策略与飞书文档权限未作真机验证。

## 安装包

`artifacts/android/camp-schedule-1.3.0.apk`，4,484,317 字节，包名 `cn.aurange.schedule`，versionCode 5，最低 API 26，target API 35。

SHA-256：`4bdcac032962f72b844f6f9e42a934e5f5c7883bf81f058761b5be40e5429e07`。

`apksigner verify --verbose --print-certs` 通过，v2 签名；证书与既有版本一致，SHA-256 为 `bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`。签名凭据不进入仓库。
