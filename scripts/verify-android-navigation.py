"""Navigation regression on the dedicated CampSchedule emulator; never a physical phone."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default=shutil.which('adb'))
parser.add_argument('--device', default='emulator-5556')
parser.add_argument('--output', default='artifacts/android/issue-nav-fix')
args = parser.parse_args()
if not args.adb:
    parser.error('Provide --adb or put adb on PATH.')
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)


def adb(*values):
    return subprocess.check_output([args.adb, '-s', args.device, *values], timeout=40).decode('utf-8', errors='replace')


if not args.device.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1' or adb('emu', 'avd', 'name').splitlines()[0].strip() != 'CampSchedule_API35':
    raise SystemExit('Refusing display changes outside the dedicated CampSchedule emulator.')

original_density = re.search(r'Override density: (\d+)', adb('shell', 'wm', 'density'))
original_scale = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
width = int(re.search(r'Physical size: (\d+)x', adb('shell', 'wm', 'size')).group(1))
counter = 0


def dump():
    global counter
    counter += 1
    remote = f'/sdcard/camp-navigation-{counter}.xml'
    result = adb('shell', 'uiautomator', 'dump', remote)
    if 'dumped to:' not in result:
        raise AssertionError('UI capture did not finish')
    local = out / 'navigation-current.xml'
    adb('pull', remote, str(local))
    adb('shell', 'rm', remote)
    return list(ET.parse(local).getroot().iter('node'))


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


results = []
try:
    for width_dp, scale in [(360, 1.0), (360, 1.3), (390, 1.3), (411, 1.0), (320, 1.3), (320, 2.0), (360, 2.0)]:
        adb('shell', 'wm', 'density', str(round(width * 160 / width_dp)))
        adb('shell', 'settings', 'put', 'system', 'font_scale', str(scale))
        adb('shell', 'am', 'force-stop', 'cn.aurange.schedule')
        adb('shell', 'am', 'start', '-W', '-n', 'cn.aurange.schedule/.MainActivity')
        nodes = dump()
        nav = [node for node in nodes if node.get('text') == '随手记'][-1]
        x1, y1, x2, y2 = bounds(nav)
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        nodes = dump()
        values = []
        for title in ['今日', '营期', '随手记', '设置']:
            node = [node for node in nodes if node.get('text') == title][-1]
            box = bounds(node)
            values.append({'title': title, 'height': box[3] - box[1], 'bounds': box})
        heights = [value['height'] for value in values]
        assert max(heights) <= min(heights) * 1.15, (width_dp, scale, values)
        remote = '/sdcard/camp-navigation-screen.png'
        adb('shell', 'screencap', '-p', remote)
        filename = f'navigation-{width_dp}-font{round(scale * 100)}.png'
        adb('pull', remote, str(out / filename))
        result = {'width_dp': width_dp, 'font_scale': scale, 'labels': values, 'screenshot': filename}
        results.append(result)
        print(f'PASS: {width_dp}dp / font {scale}: all four labels occupy one equal-height line', flush=True)
    (out / 'navigation-results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
finally:
    adb('shell', 'wm', 'density', original_density.group(1) if original_density else 'reset')
    adb('shell', 'settings', 'put', 'system', 'font_scale', original_scale if original_scale not in ('', 'null') else '1.0')
