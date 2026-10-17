---
schema_version: 1
type: "continuation"
id: "capture-location-3.1"
title: "3.1.0 取景报告与前台定位"
module_id: "android"
status: "done"
next_step: "用户安装3.1.0后，在device-validation-3.1记录真实定位、照片方向/非空识别框、导出与持续稳定性反馈。"
updated_at: "2026-10-17T20:21:44+08:00"
external_checks: [{"checked_at": "2026-10-17T12:21:24Z", "service": "GitHub Actions / Releases", "status": "passed", "evidence": "v3.1.0源79ad8d30cea7f0e63e1198e59b52ea3eadf35fa6；https://github.com/lvshanyewu/wandervision/actions/runs/39032008856 成功。最终Release APK下载回验40,610,530 bytes，SHA256 dec3f42465ef3705af2e5bf9a3a601568764eaac9a4aba9065bbfe8d35651d28；check_apk/apksigner/zipalign/aapt通过，证书与0.2/0.3相同。"}]
---

# 完成实现

版本3.1.0/code4，基线用户明确确认的是0.2功能正常，不能推断0.3或本轮真机全项通过。统一底部上滑面板；按需JNI下一帧捕获同帧RGB、检测框/类别/置信度、统计与单调时刻。生成独立巡检JPEG，前台平台GPS/network约10秒请求，底部显示WGS84坐标/精度；权限/关闭/过期均有真实状态。AtomicFile保存每张JSON（安装UUID来源、同帧目标、坐标质量、JPEG SHA256、local_only），最近列表30条；分享照片+JSON及系统文件选择器导出JSON。模型/CPU/系统Vulkan不变。

# 已验证

最终本地ARM64通过ADB MCP assembleDebug lintDebug（0错误21警告）、tools/check_apk.py、apksigner及16KB zipalign；签名与0.2/0.3一致。local APK SHA256 6ae6937bd266acf93cffe6e381188f1f8e1959dd1d4229c61e84fc768c734382，CI产物待回验。

模拟器实际生成纯取景与启用识别照片、fresh GPS/关闭定位off/拒绝权限permission_denied/近似授权无网络位置searching记录；check_report验证时间/对象/位置/照片hash，PIL回读GPS EXIF，新记录包含来源ID。观察过期缓存显示stale，dumpsys证明约10秒更新、离开应用停止注册。横竖屏照片及底部面板视觉检查通过。系统分享页显示2文件；ACTION_CREATE_DOCUMENT保存至Download后pull回，JSON和私有记录完全相同；crash buffer无崩溃。所有定位为模拟器测试坐标，geo fix未必标mock，不是真实GNSS证据。

# 调查结论

RDD2022 YOLOv8n固定revision权重已下载并静态检查Detect/DFL与四类病害，未执行pickle、未转换或进入APK；原始头输出仍需适配。Turnip R8 readelf确认HMI及受限平台库依赖；libadrenotools需要解包/命名空间及Vulkan句柄桥，未直接引入。证据与后续路线见docs/road-models-and-turnip.md。

# 边界

Android不上传，不接监控中心。真实小米13定位/相机/持续性能待安装反馈。Web任务相关AGENTS/README/roadmap/overview/Web等改动保留，不纳入本次提交。
