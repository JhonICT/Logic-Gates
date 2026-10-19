#!/usr/bin/env python3
"""Send a release announcement to the Firebase app_updates topic."""
from __future__ import annotations

import json
import os
import sys

try:
    import requests
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
except ImportError as exc:
    print(f"Missing dependency: {exc}. Install requirements in scripts/requirements-fcm.txt", file=sys.stderr)
    sys.exit(2)

raw_service_account = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
version = os.environ.get("RELEASE_VERSION", "")
release_url = os.environ.get("RELEASE_URL", "")
if not raw_service_account:
    print("FIREBASE_SERVICE_ACCOUNT_JSON is required when release announcements are enabled", file=sys.stderr)
    sys.exit(2)
if not version or not release_url:
    print("RELEASE_VERSION and RELEASE_URL are required", file=sys.stderr)
    sys.exit(2)

try:
    info = json.loads(raw_service_account)
    project_id = info["project_id"]
    credentials = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/firebase.messaging"],
    )
    credentials.refresh(Request())
except Exception as exc:
    print(f"Could not load Firebase service account or get an access token: {exc}", file=sys.stderr)
    sys.exit(2)

body = {
    "message": {
        "topic": "app_updates",
        "notification": {
            "title": "74LS Logic Lab update available",
            "body": f"Version {version} is available. Visit the GitHub Releases page to download it.",
        },
        "data": {
            "title": "74LS Logic Lab update available",
            "body": f"Version {version} is available. Visit the GitHub Releases page to download it.",
            "version": version,
            "release_url": release_url,
        },
    }
}
endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
try:
    response = requests.post(
        endpoint,
        headers={"Authorization": f"Bearer {credentials.token}", "Content-Type": "application/json"},
        json=body,
        timeout=30,
    )
    if not response.ok:
        print(f"Firebase send failed with HTTP {response.status_code}: {response.text[:1000]}", file=sys.stderr)
        sys.exit(1)
    result = response.json()
    print(f"Firebase accepted update announcement for topic app_updates: {result.get('name', 'message accepted')}")
except requests.RequestException as exc:
    print(f"Request to Firebase Cloud Messaging failed: {exc}", file=sys.stderr)
    sys.exit(1)
