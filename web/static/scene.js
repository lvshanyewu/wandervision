/* Lightweight perspective field illustrations. All objects and boxes are synthetic. */
'use strict';
window.WVScene=(()=>{
  const colors={road:'#414c54',edge:'#9da9ab',yellow:'#f4d454'};
  let c,w,h,weather;
  function poly(points,fill,stroke=null,width=1){c.beginPath();points.forEach((p,i)=>i?c.lineTo(...p):c.moveTo(...p));c.closePath();if(fill){c.fillStyle=fill;c.fill();}if(stroke){c.strokeStyle=stroke;c.lineWidth=width;c.stroke();}}
  function line(points,color,width=1){c.beginPath();points.forEach((p,i)=>i?c.lineTo(...p):c.moveTo(...p));c.strokeStyle=color;c.lineWidth=width;c.stroke();}
  function label(box,text,color=colors.yellow){
    let [x,y,ww,hh]=box;if(ww<10||hh<6||x>w||y>h||x+ww<0||y+hh<0)return;
    c.strokeStyle=color;c.lineWidth=Math.max(1,w/800);c.strokeRect(x,y,ww,hh);
    const fs=Math.max(9,w/90);c.font=`${fs}px "Segoe UI","Microsoft YaHei",sans-serif`;
    const tw=c.measureText(text).width+9,ty=Math.max(17,y-4);c.fillStyle=color;c.fillRect(x,ty-fs-4,tw,fs+6);c.fillStyle='#172229';c.textAlign='left';c.fillText(text,x+4,ty-2);
  }
  function bounds(points){const xs=points.map(p=>p[0]),ys=points.map(p=>p[1]);return [Math.min(...xs),Math.min(...ys),Math.max(...xs)-Math.min(...xs),Math.max(...ys)-Math.min(...ys)];}
  function box(P,x,z,width,depth,height,palette,windows=false){
    const a=P(x-width/2,0,z-depth/2),b=P(x+width/2,0,z-depth/2),d=P(x-width/2,0,z+depth/2),e=P(x+width/2,0,z+depth/2);
    const A=P(x-width/2,height,z-depth/2),B=P(x+width/2,height,z-depth/2),D=P(x-width/2,height,z+depth/2),E=P(x+width/2,height,z+depth/2);
    poly([a,b,B,A],palette[0]);poly([b,e,E,B],palette[1]);poly([a,d,D,A],palette[1]);poly([A,B,E,D],palette[2]);
    if(windows){
      const rows=Math.min(7,Math.floor(height/2.8));
      for(let j=1;j<=rows;j++)for(let i=0;i<3;i++){
        const xx=x-width*.38+i*width*.28,yy=j*2.4;
        poly([P(xx,yy,z-depth/2-.05),P(xx+width*.12,yy,z-depth/2-.05),P(xx+width*.12,yy+1,z-depth/2-.05),P(xx,yy+1,z-depth/2-.05)],weather==='night'&&((i+j)%3)?'#c1af71':'#6c858e');
      }
    }
    return [a,b,d,e,A,B,D,E];
  }
  function sky(){
    const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,weather==='night'?'#07111e':weather==='rain'?'#314554':'#486678');g.addColorStop(.55,weather==='night'?'#26333d':'#b2c2c8');g.addColorStop(1,'#263b35');c.fillStyle=g;c.fillRect(0,0,w,h);
    const seed=(i)=>Math.sin(i*127.1)*.5+.5;
    for(let i=0;i<13;i++){const x=i*w/12,high=h*(.06+seed(i)*.11);c.fillStyle=weather==='night'?'#162c33':'#617d86';c.fillRect(x,h*.37-high,w/10,high);}
    c.fillStyle=weather==='night'?'#1b302f':'#405e4b';poly([[0,h*.37],[0,h*.3],[w*.16,h*.25],[w*.3,h*.35],[w*.65,h*.34],[w*.79,h*.24],[w,h*.31],[w,h*.39]],c.fillStyle);
  }
  function patrol(t,campus){
    sky();const horizon=h*.34,f=w*.85;
    const curve=z=>Math.sin(z*.018+t*.17)*z*.08-Math.sin(t*.17)*z*.08;
    const P=(x,y,z)=>[w/2+(x+curve(z))*f/(z+12),horizon+(6-y)*f/(z+12)];
    poly([[0,horizon],[w,horizon],[w,h],[0,h]],weather==='night'?'#1a2928':'#47604c');
    const road=[];for(let z=230;z>=0;z-=5)road.push(P(-7,0,z));for(let z=0;z<=230;z+=5)road.push(P(7,0,z));poly(road,weather==='night'?'#222c34':weather==='rain'?'#3b4b55':colors.road);
    for(const side of [-1,1]){
      const edge=[],walk=[];for(let z=230;z>=0;z-=5)edge.push(P(side*7,0,z));for(let z=0;z<=230;z+=5)walk.push(P(side*10,0,z));poly([...edge,...walk],weather==='night'?'#344043':'#8a9895');
      line(edge,'#b7c3bb',Math.max(1,w/550));
      for(let i=0;i<14;i++){
        const z=230-((t*(campus?5:9)+i*19)%238);if(z<1)continue;
        const height=campus?7+(i%3)*2:8+(i%5)*3;
        box(P,side*(16+(i%2)*4),z,8+(i%3)*2,8,height,[weather==='night'?'#2c4149':'#809399',weather==='night'?'#1f3039':'#586d75',weather==='night'?'#3a4c51':'#a3b3b5'],true);
        if(i%2===0){const trunk=P(side*10.5,0,z),top=P(side*10.5,5,z);line([trunk,top],'#62716a',Math.max(1,30/(z+10)));c.fillStyle=weather==='night'?'#254135':'#41694d';c.beginPath();c.ellipse(top[0],top[1],w*1.5/(z+21),w*2/(z+21),0,0,Math.PI*2);c.fill();}
      }
    }
    for(let i=0;i<27;i++){
      const z=230-((t*12+i*12)%240);if(z<1)continue;
      for(const x of [-2.5,2.5])line([P(x,.02,z),P(x,.02,z+5)],'#d8ddd4',Math.max(1,90/(z+21)));
      line([P(0,.02,z),P(0,.02,z+8)],colors.yellow,Math.max(1,65/(z+21)));
    }
    for(let i=0;i<4;i++){
      const z=145-((t*(i%2?4:7)+i*33)%140),x=i%2?-4.5:4.5;
      const bb=box(P,x,z,1.9,4.4,1.55,[['#c6cbc5','#647e91','#c0a16d','#6b9491'][i],'#485e69','#d9dfd7']);
      poly([P(x-.68,1.1,z-2.25),P(x+.68,1.1,z-2.25),P(x+.6,1.4,z-2.1),P(x-.6,1.4,z-2.1)],'#29434e');
      line([P(x-.7,.4,z-2.25),P(x-.35,.4,z-2.25)],'#ef8d70',Math.max(2,50/(z+21)));line([P(x+.35,.4,z-2.25),P(x+.7,.4,z-2.25)],'#ef8d70',Math.max(2,50/(z+21)));
      label(bounds(bb),`car · 0.${92-i}`,colors.yellow);
    }
    for(let i=0;i<2;i++){
      const z=170-((t*(campus?7:11)+i*87)%184);if(z<1||z>150)continue;
      const x=i?3.8:-1.4;
      if(i===0){const ring=[];for(let a=0;a<9;a++){const angle=a*Math.PI*2/9,r=1+(a%3)*.18;ring.push(P(x+Math.cos(angle)*r,.03,z+Math.sin(angle)*1.8*r));}poly(ring,'#1a252a','#817c68',1);poly(ring.map(p=>[p[0]+1,p[1]+2]),'#1b292b');}
      else{const points=[];for(let a=0;a<9;a++)points.push(P(x+Math.sin(a*2.3)*.65,.04,z+a*.55-2));line(points,'#111c24',Math.max(1,w/(z+21)*.07));}
      label(bounds([P(x-1.6,0,z-2.4),P(x+1.6,0,z-2.4),P(x-1.6,0,z+2.4),P(x+1.6,0,z+2.4)]),i?'裂缝示意 · 0.83':'坑槽示意 · 0.89','#f5a66f');
    }
    if(campus){const sign=P(-10,5,34);c.fillStyle='#344d47';c.fillRect(sign[0]-w*.1,sign[1]-h*.04,w*.23,h*.07);c.fillStyle='#e4d98e';c.font=`${w/60}px "Microsoft YaHei"`;c.fillText('CQJTU · 南岸校区',sign[0]-w*.08,sign[1]+h*.005);}
    const sweep=(t%4)/4;line([[w*.2,h*.5+sweep*h*.4],[w*.8,h*.5+sweep*h*.4]],'#a1d8c344',1);
  }
  function intersection(t){
    c.fillStyle=weather==='night'?'#16282a':'#476456';c.fillRect(0,0,w,h);
    const scale=Math.min(w/100,h/63),P=(x,y,z)=>[w*.5+(x-z)*scale,h*.42+(x+z)*scale*.42-y*scale];
    poly([P(-55,0,-8),P(55,0,-8),P(55,0,8),P(-55,0,8)],'#45535b');
    poly([P(-8,0,-55),P(8,0,-55),P(8,0,55),P(-8,0,55)],'#45535b');
    for(const side of [-1,1])for(const axis of [0,1]){
      const coords=axis?[P(-55,.03,side*8),P(55,.03,side*8)]:[P(side*8,.03,-55),P(side*8,.03,55)];line(coords,'#bcc2ae',1.5);
      for(let i=-48;i<50;i+=9){if(Math.abs(i)<12)continue;line(axis?[P(i,.05,0),P(i+5,.05,0)]:[P(0,.05,i),P(0,.05,i+5)],'#d7bb5f',1.5);}
    }
    for(const x of [-1,1])for(const z of [-1,1]){
      box(P,x*25,z*25,18,17,6+(x===z?2:0),weather==='night'?['#30424d','#253742','#657577']:['#839b9d','#58767e','#b6c4bf'],true);
      for(let i=0;i<6;i++)poly([P(x*(9+i*.9),.1,z*11),P(x*(9.4+i*.9),.1,z*11),P(x*(9.4+i*.9),.1,z*15),P(x*(9+i*.9),.1,z*15)],'#d9dcc8');
      const pole=P(x*10,0,z*10),top=P(x*10,6,z*10);line([pole,top],'#7c918e',2);
      c.fillStyle='#203239';c.fillRect(top[0]-4,top[1]-9,8,17);c.fillStyle=(Math.floor(t/10)%2===(x===z?0:1))?'#65d99d':'#ec786c';c.beginPath();c.arc(top[0],top[1],2.5,0,7);c.fill();
    }
    const axisGreen=Math.floor(t/10)%2;
    for(let i=0;i<8;i++){
      const axis=i%2,dir=i%4<2?1:-1;let v=-55+((t*(axis===0?7:6)+i*17)%110);
      if(axis!==axisGreen&&v< -9)v=-11-(i%3)*5;
      const x=axis?v*dir:dir*3.5,z=axis?dir*3.5:v*dir;
      const points=box(P,x,z,axis?4.2:2,axis?2:4.2,1.5,[['#d2d4bf','#6f99a2','#bfaa75','#ac8f78'][i%4],'#486573','#dbe1cd']);
      poly([P(x-.6,1.52,z-.6),P(x+.6,1.52,z-.6),P(x+.6,1.52,z+.6),P(x-.6,1.52,z+.6)],'#2c4652');
      if(i<4)label(bounds(points),'car · 0.9'+(i%3),colors.yellow);
    }
    c.fillStyle='#d2dcd3';c.font=`${Math.max(12,w/70)}px "Microsoft YaHei"`;c.textAlign='left';c.fillText('固定路口 · 信号灯交替 / 交叉车流',w*.035,h*.9);
  }
  function draw(context,options){
    c=context;w=c.canvas.width;h=c.canvas.height;weather=options.weather||'day';
    const device=options.device;if(!device||device.source==='real'){
      c.fillStyle='#182831';c.fillRect(0,0,w,h);c.strokeStyle='#4c6570';c.lineWidth=2;c.strokeRect(w*.475,h*.24,w*.05,h*.34);c.fillStyle='#a5bac4';c.font=`${Math.max(12,w/40)}px "Microsoft YaHei"`;c.textAlign='center';c.fillText(device?.last_seen?'设备已连接 · 等待照片上传':'等待手机配对与照片上传',w*.5,h*.76);return;
    }
    const t=options.time||0,mode=options.mode==='auto'||!options.mode?(device.type==='fixed'?'intersection':device.id==='CQ-V04'?'campus':'patrol'):options.mode;
    c.save();c.clearRect(0,0,w,h);
    if(mode==='intersection')intersection(t);else patrol(t,mode==='campus');
    if(weather==='rain'){
      c.strokeStyle='#d6e7ec40';c.lineWidth=1;for(let i=0;i<45;i++){const x=(i*83+t*13)%w,y=(i*47+t*150)%h;c.beginPath();c.moveTo(x,y);c.lineTo(x-5,y+17);c.stroke();}
      c.fillStyle='#10202d22';c.fillRect(0,0,w,h);
    }
    const vignette=c.createRadialGradient(w*.5,h*.5,h*.2,w*.5,h*.5,w*.7);vignette.addColorStop(0,'#0000');vignette.addColorStop(1,'#07101899');c.fillStyle=vignette;c.fillRect(0,0,w,h);
    if(device.status!=='online'){c.fillStyle='#0b151aac';c.fillRect(0,0,w,h);c.fillStyle='#d2dcd7';c.textAlign='center';c.font=`${Math.max(12,w/40)}px "Microsoft YaHei"`;c.fillText(device.status==='offline'?'设备离线 · 循环采集已停止':'设备暂停 · 循环采集已停止',w/2,h/2);}
    c.restore();
  }
  return {draw};
})();
