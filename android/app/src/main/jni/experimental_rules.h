// Small, detector-independent experimental rules; no real-world distance/speed inference.
#pragma once
#include <sstream>
#include <locale>
#include <set>

class ExperimentalRules {
    struct Track {int id,label,hits=0,flags=0;float x,y,w,h,firstX,firstY,anchorX,anchorY,side=0;double first,last,stable;};
    std::vector<Track> tracks;int nextId=1;
    static float distance(float x,float y,float a,float b){return std::hypot(x-a,y-b);}
    bool vehicle(int model,int label)const{return model==4?(label>=2&&label<=7):(model<2&&(label==1||label==2||label==3||label==5||label==7));}
    bool litter(int model,int label)const{return model==8||model==9||(model<2&&(label==24||label==25||label==26||label==28||label==39||label==41||label==56));}
public:
    int enabled=0,direction=0;float left=.1f,top=.4f,right=.9f,bottom=.92f;double dwellMs=5000;
    void reset(){tracks.clear();nextId=1;}
    void configure(int flags,float l,float t,float r,float b,int dir,double dwell){enabled=flags;left=l;top=t;right=r;bottom=b;direction=dir;dwellMs=dwell;reset();}
    std::string process(cv::Mat& image,std::vector<Object>& objects,int model,double now){
        if(!enabled)return "{\"enabled\":false,\"event_count\":0}";
        tracks.erase(std::remove_if(tracks.begin(),tracks.end(),[&](const Track& t){return now-t.last>2000;}),tracks.end());
        std::set<int> used;std::vector<int> visible;
        for(auto& object:objects){
            bool veh=vehicle(model,object.label),debris=litter(model,object.label);
            if((!veh&&!(debris&&(enabled&8)))||object.prob<.5f)continue;
            float x=object.rect.x/image.cols,y=object.rect.y/image.rows,w=object.rect.width/image.cols,h=object.rect.height/image.rows,cx=x+w*.5f,cy=y+h*.5f;
            int best=-1;float bestCost=.12f;
            for(size_t i=0;i<tracks.size();i++){
                const auto& t=tracks[i];if(t.label!=object.label||used.count(t.id))continue;
                float ratio=(w*h+1e-6f)/(t.w*t.h+1e-6f);if(ratio<.4f||ratio>2.5f)continue;
                float intersection=std::max(0.f,std::min(x+w,t.x+t.w)-std::max(x,t.x))*std::max(0.f,std::min(y+h,t.y+t.h)-std::max(y,t.y));
                float iou=intersection/std::max(1e-6f,w*h+t.w*t.h-intersection);
                float d=distance(cx,cy,t.x+t.w*.5f,t.y+t.h*.5f);
                if((iou>.1f||d<.06f)&&d<bestCost){bestCost=d;best=i;}
            }
            if(best<0){if(tracks.size()>=256)continue;Track t;t.id=nextId++;t.label=object.label;t.firstX=t.anchorX=cx;t.firstY=t.anchorY=cy;t.first=t.last=t.stable=now;t.x=x;t.y=y;t.w=w;t.h=h;tracks.push_back(t);best=tracks.size()-1;}
            Track& t=tracks[best];used.insert(t.id);visible.push_back(best);t.hits++;t.x=x;t.y=y;t.w=w;t.h=h;t.last=now;
            if(distance(cx,cy,t.anchorX,t.anchorY)>.025f){t.anchorX=cx;t.anchorY=cy;t.stable=now;}
            bool inside=cx>=left&&cx<=right&&cy>=top&&cy<=bottom;
            float side=direction<2?cy-(top+bottom)*.5f:cx-(left+right)*.5f;
            if(t.hits>=5&&now-t.first>=800&&inside){
                if(veh&&(enabled&1)&&now-t.stable>=dwellMs)t.flags|=1;
                float dx=cx-t.firstX,dy=cy-t.firstY;
                float opposite=direction==0?dy:direction==1?-dy:direction==2?dx:-dx;
                if(veh&&(enabled&2)&&opposite>.1f)t.flags|=2;
                if(veh&&(enabled&4)&&side*t.side<0&&std::abs(side-t.side)>.002f)t.flags|=4;
                if(debris&&(enabled&8))t.flags|=8;
            }
            if(std::abs(side)>.002f)t.side=side;
            char label[72];snprintf(label,sizeof(label),"ID %d%s",t.id,t.flags?"  CHECK":"");
            cv::putText(image,label,cv::Point(std::max(0,(int)object.rect.x),std::min(image.rows-3,(int)(object.rect.y+object.rect.height+12))),cv::FONT_HERSHEY_SIMPLEX,.35,cv::Scalar(255,223,27),1,cv::LINE_AA);
        }
        cv::rectangle(image,cv::Point(left*image.cols,top*image.rows),cv::Point(right*image.cols,bottom*image.rows),cv::Scalar(255,223,27),1);
        if(enabled&4){if(direction<2)cv::line(image,cv::Point(left*image.cols,(top+bottom)*.5f*image.rows),cv::Point(right*image.cols,(top+bottom)*.5f*image.rows),cv::Scalar(255,223,27),1);else cv::line(image,cv::Point((left+right)*.5f*image.cols,top*image.rows),cv::Point((left+right)*.5f*image.cols,bottom*image.rows),cv::Scalar(255,223,27),1);}
        std::ostringstream out;out.imbue(std::locale::classic());int events=0;
        out<<"{\"enabled\":true,\"experimental\":true,\"camera_assumption\":\""<<((enabled&7)?"fixed":"user_selected_road_roi")<<"\",\"tracking_algorithm\":\"class_aware_iou_centroid\",\"direction\":"<<direction<<",\"dwell_threshold_ms\":"<<dwellMs<<",\"roi_xyxy_normalized\":["<<left<<","<<top<<","<<right<<","<<bottom<<"],\"tracks\":[";
        bool first=true;for(int index:visible){const auto& t=tracks[index];if(!first)out<<",";first=false;
            out<<"{\"track_id\":"<<t.id<<",\"class_id\":"<<t.label<<",\"age_ms\":"<<(now-t.first)<<",\"stationary_ms\":"<<(now-t.stable)<<",\"bbox_xywh_normalized\":["<<t.x<<","<<t.y<<","<<t.w<<","<<t.h<<"],\"flags\":[";
            bool comma=false;const char* names[]={"suspected_roi_dwell","suspected_wrong_way","virtual_line_crossing","suspected_road_object"};
            for(int bit=0;bit<4;bit++)if(t.flags&(1<<bit)){if(comma)out<<",";out<<"\""<<names[bit]<<"\"";comma=true;events++;}out<<"]}";
        }
        out<<"],\"event_count\":"<<events<<"}";return out.str();
    }
};
