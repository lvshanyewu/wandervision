---
schema_version: 1
type: "continuation"
id: "android-link-3.2"
title: "3.2.0监控中心配对与照片上传"
module_id: "android"
status: "done"
next_step: "用户安装3.2.0，用网页配对码联动；在device-validation-3.2记录小米13真实网络/GPS/照片方向与持续稳定性反馈。"
updated_at: "2026-10-17T21:56:24+08:00"
external_checks: [{"checked_at": "2026-10-17T13:54:47Z", "service": "GitHub Actions / Release v3.2.0", "status": "passed", "evidence": "https://github.com/lvshanyewu/wandervision/actions/runs/39038186698 成功；Release APK下载回验40,627,999 bytes / SHA256 895ad88178caacd5d3f295a042838697e75568ed76a11978f515c5fd4a899f28；check_apk/apksigner/zipalign/aapt通过，固定证书与0.2/0.3/3.1一致。"}, {"checked_at": "2026-10-17T13:45:30Z", "service": "Monitor HTTPS integration (emulator TEST)", "status": "passed", "evidence": "PHONE-01配对/前台心跳/拍照上传与3次同UUID确认通过；server report-208ec932c3f1def0daf50552，一条档案test_data=true，无有效GPS；原JPEG/JSON相同。18项假传输Instrumentation通过，非实体手机验收。"}]
---

# 授权/范围

已在Web对话真实用户消息核对App/Web联动授权。仅改Android、其发布流程与自身文档/交接，不改web/**、部署、README/AGENTS或Web交接。3.2.0/code5，保留3.1本地视觉和同帧JPEG/JSON。

# 已实现

固定HTTPS+系统浏览器仪表盘（配对后带device=PHONE-01，无令牌URL）；6位码配对私有保存令牌；前台10秒心跳、暂停/解除连接、401失效重配、空GPS。失效/模拟位置和已知模拟器不冒充GPS。上传完整原JSON+原JPEG NO_WRAP，4MiB图/6MiB请求/SHA校验，不改原图或原JSON；独立AtomicFile回执、手动重试同UUID幂等、成功显示server_event_id/时间。公开课程Demo可清库提示在按钮旁。COCO不改成病害。网络线程与推理分开，HTTPS默认TLS不跟重定向。

# 契约测试

18项Android Instrumentation检查通过（假传输，不上传公网）：码/凭据验证、固定浏览器URL、空GPS、原图/JSON不变、同event_id重复、hash/大小/响应错误、401/超时脱敏、旧响应不能覆盖新连接、独立回执持久化、发送前去除过期GPS、前台发送/进入后台停止、暂停不发。初版测试上下文存储权限问题已修正为目标应用隔离测试偏好/缓存，未改用户真实连接设置。

# 真实HTTP端到端证据

App界面真实配对PHONE-01，名称Android-Emulator-E2E-TEST，显示前台心跳成功。网页snapshot收到fps且location_valid=false（服务器保留示意lat/lon，不当作有效GPS）。新拍报告event_id=ef0ce2a9-c69d-407b-b0b0-ff7f59efc63c，server_event_id=report-208ec932c3f1def0daf50552；App第一上传duplicate=false，再次点击duplicate=true、attempts=2、id与received_at不变，档案只有一条。

公网页面/api/v1/reports确认test_data=true；GET详情原始JSON与本机完整对象相同，JPEG字节相同，SHA256=8a245dd5ea6a22ab5ece2073a517061b640415b715ada8d6bc34a2220c556ea1，34645 bytes，480×751，定位为null。测试图和JSON保留供Web对话核对，未清库。证据artifacts/report-3.2-e2e.*、live-3.2-verification.json。

App激活原地图入口，以ACTION_VIEW打开系统浏览器 https://lvshanyewu.site/wandervision/?device=PHONE-01；模拟器浏览器已加载校园仪表盘并显示PHONE-01待定位。Web测试位source=real仅表示实际HTTP设备通道，照片明确TEST；不是实体手机/真实GNSS/道路识别验收。

# 最终验证与发布

最终自有User-Agent版本已再次确认真实心跳online、无有效GPS，第三次App界面上传仍duplicate=true、attempts=3、同server_event_id。原图/JSON再次核对一致。最终18项契约测试复跑通过；本地ADB MCP ARM64 assembleDebug lintDebug通过（0错误22警告），check_apk/apksigner/zipalign通过。固定证书fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac；本地APK40,628,215 bytes，SHA256 d9140e5a25ba120e1a2ba7961db94c8b83a04d9eaab2ffbc19ebe84969b8d697。docs/android-monitor.md与docs/releases/3.2.0.md已完成，源码提交31cb2304ca035e795183beb35e56164f539f63c8，标签v3.2.0已推送。标签CI39038186698成功发布，最终APK已下载回验40,627,999 bytes，SHA256 895ad88178caacd5d3f295a042838697e75568ed76a11978f515c5fd4a899f28，与Release校验和一致，ARM64/签名/对齐/版本检查通过；证书与旧版相同可覆盖。详见docs/build-verification-3.2.0.md。模拟器已关闭，测试图仍在公网TEST档案，未清库；未改Web源或其交接。
