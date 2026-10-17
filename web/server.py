"""wandervision demo server. Python 3.11+, no third-party runtime dependencies."""
from __future__ import annotations

import argparse
import base64
import binascii
from collections import Counter, deque
from datetime import datetime, timezone
import hmac
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
from pathlib import Path
import random
import re
import secrets
import sqlite3
import threading
import time
from urllib.parse import unquote, urlsplit

HERE = Path(__file__).resolve().parent
KINDS = {'pothole': ('道路坑槽', 'high'), 'crack': ('路面裂缝', 'medium'),
         'vehicle': ('车辆检测', 'low'), 'device': ('设备状态', 'info'), 'report': ('巡检照片', 'info')}
CAMPUS = {'lat':29.4903657, 'lon':106.567229, 'name':'重庆交通大学 · 南岸校区', 'zoom':15}
PUBLIC_BASE = os.environ.get('WV_PUBLIC_BASE', 'https://lvshanyewu.site/wandervision').rstrip('/')
MAX_IMAGE = 4 * 1024 * 1024
MAX_REPORTS = 50
MAX_REPORT_BYTES = 96 * 1024 * 1024


def jpeg_size(data):
    """Read JPEG SOF dimensions without decoding untrusted pixels or dependencies."""
    if not data.startswith(b'\xff\xd8') or not data.endswith(b'\xff\xd9'):
        raise ValueError('A complete JPEG image is required')
    pos=2
    while pos+4<len(data):
        if data[pos]!=255:
            raise ValueError('Invalid JPEG marker')
        while pos<len(data) and data[pos]==255:pos+=1
        marker=data[pos];pos+=1
        if marker in [0xd8,0xd9] or 0xd0<=marker<=0xd7:continue
        if pos+2>len(data):break
        length=int.from_bytes(data[pos:pos+2],'big')
        if length<2 or pos+length>len(data):break
        if marker in [0xc0,0xc1,0xc2]:
            if length<8:break
            h=int.from_bytes(data[pos+3:pos+5],'big');w=int.from_bytes(data[pos+5:pos+7],'big')
            if 1<=w<=12000 and 1<=h<=12000 and w*h<=40000000:return w,h
            break
        if marker==0xda:break
        pos+=length
    raise ValueError('Unsupported or invalid JPEG dimensions')


def number(value, low, high, name):
    if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f'{name} must be a finite number in [{low}, {high}]')
    return value


def timestamp(value):
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError('observed_at requires an ISO 8601 timestamp with timezone')
    try:
        parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if parsed.tzinfo is None:
            raise ValueError()
        result = parsed.timestamp()
        number(result, time.time() - 86400, time.time() + 300, 'observed_at')
        return result
    except (ValueError, OverflowError):
        raise ValueError('observed_at must be within the last 24h or next 5min, with timezone') from None


def iso(value):
    return datetime.fromtimestamp(value, timezone.utc).isoformat().replace('+00:00', 'Z')


def distance(a, b):
    lat = math.radians((a[1] + b[1]) / 2)
    return math.hypot((a[0] - b[0]) * math.cos(lat), a[1] - b[1]) * 111195


