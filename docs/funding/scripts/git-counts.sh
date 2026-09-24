#!/usr/bin/env bash
# Reproduces every git number used in docs/funding/. Run from anywhere inside the repo.
# Output is plain text; docs/funding/evidence/git-numbers.txt is one saved run.
#
# Caveat on keyword counts: a keyword-matched commit count is a PROXY for "how often we came back
# to this area", not an iteration count. The design-decisions skeleton names the concrete commits
# that mark each real iteration; these counts only size the area.

set -u
cd "$(git rev-parse --show-toplevel)"

echo "# git numbers, generated $(date -u +%Y-%m-%dT%H:%MZ) at $(git rev-parse --short HEAD) on $(git rev-parse --abbrev-ref HEAD)"
echo

echo "## totals"
echo "commits reachable from HEAD: $(git rev-list --count HEAD)"
echo "merge commits:               $(git rev-list --count --merges HEAD)"
echo "first commit:                $(git log --reverse --format='%h %ad' --date=short | head -1)"
echo "last commit:                 $(git log -1 --format='%h %ad' --date=short)"
echo "tags:                        $(git tag | wc -l)  ($(git tag --sort=creatordate | head -1) .. $(git tag --sort=creatordate | tail -1))"
echo

echo "## commits per month"
git log --format='%ad' --date=format:'%Y-%m' | sort | uniq -c
echo

echo "## author identities (name <email>)"
git log --format='%an <%ae>' | sort | uniq -c | sort -rn
echo

echo "## AI co-author trailers (Co-Authored-By)"
git log --format='%(trailers:key=Co-Authored-By,valueonly)' | sed '/^$/d' | sed 's/<.*//' | sort | uniq -c | sort -rn
echo "commits carrying any Co-Authored-By trailer: $(git log --format='%H %(trailers:key=Co-Authored-By,valueonly)' | awk 'NF>1{print $1}' | sort -u | wc -l)"
echo "first trailer commit: $(git log --reverse --format='%h %ad %(trailers:key=Co-Authored-By,valueonly)' --date=short | awk 'NF>2' | head -1)"
echo "trailer commits per month:"
git log --format='%ad %(trailers:key=Co-Authored-By,valueonly)' --date=format:'%Y-%m' | awk 'NF>1{print $1}' | sort | uniq -c
echo

echo "## commits touching each module (git log -- <path>, no rename following)"
for p in strudel sprudel klangscript klangscript-libs klangscript-ksp klangscript-ui audio_be audio_bridge audio_fe \
         audio_jsworklet audio-wire-codec-ksp audio_benchmark tones klang src docs/whitepaper docs/plans docs/tasks \
         docs/tasks-archive docs/blog .claude/skills DEV-DIARY.MD; do
  echo "$p $(git log --oneline -- "$p" | wc -l)"
done
echo

echo "## keyword-matched commits per design area (proxy, see caveat): count | first | last"
kw() {
  local n f l
  n=$(git log --oneline -i -E --grep="$2" | wc -l)
  f=$(git log --reverse --format='%ad' --date=short -i -E --grep="$2" | head -1)
  l=$(git log -1 --format='%ad' --date=short -i -E --grep="$2")
  printf '%-34s %4s | %s | %s   grep: %s\n' "$1" "$n" "$f" "$l" "$2"
}
kw "revert"                       "revert"
kw "rework / rewrite / redesign"  "rework|rewrite|redesign|redo"
kw "Rational / CycleTime"         "rational|cycletime|fixed.point|fix.point"
kw "named params"                 "named param|named arg"
kw "strudel compat tests"         "compat"
kw "Exciter/Engine/Pipeline DSL"  "exciter|enginedsl|engine dsl|pipelinedsl|pipeline dsl"
kw "Ignitor"                      "ignitor"
kw "Katalyst"                     "katalyst|katalyzer"
kw "Master"                       "master"
kw "parity / unification"         "parity|unif"
kw "EQ / optimizer"               "equalizer|eqcore|optimi[sz]er"
kw "warehouse / warmup"           "warehouse|warmup"
kw "wire / codec"                 "codec|wire"
kw "VoiceData"                    "voicedata|voice data"
kw "phase pool"                   "phase.?pool|lottery"
kw "block framing / size"         "block.?size|block-fram|framing"
kw "klangblocks"                  "klangblocks"
kw "Motor naming"                 "mot[oö]r"
kw "configure lambda / builder"   "configure|builder"
kw "field accessors"              "accessor"
kw "licensing"                    "licen[sc]|agpl|spdx"
kw "benchmark / perf"             "benchmark|perf"
kw "white paper"                  "white.?paper"
kw "tutorials"                    "tutorial|curriculum"
kw "signal flow / gain"           "signal.flow|pregain|postgain"
kw "door shape"                   "door"
kw "review rounds"                "review"
echo

echo "## tracked Kotlin lines (wc -l over git ls-files, includes blank and comment lines; reads the working tree)"
echo "all .kt:  $(git ls-files '*.kt' | xargs cat | wc -l)  in $(git ls-files '*.kt' | wc -l) files"
echo "test .kt: $(git ls-files '*.kt' | grep -E '/src/[a-zA-Z]*Test/' | xargs cat | wc -l)"
echo "main .kt: $(git ls-files '*.kt' | grep -v -E '/src/[a-zA-Z]*Test/' | xargs cat | wc -l)"
echo "test files (*Spec.kt, *Test.kt): $(git ls-files | grep -E '(Spec|Test)\.kt$' | wc -l)"
echo "per module:"
for m in $(git ls-files '*.kt' | cut -d/ -f1 | sort -u); do
  echo "  $m $(git ls-files "$m/*.kt" | xargs cat | wc -l)"
done | sort -k2 -rn
echo

echo "## docs"
echo "blog posts:            $(ls -d docs/blog/20*/ | wc -l)"
echo "archived task docs:    $(find docs/tasks-archive -name '*.md' | wc -l)"
echo "open task docs:        $(find docs/tasks -maxdepth 1 -name '*.md' | wc -l) (+ $(find docs/tasks/future -name '*.md' | wc -l) in future/)"
echo "plans:                 $(find docs/plans -name '*.md' | wc -l)"
echo "built-in song files:   $(git ls-files 'src/commonMain/kotlin/builtinsongs/*' | wc -l)"
