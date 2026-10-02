"""Reproducibly extract the pinned legacy catalog and recipes, without running Java."""
from __future__ import annotations

import argparse
import ast
import csv
import hashlib
import json
import re
import shutil
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = '523515c4988e485adbcf5eb51c73caec71d5361d'
LEGACY = ROOT.parent.parent / 'work' / 'legacy' / ('FoodCraft-' + SHA)
JAVA = LEGACY / 'src/main/java/com/cfyifei'
RES = ROOT / 'common/src/main/resources'
ASSETS = RES / 'assets/foodcraft'
DATA = RES / 'data/foodcraft'
MACHINES = {'Nmj': 'milling_machine', 'Caiban': 'cutting_board', 'Guo': 'pot',
            'PDG': 'frying_pan', 'Gyg': 'pressure_cooker', 'YZJ': 'deep_fryer',
            'Tpj': 'drink_maker', 'Nt': 'fermenting_barrel', 'Zl': 'stove'}
BLOCK_ITEM_DISPLAY = {
    'gui': {'rotation': [30,225,0], 'translation': [0,0,0], 'scale': [0.625]*3},
    'ground': {'rotation': [0,0,0], 'translation': [0,3,0], 'scale': [0.25]*3},
    'fixed': {'rotation': [0,0,0], 'translation': [0,0,0], 'scale': [0.5]*3},
    'thirdperson_righthand': {'rotation': [75,45,0], 'translation': [0,2.5,0], 'scale': [0.375]*3},
    'firstperson_righthand': {'rotation': [0,45,0], 'translation': [0,0,0], 'scale': [0.4]*3},
    'firstperson_lefthand': {'rotation': [0,225,0], 'translation': [0,0,0], 'scale': [0.4]*3},
}
ALIASES = {'ItemYangrou': 'minecraft:mutton', 'ItemShuyangrou': 'minecraft:cooked_mutton'}
FABRIC_STANDARD_TAGS = {'forge:ingots/iron':'c:iron_ingots', 'forge:ingots/gold':'c:gold_ingots',
                        'forge:gems/diamond':'c:diamonds', 'forge:gems/emerald':'c:emeralds'}
VARIANTS = ['Putao', 'Jinputao', 'Li', 'Taozi', 'Juzi', 'Ningmeng', 'Caomei', 'Yezi']
IDS: dict[str, str] = {}
CATALOG: list[dict] = []
FIXES: list[dict] = []


def read(path: str) -> str:
    return (JAVA / path).read_text('utf-8', errors='replace')


def uncomment(text: str) -> str:
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    return re.sub(pattern, lambda m: ' ' if m[0].startswith('/') else m[0], text)


def split_args(text: str) -> list[str]:
    depth = 0
    quoted = ''
    escaped = False
    start = 0
    result = []
    for i, c in enumerate(text):
        if quoted:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == quoted:
                quoted = ''
        elif c in '\"\'':
            quoted = c
        elif c in '({[':
            depth += 1
        elif c in ')}]':
            depth -= 1
        elif c == ',' and depth == 0:
            result.append(text[start:i].strip())
            start = i + 1
    if text[start:].strip():
        result.append(text[start:].strip())
    return result


def calls(text: str, names: str):
    clean = uncomment(text)
    for match in re.finditer(r'\b(' + names + r')\s*\(', clean):
        start = match.end()
        depth = 1
        quote = ''
        escaped = False
        for i in range(start, len(clean)):
            c = clean[i]
            if quote:
                if escaped:
                    escaped = False
                elif c == '\\':
                    escaped = True
                elif c == quote:
                    quote = ''
            elif c in '\"\'':
                quote = c
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    yield match[1], split_args(clean[start:i])
                    break


def ident(name: str) -> str:
    if name in MACHINES:
        return MACHINES[name]
    name = re.sub(r'^(Item|Block)', '', name)
    name = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', name)
    return re.sub(r'[^a-z0-9_]', '_', name.lower()).strip('_')


def tex_id(name: str) -> str:
    # Asset names are case-insensitive in the legacy atlas. Do not interpret Item/Block as ID prefixes.
    return re.sub(r'[^a-z0-9_]', '_', name.lower())


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf-8')


def java_string(s: str):
    return json.dumps(s, ensure_ascii=False)


def constructors(text: str):
    result = {}
    for m in re.finditer(r'\b(\w+)\s*=\s*new\s+(\w+)\s*\(', uncomment(text)):
        tail = uncomment(text)[m.start():]
        cls, args = next(calls(tail, re.escape(m[2])))
        result[m[1]] = (cls, args)
    return result


def method_body(text: str,name: str):
    match=re.search(r'\b'+re.escape(name)+r'\([^)]*\)\s*\{',uncomment(text))
    if not match:return ''
    clean=uncomment(text);start=match.end();depth=1
    for i in range(start,len(clean)):
        if clean[i]=='{':depth+=1
        elif clean[i]=='}':
            depth-=1
            if depth==0:return clean[start:i]
    raise ValueError('Unterminated method '+name)


def crop_loot(row,src,seed,produce):
    refs={'this.getSeed()':seed,'getSeed()':seed,'this.getCrop()':produce,'getCrop()':produce}
    for fn in ['getSeed','getCrop']:
        body=method_body(src,fn)
        match=re.search(r'return\s+(ModItem\.\w+)\s*;',body)
        if match:
            refs['this.'+fn+'()']=refs[fn+'()']=resolve(match[1])
    body=method_body(src,'getItemDropped')
    clauses=re.findall(r'if\s*\(([^)]+)\)\s*\{\s*return\s+([^;]+);',body)
    returns=re.findall(r'return\s+([^;]+);',body)
    def condition_value(condition,age):
        condition=re.sub(r'\b(?:par1|p_\w+|meta)\b',str(age),condition).replace('||',' or ').replace('&&',' and ')
        if not re.fullmatch(r'[0-9 <>=!()orand]+',condition):raise ValueError('Unsupported crop drop condition '+condition)
        return eval(condition,{'__builtins__':{}},{})
    def expression_value(expression,age):
        expression=expression.strip()
        if '?' in expression:
            condition,branches=expression.split('?',1)
            yes,no=branches.split(':',1)
            return expression_value(yes if condition_value(condition,age) else no,age)
        if expression in refs:return refs[expression]
        if re.fullmatch(r'ModItem\.\w+',expression):return resolve(expression)
        raise ValueError('Unsupported crop drop expression '+expression)
    values=[]
    for age in range(8):
        output=refs['this.getCrop()'] if age>=7 else refs['this.getSeed()']
        if returns:
            output=expression_value(returns[-1],age)
            for condition,expression in clauses:
                if condition_value(condition,age):
                    output=expression_value(expression,age);break
        values.append(output)
    row['drop_by_age']=values;row['harvest_seed']=refs['this.getSeed()']
    early='== 5' in method_body(src,'quantityDropped') and '== 6' in method_body(src,'quantityDropped')
    pools=[]
    for age,output in enumerate(values):
        condition=dict(condition='minecraft:block_state_property',block=row['id'],properties=dict(age=str(age)))
        entry=dict(type='minecraft:item',name=output)
        if early and age in [5,6]:
            entry['functions']=[dict(function='minecraft:apply_bonus',enchantment='minecraft:fortune',formula='minecraft:binomial_with_bonus_count',
                                     parameters=dict(extra=3,probability=(age+1)/15))]
        pools.append(dict(rolls=1,conditions=[condition],entries=[entry]))
    pools.append(dict(rolls=1,conditions=[dict(condition='minecraft:block_state_property',block=row['id'],properties=dict(age='7'))],
        entries=[dict(type='minecraft:item',name=row['harvest_seed'],functions=[dict(function='minecraft:set_count',count=0),
            dict(function='minecraft:apply_bonus',enchantment='minecraft:fortune',formula='minecraft:binomial_with_bonus_count',parameters=dict(extra=3,probability=8/15))])]))
    return dict(type='minecraft:block',pools=pools)


