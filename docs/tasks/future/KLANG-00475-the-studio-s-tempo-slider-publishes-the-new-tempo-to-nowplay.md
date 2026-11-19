# KLANG-00475: The studio's tempo slider publishes the new tempo to nowPlaying

> **Status (2026-11-19): NOT SCHEDULED.** Found by Klang-Research's review of KLANG-00450 (2026-11-18); the maintainer, 2026-11-19: 'yes to fixing the tempo change issue'. Not started: waits for the token refill. The tempo slider calls the debounced updateRpm (apps/studio/src/jsMain/kotlin/spine/KlangCodePlaybackCtrl.kt, around lines 146 to 147) and never publishes nowPlaying, so nowPlaying.rpm stays old while a song plays. The live act's notes are right at once (it reads the playback's cycles), but its bar length (the nods, the lights' beat) stays the old tempo's until the next Play. Fix: nowPlaying follows the slider; the band then makes a new show on it by itself (Klang-Research). Check what else reads nowPlaying.rpm before changing it.

## Why this exists

## Decide before implementing

## The work

## Acceptance

## History

- 2026-11-19: created.
