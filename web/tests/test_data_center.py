"""Course data workflow tests using synthetic JPEG and isolated SQLite only."""
import base64
import csv
import hashlib
import http.client
import io
import json
from datetime import datetime,timezone
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from test_server import app
from jpeg_fixture import JPEG_BASE64


class LedgerTests(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.now=time.time();self.path=Path(self.folder.name)/'data.sqlite3'
        self.engine=app.Engine(self.path,lambda:self.now);self.ledger=self.engine.ledger

    def tearDown(self):self.engine.db.close();self.folder.cleanup()

    def body(self,event='course-record'):
        return {'report':{'schema_version':'1.0','event_id':event,'captured_at':datetime.fromtimestamp(self.now,timezone.utc).isoformat(),
            'source':{'device_model':'sdk_gphone_TEST'},'capture_context':{'site_name':'南岸校门','note':'课设采集说明'},
            'image':{'sha256':hashlib.sha256(base64.b64decode(JPEG_BASE64)).hexdigest()},
            'location':{'latitude':None,'longitude':None,'status':'searching'},
            'inference':{'model_id':'rdd2022-yolov8n','mode':'road_damage','detections':[{'class_id':3,'label':'pothole','confidence':.87,'bbox_xywh_normalized':[.1,.2,.3,.4]}]},
            'plate_recognition':{'readings':[{'text':'TEST-PLATE','confidence':.9}]},'experimental_analysis':{'events':[]}},'image_base64':JPEG_BASE64}

    def test_simulation_becomes_persistent_rows_and_reset_keeps_history(self):
        rows=self.ledger.listing({'source':'sim','page_size':100})
        self.assertEqual(rows['total'],24);self.assertTrue(all(r['source']=='sim' and r['detections'] for r in rows['rows']))
        for r in rows['rows']:self.assertEqual(r['objects'],len(r['detections']))
        self.engine.control({'action':'reset'});self.assertEqual(self.ledger.summary()['total'],48)
        self.engine.db.close();self.engine=app.Engine(self.path,lambda:self.now);self.ledger=self.engine.ledger
        self.assertEqual(self.ledger.summary()['total'],72)

    def test_report_maps_context_targets_ocr_without_inventing_gps(self):
        receipt=self.engine.receive_report('PHONE-01',self.body());r=self.ledger.detail(receipt['record_id'])
        self.assertTrue(r['test_data']);self.assertEqual(r['category'],'pothole');self.assertEqual(r['location'],'南岸校门')
        self.assertIsNone(r['lat']);self.assertEqual(r['capture_context']['note'],'课设采集说明')
        self.assertEqual(r['detections'][0]['class_id'],3);self.assertEqual(r['detections'][0]['bbox_xywh_normalized'],[.1,.2,.3,.4])
        self.assertEqual(r['capture']['plate_recognition']['readings'][0]['text'],'TEST-PLATE');self.assertEqual(r['status'],'pending')

    def test_duplicate_cannot_rewrite_original_ledger_or_review(self):
        a=self.engine.receive_report('PHONE-01',self.body());r=self.ledger.detail(a['record_id'])
        self.ledger.review({'ids':[r['id']],'status':'confirmed','note':'人工复查','expected_versions':{r['id']:1}})
        body=self.body();body['report']['capture_context']['site_name']='试图覆盖'
        b=self.engine.receive_report('PHONE-01',body);self.assertTrue(b['duplicate']);self.assertEqual(a['record_id'],b['record_id'])
        r=self.ledger.detail(a['record_id']);self.assertEqual(r['location'],'南岸校门');self.assertEqual(r['status'],'confirmed')
        self.assertEqual(self.ledger.listing({'source':'real'})['total'],1)

    def test_review_batch_is_atomic_and_logs_versions(self):
        rows=self.ledger.listing({})['rows'];a,b=rows[0],rows[1]
        self.ledger.review({'ids':[a['id']],'status':'confirmed','priority':'urgent','tags':['复查'],'note':'待安排','expected_versions':{a['id']:1}})
        with self.assertRaises(app.ReviewConflict):self.ledger.review({'ids':[b['id'],a['id']],'status':'resolved','expected_versions':{a['id']:1,b['id']:1}})
        self.assertEqual(self.ledger.detail(b['id'])['status'],'pending')
        r=self.ledger.detail(a['id']);self.assertEqual(r['version'],2);self.assertEqual(len(r['reviews']),1)
        self.assertEqual(r['reviews'][0]['after']['tags'],['复查'])

    def test_filters_paging_summary_and_attention_follow_review(self):
        receipt=self.engine.receive_report('PHONE-01',self.body());key=receipt['record_id']
        self.assertEqual(self.ledger.listing({'source':'test','q':'南岸校门'})['total'],1)
        r=self.ledger.summary({'source':'test'});self.assertEqual(r['attention'],1);self.assertEqual(r['pending'],1)
        self.ledger.review({'ids':[key],'status':'rejected'})
        self.assertEqual(self.ledger.summary({'source':'test'})['attention'],0)
        self.assertEqual(self.ledger.listing({'page_size':'3','page':'2'})['page'],2)
        self.assertEqual(len(self.ledger.listing({'page_size':'3'})['rows']),3)
        with self.assertRaises(ValueError):self.ledger.listing({'status':'bad'})
        with self.assertRaises(ValueError):self.ledger.summary({'start':'not-a-date'})

    def test_filtered_exports_match_rows_and_keep_yolo_fields(self):
        receipt=self.engine.receive_report('PHONE-01',self.body());self.ledger.review({'ids':[receipt['record_id']],'note':'=1+1','tags':['课设']})
        raw=b''.join(self.ledger.export({'source':'test'},'csv'));self.assertTrue(raw.startswith(b'\xef\xbb\xbf'))
        lines=list(csv.DictReader(io.StringIO(raw.decode('utf-8-sig'))));self.assertEqual(len(lines),1)
        self.assertEqual(lines[0]['复核备注'],"'=1+1");self.assertIn('pothole',lines[0]['目标详情JSON'])
        data=json.loads(b''.join(self.ledger.export({'source':'test'},'json')))
        self.assertEqual(data['rows'][0]['capture_context']['site_name'],'南岸校门');self.assertEqual(data['rows'][0]['detections'][0]['confidence'],.87)

    def test_report_is_self_contained_and_escapes_submitted_strings(self):
        body=self.body();body['report']['capture_context']['site_name']='<script>bad()</script>'
        self.engine.receive_report('PHONE-01',body)
        report=self.ledger.report_html({'source':'test'})
        self.assertIn('道路巡检具身智能体',report);self.assertIn('<svg',report);self.assertIn('需要关注的条目',report)
        self.assertNotIn('<script>bad()',report);self.assertIn('&lt;script&gt;',report);self.assertNotIn('<script src=',report)

    def test_photo_pruning_keeps_structured_row_and_clear_removes_both(self):
        with patch.object(app,'MAX_REPORTS',0):receipt=self.engine.receive_report('PHONE-01',self.body())
        row=self.ledger.detail(receipt['record_id']);self.assertFalse(row['report_available']);self.assertEqual(row['objects'],1)
        self.ledger.review({'ids':[row['id']],'priority':'important'})
        self.engine.maintenance({'action':'clear','keep_pairing':True})
        self.assertEqual(self.ledger.summary()['total'],0);self.assertIsNone(self.ledger.detail(row['id']))
        self.assertEqual(self.engine.db.execute('SELECT COUNT(*) FROM review_log').fetchone()[0],0)

    def test_old_capture_date_is_preserved_until_receipt_retention_expires(self):
        body=self.body();body['report']['captured_at']='2020-01-01T01:00:00Z'
        receipt=self.engine.receive_report('PHONE-01',body);self.assertTrue(self.ledger.detail(receipt['record_id'])['observed_at'].startswith('2020-01-01'))
        self.now+=31*86400;self.ledger.prune();self.assertIsNone(self.ledger.detail(receipt['record_id']))


class LedgerHTTPTests(unittest.TestCase):
    def test_view_review_export_report_and_origin_boundary(self):
        with tempfile.TemporaryDirectory() as folder:
            engine=app.Engine(Path(folder)/'http.sqlite3');server=app.Server(('127.0.0.1',0),engine)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            def request(method,path,body=None,headers=None):
                c=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=4);h={'Content-Type':'application/json'};h.update(headers or {})
                c.request(method,path,json.dumps(body) if body is not None else None,h);r=c.getresponse();result=r.status,r.read(),dict(r.getheaders());c.close();return result
            try:
                s,b,_=request('GET','/admin/');self.assertEqual(s,200);self.assertIn('巡检数据台账',b.decode())
                s,b,_=request('GET','/api/v1/records?source=sim&page_size=2');self.assertEqual(s,200);rows=json.loads(b)['rows'];key=rows[0]['id']
                body={'ids':[key],'status':'confirmed','expected_versions':{key:1}}
                self.assertEqual(request('POST','/api/v1/records/review',body,{'Origin':'https://wrong.example'})[0],403)
                self.assertEqual(request('POST','/api/v1/records/review',body)[0],200)
                self.assertEqual(request('POST','/api/v1/records/review',body)[0],409)
                s,b,h=request('GET','/api/v1/records/export?format=csv&status=confirmed');self.assertEqual(s,200);self.assertIn('attachment',h['Content-Disposition'])
                self.assertEqual(len(list(csv.DictReader(io.StringIO(b.decode('utf-8-sig'))))),1)
                s,b,_=request('GET','/api/v1/records/export?format=json&status=confirmed');self.assertEqual(len(json.loads(b)['rows']),1)
                s,b,_=request('GET','/report/?source=sim');self.assertEqual(s,200);self.assertIn('巡检数据可视化报告',b.decode())
                self.assertEqual(request('GET','/api/v1/records?status=bad')[0],400)
            finally:server.shutdown();server.server_close();thread.join();engine.db.close()


if __name__=='__main__':unittest.main()
