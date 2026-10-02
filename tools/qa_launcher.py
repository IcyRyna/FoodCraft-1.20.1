"""Launch the packaged JAR with official loader profiles in an isolated Windows instance."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile
import ctypes
import sys
import platform
from ctypes import wintypes

ROOT=Path(__file__).resolve().parents[1]
HOST_OS={'win32':'windows','linux':'linux','darwin':'osx'}.get(sys.platform,sys.platform)

def prepare_guard(java:Path,work:Path)->Path:
    source=ROOT/'tools/qa_guard/org/foodcraft/qa/InputGuard.java'
    fingerprint=hashlib.sha256(source.read_bytes()).hexdigest()
    root=work/'qa-guard'/fingerprint;root.mkdir(parents=True,exist_ok=True)
    artifact=root/'foodcraft-qa-guard.jar'
    if artifact.exists():return artifact
    javac=java.resolve().with_name('javac.exe' if os.name=='nt' else 'javac');jar=java.resolve().with_name('jar.exe' if os.name=='nt' else 'jar')
    if not javac.exists() or not jar.exists():raise RuntimeError('The isolated QA launcher requires a complete JDK 17 for its input/audio guard')
    classes=root/'classes';classes.mkdir(exist_ok=True)
    subprocess.run([str(javac),'--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','-d',str(classes),str(source)],check=True)
    manifest=root/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: org.foodcraft.qa.InputGuard\n\n','utf-8')
    subprocess.run([str(jar),'--create','--file',str(artifact),'--manifest',str(manifest),'-C',str(classes),'.'],check=True)
    return artifact

def silent_options(directory:Path):
    path=directory/'options.txt'
    options={}
    if path.exists():
        for line in path.read_text('utf-8').splitlines():
            key,separator,value=line.partition(':')
            if separator:options[key]=value
    options.update(soundCategory_master='0.0',rawMouseInput='false',pauseOnLostFocus='false')
    path.write_text(''.join(key+':'+value+'\n' for key,value in options.items()),'utf-8')

def run_on_private_desktop(command:list[str],directory:Path,visible:bool=False):
    """Give test windows their own desktop without switching the user's input desktop."""
    if os.name!='nt':
        if os.environ.get('FOODCRAFT_QA_VIRTUAL_DISPLAY')!='true' or not os.environ.get('DISPLAY'):
            raise RuntimeError('Non-Windows QA requires its own explicitly configured virtual display')
        return subprocess.run(command,cwd=directory).returncode
    user32=ctypes.WinDLL('user32',use_last_error=True);kernel32=ctypes.WinDLL('kernel32',use_last_error=True)
    user32.CreateDesktopW.argtypes=[wintypes.LPCWSTR,wintypes.LPCWSTR,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p]
    user32.CreateDesktopW.restype=wintypes.HANDLE
    user32.CloseDesktop.argtypes=[wintypes.HANDLE]
    name='FoodCraftQA_'+str(os.getpid())
    # No DESKTOP_SWITCHDESKTOP permission and no SwitchDesktop call.
    desktop=None if visible else user32.CreateDesktopW(name,None,None,0,0xC7,None)
    if not visible and not desktop:raise ctypes.WinError(ctypes.get_last_error())
    class StartupInfo(ctypes.Structure):
        _fields_=[('cb',wintypes.DWORD),('reserved',wintypes.LPWSTR),('desktop',wintypes.LPWSTR),('title',wintypes.LPWSTR),
            ('x',wintypes.DWORD),('y',wintypes.DWORD),('width',wintypes.DWORD),('height',wintypes.DWORD),('chars_x',wintypes.DWORD),('chars_y',wintypes.DWORD),
            ('fill',wintypes.DWORD),('flags',wintypes.DWORD),('show',wintypes.WORD),('reserved_size',wintypes.WORD),('reserved_pointer',ctypes.c_void_p),
            ('stdin',wintypes.HANDLE),('stdout',wintypes.HANDLE),('stderr',wintypes.HANDLE)]
    class ProcessInfo(ctypes.Structure):
        _fields_=[('process',wintypes.HANDLE),('thread',wintypes.HANDLE),('pid',wintypes.DWORD),('tid',wintypes.DWORD)]
    kernel32.CreateProcessW.argtypes=[wintypes.LPCWSTR,wintypes.LPWSTR,ctypes.c_void_p,ctypes.c_void_p,wintypes.BOOL,wintypes.DWORD,ctypes.c_void_p,wintypes.LPCWSTR,ctypes.POINTER(StartupInfo),ctypes.POINTER(ProcessInfo)]
    kernel32.CreateProcessW.restype=wintypes.BOOL
    kernel32.GetStdHandle.argtypes=[wintypes.DWORD];kernel32.GetStdHandle.restype=wintypes.HANDLE
    kernel32.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
    kernel32.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
    kernel32.CloseHandle.argtypes=[wintypes.HANDLE]
    info=StartupInfo();info.cb=ctypes.sizeof(info);info.desktop=None if visible else 'WinSta0\\'+name;info.flags=0x101;info.show=1 if visible else 0
    info.stdin=kernel32.GetStdHandle(-10&0xffffffff);info.stdout=kernel32.GetStdHandle(-11&0xffffffff);info.stderr=kernel32.GetStdHandle(-12&0xffffffff)
    process=ProcessInfo();line=ctypes.create_unicode_buffer(subprocess.list2cmdline(command))
    try:
        if not kernel32.CreateProcessW(str(command[0]),line,None,None,True,0x200,None,str(directory),ctypes.byref(info),ctypes.byref(process)):raise ctypes.WinError(ctypes.get_last_error())
        print('Client running on', 'visible desktop' if visible else 'private desktop '+name, 'PID',process.pid,flush=True)
        while kernel32.WaitForSingleObject(process.process,1000)==0x102:pass
        code=wintypes.DWORD()
        if not kernel32.GetExitCodeProcess(process.process,ctypes.byref(code)):raise ctypes.WinError(ctypes.get_last_error())
        return code.value
    finally:
        if process.thread:kernel32.CloseHandle(process.thread)
        if process.process:kernel32.CloseHandle(process.process)
        if desktop:user32.CloseDesktop(desktop)

