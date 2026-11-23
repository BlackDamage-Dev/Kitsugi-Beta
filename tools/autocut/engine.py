"""
Autocut kurgu motoru.

Akis:
  klasoru tara -> her klibin ses analizini yap -> tepe noktalarini bul ->
  en iyileri sec -> normalize et -> arka arkaya birlestir ->
  tepe noktalarina zoom-punch + flash + shake + meme + yazı ekle ->
  telifsiz muzigi beat'lere gore ekle -> -14 LUFS -> mp4

Tum fonksiyonlar headless calisir (GUI olmadan test edilebilir).
"""
from __future__ import annotations

import json
import random
import re
import subprocess
import tempfile
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Callable

import music as music_lib
from core import (AUDIO_EXT, VIDEO_EXT, duration_of, ensure_dirs, esc_path, find_ffmpeg,
                  find_ffprobe, human, load_config, pick_encoder, pick_font, run)

Log = Callable[[str], None]


def _noop(_msg: str) -> None:
    pass


# --------------------------------------------------------------------------- #
# 1) ANALIZ
# --------------------------------------------------------------------------- #

@dataclass
class Clip:
    path: Path
    duration: float
    peaks: list[float]
    score: float
    tag: str = "hype"
    width: int = 0
    height: int = 0

    def to_dict(self) -> dict:
        d = asdict(self)
        d["path"] = str(self.path)
        return d

    def view(self, start: float, dur: float) -> "Clip":
        """Kaydın `start`ten başlayan `dur` saniyelik bölümüne inanmış görünüm.

        Tepe noktaları bölümün içine göre yerel zamana çevrilir ve türü
        (ace/clutch/...) o bölüme göre yeniden hesaplanır. Böylece 30 dakikalık
        bir kaydın farklı bölümleri farklı meme'ler alabilir.
        """
        local = [round(p - start, 3) for p in self.peaks if start - 0.01 <= p <= start + dur]
        if not local:
            local = [round(dur * 0.5, 2)]
        v = Clip(path=self.path, duration=round(dur, 3), peaks=local, score=self.score,
                 width=self.width, height=self.height)
        v.tag = classify(v)
        return v


