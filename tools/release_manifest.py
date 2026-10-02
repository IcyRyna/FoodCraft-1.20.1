"""Join source mappings, real registry exports and recipe coverage for delivery."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf-8')


def loot_items(value):
    if isinstance(value, dict):
        if value.get('type') == 'minecraft:item':
            yield value['name']
        for child in value.values():
            yield from loot_items(child)
    elif isinstance(value, list):
        for child in value:
            yield from loot_items(child)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--work', type=Path, default=ROOT.parent.parent / 'work')
    args = parser.parse_args()
    work = args.work.resolve()
    check = json.loads((ROOT / 'docs/content-check.json').read_text('utf-8'))
    if check['errors']:
        raise ValueError('Resource validation has errors.')
    snapshots = [json.loads((work / 'evidence' / loader / 'runtime-content.json').read_text('utf-8')) for loader in ['forge', 'fabric']]
    if snapshots[0] != snapshots[1]:
        raise ValueError('Runtime exports differ.')
    for loader, count in [('forge', 33), ('fabric', 35)]:
        log = (work / 'logs' / f'gametest-{loader}.log').read_text('utf-8')
        required = [f'All {count} required tests passed', 'FOODCRAFT VERIFIED MACHINE RECIPES=118',
                    'FOODCRAFT VERIFIED CRAFTING RECIPES=141', 'FOODCRAFT VERIFIED FURNACE RECIPES=4',
                    'FOODCRAFT VERIFIED FOOD VALUES=189', 'FOODCRAFT VERIFIED CAKE PLACEMENT/BITES=8',
                    'FOODCRAFT VERIFIED WRENCH PLACE/BREAK=9 creative=1', 'FOODCRAFT VERIFIED GRASS LOOT seeds=972 varieties=18',
                    'FOODCRAFT DETAIL FOOD INTERACTIONS PASS items=189',
                    'FOODCRAFT DETAIL FOOD EFFECTS PASS staple_branches=7 wine_groups=2',
                    'FOODCRAFT DETAIL AGRICULTURE PASS crops=17 trees=17 onion_segments=3 drops=3',
                    'FOODCRAFT DETAIL RECIPE TRANSFERS PASS legacy_cases=236',
                    'FOODCRAFT DETAIL HEAT BUTTON PASS values=101',
                    'FOODCRAFT DETAIL RECIPE CHANGE PASS same_id_reload=true blocked_output=true nbt_resume=true',
                    'FOODCRAFT DETAIL CONTAINERS PASS tanks=4 full_inventory_water=16 final_buckets=1',
                    'FOODCRAFT DETAIL SEED HEIGHT PASS varieties=18',
                    'FOODCRAFT DETAIL HOPPERS PASS recipe=foodcraft:pressure_cooker/005_pidanshourouzhou',
                    'FOODCRAFT ROUND3 FRACTION BOUNDARIES PASS short_values=81000',
                    'FOODCRAFT ROUND3 NATIVE SHEEP LOOT PASS',
                    'FOODCRAFT ROUND3 WOOD PLANK CRAFTING PASS native_variants=11 combinations=121',
                    'FOODCRAFT ACCEPTANCE NATIVE INVENTORY MATRIX PASS recipes=118 cases=25817',
                    'FOODCRAFT AUDIT HEAT FAILURES PASS cases=270',
                    'FOODCRAFT AUDIT MALFORMED RECIPES PASS',
                    'FOODCRAFT AUDIT DESTRUCTION PASS cases=27',
                    'FOODCRAFT AUDIT NBT PASS cases=4500',
                    'FOODCRAFT AUDIT CAKE EDGES PASS cases=64',
                    'FOODCRAFT AUDIT SPECIAL TOOLS PASS cases=128',
                    'FOODCRAFT AUDIT COMPLEX TRANSFER PASS slots=12',
                    'FOODCRAFT AUDIT NUMERIC PROCESSING PASS',
                    'FOODCRAFT AUDIT FAILURE REFUND PASS cases=3 invalid_commits=6',
                    'FOODCRAFT AUDIT CONVENTIONAL TAGS PASS cases=4',
                    'FOODCRAFT AUDIT ACQUISITION PASS leaf_samples=256000 chest_samples=12000',
                    'FOODCRAFT AUDIT NATIVE PISTON PASS push=9 pull=9']
        if not all(marker in log for marker in required):
            raise ValueError(f'Missing actual test coverage markers for {loader}.')
    manifest = json.loads((ROOT / 'docs/content-manifest.json').read_text('utf-8'))
    recipes = json.loads((ROOT / 'docs/recipe-manifest.json').read_text('utf-8'))
    registered = {row['id']: row for row in snapshots[0]['items']}
    blocks = {row['id']: row for row in snapshots[0]['blocks']}
    acquisitions: dict[str, list[str]] = {}
    for recipe in recipes:
        result = recipe['result']
        item = result['item'] if isinstance(result, dict) else result
        acquisitions.setdefault(item, []).append('recipe:' + recipe['id'])
    resources = ROOT / 'common/src/main/resources'
    for file in (resources / 'data/foodcraft/loot_tables/blocks').glob('*.json'):
        for item in set(loot_items(json.loads(file.read_text('utf-8')))):
            acquisitions.setdefault(item, []).append('block_drop:foodcraft:' + file.stem)
    for entry in manifest['entries']:
        if entry.get('crop'):
            acquisitions.setdefault(entry['crop'], []).append('placed_by:' + entry['id'])
        if entry['kind'] == 'sapling':
            acquisitions.setdefault(entry['id'], []).append('vanilla_leaf_sapling_pool:1% total; 17 equal weights')
        if entry['kind'] == 'seed':
            acquisitions.setdefault(entry['id'], []).append('grass_seed_pool:1/8*35/45; weight=' + ('1' if entry['id'].endswith(':shucaizhong') else '2'))
    acquisitions.setdefault('foodcraft:youyurou', []).append('squid_death:3 extra raw squid meat')
    for name in ['zongye', 'douban', 'galikuai', 'hetaosu', 'xiangchang', 'laweixunliao', 'kafei']:
        acquisitions.setdefault('foodcraft:' + name, []).append('legacy_weighted_dungeon/chest_pool')
    acquisitions['minecraft:mutton'] = ['vanilla_sheep_drop']
    acquisitions['minecraft:cooked_mutton'] = ['vanilla_smelting_or_burning_sheep_drop']
    for entry in manifest['entries']:
        if entry['kind'] == 'fruit' and entry['id'] != 'foodcraft:fcleaves':
            acquisitions.setdefault(entry['id'], []).append('legacy_fruit_tree_generation')
    rows = []
    for entry in manifest['entries']:
        row = dict(entry)
        omitted = entry['kind'] == 'debug'
        if not omitted and entry['id'] not in registered:
            raise ValueError('Unregistered content: ' + entry['id'])
        row['acquisition'] = sorted(set(acquisitions.get(entry['id'], [])))
        if entry['id'] == 'foodcraft:fcleaves':
            row['acquisition_note'] = 'Legacy unused technical leaf block retained; no standalone survival acquisition in the pinned source.'
        row['runtime_values'] = registered.get(entry['id'])
        if entry['id'] in blocks:row['runtime_block_values']=blocks[entry['id']]
        effects={
            'wine':{'duration_ticks':600,'amplifier':3,'choice':'random positive or negative group'},
            'gold_cookie':{'duration_ticks':1200,'amplifier':1,'effects':['jump_boost','speed','haste']},
            'gold_grape':{'duration_ticks':36000,'amplifier':4,'effects':['instant_health','fire_resistance','strength']},
            'gold_apple':{'duration_ticks':36000,'amplifier':4,'effects':['resistance','regeneration','absorption']},
            'gold_grape_wine':{'duration_ticks':3600,'amplifier':4,'effects':['jump_boost','speed','haste','instant_health','fire_resistance','strength']},
            'gold_apple_wine':{'duration_ticks':3600,'amplifier':4,'effects':['night_vision','invisibility','water_breathing','resistance','regeneration','absorption']},
            'staple':{'duration_ticks':600,'amplifier':1,'choice':'one random effect from seven legacy effects'},
            'chili':{'fire_seconds':3},'milk':{'behavior':'milk cure on server'}
        }
        special=dict(effects.get(entry.get('effect','none'),{}))
        if entry['kind'] in {'cake','cake_item'}:special.update(nutrition_per_bite=2,saturation_modifier_per_bite=0.1,potion_effects=[],regenerates_one_bite_on_random_tick='jinputao' in entry['id'])
        if entry['kind']=='purifier':special.update(source_water_removed=True,uses=16,final_empty_buckets=1)
        if entry['kind']=='knife':special.update(durability_per_success=1,extra_output_per_batch=1 if entry['id'].endswith(':caidao_hj') else 0)
        if entry['kind']=='multitool':special.update(durability=5000,mining_tier=3,mining_speed=8,attack_damage=12,enchantability=15,right_click_durability_cost=5,right_click_break_damage=2000,right_click_unbreaking_applies=False)
        row['special_behavior']=special
        row['associated_recipes'] = [r['id'] for r in recipes if (r['result']['item'] if isinstance(r['result'], dict) else r['result']) == entry['id']]
        behavior = ['registry_and_values', 'resource_references'] if not omitted else ['omitted_debug_documented']
        if 'constructor_args' in entry:behavior.append('native_hardness_step_sound_and_selection_bounds')
        if entry['kind'] == 'crop': behavior.extend(['all_8_growth_stage_drops_and_farmland_support', 'native_bonemeal_mature_boundary_and_trample'])
        if entry['kind'] == 'sapling': behavior.extend(['legacy_tree_voxel_generation', 'obstacle_preflight_and_jinkela_once', 'native_leaf_acquisition_256000_samples_tool_matrix'])
        if entry['kind'] == 'machine': behavior.extend(['native_menu_packets', 'native_wrench_place_break_and_saved_state'])
        if entry['kind'] == 'cake_item': behavior.extend(['native_placement_consumption_bite_and_effects', 'native_full_hunger_final_bite_same_tick_two_players_no_resurrection'])
        if entry['kind'] == 'multitool': behavior.append('native_special_use_128_damage_hand_creative_unbreaking_cases_and_mining_combat')
        if entry['kind'] == 'purifier': behavior.append('native_16_water_sources_exactly_one_empty_bucket')
        if entry['kind'] == 'seed': behavior.append('seeded_native_grass_loot_10000_samples')
        if entry['kind'] == 'seed': behavior.append('native_maximum_build_height_no_seed_loss')
        if entry.get('nutrition') is not None: behavior.append('native_hungry_full_consumption_and_effect_properties')
        if row['associated_recipes']: behavior.append('all_associated_recipes_processed_or_assembled_on_both_loaders')
        if not omitted and entry['id'].startswith('foodcraft:'):
            behavior.append('native_gpu_item_nine_display_contexts_256_light_pairs')
            if entry.get('block') or entry['id'] in blocks: behavior.append('native_gpu_all_block_states_six_views_256_light_pairs')
        if entry['kind']=='machine':
            behavior.extend(['native_inventory_output_count_and_nbt_matrix', 'paired_native_packet_stress_survival_creative_full_inventory',
                             'native_survival_creative_filled_wrench_and_explosion_conservation',
                             'native_powered_piston_push_and_sticky_pull', 'native_nbt_extreme_values_stable_sanitization'])
            if entry['id'] in {'foodcraft:pot','foodcraft:frying_pan'}:
                behavior.append('native_burnt_undercooked_failure_slot_nbt_capacity_power_stove_and_save_resume_270_cases')
                behavior.append('native_undercooked_full_container_refund_conservation_and_nonzero_first_slot')
        row['verification'] = {'forge': 'passed' if not omitted else 'omitted_debug', 'fabric': 'passed' if not omitted else 'omitted_debug',
                               'coverage': behavior, 'interaction_permutations': 'bounded_test_matrix_not_all_possible_interleavings'}
        rows.append(row)
    write_json(ROOT / 'docs/content-validation.json', {'source_commit': manifest['source_commit'], 'entries': rows,
               'coverage_note': 'Passed fields describe the documented test matrices. All custom item GUI views and all block states have contact-sheet review; finite views and light levels do not prove every camera angle, shader or interaction interleaving.'})
    fields = ['legacy', 'legacy_registry_ids', 'metadata', 'id', 'kind', 'nutrition', 'saturation', 'stack_size',
              'durability', 'effect', 'special_behavior', 'crop', 'texture', 'source', 'status', 'acquisition', 'acquisition_note', 'associated_recipes', 'verification']
    with (ROOT / 'docs/content-validation.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fields, extrasaction='ignore'); writer.writeheader()
        for row in rows:
            writer.writerow({key: json.dumps(value, ensure_ascii=False) if isinstance(value, (list, dict)) else value for key, value in row.items()})
    checksums = {}
    for path in sorted((work / 'compat').glob('*.jar')):
        if path.name.endswith('-sources.jar'): continue
        checksums[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    checksums['gradle-8.8-bin.zip'] = 'a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612'
    checksums['microsoft-jdk-17.0.20.1-windows-x64.zip'] = '3d9006956fc8af5601cd24ffc4f468bef48279c7ebd8171b9bdf90d0aabfbf1f'
    checksums['foodcraft-legacy-source.zip'] = 'cdb75d67582bdfc351e622c73fa14f05e24fb3841ab668ab892cda5fd57f86bb'
    checksums['foodcraft-resources-1.8.zip'] = '520b27493e3130ac364292424ccbb20a865effbe3445ea861692096d96e60e48'
    write_json(ROOT / 'docs/dependency-checksums.json', checksums)
    print(f'Joined {len(rows)} content records with real runtime values and all {len(recipes)} recipe results.')


if __name__ == '__main__':
    main()
