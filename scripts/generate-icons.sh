#!/usr/bin/env bash
# Regenerates the placeholder PWA icons in src/main/resources/static/icons/ with ImageMagick.
# The PNGs are committed, so nobody needs to run this unless the artwork changes (PLAN.md D5;
# real artwork is Phase 5 "PWA polish"). Needs `magick` (ImageMagick 7) or `convert` (v6).
#
# Artwork on a 512 x 512 canvas: a white plate (ring) and a yellow four-point sparkle on the
# brand green (#15803d, the same as the manifest's theme_color).
#   icon-192.png / icon-512.png   "any" purpose: rounded square with transparent corners
#   icon-maskable-512.png         full-bleed square; the artwork stays inside the central 80 %
#                                 "safe zone" so Android can crop it to a circle or squircle
#   apple-touch-icon.png (180)    iOS ignores the manifest; it wants an opaque square
set -euo pipefail

if command -v magick >/dev/null; then
  im=(magick)
elif command -v convert >/dev/null; then
  im=(convert)
else
  echo "ImageMagick (magick or convert) is required." >&2
  exit 1
fi

cd "$(dirname "${BASH_SOURCE[0]}")/../src/main/resources/static/icons"

green='#15803d'
art=$(mktemp --suffix=.png)
trap 'rm -f "$art"' EXIT

# Artwork only (transparent background), drawn for the maskable safe zone: everything lies
# within a circle of radius 180 around the centre (256, 256).
"${im[@]}" -size 512x512 xc:none \
  -fill none -stroke white -strokewidth 28 -draw 'circle 256,256 256,106' \
  -fill white -stroke none -draw 'circle 256,256 256,176' \
  -fill '#facc15' -draw 'polygon 256,150 274,238 362,256 274,274 256,362 238,274 150,256 238,238' \
  "$art"

# Maskable: opaque green, artwork unchanged.
"${im[@]}" -size 512x512 "xc:$green" "$art" -composite icon-maskable-512.png

# "Any": rounded square, artwork enlarged slightly because nothing gets cropped.
"${im[@]}" -size 512x512 xc:none -fill "$green" -draw 'roundrectangle 0,0 511,511 96,96' \
  \( "$art" -resize 118% -gravity center -background none -extent 512x512 \) -composite icon-512.png
"${im[@]}" icon-512.png -resize 192x192 icon-192.png

# iOS: opaque square (iOS rounds the corners itself).
"${im[@]}" -size 512x512 "xc:$green" \
  \( "$art" -resize 118% -gravity center -background none -extent 512x512 \) -composite \
  -resize 180x180 apple-touch-icon.png

echo "Wrote the icons into $(pwd)"
