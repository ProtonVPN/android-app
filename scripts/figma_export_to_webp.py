# Copyright (c) 2026 Proton AG
#
# This file is part of ProtonVPN.
#
# ProtonVPN is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# ProtonVPN is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
#

"""Convert a Figma PNG export to Android density webps.

Usage: python3 scripts/figma_export_to_webp.py <figma-export.zip>

To export 1+ icons from Figma, select them, then click on Export in the panel on the right, add the
1x, 1.5x, 2x, 3x, 4x PNG options (you don't need to specify a suffix, and keep sRGB color space),
and export the .zip.

When you don't specify suffixes for 1x, 1.5x and 4x, Figma names the scales inconsistently: foo.png,
foo-1.png, foo@2x.png, foo@3x.png, foo-2.png. The PNGs are grouped by name with the scale suffix
dropped and dashes turned into underscores, so globe-error@2x.png belongs to globe_error. Each group
is then sorted by pixel size and assigned to the densities in order.

Each density of each group is encoded as optipng-optimized PNG, lossless webp, q95 webp and q90 webp,
and the smallest one is written to app/src/main/res/drawable-<density>/<name>.{png,webp}, so
densities of the same icon may end up in different formats (allowed by Android).

Requires optipng and ImageMagick 7 (`magick`) with webp support.
"""

import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import NamedTuple

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
APP_SRC_DIR = Path(__file__).resolve().parent.parent / "app" / "src"
RES_DIR = APP_SRC_DIR / "main" / "res"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
ENCODINGS = {
    "optipng": ("png", None),
    "lossless": ("webp", ["-quality", "100", "-define", "webp:lossless=true"]),
    # Android Studio's "Convert to WebP" uses 75 but compression artifacts become too visible
    "q95": ("webp", ["-quality", "95"]),
    "q90": ("webp", ["-quality", "90"]),
}


class Png(NamedTuple):
    filename: str
    width: int
    height: int
    data: bytes


def fail(message):
    sys.exit(f"error: {message}")


def kb(size):
    value = size / 1000
    return f"{value:.1f}kB" if round(value, 1) < 10 else f"{round(value)}kB"


def png_size(data, filename):
    # width and height are the first fields of the IHDR chunk
    if len(data) < 24 or not data.startswith(PNG_SIGNATURE):
        fail(f"{filename!r} is not a valid PNG")
    return struct.unpack(">II", data[16:24]) # returns (width, height)


def read_pngs(zip_path):
    pngs = []
    with zipfile.ZipFile(zip_path) as archive:
        for info in archive.infolist():
            path = Path(info.filename)
            # skip folders and macOS metadata (__MACOSX/, ._foo.png)
            if info.is_dir() or "__MACOSX" in path.parts or path.name.startswith("."):
                continue
            if path.suffix.lower() != ".png":
                fail(f"the export contains files that are not PNGs: {info.filename!r}")
            data = archive.read(info)
            w, h = png_size(data, path.name)
            pngs.append(Png(path.name, w, h, data))
    if not pngs:
        fail(f"no PNGs in {str(zip_path)!r}")
    return pngs


def group_by_name(pngs):
    # foo@2x.png and foo@3x.png are always suffixed, so they give the icon names. A name can itself
    # end in -N, so foo-1.png is the 1.5x of foo only if it isn't an icon name.
    names = {match[1] for png in pngs
             if (match := re.fullmatch(r"(.+)@[0-9.]+x", Path(png.filename).stem))}
    groups = defaultdict(list)
    for png in pngs:
        stem = re.sub(r"@[0-9.]+x$", "", Path(png.filename).stem)
        if stem not in names and (match := re.fullmatch(r"(.+)-[12]", stem)) and match[1] in names:
            stem = match[1]
        name = stem.replace("-", "_").replace(" ", "_").lower()
        if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
            fail(f"cannot infer a valid drawable name from {png.filename!r} (got {name!r})")
        groups[name].append(png)
    # sort images by size, matching the order of DENSITIES
    return {name: sorted(images, key=lambda png: png.width * png.height)
            for name, images in groups.items()}


def replaceable_paths(name):
    """The files this script writes for name, which may be replaced when they already exist."""
    return {RES_DIR / f"drawable-{density}" / f"{name}.{ext}"
            for density in DENSITIES for ext in ("png", "webp")}


def find_existing_resources(names):
    """Files in any drawable folder of the app whose resource name (up to the first dot) is in names.
    Other resource types (layout, mipmap, ...) can share names with drawables."""
    return sorted(path for path in APP_SRC_DIR.glob("*/res/drawable*/*")
                  if path.name.split(".")[0] in names)


