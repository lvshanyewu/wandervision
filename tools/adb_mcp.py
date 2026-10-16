"""Small stdio MCP bridge for the already installed adb-mcp server."""
import json
import os
import subprocess
import sys

env = os.environ.copy()
env.update(JAVA_HOME=r"D:\AndroidStudio\jbr", ANDROID_HOME=r"D:\AndroidStudioSDK",
           GRADLE_USER_HOME=r"D:\Androiddevedoc\.gradle-fresh")
p = subprocess.Popen([r"D:\Androiddevedoc\adb-mcp\adb-mcp.exe", "-sdk", r"D:\AndroidStudioSDK"],
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
    if len(sys.argv) == 1:
        result = rpc(2, "tools/list", {})
        print(json.dumps([t for t in result["result"]["tools"] if any(s in t["name"] for s in ("gradle", "device", "emulator"))], ensure_ascii=False, indent=2))
    else:
        print(json.dumps(rpc(2, "tools/call", {"name":sys.argv[1], "arguments":json.loads(sys.argv[2]) if len(sys.argv)>2 else {}}), ensure_ascii=False, indent=2))
finally:
    p.terminate()
