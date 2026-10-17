'use strict';
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = name => `<svg aria-hidden="true"><use href="icons.svg?v=20261017-v2-4#${name}"/></svg>`;
const typeNames = {fixed:'固定哨点',mobile:'移动巡检',phone:'真实手机'};
const typeIcons = {fixed:'camera',mobile:'car',phone:'phone'};
const statusNames = {online:'在线',offline:'离线',pending:'待接入',paused:'暂停'};
const eventNames = {pothole:'道路坑槽',crack:'路面裂缝',vehicle:'车辆检测',device:'设备状态',report:'巡检照片'};
const eventColors = {pothole:'#ffd43b',crack:'#bcb9a5',vehicle:'#5d9e8b',device:'#617181',report:'#7c9fcc'};
const state = {snapshot:null, selected:(new URLSearchParams(location.search).get('device')||'CQ-V01'), deviceFilter:'all',eventFilter:'all',deviceEvents:null,query:'',mapMode:0,markers:new Map(),eventMarkers:[],routes:[],polylines:[],eventKey:'',showEvents:true, connected:false};
let toastTimer;
function toast(message) { $('#toast').textContent=message; $('#toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').hidden=true,3500); }
function localTime(seconds, options={hour:'2-digit',minute:'2-digit',second:'2-digit'}) {return new Date(seconds*1000).toLocaleTimeString('zh-CN',{...options,timeZone:'Asia/Shanghai',hour12:false});}
function shortTime(seconds) {return localTime(seconds,{hour:'2-digit',minute:'2-digit'});}
function sourceTag(source) {return `<span class="source-tag ${source==='real'?'real':''}">${source==='real'?'REAL':'SIM'}</span>`;}
function statusTag(status) {return `<span class="status ${esc(status)}">${esc(statusNames[status])}</span>`;}

const map = L.map('map',{zoomControl:false,attributionControl:true,preferCanvas:true,minZoom:12,maxZoom:18,maxBounds:[[29.35,106.2],[29.9,107.05]],maxBoundsViscosity:.7}).setView([29.4903657,106.567229],15);
map.attributionControl.setPrefix('');
map.attributionControl.addAttribution('© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> · ODbL');
L.control.scale({position:'bottomleft',imperial:false,maxWidth:75}).addTo(map);
const roadRenderer = L.canvas({padding:.2});
const baseLayers=L.layerGroup().addTo(map);
map.createPane('districts'); map.getPane('districts').style.zIndex=390; map.getPane('districts').style.pointerEvents='none';
function mapLabel(text,lat,lon,className) {L.marker([lat,lon],{pane:'districts',interactive:false,keyboard:false,icon:L.divIcon({className,html:esc(text),iconSize:[100,20],iconAnchor:[50,10]})}).addTo(baseLayers);}
mapLabel('重庆交通大学 · 南岸校区',29.4903657,106.567229,'campus-label');
for(const [text,lat,lon] of [['学府大道',29.499,106.57],['汇龙路',29.483,106.576],['交大轨道站',29.49143,106.56911],['南岸区',29.512,106.595],['南山',29.509,106.588]])mapLabel(text,lat,lon,'place-label');
mapLabel('长 江',29.501,106.549,'river-label');
let onlineTiles=null, onlineError=0;
function setMapMode(mode) {
  if(onlineTiles){map.removeLayer(onlineTiles);onlineTiles=null;}
  map.getPane('tilePane').classList.remove('online-dark');
  state.mapMode=mode;
  if(mode){
    onlineError=0;
    onlineTiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,keepBuffer:1,updateWhenIdle:true,attribution:''});
    onlineTiles.on('tileerror',()=>{if(++onlineError>=4){setMapMode(0);toast('在线街道图暂不可用，已恢复本地矢量底图');}});
    if(mode===1)map.getPane('tilePane').classList.add('online-dark');
    onlineTiles.addTo(map);
  }
  $('#map-source').textContent=['本地矢量 · 无需地图密钥','在线街道图 · 深色','在线街道图 · 原色'][mode];
  $('#layers').title=['切换在线深色街道图','切换在线原色街道图','切换本地矢量底图'][mode];
}
async function loadGeography() {
  try{
    const response=await fetch('data/chongqing.geojson'); if(!response.ok)throw new Error('map'); const geo=await response.json();
    const waters=geo.features.filter(f=>f.geometry.type==='Polygon');
    const roads=geo.features.filter(f=>f.geometry.type==='LineString');
    L.geoJSON(waters,{renderer:roadRenderer,interactive:false,style:f=>f.properties.building?{fillColor:'#26373e',color:'#45565b',weight:.6,fillOpacity:.8}:f.properties.amenity?{fillColor:'#736b27',color:'#b2a05a',weight:1.5,fillOpacity:.16}:f.properties.leisure?{fillColor:'#29483a',color:'#385c46',weight:1,fillOpacity:.7}:{fillColor:'#173a49',color:'#285166',weight:1,fillOpacity:.95}}).addTo(baseLayers);
    L.geoJSON(roads,{renderer:roadRenderer,interactive:false,smoothFactor:1.5,style:f=>{
      const h=f.properties.highway;
      if(f.properties.waterway)return {color:'#244959',weight:5,opacity:.6};
      const major=['motorway','trunk','primary'].includes(h),secondary=h==='secondary';
      return {color:major?'#8b7944':secondary?'#605e48':'#354249',weight:major?2:secondary?1.25:.65,opacity:major?.9:secondary?.8:.65};
    }}).addTo(baseLayers);
  }catch{toast('本地地图资源加载失败，可尝试切换在线街道图');$('#map-source').textContent='地图资源不可用 · 设备定位仍可交互';}
  try{
    const response=await fetch('data/routes.json');if(!response.ok)throw new Error('routes');state.routes=await response.json();
    for(const route of state.routes){
      const coords=route.coordinates.map(p=>[p[1],p[0]]);
      const line=L.polyline(coords,{color:'#698b76',weight:3,opacity:.4,interactive:false}).addTo(map);
      state.polylines.push({route,line});
    }
    renderRouteSelection();
  }catch{toast('巡检路线资源不可用，请刷新重试');}
}
loadGeography();

