"""
Autocut cekirdek yardimcilari (FFmpeg bulma, sure okuma, komut calistirma).
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VIDEO_EXT = {".mp4", ".mov", ".mkv", ".webm", ".ts", ".m4v", ".avi"}
AUDIO_EXT = {".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac"}
IMG_EXT = {".png", ".jpg", ".jpeg", ".webp"}


def load_config(path: str | os.PathLike | None = None) -> dict:
    p = Path(path) if path else HERE / "config.json"
    if not p.exists():
        return {}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_config(cfg: dict, path: str | os.PathLike | None = None) -> None:
    p = Path(path) if path else HERE / "config.json"
    p.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8")


def find_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        sys.exit(
            "[HATA] ffmpeg bulunamadi.\n"
            "       Kur:  winget install Gyan.FFmpeg\n"
            "       veya:  pip install imageio-ffmpeg"
        )


def find_ffprobe() -> str | None:
    return shutil.which("ffprobe")


def run(cmd: list[str], *, quiet: bool = False) -> subprocess.CompletedProcess:
    cmd = [str(c) for c in cmd]
    dbg = os.environ.get("AUTOCUT_DEBUG_CMD")
    if dbg:
        with open(dbg, "a", encoding="utf-8") as f:
            f.write(json.dumps(cmd, ensure_ascii=False) + "\n")
    if not quiet:
        print("  > " + " ".join(f'"{c}"' if " " in c else c for c in cmd[:10]))
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(
            "Komut basarisiz:\n" + " ".join(cmd) + "\n\n" + (proc.stderr or "")[-3000:]
        )
    return proc


def duration_of(ffmpeg: str, ffprobe: str | None, path: Path) -> float:
    if ffprobe:
        p = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True,
        )
        if p.returncode == 0 and p.stdout.strip():
            try:
                return float(p.stdout.strip())
            except ValueError:
                pass
    p = subprocess.run([ffmpeg, "-i", str(path)], capture_output=True, text=True, errors="replace")
    m = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", p.stderr or "")
    if not m:
        return 0.0
    h, mnt, s = m.groups()
    return int(h) * 3600 + int(mnt) * 60 + float(s)


def esc_path(p: str | os.PathLike) -> str:
    s = str(p).replace("\\", "/")
    return s.replace(":", "\\:").replace("'", "\\'")


def ensure_dirs(*paths: Path) -> None:
    for p in paths:
        p.mkdir(parents=True, exist_ok=True)


def human(sec: float) -> str:
    m, s = divmod(max(int(sec), 0), 60)
    return f"{m:02d}:{s:02d}"


def pick_font(cfg: dict) -> str | None:
    """Windows icin uygun bir kalin font bulur."""
    for cand in [
        cfg.get("paths", {}).get("font"),
        "C:/Windows/Fonts/impact.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]:
        if cand and Path(cand).exists():
            return cand
    return None


_ENCODER_CACHE: dict[str, tuple[str, list[str]]] = {}


def pick_encoder(ffmpeg: str, preference: str = "auto") -> tuple[str, list[str]]:
    """Donanim kodlayiciyi GERCEKTEN dener; calismiyorsa libx264'e duser."""
    pref = (preference or "auto").lower()
    key = f"{ffmpeg}|{pref}"
    if key in _ENCODER_CACHE:
        return _ENCODER_CACHE[key]

    def probe(encoder: str, args: list[str]) -> bool:
        try:
            p = subprocess.run(
                [ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                 "-i", "color=c=black:s=256x256:d=0.2:r=30",
                 "-c:v", encoder, *args, "-f", "null", "-"],
                capture_output=True, text=True, timeout=25,
            )
            return p.returncode == 0 and "Cannot load" not in (p.stderr or "")
        except Exception:
            return False

    result: tuple[str, list[str]] = ("libx264", ["-preset", "veryfast", "-crf", "21"])

    candidates: list[tuple[str, list[str]]] = [
        ("h264_nvenc", ["-preset", "p4", "-rc", "vbr", "-cq", "23", "-b:v", "0"]),
        ("h264_amf", ["-quality", "balanced", "-rc", "vbr", "-b:v", "0"]),
        ("h264_qsv", ["-preset", "fast", "-global_quality", "23"]),
    ]
    for enc, args in candidates:
        if pref not in ("auto", enc.replace("h264_", "")):
            continue
        if probe(enc, args):
            result = (enc, args)
            break

    _ENCODER_CACHE[key] = result
    return result