def fetch(url:str,path:Path,sha1:str|None=None):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and (not sha1 or hashlib.sha1(path.read_bytes()).hexdigest()==sha1):return path
    temporary=path.with_name(path.name+'.download')
    subprocess.run(['curl.exe' if os.name=='nt' else 'curl','--fail','--silent','--show-error','--location','--retry','3','--output',str(temporary),url],check=True)
    if sha1 and hashlib.sha1(temporary.read_bytes()).hexdigest()!=sha1:raise ValueError('Download SHA-1 mismatch: '+url)
    temporary.replace(path)
    return path

def read_json(url:str,path:Path):return json.loads(fetch(url,path).read_text('utf-8'))

def allowed(rules:list[dict],features:dict|None=None):
    result=not rules
    for rule in rules:
        os_rule=rule.get('os',{})
        if os_rule.get('name',HOST_OS)!=HOST_OS:continue
        if os_rule.get('arch','x86_64') not in {'x86_64','amd64'}:continue
        if 'version' in os_rule and not re.search(os_rule['version'],os.environ.get('OS',platform.release())):continue
        if any((features or {}).get(key,False)!=value for key,value in rule.get('features',{}).items()):continue
        result=rule['action']=='allow'
    return result

def arguments(values:list,variables:dict):
    result=[]
    for value in values:
        if isinstance(value,dict):
            if not allowed(value.get('rules',[])):continue
            value=value['value']
        for text in value if isinstance(value,list) else [value]:
            text=re.sub(r'\$\{([^}]+)\}',lambda match:variables[match[1]],text)
            result.append(text)
    return result

