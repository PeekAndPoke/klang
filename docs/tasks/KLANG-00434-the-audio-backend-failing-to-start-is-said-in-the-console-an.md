# KLANG-00434: The audio backend failing to start is said in the Console, and the Console opens

> **Status (2026-11-10): NOT STARTED.** From the maintainer's request of 2026-11-10; after KLANG-00422 step 1.

## Why this exists

The maintainer (2026-11-10): "when the audio backend cannot be started for 1. wrong wire-hash or 2. not able to load
the js we need to report this in the console and open the console so the user can see the error."

## How it is today (read 2026-11-10)

- **The worklet's script does not load.** `JsAudioBackend.run` awaits `ctx.audioWorklet.addModule("/klang-worklet.js")`;
  a rejection is caught there, written with the browser's own `console.error("AudioWorklet Error:", e)` and thrown
  again. It is thrown inside the job `KlangPlayer` launched for the backend (`backendJob`), after `klangPlayer(...)`
  returned: so `Player.ensure()` has already said READY, the status never becomes FAILED, and the app's own Console
  (the log's listener) hears nothing, since the browser's console is not the `Logger`.
- **A wrong wire hash.** `WorkletContract.requireSchema` throws on every message whose stamp differs from
  `WIRE_SCHEMA_HASH` ("Worklet wire-schema mismatch [...] stale cached worklet? reload"). Where that throw lands
  (the frontend's feedback loop, the worklet's command handler) and what the user sees is to be traced; no `Logger`
  is on that path either.
- **A way across exists for a failure while playing:** `KlangCommLink.Feedback.PlaybackFailed` with
  `SYSTEM_PLAYBACK_ID` (the processor stopped), which `KlangPlayer` prints with `println`.
- **The app's side is ready:** an error logged with the `Logger` reaches `spine.console`, and its count shows on the
  bottom panel's key. Nothing opens the Console by itself yet.

## Proposal

- **The engine says it failed, with a reason.** A backend that cannot start (the script not loaded, the first
  exchange answered with another wire hash) reports it to the player as one typed failure, with what the user can
  do ("the audio engine's script could not be loaded", "the page and the audio engine are of different builds:
  reload"). The player's status becomes FAILED also when the failure comes after it was READY.
- **It is logged with the `Logger`** (area Engine), so it is in the app's Console and in the browser's.
- **The Console opens.** The layout follows `spine.player.status`: on FAILED it shows the bottom panel at the
  Console's tab (`showBottom(ConsoleTool.TAB_ID)`), once per failure, not on every redraw. State down by a stream,
  no call from the engine into the UI.
- **A wrong wire hash is found at the start,** by a first message each way, not on the first note played.

This touches the engine (`audio_be`, `klang`): the engine's rules apply (`audio/CLAUDE.md`), and other sessions work
there now (the audit fixes after KLANG-00420), so the engine part is agreed with the maintainer before it starts.

## Decide before implementing

- **D1, how the failure crosses:** a new feedback for the start (beside `PlaybackFailed`), or the backend's job
  failing and the player catching that.
- **D2, what the user reads** for each of the two cases, and whether a wrong wire hash offers a reload key.
- **D3, the Console opening on other errors too** (an uncaught error of the app), or only for the engine's start.

## The work

1. Trace both failures to where they land today; a spec that makes each happen (a worklet address that does not
   exist, a stamp of another hash).
2. The typed failure from the backend to the player; the player's status FAILED; the log line.
3. The layout opens the Console on FAILED; a spec with a player that fails.
4. A hand test in Chrome and Firefox: the worklet's file renamed, a stale worklet served.

## Acceptance

- With the worklet's script missing, and with a worklet of another build, the app's Console shows one error that
  says what happened and what to do, and the Console is open.
- The Play key does not spin forever; it shows that the engine failed.

## History

- 2026-11-10: created from the maintainer's request.
