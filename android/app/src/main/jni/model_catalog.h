// Generated from pinned model manifests. Keep per-model class order unchanged.
#pragma once
#include <string>
#include <vector>
struct ModelSpec { const char* param; const char* bin; int classes; std::vector<std::string> names; };
static const ModelSpec MODEL_SPECS[]={
{"yolov8n.ncnn.param","yolov8n.ncnn.bin",80,{}},
{"yolov8s.ncnn.param","yolov8s.ncnn.bin",80,{}},
{"rdd2022-yolov8n.ncnn.param","rdd2022-yolov8n.ncnn.bin",4,{"longitudinal crack","transverse crack","alligator crack","pothole"}},
{"rdd2022-yolov8s.ncnn.param","rdd2022-yolov8s.ncnn.bin",4,{"longitudinal crack","transverse crack","alligator crack","pothole"}},
{"bdd100k.ncnn.param","bdd100k.ncnn.bin",10,{"person","rider","car","truck","bus","train","motor","bike","traffic light","traffic sign"}},
{"signs36.ncnn.param","signs36.ncnn.bin",36,{"Cycle Zone","Danger Ahead","Deer Zone","End of Right Road -Go straight-","Give Way","Go Left or Straight","Go Right or Straight","Go Straight","Huddle Road","Left Curve Ahead","Left Sharp Curve","No Entry","No Over Taking Trucks","No Over Taking","No Stopping","No Waiting","Pedestrian","Right Curve Ahead","Right Sharp Curve","Road Work","RoundAbout","Slippery Road","Snow Warning Sign","Speed Limit 100","Speed Limit 120","Speed Limit 20","Speed Limit 30","Speed Limit 50","Speed Limit 60","Speed Limit 70","Speed Limit 80","Stop","Traffic Signals Ahead","Truck Sign","Turn Left","Turn Right"}},
{"signs21.ncnn.param","signs21.ncnn.bin",21,{"bus_stop","do_not_enter","do_not_stop","do_not_turn_l","do_not_turn_r","do_not_u_turn","enter_left_lane","green_light","left_right_lane","no_parking","parking","ped_crossing","ped_zebra_cross","railway_crossing","red_light","stop","t_intersection_l","traffic_light","u_turn","warning","yellow_light"}},
{"plate.ncnn.param","plate.ncnn.bin",1,{"license_plate"}},
{"garbage_v8.ncnn.param","garbage_v8.ncnn.bin",6,{"biological","cardboard","metal","paper","plastic","other"}},
{"litter_v11.ncnn.param","litter_v11.ncnn.bin",9,{"Plastic bag & wrapper","Cigarette","Bottle","Bottle cap","Can","Other plastic","Carton","Cup","Other"}},
};
static const int MODEL_COUNT=sizeof(MODEL_SPECS)/sizeof(MODEL_SPECS[0]);
