# Code-style pass: name the arguments that could be swapped

Status: **`audio_be` and `audio_bridge` done 2026-10-07, reviewed (round 1 clean) and committed on `engine-pass-1`
(v0.6.0): `cf584b8c` (Task B), `315595ce` (`audio_bridge`), `3daf349e` (`audio_be` main), `a6fbbddf` (`audio_be`
ignitor specs), `86065a81` (the other `audio_be` specs and the §24 exemptions); the 18-song corpus bit-identical.**
Report: `tmp/reviews/named-args-report.md`.

**What is left:**
- the other modules (`sprudel`, `klangscript`, `klangscript-libs`, the UI modules, the root), if the maintainer wants
  the pass there too; new code follows §24 already;
- the data-table exception (`VowelBands.b`, `BodyMaterials.m`, 300 positional rows): Q18 in
  [`_maintainer-questions.md`](_maintainer-questions.md);
- `VoiceFactory.buildVoice`'s redundant `cut = data.cut` beside `data` moved to
  [`engine-follow-ups.md`](engine-follow-ups.md).

## Why

The new `/code-style` §24 (maintainer, 2026-10-07): at a call site, pass arguments by name whenever two or more of
them could be swapped and still compile, or a parameter reorder would silently shift them. Found in review:
`Cylinders.checkIn(id: Int, voiceId: Int, ...)`, two ids that swap silently, in a different order than
`Cylinder.checkIn`.

## Scope

`audio_be` and `audio_bridge` first (main and test sources), then the other modules as a follow-up if the
maintainer wants it.

## How

- Find every call with two or more arguments of the same or confusable type (`Int`, `Double`, `Boolean`, `String`,
  frames vs seconds, ids), and name them. Constructors and data-class copies included.
- Where a declaration invites the mistake (two adjacent ids, parameter orders that differ between sibling
  functions), fix the declaration too, if it is plain to do.
- Mechanical and behaviour-neutral: the 18-song corpus bit-identical, the suites green, no mutation campaign needed
  for pure renames (the review checks the diff).

## Done: `audio_be` and `audio_bridge`, 2026-10-07

**Method.** A type-aware scan, not a grep: an IntelliJ inspection script (Kotlin Analysis API, run through the IDE)
resolves every call with two or more value arguments, maps each positional argument to its parameter, and flags
the call when two positional arguments could trade places and still compile (each argument's type is a subtype of
the other's parameter type). Constructors, data-class `copy`, super and delegation calls are calls too. All 412
files of both modules, main and test, were scanned; no call failed to resolve. The flagged calls got every positional
argument named (a vararg and a trailing lambda stay as they are), inserted at the exact offsets the scan reported and
checked against the source text. A second scan afterwards finds only the exemptions below.

**Counts.** 2,557 calls named, 7,915 arguments: `audio_bridge` 186 calls (main 96, tests 90), `audio_be` main 392,
`audio_be` tests 1,979. Pure insertions; nothing else changed in those lines, nothing reflowed (505 more lines now
run past 120 characters, a formatting matter).

**Exempt on purpose (stay positional):**
- Commutative or range-pair library calls whose order is a universal convention: `minOf`, `maxOf`, `min`, `max`,
  `coerceIn`, `fill(value, from, to)`, `copyOfRange`, `subList`, `Random.nextDouble(from, until)`, and the tuples
  `Pair` / `Triple`. `copyInto` is NOT exempt (its destination offset sits next to a start/end range): its 103 calls
  are named.
- The two literal data tables, `VowelBands.b(freq, db, q)` (180 rows) and `BodyMaterials.m(freq, db, q)` (120 rows):
  the column order is the private helper declared just above each table, and naming every cell would triple each
  row. The helpers' own bodies (`VowelBands.Band(...)`, `BodyMaterials.Mode(...)`; `FilterDef.Formant.Band` / `FilterDef.Body.Mode` until engine tidy-up step 12 (c)) are named. **For the
  maintainer:** if §24 has no table exception, naming the 300 rows is one more mechanical run.

**Cannot be named:** calls of a function-typed value (`Function2`..`Function7.invoke`, 22 calls in tests) and two
Java calls (`AudioFormat(...)`, `SourceDataLine.write(...)` in `jvmMain`).

**Declarations.** No declaration needed a change for this pass. The `Cylinders.checkIn` / `Cylinder.checkIn` pair that
started it is already fixed (`checkIn(id, blockStart)` and `checkIn(blockStart)`). The id-pair siblings agree on one
order (`Cylinder.install` / `beginFade` and `MasterBus.land` all take `key, rawName, dsl`). A sibling-order scan found
one difference: the runtime `Ignitor.lowpass` / `highpass` / `bandpass` / `notch` take `env, analog` and the DSL doors
take `analog, env`. The types differ (`FilterEnvDef` against `Ignitor`), so a swap cannot compile, and the DSL order is
a public surface; left as is. `VoiceFactory.buildVoice` (17 parameters) is named at both call sites; it also takes
`cut = data.cut` beside `data`, a redundancy left for the tidy-up.

**Commits** (in this order; the file lists are in the report): Task B, the deferred `VoiceFactory` items of tidy-up
step 1 (`../tasks-archive/2026-10/20261009-engine-tidy-up.md`), as `tmp/reviews/named-args-taskB.patch`, because 15 of its files also carry named
arguments; then `audio_bridge`; `audio_be` main; `audio_be` ignitor tests; the other `audio_be` tests. Each commit
compiles on its own.