class Engine:
    def __init__(self, db_path, clock=time.time):
        self.clock = clock
        self.db_path = Path(db_path)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY, device_id TEXT NOT NULL,
            event_id TEXT NOT NULL, observed REAL NOT NULL, payload TEXT NOT NULL,
            UNIQUE(device_id,event_id));
            CREATE TABLE IF NOT EXISTS credentials (device_id TEXT PRIMARY KEY, token_hash TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS reports (id TEXT PRIMARY KEY, device_id TEXT NOT NULL,
              event_id TEXT NOT NULL, received REAL NOT NULL, payload TEXT NOT NULL,
              image BLOB NOT NULL, image_hash TEXT NOT NULL, UNIQUE(device_id,event_id));
            CREATE INDEX IF NOT EXISTS report_received ON reports(received);
            CREATE INDEX IF NOT EXISTS event_observed ON events(observed);''')
        self.pairings = {}
        self.pair_attempts = deque(maxlen=100)
        self.last_prune = self.clock()
        self.routes = json.loads((HERE / 'static/data/routes.json').read_text(encoding='utf-8'))
        for route in self.routes:
            points = route['coordinates']
            route['lengths'] = [0.0]
            for a, b in zip(points, points[1:]):
                route['lengths'].append(route['lengths'][-1] + distance(a, b))
        self.real = []
        saved = dict(self.db.execute('SELECT id,payload FROM devices'))
        for i, (lat, lon, district) in enumerate([(29.4903,106.5672,'南岸区'),(29.4947,106.5701,'南岸区'),(29.4855,106.5693,'南岸区')], 1):
            key = f'PHONE-{i:02}'
            self.real.append(json.loads(saved[key]) if key in saved else {
                'id': key, 'name': f'真实手机 {i:02}', 'source': 'real', 'type': 'phone',
                'status': 'pending', 'lat': lat, 'lon': lon, 'location_valid': False,
                'district': district, 'location': '预留接入位置 · 尚无 GPS', 'fps': None,
                'battery': None, 'speed': None, 'last_seen': None, 'preview_url': None})
        self.defaults = json.loads(json.dumps(self.real))
        self.reset()
        self.prune()

    def reset(self, seed=True):
        with self.lock:
            self.random = random.Random(20261017)
            now = self.clock()
            self.started = self.last_tick = now
            self.elapsed = 0.0
            self.running = True
            self.scenario = 'normal'
            self.next_event = 7.0
            self.cooldowns = {}
            self.sim_events = deque(maxlen=600)
            self.sim_counts = Counter()
            self.serial = 0
            self.round = getattr(self, 'round', 0) + 1
            self.devices = []
            fixed = [(29.4914,106.5691,'南岸区','交大轨道站 · 学府大道'),
                     (29.4958,106.5688,'南岸区','学府大道 · 北段路口'),
                     (29.4880,106.5660,'南岸区','南岸校区 · 西侧观察点'),
                     (29.4861,106.5690,'南岸区','学府大道 · 南段路口'),
                     (29.4847,106.5752,'南岸区','汇龙路 · 路口观察点'),
                     (29.4927,106.5637,'南岸区','南岸校区 · 校园道路')]
            for i, (lat, lon, district, location) in enumerate(fixed, 1):
                self.devices.append({'id': f'CQ-F{i:02}', 'name': location.split(' · ')[0]+'哨点',
                    'type':'fixed', 'source':'sim', 'status':'online', 'lat':lat, 'lon':lon,
                    'district':district, 'location':location, 'location_valid':True,
                    'fps':24+i%3, 'battery':100, 'speed':0, 'last_seen':now, 'preview_url':None})
            for i in range(4):
                route = self.routes[i % 3]
                self.devices.append({'id': f'CQ-V{i+1:02}', 'name':['交大巡检车','学府巡检车','汇龙巡检车','校园巡检车'][i],
                    'type':'mobile', 'source':'sim', 'status':'online', 'lat':0, 'lon':0,
                    'district':'南岸区', 'location':route['name'],
                    'location_valid':True, 'route_id':route['id'], 'route_index':i%3,
                    'offset':i*180, 'distance_m':0, 'fps':23, 'battery':90-i*9, 'speed':18+i*2,
                    'last_seen':now, 'preview_url':None})
            for d in self.devices:
                if d['type'] == 'mobile':
                    self.place(d)
            # Explicitly simulated sample history, so first load is useful for a presentation.
            for i in range(24 if seed else 0):
                d = self.devices[i % 10]
                self.add_sim_event(d, ['vehicle','vehicle','crack','pothole'][i%4], now-(24-i)*62)

    def place(self, d):
        route = self.routes[d['route_index']]
        total = route['lengths'][-1]
        progress = (d['offset'] + d['distance_m']) % (2 * total)
        target = progress if progress <= total else 2 * total - progress
        import bisect
        index = min(max(1, bisect.bisect_left(route['lengths'], target)), len(route['lengths'])-1)
        start, end = route['coordinates'][index-1:index+1]
        span = route['lengths'][index] - route['lengths'][index-1]
        ratio = (target-route['lengths'][index-1])/span if span else 0
        d['lon'], d['lat'] = [a+(b-a)*ratio for a,b in zip(start,end)]

    def add_sim_event(self, d, kind, now, message=None):
        self.serial += 1
        title, severity = KINDS[kind]
        event = {'id': f'sim-{self.round}-{self.serial}', 'device_id':d['id'], 'source':'sim',
                 'kind':kind, 'title':title, 'severity':severity, 'timestamp':now,
                 'lat':d['lat'], 'lon':d['lon'], 'location':d['location'],
                 'confidence':None if kind=='device' else round(self.random.uniform(.76,.97),2),
                 'message':message or {'pothole':'发现疑似坑槽，建议复核', 'crack':'发现疑似路面裂缝',
                                      'vehicle':f'检测到 {self.random.randint(2,12)} 辆通行车辆',
                                      'device':'设备状态发生变化'}[kind]}
        self.sim_events.appendleft(event)
        self.sim_counts[kind] += 1
        self.cooldowns[(d['id'],kind)] = now

    def tick(self):
        with self.lock:
            now = self.clock()
            if now-self.last_prune>=3600:
                self.prune();self.last_prune=now
            dt = min(3, max(0, now - self.last_tick))
            self.last_tick = now
            if not self.running:
                return
            self.elapsed += dt
            for i, d in enumerate(self.devices):
                status = 'online'
                if d['id']=='CQ-F06' and (self.scenario=='offline' or 22 <= self.elapsed % 100 < 44):
                    status = 'offline'
                if d['id']=='CQ-V04' and 60 <= self.elapsed % 140 < 80:
                    status = 'paused'
                if status != d['status']:
                    d['status'] = status
                    self.add_sim_event(d, 'device', now, {'online':'连接已恢复，继续巡检','offline':'设备离线，已停止上报','paused':'车辆临时停靠，暂停巡检'}[status])
                if status != 'online':
                    continue
                d['last_seen'] = now
                d['fps'] = 22 + int(self.elapsed/8+i)%5
                if d['type']=='mobile':
                    d['distance_m'] += dt * d['speed']/3.6
                    d['battery'] = round(max(15, 90-(i-6)*9-self.elapsed/900),1)
                    self.place(d)
            if self.elapsed >= self.next_event:
                self.next_event = self.elapsed + (4 if self.scenario in ['busy','roadwork'] else 8)
                choices=['pothole','crack','crack','vehicle'] if self.scenario=='roadwork' else ['vehicle','vehicle','crack','pothole']
                candidates = [(d,k) for d in self.devices if d['status']=='online'
                              for k in choices
                              if now-self.cooldowns.get((d['id'],k),0) >= 45]
                if candidates:
                    d, kind = self.random.choice(candidates)
                    self.add_sim_event(d, kind, now)

    def heartbeat(self, device_id, body):
        if not isinstance(body, dict):
            raise ValueError('JSON object required')
        valid = body.get('lat') is not None or body.get('lon') is not None
        lat = number(body.get('lat'), -90, 90, 'lat') if valid else None
        lon = number(body.get('lon'), -180, 180, 'lon') if valid else None
        metrics = {key:number(body[key],0,limit,key) for key,limit in [('battery',100),('fps',240),('speed',200)] if key in body}
        status = body.get('status','online')
        if status not in ['online','paused']:
            raise ValueError('status must be online or paused')
        if 'observed_at' in body:
            timestamp(body['observed_at'])
        with self.lock:
            d = next(d for d in self.real if d['id']==device_id)
            if valid:d.update(lat=lat,lon=lon)
            d.update(metrics, location_valid=valid, status=status,
                     last_seen=self.clock(), location='真实设备 GPS 上报' if valid else '设备在线 · 等待 GPS')
            self.db.execute('INSERT OR REPLACE INTO devices VALUES (?,?)',(device_id,json.dumps(d)))
            self.db.commit()
            return {'ok':True,'device_id':device_id,'received_at':iso(d['last_seen'])}

    def ingest(self, device_id, body):
        if not isinstance(body,dict):
            raise ValueError('JSON object required')
        event_id = body.get('event_id')
        if not isinstance(event_id,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',event_id):
            raise ValueError('event_id: 1-80 alphanumeric, underscore or hyphen characters required')
        kind = body.get('kind')
        if kind not in ['pothole','crack','vehicle']:
            raise ValueError('kind must be pothole, crack or vehicle')
        confidence = number(body.get('confidence'),0,1,'confidence')
        observed = timestamp(body.get('observed_at'))
        with self.lock:
            existing = self.db.execute('SELECT payload FROM events WHERE device_id=? AND event_id=?',(device_id,event_id)).fetchone()
            if existing:
                return {'ok':True,'duplicate':True,'event':json.loads(existing[0])}
            d = next(d for d in self.real if d['id']==device_id)
            if d['last_seen'] is None or self.clock()-d['last_seen']>30 or d['status']!='online':
                raise ValueError('Send an online heartbeat before reporting detections (30s TTL)')
            lat = number(body.get('lat',d['lat'] if d['location_valid'] else None),-90,90,'lat')
            lon = number(body.get('lon',d['lon'] if d['location_valid'] else None),-180,180,'lon')
            event = {'id':f'real-{device_id}-{event_id}', 'device_id':device_id, 'source':'real',
                     'kind':kind,'title':KINDS[kind][0],'severity':KINDS[kind][1],
                     'timestamp':observed,'confidence':confidence,'lat':lat,'lon':lon,
                     'location':d['location'],'message':'真实设备上报 · 待人工复核'}
            self.db.execute('INSERT INTO events(device_id,event_id,observed,payload) VALUES (?,?,?,?)',
                            (device_id,event_id,observed,json.dumps(event)))
            self.db.commit()
            return {'ok':True,'duplicate':False,'event':event}

    def snapshot(self):
        with self.lock:
            now = self.clock()
            real = json.loads(json.dumps(self.real))
            for d in real:
                if d['last_seen'] is not None and now-d['last_seen']>30:
                    d['status']='offline'
            devices = json.loads(json.dumps(self.devices)) + real
            recent_real = [json.loads(row[0]) for row in self.db.execute('SELECT payload FROM events ORDER BY observed DESC LIMIT 600')]
            events = sorted(list(self.sim_events)+recent_real,key=lambda e:e['timestamp'],reverse=True)
            counts = self.sim_counts.copy()
            for row in self.db.execute('SELECT json_extract(payload,\'$.kind\'), COUNT(*) FROM events GROUP BY 1'):
                counts[row[0]] += row[1]
            bucket = 300
            end = int(now//bucket)*bucket
            trend = [{'timestamp':end-(5-i)*bucket,'sim':0,'real':0} for i in range(6)]
            for e in events:
                index = int((e['timestamp']-trend[0]['timestamp'])//bucket)
                if 0 <= index < 6:
                    trend[index][e['source']] += 1
            return {'version':1,'server_time':iso(now),'started_at':iso(self.started),
                'simulation':{'running':self.running,'scenario':self.scenario,'elapsed':round(self.elapsed)},
                'devices':devices, 'events':events[:200], 'distribution':dict(counts), 'trend':trend,
                'stats':{'total':13,'online':sum(d['status']=='online' for d in devices),
                    'sim_total':10,'real_connected':sum(d['last_seen'] is not None for d in real),
                    'anomalies':counts['pothole']+counts['crack'],'events':sum(counts.values()),
                    'distance_km':round(sum(d.get('distance_m',0) for d in self.devices)/1000,2)},
                'campus':CAMPUS,
                'reports':self.report_list(),
                'ingestion':{'configured':bool(os.environ.get('WV_DEVICE_TOKENS')) or bool(self.db.execute('SELECT 1 FROM credentials LIMIT 1').fetchone()),'heartbeat_ttl_seconds':30}}

    def control(self, body):
        if not isinstance(body,dict):
            raise ValueError('JSON object required')
        with self.lock:
            action = body.get('action')
            if action == 'reset':
                self.reset()  # Only simulation state; real data is preserved.
            elif action in ['pause','resume']:
                self.running = action == 'resume'
                self.last_tick = self.clock()
            elif action == 'scenario' and body.get('scenario') in ['normal','busy','roadwork','offline','night']:
                self.scenario = body['scenario']
            else:
                raise ValueError('Unknown simulation action or scenario')
            return {'ok':True,'running':self.running,'scenario':self.scenario}

    def authorize(self, device_id, authorization):
        token=json.loads(os.environ.get('WV_DEVICE_TOKENS','{}')).get(device_id)
        if isinstance(token,str) and len(token)>=24 and hmac.compare_digest(authorization,'Bearer '+token):return True
        if not authorization.startswith('Bearer '):return False
        raw=authorization[7:]
        if not 24<=len(raw)<=256:return False
        with self.lock:
            row=self.db.execute('SELECT token_hash FROM credentials WHERE device_id=?',(device_id,)).fetchone()
            return bool(row and hmac.compare_digest(row[0],hashlib.sha256(raw.encode()).hexdigest()))

    def pairing_code(self, body):
        device_id=body.get('device_id') if isinstance(body,dict) else None
        if device_id not in ['PHONE-01','PHONE-02','PHONE-03']:raise ValueError('Invalid phone slot')
        with self.lock:
            now=self.clock()
            self.pairings={code:p for code,p in self.pairings.items() if p['expires']>now and p['device_id']!=device_id}
            code=f'{secrets.randbelow(1000000):06d}'
            while code in self.pairings:code=f'{secrets.randbelow(1000000):06d}'
            self.pairings[code]={'device_id':device_id,'expires':now+300}
            return {'ok':True,'device_id':device_id,'code':code,'expires_at':iso(now+300)}

    def pair(self, body):
        if not isinstance(body,dict) or not isinstance(body.get('code'),str):raise ValueError('Pairing code required')
        with self.lock:
            now=self.clock()
            self.pair_attempts=deque((t for t in self.pair_attempts if now-t<60),maxlen=100)
            if len(self.pair_attempts)>=12:raise ValueError('Too many pairing attempts; retry after one minute')
            self.pair_attempts.append(now)
            p=self.pairings.pop(body['code'],None)
            if not p or p['expires']<now:raise ValueError('Pairing code expired or invalid')
            token=secrets.token_urlsafe(32)
            self.db.execute('INSERT OR REPLACE INTO credentials VALUES (?,?)',(p['device_id'],hashlib.sha256(token.encode()).hexdigest()))
            if isinstance(body.get('name'),str) and body['name'].strip():
                d=next(d for d in self.real if d['id']==p['device_id']);d['name']=body['name'].strip()[:40]
                self.db.execute('INSERT OR REPLACE INTO devices VALUES (?,?)',(d['id'],json.dumps(d)))
            self.db.commit()
            return {'ok':True,'device_id':p['device_id'],'token':token,'dashboard_url':PUBLIC_BASE+'/?device='+p['device_id']}

    def report_list(self):
        rows=self.db.execute('SELECT id,device_id,event_id,received,payload,LENGTH(image),image_hash FROM reports ORDER BY received DESC LIMIT 50')
        result=[]
        for rid,device,event,received,payload,size,digest in rows:
            report=json.loads(payload); loc=report.get('location') or {};inf=report.get('inference') or {}
            source=report.get('source') or {};model=str(source.get('device_model','')) if isinstance(source,dict) else ''
            test_data=loc.get('mock') is True or (isinstance(source,dict) and source.get('test_data') is True) or bool(re.search('emulator|gphone|generic|sdk',model,re.I))
            result.append({'id':rid,'device_id':device,'event_id':event,'received_at':iso(received),
                'captured_at':report.get('captured_at'),'image_url':PUBLIC_BASE+'/api/v1/reports/'+rid+'/image',
                'bytes':size,'sha256':digest,'location':loc,'objects':len(inf.get('detections',[])),
                'model':inf.get('model_id',report.get('model','—')),'source':'real','test_data':test_data})
        return result

    def receive_report(self, device_id, body):
        if not isinstance(body,dict) or not isinstance(body.get('report'),dict):raise ValueError('report object required')
        report=body['report'];event_id=report.get('event_id')
        if not isinstance(event_id,str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,80}',event_id):raise ValueError('Invalid report event_id')
        if report.get('schema_version')!='1.0':raise ValueError('Report schema_version 1.0 required')
        if len(json.dumps(report,ensure_ascii=False).encode())>256*1024:raise ValueError('Report metadata too large')
        image_info=report.get('image')
        if not isinstance(image_info,dict):raise ValueError('image metadata required')
        expected=image_info.get('sha256')
        if not isinstance(expected,str) or not re.fullmatch(r'[a-fA-F0-9]{64}',expected):raise ValueError('JPEG SHA256 required')
        try:
            if not isinstance(body.get('image_base64'),str):raise ValueError('Image required')
            image=base64.b64decode(body['image_base64'],validate=True)
        except (binascii.Error,UnicodeError):raise ValueError('Invalid base64 JPEG') from None
        if not 1<=len(image)<=MAX_IMAGE:raise ValueError('JPEG must be at most 4MiB')
        if hashlib.sha256(image).hexdigest()!=expected.lower():raise ValueError('JPEG SHA256 does not match report')
        width,height=jpeg_size(image)
        if any(key in image_info and image_info[key]!=value for key,value in [('width',width),('width_px',width),('height',height),('height_px',height)]):
            raise ValueError('JPEG dimensions do not match report')
        if 'bytes' in image_info and image_info['bytes']!=len(image):raise ValueError('JPEG byte size does not match report')
        if image_info.get('mime_type','image/jpeg')!='image/jpeg':raise ValueError('Only image/jpeg reports are supported')
        # Report metadata is retained, never treated as executable markup or a URL.
        location=report.get('location') or {}
        if not isinstance(location,dict) or not isinstance(report.get('inference',{}),dict):raise ValueError('Invalid location/inference metadata')
        lat=location.get('latitude');lon=location.get('longitude')
        valid=lat is not None and lon is not None
        if valid:number(lat,-90,90,'latitude');number(lon,-180,180,'longitude')
        if not isinstance(report.get('inference',{}).get('detections',[]),list):raise ValueError('detections must be an array')
        if len(report.get('inference',{}).get('detections',[]))>500:raise ValueError('Too many detections')
        with self.lock:
            row=self.db.execute('SELECT id,received,image_hash FROM reports WHERE device_id=? AND event_id=?',(device_id,event_id)).fetchone()
            if row:
                if row[2]!=expected.lower():raise ValueError('event_id already used for a different image')
                rid,received,_=row;duplicate=True
            else:
                now=self.clock();rid='report-'+secrets.token_hex(12);received=now;duplicate=False
                self.db.execute('INSERT INTO reports VALUES (?,?,?,?,?,?,?)',(rid,device_id,event_id,now,json.dumps(report,ensure_ascii=False),image,expected.lower()))
                d=next(d for d in self.real if d['id']==device_id)
                d.update(last_seen=now,status='online',preview_url=PUBLIC_BASE+'/api/v1/reports/'+rid+'/image')
                d.update(location_valid=valid,location='照片已接收 · 未提供本次定位' if not valid else d['location'])
                if valid:d.update(lat=lat,lon=lon,location_valid=True,location='设备报告位置 · '+str(location.get('status','unknown'))[:24])
                self.db.execute('INSERT OR REPLACE INTO devices VALUES (?,?)',(device_id,json.dumps(d)))
                event={'id':rid,'device_id':device_id,'source':'real','kind':'report','title':'巡检照片上传','severity':'info',
                       'timestamp':now,'confidence':None,'lat':lat if valid else None,'lon':lon if valid else None,
                       'location':d['location'],'message':'收到巡检照片 · '+str(len(report.get('inference',{}).get('detections',[])))+' 个同帧目标',
                       'report_id':rid}
                self.db.execute('INSERT INTO events(device_id,event_id,observed,payload) VALUES (?,?,?,?)',(device_id,'photo-'+event_id,now,json.dumps(event,ensure_ascii=False)))
                self.db.commit();self.prune()
            return {'ok':True,'duplicate':duplicate,'server_event_id':rid,'received_at':iso(received),'image_url':PUBLIC_BASE+'/api/v1/reports/'+rid+'/image'}

    def prune(self):
        with self.lock:
            cutoff=self.clock()-7*86400
            self.db.execute('DELETE FROM events WHERE observed<? OR id NOT IN (SELECT id FROM events ORDER BY observed DESC LIMIT 5000)',(cutoff,))
            rows=self.db.execute('SELECT id,device_id,event_id,LENGTH(image),received FROM reports ORDER BY received DESC').fetchall()
            total=0
            for i,(rid,device,event,size,received) in enumerate(rows):
                total+=size
                if i>=MAX_REPORTS or total>MAX_REPORT_BYTES or received<cutoff:
                    self.db.execute('DELETE FROM reports WHERE id=?',(rid,))
                    self.db.execute('DELETE FROM events WHERE device_id=? AND event_id=?',(device,'photo-'+event))
            self.db.commit()
            self.db.execute('PRAGMA wal_checkpoint(TRUNCATE)')

    def storage(self):
        with self.lock:
            reports,images=self.db.execute('SELECT COUNT(*),COALESCE(SUM(LENGTH(image)),0) FROM reports').fetchone()
            return {'reports':reports,'image_bytes':images,'events':self.db.execute('SELECT COUNT(*) FROM events').fetchone()[0],
                    'database_bytes':sum(p.stat().st_size for p in [self.db_path,Path(str(self.db_path)+'-wal')] if p.exists()),
                    'max_reports':MAX_REPORTS,'max_image_bytes':MAX_REPORT_BYTES,'retention_days':7,
                    'paired_devices':[row[0] for row in self.db.execute('SELECT device_id FROM credentials')]}

    def maintenance(self, body):
        if not isinstance(body,dict) or body.get('action') not in ['clear','compact']:raise ValueError('Unknown maintenance action')
        with self.lock:
            before=self.storage()['database_bytes']
            if body['action']=='clear':
                self.db.executescript('DELETE FROM events; DELETE FROM reports; DELETE FROM devices;')
                if body.get('keep_pairing',True) is False:self.db.execute('DELETE FROM credentials')
                self.pairings.clear()
                self.real=[{'id':f'PHONE-{i+1:02}','name':f'真实手机 {i+1:02}','source':'real','type':'phone','status':'pending',
                    'lat':CAMPUS['lat']+(i-1)*.003,'lon':CAMPUS['lon']+.003*i,'location_valid':False,'district':'南岸区',
                    'location':'预留接入位置 · 尚无 GPS','fps':None,'battery':None,'speed':None,'last_seen':None,'preview_url':None} for i in range(3)]
                self.reset(seed=False)
            else:self.prune()
            self.db.commit();self.db.execute('PRAGMA wal_checkpoint(TRUNCATE)');self.db.execute('VACUUM')
            self.db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
            after=self.storage()
            return {'ok':True,'freed_bytes':max(0,before-after['database_bytes']),'storage':after}


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, engine):
        self.engine = engine
        self.rate_lock = threading.Lock()
        self.upload_gate = threading.BoundedSemaphore(1)
        self.rates = {}
        super().__init__(address, Handler)


class Handler(SimpleHTTPRequestHandler):
    server_version = 'wandervision/1.0'

    def __init__(self,*args,**kwargs):
        super().__init__(*args,directory=str(HERE/'static'),**kwargs)

    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def end_headers(self):
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Referrer-Policy','strict-origin-when-cross-origin')
        self.send_header('X-Frame-Options','SAMEORIGIN')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'")
        super().end_headers()

    def list_directory(self, path):
        self.send_error(404)
        return None

    def json_response(self,status,data):
        payload=json.dumps(data,ensure_ascii=False,allow_nan=False).encode()
        self.send_response(status)
        self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Content-Length',str(len(payload)))
        self.send_header('Cache-Control','no-store')
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        path=urlsplit(self.path).path
        if path=='/api/v1/health':
            return self.json_response(200,{'ok':True,'service':'wandervision','version':'2.0.0'})
        if path=='/api/v1/snapshot':
            return self.json_response(200,self.server.engine.snapshot())
        if path=='/api/v1/settings':
            return self.json_response(200,{'ok':True,'campus':CAMPUS,'storage':self.server.engine.storage(),
                'scenarios':['normal','busy','roadwork','offline','night']})
        if path=='/api/v1/reports':
            with self.server.engine.lock:return self.json_response(200,{'ok':True,'reports':self.server.engine.report_list()})
        report_match=re.fullmatch(r'/api/v1/reports/(report-[a-f0-9]{24})(/image)?',path)
        if report_match:
            with self.server.engine.lock:
                row=self.server.engine.db.execute('SELECT payload,image FROM reports WHERE id=?',(report_match[1],)).fetchone()
            if not row:return self.json_response(404,{'error':'Report not found or cleaned'})
            if not report_match[2]:return self.json_response(200,{'ok':True,'report':json.loads(row[0])})
            self.send_response(200);self.send_header('Content-Type','image/jpeg');self.send_header('Content-Length',str(len(row[1])))
            self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(row[1]);return
        if path.startswith('/api/'):
            return self.json_response(404,{'error':'Unknown API endpoint'})
        if any(p.startswith('.') for p in unquote(path).split('/') if p):
            return self.json_response(404,{'error':'Not found'})
        return super().do_GET()

    def limited(self):
        now = time.monotonic()
        key=self.client_address[0]
        with self.server.rate_lock:
            if len(self.server.rates)>2048:
                self.server.rates={k:v for k,v in self.server.rates.items() if now-v[0]<60}
            start,count=self.server.rates.get(key,(now,0))
            if now-start>=60:
                start,count=now,0
            self.server.rates[key]=(start,count+1)
            return count>=120

    def do_POST(self):
        upload_locked=False
        if self.limited():
            return self.json_response(429,{'error':'Rate limit: 120 writes/minute per address'})
        path=urlsplit(self.path).path
        try:
            match=re.fullmatch(r'/api/v1/devices/(PHONE-0[1-3])/(heartbeat|events|reports)',path)
            if match:
                configured=json.loads(os.environ.get('WV_DEVICE_TOKENS','{}')).get(match[1])
                with self.server.engine.lock:
                    paired=self.server.engine.db.execute('SELECT 1 FROM credentials WHERE device_id=?',(match[1],)).fetchone()
                if not configured and not paired:
                    return self.json_response(503,{'error':'Device token is not configured on this server'})
                if not self.server.engine.authorize(match[1],self.headers.get('Authorization','')):
                    return self.json_response(401,{'error':'Valid per-device Bearer token required'})
            elif path in ['/api/v1/simulation','/api/v1/pairing','/api/v1/maintenance']:
                origin=self.headers.get('Origin')
                if origin and urlsplit(origin).netloc != self.headers.get('Host'):
                    return self.json_response(403,{'error':'Same-origin simulation control required'})
                if self.headers.get('Sec-Fetch-Site')=='cross-site':
                    return self.json_response(403,{'error':'Cross-site writes rejected'})
            elif path!='/api/v1/pair':
                return self.json_response(404,{'error':'Unknown API endpoint'})
            if match and match[2]=='reports':
                upload_locked=self.server.upload_gate.acquire(blocking=False)
                if not upload_locked:return self.json_response(429,{'error':'Another photo upload is active; please retry'})
            length=int(self.headers.get('Content-Length','0'))
            limit=6*1024*1024 if match and match[2]=='reports' else 16384
            if length<1 or length>limit:
                return self.json_response(413,{'error':'JSON body exceeds endpoint size limit'})
            if self.headers.get_content_type()!='application/json':
                return self.json_response(415,{'error':'Content-Type application/json required'})
            body=json.loads(self.rfile.read(length),parse_float=lambda v:number(float(v),-1e100,1e100,'JSON number'),
                            parse_constant=lambda x: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))
            engine=self.server.engine
            if path=='/api/v1/simulation':
                result=engine.control(body)
            elif path=='/api/v1/pairing':result=engine.pairing_code(body)
            elif path=='/api/v1/pair':result=engine.pair(body)
            elif path=='/api/v1/maintenance':result=engine.maintenance(body)
            elif match[2]=='heartbeat':
                result=engine.heartbeat(match[1],body)
            elif match[2]=='reports':result=engine.receive_report(match[1],body)
            else:
                result=engine.ingest(match[1],body)
            self.json_response(200,result)
        except (ValueError,TypeError,UnicodeError) as exc:
            self.json_response(400,{'error':str(exc)[:180] or 'Invalid request'})
        except (ConnectionError,TimeoutError):
            self.close_connection=True
        finally:
            if upload_locked:self.server.upload_gate.release()

    def log_message(self,format,*args):
        # Never log body, credentials, query strings or submitted device data.
        if len(args)>1 and str(args[1]).startswith(('4','5')):
            print(f'HTTP {args[1]}',flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=8090)
    parser.add_argument('--db',default=os.environ.get('WV_DB',str(HERE/'runtime'/'wandervision.sqlite3')))
    args=parser.parse_args()
    Path(args.db).parent.mkdir(parents=True,exist_ok=True)
    configured=json.loads(os.environ.get('WV_DEVICE_TOKENS','{}'))
    if not isinstance(configured,dict) or any(k not in ['PHONE-01','PHONE-02','PHONE-03'] or not isinstance(v,str) or len(v)<24 for k,v in configured.items()):
        parser.error('WV_DEVICE_TOKENS must map PHONE-01..03 to unique secrets of at least 24 characters')
    if len(set(configured.values())) != len(configured):
        parser.error('Each device must have a unique token')
    engine=Engine(args.db)
    stop=threading.Event()
    def simulate():
        while not stop.wait(1):
            engine.tick()
    threading.Thread(target=simulate,daemon=True).start()
    server=Server((args.host,args.port),engine)
    print(f'wandervision: http://{args.host}:{args.port}/',flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        stop.set()
        server.server_close()
        engine.db.close()


if __name__=='__main__':
    main()