def item_catalog():
    text = read('item/ModItem.java')
    ctors = constructors(text)
    registered = [args[0] for _, args in calls(text, 'registerItem') if len(args) == 2]
    registry_names = {args[0]: args[1].strip('"') for _, args in calls(text, 'registerItem') if len(args) == 2}
    # Collection reward constructors self-register outside the main registration list.
    for name, (cls, _) in ctors.items():
        if cls in {'ItemBook', 'ItemShi', 'ItemAnTools'} and name not in registered:
            registered.append(name)
            FIXES.append(dict(legacy=name, change='include collection reward registered by its own constructor'))
    for name in registered:
        if name not in ctors:
            raise ValueError(f'Registered item has no constructor: {name}')
        cls, args = ctors[name]
        modern = ALIASES.get(name, 'foodcraft:' + ident(name))
        IDS[name] = modern
        textures = re.findall(re.escape(name) + r'\.setTextureName\("(?:[^"]+:)?([^"]+)"\)', text)
        assignment = re.search(re.escape(name) + r'\s*=([^;]+);', text)
        chained = re.findall(r'\.setTextureName\("(?:[^"]+:)?([^"]+)"\)', assignment[1]) if assignment else []
        textures += chained
        texture = textures[-1] if textures else name
        if cls == 'ItemBook':
            texture = 'ItemBook'
        row = dict(legacy=name, id=modern, kind='item', legacy_class=cls, texture=texture,
                   nutrition=0, saturation=0.0, always_edible=False, durability=0,
                   crop='', effect='none', variants=0, status='generated', source='item/ModItem.java')
        if cls == 'ItemTest':
            row.update(kind='debug', status='omitted_debug')
            FIXES.append(dict(legacy=name, change='development-only item has no texture and is omitted from playable registration'))
        if cls in {'ItemFcFood', 'ItemZhushi', 'ItemYingliao', 'ItemFoodJiu',
                   'ItemFoodJinputaojiu', 'ItemFoodJinpingguojiu', 'ItemMilk', 'ItemBinggan'}:
            nutrition = float(args[1].rstrip('Ff'))
            row.update(nutrition=int(nutrition), saturation=nutrition / 3)
            row['effect'] = {'ItemZhushi': 'staple', 'ItemFoodJiu': 'wine',
                             'ItemFoodJinputaojiu': 'gold_grape_wine',
                             'ItemFoodJinpingguojiu': 'gold_apple_wine', 'ItemMilk': 'milk'}.get(cls, 'none')
            if name in {'ItemZongzi', 'ItemYuebing', 'ItemTangyuan'}:
                row['effect'] = 'staple'
            if cls == 'ItemYingliao' and len(args) >= 6:
                row['effect'] = 'gold_grape' if args[5] == '0' else 'gold_apple'
            row['glint']=len(args)>=5 and args[4]=='true' or cls.startswith('ItemFood')
        if '.setAlwaysEdible()' in re.search(re.escape(name) + r'\s*=([^;]+)', text)[1]:
            row['always_edible'] = True
        if cls in {'ItemCaidaoHJ', 'ItemCaidaoZS', 'ItemCaidaoLBS'} or name == 'ItemCaidao':
            row['kind'] = 'knife'
            damage = re.search(re.escape(name) + r'\.setMax(?:Damage|Durability)\((\d+)\)', text)
            own = read('item/' + cls + '.java') if cls != 'Item' else ''
            own_damage = re.search(r'setMax(?:Damage|Durability)\((\d+)\)', own)
            row['durability'] = int(damage[1]) if damage else int(own_damage[1]) if own_damage else 128
        if cls == 'ItemJinghuashuitong':
            row.update(kind='purifier', durability=16)
        if cls == 'ItemWrench':
            row['kind'] = 'wrench'
        if cls == 'ItemAnTools':
            row.update(kind='multitool', durability=5000)
        if cls == 'ItemChili':
            row.update(nutrition=1, saturation=1.0/3, effect='chili')
        if args and (m := re.search(r'ModBlocks\.(Block\w+)', args[0])):
            row.update(kind='seed', crop=m[1])
            if cls == 'ItemHongshu':
                row.update(nutrition=3, saturation=1.0)
        if name in {'ItemBinggan', 'ItemDangao', 'ItemGuojiang'}:
            suffix = {'ItemBinggan': 'BG', 'ItemDangao': 'DG', 'ItemGuojiang': 'GJ'}[name]
            row['variants'] = 8
            row['kind'] = {'BG': 'cookie', 'DG': 'cake_item', 'GJ': 'jam'}[suffix]
            for meta, fruit in enumerate(VARIANTS):
                v = dict(row, legacy=f'{name}:{meta}', id='foodcraft:' + ident('Item' + fruit + suffix),
                         texture='Item' + fruit + suffix, variants=0)
                v.update(legacy_registry_ids=['FoodCraft:'+registry_names.get(name,args[0].strip('"') if args else name)], metadata=meta,stack_size=64)
                v['glint']=meta==1
                if row['kind'] == 'cake_item':
                    v['crop'] = 'Block' + fruit + 'DG'
                if row['kind'] == 'cookie' and meta == 1:
                    v['effect'] = 'gold_cookie'
                CATALOG.append(v)
            IDS[name] = 'foodcraft:' + ident('Item' + VARIANTS[0] + suffix)
            continue
        if name in ALIASES:
            row['status'] = 'merged_vanilla'
        stack = re.search(re.escape(name)+r'\.setMaxStackSize\((\d+)\)',text)
        row['stack_size']=int(stack[1]) if stack else 1 if row['durability']>0 or cls=='ItemWrench' else 64
        row['legacy_registry_ids']=['FoodCraft:'+registry_names.get(name,args[0].strip('"') if cls in {'ItemBook','ItemShi','ItemAnTools'} else name)]
        row['metadata']=None
        CATALOG.append(row)


