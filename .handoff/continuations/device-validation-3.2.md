---
schema_version: 1
type: "continuation"
id: "device-validation-3.2"
title: "等待小米13反馈：3.2监控中心联动"
module_id: "android"
status: "open"
next_step: "覆盖安装3.2，网页生成配对码，验证真实网络心跳/位置、照片上传/幂等重试、浏览器前后台及既有识别和照片方向。"
updated_at: "2026-10-17T21:52:40+08:00"
---

# 待真机验收

版本3.2.0/code5，固定签名可覆盖3.1/0.3。网页设置生成6位一次性码，App工具→监控中心连接；前台10秒心跳，GPS无效为null；照片逐张手动上传与回执，同UUID重试幂等；原图/JSON不改写。浏览器查看带设备ID的仪表盘，离开App心跳暂停、返回恢复。公开Demo会显示上传照片/位置，可由网页清空。

# 手机检查重点

小米13配对/错误码/过期码、定位授权与室外位置质量、移动网络断开重试、照片方向/非空检测框、原图和JSON、重复上传只一条、前后台及重启恢复；核对系统浏览器打开的设备地图。照片被编辑或删除应拒绝上传，旧3.1巡检照片可传，旧无JSON截图只查看/分享。Demo清库后旧回执仅代表上次确认，可重试。

# 开发机证据与界限

18项隔离假传输Instrumentation通过；模拟器实际HTTPS配对PHONE-01、心跳、拍照、上传及三次同一UUID确认，网页TEST档案一条、原JSON/JPEG字节一致，App浏览器载入仪表盘。本地ARM64构建/lint/签名/包/对齐通过，正式发布记录见docs/build-verification-3.2.0.md。模拟器和source=real设备HTTP通道不是实体手机/GNSS/道路识别验证。保留COCO Nano/Small，未增加病害模型、Turnip或实时视频。
