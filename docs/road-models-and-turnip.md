# 路面模型与 Turnip 调查（2026-10-17）

## 可直接复用的训练成果

结论：优先在现有链路旁新增 RDD2022 YOLOv8n 病害模式，保留 COCO Nano/Small。本次3.1.0交付定位与取景记录；尚未将病害模型或Turnip加入运行菜单。

| 候选 | 已有成果与用途 | 导入判断 |
| --- | --- | --- |
| [dronefreak/RDD2022 YOLOv8n](https://huggingface.co/dronefreak/rdd2022-yolov8n) | 已训练权重best.pt，纵向/横向/龟裂与坑洞4类；卡片标AGPL-3.0 | 首选，架构与现有模型接近。卡片自报mAP50为58.8%，不能当作本机或国内夜间效果 |
| [同系列YOLOv8s](https://huggingface.co/dronefreak/rdd2022-yolov8s) | 相同病害类别，较大模型候选 | 可作为准确度对照，需独立真机测耗时、温度和效果，不能由模型大小推断更适合 |
| [cazzz307坑洞YOLOv8](https://huggingface.co/cazzz307/Pothole-Finetuned-YoloV8) | 坑洞专项，卡片标Apache-2.0 | 文件要求登录并接受条件；当前没有下载，不适合作为无门槛首选；卡片“accuracy”口径不等同mAP |
| [hustvl/YOLOP](https://github.com/hustvl/YOLOP) | 提供训练权重，交通目标、可行驶区、车道线多任务；仓库MIT | 适合后续路面区域过滤/车道展示；输出含分割分支，不能套现有单检测头后处理，工程成本更高 |

### 实际权重检查

已通过Hugging Face API固定YOLOv8n仓库revision `acf216a43d796f7910fc854f662c1e9cfa03fc01`，下载best.pt至忽略目录 `.deps/research/`。SHA256：`d1c3536d8e4a873b7ff251d1cb81c0047100f06c86f92e08596de2d618618a3e`。

仅用ZIP/pickletools检查静态内容，没有执行第三方pickle：存在Ultralytics DetectionModel、Detect/DFL和4个病害类别字符串。仓库文件列表没有现成NCNN param/bin。此检查证明文件可获取和架构线索，不证明导出成功或推理准确。

### 和当前源码的兼容点

`yolov8_det.cpp`使用pnnx动态形状导出：输入in0，输出out0，原始DFL框分布64列加类别logits；COCO为144列，4类病害预期68列。现有解码通过 `pred.w - 64` 计算类别数，但draw仍绑定80类COCO名字，且车辆过滤绑定COCO编号。

[Ultralytics支持NCNN导出](https://docs.ultralytics.com/integrations/ncnn/)，但普通导出可能带已解码的输出，与这里的原始头输出不同。不能改后缀直接加载，也不能重复执行DFL/sigmoid。后续应沿上游源码注释步骤剥离后处理、导出动态shape，核验320/480/640与长宽比、类别顺序和框坐标；用相同测试图对比PyTorch与NCNN输出，再加入独立病害模式/标签与许可。暂不训练新模型。

## Mesa Turnip 的接入结论

Turnip是Adreno的Vulkan驱动，不是另一种NPU推理引擎。[Mesa官方说明](https://docs.mesa3d.org/drivers/freedreno.html)要求按源码设备表核对具体GPU；文档概述中旧的6xx描述不能据此判断所有7xx不可用。用户设备为骁龙8 Gen 2，需在其实际系统上核对Adreno740、KGSL和Vulkan能力。

当前NCNN20260526确实有 `create_gpu_instance(const char* driver_path)`，且预编译库启用了NCNN_SIMPLEVK。已检查固定版本的 [gpu.cpp](https://github.com/Tencent/ncnn/blob/20260526/src/gpu.cpp) 和 [simplevk.cpp](https://github.com/Tencent/ncnn/blob/20260526/src/simplevk.cpp)：先尝试普通Vulkan/ICD导出，再尝试Android HAL的HMI入口；失败可能自动尝试系统驱动。因此不能只看到“GPU可用”就宣称Turnip已生效。

### 实际下载与ELF检查

下载了 [K11MCH1的Turnip v26.0.0 R8普通包](https://github.com/K11MCH1/AdrenoToolsDrivers/releases/tag/v26.0.0-rc08)，没有下载Qualcomm闭源提取包、a8xx试验包或替换系统驱动。ZIP SHA256：`e634db0f929e2205e95511c769071817d0390180ec72c8e690bc76375e813715`；包含meta.json和17,159,433字节的 `vulkan.ad07xx.so`。发布页声明Mesa base commit `e9a83d530cce`，这是第三方构建，不等同Mesa官方Android二进制发布。

用NDK llvm-readelf检查：ARM64驱动有HMI导出，依赖libcutils.so、libhardware.so、libsync.so等Android平台库。普通应用链接器命名空间未必能访问这些库；现有ncnn的driver_path并不解决此限制。

[libadrenotools](https://github.com/bylaws/libadrenotools)提供Android9+ ARM64无Root应用内加载，但不是全系统驱动安装器。其[driver.h](https://github.com/bylaws/libadrenotools/blob/master/include/adrenotools/driver.h)明确要求原生库解包目录（useLegacyPackaging=true）；[实现](https://github.com/bylaws/libadrenotools/blob/master/src/driver.cpp)通过独立命名空间与hooks返回libvulkan句柄。当前应用useLegacyPackaging=false，NCNN只接受路径，不能直接传入该句柄。

本次不把未经真机验证的驱动打进常规APK，也不添加假Turnip开关。系统CPU/Vulkan继续使用原基线。后续独立实验应：固定Mesa/加载器源码与全部许可和哈希，构建隔离Vulkan桥或修改NCNN入口，核对实际driverName/driverID，失败回退并保证重启恢复CPU；在同一模型/输入/场景对比输出、耗时与发热。Turnip使用GPU，不会调用Hexagon NPU；不保证比系统驱动更快。此路线可行性仍需小米13的加载与推理实测。

下载包留在本机忽略目录供研究，未进入源码或APK。若未来分发，需按[Mesa组件许可](https://docs.mesa3d.org/license.html)保留相应版权/许可，而不能只贴单一“MIT”标签。