def energy_curve(ffmpeg: str, path: Path, block: float = 0.5) -> list[float]:
    """Sesi `block` saniyelik parcalara bolup her parcanin RMS (dB) degerini verir."""
    n = max(int(48000 * block), 1)
    cmd = [
        ffmpeg, "-hide_banner", "-nostats", "-i", str(path),
        "-vn", "-af",
        f"aresample=48000,asetnsamples=n={n}:p=0,astats=metadata=1:reset=1,"
        "ametadata=print:key=lavfi.astats.Overall.RMS_level:file=-",
        "-f", "null", "-",
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out: list[float] = []
    for line in (proc.stdout or "").splitlines():
        m = re.search(r"RMS_level=(-?[\d.]+|-inf)", line)
        if m:
            out.append(-90.0 if m.group(1) == "-inf" else float(m.group(1)))
    return out


def find_peaks(curve: list[float], block: float, *, rel_db: float = 7.0,
               min_gap_s: float = 1.0, baseline_s: float = 6.0) -> list[float]:
    """Ses enerjisi tepe noktalarini (kill/bagirma/patlama) bulur.

    Sabit esik kullanmaz: oyun kayitlarinda ses surekli yuksek olur, bu yuzden
    medyan gibi global bir taban tepe sayisini 0'a indirir. Bunun yerine
    hareketli ortalamaya dayali *yerel* taban kiyaslar -> sadece ansin
    yukselisler (vurgular) tepe sayilir.
    """
    if not curve:
        return []
    n = len(curve)
    win = max(3, int(baseline_s / block))
    half = win // 2
    prefix = [0.0]
    for v in curve:
        prefix.append(prefix[-1] + v)
    base = [(prefix[min(n, i + half + 1)] - prefix[max(0, i - half)]) /
            max(1, min(n, i + half + 1) - max(0, i - half)) for i in range(n)]

    peaks: list[float] = []
    for factor in (1.0, 0.7, 0.5, 0.35):
        thr = rel_db * factor
        peaks = []
        last_t = -999.0
        for i in range(1, n - 1):
            if curve[i] < base[i] + thr:
                continue
            if curve[i] < curve[i - 1] or curve[i] < curve[i + 1]:
                continue
            t = i * block
            if t - last_t < min_gap_s:
                continue
            peaks.append(round(t, 2))
            last_t = t
        if len(peaks) >= 3:
            break
    return peaks


def resolution_of(ffmpeg: str, path: Path) -> tuple[int, int]:
    p = subprocess.run([ffmpeg, "-hide_banner", "-i", str(path)],
                       capture_output=True, text=True, errors="replace")
    m = re.search(r"(\d{2,5})x(\d{2,5})[\s,]", p.stderr or "")
    if not m:
        return 0, 0
    w, h = int(m.group(1)), int(m.group(2))
    if w > h * 3 or h > w * 3:  # bozuk okuma
        return 0, 0
    return w, h


def analyze_clip(ffmpeg: str, ffprobe: str | None, path: Path,
                 *, block: float = 0.5, rel_db: float = 7.0) -> Clip:
    duration = duration_of(ffmpeg, ffprobe, path)
    curve = energy_curve(ffmpeg, path, block)
    peaks = find_peaks(curve, block, rel_db=rel_db)
    if not peaks and curve:
        peaks = [max(range(len(curve)), key=lambda i: curve[i]) * block]
    vals = curve or [0.0]
    peak_val = max(vals)
    loudness = max(peak_val - sum(vals) / len(vals), 0.0)
    bonus = 2.0 if 8 <= duration <= 45 else 0.0
    if duration <= 60:
        score = len(peaks) * 3.0 + loudness * 0.8 + bonus
    else:
        # uzun kayitlarda tepe sayisi surekle dogru orantili buyur;
        # yogunluga (tepe/dakika) cevir ki kisa kliplerle kiyaslanabilsin
        per_min = len(peaks) / max(duration / 60.0, 0.25)
        score = min(per_min, 12.0) * 3.0 + loudness * 0.8 + bonus
    w, h = resolution_of(ffmpeg, path)
    return Clip(path=path, duration=duration, peaks=peaks, score=round(score, 2),
                width=w, height=h)


SCARY_HINTS = ("backrooms", "korku", "horror", "scary", "scp", "liminal",
               "ghost", "hayalet", "dehset")


def classify(clip: Clip, config_tags: dict | None = None) -> str:
    """Klibin 'turu'ne gore meme etiketi secer.

    Ses analizi "korku"yu tek basina anlayamaz; bu yuzden dosya/klasor adina
    bakilir (backrooms, horror, korku...). Boylece Backrooms kayitlari scary
    meme'leri ve scary muzigi alir.
    """
    name = str(clip.path).lower()
    if any(h in name for h in SCARY_HINTS):
        return "scary"
    n = len(clip.peaks)
    if n >= 4:
        return "ace"
    if n == 3:
        return "clutch"
    if n == 2:
        return "hype"
    if clip.peaks and clip.duration > 0 and clip.peaks[-1] > clip.duration * 0.72:
        return "whiff"
    return "hype" if n else "chill"


def scan_folder(ffmpeg: str, ffprobe: str | None, folder: Path, *,
                recursive: bool = False, log: Log = _noop,
                progress: Callable[[int, int], None] | None = None) -> list[Clip]:
    it = folder.rglob("*") if recursive else folder.glob("*")
    files = sorted([f for f in it if f.suffix.lower() in VIDEO_EXT and f.is_file()],
                   key=lambda p: p.stat().st_mtime)
    out: list[Clip] = []
    for i, f in enumerate(files, 1):
        try:
            log(f"  analiz {i}/{len(files)}: {f.name}")
            c = analyze_clip(ffmpeg, ffprobe, f)
            if c.duration >= 2.0:
                c.tag = classify(c)
                out.append(c)
        except Exception as exc:
            log(f"  [!] atlandi {f.name}: {exc}")
        if progress:
            progress(i, len(files))
    return out


def plan(clips: list[Clip], target_duration: float, max_clips: int = 8) -> list[Clip]:
    """En yuksek skorlulardan baslayarak hedef sureyi doldurur."""
    ordered = sorted(clips, key=lambda c: c.score, reverse=True)
    chosen: list[Clip] = []
    total = 0.0
    for c in ordered:
        if len(chosen) >= max_clips:
            break
        if total >= target_duration:
            break
        chosen.append(c)
        total += min(c.duration, 25.0)
    return chosen


# --------------------------------------------------------------------------- #
# 2) MUZIK BEAT ANALIZI
# --------------------------------------------------------------------------- #

def beats_from_music(ffmpeg: str, path: Path, block: float = 0.1,
                     max_beats: int = 400) -> list[float]:
    """Muzikteki beat/onset zamanlarini bulur (saniye).

    Iki asamali:
      1) enerji akisi (flux): enerji birden yukselirse = vurgu. Gercek muzikte
         ses seviyesi surekli yuksek olabilir; mutlak esik bu yuzden calismaz.
      2) flux bir sey bulamazsa enerji tepelerine duser (medyan + MAD esigi).
    """
    curve = energy_curve(ffmpeg, path, block)
    if len(curve) < 4:
        return []

    lo, hi = min(curve), max(curve)
    if hi - lo < 0.5:            # neredeyse dutuk ses -> beat yok
        return []
    norm = [(v - lo) / (hi - lo) for v in curve]

    # --- 1) pozitif enerji akisi ---
    flux = [0.0]
    for i in range(1, len(norm)):
        flux.append(max(0.0, norm[i] - norm[i - 1]))

    def _adaptive(seq: list[float]) -> float:
        srt = sorted(seq)
        med = srt[len(srt) // 2]
        mad = sorted(abs(x - med) for x in seq)[len(seq) // 2]
        return med + 1.5 * mad + 0.02

    beats = _pick(flux, block, _adaptive(flux), 0.25)
    if not beats:                                    # --- 2) yedek: tepe noktalari ---
        beats = _pick(norm, block, _adaptive(norm), 0.35)
    return beats[:max_beats]


def _pick(seq: list[float], block: float, threshold: float,
          min_gap: float) -> list[float]:
    """`seq` icinde esik uzerindeki yerel maksimumlari zaman olarak dondurur."""
    out: list[float] = []
    last = -99.0
    for i in range(1, len(seq) - 1):
        v = seq[i]
        if v < threshold:
            continue
        if v < seq[i - 1] or v < seq[i + 1]:
            continue
        t = round(i * block, 3)
        if t - last < min_gap:
            continue
        out.append(t)
        last = t
    return out


# --------------------------------------------------------------------------- #
# 3) RENDER
# --------------------------------------------------------------------------- #

@dataclass
class MemeCall:
    """Video icinde bir meme gostergeci."""
    path: Path | None
    text: str
    tag: str
    at: float
    duration: float = 1.25
    callout: str = ""


def _normalize_segment(ffmpeg: str, clip: Clip, start: float, dur: float, dst: Path,
                       *, vertical: bool, fps: int, crf: int) -> None:
    if vertical:
        vf = (f"scale=1080:1920:force_original_aspect_ratio=increase,"
              f"crop=1080:1920,setsar=1,fps={fps}")
    else:
        vf = (f"scale=1920:1080:force_original_aspect_ratio=decrease,"
              f"pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps={fps}")
    cmd = [
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
        "-ss", f"{max(start, 0):.3f}", "-t", f"{dur:.3f}", "-i", str(clip.path),
        "-vf", vf,
        "-af", "aresample=48000,aformat=channel_layouts=stereo,loudnorm=I=-16:LRA=11:TP=-1.5",
        "-c:v", "libx264", "-preset", "ultrafast", "-crf", "24",
        "-threads", "0",
        "-pix_fmt", "yuv420p", "-r", str(fps),
        "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2",
        "-movflags", "+faststart", str(dst),
    ]
    run(cmd, quiet=True)


def _concat(ffmpeg: str, parts: list[Path], dst: Path) -> None:
    if len(parts) == 1:
        import shutil as _sh

        _sh.copy2(parts[0], dst)
        return
    lst = dst.parent / "concat.txt"
    with open(lst, "w", encoding="utf-8") as f:
        for p in parts:
            s = str(p.resolve()).replace("\\", "/").replace("'", "'\\''")
            f.write(f"file '{s}'\n")
    run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0",
         "-i", str(lst), "-c", "copy", "-movflags", "+faststart", str(dst)], quiet=True)


def _sum_between(times: list[float], dur: float) -> str:
    if not times:
        return "0"
    return "+".join(f"between(t,{t:.3f},{t + dur:.3f})" for t in times[:24])


def render_video(segments: list[tuple[Clip, float, float]],
                 out: Path, *,
                 vertical: bool = True, fps: int = 30, crf: int = 21,
                 music: Path | None = None,
                 music_volume: float = 0.13,
                 memes: list[MemeCall] | None = None,
                 srt: Path | None = None,
                 intro: str = "", outro: str = "",
                 font: str | None = None,
                 beat_flash: bool = True,
                 shake_strength: float = 9.0,
                 encoder: str = "auto",
                 log: Log = _noop,
                 progress: Callable[[float], None] | None = None) -> dict:
    """
    Tek geciste kurgu: tum klipler tek komutta olceklenir, birlestirilir,
    efekt + meme + muzik uygulanir. Ara dosya yok, iki kez kodlama yok.
    segment: (Clip, baslangic_saniyesi, suresi)
    """
    ffmpeg = find_ffmpeg()
    ffprobe = find_ffprobe()
    ensure_dirs(out.parent)
    memes = [m for m in (memes or [])
             if (m.path is None or Path(m.path).exists()) and 0 <= m.at < 10 ** 6]

    # --- timeline ---
    timeline: list[tuple[Clip, float]] = []
    cursor = 0.0
    for clip, _start, dur in segments:
        timeline.append((clip, cursor))
        cursor += dur
    total = cursor

    peak_times: list[float] = []
    for clip, offset in timeline:
        for pk in clip.peaks:
            if 0.0 <= pk <= clip.duration:
                peak_times.append(round(offset + pk, 3))
    peak_times = sorted(set(peak_times))[:18]

    beats: list[float] = []
    if music and Path(music).exists():
        raw_beats = beats_from_music(ffmpeg, Path(music))
        # videoya sigan beat'leri al; cok fazlarsa esit aralikla seyrelt ki
        # flash sadece ilk saniyelerde kalmasin.
        beats = [b for b in raw_beats if b < total]
        if len(beats) > 30:
            step = len(beats) / 30.0
            beats = [beats[int(i * step)] for i in range(30)]
        log(f"  muzik: {len(raw_beats)} beat ({len(beats)} flash)")

    memes = sorted([m for m in memes if m.at < total], key=lambda m: m.at)
    log(f"  {len(memes)} meme, {len(peak_times)} tepe noktasi, {human(total)}")

    # --- girdiler ---
    inputs: list[str] = []
    for _clip, start, dur in segments:
        inputs += ["-ss", f"{max(start, 0):.3f}", "-t", f"{dur:.3f}", "-i", str(_clip.path)]
    n = len(segments)

    music_idx = None
    if music and Path(music).exists():
        music_idx = n
        inputs += ["-stream_loop", "-1", "-t", f"{total + 2:.3f}", "-i", str(music)]

    meme_base = n + (1 if music_idx is not None else 0)
    for m in memes:
        if m.path:
            inputs += ["-loop", "1", "-framerate", str(fps), "-t", f"{m.duration:.3f}",
                       "-i", str(m.path)]

    # --- video filtreleri ---
    W, H = (1080, 1920) if vertical else (1920, 1080)
    sx, sy = int(W * 1.06), int(H * 1.06)

    parts: list[str] = []
    for i in range(n):
        parts.append(f"[{i}:v]scale={sx}:{sy}:in_range=auto:out_range=tv,"
                     f"setsar=1,fps={fps}[v{i}]")
        parts.append(f"[{i}:a]aresample=48000,aformat=channel_layouts=stereo,"
                     f"asetpts=PTS-STARTPTS[a{i}]")
    parts.append("".join(f"[v{i}][a{i}]" for i in range(n)) + f"concat=n={n}:v=1:a=1[basev][basea]")

    chain = _build_chain(
        base_label="basev",
        W=W, H=H, sx=sx, sy=sy, fps=fps,
        shake=_sum_between(peak_times, 0.22),
        flash=_sum_between(peak_times, 0.09),
        beatf=_sum_between(beats, 0.05) if (beat_flash and beats) else "0",
        shake_strength=shake_strength,
        memes=memes, stream_idx=meme_base, font=font, srt=srt,
        intro=intro, outro=outro, total=total,
    )

    # --- ses ---
    if music_idx is not None:
        audio_filter = (
            f"[{music_idx}:a]aformat=channel_layouts=stereo,volume={music_volume:.3f}[mus];"
            "[mus][basea]sidechaincompress=threshold=0.035:ratio=9:attack=15:release=350[duck];"
            "[duck][basea]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[mixed];"
            "[mixed]loudnorm=I=-14:LRA=11:TP=-1.5,aresample=48000[afinal]"
        )
    else:
        audio_filter = "[basea]loudnorm=I=-14:LRA=11:TP=-1.5,aresample=48000[afinal]"

    enc, enc_args = pick_encoder(ffmpeg, encoder)
    cmd = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", *inputs,
           "-filter_complex", ";".join(parts) + ";" + chain + ";" + audio_filter,
           "-map", "[vout]", "-map", "[afinal]",
           "-c:v", enc, *enc_args, "-pix_fmt", "yuv420p", "-r", str(fps),
           "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
           "-movflags", "+faststart", "-t", f"{max(total - 0.05, 0.1):.3f}", str(out)]
    log("  render basladi...")
    run(cmd, quiet=True)

    size = out.stat().st_size / 1024 / 1024
    log(f"  cikti: {out.name}  {human(total)}  {size:.1f} MB")
    return {"duration": total, "size_mb": round(size, 1), "peaks": peak_times,
            "memes": len(memes), "beats": len(beats)}


