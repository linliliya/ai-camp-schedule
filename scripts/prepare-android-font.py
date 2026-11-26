"""Install original MiSans and Lora app fonts, with licenses (requires fonttools and pypdf)."""
from pathlib import Path
from urllib.request import urlopen
from urllib.parse import quote
from io import BytesIO
from zipfile import ZipFile
import hashlib
from fontTools.ttLib import TTFont
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
cache = root / 'artifacts/android/workbench'
cache.mkdir(parents=True, exist_ok=True)
fonts = root / 'android/app/src/main/res/font'
assets = root / 'android/app/src/main/assets'
archive = cache / 'MiSans.zip'
if not archive.exists():
    archive.write_bytes(urlopen('https://hyperos.mi.com/font-download/MiSans.zip', timeout=120).read())
if hashlib.sha256(archive.read_bytes()).hexdigest() != 'b6aa1fc827035922612df8edf36e5609bca1c5441e25cd57572204569b7b81d9':
    raise RuntimeError('MiSans archive changed; review the upstream release before updating the pinned hash.')
with ZipFile(archive) as z:
    for weight in ['Regular', 'Medium']:
        original = z.read(f'MiSans/otf/MiSans-{weight}.otf')
        target = fonts / f'misans_{weight.lower()}.otf'
        target.write_bytes(original)  # Preserve original files: no subsetting, renaming or weight edits.
        print(target.name, len(original), hashlib.sha256(original).hexdigest())
    copyright_notice = TTFont(BytesIO(z.read('MiSans/otf/MiSans-Regular.otf')))['name'].getDebugName(0)
pdf_url = 'https://hyperos.mi.com/font-download/' + quote('MiSans字体知识产权许可协议.pdf')
pdf = urlopen(pdf_url, timeout=30).read()
assets.joinpath('misans-license.pdf').write_bytes(pdf)
license_text = copyright_notice + '\n\n' + '\n\n'.join(p.extract_text() for p in PdfReader(BytesIO(pdf)).pages)
assets.joinpath('font-license.txt').write_text('\n'.join(line.rstrip() for line in license_text.splitlines()) + '\n', encoding='utf-8', newline='\n')

lora_base = 'https://raw.githubusercontent.com/cyrealtype/Lora-Cyrillic/403b1a66ca2e79f81d749c5299559e168591a4df/'
lora = urlopen(lora_base + 'fonts/variable/Lora%5Bwght%5D.ttf', timeout=45).read()
fonts.joinpath('lora_regular.ttf').write_bytes(lora)
notice = urlopen(lora_base + 'OFL.txt', timeout=30).read().decode('utf-8')
assets.joinpath('lora-license.txt').write_text('\n'.join(line.rstrip() for line in notice.splitlines())+'\n', encoding='utf-8', newline='\n')
print('lora_regular.ttf', len(lora), hashlib.sha256(lora).hexdigest())
for name in ['schedule_sans_regular.ttf', 'schedule_sans_semibold.ttf']:
    fonts.joinpath(name).unlink(missing_ok=True)
