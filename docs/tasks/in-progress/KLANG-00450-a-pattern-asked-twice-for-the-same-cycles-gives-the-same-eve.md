# KLANG-00450: A pattern asked twice for the same cycles gives the same events: wall-clock seeds and sub-cycle windows

> **Status (2026-11-16): IN PROGRESS.** Handed over by the maintainer through Klang-Research (2026-11-16), for the live act KLANG-00449.

## Why this exists

The live act (KLANG-00449) asks a song's pattern for cycles ahead of the sound. If a second asking gives other
events, the band plays notes that are not the ones heard. The maintainer (2026-11-16): "querying the same cycles
must be idempotent". Klang-Research measured that Der Schmetterling's `lead` gives another count of events at every
asking and handed the search over.

## Found (2026-11-16)

Measured with a throwaway JVM test on Der Schmetterling, cycles 64 to 96, all tags:

1. **The song seeds itself from the wall clock.** Its last line is `.seed(timeOfDay.mul(60*60*60*24))`, and
   `timeOfDay` reads the system clock at every query (`lang_continuous_clock.kt`), so the seed is another one each
   time, also from cycle to cycle. The song as it is: 3052 and 3064 events for the same window, not equal. With the
   line swapped for `seed(12345)`: 3042 and 3042, equal. Sprudel has no randomness without a seed: every random
   source goes through `QueryContext.getSeededRandom`, and no seed set means seed 0. The parts that set their own
   seed (the guitars) were stable for that reason; the others moved with the clock.
2. **Asked in windows smaller than a cycle, a pattern gives other events, and more of them.** With the seed
   pinned, cycles 64 to 96 asked in one piece, in steps of 1 and in steps of 2 give the same 3042 events. In steps
   of 0.5: 3074 events, 3003 in common. In steps of 0.25: 3138, 2953 in common. The live scheduler asks in whole
   cycles (`fetchChunk = 1.0`), except where a tempo change falls inside a cycle and when it catches up to "now".
   Not traced yet. One cause seen in the code: a continuous signal as the amount of `late` is read once per query,
   at the query's start (`ContinuousPattern`, `TimeShiftPattern`), so the shift depends on the window.
3. Beside it: `perlin` and `berlin` keep one noise engine per seed in a map that is never emptied, so a seed that
   changes all the time makes it grow for as long as the pattern lives.

## Decided (the maintainer, 2026-11-16)

- **Another seed for every run of the code is `seed(Random.nextInt())`**, read once when the code runs. The stdlib
  had no random function: "it should have the same functions as the Kotlin Random, f.e. nextInt(0, 100) and
  similar".
- **"pattern is asked in full cycles":** the sub-cycle windows are not traced further. The review showed the rule
  that holds is narrower: one whole cycle at a time. Several cycles in one asking can differ too, where no
  top-level `seed` splits the query (a continuous amount in `late` is read once per asking, from the window's
  start); Der Schmetterling hid that, its `seed` asks cycle by cycle.

## Open

- `timeOfDay` and its siblings still read the clock at every asking; a song that seeds from them is not
  idempotent. Der Schmetterling's seed line is the maintainer's to change.
- DONE 2026-11-16 (below): the scheduler asked other windows than one cycle (a cycle cut at a tempo change, the
  catch-up, the sample preload, the re-emit of voice signals).
- The live act compiles the playing code a second time and asks that pattern itself (Klang-Research, 2026-11-16):
  it gets nothing from the scheduler's answers, and with `seed(Random.nextInt())` its compile draws another seed
  than the one that sounds. A way for it to read the playback's cycles is the maintainer's to decide.
- The noise engines kept per seed (Found, point 3).
- `Random` is not yet named in the song writers' reference of the music writing skill: the maintainer's OK is
  asked (the two CLAUDE.md files are done).

## The work

- `Random` in KlangScript's stdlib (`KlangScriptRandom.kt`): `nextInt`, `nextDouble`, each without arguments, with
  an end, or with a start and an end (a named `until` alone is the form with an end), and `nextBoolean`. Left out:
  `nextLong`, `nextFloat`, `nextBits`, `nextBytes`, a seeded `Random(seed)`.
- The contract of `KlangPattern.queryEvents` says how to ask for the events that are heard.

## Acceptance

- `StdLibRandomSpec`, 10 tests, on the JVM and in the browser; `:klangscript-libs:jvmTest` 1140 and
  `:klangscript-libs:jsBrowserTest` 915, 0 failed. 9 mutants killed.
- Review: round 1 (reviewer-opus-high) NOT CLEAN, 2 MAJOR (a named `until` alone returned any number; the contract
  sentence promised any whole-cycle window), fixed. Round 2 (reviewer-fable-medium) CLEAN, 1 MINOR and 2 NIT applied.
- Not shown yet: Der Schmetterling with its seed line changed. With a plain number in that line it was idempotent
  (Found, point 1).

## History

- 2026-11-16: created.

