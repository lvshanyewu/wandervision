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

#include "ndkcamera.h"

#include <string>

#include <android/log.h>

#include <opencv2/core/core.hpp>

#include "mat.h"

static void onDisconnected(void* context, ACameraDevice* device)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onDisconnected %p", device);
}

static void onError(void* context, ACameraDevice* device, int error)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onError %p %d", device, error);
}

static void onImageAvailable(void* context, AImageReader* reader)
{
//     __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onImageAvailable %p", reader);

    AImage* image = 0;
    media_status_t status = AImageReader_acquireLatestImage(reader, &image);

    if (status != AMEDIA_OK)
    {
        // error
        return;
    }

    int32_t format;
    AImage_getFormat(image, &format);

    // assert format == AIMAGE_FORMAT_YUV_420_888

    int32_t width = 0;
    int32_t height = 0;
    AImage_getWidth(image, &width);
    AImage_getHeight(image, &height);

    int32_t y_pixelStride = 0;
    int32_t u_pixelStride = 0;
    int32_t v_pixelStride = 0;
    AImage_getPlanePixelStride(image, 0, &y_pixelStride);
    AImage_getPlanePixelStride(image, 1, &u_pixelStride);
    AImage_getPlanePixelStride(image, 2, &v_pixelStride);

    int32_t y_rowStride = 0;
    int32_t u_rowStride = 0;
    int32_t v_rowStride = 0;
    AImage_getPlaneRowStride(image, 0, &y_rowStride);
    AImage_getPlaneRowStride(image, 1, &u_rowStride);
    AImage_getPlaneRowStride(image, 2, &v_rowStride);

    uint8_t* y_data = 0;
    uint8_t* u_data = 0;
    uint8_t* v_data = 0;
    int y_len = 0;
    int u_len = 0;
    int v_len = 0;
    AImage_getPlaneData(image, 0, &y_data, &y_len);
    AImage_getPlaneData(image, 1, &u_data, &u_len);
    AImage_getPlaneData(image, 2, &v_data, &v_len);

    if (u_data == v_data + 1 && v_data == y_data + width * height && y_pixelStride == 1 && u_pixelStride == 2 && v_pixelStride == 2 && y_rowStride == width && u_rowStride == width && v_rowStride == width)
    {
        // already nv21  :)
        ((NdkCamera*)context)->on_image((unsigned char*)y_data, (int)width, (int)height);
    }
    else
    {
        // construct nv21
        unsigned char* nv21 = new unsigned char[width * height + width * height / 2];
        {
            // Y
            unsigned char* yptr = nv21;
            for (int y=0; y<height; y++)
            {
                const unsigned char* y_data_ptr = y_data + y_rowStride * y;
                for (int x=0; x<width; x++)
                {
                    yptr[0] = y_data_ptr[0];
                    yptr++;
                    y_data_ptr += y_pixelStride;
                }
            }

            // UV
            unsigned char* uvptr = nv21 + width * height;
            for (int y=0; y<height/2; y++)
            {
                const unsigned char* v_data_ptr = v_data + v_rowStride * y;
                const unsigned char* u_data_ptr = u_data + u_rowStride * y;
                for (int x=0; x<width/2; x++)
                {
                    uvptr[0] = v_data_ptr[0];
                    uvptr[1] = u_data_ptr[0];
                    uvptr += 2;
                    v_data_ptr += v_pixelStride;
                    u_data_ptr += u_pixelStride;
                }
            }
        }

        ((NdkCamera*)context)->on_image((unsigned char*)nv21, (int)width, (int)height);

        delete[] nv21;
    }

    AImage_delete(image);
}

static void onSessionActive(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionActive %p", session);
}

static void onSessionReady(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionReady %p", session);
}

static void onSessionClosed(void* context, ACameraCaptureSession *session)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onSessionClosed %p", session);
}

void onCaptureFailed(void* context, ACameraCaptureSession* session, ACaptureRequest* request, ACameraCaptureFailure* failure)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureFailed %p %p %p", session, request, failure);
}

