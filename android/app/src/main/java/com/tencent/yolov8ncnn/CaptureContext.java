package com.tencent.yolov8ncnn;

import org.json.*;

/** User-supplied collection context, frozen into new reports without rewriting past evidence. */
final class CaptureContext {
    static String clean(String value,int limit){
        String text=value==null?"":value.trim();
        return text.codePointCount(0,text.length())<=limit?text:text.substring(0,text.offsetByCodePoints(0,limit));
    }
    static JSONObject create(String site,String note) throws JSONException {
        return new JSONObject().put("site_name",clean(site,80)).put("note",clean(note,240));
    }
    static String summary(JSONObject report){
        JSONObject context=report.optJSONObject("capture_context");if(context==null)return "";
        String site=context.optString("site_name"),note=context.optString("note");
        return (site.isEmpty()?"":"采集点："+site)+(note.isEmpty()?"":(site.isEmpty()?"":"\n")+"备注："+note);
    }
}
