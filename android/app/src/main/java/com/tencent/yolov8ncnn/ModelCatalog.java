package com.tencent.yolov8ncnn;

/** Each experimental detector owns its labels; IDs are stable in saved reports. */
final class ModelCatalog {
    static final String[] IDS={"coco-yolov8n", "coco-yolov8s", "rdd2022-yolov8n", "rdd2022-yolov8s", "bdd100k-yolov8n", "traffic-signs36-yolov8", "traffic-signs21-yolov8", "plate-yolov8n", "garbage-yolov8s", "litter-yolo11s"};
    static final String[] NAMES={"COCO Nano", "COCO Small", "路害 Nano", "路害 Small", "道路 Nano", "标志 36类", "标志 21类", "车牌 + OCR", "垃圾 6类", "遗留物 9类"};
    static final int COUNT=IDS.length;
    static final String[] ROAD_LABELS={"longitudinal_crack","transverse_crack","alligator_crack","pothole"};
    static final String[][] EXTRA={
        {"person", "rider", "car", "truck", "bus", "train", "motor", "bike", "traffic light", "traffic sign"},
        {"Cycle Zone", "Danger Ahead", "Deer Zone", "End of Right Road -Go straight-", "Give Way", "Go Left or Straight", "Go Right or Straight", "Go Straight", "Huddle Road", "Left Curve Ahead", "Left Sharp Curve", "No Entry", "No Over Taking Trucks", "No Over Taking", "No Stopping", "No Waiting", "Pedestrian", "Right Curve Ahead", "Right Sharp Curve", "Road Work", "RoundAbout", "Slippery Road", "Snow Warning Sign", "Speed Limit 100", "Speed Limit 120", "Speed Limit 20", "Speed Limit 30", "Speed Limit 50", "Speed Limit 60", "Speed Limit 70", "Speed Limit 80", "Stop", "Traffic Signals Ahead", "Truck Sign", "Turn Left", "Turn Right"},
        {"bus_stop", "do_not_enter", "do_not_stop", "do_not_turn_l", "do_not_turn_r", "do_not_u_turn", "enter_left_lane", "green_light", "left_right_lane", "no_parking", "parking", "ped_crossing", "ped_zebra_cross", "railway_crossing", "red_light", "stop", "t_intersection_l", "traffic_light", "u_turn", "warning", "yellow_light"},
        {"license_plate"},
        {"biological", "cardboard", "metal", "paper", "plastic", "other"},
        {"Plastic bag & wrapper", "Cigarette", "Bottle", "Bottle cap", "Can", "Other plastic", "Carton", "Cup", "Other"}
    };
    static String name(int index){return NAMES[index];}
    static String[] labels(int index){return index<2?InspectionReport.LABELS:index<4?ROAD_LABELS:EXTRA[index-4];}
    static boolean vehicles(int index){return index<2||index==4;}
    static boolean road(int index){return index==2||index==3;}
    static String modeName(int index,int mode){return mode==2?"取景":index<2?(mode==1?"车辆":"通用"):road(index)?"路害":index==4?"交通场景":index==5||index==6?"交通标志":index==7?"车牌":index==8||index==9?"道路异物":"实验";}
    static String modeId(int index,int mode){return mode==2?"viewfinder":index<2?(mode==1?"vehicles":"general"):road(index)?"road_damage":index==4?"traffic_scene":index==5||index==6?"traffic_signs":index==7?"license_plate":"road_litter";}
}
