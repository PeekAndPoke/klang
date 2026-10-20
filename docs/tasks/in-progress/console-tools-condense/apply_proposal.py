#!/usr/bin/env python3
"""Apply the condense proposal to COPIES of the files (never the repo) and count lines and bytes.

  python3 apply_proposal.py <worktree> <out dir>

Writes <out dir>/before/<path> and <out dir>/after/<path>, and prints the before/after table.
Every replacement asserts that its old text occurs exactly once.
"""
import os
import shutil
import sys

W, OUT = sys.argv[1], sys.argv[2]

CT_BLOCK = """
**Console tools** in `console/tools/`, how and when in `/console-tools`. Use them instead of a hand snippet:
- `test-digest`: what really ran; a spec counts only with XML from this run, `--expect` names the specs that must run.
- `mutate`: Standard 2 in one lock call; hash-verified byte restore, and red counts only for the expected reason.
- `review-pack`: each round's full diff and fix delta, tree untouched; in a shared tree it holds others' edits too.
- `docs-lint`: broken links, a task file in the wrong folder, a moved task's path left in docs or KDoc, dashes.
- `stack-merge`: reviewed branches onto a new `stack/<name>` branch; only pure insertions auto-resolved, main untouched.
"""

EDITS = {
    "CLAUDE.md": [
        # the block, at the end of "Agents and skills"
        ("""the task. The `music-platform-strategist` agent runs only when asked for by name ("talk to the strategist").
""", """the task. The `music-platform-strategist` agent runs only when asked for by name ("talk to the strategist").
""" + CT_BLOCK),
        # the rows keep their rule; only the mechanics and the Detail column change
        ("reviewers may build under the lock (a doubt tested by a mutation stays inside one lock call and is restored), other workers never fan out. | 2026-08-04, reviewers 2026-10-04 | `/agent-fleet` |",
         "reviewers may build under the lock (a doubt tested by a mutant runs as one `mutate` campaign), other workers never fan out. | 2026-08-04, reviewers 2026-10-04 | `/agent-fleet`, `/console-tools` |"),
    ],
    "klangscript/CLAUDE.md": [
        ("""## Build & Test

```bash
./gradlew :klangscript:jvmTest          # language tests (fast)
./gradlew :klangscript:jsTest           # JS platform tests
./gradlew :klangscript-libs:jvmTest     # stdlib tests (separate module)
./gradlew :klangscript:compileKotlinJvm # compile only
```""", """## Build & Test

`:klangscript:jvmTest` (fast), `:klangscript:jsTest` (browser), `:klangscript-libs:jvmTest` (the stdlib),
`:klangscript:compileKotlinJvm` (compile only); through the build lock, read with test-digest (`/console-tools`)."""),
    ],
    "audio/CLAUDE.md": [
        ("""## Build & Test

```bash
./gradlew :audio_bridge:jvmTest
./gradlew :audio_be:jvmTest
./gradlew :audio_fe:jvmTest
```""", """## Build & Test

`:audio_bridge:jvmTest`, `:audio_be:jvmTest`, `:audio_fe:jvmTest`; through the build lock, read with test-digest
(`/console-tools`)."""),
    ],
    "sprudel/CLAUDE.md": [
        ("""## Build & Test

```bash
./gradlew :sprudel:jvmTest                          # preferred (fast)
./gradlew :sprudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :sprudel:jsTest                           # browser-specific only
```""", """## Build & Test

`:sprudel:jvmTest` (preferred, fast; one class: `--tests LangBpmSpec`, no quotes), `:sprudel:jsTest` (browser-specific
only); through the build lock, read with test-digest (`/console-tools`)."""),
    ],
    "klangscript-libs/CLAUDE.md": [
        ("""## Build & Test

```bash
./gradlew :klangscript-libs:jvmTest          # stdlib + analyzer-with-stdlib tests
./gradlew :klangscript-libs:jsTest           # JS platform
./gradlew :klangscript:jvmTest               # the language alone (no stdlib)
```""", """## Build & Test

`:klangscript-libs:jvmTest` (stdlib, analyzer with the stdlib), `:klangscript-libs:jsTest`, `:klangscript:jvmTest` (the
language alone); through the build lock, read with test-digest (`/console-tools`)."""),
    ],
    ".claude/skills/review-loop/SKILL.md": [
        ("1. **Collect the change set** — the current diff (vs branch base, or the files just edited).",
         "1. **Collect the change set** with `review-pack` (`/console-tools`): `full.diff`, from round 2 on also `delta.diff`."),
        ("""### Protocol (per new test)

1. **GREEN** — write the test, run it, confirm it passes.
2. **MUTATE** — introduce ONE targeted mutation that should break the tested behavior. **Prefer mutating the code under
   test** (flip an operator, off-by-one a constant, drop a term, swap a branch); mutating the test's inputs is the
   fallback when the code can't be safely touched.
3. **RED** — run the test. It MUST fail. If it stays green, the test is toothless → fix the test and repeat from 1.
4. **RESTORE** — revert the mutation exactly; run again → green. **Verify with `git diff` that only the intended change
   remains. NEVER leave a mutation behind.** An agent can be stopped mid-run with a mutant applied (2026-09-25,
   ledger): a worker that takes over from a stopped one first `cmp`s every production file the predecessor's
   mutation runner touched against that runner's backups, before it reads the code as the predecessor's work.
5. **REPORT** one line per test: `mutation-checked: <what was mutated> → red ✓`""", """### Protocol (per new test)

Run it as a `mutate` campaign (`/console-tools`): its baseline is GREEN, each mutant is MUTATE and RED, its
hash-verified restore and final run are RESTORE, its table is the REPORT. What stays yours:
1. **MUTATE** ONE targeted thing that should break the tested behavior. **Prefer mutating the code under test** (flip
   an operator, off-by-one a constant, drop a term, swap a branch); the test's inputs are the fallback.
2. **RED for the right reason**: `expect_message` names the failure you expect. A SURVIVED mutant means a toothless
   test (fix it and run again); KILLED-OTHER, KILLED-BY-COMPILE and NOT-RUN prove nothing.
3. **REPORT** one line per test: `mutation-checked: <what was mutated> → red ✓` (the campaign's row).
A worker that takes over from a stopped one runs `mutate.py restore <backup dir>` first (2026-09-25, ledger)."""),
        ("| tooling | a mechanical check would have caught it | a pre-commit grep or a script (`git diff | grep -c '—'`) |",
         "| tooling | a mechanical check would have caught it | a tool in `console/tools/` (`/console-tools`) or a pre-commit grep |"),
        ("""- **`:<module>:jsBrowserTest --tests X` runs the WHOLE JS suite**; read the result XML for the spec you want, and a
  spec's printed output lands in the aggregate `TEST-Kotest.xml`, not in its own XML.
""", ""),
        ("""- **Reviewers may run Gradle, under the lock** (maintainer, 2026-10-04): a doubt settled by a run beats a guess; the
  rules (one lock call around mutate, build, restore; `cp` and `cmp`) are in `/agent-fleet`, "Reviewers may build".""",
         """- **Reviewers may run Gradle, under the lock** (maintainer, 2026-10-04): a doubt settled by a run beats a guess; a
  mutant runs as one `mutate` campaign in one lock call (`/agent-fleet`, "Reviewers may build")."""),
        ("""reviewers only name mutants). Reviewers run their own mutants, each in one lock call (back up, mutate, run, restore
  with `cp`, check with `cmp`). When the safety check refuses a reviewer's call""",
         """reviewers only name mutants). Reviewers run their own mutants, each as a `mutate` campaign in one lock call.
  When the safety check refuses a reviewer's call"""),
        ("""- **Snapshot the working tree only inside a lock call** (2026-10-08).""",
         """- **Snapshot the working tree only inside a lock call** (2026-10-08; `review-pack` too)."""),
        ("""  to match nothing at all (2026-07-03, 2026-09-06). In a mutate-then-expect-red script that
  `No tests found` exit is indistinguishable from a real kill and produced three spurious RED
  verdicts (2026-08-20): every expect-red runner must grep the log for `No tests found` and
  treat it as a script error, and print the failing test names (an empty list on a "red" is the tell).""",
         """  to match nothing at all (2026-07-03, 2026-09-06). Its `No tests found` exit once read as three spurious RED
  verdicts (2026-08-20); `mutate` and `test-digest` read it as NOT-RUN or MISSING, never as a kill."""),
        ("""- **A `--tests` filter does not apply to a JS test task the way it does on the JVM** (2026-09-17):
  `:audio_bridge:jvmTest :audio_bridge:jsTest --tests <Fqcn>` in one invocation fails. Filter the JVM
  task; run the JS task unfiltered, on its own.
""", ""),
    ],
    ".claude/skills/agent-fleet/SKILL.md": [
        ("""console/with-build-lock.sh bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'
```
""", """console/with-build-lock.sh python3 console/tools/mutate/mutate.py run mutants.json   # a whole mutation campaign
```

From a worktree, set `KLANG_LOCK_FILE=/opt/dev/peekandpoke/klang/.claude/build.lock`: the script's default is its own
checkout's file, which serializes against nobody (`/console-tools`).
"""),
        ("""**For a mutation check the critical section is `mutate → build → restore`, not just the build** — wrap all three.""",
         """**For a mutation check the critical section is `mutate → build → restore`, not just the build**: one `mutate` campaign."""),
        ("""wraps the whole critical section in ONE lock call (back up the file, mutate, build and run, restore with `cp`,
  verify with `cmp`, never with git) and reports the mutant and its verdict.""",
         """runs it as one `mutate` campaign in ONE lock call (byte backup, hash-verified restore, never git) and reports
  the mutant and its verdict."""),
        ("""- **Counting tests: clear old results with `find`, never `rm -rf`** (2026-10-10: the pitch step 4 and step 5
  workers each ran `rm -rf <module>/build/test-results/<task>` to get a clean count, against their brief). The safe
  form: `find <module>/build/test-results/<task> -name '*.xml' -delete`, or count only the XML files newer than the
  run's start. Put this line in every brief that asks for test counts.""",
         """- **Counting tests: `test-digest`, never `rm -rf`** (2026-10-10: the pitch step 4 and step 5 workers each ran
  `rm -rf <module>/build/test-results/<task>` to get a clean count, against their brief). test-digest counts only this
  run's XML, so nothing needs clearing; if you must, `find <dir> -name '*.xml' -delete`. Put this line in every brief
  that asks for test counts."""),
    ],
    ".claude/skills/code-style/SKILL.md": [
        ("""or a new sentence. The A/B comment suffix is `, swap`. Why (maintainer, 2026-08): the dash reads as an AI tell.""",
         """or a new sentence. The A/B comment suffix is `, swap`. Why (maintainer, 2026-08): the dash reads as an AI tell.
Check: `review-pack --dash-scan` (added lines, any file), `docs-lint --forbid-dashes` (new docs); `/console-tools`."""),
    ],
    ".claude/skills/sprudel-dev-knowhow/SKILL.md": [
        ("""## Testing

```bash
./gradlew :sprudel:jvmTest                          # preferred (fast)
./gradlew :sprudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :sprudel:jsTest                           # browser-specific only
```""", """## Testing

`:sprudel:jvmTest` (preferred; `--tests LangBpmSpec`, no quotes), `:sprudel:jsTest` (browser-specific only); through
the build lock, read with test-digest (`/console-tools`). The rest: `sprudel/ref/testing.md`."""),
    ],
    ".claude/skills/klangaudio-knowhow/SKILL.md": [
        ("""## Testing

```bash
./gradlew :audio_bridge:jvmTest    # bridge types + comm-link tests
./gradlew :audio_be:jvmTest        # DSP engine + voice tests
./gradlew :audio_fe:jvmTest        # sample management tests
```""", """## Testing

`:audio_bridge:jvmTest` (bridge types, comm-link), `:audio_be:jvmTest` (DSP engine, voices), `:audio_fe:jvmTest`
(samples); through the build lock, read with test-digest (`/console-tools`)."""),
    ],
    ".claude/skills/klangscript-knowhow/SKILL.md": [
        ("""## Testing

```bash
./gradlew :klangscript:jvmTest         # language tests (fast)
./gradlew :klangscript-libs:jvmTest    # stdlib / DSL door tests
./gradlew :klangscript:jsTest          # when testing JS-specific behavior
```""", """## Testing

`:klangscript:jvmTest` (language, fast), `:klangscript-libs:jvmTest` (stdlib, DSL doors), `:klangscript:jsTest` (JS
behaviour); through the build lock, read with test-digest (`/console-tools`)."""),
    ],
}

