#!/usr/bin/env python3
"""Pushes Play Store listing text (title, short/full description) from
playstore/metadata/<lang>/*.txt to Google Play Console via the Android
Publisher API.

Requires GOOGLE_PLAY_SERVICE_ACCOUNT_JSON in the environment. The service
account must have been granted "Edit store listing" (part of "Manage store
presence") permission for this app under Play Console -> Users and
permissions -> [service account] -> App permissions -- the same account used
to publish releases does NOT automatically have this permission.
"""
import json
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

PACKAGE_NAME = "com.charles.livecaptionn"
METADATA_ROOT = Path("playstore/metadata")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def main() -> None:
    sa_key = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    if not sa_key:
        print("::error::GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is missing", file=sys.stderr)
        sys.exit(1)

    creds = service_account.Credentials.from_service_account_info(
        json.loads(sa_key),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    service = build("androidpublisher", "v3", credentials=creds)

    edit_id = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()["id"]

    updated = []
    for lang_dir in sorted(p for p in METADATA_ROOT.iterdir() if p.is_dir()):
        title_file = lang_dir / "title.txt"
        short_file = lang_dir / "short_description.txt"
        full_file = lang_dir / "full_description.txt"
        if not (title_file.exists() and short_file.exists() and full_file.exists()):
            continue

        language = lang_dir.name
        body = {
            "language": language,
            "title": read(title_file),
            "shortDescription": read(short_file),
            "fullDescription": read(full_file),
        }
        try:
            service.edits().listings().update(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                language=language,
                body=body,
            ).execute()
        except HttpError as exc:
            if exc.resp.status == 403:
                print(
                    "::error::Play API returned 403 updating the store listing. "
                    "The service account most likely lacks the 'Edit store listing' "
                    "permission -- grant it under Play Console -> Users and "
                    "permissions -> (service account) -> App permissions, then retry.",
                    file=sys.stderr,
                )
            raise
        updated.append(language)
        print(f"Staged listing update for {language}")

    if not updated:
        print(
            "::error::No complete listing metadata found under playstore/metadata/ "
            "(expected title.txt, short_description.txt, full_description.txt per language folder)",
            file=sys.stderr,
        )
        sys.exit(1)

    service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print(f"Committed Play Store listing update for: {', '.join(updated)}")


if __name__ == "__main__":
    main()