function deviceIcon(d) {return L.divIcon({className:'device-map-icon',html:`<div class="device-pin ${esc(d.type)} ${esc(d.status)} ${d.id===state.selected?'selected':''}">${icon(typeIcons[d.type])}<span class="pin-label">${esc(d.id)}${d.source==='real'&&!d.location_valid?(d.last_seen?' · 待定位':' · 预留'):''}</span></div>`,iconSize:[33,50],iconAnchor:[16,16]});}
function renderMarkers() {
  if(!state.snapshot)return;
  for(const d of state.snapshot.devices){
    const version=[d.status,d.id===state.selected,d.location_valid].join('-');
    let entry=state.markers.get(d.id);
    const latlng=L.latLng(d.lat,d.lon);
    if(!entry){
      const marker=L.marker(latlng,{icon:deviceIcon(d),title:`${d.id} · ${d.source==='sim'?'模拟设备':d.location_valid?'真实设备':'预留位置，非实际 GPS'}`,zIndexOffset:d.source==='real'?0:100}).addTo(map);
      marker.on('click',()=>selectDevice(d.id,false));
      marker.bindTooltip('',{direction:'top',offset:[0,-15],className:'map-tooltip'});
      entry={marker,version,from:latlng,to:latlng,start:performance.now()};state.markers.set(d.id,entry);
    }
    if(version!==entry.version){entry.marker.setIcon(deviceIcon(d));entry.version=version;}
    entry.marker.setTooltipContent(`${esc(d.name)} · ${esc(statusNames[d.status])}${d.location_valid?'':' · 预留坐标'}`);
    entry.from=entry.marker.getLatLng();entry.to=latlng;entry.start=performance.now();
    if(d.type!=='mobile'||!state.snapshot.simulation.running)entry.marker.setLatLng(latlng);
    entry.marker.setZIndexOffset(d.id===state.selected?1000:d.source==='real'?0:100);
  }
  renderRouteSelection(); renderEventMarkers();
}
function renderRouteSelection(){
  const selected=state.snapshot?.devices.find(d=>d.id===state.selected);
  for(const {route,line} of state.polylines){const active=selected?.route_id===route.id;line.setStyle({color:active?'#76dcb0':'#698b76',opacity:active?.85:.32,weight:active?4:2.5});}
}
let lastFrame=0;
function animate(now){
  requestAnimationFrame(animate);
  if(document.hidden||now-lastFrame<(window.WVPrefs?.quality==='eco'?100:50))return; lastFrame=now;
  if(state.snapshot?.simulation.running && state.connected){
    for(const entry of state.markers.values()){
      const t=Math.min(1,(now-entry.start)/2600);
      if(t<1)entry.marker.setLatLng([entry.from.lat+(entry.to.lat-entry.from.lat)*t,entry.from.lng+(entry.to.lng-entry.from.lng)*t]);
    }
  }
  if(['overview','devices','map'].includes(document.body.dataset.view))drawPreview(now);
  if(window.WVUpgrade)window.WVUpgrade.animate(now);
  const followed=state.snapshot?.devices.find(d=>d.id===state.selected);
  if(window.WVPrefs?.follow&&followed?.location_valid&&followed.type==='mobile'&&now-(state.lastFollow||0)>2500){state.lastFollow=now;map.panTo(state.markers.get(followed.id).marker.getLatLng(),{animate:false});}
}
requestAnimationFrame(animate);
function renderEventMarkers(){
  for(const m of state.eventMarkers)map.removeLayer(m);state.eventMarkers=[];
  if(!state.showEvents||!state.snapshot)return;
  const anomalies=state.snapshot.events.filter(e=>['pothole','crack'].includes(e.kind)).slice(0,8);
  for(const event of anomalies){
    const m=L.marker([event.lat,event.lon],{icon:L.divIcon({className:'device-map-icon',html:`<div class="event-pin">${icon('alert')}</div>`,iconSize:[18,18],iconAnchor:[9,9]}),zIndexOffset:50,title:`${event.source==='sim'?'模拟':'真实'} · ${event.title}`}).addTo(map);
    m.bindTooltip(`${event.source==='sim'?'SIM 模拟':'REAL 真实'} · ${esc(event.title)} · ${esc(event.device_id)}`,{className:'map-tooltip',direction:'top'});
    m.on('click',()=>{selectDevice(event.device_id,false);toast(`${event.source==='sim'?'模拟事件':'真实上报'}：${event.message}`);});
    state.eventMarkers.push(m);
  }
}

