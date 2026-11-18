#!/bin/bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Deploys the studio's production build (apps/studio) to klang.finzo.de as a versioned release.
#
# Each deploy lands in its own directory named after the version and the git commit, and the `current` symlink
# points at the release that is live:
#
#   studio/versions/v<version>-<git hash>/   one directory per deployed commit, e.g. v0.5.3-4f5502c8c683
#   studio/current -> versions/v<version>-<git hash>
#
# The server folder is /www/htdocs/w0057ac0/klang/studio since KLANG-00465 (the maintainer, 2026-11-18), beside
# notstrom's; it was /www/htdocs/w0057ac0/finzo/klang.finzo.de before. The site stays klang.finzo.de: the host's
# document root for it must be switched ONCE to $BASE/current (in the host settings), as it was switched to the old
# place's current on 2026-10-07. The last step fetches the live version.json and fails loudly if the site does not
# serve the release just deployed, so a host still pointing at the old folder cannot go unnoticed. Setting the
# document root in the hosting panel may create `current` as an empty directory; the script refuses to replace a real
# directory, so remove it once (`rmdir`) before the first deploy (the host follows the symlink).
#
# A removed page or post is really gone in the next release, and a rollback is one symlink. A browser tab left open
# across a deploy loses the old release's on-demand chunks (webpack's ChunkLoadError) until it is reloaded. The newest
# KEEP_VERSIONS releases are kept; older ones are deleted, never the one `current` points at.
#
# Run after console/build-studio.sh. The distribution folder holds the bundle AND a copy of every file in
# apps/studio/src/jsMain/resources, the band's figures, the worklet and the version.json the build wrote, so it is
# the one thing uploaded and the one thing checked. It is the build directory of the user who runs this (the
# per-user layout, MC-00031: apps/studio/build/ for the maintainer, .build-claude/apps/studio/ for the agents).

set -euo pipefail

HOST="finzo"
BASE="/www/htdocs/w0057ac0/klang/studio"
SITE="https://klang.finzo.de"
KEEP_VERSIONS=10

cd "$(dirname "$0")/.."
echo "Working in $(pwd)"

# Where this user's build outputs live when the per-user layout is on (MC-00031): as in console/build-studio.sh.
build_root() {
    local value user

    value=${ORG_GRADLE_PROJECT_buildRoot:-$(sed -n 's/^[[:space:]]*buildRoot[[:space:]]*=[[:space:]]*//p' gradle.properties 2>/dev/null | head -1)}

    if [ -n "$value" ]; then
        printf '%s\n' "./$value"
        return 0
    fi

    user=$(id -un)

    if [ -d "./.build-$user" ] && [ -O "./.build-$user" ]; then
        printf '%s\n' "./.build-$user"
    fi
}

ROOT=$(build_root)

if [ -n "$ROOT" ]; then
    DIST="$ROOT/apps/studio/dist/js/productionExecutable"
else
    DIST="./apps/studio/build/dist/js/productionExecutable"
fi

echo "Distribution: $DIST"

# The release is named after what was built: the distribution's version.json carries the version and the git
# revision it was built at, and `-dirty` when the tree had uncommitted changes (maintainer, 2026-10-07: deploying
# a build with uncommitted changes is allowed; its name says so).
HASH="$(sed -n 's/.*"gitRev": *"\([0-9a-f]*\)".*/\1/p' "$DIST/version.json" 2>/dev/null || true)"
if [ -z "$HASH" ]; then
  echo "No gitRev in $DIST/version.json: run console/build-studio.sh first." >&2
  exit 1
fi

VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$DIST/version.json")"
if [ -z "$VERSION" ]; then
  echo "No version in $DIST/version.json." >&2
  exit 1
fi

NAME="v$VERSION-$HASH"
if grep -q '"gitDesc": *"[^"]*-dirty"' "$DIST/version.json"; then
  NAME="$NAME-dirty"
fi

HEAD_HASH="$(git rev-parse --short=8 HEAD)"
if [ "$HEAD_HASH" != "$HASH" ]; then
  echo "Note: the build is from $HASH, HEAD is $HEAD_HASH."
fi

RELEASE="$BASE/versions/$NAME"

echo "Release $NAME -> $HOST:$RELEASE"
ssh "$HOST" "mkdir -p '$RELEASE' && touch '$RELEASE'"

# rsync sends only what differs, so a repeated deploy of the same release is quick, and --delete makes the release
# directory exactly the distribution. --chmod sets the modes the web server needs, whatever the local ones are: the
# build tree here has no permissions for "other" (files 660, directories 770), and scp carried those over, so the
# server could not read .htaccess and answered 403 (2026-11-13).
echo "Uploading the distribution"
rsync -rltz --delete --chmod=D755,F644 "$DIST"/ "$HOST:$RELEASE/"

# Swap the link in one step (a new link renamed over the old one), and refuse if `current` is a real directory.
echo "Switching current to versions/$NAME"
ssh "$HOST" "cd '$BASE' && if [ -e current ] && [ ! -L current ]; then echo 'current is not a symlink, refusing' >&2; exit 1; fi \
  && ln -sfn 'versions/$NAME' current.new && mv -T current.new current"

echo "Keeping the newest $KEEP_VERSIONS releases"
ssh "$HOST" "cd '$BASE' && live=\"\$(basename \"\$(readlink current)\")\" && cd versions && ls -1t | tail -n +$((KEEP_VERSIONS + 1)) | while read -r old; do
  if [ \"\$old\" != \"\$live\" ]; then
    echo \"  removing \$old\"
    rm -rf -- \"\$old\"
  fi
done"

echo "Checking that $SITE serves the new release"
LIVE_REV="$(curl -fsS "$SITE/version.json?nocache=$HASH" | sed -n 's/.*"gitRev": *"\([0-9a-f]*\)".*/\1/p' || true)"
if [ -z "$LIVE_REV" ] || [ "${HASH#"$LIVE_REV"}" = "$HASH" ]; then
  echo "WARNING: $SITE serves '${LIVE_REV:-nothing readable}', not $HASH." >&2
  echo "Is the host's document root $BASE/current? (or a cache still holds the old version.json)" >&2
  exit 2
fi

echo "Done: $SITE is versions/$NAME"
