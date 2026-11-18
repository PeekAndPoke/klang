#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Builds both apps' production distributions (KLANG-00465): the audio worklet once, then the studio (apps/studio) and
# notstrom (apps/notstrom). The worklet is built first and not again: both apps lay the same production worklet beside
# their page.
#
# Three steps, each in its own console/with-build-lock.sh call (a waiting agent waits for one step, not for all):
#   worklet   ./gradlew :audio_jsworklet:clean :audio_jsworklet:jsBrowserProductionWebpack
#   studio    ./gradlew :apps:studio:jsBrowserDistribution
#   notstrom  ./gradlew :apps:notstrom:jsBrowserDistribution
#
# The distributions land in the build directory of the user who runs this (the per-user layout, MC-00031), as
# console/build-studio.sh and console/build-notstrom.sh say; the script prints both folders.
#
# USAGE
#   console/build-all.sh
#
# Then console/deploy-finzo.sh and console/deploy-notstrom.sh upload them.

set -euo pipefail
cd "$(dirname "$0")/.."

LOCK=console/with-build-lock.sh

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

# dist <app>: the app's production distribution folder in this user's layout
dist() {
    if [ -n "$ROOT" ]; then
        printf '%s\n' "$ROOT/apps/$1/dist/js/productionExecutable"
    else
        printf '%s\n' "./apps/$1/build/dist/js/productionExecutable"
    fi
}

echo "Building the audio worklet for production (cleaned first)"
"$LOCK" ./gradlew :audio_jsworklet:clean :audio_jsworklet:jsBrowserProductionWebpack

echo "Building the studio's production distribution"
"$LOCK" ./gradlew :apps:studio:jsBrowserDistribution

echo "Building notstrom's production distribution"
"$LOCK" ./gradlew :apps:notstrom:jsBrowserDistribution

for app in studio notstrom; do
    for f in index.html klang-worklet.js version.json .htaccess; do
        if [ ! -f "$(dist "$app")/$f" ]; then
            echo "The build ran, but $(dist "$app")/$f is not there: is this the build directory of the user who ran it?" >&2
            exit 1
        fi
    done
done

echo "Done: the studio's distribution is $(cd "$(dist studio)" && pwd)"
echo "      notstrom's distribution is $(cd "$(dist notstrom)" && pwd)"
