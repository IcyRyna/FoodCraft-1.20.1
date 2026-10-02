"""Create a clean, portable source archive without build caches or game binaries."""
from __future__ import annotations
import hashlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'.gradle', 'build', 'dist', '__pycache__', '.git', 'work'}


def main():
    archive = ROOT.parent / 'foodcraft-1.20.1-source-2.0.0.zip'
    files = [file for file in ROOT.rglob('*') if file.is_file() and not set(file.relative_to(ROOT).parts).intersection(EXCLUDED)
             and file.suffix not in {'.pyc', '.download', '.part'}]
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for file in sorted(files):
            relative = file.relative_to(ROOT).as_posix()
            info = zipfile.ZipInfo('foodcraft-1.20.1/' + relative, (2026, 10, 2, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if relative == 'gradlew' else 0o100644) << 16
            target.writestr(info, file.read_bytes())
    with zipfile.ZipFile(archive) as target:
        bad = target.testzip()
        if bad: raise ValueError('Source archive failed CRC verification: ' + bad)
    deliverables = [archive] + sorted((ROOT / 'dist').glob('foodcraft-1.20.1-*-2.0.0.jar'))
    checksums = ''.join(hashlib.sha256(file.read_bytes()).hexdigest() + '  ' +
        file.relative_to(ROOT.parent).as_posix() + '\n' for file in deliverables)
    (ROOT.parent / 'foodcraft-1.20.1-SHA256SUMS.txt').write_text(checksums, 'utf-8')
    # Keep the historical filename in sync for existing download links.
    (ROOT.parent / 'FoodCraft-SHA256SUMS.txt').write_text(checksums, 'utf-8')
    print(f'Source archive: {archive.name}; {len(files)} files; {archive.stat().st_size} bytes.')


if __name__ == '__main__':
    main()
