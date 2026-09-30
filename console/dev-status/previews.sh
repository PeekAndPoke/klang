#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Preview images for the cards on the Dev status page (/dev-status): a screenshot of each page,
# taken with headless Chrome at 1600x900 and scaled to 800x450 JPEG. Run after build.py when a
# page changed its look; the images are committed with the pages.
#
#   console/dev-status/previews.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PAGES="$ROOT/src/jsMain/resources"
OUT="$PAGES/images/dev-status"
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

# image name = page file under src/jsMain/resources
for entry in topic-map=klang-topic-map.html mission-log=klang-mission-log.html \
  whitepaper=klang-whitepaper.html blog=blog/index.html; do
  page="${entry%%=*}"
  file="${entry#*=}"
  "$CHROME" --headless=new --disable-gpu --no-sandbox --hide-scrollbars --virtual-time-budget=6000 \
    --window-size=1600,900 --screenshot="$TMP/$page.png" "file://$PAGES/$file" >/dev/null 2>&1
  convert "$TMP/$page.png" -resize 800x450 -strip -quality 82 "$OUT/$page.jpg"
  echo "wrote src/jsMain/resources/images/dev-status/$page.jpg ($(( $(stat -c %s "$OUT/$page.jpg") / 1024 )) KB)"
done
