"""Run an isolated, localhost-only packaged-JAR acceptance server.

The template supplies an already installed official loader runtime. Worlds,
configuration, mods, logs and the command queue always belong to this QA profile.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import threading
import time
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def copy_library(source:Path,target:Path):
    try:os.link(source,target)
    except OSError:shutil.copyfile(source,target)
    return str(target)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader',choices=['forge','fabric'],required=True)
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--template',type=Path,required=True)
    parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--name',default='run')
    parser.add_argument('--port',type=int,required=True)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--compat',nargs='*',choices=['jei','crafttweaker'],default=[])
    parser.add_argument('--extra-mod',type=Path,action='append',default=[])
    parser.add_argument('--heap',default='1G')
    args=parser.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9_-]{0,47}',args.name):parser.error('name must be a short lowercase profile name')
    if not 1024<=args.port<=65535:parser.error('port must be between 1024 and 65535')
    java=args.java.resolve(strict=True);template=args.template.resolve(strict=True);jar=args.jar.resolve(strict=True)
    version=subprocess.run([str(java),'-version'],capture_output=True,text=True).stderr
    if not re.search(r'version "17\.',version):raise RuntimeError('QA server requires an explicit Java 17 executable')
    with zipfile.ZipFile(jar) as archive:
        if b'stacksTo' in archive.read('org/foodcraft/mixin/LegacyStackSizeMixin.class'):
            raise ValueError('Complete the remapped release build before starting the QA server')
    # Never attach to or stop an existing process that owns this port.
    with socket.socket() as check:check.bind(('127.0.0.1',args.port))
    work=args.work.resolve();profile=work/'qa-servers'/args.loader/args.name;profile.mkdir(parents=True,exist_ok=True)
    descriptor={'loader':args.loader,'port':args.port,'compat':sorted(set(args.compat)),
                'extra_mods':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in args.extra_mod}}
    manifest=profile/'profile.json'
    if manifest.exists() and json.loads(manifest.read_text('utf-8'))!=descriptor:
        raise ValueError('Existing QA profile has different ports or optional mods; choose a new --name')
    manifest.write_text(json.dumps(descriptor,indent=2)+'\n','utf-8')
    for name in ['libraries','versions']:
        source=template/name;target=profile/name
        if source.is_dir() and not target.exists():shutil.copytree(source,target,copy_function=copy_library)
    if args.loader=='fabric':
        source=template/'.fabric/server';target=profile/'.fabric/server'
        if source.is_dir() and not target.exists():shutil.copytree(source,target)
        shutil.copyfile(template/'fabric-server-launch.jar',profile/'fabric-server-launch.jar')
        entry=['-jar','fabric-server-launch.jar']
    else:
        arg_file=profile/'libraries/net/minecraftforge/forge/1.20.1-47.4.10'/('win_args.txt' if os.name=='nt' else 'unix_args.txt')
        if not arg_file.is_file():raise FileNotFoundError('Template does not contain Forge 47.4.10 server arguments')
        entry=['@'+str(arg_file.relative_to(profile))]
    mods=profile/'mods';mods.mkdir(exist_ok=True);shutil.copyfile(jar,mods/jar.name)
    checksums=json.loads((ROOT/'docs/dependency-checksums.json').read_text('utf-8'))
    def install(path:Path):
        if not path.is_file():raise FileNotFoundError(path)
        expected=checksums.get(path.name)
        if expected and hashlib.sha256(path.read_bytes()).hexdigest()!=expected:raise ValueError('Optional mod checksum mismatch: '+path.name)
        shutil.copyfile(path,mods/path.name)
    if args.loader=='fabric':install(work/'compat/fabric-api-0.92.2+1.20.1.jar')
    for kind in sorted(set(args.compat)):
        install(work/'compat'/(f'{kind}-{args.loader}-'+('15.12.2.51' if kind=='jei' else '14.0.60')+'.jar'))
    for path in args.extra_mod:install(path.resolve(strict=True))
    if 'crafttweaker' in args.compat:
        scripts=profile/'scripts';scripts.mkdir(exist_ok=True)
        shutil.copyfile(ROOT/'examples/crafttweaker/validation.zs',scripts/'foodcraft-validation.zs')
    (profile/'eula.txt').write_text('eula=true\n','utf-8')
    flat=json.dumps({'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'lakes':False,'features':False,'structure_overrides':[]},separators=(',',':'))
    (profile/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings={flat}\nmax-tick-time=180000\nmax-players=4\n','utf-8')
    (profile/'ready.txt').unlink(missing_ok=True)
    commands=profile/'commands.txt';commands.write_text('','utf-8');offset=0;ready=False
    stamp=time.strftime('%Y%m%d-%H%M%S',time.gmtime());log=profile/('server-'+stamp+'.log')
    flags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
    process=subprocess.Popen([str(java),'-Xmx'+args.heap,'-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dfoodcraft.qa.server=true']+entry+['nogui'],cwd=profile,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=flags)
    (profile/'process.json').write_text(json.dumps({'pid':process.pid,'port':args.port,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'log':str(log),'command_file':str(commands)},indent=2)+'\n','utf-8')
    messages=queue.Queue()
    def read_output():
        for line in process.stdout:messages.put(line)
    threading.Thread(target=read_output,daemon=True).start()
    print('QA server profile:',profile,'port:',args.port,'PID:',process.pid,flush=True)
    print('Append Minecraft console commands to:',commands,flush=True)
    with log.open('w',encoding='utf-8') as output:
        try:
            while process.poll() is None:
                try:
                    line=messages.get(timeout=.1);output.write(line);output.flush()
                    if not ready and 'Done (' in line and 'For help' in line:
                        ready=True;(profile/'ready.txt').write_text(stamp,'utf-8');print('QA server ready',flush=True)
                except queue.Empty:pass
                content=commands.read_text('utf-8');new=content[offset:];offset=len(content)
                if new:process.stdin.write(new);process.stdin.flush()
        except KeyboardInterrupt:
            process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=60)
        finally:
            if process.poll() is None:
                process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=60)
            while not messages.empty():output.write(messages.get_nowait())
            output.write('FoodCraft QA server exited '+str(process.returncode)+'\n')
    if not ready:raise RuntimeError('Server did not reach startup completion; see '+str(log))
    if process.returncode:raise SystemExit(process.returncode)

if __name__=='__main__':main()
