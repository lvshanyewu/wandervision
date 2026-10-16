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

package com.tencent.yolov8ncnn;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int REQUEST_CAMERA = 100;
    private final YOLOv8Ncnn detector = new YOLOv8Ncnn();
    private int facing = 1;
    private int mode = 0;
    private int resolution = 0;
    private int backend = 0;
    private boolean resumed;
    private boolean surfaceReady;
    private boolean modelReady;
    private boolean cameraOpen;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SurfaceView camera = findViewById(R.id.cameraview);
        camera.getHolder().setFormat(PixelFormat.RGBA_8888);
        camera.getHolder().addCallback(this);
        findViewById(R.id.buttonSwitchCamera).setOnClickListener(v -> {
            closeCamera();
            facing = 1 - facing;
            openCameraIfReady();
        });
        bindSpinner(R.id.spinnerTask, 0);
        bindSpinner(R.id.spinnerModel, 1);
        bindSpinner(R.id.spinnerCPUGPU, 2);
        reload();
    }

    private void bindSpinner(int id, final int kind) {
        ((Spinner) findViewById(id)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long itemId) {
                int previous = kind == 0 ? mode : kind == 1 ? resolution : backend;
                if (previous == position) return;
                if (kind == 0) mode = position;
                else if (kind == 1) resolution = position;
                else backend = position;
                reload();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void reload() {
        modelReady = detector.loadModel(getAssets(), mode, resolution, backend);
        if (!modelReady) Toast.makeText(this, "模型加载失败，请切换 CPU 后重试", Toast.LENGTH_LONG).show();
        openCameraIfReady();
    }

    private void openCameraIfReady() {
        if (!cameraOpen && resumed && surfaceReady && modelReady && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraOpen = detector.openCamera(facing);
            if (!cameraOpen) Toast.makeText(this, "摄像头打开失败", Toast.LENGTH_LONG).show();
        }
    }

    private void closeCamera() {
        if (cameraOpen) detector.closeCamera();
        cameraOpen = false;
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else openCameraIfReady();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_CAMERA && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openCameraIfReady();
        else if (code == REQUEST_CAMERA) Toast.makeText(this, "需要相机权限才能进行本地检测，可在系统设置中开启", Toast.LENGTH_LONG).show();
    }

    @Override public void onPause() {
        resumed = false;
        closeCamera();
        super.onPause();
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        closeCamera();
        detector.setOutputWindow(holder.getSurface());
        surfaceReady = true;
        openCameraIfReady();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        closeCamera();
    }
}
