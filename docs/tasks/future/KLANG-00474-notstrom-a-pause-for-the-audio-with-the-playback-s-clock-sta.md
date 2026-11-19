# KLANG-00474: Notstrom: a pause for the audio, with the playback's clock standing still

> **Status (2026-11-19): NOT SCHEDULED.** Asked by Klang-Research for notstrom's drawer (a Pause/Play button), 2026-11-18; the maintainer, 2026-11-19: 'yes to pause button'. Not started: waits for the token refill. What is asked: one call to hold and release (for example hold(on) on AudioBackend / KlangPlayer that suspends the audio context and that the wake-on-click keepRunning respects), a readable state (held or not), and heardSec standing still while held. The engine has no pause today; it reaches the backend's clock, so it is strict code with the full review loop. Klang-Main-Dev builds the engine half in klang/ and the audio modules, Klang-Research the button in apps/notstrom.

## Why this exists

## Decide before implementing

## The work

## Acceptance

## History

- 2026-11-19: created.
