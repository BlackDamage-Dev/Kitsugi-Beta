import subprocess
import time
import os
import xml.etree.ElementTree as ET
import re

ADB = r"C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ARTIFACT_DIR = r"C:\Users\Administrator\.gemini\antigravity\brain\89696702-c0b4-43db-836b-9baa35fd258c"
PACKAGE = "com.kitsugi.animelist"
ACTIVITY = "com.kitsugi.animelist.MainActivity"

def run_adb(args, timeout=30):
    cmd = [ADB] + args
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return res.stdout, res.stderr

def take_screenshot(name):
    os.makedirs(ARTIFACT_DIR, exist_ok=True)
    local_path = os.path.join(ARTIFACT_DIR, f"{name}.png")
    run_adb(["shell", "screencap", "-p", "/sdcard/screen.png"])
    run_adb(["pull", "/sdcard/screen.png", local_path])
    print(f"[Screenshot] Saved: {local_path}")
    return local_path

def get_ui_dump():
    run_adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    out, _ = run_adb(["shell", "cat", "/sdcard/window_dump.xml"])
    return out

def find_node(xml_content, text=None, content_desc=None, class_name=None):
    if not xml_content:
        return None
    try:
        root = ET.fromstring(xml_content)
    except Exception as e:
        print(f"XML parse error: {e}")
        return None

    for elem in root.iter("node"):
        t = elem.attrib.get("text", "")
        cd = elem.attrib.get("content-desc", "")
        cls = elem.attrib.get("class", "")

        match = True
        if text is not None and text.lower() not in t.lower():
            match = False
        if content_desc is not None and content_desc.lower() not in cd.lower():
            match = False
        if class_name is not None and class_name.lower() not in cls.lower():
            match = False

        if match:
            bounds = elem.attrib.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                center_x = (x1 + x2) // 2
                center_y = (y1 + y2) // 2
                return {"x": center_x, "y": center_y, "text": t, "desc": cd, "bounds": (x1, y1, x2, y2)}
    return None

def tap_center(node):
    if node:
        run_adb(["shell", "input", "tap", str(node["x"]), str(node["y"])])
        return True
    return False

def check_crashes():
    logs, _ = run_adb(["logcat", "-d"])
    crash_keywords = [
        "IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints",
        "CheckScrollableContainerConstraintsKt",
        "FATAL EXCEPTION"
    ]
    found = []
    for line in logs.splitlines():
        for kw in crash_keywords:
            if kw in line:
                found.append(line)
    return found

print("Helper script ready.")
