package com.tencent.yolov8ncnn;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

/** Native inspection UI. Colors, spacing and presentation live here; inference stays in JNI. */
final class CameraUi {
    interface Actions {
        void mode(int value); void model(); void size(); void engine(); void shutter();
        void history(); void flip(); void settings(); void dashboard();
        void locationSettings();
        void monitorDashboard();
    }
    static final int INK=0xff0c0d0f, PANEL=0xff191b1f, LINE=0xff35383e;
    static final int WHITE=0xffedf0f3, MUTED=0xff999fa8;
    static final int YELLOW=0xffffdf1b, GREEN=0xff6be455;
    // Kept as semantic aliases for existing dialog callers.
    static final int MINT=YELLOW, AMBER=YELLOW;
    final Activity activity;
    final Actions actions;
    final LinearLayout root, tools;
    final SurfaceView surface;
    final FrameLayout viewport;
    final TextView status, lens, message, fps, latency, objects, model, size, engine, captureCount;
    final TextView[] modes=new TextView[3];
    final GridOverlay overlay;
    private final Gauge fpsGauge, confidenceGauge;
    private final TextView profileBadge;
    private final TextView locationStatus;
    private final boolean landscape;
    private final ArrayList<Dialog> sheets=new ArrayList<>();

    CameraUi(Activity a, Actions callbacks) {
        activity=a; actions=callbacks;
        landscape=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        root=column(); root.setBackgroundColor(INK);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header=row(); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14),0,dp(14),0);
        header.addView(new Mark(a),new LinearLayout.LayoutParams(dp(32),dp(38)));
        LinearLayout brand=column(); brand.setPadding(dp(8),0,0,0);
        android.text.SpannableString word=new android.text.SpannableString("wandervision");
        word.setSpan(new android.text.style.ForegroundColorSpan(YELLOW),6,12,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView name=text("",22,WHITE);name.setText(word);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));brand.addView(name);
        TextView sub=text("AI ROAD INSPECTION",8,MUTED);sub.setLetterSpacing(.22f);brand.addView(sub);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("FIELD MODE  ●",9,YELLOW);local.setLetterSpacing(.06f);header.addView(local);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));

        LinearLayout body=new LinearLayout(a);body.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        viewport=new FrameLayout(a);viewport.setBackground(shape(0xff101114,12,LINE));viewport.setClipToOutline(true);
        surface=new SurfaceView(a);viewport.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        overlay=new GridOverlay(a);overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        viewport.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout badges=row();badges.setGravity(Gravity.CENTER_VERTICAL);badges.setPadding(dp(10),dp(10),dp(58),0);
        status=text("●  准备中",10,WHITE);status.setPadding(dp(9),dp(7),dp(9),dp(7));status.setBackground(shape(0xdc15181c,7,0x40646b73));badges.addView(status);
        badges.addView(new Space(a),new LinearLayout.LayoutParams(0,1,1));
        profileBadge=text("NANO · 320",9,WHITE);profileBadge.setPadding(dp(8),dp(7),dp(8),dp(7));profileBadge.setBackground(shape(0xdc15181c,7,0x40646b73));badges.addView(profileBadge);
        viewport.addView(badges,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        message=text("正在准备本地视觉引擎",13,WHITE);message.setGravity(Gravity.CENTER);message.setPadding(dp(22),dp(10),dp(60),dp(10));
        viewport.addView(message,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));

        LinearLayout rail=column();rail.setPadding(dp(3),dp(5),dp(3),dp(5));rail.setBackground(shape(0xe8141619,10,0xff383c42));
        rail.addView(railItem("settings","设置",actions::settings));
        rail.addView(railItem("layers","模型",actions::model));
        rail.addView(railItem("chart","统计",actions::dashboard));
        View map=railItem("pin","仪表盘",actions::monitorDashboard);map.setContentDescription("打开监控中心仪表盘和设备地图");rail.addView(map);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(46),-2,Gravity.TOP|Gravity.RIGHT);rp.topMargin=dp(7);rp.rightMargin=dp(5);viewport.addView(rail,rp);

        LinearLayout selector=row();selector.setPadding(dp(3),dp(3),dp(3),dp(3));selector.setBackground(shape(0xdc111317,9,0xff45494f));
        String[] names={"通用","车辆","取景"};
        for(int i=0;i<3;i++) {
            final int value=i;modes[i]=text(names[i],11,WHITE);modes[i].setGravity(Gravity.CENTER);
            modes[i].setOnClickListener(v->{haptic(v);actions.mode(value);});modes[i].setContentDescription(names[i]+"模式");
            selector.addView(modes[i],new LinearLayout.LayoutParams(dp(51),dp(38)));
        }
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.LEFT);sp.setMargins(dp(10),0,0,dp(10));viewport.addView(selector,sp);
        lens=text("后置",9,WHITE);lens.setPadding(dp(9),dp(7),dp(9),dp(7));lens.setBackground(shape(0xdc111317,7,0xff45494f));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);lp.setMargins(0,0,dp(10),dp(16));viewport.addView(lens,lp);
        LinearLayout.LayoutParams vp=landscape?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1);
        vp.setMargins(dp(10),0,landscape?0:dp(10),0);body.addView(viewport,vp);

        tools=column();tools.setPadding(dp(10),dp(8),dp(10),0);
        body.addView(tools,landscape?new LinearLayout.LayoutParams(dp(304),-1):new LinearLayout.LayoutParams(-1,dp(310)));
        LinearLayout metrics=row();
        LinearLayout fpsCard=metricCard(metrics,"帧率 / FPS");fpsGauge=new Gauge(a,GREEN);fpsCard.addView(fpsGauge,new LinearLayout.LayoutParams(-1,0,1));fps=fpsGauge.number;
        LinearLayout confCard=metricCard(metrics,"最高置信度");confidenceGauge=new Gauge(a,YELLOW);confCard.addView(confidenceGauge,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout objCard=metricCard(metrics,"当前目标");objects=numberIn(objCard,"—","个 / 当前帧");
        LinearLayout msCard=metricCard(metrics,"推理耗时");latency=numberIn(msCard,"—","ms");
        tools.addView(metrics,new LinearLayout.LayoutParams(-1,dp(landscape?74:85)));
        metrics.setOnClickListener(v->actions.dashboard());metrics.setContentDescription("查看真实检测仪表盘");metrics.setFocusable(true);
        LinearLayout params=row();params.setPadding(0,dp(7),0,0);
        model=parameter(params,"模型","NANO",actions::model);size=parameter(params,"输入","320",actions::size);engine=parameter(params,"计算","CPU",actions::engine);
        tools.addView(params,new LinearLayout.LayoutParams(-1,dp(landscape?40:43)));

        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(dp(4),dp(6),dp(4),0);
        controls.setBackground(shape(0xff14161a,12,LINE));
        LinearLayout gallery=actionTile("gallery","相册",actions::history);captureCount=text("0 张",8,MUTED);captureCount.setGravity(Gravity.CENTER);gallery.addView(captureCount);
        controls.addView(gallery,new LinearLayout.LayoutParams(0,-1,1));
        Shutter shutter=new Shutter(a);shutter.setContentDescription("保存当前画面与检测信息");shutter.setOnClickListener(v->{haptic(v);actions.shutter();});
        controls.addView(shutter,new LinearLayout.LayoutParams(dp(landscape?66:76),dp(landscape?66:76)));
        controls.addView(actionTile("flip","翻转",actions::flip),new LinearLayout.LayoutParams(0,-1,1));
        controls.addView(actionTile("chart","仪表盘",actions::dashboard),new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(landscape?76:86));cp.topMargin=dp(8);tools.addView(controls,cp);
        LinearLayout nav=row();
        nav.addView(navItem("scan","巡检",true,null),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("records","记录",false,actions::history),new LinearLayout.LayoutParams(0,-1,1));
        View track=navItem("pin","轨迹",false,null);track.setAlpha(.3f);track.setEnabled(false);track.setContentDescription("轨迹，尚未接入");nav.addView(track,new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navItem("settings","工具",false,actions::settings),new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(landscape?48:53));np.topMargin=dp(landscape?6:7);tools.addView(nav,np);
        locationStatus=text("定位准备中",9,MUTED);locationStatus.setGravity(Gravity.CENTER);locationStatus.setSingleLine(true);locationStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);locationStatus.setContentDescription("前台定位状态，点击设置");locationStatus.setOnClickListener(v->actions.locationSettings());tools.addView(locationStatus,new LinearLayout.LayoutParams(-1,dp(landscape?16:18)));
    }

    void profile(int mode,int capacity,int resolution,int backend,int captures) {
        for(int i=0;i<3;i++) {
            modes[i].setTextColor(i==mode?INK:WHITE);modes[i].setTypeface(Typeface.DEFAULT,i==mode?Typeface.BOLD:Typeface.NORMAL);
            modes[i].setBackground(i==mode?shape(YELLOW,6,0):null);
        }
        String modelName=capacity==0?"NANO":"SMALL",input=new String[]{"320","480","640"}[resolution];
        model.setText(modelName+" ⌄");size.setText(input+" ⌄");engine.setText(backend==0?"CPU ⌄":"GPU ⌄");
        profileBadge.setText(modelName+" · "+input);captureCount.setText(captures+" 张");
    }
    void stats(float[] values,boolean running,boolean preview) {
        fpsGauge.update(running&&values[0]>0?String.format(Locale.US,"%.1f",values[0]):"—",running?values[0]/60f:0);
        confidenceGauge.update(running&&!preview&&values[2]>0?String.format(Locale.US,"%.0f%%",values[3]*100):"—",running&&!preview?values[3]:0);
        latency.setText(running&&!preview?String.format(Locale.US,"%.0f",values[1]):"—");
        objects.setText(running&&!preview?String.format(Locale.US,"%02d",(int)values[2]):"—");
        status.setText(running?(preview?"●  纯取景":"●  LIVE · YOLOv8"):"○  待机");status.setTextColor(running?WHITE:MUTED);
    }
    void busy(String text){message.setText(text);message.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);}
    void lens(int facing){lens.setText(facing==1?"后置镜头":"前置 · 镜像");}
    int dp(float value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,float sp,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(sp);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    GradientDrawable shape(int color,float radius,int border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(border!=0)g.setStroke(dp(1),border);return g;}
    GradientDrawable metal(int start,int end,float radius){return shape(PANEL,radius,LINE);}
    private LinearLayout metricCard(LinearLayout parent,String title) {
        LinearLayout cell=column();cell.setPadding(dp(4),dp(8),dp(4),dp(4));cell.setBackground(shape(PANEL,9,LINE));
        TextView caption=text(title,8.5f,MUTED);caption.setGravity(Gravity.CENTER);cell.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(2),0,dp(2),0);parent.addView(cell,p);return cell;
    }
    private TextView numberIn(LinearLayout card,String value,String unit) {
        TextView t=text(value,24,WHITE);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setGravity(Gravity.CENTER);card.addView(t,new LinearLayout.LayoutParams(-1,0,1));
        TextView u=text(unit,8,MUTED);u.setGravity(Gravity.CENTER);card.addView(u,new LinearLayout.LayoutParams(-1,dp(13)));return t;
    }
    private TextView parameter(LinearLayout parent,String caption,String value,Runnable click) {
        LinearLayout button=row();button.setGravity(Gravity.CENTER);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffdf1b),shape(PANEL,7,LINE),null));
        TextView label=text(caption+"  ",9,MUTED);button.addView(label);TextView t=text(value,11,WHITE);button.addView(t);
        button.setOnClickListener(v->{haptic(v);click.run();});button.setContentDescription(caption+"参数选择");button.setFocusable(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(2),0,dp(2),0);parent.addView(button,p);return t;
    }
    private View railItem(String kind,String label,Runnable action) {
        LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setMinimumHeight(dp(54));
        Icon image=new Icon(activity,kind);tile.addView(image,new LinearLayout.LayoutParams(dp(27),dp(27)));
        TextView caption=text(label,8,MUTED);caption.setGravity(Gravity.CENTER);tile.addView(caption);
        if(action!=null){tile.setOnClickListener(v->{haptic(v);action.run();});tile.setFocusable(true);}else tile.setEnabled(false);
        tile.setContentDescription(label);return tile;
    }
    private LinearLayout actionTile(String kind,String label,Runnable action) {
        LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setOnClickListener(v->{haptic(v);action.run();});tile.setFocusable(true);tile.setContentDescription(label);
        tile.addView(new Icon(activity,kind),new LinearLayout.LayoutParams(dp(33),dp(31)));
        TextView t=text(label,9,MUTED);t.setGravity(Gravity.CENTER);tile.addView(t);return tile;
    }
    private View navItem(String kind,String label,boolean active,Runnable action) {
        LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setBackground(shape(active?0xff29261b:0xff14161a,5,LINE));
        Icon image=new Icon(activity,kind);image.tint=active?YELLOW:MUTED;tile.addView(image,new LinearLayout.LayoutParams(dp(25),dp(26)));
        TextView t=text(label,8.5f,active?YELLOW:MUTED);t.setGravity(Gravity.CENTER);tile.addView(t);
        if(action!=null){tile.setOnClickListener(v->{haptic(v);action.run();});tile.setFocusable(true);}tile.setContentDescription(label);return tile;
    }
    static void haptic(View v){v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}

    Dialog sheet(String title,String subtitle) {
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=column();box.setPadding(dp(18),dp(16),dp(18),dp(20));box.setBackground(shape(0xff15171a,16,LINE));
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);
        View accent=new View(activity);accent.setBackgroundColor(YELLOW);heading.addView(accent,new LinearLayout.LayoutParams(dp(3),dp(22)));
        TextView name=text(title,20,WHITE);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setPadding(dp(10),0,0,0);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text("×",27,MUTED);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭面板");close.setOnClickListener(v->dialog.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));box.addView(heading);
        TextView description=text(subtitle,11,MUTED);description.setLineSpacing(dp(3),1);description.setPadding(0,dp(7),0,dp(12));box.addView(description);
        ScrollView scroll=new ScrollView(activity);scroll.setTag("sheet-scroll");scroll.setFillViewport(false);scroll.addView(box);dialog.setContentView(scroll);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams w=window.getAttributes();w.dimAmount=.48f;w.gravity=Gravity.BOTTOM;w.windowAnimations=R.style.SheetMotion;window.setAttributes(w);}
        dialog.setOnShowListener(d->{if(window!=null)window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(440)),-2);});
        sheets.add(dialog);dialog.setOnDismissListener(d->sheets.remove(dialog));return dialog;
    }
    void dismissSheets(){for(Dialog d:new ArrayList<>(sheets))d.dismiss();sheets.clear();}
    void location(String value){locationStatus.setText(value);}
    LinearLayout sheetBody(Dialog d){return (LinearLayout)((ScrollView)d.findViewById(android.R.id.content).findViewWithTag("sheet-scroll")).getChildAt(0);}
    void dashboardTiles(LinearLayout box,float[] stats,boolean fresh,boolean preview,int captures,String modelName,String actualEngine) {
        LinearLayout first=row();
        LinearLayout f=metricCard(first,"FPS");Gauge fg=new Gauge(activity,GREEN);fg.update(fresh?String.format(Locale.US,"%.1f",stats[0]):"—",fresh?stats[0]/60f:0);f.addView(fg,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout c=metricCard(first,"最高置信度");Gauge cg=new Gauge(activity,YELLOW);cg.update(fresh&&!preview&&stats[2]>0?String.format(Locale.US,"%.0f%%",stats[3]*100):"—",fresh&&!preview?stats[3]:0);c.addView(cg,new LinearLayout.LayoutParams(-1,0,1));box.addView(first,new LinearLayout.LayoutParams(-1,dp(116)));
        LinearLayout second=row();second.setPadding(0,dp(8),0,0);
        numberIn(metricCard(second,"当前帧目标"),fresh&&!preview?Integer.toString((int)stats[2]):"—","个 / 非去重");
        numberIn(metricCard(second,"推理耗时"),fresh&&!preview?String.format(Locale.US,"%.0f",stats[1]):"—","ms");
        numberIn(metricCard(second,"本地截图"),Integer.toString(captures),"张 / 最近记录");box.addView(second,new LinearLayout.LayoutParams(-1,dp(92)));
        TextView info=text("当前模型  "+modelName+"     实际引擎  "+actualEngine,12,WHITE);info.setPadding(dp(10),dp(14),dp(10),dp(14));box.addView(info);
    }
    ImageView recordCard(LinearLayout box,String title,String metadata,String detail,Runnable click) {
        LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(9),dp(10),dp(9),dp(10));card.setBackground(shape(PANEL,10,LINE));
        ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("本地截图缩略图");image.setBackground(shape(0xff292c31,6,0));image.setClipToOutline(true);card.addView(image,new LinearLayout.LayoutParams(dp(62),dp(74)));
        LinearLayout copy=column();copy.setPadding(dp(12),0,dp(4),0);
        TextView heading=text(title,15,WHITE);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);copy.addView(heading);
        TextView meta=text(metadata,10,MUTED);meta.setPadding(0,dp(7),0,dp(5));copy.addView(meta);
        TextView description=text(detail,11,MUTED);copy.addView(description);card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView marker=text("›",25,YELLOW);marker.setGravity(Gravity.CENTER);card.addView(marker,new LinearLayout.LayoutParams(dp(22),dp(44)));
        card.setOnClickListener(v->{haptic(v);click.run();});card.setFocusable(true);card.setContentDescription(title+"，"+metadata+"，"+detail+"，查看截图");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);box.addView(card,p);return image;
    }

    static final class Gauge extends FrameLayout {
        final TextView number;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF arc=new RectF();private final int color;private float progress;
        Gauge(Activity a,int tint){super(a);color=tint;setWillNotDraw(false);number=new TextView(a);number.setText("—");number.setTextSize(19);number.setTextColor(WHITE);number.setGravity(Gravity.CENTER);number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);p.topMargin=6;addView(number,p);}
        void update(String value,float amount){number.setText(value);progress=Math.max(0,Math.min(1,amount));invalidate();}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density;float w=Math.min(getWidth()-12*d,(getHeight()-7*d)*1.6f),left=(getWidth()-w)/2;arc.set(left,9*d,left+w,9*d+w);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*d);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xff35383e);c.drawArc(arc,190,160,false,paint);paint.setColor(color);if(progress>0)c.drawArc(arc,190,160*progress,false,paint);}
    }
    static final class GridOverlay extends View {
        boolean grid=false;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GridOverlay(Activity a){super(a);}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0x40ffffff);
            if(grid)for(int i=1;i<3;i++){c.drawLine(w*i/3,0,w*i/3,h,paint);c.drawLine(0,h*i/3,w,h*i/3,paint);}
            paint.setColor(0xb3ffffff);float m=8*d,l=16*d;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){float px=x==0?m:w-m,py=y==0?m:h-m;c.drawLine(px,py,px+(x==0?l:-l),py,paint);c.drawLine(px,py,px,py+(y==0?l:-l),paint);}
        }
    }
    static final class Shutter extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF body=new RectF();
        Shutter(Activity a){super(a);setFocusable(true);}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-5*d;
            p.setStyle(Paint.Style.FILL);p.setColor(0xff292a21);c.drawCircle(x,y,r+3*d,p);p.setColor(isPressed()?0xffd6b900:YELLOW);c.drawCircle(x,y,r,p);
            c.save();c.translate(x,y);c.scale(d,d);p.setColor(INK);body.set(-12,-8,12,9);c.drawRoundRect(body,3,3,p);c.drawRect(-7,-11,1,-7,p);p.setStyle(Paint.Style.STROKE);p.setColor(YELLOW);p.setStrokeWidth(2);c.drawCircle(0,0,5,p);p.setStyle(Paint.Style.FILL);c.restore();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){boolean handled=super.onTouchEvent(e);invalidate();return handled;}
        @Override public boolean performClick(){return super.performClick();}
    }
    static final class Mark extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
        Mark(Activity a){super(a);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setColor(YELLOW);path.reset();path.moveTo(w/2,h*.14f);path.lineTo(w*.98f,h*.83f);path.lineTo(w*.02f,h*.83f);path.close();c.drawPath(path,p);p.setColor(INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.07f);path.reset();path.moveTo(w*.18f,h*.55f);path.lineTo(w*.43f,h*.65f);path.lineTo(w*.57f,h*.51f);path.lineTo(w*.83f,h*.73f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    }
    static final class Icon extends View {
        final String kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();int tint=MUTED;
        Icon(Activity a,String value){super(a);kind=value;}
        @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density;c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(d*.85f,d*.85f);p.setColor(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);
            switch(kind){
                case "gallery":c.drawRoundRect(-12,-10,12,10,2,2,p);c.drawCircle(5,-4,2,p);path.reset();path.moveTo(-10,8);path.lineTo(-3,0);path.lineTo(3,6);path.lineTo(7,2);path.lineTo(11,8);c.drawPath(path,p);break;
                case "flip":c.drawRoundRect(-12,-8,12,8,3,3,p);c.drawArc(-7,-5,7,5,30,245,false,p);c.drawLine(-6,0,-6,-4,p);c.drawLine(-6,-4,-2,-4,p);break;
                case "chart":p.setStyle(Paint.Style.FILL);c.drawRect(-11,0,-6,11,p);c.drawRect(-2,-6,3,11,p);c.drawRect(7,-12,12,11,p);break;
                case "layers":for(int y=-6;y<=6;y+=6){path.reset();path.moveTo(-12,y);path.lineTo(0,y-7);path.lineTo(12,y);path.lineTo(0,y+7);path.close();c.drawPath(path,p);}break;
                case "pin":c.drawCircle(0,-4,7,p);c.drawLine(-6,0,0,12,p);c.drawLine(6,0,0,12,p);c.drawCircle(0,-4,2,p);break;
                case "records":c.drawRoundRect(-9,-12,9,12,2,2,p);for(int y=-6;y<=6;y+=6)c.drawLine(-4,y,5,y,p);break;
                case "scan":c.drawCircle(0,0,9,p);c.drawLine(-13,0,-5,0,p);c.drawLine(5,0,13,0,p);c.drawLine(0,-13,0,-5,p);c.drawLine(0,5,0,13,p);break;
                default:c.drawCircle(0,0,7,p);c.drawCircle(0,0,2,p);for(int i=0;i<8;i++){double t=i*Math.PI/4;c.drawLine((float)Math.cos(t)*9,(float)Math.sin(t)*9,(float)Math.cos(t)*12,(float)Math.sin(t)*12,p);}
            }p.setStyle(Paint.Style.FILL);c.restore();}
    }
}
