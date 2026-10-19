# 交通标志、路牌与车牌模型可行性调查

核查日期：2026-10-19。当前基线为wandervision 3.3。此次仅研究，未修改App、转换新模型或发布APK。下载文件均在项目忽略目录`.deps/research/traffic-signs/`，没有写D盘根目录。

## 结论与能力边界

交通标志检测/分类可以继续复用NCNN YOLOv8；已有可下载权重，不需要先训练。国内交通标志的精细语义识别，比“框出一块标志”需要更严格的数据和类别核对。路名/方向牌文字需要OCR；车牌框检测也不等同读出车牌号。

建议第一批只增加两个独立实验模式：36类交通标志模型，以及轻量BDD100K交通场景模型；不并行跑全部模型。前者验证限速/禁行等具体标签，后者提供车辆+标志+信号灯的道路视角对照。国内标志精细分类优先试现成PULC裁剪分类器；YOLO-TS留作更专门的TT100K研究候选。车牌先提供框选，号码识别单独选HyperLPR3。

## 可复用候选

| 候选及原始来源 | 已确认的物料/能力 | 接入评估 |
| --- | --- | --- |
| [ankitjha07/Traffic-Sign-Detection-YOLOv8](https://huggingface.co/ankitjha07/Traffic-Sign-Detection-YOLOv8) | best.pt约22.5MB；实际权重36类，包含限速20/30/50/60/70/80/100/120、禁止驶入、道路施工等；没有限速40类。模型卡标MIT | 标准YOLOv8模块，预计转换成本较低，适合第一轮具体标志演示。但数据来源/国内覆盖不充分，不能当作中国全套交通标志模型 |
| [dronefreak/bdd100k-yolov8n](https://huggingface.co/dronefreak/bdd100k-yolov8n) | best.pt约6.3MB；10类含car/truck/bus、traffic sign、traffic light；模型卡AGPL-3.0 | 轻量交通场景候选，和已集成RDD系列同发布者/类似标准架构。只区分“标志”大类，不知道限速数值，也不区分红黄绿；境外道路数据对国内泛化须实测 |
| [yahyagul/traffic-sign-yolov8](https://huggingface.co/yahyagul/traffic-sign-yolov8) | best_roboflow.pt约22.5MB；21类含禁止转向、停车、人行横道、红黄绿灯等；模型卡MIT | 适合信号灯颜色/禁行类补充对照，预计转换成本较低。但没有细分限速类别，国内覆盖也未经证明；红/黄/绿与traffic_light类别重叠须检查实景输出 |
| [PaddleClas PULC交通标志分类](https://github.com/PaddlePaddle/PaddleClas/blob/release/2.6/docs/zh_CN/models/PULC/PULC_traffic_sign.md) | 官方预训练推理包traffic_sign_infer.tar可访问，8,581,120字节；配置/标签表232类、输入224×224，使用TT100K标志裁剪训练 | 适合国内具体标志语义。它是分类器，不能独立在整帧找框；可串接YOLO检测→裁剪→分类。经Paddle2ONNX再转NCNN或用Paddle Lite，需另做数值验证，成本中等 |
| [YOLO-TS](https://github.com/Heqiang-Huang/YOLO-TS) | 作者提供TT100K等训练权重网盘入口，未下载核验；配置nc45，SimFusion_3in/dilation_block与单P2检测头 | 更贴近国内小目标交通标志，但不是普通v8换权重：当前解码stride8/16/32，该配置用P2/stride4，需修改网格/输出及自定义模块导出。仓库顶层许可元数据未明确，部分文件有AGPL标头，分发前还需核清；不作为低成本首选 |

PULC原始权重：[官方直接下载](https://paddleclas.bj.bcebos.com/models/PULC/traffic_sign_infer.tar)；[配置](https://github.com/PaddlePaddle/PaddleClas/blob/release/2.6/ppcls/configs/PULC/traffic_sign/PPLCNet_x1_0.yaml)；[类别表](https://github.com/PaddlePaddle/PaddleClas/blob/release/2.6/ppcls/utils/PULC_label_list/traffic_sign_label_list.txt)。232是该配置实际类别数，不应把TT100K常见45类/221类映射强套到这里。

模型卡许可只是发布者标注；打包仍需保留Ultralytics及模型来源、核对数据/权重条件，不能用模型卡MIT抹去其他来源条件。

## 车牌：定位与号码识别分开

- [joker5914/yolov8n-license-plate](https://huggingface.co/joker5914/yolov8n-license-plate)：已下载best.pt，约6.2MB，静态类别只有license_plate；另有ONNX。标准v8检测模块，适合低成本框选、拍照记录，但不识别号码，也未证明对中国新能源/双层牌的覆盖。模型卡AGPL-3.0。
- [HyperLPR3 Android SDK](https://github.com/HyperInspire/hyperlpr3-android-sdk)及[主项目](https://github.com/szad670401/HyperLPR)：现成Android API、ARM64库/源码，Apache-2.0；主项目明确支持常见蓝牌、黄牌、新能源牌，并说明特殊牌有限支持。它是完整中文车牌检测+识别轮子，优于为了“全部YOLO”重复造OCR。原生端依赖MNN/OpenCV，需要核对现有NDK、库重复和16KiB对齐，不能当作已有NCNN模型直接装载。优先做拍照后按需识别，避免每帧多引擎竞争。
- [we0091234/yolov8-plate](https://github.com/we0091234/yolov8-plate)：有检测+字符识别权重使用流程、双层处理代码。检测源码还读取关键点与专门输出，非普通bbox头；顶层未发现明确LICENSE，适合作技术参考，当前不优先直接分发。

如果目标是读“某某路、前方目的地”等路牌文字，需要增加[PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)一类文字检测/识别链路。标志类别YOLO不会自动读文字。可以后续先对取景照片离线按需OCR，成本高于加一个检测模型。

## 对当前App的实际影响

1. `ndkcamera.cpp`当前`AImageReader_new(640,480,...)`。远处标志和车牌像素很少；把YOLO输入从320改640不能恢复原始细节。需要实测720p/1080p采集、再缩放检测，保留原分辨率ROI用于分类/OCR，观察温度和FPS。
2. 普通YOLOv8候选预计可沿现有原始DFL导出流程适配，分别为64+10/36/21/1列。尚未实际导出，不能宣称算子或动态尺寸已通过。
3. 扩展ModelCatalog为每模型独立类别数/标签/能力，原生解码做形状校验；渲染和导出JSON使用对应model_id，避免COCO/RDD编号混用。分类输出应另附classification标签/分数，不能覆盖原检测置信度。
4. 保留COCO/RDD；新模型通过菜单单选，先640输入测试小目标。需要未知/低置信度状态，尤其分类器不能把所有陌生标志强行解释成已知类别。
5. 先进行转换及固定图片对照，再交小米13实测。夜间反光、运动模糊、遮挡、国内字形和远距离效果不能从作者mAP或桌面FPS推断。

## 实际核验与排除项

已匿名下载BDD100K、36类标志、21类标志、单类车牌四份固定revision权重，SHA256与HF LFS记录一致；使用ZIP/pickletools静态检查类别和模块，没有执行第三方pickle、推理或训练。均发现DetectionModel/Detect/DFL/C2f/SPPF等标准模块。完整revision、哈希和类别见[核验清单](traffic-sign-model-candidates.json)。这说明存在可检查的物料，不证明检测准确或NCNN转换成功。

[youssefhazem/yolov8s-tt100k](https://huggingface.co/youssefhazem/yolov8s-tt100k/tree/main)当前文件API仅.gitattributes；[Genius-Society/yolov4_tt100k](https://huggingface.co/Genius-Society/yolov4_tt100k/tree/main)当前仅README/.gitattributes。虽然名称/介绍相关，此次没有发现实际权重，不列为即取即用模型。YOLO-TS仅核对源码与下载入口，没有核验网盘内文件。
