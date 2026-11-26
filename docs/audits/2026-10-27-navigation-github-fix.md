# 1.6.1 导航与 GitHub 连接修复

用户反馈：小米手机底部“随手记”换行，GitHub 配置无法连接。

## 修复

- 在 360dp、字体 1.3 倍的模拟器复现旧导航问题：“随手记”文本高度 156px，其余导航 96px。四项改为上方图标、下方单行文字；保留玻璃背景与选中动效，窄屏按测量结果调整标签字号。
- 用户已注册并安装 camp-sop-inbox-arvvinn。核验所有者 Arvvinn，权限为 Contents/Issues 读写、Metadata 只读；实际 Device Flow 请求可用。配置写入公开 client ID，减少正常登录对匿名应用发现接口的依赖。未嵌入用户令牌、应用密钥或私钥。
- 区分设备授权未开启、GitHub 限流、授权网络失败；后续错误不再被旧的“暂未启用”状态覆盖。草稿保持保留。

## 验证

- Android 单元测试 33 项，失败 0；网页测试 25 项通过；Android lint 与 release 构建通过。
- 自动导航检查 7 组全部通过：宽度/字体倍率为 360/1.0、360/1.3、390/1.3、411/1.0、320/1.3、320/2.0、360/2.0。四项标签均为完整单行。证据在 artifacts/android/issue-nav-fix/navigation-results.json 及同目录截图。
- 在专用 CampSchedule_API35 模拟器执行真实 GitHub HTTPS 设备授权请求，多次取得官方短码后取消等待。没有代用户确认账号授权、取得用户令牌或创建真实 Issue。
- 模拟器直连 GitHub.com 超时，配置为使用电脑现有代理后成功。这是本次测试环境设置，不会写入 APK，也不改变用户手机网络。
- 修复包延续原签名，支持覆盖安装。最终 APK SHA-256：37b5720e00498821ca2f8243bfc70a10e2fedc36c3903bf186a871577a7fac3a。
- 签名证书 SHA-256：bbb0293d2cf173d68c33cc3d1761b702c01d879a39c4b0f5ed87fc56287b6d38。

## 交付边界

安装包：artifacts/android/camp-schedule-1.6.1.apk。用户手机需要完成自己的 GitHub 设备授权并选择 sop-inbox，之后再验收真实提交。GitHub Projects 看板仍待电脑 project 权限。未提交或推送源代码，未部署网站。
