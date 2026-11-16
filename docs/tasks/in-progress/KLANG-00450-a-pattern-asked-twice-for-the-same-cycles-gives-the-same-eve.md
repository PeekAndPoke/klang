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

## Decide before implementing

- **How a song says "another seed for every playback".** The maintainer's proposal (2026-11-16):
  `seed(Random.nextInt())`, read once when the code is run. KlangScript's stdlib has no random function yet.
- **Whether `timeOfDay` and its siblings stay as they are** (read at every query, so a pattern that uses them is
  not idempotent), or are frozen per playback.
- **The sub-cycle windows (point 2):** trace and fix, or record that a pattern is asked in whole cycles.

## The work

## Acceptance

## History

- 2026-11-16: created.
