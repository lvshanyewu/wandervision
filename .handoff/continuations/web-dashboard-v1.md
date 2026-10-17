---
schema_version: 1
type: "continuation"
id: "web-dashboard-v1"
title: "Web Dashboard 首版开发与部署"
module_id: "web-dashboard"
status: "done"
next_step: "请用户在线验收 https://lvshanyewu.site/wandervision/；后续 Android 加入 GPS/WGS84、本地队列及心跳/幂等事件同步。"
updated_at: "2026-10-17T09:19:01+08:00"
external_checks: [{"checked_at": "2026-10-17T09:18:00+08:00", "service": "Web local and HTTPS demo", "status": "passed", "evidence": "22 unittest; Python/JS syntax; 5 resource SHA256; browser desktop/390px; HTTPS page+health+snapshot+assets 200; 10 sim +3 pending; independent service active."}]
---

# 首版交付
生成日期：2026-10-17 · 版本：web-demo-v1

## 已完成
原生 HTML/CSS/JS 黑灰黄仪表盘，重庆本地OSM交互地图和3条OSRM道路快照，6固定+4移动模拟与3个PHONE待接入位；实时事件/筛选/统计图表/设备详情/合成预览，暂停/继续/重置/验收场景。Python标准库API、SQLite真实数据、独立设备令牌和心跳TTL/事件幂等。docs/web-dashboard.md 包含启动、API、部署及回退。默认无地图Key或运行依赖。

## 测试结果
22项unittest通过；Python编译、node --check通过；5个地图/Leaflet资源SHA256验证通过。浏览器桌面与390px响应式布局检查无横向溢出；设备真实筛选/待接入详情、事件坑槽筛选、分析视图、API说明、暂停/继续正常，未见JS错误。线上HTML、health、snapshot、JS/CSS及地图/路由均200，静态运行资源SHA与本地匹配；快照10 sim +3 real(pending)，只有模拟事件，接口Cache-Control no-store，Nginx gzip生效。公网浏览器验证暂停/继续和场景切换。

## 部署事实
公网 https://lvshanyewu.site/wandervision/ 。独立 systemd wandervision ActiveState=active，启动内存约12–14MB；端口5065仅绑定Docker实际内网网关。新路径反代到本服务，nginx -t后reload，无其他业务重启、原应用数据/静态文件变更。代码 /opt/wandervision/releases/20261017T011553Z ，current指向该版；保留首次20261017T011216Z发布。未配置真实手机令牌，接口503拒绝真实写入。没有往线上写入测试手机/个人GPS数据。

## 精确回退
本轮备份 /opt/wandervision/backups/20261017T011553Z ，从仓库执行 python web/deploy/deploy.py --rollback /opt/wandervision/backups/20261017T011553Z 回前一个Web版本；首次备份 /opt/wandervision/backups/20261017T011216Z 可撤销首次路由/停本服务。脚本拒绝覆盖之后并发Nginx改动，保留数据库、发布与备份。

## Git与并行工作边界
共享主目录中检测到其他会话正在修改Android文件与capture-location-3.1记录，本任务未修改/纳入这些内容。Web代码和自己的文档在独立 codex/web-dashboard-v1 分支整理，避免共享分支/索引冲突。Git最终提交信息补充见后续记录。

## 下一阶段
用户在线验收界面/演示，再对接Android GPS权限、WGS84、本地事件队列、每设备令牌、心跳和幂等检测API。真实病害模型和预览/视频另阶段实现。Web合成示意不代表真实摄像头/推理成功。
