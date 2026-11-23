"""
KITSUGI AUTOCUT - Windows 10 icin otomatik video edit programi.

Baslat:      python app.py
Headless:    python app.py --headless --source "C:/Klipler" --out "C:/Cikti/a.mp4"
"""
from __future__ import annotations

import argparse
import json
import os
import queue
import random
import subprocess
import sys
import threading
import time
import traceback
from pathlib import Path

from core import (AUDIO_EXT, IMG_EXT, VIDEO_EXT, ensure_dirs, find_ffmpeg, human,
                  load_config, save_config)
import engine
import memes as meme_lib
import music as music_lib
import metadata as meta_lib
import uploader

try:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    HAS_TK = True
except Exception:  # pragma: no cover
    HAS_TK = False
    tk = None
    ttk = None

_Base = tk.Tk if HAS_TK else object

HERE = Path(__file__).resolve().parent
BG = "#0d1117"
PANEL = "#161b22"
PANEL2 = "#1c2230"
BORDER = "#262c3a"
TEXT = "#e6edf3"
MUTED = "#8b949e"
ACCENT = "#7c4dff"
ACCENT_H = "#946bff"
GREEN = "#3fb950"
RED = "#f85149"
YELLOW = "#d29922"
FONT = "Segoe UI"


# --------------------------------------------------------------------------- #
# HEADLESS (test / sunucu kullanimi)
# --------------------------------------------------------------------------- #

def run_headless(args) -> int:
    cfg = load_config(args.config or (HERE / "config.json"))
    _mp = Path(cfg.get("paths", {}).get("memes", "data/memes"))
    if not _mp.is_absolute():
        _mp = HERE / _mp
    meme_defs = meme_lib.load_library(_mp)
    memes = [engine.MemeCall(m.path, m.text, m.tags[0], 0, 1.25, m.callout) for m in meme_defs]

    out = Path(args.out)
    ensure_dirs(out.parent)
    if args.file:
        result = engine.build_single(Path(args.file), out, cfg, memes=memes, log=print)
    else:
        result = engine.build_video(Path(args.source), out, cfg, memes=memes, log=print,
                                    target_duration=args.duration or None)
    print(json.dumps({k: v for k, v in result.items() if k != "segments"}, indent=2,
                     ensure_ascii=False, default=str))
    return 0


# --------------------------------------------------------------------------- #
# GUI
# --------------------------------------------------------------------------- #

