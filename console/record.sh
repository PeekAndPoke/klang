#!/bin/bash
#
# Render sprudel code to a WAV file.
#
# Examples:
#
#   # Simple scale
#   ./console/record.sh --code 'note("c3 d3 e3 f3 g3 a3 b3 c4")' --cycles 4 --rpm 30 -o scale.wav
#
#   # Supersaw chord with reverb
#   ./console/record.sh --code 'note("c3 e3 g3 c4").sound("supersaw").roomWet(0.5)' --cycles 4 --rpm 60 -o chord.wav
#
#   # Render from a .sprudel file
#   ./console/record.sh --file my_song.sprudel --cycles 16 --rpm 120 -o song.wav
#
#   # Show all options
#   ./console/record.sh --help
#

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# the studio's CLI (apps/studio); Gradle runs it in the repository's root folder, so a relative --file or -o means
# what it says here
exec ./gradlew -q :apps:studio:runCli --args="klang:record:wav $*"