def prepare(args):
    if not re.fullmatch(r'[A-Za-z0-9_]{1,16}',args.username):raise ValueError('Minecraft QA usernames must contain 1–16 ASCII letters, digits or underscores')
    work=args.work.resolve();runtime=work/('client-runtime-'+args.loader);runtime.mkdir(parents=True,exist_ok=True)
    manifests=work/'launcher-metadata'
    manifest=read_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',manifests/'versions.json')
    reference=next(v for v in manifest['versions'] if v['id']=='1.20.1')
    vanilla=json.loads(fetch(reference['url'],manifests/'1.20.1.json',reference.get('sha1')).read_text('utf-8'))
    game_jar=runtime/'versions/1.20.1/1.20.1.jar'
    cached=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
    if not game_jar.exists() and cached.exists() and hashlib.sha1(cached.read_bytes()).hexdigest()==vanilla['downloads']['client']['sha1']:
        game_jar.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(cached,game_jar)
    fetch(vanilla['downloads']['client']['url'],game_jar,vanilla['downloads']['client']['sha1'])
    (game_jar.parent/'1.20.1.json').write_text(json.dumps(vanilla),'utf-8')
    if args.loader=='forge':
        forge_file=runtime/'versions/1.20.1-forge-47.4.10/1.20.1-forge-47.4.10.json'
        if not forge_file.exists():
            (runtime/'launcher_profiles.json').write_text('{"profiles":{}}','utf-8')
            installer=fetch('https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar',work/'installers/forge-installer.jar')
            with (work/'forge-client-install.log').open('w',encoding='utf-8') as log:
                subprocess.run([str(args.java),'-jar',str(installer),'--installClient',str(runtime)],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,check=True)
        profile=json.loads(forge_file.read_text('utf-8'))
    else:
        profile=read_json('https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.19.5/profile/json',manifests/'fabric-client.json')
    libraries={}
    for lib in vanilla['libraries']+profile.get('libraries',[]):
        name=lib['name'].split(':');key=':'.join(name[:2]+name[3:])
        libraries[key]=lib
    paths=[];natives=runtime/'natives'/args.username/('+'.join(sorted(args.compat)) or 'none');natives.mkdir(parents=True,exist_ok=True)
    for lib in libraries.values():
        if not allowed(lib.get('rules',[])):continue
        artifact=lib.get('downloads',{}).get('artifact')
        if not artifact:
            parts=lib['name'].split(':');group,name,version=parts[:3];classifier='-'+parts[3] if len(parts)>3 else ''
            relative=group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+classifier+'.jar'
            artifact={'path':relative,'url':lib.get('url','https://libraries.minecraft.net/')+relative}
        path=runtime/'libraries'/artifact['path']
        if not path.exists():
            # Reuse verified artifacts from the independent server installation when available.
            for root in [work/'production-forge/libraries',work/'production-fabric/libraries']:
                source=root/artifact['path']
                if source.exists() and (not artifact.get('sha1') or hashlib.sha1(source.read_bytes()).hexdigest()==artifact['sha1']):
                    path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,path);break
            if not path.exists():
                group,name,version=lib['name'].split(':')[:3]
                cache=Path.home()/'.gradle/caches/modules-2/files-2.1'/group/name/version
                for source in cache.glob('*/'+Path(artifact['path']).name):
                    if not artifact.get('sha1') or hashlib.sha1(source.read_bytes()).hexdigest()==artifact['sha1']:
                        path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,path);break
        fetch(artifact['url'],path,artifact.get('sha1'));paths.append(path)
        native_classifier={'windows':'natives-windows','linux':'natives-linux','osx':'natives-macos'}[HOST_OS]
        if native_classifier in lib['name']:
            with zipfile.ZipFile(path) as archive:
                for entry in archive.infolist():
                    extension={'windows':'.dll','linux':'.so','osx':'.dylib'}[HOST_OS]
                    if entry.filename.lower().endswith(extension) and (entry.filename.startswith(HOST_OS+'/x64/') or '/' not in entry.filename and not lib['name'].endswith(('-arm64','-x86'))):
                        target=natives/Path(entry.filename).name;data=archive.read(entry)
                        if target.exists():
                            if target.read_bytes()!=data:raise ValueError('Cached native library differs from the pinned artifact: '+str(target))
                        else:
                            temporary=target.with_name(target.name+'.'+uuid.uuid4().hex+'.tmp');temporary.write_bytes(data);temporary.replace(target)
    paths.append(game_jar)
    assets=work/'launcher-assets'
    index=json.loads(fetch(vanilla['assetIndex']['url'],assets/'indexes'/f'{vanilla["assetIndex"]["id"]}.json',vanilla['assetIndex'].get('sha1')).read_text('utf-8'))
    for asset in index['objects'].values():
        checksum=asset['hash'];target=assets/'objects'/checksum[:2]/checksum
        if not target.exists():
            for base in [Path.home()/'.gradle/caches/forge_gradle/assets',Path.home()/'.gradle/caches/fabric-loom/assets']:
                source=base/'objects'/checksum[:2]/checksum
                if source.exists() and hashlib.sha1(source.read_bytes()).hexdigest()==checksum:
                    target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);break
        fetch(f'https://resources.download.minecraft.net/{checksum[:2]}/{checksum}',target,checksum)
    game_dir=work/'release-clients'/args.loader/args.username/('+'.join(sorted(args.compat)) or 'none');game_dir.mkdir(parents=True,exist_ok=True)
    silent_options(game_dir)
    guard=prepare_guard(args.java,work)
    mods=game_dir/'mods';mods.mkdir(exist_ok=True)
    jar=args.jar.resolve() if args.jar else ROOT/'dist'/f'foodcraft-1.20.1-{args.loader}-2.0.0.jar'
    if not jar.exists():jar=ROOT/args.loader/'build/libs'/jar.name
    if not jar.exists():raise FileNotFoundError('Build the release JAR first: '+str(jar))
    with zipfile.ZipFile(jar) as archive:
        if b'stacksTo' in archive.read('org/foodcraft/mixin/LegacyStackSizeMixin.class'):
            raise ValueError('QA launcher refuses an intermediate, unremapped development JAR. Complete the build and freeze a separate snapshot first.')
    shutil.copyfile(jar,mods/jar.name)
    if args.loader=='fabric':
        api=fetch('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.2%2B1.20.1/fabric-api-0.92.2%2B1.20.1.jar',work/'compat/fabric-api-0.92.2+1.20.1.jar')
        shutil.copyfile(api,mods/api.name)
    for kind in args.compat:
        name=f'{kind}-{args.loader}-'+('15.12.2.51' if kind=='jei' else '14.0.60')+'.jar'
        shutil.copyfile(work/'compat'/name,mods/name)
    for extra in args.extra_mod:
        if extra.suffix.lower()!='.jar' or not extra.is_file():raise ValueError('Invalid additional compatibility mod JAR: '+str(extra))
        shutil.copyfile(extra.resolve(),mods/extra.name)
    if 'crafttweaker' in args.compat:
        scripts=game_dir/'scripts';scripts.mkdir(exist_ok=True)
        shutil.copyfile(ROOT/'examples/crafttweaker/validation.zs',scripts/'foodcraft-validation.zs')
    if args.mode=='single' and not (game_dir/'saves/FoodCraftQA').exists():
        source=work/('run-forge' if args.loader=='forge' else 'run-fabric-client')/'saves/FoodCraftQA'
        shutil.copytree(source,game_dir/'saves/FoodCraftQA',ignore=shutil.ignore_patterns('session.lock'))
    variables={'auth_player_name':args.username,'version_name':profile['id'],'game_directory':str(game_dir),'assets_root':str(assets),
        'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+args.username).encode()).digest(),version=3)),
        'auth_access_token':'0','clientid':'FoodCraftQA','auth_xuid':'0','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720',
        'natives_directory':str(natives),'launcher_name':'FoodCraft-validation','launcher_version':'2.0.0','classpath':os.pathsep.join(map(str,paths)),
        'library_directory':str(runtime/'libraries'),'classpath_separator':os.pathsep,'primary_jar':str(game_jar)}
    vm=arguments(vanilla['arguments']['jvm']+profile.get('arguments',{}).get('jvm',[]),variables)
    if args.loader=='forge':
        vm=[value+','+game_jar.name if value.startswith('-DignoreList=') else value for value in vm]
    game=arguments(vanilla['arguments']['game']+profile.get('arguments',{}).get('game',[]),variables)
    evidence=work/'evidence'/f'release-{args.loader}-{args.username}'/uuid.uuid4().hex
    command=[str(args.java.resolve()),'-Xmx'+args.heap,'-XX:ActiveProcessorCount=2','--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
             '-Dfoodcraft.qa.sandbox=true','-Dfoodcraft.qa.server=true','-javaagent:'+str(guard),'-Dfile.encoding=UTF-8','-Duser.language=en','-Dfoodcraft.qa.details='+str(args.details).lower(),'-Dfoodcraft.qa.audit='+str(args.audit).lower(),'-Dfoodcraft.qa.stress='+str(args.stress).lower(),'-Dfoodcraft.qa.visual='+str(args.visual).lower(),'-Dfoodcraft.qa.actor='+args.actor,'-Dfoodcraft.verify.client='+args.mode,
             '-Dfoodcraft.evidence.dir='+str(evidence),'-Dfoodcraft.qa.address='+args.server,'-Dfoodcraft.qa.edges='+str(args.edges).lower(),'-Dfoodcraft.qa.negative='+str(args.negative).lower(),'-Dfoodcraft.qa.auditStart='+str(args.audit_start),'-Dfoodcraft.qa.visual.filter='+args.visual_filter]+vm+[profile['mainClass']]+game
    return command,game_dir,evidence

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--loader',choices=['forge','fabric'],required=True)
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--heap',default='2G')
    default_work=ROOT.parent.parent/'work' if ROOT.parent.name=='outputs' else ROOT.parent/'foodcraft-work'
    parser.add_argument('--work',type=Path,default=Path(os.environ.get('FOODCRAFT_WORK_DIR',str(default_work))))
    parser.add_argument('--username',default='FoodCraftQA1')
    parser.add_argument('--mode',choices=['single','multi'],default='single')
    parser.add_argument('--server',default='127.0.0.1:25575')
    parser.add_argument('--compat',nargs='*',choices=['jei','crafttweaker'],default=[])
    parser.add_argument('--details',action='store_true')
    parser.add_argument('--audit',action='store_true')
    parser.add_argument('--audit-start',type=int,default=0)
    parser.add_argument('--negative',action='store_true')
    parser.add_argument('--edges',action='store_true')
    parser.add_argument('--use-lifecycle',action='store_true')
    parser.add_argument('--stress',action='store_true')
    parser.add_argument('--visual',action='store_true')
    parser.add_argument('--visual-filter',default='')
    parser.add_argument('--actor',default='1')
    parser.add_argument('--visible',action='store_true',help='Explicitly show the guarded, silent test client on the normal desktop')
    parser.add_argument('--jar',type=Path)
    parser.add_argument('--extra-mod',type=Path,action='append',default=[])
    args=parser.parse_args()
    if not 0<=args.audit_start<=756:parser.error('audit-start must be between 0 and 756')
    command,directory,evidence=prepare(args)
    print('Launching packaged FoodCraft JAR:',args.loader,args.username,flush=True)
    code=run_on_private_desktop(command,directory,args.visible)
    if code:raise SystemExit(code)
    failed=evidence/'client-failed.txt'
    if failed.exists():raise RuntimeError('Client verification failed: '+failed.read_text('utf-8'))
    if not (evidence/'client-complete.txt').exists():raise RuntimeError('Client exited without a completion marker: '+str(evidence))
    guard=evidence/'qa-guard.txt'
    if not guard.exists() or 'glfw=true audio=true' not in guard.read_text('utf-8'):
        raise RuntimeError('Client verification did not prove both native input and audio guards were active')
    print('Verified native guard completion:',guard.read_text('utf-8').strip(),flush=True)
    print('Release-client completion evidence:',evidence,flush=True)

if __name__=='__main__':main()