def _build_chain(*, base_label: str, W: int, H: int, sx: int, sy: int, fps: int,
                 shake: str, flash: str, beatf: str, shake_strength: float,
                 memes: list[MemeCall], stream_idx: int, font: str | None,
                 srt: Path | None, intro: str, outro: str, total: float) -> str:
    """Tek bir video filtre zinciri olusturur."""
    parts: list[str] = []
    parts.append(f"[{base_label}]"
                 f"crop={W}:{H}:x='(iw-ow)/2+{shake_strength:.1f}*sin(t*38)*({shake})':"
                 f"y='(ih-oh)/2+{shake_strength * 0.6:.1f}*cos(t*33)*({shake})',"
                 f"setsar=1,fps={fps},"
                 f"eq=brightness='0.22*({flash})+0.06*({beatf})':contrast=1.02:saturation=1.05[vbase]")

    prev = "vbase"
    text_memes: list[MemeCall] = []
    img_i = 0
    for i, m in enumerate(memes):
        if not m.path:
            text_memes.append(m)
            continue
        idx = stream_idx + img_i
        img_i += 1
        parts.append(
            f"[{idx}:v]scale={int(W * 0.42)}:-1:force_original_aspect_ratio=decrease,"
            f"format=rgba,fade=t=in:st=0:d=0.10:alpha=1,"
            f"fade=t=out:st={max(m.duration - 0.18, 0.1):.2f}:d=0.18:alpha=1,"
            f"setpts=PTS+{m.at:.3f}/TB[m{i}]"
        )
        corner = i % 4
        if corner == 0:
            ox, oy = f"{int(W * 0.04)}", f"H-h-{int(H * 0.22)}"
        elif corner == 1:
            ox, oy = f"W-w-{int(W * 0.04)}", f"{int(H * 0.10)}"
        elif corner == 2:
            ox, oy = f"{int(W * 0.04)}", f"{int(H * 0.10)}"
        else:
            ox, oy = f"W-w-{int(W * 0.04)}", f"H-h-{int(H * 0.22)}"
        cur = f"v{i}"
        parts.append(f"[{prev}][m{i}]overlay=x={ox}:y={oy}:"
                     f"enable='between(t,{m.at:.3f},{m.at + m.duration:.3f})'[{cur}]")
        prev = cur

    # altyazi
    if srt and Path(srt).exists():
        parts.append(f"[{prev}]subtitles={esc_path(srt)}:force_style="
                     f"'FontName=Arial,FontSize={26 if H > W else 22},"
                     f"PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,"
                     f"BorderStyle=1,Outline=2,Shadow=1,MarginV={110 if H > W else 60}'[vsub]")
        prev = "vsub"

    # yazilar
    if font and Path(font).exists():
        if intro:
            t = _drawtext_safe(intro)
            parts.append(f"[{prev}]drawtext=fontfile='{esc_path(font)}':text='{t}':"
                         f"fontcolor=white:fontsize={int(H * 0.045)}:borderw=5:bordercolor=black:"
                         f"x=(w-text_w)/2:y=(h-text_h)/2:enable='lt(t,2.6)'[vi]")
            prev = "vi"
        if outro:
            t = _drawtext_safe(outro)
            parts.append(f"[{prev}]drawtext=fontfile='{esc_path(font)}':text='{t}':"
                         f"fontcolor=white:fontsize={int(H * 0.035)}:borderw=4:bordercolor=black:"
                         f"x=(w-text_w)/2:y=(h-text_h)/2:"
                         f"enable='gt(t,{max(total - 2.4, 0):.2f})'[vo]")
            prev = "vo"
        # resimsiz meme'ler: yazi karti (siyah serit + buyuk yazi)
        for i, m in enumerate(text_memes):
            t = _drawtext_safe(m.text or m.callout)
            if not t:
                continue
            box = f"tb{i}"
            parts.append(f"[{prev}]drawbox=x=0:y={int(H * 0.40)}:w=iw:h={int(H * 0.20)}:"
                         f"color=black@0.55:t=fill:"
                         f"enable='between(t,{m.at:.3f},{m.at + m.duration:.3f})'[{box}]")
            prev = box
            cur = f"tx{i}"
            parts.append(f"[{prev}]drawtext=fontfile='{esc_path(font)}':text='{t}':"
                         f"fontcolor=white:fontsize={int(H * 0.062)}:borderw=5:bordercolor=black:"
                         f"x=(w-text_w)/2:y=(h-text_h)/2:"
                         f"enable='between(t,{m.at:.3f},{m.at + m.duration:.3f})'[{cur}]")
            prev = cur

        # meme callout'lari (buyuk yazi)
        for i, m in enumerate(memes):
            if not m.callout or not m.path:
                continue
            t = _drawtext_safe(m.callout)
            cur = f"c{i}"
            parts.append(f"[{prev}]drawtext=fontfile='{esc_path(font)}':text='{t}':"
                         f"fontcolor=white:fontsize={int(H * 0.075)}:borderw=6:bordercolor=black:"
                         f"x=(w-text_w)/2:y=(h-text_h)/2:"
                         f"enable='between(t,{m.at:.3f},{m.at + 0.9:.3f})'[{cur}]")
            prev = cur

    if prev != "vout":
        # format=yuv420p: tum kaynaklari (yuvj420p/full-range dahil) YouTube'un
        # en sorunsuz oynadigi piksel formatina normalize eder.
        parts.append(f"[{prev}]format=yuv420p[vout]")
    return ";".join(parts)


