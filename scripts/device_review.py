"""Small ADB helper for reviewing this app with its public blank sample only.

Never dismisses a lock screen. Screenshots require this app to own focus.
Usage: device_review.py start | tap "text" | snapshot name | wait "text"
"""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = r"C:\Users\dever\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PACKAGE = "com.example.gabayai.enhanced"
COMPONENT = PACKAGE + "/com.example.gabayai.MainActivity"
OUT = ROOT / "docs/evidence"

def adb(*args):
    return subprocess.check_output([ADB, *args], text=True, encoding="utf-8")

def guard():
    policy = adb("shell", "dumpsys", "window", "policy")
    if "mIsShowing=true" in policy:
        raise RuntimeError("Phone locked: unlock normally before review")
    focus = "\n".join(line for line in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus=" in line)
    if COMPONENT not in focus:
        raise RuntimeError("Gabay.AI does not own focus; no screenshot or tap performed")

def ui():
    guard()
    adb("shell", "uiautomator", "dump", "/sdcard/gabay-review.xml")
    source = adb("shell", "cat", "/sdcard/gabay-review.xml")
    adb("shell", "rm", "/sdcard/gabay-review.xml")
    return source, ET.fromstring(source)

action = sys.argv[1]
if action == "start":
    if "mIsShowing=true" in adb("shell", "dumpsys", "window", "policy"):
        raise RuntimeError("Phone is locked")
    print(adb("shell", "am", "start", "-W", "-n", COMPONENT))
elif action == "tap":
    candidates = []
    for _ in range(4):
        _, tree = ui()
        candidates = [node for node in tree.iter("node") if sys.argv[2] in (node.get("text"), node.get("content-desc"))]
        if candidates:
            break
        scrolling = [node for node in tree.iter("node") if node.get("scrollable") == "true"]
        if not scrolling:
            break
        area = list(map(int, re.findall(r"\d+", scrolling[0].get("bounds", ""))))
        assert len(area) == 4
        x = (area[0] + area[2]) // 2
        guard()
        adb("shell", "input", "swipe", str(x), str(area[3] - 80), str(x), str(area[1] + 80), "300")
    if not candidates:
        raise RuntimeError("Requested visible control was not found")
    bounds = list(map(int, re.findall(r"\d+", candidates[0].get("bounds", ""))))
    assert len(bounds) == 4
    guard()
    print(adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)))
elif action == "snapshot":
    name = sys.argv[2]
    assert re.fullmatch(r"[a-z0-9-]+", name)
    source, tree = ui()
    (OUT / (name + "-ui.xml")).write_text(source, encoding="utf-8")
    guard()
    with (OUT / (name + ".png")).open("wb") as target:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=target, check=True)
    print([node.get("text") for node in tree.iter("node") if node.get("text")])
elif action == "wait":
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        _, tree = ui()
        if any(sys.argv[2] == node.get("text") for node in tree.iter("node")):
            print("Visible:", sys.argv[2])
            break
        time.sleep(.5)
    else:
        raise RuntimeError("Expected app text did not appear")
else:
    raise ValueError("Unknown review command")
