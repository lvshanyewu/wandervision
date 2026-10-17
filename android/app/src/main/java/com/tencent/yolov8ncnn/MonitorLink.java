package com.tencent.yolov8ncnn;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Foreground heartbeat scheduling and explicitly requested uploads, separate from inference. */
final class MonitorLink {
    interface HeartbeatSource {JSONObject snapshot() throws Exception;}
    interface Completion {void done(String error);}
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final Set<String> UPLOADING=ConcurrentHashMap.newKeySet();
    private final Handler main=new Handler(Looper.getMainLooper());
    final MonitorStore store;
    private final MonitorApi api;
    private final Context context;
    private HeartbeatSource source;
    private Runnable changed;
    private volatile boolean foreground;
    private boolean beating,pairing;
    private String note="";
    private long lastHeartbeat;
    MonitorLink(Context context,HeartbeatSource source,Runnable changed){this(context,source,changed,new MonitorApi(),new MonitorStore(context.getApplicationContext()));}
    MonitorLink(Context context,HeartbeatSource source,Runnable changed,MonitorApi api,MonitorStore store){this.context=context.getApplicationContext();this.store=store;this.api=api;this.source=source;this.changed=changed;}
    String describe(){MonitorApi.Credentials c=store.credentials();return c==null?(note.isEmpty()?"尚未配对 · 在网页设置生成6位配对码":note):c.device+(store.enabled()?" · 前台连接":" · 已暂停连接")+"\n"+(lastHeartbeat==0?"等待前台心跳":("心跳成功 "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(lastHeartbeat))))+(note.isEmpty()?"":"\n"+note);}
    private void changed(){if(changed!=null)changed.run();}
    void foreground(boolean value){foreground=value;main.removeCallbacks(beat);if(value)main.post(beat);}
    void destroy(){foreground(false);source=null;changed=null;}
    void enabled(boolean value){store.enabled(value);note="";foreground(foreground);changed();}
    void disconnect(){store.disconnect();note="已断开；本地照片、JSON和上次回执仍保留";main.removeCallbacks(beat);changed();}
    private final Runnable beat=new Runnable(){@Override public void run(){
        if(!foreground||source==null)return;
        main.postDelayed(this,10_000);
        if(!store.enabled()||store.credentials()==null){changed();return;}
        if(beating)return;
        final MonitorApi.Credentials credentials=store.credentials();
        final JSONObject data;try{data=source.snapshot();}catch(Exception e){note="心跳数据暂不可用";changed();return;}
        final long sampledAt=SystemClock.elapsedRealtime();
        beating=true;
        NETWORK.execute(()->{
            String error=null;boolean sent=false;
            try{if(foreground&&store.enabled()&&store.current(credentials)&&SystemClock.elapsedRealtime()-sampledAt<10_000){
                long fixElapsed=data.optLong("_fix_elapsed_ns",0);data.remove("_fix_elapsed_ns");
                if(fixElapsed==0||SystemClock.elapsedRealtimeNanos()-fixElapsed>30_000_000_000L){data.put("lat",JSONObject.NULL);data.put("lon",JSONObject.NULL);data.remove("speed");}
                api.heartbeat(credentials,data);sent=true;}}
            catch(Exception e){error=MonitorApi.message(e);if(e instanceof MonitorApi.Failure&&(((MonitorApi.Failure)e).status==401||((MonitorApi.Failure)e).status==403))store.invalidate(credentials);}
            final String result=error;final boolean success=sent;
            main.post(()->{beating=false;if(success&&store.current(credentials)){lastHeartbeat=System.currentTimeMillis();note="";}else if(result!=null)note=result;changed();});
        });
    }};
    void pair(String code,String name,Completion callback){
        if(pairing){callback.done("正在配对，请稍候");return;}pairing=true;long epoch=store.beginPair();
        NETWORK.execute(()->{String error=null;try{MonitorApi.Credentials credentials=api.pair(code,name,epoch);if(!store.save(credentials))throw new MonitorApi.Failure(0,"配对操作已取消或本机设置未能保存，请重新配对");}catch(Exception e){error=MonitorApi.message(e);}final String result=error;
            main.post(()->{pairing=false;note=result==null?"":result;if(result==null){lastHeartbeat=0;foreground(foreground);}changed();callback.done(result);});});
    }
    boolean uploading(String id){return UPLOADING.contains(id);}
    String receiptText(String id){JSONObject receipt=store.receipt(id);String text;
        if(receipt.has("server_event_id"))text="上次服务器确认 · "+receipt.optString("device_id")+"\n"+receipt.optString("server_event_id")+"\n接收时间 "+receipt.optString("received_at");
        else text="本地记录 · 尚无成功上传回执";
        if(UPLOADING.contains(id))text+="\n正在上传，请稍候…";
        else if(!receipt.isNull("last_error")&&!receipt.optString("last_error").isEmpty())text+="\n"+receipt.optString("last_error");
        return text;
    }
    void upload(String id,Completion callback){
        MonitorApi.Credentials credentials=store.credentials();if(credentials==null||!store.enabled()){callback.done("请先连接并启用监控中心");return;}
        JSONObject previous=store.receipt(id);if(previous.has("server_event_id")&&!credentials.device.equals(previous.optString("device_id"))){callback.done("此记录已有其他设备的回执，请连接原设备后重试");return;}
        if(!UPLOADING.add(id)){callback.done("该记录正在上传，请稍候");return;}
        NETWORK.execute(()->{
            String error=null;JSONObject receipt=store.receipt(id);int attempts=receipt.optInt("attempts",0)+1;
            try{
                if(!store.current(credentials)||!store.enabled())throw new MonitorApi.Failure(0,"连接已更改，本次未开始上传");
                JSONObject report=store.report(id);Uri image=Uri.parse(report.getString("uri"));
                if(!"content".equals(image.getScheme())||!"media".equals(image.getAuthority()))throw new MonitorApi.Failure(0,"原始相册照片不可读取");
                byte[] jpeg;try(InputStream in=context.getContentResolver().openInputStream(image)){jpeg=MonitorApi.read(in,MonitorApi.MAX_IMAGE);}
                JSONObject response=api.upload(credentials,report,jpeg);response.put("attempts",attempts);response.put("last_error",JSONObject.NULL);
                try{store.receipt(id,response);}catch(IOException e){throw new MonitorApi.Failure(0,"服务器已接收，但本机回执未保存；请用同一记录重试确认");}
            }catch(Exception e){error=e instanceof FileNotFoundException?"原始照片或JSON已移除，无法上传":MonitorApi.message(e);
                if(e instanceof MonitorApi.Failure&&(((MonitorApi.Failure)e).status==401||((MonitorApi.Failure)e).status==403))store.invalidate(credentials);
                try{receipt.put("state",receipt.has("server_event_id")?"uploaded":"failed");receipt.put("attempts",attempts);receipt.put("last_error",error);receipt.put("last_attempt_epoch_ms",System.currentTimeMillis());store.receipt(id,receipt);}catch(Exception ignored){}
            }finally{UPLOADING.remove(id);}
            final String result=error;main.post(()->{changed();callback.done(result);});
        });
    }
}
