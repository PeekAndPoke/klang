#!/usr/bin/env bash
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# The full check: every test, the corpus, the timing benchmarks, the allocation check. It runs before a release or
# a merge, not on every build. If CI comes later, this script is the CI job.
#
# Four steps, each in its own console/with-build-lock.sh call (so a waiting agent waits for one step, not for the
# whole run):
#   tests   ./gradlew allTests :klangscript-ksp:test --continue
#   corpus  ./gradlew runSongBenchmark --args=corpus              (src/jvmMain/kotlin/CorpusCheck.kt)
#           (red when a frozen text or an unedited live song sounds different; an edited live song is information)
#   timing  console/run-dsp-benchmarks.sh                         (no budgets: never red on its numbers)
#   alloc   the production node bundle (relinked, so its time is fresh), then
#           KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun
#           (audio_benchmark/src/jvmMain/kotlin/AllocationCheck.kt, budgets in audio_benchmark/alloc-budgets.txt)
#           A red alloc step on spluck-a may be a known tail: the "Known tails" paragraph in that file.
#
# Everything the summary reads is taken inside a lock call: the test XML inside the tests step's own call, the
# tree's state before inside the first call and after inside the last one. Another agent's build between two steps
# can therefore neither replace this run's test results nor look like a change of the tree.
#
# The tree guard: HEAD, and every path `git status --porcelain` lists with its content hash. If the state after
# differs from the state before, the run is red: its results would mix two states of the tree. Only what the run
# writes itself is left out: docs/benchmarks/alloc-scoreboard.md and the timing step's dated files.
#
# The test XML is searched only in the root's and the top-level modules' build dirs, never in a nested checkout or
# worktree (.claude/worktrees/..., or any top-level folder holding a .git).
#
# Needs Linux tools: flock (console/with-build-lock.sh), GNU sed, sha1sum.
#
# USAGE
#   console/full-check.sh            Gradle skips the test tasks whose inputs did not change
#   console/full-check.sh --fresh    every test task runs again: the old test XML in the build dirs is deleted first
#
# OUTPUT
#   one log per step and the summary under tmp/full-check/<date>_<time>/, the summary also on stdout
#
# EXIT CODES
#   0 every step green; 1 red (a failed test, a changed song, an allocation over budget, a step that failed, could
#   not get the lock or printed no result line, a tree that changed during the run); 64 bad usage.

set -uo pipefail
cd "$(dirname "$0")/.."

FRESH=0

for arg in "$@"; do
    case "$arg" in
        --fresh) FRESH=1 ;;
        *)
            echo "usage: $(basename "$0") [--fresh]" >&2
            exit 64
            ;;
    esac
done

# A mode or case exported in this shell would turn the timing run into an allocation run, or the reverse.
unset KLANG_BENCH_MODE KLANG_BENCH_FILTER KLANG_ALLOC_CASE KLANG_ALLOC_WARM KLANG_ALLOC_COUNT

LOCK=console/with-build-lock.sh
STAMP=$(date +%Y-%m-%d_%H%M%S)
# tmp/, not build/: build/ belongs to the maintainer's IDE, the agents build elsewhere (MC-00031)
LOGDIR=tmp/full-check/$STAMP
mkdir -p "$LOGDIR"

# The state of the tree: HEAD, then every listed path with its content hash. `-z` keeps paths unquoted (a space or a
# non-ASCII byte), and a rename's source path is read past.
tree_state() {
    git rev-parse HEAD
    git status --porcelain -z -uall -- . \
        ':(exclude)docs/benchmarks/alloc-scoreboard.md' \
        ':(exclude,glob)docs/benchmarks/[0-9]*_jvm.md' \
        ':(exclude,glob)docs/benchmarks/[0-9]*_nodejs.md' \
        ':(exclude,glob)docs/benchmarks/[0-9]*_compare.md' |
        while IFS= read -r -d '' entry; do
            case "$entry" in
                R* | C*) IFS= read -r -d '' _ ;;
            esac

            printf '%s %s\n' "$entry" "$(git hash-object -- "${entry:3}" 2>/dev/null)"
        done | sha1sum
}

