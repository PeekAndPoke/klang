#!/bin/bash
set -e

# Uploads the sample mirror to the static sample host.
#
# Serving url: https://klang-assets.finzo.de/samples/...
#
# Workflow:
#   1. ./gradlew runSampleMirror                    (delta-only: skips files already mirrored)
#   2. ./gradlew runSampleMirror --args="--verify"  (must report zero missing)
#   3. ./console/deploy-samples-finzo.sh
#
# NOTE: adjust TARGET if the vhost docroot for klang-assets.finzo.de differs.

SOURCE="$(cd "$(dirname "$0")/../../peekandpoke.github.io/klang" && pwd)/"
TARGET="finzo:/www/htdocs/w0057ac0/finzo/klang-assets.finzo.de/samples/"

echo "Uploading sample mirror: $SOURCE -> $TARGET"

if command -v rsync > /dev/null 2>&1; then
    # -a keeps timestamps so re-runs only transfer new files; includes dotfiles (.htaccess)
    rsync -av "$SOURCE" "$TARGET"
else
    # Fallback: full re-upload; dotglob so .htaccess is included
    shopt -s dotglob
    scp -r "$SOURCE"* "$TARGET"
fi
