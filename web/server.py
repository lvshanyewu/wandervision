"""wandervision demo server. Python 3.11+, no third-party runtime dependencies."""
from __future__ import annotations

import argparse
from collections import Counter, deque
from datetime import datetime, timezone
import hmac
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
from pathlib import Path
import random
import re
import sqlite3
import threading
import time
from urllib.parse import unquote, urlsplit

HERE = Path(__file__).resolve().parent
KINDS = {'pothole': ('道路坑槽', 'high'), 'crack': ('路面裂缝', 'medium'),
         'vehicle': ('车辆检测', 'low'), 'device': ('设备状态', 'info')}


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
        self.lock = threading.RLock()
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.executescript('''CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY, device_id TEXT NOT NULL,
            event_id TEXT NOT NULL, observed REAL NOT NULL, payload TEXT NOT NULL,
            UNIQUE(device_id,event_id));''')
        self.routes = json.loads((HERE / 'static/data/routes.json').read_text(encoding='utf-8'))
        for route in self.routes:
            points = route['coordinates']
            route['lengths'] = [0.0]
            for a, b in zip(points, points[1:]):
                route['lengths'].append(route['lengths'][-1] + distance(a, b))
        self.real = []
        saved = dict(self.db.execute('SELECT id,payload FROM devices'))
        for i, (lat, lon, district) in enumerate([(29.558,106.578,'渝中区'),(29.583,106.568,'江北区'),(29.548,106.585,'南岸区')], 1):
            key = f'PHONE-{i:02}'
            self.real.append(json.loads(saved[key]) if key in saved else {
                'id': key, 'name': f'真实手机 {i:02}', 'source': 'real', 'type': 'phone',
                'status': 'pending', 'lat': lat, 'lon': lon, 'location_valid': False,
                'district': district, 'location': '预留接入位置 · 尚无 GPS', 'fps': None,
                'battery': None, 'speed': None, 'last_seen': None, 'preview_url': None})
        self.reset()

    def reset(self):
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
            fixed = [(29.5635,106.5797,'渝中区','解放碑 · 民权路'),
                     (29.5712,106.5837,'渝中区','朝天门 · 长滨路'),
                     (29.5871,106.5339,'江北区','观音桥 · 建新东路'),
                     (29.5799,106.5682,'江北区','江北嘴 · 北滨路'),
                     (29.5415,106.5670,'南岸区','南坪 · 江南大道'),
                     (29.5524,106.5920,'南岸区','龙门浩 · 南滨路')]
            for i, (lat, lon, district, location) in enumerate(fixed, 1):
                self.devices.append({'id': f'CQ-F{i:02}', 'name': location.split(' · ')[0]+'哨点',
                    'type':'fixed', 'source':'sim', 'status':'online', 'lat':lat, 'lon':lon,
                    'district':district, 'location':location, 'location_valid':True,
                    'fps':24+i%3, 'battery':100, 'speed':0, 'last_seen':now, 'preview_url':None})
            for i in range(4):
                route = self.routes[i % 3]
                self.devices.append({'id': f'CQ-V{i+1:02}', 'name':['半岛巡检车','江北巡检车','南滨巡检车','渝中巡检车'][i],
                    'type':'mobile', 'source':'sim', 'status':'online', 'lat':0, 'lon':0,
                    'district':['渝中区','江北区','南岸区','渝中区'][i], 'location':route['name'],
                    'location_valid':True, 'route_id':route['id'], 'route_index':i%3,
                    'offset':i*640, 'distance_m':0, 'fps':23, 'battery':90-i*9, 'speed':28+i*3,
                    'last_seen':now, 'preview_url':None})
            for d in self.devices:
                if d['type'] == 'mobile':
                    self.place(d)
            # Explicitly simulated sample history, so first load is useful for a presentation.
            for i in range(24):
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
            dt = min(3, max(0, now - self.last_tick))
            self.last_tick = now
            if not self.running:
                return
            self.elapsed += dt
            for i, d in enumerate(self.devices):
                status = 'online'
                if d['id']=='CQ-F06' and 22 <= self.elapsed % 100 < 44:
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
                self.next_event = self.elapsed + (4 if self.scenario=='busy' else 8)
                candidates = [(d,k) for d in self.devices if d['status']=='online'
                              for k in ['vehicle','vehicle','crack','pothole']
                              if now-self.cooldowns.get((d['id'],k),0) >= 45]
                if candidates:
                    d, kind = self.random.choice(candidates)
                    self.add_sim_event(d, kind, now)

    def heartbeat(self, device_id, body):
        if not isinstance(body, dict):
            raise ValueError('JSON object required')
        lat = number(body.get('lat'), -90, 90, 'lat')
        lon = number(body.get('lon'), -180, 180, 'lon')
        metrics = {key:number(body[key],0,limit,key) for key,limit in [('battery',100),('fps',240),('speed',200)] if key in body}
        status = body.get('status','online')
        if status not in ['online','paused']:
            raise ValueError('status must be online or paused')
        if 'observed_at' in body:
            timestamp(body['observed_at'])
        with self.lock:
            d = next(d for d in self.real if d['id']==device_id)
            d.update(metrics, lat=lat, lon=lon, location_valid=True, status=status,
                     last_seen=self.clock(), location='真实设备 GPS 上报')
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
            lat = number(body.get('lat',d['lat']),-90,90,'lat')
            lon = number(body.get('lon',d['lon']),-180,180,'lon')
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
                'ingestion':{'configured':bool(os.environ.get('WV_DEVICE_TOKENS')),'heartbeat_ttl_seconds':30}}

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
            elif action == 'scenario' and body.get('scenario') in ['normal','busy']:
                self.scenario = body['scenario']
            else:
                raise ValueError('Unknown simulation action or scenario')
            return {'ok':True,'running':self.running,'scenario':self.scenario}


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, engine):
        self.engine = engine
        self.rate_lock = threading.Lock()
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
            return self.json_response(200,{'ok':True,'service':'wandervision','version':'1.0.0'})
        if path=='/api/v1/snapshot':
            return self.json_response(200,self.server.engine.snapshot())
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
        if self.limited():
            return self.json_response(429,{'error':'Rate limit: 120 writes/minute per address'})
        path=urlsplit(self.path).path
        try:
            match=re.fullmatch(r'/api/v1/devices/(PHONE-0[1-3])/(heartbeat|events)',path)
            if match:
                tokens=json.loads(os.environ.get('WV_DEVICE_TOKENS','{}'))
                token=tokens.get(match[1])
                if not isinstance(token,str) or len(token)<24:
                    return self.json_response(503,{'error':'Device token is not configured on this server'})
                if not hmac.compare_digest(self.headers.get('Authorization',''), 'Bearer '+token):
                    return self.json_response(401,{'error':'Valid per-device Bearer token required'})
            elif path=='/api/v1/simulation':
                origin=self.headers.get('Origin')
                if origin and urlsplit(origin).netloc != self.headers.get('Host'):
                    return self.json_response(403,{'error':'Same-origin simulation control required'})
                if self.headers.get('Sec-Fetch-Site')=='cross-site':
                    return self.json_response(403,{'error':'Cross-site writes rejected'})
            else:
                return self.json_response(404,{'error':'Unknown API endpoint'})
            length=int(self.headers.get('Content-Length','0'))
            if length<1 or length>16384:
                return self.json_response(413,{'error':'JSON body required, maximum 16 KiB'})
            if self.headers.get_content_type()!='application/json':
                return self.json_response(415,{'error':'Content-Type application/json required'})
            body=json.loads(self.rfile.read(length),parse_constant=lambda x: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))
            engine=self.server.engine
            if path=='/api/v1/simulation':
                result=engine.control(body)
            elif match[2]=='heartbeat':
                result=engine.heartbeat(match[1],body)
            else:
                result=engine.ingest(match[1],body)
            self.json_response(200,result)
        except (ValueError,TypeError,UnicodeError):
            self.json_response(400,{'error':'Invalid request: check fields, ranges, timestamp, and online heartbeat'})
        except (ConnectionError,TimeoutError):
            self.close_connection=True

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
