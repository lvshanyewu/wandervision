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
    if (g_yolov8 && g_detection_enabled) {
        std::vector<Object> objects;
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
}

static MyNdkCamera* g_camera = 0;

extern "C" {

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
    if (taskid < 0 || taskid > 1 || modelid < 0 || modelid > 2 || cpugpu < 0 || cpugpu > 1 || capacity < 0 || capacity > 1)
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
    g_yolov8 = new YOLOv8_det_coco;
    const char* param = capacity == 0 ? "yolov8n.ncnn.param" : "yolov8s.ncnn.param";
    const char* weights = capacity == 0 ? "yolov8n.ncnn.bin" : "yolov8s.ncnn.bin";
    g_stats[6]=0;
    if (g_yolov8->load(mgr, param, weights, use_gpu) != 0) {
        delete g_yolov8;
        g_yolov8 = 0;
        return JNI_FALSE;
    }
    const int sizes[] = {320, 480, 640};
    g_yolov8->set_det_target_size(sizes[modelid]);
    g_vehicle_only = taskid == 1;
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

}
