"""Exercise the refresh picker on a disposable, already-running Android emulator."""
from pathlib import Path
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
assert serial.startswith('emulator-'), 'Use a disposable emulator'

def adb(*args):
    return subprocess.check_output(['adb', '-s', serial, *args], text=True)

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1'

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/refresh-ui.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/refresh-ui.xml')).iter('node'))

def find(text):
    for _ in range(4):
        for node in nodes():
            if node.get('text') == text:
                return node
        time.sleep(1)
    raise AssertionError(f'Missing UI text: {text}')

def tap(text):
    node = find(text)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)

reports = Path(os.environ.get('SMOKE_REPORTS', 'smoke-reports'))
reports.mkdir(parents=True, exist_ok=True)

def screenshot(name):
    data = subprocess.check_output(['adb', '-s', serial, 'exec-out', 'screencap', '-p'])
    (reports / name).write_bytes(data)

adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
find('YOUR RADAR')
find('Find something worth watching')
screenshot('UI-watchlist.png')
tap('Find something worth watching')
find('Find your next favourite')
screenshot('UI-search.png')
adb('shell', 'input', 'keyevent', '4')
tap('Settings')
find('PRICE MONITORING')
tap('Automatic refresh')
for hours in [1, 3, 6, 12, 24]:
    find(f'Every {hours} {"hour" if hours == 1 else "hours"}')
tap('Every 1 hour')
tap('Save')
find('Every 1 hour')
tap('Automatic refresh')
tap('Every 24 hours')
tap('Cancel')
find('Every 1 hour')
adb('shell', 'am', 'force-stop', 'ro.priceradar')
adb('shell', 'am', 'start', '-W', '-n', 'ro.priceradar/.MainActivity')
tap('Settings')
find('Every 1 hour')
tap('Automatic refresh')
tap('Every 6 hours')
tap('Save')
find('Every 6 hours')
screenshot('UI-settings.png')
print('UI remake rendered; refresh picker choices, save, cancel and persistence passed')
