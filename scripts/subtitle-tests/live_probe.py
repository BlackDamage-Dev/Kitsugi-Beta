#!/usr/bin/env python3
"""Small, read-only live subtitle contract probe. No credentials or videos are sent.

Run: python3 scripts/subtitle-tests/live_probe.py
Optionally pass extra Stremio manifests, e.g. --manifest https://host/manifest.json
The known examples are public movie/episode IDs; providers may be temporarily down.
"""
import argparse
import ctypes
import ctypes.util
import io
import ipaddress
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile

UA = "Kitsugi-Subtitle-Contract-Test/1.0"
LIMIT = 10 * 1024 * 1024


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json, text/plain, */*"})
    with urllib.request.urlopen(req, timeout=15) as response:
        body = response.read(LIMIT + 1)
        if len(body) > LIMIT:
            raise ValueError("response exceeds 10 MiB")
        return response.status, response.headers.get("Content-Type", ""), body


def describe_error(exc):
    if isinstance(exc, urllib.error.HTTPError):
        return f"HTTP {exc.code}"
    if isinstance(exc, urllib.error.URLError):
        return f"URLError: {str(exc.reason)[:120]}"
    return str(exc) if isinstance(exc, ValueError) else type(exc).__name__


def json_get(url):
    _, _, body = fetch(url)
    return json.loads(body)


def endpoint(manifest, media_type, media_id):
    parts = urllib.parse.urlsplit(manifest)
    path = parts.path.rstrip("/")
    if path.endswith("/manifest.json"):
        path = path[:-len("/manifest.json")]
    media_id = urllib.parse.quote(media_id, safe=":")
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, f"{path}/subtitles/{media_type}/{media_id}.json", parts.query, ""))


def detect(body):
    prefix = body[:2048].lstrip(b"\xef\xbb\xbf \r\n\t").lower()
    if prefix.startswith(b"[script info]"):
        return "ass"
    if prefix.startswith(b"webvtt"):
        return "vtt"
    if b"-->" in prefix:
        return "srt/vtt"
    if prefix.startswith((b"<!doctype html", b"<html", b"{")):
        return "error-page/json"
    if prefix.startswith(b"pk\x03\x04"):
        return "zip"
    if prefix.startswith(b"rar!"):
        return "rar"
    return "unknown"


def run_provider(name, manifest):
    report = {"provider": name}
    try:
        m = json_get(manifest)
        report["manifest"] = "ok" if isinstance(m, dict) and "resources" in m else "not a Stremio manifest"
    except Exception as exc:
        report["manifest"] = f"error: {describe_error(exc)}"
        return report
    for kind, ident in (("movie", "tt0111161"), ("series", "tt0903747:1:1")):
        try:
            data = json_get(endpoint(manifest, kind, ident))
            subs = data.get("subtitles", [])
            tr = [s for s in subs if str(s.get("lang", "")).lower() in ("tr", "tur", "turkish", "türkçe")]
            result = {"total": len(subs), "turkish": len(tr)}
            if tr and tr[0].get("url"):
                try:
                    host = urllib.parse.urlsplit(tr[0]["url"]).hostname or ""
                    try:
                        loopback = ipaddress.ip_address(host).is_loopback
                    except ValueError:
                        loopback = host.lower() == "localhost" or host.lower().endswith(".localhost")
                    if loopback:
                        raise ValueError("provider returned Stremio-only loopback proxy URL")
                    status, ctype, body = fetch(tr[0]["url"])
                    result.update(download_status=status, download_type=ctype, detected=detect(body))
                    try:
                        body.decode("utf-8", "strict")
                        result["valid_utf8"] = True
                    except UnicodeDecodeError:
                        result["valid_utf8"] = False
                        try:
                            decoded = body.decode("cp1254", "strict")
                            result["turkish_legacy_characters"] = sum(decoded.count(c) for c in "çğıöşüÇĞİÖŞÜ")
                        except UnicodeDecodeError:
                            result["turkish_legacy_characters"] = None
                except Exception as exc:
                    result["download_error"] = describe_error(exc)
            report[kind] = result
        except Exception as exc:
            report[kind] = f"error: {describe_error(exc)}"
    return report


def inspect_rar(raw):
    """Read names and bounded subtitle data with the runner's native libarchive."""
    lib = ctypes.CDLL(ctypes.util.find_library("archive") or "libarchive.so.13")
    ptr = ctypes.c_void_p
    for name in ("archive_read_new", "archive_entry_pathname"):
        getattr(lib, name).restype = ptr
    lib.archive_entry_pathname.restype = ctypes.c_char_p
    lib.archive_entry_pathname.argtypes = [ptr]
    for name in ("archive_read_support_filter_all", "archive_read_support_format_rar", "archive_read_support_format_rar5", "archive_read_free"):
        getattr(lib, name).argtypes = [ptr]
    lib.archive_read_open_memory.argtypes = [ptr, ptr, ctypes.c_size_t]
    lib.archive_read_next_header.argtypes = [ptr, ctypes.POINTER(ptr)]
    lib.archive_read_data.argtypes = [ptr, ptr, ctypes.c_size_t]
    lib.archive_read_data.restype = ctypes.c_ssize_t
    archive = lib.archive_read_new()
    data = ctypes.create_string_buffer(raw)
    names, sample = [], None
    try:
        for name in ("archive_read_support_filter_all", "archive_read_support_format_rar", "archive_read_support_format_rar5"):
            if getattr(lib, name)(archive) < 0:
                raise ValueError("libarchive setup failed")
        if lib.archive_read_open_memory(archive, data, len(raw)) != 0:
            raise ValueError("RAR open failed")
        entry = ptr()
        for _ in range(200):
            code = lib.archive_read_next_header(archive, ctypes.byref(entry))
            if code == 1:
                break
            if code != 0:
                raise ValueError("RAR header failed")
            name = (lib.archive_entry_pathname(entry) or b"").decode("utf-8", "replace")
            if name.lower().endswith((".ass", ".ssa", ".srt", ".vtt")):
                names.append(name)
                buf, chunks, total = ctypes.create_string_buffer(8192), [], 0
                while True:
                    count = lib.archive_read_data(archive, buf, len(buf))
                    if count < 0:
                        raise ValueError("RAR extraction failed")
                    if not count:
                        break
                    total += count
                    if total > 5 * 1024 * 1024:
                        raise ValueError("RAR entry too large")
                    chunks.append(buf.raw[:count])
                if sample is None:
                    sample = detect(b"".join(chunks))
        else:
            raise ValueError("too many entries")
    finally:
        lib.archive_read_free(archive)
    return {"subtitle_files": len(names), "sample_filenames": names[:4], "sample_detected": sample}


def run_anisub():
    report = {"provider": "AniSub", "test": "Mushoku Tensei / AniList 108465 / episode 1"}
    try:
        data = json_get("https://anisub.co/api/subtitles?q=Mushoku%20Tensei")
        items = data["data"]["data"]
        matches = [x for x in items if x.get("subtitle_anilist_id") == 108465]
        report["matching_releases"] = len(matches)
        pack = next((x for x in matches if x.get("content_type") == "Paket"), None)
        if pack:
            report["sample_id"] = pack["subtitle_id"]
            report["episode_description"] = pack.get("episode_description")
            info = json_get(f'https://anisub.co/api/subtitles/{pack["subtitle_id"]}/download')["data"]
            report["archive_type"] = info.get("filename", "").rsplit(".", 1)[-1].lower()
            if report["archive_type"] == "zip":
                _, _, raw = fetch(info["download_url"])
                with zipfile.ZipFile(io.BytesIO(raw)) as z:
                    names = [x.filename for x in z.infolist() if x.filename.lower().endswith((".ass", ".ssa", ".srt", ".vtt"))]
                    report["subtitle_files"] = len(names)
                    report["sample_filenames"] = names[:4]
    except Exception as exc:
        report["error"] = type(exc).__name__
    try:
        info = json_get("https://anisub.co/api/subtitles/3607/download")["data"]
        _, _, raw = fetch(info["download_url"])
        report["rar_3607"] = inspect_rar(raw)
    except Exception as exc:
        report["rar_3607"] = {"error": type(exc).__name__, "detail": str(exc)[:200]}
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", action="append", default=[], help="additional Stremio manifest URL")
    args = parser.parse_args()
    cases = [("OpenSubtitles v3", "https://opensubtitles-v3.strem.io/manifest.json"),
             ("YTS", "https://yts-subtitles.strem.io/manifest.json"),
             ("YIFY alternative, Turkish", "https://2ecbbd610840-yifysubtitles.baby-beamup.club/turkish/manifest.json"),
             ("SubSense alternative, Turkish", "https://subsense.nepiraw.com/%7B%22languages%22%3A%5B%22tur%22%5D%2C%22maxSubtitles%22%3A25%7D/manifest.json"),
             ("AltyaziDB", "https://altyazidb.online/manifest.json")]
    cases += [(f"extra-{i + 1}", url) for i, url in enumerate(args.manifest)]
    reports = [run_provider(name, url) for name, url in cases] + [run_anisub()]
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0  # Provider downtime is reported, not confused with a build failure.


if __name__ == "__main__":
    sys.exit(main())
