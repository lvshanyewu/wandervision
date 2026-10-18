package com.tencent.yolov8ncnn;

import android.graphics.Bitmap;
import android.os.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

/** Latest-frame long upload: bounded memory, TCP backpressure, no video archive/queue. */
final class LiveStream {
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final ExecutorService STATUS=Executors.newSingleThreadExecutor();
    private static final ExecutorService CANCEL=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final YOLOv8Ncnn camera;
    private final MonitorLink link;
    private final Runnable changed;
    private final MonitorApi api=new MonitorApi();
    private volatile boolean active;
    private volatile int generation;
    private volatile String streamId="";
    private volatile MonitorApi.LiveSession session;
    private volatile long writeMarker;
    private String note="LIVE · 已关闭";
    private boolean checking;
    private MonitorApi.Credentials credentials;
    LiveStream(YOLOv8Ncnn camera,MonitorLink link,Runnable changed){this.camera=camera;this.link=link;this.changed=changed;}
    boolean active(){return active;}
    String label(){return note;}
    void connectionChanged(){if(active&&(!link.store.enabled()||!link.store.current(credentials)))stop();}
    void start(){
        if(active)return;
        MonitorApi.Credentials c=link.store.credentials();if(c==null||!link.store.enabled())return;
        credentials=c;active=true;int ticket=++generation;
        note="LIVE · 连接中";camera.setLiveEnabled(true);changed.run();
        main.removeCallbacks(status);main.post(status);NETWORK.execute(()->run(c,ticket));
    }
    void stop(){
        if(!active)return;
        MonitorApi.Credentials c=credentials;credentials=null;
        active=false;++generation;main.removeCallbacks(status);camera.setLiveEnabled(false);
        note="LIVE · 已关闭";changed.run();
        MonitorApi.LiveSession pending=session;if(pending!=null)CANCEL.execute(pending::cancel);
        if(c!=null)NETWORK.execute(()->{try{api.stopLive(c);}catch(Exception ignored){}});
    }
    private final Runnable status=new Runnable(){@Override public void run(){
        if(!active)return;main.postDelayed(this,2000);if(checking||credentials==null)return;
        checking=true;int ticket=generation;String id=streamId;MonitorApi.Credentials c=credentials;
        STATUS.execute(()->{String label="LIVE · 等待接收";
            try{JSONObject response=api.liveState(c);
                if(id.equals(response.optString("stream_id"))&&"live".equals(response.optString("state")))
                    label=String.format(Locale.US,"● LIVE · %.1f fps",response.optDouble("fps",0));
            }catch(Exception ignored){label="LIVE · 网络重试";}
            final String value=label;main.post(()->{checking=false;if(active&&generation==ticket&&streamId.equals(id)){note=value;changed.run();}});
        });
    }};
    private boolean current(MonitorApi.Credentials c,int ticket){return active&&generation==ticket&&link.store.current(c)&&link.store.enabled();}
    private void run(MonitorApi.Credentials c,int ticket){
        try{
            while(current(c,ticket)){
                MonitorApi.LiveSession connection=null;
                try{
                    streamId=UUID.randomUUID().toString();connection=api.openLive(c,isTestDevice(),streamId);session=connection;
                    long begin=SystemClock.elapsedRealtime(),bytes=0;
                    while(current(c,ticket)&&SystemClock.elapsedRealtime()-begin<55_000&&bytes<14*1024*1024){
                        long frameAt=SystemClock.elapsedRealtime();int[] pixels=camera.takeLivePixels();
                        if(pixels!=null){
                            Bitmap bitmap=Bitmap.createBitmap(pixels,2,pixels[0],pixels[0],pixels[1],Bitmap.Config.ARGB_8888);
                            byte[] jpeg;
                            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                                bitmap.compress(Bitmap.CompressFormat.JPEG,55,out);jpeg=out.toByteArray();
                            }finally{bitmap.recycle();}
                            if(!current(c,ticket))break;
                            final MonitorApi.LiveSession writing=connection;final long marker=System.nanoTime();writeMarker=marker;
                            Runnable watchdog=()->CANCEL.execute(()->{if(writeMarker==marker)writing.cancel();});
                            main.postDelayed(watchdog,2500);
                            try{connection.send(jpeg);bytes+=jpeg.length+4;}finally{writeMarker=0;main.removeCallbacks(watchdog);}
                        }
                        Thread.sleep(Math.max(1,80-(SystemClock.elapsedRealtime()-frameAt)));
                    }
                    if(current(c,ticket))connection.finish();
                }catch(Exception error){
                    android.util.Log.w("WVLive","Stream failure: "+error.getClass().getSimpleName()+(error instanceof MonitorApi.Failure?" HTTP "+((MonitorApi.Failure)error).status:""));
                    if(error instanceof MonitorApi.Failure&&(((MonitorApi.Failure)error).status==401||((MonitorApi.Failure)error).status==403)){
                        link.store.invalidate(c);break;
                    }
                    main.post(()->{if(active&&generation==ticket){note="LIVE · 网络重试";changed.run();}});
                    if(current(c,ticket))Thread.sleep(1000);
                }finally{if(connection!=null)connection.cancel();session=null;writeMarker=0;}
            }
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        finally{main.post(()->{if(generation==ticket){active=false;main.removeCallbacks(status);camera.setLiveEnabled(false);note="LIVE · 连接已停止";changed.run();}});}
    }
    static boolean isTestDevice(){return Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish")||Build.FINGERPRINT.startsWith("generic")||Build.MODEL.contains("sdk_gphone");}
}