def _drawtext_safe(text: str) -> str:
    """drawtext icin metni temizler."""
    t = text.replace("\\", "").replace("'", "").replace(":", "\\:")
    t = re.sub(r"\s+", " ", t).strip()
    return t[:60]


# --------------------------------------------------------------------------- #
# 4) TAM BORU HATTI
# --------------------------------------------------------------------------- #



def _mood_of(segments) -> str:
    """Kurgudaki baskin ture gore muzik ruhu secer (epic / hype / scary / chill)."""
    tags = [(item[0] if isinstance(item, (tuple, list)) else item).tag
            for item in segments]
    if not tags:
        return "epic"
    for candidate in ("scary", "chill", "hype"):
        if tags.count(candidate) * 2 >= len(tags):
            return candidate
    if "whiff" in tags or "fail" in tags:
        return "hype"
    return "epic"


def pick_music_for(cfg: dict, segments, log: Log = _noop) -> Path | None:
    """Kullanici parca sectiyse onu, secmediyse ture uygun guvenli parcayi bulur."""
    a = cfg.get("audio", {})
    chosen = str(a.get("music") or "")
    if chosen and Path(chosen).exists():
        return Path(chosen)
    if not a.get("enabled", True):
        return None
    folder = Path(cfg.get("paths", {}).get("music", "data/music"))
    if not folder.is_absolute():
        folder = Path(__file__).resolve().parent / folder
    tracks = music_lib.load_tracks(folder)
    mood = _mood_of(segments)
    t = music_lib.pick_track(tracks, random.Random(), mood=mood)
    if t is None:
        log("  [M] Guvenli lisansli muzik yok -> muziksiz render")
        return None
    log(f"  [M] Muzik: {t.title}  ({t.license}, {mood})")
    return t.path


