---
schema_version: 1
type: "continuation"
id: "data-center-v4"
title: "课设数据中心、审核导出与报告展示"
module_id: "web-dashboard"
status: "done"
next_step: "用户按前台/后台/报告与Android3.4.1真实手机上报验收；通过后由“整理项目报告母版”对话依交接文档修改三份报告。"
updated_at: "2026-10-29T15:58:50+08:00"
---

## 交付 2026-10-29
Web数据中心4.0.0已上线 https://lvshanyewu.site/wandervision/ ，后台admin/，可视化报告report/。副标题道路巡检具身智能体。采集业务持久台账、人工查看/复核/标签/备注/批量处理、来源/类别/日期/状态筛选、CSV/JSON导出、独立HTML报告与打印PDF；首页精简为校园地图/计数/关注线索/设备预览。保留原配对、手机地图、LIVE、设置与清库。

## 代码和测试
Web提交0238556，合并origin/main至1e3dc3c并已推送codex/web-dashboard-v1（PR1）。58项Python测试、3个JS语法检查、Python编译通过。浏览器本地查看/复核/筛选、前台布局/报告检查通过。线上实际SIM条目rec-f7db220653a29ce76b0a已确认、标记重点/课堂演示、备注保存，筛选已确认返回1条；不把此模拟条目当真实采集。线上health4.0.0，脚本/样式字节一致；HTML额外包含Cloudflare托管统计脚本，正文一致。CSV/JSON/HTML报告接口200，线上报告8个分析区块。后台交付截图artifacts/data-center-admin-final.png（忽略文件，不入仓库）。

## 部署
新IP依据myseoul已验证SSH记录，使用物理网卡绑定和既有主机指纹核验，独立私有JSON连接配置；仓库跟踪文件旧公网地址引用0处，App域名/Web相对接口。发布/备份20261029T075403Z，备份目录/opt/wandervision/backups/20261029T075403Z含SQLite一致性备份及旧服务/Nginx配置。只重启本服务与nginx-t/reload，未改其他项目。active，发布启动MemoryCurrent=15630336。

## Android与报告交接
原App对话发布3.4.1（main56d5b10+d5673f7），新增capture_context采集点/备注并保留原始完整YOLO/模型/位置/OCR/规则结构。APK https://github.com/lvshanyewu/wandervision/releases/download/v3.4.1/wandervision-3.4.1-arm64.apk 。构建/签名/模拟器由App对话记录；本次真实手机上传/GPS/识别效果待用户验收。
已通知App对话Web上线无需扩展，并向“整理项目报告母版”发送docs/course-report-v4-update.md等指导；要求先准备三份修改清单，待用户验收后再改现有Word/PDF。未覆盖旧报告。

## 容量边界
台账3000条/接收30天，照片7天/50张/96MiB；照片过期台账可查，重置模拟保留台账，清库清除台账/日志/照片并回收空间。公开课程Demo无生产登录/审批权限，报告为统计建议，不是云端AI分析。
