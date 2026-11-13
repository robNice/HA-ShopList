import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
LISTING_FIELDS = {"title": "title", "short_description": "shortDescription", "full_description": "fullDescription"}
SINGLE_IMAGES = {"icon": "icon_512.png", "featureGraphic": "feature_graphic.png"}
SCREENSHOTS = {"phoneScreenshots": re.compile(r"phone_(\d+)\.png$"), "wearScreenshots": re.compile(r"wear_(\d+)\.png$")}


class PlayPublisher:
    def __init__(self, package_name: str, credentials_json: str):
        info = json.loads(credentials_json)
        credentials = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
        self.package = package_name
        self.edits = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False).edits()
        self.edit_id = self.edits.insert(packageName=package_name, body={}).execute()["id"]

    def abort(self):
        self.edits.delete(packageName=self.package, editId=self.edit_id).execute()

    # Returns False if Play only saved the edit: with changes from the Play Console that are not sent for review
    # (e.g. a new form factor), everything has to be sent for review there together.
    def commit(self) -> bool:
        try:
            self.edits.commit(packageName=self.package, editId=self.edit_id).execute()
            return True
        except HttpError as e:
            if e.resp.status != 400 or "changesNotSentForReview" not in str(e):
                raise
        self.edits.commit(packageName=self.package, editId=self.edit_id, changesNotSentForReview=True).execute()
        return False

    def has_bundle(self, version_code: int) -> bool:
        bundles = self.edits.bundles().list(packageName=self.package, editId=self.edit_id).execute()
        return any(b["versionCode"] == version_code for b in bundles.get("bundles", []))

    def upload_bundle(self, aab: Path) -> int:
        media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
        result = self.edits.bundles().upload(packageName=self.package, editId=self.edit_id, media_body=media).execute()
        return result["versionCode"]

    def listing_languages(self) -> set:
        listings = self.edits.listings().list(packageName=self.package, editId=self.edit_id).execute()
        return {l["language"] for l in listings.get("listings", [])}

    def sync_listing(self, language: str, folder: Path) -> bool:
        data = json.loads((folder / "listing.json").read_text(encoding="utf-8"))
        wanted = {api: data[key] for key, api in LISTING_FIELDS.items() if data.get(key)}
        try:
            current = self.edits.listings().get(packageName=self.package, editId=self.edit_id, language=language).execute()
        except HttpError as e:
            if e.resp.status != 404:
                raise
            current = {}
        if all(current.get(k) == v for k, v in wanted.items()):
            return False
        self.edits.listings().update(packageName=self.package, editId=self.edit_id, language=language,
                                     body={**current, "language": language, **wanted}).execute()
        return True

    def sync_images(self, language: str, image_type: str, files: list) -> bool:
        if not files:
            return False
        images = self.edits.images()
        current = images.list(packageName=self.package, editId=self.edit_id, language=language,
                              imageType=image_type).execute().get("images", [])
        wanted = [hashlib.sha256(f.read_bytes()).hexdigest() for f in files]
        if [i.get("sha256") for i in current] == wanted:
            return False
        if current:
            images.deleteall(packageName=self.package, editId=self.edit_id, language=language,
                             imageType=image_type).execute()
        for f in files:
            images.upload(packageName=self.package, editId=self.edit_id, language=language, imageType=image_type,
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
        self.edits.tracks().update(packageName=self.package, editId=self.edit_id, track=track,
                                   body={"track": track, "releases": [release]}).execute()


def read_release_notes(path: Path) -> dict:
    if not path.is_file():
        sys.exit(f"Release notes missing: {path}")
    text = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n")
    notes = {lang: body.strip() for lang, body in re.findall(r"<([\w-]+)>\s*\n(.*?)\n\s*</\1>", text, re.S)}
    too_long = {lang: len(t) for lang, t in notes.items() if len(t) > 500}
    if not notes or too_long:
        sys.exit(f"Release notes invalid in {path}: " + (f"over 500 characters: {too_long}" if too_long else "no language blocks"))
    return notes


def sync_store(publisher: PlayPublisher, store_dir: Path):
    for folder in sorted(p for p in store_dir.iterdir() if (p / "listing.json").is_file()):
        language = folder.name
        changes = []
        if publisher.sync_listing(language, folder):
            changes.append("texts")
        for image_type, name in SINGLE_IMAGES.items():
            if (folder / name).is_file() and publisher.sync_images(language, image_type, [folder / name]):
                changes.append(image_type)
        for image_type, pattern in SCREENSHOTS.items():
            shots = sorted((f for f in folder.iterdir() if pattern.match(f.name)),
                           key=lambda f: int(pattern.match(f.name).group(1)))
            if publisher.sync_images(language, image_type, shots):
                changes.append(image_type)
        print(f"Store listing {language}: {', '.join(changes) if changes else 'unchanged'}")


def download(url: str, target: Path):
    # Play image URLs serve a scaled copy by default; "=s0" asks for the original size.
    for candidate in (f"{url}=s0", url):
        try:
            target.write_bytes(urllib.request.urlopen(candidate, timeout=60).read())
            return
        except urllib.error.HTTPError:
            continue
    print(f"  warning: could not download {url}")


# Writes the live Play listing in the layout of store/playstore, without feature_title, feature_tagline and captions.
def pull_store(publisher: PlayPublisher, out_dir: Path):
    listings = publisher.edits.listings().list(packageName=publisher.package, editId=publisher.edit_id).execute()
    for listing in listings.get("listings", []):
        language = listing["language"]
        folder = out_dir / language
        folder.mkdir(parents=True, exist_ok=True)
        data = {key: listing[api] for key, api in LISTING_FIELDS.items() if listing.get(api)}
        (folder / "listing.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        targets = {**{t: [name] for t, name in SINGLE_IMAGES.items()},
                   "phoneScreenshots": [f"phone_{i}.png" for i in range(1, 9)],
                   "wearScreenshots": [f"wear_{i}.png" for i in range(1, 9)]}
        for image_type, names in targets.items():
            current = publisher.edits.images().list(packageName=publisher.package, editId=publisher.edit_id,
                                                    language=language, imageType=image_type).execute()
            for image, name in zip(current.get("images", []), names):
                download(image["url"], folder / name)
        print(f"Pulled store listing {language}")


def write_output(name: str, value: str):
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as f:
            f.write(f"{name}={value}\n")
    print(f"{name}={value}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["check", "publish", "pull"])
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    parser.add_argument("--track")
    parser.add_argument("--status", default="completed")
    parser.add_argument("--aab", type=Path)
    parser.add_argument("--wear-version-code", type=int)
    parser.add_argument("--wear-track")
    parser.add_argument("--wear-aab", type=Path)
    parser.add_argument("--store-dir", type=Path)
    args = parser.parse_args()

    publisher = PlayPublisher(args.package, os.environ["PLAY_SERVICE_ACCOUNT_JSON"])

    if args.command == "pull":
        pull_store(publisher, args.store_dir)
        publisher.abort()
        return

    if args.command == "check":
        write_output("bundle_exists", str(publisher.has_bundle(args.version_code)).lower())
        if args.wear_version_code:
            write_output("wear_bundle_exists", str(publisher.has_bundle(args.wear_version_code)).lower())
        publisher.abort()
        return

    notes = read_release_notes(args.store_dir / f"release_notes_{args.version_name}.txt")
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

    sync_store(publisher, args.store_dir)

    languages = publisher.listing_languages()
    skipped = sorted(set(notes) - languages)
    if skipped:
        print(f"Release notes skipped, no store listing in: {', '.join(skipped)}")
    listed_notes = {lang: text for lang, text in notes.items() if lang in languages}
    publisher.set_track(args.track, args.version_code, args.version_name, args.status, listed_notes)
    if args.wear_track and args.wear_version_code and publisher.has_bundle(args.wear_version_code):
        publisher.set_track(args.wear_track, args.wear_version_code, args.version_name, args.status, listed_notes)
        print(f"Released watch {args.version_name} ({args.wear_version_code}) to track '{args.wear_track}'")
    if publisher.commit():
        print(f"Released {args.version_name} ({args.version_code}) to track '{args.track}' with status '{args.status}'")
    else:
        print(f"Saved {args.version_name} ({args.version_code}) for track '{args.track}', not sent for review yet")
        print("::warning::The Play Console has changes that must be sent for review there. "
              "Open Publishing overview in the Play Console and send all changes for review.")


if __name__ == "__main__":
    main()
