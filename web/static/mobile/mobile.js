'use strict';
(()=>{
  const $=s=>document.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const names={online:'在线',offline:'离线',pending:'待接入',paused:'暂停'};
  const kinds={vehicle:'车辆检测',pothole:'道路坑槽',crack:'路面裂缝',device:'设备状态',report:'巡检照片'};
  const wanted=new URLSearchParams(location.search).get('device');
  let selected=/^PHONE-0[1-3]$/.test(wanted)?wanted:'PHONE-01',snapshot=null,first=true,markerKey='',tileErrors=0,local=null,online=true;
  const map=L.map('map',{zoomControl:false,scrollWheelZoom:false,minZoom:12,maxZoom:18,maxBounds:[[29.35,106.2],[29.9,107.05]],maxBoundsViscosity:.5}).setView([29.4903657,106.567229],15);
  map.attributionControl.addAttribution('© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · ODbL');
  const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,keepBuffer:1,updateWhenIdle:true}).addTo(map);
  const markers=new Map();
  async function localMap(){
    if(!local){const r=await fetch('../data/chongqing.geojson');if(!r.ok)throw new Error('map');local=L.geoJSON(await r.json(),{renderer:L.canvas(),interactive:false,style:f=>{
      const p=f.properties||{},water=!!(p.water||p.waterway||p.natural==='water');
      return {color:water?'#305b70':p.highway?'#8b8056':p.amenity?'#9a8e4c':'#455157',weight:p.highway?2:water?4:1,
        fillColor:water?'#244a5e':p.amenity?'#504d2d':p.building?'#29363c':'#273b31',fillOpacity:.65};}});}
    if(online){map.removeLayer(tiles);local.addTo(map);online=false;$('#map-source').textContent='本地校园矢量 · 离线可用';$('#map-style').setAttribute('aria-label','切换在线地图');}
  }
  tiles.on('tileerror',()=>{if(++tileErrors>=3)localMap().catch(()=>{$('#map-source').textContent='地图暂不可用 · 设备状态仍可查看';});});
  $('#map-style').addEventListener('click',()=>{if(online)localMap().catch(()=>{});else{if(local)map.removeLayer(local);tiles.addTo(map);online=true;tileErrors=0;$('#map-source').textContent='在线街道图';$('#map-style').setAttribute('aria-label','切换本地地图');}});
  $('#recenter').addEventListener('click',()=>map.setView([29.4903657,106.567229],15));
  const viewer=new WVLive.Viewer({base:'../api/v1/',image:$('#live-image'),visible:()=>!$('#live-card').hidden,onState:(m,text)=>{
    $('#live-status').textContent=m.state==='live'?(m.test_data?'TEST LIVE · 联调画面':'LIVE · 手机取景'):text;
    $('#live-fps').textContent=m.state==='live'?`${m.fps} FPS · ${m.width} × ${m.height}`:'最新帧 · 不存档';$('#live-empty').hidden=m.state==='live';
  }});
  function select(id,pan=true){selected=id;render();const d=snapshot?.devices.find(x=>x.id===id);if(pan&&d?.location_valid)map.panTo([d.lat,d.lon]);}
  function render(){
    if(!snapshot)return;
    const devices=snapshot.devices,device=devices.find(d=>d.id===selected)||devices[0];
    $('#online').innerHTML=snapshot.stats.online+'<small>/ 13</small>';$('#anomalies').textContent=snapshot.stats.anomalies;
    $('#live-count').innerHTML=devices.filter(d=>d.live?.state==='live').length+'<small>路</small>';
    const key=devices.map(d=>d.id+':'+d.status+':'+(d.id===selected)).join('|');
    if(key!==markerKey){
      markerKey=key;const sorted=[...devices].sort((a,b)=>(a.source==='real'?0:1)-(b.source==='real'?0:1));
      $('#devices').innerHTML=sorted.map(d=>`<button class="device-chip ${d.id===selected?'active':''}" data-device="${esc(d.id)}" aria-pressed="${d.id===selected}"><strong>${esc(d.id)}</strong><small><i class="dot ${d.status==='online'?'':'off'}"></i>${d.source==='sim'?'SIM · ':''}${names[d.status]}</small></button>`).join('');
    }
    for(const d of devices){
      const symbol=d.type==='mobile'?'↗':d.type==='phone'?'▯':'▣';
      const icon=L.divIcon({className:'device-marker',html:`<span class="marker-dot ${esc(d.type)} ${d.id===selected?'selected':''} ${!d.location_valid?'reserved':''} ${d.status==='offline'?'offline':''}">${symbol}</span>`,iconSize:[27,27],iconAnchor:[13,13]});
      if(!markers.has(d.id)){const m=L.marker([d.lat,d.lon],{icon,title:d.id+(d.location_valid?'':' · 预留位置，非实际GPS')}).addTo(map);m.on('click',()=>select(d.id,false));markers.set(d.id,m);}
      const m=markers.get(d.id);m.setLatLng([d.lat,d.lon]);m.setIcon(icon);m.setZIndexOffset(d.id===selected?1000:0);
    }
    $('#device-name').textContent=device.name||device.id;$('#device-source').textContent=device.id+' / '+(device.source==='sim'?'SIM · 模拟设备':'PHONE · 设备接入');
    $('#device-status').textContent=names[device.status];$('#device-status').className='status '+device.status;
    $('#device-location').textContent=device.location_valid?`${device.location} · ${device.lat.toFixed(5)}, ${device.lon.toFixed(5)}`:'尚无有效 GPS · 地图标记仅为预留位置';
    $('#device-fps').textContent=device.status==='online'&&device.fps!=null?device.fps.toFixed(1)+' FPS':'—';$('#device-battery').textContent=device.battery!=null?Math.round(device.battery)+'%':'—';
    $('#device-age').textContent=device.last_seen?Math.max(0,Math.round(Date.parse(snapshot.server_time)/1000-device.last_seen))+'s 前':'未接入';
    $('#live-card').hidden=device.source!=='real';$('#sim-note').hidden=device.source!=='sim';viewer.setDevice(device.source==='real'?device.id:null);
    $('#events').innerHTML=snapshot.events.length?snapshot.events.map(e=>`<div class="activity-row"><span class="event-icon">${e.kind==='device'?'◉':e.kind==='vehicle'?'↗':'◇'}</span><div><p>${esc(e.message)}</p><small>${e.source==='sim'?'SIM':'REAL'} · ${esc(e.device_id)} · ${kinds[e.kind]||'巡检事件'}</small></div><time>${new Date(e.timestamp*1000).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}</time></div>`).join(''):'<p class="muted">暂无巡检事件。</p>';
    $('#updated').textContent=new Date(snapshot.server_time).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'});
    if(first){first=false;if(device.location_valid)map.panTo([device.lat,device.lon]);}
  }
  $('#devices').addEventListener('click',e=>{const b=e.target.closest('[data-device]');if(b)select(b.dataset.device);});
  async function poll(){
    try{if(!document.hidden){const r=await fetch('../api/v1/mobile',{cache:'no-store',signal:AbortSignal.timeout(6000)});if(!r.ok)throw new Error('offline');snapshot=await r.json();render();$('#connection').textContent='已连接';$('#connection').className='connection online';}}
    catch{$('#connection').textContent='连接中断 · 重试中';$('#connection').className='connection';}
    finally{setTimeout(poll,document.hidden?10000:3000);}
  }
  poll();
})();
