# Editor diagnostics for the voice doors (phase 3 step 11)

Status: **future, deferred by the maintainer (2026-09-27):** "no warnings yet; diagnostics tools come later once the
design is fully settled". Opened 2026-09-28 when the phase 3 record was archived
(`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`, section 9 row 11 and section 3c).

## What it is

Phase 3 made every voice an Ignitor tree and every voice door a `classic()` slot. Several old forms now do
something else, or nothing, without an error: the SILENT rows of the release-note table (record section 3c). This
task is the editor's answer to them: a warning where a script says something the engine will not do.

## The candidates, in the order they were raised

1. **An authored instrument that does not end in `classic()`, played with voice doors** (the step 11 diagnostic).
   Since phase 3 step 9 such a tree plays as its bare tree: pattern doors (`lpf`, `adsr`, `crush`, ...),
   `onepole(x)` and `adsrOn`/`adsrOff` do nothing on it (record section 3c, rows "8, 9" and "9"). The tag already
   exists: `IgnitorDsl.endsInClassic()` (`audio_bridge/.../IgnitorDslClassic.kt`). The step 11 plan had two parts:
   (a) a `classic()` warning at evaluation time, (b) a per-slot check (a door whose slot the instrument does not
   place). That plan was kept in a session scratchpad only and is lost; redo it from this file.
2. **A string literal in a wet slot.** A positional sprudel `body("wood")` or `vowel("a")` puts the string in
   `wet`, where the wet head writes nothing (the raw Motor forbids a `require`). Raised in the door-shape walk (`.claude/skills/dsl-design/door-shapes.md`)
   (the wet rule) as open for the maintainer.
3. **The other SILENT rows** of section 3c, where a static check can see them (for example the positional
   reinterpretations of `phaser(0.3)` and `shimmer(0.4)`, a compressor-shaped five-argument `limiter`). Whether
   any of them deserves a warning is the maintainer's call, row by row.
4. **The signal-flow plan's deliberate open point** (`docs/plans/signal-flow-redesign.md` section 6, "Considered
   and rejected"): a construction that makes "this instrument does not listen to that door" impossible to write
   by accident, without a warning. A type boundary between a signal and an instrument was discussed and not
   adopted.

## What exists to build on

- The editor's linter renders `AnalyzedAst.diagnostics` (`klangscript/.../intel/AnalyzedAst.kt`, the checker
  pipeline where `NamedArgumentChecker` sits); see `docs/tasks/klangscript-intellisense.md` (step 0 done).
- Runtime errors reach the editor through `SprudelDiagnostics` (`sprudel/.../SprudelDiagnostics.kt`,
  `docs/tasks/runtime-errors-in-the-editor.md`).
- The graph plan may change the question: `docs/plans/future/signal-graph-engine.md` section 1 attaches
  `.sprudel()` (today's `classic()`) automatically to an authored instrument, which would make candidate 1 moot.
  Decide that first.

## History

Step 8 accepted the doors going dark on a bare authored instrument on the condition that this diagnostic lands
before the branch merges; the deferral of 2026-09-27 dropped that merge gate (record section 9, "Merge gate,
revised").

## Decisions it needs

- Whether candidate 1 is answered by a warning or by the graph plan's auto-attach.
- Which of candidates 2 and 3 get a warning at all.
