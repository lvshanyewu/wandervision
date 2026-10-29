"""Targeted deployment to an existing myseoul/startpage host. Requires Paramiko.

SSH connection values come only from DEPLOY_HOST/PORT/USER/PASSWORD or SSH keys.
No credentials or production config are printed or downloaded to local files.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import socket
import time

ROOT='/opt/wandervision'
UNIT='/etc/systemd/system/wandervision.service'
CONFIG='/opt/cloud/landing/nginx.conf'
SITE='lvshanyewu.site'
PORT=5065
BEGIN='    # BEGIN WANDERVISION'
END='    # END WANDERVISION'
HERE=Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def patch_config(source,gateway):
    if not re.fullmatch(r'\d{1,3}(\.\d{1,3}){3}',gateway):
        raise ValueError('Invalid Docker gateway')
    streams='\n'.join(f'''    location = /wandervision/api/v1/devices/{device}/live/stream {{
        proxy_pass http://{gateway}:{PORT}/api/v1/devices/{device}/live/stream;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_request_buffering off;
        proxy_buffering off;
        proxy_read_timeout 75s;
        proxy_send_timeout 10s;
        client_body_timeout 5s;
        client_max_body_size 16m;
    }}''' for device in ['PHONE-01','PHONE-02','PHONE-03'])
    block=f'''{BEGIN}
    location = /wandervision {{ return 301 /wandervision/; }}
{streams}
    location ^~ /wandervision/ {{
        proxy_pass http://{gateway}:{PORT}/;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 20s;
        client_max_body_size 6m;
        gzip on;
        gzip_min_length 1024;
        gzip_types application/json application/geo+json application/javascript text/javascript text/css image/svg+xml;
    }}
{END}'''
    if BEGIN in source:
        if source.count(BEGIN)!=1 or source.count(END)!=1:
            raise ValueError('Ambiguous existing dashboard block')
        return re.sub(re.escape(BEGIN)+r'.*?'+re.escape(END),lambda m:block,source,flags=re.S)
    if '/wandervision' in source:
        raise ValueError('An unmanaged dashboard route already exists')
    # Nginx braces in comments/quoted strings must not change nesting depth.
    tokens=list(re.finditer(r'#[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|\bserver\s*\{|[{}]',source))
    blocks=[]
    for index,token in enumerate(tokens):
        if not re.fullmatch(r'server\s*\{',token.group()):
            continue
        depth=1
        for end in tokens[index+1:]:
            if end.group()=='{': depth+=1
            elif end.group()=='}': depth-=1
            if depth==0:
                chunk=source[token.end():end.start()]
                names=re.findall(r'\bserver_name\s+([^;]+);',re.sub(r'#[^\n]*','',chunk))
                if any(SITE in name.split() for name in names):
                    blocks.append(end.start())
                break
    if len(blocks)!=1:
        raise ValueError('Expected exactly one named main-site server block')
    return source[:blocks[0]]+'\n'+block+'\n'+source[blocks[0]:]


def service_unit(gateway):
    return f'''[Unit]
Description=wandervision road inspection demo
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
DynamicUser=yes
StateDirectory=wandervision
WorkingDirectory={ROOT}/current
Environment=PYTHONDONTWRITEBYTECODE=1
EnvironmentFile=-/etc/wandervision.env
ExecStart=/usr/bin/python3 -u {ROOT}/current/server.py --host {gateway} --port {PORT} --db /var/lib/wandervision/wandervision.sqlite3
Restart=on-failure
RestartSec=3
MemoryMax=128M
CPUQuota=25%
TasksMax=64
NoNewPrivileges=yes
PrivateTmp=yes
ProtectHome=yes
ProtectSystem=strict
UMask=0077

[Install]
WantedBy=multi-user.target
'''


def run(args):
    import paramiko
    config_path=getattr(args,'config',None) or os.environ.get('WV_DEPLOY_CONFIG')
    settings=json.loads(Path(config_path).read_text(encoding='utf-8-sig')) if config_path else {}
    host=os.environ.get('DEPLOY_HOST') or settings.get('host')
    if not host:raise ValueError('Set DEPLOY_HOST or --config / WV_DEPLOY_CONFIG; no built-in server IP')
    port=int(os.environ.get('DEPLOY_PORT') or settings.get('port',22))
    bind_address=os.environ.get('DEPLOY_BIND_ADDRESS') or settings.get('bind_address')
    alias=os.environ.get('DEPLOY_HOST_KEY_ALIAS') or settings.get('host_key_alias')
    client=paramiko.SSHClient();client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    if alias:
        trusted=client.get_host_keys().lookup(alias) or client._system_host_keys.lookup(alias)
        if not trusted:raise ValueError('Trusted host-key alias not found in known_hosts')
        target=host if port==22 else '['+host+']:'+str(port)
        for name,key in trusted.items():client.get_host_keys().add(target,name,key)
    connection=None
    if bind_address:
        connection=socket.socket(socket.AF_INET,socket.SOCK_STREAM);connection.settimeout(20)
        connection.bind((bind_address,0));connection.connect((host,port))
    client.connect(host,port=port,sock=connection,
                   username=os.environ.get('DEPLOY_USER') or settings.get('user','root'),password=os.environ.get('DEPLOY_PASSWORD'),
                   timeout=20,banner_timeout=30)
    sftp=client.open_sftp()
    def command(cmd):
        _,out,err=client.exec_command(cmd,timeout=45)
        result,error=out.read().decode(),err.read().decode()
        if out.channel.recv_exit_status():
            raise RuntimeError('Remote operation failed: '+error[-350:])
        return result.strip()
    def read(path,optional=False):
        try:
            with sftp.open(path,'rb') as handle:return handle.read()
        except FileNotFoundError:
            if optional:return None
            raise
    def write(path,data,mode=0o644):
        with sftp.open(path,'wb') as handle:handle.write(data)
        sftp.chmod(path,mode)
        if read(path)!=data:raise RuntimeError('Upload byte verification failed')
    def restore(folder,manifest):
        if sha(read(CONFIG))!=manifest['config_after']:
            raise RuntimeError('Nginx has changed since deployment; refusing to overwrite concurrent edits')
        config=read(folder+'/nginx.conf')
        if sha(config)!=manifest['config_before']:raise RuntimeError('Config backup checksum mismatch')
        write(CONFIG,config,manifest.get('config_mode',0o644))  # Keep bind-mounted inode.
        command('docker exec startpage nginx -t')
        command('docker exec startpage nginx -s reload')
        if manifest['old_release']:
            command('ln -sfn '+shlex.quote(manifest['old_release'])+' '+ROOT+'/current.rollback && mv -Tf '+ROOT+'/current.rollback '+ROOT+'/current')
            old_unit=read(folder+'/wandervision.service')
            write(UNIT,old_unit)
            command('systemctl daemon-reload && systemctl restart wandervision')
        else:
            command('systemctl disable --now wandervision')
        print('Rolled back dashboard only. Releases, database and backups are preserved.')
    try:
        if args.rollback:
            if not re.fullmatch(re.escape(ROOT)+r'/backups/[0-9TZ-]+',args.rollback):raise ValueError('Invalid backup path')
            restore(args.rollback,json.loads(read(args.rollback+'/manifest.json')));return
        mounts=json.loads(command('docker inspect startpage --format '+shlex.quote('{{json .Mounts}}')))
        if not any(m['Source']==CONFIG and m['Destination']=='/etc/nginx/conf.d/default.conf' for m in mounts):
            raise RuntimeError('Unexpected Nginx bind mount')
        networks=json.loads(command('docker inspect startpage --format '+shlex.quote('{{json .NetworkSettings.Networks}}')))
        gateway=next(iter(networks.values()))['Gateway']
        config=read(CONFIG);config_mode=sftp.stat(CONFIG).st_mode & 0o777
        revised=patch_config(config.decode(),gateway).encode()
        old_release=command('readlink -f '+ROOT+'/current 2>/dev/null || true')
        old_unit=read(UNIT,True)
        if old_release and not old_release.startswith(ROOT+'/releases/'):
            raise RuntimeError('Unexpected existing dashboard release')
        if bool(old_release)!=bool(old_unit):raise RuntimeError('Partial deployment exists; inspect before modifying')
        if not old_release and command("ss -H -tln 'sport = :5065'"):
            raise RuntimeError('Port 5065 already in use')
        command('docker exec startpage nginx -t')
        print('Preflight passed: named main-site route, bind mount, private gateway and dashboard port verified.')
        if args.check:return
        stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        release=ROOT+'/releases/'+stamp;folder=ROOT+'/backups/'+stamp
        command('mkdir -p '+release+'/static '+folder+' && chmod 700 '+folder)
        backup_code="import pathlib,sqlite3,os; p=pathlib.Path('/var/lib/wandervision/wandervision.sqlite3'); b="+repr(folder+'/wandervision.sqlite3')+"; src=sqlite3.connect(p) if p.exists() else None; dst=sqlite3.connect(b) if src else None; src.backup(dst) if src else None; dst.close() if dst else None; src.close() if src else None; os.chmod(b,0o600) if p.exists() else None"
        command('python3 -c '+shlex.quote(backup_code))
        payload={name:(HERE/name).read_bytes() for name in ['server.py','data_center.py']}
        payload.update({'static/'+p.relative_to(HERE/'static').as_posix():p.read_bytes() for p in (HERE/'static').rglob('*') if p.is_file()})
        if any('qa-' in name for name in payload):raise RuntimeError('Temporary browser QA artifacts must not be deployed')
        manifest={'release':release,'old_release':old_release,'config_before':sha(config),'config_after':sha(revised),
                  'config_mode':config_mode,'files':{p:sha(b) for p,b in payload.items()}}
        write(folder+'/nginx.conf',config,0o600)
        if old_unit:write(folder+'/wandervision.service',old_unit,0o600)
        write(folder+'/manifest.json',json.dumps(manifest,indent=2).encode(),0o600)
        for parent in sorted({str(Path(release+'/'+name).parent).replace('\\','/') for name in payload}):
            command('mkdir -p '+shlex.quote(parent))
        for name,data in payload.items():
            target=release+'/'+name
            write(target,data)
        command('python3 -m py_compile '+release+'/server.py '+release+'/data_center.py')
        write(release+'/manifest.json',json.dumps(manifest['files']).encode())
        unit=service_unit(gateway).encode()
        if sha(read(CONFIG))!=manifest['config_before']:raise RuntimeError('Nginx changed during upload, stopping before activation')
        activated=False
        try:
            write(UNIT,unit)
            command('ln -sfn '+shlex.quote(release)+' '+ROOT+'/current.next && mv -Tf '+ROOT+'/current.next '+ROOT+'/current')
            activated=True
            command('systemctl daemon-reload && systemctl enable wandervision && systemctl restart wandervision')
            for attempt in range(10):
                try:
                    health=command('curl -fsS --max-time 3 http://'+gateway+':5065/api/v1/health')
                    if json.loads(health)['ok']:break
                except RuntimeError:
                    time.sleep(.5)
            else:raise RuntimeError('Dashboard service health check failed')
            if sha(read(CONFIG))!=manifest['config_before']:raise RuntimeError('Concurrent Nginx edit; manual route activation required')
            write(CONFIG,revised,config_mode)
            command('docker exec startpage nginx -t')
            command('docker exec startpage nginx -s reload')
            health=command("curl -fsS --max-time 8 -H 'Host: lvshanyewu.site' http://127.0.0.1/wandervision/api/v1/health")
            if not json.loads(health)['ok']:raise RuntimeError('Proxy health check failed')
        except Exception:
            if activated:
                current=sha(read(CONFIG))
                if current==manifest['config_before']:
                    # Restore expects the deployed hash; no route change occurred yet.
                    manifest['config_after']=current
                restore(folder,manifest)
            raise
        print('Deployed: https://'+SITE+'/wandervision/')
        print('Rollback: python web/deploy/deploy.py --rollback '+folder)
        print(command('systemctl show wandervision -p ActiveState -p MemoryCurrent -p CPUUsageNSec'))
    finally:
        sftp.close();client.close()


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--rollback')
    parser.add_argument('--config',help='Private JSON connection profile; credentials remain in environment')
    run(parser.parse_args())
