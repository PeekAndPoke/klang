# KLANG-00398: A song started while the Motor loads plays on after its page is gone

> **Status (2026-10-31): NOT SCHEDULED.** Found in KLANG-00396's wake lock review; the lock now stays on with it.

## Why this exists

`KlangCodePlaybackCtrl.runPlay` (`src/jsMain/kotlin/comp/KlangCodePlaybackCtrl.kt`) suspends on
`Player.ensure().await()` before it creates the playback. If the page unmounts during that wait, its `ctrl.stop()`
runs while there is nothing to stop; the playback then starts with no UI left to stop it. The sound plays on, and
since KLANG-00396 step 10 the screen stays awake with it. Possible only while the Motor loads (the first play of a
visit). Seen by reading the code (the level-1 review of KLANG-00396, round 1), not yet in a browser.

## Decide before implementing

- Nothing: the fix is local.

## The work

1. A `stop()` during the wait wins: `runPlay` gives up after the await when a stop came meanwhile (a generation
   counter that `stop()` bumps and `runPlay` compares after each suspension).
2. A spec: stop during a pending `ensure()`, then let it complete; no playback starts.

## Acceptance

- Leaving a page while its first song loads leaves nothing playing.

## History

- 2026-10-31: created.
