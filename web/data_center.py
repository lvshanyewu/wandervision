"""Course-demo ledger, review and exports. Standard library, same SQLite connection."""
from collections import Counter
from datetime import datetime, timezone, timedelta
import csv
import html
import io
import json
import re
import secrets

CN=timezone(timedelta(hours=8))
CATEGORIES={'pothole':'坑槽','crack':'裂缝','vehicle':'车辆','sign':'交通标志','debris':'道路遗留物','plate':'车牌','behavior':'行为提示','observation':'巡检记录'}
STATUSES={'pending':'待复核','confirmed':'已确认','rejected':'已排除','resolved':'已处理'}
PRIORITIES={'normal':'常规','important':'重点关注','urgent':'优先处理'}


class ReviewConflict(ValueError):
    pass


def obj(value):return value if isinstance(value,dict) else {}
def stamp(value):return datetime.fromtimestamp(value,CN).isoformat(timespec='seconds')
def text(value,size=240):return str(value or '')[:size]


class DataCenter:
    def __init__(self,db,clock,lock):
        self.db,self.clock,self.lock=db,clock,lock
        db.executescript('''CREATE TABLE IF NOT EXISTS records (
          id TEXT PRIMARY KEY,source TEXT NOT NULL,test_data INTEGER NOT NULL,device_id TEXT NOT NULL,
          event_id TEXT NOT NULL,observed REAL NOT NULL,received REAL NOT NULL,category TEXT NOT NULL,
          severity TEXT NOT NULL,confidence REAL,objects INTEGER NOT NULL,location TEXT NOT NULL,
          lat REAL,lon REAL,model_id TEXT NOT NULL,title TEXT NOT NULL,payload TEXT NOT NULL,report_id TEXT,
          status TEXT NOT NULL DEFAULT 'pending',priority TEXT NOT NULL DEFAULT 'normal',
          tags TEXT NOT NULL DEFAULT '[]',note TEXT NOT NULL DEFAULT '',updated REAL NOT NULL,version INTEGER NOT NULL DEFAULT 1,
          UNIQUE(source,device_id,event_id));
          CREATE INDEX IF NOT EXISTS record_observed ON records(observed DESC);
          CREATE INDEX IF NOT EXISTS record_review ON records(status,category,source);
          CREATE TABLE IF NOT EXISTS review_log(id INTEGER PRIMARY KEY,record_id TEXT,changed REAL,before_json TEXT,after_json TEXT);''')
        self.columns=[c[0] for c in db.execute('SELECT r.*,0 AS report_available FROM records r LIMIT 0').description]

    def insert(self,row):
        with self.lock:
            existing=self.db.execute('SELECT id FROM records WHERE source=? AND device_id=? AND event_id=?',
                (row['source'],row['device_id'],row['event_id'])).fetchone()
            if existing:return existing[0]
            row.update(id='rec-'+secrets.token_hex(10),updated=self.clock())
            keys=list(row)
            self.db.execute('INSERT INTO records ('+','.join(keys)+') VALUES ('+','.join('?' for _ in keys)+')',[row[k] for k in keys])
            self.prune();self.db.commit();return row['id']

    def event(self,event,device=None):
        if event['kind']=='device':return None
        kind=event['kind'];confidence=event.get('confidence');sim=event['source']=='sim'
        count=int(re.search(r'\d+',event['message'])[0]) if kind=='vehicle' and re.search(r'\d+',event['message']) else 1
        label={'pothole':'pothole','crack':'longitudinal_crack','vehicle':'car'}.get(kind,kind)
        detections=[{'class_id':None,'label':label,'confidence':confidence,'bbox_xywh_normalized':([round(.08+(i%4)*.22,3),round(min(.78,.18+(i//4)*.18),3),.16,.13] if kind=='vehicle' else [.47,.6,.2,.16])} for i in range(min(count,30))]
        return self.insert(dict(source=event['source'],test_data=0,device_id=event['device_id'],event_id=str(event['id']),
            observed=event['timestamp'],received=self.clock(),category=kind if kind in CATEGORIES else 'observation',
            severity=event.get('severity','info'),confidence=confidence,objects=count,location=text(event.get('location'),160),
            lat=event.get('lat'),lon=event.get('lon'),model_id='simulation-demo' if sim else 'device-event',
            title=text(event.get('message'),240),report_id=None,payload=json.dumps({'detections':detections,
                'capture_context':{'site_name':event.get('location',''),'note':'程序生成的课堂模拟条目' if sim else ''},
                'device':{'type':obj(device).get('type')},'simulated':sim},ensure_ascii=False)))

    def report(self,device_id,report,report_id,received):
        inf,loc,source=obj(report.get('inference')),obj(report.get('location')),obj(report.get('source'))
        raw=inf.get('detections',[]);detections=[d for d in raw if isinstance(d,dict)] if isinstance(raw,list) else []
        labels=[text(d.get('label'),80).lower() for d in detections];model=text(inf.get('model_id',report.get('model')),100)
        context=obj(report.get('capture_context'));site=text(context.get('site_name'),80).strip()
        category='observation'
        if any('pothole' in label for label in labels):category='pothole'
        elif any('crack' in label for label in labels):category='crack'
        elif obj(report.get('experimental_analysis')).get('events'):category='behavior'
        elif labels and any(k in model.lower() for k in ['garbage','trash','litter','taco']):category='debris'
        elif any('plate' in label for label in labels):category='plate'
        elif labels and (any(k in model.lower() for k in ['sign','tt100k','gtsdb']) or 'traffic sign' in labels or 'stop sign' in labels):category='sign'
        elif any(label in ['car','bus','truck','motorcycle','bicycle','train'] for label in labels):category='vehicle'
        scores=[float(d['confidence']) for d in detections if type(d.get('confidence')) in (int,float) and 0<=d['confidence']<=1]
        test=loc.get('mock') is True or source.get('test_data') is True or bool(re.search('emulator|gphone|generic|sdk',str(source.get('device_model','')),re.I))
        latitude,longitude=loc.get('latitude'),loc.get('longitude')
        valid=type(latitude) in (int,float) and type(longitude) in (int,float)
        capture={k:report[k] for k in ['source','camera','location','plate_recognition','experimental_analysis'] if k in report}
        payload={'detections':detections,'total_detections':len(raw),'inference':{k:v for k,v in inf.items() if k!='detections'},
            'capture':capture,'capture_context':{'site_name':site,'note':text(context.get('note'),240)}}
        title=(CATEGORIES[category]+'识别线索' if category!='observation' else '巡检照片与数据条目')+' · '+str(len(raw))+' 个目标'
        observed=received
        try:
            parsed=datetime.fromisoformat(str(report.get('captured_at','')).replace('Z','+00:00'))
            if parsed.tzinfo and 0<=parsed.timestamp()<=received+300:observed=parsed.timestamp()
        except (ValueError,OverflowError):pass
        return self.insert(dict(source='real',test_data=int(test),device_id=device_id,event_id=report['event_id'],observed=observed,
            received=received,category=category,severity='high' if category=='pothole' else 'medium' if category in ['crack','debris','behavior'] else 'info',
            confidence=max(scores) if scores else None,objects=len(raw),location=site or ('设备GPS采集点' if valid else '未命名采集点 · 无有效GPS'),
            lat=latitude if valid else None,lon=longitude if valid else None,model_id=model,title=title,report_id=report_id,
            payload=json.dumps(payload,ensure_ascii=False)))

    def prune(self):
        cutoff=self.clock()-30*86400
        self.db.execute('DELETE FROM records WHERE received<? OR id NOT IN (SELECT id FROM records ORDER BY received DESC LIMIT 3000)',(cutoff,))
        self.db.execute('DELETE FROM review_log WHERE changed<? OR id NOT IN (SELECT id FROM review_log ORDER BY id DESC LIMIT 600)',(cutoff,))

    def where(self,filters):
        clauses=[];args=[]
        for key,allowed in [('status',STATUSES),('priority',PRIORITIES),('category',CATEGORIES),('source',{'sim':1,'real':1,'test':1})]:
            value=filters.get(key,'')
            if value in ['', 'all',None]:continue
            if value not in allowed:raise ValueError('Invalid '+key+' filter')
            if key=='source' and value=='test':clauses.append('r.test_data=1');continue
            clauses.append('r.'+key+'=?');args.append(value)
        q=text(filters.get('q'),100).strip()
        if q:
            clauses.append('(r.id LIKE ? OR r.device_id LIKE ? OR r.location LIKE ? OR r.title LIKE ? OR r.note LIKE ? OR r.tags LIKE ?)')
            args.extend(['%'+q+'%']*6)
        for key,operator in [('start','>='),('end','<')]:
            if not filters.get(key):continue
            try:
                dt=datetime.fromisoformat(filters[key]);dt=dt.replace(tzinfo=CN) if dt.tzinfo is None else dt
                if key=='end' and len(filters[key])==10:dt+=timedelta(days=1)
                value=dt.timestamp()
            except (ValueError,OverflowError):raise ValueError('Invalid date filter') from None
            clauses.append('r.observed'+operator+'?');args.append(value)
        return (' WHERE '+' AND '.join(clauses) if clauses else ''),args

    def rows(self,filters,limit=20,offset=0):
        where,args=self.where(filters)
        return self.db.execute('SELECT r.*, CASE WHEN p.id IS NULL THEN 0 ELSE 1 END AS report_available FROM records r LEFT JOIN reports p ON r.report_id=p.id'+where+' ORDER BY r.observed DESC,r.id DESC LIMIT ? OFFSET ?',args+[limit,offset])

    def decode(self,row):
        result=dict(zip(self.columns,row))
        result.update(json.loads(result.pop('payload')));result['tags']=json.loads(result['tags']);result['test_data']=bool(result['test_data'])
        result['observed_at']=stamp(result['observed']);result['received_at']=stamp(result['received']);return result

    def listing(self,filters):
        try:page=max(1,int(filters.get('page',1)));size=min(100,max(1,int(filters.get('page_size',20))))
        except (ValueError,TypeError):raise ValueError('Invalid page') from None
        with self.lock:
            where,args=self.where(filters);total=self.db.execute('SELECT COUNT(*) FROM records r'+where,args).fetchone()[0]
            return {'rows':[self.decode(r) for r in self.rows(filters,size,(page-1)*size)],'total':total,'page':page,'page_size':size}

    def detail(self,record_id):
        with self.lock:
            row=self.db.execute('SELECT r.*,CASE WHEN p.id IS NULL THEN 0 ELSE 1 END AS report_available FROM records r LEFT JOIN reports p ON r.report_id=p.id WHERE r.id=?',(record_id,)).fetchone()
            if row is None:return None
            result=self.decode(row)
            result['reviews']=[{'changed_at':stamp(t),'before':json.loads(a),'after':json.loads(b)} for t,a,b in self.db.execute('SELECT changed,before_json,after_json FROM review_log WHERE record_id=? ORDER BY id DESC LIMIT 20',(record_id,))]
            return result

    def review(self,body):
        if not isinstance(body,dict) or not isinstance(body.get('ids'),list):raise ValueError('ids array required')
        ids=list(dict.fromkeys(body['ids'])) if all(isinstance(i,str) for i in body['ids']) else []
        if not 1<=len(ids)<=100:raise ValueError('Review 1..100 records')
        changes={k:body[k] for k in ['status','priority','tags','note'] if k in body}
        if not changes:raise ValueError('Review changes required')
        if 'status' in changes and changes['status'] not in STATUSES:raise ValueError('Invalid status')
        if 'priority' in changes and changes['priority'] not in PRIORITIES:raise ValueError('Invalid priority')
        if 'note' in changes and (not isinstance(changes['note'],str) or len(changes['note'])>600):raise ValueError('Note limited to 600 characters')
        if 'tags' in changes:
            tags=changes['tags']
            if not isinstance(tags,list) or len(tags)>8 or any(not isinstance(t,str) or len(t)>24 for t in tags):raise ValueError('At most 8 tags, 24 characters each')
            changes['tags']=list(dict.fromkeys(t.strip() for t in tags if t.strip()))
        expected=body.get('expected_versions',{})
        if not isinstance(expected,dict):raise ValueError('Invalid versions')
        with self.lock:
            rows=[self.detail(i) for i in ids]
            if any(r is None for r in rows):raise ValueError('Record no longer exists')
            if any(i in expected and expected[i]!=r['version'] for i,r in zip(ids,rows)):raise ReviewConflict('数据已被更新，请刷新后再提交')
            try:
                for row in rows:
                    before={k:row[k] for k in ['status','priority','tags','note']};after=before|changes
                    self.db.execute('UPDATE records SET status=?,priority=?,tags=?,note=?,updated=?,version=version+1 WHERE id=?',
                        (after['status'],after['priority'],json.dumps(after['tags'],ensure_ascii=False),after['note'],self.clock(),row['id']))
                    self.db.execute('INSERT INTO review_log(record_id,changed,before_json,after_json) VALUES (?,?,?,?)',
                        (row['id'],self.clock(),json.dumps(before,ensure_ascii=False),json.dumps(after,ensure_ascii=False)))
                self.prune();self.db.commit()
            except Exception:self.db.rollback();raise
            return {'ok':True,'updated':len(rows)}

    def summary(self,filters=None):
        filters=filters or {};where,args=self.where(filters)
        with self.lock:
            sql=' FROM records r'+where
            total,pending,attention,unlocated=self.db.execute("SELECT COUNT(*),COALESCE(SUM(status='pending'),0),COALESCE(SUM(status IN ('pending','confirmed') AND (severity IN ('high','medium') OR priority!='normal')),0),COALESCE(SUM(lat IS NULL),0)"+sql,args).fetchone()
            def group(field):return [{'name':k,'count':n} for k,n in self.db.execute('SELECT '+field+',COUNT(*)'+sql+' GROUP BY '+field+' ORDER BY COUNT(*) DESC',args)]
            categories,statuses,sources=group('category'),group('status'),group("CASE WHEN source='sim' THEN 'sim' WHEN test_data=1 THEN 'test' ELSE 'real' END")
            areas=group('location')[:8];trend=group("date(observed,'unixepoch','+8 hours')");trend=sorted(trend,key=lambda r:r['name'])[-14:]
            extra=" AND " if where else ' WHERE '
            issues=[dict(zip(['id','title','device_id','location','source','test_data','category','status','priority'],r)) for r in self.db.execute(
                "SELECT id,title,device_id,location,source,test_data,category,status,priority"+sql+extra+"status IN ('pending','confirmed') AND (severity IN ('high','medium') OR priority!='normal') ORDER BY CASE priority WHEN 'urgent' THEN 0 WHEN 'important' THEN 1 ELSE 2 END,observed DESC LIMIT 5",args)]
            insights=[f'当前筛选共 {total} 条记录，{pending} 条等待人工复核，{attention} 条列入关注线索。']
            if areas:insights.append(f'条目较集中的采集点为“{areas[0]["name"]}”（{areas[0]["count"]} 条），可优先核对重复上报和现场情况。')
            if unlocated:insights.append(f'{unlocated} 条未提供有效GPS；保留采集点说明，不使用预留坐标冒充定位。')
            insights.append('模拟、测试和设备上报分别统计；框数是同帧目标数量，不等于独立病害数量。')
            return {'total':total,'pending':pending,'attention':attention,'unlocated':unlocated,'categories':categories,'statuses':statuses,'sources':sources,
                'areas':areas,'trend':trend,'issues':issues,'insights':insights,'generated_at':stamp(self.clock()),'filters':{k:v for k,v in filters.items() if k not in ['page','page_size','format']}}

    def export(self,filters,format):
        if format not in ['csv','json']:raise ValueError('format must be csv or json')
        def safe(value):return "'"+value if isinstance(value,str) and value.startswith(('=','+','-','@','\t','\r')) else value
        with self.lock:
            if format=='json':yield ('{"generated_at":'+json.dumps(stamp(self.clock()))+',"rows":[').encode()
            else:
                yield b'\xef\xbb\xbf'
                s=io.StringIO(newline='');csv.writer(s).writerow(['数据编号','来源','采集时间','设备','采集点','纬度','经度','类别','模型','目标数','最高置信度','审核状态','关注级别','标记','复核备注','目标详情JSON','采集信息JSON']);yield s.getvalue().encode()
            first=True
            for raw in self.rows(filters,3000):
                r=self.decode(raw)
                if format=='json':yield (('' if first else ',')+json.dumps(r,ensure_ascii=False,allow_nan=False)).encode();first=False
                else:
                    source='SIM 模拟' if r['source']=='sim' else 'TEST 联调' if r['test_data'] else 'REAL 上报'
                    values=[r['id'],source,r['observed_at'],r['device_id'],r['location'],r['lat'],r['lon'],CATEGORIES[r['category']],r['model_id'],r['objects'],r['confidence'],STATUSES[r['status']],PRIORITIES[r['priority']],';'.join(r['tags']),r['note'],json.dumps(r['detections'],ensure_ascii=False),json.dumps(r.get('capture_context',{}),ensure_ascii=False)]
                    s=io.StringIO(newline='');csv.writer(s).writerow([safe(v) for v in values]);yield s.getvalue().encode()
            if format=='json':yield b']}'

    def report_html(self,filters):
        summary=self.summary(filters);esc=lambda v:html.escape(str(v))
        def bars(items,names):
            maximum=max([r['count'] for r in items]+[1])
            return ''.join(f'<div class="bar-row"><span>{esc(names.get(r["name"],r["name"]))}</span><div><i style="width:{r["count"]/maximum*100:.1f}%"></i></div><b>{r["count"]}</b></div>' for r in items) or '<p>暂无数据</p>'
        trend=summary['trend'];maximum=max([r['count'] for r in trend]+[1]);n=max(len(trend),1)
        svg='<svg viewBox="0 0 600 150" role="img" aria-label="按采集日期统计条目数量">'+''.join(f'<rect x="{i*580/n+10:.1f}" y="{115-r["count"]/maximum*90:.1f}" width="{max(5,450/n):.1f}" height="{r["count"]/maximum*90:.1f}" rx="4" fill="#e6bb25"/><text x="{i*580/n+10:.1f}" y="135" font-size="10">{esc(r["name"][5:])}</text><text x="{i*580/n+10:.1f}" y="{107-r["count"]/maximum*90:.1f}" font-size="11">{r["count"]}</text>' for i,r in enumerate(trend))+'</svg>'
        issues=''.join(f'<tr><td>{esc(r["device_id"])}</td><td>{esc(r["location"])}</td><td>{esc(r["title"])}</td><td>{STATUSES[r["status"]]}</td><td>{"SIM" if r["source"]=="sim" else "TEST" if r["test_data"] else "REAL"}</td></tr>' for r in summary['issues']) or '<tr><td colspan="5">当前没有需关注条目。</td></tr>'
        with self.lock:rows=[self.decode(r) for r in self.rows(filters,60)]
        detail=''.join(f'<tr><td>{esc(r["observed_at"][5:16])}</td><td>{esc(r["device_id"])}</td><td>{esc(r["location"])}</td><td>{CATEGORIES[r["category"]]}</td><td>{r["objects"]}</td><td>{STATUSES[r["status"]]}</td></tr>' for r in rows)
        return f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>wandervision 巡检数据可视化报告</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eff1f4;color:#202933;font:14px/1.7 "Segoe UI","Microsoft YaHei",sans-serif}}main{{max-width:1000px;margin:30px auto;background:white;padding:48px;border-radius:22px}}header{{border-bottom:4px solid #e6bb25;padding-bottom:26px}}small{{color:#788491;letter-spacing:1px}}h1{{font-size:30px;margin:8px 0}}h2{{font-size:18px;margin:0 0 20px}}.muted{{color:#73808d}}.kpis{{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:28px 0}}.kpis div{{background:#f5f6f7;padding:18px;border-radius:14px}}.kpis b{{display:block;font-size:30px}}.grid{{display:grid;grid-template-columns:1fr 1fr;gap:28px}}section{{margin:30px 0;break-inside:avoid}}.bar-row{{display:grid;grid-template-columns:95px 1fr 30px;gap:10px;align-items:center;font-size:12px;margin:12px 0}}.bar-row>div{{height:11px;background:#f0f1f2;border-radius:5px}}i{{display:block;background:#e6bb25;height:100%;border-radius:5px}}svg{{width:100%;max-height:180px}}table{{width:100%;border-collapse:collapse;font-size:11px}}th,td{{text-align:left;padding:10px 8px;border-bottom:1px solid #e5e9ed;overflow-wrap:anywhere}}th{{background:#f3f5f7}}li{{margin:8px 0}}footer{{border-top:1px solid #ddd;color:#7a8590;font-size:11px;padding-top:20px}}a{{color:#566476}}@media(max-width:650px){{main{{margin:0;padding:24px}}.grid{{grid-template-columns:1fr}}.kpis{{grid-template-columns:1fr 1fr}}}}@media print{{body{{background:white}}main{{margin:0;padding:15px;max-width:none}}a{{display:none}}*{{print-color-adjust:exact;-webkit-print-color-adjust:exact}}thead{{display:table-header-group}}tr{{break-inside:avoid}}}}</style>
<main><header><small>WANDERVISION / FIELD INTELLIGENCE REPORT</small><h1>巡检数据可视化报告</h1><p>道路巡检具身智能体 · 重庆交通大学南岸校区</p><span class="muted">生成时间：{esc(summary['generated_at'])} · 当前筛选：{esc(json.dumps(summary['filters'],ensure_ascii=False)) if summary['filters'] else '全部数据'}</span></header>
<div class="kpis">{''.join(f'<div>{name}<b>{summary[key]}</b></div>' for name,key in [('数据条目','total'),('待复核','pending'),('关注线索','attention'),('未定位条目','unlocated')])}</div>
<div class="grid"><section><h2>识别类别分布</h2>{bars(summary['categories'],CATEGORIES)}</section><section><h2>人工复核进度</h2>{bars(summary['statuses'],STATUSES)}</section></div><section><h2>采集日期趋势</h2>{svg}</section>
<div class="grid"><section><h2>采集点分布</h2>{bars(summary['areas'],{})}</section><section><h2>数据来源</h2>{bars(summary['sources'],{'sim':'SIM 模拟','test':'TEST 联调','real':'REAL 设备上报'})}<p class="muted">来源分别统计，不将模拟线索当成真实路况。</p></section></div>
<section><h2>需要关注的条目</h2><table><thead><tr><th>设备</th><th>采集点</th><th>线索</th><th>复核</th><th>来源</th></tr></thead><tbody>{issues}</tbody></table></section><section><h2>自动汇总与复核建议</h2><ul>{''.join('<li>'+esc(s)+'</li>' for s in summary['insights'])}</ul></section>
<section><h2>数据附表（前60条，完整数据可从后台导出）</h2><table><thead><tr><th>时间</th><th>设备</th><th>采集点</th><th>类别</th><th>目标数</th><th>复核</th></tr></thead><tbody>{detail}</tbody></table></section><footer>课堂演示自动汇总 · 根据当前台账生成，审核与标记为演示流程。报告不自动判定违法或现场安全结论。<br><a href="../admin/">返回数据后台</a> · 可使用浏览器打印保存PDF；下载HTML可离线浏览。</footer></main></html>'''