def block_catalog():
    for file in ['block/ModBlocks.java', 'plant/blocks/Plant.java', 'gui/blocks/ModGui.java']:
        text = read(file)
        ctors = constructors(text)
        for _, args in calls(text, 'registerBlock'):
            name = args[0]
            if name.startswith('lit_'):
                IDS[name] = 'foodcraft:' + MACHINES[name[4:]]
                FIXES.append(dict(legacy=name, change='lit variant represented by block state'))
                continue
            cls, ctorargs = ctors.get(name, ('Block', []))
            modern = 'foodcraft:' + ident(name)
            # Crop/fruit blocks and edible fruit have distinct registry paths.
            if any(r['id'] == modern for r in CATALOG):
                modern += '_block'
            IDS[name] = modern
            textures = re.findall(re.escape(name) + r'\.setTextureName\("(?:[^"]+:)?([^"]+)"\)', text)
            kind = 'block'
            if name in MACHINES:
                kind = 'machine'
            elif cls in {'BlockTree','TreeBannana','TreeCoconut'}:
                kind = 'sapling'
            elif cls in {'BlockFruit', 'BlockCoconut', 'BlockBannana'} or name == 'FCleaves':
                kind = 'fruit'
            elif cls == 'BlockDangao' or name.endswith('DG'):
                kind = 'cake'
            elif name == 'BlockCong':
                kind = 'onion'
            elif name in {r['crop'] for r in CATALOG if r['kind'] == 'seed'}:
                kind = 'crop'
            own_files=list(JAVA.rglob(cls+'.java'));own=read(str(own_files[0].relative_to(JAVA))) if own_files else ''
            assignment=re.search(re.escape(name)+r'\s*=([^;]+);',text)
            chained=assignment[1] if assignment else ''
            hardness=re.findall(re.escape(name)+r'\.setHardness\(([0-9.]+)[Ff]?\)',text) or re.findall(r'setHardness\(([0-9.]+)[Ff]?\)',chained) or re.findall(r'setHardness\(([0-9.]+)[Ff]?\)',own)
            sound=re.findall(re.escape(name)+r'\.setStepSound\(Block\.soundType(\w+)\)',text) or re.findall(r'setStepSound\(Block\.soundType(\w+)\)',chained) or re.findall(r'setStepSound\((?:Block\.)?soundType(\w+)\)',own)
            bounds=None
            for match in re.finditer(r'setBlockBounds\(([^)]+)\)',own):
                values=split_args(match[1])
                constants={key:float(value.rstrip('Ff')) for key,value in re.findall(r'(?:float|double)\s+(\w+)\s*=\s*(-?[0-9.]+[Ff]?)\s*;',own[:match.start()])}
                def scalar(node):
                    if isinstance(node,ast.Constant) and isinstance(node.value,(int,float)):return node.value
                    if isinstance(node,ast.Name) and node.id in constants:return constants[node.id]
                    if isinstance(node,ast.UnaryOp) and isinstance(node.op,ast.USub):return -scalar(node.operand)
                    if isinstance(node,ast.BinOp):
                        left,right=scalar(node.left),scalar(node.right)
                        if isinstance(node.op,ast.Add):return left+right
                        if isinstance(node.op,ast.Sub):return left-right
                        if isinstance(node.op,ast.Mult):return left*right
                        if isinstance(node.op,ast.Div):return left/right
                    raise ValueError('Nonconstant block bounds')
                if len(values)==6:
                    try:bounds=[scalar(ast.parse(re.sub(r'(?<=\d)[Ff]\b','',v),mode='eval').body)*16 for v in values];break
                    except (SyntaxError,ValueError,ZeroDivisionError):continue
            light=re.findall(re.escape('lit_'+name)+r'\.setLightLevel\(([0-9.]+)[Ff]?\)',text)
            CATALOG.append(dict(legacy=name, id=modern, kind=kind, legacy_class=cls,
                                texture=textures[-1] if textures else name, source=file,
                                constructor_args=ctorargs, status='generated',hardness=float(hardness[-1]) if hardness else 0.0,sound=sound[-1] if sound else 'Stone',
                                collision=bounds,lit_light=int(float(light[-1])*15) if light else 0))
            row=CATALOG[-1]
            stack=re.search(r'Item\.getItemFromBlock\('+re.escape(name)+r'\)\.setMaxStackSize\((\d+)\)',text)
            row.update(stack_size=int(stack[1]) if stack else 64,metadata=None,legacy_registry_ids=['FoodCraft:'+args[-1].strip('"')])
        for _,args in calls(text,'registerBlock'):
            if args[0].startswith('lit_'):
                next(row for row in CATALOG if row['id']==IDS[args[0]])['legacy_registry_ids'].append('FoodCraft:'+args[-1].strip('"'))
    for row in CATALOG:
        if row.get('crop'):
            row['crop'] = IDS[row['crop']]
    for row in CATALOG:
        text=read(row['source'])
        base=row['legacy'].split(':')[0]
        assignment=re.search(re.escape(base)+r'\s*=([^;]+);',text)
        tab=re.search(re.escape(base)+r'\.setCreativeTab\(FoodCraft\.FcTab(\w+)\)',text) or re.search(r'\.setCreativeTab\(FoodCraft\.FcTab(\w+)\)',assignment[1] if assignment else '')
        row['creative_tab']=tab[1].lower() if tab else 'zhiwu' if row['kind']=='sapling' else 'jiqi' if row['kind']=='machine' else 'collection' if row['legacy_class'] in {'ItemBook','ItemShi','ItemAnTools'} else ''
    FIXES.append(dict(legacy='grass seeds',change='copy-pasted Hongdou grass entries corrected to the green bean, sweet potato and cucumber registered beside them'))


def parse_stack(expr: str) -> dict:
    expr = expr.strip()
    if expr == 'null':
        return {}
    if expr.startswith('new ItemStack('):
        _, args = next(calls(expr, 'ItemStack'))
        value = resolve(args[0], int(args[2]) if len(args) > 2 else 0)
        return dict(item=value, count=int(args[1]) if len(args) > 1 else 1)
    return dict(item=resolve(expr), count=1)