def _windows_for(clip: Clip, target: float, max_segments: int) -> list[tuple[float, float]]:
    """Bir klibin icinden kullanilacak (baslangic, sure) pencerelerini bulur.

    Kisa klip -> tek pencere, en guclu tepe noktasinin etrafi.
    Uzun kayit (30 dk vb.) -> kaydin tamamini kaplayan, tepe noktalarina
    yapistirilmis birden fazla pencere. Boylece 40 dakikalik kaydin sadece
    ilk 20 saniyesi degil, en hareketli bolumleri kullanilir.
    """
    if clip.duration <= 30.0:
        dur = min(clip.duration, max(8.0, min(clip.duration, 20.0)))
        if clip.duration <= dur:
            return [(0.0, clip.duration)]
        center = clip.peaks[0] if clip.peaks else clip.duration / 2
        start = max(0.0, min(center - dur * 0.35, clip.duration - dur))
        return [(round(start, 3), round(dur, 3))]

    k = max(1, min(max_segments, 6, int(target // 12) or 1))
    win = min(22.0, max(10.0, target / k))
    out: list[tuple[float, float]] = []
    for i in range(k):
        ideal = clip.duration * (i + 0.5) / k
        if clip.peaks:
            center = min(clip.peaks, key=lambda p: abs(p - ideal))
        else:
            center = ideal
        start = max(0.0, min(center - win * 0.35, clip.duration - win))
        if any(abs(start - s) < win * 0.75 for s, _d in out):
            continue
        out.append((round(start, 3), round(win, 3)))
    if not out:
        out.append((0.0, round(min(clip.duration, win), 3)))
    return out


def _pick_meme(pool: list[MemeCall], tag: str, rng: random.Random,
               used: list[MemeCall]) -> MemeCall | None:
    """Ture uygun meme secer; yoksa hype'a, o da yoksa herhangi birine duser."""
    for want in (tag, "hype", None):
        cands = [m for m in pool if want is None or m.tag == want]
        if not cands:
            continue
        # resimli meme'ler once: yazi karti sadece resim yoksa devreye girer
        with_img = [m for m in cands if m.path]
        if with_img:
            cands = with_img
        fresh = [m for m in cands if m.path not in {u.path for u in used}] or cands
        return rng.choice(fresh)
    return None


def build_video(source_dir: Path, out: Path, cfg: dict, *,
                target_duration: float | None = None,
                max_clips: int = 8,
                memes: list[MemeCall] | None = None,
                log: Log = _noop,
                progress: Callable[[int, int, str], None] | None = None) -> dict:
    """Taray -> sec -> kurgula. GUI ve CLI ayni fonksiyonu cagirir."""
    ffmpeg = find_ffmpeg()
    ffprobe = find_ffprobe()
    v = cfg.get("video", {})
    a = cfg.get("audio", {})

    target = target_duration or v.get("target_duration", 45)
    max_clips = max_clips or v.get("max_clips", 8)
    vertical = v.get("vertical", True)

    log(f"[1/3] Klasor taraniyor: {source_dir}")
    clips = scan_folder(ffmpeg, ffprobe, source_dir,
                        recursive=v.get("recursive", False), log=log)
    if not clips:
        raise RuntimeError("Klasorde uygun video bulunamadi (en az 2 sn olmali).")

    log(f"      {len(clips)} klip bulundu, en iyileri seciliyor...")
    chosen = plan(clips, target, max_clips)
    if not chosen:
        raise RuntimeError("Secilecek klip yok.")

    segments: list[tuple[Clip, float, float]] = []
    used_dur = 0.0
    for c in chosen:
        if len(segments) >= max_clips or used_dur >= target * 1.15:
            break
        for start, dur in _windows_for(c, target, max_clips - len(segments)):
            segments.append((c.view(start, dur), start, dur))
            used_dur += dur
            if len(segments) >= max_clips or used_dur >= target * 1.15:
                break

    log(f"[2/3] Kurgu Plani:")
    for i, (c, s, d) in enumerate(segments, 1):
        log(f"      {i}. {c.path.name}  {human(s)}-{human(s + d)}  [{c.tag}]  skor {c.score}")

    # meme'ler: tepe noktalarina yerlestir
    calls: list[MemeCall] = []
    if memes:
        rng = random.Random()
        offset = 0.0
        for c, s, d in segments:
            local_peaks = [p for p in c.peaks if 0.0 <= p <= d]
            for p in local_peaks[:2]:
                at = round(offset + p, 2)
                if any(abs(mm.at - at) < 1.2 for mm in calls):
                    continue
                m = _pick_meme(memes, c.tag, rng, calls)
                if m is None:
                    continue
                calls.append(MemeCall(m.path, m.text, m.tag, at,
                                      duration=m.duration, callout=m.callout))
            offset += d

    log(f"[3/3] Render ediliyor...")
    music_file = pick_music_for(cfg, segments, log)
    result = render_video(
        segments, out,
        vertical=vertical,
        fps=int(v.get("fps", 30)),
        crf=int(v.get("crf", 21)),
        music=music_file,
        music_volume=float(a.get("music_volume", 0.13)),
        memes=calls,
        srt=None,
        intro=v.get("intro_text", ""),
        outro=v.get("outro_text", ""),
        font=pick_font(cfg),
        beat_flash=v.get("beat_flash", True),
        shake_strength=float(v.get("shake_strength", 9.0)),
        encoder=str(v.get("encoder", "auto")),
        log=log,
    )
    result["segments"] = [
        {"file": str(c.path), "start": round(s, 2), "end": round(s + d, 2),
         "tag": c.tag, "score": c.score}
        for c, s, d in segments
    ]
    result["meme_calls"] = [asdict(m) | {"path": str(m.path) if m.path else None}
                            for m in calls]
    return result


# --------------------------------------------------------------------------- #
# 5) TEK KLIPTEN SHORT (otomatik izleme modu icin)
# --------------------------------------------------------------------------- #

def build_single(clip_path: Path, out: Path, cfg: dict, *,
                 memes: list[MemeCall] | None = None,
                 log: Log = _noop) -> dict:
    ffmpeg = find_ffmpeg()
    ffprobe = find_ffprobe()
    v = cfg.get("video", {})
    a = cfg.get("audio", {})

    log(f"  analiz: {clip_path.name}")
    c = analyze_clip(ffmpeg, ffprobe, Path(clip_path))
    if c.duration < 2:
        raise RuntimeError("Klip cok kisa (en az 2 sn).")
    c.tag = classify(c)

    max_dur = float(v.get("single_max_duration", 45))
    dur = min(c.duration, max_dur)
    center = c.peaks[0] if c.peaks else c.duration / 2
    start = max(0.0, min(center - dur * 0.3, c.duration - dur))
    if c.duration <= dur:
        start, dur = 0.0, c.duration

    seg = c.view(start, dur)
    log(f"  [{seg.tag}] {human(dur)}  tepe: {len(seg.peaks)}")

    calls: list[MemeCall] = []
    if memes:
        rng = random.Random()
        for p in seg.peaks[:2]:
            m = _pick_meme(memes, seg.tag, rng, calls)
            if m is None:
                continue
            calls.append(MemeCall(m.path, m.text, m.tag,
                                  round(p, 2), m.duration, m.callout))

    music_file = pick_music_for(cfg, [(seg, start, dur)], log)
    return render_video(
        [(seg, start, dur)], out,
        vertical=v.get("vertical", True),
        fps=int(v.get("fps", 30)),
        crf=int(v.get("crf", 21)),
        music=music_file,
        music_volume=float(a.get("music_volume", 0.13)),
        memes=calls,
        srt=None,
        intro=v.get("intro_text", ""),
        outro=v.get("outro_text", ""),
        font=pick_font(cfg),
        beat_flash=v.get("beat_flash", True),
        shake_strength=float(v.get("shake_strength", 9.0)),
        encoder=str(v.get("encoder", "auto")),
        log=log,
    )
