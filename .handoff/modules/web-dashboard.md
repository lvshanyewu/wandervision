---
schema_version: 1
type: "module"
id: "web-dashboard"
title: "Web 道路巡检监控中心"
summary: "Web数据中心v4：校园展示仪表盘、持久台账与后台人工复核/标记、CSV/JSON导出和自包含可视化报告。Android3.4.1采集上下文兼容，58项测试；独立私有配置适配服务器新IP，真机效果待用户验收。"
aliases: []
tags: []
sources: ["web/server.py", "web/data_center.py", "web/static/index.html", "web/static/app.js", "web/static/style.css", "web/static/data-center.css", "web/static/admin/*", "web/static/upgrade.js", "web/static/upgrade.css", "web/static/scene.js", "web/static/data/SOURCES.md", "web/static/data/asset-checksums.json", "web/deploy/deploy.py", "web/tests/*.py", "tools/prepare_web_maps.py", "docs/web-dashboard.md", "docs/data-center-contract.md", "docs/course-report-v4-update.md", "web/static/live.js", "web/static/live.css", "web/static/mobile/*", "docs/android-live-contract.md"]
dependencies: []
related_modules: ["android", "roadmap"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-29T15:55:03+08:00"
---

# Web 道路巡检监控中心

## 当前范围

2026-10-29用户授权将课程Demo完善为数据汇集整理中心，副标题“道路巡检具身智能体”。首页提供精简校园地图/统计/关注线索，/admin/后台查看、复核、标记和导出，/report/生成可视化报告。Android由现有App对话负责；报告母版待用户验收后再修改，交接见docs/course-report-v4-update.md。保留原有校园模拟、配对、照片和LIVE，不声称生产级审批或道路检测效果。

## 架构与地图
Python 3.11 标准库 ThreadingHTTPServer + SQLite WAL，无第三方运行依赖。原生 HTML/CSS/JS、自托管 Leaflet1.9.4、SVG图表、Canvas透视场景；3秒轮询、后台降频。WGS84 中心重庆交通大学南岸校区29.4903657,106.567229，默认zoom15。OSM1610个本地要素约0.6MB、3条OSRM道路快照；在线深色街道图失败回退本地校园矢量。完整署名/来源与资产SHA256保留。

## 模拟与设置
6固定+4移动模拟，3个PHONE预留位。移动按路线距离插值及端点折返，离线/停靠停止上报，同类事件冷却。正常/高峰/病害/断线/夜间五预设，暂停/重置。SIM透视循环画面含弯道/校园/路口、车辆和病害框、日夜雨天，不执行真实AI、不写入照片档案。10/20FPS选择、后台停止画面。设置支持手机配对、地图/跟随/事件标记、存储配额/压缩/一键清空。

## App 接口
POST api/v1/pairing生成单次6位码，5分钟有效；POST pair换取PHONE独立Bearer令牌，数据库仅存SHA256。App3.2在前台约10秒heartbeat；无GPS可传null，location_valid=false，保留位置明确非实际定位，不用预留坐标伪造检测。
POST devices/PHONE-01/reports接受原始schema_version1.0 report和image_base64；JPEG最多4MiB，JSON元数据256KiB，总请求6MiB，单并发上传。验证哈希/JPEG尺寸/声明字节。event_id按设备幂等，重复原图返回同一server_event_id。原图及JSON逐字节保存，上传回执与原始sync.local_only分开；不由COCO识别虚构路害。GET reports、reports/id、reports/id/image提供档案。模拟器/模拟定位报告标TEST，真实接入通道source=real，模拟引擎source=sim。旧heartbeat/events鉴权接口保留，events要求有效本次/最新GPS。

## 数据与清理

设备状态类模拟噪声仅在内存事件流，车辆/病害业务事件持久化到统一records台账。每张App照片报告对应一条台账，完整目标数组/模型/位置/OCR/规则信息与原始JSON保存，采集点和备注通过capture_context保留；SIM/TEST/REAL始终区别。复核状态pending/confirmed/rejected/resolved、priority、tags、note与原始数据分离，修改留日志并用版本号防覆盖。支持筛选、分页、批量复核、带BOM的CSV及完整JSON导出、自包含HTML报告与打印PDF；报告建议为确定性汇总，不是云端AI。台账最多3000条，按接收时间保留30天；照片7天/50张/96MiB及事件5000条沿用。照片过期台账仍可查。模拟重置保留台账，一键清空删除台账/复核日志/照片/状态，默认保留配对并实际压缩回收空间。

## 部署与使用

https://lvshanyewu.site/wandervision/ ，后台admin/，报告report/。仓库根python web/server.py后访问http://127.0.0.1:8090/。相对Web API和App HTTPS域名避免运行时依赖公网IP。部署连接使用私有JSON或进程环境；myseoul更换IP后由已验证SSH记录获取新目标，必要时绑定物理网卡，严格校验既有主机指纹。部署脚本包含data_center.py，激活前备份SQLite数据库/服务/单站点Nginx配置，独立systemd服务，StateDirectory，5065只绑定Docker网关，128MiB/25%CPU。只重启wandervision并nginx-t/reload，不重启其他应用；发布恢复点见continuation。

## 验证边界

58项Python测试通过，覆盖原有配对/照片/LIVE与新台账持久化、去重、上下文、复核冲突、筛选导出、报告转义、保留和清库；JS语法检查通过。浏览器完成条目查看→状态/优先级/标签/备注保存→筛选结果及前台/后台/报告布局检查。Android3.4.1由App对话构建发布，新增采集点与备注，保留完整YOLO等结构化上传；真机联调、GPS、检测质量/FPS仍须用户检查。此前App3.2模拟器图片原图SHA验证及v3合成LIVE公网链路检查仍是历史证据，不替代本次真实手机验收。

## LIVE与手机仪表盘（2026-10-18）
/mobile/?device=PHONE-01只读简版地图/设备/LIVE，支持App内WebView，无token URL、网页相机定位或JS桥。桌面真实设备预览展开LIVE，原照片留采集档案。上传兼容rawJPEG，推荐/live/stream HTTP1.1 chunked：4字节BE长度/JPEG/零结束，Bearer和UUID stream_id；单帧256KiB/1280边/921600像素，15FPS，一写通道，每会话60秒/16MiB，5秒读超时。stop/重配/清库epoch阻断晚帧，逐帧凭据复核。三条精确Nginx路径关闭请求缓冲，其他服务不变。
每手机仅最新帧内存，2秒stale/5秒释放，无报告SQL写入或假GPS。GET状态/frame.jpg ETag/原生mjpeg，最多6观看连接60秒，不转码，浏览器隐藏断开；元数据过期定时清图，MJPEG不可用回退顺序JPEG读取。报告使用model_id和label展示RDD四类，保留原JSON而不按COCO编号猜类别。完整约束见docs/android-live-contract.md。
48项自动测试及本地浏览器移动/桌面已通过；公网合成长流在请求未结束时约11.5–13.6FPS，原图一致。App逐帧原生实测1.7–1.8FPS，Android HTTP缓冲/HTTP1.1改动及真实手机10–15FPS仍待用户实际测试，不能用合成流代替。用户2026-10-18因额度要求快速收尾，不继续扩展/长测。