def resolve(expr: str, meta: int = 0) -> str:
    expr = expr.strip()
    if expr.startswith('Item.getItemFromBlock('):
        _, args = next(calls(expr, 'getItemFromBlock'))
        return resolve(args[0], meta)
    if expr.startswith('OreDictionary.getOres('):
        return '#' + tag_name(re.search(r'"([^"]+)"', expr)[1])
    name = expr.split('.')[-1]
    if name in IDS:
        if name in {'ItemBinggan', 'ItemGuojiang', 'ItemDangao'}:
            suffix = {'ItemBinggan': 'BG', 'ItemGuojiang': 'GJ', 'ItemDangao': 'DG'}[name]
            return 'foodcraft:' + ident('Item' + VARIANTS[meta] + suffix)
        return IDS[name]
    if expr.startswith(('ModItem.', 'ModBlocks.', 'ModGui.', 'Plant.')):
        raise ValueError(f'Unknown FoodCraft reference: {expr}')
    vanilla = {'melon': 'melon_slice', 'potionitem': 'potion', 'reeds': 'sugar_cane',
               'leaves': 'oak_leaves', 'sapling': 'oak_sapling', 'planks': 'oak_planks',
               'log': 'oak_log', 'cooked_fish': 'cooked_cod', 'fish': 'cod', 'waterlily': 'lily_pad'}
    if name == 'dye':
        return 'minecraft:' + ['ink_sac', 'red_dye', 'green_dye', 'cocoa_beans', 'lapis_lazuli',
                              'purple_dye', 'cyan_dye', 'light_gray_dye', 'gray_dye', 'pink_dye',
                              'lime_dye', 'yellow_dye', 'light_blue_dye', 'magenta_dye', 'orange_dye', 'bone_meal'][meta]
    if name == 'wool':
        return '#minecraft:wool'
    if name in vanilla and name in {'leaves', 'sapling', 'planks'}:
        return '#minecraft:' + {'leaves': 'leaves', 'sapling': 'saplings', 'planks': 'planks'}[name]
    return 'minecraft:' + vanilla.get(name, name)


def tag_name(name: str) -> str:
    standard = {'ingotIron': 'forge:ingots/iron', 'ingotGold': 'forge:ingots/gold',
                'gemDiamond': 'forge:gems/diamond', 'gemEmerald': 'forge:gems/emerald',
                'plankWood': 'minecraft:planks', 'logWood': 'minecraft:logs',
                'listAllmushroom': 'foodcraft:mushrooms'}
    return standard.get(name, 'foodcraft:legacy/' + ident(name))


def ingredient(expr: str):
    if expr.startswith('"'):
        value = '#' + tag_name(expr.strip('"'))
    else:
        value = parse_stack(expr).get('item')
    if value is None:
        return {}
    if value.startswith('#') and value[1:] in FABRIC_STANDARD_TAGS:
        tag=value[1:]
        # Alternative ingredients avoid a cross-mod cycle between Forge and c tags.
        return [{'tag':tag}, {'tag':FABRIC_STANDARD_TAGS[tag]}, {'tag':'c:'+tag.split(':',1)[1].replace('/','_')}]
    return {'tag': value[1:]} if value.startswith('#') else {'item': value}


def generate_recipes():
    recipe_dir = DATA / 'recipes'
    machines = {'Caiban': (MACHINES['Caiban'], [1, 2, 3], 1, 0),
                'Gyg': (MACHINES['Gyg'], [0, 1, 2], 480, 2),
                'Nt': (MACHINES['Nt'], [0, 1, 2], 3600, 8),
                'Guo': (MACHINES['Guo'], list(range(12)), 500, 0),
                'Nmj': (MACHINES['Nmj'], [0], 200, 0),
                'PDG': (MACHINES['PDG'], [0], 400, 0),
                'YZJ': (MACHINES['YZJ'], [0], 400, 2),
                'Tpj': (MACHINES['Tpj'], [1], 350, 1)}
    audit = []
    for old, (machine, slots, ticks, water) in machines.items():
        text = read('gui/recipes/' + old + 'recipe.java')
        # Only constructor registrations, not the public API implementations.
        body = text.split('private ' + old + 'recipe()')[1].split('public static')[0]
        n = 0
        for fn, args in calls(body, 'addrecipe|addRecipeItem|addrecipeItem|itemregister|register'):
            minheat, maxheat, milk, cold = 0, 2**31-1, False, False
            xp = 0.0
            if old in {'Caiban', 'Gyg', 'Nt'}:
                inputs, result = args[:3], parse_stack(args[3])
            elif old == 'Guo':
                inputs, result = args[:12], parse_stack(args[12])
                minheat, maxheat = int(args[13]), int(args[14])
            elif old == 'Tpj':
                inputs, result = [args[0]], parse_stack(args[3])
                milk, cold = args[1] == 'true', args[2] == 'true'
            else:
                inputs, result = [args[0]], parse_stack(args[1])
                xp = float(args[2].rstrip('Ff'))
                if old == 'PDG':
                    minheat, maxheat = int(args[3]), int(args[4])
            parts = []
            for slot, expr in zip(slots, inputs):
                part = ingredient(expr)
                if part:
                    parts.append(dict(slot=slot, ingredient=part, count=1))
            recipe = dict(type='foodcraft:' + machine, inputs=parts, result=result,
                          time=ticks, water=water, milk=milk, cold=cold,
                          min_heat=minheat, max_heat=maxheat, experience=xp,
                          exclusive_slots=slots)
            path = f'{machine}/{n:03d}_{result["item"].split(":")[1]}'
            write_json(recipe_dir / (path + '.json'), recipe)
            audit.append(dict(id='foodcraft:' + path, **recipe, source='gui/recipes/' + old + 'recipe.java'))
            n += 1
    crafting = read('recipe/Recipe.java')
    # The registrations are in init(), outside the wrapper method declarations.
    body = crafting.split('public static void init()')[1].split('public static void registerChestLoot')[0]
    n = 0
    registrations = []
    for fn, args in calls(body, 'addOreRecipe|addOreShapelessRecipe|addRecipe|addShapelessRecipe|addSmelting'):
        if any(re.search(r'\bi1\b', a) for a in args):
            registrations.extend((fn, [re.sub(r'\bi1\b', str(meta), a) for a in args]) for meta in range(8))
        else:
            registrations.append((fn, args))
    for fn, args in registrations:
        if len(args) < 2 or not args[0].startswith('new ItemStack('):
            continue
        result = parse_stack(args[0])
        if fn == 'addSmelting':
            output=parse_stack(args[1]);path='smelting/'+output['item'].split(':')[1]
            if result['item']=='minecraft:mutton':
                FIXES.append(dict(legacy='mutton smelting',change='uses the vanilla mutton recipe and experience instead of registering a duplicate'));continue
            recipe=dict(type='minecraft:smelting',ingredient=ingredient(args[0]),result=output['item'],experience=float(args[2].rstrip('Ff')),cookingtime=200)
            if output['count']!=1:recipe.update(type='foodcraft:smelting',result=output)
            write_json(recipe_dir/(path+'.json'),recipe)
            audit.append(dict(id='foodcraft:'+path,**recipe,source='recipe/Recipe.java'))
            continue
        array = args[1].strip()
        if array.startswith('new Object'):
            array = array[array.index('{') + 1:array.rindex('}')]
            params = split_args(array)
        else:
            params = args[1:]
        shaped = fn in {'addOreRecipe', 'addRecipe'}
        if shaped:
            patterns = []
            while params and params[0].startswith('"') and len(params[0].strip('"')) <= 3:
                patterns.append(params.pop(0).strip('"'))
            keys = {}
            for i in range(0, len(params), 2):
                keys[params[i].strip("'")] = ingredient(params[i+1])
            if not patterns:
                raise ValueError(f'Missing shaped pattern: {args}')
            # Strip only entirely empty rows/columns, as required by modern recipes.
            while patterns and not patterns[0].strip():
                patterns.pop(0)
            while patterns and not patterns[-1].strip():
                patterns.pop()
            left = min(len(p) - len(p.lstrip()) for p in patterns)
            right = max(len(p.rstrip()) for p in patterns)
            patterns = [p[left:right] for p in patterns]
            recipe = dict(type='minecraft:crafting_shaped', pattern=patterns, key=keys, result=result)
        else:
            recipe = dict(type='minecraft:crafting_shapeless', ingredients=[ingredient(p) for p in params], result=result)
        path = f'crafting/{n:03d}_{result["item"].split(":")[1]}'
        write_json(recipe_dir / (path + '.json'), recipe)
        audit.append(dict(id='foodcraft:' + path, **recipe, source='recipe/Recipe.java'))
        n += 1
    write_json(ROOT / 'docs/recipe-manifest.json', audit)
    generate_tags(crafting)


