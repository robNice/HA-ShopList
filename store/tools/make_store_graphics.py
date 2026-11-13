"""Builds the store graphics in store/playstore/<language>/ from listing.json and the raw screenshots.

    phone_1.png ... phone_6.png   1080 x 1920, caption from "captions" above the raw screenshot in a phone frame
    feature_graphic.png           1024 x 500, "feature_title", "feature_tagline", icon and a tilted phone
    wear_N.png                    saved again without alpha channel (Play wants 24-bit PNG), otherwise unchanged

Raw screenshots come from store/screenshots_raw/<language>/ (store/tools/capture_phone_screenshots.sh).
Languages the app is not translated into use the screenshots of FALLBACK, with their own captions.

The pages are rendered with headless Chrome or Edge, so every script (Arabic, Hebrew, Hindi, Thai, CJK) is set
correctly with the system fonts. Set CHROME to the browser executable if it is not found.

Usage: python store/tools/make_store_graphics.py [language ...]
"""
import html
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / "store" / "playstore"
RAW = ROOT / "store" / "screenshots_raw"
ICON = STORE / "en-US" / "icon_512.png"
FALLBACK = {"es-419": "es-ES", "pt-PT": "pt-BR"}
DEFAULT_RAW = "en-US"
RTL = {"ar", "iw-IL"}
FONTS = {
    "ja-JP": '"Yu Gothic UI", "Yu Gothic", "Segoe UI"',
    "ko-KR": '"Malgun Gothic", "Segoe UI"',
    "zh-CN": '"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI"',
    "hi-IN": '"Nirmala UI", "Segoe UI"',
    "th": '"Leelawadee UI", "Segoe UI"',
}
DEFAULT_FONT = '"Segoe UI", "Nirmala UI", "Leelawadee UI", Arial'

BACKGROUND = """
  background:
    radial-gradient(ellipse at 50% 0%, rgba(255,255,255,0.35), rgba(255,255,255,0) 55%),
    linear-gradient(165deg, #8FD2FF 0%, #4EA8F6 42%, #1F78E0 100%);
"""

PHONE_PAGE = """<!doctype html>
<html lang="{lang}" dir="{dir}"><head><meta charset="utf-8"><style>
  html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; }}
  body {{ {background} font-family: {font}, sans-serif; position: relative; }}
  .caption {{
    position: absolute; left: 70px; right: 70px; top: 60px; height: 330px;
    display: flex; align-items: center; justify-content: center; text-align: center;
    color: #fff; font-size: {size}px; font-weight: 700; line-height: 1.18; text-wrap: balance;
    word-break: auto-phrase; text-shadow: 0 4px 18px rgba(15, 60, 130, 0.35);
  }}
  .phone {{
    position: absolute; left: 210px; top: 430px; width: 632px; padding: 14px;
    background: #10131a; border-radius: 66px;
    box-shadow: 0 40px 80px rgba(10, 40, 100, 0.45), inset 0 0 0 3px #2c3340;
  }}
  .phone img {{ display: block; width: 632px; border-radius: 52px; }}
</style></head><body>
  <div class="caption">{caption}</div>
  <div class="phone"><img src="{shot}"></div>
</body></html>
"""

FEATURE_PAGE = """<!doctype html>
<html lang="{lang}" dir="ltr"><head><meta charset="utf-8"><style>
  html, body {{ margin: 0; width: 1024px; height: 500px; overflow: hidden; }}
  body {{ {background} font-family: {font}, sans-serif; position: relative; }}
  .icon {{ position: absolute; left: 64px; top: 70px; width: 128px; height: 128px; border-radius: 30px;
          box-shadow: 0 14px 30px rgba(10, 40, 100, 0.35); }}
  .text {{ position: absolute; left: 64px; top: 222px; width: 560px; text-align: left; }}
  .title {{ color: #fff; font-family: "Segoe UI", Arial, sans-serif; font-size: 74px; font-weight: 800;
           line-height: 1; letter-spacing: -1px; text-shadow: 0 4px 16px rgba(15, 60, 130, 0.35); }}
  .tagline {{ margin-top: 18px; color: #FFE08A; font-size: {tagline_size}px; font-weight: 600; line-height: 1.25;
             text-wrap: balance; word-break: auto-phrase; unicode-bidi: plaintext; text-align: left;
             text-shadow: 0 2px 10px rgba(15, 60, 130, 0.35); }}
  .phone {{ position: absolute; left: 700px; top: 34px; width: 280px; padding: 8px; background: #10131a;
           border-radius: 34px; transform: rotate(8deg); transform-origin: top left;
           box-shadow: 0 30px 60px rgba(10, 40, 100, 0.5), inset 0 0 0 2px #2c3340; }}
  .phone img {{ display: block; width: 280px; border-radius: 27px; }}
</style></head><body>
  <img class="icon" src="{icon}">
  <div class="text"><div class="title">{title}</div><div class="tagline" dir="{dir}">{tagline}</div></div>
  <div class="phone"><img src="{shot}"></div>
</body></html>
"""


