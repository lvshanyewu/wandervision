---
schema_version: 1
type: "module"
id: "web-dashboard"
title: "Web 道路巡检监控中心"
summary: "校园Web v3：轻量手机地图与LIVE、内存短TTL最新JPEG、鉴权长连接上传/原生MJPEG观看；48项测试，合成公网流约12FPS，App原生/真机效果待用户验收。"
aliases: []
tags: []
sources: ["web/server.py", "web/static/index.html", "web/static/app.js", "web/static/style.css", "web/static/upgrade.js", "web/static/upgrade.css", "web/static/scene.js", "web/static/data/SOURCES.md", "web/static/data/asset-checksums.json", "web/deploy/deploy.py", "web/tests/*.py", "tools/prepare_web_maps.py", "docs/web-dashboard.md", "web/static/live.js", "web/static/live.css", "web/static/mobile/*", "docs/android-live-contract.md"]
dependencies: []
related_modules: ["android", "roadmap"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-18T10:09:49+08:00"
---

# Web 道路巡检监控中心

## 当前范围
2026-10-17 用户授权完善校园 Demo、清空无价值演示数据、协调现有 Android 对话实现联动并复用 myseoul 服务器部署。本阶段 Web 与 Android 分别交付，不混淆模拟、模拟器测试和实机验证。

## 架构与地图
Python 3.11 标准库 ThreadingHTTPServer + SQLite WAL，无第三方运行依赖。原生 HTML/CSS/JS、自托管 Leaflet1.9.4、SVG图表、Canvas透视场景；3秒轮询、后台降频。WGS84 中心重庆交通大学南岸校区29.4903657,106.567229，默认zoom15。OSM1610个本地要素约0.6MB、3条OSRM道路快照；在线深色街道图失败回退本地校园矢量。完整署名/来源与资产SHA256保留。

## 模拟与设置
6固定+4移动模拟，3个PHONE预留位。移动按路线距离插值及端点折返，离线/停靠停止上报，同类事件冷却。正常/高峰/病害/断线/夜间五预设，暂停/重置。SIM透视循环画面含弯道/校园/路口、车辆和病害框、日夜雨天，不执行真实AI、不写入照片档案。10/20FPS选择、后台停止画面。设置支持手机配对、地图/跟随/事件标记、存储配额/压缩/一键清空。

## App 接口
POST api/v1/pairing生成单次6位码，5分钟有效；POST pair换取PHONE独立Bearer令牌，数据库仅存SHA256。App3.2在前台约10秒heartbeat；无GPS可传null，location_valid=false，保留位置明确非实际定位，不用预留坐标伪造检测。
POST devices/PHONE-01/reports接受原始schema_version1.0 report和image_base64；JPEG最多4MiB，JSON元数据256KiB，总请求6MiB，单并发上传。验证哈希/JPEG尺寸/声明字节。event_id按设备幂等，重复原图返回同一server_event_id。原图及JSON逐字节保存，上传回执与原始sync.local_only分开；不由COCO识别虚构路害。GET reports、reports/id、reports/id/image提供档案。模拟器/模拟定位报告标TEST，真实接入通道source=real，模拟引擎source=sim。旧heartbeat/events鉴权接口保留，events要求有效本次/最新GPS。

## 数据与清理
模拟事件仅内存最多600条。真实报告/事件/设备状态/凭据存SQLite。自动保留7天、最多50张、96MiB图片及5000事件；启动/上传/每小时执行。maintenance action=compact清理压缩，action=clear删除本Demo照片/事件/设备状态并重置模拟；默认keep_pairing=true，可选撤销。checkpoint→VACUUM→checkpoint实际回收磁盘，不修改手机本地内容或其他服务。

## 部署与使用
https://lvshanyewu.site/wandervision/；仓库根python web/server.py后访问http://127.0.0.1:8090/。部署及完整契约见docs/web-dashboard.md。独立systemd wandervision，/opt/wandervision/releases/current、StateDirectory=/var/lib/wandervision；5065仅绑定Docker网关。DynamicUser、128MiB/25%CPU；Nginx只改命名主站/wandervision/路由和6MiB body限制，每次备份/nginx-t/reload，无其他服务全量重启。精确发布/回退目录在continuation。

## 验证边界
48项Python测试涵盖状态/鉴权/配对/幂等/原图/限额/回收空间/部署。浏览器桌面和390px移动检查及线上资产哈希通过。实际App3.2模拟器配对、心跳、拍照上传、重复上传和网页原图SHA256/本机字节一致已验证；无有效GPS、0检测、测试照片标TEST。真实手机GPS/摄像头/识别效果/FPS/稳定性仍须用户实机反馈，编码视频尚未接入；最新JPEG LIVE见下节。


## LIVE与手机仪表盘（2026-10-18）
/mobile/?device=PHONE-01只读简版地图/设备/LIVE，支持App内WebView，无token URL、网页相机定位或JS桥。桌面真实设备预览展开LIVE，原照片留采集档案。上传兼容rawJPEG，推荐/live/stream HTTP1.1 chunked：4字节BE长度/JPEG/零结束，Bearer和UUID stream_id；单帧256KiB/1280边/921600像素，15FPS，一写通道，每会话60秒/16MiB，5秒读超时。stop/重配/清库epoch阻断晚帧，逐帧凭据复核。三条精确Nginx路径关闭请求缓冲，其他服务不变。
每手机仅最新帧内存，2秒stale/5秒释放，无报告SQL写入或假GPS。GET状态/frame.jpg ETag/原生mjpeg，最多6观看连接60秒，不转码，浏览器隐藏断开；元数据过期定时清图，MJPEG不可用回退顺序JPEG读取。报告使用model_id和label展示RDD四类，保留原JSON而不按COCO编号猜类别。完整约束见docs/android-live-contract.md。
48项自动测试及本地浏览器移动/桌面已通过；公网合成长流在请求未结束时约11.5–13.6FPS，原图一致。App逐帧原生实测1.7–1.8FPS，Android HTTP缓冲/HTTP1.1改动及真实手机10–15FPS仍待用户实际测试，不能用合成流代替。用户2026-10-18因额度要求快速收尾，不继续扩展/长测。
