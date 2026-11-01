#!/usr/bin/env python3
"""Regenerates the app icons in src/main/resources/static from the wizard artwork below.

The PNGs, favicon.svg and favicon.ico are committed, so nobody needs to run this unless the
artwork changes (PLAN.md D5, plan-ui-redesign.md R7). It needs two pip packages, which are
self-contained wheels (no Cairo, no ImageMagick):

    python -m venv .tools/icons-venv
    .tools/icons-venv/bin/pip install pillow resvg-py      # Scripts\\pip.exe on Windows
    .tools/icons-venv/bin/python scripts/generate-icons.py

Artwork: a cartoon wizard (violet star hat, white beard) on a gold tile, drawn in the design
system's colours (plan-ui-redesign.md, Appendix A). One drawing, three framings:

    favicon.svg, icon-192.png, icon-512.png   rounded tile with transparent corners ("any" purpose)
    icon-maskable-512.png                     full-bleed square; the artwork stays inside the central
                                              80 % "safe zone" so Android can crop it to a circle
    apple-touch-icon.png (180)                opaque square (iOS rounds the corners itself)
    favicon.ico                               16, 32 and 48 px, for browsers that ask for /favicon.ico
"""
import io
from pathlib import Path

import resvg_py
from PIL import Image

STATIC = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "static"
ICONS = STATIC / "icons"

OUTLINE, VIOLET, VIOLET_DARK, GOLD = "#24174d", "#6d3fe0", "#5a2dc4", "#ffc83d"
SKIN, CHEEK, NOSE = "#ffd7b5", "#ff8fb8", "#ffb98f"

# The hat is one path, used for the fill, the clip of the band and the outline.
HAT = ("M148 266 C176 196 208 122 252 66 C262 52 286 54 292 70 C296 84 284 92 294 114 "
       "C316 162 346 214 366 266 Z")


def sparkle(cx, cy, r, fill, stroke_width=6):
    """A four-point star centred on (cx, cy) with radius r."""
    k = 0.28 * r
    d = (f"M{cx} {cy - r} L{cx + k:.1f} {cy - k:.1f} L{cx + r} {cy} L{cx + k:.1f} {cy + k:.1f} "
         f"L{cx} {cy + r} L{cx - k:.1f} {cy + k:.1f} L{cx - r} {cy} L{cx - k:.1f} {cy - k:.1f} Z")
    return (f'<path d="{d}" fill="{fill}" stroke="{OUTLINE}" stroke-width="{stroke_width}" '
            'stroke-linejoin="round"/>')


# The wizard spans y 52..502 around x 256 on a 512 canvas; draw order = back to front.
WIZARD = f"""
  <defs><clipPath id="hat"><path d="{HAT}"/></clipPath></defs>
  <circle cx="256" cy="318" r="92" fill="{SKIN}" stroke="{OUTLINE}" stroke-width="12"/>
  <circle cx="196" cy="352" r="15" fill="{CHEEK}"/>
  <circle cx="316" cy="352" r="15" fill="{CHEEK}"/>
  <ellipse cx="220" cy="332" rx="12" ry="17" fill="{OUTLINE}"/>
  <ellipse cx="292" cy="332" rx="12" ry="17" fill="{OUTLINE}"/>
  <circle cx="224" cy="325" r="4.5" fill="#fff"/>
  <circle cx="296" cy="325" r="4.5" fill="#fff"/>
  <circle cx="256" cy="356" r="17" fill="{NOSE}" stroke="{OUTLINE}" stroke-width="8"/>
  <path d="M170 350 C160 430 196 496 256 502 C316 496 352 430 342 350 C320 376 292 384 256 384 C220 384 192 376 170 350 Z"
        fill="#fff" stroke="{OUTLINE}" stroke-width="12" stroke-linejoin="round"/>
  <ellipse cx="231" cy="386" rx="31" ry="15" transform="rotate(-12 231 386)" fill="#fff" stroke="{OUTLINE}" stroke-width="9"/>
  <ellipse cx="281" cy="386" rx="31" ry="15" transform="rotate(12 281 386)" fill="#fff" stroke="{OUTLINE}" stroke-width="9"/>
  <path d="{HAT}" fill="{VIOLET}"/>
  <g clip-path="url(#hat)">
    <rect x="100" y="206" width="320" height="42" fill="{GOLD}"/>
    <path d="M100 206H420M100 248H420" stroke="{OUTLINE}" stroke-width="8"/>
  </g>
  <path d="{HAT}" fill="none" stroke="{OUTLINE}" stroke-width="12" stroke-linejoin="round"/>
  {sparkle(246, 148, 22, GOLD)}
  {sparkle(294, 176, 12, GOLD, 5)}
  <circle cx="214" cy="178" r="6" fill="{GOLD}" stroke="{OUTLINE}" stroke-width="4"/>
  <ellipse cx="256" cy="262" rx="150" ry="30" fill="{VIOLET_DARK}" stroke="{OUTLINE}" stroke-width="12"/>
"""


def art(scale):
    """The wizard, scaled and centred on (256, 256)."""
    return f'<g transform="translate(256 256) scale({scale}) translate(-256 -277)">{WIZARD}</g>'


def build_svg(kind):
    """The whole icon as SVG text: kind is "any", "maskable" or "apple"."""
    head = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">'
    title = "<title>Meal Magician: a cartoon wizard</title>"
    if kind == "any":
        background = f'<rect x="12" y="12" width="488" height="488" rx="112" fill="{GOLD}" stroke="{OUTLINE}" stroke-width="14"/>'
        stars = sparkle(86, 112, 28, "#fff") + sparkle(430, 150, 17, "#fff", 5) + sparkle(436, 408, 22, "#fff")
        scale = 0.9
    elif kind == "maskable":
        background = f'<rect width="512" height="512" fill="{GOLD}"/>'
        stars = sparkle(150, 160, 20, "#fff", 5) + sparkle(372, 168, 13, "#fff", 4) + sparkle(380, 352, 16, "#fff", 5)
        scale = 0.66
    else:
        background = f'<rect width="512" height="512" fill="{GOLD}"/>'
        stars = sparkle(66, 92, 30, "#fff") + sparkle(448, 130, 18, "#fff", 5) + sparkle(452, 414, 24, "#fff")
        scale = 0.86
    return head + title + background + stars + art(scale) + "</svg>\n"


def render(svg_text, size):
    """Rasterises SVG text to a square RGBA image."""
    data = bytes(resvg_py.svg_to_bytes(svg_string=svg_text, width=size, height=size))
    return Image.open(io.BytesIO(data)).convert("RGBA")


def main():
    ICONS.mkdir(parents=True, exist_ok=True)
    tile, maskable, apple = build_svg("any"), build_svg("maskable"), build_svg("apple")
    (ICONS / "favicon.svg").write_text(tile, encoding="utf-8", newline="\n")
    render(tile, 512).save(ICONS / "icon-512.png")
    render(tile, 192).save(ICONS / "icon-192.png")
    render(maskable, 512).save(ICONS / "icon-maskable-512.png")
    render(apple, 180).convert("RGB").save(ICONS / "apple-touch-icon.png")
    render(tile, 256).save(STATIC / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    print(f"Wrote the icons into {STATIC}")


if __name__ == "__main__":
    main()