def generate_tags(crafting: str):
    tags: dict[str, set[str]] = {}
    defaults = {'ingotIron': ['minecraft:iron_ingot'], 'ingotGold': ['minecraft:gold_ingot'],
                'gemDiamond': ['minecraft:diamond'], 'gemEmerald': ['minecraft:emerald'],
                'listAllmushroom': ['minecraft:brown_mushroom', 'minecraft:red_mushroom'],
                'listAllveggie': ['minecraft:carrot', 'minecraft:potato', IDS['ItemShucai'], IDS['ItemCong'], IDS['ItemBailuobo']],
                'listAllmeatcooked': ['minecraft:cooked_beef', 'minecraft:cooked_porkchop', 'minecraft:cooked_chicken', 'minecraft:cooked_mutton', IDS['ItemShuyouyurou']],
                'listAllmeatraw': ['minecraft:beef', 'minecraft:porkchop', 'minecraft:chicken', 'minecraft:mutton', IDS['ItemYouyurou']]}
    explicit = {'plateIron': 'ItemTiepian', 'foodFlour': 'ItemMianfen', 'foodRice': 'Itemfan',
                'foodRicesoup': 'ItemXifan', 'foodCiba': 'ItemCiba', 'foodRicecake': 'ItemAici',
                'cropTomato': 'ItemFanqie', 'foodGreenonion': 'ItemCong', 'foodApplejuice': 'ItemPingguozhi',
                'foodGrapejuice': 'ItemPutaozhi', 'foodPancakes': 'ItemLaobing', 'foodCheese': 'ItemNainao',
                'cropPeanut': 'ItemHuashen', 'cropSoybean': 'ItemDouzi', 'cropRice': 'ItemDami',
                'foodSoysauce': 'ItemJiangyou', 'foodSausage': 'ItemXiangchang', 'foodSilkentofu': 'ItemDoufugan',
                'cropChilipepper': 'ItemLajiao', 'foodMayonnaise': 'ItemNainao', 'cropCoffee': 'ItemKafei', 'seedCoffee': 'ItemKafei',
                'foodChocolatemilk': 'ItemQiaokelinai', 'foodMelonjuice': 'ItemXiguazhi', 'foodVegetablesoup': 'ItemShucaizhi',
                'foodSoymilk': 'ItemDoujiang', 'foodSalt': 'ItemYan', 'foodChocolate': 'ItemQiaokeli',
                'foodVinegar': 'ItemCu', 'foodTofu': 'ItemDoufu', 'foodToufu': 'ItemDoufu', 'foodNoodles': 'ItemMian',
                'foodCookingOil': 'ItemHuashenyou', 'foodSoySauce': 'ItemJiangyou'}
    for ore, values in defaults.items():
        tags[tag_name(ore)] = set(values)
    for ore, name in explicit.items():
        tags.setdefault(tag_name(ore), set()).add(IDS[name])
    used = set(re.findall(r'"((?:food|crop|seed|listAll|ingot|gem|plate|plank|log)[A-Za-z]+)"', crafting))
    for name in used:
        if tag_name(name).startswith('minecraft:'):
            continue
        if tag_name(name) not in tags:
            raise ValueError(f'Legacy tag has no values: {name}')
    for tag, values in tags.items():
        ns, path = tag.split(':')
        write_json(RES / f'data/{ns}/tags/items/{path}.json', dict(replace=False, values=sorted(values)))
        if ns == 'forge':
            # Keep legacy Forge tag names usable on Fabric, plus the Fabric common counterpart.
            write_json(RES / f'data/c/tags/items/{path.replace("/", "_")}.json', dict(replace=False, values=sorted(values)))
            if tag in FABRIC_STANDARD_TAGS:
                standard=FABRIC_STANDARD_TAGS[tag].split(':',1)[1]
                write_json(RES / f'data/c/tags/items/{standard}.json', dict(replace=False, values=sorted(values)))
    FIXES.append(dict(legacy='ingotIron/ingotGold/gemDiamond/gemEmerald',change='Forge tags and Fabric 1.20.1 conventional iron_ingots/gold_ingots/diamonds/emeralds are accepted as ingredient alternatives; preserve prior c aliases without cyclic tag references'))


def texture_path(texture: str, folder: str, available: dict[str, Path]):
    key = (folder + '/' + texture).lower()
    if key in available:
        return 'foodcraft:' + {'blocks':'block','items':'item'}.get(folder,folder) + '/' + tex_id(available[key].stem)
    return None


