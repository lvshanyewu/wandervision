package com.tencent.yolov8ncnn;

import android.content.res.AssetManager;
import org.json.*;

/** Plate text is read on demand from the exact captured frame, not from an earlier preview. */
final class PlateOcr {
    static String summary(JSONObject record){
        JSONObject ocr=record.optJSONObject("plate_recognition");if(ocr==null)return "";JSONArray readings=ocr.optJSONArray("readings");StringBuilder out=new StringBuilder("车牌OCR · 实验 / 拍照同帧");
        if(readings==null||readings.length()==0)return out+"\n没有可读车牌";
        for(int i=0;i<readings.length();i++){JSONObject row=readings.optJSONObject(i);if(row==null)continue;out.append("\n").append(row.optBoolean("accepted")?"候选 ":"待复核 ").append(row.optString("text").isEmpty()?"未读出号码":row.optString("text")).append(String.format(java.util.Locale.US,"  %.0f%%",row.optDouble("confidence")*100));}
        return out.toString();
    }
    static JSONObject read(YOLOv8Ncnn detector,AssetManager assets,FrameCapture frame) throws JSONException {
        JSONObject result=new JSONObject();result.put("model_id","hyperlpr3-rec-ncnn-20230229");result.put("engine","ncnn_cpu");result.put("experimental",true);result.put("result_alignment","same_native_frame");result.put("dictionary_classes",77);result.put("output_classes",78);
        JSONArray readings=new JSONArray();result.put("readings",readings);
        if(frame.detections.length==0){result.put("status","no_plate");return result;}
        int width=frame.image.getWidth(),height=frame.image.getHeight();int[] pixels=new int[width*height];frame.image.getPixels(pixels,0,width,0,0,width,height);
        String[] values=detector.readPlateText(assets,pixels,width,height,frame.detections);
        if(values==null){result.put("status","unavailable");return result;}
        for(int offset=0;offset+2<values.length;offset+=3){
            int index=Integer.parseInt(values[offset]);String text=values[offset+1];double confidence=Double.parseDouble(values[offset+2]);
            if(index<0||index*6+5>=frame.detections.length)continue;
            JSONObject reading=new JSONObject();reading.put("detection_index",index);reading.put("text",text);reading.put("confidence",confidence);
            reading.put("format_plausible",text.matches("[\\p{IsHan}][A-Z][A-Z0-9\\p{IsHan}]{5,6}"));reading.put("accepted",confidence>=.8&&reading.getBoolean("format_plausible"));
            JSONArray box=new JSONArray();for(int k=2;k<6;k++)box.put(frame.detections[index*6+k]);reading.put("bbox_xywh_normalized",box);readings.put(reading);
        }
        result.put("status",readings.length()>0?"completed":"no_readable_crop");return result;
    }
}
