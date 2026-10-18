'use strict';
window.WVPrefs={quality:'balanced',follow:false,map:1,events:true};
try{Object.assign(window.WVPrefs,JSON.parse(localStorage.getItem('wv-demo-preferences')||'{}'));}catch{}
window.WVUpgrade=(()=>{
  const scenarios=[
    ['normal','校园日常','沿学府大道缓慢巡检，少量事件，适合日常查看。','road','NORMAL'],
    ['busy','高峰车流','缩短事件间隔，持续展示车辆检测与事件更新。','car','RUSH HOUR'],
    ['roadwork','病害发现','提高坑槽与裂缝模拟事件比例，演示巡检发现。','alert','ROAD CARE'],
    ['offline','断线演练','固定哨点离线，其他设备继续运行；切回日常后恢复。','wifi','CONNECTION'],
    ['night','夜间巡检','深色合成场景与亮窗建筑，演示夜间道路采集。','camera','NIGHT SHIFT']
  ];
  let reportKey='',scenePaused=false,sceneFrozen=0,sceneStart=performance.now(),sceneClock=0,lastSceneFrame=0;
  const sceneTimes=new Map();
  const bytes=n=>n<1024?`${n} B`:n<1048576?`${(n/1024).toFixed(1)} KiB`:`${(n/1048576).toFixed(1)} MiB`;
  function prefs(){try{localStorage.setItem('wv-demo-preferences',JSON.stringify(window.WVPrefs));}catch{}}
  function settingsTab(name){
    $$('[data-setting]').forEach(b=>b.classList.toggle('active',b.dataset.setting===name));
    $$('[data-setting-page]').forEach(p=>p.hidden=p.dataset.settingPage!==name);
    if(name==='storage')storage();
  }
  function openSettings(name='scenes'){$('#settings-dialog').showModal();settingsTab(name);render();}
  async function post(path,body){
    const response=await fetch('api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
    const result=await response.json();if(!response.ok)throw new Error(result.error||'请求失败');return result;
  }
  async function storage(){
    try{
      const response=await fetch('api/v1/settings',{cache:'no-store'});if(!response.ok)throw new Error('读取失败');const {storage:s}=await response.json();
      $('#storage-stats').innerHTML=[['数据库占用',bytes(s.database_bytes)],['照片空间',bytes(s.image_bytes)],['采集照片',`${s.reports} / ${s.max_reports}`],['设备事件',`${s.events} / 5000`]].map(([a,b])=>`<div><span>${a}</span><strong>${b}</strong></div>`).join('');
      $('#storage-fill').style.width=Math.min(100,s.image_bytes/s.max_image_bytes*100)+'%';
    }catch{toast('暂时无法读取存储状态');}
  }
  async function maintenance(action){
    $('#clear-data').disabled=$('#compact-data').disabled=true;
    try{
      const result=await post('maintenance',{action,keep_pairing:$('#keep-pairing').checked});
      $('#storage-result').textContent=`${action==='clear'?'演示数据库已清空':'过期与超额数据已清理'}，回收 ${bytes(result.freed_bytes)}。`;
      const response=await fetch('api/v1/snapshot',{cache:'no-store'});if(response.ok){state.snapshot=await response.json();render();}
      await storage();toast(action==='clear'?'演示数据已清空，模拟引擎从零开始':'清理与压缩已完成');
    }catch(error){toast(error.message);}
    finally{$('#clear-data').disabled=$('#compact-data').disabled=false;}
  }
  function render(){
    const active=state.snapshot?.simulation.scenario||'normal';
    $('#scenario-grid').innerHTML=scenarios.map(([key,name,desc,ico,en])=>`<button class="scenario-card ${active===key?'active':''}" data-scenario="${key}" aria-pressed="${active===key}"><span class="scenario-icon">${icon(ico)}</span><span><small>${en}</small><strong>${name}</strong><p>${desc}</p></span><i>${active===key?'●':'↗'}</i></button>`).join('');
    const reports=state.snapshot?.reports||[];
    $('#report-total').textContent=reports.length;
    const key=reports.map(r=>r.id).join(',');
    if(key!==reportKey||!$('#report-grid').children.length){
      reportKey=key;
      $('#report-grid').innerHTML=reports.length?reports.map(r=>{
        const loc=r.location||{},valid=Number.isFinite(loc.latitude)&&Number.isFinite(loc.longitude);
        const label=loc.mock?'测试定位':loc.status==='stale'?'过期位置':valid?'WGS84':'未定位';
        return `<button class="report-card" data-report="${esc(r.id)}"><div class="report-thumb"><img src="api/v1/reports/${esc(r.id)}/image" alt="${esc(r.device_id)} 上传的巡检照片" loading="lazy"><span>${r.test_data?'TEST · 联调数据':'REAL · 设备上传'}</span></div><div class="report-card-copy"><h3>${esc(r.device_id)} <small>${localTime(Date.parse(r.received_at)/1000)}</small></h3><p>${r.objects} 个同帧目标 <span>${label} · ${bytes(r.bytes)}</span></p><small>${esc(r.model)} · ${esc(r.event_id.slice(0,12))}…</small></div></button>`;
      }).join(''):'<div class="empty report-empty"><span>'+icon('photo')+'</span><h3>下一张巡检照片，从这里开始。</h3><p>在设置里生成配对码，连接 App 后拍照并一键上传。<br>真实设备上传会显示在这里，模拟画面不会写入档案。</p><button class="primary-button" id="empty-connect">连接手机</button></div>';
      $('#empty-connect')?.addEventListener('click',()=>openSettings('connect'));
    }
  }
  async function openReport(id){
    try{
      const response=await fetch('api/v1/reports/'+encodeURIComponent(id),{cache:'no-store'});if(!response.ok)throw new Error('该报告已被清理或暂时不可用');
      const {report}=await response.json();$('#report-title').textContent='巡检照片 · '+report.event_id.slice(0,8);
      $('#report-image').src='api/v1/reports/'+encodeURIComponent(id)+'/image';
      const location=report.location||{};
      $('#report-info').innerHTML=[['接收状态','服务器已接收 · 原图与原始 JSON 未改动'],['采集时间',report.captured_at||'—'],['定位质量',location.mock?'测试/模拟定位':location.status||'未定位'],['位置',Number.isFinite(location.latitude)?`${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`:'未采集 GPS'],['图像 SHA-256',report.image.sha256]].map(([a,b])=>`<div><span>${a}</span><strong>${esc(b)}</strong></div>`).join('');
      $('#report-json').textContent=JSON.stringify(report,null,2);$('#report-dialog').showModal();
      const inference=report.inference||{},roadLabels={longitudinal_crack:'纵向裂缝',transverse_crack:'横向裂缝',alligator_crack:'龟裂',pothole:'坑洞'};
      const detections=(Array.isArray(inference.detections)?inference.detections:[]).filter(d=>d&&typeof d.label==='string');
      $('#report-detections').innerHTML=`<p class="live-note">模型：${esc(inference.model_id||'未提供')} · ${esc(inference.mode||'未提供模式')} · 以报告 label 为准</p>`+
        (detections.length?detections.slice(0,50).map(d=>`<span class="detection-label">${esc(roadLabels[d.label]||d.label.slice(0,80))}${Number.isFinite(d.confidence)?` <b>${Math.round(d.confidence*100)}%</b>`:''}</span>`).join(''):'<p class="live-note">此帧没有上报检测目标。</p>');
    }catch(e){toast(e.message);}
  }
  function openPreview(){
    const d=state.snapshot?.devices.find(d=>d.id===state.selected);if(!d)return;
    if(d.source==='real'){
      window.WVLive.open(d.id);return;
    }
    $('#scene-title').textContent=d.name+' · '+d.id;$('#scene-device').textContent=d.id+' · SIM / 程序合成';
    $('#scene-weather').value=state.snapshot.simulation.scenario==='night'?'night':'day';
    scenePaused=false;$('#scene-pause').textContent='暂停画面';sceneStart=performance.now();sceneClock=0;
    $('#scene-dialog').showModal();
  }
  function time(now,d){
    if(d?.status==='online')sceneTimes.set(d.id,(state.snapshot?.simulation.elapsed||0)+(state.snapshot?.simulation.running?Math.min(4,(now-(state.receivedAt||now))/1000):0));
    return sceneTimes.get(d?.id)||0;
  }
  function animate(now){
    if(!$('#scene-dialog').open||now-lastSceneFrame<(window.WVPrefs.quality==='eco'?100:50))return;
    lastSceneFrame=now;
    const d=state.snapshot?.devices.find(d=>d.id===state.selected);if(!d)return;
    if(!scenePaused&&state.connected&&state.snapshot.simulation.running&&d.status==='online')sceneClock+=(now-sceneStart)/1000;
    sceneStart=now;
    window.WVScene.draw($('#scene-canvas').getContext('2d'),{device:d,time:scenePaused?sceneFrozen:sceneClock,mode:$('#scene-mode').value,weather:$('#scene-weather').value});
    $('#scene-state').textContent=scenePaused?'画面已暂停 · 仅影响本窗口':!state.snapshot.simulation.running?'模拟引擎已暂停':'循环演示 · 非真实摄像头';
  }
  $('#settings').addEventListener('click',()=>openSettings());
  $('#connect-phone').addEventListener('click',()=>openSettings('connect'));
  $$('[data-setting]').forEach(b=>b.addEventListener('click',()=>settingsTab(b.dataset.setting)));
  $$('[data-close]').forEach(b=>b.addEventListener('click',()=>$('#'+b.dataset.close).close()));
  $('#scenario-grid').addEventListener('click',e=>{const b=e.target.closest('[data-scenario]');if(b)control('scenario',{scenario:b.dataset.scenario});});
  $('#restart-demo').addEventListener('click',()=>control('reset'));
  $('#generate-pair').addEventListener('click',async()=>{
    $('#generate-pair').disabled=true;
    try{const r=await post('pairing',{device_id:$('#pair-device').value});$('#pair-code').textContent=r.code;$('#pair-result').hidden=false;$('#pair-expires').textContent=`${r.device_id} · 有效至 ${localTime(Date.parse(r.expires_at)/1000)}`;}
    catch(e){toast(e.message);}finally{$('#generate-pair').disabled=false;}
  });
  $('#compact-data').addEventListener('click',()=>maintenance('compact'));$('#clear-data').addEventListener('click',()=>maintenance('clear'));
  $('#quality').value=window.WVPrefs.quality;$('#quality').addEventListener('change',e=>{window.WVPrefs.quality=e.target.value;prefs();});
  $('#follow-device').checked=window.WVPrefs.follow;$('#follow-device').addEventListener('change',e=>{window.WVPrefs.follow=e.target.checked;prefs();});
  $('#map-preference').value=String(window.WVPrefs.map);$('#map-preference').addEventListener('change',e=>{window.WVPrefs.map=Number(e.target.value);setMapMode(window.WVPrefs.map);prefs();});
  $('#events-preference').checked=window.WVPrefs.events;$('#events-preference').addEventListener('change',e=>{window.WVPrefs.events=e.target.checked;state.showEvents=e.target.checked;$('#show-events').checked=e.target.checked;renderEventMarkers();prefs();});
  $('#preview').addEventListener('click',openPreview);$('#preview').addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openPreview();}});
  $('#scene-pause').addEventListener('click',()=>{scenePaused=!scenePaused;if(scenePaused)sceneFrozen=sceneClock;sceneStart=performance.now();$('#scene-pause').textContent=scenePaused?'继续画面':'暂停画面';});
  $('#report-grid').addEventListener('click',e=>{const card=e.target.closest('[data-report]');if(card)openReport(card.dataset.report);});
  state.showEvents=window.WVPrefs.events;$('#show-events').checked=state.showEvents;
  if([1,2].includes(window.WVPrefs.map))setMapMode(window.WVPrefs.map);
  render();
  return {render,animate,openReport,time};
})();
