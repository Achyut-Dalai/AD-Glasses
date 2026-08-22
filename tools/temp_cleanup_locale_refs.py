from pathlib import Path
import re

root = Path('android/AD-Glasses/app/src/main/res')
pattern = re.compile(r'^\s*<string name="compose_external_[^"]+">.*?</string>\s*\n?', re.MULTILINE)

changed = 0
for path in root.glob('values*/strings_compose.xml'):
    text = path.read_text(encoding='utf-8')
    updated = pattern.sub('', text)
    if updated != text:
        path.write_text(updated, encoding='utf-8')
        changed += 1

if changed != 9:
    raise SystemExit(f'expected 9 locale/base resource files to change, changed {changed}')

print(f'removed retired compose_external resources from {changed} files')