void onCaptureSequenceCompleted(void* context, ACameraCaptureSession* session, int sequenceId, int64_t frameNumber)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureSequenceCompleted %p %d %ld", session, sequenceId, frameNumber);
}

void onCaptureSequenceAborted(void* context, ACameraCaptureSession* session, int sequenceId)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureSequenceAborted %p %d", session, sequenceId);
}

void onCaptureCompleted(void* context, ACameraCaptureSession* session, ACaptureRequest* request, const ACameraMetadata* result)
{
//     __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "onCaptureCompleted %p %p %p", session, request, result);
}

NdkCamera::NdkCamera()
{
    camera_facing = 0;
    camera_orientation = 0;

    camera_manager = 0;
    camera_device = 0;
    image_reader = 0;
    image_reader_surface = 0;
    image_reader_target = 0;
    capture_request = 0;
    capture_session_output_container = 0;
    capture_session_output = 0;
    capture_session = 0;


    // setup imagereader and its surface
    {
        AImageReader_new(640, 480, AIMAGE_FORMAT_YUV_420_888, /*maxImages*/2, &image_reader);

        AImageReader_ImageListener listener;
        listener.context = this;
        listener.onImageAvailable = onImageAvailable;

        AImageReader_setImageListener(image_reader, &listener);

        AImageReader_getWindow(image_reader, &image_reader_surface);

        ANativeWindow_acquire(image_reader_surface);
    }
}

NdkCamera::~NdkCamera()
{
    close();

    if (image_reader)
    {
        AImageReader_delete(image_reader);
        image_reader = 0;
    }

    if (image_reader_surface)
    {
        ANativeWindow_release(image_reader_surface);
        image_reader_surface = 0;
    }
}