def generate_assets():
    source = LEGACY / 'src/main/resources/assets/foodcraft'
    available = {}
    for png in source.rglob('*.png'):
        relative = png.relative_to(source / 'textures') if 'textures' in png.parts else None
        if relative is None:
            continue
        key = relative.with_suffix('').as_posix().lower()
        available[key] = png
        parts=list(relative.parent.parts)
        if parts:parts[0]={'blocks':'block','items':'item'}.get(parts[0],parts[0])
        target = ASSETS / 'textures' / Path(*parts) / (tex_id(png.stem) + '.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(png, target)
    for png in (ROOT / 'reference-assets').rglob('*.png'):
        folder = 'blocks' if png.parent.name == 'blocks' else 'items'
        available[folder + '/' + png.stem.lower()] = png
        target = ASSETS / 'textures' / {'blocks':'block','items':'item'}[folder] / (tex_id(png.stem) + '.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(png, target)
        FIXES.append(dict(legacy=png.stem, change='missing 1.7.10 texture restored from author 1.8.0 commit 3bfe0f16df7d79338c1627086096fe497fe4a96b'))
    for row in CATALOG:
        if row['id'].startswith('minecraft:') or row['kind'] == 'debug':
            continue
        path = row['id'].split(':')[1]
        kind = row['kind']
        block = 'constructor_args' in row
        if not block:
            tex = texture_path(row['texture'], 'items', available)
            if not tex:
                raise ValueError(f'Missing item texture: {row}')
            write_json(ASSETS / f'models/item/{path}.json', dict(parent='minecraft:item/generated', textures=dict(layer0=tex)))
            continue
        tex = texture_path(row['texture'], 'blocks', available)
        if kind == 'crop':
            src = read('block/' + row['legacy_class'] + '.java')
            prefix = re.search(r'registerIcon\("foodcraft:([^"+]+)"\s*\+', src)
            if prefix:
                stages = [k for k in available if k.startswith('blocks/' + prefix[1].lower())]
                stages = sorted(stages, key=lambda s: int(s.rsplit('_', 1)[1]))
            else:
                stages = [k for k in available if k == 'blocks/' + row['texture'].lower()]
            if not stages:
                raise ValueError(f'Missing crop stages: {row}')
            row['age_max'] = 7
            variants = {}
            for age in range(8):
                stage = stages[([0,0,1,1,2,3,3,4][age] if len(stages)==5 else age*(len(stages)-1)//7)].split('/')[1]
                model = path + '_' + str(age)
                write_json(ASSETS / f'models/block/{model}.json', dict(parent='minecraft:block/crop', textures=dict(crop='foodcraft:block/' + tex_id(stage))))
                variants['age=' + str(age)] = dict(model='foodcraft:block/' + model)
            write_json(ASSETS / f'blockstates/{path}.json', dict(variants=variants))
            write_json(ASSETS / f'models/item/{path}.json', dict(parent='foodcraft:block/' + path + '_7'))
            row['texture'] = 'foodcraft:block/' + tex_id(stages[-1].split('/')[1])
            seed = next(r for r in CATALOG if r.get('crop') == row['id'] and r['kind'] == 'seed')
            produce_names = {'BlockShuidao':'ItemDami','BlockDouzi':'ItemDouzi','BlockHuashen':'ItemHuashen',
                'BlockShucai':'ItemShucai','BlockFanqie':'ItemFanqie','BlockLajiao':'ItemLajiao','BlockPutao':'ItemPutao',
                'BlockQiezi':'ItemQiezi','BlockNuodao':'ItemNuomi','BlockCong':'ItemCong','BlockBailuobo':'ItemBailuobo',
                'BlockQingjiao':'ItemQingjiao','BlockHongdou':'ItemHongdou','BlockLvdou':'ItemLvdou','BlockHongshu':'ItemHongshu',
                'BlockYumi':'ItemYumi','BlockHuanggua':'ItemHuanggua','BlockCaomei':'ItemCaomei'}
            row['produce'] = IDS[produce_names[row['legacy']]]
            write_json(DATA / f'loot_tables/blocks/{path}.json',crop_loot(row,src,seed['id'],row['produce']))
            continue
        if kind == 'onion':
            if not tex:raise ValueError('Missing onion texture')
            write_json(ASSETS / f'models/block/{path}.json',dict(parent='minecraft:block/cross',textures=dict(cross=tex)))
            variants={f'age={age}':dict(model='foodcraft:block/'+path) for age in range(16)}
        elif kind == 'machine':
            old = row['legacy'].lower()
            if old in {'guo', 'pdg', 'gyg', 'caiban'}:
                tex = texture_path('pdg_' if old in {'guo', 'pdg'} else old, 'blocks', available)
                generate_machine_model(row, available)
            else:
                side=texture_path('nt_side' if old=='nt' else 'zl_side' if old=='zl' else 'nmj_side','blocks',available)
                tex=side
                for lit in [False,True]:
                    top='minecraft:block/oak_planks' if old=='nt' else texture_path('zl_top' if old=='zl' else 'nmj_top_on' if old=='nmj' and lit else 'nmj_top','blocks',available)
                    front=side if old in {'nt','zl'} else texture_path(old+'_'+old+('_on' if lit else '_off'),'blocks',available)
                    bottom=texture_path('zl_down','blocks',available) if old=='zl' else top
                    if not all([side,top,front,bottom]):raise ValueError('Missing machine faces '+old)
                    model=dict(parent='minecraft:block/cube',textures=dict(particle=side,down=bottom,up=top,north=front,south=side,east=side,west=side))
                    write_json(ASSETS/f'models/block/{path}{"_lit" if lit else ""}.json',model)
            variants = {}
            for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
                for lit in [False, True]:
                    suffix='_lit' if lit and old not in {'guo','pdg','gyg','caiban'} else ''
                    variants[f'facing={facing},lit={str(lit).lower()}'] = dict(model='foodcraft:block/' + path+suffix, y=angle)
        elif kind == 'cake':
            top = texture_path(row['legacy'] + '_top', 'blocks', available)
            side = texture_path(row['legacy'] + '_side', 'blocks', available)
            if not top or not side:
                raise ValueError(f'Missing cake textures: {row}')
            tex = top
            variants = {}
            for bites in range(7):
                model = path + '_' + str(bites)
                write_json(ASSETS / f'models/block/{model}.json', dict(parent=f'minecraft:block/cake_slice{bites}' if bites else 'minecraft:block/cake',
                           textures=dict(bottom='minecraft:block/cake_bottom', top=top, side=side, inside='minecraft:block/cake_inner')))
                variants['bites=' + str(bites)] = dict(model='foodcraft:block/' + model)
        elif kind == 'sapling':
            tex = tex or texture_path(row['legacy'], 'items', available)
            if not tex:
                tex = 'minecraft:block/oak_sapling'
                FIXES.append(dict(legacy=row['legacy'], change='legacy sapling missing texture; vanilla oak sapling texture reused'))
            write_json(ASSETS / f'models/block/{path}.json', dict(parent='minecraft:block/cross', textures=dict(cross=tex)))
            variants = {'stage=0': dict(model='foodcraft:block/' + path), 'stage=1': dict(model='foodcraft:block/' + path)}
        else:
            if not tex:
                # Standalone leaves use their constructor texture, not unlocalized name.
                if row.get('constructor_args') and row['constructor_args'][0].startswith('"'):
                    tex = texture_path(row['constructor_args'][0].strip('"'), 'blocks', available)
            if not tex:
                raise ValueError(f'Missing block texture: {row}')
            if kind=='fruit':
                base=texture_path('FCleaves','blocks',available)
                write_json(ASSETS/f'models/block/{path}.json',dict(parent='minecraft:block/cube',textures=dict(particle=tex,up=base,down=base,north=tex,south=tex,east=tex,west=tex)))
            else:write_json(ASSETS / f'models/block/{path}.json', dict(parent='minecraft:block/cube_all', textures=dict(all=tex)))
            variants = {'': dict(model='foodcraft:block/' + path)}
        write_json(ASSETS / f'blockstates/{path}.json', dict(variants=variants))
        parent = 'foodcraft:block/' + path + ('_0' if kind == 'cake' else '')
        item_model=dict(parent=parent)
        if kind=='cake':item_model.update(gui_light='side',display=BLOCK_ITEM_DISPLAY)
        write_json(ASSETS / f'models/item/{path}.json', item_model)
        row['texture'] = tex or row['texture']
        loot = dict(type='minecraft:block', pools=[dict(rolls=1, entries=[dict(type='minecraft:item', name=row['id'])],
                    conditions=[dict(condition='minecraft:survives_explosion')])])
        if kind == 'fruit':
            args = row['constructor_args']
            fruit = resolve(args[1]) if len(args)>1 else 'minecraft:oak_sapling'
            if fruit.startswith('#'):fruit='minecraft:oak_sapling'
            trees = [r['id'] for r in CATALOG if r['kind']=='sapling' and r.get('constructor_args') and r['constructor_args'][0]==row['legacy']]
            row['produce']=fruit
            loot=dict(type='minecraft:block',pools=[dict(rolls=1,entries=[dict(type='minecraft:item',name=fruit,
                functions=[dict(function='minecraft:set_count',count=dict(type='minecraft:uniform',min=1,max=2))])])])
            if row['legacy']=='FCleaves':
                loot['pools'][0]['entries'][0].pop('functions')
                loot['pools'][0]['conditions']=[dict(condition='minecraft:random_chance',chance=0.3)]
        if kind=='onion':loot=dict(type='minecraft:block',pools=[dict(rolls=1,entries=[dict(type='minecraft:item',name=IDS['ItemCong'])])])
        if kind=='cake':loot=dict(type='minecraft:block',pools=[])
        write_json(DATA / f'loot_tables/blocks/{path}.json', loot)
    generate_languages(source)
    from normalize_textures import normalize
    for change in normalize(ASSETS):
        FIXES.append(dict(legacy=change['texture'],change=change['conversion']))
    write_json(RES / 'pack.mcmeta', dict(pack=dict(pack_format=15, description='FoodCraft 1.20.1 assets and recipes')))


def generate_machine_model(row, available):
    name = {'Caiban': 'ModelCaiban', 'Guo': 'ModelGuo', 'PDG': 'ModelPDG', 'Gyg': 'ModelGYG'}[row['legacy']]
    text = read('gui/blocks/' + name + '.java')
    width = int(re.search(r'textureWidth\s*=\s*(\d+)',text)[1])
    height = int(re.search(r'textureHeight\s*=\s*(\d+)',text)[1])
    renderer={'Caiban':'CaibanRenderer','Guo':'GuoRenderer','PDG':'PDGRenderer','Gyg':'GygRenderer'}[row['legacy']]
    atlas=re.search(r'new ResourceLocation\("foodcraft:textures/blocks/([^"/]+)\.png"\)',read('modelrenderer/'+renderer+'.java'))[1]
    texture=texture_path(atlas,'blocks',available)
    if not texture:raise ValueError('Missing legacy renderer atlas '+atlas)
    elements = []
    boxes = re.findall(r'(\w+)\.addBox\(([^;]+)\);', text)
    for part, values in boxes:
        vals = [float(v.strip().rstrip('Ff')) for v in split_args(values)[:6]]
        x, y, z, w, h, d = vals
        point = re.search(re.escape(part) + r'\.setRotationPoint\(([^;]+)\);', text)
        px, py, pz = [float(v.rstrip('Ff')) for v in split_args(point[1])] if point else [0, 0, 0]
        low = [x + px + 8, 24 - (y + py + h), z + pz + 8]
        high = [low[0] + w, low[1] + h, low[2] + d]
        if min(w, h, d) <= 0:
            continue
        offsets = re.search(re.escape(part) + r'\s*=\s*new ModelRenderer\(this,\s*(\d+),\s*(\d+)\)',text)
        u,v = (int(offsets[1]),int(offsets[2])) if offsets else (0,0)
        rectangles = {'west':[u,v+d,u+d,v+d+h], 'north':[u+d,v+d,u+d+w,v+d+h],
                      'east':[u+d+w,v+d,u+2*d+w,v+d+h], 'south':[u+2*d+w,v+d,u+2*d+2*w,v+d+h],
                      'up':[u+d,v,u+d+w,v+d], 'down':[u+d+w,v,u+d+2*w,v+d]}
        faces = {face:dict(texture='#all',uv=[coords[0]*16/width,coords[1]*16/height,coords[2]*16/width,coords[3]*16/height])
                 for face,coords in rectangles.items()}
        elements.append({'from': low, 'to': high, 'faces': faces})
    if not elements:
        raise ValueError(f'No legacy model boxes in {name}')
    write_json(ASSETS / ('models/block/' + row['id'].split(':')[1] + '.json'), dict(parent='minecraft:block/block',textures=dict(all=texture, particle=texture), elements=elements))


def generate_languages(source):
    for locale, filename in [('zh_cn', 'zh_CN'), ('zh_tw', 'zh_TW'), ('en_us', 'en_US')]:
        old = {}
        for line in (source / ('lang/' + filename + '.lang')).read_text('utf-8-sig', errors='replace').splitlines():
            if '=' in line and not line.startswith('#'):
                k, v = line.split('=', 1)
                old[k] = v
        values = {'itemGroup.foodcraft': '食物工艺' if locale != 'en_us' else 'FoodCraft'}
        for key,chinese,english in [('jiqi','机器与工具','Machines and tools'),('zhiwu','作物与果树','Crops and fruit trees'),('yingliao','饮料与酒','Drinks and wine'),('zhushi','主食','Staples'),('shicai','食材','Ingredients'),('xiaodian','零食','Snacks'),('collection','收集奖励','Collection rewards')]:
            values['itemGroup.foodcraft.'+key]=english if locale=='en_us' else chinese
        for row in CATALOG:
            if row['id'].startswith('minecraft:'):
                continue
            key = row['legacy'] if ':' not in row['legacy'] else row['texture']
            prefix = 'tile' if 'constructor_args' in row else 'item'
            modernprefix = 'block' if prefix == 'tile' else 'item'
            values[modernprefix + '.' + row['id'].replace(':', '.')] = old.get(prefix + '.' + key + '.name', old.get(key + '.name', key))
        for oldname, machine in MACHINES.items():
            values['container.foodcraft.' + machine] = old.get('Title' + oldname, machine)
        values.update({'screen.foodcraft.progress': '进度：%s / %s' if locale != 'en_us' else 'Progress: %s / %s',
                       'screen.foodcraft.water': '液体：%s / 8' if locale != 'en_us' else 'Liquid: %s / 8',
                       'screen.foodcraft.heat': '火力：%s' if locale != 'en_us' else 'Heat: %s',
                       'screen.foodcraft.skill': '熟练度：%s' if locale != 'en_us' else 'Proficiency: %s'})
        translations={'remaining':('剩余：%s 秒','Remaining: %s s'),'milk':('牛奶：%s / 8','Milk: %s / 8'),
                      'transfer.full':('背包空位不足，无法返还已有食材','Not enough inventory space to return existing ingredients'),
                      'transfer.missing':('缺少食材，或食材数量不足','Missing ingredients or insufficient quantities'),
                      'oil':('花生油：%s / 8','Peanut oil: %s / 8'),
                      'jei.oil':('油：%s / 8','Oil: %s / 8'),'jei.milk':('牛奶：%s / 8','Milk: %s / 8'),'jei.water':('水：%s / 8','Water: %s / 8'),
                      'slot.output':('成品 / 失败产物','Product / failed batch'),'slot.fuel':('燃料','Fuel'),
                      'slot.liquid':('水 / 净化水；饮料机也接受牛奶；油炸机接受花生油','Water / purified water; drink maker also takes milk; fryer takes peanut oil'),
                      'slot.knife':('菜刀；黄金菜刀每次多产出一个','Kitchen knife; gold knife adds one product'),
                      'slot.ice':('冰块：冷饮冷却剂','Ice: coolant for cold drinks'),'slot.ingredient':('食材：顺序与配方相同','Ingredient: place in recipe order')}
        for key,(chinese,english) in translations.items():values['screen.foodcraft.'+key]=english if locale=='en_us' else chinese
        tooltips={
            'special_remaining':('特殊使用剩余：%s 次','特殊使用剩餘：%s 次','Special uses left: %s'),
            'heal':('右键恢复至20点生命；特殊使用在2000损伤时耗尽','右鍵恢復至20點生命；特殊使用在2000損傷時耗盡','Right-click: heal up to 20 health; breaks at 2000 wear'),
            'torch':('右键放置火把；特殊使用在2000损伤时耗尽','右鍵放置火把；特殊使用在2000損傷時耗盡','Right-click: place a torch; breaks at 2000 wear')}
        for key,strings in tooltips.items():values['item.foodcraft.multitool.'+key]=strings[{'zh_cn':0,'zh_tw':1,'en_us':2}[locale]]
        if locale=='zh_tw':
            replacements={'进':'進','体':'體','练':'練','余':'餘','败':'敗','净':'淨','饮':'飲','机':'機','块':'塊','黄':'黃','产':'產','个':'個','与':'與','树':'樹','奖':'獎','励':'勵','顺':'順'}
            for key in values:
                if key.startswith(('screen.foodcraft.','itemGroup.foodcraft.')):values[key]=values[key].translate(str.maketrans(replacements))
        overrides_path=ROOT/'tools/language_overrides.json'
        if overrides_path.exists():
            for key,value in json.loads(overrides_path.read_text('utf-8')).get(locale,{}).items():
                if key not in values:raise ValueError('Unknown reviewed language key: '+key)
                values[key]=value
        write_json(ASSETS / ('lang/' + locale + '.json'), values)


def generate_java():
    write_json(ASSETS / 'catalog.json', CATALOG)
    layouts = {}
    for old, machine in MACHINES.items():
        name = {'Gyg': 'Gyg', 'Caiban': 'Caiban', 'Guo': 'Guo', 'PDG': 'PDG', 'Nmj': 'Nmj',
                'YZJ': 'YZJ', 'Tpj': 'Tpj', 'Nt': 'Nt', 'Zl': 'Zl'}[old]
        text = read('gui/containers/Container' + name + '.java')
        slots = re.findall(r'new Slot(?:Furnace)?\([^;]+?,\s*(\d+),\s*(\d+),\s*(\d+)\)\)', text)
        layouts[machine] = [[int(v) for v in triple] for triple in slots]
    write_json(ASSETS / 'machine-layouts.json', layouts)
    write_json(ASSETS / 'recipe-manifest.json', json.loads((ROOT/'docs/recipe-manifest.json').read_text('utf-8')))
    for name in ['bread','cake','pumpkin_pie']:
        write_json(RES/f'data/minecraft/recipes/{name}.json',dict(type='foodcraft:disabled'))
    FIXES.append(dict(legacy='vanilla bread/cake/pumpkin_pie recipes',change='old removeAnyRecipe calls preserved; FoodCraft replacement recipes remain available'))
    geometry={}
    for cls in ['BlockTree','TreeBannana','TreeCoconut']:
        placements=[]
        for _,args in calls(read('plant/blocks/'+cls+'.java'),'setBlockToTree'):
            if len(args)!=5 or args[0]!='w':continue
            coords=[]
            for axis,expression in zip('xyz',args[1:4]):
                expression=expression.replace(' ','')
                if not re.fullmatch(axis+r'(?:[+-]\d+)?',expression):raise ValueError('Unsupported tree coordinate '+expression)
                coords.append(int(expression[1:] or '0'))
            block=args[4].removeprefix('this.')
            block_id='fruit' if block=='fruit' else 'minecraft:oak_leaves' if block=='Blocks.leaves' else resolve(block)
            placements.append(dict(offset=coords,block=block_id))
        if not placements:raise ValueError('Missing tree geometry for '+cls)
        geometry[cls]=placements
    write_json(ASSETS/'tree-geometry.json',geometry)
    FIXES.append(dict(legacy='fruit trees',change='pinned 1.7.10 source has no survival tree acquisition; vanilla leaves provide a 1% weighted sapling drop to make all fruit chains obtainable'))


def main():
    global LEGACY,JAVA
    parser = argparse.ArgumentParser()
    parser.add_argument('--legacy', type=Path, default=LEGACY)
    args = parser.parse_args()
    LEGACY=args.legacy.resolve();JAVA=LEGACY/'src/main/java/com/cfyifei'
    hashes=json.loads((ROOT/'tools/legacy-source-sha256.json').read_text('utf-8'))
    for relative,checksum in hashes.items():
        file=LEGACY/relative
        if not file.exists() or hashlib.sha256(file.read_bytes()).hexdigest()!=checksum:raise ValueError('Pinned baseline mismatch: '+relative)
    for directory in [DATA/'recipes',DATA/'loot_tables/blocks',ASSETS/'models',ASSETS/'blockstates']:
        if directory.exists():
            for file in directory.rglob('*.json'):
                file.resolve().relative_to(RES.resolve())
                if file.is_symlink():raise ValueError('Unexpected symlink in generated resources')
                file.unlink()
    if (ASSETS/'textures').exists():
        for file in (ASSETS/'textures').rglob('*.png'):
            file.resolve().relative_to(RES.resolve())
            if file.is_symlink():raise ValueError('Unexpected texture symlink')
            file.unlink()
    item_catalog()
    block_catalog()
    generate_recipes()
    generate_assets()
    generate_java()
    report = ROOT / 'docs'
    write_json(report / 'content-manifest.json', dict(source_commit=SHA, entries=CATALOG, id_map=IDS, fixes=FIXES))
    report.mkdir(exist_ok=True)
    with (report / 'content-manifest.csv').open('w', encoding='utf-8-sig', newline='') as f:
        fields = ['legacy', 'legacy_registry_ids', 'metadata', 'id', 'kind', 'nutrition', 'saturation', 'stack_size', 'durability', 'effect', 'crop', 'texture', 'source', 'status']
        writer = csv.DictWriter(f, fields, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(CATALOG)
    print(json.dumps(dict(entries=len(CATALOG), kinds=Counter(r['kind'] for r in CATALOG)), ensure_ascii=False))


if __name__ == '__main__':
    main()
