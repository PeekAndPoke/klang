#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Builds notstrom's production distribution (KLANG-00465): the band's own app, apps/notstrom.
#
# Two steps, each in its own console/with-build-lock.sh call (a waiting agent waits for one step, not for both):
#   worklet   ./gradlew :audio_jsworklet:clean :audio_jsworklet:jsBrowserProductionWebpack
#             (cleaned first, so no worklet of an older build is laid beside the page)
#   notstrom  ./gradlew :apps:notstrom:jsBrowserDistribution
#             (the page, its bundle with a content hash in its name, the band's figures, the worklet, version.json and
#             the .htaccess, all in one folder)
#
# The distribution lands in the build directory of the user who runs this: apps/notstrom/build/ for the maintainer,
# .build-claude/apps/notstrom/ for the agents (the per-user layout, MC-00031). The script prints which.
#
# USAGE
#   console/build-notstrom.sh
#
# Then console/deploy-notstrom.sh uploads that folder.

set -euo pipefail
cd "$(dirname "$0")/.."

LOCK=console/with-build-lock.sh

# Where this user's build outputs live when the per-user layout is on (MC-00031): buildRoot from
# ORG_GRADLE_PROJECT_buildRoot or the project's gradle.properties, else ./.build-<user> when it exists and belongs to
# the user running this (as console/full-check.sh and test-digest find it; never ~/.gradle/gradle.properties, which
# agents do not read). Empty for the plain <module>/build/ layout.
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
    DIST="$ROOT/apps/notstrom/dist/js/productionExecutable"
else
    DIST="./apps/notstrom/build/dist/js/productionExecutable"
fi

echo "Building the audio worklet for production (cleaned first)"
"$LOCK" ./gradlew :audio_jsworklet:clean :audio_jsworklet:jsBrowserProductionWebpack

echo "Building notstrom's production distribution"
"$LOCK" ./gradlew :apps:notstrom:jsBrowserDistribution

for f in index.html klang-worklet.js version.json .htaccess; do
    if [ ! -f "$DIST/$f" ]; then
        echo "The build ran, but $DIST/$f is not there: is this the build directory of the user who ran it?" >&2
        exit 1
    fi
done

echo "Done: notstrom's distribution is $(cd "$DIST" && pwd)"
