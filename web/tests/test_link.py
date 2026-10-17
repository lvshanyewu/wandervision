import base64
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import http.client
import threading

from test_server import app
from jpeg_fixture import JPEG_BASE64


class LinkTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.path=Path(self.temp.name)/'data.sqlite3'
        self.now=time.time();self.engine=app.Engine(self.path,lambda:self.now)
        self.image=base64.b64decode(JPEG_BASE64)

    def tearDown(self):
        self.engine.db.close();self.temp.cleanup()

    def pair(self):
        code=self.engine.pairing_code({'device_id':'PHONE-01'})['code']
        return self.engine.pair({'code':code})

    def body(self,event_id='test-001'):
        return {'report':{'schema_version':'1.0','event_id':event_id,'captured_at':datetime.now(timezone.utc).isoformat(),
            'image':{'sha256':hashlib.sha256(self.image).hexdigest(),'width':64,'height':48},
            'location':{'latitude':29.4903657,'longitude':106.567229,'status':'fresh','mock':True},
            'inference':{'detections':[{'label':'car','confidence':.9}]}},'image_base64':JPEG_BASE64}

    def test_pair_one_use_hash_only_and_persistence(self):
        code=self.engine.pairing_code({'device_id':'PHONE-01'})['code']
        result=self.engine.pair({'code':code});token=result['token']
        self.assertTrue(self.engine.authorize('PHONE-01','Bearer '+token))
        self.assertFalse(self.engine.authorize('PHONE-02','Bearer '+token))
        self.assertNotEqual(self.engine.db.execute('SELECT token_hash FROM credentials').fetchone()[0],token)
        with self.assertRaises(ValueError):self.engine.pair({'code':code})
        self.engine.db.close();self.engine=app.Engine(self.path,lambda:self.now)
        self.assertTrue(self.engine.authorize('PHONE-01','Bearer '+token))
        self.assertNotIn(token,json.dumps(self.engine.snapshot()))

    def test_pair_expired_and_reissued_codes(self):
        first=self.engine.pairing_code({'device_id':'PHONE-01'})['code']
        second=self.engine.pairing_code({'device_id':'PHONE-01'})['code']
        if first!=second:
            with self.assertRaises(ValueError):self.engine.pair({'code':first})
        self.now+=301
        with self.assertRaises(ValueError):self.engine.pair({'code':second})

    def test_no_gps_phone_is_online_without_fake_coordinate(self):
        self.engine.heartbeat('PHONE-01',{'lat':None,'lon':None,'status':'online','battery':50})
        d=self.engine.snapshot()['devices'][10]
        self.assertEqual(d['status'],'online');self.assertFalse(d['location_valid'])
        self.assertIn('等待 GPS',d['location'])
        with self.assertRaises(ValueError):self.engine.heartbeat('PHONE-01',{'lat':29.49,'lon':None})

    def test_photo_dedup_and_no_invented_damage_detection(self):
        self.pair()
        result=self.engine.receive_report('PHONE-01',self.body());again=self.engine.receive_report('PHONE-01',self.body())
        self.assertFalse(result['duplicate']);self.assertTrue(again['duplicate'])
        self.assertEqual(result['server_event_id'],again['server_event_id'])
        snap=self.engine.snapshot();events=[e for e in snap['events'] if e['source']=='real']
        self.assertEqual(len(events),1);self.assertEqual(events[0]['kind'],'report')
        self.assertEqual(snap['reports'][0]['objects'],1);self.assertTrue(snap['reports'][0]['location']['mock'])
        self.assertEqual(self.engine.db.execute('SELECT image FROM reports').fetchone()[0],self.image)

    def test_invalid_hash_jpeg_dimensions_and_schema(self):
        for key,value in [('schema_version','0'),('event_id','../escape')]:
            body=self.body();body['report'][key]=value
            with self.assertRaises(ValueError):self.engine.receive_report('PHONE-01',body)
        body=self.body();body['report']['image']['sha256']='0'*64
        with self.assertRaises(ValueError):self.engine.receive_report('PHONE-01',body)
        body=self.body();body['report']['image']['width']=1000
        with self.assertRaises(ValueError):self.engine.receive_report('PHONE-01',body)
        body=self.body();body['image_base64']='not a JPEG'
        with self.assertRaises(ValueError):self.engine.receive_report('PHONE-01',body)
        self.assertEqual(self.engine.storage()['reports'],0)

    def test_upload_without_location_stays_unlocated(self):
        body=self.body();body['report']['location']={'latitude':None,'longitude':None,'status':'searching'}
        self.engine.receive_report('PHONE-01',body)
        d=self.engine.snapshot()['devices'][10]
        self.assertFalse(d['location_valid'])
        event=next(e for e in self.engine.snapshot()['events'] if e['source']=='real')
        self.assertIsNone(event['lat']);self.assertIsNone(event['lon'])

    def test_manual_clear_reclaims_and_optionally_preserves_pairing(self):
        token=self.pair()['token'];self.engine.receive_report('PHONE-01',self.body())
        result=self.engine.maintenance({'action':'clear','keep_pairing':True})
        self.assertEqual(result['storage']['reports'],0);self.assertEqual(result['storage']['events'],0)
        self.assertEqual(self.engine.snapshot()['stats']['events'],0)
        self.assertTrue(self.engine.authorize('PHONE-01','Bearer '+token))
        self.assertEqual(self.engine.snapshot()['devices'][10]['status'],'pending')
        self.engine.maintenance({'action':'clear','keep_pairing':False})
        self.assertFalse(self.engine.authorize('PHONE-01','Bearer '+token))

    def test_retention_count_and_age_apply_to_photos_and_events(self):
        with patch.object(app,'MAX_REPORTS',2):
            for i in range(3):
                self.now+=1;self.engine.receive_report('PHONE-01',self.body(f'test-{i}'))
        self.assertEqual(self.engine.storage()['reports'],2)
        self.assertEqual(self.engine.storage()['events'],2)
        self.now+=8*86400;self.engine.prune()
        self.assertEqual(self.engine.storage()['reports'],0);self.assertEqual(self.engine.storage()['events'],0)

    def test_all_presets_and_campus_inventory(self):
        for scenario in ['normal','busy','roadwork','offline','night']:
            self.engine.control({'action':'scenario','scenario':scenario})
            self.assertEqual(self.engine.snapshot()['simulation']['scenario'],scenario)
        self.assertAlmostEqual(self.engine.snapshot()['campus']['lat'],29.4903657)
        for d in self.engine.devices:
            self.assertTrue(29.46<d['lat']<29.53)




