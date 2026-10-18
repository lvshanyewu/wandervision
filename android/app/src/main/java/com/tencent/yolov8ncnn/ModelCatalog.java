package com.tencent.yolov8ncnn;

/** Stable model identifiers also used in exported records. Class IDs are model-local. */
final class ModelCatalog {
    static final String[] IDS={"coco-yolov8n","coco-yolov8s","rdd2022-yolov8n","rdd2022-yolov8s"};
    static final String[] NAMES={"COCO Nano","COCO Small","路害 Nano","路害 Small"};
    static final String[] ROAD_LABELS={"longitudinal_crack","transverse_crack","alligator_crack","pothole"};
    static String name(int index){return NAMES[index];}
    static String[] labels(int index){return index>=2?ROAD_LABELS:InspectionReport.LABELS;}
}
