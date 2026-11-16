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
- The scheduler itself asks other windows than one cycle: a cycle cut at a tempo change (voices that are played),
  the catch-up to "now", the sample preload, and the re-emit of voice signals, which asks several cycles at once
  and so can show other signals than the voices scheduled where a `late` by a noise is in play. Not changed.
- The noise engines kept per seed (Found, point 3).
- `Random` is not yet named in `klangscript-libs/CLAUDE.md`, `klangscript/CLAUDE.md` and the song writers'
  reference of the music writing skill: the maintainer's OK is asked.

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
