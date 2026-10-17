package com.tencent.yolov8ncnn;

import android.net.Uri;
import android.util.Base64;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Fixed-origin HTTPS API. No redirects, credentials in URLs, or permissive TLS handlers. */
final class MonitorApi {
    static final String BASE="https://lvshanyewu.site/wandervision";
    static final int MAX_IMAGE=4*1024*1024,MAX_REQUEST=6*1024*1024;
    interface Transport { JSONObject post(String path,String token,JSONObject data) throws IOException; }
    static final class Failure extends IOException {
        final int status;
        Failure(int status,String message){super(message);this.status=status;}
    }
    static final class Credentials {
        final String device,token;
        final long epoch;
        Credentials(String device,String token,long epoch){this.device=device;this.token=token;this.epoch=epoch;}
    }
    private final Transport transport;
    MonitorApi(){this(new HttpsTransport());}
    MonitorApi(Transport transport){this.transport=transport;}
    static boolean validDevice(String value){return value!=null&&value.matches("PHONE-[0-9]{2}");}
    static String dashboard(String device){return BASE+"/"+(validDevice(device)?"?device="+Uri.encode(device):"");}
    Credentials pair(String code,String name,long epoch) throws IOException {
        if(code==null||!code.matches("[0-9]{6}"))throw new Failure(0,"请输入6位数字配对码");
        try{
            JSONObject request=new JSONObject();request.put("code",code);if(name!=null&&!name.trim().isEmpty())request.put("name",name.trim().substring(0,Math.min(64,name.trim().length())));
            JSONObject response=transport.post("/api/v1/pair",null,request);requireOk(response);
            String device=response.getString("device_id"),token=response.getString("token");
            if(!validDevice(device)||!token.matches("[!-~]{8,512}"))throw new JSONException("Invalid credentials");
            // The response's dashboard_url is deliberately not used as a navigation target.
            return new Credentials(device,token,epoch);
        }catch(JSONException e){throw new Failure(0,"服务器配对响应格式不正确");}
    }
    void heartbeat(Credentials credentials,JSONObject data) throws IOException {requireOk(transport.post(path(credentials,"heartbeat"),credentials.token,data));}
    JSONObject upload(Credentials credentials,JSONObject report,byte[] jpeg) throws IOException {
        try{
            if(!"1.0".equals(report.getString("schema_version"))||!report.getString("event_id").matches("[a-f0-9\\-]{36}"))throw new Failure(0,"记录格式不支持上传");
            if(jpeg.length==0||jpeg.length>MAX_IMAGE)throw new Failure(0,"原始照片须小于或等于4 MiB；请重新取景");
            if(!sha256(jpeg).equals(report.getJSONObject("image").getString("sha256")))throw new Failure(0,"照片已变更或损坏，校验失败；原记录仍保留");
            JSONObject request=new JSONObject();request.put("report",report);request.put("image_base64",Base64.encodeToString(jpeg,Base64.NO_WRAP));
            if(request.toString().getBytes(StandardCharsets.UTF_8).length>MAX_REQUEST)throw new Failure(0,"报告超过6 MiB请求限制");
            JSONObject response=transport.post(path(credentials,"reports"),credentials.token,request);requireOk(response);
            String id=response.getString("server_event_id"),at=response.getString("received_at");
            if(id.length()>160||id.isEmpty()||at.length()>80||at.isEmpty())throw new JSONException("Invalid receipt");
            JSONObject receipt=new JSONObject();receipt.put("state","uploaded");receipt.put("device_id",credentials.device);receipt.put("event_id",report.getString("event_id"));receipt.put("server_event_id",id);receipt.put("received_at",at);receipt.put("duplicate",response.getBoolean("duplicate"));receipt.put("confirmed_at_epoch_ms",System.currentTimeMillis());return receipt;
        }catch(JSONException e){throw new Failure(0,"报告或服务器回执格式不正确；可用同一记录重试");}
    }
    private static String path(Credentials c,String suffix) throws Failure {if(c==null||!validDevice(c.device))throw new Failure(401,"请先连接监控中心");return "/api/v1/devices/"+c.device+"/"+suffix;}
    private static void requireOk(JSONObject response) throws Failure {if(response==null||!response.optBoolean("ok",false))throw new Failure(0,"服务器未确认本次操作，请稍后重试");}
    static String sha256(byte[] bytes) throws IOException {try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.US,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IOException("SHA256 unavailable");}}
    static byte[] read(InputStream in,int limit) throws IOException {
        if(in==null)throw new Failure(0,"文件不可读取");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new Failure(0,"文件超过大小限制");out.write(buffer,0,n);}return out.toByteArray();
    }
    static String message(Exception error){return error instanceof Failure?error.getMessage():"网络连接失败，请检查网络后重试";}
    private static final class HttpsTransport implements Transport {
        @Override public JSONObject post(String path,String token,JSONObject data) throws IOException {
            if(!path.matches("/api/v1/(pair|devices/PHONE-[0-9]{2}/(heartbeat|reports))"))throw new Failure(0,"无效的监控中心接口");
            HttpsURLConnection connection=(HttpsURLConnection)new URL(BASE+path).openConnection();
            try{
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10_000);connection.setReadTimeout(30_000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("User-Agent","wandervision/3.2.0 (Android)");
                if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
                byte[] payload=data.toString().getBytes(StandardCharsets.UTF_8);if(payload.length>MAX_REQUEST)throw new Failure(0,"请求超过大小限制");connection.setFixedLengthStreamingMode(payload.length);
                try(OutputStream out=connection.getOutputStream()){out.write(payload);}
                int status=connection.getResponseCode();
                if(status<200||status>=300){
                    if(status==401||status==403)throw new Failure(status,"配对已失效或无权限，请重新配对");
                    if(status==400||status==404||status==409||status==410)throw new Failure(status,path.endsWith("/pair")?"配对码无效、已使用或已过期，请在网页重新生成":"服务器未接受报告，请检查记录或重新配对后重试");
                    if(status==413)throw new Failure(status,"照片或报告超过服务器限制");
                    if(status==429)throw new Failure(status,"请求过于频繁，请稍后重试");
                    throw new Failure(status,"监控中心暂不可用（HTTP "+status+"），请稍后重试");
                }
                try(InputStream in=connection.getInputStream()){return new JSONObject(new String(read(in,64*1024),StandardCharsets.UTF_8));}
                catch(JSONException e){throw new Failure(0,"监控中心响应格式不正确");}
            }finally{connection.disconnect();}
        }
    }
}