## 2026-11-16: the song's seed line, measured

The maintainer changed Der Schmetterling's last line to `.seed(Random.nextInt())` (in their working tree). Measured
with a throwaway JVM test (removed): the song compiled once, cycles 64 to 96 asked one cycle at a time, twice, 1.5
seconds apart: the same events both times. `Random` is now named in `klangscript-libs/CLAUDE.md` and
`klangscript/CLAUDE.md` (maintainer's OK, 2026-11-16).

## 2026-11-16: the playback asks whole cycles and keeps the answers

The maintainer: "we always query one full cycle at a time internally"; an edge that is not on a cycle is filtered
after the asking; the answers are kept in the playback. Built as `PatternCycles`
(`klang/src/commonMain/kotlin/PatternCycles.kt`), which every query of `KlangPatternScheduler` goes through:

- Whatever the window, the pattern is asked one whole cycle at a time; a cut edge keeps events by their start.
- A tempo change inside a cycle asks that cycle whole once per tempo; each event comes once, at its tempo.
- The last 32 answers are kept, each for its pattern, cycle and tempo. A new pattern and a stop empty the store; a
  tempo change does not (the answers at the old tempo stay right for the part that still plays at it).

What changes in behaviour:

- The fetch plays the answer the sample look-ahead got about 8 cycles earlier, so a pattern that reads the wall
  clock (`timeOfDay`) reads it about 8 cycles before it is heard (it was about 2).
- The diagnostics' pattern work figure drops (most fetches find their answer), so old and new numbers do not compare.
- A catch-up asks up to 64 single cycles in place of one wide window. Not measured.

Checks: `:klang:jvmTest` 105, 0 failed (`PatternCyclesSpec` 12, and one scheduler test that every asking is a whole
cycle across a tempo change); 17 mutants killed; review round 1 (`reviewer-opus-high`) CLEAN, 3 MINOR and 2 NIT
applied. Not mutation-checked: the guard against an infinite window's start (its mutant would not end).

## 2026-11-18: the maintainer closes the leftovers

- The song writers' reference no longer says "No `Math.random()`" (the maintainer: "Remove the rule").
- `timeOfDay`'s KDoc note and the noise engines kept per seed: "consider this as closed". Nothing is changed there.
- Still open: the live act's own compile of the playing code (asked of Klang-Research).

## 2026-11-18: a reader beside the sound reads the playback's cycles

The live act compiled the playing code a second time (Klang-Research, KLANG-00449): it missed the playback's answers
and, with `seed(Random.nextInt())`, drew another seed than the one that sounds. The maintainer: the two sessions
build what the band needs together. The engine half, on `KlangCyclicPlayback`:

- `cycleEvents(cycle)`: the events of a whole cycle as `KlangCycleEvent` (seconds on the tempo map, the end is the
  slot, the scheduler's `VoiceData`). It reads the scheduler's own store, so whoever asks a cycle first, that answer
  is the cycle's. It works on a playback made and not started, and a start keeps the answers. A failing cycle gives
  no events.
- `heardCycles()` and `rpmAt(cycle)`.
- `PatternUpdated` is the sign that what was read is out of date. `updatePattern` now swaps the pattern and empties
  the store before it signals. The signal now also comes when the tempo is set before the playback has begun, and
  at a start whose tempo map is not the one set (another tempo, or a map left with a change in it by the last run).
- The store keeps 64 answers (it was 32).

Before a start the tempo is 30 rpm until `updateRpm` sets it: a reader sets the tempo, reads, then starts at it.

Checks: `:klang:jvmTest` 115 and `:klang:jsBrowserTest`, 0 failed; the apps compile; 18 mutants killed; review
round 1 (`reviewer-opus-high`) CLEAN, 4 MINOR and 4 of 5 NIT applied. Left as it is: `heardCycles` can read on the
next tempo for about the output latency when two tempo changes follow each other within a quarter second across a
cycle border. Found by the review, not from this change, not fixed: `OneShotPlayback.updatePattern` drops its cycle
limit; `updatePattern` on a stopped playback still sends a `ReplaceVoices`.

Open: the live act's side in `apps/common` (Klang-Research), and a look at it in the app.

## 2026-11-18: the live act's side is in

Klang-Research, 166f3c3c1: `SongReading` asks the playback's `cycleEvents`; no second compile. The band keeps at
most 34 cycles (a bar back, up to 32 ahead), inside the store of 64. Their tests: `:apps:common:jsBrowserTest` 52,
`:apps:notstrom:jsBrowserTest` 9, `:apps:studio:jsBrowserTest` 253, `:klangviz:jvmTest` 240, green; 17 mutants; two
clean rounds (their report, not run here).

Open: the maintainer's look in the browser (the band follows what sounds, also with `seed(Random.nextInt())` and
after a code edit). Found by their review, older, not fixed: the studio's tempo slider does not publish
`nowPlaying`, so the band's bar length keeps the old tempo until the next Play.
