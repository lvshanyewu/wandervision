"""Synthetic JPEG / isolated database tests; no public camera or personal data."""
import base64
import http.client
import hashlib
import json
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from test_server import app
from jpeg_fixture import JPEG_BASE64

JPEG=base64.b64decode(JPEG_BASE64)
TOKEN='live-test-only-token-123456789'


class LiveEngineTests(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.now=time.time()
        self.engine=app.Engine(Path(self.folder.name)/'live.sqlite3',lambda:self.now)

    def tearDown(self):
        self.engine.db.close();self.folder.cleanup()

    def test_latest_only_no_sql_or_fake_heartbeat_or_location(self):
        before=self.engine.db.total_changes
        for i in range(30):
            self.now+=.08;r=self.engine.receive_live('PHONE-01',JPEG,True)
        self.assertEqual(r['frame_id'],30);self.assertEqual(r['state'],'live');self.assertAlmostEqual(r['fps'],12.5)
        self.assertEqual(self.engine.db.total_changes,before)
        self.assertEqual(len(self.engine.live_frames),1)
        self.assertEqual(self.engine.live_image('PHONE-01')[1],JPEG)
        phone=next(d for d in self.engine.snapshot()['devices'] if d['id']=='PHONE-01')
        self.assertIsNone(phone['last_seen']);self.assertFalse(phone['location_valid'])
        self.assertTrue(phone['live']['test_data']);self.assertNotIn('image',phone['live'])
        self.assertEqual(self.engine.report_list(),[])

    def test_stale_hides_frame_then_tick_physically_discards_even_paused(self):
        self.engine.receive_live('PHONE-01',JPEG);self.engine.control({'action':'pause'})
        self.now+=2.01;meta,data=self.engine.live_image('PHONE-01')
        self.assertEqual(meta['state'],'stale');self.assertIsNone(data)
        self.assertIn('image',self.engine.live_frames['PHONE-01'])
        self.now+=3.01;self.engine.tick()
        self.assertNotIn('image',self.engine.live_frames['PHONE-01'])
        self.assertEqual(self.engine.live_meta('PHONE-01')['state'],'expired')

    def test_rate_is_sliding_per_device_and_bounded(self):
        for _ in range(15):self.engine.reserve_live('PHONE-01')
        with self.assertRaises(app.LiveRateLimit):self.engine.reserve_live('PHONE-01')
        self.engine.reserve_live('PHONE-02');self.now+=1
        self.engine.reserve_live('PHONE-01');self.assertEqual(len(self.engine.live_attempts['PHONE-01']),1)

    def test_repair_and_clear_revoke_existing_frames(self):
        self.engine.receive_live('PHONE-01',JPEG)
        code=self.engine.pairing_code({'device_id':'PHONE-01'})['code']
        self.engine.pair({'code':code});self.assertIsNone(self.engine.live_image('PHONE-01')[1])
        self.engine.receive_live('PHONE-01',JPEG);self.engine.maintenance({'action':'clear'})
        self.assertEqual(self.engine.live_meta('PHONE-01')['state'],'idle');self.assertEqual(self.engine.live_frames,{})

    def test_invalid_or_large_jpeg_rejected_without_replacing_latest(self):
        self.engine.receive_live('PHONE-01',JPEG)
        for data in [b'bad',JPEG[:-2],JPEG+b'\0'*(262145-len(JPEG))]:
            with self.assertRaises(ValueError):self.engine.receive_live('PHONE-01',data)
        with patch.object(app,'jpeg_size',return_value=(1281,720)):
            with self.assertRaises(ValueError):self.engine.receive_live('PHONE-01',JPEG)
        with patch.object(app,'jpeg_size',return_value=(1024,1024)):
            with self.assertRaises(ValueError):self.engine.receive_live('PHONE-01',JPEG)
        self.assertEqual(self.engine.live_meta('PHONE-01')['frame_id'],1)

    def test_road_models_keep_their_own_labels_and_class_ids(self):
        for model in ['rdd2022-yolov8n','rdd2022-yolov8s']:
            report={'schema_version':'1.0','event_id':model,'image':{'sha256':hashlib.sha256(JPEG).hexdigest()},
                'inference':{'model_id':model,'mode':'road_damage',
                    'detections':[{'class_id':0,'label':'longitudinal_crack','confidence':.9}]}}
            result=self.engine.receive_report('PHONE-01',{'report':report,'image_base64':JPEG_BASE64})
            raw=json.loads(self.engine.db.execute('SELECT payload FROM reports WHERE id=?',(result['server_event_id'],)).fetchone()[0])
            self.assertEqual(raw,report)
        self.assertEqual({r['model'] for r in self.engine.report_list()},{'rdd2022-yolov8n','rdd2022-yolov8s'})


class LiveHTTPTests(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.now=time.time()
        self.engine=app.Engine(Path(self.folder.name)/'http.sqlite3',lambda:self.now)
        self.env=patch.dict(app.os.environ,{'WV_DEVICE_TOKENS':json.dumps({'PHONE-01':TOKEN,'PHONE-02':TOKEN+'two'})})
        self.env.start();self.server=app.Server(('127.0.0.1',0),self.engine)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()

    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.engine.db.close();self.env.stop();self.folder.cleanup()

    def request(self,method='GET',suffix='',body=None,token=None,headers=None,device='PHONE-01'):
        h={'Content-Type':'image/jpeg'}
        if token:h['Authorization']='Bearer '+token
        h.update(headers or {})
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=3)
        c.request(method,'/api/v1/devices/'+device+'/live'+suffix,body,h)
        r=c.getresponse();result=(r.status,r.read(),dict(r.getheaders()));c.close();return result

    def test_upload_auth_isolation_and_etag_original_bytes(self):
        self.assertEqual(self.request('POST',body=JPEG)[0],401)
        self.assertEqual(self.request('POST',body=JPEG,token=TOKEN,device='PHONE-02')[0],401)
        s,b,_=self.request('POST',body=JPEG,token=TOKEN,headers={'X-WV-Test-Data':'1'})
        self.assertEqual(s,200);self.assertTrue(json.loads(b)['test_data'])
        s,b,h=self.request(suffix='/frame.jpg');self.assertEqual(s,200);self.assertEqual(b,JPEG)
        self.assertEqual(h['Cache-Control'],'no-store');self.assertEqual(h['Content-Type'],'image/jpeg')
        self.assertIn("img-src 'self' data: blob:",h['Content-Security-Policy'])
        s,b,_=self.request(suffix='/frame.jpg',headers={'If-None-Match':h['ETag']});self.assertEqual(s,304);self.assertEqual(b,b'')
        self.now+=2.01;self.assertEqual(self.request(suffix='/frame.jpg')[0],410)

    def test_uploads_bypass_ip_120_per_minute_but_device_still_has_15_fps_limit(self):
        for i in range(130):
            self.now+=.08;self.assertEqual(self.request('POST',body=JPEG,token=TOKEN)[0],200)
        self.now+=2
        for i in range(15):self.assertEqual(self.request('POST',body=JPEG,token=TOKEN)[0],200)
        status,_,h=self.request('POST',body=JPEG,token=TOKEN)
        self.assertEqual(status,429);self.assertEqual(h['Retry-After'],'1')

    def test_stop_and_inflight_gate_and_input_limits(self):
        self.assertEqual(self.request('POST',body=JPEG,token=TOKEN)[0],200)
        self.assertEqual(self.request('POST','/stop',b'{}',TOKEN,{'Content-Type':'application/json'})[0],200)
        self.assertEqual(json.loads(self.request()[1])['state'],'stopped')
        self.assertEqual(self.request(suffix='/frame.jpg')[0],410)
        gate=self.server.live_gates['PHONE-01'];gate.acquire()
        try:self.assertEqual(self.request('POST',body=JPEG,token=TOKEN)[0],429)
        finally:gate.release()
        self.assertEqual(self.request('POST',body=b'bad',token=TOKEN)[0],400)
        self.assertEqual(self.request('POST',body=b'{}',token=TOKEN,headers={'Content-Type':'application/json'})[0],415)
        self.assertEqual(self.request('POST',body=b'',token=TOKEN,headers={'Content-Length':'262145'})[0],413)

    def test_mobile_route_and_small_snapshot(self):
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=3)
        c.request('GET','/mobile/?device=PHONE-01');r=c.getresponse();self.assertEqual(r.status,200)
        self.assertIn('随行巡检',r.read().decode());c.close()
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=3)
        c.request('GET','/api/v1/mobile');r=c.getresponse();d=json.loads(r.read());c.close()
        self.assertEqual(len(d['devices']),13);self.assertLessEqual(len(d['events']),3)
        self.assertNotIn('reports',d);self.assertNotIn('trend',d)


if __name__=='__main__':unittest.main()