def encode(data, encoding, dest):
    ext, magick_args = ENCODINGS[encoding]
    if ext == "png":
        dest.write_bytes(data)
        subprocess.run(["optipng", "-quiet", "-force", "-o5", "-strip", "all", str(dest)], check=True)
    else:
        subprocess.run(
            [
                "magick", "png:-",
                "-strip", # drop metadata and color space
                *magick_args,
                "-define", "webp:method=6",
                f"webp:{dest}",
            ],
            input=data,
            check=True,
        )


def main():
    if len(sys.argv) != 2:
        sys.exit(f"usage: python3 {sys.argv[0]} <figma-export.zip>")
    zip_path = Path(sys.argv[1])
    if not zip_path.is_file():
        fail(f"{str(zip_path)!r} missing")
    if shutil.which("magick") is None:
        fail("magick (ImageMagick 7) not found in PATH")
    if shutil.which("optipng") is None:
        fail("optipng not found in PATH")
    for density in DENSITIES:
        if not (RES_DIR / f"drawable-{density}").is_dir():
            fail(f"{RES_DIR / f'drawable-{density}'} missing")

    # find all of the assets
    groups = group_by_name(read_pngs(zip_path))

    # validate everything first so a bad export doesn't leave partial output
    for name, images in groups.items():
        if len(images) != len(DENSITIES):
            files = ", ".join(sorted(repr(png.filename) for png in images))
            fail(f"expected {len(DENSITIES)} PNGs for {name}, found {len(images)}: {files}")

        # each image must be the smallest one times its density scale, allowing for Figma rounding
        # both to whole pixels (e.g. 25x17 at 1.5x is 38x26)
        w0, h0 = images[0].width, images[0].height
        for scale, png in zip(DENSITIES.values(), images):
            tolerance = (1 + scale) / 2
            if abs(png.width - w0 * scale) > tolerance or abs(png.height - h0 * scale) > tolerance:
                fail(f"{png.filename!r} is {png.width}x{png.height}, expected about "
                     f"{w0 * scale:g}x{h0 * scale:g} for "
                     f"{scale:g}x, check the export")

    def file_list(paths):
        return "\n".join(f"  {path.relative_to(APP_SRC_DIR.parent.parent)}" for path in paths)

    existing = find_existing_resources(groups.keys())
    conflicts = [path for path in existing if path not in replaceable_paths(path.name.split(".")[0])]
    if conflicts:
        fail(f"resources with these names already exist, delete or rename them first:\n"
             f"{file_list(conflicts)}")
    if existing:
        print(f"warning: replacing existing files:\n{file_list(existing)}\n", file=sys.stderr, flush=True)

    with tempfile.TemporaryDirectory() as temp_dir:
        for name in sorted(groups):
            print(f"== {name}")

            # print a table of the various encodings, and pick the smallest one for each density
            print(f"  {'':<8} {'':<9} {'original':>9}" + "".join(f" {e:>9}" for e in ENCODINGS)
                  + f" {'picked':>9}", flush=True)
            totals = dict.fromkeys(ENCODINGS, 0)
            picked_total = 0
            for density, png in zip(DENSITIES, groups[name]):
                sizes = {}
                paths = {}
                for encoding, (ext, _) in ENCODINGS.items():
                    paths[encoding] = Path(temp_dir) / f"{name}_{density}_{encoding}.{ext}"
                    encode(png.data, encoding, paths[encoding])
                    sizes[encoding] = paths[encoding].stat().st_size
                    totals[encoding] += sizes[encoding]
                best = min(sizes, key=sizes.get) # on ties the earlier encoding wins
                picked_total += sizes[best]
                drawable_dir = RES_DIR / f"drawable-{density}"
                # the format may change, and foo.png and foo.webp together are a duplicate resource
                for ext in ("png", "webp"):
                    (drawable_dir / f"{name}.{ext}").unlink(missing_ok=True)
                shutil.copyfile(paths[best], drawable_dir / f"{name}.{ENCODINGS[best][0]}")
                print(f"  {density:<8} {f'{png.width}x{png.height}':<9} {kb(len(png.data)):>9}"
                      + "".join(f" {kb(size):>9}" for size in sizes.values()) + f" {best:>9}", flush=True)

            # add totals to the table
            original_total = sum(len(png.data) for png in groups[name])
            print(f"  {'total':<8} {'':<9} {kb(original_total):>9}"
                  + "".join(f" {kb(total):>9}" for total in totals.values()) + f" {kb(picked_total):>9}")

            def vs_original(total):
                return f"{(total - original_total) * 100 / original_total:+.0f}%"

            print(f"  {'vs orig':<8} {'':<9} {'':>9}"
                  + "".join(f" {vs_original(total):>9}" for total in totals.values())
                  + f" {vs_original(picked_total):>9}\n", flush=True)


if __name__ == "__main__":
    main()
