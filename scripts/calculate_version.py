#!/usr/bin/env python3
"""Validate a vMAJOR.MINOR.PATCH tag and print GitHub Actions outputs."""
from __future__ import annotations

import re
import sys

pattern = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
tag = sys.argv[1] if len(sys.argv) > 1 else ""
match = pattern.fullmatch(tag)
if not match:
    print("Expected a semantic version tag like v1.1.1", file=sys.stderr)
    sys.exit(2)
major, minor, patch = map(int, match.groups())
if minor >= 1000 or patch >= 1000:
    print("Minor and patch numbers must each be below 1000", file=sys.stderr)
    sys.exit(2)
version_code = major * 1_000_000 + minor * 1_000 + patch
if version_code < 1 or version_code > 2_100_000_000:
    print("Calculated Android versionCode is outside the supported positive range", file=sys.stderr)
    sys.exit(2)
print(f"name={major}.{minor}.{patch}")
print(f"code={version_code}")
