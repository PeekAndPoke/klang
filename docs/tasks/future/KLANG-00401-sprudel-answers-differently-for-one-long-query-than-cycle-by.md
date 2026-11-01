# KLANG-00401: sprudel answers differently for one long query than cycle by cycle (voicing with a continuous rank)

> **Status (2026-11-01): NOT SCHEDULED.** Found by KLANG-00399 step 4's review; the cause is not pinned down yet.

## Why this exists

A pattern must give the same events for a span whether it is asked in one query or in pieces: live playback asks
cycle by cycle, and since KLANG-00399 step 4 the offline renderer does too, but specs, tools and anyone else may ask
for a whole song at once. "The Synthsale Piper's Last Rave" (`src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt`)
does not: queried `[0, 256)` at once against `[k, k + 1)` for each cycle, 141 of 19,875 events differ, the first at
cycle 41, a pad chord in another octave and gain (F3, A3, F2 at once against F4, A4 cycle by cycle). The likely cause is
the pad's `voicing(rank = sine.range(0, 1.9).fast(7).add(perlin.range(0, 0.3)))`: a continuous signal sampled from the
query span rather than from each event. Not verified.

What it cost: before step 4 the offline renders and the corpus (`CorpusCheck`) of this song differed from what plays
live; since step 4 they match live, and the corpus reference took the new render (an edited live song, information
only). Live playback's steady fetch asks cycle by cycle and was never affected; until step 4 its resync after an edit
or a tempo change asked `[floor(now), ceil(cursor))` in one query (now cycle by cycle), and a few live paths still ask
longer spans: `catchUp` (state only), `reemitVoiceSignals` (highlights), `preloadSamples` and `updatePattern`'s sample
look-ahead. The fix, or its guard, covers those.

## Decide before implementing

- Nothing: a pattern's answer must not depend on how its span is split.

## The work

1. A spec: the song's pattern (or the smallest pattern that shows it) queried whole and cycle by cycle gives the same
   events, in the same order within each cycle. The scratch comparison of KLANG-00399's review did exactly that.
2. Find the operator that samples the query span (`voicing`'s rank, or the signals `sine`/`perlin` under `range`,
   `fast` or `add`), fix it to sample each event's own time.
3. A general guard: a property spec over the built-in songs, whole against cycle by cycle, for a few dozen cycles.

## Acceptance

- Every built-in song answers the same in one query as cycle by cycle.

## History

- 2026-11-01: created from KLANG-00399 step 4's review (Klang-Research).
