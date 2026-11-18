#!/bin/bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Deploys notstrom's production build (KLANG-00465) to notstrom.band as a versioned release, as
# console/deploy-finzo.sh does for the studio.
#
# Each deploy lands in its own directory named after the version and the git commit, and the `current` symlink
# points at the release that is live:
#
#   notstrom/versions/v<version>-<git hash>/   one directory per deployed commit, e.g. v0.8.15-4f5502c8
#   notstrom/current -> versions/v<version>-<git hash>
#
# The domain's document root is /www/htdocs/w0057ac0/klang/notstrom/current (set in the host's settings). Setting it
# there may create `current` as an empty directory; the script refuses to replace a real directory, so remove it once
# (`rmdir`) before the first deploy.
#
# The site is behind basic auth: the release's .htaccess (apps/notstrom/src/jsMain/resources/.htaccess) asks for a
# user of notstrom/.htpasswd, which this script writes on every deploy, outside the releases, so a rollback keeps it.
# It is a soft gate for showing the band to friends before it is public, not a secret: the default user and password
# are written here. NOTSTROM_AUTH_USER and NOTSTROM_AUTH_PASSWORD give others.
#
# The last steps fetch the live version.json with the user and password and fail loudly if the site does not serve
# the release just deployed, and check that the site answers 401 without them. The newest KEEP_VERSIONS releases are
# kept; older ones are deleted, never the one `current` points at.
#
# Run after console/build-notstrom.sh. The distribution folder holds the page, the bundle, the band's figures, the
# worklet, version.json and the .htaccess, so it is the one thing uploaded and the one thing checked. NOTSTROM_DIST
# names another folder.

set -euo pipefail

HOST="finzo"
BASE="/www/htdocs/w0057ac0/klang/notstrom"
SITE="https://notstrom.band"
KEEP_VERSIONS=10

# The soft gate (see above)
AUTH_USER="${NOTSTROM_AUTH_USER:-sound}"
AUTH_PASSWORD="${NOTSTROM_AUTH_PASSWORD:-first}"

# They go to curl in a quoted config line (see the last check): a quote or a backslash would break it, and a colon in
# the user would end it early (user:password)
case "$AUTH_USER$AUTH_PASSWORD" in
    *\"* | *\\*)
        echo "NOTSTROM_AUTH_USER and NOTSTROM_AUTH_PASSWORD must not hold a quote or a backslash." >&2
        exit 64
        ;;
esac

case "$AUTH_USER" in
    *:*)
        echo "NOTSTROM_AUTH_USER must not hold a colon." >&2
        exit 64
        ;;
esac

cd "$(dirname "$0")/.."
echo "Working in $(pwd)"

# Where this user's build outputs live when the per-user layout is on (MC-00031): as in console/build-notstrom.sh.
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

if [ -n "${NOTSTROM_DIST:-}" ]; then
    DIST="$NOTSTROM_DIST"
elif [ -n "$ROOT" ]; then
    DIST="$ROOT/apps/notstrom/dist/js/productionExecutable"
else
    DIST="./apps/notstrom/build/dist/js/productionExecutable"
fi

echo "Distribution: $DIST"

# Without its .htaccess the release would be public
if [ ! -f "$DIST/.htaccess" ]; then
    echo "No .htaccess in $DIST: run console/build-notstrom.sh first." >&2
    exit 1
fi

# The .htaccess must ask for the .htpasswd this script writes: another path would lock everyone out, or let anyone in
AUTH_FILE="$(sed -n 's/^[[:space:]]*AuthUserFile[[:space:]]\{1,\}//p' "$DIST/.htaccess" | head -1)"
if [ "$AUTH_FILE" != "$BASE/.htpasswd" ]; then
    echo "The .htaccess asks for '$AUTH_FILE', but this script writes $BASE/.htpasswd: refusing." >&2
    exit 1
fi

# The release is named after what was built: the distribution's version.json carries the version and the git
# revision it was built at, and `-dirty` when the tree had uncommitted changes.
HASH="$(sed -n 's/.*"gitRev": *"\([0-9a-f]*\)".*/\1/p' "$DIST/version.json" 2>/dev/null || true)"
if [ -z "$HASH" ]; then
  echo "No gitRev in $DIST/version.json: run console/build-notstrom.sh first." >&2
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

# rsync sends only what differs, and --delete makes the release directory exactly the distribution (the .htaccess
# with it). --chmod sets the modes the web server needs, whatever the local ones are (the build tree has no
# permissions for "other", and the server could not read an .htaccess so: 2026-11-13, the studio's deploy).
echo "Uploading the distribution"
rsync -rltz --delete --chmod=D755,F644 "$DIST"/ "$HOST:$RELEASE/"

# The password's hash is made here (openssl's apr1, which Apache reads) and sent on stdin: the password never stands
# in a command line on the server. Written beside the releases, renamed into place in one step.
echo "Writing $BASE/.htpasswd for user $AUTH_USER"
HTPASSWD_LINE="$AUTH_USER:$(printf '%s' "$AUTH_PASSWORD" | openssl passwd -apr1 -stdin)"
printf '%s\n' "$HTPASSWD_LINE" | ssh "$HOST" "cat > '$BASE/.htpasswd.new' && chmod 644 '$BASE/.htpasswd.new' \
  && mv -f '$BASE/.htpasswd.new' '$BASE/.htpasswd'"

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

echo "Checking that $SITE serves the new release (with the user and password)"
# the user and password go to curl on stdin (-K -), not on its command line, where the machine's process list shows them
LIVE_REV="$(printf 'user = "%s:%s"\n' "$AUTH_USER" "$AUTH_PASSWORD" | curl -fsS -K - "$SITE/version.json?nocache=$HASH" \
  | sed -n 's/.*"gitRev": *"\([0-9a-f]*\)".*/\1/p' || true)"
if [ -z "$LIVE_REV" ] || [ "${HASH#"$LIVE_REV"}" = "$HASH" ]; then
  echo "WARNING: $SITE serves '${LIVE_REV:-nothing readable}', not $HASH." >&2
  echo "Is the domain's document root $BASE/current? (or a cache still holds the old version.json)" >&2
  exit 2
fi

echo "Checking that $SITE asks for the password without it"
STATUS="$(curl -sS -o /dev/null -w '%{http_code}' "$SITE/?nocache=$HASH" || true)"
if [ "$STATUS" != "401" ]; then
  echo "WARNING: $SITE answers $STATUS without the password, not 401: is the .htaccess read?" >&2
  exit 3
fi

echo "Done: $SITE is versions/$NAME"
