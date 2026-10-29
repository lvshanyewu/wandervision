# 3.4.0 构建与验证

2026-10-19，versionCode7。使用既有 NCNN/Android 工具链，未增加第二种推理运行时。新增模型作为可选择的实验选项，不默认同时运行。

- Android Studio ADB MCP 辅助执行签名 ARM64 `assembleDebug lintDebug --offline`，成功；日志保存在本机 `artifacts/mcp-3.4.0.log`。
- 最终源码 x86_64 `assembleDebug assembleDebugAndroidTest` 成功，API34 模拟器 Instrumentation：`PASS: 55 contract/model checks (synthetic inputs; no public uploads)`。包括十个模型各三种输入形状的原生加载/推理，报告类别、固定机位规则正负序列、合成车牌裁剪OCR调用及既有配对/上传契约。
- 六个新增检测器 TorchScript/NCNN 18个输入形状数值检查通过。原始 FP16 logits 可能有局部较大差值，同时检查概率与解码距离，详见 `experiment-export-verification.json`；不是准确率测试。
- OCR ONNX/NCNN 随机输入输出均为20×78，最大概率差0.0066656；字典77符号，未知索引拒绝。仅合成输入，非真实车牌读号准确率。
- 模拟器车牌模式拍照成功，无车牌时 `plate_recognition.status=no_plate`；JSON、同帧来源、时间、位置待定位状态、JPEG SHA/EXIF通过 `tools/check_report.py`。未发送该照片到公网。
- `tools/check_apk.py` 通过：仅 ARM64 ELF、十个检测器及OCR/字典，全部新增资产匹配固定哈希。
- `apksigner verify --verbose --print-certs` 通过 v2；证书 SHA256 `fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac`，沿用既有测试签名。
- `zipalign -c -P 16 4` 通过。

交付 APK：`wandervision-3.4.0-arm64.apk`，160158481字节，SHA256 `6fc9a1dfa71db00d37a76b4e93c9f99b9b6d3814f90b254c69b5f6d36c08806c`。构建产物与签名私钥不提交 Git。

模拟器结果不证明小米13摄像头、系统Vulkan、720p采集支持、真实GNSS、夜间模型效果、车牌号码准确率、发热或连续LIVE帧率；待用户覆盖安装后验证。实验规则仅固定机位手选ROI使用，不能作为违法判定。新字段保持schema1.0可选扩展，本轮未修改Web/API或部署。
