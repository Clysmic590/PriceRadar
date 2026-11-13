"""Verify that the bottom selection pill can be scrubbed between destinations."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

serial = os.environ.get("ANDROID_SERIAL", "emulator-5554")
assert serial.startswith("emulator-"), "Run only on a disposable emulator"


def adb(*args):
    return subprocess.check_output(["adb", "-s", serial, *args], text=True)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/navigation.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/navigation.xml")).iter("node"))


def center(text):
    for node in nodes():
        if node.get("text") == text:
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
            return (left + right) // 2, (top + bottom) // 2
    raise AssertionError(f"Missing UI text: {text}")


def wait_for(text):
    for _ in range(8):
        if any(node.get("text") == text for node in nodes()):
            return
        time.sleep(0.5)
    raise AssertionError(f"Screen did not show: {text}")


assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1"
watch_x, nav_y = center("Watchlist")
settings_x, _ = center("Settings")

# A slow swipe begins on the selected pill, matching a hold-and-slide gesture.
adb("shell", "input", "swipe", str(watch_x), str(nav_y), str(settings_x), str(nav_y), "900")
wait_for("Quick actions")

watch_x, nav_y = center("Watchlist")
settings_x, _ = center("Settings")
adb("shell", "input", "swipe", str(settings_x), str(nav_y), str(watch_x), str(nav_y), "900")
wait_for("YOUR RADAR")
print("Bottom navigation drag passed in both directions")
