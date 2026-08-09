#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Serialize builds, tests and anything else that must not run twice at once.
#
# WHY: two concurrent Gradle invocations corrupt the sprudel KSP cache (recovery:
# `./gradlew :sprudel:clean`). And during a mutation-check campaign the hazard is worse than a
# corrupt cache — if one agent edits production code while another runs a test, the second agent's
# verdict is about code it never saw. A green that should be red is indistinguishable from a
# toothless test. The lock is what makes a verdict mean something.
#
# USAGE
#   console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests some.Spec
#   console/with-build-lock.sh bash -c 'cmd1 && cmd2'         # a whole critical section
#
# For a MUTATION CHECK the critical section is mutate -> build -> restore, NOT just the build.
# Wrap all three, or (preferred) let a single owner do the whole campaign serially.
#
# ENV
#   KLANG_LOCK_TIMEOUT   seconds to wait for the lock (default 900; 0 = fail immediately)
#   KLANG_LOCK_FILE      lock file path (default <repo>/.claude/build.lock)
#
# EXIT CODES
#   the wrapped command's exit code, or 75 (EX_TEMPFAIL) if the lock could not be acquired.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_FILE="${KLANG_LOCK_FILE:-$REPO_ROOT/.claude/build.lock}"
TIMEOUT="${KLANG_LOCK_TIMEOUT:-900}"

if [ $# -eq 0 ]; then
    echo "usage: $(basename "$0") <command> [args...]" >&2
    exit 64
fi

mkdir -p "$(dirname "$LOCK_FILE")"

exec 9>"$LOCK_FILE"

if ! flock --exclusive --wait "$TIMEOUT" 9; then
    echo "BUILD LOCK: could not acquire $LOCK_FILE within ${TIMEOUT}s." >&2
    echo "Another agent or shell is building. Current holder:" >&2
    cat "$LOCK_FILE" >&2 2>/dev/null || true
    exit 75
fi

# Record who holds it, so a human looking at a stuck build can see what is running.
# Truncate first: the fd is shared, and a shorter record must not leave the old tail behind.
: >&9
printf 'pid=%s\nsince=%s\ncmd=%s\n' "$$" "$(date -Iseconds)" "$*" >&9

status=0
"$@" || status=$?

# The lock releases when fd 9 closes on exit. Blank the record so a stale holder line
# does not mislead the next reader of the file.
: >&9

exit "$status"
