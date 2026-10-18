'use strict';
/* One frame request at a time, 10 FPS viewing ceiling; no history or token URLs. */
window.WVLive=(()=>{
  const labels={idle:'尚未开启 LIVE',live:'LIVE',stale:'画面已过期',expired:'直播已断流',stopped:'直播已停止',unavailable:'连接暂不可用',paused:'预览已暂停'};
  class Viewer{
    constructor({image,onState,visible=()=>true,base='api/v1/'}){
      Object.assign(this,{image,onState,visible,base,device:null,generation:0,etag:'',url:null,meta:null,lastMeta:0});
      this.timer=setTimeout(()=>this.tick(),0);
    }
    setDevice(id){
      id=/^PHONE-0[1-3]$/.test(id)?id:null;
      if(id===this.device)return;
      this.device=id;this.generation++;this.abort?.abort();this.etag='';this.meta=null;this.lastMeta=0;this.clear();
    }
    clear(){clearTimeout(this.frameTimer);this.image.hidden=true;this.image.removeAttribute('src');if(this.url){URL.revokeObjectURL(this.url);this.url=null;}}
    state(meta){this.meta=meta;if(meta.state!=='live'){this.clear();this.etag='';}this.onState?.(meta,labels[meta.state]||'等待直播');}
    async tick(){
      const start=performance.now(),generation=this.generation,id=this.device;
      let delay=1000;
      try{
        if(!id||document.hidden||!this.visible()){
          this.clear();this.etag='';this.lastMeta=0;
          if(id)this.onState?.({state:'paused',fps:0},labels.paused);
          return;
        }
        this.abort=new AbortController();const timeout=setTimeout(()=>this.abort?.abort(),4500);
        try{
          const endpoint=this.base+'devices/'+id+'/live';
          if(!this.meta||start-this.lastMeta>=800){
            const r=await fetch(endpoint,{cache:'no-store',signal:this.abort.signal});if(!r.ok)throw new Error('LIVE status');
            const meta=await r.json();if(generation!==this.generation)return;this.lastMeta=performance.now();this.state(meta);
          }
          if(this.meta?.state!=='live')return;
          delay=100;
          const r=await fetch(endpoint+'/frame.jpg',{cache:'no-store',headers:this.etag?{'If-None-Match':this.etag}:{},signal:this.abort.signal});
          if(generation!==this.generation)return;
          if(r.status===304)return;
          if(r.status===410){this.etag='';this.lastMeta=0;this.state(await r.json());return;}
          if(!r.ok)throw new Error('LIVE image');
          const blob=await r.blob();if(generation!==this.generation)return;
          const url=URL.createObjectURL(blob),old=this.url;this.url=url;this.etag=r.headers.get('ETag')||'';
          this.image.src=url;this.image.hidden=false;
          try{await this.image.decode();}finally{if(old)URL.revokeObjectURL(old);}
          if(generation!==this.generation)return;
          clearTimeout(this.frameTimer);
          this.frameTimer=setTimeout(()=>{this.lastMeta=0;this.state({...this.meta,state:'stale',fps:0});},2000);
        }finally{clearTimeout(timeout);}
      }catch{
        if(generation===this.generation){this.etag='';this.lastMeta=0;this.state({state:'unavailable',fps:0});}
      }finally{this.timer=setTimeout(()=>this.tick(),Math.max(0,delay-(performance.now()-start)));}
    }
  }
  let preview=null,dialogViewer=null,selected=null,baseLabel='',baseCorner='';
  const dialog=document.querySelector('#live-dialog');
  if(dialog){
    const label=document.querySelector('#preview-label'),corner=document.querySelector('#preview-corner');
    preview=new Viewer({image:document.querySelector('#live-preview'),visible:()=>!dialog.open&&!!document.querySelector('#preview').getClientRects().length,
      onState:(meta,text)=>{label.textContent=meta.state==='live'?`${meta.test_data?'TEST LIVE':'LIVE'} · ${meta.fps} FPS`:baseLabel;corner.textContent=meta.state==='live'?'实时取景 · 点击展开 ↗':`${text} · 点击查看`;}});
    dialogViewer=new Viewer({image:document.querySelector('#live-image'),visible:()=>dialog.open,
      onState:(meta,text)=>{document.querySelector('#live-state').textContent=meta.state==='live'?`${meta.test_data?'TEST LIVE':'LIVE'} · ${meta.fps} FPS`:text;
        document.querySelector('#live-empty').hidden=meta.state==='live';document.querySelector('#live-detail').textContent=meta.state==='live'?`${meta.width} × ${meta.height} · 仅内存最新帧`:'在已配对 App 中点击 LIVE 开启。超过 2 秒无新帧即移除画面。';}});
    document.querySelector('#close-live').addEventListener('click',()=>dialog.close());
    dialog.addEventListener('close',()=>dialogViewer.setDevice(null));
  }
  function select(device){
    if(!preview)return;selected=device?.source==='real'?device:null;
    baseLabel=document.querySelector('#preview-label').textContent;baseCorner=document.querySelector('#preview-corner').textContent;
    preview.setDevice(selected?.id);if(selected&&preview.meta)preview.onState(preview.meta,labels[preview.meta.state]);
  }
  function open(id){if(!dialog)return;document.querySelector('#live-title').textContent=id+' · 实时取景';dialog.showModal();dialogViewer.setDevice(id);}
  return {Viewer,select,open,labels};
})();
