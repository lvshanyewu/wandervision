package com.tencent.yolov8ncnn;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Dependency-free contract runner; fakes never connect to the public service. */
public final class ContractInstrumentation extends Instrumentation {
    private int passed;
    private boolean quick;
    private static final String TOKEN="contract-test-placeholder-not-a-real-token";
    interface Check {void run() throws Exception;}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void test(Check check) throws Exception {check.run();passed++;}
    void rejected(Check operation) throws Exception {try{operation.run();throw new AssertionError("Expected rejection");}catch(MonitorApi.Failure expected){}}
    MonitorStore testStore(){return new MonitorStore(getTargetContext().getSharedPreferences("monitor-contract-tests",0),new File(getTargetContext().getCacheDir(),"monitor-contract-tests"));}
    static final class Fake implements MonitorApi.Transport {
        volatile int calls;String path,token;volatile JSONObject sent;
        final CountDownLatch called=new CountDownLatch(1);
        JSONObject result;IOException error;
        @Override public JSONObject post(String path,String token,JSONObject data) throws IOException {calls++;this.path=path;this.token=token;try{sent=new JSONObject(data.toString());}catch(JSONException e){throw new IOException();}called.countDown();if(error!=null)throw error;return result;}
    }
    JSONObject pairResponse() throws Exception {return new JSONObject().put("ok",true).put("device_id","PHONE-01").put("token",TOKEN).put("dashboard_url","https://example.invalid/untrusted");}
    JSONObject receipt(boolean duplicate) throws Exception {return new JSONObject().put("ok",true).put("server_event_id","report-contract-1").put("received_at","2026-10-17T00:00:00Z").put("duplicate",duplicate);}
    JSONObject report(byte[] image) throws Exception {return new JSONObject().put("schema_version","1.0").put("event_id","01234567-89ab-4cde-8abc-0123456789ab").put("image",new JSONObject().put("sha256",MonitorApi.sha256(image))).put("location",new JSONObject().put("status","permission_denied").put("latitude",JSONObject.NULL).put("longitude",JSONObject.NULL)).put("inference",new JSONObject().put("model_id","coco-yolov8n").put("detections",new JSONArray().put(new JSONObject().put("class_id",2).put("label","car").put("confidence",0.9)))).put("sync",new JSONObject().put("state","local_only"));}
    @Override public void onCreate(Bundle args){super.onCreate(args);quick=args!=null&&"true".equals(args.getString("quick"));start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Fake fake=new Fake();MonitorApi api=new MonitorApi(fake);fake.result=pairResponse();
        test(()->{MonitorApi.Credentials c=api.pair("123456"," test phone ",7);check(c.device.equals("PHONE-01")&&c.epoch==7,"pair fields");check(fake.path.equals("/api/v1/pair")&&fake.token==null,"pair route/auth");check(fake.sent.getString("name").equals("test phone"),"name trim");});
        test(()->{int count=fake.calls;rejected(()->api.pair("12345","",0));rejected(()->api.pair("１２３４５６","",0));check(fake.calls==count,"invalid codes must not send");});
        test(()->{fake.result=pairResponse().put("token",TOKEN+"\r\nHeader: invalid");rejected(()->api.pair("123456","",0));fake.result=pairResponse().put("device_id","PHONE-01/../../other");rejected(()->api.pair("123456","",0));fake.result=pairResponse();});
        test(()->{check(MonitorApi.dashboard("PHONE-01").equals(MonitorApi.BASE+"/?device=PHONE-01"),"fixed browser URL");check(MonitorApi.dashboard("https://example.invalid").equals(MonitorApi.BASE+"/"),"reject arbitrary navigation");});
        MonitorApi.Credentials credentials=new MonitorApi.Credentials("PHONE-01",TOKEN,0);
        test(()->{String emoji=new String(Character.toChars(0x1f4cd));check(CaptureContext.clean(emoji+emoji,1).equals(emoji),"no split surrogate");check(CaptureContext.create(" 校门 "," 夜间 ").getString("site_name").equals("校门"),"context trimmed");byte[] image={(byte)255,(byte)216,(byte)255,(byte)217};JSONObject extended=report(image).put("capture_context",CaptureContext.create("校门","夜间")).put("plate_recognition",new JSONObject().put("experimental",true).put("readings",new JSONArray())).put("experimental_analysis",new JSONObject().put("enabled",false));String originalText=extended.toString();fake.result=receipt(false);api.upload(credentials,extended,image);check(fake.sent.getJSONObject("report").toString().equals(originalText)&&extended.toString().equals(originalText),"all optional fields uploaded unchanged");check(CaptureContext.summary(new JSONObject()).isEmpty(),"old reports supported");});
        test(()->{fake.result=new JSONObject().put("ok",true);api.heartbeat(credentials,new JSONObject().put("lat",JSONObject.NULL).put("lon",JSONObject.NULL).put("status","online"));check(fake.path.endsWith("/PHONE-01/heartbeat")&&TOKEN.equals(fake.token),"heartbeat auth route");check(fake.sent.isNull("lat")&&fake.sent.isNull("lon"),"no invented coordinates");check(!fake.sent.toString().contains(TOKEN),"token is not payload");});
        byte[] jpeg={(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9};JSONObject original=report(jpeg);String unchanged=original.toString();
        test(()->{fake.result=receipt(false);JSONObject response=api.upload(credentials,original,jpeg);check(response.getString("server_event_id").equals("report-contract-1"),"receipt");check(fake.path.endsWith("/reports"),"reports endpoint");check(Arrays.equals(Base64.decode(fake.sent.getString("image_base64"),Base64.NO_WRAP),jpeg),"original bytes");check(!fake.sent.getString("image_base64").contains("\n"),"NO_WRAP");check(fake.sent.getJSONObject("report").toString().equals(unchanged)&&original.toString().equals(unchanged),"report not modified");check(fake.sent.getJSONObject("report").getJSONObject("inference").getJSONArray("detections").getJSONObject(0).getString("label").equals("car"),"COCO not converted to damage");});
        test(()->{String event=fake.sent.getJSONObject("report").getString("event_id");fake.result=receipt(true);JSONObject duplicate=api.upload(credentials,original,jpeg);check(duplicate.getBoolean("duplicate")&&duplicate.getString("server_event_id").equals("report-contract-1"),"duplicate receipt");check(event.equals(fake.sent.getJSONObject("report").getString("event_id")),"retry preserves idempotency UUID");});
        test(()->{int count=fake.calls;rejected(()->api.upload(credentials,original,new byte[]{1,2,3}));check(fake.calls==count,"hash failure no upload");});
        test(()->{int count=fake.calls;rejected(()->api.upload(credentials,original,new byte[MonitorApi.MAX_IMAGE+1]));check(fake.calls==count,"oversize image no upload");});
        test(()->{JSONObject huge=new JSONObject(original.toString());char[] chars=new char[MonitorApi.MAX_REQUEST];Arrays.fill(chars,'x');huge.put("oversize",new String(chars));int count=fake.calls;rejected(()->api.upload(credentials,huge,jpeg));check(fake.calls==count,"oversize request no upload");});
        test(()->{fake.error=new MonitorApi.Failure(401,"expired");try{api.upload(credentials,original,jpeg);throw new AssertionError("401 accepted");}catch(MonitorApi.Failure e){check(e.status==401,"401 retained for invalidation");}fake.error=null;});
        test(()->{fake.error=new SocketTimeoutException(TOKEN);try{api.upload(credentials,original,jpeg);throw new AssertionError("timeout accepted");}catch(IOException e){check(!MonitorApi.message(e).contains(TOKEN),"redact transport internals");}fake.error=null;});
        test(()->{fake.result=new JSONObject().put("ok",true);rejected(()->api.upload(credentials,original,jpeg));fake.result=receipt(false);});
        test(()->{rejected(()->MonitorApi.read(new ByteArrayInputStream(new byte[33]),32));check(MonitorApi.read(new ByteArrayInputStream(jpeg),4).length==4,"bounded reads");});
        test(()->{MonitorStore store=testStore();store.disconnect();long first=store.beginPair();MonitorApi.Credentials old=new MonitorApi.Credentials("PHONE-01",TOKEN,first);check(store.save(old),"save credentials");check(store.current(old)&&store.enabled(),"restore private session");long second=store.beginPair();MonitorApi.Credentials current=new MonitorApi.Credentials("PHONE-02",TOKEN+"2",second);check(store.save(current),"replace session");check(!store.save(old),"old pairing cannot overwrite");store.invalidate(old);check(store.current(current),"old 401 cannot clear new session");store.invalidate(current);check(store.credentials()==null,"active 401 disconnects");});
        test(()->{MonitorStore store=testStore();String id=original.getString("event_id");File dir=new File(getTargetContext().getCacheDir(),"monitor-contract-tests/reports");dir.mkdirs();File reportFile=new File(dir,id+".json");byte[] bytes=original.toString(2).getBytes(StandardCharsets.UTF_8);try(OutputStream out=new FileOutputStream(reportFile)){out.write(bytes);}store.receipt(id,receipt(false).put("state","uploaded"));check(store.receipt(id).has("server_event_id"),"persist receipt");try(InputStream in=new FileInputStream(reportFile)){check(Arrays.equals(bytes,MonitorApi.read(in,1024*1024)),"receipt does not change original report");}check(store.report(id).getString("event_id").equals(id),"restore original report");});
        test(()->{
            MonitorStore store=testStore();long epoch=store.beginPair();store.save(new MonitorApi.Credentials("PHONE-01",TOKEN,epoch));Fake transport=new Fake();transport.result=new JSONObject().put("ok",true);
            MonitorLink[] link=new MonitorLink[1];runOnMainSync(()->{link[0]=new MonitorLink(getTargetContext(),()->new JSONObject().put("lat",29.56).put("lon",106.55).put("speed",20).put("_fix_elapsed_ns",android.os.SystemClock.elapsedRealtimeNanos()-31_000_000_000L),()->{},new MonitorApi(transport),store);link[0].foreground(true);});
            check(transport.called.await(5,TimeUnit.SECONDS),"foreground heartbeat sends");check(transport.sent.isNull("lat")&&transport.sent.isNull("lon")&&!transport.sent.has("speed"),"stale GPS removed at send time");check(!transport.sent.has("_fix_elapsed_ns"),"internal sample marker not transmitted");runOnMainSync(()->link[0].foreground(false));Thread.sleep(10_800);check(transport.calls==1,"background stops periodic heartbeat");runOnMainSync(()->link[0].destroy());store.disconnect();
        });
        test(()->{
            MonitorStore store=testStore();long epoch=store.beginPair();store.save(new MonitorApi.Credentials("PHONE-01",TOKEN,epoch));store.enabled(false);Fake transport=new Fake();transport.result=new JSONObject().put("ok",true);MonitorLink[] link=new MonitorLink[1];runOnMainSync(()->{link[0]=new MonitorLink(getTargetContext(),()->new JSONObject().put("lat",JSONObject.NULL).put("lon",JSONObject.NULL),()->{},new MonitorApi(transport),store);link[0].foreground(true);});check(!transport.called.await(1,TimeUnit.SECONDS),"paused connection does not send");runOnMainSync(()->link[0].destroy());store.disconnect();
        });
        test(()->{check(MonitorUi.trusted(android.net.Uri.parse(MonitorApi.BASE+"/mobile/?device=PHONE-01")),"trusted mobile origin");for(String bad:new String[]{"http://lvshanyewu.site/wandervision/mobile/","https://lvshanyewu.site.evil.invalid/wandervision/","https://lvshanyewu.site/other/","file:///wandervision/mobile/","https://user@lvshanyewu.site/wandervision/"})check(!MonitorUi.trusted(android.net.Uri.parse(bad)),"blocked navigation");});
        test(()->{Fake f=new Fake();f.result=new JSONObject().put("ok",true);new MonitorApi(f).stopLive(new MonitorApi.Credentials("PHONE-01",TOKEN,1));check(f.path.equals("/api/v1/devices/PHONE-01/live/stop")&&f.token.equals(TOKEN),"stop route/auth");});
        test(()->{check(ModelCatalog.labels(0).length==80&&ModelCatalog.labels(3).length==4,"model class separation");check(ModelCatalog.labels(2)[3].equals("pothole")&&ModelCatalog.IDS[3].equals("rdd2022-yolov8s"),"road model identity");});
        test(()->{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);byte[] liveJpeg={(byte)255,(byte)216,4,5,(byte)255,(byte)217};MonitorApi.writeLiveFrame(out,liveJpeg);out.writeInt(0);DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));check(in.readInt()==6,"big-endian JPEG length");byte[] read=new byte[6];in.readFully(read);check(Arrays.equals(read,liveJpeg)&&in.readInt()==0&&in.read()==-1,"framing and terminator");rejected(()->MonitorApi.writeLiveFrame(out,new byte[262145]));rejected(()->MonitorApi.writeLiveFrame(out,new byte[0]));});
        if(quick){result.putString("stream","PASS: "+passed+" quick data/upload contract checks (no public uploads)\n");result.putInt("passed",passed);finish(-1,result);return;}
        YOLOv8Ncnn detector=new YOLOv8Ncnn();
        test(()->check(detector.smokeRules(),"rule dwell/wrong-way/normal/crossing/disable sequences"));
        test(()->{int[] pixels=new int[160*48];Arrays.fill(pixels,0xff808080);String[] values=detector.readPlateText(getTargetContext().getAssets(),pixels,160,48,new float[]{0,.95f,0,0,1,1});check(values!=null&&values.length==3,"OCR model CPU shape + CTC smoke");check(Double.isFinite(Double.parseDouble(values[2])),"finite OCR confidence");});
        test(()->{LocationTracker.Fix fixedLocation=new LocationTracker.Fix(false,false,false,"off",null);for(int model=0;model<ModelCatalog.COUNT;model++){
            int label=ModelCatalog.labels(model).length-1;FrameCapture frame=new FrameCapture(new int[64],8,8,new float[7],new float[]{label,.9f,.1f,.1f,.2f,.2f},42,android.os.SystemClock.elapsedRealtimeNanos(),"{\"enabled\":false}");
            try{JSONObject record=InspectionReport.create(frame,model,320,0,1,0,fixedLocation,System.currentTimeMillis(),"test");JSONObject inference=record.getJSONObject("inference");check(inference.getString("model_id").equals(ModelCatalog.IDS[model]),"stable saved model ID");check(inference.getJSONArray("detections").getJSONObject(0).getString("label").equals(ModelCatalog.labels(model)[label]),"model-local labels in record");}finally{frame.image.recycle();}
        }});
        for(int model=0;model<ModelCatalog.COUNT;model++)for(int input=0;input<3;input++){
            final int m=model,r=input;test(()->{check(detector.loadModel(getTargetContext().getAssets(),0,r,0,m),"model loads");check(detector.smokeModel(480,320),"rectangular native inference/DFL/draw");});
        }
        result.putString("stream","PASS: "+passed+" contract/model checks (synthetic inputs; no public uploads)\n");result.putInt("passed",passed);finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAIL after "+passed+" checks: "+error.getClass().getSimpleName()+(error instanceof AssertionError?" "+error.getMessage():"")+"\n");finish(0,result);}}
}
