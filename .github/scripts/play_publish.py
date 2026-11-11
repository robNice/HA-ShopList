"""Upload bundles, release notes and store listing to Google Play.

Store listing layout (fastlane, also read by F-Droid):

    fastlane/metadata/android/<language>/
        title.txt, short_description.txt, full_description.txt
        images/icon.png, images/featureGraphic.png
        images/phoneScreenshots/1.png, 2.png ...
        images/wearScreenshots/1.png, 2.png ...

Release notes: one file per version with a block per language, e.g.

    <en-US>
    • New feature
    </en-US>
    <de-DE>
    • Neue Funktion
    </de-DE>

Commands:
    check    writes bundle_exists / wear_bundle_exists to GITHUB_OUTPUT
    publish  uploads bundles, optionally syncs the store listing, sets the tracks
    pull     downloads the current Play store listing into a fastlane folder
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

try:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:  # "validate" works without the Play API client
    service_account = build = MediaFileUpload = None

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

# fastlane file -> (Play API field, max length)
TEXT_FILES = {
    "title.txt": ("title", 30),
    "short_description.txt": ("shortDescription", 80),
    "full_description.txt": ("fullDescription", 4000),
}
# Play image type -> fastlane file below images/
SINGLE_IMAGES = {"icon": "icon.png", "featureGraphic": "featureGraphic.png"}
# Play image type -> fastlane folder below images/
SCREENSHOT_DIRS = {
    "phoneScreenshots": "phoneScreenshots",
    "sevenInchScreenshots": "sevenInchScreenshots",
    "tenInchScreenshots": "tenInchScreenshots",
    "wearScreenshots": "wearScreenshots",
}
MAX_SCREENSHOTS = 8
MAX_RELEASE_NOTES = 500


class PlayPublisher:
    def __init__(self, package_name: str, credentials_json: str):
        if build is None:
            sys.exit("Play API client missing: pip install google-api-python-client google-auth")
        info = json.loads(credentials_json)
        credentials = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
        self.package = package_name
        self.edits = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False).edits()
        self.edit_id = self.edits.insert(packageName=package_name, body={}).execute()["id"]

    def _args(self, **kwargs):
        return {"packageName": self.package, "editId": self.edit_id, **kwargs}

    def abort(self):
        self.edits.delete(**self._args()).execute()

    def commit(self):
        self.edits.commit(**self._args()).execute()

    def has_bundle(self, version_code: int) -> bool:
        bundles = self.edits.bundles().list(**self._args()).execute()
        return any(b["versionCode"] == version_code for b in bundles.get("bundles", []))

    def upload_bundle(self, aab: Path) -> int:
        media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
        return self.edits.bundles().upload(**self._args(), media_body=media).execute()["versionCode"]

    def listings(self) -> dict:
        result = self.edits.listings().list(**self._args()).execute()
        return {l["language"]: l for l in result.get("listings", [])}

    def update_listing(self, language: str, current: dict, wanted: dict) -> bool:
        if all(current.get(k) == v for k, v in wanted.items()):
            return False
        body = {**current, "language": language, **wanted}
        self.edits.listings().update(**self._args(language=language), body=body).execute()
        return True

    def list_images(self, language: str, image_type: str) -> list:
        return self.edits.images().list(**self._args(language=language, imageType=image_type)).execute().get("images", [])

    def sync_images(self, language: str, image_type: str, files: list) -> bool:
        if not files:
            return False
        current = self.list_images(language, image_type)
        wanted = [hashlib.sha256(f.read_bytes()).hexdigest() for f in files]
        if [i.get("sha256") for i in current] == wanted:
            return False
        images = self.edits.images()
        if current:
            images.deleteall(**self._args(language=language, imageType=image_type)).execute()
        for f in files:
            images.upload(**self._args(language=language, imageType=image_type),
                          media_body=MediaFileUpload(str(f), mimetype="image/png")).execute()
        return True

    # Phone and watch releases go into one edit, so both appear on Play together.
    def set_track(self, track: str, version_code: int, version_name: str, status: str, notes: dict):
        release = {
            "name": version_name,
            "versionCodes": [str(version_code)],
            "status": status,
            "releaseNotes": [{"language": lang, "text": text} for lang, text in sorted(notes.items())],
        }
        self.edits.tracks().update(**self._args(track=track), body={"track": track, "releases": [release]}).execute()


# ------------------------------------------------------------------ helpers

def read_release_notes(path: Path) -> dict:
    if not path.is_file():
        sys.exit(f"Release notes missing: {path}")
    text = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n")
    notes = {lang: body.strip() for lang, body in re.findall(r"<([\w-]+)>\s*\n(.*?)\n\s*</\1>", text, re.S)}
    too_long = {lang: len(t) for lang, t in notes.items() if len(t) > MAX_RELEASE_NOTES}
    if not notes or too_long:
        sys.exit(f"Release notes invalid in {path}: "
                 + (f"over {MAX_RELEASE_NOTES} characters: {too_long}" if too_long else "no language blocks"))
    return notes


def numbered_pngs(folder: Path) -> list:
    if not folder.is_dir():
        return []
    files = [f for f in folder.iterdir() if re.fullmatch(r"\d+\.png", f.name)]
    return sorted(files, key=lambda f: int(f.stem))


def read_texts(folder: Path) -> dict:
    wanted, errors = {}, []
    for name, (field, limit) in TEXT_FILES.items():
        file = folder / name
        if not file.is_file():
            continue
        value = file.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()
        if len(value) > limit:
            errors.append(f"{file}: {len(value)} characters, Play allows {limit}")
        elif value:
            wanted[field] = value
    if errors:
        sys.exit("Store texts too long:\n  " + "\n  ".join(errors))
    return wanted


def validate_store(store_dir: Path):
    errors = []
    for folder in sorted(p for p in store_dir.iterdir() if p.is_dir()):
        for subdir in SCREENSHOT_DIRS.values():
            count = len(numbered_pngs(folder / "images" / subdir))
            if count > MAX_SCREENSHOTS:
                errors.append(f"{folder.name}/images/{subdir}: {count} screenshots, Play allows {MAX_SCREENSHOTS}")
        read_texts(folder)
    if errors:
        sys.exit("Store listing invalid:\n  " + "\n  ".join(errors))


def sync_store(publisher: PlayPublisher, store_dir: Path):
    validate_store(store_dir)
    listings = publisher.listings()
    for folder in sorted(p for p in store_dir.iterdir() if p.is_dir()):
        language = folder.name
        wanted = read_texts(folder)
        if language not in listings and "title" not in wanted:
            print(f"Store listing {language}: skipped, not on Play and no title.txt")
            continue
        changes = []
        if wanted and publisher.update_listing(language, listings.get(language, {}), wanted):
            changes.append("texts")
        images = folder / "images"
        for image_type, name in SINGLE_IMAGES.items():
            if (images / name).is_file() and publisher.sync_images(language, image_type, [images / name]):
                changes.append(image_type)
        for image_type, subdir in SCREENSHOT_DIRS.items():
            if publisher.sync_images(language, image_type, numbered_pngs(images / subdir)):
                changes.append(image_type)
        print(f"Store listing {language}: {', '.join(changes) if changes else 'unchanged'}")


def download(url: str, target: Path, sha256: str):
    # Play image URLs serve a scaled copy by default; "=s0" asks for the original size.
    for candidate in (f"{url}=s0", url):
        try:
            data = urllib.request.urlopen(candidate, timeout=60).read()
        except urllib.error.HTTPError:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        if sha256 and hashlib.sha256(data).hexdigest() != sha256:
            print(f"  note: {target} differs from the uploaded original (Play re-encoded it)")
        return
    print(f"  warning: could not download {url}")


def pull_store(publisher: PlayPublisher, out_dir: Path):
    for language, listing in sorted(publisher.listings().items()):
        folder = out_dir / language
        folder.mkdir(parents=True, exist_ok=True)
        for name, (field, _) in TEXT_FILES.items():
            if listing.get(field):
                (folder / name).write_text(listing[field] + "\n", encoding="utf-8")
        for image_type, name in SINGLE_IMAGES.items():
            for image in publisher.list_images(language, image_type)[:1]:
                download(image["url"], folder / "images" / name, image.get("sha256"))
        for image_type, subdir in SCREENSHOT_DIRS.items():
            for index, image in enumerate(publisher.list_images(language, image_type), start=1):
                download(image["url"], folder / "images" / subdir / f"{index}.png", image.get("sha256"))
        print(f"Pulled store listing {language}")


def write_output(name: str, value: str):
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as f:
            f.write(f"{name}={value}\n")
    print(f"{name}={value}")


# --------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["check", "publish", "pull", "validate"])
    parser.add_argument("--package")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    parser.add_argument("--track")
    parser.add_argument("--status", default="completed")
    parser.add_argument("--aab", type=Path)
    parser.add_argument("--wear-version-code", type=int)
    parser.add_argument("--wear-track")
    parser.add_argument("--wear-aab", type=Path)
    parser.add_argument("--release-notes", type=Path)
    parser.add_argument("--store-dir", type=Path, help="fastlane metadata folder; omit to leave the listing untouched")
    parser.add_argument("--out", type=Path, help="target folder for pull")
    args = parser.parse_args()

    # Offline check of texts, screenshot counts and release notes (no Play access needed)
    if args.command == "validate":
        if args.store_dir:
            validate_store(args.store_dir)
        if args.release_notes:
            read_release_notes(args.release_notes)
        print("Store data valid")
        return

    publisher = PlayPublisher(args.package, os.environ["PLAY_SERVICE_ACCOUNT_JSON"])

    if args.command == "check":
        write_output("bundle_exists", str(publisher.has_bundle(args.version_code)).lower())
        if args.wear_version_code:
            write_output("wear_bundle_exists", str(publisher.has_bundle(args.wear_version_code)).lower())
        publisher.abort()
        return

    if args.command == "pull":
        pull_store(publisher, args.out)
        publisher.abort()
        return

    notes = read_release_notes(args.release_notes)
    if args.store_dir:
        validate_store(args.store_dir)

    if args.aab:
        uploaded = publisher.upload_bundle(args.aab)
        if uploaded != args.version_code:
            publisher.abort()
            sys.exit(f"Uploaded bundle has versionCode {uploaded}, expected {args.version_code}")
        print(f"Uploaded bundle {uploaded}")
    elif not publisher.has_bundle(args.version_code):
        publisher.abort()
        sys.exit(f"versionCode {args.version_code} is not on Play and no bundle was given")
    if args.wear_aab:
        uploaded = publisher.upload_bundle(args.wear_aab)
        if uploaded != args.wear_version_code:
            publisher.abort()
            sys.exit(f"Uploaded watch bundle has versionCode {uploaded}, expected {args.wear_version_code}")
        print(f"Uploaded watch bundle {uploaded}")

    if args.store_dir:
        sync_store(publisher, args.store_dir)
    else:
        print("Store listing: not synced (disabled)")

    languages = set(publisher.listings())
    skipped = sorted(set(notes) - languages)
    if skipped:
        print(f"Release notes skipped, no store listing in: {', '.join(skipped)}")
    listed_notes = {lang: text for lang, text in notes.items() if lang in languages}
    publisher.set_track(args.track, args.version_code, args.version_name, args.status, listed_notes)
    if args.wear_track and args.wear_version_code and publisher.has_bundle(args.wear_version_code):
        publisher.set_track(args.wear_track, args.wear_version_code, args.version_name, args.status, listed_notes)
        print(f"Released watch {args.version_name} ({args.wear_version_code}) to track '{args.wear_track}'")
    publisher.commit()
    print(f"Released {args.version_name} ({args.version_code}) to track '{args.track}' with status '{args.status}'")


if __name__ == "__main__":
    main()