def find_browser() -> str:
    candidates = [os.environ.get("CHROME", ""),
                  r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                  r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                  shutil.which("google-chrome") or "", shutil.which("chromium") or "", shutil.which("msedge") or ""]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    sys.exit("No Chrome or Edge found; set CHROME to the browser executable")


def render(browser: str, page: str, size: tuple, target: Path, work: Path):
    source = work / "page.html"
    source.write_text(page, encoding="utf-8")
    shot = work / "shot.png"
    shot.unlink(missing_ok=True)
    subprocess.run([browser, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--user-data-dir={work / 'profile'}", f"--window-size={size[0]},{size[1]}",
                    f"--screenshot={shot}", "--virtual-time-budget=3000", source.as_uri()],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    image = Image.open(shot).convert("RGB")
    if image.size != size:
        sys.exit(f"{target}: rendered {image.size}, expected {size}")
    image.save(target, optimize=True)


def text(value: str) -> str:
    # Keeps the product name on one line, otherwise right-to-left lines can tear it apart
    return html.escape(value).replace("Home Assistant", "Home&nbsp;Assistant")


def caption_size(text: str) -> int:
    length = len(text)
    return 76 if length <= 40 else 70 if length <= 55 else 64


def build(language: str, browser: str, work: Path):
    folder = STORE / language
    listing = json.loads((folder / "listing.json").read_text(encoding="utf-8"))
    captions = listing.get("captions", [])
    raw = RAW / language
    if not raw.is_dir():
        raw = RAW / FALLBACK.get(language, DEFAULT_RAW)
    shots = [raw / f"{n}.png" for n in range(1, len(captions) + 1)]
    missing = [str(s) for s in shots if not s.is_file()]
    if missing:
        sys.exit(f"{language}: raw screenshots missing: {', '.join(missing)}")

    common = {"lang": language.split("-")[0], "dir": "rtl" if language in RTL else "ltr",
              "font": FONTS.get(language, DEFAULT_FONT), "background": BACKGROUND}
    for number, (caption, shot) in enumerate(zip(captions, shots), start=1):
        page = PHONE_PAGE.format(caption=text(caption), size=caption_size(caption), shot=shot.as_uri(), **common)
        render(browser, page, (1080, 1920), folder / f"phone_{number}.png", work)
    for stale in folder.glob("phone_*.png"):
        if int(stale.stem.split("_")[1]) > len(captions):
            stale.unlink()

    tagline = listing.get("feature_tagline", "")
    page = FEATURE_PAGE.format(title=text(listing.get("feature_title", "")), tagline=text(tagline),
                               tagline_size=34 if len(tagline) <= 34 else 30, icon=ICON.as_uri(),
                               shot=(raw / "2.png").as_uri(), **common)
    render(browser, page, (1024, 500), folder / "feature_graphic.png", work)

    for wear in folder.glob("wear_*.png"):
        image = Image.open(wear)
        if image.mode != "RGB":
            image.convert("RGB").save(wear, optimize=True)
    print(f"{language}: {len(captions)} phone screenshots, feature graphic (screenshots from {raw.name})")


def main():
    browser = find_browser()
    languages = sys.argv[1:] or sorted(p.name for p in STORE.iterdir() if (p / "listing.json").is_file())
    with tempfile.TemporaryDirectory() as tmp:
        for language in languages:
            build(language, browser, Path(tmp))


if __name__ == "__main__":
    main()
