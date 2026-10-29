package com.tencent.yolov8ncnn;

import android.graphics.Bitmap;

/** One annotated native frame and its matching inference results. Constructed by JNI. */
final class FrameCapture {
    final Bitmap image;
    final float[] stats, detections;
    final long frameId, elapsedNanos;
    final String analysis;
    FrameCapture(int[] pixels,int width,int height,float[] metrics,float[] objects,long frame,long elapsed) {
        this(pixels,width,height,metrics,objects,frame,elapsed,"{\"enabled\":false}");
    }
    FrameCapture(int[] pixels,int width,int height,float[] metrics,float[] objects,long frame,long elapsed,String analysis) {
        image=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
        stats=metrics; detections=objects; frameId=frame; elapsedNanos=elapsed;
        this.analysis=analysis;
    }
}
