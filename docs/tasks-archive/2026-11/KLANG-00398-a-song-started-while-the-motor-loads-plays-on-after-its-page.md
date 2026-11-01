# KLANG-00398: A song started while the Motor loads plays on after its page is gone

> **Status (2026-11-01): DONE.** fixed with KLANG-00406 step 2

## Why this exists

`KlangCodePlaybackCtrl.runPlay` (`src/jsMain/kotlin/spine/KlangCodePlaybackCtrl.kt`) suspends on
`Player.ensure().await()` (through `PlaybackEngine.readyPlayer()`) before it creates the playback. If the page unmounts
during that wait, its `ctrl.release()` stops while there is nothing to stop; the playback then starts with no UI left to
stop it. The sound plays on, and since KLANG-00396 step 10 the screen stays awake with it. Possible only while the Motor
loads (the first play of a visit). Seen by reading the code (the level-1 review of KLANG-00396, round 1), not yet in a
browser.

## Decide before implementing

- Nothing: the fix is local.

## The work

1. A `stop()` during the wait wins: `runPlay` gives up after the await when a stop came meanwhile (a generation
   counter that `stop()` bumps and `runPlay` compares after each suspension).
2. A spec: stop during a pending `ensure()`, then let it complete; no playback starts. `SongDeckSpec`'s
   `PlaybackEngine` seam lets a spec hold the engine's start.
3. The same race on the song deck (KLANG-00406): `SongDeck.stop()` during the first wait leaves `onDeck` empty while
   the song then starts, and a start that fails leaves its song on the deck though nothing plays. Step 2 of
   KLANG-00406 shows `onDeck` on the song page, so fix both with it or before.

## Acceptance

- Leaving a page while its first song loads leaves nothing playing.

## History

- 2026-10-31: created.
- 2026-11-01: the ctrl moved to `spine/` with `release()`; the same race on `SongDeck` (KLANG-00406 step 1's review).
- 2026-11-01: fixed with KLANG-00406 step 2: `KlangCodePlaybackCtrl` counts its stops, and a start that waited for the
  engine gives up when a stop came after its Play (`SongDeckSpec`, "a stop while the engine starts wins"). Leaving a
  song page now stops nothing by design (the song plays on, on the deck); Klang Amp's leave and every Stop still win
  over a start that waits.
- 2026-11-01: DONE. fixed with KLANG-00406 step 2
