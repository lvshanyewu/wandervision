package com.tencent.yolov8ncnn;

import android.graphics.*;
import android.media.ExifInterface;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Upload-neutral event contract and a clean report image, independent of on-screen controls. */
final class InspectionReport {
    static final String[] LABELS=("person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,traffic light,fire hydrant,stop sign,parking meter,bench,bird,cat,dog,horse,sheep,cow,elephant,bear,zebra,giraffe,backpack,umbrella,handbag,tie,suitcase,frisbee,skis,snowboard,sports ball,kite,baseball bat,baseball glove,skateboard,surfboard,tennis racket,bottle,wine glass,cup,fork,knife,spoon,bowl,banana,apple,sandwich,orange,broccoli,carrot,hot dog,pizza,donut,cake,chair,couch,potted plant,bed,dining table,toilet,tv,laptop,mouse,remote,keyboard,cell phone,microwave,oven,toaster,sink,refrigerator,book,clock,vase,scissors,teddy bear,hair drier,toothbrush").split(",");
    static String utc(long time){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(time));}
    static JSONObject create(FrameCapture frame,int capacity,int input,int mode,int facing,int requestedBackend,LocationTracker.Fix fix,long time,String installationId) throws Exception {
        JSONObject j=new JSONObject();String id=UUID.randomUUID().toString();
        j.put("schema_version","1.0");j.put("event_id",id);j.put("app_version","3.4.1");j.put("captured_at",utc(time));j.put("captured_at_epoch_ms",time);j.put("capture_elapsed_ns",frame.elapsedNanos);
        JSONObject source=new JSONObject();source.put("installation_id",installationId);source.put("manufacturer",android.os.Build.MANUFACTURER);source.put("device_model",android.os.Build.MODEL);source.put("android_api",android.os.Build.VERSION.SDK_INT);j.put("source",source);
        // Flat compatibility fields keep pre-3.1 history readable on upgrade.
        j.put("time",time);j.put("model",ModelCatalog.name(capacity));j.put("size",input);j.put("mode",ModelCatalog.modeName(capacity,mode));j.put("fps",frame.stats[0]);j.put("objects",frame.detections.length/6);
        JSONObject camera=new JSONObject();camera.put("facing",facing==1?"back":"front");camera.put("frame_id",frame.frameId);camera.put("width_px",frame.image.getWidth());camera.put("height_px",frame.image.getHeight());camera.put("orientation","display_upright");camera.put("source","native_viewfinder_frame");j.put("camera",camera);
        JSONObject inference=new JSONObject();inference.put("enabled",mode!=2);inference.put("vehicle_filter",mode==1&&ModelCatalog.vehicles(capacity));inference.put("model_id",ModelCatalog.IDS[capacity]);inference.put("mode",ModelCatalog.modeId(capacity,mode));inference.put("input_size",input);inference.put("requested_backend",requestedBackend==1?"vulkan":"cpu");inference.put("actual_backend",frame.stats[5]==1?"vulkan":"cpu");inference.put("driver","system");inference.put("display_fps",frame.stats[0]);inference.put("inference_ms",mode==2?JSONObject.NULL:frame.stats[1]);inference.put("max_confidence",mode==2||frame.detections.length==0?JSONObject.NULL:frame.stats[3]);inference.put("result_alignment","same_native_frame");
        String[] labels=ModelCatalog.labels(capacity);
        JSONArray detections=new JSONArray();for(int i=0;i<frame.detections.length;i+=6){
            int label=(int)frame.detections[i];JSONObject o=new JSONObject();o.put("class_id",label);o.put("label",label>=0&&label<labels.length?labels[label]:"unknown");o.put("confidence",frame.detections[i+1]);
            JSONArray box=new JSONArray();for(int k=2;k<6;k++)box.put(Math.max(0,Math.min(1,frame.detections[i+k])));o.put("bbox_xywh_normalized",box);detections.put(o);
        }inference.put("detections",detections);j.put("inference",inference);j.put("location",fix.json(frame.elapsedNanos));j.put("experimental_analysis",new JSONObject(frame.analysis));inference.put("experimental_model",capacity>=4);
        JSONObject sync=new JSONObject();sync.put("state","local_only");sync.put("attempts",0);sync.put("server_event_id",JSONObject.NULL);j.put("sync",sync);
        return j;
    }
    static Bitmap render(FrameCapture frame,JSONObject record,LocationTracker.Fix fix) throws Exception {
        int w=frame.image.getWidth();float s=w/960f;String context=CaptureContext.summary(record);int header=Math.round(76*s),footer=Math.round((context.isEmpty()?340:420)*s);
        Bitmap report=Bitmap.createBitmap(w,header+frame.image.getHeight()+footer,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(report);c.drawColor(CameraUi.INK);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(CameraUi.YELLOW);c.drawRect(0,0,8*s,header,p);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(32*s);c.drawText("wandervision",28*s,48*s,p);
        p.setTextSize(19*s);p.setColor(CameraUi.MUTED);c.drawText("FIELD RECORD / 3.4.1",w-275*s,47*s,p);
        c.drawBitmap(frame.image,0,header,null);
        JSONObject ocr=record.optJSONObject("plate_recognition");JSONArray readings=ocr==null?null:ocr.optJSONArray("readings");
        if(readings!=null)for(int i=0;i<readings.length();i++){
            JSONObject reading=readings.getJSONObject(i);String value=reading.optString("text");if(value.isEmpty())continue;
            JSONArray box=reading.getJSONArray("bbox_xywh_normalized");float x=(float)box.getDouble(0)*w,y=header+(float)box.getDouble(1)*frame.image.getHeight();
            String text=(reading.optBoolean("accepted")?"":"疑似 ")+value+String.format(Locale.US," %.0f%%",reading.optDouble("confidence")*100);
            p.setTextSize(Math.max(14,26*s));float tw=Math.min(w-8,p.measureText(text)+12);x=Math.max(0,Math.min(w-tw,x));y=Math.max(header+p.getTextSize()+8,y);
            p.setColor(CameraUi.INK);c.drawRect(x,y-p.getTextSize()-8,x+tw,y+3,p);p.setColor(CameraUi.YELLOW);c.drawText(text,x+6,y-3,p);
        }
        float y=header+frame.image.getHeight();p.setColor(CameraUi.YELLOW);c.drawRect(24*s,y+20*s,w-24*s,y+23*s,p);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(24*s);
        String time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX",Locale.US).format(new Date(record.getLong("time")));
        JSONObject inf=record.getJSONObject("inference");
        String locDetail=fix.location==null?"定位未就绪 · 本次不填入坐标":String.format(Locale.US,"位置采样 %.0f 秒前 / %s%s",fix.age(frame.elapsedNanos)/1000f,fix.location.getProvider(),fix.location.isFromMockProvider()?" / 模拟位置":"");
        String[] lines={time,"WGS84  "+fix.label(frame.elapsedNanos),locDetail,record.getString("mode")+"  /  YOLOv8 "+record.getString("model")+"  /  "+record.getInt("size")+" px  /  "+inf.getString("actual_backend").toUpperCase(Locale.US),
            inf.getBoolean("enabled")?String.format(Locale.US,"当前帧 %d 个目标  ·  %.1f FPS  ·  %.1f ms",record.getInt("objects"),frame.stats[0],frame.stats[1]):"纯取景 · 未执行识别",
            "记录 "+record.getString("event_id").substring(0,8)+"  /  FRAME "+frame.frameId,"本地原图 · 上传状态以记录回执为准"};
        for(int i=0;i<lines.length;i++){p.setColor(i==1?CameraUi.YELLOW:i==2||i==6?CameraUi.MUTED:CameraUi.WHITE);c.drawText(lines[i],28*s,y+(64+i*40)*s,p);}
        if(!context.isEmpty()){
            JSONObject metadata=record.getJSONObject("capture_context");String[] extra={"采集点："+metadata.optString("site_name"),"备注："+metadata.optString("note")};
            p.setColor(CameraUi.WHITE);for(int i=0;i<extra.length;i++){String value=extra[i].replace('\n',' ').replace('\r',' ');int end=p.breakText(value,true,w-56*s,null);if(end<value.length()){end=p.breakText(value,true,w-86*s,null);value=value.substring(0,end)+"…";}c.drawText(value,28*s,y+(344+i*40)*s,p);}
        }
        return report;
    }
    static void exif(ExifInterface e,JSONObject record) throws Exception {
        e.setAttribute(ExifInterface.TAG_DATETIME,new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",Locale.US).format(new Date(record.getLong("time"))));
        e.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION,"wandervision 3.4.1 event="+record.getString("event_id"));
        JSONObject loc=record.getJSONObject("location");String status=loc.getString("status");
        if((status.equals("fresh")||status.equals("approximate"))&&!loc.isNull("latitude")) {
            double lat=loc.getDouble("latitude"),lon=loc.getDouble("longitude");
            e.setAttribute(ExifInterface.TAG_GPS_LATITUDE,dms(lat));e.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,lat<0?"S":"N");
            e.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,dms(lon));e.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,lon<0?"W":"E");
            e.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM,"WGS-84");
        }
        e.saveAttributes();
    }
    private static String dms(double x){x=Math.abs(x);int degrees=(int)x;double mins=(x-degrees)*60;int minutes=(int)mins;return degrees+"/1,"+minutes+"/1,"+Math.round((mins-minutes)*60*1000000)+"/1000000";}
}