function renderDeviceList(){
  if(!state.snapshot)return;
  const devices=state.snapshot.devices.filter(d=>(state.deviceFilter==='all'||d.type===state.deviceFilter)&&[d.id,d.name,d.location,d.district].join(' ').toLowerCase().includes(state.query.toLowerCase()));
  $('#device-list').innerHTML=devices.length?devices.map(d=>`<button class="device-row ${d.id===state.selected?'active':''}" data-device="${esc(d.id)}" aria-pressed="${d.id===state.selected}"><span class="device-thumb ${esc(d.type)}">${icon(typeIcons[d.type])}</span><span class="device-copy"><span class="device-line"><strong>${esc(d.id)}</strong>${statusTag(d.status)}</span><span class="device-location">${esc(d.location)}</span><span class="device-tags">${sourceTag(d.source)}<span class="type-tag">${typeNames[d.type]}</span>${d.type==='mobile'?`<span>· ${Math.round(d.battery)}%</span>`:''}</span></span></button>`).join(''):'<div class="empty">没有匹配的设备<br>试试其他名称或位置</div>';
}
function selectDevice(id,pan=true){
  state.focusDone=true;
  state.selected=id;renderDeviceList();renderMarkers();renderDetail();
  const d=state.snapshot?.devices.find(d=>d.id===id);
  if(pan&&d)map.panTo([d.lat,d.lon],{animate:!reducedMotion});
}
function renderEvents(force=false){
  if(!state.snapshot)return;
  const events=state.snapshot.events.filter(e=>(state.eventFilter==='all'||e.kind===state.eventFilter)&&(!state.deviceEvents||e.device_id===state.deviceEvents));
  const key=[state.eventFilter,state.deviceEvents,...events.map(e=>e.id)].join(',');
  if(!force&&key===state.eventKey)return;state.eventKey=key;
  $('#events-panel h2').textContent=state.deviceEvents?`${state.deviceEvents} 记录`:'实时事件';
  $('#event-list').innerHTML=events.length?events.slice(0,60).map(e=>`<button class="event-row" data-event-device="${esc(e.device_id)}" ${e.report_id?`data-report="${esc(e.report_id)}"`:''}><span class="event-thumb ${esc(e.kind)} ${e.source==='real'?'real':''}">${icon({pothole:'road',crack:'activity',vehicle:'car',device:'wifi',report:'photo'}[e.kind])}</span><span class="event-copy"><span class="event-meta"><time>${localTime(e.timestamp)}</time><span class="event-badge ${esc(e.kind)}">${eventNames[e.kind]}</span></span><h3>${esc(e.message)}</h3><p><span>${esc(e.device_id)} · ${e.source==='sim'?'模拟':'真实'}</span><span>${e.confidence!==null?`置信度 ${Math.round(e.confidence*100)}%`:e.kind==='report'?'照片已保存':'设备状态'}</span></p></span></button>`).join(''):'<div class="empty">暂无匹配事件<br>设备巡检记录会在这里出现</div>';
  $('#event-count').textContent=`最近 ${events.length} 条`;
}
function renderCharts(){
  const s=state.snapshot; const counts=s.distribution; const total=Object.values(counts).reduce((a,b)=>a+b,0);
  $('#distribution-total').textContent=total;
  let angle=0; const pieces=[];
  $('#distribution-legend').innerHTML=Object.keys(eventNames).map(kind=>{
    const start=angle;angle+=(counts[kind]||0)/Math.max(1,total)*360;pieces.push(`${eventColors[kind]} ${start}deg ${angle}deg`);
    return `<div><i style="background:${eventColors[kind]}"></i><span>${{pothole:'坑槽',crack:'裂缝',vehicle:'车辆',device:'状态',report:'照片'}[kind]}</span><b>${counts[kind]||0}</b></div>`;
  }).join('');
  $('#donut').style.background=total?`conic-gradient(${pieces.join(',')})`:'#27313a';
  const max=Math.max(4,...s.trend.map(b=>b.sim+b.real)); const top=Math.ceil(max/4)*4;
  let svg='<svg viewBox="0 0 320 150" role="img" aria-label="检测趋势，黄色模拟事件，绿色真实事件">';
  for(let i=0;i<5;i++){const y=12+i*27;svg+=`<line x1="27" y1="${y}" x2="311" y2="${y}"/><text x="20" y="${y+3}" text-anchor="end">${top*(4-i)/4}</text>`;}
  for(let i=0;i<6;i++){const b=s.trend[i],x=40+i*45,sim=b.sim/top*108,real=b.real/top*108;svg+=`<rect class="bar-sim" x="${x}" y="${120-sim}" width="16" height="${sim}" rx="2"><title>${shortTime(b.timestamp)} 模拟 ${b.sim} 条</title></rect><rect class="bar-real" x="${x+18}" y="${120-real}" width="7" height="${real}" rx="2"><title>真实 ${b.real} 条</title></rect><text x="${x+10}" y="140" text-anchor="middle">${shortTime(b.timestamp)}</text>`;}
  $('#trend-chart').innerHTML=svg+'</svg>';
  $('#last-update').textContent=`更新于 ${localTime(Date.parse(s.server_time)/1000)}`;
}
function renderDetail(){
  const d=state.snapshot?.devices.find(d=>d.id===state.selected);if(!d)return;
  $('#detail-id').textContent=d.id;
  $('#detail-status').outerHTML=`<span id="detail-status" class="status ${d.status}">${statusNames[d.status]}</span>`;
  const latest=state.snapshot.reports?.find(r=>r.device_id===d.id);
  $('#preview-label').textContent=d.source==='sim'?'SIM · 合成示意画面':latest?.test_data?'TEST · 联调上传':latest?'REAL · 手机上传':'PHONE · 等待采集照片';
  $('#preview-corner').textContent=d.source==='real'?(latest?'最近上传 · 点击查看':'尚无巡检照片'):d.status==='online'?'点击展开循环场景 ↗':`${statusNames[d.status]} · 停止模拟画面`;
  $('#real-preview').hidden=!latest||d.source!=='real';
  if(latest&&d.source==='real'){const src='api/v1/reports/'+latest.id+'/image';if($('#real-preview').getAttribute('src')!==src)$('#real-preview').src=src;}
  $('#preview-canvas').hidden=!$('#real-preview').hidden;
  $('#detail-location').textContent=d.location_valid?`${d.location} · ${d.lat.toFixed(4)}, ${d.lon.toFixed(4)}`:'预留地图位置 · 尚未收到真实 GPS';
  const age=d.last_seen?Math.max(0,Math.round(Date.parse(state.snapshot.server_time)/1000-d.last_seen)):null;
  $('#detail-metrics').innerHTML=[['帧率',d.status==='online'&&d.fps!==null?`${d.fps} FPS`:'—'],['电量',d.battery!==null?`${Math.round(d.battery)}%`:'—'],['速度',d.status==='online'&&d.speed!==null?`${d.speed} km/h`:'—'],['最近心跳',age!==null?`${age}s 前`:'未接入']].map(([label,value])=>`<div>${label}<strong>${value}</strong></div>`).join('');
  $('#detail-history').title=`${d.name} · ${d.location_valid?`${d.lat.toFixed(5)}, ${d.lon.toFixed(5)}`:'预留位置，非真实 GPS'} · 查看巡检记录`;
  $('#api-config').textContent=state.snapshot.ingestion.configured?'已有设备连接凭据，可使用已配对的 App 上报。':'暂无已配对手机，可在设置中生成一次性配对码。';
}
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)').matches;
const ctx=$('#preview-canvas').getContext('2d');
function drawPreview(now){
  const d=state.snapshot?.devices.find(d=>d.id===state.selected);
  window.WVScene.draw(ctx,{device:d,time:(window.WVUpgrade?.time(now,d)||0),weather:state.snapshot?.simulation.scenario==='night'?'night':'day',mode:'auto'});
}

