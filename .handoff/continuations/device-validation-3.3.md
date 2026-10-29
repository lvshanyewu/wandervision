---
schema_version: 1
type: "continuation"
id: "device-validation-3.3"
title: "小米13验收3.3：LIVE与四模型"
module_id: "android"
status: "open"
next_step: "用户下载安装3.3.0，测试四模型/真实道路及LIVE持续传输，反馈FPS与异常截图"
updated_at: "2026-10-18T10:13:49+08:00"
---

## 用户接管实测
按用户额度不足快速收尾要求，保留以下真机验收：COCO/RDD Nano/Small×320/480/640、CPU/Vulkan的检测效果/方向/性能/发热；LIVE首开/一键关/后台停止/切模型停止、App内手机地图、照片原JSON/定位及手动上传。
## 明确边界
34项模拟器契约/原生模型检查通过；最后HTTP1.1 TLS长流实现仅构建/静态校验完成，未验证持续帧率，不能宣称App达10–15FPS。Web独立合成输入约12FPS不代表App。初始等待数秒允许，长时间等待接收请反馈。沿用3.2签名，来源commit dd067ed，Release v3.3.0，最终本地APK SHA256483a59306c54143214a3f2002df8a1c2d778e6ecb5f76c5f369bccfa8cf4795d。
