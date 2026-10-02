"""Compare canonical snapshots exported by each loader's actual GameTest server."""
import json
import sys
from pathlib import Path

def main():
    forge,fabric=[json.loads(Path(value).read_text('utf-8')) for value in sys.argv[1:3]]
    if forge!=fabric:
        for group in ['items','recipes','blocks']:
            left={value['id']:value for value in forge[group]};right={value['id']:value for value in fabric[group]}
            for key in sorted(left.keys()|right.keys()):
                if left.get(key)!=right.get(key):print('Loader mismatch:',key,json.dumps(left.get(key)),json.dumps(right.get(key)))
        raise SystemExit(1)
    print('Forge/Fabric runtime snapshots match:',len(forge['items']),'items;',len(forge['blocks']),'blocks;',len(forge['recipes']),'recipes')

if __name__=='__main__':main()
