package com.tencent.yolov8ncnn;

import android.app.*;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.*;

/** Explicitly enabled experiment controls. The ROI is provided by the user, not road segmentation. */
final class LabUi {
    interface Apply {void apply(int flags,float[] roi,int direction,long dwell);}
    private final Activity activity;private final CameraUi ui;private final SharedPreferences prefs;
    LabUi(Activity activity,CameraUi ui,SharedPreferences prefs){this.activity=activity;this.ui=ui;this.prefs=prefs;}
    private void button(LinearLayout box,String label,Runnable action){TextView row=ui.text(label,14,CameraUi.YELLOW);row.setMinHeight(ui.dp(48));row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));row.setBackground(ui.shape(CameraUi.PANEL,8,CameraUi.LINE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=ui.dp(8);box.addView(row,p);row.setOnClickListener(v->action.run());}
    private SeekBar slider(LinearLayout box,String title,int progress,int max){TextView label=ui.text(title+" · "+progress,12,CameraUi.WHITE);label.setPadding(0,ui.dp(12),0,0);box.addView(label);SeekBar bar=new SeekBar(activity);bar.setMax(max);bar.setProgress(progress);bar.setContentDescription(title);box.addView(bar,new LinearLayout.LayoutParams(-1,ui.dp(40)));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(title+" · "+value);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});return bar;}
    void show(int active,String model,Runnable models,Runnable capture,Runnable reset,Apply apply){
        Dialog d=ui.sheet("实验室 · 可独立关闭","当前："+model+"\n车辆规则仅固定机位有效：先固定手机，再指定区域和正常行驶方向。区域停留不等于违法停车；虚拟线不等于实线或应急车道。切镜头、模型或离开前台会关闭规则。GPS速度不是其他车辆速度。");LinearLayout box=ui.sheetBody(d);
        button(box,"切换实验模型",()->{d.dismiss();models.run();});button(box,"采集分辨率 · 640×480 / 1280×720",()->{d.dismiss();capture.run();});
        String[] names={"固定机位：区域停留（疑似）","固定机位：逆向运动（疑似）","固定机位：虚拟线跨越","道路异物：限定已知类别 + 手选区域"};CheckBox[] flags=new CheckBox[4];
        for(int i=0;i<4;i++){flags[i]=new CheckBox(activity);flags[i].setText(names[i]);flags[i].setTextColor(CameraUi.WHITE);flags[i].setTextSize(13);flags[i].setChecked((active&(1<<i))!=0);box.addView(flags[i]);}
        CheckBox fixed=new CheckBox(activity);fixed.setText("我已固定手机（启用车辆规则必须勾选）");fixed.setTextColor(CameraUi.YELLOW);box.addView(fixed);
        SeekBar[] roi=new SeekBar[4];String[] labels={"区域左界 %","区域上界 %","区域右界 %","区域下界 %"};String[] keys={"roi_l","roi_t","roi_r","roi_b"};float[] defaults={.1f,.4f,.9f,.92f};
        for(int i=0;i<4;i++)roi[i]=slider(box,labels[i],Math.round(prefs.getFloat(keys[i],defaults[i])*100),100);
        TextView directionLabel=ui.text("正常行驶方向（画面坐标）",12,CameraUi.YELLOW);box.addView(directionLabel);
        RadioGroup direction=new RadioGroup(activity);int[] directionIds=new int[4];String[] directions={"向上","向下","向左","向右"};
        for(int i=0;i<4;i++){RadioButton choice=new RadioButton(activity);directionIds[i]=android.view.View.generateViewId();choice.setId(directionIds[i]);choice.setText(directions[i]);choice.setTextColor(CameraUi.WHITE);direction.addView(choice);}direction.check(directionIds[Math.max(0,Math.min(3,prefs.getInt("rule_direction",0)))]);box.addView(direction);
        SeekBar dwell=slider(box,"静止阈值秒（最小2）",prefs.getInt("rule_dwell_s",5),30);
        button(box,"应用本次实验设置",()->{
            int selected=0;for(int i=0;i<4;i++)if(flags[i].isChecked())selected|=1<<i;
            if((selected&7)!=0&&!fixed.isChecked()){Toast.makeText(activity,"车辆规则需要先固定手机并勾选确认",Toast.LENGTH_SHORT).show();return;}
            float[] values=new float[4];for(int i=0;i<4;i++)values[i]=roi[i].getProgress()/100f;
            if(values[2]-values[0]<.05f||values[3]-values[1]<.05f){Toast.makeText(activity,"右界/下界必须大于左界/上界至少5%",Toast.LENGTH_SHORT).show();return;}
            SharedPreferences.Editor editor=prefs.edit();for(int i=0;i<4;i++)editor.putFloat(keys[i],values[i]);int dir=0;for(int i=0;i<4;i++)if(direction.getCheckedRadioButtonId()==directionIds[i])dir=i;int seconds=Math.max(2,dwell.getProgress());editor.putInt("rule_direction",dir).putInt("rule_dwell_s",seconds).apply();d.dismiss();apply.apply(selected,values,dir,seconds*1000L);
        });
        button(box,"关闭全部实验规则",()->{d.dismiss();apply.apply(0,defaults,0,5000);});
        button(box,"一键恢复 COCO Nano / CPU / 基线采集",()->{d.dismiss();reset.run();});d.show();
    }
}
