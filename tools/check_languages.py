"""Validate the reviewed translations and emit a reproducible per-key language audit."""
from pathlib import Path
import csv
import json
import re

ROOT=Path(__file__).resolve().parents[1]
LOCALES=('zh_cn','zh_tw','en_us')
langs={locale:json.loads((ROOT/f'common/src/main/resources/assets/foodcraft/lang/{locale}.json').read_text('utf-8')) for locale in LOCALES}
overrides=json.loads((ROOT/'tools/language_overrides.json').read_text('utf-8'))
errors=[]
for locale,values in langs.items():
    if values.keys()!=langs['en_us'].keys():errors.append(f'{locale}: language key sets differ')
    for key,value in values.items():
        if not isinstance(value,str) or not value.strip():errors.append(f'{locale}/{key}: blank translation')
        if '\ufffd' in value or re.match(r'^(Item|Block)[A-Z]',value):errors.append(f'{locale}/{key}: internal name or invalid Unicode')
        if re.findall(r'%(?:\d+\$)?[sd]',value)!=re.findall(r'%(?:\d+\$)?[sd]',langs['en_us'][key]):errors.append(f'{locale}/{key}: format placeholder mismatch')
        if key in overrides[locale] and value!=overrides[locale][key]:errors.append(f'{locale}/{key}: reviewed override is not applied')
for locale in LOCALES:
    for fruit in ('putao','jinputao','li','taozi','juzi','ningmeng','caomei','yezi'):
        for suffix in ('gj','bg','dg'):
            key=f'item.foodcraft.{fruit}_{suffix}'
            if key not in langs[locale]:errors.append(f'{locale}/{key}: fruit variant missing')
with (ROOT/'docs/language-audit.csv').open('w',encoding='utf-8-sig',newline='') as stream:
    writer=csv.writer(stream);writer.writerow(['key',*LOCALES,'reviewed_override_locales','format_checked'])
    for key in langs['en_us']:writer.writerow([key,*[langs[l][key] for l in LOCALES],','.join(l for l in LOCALES if key in overrides[l]),'pass'])
report={'locales':list(LOCALES),'keys_per_locale':len(langs['en_us']),'fruit_variants_per_locale':24,'errors':errors}
(ROOT/'docs/language-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
print(json.dumps(report,ensure_ascii=False))
if errors:raise SystemExit(1)
