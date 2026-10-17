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
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements SurfaceHolder.Callback, CameraUi.Actions {
    private static final int REQUEST_CAMERA=100,REQUEST_LOCATION=101;
    private static final ExecutorService ENGINE=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final YOLOv8Ncnn detector=new YOLOv8Ncnn();
    private final float[] fpsHistory=new float[48];
    private CameraUi ui;
    private SharedPreferences preferences;
    private LocationTracker location;
    private String exportId;
    private String installationId;
    private int facing=1,mode=0,capacity=0,resolution=0,backend=0,generation;
    private boolean resumed,surfaceReady,modelReady,cameraOpen,capturing;
    private volatile boolean destroyed;
    private float[] stats=new float[7];
    private float frameAtOpen;
    private JSONArray captures=new JSONArray();
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(!resumed||destroyed)return;
        ENGINE.execute(()->{final float[] next=detector.getStats();main.post(()->{
            if(!resumed||destroyed)return;
            stats=next;
            boolean fresh=cameraOpen&&stats[4]>frameAtOpen;
            ui.stats(stats,fresh,mode==2);
            ui.location(location.label());
            if(fresh){ui.busy("");System.arraycopy(fpsHistory,1,fpsHistory,0,fpsHistory.length-1);fpsHistory[fpsHistory.length-1]=stats[0];}
            main.postDelayed(this,500);
        });});
    }};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        preferences=getSharedPreferences("instrument",MODE_PRIVATE);
        installationId=preferences.getString("installation_id",null);if(installationId==null){installationId=UUID.randomUUID().toString();preferences.edit().putString("installation_id",installationId).apply();}
        exportId=state==null?null:state.getString("export_id");
        facing=preferences.getInt("facing",1); mode=preferences.getInt("mode",0); capacity=preferences.getInt("capacity",0); resolution=preferences.getInt("resolution",0); backend=preferences.getInt("backend",0);
        if(facing<0||facing>1)facing=1;if(mode<0||mode>2)mode=0;if(capacity<0||capacity>1)capacity=0;if(resolution<0||resolution>2)resolution=0;if(backend<0||backend>1)backend=0;
        try{captures=new JSONArray(preferences.getString("captures","[]"));}catch(JSONException ignored){captures=new JSONArray();}
        ui=new CameraUi(this,this); setContentView(ui.root); ui.root.requestApplyInsets();
        location=new LocationTracker(this,()->{if(!destroyed)ui.location(location.label());});
        ui.surface.getHolder().setFormat(PixelFormat.RGBA_8888);ui.surface.getHolder().addCallback(this);
        ui.overlay.grid=preferences.getBoolean("grid",false); refreshProfile();reload();
    }
    private void refreshProfile(){ui.profile(mode,capacity,resolution,backend,captures.length());ui.lens(facing);}
    private void persist(){preferences.edit().putInt("facing",facing).putInt("mode",mode).putInt("capacity",capacity).putInt("resolution",resolution).putInt("backend",backend).apply();}
    private void reload(){
        closeCamera();modelReady=false;ui.busy("正在载入 "+(capacity==0?"YOLOv8 Nano":"YOLOv8 Small")+"…");
        persist();refreshProfile();final int ticket=++generation,m=mode,c=capacity,r=resolution,b=backend;
        ENGINE.execute(()->{
            if(destroyed)return;
            detector.setDetectionEnabled(m!=2);
            boolean ok=detector.loadModel(getAssets(),m==1?1:0,r,b,c);
            main.post(()->{
                if(destroyed||ticket!=generation)return;
                modelReady=ok;
                if(!ok){ui.busy("模型加载失败\n请选择 Nano / CPU 重试");toast("模型加载失败");}
                else{ui.busy("等待相机画面…");openCameraIfReady();}
            });
        });
    }
    private void openCameraIfReady(){
        if(!cameraOpen&&resumed&&surfaceReady&&modelReady&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
            detector.configureView(getWindowManager().getDefaultDisplay().getRotation()*90,ui.surface.getWidth(),ui.surface.getHeight());
            frameAtOpen=detector.getStats()[4]; cameraOpen=detector.openCamera(facing);
            if(!cameraOpen)ui.busy("摄像头未能打开\n请检查权限，或切换镜头重试");
        }
    }
    private void closeCamera(){if(cameraOpen)detector.closeCamera();cameraOpen=false;}
    @Override public void onResume(){super.onResume();resumed=true;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);
        else {openCameraIfReady();startLocation();}main.removeCallbacks(poll);main.post(poll);
    }
    @Override public void onPause(){resumed=false;main.removeCallbacks(poll);location.stop();closeCamera();super.onPause();}
    @Override public void onDestroy(){destroyed=true;generation++;main.removeCallbacksAndMessages(null);ui.dismissSheets();super.onDestroy();}
    @Override public void onSaveInstanceState(Bundle state){state.putString("export_id",exportId);super.onSaveInstanceState(state);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=102)return;String id=exportId;exportId=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||id==null||!id.matches("[a-f0-9\\-]{36}"))return;
        Uri target=data.getData();ENGINE.execute(()->{try(InputStream in=new FileInputStream(new File(getFilesDir(),"reports/"+id+".json"));OutputStream out=getContentResolver().openOutputStream(target,"wt")){
            if(out==null)throw new IOException("目标文件不可写");byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);main.post(()->{if(!destroyed)toast("JSON已导出");});
        }catch(IOException e){main.post(()->{if(!destroyed)toast("JSON导出失败");});}});
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQUEST_CAMERA&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){openCameraIfReady();startLocation();}
        else if(code==REQUEST_CAMERA)ui.busy("需要相机权限\n可从右上角设置前往系统授权");
        if(code==REQUEST_LOCATION&&resumed)location.start(preferences.getBoolean("location_enabled",true));
    }
    private void startLocation(){
        boolean enabled=preferences.getBoolean("location_enabled",true);location.start(enabled);
        if(enabled&&!location.permitted()&&!preferences.getBoolean("location_asked",false))requestLocation();
    }
    private void requestLocation(){preferences.edit().putBoolean("location_asked",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_LOCATION);}
    @Override public void locationSettings(){
        Dialog d=ui.sheet("前台定位",location.label()+"\n请求间隔约10秒，实际更新取决于信号与系统；超过30秒标为过期。坐标为WGS84，仅随手动取景保存在本机。");LinearLayout box=ui.sheetBody(d);
        boolean enabled=preferences.getBoolean("location_enabled",true);
        sheetRow(box,(enabled?"●  ":"○  ")+"启用前台定位",CameraUi.YELLOW,()->{preferences.edit().putBoolean("location_enabled",!enabled).apply();d.dismiss();if(resumed)startLocation();});
        sheetRow(box,"授予 / 重试定位权限",CameraUi.WHITE,()->{d.dismiss();preferences.edit().putBoolean("location_enabled",true).apply();requestLocation();});
        sheetRow(box,"系统定位设置",CameraUi.WHITE,()->{d.dismiss();try{startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(ActivityNotFoundException e){toast("无法打开系统定位设置");}});
        sheetRow(box,"应用权限设置",CameraUi.WHITE,()->{d.dismiss();startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));});d.show();
    }
    @Override public void surfaceCreated(SurfaceHolder holder){}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        closeCamera();detector.configureView(getWindowManager().getDefaultDisplay().getRotation()*90,width,height);detector.setOutputWindow(holder.getSurface());surfaceReady=true;openCameraIfReady();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;closeCamera();}
    @Override public void mode(int value){if(captureBusy())return;if(mode!=value){mode=value;reload();}}
    @Override public void model(){choose("检测模型","两个模型均可识别 COCO 80 类；车辆模式只显示车辆类别。",new String[]{"Nano · 轻快基线  /  6.3 MB","Small · 更大模型  /  22.3 MB"},capacity,v->{capacity=v;reload();});}
    @Override public void size(){choose("推理输入尺寸","更大尺寸有助于保留细节，也会增加推理开销。请在真机比较。",new String[]{"320 px · 流畅优先","480 px · 平衡","640 px · 细节优先"},resolution,v->{resolution=v;reload();});}
    @Override public void engine(){choose("计算引擎","GPU 速度不保证高于 CPU；设备不支持 Vulkan 时会回退到 CPU。",new String[]{"CPU · 本地计算","Vulkan GPU · 本地计算"},backend,v->{backend=v;reload();});}
    @Override public void flip(){if(captureBusy())return;closeCamera();facing=1-facing;persist();ui.lens(facing);ui.busy("正在切换镜头…");openCameraIfReady();}
    private interface Selection{void set(int value);}
    private void choose(String title,String subtitle,String[] options,int selected,Selection callback){
        if(captureBusy())return;
        Dialog d=ui.sheet(title,subtitle);LinearLayout box=ui.sheetBody(d);
        for(int i=0;i<options.length;i++){final int index=i;sheetRow(box,(i==selected?"●  ":"○  ")+options[i],i==selected?CameraUi.MINT:CameraUi.WHITE,()->{d.dismiss();callback.set(index);});}d.show();
    }
    private TextView sheetRow(LinearLayout box,String label,int color,Runnable click){
        TextView row=ui.text(label,14,color);row.setMinHeight(ui.dp(54));row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));
        row.setBackground(ui.shape(CameraUi.PANEL,9,CameraUi.LINE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=ui.dp(8);box.addView(row,p);
        row.setOnClickListener(v->{CameraUi.haptic(v);click.run();});row.setFocusable(true);return row;
    }
    @Override public void settings(){
        Dialog d=ui.sheet("仪器设置","识别与记录在本机完成；定位仅前台更新，本版不上传数据。");LinearLayout box=ui.sheetBody(d);
        sheetRow(box,(ui.overlay.grid?"●  ":"○  ")+"三分构图网格",CameraUi.WHITE,()->{ui.overlay.grid=!ui.overlay.grid;ui.overlay.invalidate();preferences.edit().putBoolean("grid",ui.overlay.grid).apply();d.dismiss();});
        sheetRow(box,"实时检测仪表盘",CameraUi.WHITE,()->{d.dismiss();dashboard();});
        sheetRow(box,"定位与位置权限",CameraUi.WHITE,()->{d.dismiss();locationSettings();});
        sheetRow(box,"传输与同步  ·  本地记录",CameraUi.MUTED,()->{d.dismiss();Dialog info=ui.sheet("本地优先","每次取景保存带识别框和定位信息的照片，以及持久化JSON。可在记录详情分享照片+JSON；尚未配置监控中心，不自动上传。最近列表显示30张，JSON保留在应用私有目录，卸载或清除应用数据会移除JSON。");info.show();});
        sheetRow(box,"相机权限与应用信息",CameraUi.WHITE,()->{d.dismiss();startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));});
        sheetRow(box,"开源来源与许可",CameraUi.MUTED,()->{d.dismiss();showLicenses();});d.show();
    }
    private void showLicenses(){Dialog d=ui.sheet("开源许可","wandervision 3.1.0 · AGPL-3.0\nNCNN Android YOLOv8 / Tencent NCNN / OpenCV Mobile");
        LinearLayout box=ui.sheetBody(d);String[] paths={"THIRD_PARTY_NOTICES.md","LICENSE","ncnn-BSD-3-Clause.txt","opencv-LICENSE.txt","opencv-mobile-LICENSE.txt","Apache-2.0.txt"};
        for(String path:paths)sheetRow(box,path,CameraUi.WHITE,()->{d.dismiss();ENGINE.execute(()->{try(InputStream in=getAssets().open("licenses/"+path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);String text=out.toString("UTF-8");main.post(()->{if(destroyed)return;Dialog license=ui.sheet(path,"随 APK 提供的原始许可文本");TextView t=ui.text(text,11,CameraUi.MUTED);t.setTextIsSelectable(true);ui.sheetBody(license).addView(t);license.show();});}catch(IOException e){main.post(()->toast("无法读取许可文本"));}});});d.show();
    }
    @Override public void dashboard(){
        boolean fresh=cameraOpen&&stats[4]>frameAtOpen;String active=fresh?(mode==2?"纯取景":"本地检测"):"等待画面";
        Dialog d=ui.sheet("实时仪表盘",active+"  /  "+(capacity==0?"YOLOv8 Nano":"YOLOv8 Small")+"\n以下为打开面板时的真实快照；目标数是当前帧数量，不是去重车辆总数。");LinearLayout box=ui.sheetBody(d);
        ui.dashboardTiles(box,stats,fresh,mode==2,captures.length(),capacity==0?"YOLOv8 Nano":"YOLOv8 Small",stats[5]==1?"Vulkan GPU":"CPU");
        TextView chartlabel=ui.text("近期显示帧率趋势",10,CameraUi.MUTED);box.addView(chartlabel);
        box.addView(new FpsChart(this,fpsHistory.clone()),new LinearLayout.LayoutParams(-1,ui.dp(88)));d.show();
    }
    private boolean captureBusy(){if(capturing){toast("取景照片正在生成，请稍候");return true;}return false;}
    @Override public void shutter(){
        if(captureBusy())return;
        if(!cameraOpen||stats[4]<=frameAtOpen){toast("请等待相机首帧后取景");return;}
        if(Build.VERSION.SDK_INT<29){toast("巡检照片保存需要 Android 10 或更新版本");return;}
        capturing=true;final int c=capacity,r=resolution,m=mode,f=facing,b=backend;
        final LocationTracker.Fix fix=location.freeze();
        ENGINE.execute(()->{
            FrameCapture frame=detector.captureFrame();
            if(frame==null){main.post(()->{capturing=false;if(!destroyed)toast("取景超时，请等待画面稳定后重试");});return;}
            long capturedAt=System.currentTimeMillis()-(SystemClock.elapsedRealtimeNanos()-frame.elapsedNanos)/1_000_000L;
            saveCapture(frame,c,r,m,f,b,fix,capturedAt);
        });
    }
    private void saveCapture(FrameCapture frame,int c,int r,int m,int facing,int backend,LocationTracker.Fix fix,long capturedAt){
        Uri saved=null;Bitmap report=null;File jsonFile=null;
        try{
            JSONObject record=InspectionReport.create(frame,c,new int[]{320,480,640}[r],m,facing,backend,fix,capturedAt,installationId);
            report=InspectionReport.render(frame,record,fix);
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date(capturedAt));String name="WV_"+stamp+"_"+record.getString("event_id").substring(0,8)+".jpg";
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/wandervision");values.put(MediaStore.Images.Media.IS_PENDING,1);
            saved=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(saved==null)throw new IOException("相册不可用");
            try(OutputStream out=getContentResolver().openOutputStream(saved)){if(out==null||!report.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("图片写入失败");}
            boolean exifWritten=false;
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(saved,"rw")){
                if(fd!=null){InspectionReport.exif(new android.media.ExifInterface(fd.getFileDescriptor()),record);exifWritten=true;}
            }catch(IOException ignored){} // Visible location and JSON remain usable if EXIF is unavailable.
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long bytes=0;
            try(InputStream in=getContentResolver().openInputStream(saved)){if(in==null)throw new IOException("图片回读失败");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){digest.update(buffer,0,n);bytes+=n;}}
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.US,"%02x",value&255));
            record.put("uri",saved.toString());record.put("name",name);
            JSONObject asset=new JSONObject();asset.put("file_name",name);asset.put("mime_type","image/jpeg");asset.put("width_px",report.getWidth());asset.put("height_px",report.getHeight());asset.put("bytes",bytes);asset.put("sha256",hash.toString());asset.put("exif_written",exifWritten);asset.put("contains_ui_controls",false);record.put("image",asset);
            asset.put("annotations_baked",m!=2);asset.put("viewfinder_rect_px",new JSONArray(new int[]{0,Math.round(76*frame.image.getWidth()/960f),frame.image.getWidth(),frame.image.getHeight()}));
            File folder=new File(getFilesDir(),"reports");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("记录目录不可用");
            jsonFile=new File(folder,record.getString("event_id")+".json");
            android.util.AtomicFile atomic=new android.util.AtomicFile(jsonFile);FileOutputStream jsonOut=atomic.startWrite();
            try{jsonOut.write(record.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(jsonOut);}catch(Exception e){atomic.failWrite(jsonOut);throw e;}
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(saved,values,null,null);
            JSONArray existing=new JSONArray(preferences.getString("captures","[]"));JSONArray next=new JSONArray();next.put(record);for(int i=0;i<Math.min(existing.length(),29);i++)next.put(existing.optJSONObject(i));
            if(!preferences.edit().putString("captures",next.toString()).commit())throw new IOException("记录索引写入失败");
            main.post(()->{if(!destroyed){captures=next;refreshProfile();toast("巡检照片与JSON已保存 · 未上传");}capturing=false;});
        }catch(Exception e){if(saved!=null)getContentResolver().delete(saved,null,null);if(jsonFile!=null)jsonFile.delete();final String reason=e.getMessage();main.post(()->{capturing=false;if(!destroyed)toast("保存失败："+reason);});}
        finally{frame.image.recycle();if(report!=null)report.recycle();}
    }
    @Override public void history(){
        if(capturing){toast("截图正在保存，请稍候");return;}
        try{captures=new JSONArray(preferences.getString("captures","[]"));}catch(JSONException ignored){}
        refreshProfile();
        Dialog d=ui.sheet("取景记录",captures.length()==0?"尚无记录。点击黄色快门生成带定位与识别信息的巡检照片。":"本地相册 · 最近 "+captures.length()+" 张记录\n查看原图或通过系统分享；本版未连接云端。");LinearLayout box=ui.sheetBody(d);
        for(int i=0;i<captures.length();i++){JSONObject record=captures.optJSONObject(i);if(record==null)continue;String time=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(record.optLong("time")));
            ImageView thumbnail=ui.recordCard(box,record.optString("mode")+" · 本地截图",time+"  /  "+record.optString("model")+" "+record.optInt("size"),record.optString("mode").equals("取景")?"纯取景 · 未执行检测":"当前帧 "+record.optInt("objects")+" 个目标",()->{d.dismiss();showCapture(record);});
            ENGINE.execute(()->{try(InputStream in=getContentResolver().openInputStream(Uri.parse(record.optString("uri")))){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);main.post(()->{if(!destroyed)thumbnail.setImageBitmap(bitmap);});}catch(Exception ignored){}});
        }d.show();
    }
    private void showCapture(JSONObject record){
        Uri uri=Uri.parse(record.optString("uri"));Dialog d=ui.sheet("巡检照片详情",record.optString("name")+"\n"+record.optString("model")+" / "+record.optInt("size")+" · "+record.optString("mode"));LinearLayout box=ui.sheetBody(d);
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setContentDescription("已保存的识别截图");box.addView(image,new LinearLayout.LayoutParams(-1,ui.dp(320)));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ENGINE.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);main.post(()->{if(!destroyed)image.setImageBitmap(bitmap);});}catch(Exception e){main.post(()->toast("图片可能已从相册移除"));}});
        if(record.has("event_id")){
            JSONObject loc=record.optJSONObject("location");
            String position=loc==null?"旧版记录无定位":loc.isNull("latitude")?"定位："+loc.optString("status"):String.format(Locale.US,"WGS84 %.6f, %.6f\n精度 ±%.0fm · 采样 %.0f 秒前 · %s",loc.optDouble("latitude"),loc.optDouble("longitude"),loc.optDouble("accuracy_m"),loc.optLong("age_ms")/1000f,loc.optString("status"));
            TextView details=ui.text("记录 "+record.optString("event_id")+"\n"+position+"\n状态：仅本地保存，未上传",11,CameraUi.MUTED);details.setTextIsSelectable(true);details.setPadding(0,ui.dp(12),0,ui.dp(8));box.addView(details);
            sheetRow(box,"分享照片 + JSON 记录",CameraUi.YELLOW,()->shareReport(record,uri));
            sheetRow(box,"导出 JSON 文件",CameraUi.WHITE,()->{exportId=record.optString("event_id");Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.setType("application/json");save.addCategory(Intent.CATEGORY_OPENABLE);save.putExtra(Intent.EXTRA_TITLE,exportId+".json");try{startActivityForResult(save,102);}catch(ActivityNotFoundException e){exportId=null;toast("没有可用的文件保存应用");}});
        }
        sheetRow(box,"分享截图",CameraUi.MINT,()->{Intent share=new Intent(Intent.ACTION_SEND);share.setType("image/jpeg");share.putExtra(Intent.EXTRA_STREAM,uri);share.setClipData(ClipData.newUri(getContentResolver(),"wandervision capture",uri));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(share,"分享巡检截图"));}catch(ActivityNotFoundException e){toast("没有可用的分享应用");}});d.show();
    }
    private void shareReport(JSONObject record,Uri image){
        Uri json=Uri.parse("content://"+getPackageName()+".reports/reports/"+record.optString("event_id")+".json");
        Intent share=new Intent(Intent.ACTION_SEND_MULTIPLE);share.setType("*/*");ArrayList<Uri> files=new ArrayList<>();files.add(image);files.add(json);share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,files);
        ClipData clip=new ClipData("巡检记录",new String[]{"image/jpeg","application/json"},new ClipData.Item(image));clip.addItem(new ClipData.Item(json));share.setClipData(clip);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(share,"导出巡检照片与结构化记录"));}catch(ActivityNotFoundException e){toast("没有可用的分享应用");}
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    static final class FpsChart extends View{
        final float[] data;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        FpsChart(Activity a,float[] values){super(a);data=values;setContentDescription("显示帧率趋势，实际采样数据");}
        @Override protected void onDraw(Canvas c){float max=1;for(float v:data)max=Math.max(max,v);p.setStrokeWidth(1);p.setColor(CameraUi.LINE);for(int i=1;i<4;i++)c.drawLine(0,getHeight()*i/4f,getWidth(),getHeight()*i/4f,p);
            Path path=new Path();for(int i=0;i<data.length;i++){float x=getWidth()*i/(float)(data.length-1),y=getHeight()-8-(getHeight()-16)*data[i]/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setStyle(Paint.Style.STROKE);p.setColor(CameraUi.MINT);p.setStrokeWidth(3);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
    }
}
