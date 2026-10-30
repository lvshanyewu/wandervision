---
schema_version: 1
type: "continuation"
id: "web-live-v3"
title: "LIVE最新帧与手机仪表盘"
module_id: "web-dashboard"
status: "done"
next_step: "按用户额度要求收尾。用户测试App3.3原生LIVE流/实际帧率、内置手机地图、停止/后台与模型效果；未证实的原生HTTP1.1改动不得宣称通过。"
updated_at: "2026-10-18T10:10:25+08:00"
---

# 最终交付（2026-10-18）
用户明确因额度将不足要求快速收尾，允许功能留给本人测试。因此完成现有Web部署/必要检查，不继续扩展或长测；App对话已收到同一收尾指示。

# 实现与代码
27cd5e8 + 6117b9d + bad0ea1已推送codex/web-dashboard-v1/PR1，Android源码未由本对话修改。/mobile/?device=PHONE-01为原生只读手机地图/设备/LIVE；桌面手机预览展开LIVE。Bearer rawJPEG兼容及/live/stream HTTP1.1 chunked四字节BE/JPEG/零结束，UUID stream_id、每帧256KiB/1280边/921600像素/15FPS、一写gate、60秒/16MiB/5秒读超时。epoch阻断stop/重配/清库后旧帧；最新帧仅内存2秒stale/5秒释放，无SQL/假GPS。MJPEG原生观看最多6连接/60秒/原图不转码，元数据控制过期消隐，失败回退单帧。报告按model_id/label展示RDD，原JSON/图不改。共享契约docs/android-live-contract.md已与App对话同步。

# 检查与边界
48项Python测试通过；Python/JS语法通过。本地390px无溢出，在线/离线校园地图、设备切换、桌面与手机JPEG/MJPEG自然宽480加载、TEST标识及断流移除通过，无JS错误。仅隔离测试库清理，无清生产。
公网PHONE-03合成JPEG18秒长流：POST未结束时UUID匹配、frame_id持续递增，接收11.5–13.6FPS；原图字节一致；最终200 frames177/dropped40、客户端sent217。测试已stop，无照片存档。初始3–5秒转发延迟/突发后约12FPS；不等同真机。App原逐帧实测1.7–1.8FPS；原生HttpsURLConnection长流出现缓冲/结束才突发，App对话正换强制HTTP1.1 TLS，效果未验证留用户测试。不声称原生/真机已经达到10–15FPS，不声称GPS/路害质量。

# 部署
当前 /opt/wandervision/releases/20261018T020843Z，rollback: python web/deploy/deploy.py --rollback /opt/wandervision/backups/20261018T020843Z。systemd active，MemoryCurrent15446016 bytes。仅自身服务重启，三条自有stream精确Nginx路由关闭请求缓冲/HTTP1.1，nginx-t/reload，其他应用数据/进程不动。现有PHONE-01配对和报告保留。health3.0.1，公网 https://lvshanyewu.site/wandervision/ 和 /mobile/。
