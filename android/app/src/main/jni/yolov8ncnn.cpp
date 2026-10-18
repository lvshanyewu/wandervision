// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <algorithm>
#include <vector>
#include <mutex>
#include <condition_variable>
#include <chrono>
#include <time.h>
#include <cmath>

#include <platform.h>
#include <gpu.h>
#include <benchmark.h>

#include "yolov8.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static YOLOv8* g_yolov8 = 0;
static bool g_vehicle_only = false;
static bool g_detection_enabled = true;
static float g_stats[7] = {0}; // fps, inference ms, visible detections, max confidence, frames, backend, ready
static double g_last_frame = 0;
static double g_frame_interval = 0;
static ncnn::Mutex lock;
// Copy only the next requested frame, not every preview frame. Pixels and objects
// are captured under the inference lock before this same annotated frame is rendered.
static std::mutex capture_lock;
static std::condition_variable capture_ready;
static bool capture_wanted = false;
static cv::Mat capture_rgb;
static std::vector<Object> capture_objects;
static float capture_stats[7];
static jlong capture_elapsed_ns = 0, capture_frame_id = 0, frame_counter = 0;
static std::mutex live_lock;
static bool live_enabled = false;
static cv::Mat live_rgb;
static double live_at = 0;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const {
    ncnn::MutexLockGuard guard(lock);
    double now = ncnn::get_current_time();
    double delta = now - g_last_frame;
    if (g_last_frame > 0 && delta > 0 && delta < 1500) {
        g_frame_interval = g_frame_interval == 0 ? delta : g_frame_interval*0.8 + delta*0.2;
        g_stats[0] = 1000.f / g_frame_interval;
    }
    g_last_frame = now;
    g_stats[1]=0; g_stats[2]=0; g_stats[3]=0;
    std::vector<Object> objects;
    if (g_yolov8 && g_detection_enabled) {
        double begin = ncnn::get_current_time();
        g_yolov8->detect(rgb, objects);
        g_stats[1] = ncnn::get_current_time()-begin;
        if (g_vehicle_only) {
            objects.erase(std::remove_if(objects.begin(), objects.end(), [](const Object& o) {
                return o.label != 1 && o.label != 2 && o.label != 3 && o.label != 5 && o.label != 7;
            }), objects.end());
        }
        g_stats[2]=objects.size();
        for (const auto& o:objects) g_stats[3]=std::max(g_stats[3],o.prob);
        g_yolov8->draw(rgb, objects);
    }
    g_stats[4]++;
    ++frame_counter;
    {
        std::lock_guard<std::mutex> live_guard(live_lock);
        if (live_enabled && now-live_at >= 80) {
            const float scale = std::min(1.f, 640.f / std::max(rgb.cols, rgb.rows));
            cv::resize(rgb, live_rgb, cv::Size(std::max(1,(int)(rgb.cols*scale)), std::max(1,(int)(rgb.rows*scale))));
            live_at = now;
        }
    }
    std::lock_guard<std::mutex> capture_guard(capture_lock);
    if (capture_wanted) {
        rgb.copyTo(capture_rgb);
        capture_objects = objects;
        std::copy(g_stats, g_stats + 7, capture_stats);
        struct timespec ts;
        clock_gettime(CLOCK_BOOTTIME, &ts);
        capture_elapsed_ns = (jlong)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        capture_frame_id = frame_counter;
        capture_wanted = false;
        capture_ready.notify_one();
    }
}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_smokeModel(JNIEnv*, jobject, jint width, jint height) {
    if(width<32||height<32||width>640||height>640)return JNI_FALSE;
    ncnn::MutexLockGuard guard(lock);
    if(!g_yolov8)return JNI_FALSE;
    cv::Mat input(height,width,CV_8UC3,cv::Scalar(128,128,128));std::vector<Object> objects;
    if(g_yolov8->detect(input,objects)!=0)return JNI_FALSE;
    for(const auto& o:objects)if(!std::isfinite(o.prob)||o.label<0||!std::isfinite(o.rect.x)||!std::isfinite(o.rect.y))return JNI_FALSE;
    g_yolov8->draw(input,objects);return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setLiveEnabled(JNIEnv*, jobject, jboolean enabled) {
    std::lock_guard<std::mutex> guard(live_lock);
    live_enabled = enabled; live_rgb.release(); live_at = 0;
}
JNIEXPORT jintArray JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_takeLivePixels(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(live_lock);
    if (!live_enabled || live_rgb.empty() || ncnn::get_current_time()-live_at > 500) return nullptr;
    int w=live_rgb.cols, h=live_rgb.rows;
    std::vector<jint> pixels(w*h+2);pixels[0]=w;pixels[1]=h;
    for(int y=0;y<h;y++) { const unsigned char* row=live_rgb.ptr<unsigned char>(y);
        for(int x=0;x<w;x++) pixels[2+y*w+x]=(jint)(0xff000000u|(row[x*3]<<16)|(row[x*3+1]<<8)|row[x*3+2]);
    }
    live_rgb.release();
    jintArray result=env->NewIntArray(pixels.size());
    if(result)env->SetIntArrayRegion(result,0,pixels.size(),pixels.data());
    return result;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolov8;
        g_yolov8 = 0;
    }

    ncnn::destroy_gpu_instance();

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int taskid, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint taskid, jint modelid, jint cpugpu, jint capacity)
{
    if (taskid < 0 || taskid > 1 || modelid < 0 || modelid > 2 || cpugpu < 0 || cpugpu > 1 || capacity < 0 || capacity > 3)
        return JNI_FALSE;
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    ncnn::MutexLockGuard g(lock);
    // Keep the upstream dynamic-shape COCO detection chain; only filter drawn classes.
    delete g_yolov8;
    g_yolov8 = 0;
    ncnn::destroy_gpu_instance();
    bool use_gpu = cpugpu == 1;
    if (use_gpu) {
        ncnn::create_gpu_instance();
        if (ncnn::get_gpu_count() == 0) use_gpu = false;
    }
    auto* detector = new YOLOv8_det_coco;
    detector->road = capacity >= 2;
    detector->classes = capacity >= 2 ? 4 : 80;
    g_yolov8 = detector;
    const char* params[] = {"yolov8n.ncnn.param","yolov8s.ncnn.param","rdd2022-yolov8n.ncnn.param","rdd2022-yolov8s.ncnn.param"};
    const char* bins[] = {"yolov8n.ncnn.bin","yolov8s.ncnn.bin","rdd2022-yolov8n.ncnn.bin","rdd2022-yolov8s.ncnn.bin"};
    const char* param = params[capacity];
    const char* weights = bins[capacity];
    g_stats[6]=0;
    if (g_yolov8->load(mgr, param, weights, use_gpu) != 0) {
        delete g_yolov8;
        g_yolov8 = 0;
        return JNI_FALSE;
    }
    const int sizes[] = {320, 480, 640};
    g_yolov8->set_det_target_size(sizes[modelid]);
    g_vehicle_only = taskid == 1 && capacity < 2;
    g_stats[5]=use_gpu ? 1 : 0; g_stats[6]=1;
    g_stats[0]=0; g_stats[1]=0; g_stats[2]=0; g_stats[3]=0; g_last_frame=0; g_frame_interval=0;
    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    { ncnn::MutexLockGuard guard(lock); g_last_frame=0; g_frame_interval=0; g_stats[0]=0; }
    return g_camera->open((int)facing) == 0 ? JNI_TRUE : JNI_FALSE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);
    ANativeWindow_release(win);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_configureView(JNIEnv*, jobject, jint rotation, jint width, jint height) {
    g_camera->configure_view(rotation, width, height);
}
JNIEXPORT void JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setDetectionEnabled(JNIEnv*, jobject, jboolean enabled) {
    ncnn::MutexLockGuard guard(lock);
    g_detection_enabled=enabled;
}
JNIEXPORT jfloatArray JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_getStats(JNIEnv* env, jobject) {
    ncnn::MutexLockGuard guard(lock);
    jfloatArray result=env->NewFloatArray(7);
    env->SetFloatArrayRegion(result,0,7,g_stats);
    return result;
}

