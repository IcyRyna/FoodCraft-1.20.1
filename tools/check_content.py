"""Fail closed on missing IDs, invalid recipes, resources, language keys and unsafe layouts."""
from __future__ import annotations
import json
import sys
import re
from pathlib import Path
from collections import Counter

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'common/src/main/resources'


def check():
    manifest=json.loads((ROOT/'docs/content-manifest.json').read_text('utf-8'))
    entries=manifest['entries']
    errors=[]
    registered=[r for r in entries if r['kind']!='debug' and not r['id'].startswith('minecraft:')]
    ids={r['id'] for r in registered}
    counts=Counter(r['id'] for r in registered)
    errors.extend('Duplicate ID '+key for key,count in counts.items() if count>1)
    if any(r['id'] in {'foodcraft:yangrou','foodcraft:shuyangrou'} for r in registered):errors.append('Duplicate vanilla mutton')
    languages={name:json.loads((RES/f'assets/foodcraft/lang/{name}.json').read_text('utf-8')) for name in ['zh_cn','zh_tw','en_us']}
    for row in registered:
        path=row['id'].split(':')[1]
        prefix='block' if 'constructor_args' in row else 'item'
        for locale,values in languages.items():
            if f'{prefix}.foodcraft.{path}' not in values:errors.append(f'Missing {locale} language key {path}')
        if not (RES/f'assets/foodcraft/models/item/{path}.json').exists():errors.append('Missing item model '+path)
        if 'constructor_args' in row and not (RES/f'assets/foodcraft/blockstates/{path}.json').exists():errors.append('Missing blockstates '+path)
    def item(value,where):
        if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+',value):errors.append(f'{where}: invalid item ID {value}')
        if value.startswith('foodcraft:') and value not in ids:errors.append(f'{where}: unknown item {value}')
    def tag(value,where):
        namespace,path=value.split(':')
        if namespace!='minecraft' and not (RES/f'data/{namespace}/tags/items/{path}.json').exists():errors.append(f'{where}: missing tag {value}')
    recipes=list((RES/'data/foodcraft/recipes').rglob('*.json'))
    def loot_nodes(value,where):
        if isinstance(value,dict):
            if value.get('type')=='minecraft:item':item(value['name'],where)
            for child in value.values():loot_nodes(child,where)
        elif isinstance(value,list):
            for child in value:loot_nodes(child,where)
    for file in (RES/'data/foodcraft/loot_tables').rglob('*.json'):
        loot_nodes(json.loads(file.read_text('utf-8')),str(file.relative_to(RES)))
    for file in recipes:
        value=json.loads(file.read_text('utf-8'));where=str(file.relative_to(RES))
        result={'item':value['result']} if isinstance(value['result'],str) else value['result']
        item(result['item'],where)
        if result.get('count',1)<1 or result.get('count',1)>64:errors.append(where+': invalid output count')
        ingredients=value.get('ingredients',list(value.get('key',{}).values()))
        if 'ingredient' in value:ingredients=[value['ingredient']]
        if value['type'].startswith('foodcraft:') and 'inputs' in value:
            ingredients=[x['ingredient'] for x in value['inputs']]
            slots=[x['slot'] for x in value['inputs']]
            if len(slots)!=len(set(slots)):errors.append(where+': duplicate input slot')
        if value['type']=='minecraft:crafting_shaped':
            used=set(''.join(value['pattern']))-{' '}
            if used!=set(value['key']):errors.append(where+': unmatched crafting keys')
        for ingredient in ingredients:
            alternatives=ingredient if isinstance(ingredient,list) else [ingredient]
            if not alternatives:errors.append(where+': empty ingredient alternatives')
            for alternative in alternatives:
                if not isinstance(alternative,dict) or ('item' in alternative)==('tag' in alternative):
                    errors.append(where+': ingredient must specify exactly one item or tag')
                elif 'item' in alternative:item(alternative['item'],where)
                else:tag(alternative['tag'],where)
    textures=0
    for file in (RES/'assets/foodcraft/models').rglob('*.json'):
        value=json.loads(file.read_text('utf-8'));where=str(file.relative_to(RES))
        parent=value.get('parent','')
        if parent.startswith('foodcraft:') and not (RES/('assets/foodcraft/models/'+parent.split(':')[1]+'.json')).exists():errors.append(where+': missing parent '+parent)
        for texture in value.get('textures',{}).values():
            if texture.startswith('foodcraft:'):
                if not texture.split(':')[1].startswith(('block/','item/')):errors.append(where+': texture outside Minecraft atlas directories '+texture)
                target=RES/('assets/foodcraft/textures/'+texture.split(':')[1]+'.png')
                if not target.exists():errors.append(where+': missing texture '+texture)
                else:textures+=1
        for element in value.get('elements',[]):
            if any(a>b for a,b in zip(element['from'],element['to'])):errors.append(where+': inverted model box')
            if any(n<-16 or n>32 for n in element['from']+element['to']):errors.append(where+': model box out of Minecraft bounds')
    layouts=json.loads((RES/'assets/foodcraft/machine-layouts.json').read_text('utf-8'))
    for kind,slots in layouts.items():
        if sorted(x[0] for x in slots)!=list(range(len(slots))):errors.append(kind+': missing menu index')
        rectangles=[]
        for index,x,y in slots:
            rectangle=(x,y,x+16,y+16)
            for a,b,c,d in rectangles:
                if max(a,x)<min(c,x+16) and max(b,y)<min(d,y+16):errors.append(kind+': overlapping slots')
            rectangles.append(rectangle)
    result=dict(entries=len(entries),registered=len(registered),recipes=len(recipes),models=len(list((RES/'assets/foodcraft/models').rglob('*.json'))),texture_references=textures,errors=errors)
    (ROOT/'docs/content-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
    print(json.dumps(result,ensure_ascii=False))
    return not errors


if __name__=='__main__':sys.exit(0 if check() else 1)
