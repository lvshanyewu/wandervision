# 0.1.0 构建与交付记录

日期：2026-10-16（Asia/Shanghai）。代码标签 `v0.1.0`，应用源提交 `f921fee080cad1750f03d33572f0794f7afb9255`。后续 main 中的交付记录与 MCP 桥接配置改进不改变此 APK 的应用代码。

## 发布产物

- [GitHub Release](https://github.com/lvshanyewu/wandervision/releases/tag/v0.1.0)
- [直接下载 APK](https://github.com/lvshanyewu/wandervision/releases/download/v0.1.0/wandervision-0.1.0-arm64.apk)
- 本地同一份产物：`artifacts/wandervision-0.1.0-arm64.apk`
- 大小：19,698,170 bytes（约 18.79 MiB）
- SHA-256：`fad588cf28f9e00bc03fdb95fe4f3ca58788e3c1d472077d1cfbe3ad70eeccc7`
- 与 Release 的 `SHA256SUMS.txt` 一致，APK v2 签名有效。

## 已通过

- [标签构建 38906201825](https://github.com/lvshanyewu/wandervision/actions/runs/38906201825)：SDK/NDK 准备、原生依赖校验、`assembleDebug lintDebug`、ARM64/模型/ELF 打包检查、签名验证及自动预发布均成功。
- Windows 本地：通过已安装 ADB MCP 0.23.0 的 `gradle_build` 完成 `assembleDebug --offline`（29 秒），及 `lintDebug --offline`；lint 0 错误、6 条非阻塞警告。日志保存在本地 `artifacts/mcp-build.log`、`artifacts/mcp-lint.log`。
- 本地 APK 同样通过模型、ARM64 ELF 和签名检查；位于 `android/app/build/outputs/apk/debug/app-debug.apk`。因平台工具和测试签名环境不同，本地构建与 Release 文件哈希不同，发给用户的本地 artifacts 文件直接下载自 Release。
- 对最终 Release 文件执行 `aapt dump badging`：包名 `io.github.lvshanyewu.wandervision`，版本 0.1.0/1，minSdk24、targetSdk35，仅 CAMERA 权限，ABI `arm64-v8a`，启动 Activity 正确。
- `zipalign -c -P 16 -v 4` 通过；模型二进制与仓库来源文件一致；许可证包含于 APK assets。
- NCNN/OpenCV 下载包通过固定 SHA-256；NDK29 官方 Windows ZIP 通过官方 SDK 仓库清单 SHA-1，已安装到本机 SDK；CMake3.31.5 已准备。
- Vervision 项目格式与来源路径校验通过。

本地 lint 曾发现未跟踪 `local.properties` 的盘符转义问题，已修正并强制重新分析后通过；没有屏蔽 lint 错误。余下警告涉及 targetSdk35、仅 ARM64、默认图标、备份规则、重复 label 和布局硬编码文字，作为后续界面阶段改进项。

## 待用户验证

没有连接 ARM64 手机；现有 x86_64 模拟器不能替代本 APK 的 ARM64 摄像头与推理测试。本轮不声称完成设备安装、相机打开、识别准确性、FPS、GPU 性能、发热或长时间稳定性验证。按 [真机测试说明](releases/0.1.0.md) 安装反馈后再进入下一轮迭代。
