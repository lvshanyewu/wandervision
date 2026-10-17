---
schema_version: 1
type: "continuation"
id: "web-dashboard-v2"
title: "交大南岸校区 Demo 完善与 App 联动"
module_id: "web-dashboard"
status: "open"
next_step: "完成App3.2 APK发布和检查汇总，合并最新main至Web分支，提交最终交付记录；用户安装真机验证GPS/摄像头与实际识别。"
updated_at: "2026-10-17T21:41:57+08:00"
---

# 已实现
交大南岸校区中心29.4903657,106.567229，附近1610个OSM要素587KB和3条OSRM道路路线。10模拟+3PHONE位，五预设，详细透视Canvas循环巡检/校园/路口画面、车辆/病害框、日夜雨、节能/跟随设置。原生Web及标准库Python/SQLite。

# App 联动与数据
一次性6位配对、Bearer散列持久化、无GPS前台心跳、原始JPEG+JSON上传、同event_id幂等。4MiB照片/6MiB请求，SHA256/JPEG尺寸/声明字节校验，单并发。采集档案显示TEST模拟器标识、原图/JSON；不由COCO虚构路害。7天/50照片/96MiB/5000事件自动配额，手动clear可保留配对，checkpoint/VACUUM/checkpoint实测回收。模拟场景不写真实档案。

# 验证
32项unittest最终通过（2026-10-17），包括大照片HTTP上传回读/去重/清理404及>100KB物理回收。Python和JS语法/供应商资源校验通过。线上HTML/全部运行资产哈希与本地一致；浏览器桌面1440与手机390px设置/图片/场景无JS错误。清理仅使用隔离本地测试库。
App3.2模拟器实际界面已配对PHONE-01、前台无GPS心跳、拍照上传；报告ef0ce2a9-c69d-407b-b0b0-ff7f59efc63c为TEST，480x751约33.8KiB、0检测。公网原图HTTP200、SHA256与JSON一致且逐字节匹配App本地JPEG，重复上传duplicate=true同server_event_id。生产保留该照片演示；不代表实机GPS/摄像头/识别/FPS验证。

# 发布与回退
公网https://lvshanyewu.site/wandervision/。最终release=/opt/wandervision/releases/20261017T134020Z；rollback：python web/deploy/deploy.py --rollback /opt/wandervision/backups/20261017T134020Z。独立systemd active，启动MemoryCurrent13922304 bytes；只重启本服务，Nginx路径body6m，备份并nginx-t/reload，其他应用保持运行。SQLite和手机配对/照片跨发布保留。

# 协作与代码
codex/web-dashboard-v1分支PR1，功能与修复已推送到6c8b2a3。共享主目录Web源码同步，Android交由用户授权的现有App对话实现/构建/发布。本次沟通使用Codex线程工具，未派生额外代理。App APK发布结果待汇入最终记录。