# Where this user's build outputs live when the per-user layout is on (MC-00031): buildRoot from
# ORG_GRADLE_PROJECT_buildRoot or the project's gradle.properties, else ./.build-<user> when it exists and belongs to
# the user running this (as test-digest's build_root(); never ~/.gradle/gradle.properties, which agents do not read).
# Empty for the plain <module>/build/ layout.
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

# The test XML files of this tree, with extra find arguments (-delete, -print0): only this user's, so a run never
# counts another user's results.
test_xml() {
    local dirs=() dir root

    root=$(build_root)

    if [ -n "$root" ]; then
        find "$root" -path '*/test-results/*' -name '*.xml' -type f "$@"
        return 0
    fi

    if [ -d ./build ]; then
        dirs+=(./build)
    fi

    for dir in ./*/; do
        if [ -e "${dir}.git" ]; then
            continue
        fi

        if [ -d "${dir}build" ]; then
            dirs+=("${dir}build")
        fi
    done

    if [ "${#dirs[@]}" = 0 ]; then
        return 0
    fi

    find "${dirs[@]}" -path '*/test-results/*' -name '*.xml' -type f "$@"
}

export LOGDIR FRESH
export -f tree_state build_root test_xml

duration() {
    printf '%dm %02ds' $(($1 / 60)) $(($1 % 60))
}

thousands() {
    echo "$1" | sed ':a;s/\B[0-9]\{3\}\>/,&/;ta'
}

declare -A RC SECS

# step <name> <command...>: runs the command with its output in the step's log, records exit code and wall time.
step() {
    local name=$1
    shift
    local start
    start=$(date +%s)

    echo "== $name (log: $LOGDIR/$name.log)"
    "$@" > "$LOGDIR/$name.log" 2>&1
    RC[$name]=$?
    SECS[$name]=$(($(date +%s) - start))
    echo "   exit ${RC[$name]}, $(duration "${SECS[$name]}")"
}

DESCRIBE=$(git describe --always --dirty)
STARTED=$(date '+%Y-%m-%d %H:%M')
START=$(date +%s)

step tests $LOCK bash -c '
    tree_state > "$LOGDIR/tree.before"

    if [ "$FRESH" = 1 ]; then
        test_xml -delete || exit 1
    fi

    ./gradlew allTests :klangscript-ksp:test --continue
    rc=$?
    test_xml -print0 | xargs -0 -r grep -ho "<testsuite [^>]*>" > "$LOGDIR/tests.suites"
    exit $rc'

step corpus $LOCK ./gradlew runSongBenchmark --args=corpus -q
step timing $LOCK console/run-dsp-benchmarks.sh

step alloc $LOCK bash -c '
    ./gradlew :audio_benchmark:compileProductionExecutableKotlinJs --rerun -q &&
        KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun -q
    rc=$?
    tree_state > "$LOGDIR/tree.after"
    exit $rc'

TOTAL=$(($(date +%s) - START))

# ---- the verdicts -------------------------------------------------------------------------------------------------

RED=0
WORSE=()
LINES=()

# verdict <name> <word> <detail>: one summary line.
verdict() {
    LINES+=("$(printf '  %-7s %-6s %-50s %s' "$1" "$2" "$3" "$(duration "${SECS[$1]}")")")
}

# Why a step without its result line is red.
why_red() {
    if [ "${RC[$1]}" = 75 ]; then
        echo "could not get the build lock"
    elif [ "${RC[$1]}" = 0 ]; then
        echo "exit 0 but no result line, see $1.log"
    else
        echo "exit ${RC[$1]}, see $1.log"
    fi
}

# tests: counts and failed classes from the test XML read inside the tests step's lock call.
SUITES=$(cat "$LOGDIR/tests.suites" 2>/dev/null)
read -r TESTS FAILED <<< "$(echo "$SUITES" | awk '
    function attr(s, key,   m) { if (match(s, " " key "=\"[0-9]+\"")) { m = substr(s, RSTART, RLENGTH); gsub(/[^0-9]/, "", m); return m + 0 } return 0 }
    { t += attr($0, "tests"); f += attr($0, "failures") + attr($0, "errors") }
    END { print t + 0, f + 0 }')"
FAILED_CLASSES=$(echo "$SUITES" | awk '
    function attr(s, key,   m) { if (match(s, " " key "=\"[0-9]+\"")) { m = substr(s, RSTART, RLENGTH); gsub(/[^0-9]/, "", m); return m + 0 } return 0 }
    attr($0, "failures") + attr($0, "errors") > 0 { if (match($0, / name="[^"]+"/)) print substr($0, RSTART + 7, RLENGTH - 8) }')

if [ "${RC[tests]}" = 0 ] && [ "$FAILED" = 0 ] && [ "$TESTS" != 0 ]; then
    verdict tests GREEN "$(thousands "$TESTS") tests, 0 failed"
else
    RED=1

    if [ "$FAILED" != 0 ]; then
        verdict tests RED "$(thousands "$TESTS") tests, $(thousands "$FAILED") failed"

        while IFS= read -r cls; do
            if [ -n "$cls" ]; then
                LINES+=("          FAILED $cls")
                WORSE+=("tests $cls")
            fi
        done <<< "$FAILED_CLASSES"
    elif [ "${RC[tests]}" = 0 ]; then
        verdict tests RED "exit 0 but no test results found"
        WORSE+=("tests found no results")
    else
        verdict tests RED "$(why_red tests)"
        WORSE+=("tests did not run to the end")
    fi
fi

# corpus: green only with its own CORPUS GREEN line; the frozen texts that differ and the live songs the engine moved
# are red, the live songs whose text moved are listed as information (songs move freely).
CORPUS_LINE=$(grep -m1 '^CORPUS ' "$LOGDIR/corpus.log" | sed -E 's/^CORPUS [A-Z]+: //; s/ \([0-9]+ s\)$//')
CORPUS_INFO=$(grep -E '^INFO ' "$LOGDIR/corpus.log" | sed -E 's/^INFO +/information: live song /')

# The verdict column holds the frozen half; the live half goes on its own line, so the duration column stays aligned.
CORPUS_LIVE=""

if [[ "$CORPUS_LINE" == *"; "* ]]; then
    CORPUS_LIVE="${CORPUS_LINE#*; }"
    CORPUS_LINE="${CORPUS_LINE%%; *}"
fi

if [ "${RC[corpus]}" = 0 ] && grep -q '^CORPUS GREEN: ' "$LOGDIR/corpus.log"; then
    verdict corpus GREEN "$CORPUS_LINE"

    if [ -n "$CORPUS_LIVE" ]; then
        LINES+=("          $CORPUS_LIVE")
    fi

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            LINES+=("          $line")
        fi
    done <<< "$CORPUS_INFO"
else
    RED=1
    SONGS=$(grep -E '^(CHANGED|NEW|MISSING) ' "$LOGDIR/corpus.log")

    if grep -q '^CORPUS RED: ' "$LOGDIR/corpus.log"; then
        verdict corpus RED "$CORPUS_LINE"

        if [ -n "$CORPUS_LIVE" ]; then
            LINES+=("          $CORPUS_LIVE")
        fi
    else
        verdict corpus RED "$(why_red corpus)"
        WORSE+=("corpus did not run to the end")
    fi

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            LINES+=("          $line")
            WORSE+=("corpus $(echo "$line" | sed -E 's/^[A-Z]+ +//; s/ \|.*$//')")
        fi
    done <<< "$SONGS"

    if [ -z "$SONGS" ] && grep -q '^CORPUS RED: ' "$LOGDIR/corpus.log"; then
        WORSE+=("corpus: $CORPUS_LINE${CORPUS_LIVE:+; $CORPUS_LIVE}")
    fi

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            LINES+=("          $line")
        fi
    done <<< "$CORPUS_INFO"
fi

# timing: never red on its numbers; the compare file's red flags are information.
COMPARE=$(grep -m1 'Compare:' "$LOGDIR/timing.log" | sed -E 's/^.*Compare: *//')

if [ "${RC[timing]}" = 0 ] && [ -n "$COMPARE" ] && [ -f "$COMPARE" ]; then
    verdict timing DONE "$COMPARE"
    FLAGS=$(sed -n '/^## Red flags/,$ p' "$COMPARE" | grep -c '^- \*\*')

    if [ "$FLAGS" != 0 ]; then
        LINES+=("          information: the JVM is slower than node on $FLAGS of the compare file's rows")
    fi
else
    RED=1
    verdict timing RED "$(why_red timing)"
    WORSE+=("timing did not run to the end")
fi

# alloc: green only with its own RESULT GREEN line; every OVER, MISSING, UNKNOWN, DUPLICATE, BAD and FAIL line.
ALLOC_LINE=$(grep -m1 '^RESULT ' "$LOGDIR/alloc.log" | sed -E 's/^RESULT [A-Z]+: //')

if [ "${RC[alloc]}" = 0 ] && grep -q '^RESULT GREEN: ' "$LOGDIR/alloc.log"; then
    verdict alloc GREEN "$ALLOC_LINE"
else
    RED=1

    if grep -q '^RESULT RED: ' "$LOGDIR/alloc.log"; then
        verdict alloc RED "$ALLOC_LINE"
    else
        verdict alloc RED "$(why_red alloc)"
        WORSE+=("alloc did not run to the end")
    fi

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            LINES+=("          $line")
        fi
    done <<< "$(grep -E '^(OVER|MISSING|UNKNOWN|DUPLICATE|BAD|FAIL) ' "$LOGDIR/alloc.log")"

    while IFS= read -r line; do
        if [ -n "$line" ]; then
            WORSE+=("alloc $line")
        fi
    done <<< "$(grep -E '^OVER ' "$LOGDIR/alloc.log" | sed -E 's/^OVER ([^ ]+) ([a-z0-9]+):.*$/\1 on \2/')"

    if grep -qE '^(MISSING|UNKNOWN|DUPLICATE|BAD|FAIL) ' "$LOGDIR/alloc.log"; then
        WORSE+=("alloc could not check every case")
    fi
fi

# the tree guard: both states were taken inside a lock call.
if [ ! -s "$LOGDIR/tree.before" ] || [ ! -s "$LOGDIR/tree.after" ]; then
    RED=1
    LINES+=("  tree    RED    the tree's state was not taken before and after (a step did not get the lock)")
    WORSE+=("the tree guard could not run")
elif ! cmp -s "$LOGDIR/tree.before" "$LOGDIR/tree.after"; then
    RED=1
    LINES+=("  tree    RED    the tree changed during the run: the results mix two states")
    WORSE+=("the tree changed during the run")
fi

{
    echo "Klang full check, $STARTED, $DESCRIBE$([ "$FRESH" = 1 ] && echo ', --fresh')"
    echo "  logs    $LOGDIR"
    printf '%s\n' "${LINES[@]}"

    if [ "$RED" = 0 ]; then
        echo "GREEN. Total $(duration "$TOTAL")."
    elif [ "${#WORSE[@]}" = 0 ]; then
        echo "RED. What got worse: see the step logs in $LOGDIR. Total $(duration "$TOTAL")."
    else
        WHAT=$(printf '%s; ' "${WORSE[@]}")
        echo "RED. What got worse: ${WHAT%; }. Total $(duration "$TOTAL")."
    fi
} | tee "$LOGDIR/summary.txt"

exit "$RED"
