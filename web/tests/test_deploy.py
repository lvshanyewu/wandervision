import importlib.util
from pathlib import Path
import unittest

SPEC=importlib.util.spec_from_file_location('deployment',Path(__file__).resolve().parents[1]/'deploy/deploy.py')
deploy=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(deploy)


class ConfigTests(unittest.TestCase):
    def test_named_block_only_nested_and_quoted_braces(self):
        source='''server { server_name other.site; location / { return 200 "}"; } }
server { # } comment must be ignored
server_name lvshanyewu.site www.lvshanyewu.site;
location /api/ { proxy_pass http://backend; }
}'''
        result=deploy.patch_config(source,'172.19.0.1')
        self.assertEqual(result.split('\n')[0],source.split('\n')[0])
        self.assertEqual(result.count(deploy.BEGIN),1)
        self.assertIn('proxy_pass http://172.19.0.1:5065/;',result)
        self.assertIn('location /api/ { proxy_pass http://backend; }',result)
        self.assertEqual(result.count('proxy_request_buffering off;'),3)
        for device in ['PHONE-01','PHONE-02','PHONE-03']:
            self.assertIn(f'location = /wandervision/api/v1/devices/{device}/live/stream',result)
        self.assertIn('client_max_body_size 6m;',result)
        self.assertEqual(result.count('client_max_body_size 16m;'),3)

    def test_repeatable_and_can_update_gateway(self):
        first=deploy.patch_config('server { server_name lvshanyewu.site; }','172.19.0.1')
        self.assertEqual(first,deploy.patch_config(first,'172.19.0.1'))
        updated=deploy.patch_config(first,'172.20.0.1')
        self.assertEqual(updated.count(deploy.BEGIN),1)
        self.assertNotIn('172.19.0.1',updated)

    def test_ambiguous_missing_or_unmanaged_route_rejected(self):
        for source in ['server { server_name example.org; }',
                       'server { server_name lvshanyewu.site; location /wandervision/ {} }',
                       'server { server_name lvshanyewu.site; } server { server_name lvshanyewu.site; }']:
            with self.assertRaises(ValueError):deploy.patch_config(source,'172.19.0.1')

    def test_gateway_shell_injection_rejected(self):
        with self.assertRaises(ValueError):deploy.patch_config('','172.19.0.1; echo bad')


if __name__=='__main__':unittest.main()
