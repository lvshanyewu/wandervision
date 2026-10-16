package com.tencent.yolov8ncnn;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.ArrayList;

/** Native views with an instrument-like finish; all actionable controls have touch targets and labels. */
final class CameraUi {
    interface Actions { void mode(int value); void model(); void size(); void engine(); void shutter(); void history(); void flip(); void settings(); void dashboard(); }
    static final int INK=Color.rgb(9,14,18), PANEL=Color.rgb(23,31,38), LINE=Color.rgb(52,65,73);
    static final int WHITE=Color.rgb(233,241,240), MUTED=Color.rgb(144,160,166), MINT=Color.rgb(126,236,199), AMBER=Color.rgb(232,193,126);
    final Activity activity;
    final Actions actions;
    final LinearLayout root, tools;
    final SurfaceView surface;
    final FrameLayout viewport;
    final TextView status, lens, message, fps, latency, objects, model, size, engine, captureCount;
    final TextView[] modes=new TextView[3];
    final GridOverlay overlay;
    private final boolean landscape;
    private final ArrayList<Dialog> sheets=new ArrayList<>();

    CameraUi(Activity a, Actions callbacks) {
        activity=a; actions=callbacks;
        landscape=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        root=new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(INK);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header=row(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(18),0,dp(14),0);
        LinearLayout brand=column();
        TextView word=text("WANDERVISION",18,WHITE); word.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); word.setLetterSpacing(.12f);
        brand.addView(word); TextView edition=text("FIELD INSTRUMENT   /   02",9,MUTED); edition.setLetterSpacing(.16f); brand.addView(edition);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("●  LOCAL",10,MINT); local.setTypeface(Typeface.MONOSPACE); header.addView(local);
        header.addView(icon("settings","设置",actions::settings),new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        LinearLayout body=new LinearLayout(a); body.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        viewport=new FrameLayout(a); viewport.setBackground(shape(0xff0c1519,20,0xff354348)); viewport.setClipToOutline(true);
        surface=new SurfaceView(a); viewport.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        overlay=new GridOverlay(a); overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); viewport.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout badgebar=row(); badgebar.setGravity(Gravity.CENTER_VERTICAL); badgebar.setPadding(dp(14),dp(14),dp(14),0);
        status=text("●  INITIALIZING",10,MINT); status.setTypeface(Typeface.MONOSPACE); status.setPadding(dp(10),dp(7),dp(10),dp(7)); status.setBackground(shape(0xdf0c181b,8,0x556e9e92));
        badgebar.addView(status); Space gap=new Space(a); badgebar.addView(gap,new LinearLayout.LayoutParams(0,1,1));
        lens=text("REAR / 1×",10,WHITE); lens.setTypeface(Typeface.MONOSPACE); lens.setPadding(dp(9),dp(7),dp(9),dp(7)); lens.setBackground(shape(0xc911191f,8,0x44687980)); badgebar.addView(lens);
        viewport.addView(badgebar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        message=text("正在准备本地视觉引擎",13,MUTED); message.setGravity(Gravity.CENTER); message.setPadding(dp(20),dp(12),dp(20),dp(12));
        viewport.addView(message,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        LinearLayout lcd=row(); lcd.setPadding(dp(6),dp(9),dp(6),dp(9)); lcd.setBackground(shape(0xe80a1719,12,0xff45625a));
        fps=lcdCell(lcd,"FRAME RATE","—", "fps"); latency=lcdCell(lcd,"INFERENCE","—", "ms"); objects=lcdCell(lcd,"IN FRAME","—", "目标");
        FrameLayout.LayoutParams lcdp=new FrameLayout.LayoutParams(-1,dp(67),Gravity.BOTTOM); lcdp.setMargins(dp(14),0,dp(14),dp(14)); viewport.addView(lcd,lcdp);
        lcd.setOnClickListener(v->actions.dashboard()); lcd.setContentDescription("查看实时检测仪表盘"); lcd.setFocusable(true);
        LinearLayout.LayoutParams vp=landscape?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1);
        vp.setMargins(dp(12),0,dp(12),0); body.addView(viewport,vp);

        tools=column(); tools.setPadding(dp(16),dp(12),dp(16),0);
        if(landscape) body.addView(tools,new LinearLayout.LayoutParams(dp(264),-1));
        else body.addView(tools,new LinearLayout.LayoutParams(-1,dp(252)));
        LinearLayout modebar=row(); modebar.setPadding(dp(4),dp(4),dp(4),dp(4)); modebar.setBackground(shape(0xff111a20,14,0xff2b383f));
        String[] modeNames={"通用","车辆","取景"};
        for(int i=0;i<3;i++) { final int index=i; modes[i]=text(modeNames[i],13,MUTED); modes[i].setGravity(Gravity.CENTER); modes[i].setOnClickListener(v->{haptic(v);actions.mode(index);}); modes[i].setContentDescription(modeNames[i]+"模式"); modebar.addView(modes[i],new LinearLayout.LayoutParams(0,dp(40),1)); }
        tools.addView(modebar,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout params=row(); params.setPadding(0,dp(10),0,0);
        model=parameter(params,"MODEL","NANO",actions::model); size=parameter(params,"INPUT","320",actions::size); engine=parameter(params,"ENGINE","CPU",actions::engine);
        tools.addView(params,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout controls=row(); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setPadding(dp(10),dp(8),dp(10),0);
        LinearLayout history=column(); history.setGravity(Gravity.CENTER); history.setOnClickListener(v->{haptic(v); actions.history();}); history.setContentDescription("查看本地截图记录"); history.setFocusable(true);
        history.addView(icon("gallery","本地截图",actions::history),new LinearLayout.LayoutParams(dp(48),dp(44)));
        captureCount=text("00  CAPTURES",8,MUTED); captureCount.setTypeface(Typeface.MONOSPACE); history.addView(captureCount);
        controls.addView(history,new LinearLayout.LayoutParams(0,dp(66),1));
        Shutter shutter=new Shutter(a); shutter.setContentDescription("截取当前画面和检测信息"); shutter.setOnClickListener(v->{haptic(v); actions.shutter();});
        controls.addView(shutter,new LinearLayout.LayoutParams(dp(82),dp(82)));
        LinearLayout flipbox=column(); flipbox.setGravity(Gravity.CENTER);
        flipbox.addView(icon("flip","切换前后摄像头",actions::flip),new LinearLayout.LayoutParams(dp(50),dp(48)));
        TextView fliplabel=text("翻转镜头",9,MUTED); fliplabel.setGravity(Gravity.CENTER); flipbox.addView(fliplabel); controls.addView(flipbox,new LinearLayout.LayoutParams(0,dp(66),1));
        tools.addView(controls,new LinearLayout.LayoutParams(-1,landscape?dp(92):dp(96)));
        LinearLayout footer=row(); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(dp(2),dp(8),dp(2),0);
        TextView privacy=text("●  ON DEVICE",8,MINT); privacy.setTypeface(Typeface.MONOSPACE); footer.addView(privacy,new LinearLayout.LayoutParams(0,-2,1));
        TextView gps=text("定位未启用  ·  离线",9,MUTED); footer.addView(gps); tools.addView(footer,new LinearLayout.LayoutParams(-1,dp(30)));
    }

    void profile(int mode,int capacity,int resolution,int backend,int captures) {
        for(int i=0;i<3;i++) { modes[i].setTextColor(i==mode?INK:MUTED); modes[i].setTypeface(Typeface.DEFAULT,i==mode?Typeface.BOLD:Typeface.NORMAL); modes[i].setBackground(i==mode?metal(MINT,0xff79cbb3,10):null); }
        model.setText(capacity==0?"NANO  ⌄":"SMALL  ⌄"); size.setText(new String[]{"320  ⌄","480  ⌄","640  ⌄"}[resolution]); engine.setText(backend==0?"CPU  ⌄":"VULKAN  ⌄");
        captureCount.setText(String.format(Locale.US,"%02d  CAPTURES",captures));
    }
    void stats(float[] values,boolean running,boolean preview) {
        fps.setText(running&&values[0]>0?String.format(Locale.US,"%.1f",values[0]):"—");
        latency.setText(running&&!preview?String.format(Locale.US,"%.0f",values[1]):"—");
        objects.setText(running&&!preview?String.format(Locale.US,"%02d",(int)values[2]):"—");
        status.setText(running?(preview?"●  VIEWFINDER":"●  LIVE DETECTION"):"○  STANDBY");
    }
    void busy(String text) { message.setText(text); message.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE); }
    void lens(int facing) { lens.setText(facing==1?"REAR / 1×":"FRONT / MIRROR"); }
    int dp(float value) { return (int)(value*activity.getResources().getDisplayMetrics().density+.5f); }
    TextView text(String value,float sp,int color) { TextView t=new TextView(activity); t.setText(value); t.setTextColor(color); t.setTextSize(sp); t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false); return t; }
    LinearLayout column() { LinearLayout l=new LinearLayout(activity); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l=new LinearLayout(activity); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    GradientDrawable shape(int color,float radius,int border) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); if(border!=0)g.setStroke(dp(1),border); return g; }
    GradientDrawable metal(int start,int end,float radius) { GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{start,end}); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),0x55ffffff); return g; }
    private TextView parameter(LinearLayout row,String title,String value,Runnable click) {
        LinearLayout card=column(); card.setGravity(Gravity.CENTER); card.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x225bd5b0),metal(0xff27323a,0xff151e25,10),null)); card.setElevation(dp(2));
        TextView label=text(title,8,AMBER); label.setGravity(Gravity.CENTER); label.setLetterSpacing(.12f); card.addView(label);
        TextView content=text(value,12,WHITE); content.setTypeface(Typeface.MONOSPACE); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2); cp.topMargin=dp(6); card.addView(content,cp);
        card.setOnClickListener(v->{haptic(v);click.run();}); card.setContentDescription(title+" 参数选择"); card.setFocusable(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(dp(3),0,dp(3),0); row.addView(card,p); return content;
    }
    private TextView lcdCell(LinearLayout parent,String caption,String value,String unit) {
        LinearLayout cell=column(); cell.setGravity(Gravity.CENTER); TextView label=text(caption,8,0xff86a79b); label.setGravity(Gravity.CENTER); label.setTypeface(Typeface.MONOSPACE); cell.addView(label);
        LinearLayout numeric=row(); numeric.setGravity(Gravity.CENTER_VERTICAL); TextView t=text(value,23,MINT); t.setTypeface(Typeface.create("monospace",Typeface.BOLD)); numeric.addView(t);
        TextView u=text(" "+unit,9,0xff79988f); numeric.addView(u); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2); p.topMargin=dp(5); cell.addView(numeric,p); parent.addView(cell,new LinearLayout.LayoutParams(0,-1,1)); return t;
    }
    View icon(String kind,String label,Runnable click) { Icon v=new Icon(activity,kind); v.setContentDescription(label); v.setFocusable(true); v.setOnClickListener(w->{haptic(w);click.run();}); return v; }
    static void haptic(View v) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }

    Dialog sheet(String title,String subtitle) {
        Dialog dialog=new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column(); box.setPadding(dp(20),dp(18),dp(20),dp(20)); box.setBackground(metal(0xff202d34,0xff10191f,22));
        TextView handle=text("━━",16,0xff51636a); handle.setGravity(Gravity.CENTER); box.addView(handle,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView heading=text(title,21,WHITE); heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(heading);
        TextView sub=text(subtitle,12,MUTED); sub.setLineSpacing(dp(3),1); sub.setPadding(0,dp(8),0,dp(16)); box.addView(sub);
        ScrollView scroll=new ScrollView(activity); scroll.setTag("sheet-scroll"); scroll.setFillViewport(false); scroll.addView(box); dialog.setContentView(scroll);
        Window window=dialog.getWindow(); if(window!=null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams w=window.getAttributes(); w.dimAmount=.55f; w.gravity=Gravity.BOTTOM; window.setAttributes(w); }
        dialog.setOnShowListener(d->{if(window!=null){window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(470)), -2);}});
        sheets.add(dialog); dialog.setOnDismissListener(d->sheets.remove(dialog));
        return dialog;
    }
    void dismissSheets(){for(Dialog d:new ArrayList<>(sheets))d.dismiss();sheets.clear();}
    LinearLayout sheetBody(Dialog d) { return (LinearLayout)((ScrollView)d.findViewById(android.R.id.content).findViewWithTag("sheet-scroll")).getChildAt(0); }

    static final class GridOverlay extends View {
        boolean grid=false; final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GridOverlay(Activity a) {super(a);}
        @Override protected void onDraw(Canvas c) {
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(d);
            paint.setColor(0x3084c5b2);
            if(grid) for(int i=1;i<3;i++){c.drawLine(w*i/3,0,w*i/3,h,paint);c.drawLine(0,h*i/3,w,h*i/3,paint);}
            paint.setColor(0x90b1d5c7); float m=14*d,l=18*d;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){float px=x==0?m:w-m,py=y==0?60*d:h-94*d;c.drawLine(px,py,px+(x==0?l:-l),py,paint);c.drawLine(px,py,px,py+(y==0?l:-l),paint);}
            paint.setColor(0x70c8e9dc); c.drawLine(w/2-7*d,h/2,w/2+7*d,h/2,paint);c.drawLine(w/2,h/2-7*d,w/2,h/2+7*d,paint);
        }
    }
    static final class Shutter extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Shutter(Activity a){super(a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-3;
            p.setShader(new LinearGradient(0,0,0,getHeight(),new int[]{0xff72818a,0xff101820},null,Shader.TileMode.CLAMP));p.setShadowLayer(5,0,3,0x99000000);c.drawCircle(x,y,r,p);p.clearShadowLayer();
            p.setShader(null);p.setColor(0xff090f13);c.drawCircle(x,y,r-4,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffa2d8c2);c.drawCircle(x,y,r-9,p);p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0,y-r,0,y+r,isPressed()?0xff5fa58d:0xffd1f8e6,0xff74c9a6,Shader.TileMode.CLAMP));c.drawCircle(x,y,r-14,p);p.setShader(null);
            p.setColor(0xff24483e);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(x-10,y-7,x+10,y+7,3,3,p);c.drawCircle(x,y,3,p);p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){boolean handled=super.onTouchEvent(e);invalidate();return handled;}
        @Override public boolean performClick(){return super.performClick();}
    }
    static final class Icon extends View {
        final String kind; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Activity a,String k){super(a);kind=k;}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density; c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(d,d);p.setColor(0xffc4d6d5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setStrokeCap(Paint.Cap.ROUND);
            if(kind.equals("gallery")){c.drawRoundRect(-13,-11,13,11,4,4,p);c.drawCircle(5,-4,2,p);Path q=new Path();q.moveTo(-10,8);q.lineTo(-3,0);q.lineTo(3,6);q.lineTo(7,2);q.lineTo(11,8);c.drawPath(q,p);}
            else if(kind.equals("flip")){c.drawRoundRect(-13,-9,13,9,4,4,p);c.drawArc(-8,-6,8,6,30,245,false,p);c.drawLine(-7,0,-7,-5,p);c.drawLine(-7,-5,-2,-5,p);}
            else {c.drawCircle(0,0,8,p);c.drawCircle(0,0,3,p);for(int i=0;i<8;i++){double t=i*Math.PI/4;c.drawLine((float)Math.cos(t)*10,(float)Math.sin(t)*10,(float)Math.cos(t)*12,(float)Math.sin(t)*12,p);}}
            p.setStyle(Paint.Style.FILL); c.restore();
        }
    }
}