class App(_Base):
    def __init__(self) -> None:
        super().__init__()
        self.cfg = load_config(HERE / "config.json")
        self.q: queue.Queue = queue.Queue()
        self.worker: threading.Thread | None = None
        self.watch_thread: threading.Thread | None = None
        self.watching = False
        self.stop_flag = False
        self.seen: set[str] = set()
        self.last_output: Path | None = None
        self.memedefs: list[meme_lib.MemeDef] = []
        self.tracks: list[music_lib.Track] = []

        self.title("KITSUGI AUTOCUT")
        self.geometry("1040x760")
        self.minsize(960, 680)
        self.configure(bg=BG)

        self._style()
        self._layout()
        self.refresh_memes()
        self.refresh_music()

        self.protocol("WM_DELETE_WINDOW", self._on_close)
        self.after(120, self._poll)
        self.log("KITSUGI AUTOCUT hazir.")
        self.log("Klasorunu sec, ayarlarini yap, BASLAT'a bas. Gerisini o yapar.")
        self._status("Hazir", GREEN)

    # ---------------- stil ---------------- #
    def _style(self) -> None:
        s = ttk.Style(self)
        try:
            s.theme_use("clam")
        except Exception:
            pass
        s.configure("TNotebook", background=BG, borderwidth=0, tabmargins=[0, 8, 0, 0])
        s.configure("TNotebook.Tab", background=PANEL, foreground=MUTED,
                    padding=[18, 10], font=(FONT, 10, "bold"), borderwidth=0)
        s.map("TNotebook.Tab",
              background=[("selected", PANEL2)],
              foreground=[("selected", TEXT)])
        s.configure("TFrame", background=BG)
        s.configure("TLabel", background=BG, foreground=TEXT, font=(FONT, 10))
        s.configure("Muted.TLabel", foreground=MUTED)
        s.configure("Horizontal.TScale", background=PANEL, troughcolor=PANEL,
                    bordercolor=BORDER, lightcolor=ACCENT, darkcolor=ACCENT)
        s.configure("Horizontal.TProgressbar", troughcolor=PANEL, background=ACCENT,
                    bordercolor=PANEL, lightcolor=ACCENT, darkcolor=ACCENT, thickness=10)
        s.configure("TCombobox", fieldbackground=PANEL, background=PANEL,
                    foreground=TEXT, arrowcolor=MUTED, bordercolor=BORDER)
        s.map("TCombobox", fieldbackground=[("readonly", PANEL)],
              foreground=[("readonly", TEXT)], selectbackground=[("readonly", PANEL)],
              selectforeground=[("readonly", TEXT)])

    def _btn(self, parent, text, command, *, primary=False, small=False, fg=None):
        b = tk.Button(parent, text=text, command=command,
                      bg=ACCENT if primary else PANEL2,
                      activebackground=ACCENT_H if primary else "#242b3a",
                      fg="#ffffff" if primary else (fg or TEXT),
                      activeforeground="#ffffff",
                      font=(FONT, 9 if small else 10, "bold"),
                      relief="flat", bd=0, padx=14 if small else 20,
                      pady=7 if small else 11, cursor="hand2",
                      highlightthickness=1, highlightbackground=ACCENT if primary else BORDER,
                      highlightcolor=ACCENT if primary else BORDER)
        return b

    def _entry(self, parent, var, *, width=40):
        e = tk.Entry(parent, textvariable=var, bg=PANEL, fg=TEXT, insertbackground=TEXT,
                     relief="flat", bd=0, highlightthickness=1,
                     highlightbackground=BORDER, highlightcolor=ACCENT,
                     font=(FONT, 10))
        e.configure(insertwidth=2)
        return e

    # ---------------- yerlesim ---------------- #
    def _layout(self) -> None:
        header = tk.Frame(self, bg=BG)
        header.pack(fill="x", padx=24, pady=(18, 6))
        tk.Label(header, text="KITSUGI", bg=BG, fg=ACCENT,
                 font=(FONT, 22, "bold")).pack(side="left")
        tk.Label(header, text="AUTOCUT", bg=BG, fg=TEXT,
                 font=(FONT, 22, "bold")).pack(side="left", padx=(6, 0))
        tk.Label(header, text="  otomatik kurgu + meme + telifsiz muzik + YouTube",
                 bg=BG, fg=MUTED, font=(FONT, 10)).pack(side="left", padx=(14, 0), pady=(8, 0))

        self.status_dot = tk.Label(header, text="●", bg=BG, fg=GREEN, font=(FONT, 12))
        self.status_dot.pack(side="right", padx=(0, 6))
        self.status_lbl = tk.Label(header, text="Hazir", bg=BG, fg=MUTED, font=(FONT, 9))
        self.status_lbl.pack(side="right")

        nb = ttk.Notebook(self)
        nb.pack(fill="both", expand=True, padx=24, pady=(6, 12))
        self.nb = nb

        self.tab_main = ttk.Frame(nb)
        self.tab_memes = ttk.Frame(nb)
        self.tab_music = ttk.Frame(nb)
        self.tab_yt = ttk.Frame(nb)
        self.tab_log = ttk.Frame(nb)
        nb.add(self.tab_main, text="   Gunun Videosu   ")
        nb.add(self.tab_memes, text="   Meme Kutuphanesi   ")
        nb.add(self.tab_music, text="   Muzik   ")
        nb.add(self.tab_yt, text="   YouTube   ")
        nb.add(self.tab_log, text="   Log   ")

        self._tab_main()
        self._tab_memes()
        self._tab_music()
        self._tab_youtube()
        self._tab_log()

        foot = tk.Frame(self, bg=BG)
        foot.pack(fill="x", padx=24, pady=(0, 12))
        tk.Label(foot, text="v1.0  •  yerelde calisir, veri yuklenmez  •  F5 = BASLAT",
                 bg=BG, fg="#495057", font=(FONT, 8)).pack(side="left")
        self.bind("<F5>", lambda _e: self.start())

    # ---------------- ana sekme ---------------- #
    def _tab_main(self) -> None:
        p = self.cfg.get("paths", {})
        v = self.cfg.get("video", {})
        a = self.cfg.get("audio", {})

        wrap = tk.Frame(self.tab_main, bg=BG)
        wrap.pack(fill="both", expand=True, padx=18, pady=16)

        # kaynak / cikis
        box = tk.Frame(wrap, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        box.pack(fill="x", pady=(0, 12))
        tk.Label(box, text="KAYNAK KLASORU (OBS replay / klipler)", bg=PANEL, fg=MUTED,
                 font=(FONT, 8, "bold")).pack(anchor="w", padx=14, pady=(12, 4))
        row = tk.Frame(box, bg=PANEL)
        row.pack(fill="x", padx=14, pady=(0, 12))
        self.var_source = tk.StringVar(value=p.get("source", ""))
        self._entry(row, self.var_source, width=52).pack(side="left", fill="x", expand=True, ipady=8)
        self._btn(row, "Gozat", self.browse_source, small=True).pack(side="left", padx=(8, 0))
        self._btn(row, "Ac", lambda: self.open_folder(self.var_source.get()), small=True).pack(side="left", padx=(6, 0))

        box2 = tk.Frame(wrap, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        box2.pack(fill="x", pady=(0, 12))
        tk.Label(box2, text="CIKTI KLASORU", bg=PANEL, fg=MUTED,
                 font=(FONT, 8, "bold")).pack(anchor="w", padx=14, pady=(12, 4))
        row2 = tk.Frame(box2, bg=PANEL)
        row2.pack(fill="x", padx=14, pady=(0, 12))
        self.var_output = tk.StringVar(value=p.get("output", ""))
        self._entry(row2, self.var_output, width=52).pack(side="left", fill="x", expand=True, ipady=8)
        self._btn(row2, "Gozat", self.browse_output, small=True).pack(side="left", padx=(8, 0))
        self._btn(row2, "Ac", lambda: self.open_folder(self.var_output.get()), small=True).pack(side="left", padx=(6, 0))

        # ayarlar
        grid = tk.Frame(wrap, bg=BG)
        grid.pack(fill="x", pady=(0, 12))
        for i in range(4):
            grid.columnconfigure(i, weight=1, uniform="col")

        def field(parent, title, col):
            f = tk.Frame(parent, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
            f.grid(row=0, column=col, sticky="nsew", padx=4, ipady=6)
            tk.Label(f, text=title, bg=PANEL, fg=MUTED,
                     font=(FONT, 8, "bold")).pack(anchor="w", padx=12, pady=(8, 2))
            return f

        f1 = field(grid, "FORMAT", 0)
        self.var_format = tk.StringVar(value="Shorts 9:16" if v.get("vertical", True) else "YouTube 16:9")
        ttk.Combobox(f1, textvariable=self.var_format, state="readonly",
                     values=["Shorts 9:16", "YouTube 16:9"], font=(FONT, 10)).pack(
            fill="x", padx=12, pady=(0, 10))

        f2 = field(grid, "SURE (sn)", 1)
        self.var_duration = tk.IntVar(value=int(v.get("target_duration", 45)))
        ttk.Combobox(f2, textvariable=self.var_duration, state="readonly",
                     values=[30, 45, 60, 90, 120], font=(FONT, 10)).pack(
            fill="x", padx=12, pady=(0, 10))

        f3 = field(grid, "EN FAZLA BOLUM", 2)
        self.var_maxclips = tk.IntVar(value=int(v.get("max_clips", 6)))
        ttk.Combobox(f3, textvariable=self.var_maxclips, state="readonly",
                     values=[3, 4, 5, 6, 8, 10], font=(FONT, 10)).pack(
            fill="x", padx=12, pady=(0, 10))

        f4 = field(grid, "EFEKT SIDDETI", 3)
        self.var_shake = tk.DoubleVar(value=float(v.get("shake_strength", 9)))
        ttk.Scale(f4, from_=0, to=20, variable=self.var_shake).pack(
            fill="x", padx=12, pady=(4, 10))

        # meme + muzik
        row3 = tk.Frame(wrap, bg=BG)
        row3.pack(fill="x", pady=(0, 12))

        mbox = tk.Frame(row3, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        mbox.pack(side="left", fill="x", expand=True, padx=(0, 6))
        tk.Label(mbox, text="MEME", bg=PANEL, fg=MUTED, font=(FONT, 8, "bold")).pack(
            anchor="w", padx=12, pady=(8, 0))
        self.var_memes = tk.BooleanVar(value=bool(v.get("memes_enabled", True)))
        tk.Checkbutton(mbox, text="Uygun yerlere uygun meme ekle", variable=self.var_memes,
                       bg=PANEL, fg=TEXT, activebackground=PANEL, activeforeground=TEXT,
                       selectcolor=PANEL2, font=(FONT, 10),
                       command=self._toggle_memes).pack(anchor="w", padx=12, pady=(0, 4))
        self.var_meme_freq = tk.IntVar(value=int(v.get("meme_frequency", 60)))
        tk.Label(mbox, text="Meme sikligi", bg=PANEL, fg=MUTED, font=(FONT, 8)).pack(
            anchor="w", padx=12)
        self.scale_meme = ttk.Scale(mbox, from_=0, to=100, variable=self.var_meme_freq)
        self.scale_meme.pack(fill="x", padx=12, pady=(0, 10))

        sbox = tk.Frame(row3, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        sbox.pack(side="left", fill="x", expand=True, padx=(6, 0))
        tk.Label(sbox, text="MUZIK", bg=PANEL, fg=MUTED, font=(FONT, 8, "bold")).pack(
            anchor="w", padx=12, pady=(8, 0))
        self.var_music = tk.BooleanVar(value=bool(a.get("enabled", True)))
        tk.Checkbutton(sbox, text="Telifsiz muzik ekle (beat'e gore)", variable=self.var_music,
                       bg=PANEL, fg=TEXT, activebackground=PANEL, activeforeground=TEXT,
                       selectcolor=PANEL2, font=(FONT, 10)).pack(anchor="w", padx=12, pady=(0, 4))
        self.var_track = tk.StringVar(value="auto  (ture gore otomatik sec)")
        self.combo_track = ttk.Combobox(sbox, textvariable=self.var_track, state="readonly",
                                        font=(FONT, 9))
        self.combo_track.pack(fill="x", padx=12, pady=(0, 10))
        self.combo_track.bind("<<ComboboxSelected>>", lambda _e: self._choose_track())

        # otomasyon
        auto = tk.Frame(wrap, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        auto.pack(fill="x", pady=(0, 12))
        tk.Label(auto, text="OTOMASYON", bg=PANEL, fg=MUTED, font=(FONT, 8, "bold")).pack(
            anchor="w", padx=14, pady=(10, 4))
        self.var_watch = tk.BooleanVar(value=False)
        tk.Checkbutton(auto, text="Klasoru izle: yeni klip gelince otomatik isle",
                       variable=self.var_watch, bg=PANEL, fg=TEXT,
                       activebackground=PANEL, activeforeground=TEXT,
                       selectcolor=PANEL2, font=(FONT, 10)).pack(anchor="w", padx=14)
        self.var_upload = tk.BooleanVar(value=bool(self.cfg.get("youtube", {}).get("auto_upload", False)))
        tk.Checkbutton(auto, text="Bittikten sonra YouTube'a yukle (ozel/gizli)",
                       variable=self.var_upload, bg=PANEL, fg=TEXT,
                       activebackground=PANEL, activeforeground=TEXT,
                       selectcolor=PANEL2, font=(FONT, 10)).pack(anchor="w", padx=14, pady=(2, 0))
        self.var_publish = tk.BooleanVar(value=bool(self.cfg.get("youtube", {}).get("auto_publish", False)))
        tk.Checkbutton(auto, text="Yukler yuklemez hemen yayinla (public)",
                       variable=self.var_publish, bg=PANEL, fg=YELLOW,
                       activebackground=PANEL, activeforeground=YELLOW,
                       selectcolor=PANEL2, font=(FONT, 9)).pack(anchor="w", padx=14, pady=(0, 10))

        # butonlar
        btns = tk.Frame(wrap, bg=BG)
        btns.pack(fill="x", pady=(4, 10))
        self.btn_start = self._btn(btns, "▶  BASLAT", self.start, primary=True)
        self.btn_start.pack(side="left", ipady=4)
        self._btn(btns, "TEST (10 sn)", self.test_render).pack(side="left", padx=(8, 0))
        self.btn_stop = self._btn(btns, "■  DUR", self.stop, fg=RED)
        self.btn_stop.pack(side="left", padx=(8, 0))
        self._btn(btns, "Klasoru Ac", lambda: self.open_folder(self.var_output.get())).pack(
            side="right")

        self.progress = ttk.Progressbar(wrap, mode="determinate", maximum=100)
        self.progress.pack(fill="x", pady=(0, 6))
        self.progress_lbl = tk.Label(wrap, text="", bg=BG, fg=MUTED, font=(FONT, 9))
        self.progress_lbl.pack(anchor="w")

        # kuyruk
        tk.Label(wrap, text="KUYRUK", bg=BG, fg=MUTED, font=(FONT, 8, "bold")).pack(
            anchor="w", pady=(10, 4))
        self.listbox = tk.Listbox(wrap, bg="#010409", fg=TEXT, relief="flat",
                                  highlightthickness=1, highlightbackground=BORDER,
                                  font=("Consolas", 9), height=7, activestyle="none")
        self.listbox.pack(fill="both", expand=True)

    def _toggle_memes(self) -> None:
        state = "normal" if self.var_memes.get() else "disabled"
        self.scale_meme.configure(state=state)

    # ---------------- meme sekmesi ---------------- #
    def _tab_memes(self) -> None:
        p = self.cfg.get("paths", {})
        wrap = tk.Frame(self.tab_memes, bg=BG)
        wrap.pack(fill="both", expand=True, padx=18, pady=16)

        top = tk.Frame(wrap, bg=BG)
        top.pack(fill="x")
        tk.Label(top, text="Meme Kutuphanesi", bg=BG, fg=TEXT,
                 font=(FONT, 14, "bold")).pack(side="left")
        tk.Label(top, text="  resim + yanina .json (tags)", bg=BG, fg=MUTED,
                 font=(FONT, 9)).pack(side="left", pady=(6, 0))
        self._btn(top, "Klasoru Ac", lambda: self.open_folder(
            str(self.path_cfg("memes", "data/memes"))), small=True).pack(side="right")
        self._btn(top, "Yeniden Tara", self.refresh_memes, small=True).pack(
            side="right", padx=(0, 6))

        body = tk.Frame(wrap, bg=BG)
        body.pack(fill="both", expand=True, pady=(12, 0))
        self.meme_list = tk.Listbox(body, bg="#010409", fg=TEXT, relief="flat",
                                    highlightthickness=1, highlightbackground=BORDER,
                                    font=("Consolas", 9), width=52, activestyle="none")
        self.meme_list.pack(side="left", fill="both", expand=True)
        sb = tk.Scrollbar(body, orient="vertical", command=self.meme_list.yview,
                          bg=PANEL, troughcolor=PANEL)
        sb.pack(side="left", fill="y")
        self.meme_list.configure(yscrollcommand=sb.set)

        help_box = tk.Frame(body, bg=PANEL, highlightthickness=1, highlightbackground=BORDER,
                            width=360)
        help_box.pack(side="left", fill="y", padx=(12, 0))
        help_box.pack_propagate(False)
        txt = (
            "NASIL EKLENIR\n\n"
            "1. data/memes/ icine resim at\n"
            "   ace_01.png\n\n"
            "2. Yanina ayni isimle JSON:\n"
            "   ace_01.json\n"
            '   {"tags":["ace"],\n'
            '    "text":"ACE",\n'
            '    "callout":"ACE!"}\n\n'
            "ETIKETLER\n"
            "ace, clutch, hype, whiff,\n"
            "fail, chill, scary, troll\n\n"
            "Resim yoksa program otomatik\n"
            "yazi meme (ACE!, 1v4, KOS!)\n"
            "uretir.\n\n"
            "Program klip turunu ses\n"
            "tepe analizinden tahmin eder:\n"
            "4+ tepe -> ace\n"
            "3 tepe  -> clutch\n"
            "sonda   -> whiff"
        )
        tk.Label(help_box, text=txt, bg=PANEL, fg=MUTED, font=("Consolas", 9),
                 justify="left", anchor="nw").pack(fill="both", expand=True, padx=14, pady=14)

    # ---------------- muzik sekmesi ---------------- #
    def _tab_music(self) -> None:
        p = self.cfg.get("paths", {})
        wrap = tk.Frame(self.tab_music, bg=BG)
        wrap.pack(fill="both", expand=True, padx=18, pady=16)
        top = tk.Frame(wrap, bg=BG)
        top.pack(fill="x")
        tk.Label(top, text="Telifsiz Muzik", bg=BG, fg=TEXT,
                 font=(FONT, 14, "bold")).pack(side="left")
        self._btn(top, "Klasoru Ac", lambda: self.open_folder(
            str(self.path_cfg("music", "data/music"))), small=True).pack(side="right")
        self._btn(top, "Yeniden Tara", self.refresh_music, small=True).pack(
            side="right", padx=(0, 6))

        self.music_list = tk.Listbox(wrap, bg="#010409", fg=TEXT, relief="flat",
                                     highlightthickness=1, highlightbackground=BORDER,
                                     font=("Consolas", 9), activestyle="none")
        self.music_list.pack(fill="both", expand=True, pady=(12, 8))
        tk.Label(wrap, text=(
            "Guvenli lisanslar: youtube-audio-library, pixabay, mixkit, cc0, original\n"
            "Bilinmeyen lisansli parcalar otomatik secilmez. "
            "Her parcaya .json ile lisans yaz."),
            bg=BG, fg=MUTED, font=(FONT, 9), justify="left").pack(anchor="w", pady=(8, 0))

    # ---------------- youtube ---------------- #
    def _tab_youtube(self) -> None:
        y = self.cfg.get("youtube", {})
        wrap = tk.Frame(self.tab_yt, bg=BG)
        wrap.pack(fill="both", expand=True, padx=18, pady=16)

        tk.Label(wrap, text="YouTube Baglantisi", bg=BG, fg=TEXT,
                 font=(FONT, 14, "bold")).pack(anchor="w")
        tk.Label(wrap, text="client_secret.json bu klasorde olmali. "
                            "Ilk baglantida tarayici acilir.",
                 bg=BG, fg=MUTED, font=(FONT, 9)).pack(anchor="w", pady=(2, 12))

        self.yt_status = tk.Label(wrap, text="Bagli degil", bg=PANEL, fg=MUTED,
                                  font=(FONT, 10), anchor="w")
        self.yt_status.pack(fill="x", ipady=10, padx=0)

        row = tk.Frame(wrap, bg=BG)
        row.pack(fill="x", pady=(12, 0))
        self._btn(row, "Baglan / Dogrula", self.youtube_auth).pack(side="left")
        self._btn(row, "Son Videoyu Yukle", self.upload_last).pack(side="left", padx=(8, 0))

        frm = tk.Frame(wrap, bg=PANEL, highlightthickness=1, highlightbackground=BORDER)
        frm.pack(fill="x", pady=(18, 0))
        tk.Label(frm, text="AYARLAR", bg=PANEL, fg=MUTED,
                 font=(FONT, 8, "bold")).pack(anchor="w", padx=14, pady=(12, 6))
        self.var_privacy = tk.StringVar(value=y.get("privacy", "private"))
        for text, val in [("Ozel (private)", "private"), ("Listelenmemis (unlisted)", "unlisted"),
                          ("Herkese acik (public)", "public")]:
            tk.Radiobutton(frm, text=text, value=val, variable=self.var_privacy,
                           bg=PANEL, fg=TEXT, activebackground=PANEL, activeforeground=TEXT,
                           selectcolor=PANEL2, font=(FONT, 10)).pack(anchor="w", padx=14)
        tk.Label(frm, text="Zamanlama (ISO 8601, bos = hemen)", bg=PANEL, fg=MUTED,
                 font=(FONT, 8)).pack(anchor="w", padx=14, pady=(8, 2))
        self.var_publish_at = tk.StringVar(value="")
        self._entry(frm, self.var_publish_at, width=40).pack(fill="x", padx=14, pady=(0, 14), ipady=7)

    # ---------------- log ---------------- #
    def _tab_log(self) -> None:
        wrap = tk.Frame(self.tab_log, bg=BG)
        wrap.pack(fill="both", expand=True, padx=18, pady=16)
        top = tk.Frame(wrap, bg=BG)
        top.pack(fill="x")
        tk.Label(top, text="Log", bg=BG, fg=TEXT, font=(FONT, 14, "bold")).pack(side="left")
        self._btn(top, "Temizle", lambda: self.log_text.delete("1.0", "end"),
                  small=True).pack(side="right")

        body = tk.Frame(wrap, bg=BG)
        body.pack(fill="both", expand=True, pady=(10, 0))
        self.log_text = tk.Text(body, bg="#010409", fg="#9aa4b2", relief="flat",
                                font=("Consolas", 9), wrap="word", padx=12, pady=10,
                                highlightthickness=1, highlightbackground=BORDER,
                                insertbackground=TEXT)
        self.log_text.pack(side="left", fill="both", expand=True)
        sb = tk.Scrollbar(body, orient="vertical", command=self.log_text.yview,
                          bg=PANEL, troughcolor=PANEL)
        sb.pack(side="left", fill="y")
        self.log_text.configure(yscrollcommand=sb.set, state="disabled")

    # ---------------- yardimcilar ---------------- #
    def path_cfg(self, key: str, default: str | Path) -> Path:
        val = self.cfg.get("paths", {}).get(key, default)
        p = Path(val)
        return p if p.is_absolute() else (HERE / p)

    def log(self, msg: str) -> None:
        self.q.put(("log", msg))

    def _status(self, text: str, color: str = GREEN) -> None:
        self.q.put(("status", (text, color)))

    def _progress(self, value: float, label: str = "") -> None:
        self.q.put(("progress", (value, label)))

    def _poll(self) -> None:
        try:
            while True:
                kind, payload = self.q.get_nowait()
                if kind == "log":
                    self.log_text.configure(state="normal")
                    self.log_text.insert("end", payload + "\n")
                    self.log_text.see("end")
                    self.log_text.configure(state="disabled")
                elif kind == "status":
                    text, color = payload
                    self.status_lbl.configure(text=text, fg=color)
                    self.status_dot.configure(fg=color)
                elif kind == "progress":
                    value, label = payload
                    self.progress["value"] = value
                    if label:
                        self.progress_lbl.configure(text=label)
                elif kind == "queue":
                    self.listbox.delete(0, "end")
                    for item in payload:
                        self.listbox.insert("end", item)
                elif kind == "done":
                    self._on_done(payload)
                elif kind == "error":
                    self._on_error(payload)
        except queue.Empty:
            pass
        self.after(120, self._poll)

    def _on_done(self, result: dict) -> None:
        out = result.get("out")
        if out:
            self.last_output = Path(out)
        self._status("Tamamlandi", GREEN)
        self._progress(100, "Bitti")
        self.log(f"[TAMAM] {result.get('duration', 0):.0f} sn  |  "
                 f"{result.get('memes', 0)} meme  |  {result.get('size_mb', 0)} MB")
        if self.var_upload.get():
            self._upload_after_render(Path(out))
        else:
            self.btn_start.configure(state="normal")

    def _on_error(self, msg: str) -> None:
        self._status("Hata", RED)
        self._progress(0, "")
        self.log(f"[HATA] {msg}")
        self.btn_start.configure(state="normal")

    def browse_source(self) -> None:
        d = filedialog.askdirectory(title="Klip klasorunu sec")
        if d:
            self.var_source.set(d)
            self.cfg.setdefault("paths", {})["source"] = d
            save_config(self.cfg, HERE / "config.json")

    def browse_output(self) -> None:
        d = filedialog.askdirectory(title="Cikti klasorunu sec")
        if d:
            self.var_output.set(d)
            self.cfg.setdefault("paths", {})["output"] = d
            save_config(self.cfg, HERE / "config.json")

    def open_folder(self, path: str) -> None:
        if not path:
            messagebox.showinfo("Bilgi", "Once klasor sec.")
            return
        p = Path(path)
        p.mkdir(parents=True, exist_ok=True)
        try:
            if sys.platform.startswith("win"):
                os.startfile(str(p))
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(p)])
            else:
                subprocess.Popen(["xdg-open", str(p)])
        except Exception as exc:
            self.log(f"[!] Klasor acilamadi: {exc}")

    # ---------------- kutuphane ---------------- #
    def refresh_memes(self) -> None:
        folder = self.path_cfg("memes", "data/memes")
        self.memedefs = meme_lib.load_library(folder)
        self.meme_list.delete(0, "end")
        if not self.memedefs:
            self.meme_list.insert("end", "(bos)  data/memes/ icine resim at")
        for m in self.memedefs:
            kind = "IMG" if m.path else "TXT"
            self.meme_list.insert("end", f"[{kind}] {','.join(m.tags):<12} {m.text[:36]}")
        self.log(f"Meme kutuphanesi: {len(self.memedefs)} oge")

    def _track_choices(self) -> list[str]:
        names = ["auto  (otomatik sec)"]
        for t in self.tracks:
            mark = "" if t.safe else "!! "
            names.append(f"{mark}{t.title[:52]}")
        return names

    def _choose_track(self) -> None:
        if not hasattr(self, "tracks"):
            return
        idx = self.combo_track.current()
        audio = self.cfg.setdefault("audio", {})
        if idx <= 0:
            audio["music"] = ""
            audio["music_track"] = "auto"
            self.log("[M] Muzik: her videoya gore otomatik secilecek")
        else:
            t = self.tracks[idx - 1] if idx - 1 < len(self.tracks) else None
            if t is None:
                return
            if not t.safe:
                self.log(f"[!] {t.title}: lisans guvenli degil ({t.license}), secilmedi")
                self.var_track.set(self._track_choices()[0])
                audio["music"] = ""
                audio["music_track"] = "auto"
                return
            audio["music"] = str(t.path)
            audio["music_track"] = t.title
            self.log(f"[M] Muzik sabitlendi: {t.title}")
        self._save_settings()

    def _apply_music(self, cfg: dict) -> dict:
        """Muzik kapaliysa kapatir; aciksa parcayi motor seciyor.

        Kullanici "Bu video icin muzik" kutusundan bir parca isaretlemisse o
        kullanilir, "otomatik sec" duruyorsa engine.pick_music_for() ture gore
        (epic/hype/scary) guvenli bir parca bulur.
        """
        audio = cfg.setdefault("audio", {})
        if not self.var_music.get():
            audio["enabled"] = False
            audio["music"] = ""
            return cfg
        audio["enabled"] = True
        return cfg

    def refresh_music(self) -> None:
        folder = self.path_cfg("music", "data/music")
        self.tracks = music_lib.load_tracks(folder)
        self.music_list.delete(0, "end")
        safe = [t for t in self.tracks if t.safe]
        for t in self.tracks:
            mark = "OK " if t.safe else "!! "
            self.music_list.insert("end", f"[{mark}] {t.title[:44]:<46} {t.license}")
        if not self.tracks:
            self.music_list.insert("end", "(bos)  data/music/ icine telifsiz mp3 at")
        names = self._track_choices()
        self.combo_track["values"] = names
        # kaydedilmis parcayi geri yukle; yoksa otomatik sec
        want = str(self.cfg.get("audio", {}).get("music_track") or "")
        label = names[0]
        if want and want != "auto":
            for i, t in enumerate(self.tracks):
                if t.title == want and t.safe:
                    label = names[i + 1]
                    break
        self.var_track.set(label)
        self.log(f"Muzik: {len(safe)} guvenli / {len(self.tracks)} toplam")

    # ---------------- is akisi ---------------- #
    def start(self) -> None:
        if self.worker and self.worker.is_alive():
            return
        src = self.var_source.get().strip()
        if not src or not Path(src).exists():
            messagebox.showwarning("Eksik", "Kaynak klasorunu sec.")
            return
        out = self.var_output.get().strip() or str(Path(src).parent / "Cikti")
        ensure_dirs(Path(out))
        self._save_settings()

        self.stop_flag = False
        self.btn_start.configure(state="disabled")
        self._status("Calisiyor", YELLOW)
        self._progress(2, "Baslaniyor...")

        self.worker = threading.Thread(target=self._worker, args=(src, out), daemon=True)
        self.worker.start()

    def _worker(self, src: str, out: str) -> None:
        try:
            self._progress(5, "Klasor taraniyor...")
            meme_defs = self.memedefs or meme_lib.load_library(self.path_cfg("memes", "data/memes"))
            freq = self.var_meme_freq.get() / 100.0 if self.var_memes.get() else 0.0
            rng = random.Random()
            memes: list[engine.MemeCall] = []
            for m in meme_defs:
                if rng.random() <= freq:
                    memes.append(engine.MemeCall(m.path, m.text, m.tags[0], 0,
                                                 1.25, m.callout))

            if self.var_watch.get() and not self.watching:
                self._start_watcher()

            name = time.strftime("autocut_%Y%m%d_%H%M%S.mp4")
            target = Path(out) / name
            self._progress(10, "Kurgu planlaniyor...")

            cfg = json.loads(json.dumps(self.cfg))
            if self.var_music.get():
                cfg = self._apply_music(cfg)
            else:
                cfg["audio"]["music"] = ""

            result = engine.build_video(
                Path(src), target, cfg,
                target_duration=float(self.var_duration.get()),
                max_clips=int(self.var_maxclips.get()),
                memes=memes,
                log=self.log,
            )
            result["out"] = str(target)
            self._progress(95, "Yukleniyor..." if self.var_upload.get() else "Bitti")
            self.q.put(("done", result))
        except Exception as exc:
            self.q.put(("error", f"{exc}\n{traceback.format_exc()[-800:]}"))

    def test_render(self) -> None:
        if self.worker and self.worker.is_alive():
            return
        src = self.var_source.get().strip()
        if not src or not Path(src).exists():
            messagebox.showwarning("Eksik", "Kaynak klasorunu sec.")
            return
        out = Path(self.var_output.get().strip() or (Path(src).parent / "Cikti"))
        ensure_dirs(out)
        self._save_settings()
        target = out / f"TEST_{time.strftime('%H%M%S')}.mp4"
        self.btn_start.configure(state="disabled")
        self._status("Test", YELLOW)
        self._progress(5, "Test render (10 sn)...")

        def run():
            try:
                cfg = json.loads(json.dumps(self.cfg))
                cfg["video"]["target_duration"] = 10
                cfg["video"]["max_clips"] = 2
                cfg = self._apply_music(cfg) if self.var_music.get() else cfg
                memes = [engine.MemeCall(m.path, m.text, m.tags[0], 0, 1.25, m.callout)
                         for m in (self.memedefs or [])][:8]
                r = engine.build_video(Path(src), target, cfg, target_duration=10,
                                       max_clips=2, memes=memes, log=self.log)
                r["out"] = str(target)
                self.q.put(("done", r))
            except Exception as exc:
                self.q.put(("error", f"{exc}\n{traceback.format_exc()[-800:]}"))

        self.worker = threading.Thread(target=run, daemon=True)
        self.worker.start()

    def stop(self) -> None:
        self.stop_flag = True
        self.watching = False
        self._status("Durdu", RED)
        self.log("[!] Durduruldu (islem bitince durur)")
        self.btn_start.configure(state="normal")

    def _start_watcher(self) -> None:
        self.watching = True
        self.seen = {str(p) for p in Path(self.var_source.get()).glob("*")
                     if p.suffix.lower() in VIDEO_EXT}
        self.watch_thread = threading.Thread(target=self._watch_loop, daemon=True)
        self.watch_thread.start()

    def _watch_loop(self) -> None:
        self.log("[I] Klasor izleme basladi (15 sn'de bir kontrol)")
        out_dir = Path(self.var_output.get() or (Path(self.var_source.get()).parent / "Cikti"))
        ensure_dirs(out_dir)
        while self.watching and not self.stop_flag:
            time.sleep(15)
            try:
                src = Path(self.var_source.get())
                for p in src.glob("*"):
                    if p.suffix.lower() not in VIDEO_EXT or str(p) in self.seen:
                        continue
                    if time.time() - p.stat().st_mtime < 8:
                        continue
                    self.seen.add(str(p))
                    self.log(f"[I] Yeni klip: {p.name}")
                    meme_defs = self.memedefs or meme_lib.load_library(self.path_cfg("memes", "data/memes"))
                    freq = self.var_meme_freq.get() / 100.0 if self.var_memes.get() else 0.0
                    memes = [engine.MemeCall(m.path, m.text, m.tags[0], 0, 1.25, m.callout)
                             for m in meme_defs if random.random() <= freq]
                    target = out_dir / f"{p.stem}_short.mp4"
                    cfg = json.loads(json.dumps(self.cfg))
                    cfg = self._apply_music(cfg) if self.var_music.get() else cfg
                    r = engine.build_single(p, target, cfg, memes=memes, log=self.log)
                    r["out"] = str(target)
                    self.q.put(("done", r))
                    if self.var_upload.get():
                        self._upload_after_render(target)
            except Exception as exc:
                self.log(f"[!] Izleme hatasi: {exc}")

    def _upload_after_render(self, file: Path) -> None:
        def run():
            try:
                self.log("[YT] Yukleme basladi...")
                meta = meta_lib.generate(Path(file).stem, self.cfg, log=self.log)
                uploader.upload(
                    file,
                    title=meta.get("title", file.stem),
                    description=meta.get("description", ""),
                    tags=meta.get("tags", []),
                    privacy="public" if (self.var_publish.get() and not self.var_publish_at.get())
                    else "private",
                    publish_at=self.var_publish_at.get() or None,
                    secret=HERE / "client_secret.json",
                    token=HERE / "token.json",
                    log=self.log,
                )
                self._status("Yuklendi", GREEN)
            except Exception as exc:
                self.log(f"[YT HATA] {exc}")
            finally:
                self.btn_start.configure(state="normal")

        threading.Thread(target=run, daemon=True).start()

    def youtube_auth(self) -> None:
        def run():
            try:
                uploader.get_credentials(HERE / "client_secret.json", HERE / "token.json")
                name = uploader.channel_name()
                self.yt_status.configure(text=f"Bagli  •  {name}" if name else "Bagli",
                                         fg=GREEN)
                self.log(f"[YT] Baglanti tamam {name}")
            except Exception as exc:
                self.yt_status.configure(text=f"Hata: {exc}", fg=RED)
                self.log(f"[YT HATA] {exc}")

        threading.Thread(target=run, daemon=True).start()

    def upload_last(self) -> None:
        if not self.last_output or not self.last_output.exists():
            messagebox.showwarning("Yok", "Once bir video uret.")
            return
        self._upload_after_render(self.last_output)

    def _save_settings(self) -> None:
        p = self.cfg.setdefault("paths", {})
        p["source"] = self.var_source.get()
        p["output"] = self.var_output.get()
        v = self.cfg.setdefault("video", {})
        v["vertical"] = self.var_format.get().startswith("Shorts")
        v["target_duration"] = int(self.var_duration.get())
        v["max_clips"] = int(self.var_maxclips.get())
        v["shake_strength"] = float(self.var_shake.get())
        v["memes_enabled"] = bool(self.var_memes.get())
        v["meme_frequency"] = int(self.var_meme_freq.get())
        a = self.cfg.setdefault("audio", {})
        a["enabled"] = bool(self.var_music.get())
        if not a["enabled"]:
            a["music"] = ""
        y = self.cfg.setdefault("youtube", {})
        y["auto_upload"] = bool(self.var_upload.get())
        y["auto_publish"] = bool(self.var_publish.get())
        y["privacy"] = self.var_privacy.get()
        save_config(self.cfg, HERE / "config.json")

    def _on_close(self) -> None:
        self.watching = False
        self.stop_flag = True
        try:
            self._save_settings()
        except Exception:
            pass
        self.destroy()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--headless", action="store_true")
    ap.add_argument("--source")
    ap.add_argument("--file")
    ap.add_argument("--out")
    ap.add_argument("--duration", type=float, default=None)
    ap.add_argument("--config", default=None)
    args = ap.parse_args()

    if args.headless:
        if not args.out:
            ap.error("--headless icin --out gerekli")
        return run_headless(args)

    if not HAS_TK:
        sys.exit("[HATA] Tkinter yok. Python'u kurarken 'tcl/tk' secenegini isaretle.")
    app = App()
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
