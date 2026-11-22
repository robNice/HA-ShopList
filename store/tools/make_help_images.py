"""Crops and converts the raw help screenshots (store/help_raw/<language>/*.png, from
capture_help_screenshots.sh) into app/src/main/assets/help/<language>/*.webp.

Usage (from the project root): python store/tools/make_help_images.py [language ...]
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW = os.path.join(ROOT, "store", "help_raw")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "help")

# Part of the 1080 x 2400 screenshot that matters (left, top, right, bottom)
CROPS = {
    "main": (0, 0, 1080, 1350),
    "list_menu": (0, 0, 1080, 900),
    "settings_connection": (0, 0, 1080, 1200),
    "settings_lists": (0, 0, 1080, 1700),
    "settings_markets": (0, 0, 1080, 1500),
    "market_add": (0, 500, 1080, 1800),
    "settings_data": (0, 0, 1080, 1650),
}
# Watch screenshots (capture_wear_screenshots.sh): shown side by side as round watches
WATCH_PAIRS = {"wear": ("wear_main", "wear_add")}
WATCH_GAP = 40
WIDTH = 540        # half resolution is sharp enough at 80 % of the screen width
QUALITY = 80


def convert(language: str) -> int:
    source = os.path.join(RAW, language)
    target = os.path.join(ASSETS, language)
    os.makedirs(target, exist_ok=True)
    total = 0
    for name, box in CROPS.items():
        path = os.path.join(source, name + ".png")
        if not os.path.exists(path):
            print(f"  missing {language}/{name}.png")
            continue
        image = Image.open(path).convert("RGB").crop(box)
        image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
        out = os.path.join(target, name + ".webp")
        image.save(out, "WEBP", quality=QUALITY, method=6)
        total += os.path.getsize(out)
    for name, parts in WATCH_PAIRS.items():
        paths = [os.path.join(source, part + ".png") for part in parts]
        if not all(os.path.exists(path) for path in paths):
            print(f"  missing {language}/{name} watch screenshots")
            continue
        out = os.path.join(target, name + ".webp")
        watch_pair(paths).save(out, "WEBP", quality=QUALITY, method=6)
        total += os.path.getsize(out)
    return total


def watch_pair(paths):
    """Round watch screens next to each other on a transparent background."""
    shots = [Image.open(path).convert("RGBA") for path in paths]
    size = shots[0].width
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    canvas = Image.new("RGBA", (size * len(shots) + WATCH_GAP * (len(shots) - 1), size), (0, 0, 0, 0))
    for index, shot in enumerate(shots):
        canvas.paste(shot, (index * (size + WATCH_GAP), 0), mask)
    return canvas.resize((WIDTH, round(canvas.height * WIDTH / canvas.width)), Image.LANCZOS)


languages = sys.argv[1:] or sorted(os.listdir(RAW))
grand = 0
for lang in languages:
    size = convert(lang)
    grand += size
    print(f"{lang}: {size // 1024} KB")
print(f"total: {grand // 1024} KB")
