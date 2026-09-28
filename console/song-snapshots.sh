#!/usr/bin/env bash
#
# The song axis of the optimization blog series (docs/plans/blog-optimization-series.md, P16):
# extracts the text of a builtin song at every tag given (or the default list) into a directory
# and runs the `snapshots` suite of the song benchmark on it, so every snapshot renders on ONE
# engine and the census columns show the song's work growing while the engine stays put.
#
# Usage: ./console/song-snapshots.sh [tag ...]
#   default tags: v0.3.8.2 v0.3.8.3 v0.3.9 v0.3.10 v0.3.11 v0.3.12 v0.3.13 v0.3.14 HEAD
#   (texts before v0.3.8.2 do not parse on today's doors; the July 3 snapshot left FrozenSongs on
#   2026-09-25 and is in git history, src/jvmMain/kotlin/FrozenSongs.kt at 0cc351a2)
#
set -euo pipefail
cd "$(dirname "$0")/.."

SONG=src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt
DIR=$(mktemp -d)
TAGS=("$@")
if [ ${#TAGS[@]} -eq 0 ]; then
  TAGS=(v0.3.8.2 v0.3.8.3 v0.3.9 v0.3.10 v0.3.11 v0.3.12 v0.3.13 v0.3.14 HEAD)
fi

i=0
for t in "${TAGS[@]}"; do
  src=$(git show "$t:$SONG")
  rpm=$(printf '%s\n' "$src" | sed -n -E 's/^ *rpm *= *([0-9.]+),?.*/\1/p' | head -1)
  date=$(git log -1 --format=%ad --date=short "$t")
  # the song text is the raw string after `code = """` up to the last `"""`
  printf '%s\n' "$src" | awk '
    /code = """/ { inside = 1; sub(/.*code = """/, ""); if ($0 != "") print; next }
    inside { lines[n++] = $0 }
    END { last = 0; for (k = 0; k < n; k++) if (lines[k] ~ /"""/) last = k; for (k = 0; k < last; k++) print lines[k] }
  ' > "$DIR/$(printf '%02d' $i)__${rpm}__${t} (${date}).klang"
  i=$((i + 1))
done

ls -1 "$DIR"
KLANG_SNAPSHOT_DIR="$DIR" console/with-build-lock.sh ./gradlew -q runSongBenchmark --args=snapshots
rm -rf "$DIR"
