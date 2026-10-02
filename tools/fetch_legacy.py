"""Download and verify the exact legacy commit used by the resource generator."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import zipfile

SHA='523515c4988e485adbcf5eb51c73caec71d5361d'
ARCHIVE_SHA256='cdb75d67582bdfc351e622c73fa14f05e24fb3841ab668ab892cda5fd57f86bb'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--work',type=Path,default=Path(__file__).resolve().parents[3]/'work');args=parser.parse_args()
    work=args.work.resolve();work.mkdir(parents=True,exist_ok=True);archive=work/'foodcraft-legacy.zip'
    if not archive.exists():subprocess.run(['curl.exe','--fail','--location','--retry','3','--output',str(archive),'https://codeload.github.com/FlyInTheSky10/FoodCraft/zip/'+SHA],check=True)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=ARCHIVE_SHA256:raise ValueError('Legacy archive SHA-256 mismatch')
    target=work/'legacy';target.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as package:
        for entry in package.infolist():
            destination=target/entry.filename
            destination.resolve().relative_to(target.resolve())
            if entry.is_dir():destination.mkdir(parents=True,exist_ok=True);continue
            value=package.read(entry)
            if destination.exists():
                if destination.read_bytes()!=value:raise ValueError('Existing legacy reference was modified: '+str(destination))
            else:destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(value)
    print(target/('FoodCraft-'+SHA))

if __name__=='__main__':main()
