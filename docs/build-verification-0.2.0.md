# 0.2.0 验证与交付记录

日期：2026-10-16（Asia/Shanghai）。应用源标签 `v0.2.0`，提交 `bae25f3b8c56c92e848dc3846f2f04d2585014e5`。

## 最终发布文件

- [GitHub Release](https://github.com/lvshanyewu/wandervision/releases/tag/v0.2.0)；[APK下载](https://github.com/lvshanyewu/wandervision/releases/download/v0.2.0/wandervision-0.2.0-arm64.apk)。
- 40,561,191 bytes；SHA-256：`d7a68b345f902102cfc846bb9f7f4295e7e7ac1ea3a51a2b412cba4148b9ac4e`。
- 已从Release下载到本地 `artifacts/wandervision-0.2.0-arm64.apk`，与发布的SHA256SUMS一致；Nano和Small的模型内容与源码一致，证书与本地专用测试签名相同。

## 构建与包检查

- Windows 通过已安装 ADB MCP `gradle_build` 完成最终 ARM64 `assembleDebug lintDebug --offline`。lint 0 错误、14 条非阻塞警告（兼容目标API、仅ARM64、备份规则、自绘分配/构造器等）；未屏蔽错误。
- `tools/check_apk.py` 检查 ARM64 ELF、manifest/dex、Nano与Small两对非空模型文件；`apksigner verify`、`zipalign -c -P 16 -v 4` 通过。
- aapt确认版本0.2.0/2、独立包名 `io.github.lvshanyewu.wandervision`，只请求CAMERA权限；没有网络、GPS、读写全部相册权限。
- [最终标签CI](https://github.com/lvshanyewu/wandervision/actions/runs/38916332215)：编译/lint、包检查与签名验证，自动发布。
- 专用测试证书SHA-256：`fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac`。私钥未提交；CI使用仓库加密Secrets。本地可通过环境变量使用同一测试身份。首次从0.1.0升级可能需要卸载旧包，随后相同签名可覆盖。

## 实际运行检查

用单独 `-PtestAbi=x86_64` 开发构建，在 Android34 / 1080×2400 / 420dpi 模拟器检查；交付APK仍只含ARM64。

- 启动、真实原生帧率/耗时回传、Nano与Small切换并重开相机。
- 竖屏与横屏重新布局；横屏控制区在右，恢复竖屏继续取景。
- 纯取景模式关闭推理，耗时和目标数显示“—”；仪表盘显示真实快照与采样帧率趋势。
- 快门写入系统相册、记录Uri与模型/模式/尺寸等信息，截图详情能回读图片。
- 首次回读发现仅复制Window会让相机区域变黑；已修复为Window+Surface显式复制并合成HUD。再次保存JPEG，视觉核验包含彩色相机测试图和完整UI。证据：本地 `artifacts/emulator-captures/WV_20261016_142943_539.jpg`。
- 模拟器配置 `hw.camera.front=none`；切前摄正确显示失败提示，不崩溃，可切回后摄恢复。这只验证失败路径，不代表前摄镜像已验证。
- 上述流程未观察到AndroidRuntime崩溃。截图显示、模型加载和模拟器FPS均不能证明手机检测质量或性能。

## 待真机确认

小米13上的画面/框/标签方向（竖屏、左右横屏）、前摄镜像、Small与Nano夜间差异、CPU/Vulkan表现、截图效果、发热和持续稳定性。完整测试清单在 [发布说明](releases/0.2.0.md)。

0.1.0 的用户反馈只确认正常启动与夜间车辆分类尚可；0.2.0改变了预览方向和UI，不能沿用为新版全项通过。GPS、服务端同步、事件去重/数据库、后端与Web仍未开发。
