---
schema_version: 1
type: "continuation"
id: "web-dashboard-v1"
title: "Web Dashboard 首版开发与部署"
module_id: "web-dashboard"
status: "done"
next_step: "请用户在线验收 https://lvshanyewu.site/wandervision/；后续 Android 加入 GPS/WGS84、本地队列及心跳/幂等事件同步。"
updated_at: "2026-10-17T09:26:17+08:00"
external_checks: [{"checked_at": "2026-10-17", "service": "Final Web v1 / HTTPS", "status": "passed", "evidence": "22 unittest + Python/JS syntax; 5 Git blob map/vendor hashes; HTTPS final HTML and referenced JS/CSS SHA match; 390px no overflow and detail visible, 1440px detail visible; no JS errors; 10 sim +3 pending; standalone service active."}]
---

# 首版交付
生成日期：2026-10-17 · 版本：web-demo-v1

## 已完成
原生 HTML/CSS/JS 黑灰黄仪表盘，重庆本地OSM交互地图和3条OSRM道路快照，6固定+4移动模拟与3个PHONE待接入位；实时事件/筛选/统计图表/设备详情/合成预览，暂停/继续/重置/验收场景。Python标准库API、SQLite真实数据、独立设备令牌和心跳TTL/事件幂等。docs/web-dashboard.md 包含启动、API、部署及回退。默认无地图Key或运行依赖。

## 测试结果
22项unittest通过；Python编译、node --check通过；5个地图/Leaflet资源SHA256验证通过，包括Git暂存blob与官方/快照校验和一致。gitattributes对第三方资源保留原始字节/行尾。浏览器桌面与390px响应式无横向溢出；真实筛选/待接入详情、坑槽筛选、分析视图、API说明、暂停/继续正常，未见JS错误。线上HTML、health、snapshot、JS/CSS及地图/路线均200，静态运行资源SHA与本地匹配；10 sim +3 real(pending)，只有模拟事件，接口no-store，Nginx gzip生效。公网浏览器验证暂停/继续和场景切换。

## 部署事实
公网 https://lvshanyewu.site/wandervision/ 。独立systemd wandervision active，启动内存约12–14MB，5065只绑定Docker实际内网网关。新路径反代，本次nginx -t后reload，无其他业务重启/原应用数据/原静态页面变更。最终代码 /opt/wandervision/releases/20261017T012423Z，current指向该版；前两次发布均保留。真实令牌未配置，接口503拒绝写入；未往线上写测试手机或个人GPS。

## 精确回退
最终备份 /opt/wandervision/backups/20261017T012423Z 可回前一Web版本；此前备份011553Z保留，首次 /opt/wandervision/backups/20261017T011216Z 可撤销首次路由并停本服务。仓库 python web/deploy/deploy.py --rollback <精确备份目录>；拒绝覆盖并发Nginx变更，保留数据库/代码/备份。

## Git与并行边界
功能提交 bc173af、窄屏详情修复12f9ddb，独立分支 codex/web-dashboard-v1 已推送。草稿PR https://github.com/lvshanyewu/wandervision/pull/1 ，等待用户视觉验收，未合并。未将共享主目录中其他会话的Android改动/capture-location-3.1记录纳入。部署文件与该提交Web运行源一致。主目录保留同一Web源供本地启动；Git分支在本聊天附加的独立worktree中。

## 后续
请用户在线验收；下一阶段 Android GPS权限/WGS84、本地队列、每设备令牌、心跳/幂等事件API，病害模型与视频另阶段。Web合成示意不代表真实摄像头/推理成功。


## 最终界面复核
修复窄屏设备页/地图页详情被隐藏的问题，资产版本20261017-3避免CDN旧缓存。公网390px无横向溢出且设备详情可见；1440px设备详情可见，控制/地图/事件/图表工作，浏览器无JS错误。公网最终HTML与四个实际引用JS/CSS资源SHA逐项匹配本地。桌面/手机截图保存于忽略的artifacts，不提交生成产物。
