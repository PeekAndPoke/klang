#!/bin/bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Deploys the production build to klang.finzo.de as a versioned release.
#
# Each deploy lands in its own directory named after the version and the git commit, and the `current` symlink
# points at the release that is live:
#
#   klang.finzo.de/versions/v<version>-<git hash>/   one directory per deployed commit, e.g. v0.5.3-4f5502c8c683
#   klang.finzo.de/current -> versions/v<version>-<git hash>
#
# The host's document root must be klang.finzo.de/current (a one-time switch in the host settings, maintainer,
# 2026-10-07). The last step fetches the live version.json and fails loudly if the site does not serve the release
# just deployed, so a host still pointing at the old root cannot go unnoticed. Setting the document root in the
# hosting panel creates `current` as an empty directory; the script refuses to replace a real directory, so remove it
# once (`rmdir`) before the first deploy (happened on 2026-10-07; the host follows the symlink).
#
# A removed page or post is really gone in the next release, and a rollback is one symlink. A browser tab left open
# across a deploy loses the old release's on-demand chunks (webpack's ChunkLoadError) until it is reloaded. The newest
# KEEP_VERSIONS releases are kept; older ones are deleted, never the one `current` points at.
#
# Run after `./gradlew jsBrowserDistribution` on a clean, committed tree. The distribution folder holds the bundle
# AND a copy of every file in src/jsMain/resources, including the version.json the build wrote, so it is the one
# thing uploaded and the one thing checked.

set -euo pipefail

HOST="finzo"
BASE="/www/htdocs/w0057ac0/finzo/klang.finzo.de"
SITE="https://klang.finzo.de"
KEEP_VERSIONS=10
DIST="./build/dist/js/productionExecutable"

cd "$(dirname "$0")/.."
echo "Working in $(pwd)"

shopt -s dotglob

# The release must match its name: no uncommitted tracked change, nothing untracked in the resources the build copies.
if [ -n "$(git status --porcelain --untracked-files=no)" ] || [ -n "$(git status --porcelain -- src/jsMain/resources)" ]; then
  echo "The working tree has uncommitted or untracked changes: the release would not match its git hash." >&2
  exit 1
fi

HASH="$(git rev-parse --short=12 HEAD)"

# The build must come from this commit: the distribution's version.json carries the git revision it was built at.
BUILT_REV="$(sed -n 's/.*"gitRev": *"\([0-9a-f]*\)".*/\1/p' "$DIST/version.json" 2>/dev/null || true)"
if [ -z "$BUILT_REV" ] || [ "${HASH#"$BUILT_REV"}" = "$HASH" ]; then
  echo "The build is from '${BUILT_REV:-unknown}', HEAD is $HASH: run ./gradlew jsBrowserDistribution first." >&2
  exit 1
fi

VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$DIST/version.json")"
if [ -z "$VERSION" ]; then
  echo "No version in $DIST/version.json." >&2
  exit 1
fi

NAME="v$VERSION-$HASH"
RELEASE="$BASE/versions/$NAME"

# A build made on a dirty tree carries `-dirty` in its gitDesc: its code is not the commit it is named after.
if grep -q '"gitDesc": *"[^"]*-dirty"' "$DIST/version.json"; then
  echo "The build was made on a dirty tree: commit, then run ./gradlew jsBrowserDistribution again." >&2
  exit 1
fi

echo "Release $NAME -> $HOST:$RELEASE"
ssh "$HOST" "mkdir -p '$RELEASE' && touch '$RELEASE'"

echo "Uploading the distribution"
scp -r "$DIST"/* "$HOST:$RELEASE/"

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