int NdkCamera::open(int _camera_facing)
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "open");

    camera_facing = _camera_facing;

    camera_manager = ACameraManager_create();
    if (!camera_manager) return -1;

    // find front camera
    std::string camera_id;
    {
        ACameraIdList* camera_id_list = 0;
        if (ACameraManager_getCameraIdList(camera_manager, &camera_id_list) != ACAMERA_OK || !camera_id_list)
        {
            close();
            return -1;
        }

        for (int i = 0; i < camera_id_list->numCameras; ++i)
        {
            const char* id = camera_id_list->cameraIds[i];
            ACameraMetadata* camera_metadata = 0;
            ACameraManager_getCameraCharacteristics(camera_manager, id, &camera_metadata);

            // query faceing
            acamera_metadata_enum_android_lens_facing_t facing = ACAMERA_LENS_FACING_FRONT;
            {
                ACameraMetadata_const_entry e = { 0 };
                ACameraMetadata_getConstEntry(camera_metadata, ACAMERA_LENS_FACING, &e);
                facing = (acamera_metadata_enum_android_lens_facing_t)e.data.u8[0];
            }

            if (camera_facing == 0 && facing != ACAMERA_LENS_FACING_FRONT)
            {
                ACameraMetadata_free(camera_metadata);
                continue;
            }

            if (camera_facing == 1 && facing != ACAMERA_LENS_FACING_BACK)
            {
                ACameraMetadata_free(camera_metadata);
                continue;
            }

            camera_id = id;

            // query orientation
            int orientation = 0;
            {
                ACameraMetadata_const_entry e = { 0 };
                ACameraMetadata_getConstEntry(camera_metadata, ACAMERA_SENSOR_ORIENTATION, &e);

                orientation = (int)e.data.i32[0];
            }

            camera_orientation = orientation;

            ACameraMetadata_free(camera_metadata);

            break;
        }

        ACameraManager_deleteCameraIdList(camera_id_list);
    }

    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "open %s %d", camera_id.c_str(), camera_orientation);

    // open camera
    {
        ACameraDevice_StateCallbacks camera_device_state_callbacks;
        camera_device_state_callbacks.context = this;
        camera_device_state_callbacks.onDisconnected = onDisconnected;
        camera_device_state_callbacks.onError = onError;

        if (camera_id.empty() || ACameraManager_openCamera(camera_manager, camera_id.c_str(), &camera_device_state_callbacks, &camera_device) != ACAMERA_OK || !camera_device)
        {
            close();
            return -1;
        }
    }

    // capture request
    {
        ACameraDevice_createCaptureRequest(camera_device, TEMPLATE_PREVIEW, &capture_request);

        ACameraOutputTarget_create(image_reader_surface, &image_reader_target);
        ACaptureRequest_addTarget(capture_request, image_reader_target);
    }

    // capture session
    {
        ACameraCaptureSession_stateCallbacks camera_capture_session_state_callbacks;
        camera_capture_session_state_callbacks.context = this;
        camera_capture_session_state_callbacks.onActive = onSessionActive;
        camera_capture_session_state_callbacks.onReady = onSessionReady;
        camera_capture_session_state_callbacks.onClosed = onSessionClosed;

        ACaptureSessionOutputContainer_create(&capture_session_output_container);

        ACaptureSessionOutput_create(image_reader_surface, &capture_session_output);

        ACaptureSessionOutputContainer_add(capture_session_output_container, capture_session_output);

        if (ACameraDevice_createCaptureSession(camera_device, capture_session_output_container, &camera_capture_session_state_callbacks, &capture_session) != ACAMERA_OK || !capture_session)
        {
            close();
            return -1;
        }

        ACameraCaptureSession_captureCallbacks camera_capture_session_capture_callbacks;
        camera_capture_session_capture_callbacks.context = this;
        camera_capture_session_capture_callbacks.onCaptureStarted = 0;
        camera_capture_session_capture_callbacks.onCaptureProgressed = 0;
        camera_capture_session_capture_callbacks.onCaptureCompleted = onCaptureCompleted;
        camera_capture_session_capture_callbacks.onCaptureFailed = onCaptureFailed;
        camera_capture_session_capture_callbacks.onCaptureSequenceCompleted = onCaptureSequenceCompleted;
        camera_capture_session_capture_callbacks.onCaptureSequenceAborted = onCaptureSequenceAborted;
        camera_capture_session_capture_callbacks.onCaptureBufferLost = 0;

        if (ACameraCaptureSession_setRepeatingRequest(capture_session, &camera_capture_session_capture_callbacks, 1, &capture_request, nullptr) != ACAMERA_OK)
        {
            close();
            return -1;
        }
    }

    return 0;
}

void NdkCamera::close()
{
    __android_log_print(ANDROID_LOG_WARN, "NdkCamera", "close");

    if (capture_session)
    {
        ACameraCaptureSession_stopRepeating(capture_session);
        ACameraCaptureSession_close(capture_session);
        capture_session = 0;
    }

    if (camera_device)
    {
        ACameraDevice_close(camera_device);
        camera_device = 0;
    }

    if (capture_session_output_container)
    {
        ACaptureSessionOutputContainer_free(capture_session_output_container);
        capture_session_output_container = 0;
    }

    if (capture_session_output)
    {
        ACaptureSessionOutput_free(capture_session_output);
        capture_session_output = 0;
    }

    if (capture_request)
    {
        ACaptureRequest_free(capture_request);
        capture_request = 0;
    }

    if (image_reader_target)
    {
        ACameraOutputTarget_free(image_reader_target);
        image_reader_target = 0;
    }

    if (camera_manager)
    {
        ACameraManager_delete(camera_manager);
        camera_manager = 0;
    }
}

void NdkCamera::on_image(const cv::Mat& rgb) const
{
}

