# 3.3.0 构建与交付检查（2026-10-18）

- ADB MCP gradle_build assembleDebug + lintDebug --offline：成功，0错误/23警告。最终版新增显式HTTP1.1 TLS长流编译通过。
- tools/check_apk.py通过：仅ARM64 ELF、dex/manifest、COCO n/s和哈希固定RDD2022 n/s，zipalign -c -P16 4与apksigner通过。
- 测试签名SHA256：fcd138196a9ef353dbe2e98a23ee22da0c6ed0a6cef442599d59d742c56dbbac，可覆盖3.2。
- 发布本地APK SHA256：483a59306c54143214a3f2002df8a1c2d778e6ecb5f76c5f369bccfa8cf4795d。
- x86_64模拟器34项通过：既有18项心跳/上传回归、同源导航/停止路由/类别隔离/大端帧格式4项、四模型×3输入尺寸原生加载/矩形推理/绘制12项。最后新增LiveHttpSession传输代码编译通过，未重跑真实连续流验收。
- TorchScript vs NCNN：10组320/480/640和矩形输入形状一致，raw[N,68]，FP16权重max abs<=0.082、mean<=0.0074，数值记录随源码。并非真实道路准确率测试。
- App UI→生产配对/前台心跳、嵌入式手机地图已通过。旧逐帧POST通道有TEST帧并测得1.7–1.8FPS，确认高RTT瓶颈；Android HttpsURLConnection长流出现缓冲后改显式TLS HTTP1.1，最终原生持续帧率需用户验证，不能宣称10–15FPS已达成。Web owner单独合成JPEG公网长流约11.5–13.6FPS、未结束可读取，48项服务测试通过；该证据不等同App或真机性能。
- 用户通过Web对话要求额度不足快速收尾，停止扩展/长测。小米13摄像头/方向/路害效果/温度/CPU-GPU与LIVE帧率由用户安装反馈。不清现有监控档案。