class PhotoHTTPTests(unittest.TestCase):
    def test_http_pair_upload_original_jpeg_duplicate_and_cleanup(self):
        with tempfile.TemporaryDirectory() as folder:
            engine=app.Engine(Path(folder)/'http.sqlite3');server=app.Server(('127.0.0.1',0),engine)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            def req(method,path,body=None,token=None):
                headers={'Content-Type':'application/json'}
                if token:headers['Authorization']='Bearer '+token
                conn=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=4)
                conn.request(method,path,json.dumps(body) if body is not None else None,headers)
                r=conn.getresponse();result=(r.status,r.read(),dict(r.getheaders()));conn.close();return result
            try:
                s,b,_=req('POST','/api/v1/pairing',{'device_id':'PHONE-01'});self.assertEqual(s,200)
                code=json.loads(b)['code'];s,b,_=req('POST','/api/v1/pair',{'code':code});self.assertEqual(s,200);token=json.loads(b)['token']
                s,_,_=req('POST','/api/v1/devices/PHONE-01/heartbeat',{'lat':None,'lon':None},token);self.assertEqual(s,200)
                raw=base64.b64decode(JPEG_BASE64);comment=b'\xff\xfe'+(60002).to_bytes(2,'big')+b'x'*60000
                raw=raw[:2]+comment*3+raw[2:]
                body={'report':{'schema_version':'1.0','event_id':'http-test-photo','image':{'sha256':hashlib.sha256(raw).hexdigest(),'width':64,'height':48},'location':{},'inference':{'detections':[]}},'image_base64':base64.b64encode(raw).decode()}
                endpoint='/api/v1/devices/PHONE-01/reports'
                self.assertEqual(req('POST',endpoint,body)[0],401)
                s,b,_=req('POST',endpoint,body,token);self.assertEqual(s,200);receipt=json.loads(b);rid=receipt['server_event_id']
                s,b,_=req('POST',endpoint,body,token);self.assertEqual(s,200);self.assertTrue(json.loads(b)['duplicate'])
                s,b,h=req('GET','/api/v1/reports/'+rid+'/image');self.assertEqual(s,200);self.assertEqual(b,raw);self.assertEqual(h['Content-Type'],'image/jpeg')
                s,b,_=req('GET','/api/v1/reports/'+rid);self.assertEqual(json.loads(b)['report']['event_id'],'http-test-photo')
                s,b,_=req('POST','/api/v1/maintenance',{'action':'clear'});self.assertEqual(s,200);self.assertEqual(json.loads(b)['storage']['reports'],0);self.assertGreater(json.loads(b)['freed_bytes'],100000)
                self.assertEqual(req('GET','/api/v1/reports/'+rid+'/image')[0],404)
            finally:
                server.shutdown();server.server_close();thread.join();engine.db.close()


if __name__=='__main__':unittest.main()
