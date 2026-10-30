"""Behavior tests; synthetic test data only, isolated temporary SQLite databases."""
from datetime import datetime, timezone
import http.client
import importlib.util
import json
import math
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

SPEC=importlib.util.spec_from_file_location('dashboard',Path(__file__).resolve().parents[1]/'server.py')
app=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(app)


class SimulationTests(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory()
        self.path=Path(self.folder.name)/'test.sqlite3'
        self.now=time.time()
        self.engine=app.Engine(self.path,lambda:self.now)

    def tearDown(self):
        self.engine.db.close()
        self.folder.cleanup()

    def advance(self,seconds):
        for _ in range(seconds):
            self.now+=1;self.engine.tick()

    def test_exact_inventory_and_pending_real_slots(self):
        snapshot=self.engine.snapshot()
        self.assertEqual(len(snapshot['devices']),13)
        self.assertEqual(len([d for d in snapshot['devices'] if d['type']=='fixed']),6)
        self.assertEqual(len([d for d in snapshot['devices'] if d['type']=='mobile']),4)
        self.assertTrue(all(d['status']=='pending' and not d['location_valid'] for d in snapshot['devices'] if d['source']=='real'))

    def test_fixed_stay_fixed_mobile_continuous_at_speed(self):
        initial={d['id']:(d['lon'],d['lat']) for d in self.engine.devices}
        for _ in range(180):
            previous={d['id']:(d['lon'],d['lat']) for d in self.engine.devices}
            self.advance(1)
            for d in self.engine.devices:
                loc=(d['lon'],d['lat'])
                if d['type']=='fixed':
                    self.assertEqual(initial[d['id']],loc)
                else:
                    self.assertLessEqual(app.distance(previous[d['id']],loc),d['speed']/3.6+.1)
        self.assertGreater(self.engine.snapshot()['stats']['distance_km'],1)

    def test_offline_and_paused_stop_detections_and_heartbeats(self):
        for _ in range(180):
            self.advance(1)
            for event in self.engine.sim_events:
                if event['timestamp']==self.now and event['kind']!='device':
                    device=next(d for d in self.engine.devices if d['id']==event['device_id'])
                    self.assertEqual(device['status'],'online')
        messages=[e['message'] for e in self.engine.sim_events if e['kind']=='device']
        self.assertIn('设备离线，已停止上报',messages)
        self.assertIn('连接已恢复，继续巡检',messages)
        self.assertIn('车辆临时停靠，暂停巡检',messages)

    def test_pause_freezes_position_counts_and_elapsed(self):
        self.advance(10)
        self.engine.control({'action':'pause'})
        before=self.engine.snapshot()
        self.advance(60)
        after=self.engine.snapshot()
        self.assertEqual(before['devices'][:10],after['devices'][:10])
        self.assertEqual(before['stats'],after['stats'])
        self.assertEqual(before['simulation']['elapsed'],after['simulation']['elapsed'])
        self.engine.control({'action':'resume'})
        self.advance(2)
        self.assertGreater(self.engine.snapshot()['stats']['distance_km'],after['stats']['distance_km'])

    def test_detections_have_cooldown(self):
        self.advance(360)
        latest={}
        for e in sorted(self.engine.sim_events,key=lambda e:e['timestamp']):
            key=(e['device_id'],e['kind'])
            if e['kind']!='device' and key in latest:
                self.assertGreaterEqual(e['timestamp']-latest[key],45)
            latest[key]=e['timestamp']

    def test_route_reversal_has_no_teleport(self):
        d=self.engine.devices[6]
        route=self.engine.routes[d['route_index']]
        d['distance_m']=route['lengths'][-1]-d['offset']-2
        self.engine.place(d);before=(d['lon'],d['lat'])
        self.advance(1)
        self.assertLess(app.distance(before,(d['lon'],d['lat'])),9)

    def test_reset_changes_event_ids(self):
        old={e['id'] for e in self.engine.sim_events}
        self.engine.reset()
        self.assertFalse(old & {e['id'] for e in self.engine.sim_events})

    def heartbeat(self):
        return self.engine.heartbeat('PHONE-01',{'lat':29.563,'lon':106.578,'fps':24,'battery':86})

    def event(self):
        return {'event_id':'course-test-1','kind':'pothole','confidence':.91,'observed_at':datetime.now(timezone.utc).isoformat(),'source':'sim'}

    def test_real_requires_online_heartbeat(self):
        with self.assertRaises(ValueError):self.engine.ingest('PHONE-01',self.event())
        self.heartbeat();self.advance(31)
        with self.assertRaises(ValueError):self.engine.ingest('PHONE-01',self.event())

    def test_real_event_source_dedup_persistence_and_reset(self):
        self.heartbeat()
        result=self.engine.ingest('PHONE-01',self.event())
        self.assertEqual(result['event']['source'],'real')
        self.assertFalse(result['duplicate'])
        self.assertTrue(self.engine.ingest('PHONE-01',self.event())['duplicate'])
        self.engine.reset()
        self.engine.db.close();self.engine=app.Engine(self.path,lambda:self.now)
        events=[e for e in self.engine.snapshot()['events'] if e['source']=='real']
        self.assertEqual(len(events),1)
        self.assertTrue(self.engine.snapshot()['devices'][10]['location_valid'])
        self.assertEqual(self.engine.db.execute('SELECT COUNT(*) FROM events').fetchone()[0],1)

    def test_real_stales_even_when_simulation_paused(self):
        self.heartbeat();self.engine.control({'action':'pause'});self.advance(31)
        self.assertEqual(self.engine.snapshot()['devices'][10]['status'],'offline')

    def test_invalid_metrics_timestamps_coordinates(self):
        for field,value in [('lat',91),('lon',float('nan')),('fps',True),('battery',101),('speed',float('inf'))]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                self.engine.heartbeat('PHONE-01',{'lat':29,'lon':106,field:value})
        self.heartbeat()
        for value in ['2026-01-01T00:00:00Z','2026-10-17','bad',None]:
            with self.subTest(timestamp=value),self.assertRaises(ValueError):
                self.engine.ingest('PHONE-01',{**self.event(),'observed_at':value})

    def test_counts_match_sources_and_events(self):
        self.advance(60);self.heartbeat();self.engine.ingest('PHONE-01',self.event())
        snap=self.engine.snapshot()
        self.assertEqual(snap['stats']['events'],len(snap['events']))
        self.assertEqual(snap['stats']['events'],sum(snap['distribution'].values()))
        self.assertEqual(snap['stats']['anomalies'],sum(e['kind'] in ['pothole','crack'] for e in snap['events']))


class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder=tempfile.TemporaryDirectory()
        cls.engine=app.Engine(Path(cls.folder.name)/'test.sqlite3')
        cls.server=app.Server(('127.0.0.1',0),cls.engine)
        cls.port=cls.server.server_port
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join();cls.engine.db.close();cls.folder.cleanup()

    def request(self,method,path,body=None,headers=None):
        conn=http.client.HTTPConnection('127.0.0.1',self.port,timeout=3)
        payload=json.dumps(body) if body is not None else None
        conn.request(method,path,payload,{'Content-Type':'application/json',**(headers or {})})
        res=conn.getresponse();status=res.status;data=res.read();h=dict(res.getheaders());conn.close()
        return status,data,h

    def test_static_api_and_no_directory_listing_or_traversal(self):
        self.assertEqual(self.request('GET','/')[0],200)
        self.assertEqual(self.request('GET','/api/v1/health')[0],200)
        self.assertEqual(self.request('GET','/api/v1/snapshot')[0],200)
        for path in ['/vendor/','/../server.py','/%2e%2e/server.py','/.git/config','/api/nope']:
            self.assertEqual(self.request('GET',path)[0],404,path)

    def test_missing_token_is_closed(self):
        with patch.dict(os.environ,{'WV_DEVICE_TOKENS':'{}'}):
            self.assertEqual(self.request('POST','/api/v1/devices/PHONE-01/heartbeat',{'lat':29,'lon':106})[0],503)

    def test_token_is_per_device_and_never_in_snapshot(self):
        secret='temporary-unit-test-token-for-phone-01'
        with patch.dict(os.environ,{'WV_DEVICE_TOKENS':json.dumps({'PHONE-01':secret})}):
            endpoint='/api/v1/devices/PHONE-01/heartbeat'
            body={'lat':29.563,'lon':106.578}
            self.assertEqual(self.request('POST',endpoint,body)[0],401)
            self.assertEqual(self.request('POST',endpoint,body,{'Authorization':'Bearer '+secret})[0],200)
            self.assertEqual(self.request('POST','/api/v1/devices/PHONE-02/heartbeat',body,{'Authorization':'Bearer '+secret})[0],503)
            self.assertNotIn(secret.encode(),self.request('GET','/api/v1/snapshot')[1])

    def test_cross_origin_control_rejected_same_origin_allowed(self):
        path='/api/v1/simulation'
        self.assertEqual(self.request('POST',path,{'action':'pause'},{'Origin':'https://other.example'})[0],403)
        self.assertEqual(self.request('POST',path,{'action':'pause'},{'Origin':f'http://127.0.0.1:{self.port}'})[0],200)

    def test_reject_bad_json_and_media_type(self):
        path='/api/v1/simulation'
        self.assertEqual(self.request('POST',path,[])[0],400)
        self.assertEqual(self.request('POST',path,{'action':'bad'})[0],400)
        self.assertEqual(self.request('POST',path,{'action':'pause'},{'Content-Type':'text/plain'})[0],415)
        self.assertEqual(self.request('POST',path,{'padding':'x'*17000})[0],413)

    def test_security_headers(self):
        _,_,headers=self.request('GET','/')
        self.assertIn("script-src 'self'",headers['Content-Security-Policy'])
        self.assertEqual(headers['X-Content-Type-Options'],'nosniff')


if __name__=='__main__':
    unittest.main()
