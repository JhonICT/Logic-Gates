#!/usr/bin/env python3
"""Static/syntax smoke checks for the standalone 74LS Logic Lab HTML app."""
from __future__ import annotations

import argparse
import subprocess
import tempfile
from html.parser import HTMLParser
from pathlib import Path
import sys


class AppHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=False)
        self.inline_scripts: list[tuple[str, int]] = []
        self.ids: set[str] = set()
        self.title_parts: list[str] = []
        self.in_title = False
        self._script_attrs: dict[str, str | None] | None = None
        self._script_parts: list[str] = []
        self._script_line = 1

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_map = dict(attrs)
        if "id" in attrs_map and attrs_map["id"]:
            self.ids.add(str(attrs_map["id"]))
        if tag.lower() == "title":
            self.in_title = True
        if tag.lower() == "script":
            self._script_attrs = attrs_map
            self._script_parts = []
            self._script_line = self.getpos()[0]

    def handle_data(self, data: str) -> None:
        if self.in_title:
            self.title_parts.append(data)
        if self._script_attrs is not None:
            self._script_parts.append(data)

    def handle_entityref(self, name: str) -> None:
        if self.in_title:
            self.title_parts.append(f"&{name};")
        if self._script_attrs is not None:
            self._script_parts.append(f"&{name};")

    def handle_charref(self, name: str) -> None:
        if self.in_title:
            self.title_parts.append(f"&#{name};")
        if self._script_attrs is not None:
            self._script_parts.append(f"&#{name};")

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "title":
            self.in_title = False
        if tag.lower() == "script" and self._script_attrs is not None:
            attrs = self._script_attrs
            src = attrs.get("src")
            mime = (attrs.get("type") or "").strip().lower()
            is_data = mime in {"application/json", "application/ld+json", "importmap", "speculationrules"}
            if not src and not is_data:
                self.inline_scripts.append(("".join(self._script_parts), self._script_line))
            self._script_attrs = None
            self._script_parts = []


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("html", type=Path)
    args = ap.parse_args()
    file_path = args.html
    source = file_path.read_text(encoding="utf-8")

    parser = AppHTMLParser()
    parser.feed(source)
    parser.close()

    checks = {
        "document title includes 74LS Logic Lab": "74LS Logic Lab" in "".join(parser.title_parts),
        "application root element exists": "app" in parser.ids,
        "navigation or workspace controller exists": "setWorkspace" in source,
        "profile backup/restore feature marker exists": ("Export all progress" in source or "export all progress" in source.lower()) and ("Restore from backup" in source or "restore from backup" in source.lower()),
        "PCB workspace marker exists": "PCB Layout Studio" in source or "PCB Studio" in source,
        "at least one inline JavaScript block exists": bool(parser.inline_scripts),
    }
    for name, okay in checks.items():
        print(f"{'PASS' if okay else 'FAIL'}: {name}")
    failed = [name for name, okay in checks.items() if not okay]
    node = __import__("shutil").which("node")
    if not node:
        print("FAIL: Node.js is required for inline JavaScript syntax checking", file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(prefix="74ls-js-check-") as tmp:
        for index, (script, source_line) in enumerate(parser.inline_scripts, 1):
            js_path = Path(tmp) / f"inline-{index}.js"
            js_path.write_text(script, encoding="utf-8")
            result = subprocess.run([node, "--check", str(js_path)], text=True, capture_output=True)
            if result.returncode:
                print(f"FAIL: inline JavaScript block {index} (starts near HTML line {source_line})")
                print(result.stderr.strip())
                failed.append(f"inline JavaScript block {index}")
            else:
                print(f"PASS: inline JavaScript block {index} syntax")

    if failed:
        print(f"\n{len(failed)} check(s) failed.", file=sys.stderr)
        return 1
    print("\nAll web source checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
