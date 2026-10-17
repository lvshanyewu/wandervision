---
schema_version: 1
type: "module"
id: "web-dashboard"
title: "Web 道路巡检监控中心"
summary: "已部署的原生黑灰黄重庆仪表盘；Python 标准库/SQLite、10 模拟+3真实接入位、OSM 本地地图、鉴权心跳/事件 API；真实 Android/视频未接入。"
aliases: []
tags: []
sources: ["web/server.py", "web/static/index.html", "web/static/app.js", "web/static/style.css", "web/static/data/SOURCES.md", "web/static/data/asset-checksums.json", "web/deploy/deploy.py", "web/tests/*.py", "tools/prepare_web_maps.py", "docs/web-dashboard.md"]
dependencies: []
related_modules: ["android", "roadmap"]
business_rules: []
invariants: []
consumers: []
updated_at: "2026-10-17T09:17:47+08:00"
---

# Web 道路巡检监控中心

生成日期：2026-10-17 · 版本：web-demo-v1

## 当前范围
用户于 2026-10-17 授权优先开发独立 Web Demo 并复用 myseoul 服务器对外部署，覆盖此前暂不开发 Web 的阶段约束。Android 本次不修改。

## 已实现架构
web/server.py：Python 3.11 标准库 ThreadingHTTPServer + 1 秒模拟线程 + SQLite WAL；无第三方运行依赖。web/static：原生 HTML/CSS/JS、Leaflet 1.9.4 自托管并核对官方 SHA256、原生 SVG 图表与 Canvas 合成预览。3 秒轮询，后台 12 秒；模拟事件保留 600 条、快照最近 200 条，累计统计独立维护。

## 地图与模拟
重庆主城区 OSM 约 11969 个道路/江河要素、本地 Canvas 矢量默认底图；3 条 OSRM 路线快照，WGS84，运行不调用路线 API。可切换在线深色/原色 OSM 街道图，失败回退本地。保留 OSM/Leaflet 署名和许可。6 固定+4 移动模拟，3 PHONE 位初始 pending 且 location_valid=false。移动按距离插值/端点反向；离线/停靠停止检测与心跳，同设备类别45秒冷却。24条种子演示历史、正常8秒/验收4秒事件、全局暂停/继续/重置（不清真实数据）。

## API 契约
GET api/v1/health、snapshot；POST simulation。PHONE-01..03 的 heartbeat/events 接口要求 WV_DEVICE_TOKENS 中各自独立至少24字符令牌；未配置返回503，错误令牌401。GPS/指标有限数值检查、心跳30秒TTL、有时区事件采集时间过去24h至未来5min、每设备event_id唯一幂等，服务端固定source=real。SQLite 保存真实设备/事件，重启和模拟重置均保留。16KiB大小与地址级120次/分钟写入限制，同源模拟控制，无跨域开放。preview_url为空。

## 部署与回退
公网 https://lvshanyewu.site/wandervision/ 。独立 systemd wandervision，/opt/wandervision/releases + current，数据库StateDirectory /var/lib/wandervision；5065仅绑定实测Docker网络网关。DynamicUser、128MB/25%CPU 配额。只向 myseoul startpage 命名主站添加 /wandervision/ 路由；配置保持bind-mounted inode，nginx -t后reload，不重启现有业务。每次部署备份配置/unit并校验文件字节；精确回退目录和测试证据放 continuation。

## 使用和后续
仓库根 python web/server.py，http://127.0.0.1:8090/ 。完整契约、部署脚本环境与回退见 docs/web-dashboard.md。后续 Android 加 GPS/WGS84、本地队列、独立令牌和幂等同步，再接病害模型/可选预览。真实访问控制和数据保留需在真实手机接入前完善；当前公网只含模拟数据。

## 验证边界
模拟预览明确标注SIM/合成示意，真实设备初始待接入。Web测试不证明Android摄像头、真实推理效果、FPS或稳定性。
