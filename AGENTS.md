# wandervision

- 小步交付，以可安装的 Android APK 为当前首要成果。当前阶段不开发后端/Web，也不未经用户真机反馈宣称摄像头或推理成功。
- Android 优先复用已验证的 NCNN YOLOv8 链路，保留第三方来源与许可。Material Design 3 为后续巡检界面方向。
- 用 Vervision 维护 `.handoff/`：长期事实写模块，当前进度/测试/发布结果写 continuation；重大阶段、阻塞、发布后更新记录。
- 修改 Android 后执行相关构建及必要检查。首版基线：`assembleDebug lintDebug`、`tools/check_apk.py` 和 apksigner。真实 ARM64 摄像头、检测效果、FPS 和稳定性由用户安装后验证。
- 原生下载产物、构建产物、机器路径、签名文件不得提交。依赖版本和校验和由 `tools/prepare_native.py` 固定。
