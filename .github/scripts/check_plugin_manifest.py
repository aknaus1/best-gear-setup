"""Check runelite-plugin.properties (and icon.png, if present) the way the Plugin Hub packager does.

Mirrors the checks in runelite/plugin-hub-tooling (package/.../packager/Plugin.java) that can fail a
submission, plus one of this repository's own: version= must match build.gradle's version.

  python .github/scripts/check_plugin_manifest.py
"""
import pathlib
import re
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
REQUIRED = ("displayName", "author", "description", "plugins", "build")
KNOWN = set(REQUIRED) | {"tags", "version", "support"}
ICON_MAX_BYTES = 256 * 1024
ICON_MAX_PIXELS = 50 * 100  # the packager's limit; the recommended size is 48x72


def read_properties(path):
    props = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()
    return props


def png_size(path):
    header = path.read_bytes()[:24]
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", header[16:24])


def main():
    errors = []
    props = read_properties(ROOT / "runelite-plugin.properties")

    for key in REQUIRED:
        if not props.get(key):
            errors.append(f'"{key}" must be set')
    if props.get("build") not in (None, "", "standard", "gradle"):
        errors.append(f'build must be standard or gradle, not "{props["build"]}"')
    unknown = sorted(set(props) - KNOWN)
    if unknown:
        errors.append(f"unknown keys: {', '.join(unknown)}")

    for name in filter(None, re.split(r"[,:;]\s*", props.get("plugins", ""))):
        source = ROOT / "src/main/java" / (name.strip().replace(".", "/") + ".java")
        if not source.is_file():
            errors.append(f"plugin class {name} has no source file at {source.relative_to(ROOT)}")

    gradle_version = re.search(r"^version\s*=\s*['\"]([^'\"]+)['\"]",
        (ROOT / "build.gradle").read_text(encoding="utf-8"), re.M)
    if props.get("version") and gradle_version and props["version"] != gradle_version.group(1):
        errors.append(f"version={props['version']} differs from build.gradle's {gradle_version.group(1)}")

    if not (ROOT / "LICENSE").is_file():
        errors.append("LICENSE is missing")

    icon = ROOT / "icon.png"
    if icon.exists():
        if icon.stat().st_size > ICON_MAX_BYTES:
            errors.append(f"icon.png is {icon.stat().st_size // 1024} KiB; the limit is 256 KiB")
        size = png_size(icon)
        if size is None:
            errors.append("icon.png is not a valid PNG")
        elif size[0] * size[1] > ICON_MAX_PIXELS:
            errors.append(f"icon.png is {size[0]}x{size[1]}; it should be 48x72 px")

    for error in errors:
        print(f"::error file=runelite-plugin.properties::{error}")
    if errors:
        sys.exit(1)
    print(f"runelite-plugin.properties OK ({props['displayName']} {props.get('version') or '(commit hash)'})")


if __name__ == "__main__":
    main()
