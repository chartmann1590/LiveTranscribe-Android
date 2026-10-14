#!/usr/bin/env python3
"""Check every Vosk model URL in VoskModelCatalog.kt is alive.

Reads the catalog source, HEADs each model zip (following redirects), and
fails if any answers non-200, has no body, or is not a zip (checked from the
first bytes of a ranged GET). Also warns when the served size is far from the
size the catalog shows users.

Runs in CI on pull requests that touch the catalog and weekly on a schedule,
so a model that alphacephei.com stops publishing is caught before a user taps
a "Get" button that cannot work (a Play review in Oct 2026 came from exactly
that: vosk-model-small-id-0.22.zip answered 404).
"""
import re
import sys
import urllib.request
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/java/com/charles/livecaptionn/speech/VoskModelCatalog.kt"
BASE = "https://alphacephei.com/vosk/models/"
ROW = re.compile(r'(small|large)\(\s*"([a-z]{2})",\s*"[^"]+",\s*"([^"]+)",\s*(\d+)\s*\)')
UA = {"User-Agent": "LiveCaptionN-model-link-check/1.0"}


def head(url):
    req = urllib.request.Request(url, method="HEAD", headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.status, int(r.headers.get("Content-Length") or 0)


def magic(url):
    req = urllib.request.Request(url, headers={**UA, "Range": "bytes=0-3"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read(4)


def main():
    src = CATALOG.read_text(encoding="utf-8")
    rows = ROW.findall(src)
    if not rows:
        print(f"::error::no catalog rows found in {CATALOG}")
        return 2
    failures = []
    for tier, code, name, mb in rows:
        url = f"{BASE}{name}.zip"
        try:
            status, length = head(url)
            first = magic(url)
        except Exception as e:  # noqa: BLE001
            failures.append(f"{name} ({code}, {tier}): {e}")
            print(f"FAIL {name}: {e}")
            continue
        problems = []
        if status != 200:
            problems.append(f"HTTP {status}")
        if length <= 0:
            problems.append("empty body")
        if first[:2] != b"PK":
            problems.append(f"not a zip (starts with {first!r})")
        served_mb = length / 1e6
        size_note = ""
        if length > 0 and abs(served_mb - int(mb)) > max(15, 0.35 * int(mb)):
            size_note = f"  (catalog says ~{mb} MB, server serves {served_mb:.0f} MB)"
            print(f"::warning::{name}: catalog size {mb} MB vs served {served_mb:.0f} MB")
        if problems:
            failures.append(f"{name} ({code}, {tier}): " + ", ".join(problems))
            print(f"FAIL {name}: " + ", ".join(problems))
        else:
            print(f"ok   {name}  {served_mb:.0f} MB{size_note}")
    print(f"{len(rows)} models checked, {len(failures)} broken")
    for f in failures:
        print(f"::error::dead model link: {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
