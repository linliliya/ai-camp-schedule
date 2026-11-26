# 安卓 1.2.0 即时勾选与完成动效验证

日期：2026-10-20（Asia/Shanghai）。用户报告勾选需等待加载，并要求参考待办应用的完成交互。

## 原因与修复

原实现把单条完成动作交给全页 `run`：先检查权限、写入，再检查权限并刷新整份排期，最后更新界面。可控慢网络测试在写入请求暂停时稳定复现圆圈未勾选，回归断言 `A slow server must not delay the check animation` 在修改前失败。

现在点按同步写入内存中的逐任务显示状态，圆圈与统计立即反馈，后台仍用原权限检查、原 RPC 和捕获的版本保存；成功路径从四次请求减少到两次。全页 busy 不参与单条保存，同一任务保存中防重复，其他任务可以继续勾选。顶部仅显示保存数量。成功结果按版本合并，失败恢复已同步记录并提示；冲突保留服务端版本，权限撤销和退出会失效旧请求的界面回调。

未确认的状态不写入本机提醒缓存，也不是离线发送队列。重新打开应用仍以云端确认记录为准。服务端确认后才更新提醒缓存与手动提醒；原有排期、任务键、权限和通知规则保持。

圆圈加入轻触感、轻回弹及约 300ms 的细圆环扩散；勾线后暂留约 320ms，再用 Compose 原生列表淡出和弹性补位。初次展示已完成项不重复播放。参考 Apple 的圆圈完成交互和 Android 官方列表动画资料，动效为本应用设计，没有新增依赖。

## 验证结果

- `android/build.ps1`：网站 19 项、安卓单元测试 18 项通过，lint 无问题，R8 release 成功。新增版本合并测试验证旧响应不能覆盖新版本、另一项确认记录或较新的营期日期。
- `CompletionLatencyTest`、`CompletionControlTest`、`CloudIntegrationTest` 共 12 项设备回归通过。慢网络下在同一事件里更新勾选、两个不同任务可先后点按、同一项不重复提交，且确认前持久化缓存仍未完成；覆盖断网回退、版本冲突重试、权限撤销、退出竞争、辅助功能和任务退场后的空状态。
- `scripts/verify-android-background.ps1`：7 项设备检查通过；应用 PID 退出后，实际 AlarmManager 提醒发出，无需重新打开界面。
- `scripts/verify-android-ui.ps1` 在最终 release 上通过：登录/演示、周历滑动、切页保留日期、完成/待办筛选、详情与设置。截图保存前核对前台包名和崩溃记录。
- 1.1.1 release 覆盖升级至 1.2.0 成功；演示状态及提前 30 分钟设置保留，随后恢复默认 15 分钟。
- `node scripts/export-android-data.js --check` 通过。网站源代码未修改。

设备验证使用独立的 `CampSchedule_API35` 模拟器。慢网络测试注入本地 HTTP 替身并禁用该测试模型的后台同步；假账号夹具期间暂停恢复接收器，清理账号及 WorkManager 任务后恢复组件默认状态，避免升级/授权广播为假账号启动真实同步。生产默认仍开启后台同步，接收器行为未修改。

证据位于被 Git 忽略的 `artifacts/android/responsive/`，其中 `baseline-latency.txt` 为修改前失败，`device-tests.txt`、`background-run.txt`、`release-build.txt`、`ui-run.txt` 和 `release-upgrade.txt` 为最终验证。测试未使用真实账号，不向线上写入排期。

## 交付

包名 `cn.aurange.schedule`，1.2.0 / versionCode 4，最低 Android 8.0，target SDK 35。APK：`artifacts/android/camp-schedule-1.2.0.apk`，1,611,078 字节。

SHA-256：`b75af3609b84b70d41519343adf1b77ee1f7482bdc7ce86c00b061962d979fed`。`apksigner verify --verbose --print-certs` 验证 v2 签名，证书与 1.1.1 相同：`bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38`。签名凭据不进入仓库。

未连接真实小米手机；本次验证响应不依赖服务器返回，没有测量或宣称真机帧率。真实触感、锁屏与飞书文档权限仍需在手机检查。
