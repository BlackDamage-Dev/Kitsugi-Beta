#!/usr/bin/env python3
"""
Material ikonlarını Kitsugi Glyphs setine taşıyan betik.

Neden: uygulamada 1700+ Material ikon referansı vardı; her buton, her ayar
satırı, her açılır-kapanır başlık Google'ın stok ikonlarını kullanıyordu ve
bunların çoğu birbirinin aynı görünüyordu. Bu betik her işlevi Kitsugi'ye özel,
kendi silueti olan bir glyph'e bağlar.

Kullanım:
  python3 tools/kitsugi_glyphs/migrate_material_icons.py --dry-run
  python3 tools/kitsugi_glyphs/migrate_material_icons.py
"""

import argparse
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "app/src/main/java/com/kitsugi")
IMPORT_LINE = "import com.kitsugi.animelist.ui.theme.icons.KitsugiGlyphs"

# Material ikon adı -> Kitsugi glyph adı
MAP = {
    # gezinme / yön
    "Close": "Close",
    "Cancel": "Close",
    "ArrowBack": "Back",
    "ArrowBackIos": "Back",
    "ArrowForward": "Forward",
    "ArrowForwardIos": "Forward",
    "ArrowDropDown": "ChevronDown",
    "KeyboardArrowDown": "ChevronDown",
    "ExpandMore": "ChevronDown",
    "KeyboardArrowUp": "ChevronUp",
    "ExpandLess": "ChevronUp",
    "KeyboardArrowRight": "ChevronRight",
    "ChevronRight": "ChevronRight",
    "Menu": "Menu",
    "MoreVert": "More",
    "ChevronLeft": "ChevronLeft",
    "FastForward": "FastForward",
    "FastRewind": "FastRewind",
    "CloudDownload": "CloudDownload",
    "Bolt": "Bolt",
    "HourglassEmpty": "Hourglass",
    "HourglassFull": "Hourglass",
    "SwapHoriz": "Swap",
    "RemoveCircle": "RemoveCircle",
    "RemoveCircleOutline": "RemoveCircle",
    "Reply": "Reply",
    "ThumbUp": "ThumbUp",
    "AutoAwesome": "Sparkle",
    "AutoAwesomeOutlined": "Sparkle",
    "DeleteForever": "Delete",
    "PriorityHigh": "Warning",
    "ViewList": "Rows",
    "ContentPaste": "Copy",
    "ArrowCircleUp": "UpCircle",
    "NorthEast": "External",
    "Explore": "Explore",
    "TravelExplore": "Explore",
    "Compass": "Explore",
    "ViewStream": "Rows",
    "ViewAgenda": "Rows",
    "ViewHeadline": "Rows",
    # içerik türü
    "Movie": "Film",
    "MovieCreation": "Film",
    "LiveTv": "Tv",
    "Tv": "Tv",
    "MenuBook": "Book",
    "AutoStories": "Book",
    "Book": "Book",
    "ImportContacts": "Book",
    "LocalLibrary": "Book",
    # puanlama / beğeni
    "Star": "Star",
    "StarBorder": "Star",
    "StarOutline": "Star",
    "StarRate": "Star",
    "StarHalf": "Star",
    "Favorite": "Heart",
    "FavoriteBorder": "HeartOutline",
    "Bookmark": "Bookmark",
    "Bookmarks": "Bookmark",
    "BookmarkBorder": "Bookmark",
    "BookmarkAdd": "Bookmark",
    "BookmarkAdded": "Bookmark",
    "Label": "Tag",
    "LabelOutline": "Tag",
    "LocalOffer": "Tag",
    # onay / durum
    "Check": "Check",
    "Done": "Check",
    "CheckCircle": "CheckCircle",
    "CheckCircleOutline": "CheckCircle",
    "Info": "Info",
    "InfoOutline": "Info",
    "ErrorOutline": "Error",
    "Error": "Error",
    "Warning": "Warning",
    "WarningAmber": "Warning",
    "Notifications": "Bell",
    "NotificationsNone": "Bell",
    "NotificationsActive": "Bell",
    "NotificationsOff": "BellOff",
    "BugReport": "Bug",
    "Feedback": "Feedback",
    "RateReview": "Feedback",
    # eylemler
    "Share": "Share",
    "Send": "Send",
    "ChatBubble": "Comment",
    "ChatBubbleOutline": "Comment",
    "Comment": "Comment",
    "QuestionAnswer": "Comment",
    "Forum": "Comment",
    "ContentCopy": "Copy",
    "CopyAll": "Copy",
    "Link": "Link",
    "LinkOff": "LinkOff",
    "OpenInNew": "External",
    "Translate": "Translate",
    "GTranslate": "Translate",
    "Download": "Download",
    "DownloadForOffline": "Download",
    "FileDownload": "Download",
    "Delete": "Delete",
    "DeleteOutline": "Delete",
    "DeleteSweep": "Delete",
    "Edit": "Edit",
    "EditNote": "Edit",
    "Create": "Edit",
    "FilterList": "Filter",
    "FilterAlt": "Filter",
    "Sort": "Sort",
    "GridView": "Grid",
    "ListAlt": "Rows",
    "FormatListBulleted": "Rows",
    "List": "Rows",
    "Repeat": "Repeat",
    "RepeatOne": "RepeatOne",
    "Key": "Key",
    "VpnKey": "Key",
    "Code": "Code",
    "Refresh": "Refresh",
    "RestartAlt": "Refresh",
    "Autorenew": "Refresh",
    "Sync": "Sync",
    "CloudSync": "CloudSync",
    "CleaningServices": "Cleaning",
    "Tune": "Tune",
    "Storage": "Storage",
    "Hub": "Integrations",
    "AccountCircle": "Account",
    "AccountBox": "Account",
    "Person": "Person",
    "PersonOutline": "Person",
    "People": "People",
    "Group": "People",
    "History": "History",
    "Settings": "Settings",
    "Search": "Search",
    "ManageSearch": "Search",
    "SearchOff": "SearchOff",
    "CalendarToday": "Calendar",
    "Event": "Calendar",
    "EventAvailable": "Calendar",
    "Today": "Calendar",
    "DateRange": "Calendar",
    "Schedule": "Clock",
    "AccessTime": "Clock",
    "Timer": "Clock",
    "AvTimer": "Clock",
    "BarChart": "Chart",
    "DonutLarge": "Chart",
    "Analytics": "Chart",
    "Insights": "Chart",
    "Palette": "Palette",
    "Subtitles": "Subtitles",
    "ClosedCaption": "Subtitles",
    "ClosedCaptionOff": "Subtitles",
    "SubtitlesOff": "SubtitlesOff",
    "Visibility": "Eye",
    "VisibilityOff": "EyeOff",
    "Lock": "Shield",
    "LockOpen": "Unlock",
    "Security": "Shield",
    "GppGood": "Shield",
    "Language": "Globe",
    "Public": "Globe",
    "Image": "Image",
    "Photo": "Image",
    "PhotoLibrary": "Image",
    "Collections": "Image",
    "InsertPhoto": "Image",
    "FolderOpen": "Folder",
    "Folder": "Folder",
    "FolderSpecial": "Folder",
    "Extension": "Plugin",
    "PlayArrow": "Play",
    "Pause": "Pause",
    "PlayCircle": "PlayCircle",
    "PlayCircleOutline": "PlayCircle",
    "PlayCircleFilled": "PlayCircle",
    "SkipNext": "SkipNext",
    "VolumeUp": "Volume",
    "VolumeDown": "VolumeLow",
    "VolumeMute": "VolumeOff",
    "VolumeOff": "VolumeOff",
    "Fullscreen": "Fullscreen",
    "FullscreenExit": "FullscreenExit",
    "Cast": "Cast",
    "CastConnected": "Cast",
    "Speed": "Speed",
    "PictureInPicture": "Pip",
    "PictureInPictureAlt": "Pip",
    "PlaylistAdd": "Queue",
    "QueueMusic": "Queue",
    "Add": "Plus",
}

