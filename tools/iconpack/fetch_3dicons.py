#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
3Dicons setini (Vijay Verma, CC0) indirir.

Kaynak : https://3dicons.co  (özgün proje, CC0 1.0)
Ayna   : https://github.com/Vyshnav2255/notion-icons-3d  (Apache-2.0, render'ları barındırır)
Stil   : front/gradient  (cam + gradyan; koyu temada yüksek kontrast)

Kullanım: python3 tools/iconpack/fetch_3dicons.py
Çıktı   : tools/iconpack/raw3d/<kaynak-ad>.png
"""
import os
import subprocess

STYLE = "front/gradient"
ANGLE, STYLE_NAME = STYLE.split("/")
API = "https://api.github.com/repos/Vyshnav2255/contents/icons/"
# repo adı (API yolu)
REPO = "repos/Vyshnav2255/notion-icons-3d/contents/icons/"

# indirilecek 3dicons varlıkları (kaynak adı -> tekrar kullanım notu)
NAMES = [
    "card", "link", "colour-palette", "video-camera", "play", "pause", "next", "back",
    "puzzle", "sphere", "wifi", "locker", "trash-can", "setting", "travel", "clock",
    "bulb", "megaphone", "sheild", "pencil", "magic-trick", "tool", "chat-text", "chat",
    "headphone", "toggle", "computer", "lab", "rocket", "sun", "moon", "key", "star",
    "picture", "heart", "copy", "chart", "tick", "lock", "folder", "bell", "calender",
    "flash", "thumb-up", "mobile", "pin", "explorer", "bookmark", "zoom", "boy",
]


def curl(url, out=None):
    cmd = ["curl", "-sL", "-H", "Accept: application/vnd.github.raw", url]
    if out:
        cmd += ["-o", out]
        subprocess.run(cmd, check=True)
        return None
    return subprocess.run(cmd, check=True, capture_output=True).stdout


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, "raw3d")
    os.makedirs(out_dir, exist_ok=True)
    ok, fail = 0, []
    for name in NAMES:
        target = os.path.join(out_dir, name + ".png")
        if os.path.exists(target) and os.path.getsize(target) > 0:
            ok += 1
            continue
        url = "https://api.github.com/" + REPO + "%s/%s-%s-%s.png" % (
            STYLE, name, ANGLE, STYLE_NAME)
        try:
            curl(url, target)
            if os.path.getsize(target) < 1000:
                raise RuntimeError("indirme başarısız (boyut çok küçük)")
            ok += 1
            print("OK  %-16s %7d B" % (name, os.path.getsize(target)))
        except Exception as exc:  # noqa: BLE001
            fail.append((name, str(exc)))
            print("FAIL %-16s %s" % (name, exc))
    print("\n%d indirildi, %d hata" % (ok, len(fail)))
    for n, e in fail:
        print("  eksik:", n, e)


if __name__ == "__main__":
    main()
