package com.tencent.yolov8ncnn;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Credentials and upload receipts are private; original 3.1 reports are immutable. */
final class MonitorStore {
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    private final File root;
    MonitorStore(Context context){this(context.getSharedPreferences("monitor",Context.MODE_PRIVATE),context.getFilesDir());}
    MonitorStore(SharedPreferences prefs,File root){this.prefs=prefs;this.root=root;}
    MonitorApi.Credentials credentials(){synchronized(LOCK){String device=prefs.getString("device",null),token=prefs.getString("token",null);return MonitorApi.validDevice(device)&&token!=null?new MonitorApi.Credentials(device,token,prefs.getLong("epoch",0)):null;}}
    boolean enabled(){return prefs.getBoolean("enabled",false);}
    boolean current(MonitorApi.Credentials c){synchronized(LOCK){MonitorApi.Credentials now=credentials();return c!=null&&now!=null&&now.epoch==c.epoch&&now.token.equals(c.token)&&now.device.equals(c.device);}}
    long beginPair(){synchronized(LOCK){long epoch=prefs.getLong("epoch",0)+1;prefs.edit().putLong("epoch",epoch).commit();return epoch;}}
    boolean save(MonitorApi.Credentials c){synchronized(LOCK){return prefs.getLong("epoch",0)==c.epoch&&prefs.edit().putString("device",c.device).putString("token",c.token).putBoolean("enabled",true).commit();}}
    void disconnect(){synchronized(LOCK){prefs.edit().remove("device").remove("token").putBoolean("enabled",false).putLong("epoch",prefs.getLong("epoch",0)+1).commit();}}
    void invalidate(MonitorApi.Credentials c){synchronized(LOCK){if(current(c))disconnect();}}
    void enabled(boolean value){prefs.edit().putBoolean("enabled",value).apply();}
    private File file(String folder,String id) throws IOException {if(id==null||!id.matches("[a-f0-9\\-]{36}"))throw new IOException("Invalid event id");return new File(root,folder+"/"+id+".json");}
    JSONObject report(String id) throws Exception {try(InputStream in=new FileInputStream(file("reports",id))){JSONObject report=new JSONObject(new String(MonitorApi.read(in,MonitorApi.MAX_REQUEST),StandardCharsets.UTF_8));if(!id.equals(report.getString("event_id")))throw new IOException("Event mismatch");return report;}}
    JSONObject receipt(String id){try(InputStream in=new FileInputStream(file("uploads",id))){return new JSONObject(new String(MonitorApi.read(in,64*1024),StandardCharsets.UTF_8));}catch(Exception ignored){return new JSONObject();}}
    void receipt(String id,JSONObject value) throws IOException {
        synchronized(LOCK){File target=file("uploads",id);File folder=target.getParentFile();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Receipt directory unavailable");AtomicFile atomic=new AtomicFile(target);FileOutputStream out=atomic.startWrite();try{out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(IOException e){atomic.failWrite(out);throw e;}}
    }
}