JNIEXPORT jobject JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_captureFrame(JNIEnv* env, jobject) {
    std::unique_lock<std::mutex> guard(capture_lock);
    capture_wanted = true;
    if (!capture_ready.wait_for(guard, std::chrono::milliseconds(2000), []{ return !capture_wanted; })) {
        capture_wanted = false;
        return nullptr;
    }
    if (capture_rgb.empty()) return nullptr;
    const int width = capture_rgb.cols, height = capture_rgb.rows;
    std::vector<jint> pixels(width * height);
    for (int y=0; y<height; y++) {
        const unsigned char* row = capture_rgb.ptr<unsigned char>(y);
        for (int x=0; x<width; x++) pixels[y*width+x] = (jint)(0xff000000u | (row[x*3]<<16) | (row[x*3+1]<<8) | row[x*3+2]);
    }
    std::vector<float> boxes;
    for (const auto& o : capture_objects) {
        boxes.insert(boxes.end(), {(float)o.label, o.prob, o.rect.x/width, o.rect.y/height, o.rect.width/width, o.rect.height/height});
    }
    jintArray data=env->NewIntArray(pixels.size());
    jfloatArray metrics=env->NewFloatArray(7), detections=env->NewFloatArray(boxes.size());
    if (!data || !metrics || !detections) return nullptr;
    env->SetIntArrayRegion(data,0,pixels.size(),pixels.data());
    env->SetFloatArrayRegion(metrics,0,7,capture_stats);
    if (!boxes.empty()) env->SetFloatArrayRegion(detections,0,boxes.size(),boxes.data());
    jclass cls=env->FindClass("com/tencent/yolov8ncnn/FrameCapture");
    jmethodID ctor=env->GetMethodID(cls,"<init>","([III[F[FJJ)V");
    jobject result=env->NewObject(cls,ctor,data,width,height,metrics,detections,capture_frame_id,capture_elapsed_ns);
    capture_rgb.release(); capture_objects.clear();
    return result;
}

}