# files that are read and kept unchanged (counted for the table)
KEEP = ["klangscript/MEMORY.md", "sprudel/MEMORY.md", "audio/MEMORY.md", ".claude/skills/public-voice/SKILL.md",
        ".claude/skills/review-loop/escape-ledger.md"]


def stats(path):
    data = open(path, "rb").read()
    return data.count(b"\n"), len(data)


rows = []
for rel in list(EDITS) + KEEP:
    src = os.path.join(W, rel)
    before = os.path.join(OUT, "before", rel)
    after = os.path.join(OUT, "after", rel)
    os.makedirs(os.path.dirname(before), exist_ok=True)
    os.makedirs(os.path.dirname(after), exist_ok=True)
    shutil.copyfile(src, before)
    text = open(src, encoding="utf-8").read()
    for old, new in EDITS.get(rel, []):
        n = text.count(old)
        assert n == 1, f"{rel}: old text found {n} times: {old[:80]!r}"
        text = text.replace(old, new)
    with open(after, "w", encoding="utf-8") as f:
        f.write(text)
    (bl, bb), (al, ab) = stats(before), stats(after)
    rows.append((rel, bl, al, bb, ab))

print(f"| File | Lines before | Lines after | Change | Bytes before | Bytes after |")
print(f"|---|---:|---:|---:|---:|---:|")
for rel, bl, al, bb, ab in rows:
    print(f"| `{rel}` | {bl} | {al} | {al - bl:+d} | {bb:,} | {ab:,} |")
tl = sum(r[1] for r in rows), sum(r[2] for r in rows)
tb = sum(r[3] for r in rows), sum(r[4] for r in rows)
print(f"| **total** | {tl[0]} | {tl[1]} | {tl[1] - tl[0]:+d} | {tb[0]:,} | {tb[1]:,} |")
