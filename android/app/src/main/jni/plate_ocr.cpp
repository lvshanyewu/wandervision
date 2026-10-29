// HyperLPR3 recognition preprocessing and CTC decoding adapted under Apache-2.0.
// Source/revision, model provenance and changes: assets/licenses/EXPERIMENTS.md.
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <net.h>
#include <algorithm>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

extern "C" JNIEXPORT jobjectArray JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_readPlateText(
    JNIEnv* env,jobject,jobject assets,jintArray colors,jint width,jint height,jfloatArray boxes) {
    jclass stringClass=env->FindClass("java/lang/String");
    if(width<1||height<1||width>4096||height>4096||env->GetArrayLength(colors)!=(jlong)width*height||env->GetArrayLength(boxes)%6) return nullptr;
    int count=std::min(3,(int)env->GetArrayLength(boxes)/6);
    if(count==0)return env->NewObjectArray(0,stringClass,nullptr);
    AAssetManager* mgr=AAssetManager_fromJava(env,assets);
    AAsset* tokensAsset=AAssetManager_open(mgr,"plateocr-tokens.txt",AASSET_MODE_BUFFER);
    if(!tokensAsset)return nullptr;
    std::string tokenText((const char*)AAsset_getBuffer(tokensAsset),AAsset_getLength(tokensAsset));AAsset_close(tokensAsset);
    std::istringstream lines(tokenText);std::vector<std::string> tokens;std::string line;
    while(std::getline(lines,line)){if(!line.empty()&&line.back()=='\r')line.pop_back();tokens.push_back(line);}
    ncnn::Net net;net.opt.num_threads=2;net.opt.use_vulkan_compute=false;
    if(net.load_param(mgr,"plateocr.ncnn.param")||net.load_model(mgr,"plateocr.ncnn.bin"))return nullptr;
    std::vector<jint> input(width*height);std::vector<jfloat> detections(count*6);
    env->GetIntArrayRegion(colors,0,input.size(),input.data());env->GetFloatArrayRegion(boxes,0,detections.size(),detections.data());
    std::vector<std::string> results;
    for(int i=0;i<count;i++){
        const float* box=detections.data()+i*6;
        bool finite=true;for(int k=1;k<6;k++)if(!std::isfinite(box[k]))finite=false;if(!finite)continue;
        int x=std::max(0,(int)(box[2]*width)),y=std::max(0,(int)(box[3]*height));
        int right=std::min(width,(int)std::ceil((box[2]+box[4])*width)),bottom=std::min(height,(int)std::ceil((box[3]+box[5])*height));
        int w=right-x,h=bottom-y;if(w<16||h<8||x>=width||y>=height)continue;
        std::vector<unsigned char> rgb(w*h*3);
        for(int row=0;row<h;row++)for(int col=0;col<w;col++){unsigned int value=input[(y+row)*width+x+col];int pos=(row*w+col)*3;rgb[pos]=(value>>16)&255;rgb[pos+1]=(value>>8)&255;rgb[pos+2]=value&255;}
        int resized=std::min(160,std::max(48,(int)std::ceil(48.f*w/h)));
        ncnn::Mat image=ncnn::Mat::from_pixels_resize(rgb.data(),ncnn::Mat::PIXEL_RGB2BGR,w,h,resized,48);
        const float mean[]={127.5f,127.5f,127.5f},norm[]={1/127.5f,1/127.5f,1/127.5f};image.substract_mean_normalize(mean,norm);
        ncnn::Mat padded;ncnn::copy_make_border(image,padded,0,0,0,160-resized,ncnn::BORDER_CONSTANT,0.f);
        auto ex=net.create_extractor();if(ex.input("in0",padded))continue;ncnn::Mat out;
        if(ex.extract("out0",out)||out.dims!=2||out.w!=78||out.h!=20)continue;
        int previous=-1,characters=0;float sum=0;bool unknown=false;std::string text;
        for(int step=0;step<out.h;step++){
            const float* row=out.row(step);int best=0;for(int k=1;k<out.w;k++)if(row[k]>row[best])best=k;
            if(best!=0&&best!=previous){if(best>=(int)tokens.size())unknown=true;else{text+=tokens[best];sum+=row[best];characters++;}}previous=best;
        }
        // The source model has 78 outputs but the published dictionary only 77.
        // Reject unlisted symbols instead of shifting class IDs or guessing a glyph.
        if(unknown){text="";characters=0;sum=0;}
        results.push_back(std::to_string(i));results.push_back(text);results.push_back(std::to_string(characters?sum/characters:0));
    }
    jobjectArray result=env->NewObjectArray(results.size(),stringClass,nullptr);
    for(size_t i=0;i<results.size();i++){jstring text=env->NewStringUTF(results[i].c_str());env->SetObjectArrayElement(result,i,text);env->DeleteLocalRef(text);}
    return result;
}
