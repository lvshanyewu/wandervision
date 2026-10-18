---
schema_version: 1
type: "continuation"
id: "android-live-3.3"
title: "3.3 实时取景传输、手机仪表盘与路害模型"
module_id: "android"
status: "done"
next_step: "用户从v3.3.0 Release安装，在device-validation-3.3记录真机结果；最终长流连续帧率未验收"
updated_at: "2026-10-18T10:14:12+08:00"
external_checks: [{"checked_at": "2026-10-18T02:15:00Z", "service": "GitHub Releases", "status": "published", "evidence": "https://github.com/lvshanyewu/wandervision/releases/tag/v3.3.0；wandervision-3.3.0-arm64.apk与SHA256SUMS-3.3.0.txt已上传；source dd067ed；APK SHA256483a59306c54143214a3f2002df8a1c2d778e6ecb5f76c5f369bccfa8cf4795d"}]
---

## 范围与收尾
2026-10-18 用户授权LIVE、内置地图仪表盘、弹窗、路害模型；并在Web对话直接要求额度不足快速收尾、亲自实测。停止扩展与长测。
## 实现
COCO n/s保留，RDD2022 n/s实际转换并打包，原始DFL[N,68]、4类独立标签；工具固定公开模型包及校验和，无模型二进制提交Git。10组Torch/NCNN形状数值检查通过。
LIVE最新帧单槽640/JPEG55/80ms，显式默认可信TLS+主机名校验的HTTP1.1 chunked，UUID确认接收状态、55s/14MiB轮换、写watchdog、前台/手动/模型镜头切换关闭；无录像队列。App同源WebView手机地图已实际加载。弹窗尺寸提前设置/自身高度动画。本地仪表盘简化。
## 验证边界
34项模拟器契约/原生模型检查通过；最后HTTP1.1套接字传输实现构建通过，持续帧率未验收。旧单帧POST实测1.7–1.8FPS，长流发生过缓冲；不能宣称最终App达10–15FPS。Web独立合成图公网长流约12FPS、48服务测试通过，非App/真机证据。
最终本地ARM64 assembleDebug lintDebug成功0错误23警告；check_apk/apksigner/16KiB zipalign通过。APK SHA256 483a59306c54143214a3f2002df8a1c2d778e6ecb5f76c5f369bccfa8cf4795d，签名延续3.2。待发布。
