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
    private static final int REQUEST_CAMERA=100;
    private static final ExecutorService ENGINE=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final YOLOv8Ncnn detector=new YOLOv8Ncnn();
    private final float[] fpsHistory=new float[48];
    private CameraUi ui;
    private SharedPreferences preferences;
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
        facing=preferences.getInt("facing",1); mode=preferences.getInt("mode",0); capacity=preferences.getInt("capacity",0); resolution=preferences.getInt("resolution",0); backend=preferences.getInt("backend",0);
        if(facing<0||facing>1)facing=1;if(mode<0||mode>2)mode=0;if(capacity<0||capacity>1)capacity=0;if(resolution<0||resolution>2)resolution=0;if(backend<0||backend>1)backend=0;
        try{captures=new JSONArray(preferences.getString("captures","[]"));}catch(JSONException ignored){captures=new JSONArray();}
        ui=new CameraUi(this,this); setContentView(ui.root); ui.root.requestApplyInsets();
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
        else openCameraIfReady();main.removeCallbacks(poll);main.post(poll);
    }
    @Override public void onPause(){resumed=false;main.removeCallbacks(poll);closeCamera();super.onPause();}
    @Override public void onDestroy(){destroyed=true;generation++;main.removeCallbacksAndMessages(null);ui.dismissSheets();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQUEST_CAMERA&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCameraIfReady();
        else if(code==REQUEST_CAMERA)ui.busy("需要相机权限\n可从右上角设置前往系统授权");
    }
    @Override public void surfaceCreated(SurfaceHolder holder){}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        closeCamera();detector.configureView(getWindowManager().getDefaultDisplay().getRotation()*90,width,height);detector.setOutputWindow(holder.getSurface());surfaceReady=true;openCameraIfReady();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;closeCamera();}
    @Override public void mode(int value){if(mode!=value){mode=value;reload();}}
    @Override public void model(){choose("检测模型","两个模型均可识别 COCO 80 类；车辆模式只显示车辆类别。",new String[]{"Nano · 轻快基线  /  6.3 MB","Small · 更大模型  /  22.3 MB"},capacity,v->{capacity=v;reload();});}
    @Override public void size(){choose("推理输入尺寸","更大尺寸有助于保留细节，也会增加推理开销。请在真机比较。",new String[]{"320 px · 流畅优先","480 px · 平衡","640 px · 细节优先"},resolution,v->{resolution=v;reload();});}
    @Override public void engine(){choose("计算引擎","GPU 速度不保证高于 CPU；设备不支持 Vulkan 时会回退到 CPU。",new String[]{"CPU · 本地计算","Vulkan GPU · 本地计算"},backend,v->{backend=v;reload();});}
    @Override public void flip(){closeCamera();facing=1-facing;persist();ui.lens(facing);ui.busy("正在切换镜头…");openCameraIfReady();}
    private interface Selection{void set(int value);}
    private void choose(String title,String subtitle,String[] options,int selected,Selection callback){
        Dialog d=ui.sheet(title,subtitle);LinearLayout box=ui.sheetBody(d);
        for(int i=0;i<options.length;i++){final int index=i;sheetRow(box,(i==selected?"●  ":"○  ")+options[i],i==selected?CameraUi.MINT:CameraUi.WHITE,()->{d.dismiss();callback.set(index);});}d.show();
    }
    private TextView sheetRow(LinearLayout box,String label,int color,Runnable click){
        TextView row=ui.text(label,14,color);row.setMinHeight(ui.dp(54));row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));
        row.setBackground(ui.shape(0xff18242b,10,0xff34464c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=ui.dp(8);box.addView(row,p);
        row.setOnClickListener(v->{CameraUi.haptic(v);click.run();});row.setFocusable(true);return row;
    }
    @Override public void settings(){
        Dialog d=ui.sheet("仪器设置","所有识别在设备上完成。当前无服务器连接，也不采集位置。");LinearLayout box=ui.sheetBody(d);
        sheetRow(box,(ui.overlay.grid?"●  ":"○  ")+"三分构图网格",CameraUi.WHITE,()->{ui.overlay.grid=!ui.overlay.grid;ui.overlay.invalidate();preferences.edit().putBoolean("grid",ui.overlay.grid).apply();d.dismiss();});
        sheetRow(box,"实时检测仪表盘",CameraUi.WHITE,()->{d.dismiss();dashboard();});
        sheetRow(box,"传输与同步  ·  未连接",CameraUi.MUTED,()->{d.dismiss();Dialog info=ui.sheet("本地优先","截图已接入系统相册，可在截图记录中使用系统分享。云端事件同步、GPS 定位与远程仪表盘留作后续扩展，本版不会上传数据。");info.show();});
        sheetRow(box,"相机权限与应用信息",CameraUi.WHITE,()->{d.dismiss();startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));});
        sheetRow(box,"开源来源与许可",CameraUi.MUTED,()->{d.dismiss();showLicenses();});d.show();
    }
    private void showLicenses(){Dialog d=ui.sheet("开源许可","wandervision 0.2.0 · AGPL-3.0\nNCNN Android YOLOv8 / Tencent NCNN / OpenCV Mobile");
        LinearLayout box=ui.sheetBody(d);String[] paths={"THIRD_PARTY_NOTICES.md","LICENSE","ncnn-BSD-3-Clause.txt","opencv-LICENSE.txt","opencv-mobile-LICENSE.txt","Apache-2.0.txt"};
        for(String path:paths)sheetRow(box,path,CameraUi.WHITE,()->{d.dismiss();ENGINE.execute(()->{try(InputStream in=getAssets().open("licenses/"+path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);String text=out.toString("UTF-8");main.post(()->{if(destroyed)return;Dialog license=ui.sheet(path,"随 APK 提供的原始许可文本");TextView t=ui.text(text,11,CameraUi.MUTED);t.setTextIsSelectable(true);ui.sheetBody(license).addView(t);license.show();});}catch(IOException e){main.post(()->toast("无法读取许可文本"));}});});d.show();
    }
    @Override public void dashboard(){
        boolean fresh=cameraOpen&&stats[4]>frameAtOpen;String active=fresh?(mode==2?"纯取景":"本地检测"):"等待画面";
        Dialog d=ui.sheet("实时仪表盘",active+"  /  "+(capacity==0?"YOLOv8 Nano":"YOLOv8 Small")+"\n以下为打开面板时的真实快照；目标数是当前帧数量，不是去重车辆总数。");LinearLayout box=ui.sheetBody(d);
        TextView values=ui.text(String.format(Locale.US,"%s FPS    %s ms\n\n当前帧目标  %s\n最高置信度  %s\n已保存截图  %d\n实际引擎    %s",fresh?String.format(Locale.US,"%.1f",stats[0]):"—",fresh&&mode!=2?String.format(Locale.US,"%.1f",stats[1]):"—",fresh&&mode!=2?Integer.toString((int)stats[2]):"—",fresh&&mode!=2&&stats[2]>0?String.format(Locale.US,"%.0f%%",stats[3]*100):"—",captures.length(),stats[5]==1?"Vulkan GPU":"CPU"),16,CameraUi.MINT);
        values.setTypeface(Typeface.MONOSPACE);values.setPadding(ui.dp(8),ui.dp(12),ui.dp(8),ui.dp(16));box.addView(values);
        TextView chartlabel=ui.text("近 24 秒显示帧率  /  每 0.5 秒采样",10,CameraUi.MUTED);box.addView(chartlabel);
        box.addView(new FpsChart(this,fpsHistory.clone()),new LinearLayout.LayoutParams(-1,ui.dp(88)));d.show();
    }
    @Override public void shutter(){
        if(capturing)return;
        if(!cameraOpen||stats[4]<=frameAtOpen){toast("请等待相机首帧后截图");return;}
        if(Build.VERSION.SDK_INT<26){toast("带界面的截图需要 Android 8.0 或更新版本");return;}
        capturing=true;Bitmap image=Bitmap.createBitmap(getWindow().getDecorView().getWidth(),getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
        final int c=capacity,r=resolution,m=mode;final float[] snapshot=stats.clone();
        try{PixelCopy.request(getWindow(),image,result->{
            if(result!=PixelCopy.SUCCESS){capturing=false;image.recycle();toast("截图失败，请等画面稳定后重试");return;}
            ENGINE.execute(()->saveCapture(image,c,r,m,snapshot));
        },main);}catch(IllegalArgumentException e){capturing=false;image.recycle();toast("画面尚未准备好");}
    }
    private void saveCapture(Bitmap image,int c,int r,int m,float[] snapshot){
        Uri saved=null;
        try{
            if(Build.VERSION.SDK_INT<29)throw new IOException("截图保存到相册需要 Android 10 或更新版本");
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());String name="WV_"+stamp+".jpg";
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/wandervision");values.put(MediaStore.Images.Media.IS_PENDING,1);
            saved=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(saved==null)throw new IOException("相册不可用");
            try(OutputStream out=getContentResolver().openOutputStream(saved)){if(out==null||!image.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("图片写入失败");}
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(saved,values,null,null);
            JSONObject record=new JSONObject();record.put("uri",saved.toString());record.put("name",name);record.put("time",System.currentTimeMillis());record.put("model",c==0?"Nano":"Small");record.put("size",new int[]{320,480,640}[r]);record.put("mode",m==0?"通用":m==1?"车辆":"取景");record.put("fps",snapshot[0]);record.put("objects",m==2?0:(int)snapshot[2]);
            JSONArray existing=new JSONArray(preferences.getString("captures","[]"));JSONArray next=new JSONArray();next.put(record);for(int i=0;i<Math.min(existing.length(),29);i++)next.put(existing.optJSONObject(i));preferences.edit().putString("captures",next.toString()).apply();
            main.post(()->{if(!destroyed){captures=next;refreshProfile();toast("已保存到相册 · Pictures/wandervision");}capturing=false;});
        }catch(Exception e){if(saved!=null)getContentResolver().delete(saved,null,null);final String reason=e.getMessage();main.post(()->{capturing=false;if(!destroyed)toast("保存失败："+reason);});}
        finally{image.recycle();}
    }
    @Override public void history(){
        if(capturing){toast("截图正在保存，请稍候");return;}
        try{captures=new JSONArray(preferences.getString("captures","[]"));}catch(JSONException ignored){}
        refreshProfile();
        Dialog d=ui.sheet("本地截图",captures.length()==0?"尚无截图。点击中央快门保存当前画面、检测框和仪器信息。":"最近 30 张记录。原图保存在系统相册；可查看或通过系统分享。");LinearLayout box=ui.sheetBody(d);
        for(int i=0;i<captures.length();i++){JSONObject record=captures.optJSONObject(i);if(record==null)continue;String time=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(record.optLong("time")));
            sheetRow(box,time+"   "+record.optString("model")+" / "+record.optInt("size")+"\n"+record.optString("mode")+" · 当前帧 "+record.optInt("objects")+" 个目标",CameraUi.WHITE,()->{d.dismiss();showCapture(record);});}d.show();
    }
    private void showCapture(JSONObject record){
        Uri uri=Uri.parse(record.optString("uri"));Dialog d=ui.sheet("截图详情",record.optString("name")+"\n"+record.optString("model")+" / "+record.optInt("size")+" · "+record.optString("mode"));LinearLayout box=ui.sheetBody(d);
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setContentDescription("已保存的识别截图");box.addView(image,new LinearLayout.LayoutParams(-1,ui.dp(320)));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ENGINE.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);main.post(()->{if(!destroyed)image.setImageBitmap(bitmap);});}catch(Exception e){main.post(()->toast("图片可能已从相册移除"));}});
        sheetRow(box,"分享截图",CameraUi.MINT,()->{Intent share=new Intent(Intent.ACTION_SEND);share.setType("image/jpeg");share.putExtra(Intent.EXTRA_STREAM,uri);share.setClipData(ClipData.newUri(getContentResolver(),"wandervision capture",uri));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(share,"分享巡检截图"));}catch(ActivityNotFoundException e){toast("没有可用的分享应用");}});d.show();
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    static final class FpsChart extends View{
        final float[] data;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        FpsChart(Activity a,float[] values){super(a);data=values;setContentDescription("显示帧率趋势，实际采样数据");}
        @Override protected void onDraw(Canvas c){float max=1;for(float v:data)max=Math.max(max,v);p.setStrokeWidth(1);p.setColor(0xff2c4640);for(int i=1;i<4;i++)c.drawLine(0,getHeight()*i/4f,getWidth(),getHeight()*i/4f,p);
            Path path=new Path();for(int i=0;i<data.length;i++){float x=getWidth()*i/(float)(data.length-1),y=getHeight()-8-(getHeight()-16)*data[i]/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setStyle(Paint.Style.STROKE);p.setColor(CameraUi.MINT);p.setStrokeWidth(3);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
    }
}
