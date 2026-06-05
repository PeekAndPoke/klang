Below is the **full, updated plan** using the name **KlangPlaybackContext**, ready to hand to a coding agent.

---

# Implementation Plan: KlangPlaybackContext

## Goal

Replace the growing constructor parameter lists for playbacks with a single **KlangPlaybackContext** object owned by
`KlangPlayer`.

---

## 1) Create `KlangPlaybackContext`

**Location**  
`src/commonMain/kotlin/io/peekandpoke/klang/audio_engine/KlangPlaybackContext.kt`

**Contents (minimum)**

- `playerOptions: KlangPlayer.Options`
- `samplePreloader: SamplePreloader`
- `sendControl: (KlangCommLink.Cmd) -> Unit`
- `scope: CoroutineScope`
- `fetcherDispatcher: CoroutineDispatcher`
- `callbackDispatcher: CoroutineDispatcher`

**Optional**

- `logger` / `signals` hooks if useful later

---

## 2) Add `playbackContext` to `KlangPlayer`

**Implementation**

- Construct once and expose as `val playbackContext: KlangPlaybackContext`
- Populate it using existing fields:
    - `options`
    - `samplePreloader`
    - `::sendControl`
    - `playbackScope`
    - `playbackFetcherDispatcher`
    - `playbackCallbackDispatcher`

---

## 3) Update playback constructors

**Targets**

- `ContinuousStrudelPlayback`
- `OneShotStrudelPlayback`
- `StrudelPlaybackController`

**Changes**

- Replace params:
    - `playerOptions`, `samplePreloader`, `sendControl`, `scope`, `fetcherDispatcher`, `callbackDispatcher`
- With:
    - `context: KlangPlaybackContext`

**Internal usage**

- `context.playerOptions`
- `context.samplePreloader`
- `context.sendControl`
- `context.scope`
- `context.fetcherDispatcher`
- `context.callbackDispatcher`

---

## 4) Update player helpers

**Where**
`playStrudel(...)` and `playStrudelOnce(...)`

**Change**

- Pass `playbackContext` instead of individual parameters.

---

## 5) Update any other construction sites

Search for any direct construction of:

- `ContinuousStrudelPlayback`
- `OneShotStrudelPlayback`
- `StrudelPlaybackController`

Update them to use `KlangPlaybackContext`.

---

## 6) (Optional) Define an interface for testability

If you want easier unit testing:

- Define `interface KlangPlaybackContext` with the same fields.
- Use `DefaultKlangPlaybackContext` inside `KlangPlayer`.

---

## Acceptance Criteria

- All playbacks compile using `KlangPlaybackContext`.
- Behavior unchanged (no runtime changes).
- `KlangPlayer` remains the single owner of playback dependencies.
- Constructors become stable as new dependencies are added.

---

If you want, I can draft the actual class skeleton and a precise change list per file.
