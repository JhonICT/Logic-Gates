#!/usr/bin/env python3
"""Check key repository invariants without needing the Android SDK."""
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
checks: list[tuple[str, bool]] = []
web_file = ROOT / "index.html"
android_copy = ROOT / "android/app/src/main/assets/www/index.html"
checks.append(("web app exists", web_file.is_file()))
checks.append(("Android-embedded HTML exists", android_copy.is_file()))
checks.append(("web and Android HTML copies match exactly", web_file.exists() and android_copy.exists() and web_file.read_bytes() == android_copy.read_bytes()))

manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"
gradle_path = ROOT / "android/app/build.gradle"
manifest = manifest_path.read_text(encoding="utf-8") if manifest_path.exists() else ""
gradle = gradle_path.read_text(encoding="utf-8") if gradle_path.exists() else ""
package_id = "com.seventyfourls.logiclab"
checks.append(("stable Android application ID is configured", package_id in gradle and ".MainActivity" in manifest))
checks.append(("versionName/versionCode support release properties", "findProperty('versionName')" in gradle and "findProperty('versionCode')" in gradle))

example_path = ROOT / "android/app/google-services.json.example"
try:
    example = json.loads(example_path.read_text(encoding="utf-8"))
    project_id = example["project_info"]["project_id"]
    package_name = example["client"][0]["client_info"]["android_client_info"]["package_name"]
    safe_template = "replace-with" in str(project_id).lower() and package_name == package_id
except Exception:
    safe_template = False
checks.append(("Firebase example config is valid JSON with placeholders", safe_template))

gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8") if (ROOT / ".gitignore").exists() else ""
checks.append(("real Firebase config is git-ignored", "android/app/google-services.json" in gitignore))
checks.append(("release workflow exists", (ROOT / ".github/workflows/release.yml").is_file()))
checks.append(("web deployment workflow exists", (ROOT / ".github/workflows/pages.yml").is_file()))

failed = []
for label, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
    if not passed:
        failed.append(label)
if failed:
    print(f"\n{len(failed)} repository check(s) failed.", file=sys.stderr)
    sys.exit(1)
print("\nAll repository structure checks passed.")
