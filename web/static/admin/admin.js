'use strict';
(()=>{
  const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)],esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const statuses={pending:'待复核',confirmed:'已确认',rejected:'已排除',resolved:'已处理'},categories={pothole:'坑槽',crack:'裂缝',vehicle:'车辆',sign:'交通标志',debris:'道路遗留物',plate:'车牌',behavior:'行为提示',observation:'巡检记录'},priorities={normal:'常规',important:'重点关注',urgent:'优先处理'};
  const labels={longitudinal_crack:'纵向裂缝',transverse_crack:'横向裂缝',alligator_crack:'龟裂',pothole:'坑洞',car:'汽车',bus:'公交车',truck:'货车'};
  let page=1,total=0,rows=[],selected=new Set(),active=null,generation=0,busy=false;
  const form=$('#filters'),dialog=$('#record-dialog');
  const source=r=>r.source==='sim'?'<span class="source">SIM · 模拟</span>':r.test_data?'<span class="source test">TEST · 联调</span>':'<span class="source real">REAL · 上报</span>';
  function toast(value){$('#toast').textContent=value;$('#toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').hidden=true,3500);}
  function query(){const q=new URLSearchParams();for(const [key,value] of new FormData(form)){if(String(value).trim())q.set(key,String(value).trim());}return q;}
  function selection(){
    $('#selected-count').textContent=`已选 ${selected.size} 条`;$$('[data-batch]').forEach(b=>b.disabled=!selected.size||busy);
    $('#select-all').checked=rows.length>0&&rows.every(r=>selected.has(r.id));$('#select-all').indeterminate=selected.size>0&&!$('#select-all').checked;
  }
  function links(q){
    const csv=new URLSearchParams(q);csv.set('format','csv');$('#csv').href='../api/v1/records/export?'+csv;
    const json=new URLSearchParams(q);json.set('format','json');$('#json').href='../api/v1/records/export?'+json;
    $('#report').href=$('#nav-report').href='../report/?'+q;
    const html=new URLSearchParams(q);html.set('format','html');$('#html').href='../api/v1/records/report?'+html;
  }
  async function load(){
    const ticket=++generation,q=query();links(q);const dataQ=new URLSearchParams(q);dataQ.set('page',page);dataQ.set('page_size',20);
    $('#connection').textContent='同步中';
    try{
      const [a,b]=await Promise.all([fetch('../api/v1/records?'+dataQ,{cache:'no-store'}),fetch('../api/v1/records/summary?'+q,{cache:'no-store'})]);
      const data=await a.json(),summary=await b.json();if(ticket!==generation)return;if(!a.ok||!b.ok)throw new Error(data.error||summary.error||'读取失败');
      rows=data.rows;total=data.total;selected=new Set([...selected].filter(id=>rows.some(r=>r.id===id)));
      $('#total').textContent=summary.total;$('#pending').textContent=$('#nav-pending').textContent=summary.pending;$('#attention').textContent=summary.attention;
      const count=k=>summary.sources.find(r=>r.name===k)?.count||0;
      $('#source-count').innerHTML=`<b>${count('sim')}</b> SIM　<b>${count('test')+count('real')}</b> 上报`;
      $('#result-note').textContent=`筛选结果 ${total} 条 · 采集数据与人工复核独立保存`;
      $('#rows').innerHTML=rows.length?rows.map(r=>`<tr><td><input type="checkbox" data-select="${r.id}" aria-label="选择 ${esc(r.id)}" ${selected.has(r.id)?'checked':''}></td><td><div class="cell-title"><span class="kind-icon ${r.category}">${r.category==='vehicle'?'↗':r.category==='pothole'?'◇':'▧'}</span><div><strong>${categories[r.category]}${r.status==='pending'?' · 待复核':''}</strong><small class="subtitle">${esc(r.title)}</small></div></div></td><td><strong>${esc(r.location)}</strong><small>${esc(r.device_id)}</small></td><td>${source(r)}</td><td class="cell-time">${esc(r.observed_at.slice(0,10))}<br><small>${esc(r.observed_at.slice(11,19))}</small></td><td><strong>${r.objects} 个目标${r.confidence!=null?' · '+Math.round(r.confidence*100)+'%':''}</strong><small class="subtitle">${esc(r.model_id)}</small></td><td><span class="badge ${r.status}">${statuses[r.status]}</span>${r.priority!=='normal'?`<span class="priority">◇ ${priorities[r.priority]}</span>`:''}${r.tags.length?`<small class="subtitle">${esc(r.tags.join(' · '))}</small>`:''}</td><td><button class="view-record" data-open="${r.id}">查看 / 复核 ↗</button></td></tr>`).join(''):'<tr><td colspan="8" class="empty">当前筛选下没有记录。可重置筛选，或补充一组 SIM 演示样本。</td></tr>';
      const pages=Math.max(1,Math.ceil(total/20));$('#page').textContent=`${page} / ${pages}`;$('#page-note').textContent=`共 ${total} 条 · 每页 20 条`;
      $('#prev').disabled=page<=1;$('#next').disabled=page>=pages;$('#connection').textContent='● 数据已同步';selection();
      history.replaceState(null,'','?'+q);
    }catch(e){$('#connection').textContent='连接暂不可用';toast(e.message);}
  }
  async function post(path,body){const r=await fetch('../api/v1/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const d=await r.json();if(!r.ok)throw new Error(d.error||'操作失败');return d;}
  async function batch(action){
    if(!selected.size||busy)return;busy=true;selection();
    try{const ids=[...selected],expected_versions=Object.fromEntries(rows.filter(r=>selected.has(r.id)).map(r=>[r.id,r.version]));
      await post('records/review',{ids,expected_versions,...(action==='important'?{priority:'important'}:{status:action})});selected.clear();toast(`已更新 ${ids.length} 条记录`);await load();
    }catch(e){toast(e.message);}finally{busy=false;selection();}
  }
  const scene=`<div class="synthetic-scene"><svg viewBox="0 0 640 320" role="img" aria-label="模拟道路结构化条目示意"><rect width="640" height="320" fill="#23323a"/><path d="M0 320L240 20h160l240 300" fill="#414a50"/><path d="M320 320V20" stroke="#b3a56a" stroke-width="3" stroke-dasharray="30 22"/><path d="M130 320L270 20M510 320L370 20" stroke="#92989a" stroke-width="3"/><ellipse cx="380" cy="230" rx="48" ry="16" fill="#20252b"/><rect x="320" y="190" width="125" height="68" rx="5" stroke="#f0cc43" stroke-width="2" fill="none"/><path d="M380 200l-18 20 25 12-10 15" fill="none" stroke="#d1b768" stroke-width="2"/></svg><span>SIM · 程序模拟示意，非实拍</span></div>`;
  async function open(id){
    try{const r=await fetch('../api/v1/records/'+encodeURIComponent(id),{cache:'no-store'});const d=await r.json();if(!r.ok)throw new Error(d.error||'条目已被清理');active=d;
      $('#record-source').outerHTML=source(d).replace('class="source','id="record-source" class="source');$('#record-title').textContent=d.title;$('#record-id').textContent=d.id;
      $('#record-image').innerHTML=d.source==='sim'?scene:d.report_available?`<img src="../api/v1/reports/${esc(d.report_id)}/image" alt="该条目的原始巡检照片">`:'<div class="image-empty">原图已按保留配额清理<br>结构化条目仍可查看和导出。</div>';
      $('#record-facts').innerHTML=[['采集点',d.location],['采集时间',d.observed_at],['设备与模型',d.device_id+' / '+d.model_id],['WGS84位置',d.lat!=null?`${d.lat.toFixed(6)}, ${d.lon.toFixed(6)}`:'未提供有效GPS'],['采集备注',d.capture_context?.note||'未填写'],['服务器接收',d.received_at]].map(([a,b])=>`<div><span>${a}</span><strong>${esc(b)}</strong></div>`).join('');
      $('#targets').innerHTML=d.detections.length?d.detections.slice(0,100).map(t=>`<tr><td>${esc(labels[t.label]||t.label||'未提供标签')}<br><small>ID ${esc(t.class_id??'模拟')}</small></td><td>${Number.isFinite(t.confidence)?Math.round(t.confidence*100)+'%':'—'}</td><td>${esc(Array.isArray(t.bbox_xywh_normalized)?t.bbox_xywh_normalized.map(v=>Number(v).toFixed(3)).join(', '):'未提供')}</td></tr>`).join(''):'<tr><td colspan="3">本帧没有检测目标；仍保留采集条目。</td></tr>';
      $('#record-json').textContent=JSON.stringify({model_id:d.model_id,inference:d.inference,capture_context:d.capture_context,capture:d.capture,targets_total:d.objects,source:d.source,test_data:d.test_data},null,2);
      $('#review-status').value=d.status;$('#review-priority').value=d.priority;$('#review-tags').value=d.tags.join('，');$('#review-note').value=d.note;
      $('#review-history').innerHTML=d.reviews.length?d.reviews.map(log=>`<div>${esc(log.changed_at)}<br>${statuses[log.before.status]} → ${statuses[log.after.status]} · ${priorities[log.after.priority]}${log.after.note?'<br>'+esc(log.after.note):''}</div>`).join(''):'暂无复核操作。';
      if(!dialog.open)dialog.showModal();
    }catch(e){toast(e.message);}
  }
  form.addEventListener('submit',e=>{e.preventDefault();page=1;selected.clear();load();});
  $('#clear-filters').addEventListener('click',()=>{form.reset();page=1;selected.clear();load();});$('#refresh').addEventListener('click',load);
  $('#rows').addEventListener('click',e=>{const b=e.target.closest('[data-open]');if(b)open(b.dataset.open);});
  $('#rows').addEventListener('change',e=>{const id=e.target.dataset.select;if(id){e.target.checked?selected.add(id):selected.delete(id);selection();}});
  $('#select-all').addEventListener('change',e=>{selected=e.target.checked?new Set(rows.map(r=>r.id)):new Set();$$('[data-select]').forEach(c=>c.checked=selected.has(c.dataset.select));selection();});
  $$('[data-batch]').forEach(b=>b.addEventListener('click',()=>batch(b.dataset.batch)));
  $('#prev').addEventListener('click',()=>{page=Math.max(1,page-1);selected.clear();load();});$('#next').addEventListener('click',()=>{page++;selected.clear();load();});
  $('#close-record').addEventListener('click',()=>dialog.close());
  $('#review-form').addEventListener('submit',async e=>{e.preventDefault();if(!active)return;$('#save-review').disabled=true;
    try{await post('records/review',{ids:[active.id],expected_versions:{[active.id]:active.version},status:$('#review-status').value,priority:$('#review-priority').value,tags:$('#review-tags').value.split(/[,，]/).map(s=>s.trim()).filter(Boolean),note:$('#review-note').value});toast('复核与标记已保存');await open(active.id);await load();}catch(error){toast(error.message);}finally{$('#save-review').disabled=false;}});
  $('#demo-seed').addEventListener('click',async()=>{const b=$('#demo-seed');b.disabled=true;try{await post('simulation',{action:'reset'});form.reset();page=1;selected.clear();await load();toast('已补充24条SIM演示样本，既有台账与手机记录保留');}catch(e){toast(e.message);}finally{b.disabled=false;}});
  $$('[data-quick]').forEach(b=>b.addEventListener('click',()=>{form.reset();if(b.dataset.quick==='pending')form.elements.status.value='pending';if(b.dataset.quick==='urgent')form.elements.priority.value='urgent';$$('[data-quick]').forEach(x=>x.classList.toggle('active',x===b));page=1;selected.clear();load();}));
  const initial=new URLSearchParams(location.search);for(const [key,value] of initial){if(form.elements.namedItem(key))form.elements.namedItem(key).value=value;}
  load();setInterval(()=>{if(!document.hidden&&!dialog.open&&!selected.size&&!busy)load();},15000);
})();