function render(){
  state.receivedAt=performance.now();
  const s=state.snapshot;
  $('#stat-total').textContent=s.stats.total;
  $('#stat-online').innerHTML=`${s.stats.online}<em>/ 13</em>`;
  $('#stat-alerts').textContent=s.stats.anomalies;
  $('#stat-distance').innerHTML=`${s.stats.distance_km.toFixed(2)}<em>km</em>`;
  $('#online-note').textContent=`${s.stats.real_connected} 台真实设备已接入`;
  const peak=Math.max(1,...s.trend.map(b=>b.sim+b.real));
  $('.mini-spark').textContent=s.trend.map(b=>'▁▂▃▄▅▆▇█'[Math.min(7,Math.floor((b.sim+b.real)/peak*7))]).join('');
  $('#pause').innerHTML=icon(s.simulation.running?'pause':'play');
  $('#pause').setAttribute('aria-label',s.simulation.running?'暂停模拟':'继续模拟');
  $('#pause').title=s.simulation.running?'暂停模拟':'继续模拟';
  $('#scenario').value=s.simulation.scenario;
  $('#map-pause').hidden=s.simulation.running;
  $('#device-footer').textContent=s.simulation.running?'模拟引擎运行中 · 3 秒刷新':'模拟已暂停 · 真实接口保持接收';
  renderDeviceList();renderMarkers();renderEvents();renderCharts();renderDetail();
  const requested=new URLSearchParams(location.search).get('device');
  const target=s.devices.find(d=>d.id===requested);
  if(!state.focusDone&&target?.location_valid){state.focusDone=true;map.panTo([target.lat,target.lon],{animate:!reducedMotion});}
  window.WVUpgrade?.render();
}
async function refresh(){
  try{
    const response=await fetch('api/v1/snapshot',{cache:'no-store',signal:AbortSignal.timeout(8000)});
    if(!response.ok)throw new Error('HTTP '+response.status);
    state.snapshot=await response.json();state.connected=true;
    $('#connection').innerHTML='<i></i> 服务已连接';$('#connection').classList.remove('disconnected');$('#error-banner').hidden=true;
    render();
  }catch{
    state.connected=false;$('#connection').innerHTML='<i></i> 正在重连';$('#connection').classList.add('disconnected');$('#error-banner').hidden=false;
  }finally{setTimeout(refresh,document.hidden?12000:3000);}
}
async function control(action,extra={}){
  for(const el of [$('#pause'),$('#reset'),$('#scenario')])el.disabled=true;
  try{
    const response=await fetch('api/v1/simulation',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,...extra}),signal:AbortSignal.timeout(8000)});
    if(!response.ok)throw new Error('control');
    const snapshot=await fetch('api/v1/snapshot',{cache:'no-store'});if(!snapshot.ok)throw new Error('refresh');state.snapshot=await snapshot.json();render();
    toast({pause:'模拟已暂停，真实设备接口保持接收',resume:'模拟巡检已继续',reset:'已重置模拟数据，真实设备与事件已保留',scenario:'已切换演示场景，所有访问者同步更新'}[action]);
  }catch{toast('操作未成功，请检查服务连接后重试');}
  finally{for(const el of [$('#pause'),$('#reset'),$('#scenario')])el.disabled=false;}
}
function setView(view){
  document.body.dataset.view=view;
  $$('.nav').forEach(b=>{b.classList.toggle('active',b.dataset.view===view);b.setAttribute('aria-current',b.dataset.view===view?'page':'false');});
  setTimeout(()=>map.invalidateSize({pan:false}),80);
  if(view!=='events'&&state.deviceEvents){state.deviceEvents=null;renderEvents(true);}
}
$$('.nav').forEach(button=>button.addEventListener('click',()=>setView(button.dataset.view)));
$$('[data-filter]').forEach(button=>button.addEventListener('click',()=>{state.deviceFilter=button.dataset.filter;$$('[data-filter]').forEach(b=>b.classList.toggle('selected',b===button));renderDeviceList();}));
$$('[data-event-filter]').forEach(button=>button.addEventListener('click',()=>{state.eventFilter=button.dataset.eventFilter;if(state.eventFilter==='all')state.deviceEvents=null;$$('[data-event-filter]').forEach(b=>b.classList.toggle('selected',b===button));renderEvents(true);}));
$('#search').addEventListener('input',e=>{state.query=e.target.value;renderDeviceList();});
$('#device-list').addEventListener('click',e=>{const row=e.target.closest('[data-device]');if(row)selectDevice(row.dataset.device);});
$('#event-list').addEventListener('click',e=>{const row=e.target.closest('[data-event-device]');if(row){if(row.dataset.report){window.WVUpgrade?.openReport(row.dataset.report);return;}selectDevice(row.dataset.eventDevice);if(document.body.dataset.view==='events')toast(`已定位 ${row.dataset.eventDevice}，地图高亮显示`);}});
$('#pause').addEventListener('click',()=>state.snapshot&&control(state.snapshot.simulation.running?'pause':'resume'));
$('#reset').addEventListener('click',()=>control('reset'));
$('#scenario').addEventListener('change',e=>control('scenario',{scenario:e.target.value}));
$('#zoom-in').addEventListener('click',()=>map.zoomIn());$('#zoom-out').addEventListener('click',()=>map.zoomOut());
$('#recenter').addEventListener('click',()=>map.setView([29.4903657,106.567229],15,{animate:!reducedMotion}));
$('#layers').addEventListener('click',()=>setMapMode((state.mapMode+1)%3));
$('#show-events').addEventListener('change',e=>{state.showEvents=e.target.checked;renderEventMarkers();});
$('#detail-history').addEventListener('click',()=>{setView('events');state.deviceEvents=state.selected;state.eventFilter='all';$$('[data-event-filter]').forEach(b=>b.classList.toggle('selected',b.dataset.eventFilter==='all'));renderEvents(true);});
$('#api-help').addEventListener('click',()=>$('#api-dialog').showModal());$('#close-dialog').addEventListener('click',()=>$('#api-dialog').close());
$('#api-dialog').addEventListener('click',e=>{if(e.target===$('#api-dialog')){const b=e.target.getBoundingClientRect();if(e.clientX<b.left||e.clientX>b.right||e.clientY<b.top||e.clientY>b.bottom)e.target.close();}});
document.addEventListener('keydown',e=>{if(e.key==='/'&&!['INPUT','TEXTAREA'].includes(document.activeElement.tagName)){e.preventDefault();if(['events','analytics'].includes(document.body.dataset.view))setView('overview');$('#search').focus();}});
new ResizeObserver(()=>map.invalidateSize({pan:false})).observe($('#map-panel'));
refresh();
