package com.tencent.yolov8ncnn;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Native bottom sheets for pairing and deliberate, per-photo uploads. */
final class MonitorUi {
    private final Activity activity;
    private final CameraUi ui;
    private final MonitorLink link;
    private TextView connectionStatus;
    MonitorUi(Activity activity,CameraUi ui,MonitorLink link){this.activity=activity;this.ui=ui;this.link=link;}
    private boolean alive(){return !activity.isDestroyed()&&!activity.isFinishing();}
    private void toast(String message){if(alive())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private TextView row(LinearLayout box,String label,int color,Runnable action){
        TextView view=ui.text(label,14,color);view.setGravity(Gravity.CENTER_VERTICAL);view.setMinHeight(ui.dp(54));view.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));view.setBackground(ui.shape(CameraUi.PANEL,9,CameraUi.LINE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=ui.dp(8);box.addView(view,p);view.setFocusable(true);view.setOnClickListener(v->{CameraUi.haptic(v);action.run();});return view;
    }
    private EditText input(LinearLayout box,String hint,String value,int limit,int type){
        EditText field=new EditText(activity);field.setTextColor(CameraUi.WHITE);field.setHintTextColor(CameraUi.MUTED);field.setTextSize(16);field.setHint(hint);field.setText(value);field.setSingleLine(true);field.setInputType(type);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});field.setSaveEnabled(false);field.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));field.setBackground(ui.shape(CameraUi.PANEL,9,CameraUi.LINE));field.setContentDescription(hint);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=ui.dp(10);box.addView(field,p);return field;
    }
    void refreshStatus(){if(connectionStatus!=null&&connectionStatus.isAttachedToWindow())connectionStatus.setText(link.describe());}
    void dashboard(){
        MonitorApi.Credentials credentials=link.store.credentials();
        String url=MonitorApi.BASE+"/mobile/"+(credentials==null?"":"?device="+Uri.encode(credentials.device));
        Dialog d=ui.sheet("巡检概览 · 设备地图","公开只读监控数据 · 地图、设备状态与当前LIVE；相机继续在前台工作。");
        LinearLayout box=ui.sheetBody(d);
        android.webkit.WebView web=new android.webkit.WebView(activity);
        web.setBackgroundColor(CameraUi.INK);
        android.webkit.WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setGeolocationEnabled(false);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view,android.webkit.WebResourceRequest request){
                return !trusted(request.getUrl());
            }
            @Override public void onPageFinished(android.webkit.WebView view,String ignored){if(!trusted(Uri.parse(view.getUrl())))view.stopLoading();}
        });
        box.addView(web,new LinearLayout.LayoutParams(-1,Math.max(ui.dp(240),(int)(activity.getResources().getDisplayMetrics().heightPixels*.65))));
        row(box,"刷新概览",CameraUi.YELLOW,web::reload);
        d.setOnDismissListener(ignored->{box.removeView(web);web.stopLoading();web.destroy();});
        web.loadUrl(url);d.show();
    }
    static boolean trusted(Uri uri){return uri!=null&&"https".equals(uri.getScheme())&&"lvshanyewu.site".equals(uri.getHost())&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getUserInfo()==null&&uri.getPath()!=null&&uri.getPath().startsWith("/wandervision/");}
    void connection(){
        Dialog d=ui.sheet("监控中心连接","公开课程 Demo · 照片和位置可公开展示，网页可随时清库。\n"+MonitorApi.BASE+"/\n网页设置生成6位一次性配对码，5分钟有效。连接后仅前台约10秒上报位置/状态；照片需逐张点击上传。");LinearLayout box=ui.sheetBody(d);
        connectionStatus=ui.text(link.describe(),12,CameraUi.YELLOW);connectionStatus.setTextIsSelectable(true);box.addView(connectionStatus);
        row(box,"打开网页仪表盘 / 当前设备地图",CameraUi.WHITE,()->{d.dismiss();dashboard();});
        EditText code=input(box,"6位配对码","",6,InputType.TYPE_CLASS_NUMBER);
        EditText name=input(box,"设备名称（可选）",Build.MODEL,64,InputType.TYPE_CLASS_TEXT);
        final TextView[] pairButton=new TextView[1];
        pairButton[0]=row(box,"连接并启用前台心跳",CameraUi.YELLOW,()->{
            String value=code.getText().toString();if(!value.matches("[0-9]{6}")){code.setError("请输入6位数字配对码");return;}
            code.setText("");pairButton[0].setEnabled(false);pairButton[0].setText("正在配对…");
            link.pair(value,name.getText().toString(),error->{if(!alive())return;pairButton[0].setEnabled(true);pairButton[0].setText("连接并启用前台心跳");if(error==null){toast("配对成功，前台连接已启用");d.dismiss();connection();}else toast(error);});
        });
        if(link.store.credentials()!=null){
            boolean enabled=link.store.enabled();row(box,enabled?"暂停前台心跳与上传":"恢复前台连接",CameraUi.WHITE,()->{link.enabled(!enabled);d.dismiss();connection();});
            row(box,"解除本机连接",CameraUi.MUTED,()->{link.disconnect();d.dismiss();connection();});
        }
        TextView note=ui.text("离开App停止定时心跳；手动开始的上传可完成。解除连接只清除本机令牌，原图、JSON及已有服务器数据保留。COCO通用分类不会当作道路病害。",11,CameraUi.MUTED);note.setPadding(0,ui.dp(12),0,0);box.addView(note);d.show();
    }
    void uploadControls(LinearLayout box,JSONObject record,Dialog owner){
        String id=record.optString("event_id");
        TextView status=ui.text(link.receiptText(id),12,CameraUi.YELLOW);status.setTextIsSelectable(true);status.setPadding(0,ui.dp(12),0,ui.dp(6));box.addView(status);
        TextView disclosure=ui.text("上传原始照片、识别结果与位置到公开课程 Demo，可公开展示并随时被网页清空。原图与原JSON不改写；旧照片“未上传”是生成时状态，以回执为准。",11,CameraUi.MUTED);box.addView(disclosure);
        final TextView[] button=new TextView[1];
        button[0]=row(box,"一键上传 / 重试确认",CameraUi.YELLOW,()->{
            if(link.store.credentials()==null||!link.store.enabled()){owner.dismiss();connection();return;}
            if(link.uploading(id)){toast("该记录正在上传，请稍候");return;}
            button[0].setEnabled(false);button[0].setText("正在上传…");status.setText("正在核验原图并上传…");
            link.upload(id,error->{if(!alive())return;status.setText(link.receiptText(id));button[0].setEnabled(true);button[0].setText("一键上传 / 重试确认");toast(error==null?"监控中心已确认；原图与JSON保留":error);});
        });
    }
}