from extra_glyphs import PATHS, ALIASES
MAP.update({name: name for name in PATHS})
MAP.update(ALIASES)

ICON_RE = re.compile(
    r"(?:androidx\.compose\.material\.icons\.)?Icons\.(?:AutoMirrored\.)?"
    r"(?:Default|Filled|Rounded|Outlined|TwoTone)\.([A-Za-z0-9_]+)"
)

SKIP_DIRS = (
    "app/src/main/java/com/lagradost",
    "app/src/main/java/com/squareup",
    "app/src/main/java/eu/kanade",
    "app/src/main/java/uy/kohesive",
    "app/src/main/java/okhttp3",
    "app/src/main/java/tachiyomi",
)


def should_skip(path: str) -> bool:
    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
    if rel.endswith(("KitsugiGlyphs.kt", "KitsugiPlatformLogo.kt", "KitsugiSourceBadge.kt")):
        return True
    return any(rel.startswith(d) for d in SKIP_DIRS)


def add_import(text: str) -> str:
    if IMPORT_LINE in text:
        return text
    lines = text.split("\n")
    last_import = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import = i
    if last_import < 0:
        return text
    # paket bildiriminden sonraki import bloğunun sonuna ekle
    lines.insert(last_import + 1, IMPORT_LINE)
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    total = 0
    touched = 0
    per_glyph = {}
    unmapped = {}
    files = []

    for dirpath, _dirnames, filenames in os.walk(SRC):
        for name in filenames:
            if name.endswith(".kt"):
                files.append(os.path.join(dirpath, name))

    for path in sorted(files):
        if should_skip(path):
            continue
        with open(path, encoding="utf-8") as f:
            text = f.read()
        if "Icons." not in text:
            continue

        count = [0]

        def repl(m):
            icon = m.group(1)
            glyph = MAP.get(icon)
            if glyph is None:
                unmapped[icon] = unmapped.get(icon, 0) + 1
                return m.group(0)
            count[0] += 1
            per_glyph[glyph] = per_glyph.get(glyph, 0) + 1
            return f"KitsugiGlyphs.{glyph}"

        new_text = ICON_RE.sub(repl, text)
        if count[0] == 0:
            continue
        new_text = add_import(new_text)
        total += count[0]
        touched += 1
        if not args.dry_run:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new_text)

    print(f"{'[dry-run] ' if args.dry_run else ''}dosya: {touched}, değişen ikon: {total}")
    print("glyph dağılımı:")
    for glyph, c in sorted(per_glyph.items(), key=lambda kv: -kv[1]):
        print(f"  {glyph:14s} {c}")
    if unmapped:
        print("eşlenmeyen (Material'da kaldı):")
        for icon, c in sorted(unmapped.items(), key=lambda kv: -kv[1])[:40]:
            print(f"  {icon:24s} {c}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
