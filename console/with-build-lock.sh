#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
#   KLANG_LOCK_FILE      lock file path (default: the MAIN checkout's .claude/build.lock, found through git, so a
#                        git worktree shares the one lock with the main checkout and every other worktree)
#
# EXIT CODES
#   the wrapped command's exit code, or 75 (EX_TEMPFAIL) if the lock could not be acquired.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The main checkout, also when this script runs from a git worktree (2026-10-19): git's common dir is the main
# checkout's .git, for every worktree alike. Before, a worktree locked a file of its own and serialized against nobody.
# Outside a git checkout, the script's own checkout.
COMMON_DIR="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
MAIN_ROOT="$REPO_ROOT"

if [ -n "$COMMON_DIR" ]; then
    MAIN_ROOT="$(dirname "$COMMON_DIR")"
fi

LOCK_FILE="${KLANG_LOCK_FILE:-$MAIN_ROOT/.claude/build.lock}"
TIMEOUT="${KLANG_LOCK_TIMEOUT:-900}"

if [ $# -eq 0 ]; then
    echo "usage: $(basename "$0") <command> [args...]" >&2
    exit 64
fi

mkdir -p "$(dirname "$LOCK_FILE")"

# Open for APPEND: `>` would truncate on open, so every waiting agent would wipe the holder's record.
exec 9>>"$LOCK_FILE"

if ! flock --exclusive --wait "$TIMEOUT" 9; then
    echo "BUILD LOCK: could not acquire $LOCK_FILE within ${TIMEOUT}s." >&2
    echo "Another agent or shell is building. Current holder:" >&2
    cat "$LOCK_FILE" >&2 2>/dev/null || true
    exit 75
fi

# Record who holds it, so a human looking at a stuck build can see what is running.
# Truncate first, by path: `: >&9` writes nothing and truncates nothing on an open fd, and a
# shorter record must not leave the old tail behind. The fd appends, so it writes from 0.
truncate -s 0 "$LOCK_FILE"
printf 'pid=%s\nsince=%s\ncmd=%s\n' "$$" "$(date -Iseconds)" "$*" >&9

status=0
"$@" || status=$?

# The lock releases when fd 9 closes on exit. Blank the record so a stale holder line
# does not mislead the next reader of the file. Never let a failed blank mask the command's status.
truncate -s 0 "$LOCK_FILE" || true

exit "$status"