void NdkCamera::on_image(const unsigned char* nv21, int nv21_width, int nv21_height) const
{
    // rotate nv21
    int w = 0;
    int h = 0;
    int rotate_type = 0;
    {
        if (camera_orientation == 0)
        {
            w = nv21_width;
            h = nv21_height;
            rotate_type = camera_facing == 0 ? 2 : 1;
        }
        if (camera_orientation == 90)
        {
            w = nv21_height;
            h = nv21_width;
            rotate_type = camera_facing == 0 ? 5 : 6;
        }
        if (camera_orientation == 180)
        {
            w = nv21_width;
            h = nv21_height;
            rotate_type = camera_facing == 0 ? 4 : 3;
        }
        if (camera_orientation == 270)
        {
            w = nv21_height;
            h = nv21_width;
            rotate_type = camera_facing == 0 ? 7 : 8;
        }
    }

    cv::Mat nv21_rotated(h + h / 2, w, CV_8UC1);
    ncnn::kanna_rotate_yuv420sp(nv21, nv21_width, nv21_height, nv21_rotated.data, w, h, rotate_type);

    // nv21_rotated to rgb
    cv::Mat rgb(h, w, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21_rotated.data, w, h, rgb.data);

    on_image(rgb);
}

// Follow the actual display rotation, rather than independently rotating labels
// with gravity while Android keeps its UI locked in another orientation.
NdkCameraWindow::NdkCameraWindow() : NdkCamera(), win(0) { }
NdkCameraWindow::~NdkCameraWindow() {
    close();
    std::lock_guard<std::mutex> guard(window_mutex);
    if (win) ANativeWindow_release(win);
}
void NdkCameraWindow::configure_view(int rotation, int width, int height) {
    display_rotation.store(rotation);
    viewport_width.store(width);
    viewport_height.store(height);
}
void NdkCameraWindow::set_window(ANativeWindow* next) {
    std::lock_guard<std::mutex> guard(window_mutex);
    if (next) ANativeWindow_acquire(next);
    if (win) ANativeWindow_release(win);
    win = next;
}
void NdkCameraWindow::on_image_render(cv::Mat& rgb) const { }
void NdkCameraWindow::on_image(const unsigned char* nv21, int width, int height) const {
    std::lock_guard<std::mutex> guard(window_mutex);
    if (!win || width <= 0 || height <= 0) return;
    cv::Mat raw(height, width, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21, width, height, raw.data);
    int rotation = camera_facing == 1
        ? (camera_orientation - display_rotation.load() + 360) % 360
        : (camera_orientation + display_rotation.load()) % 360;
    cv::Mat upright;
    if (rotation == 90) cv::rotate(raw, upright, cv::ROTATE_90_CLOCKWISE);
    else if (rotation == 180) cv::rotate(raw, upright, cv::ROTATE_180);
    else if (rotation == 270) cv::rotate(raw, upright, cv::ROTATE_90_COUNTERCLOCKWISE);
    else upright = raw;
    if (camera_facing == 0) cv::flip(upright, upright, 1);
    int view_w = std::max(1, viewport_width.load());
    int view_h = std::max(1, viewport_height.load());
    int crop_w = upright.cols, crop_h = upright.rows;
    if ((double)crop_w / crop_h > (double)view_w / view_h)
        crop_w = std::max(2, (int)((double)crop_h * view_w / view_h));
    else crop_h = std::max(2, (int)((double)crop_w * view_h / view_w));
    crop_w = std::min(crop_w, upright.cols);
    crop_h = std::min(crop_h, upright.rows);
    cv::Mat rgb = upright(cv::Rect((upright.cols-crop_w)/2, (upright.rows-crop_h)/2, crop_w, crop_h)).clone();
    on_image_render(rgb);
    if (ANativeWindow_setBuffersGeometry(win, rgb.cols, rgb.rows, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM) != 0) return;
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(win, &buffer, NULL) != 0) return;
    if (buffer.width >= rgb.cols && buffer.height >= rgb.rows &&
        (buffer.format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM || buffer.format == AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM)) {
        for (int y=0; y<rgb.rows; ++y) {
            const unsigned char* src=rgb.ptr<unsigned char>(y);
            unsigned char* dst=(unsigned char*)buffer.bits+y*buffer.stride*4;
            for (int x=0; x<rgb.cols; ++x) {
                dst[0]=src[0]; dst[1]=src[1]; dst[2]=src[2]; dst[3]=255;
                src+=3; dst+=4;
            }
        }
    }
    ANativeWindow_unlockAndPost(win);
}
