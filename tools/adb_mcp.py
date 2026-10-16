"""Small stdio MCP bridge for the already installed adb-mcp server."""
import json
import os
import argparse
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server', default=os.environ.get('ADB_MCP', 'adb-mcp'))
parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
parser.add_argument('--gradle-user-home', default=os.environ.get('GRADLE_USER_HOME'))
parser.add_argument('tool', nargs='?')
parser.add_argument('arguments', nargs='?', default='{}')
args = parser.parse_args()
if not args.sdk:
    parser.error('Set ANDROID_HOME or pass --sdk')
env = os.environ.copy()
env['ANDROID_HOME'] = args.sdk
if args.java_home: env['JAVA_HOME'] = args.java_home
if args.gradle_user_home: env['GRADLE_USER_HOME'] = args.gradle_user_home
p = subprocess.Popen([args.server, "-sdk", args.sdk],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                     text=True, encoding="utf-8", env=env)
def rpc(i, method, params):
    p.stdin.write(json.dumps(dict(jsonrpc="2.0", id=i, method=method, params=params)) + "\n")
    p.stdin.flush()
    for line in p.stdout:
        obj = json.loads(line)
        if obj.get("id") == i:
            return obj
try:
    rpc(1, "initialize", {"protocolVersion":"2025-06-18", "capabilities":{}, "clientInfo":{"name":"wandervision", "version":"0.1"}})
    p.stdin.write('{"jsonrpc":"2.0","method":"notifications/initialized"}\n')
    p.stdin.flush()
    if not args.tool:
        result = rpc(2, "tools/list", {})
        print(json.dumps([t for t in result["result"]["tools"] if any(s in t["name"] for s in ("gradle", "device", "emulator"))], ensure_ascii=False, indent=2))
    else:
        result = rpc(2, "tools/call", {"name":args.tool, "arguments":json.loads(args.arguments)})
        print(json.dumps(result, ensure_ascii=False, indent=2))
        if result.get('error') or result.get('result', {}).get('isError'):
            sys.exit(1)
finally:
    p.terminate()
