"""Lossless legacy sprite conversion for Minecraft's mipmapped block/item atlas."""
from __future__ import annotations

import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def normalize(assets: Path) -> list[dict]:
    changes = []
    pot = assets / 'textures/block/guo.png'
    with Image.open(pot) as source:
        if source.size == (100, 32):
            original = source.convert('RGBA')
            padded = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
            padded.paste(original, (0, 0))
            assert padded.crop((0, 0, 100, 32)).tobytes() == original.tobytes()
            padded.save(pot, compress_level=9)
            for path in (assets / 'models').rglob('*.json'):
                model = json.loads(path.read_text('utf-8'))
                aliases = {'#' + key for key, value in model.get('textures', {}).items() if value == 'foodcraft:block/guo'}
                altered = False
                for element in model.get('elements', []):
                    for face in element.get('faces', {}).values():
                        if face.get('texture') in aliases and 'uv' in face:
                            face['uv'][0] = round(face['uv'][0] * 100 / 128, 8)
                            face['uv'][2] = round(face['uv'][2] * 100 / 128, 8)
                            altered = True
                if altered:
                    path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', 'utf-8')
            changes.append({'texture': 'block/guo', 'old_size': [100, 32], 'new_size': [128, 32],
                            'conversion': 'transparent right padding; source pixels unchanged; horizontal model UV coordinates adjusted'})
        elif source.size != (128, 32):
            raise ValueError('Unexpected legacy pot texture dimensions: ' + str(source.size))
    peach = assets / 'textures/item/itemtaozi.png'
    with Image.open(peach) as source:
        if source.size == (18, 18):
            original = source.convert('RGBA')
            assert original.getchannel('A').getbbox() == (1, 1, 17, 17), 'Peach sprite has visible pixels outside its transparent border'
            cropped = original.crop((1, 1, 17, 17))
            assert cropped.tobytes() == original.crop((1, 1, 17, 17)).tobytes()
            cropped.save(peach, compress_level=9)
            changes.append({'texture': 'item/itemtaozi', 'old_size': [18, 18], 'new_size': [16, 16],
                            'conversion': 'remove one transparent border pixel on each side; all visible pixels unchanged'})
        elif source.size != (16, 16):
            raise ValueError('Unexpected legacy peach texture dimensions: ' + str(source.size))
    return changes


if __name__ == '__main__':
    changes = normalize(ROOT / 'common/src/main/resources/assets/foodcraft')
    print(json.dumps(changes, ensure_ascii=False, indent=2))
