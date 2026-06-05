Below is a **detailed implementation plan** for a centralized sample preloader that can be reused across all playbacks.
It’s structured so a coding agent can execute it step‑by‑step.

---

# Implementation Plan: Centralized Sample Preloading

## Goal

Move sample preloading out of individual playbacks and into a shared, player‑level service that:

- caches samples across playbacks,
- avoids redundant loads and backend sends,
- keeps existing preloading signals consistent.

---

## 1) Introduce a shared `SamplePreloader`

**Location suggestion**
`src/commonMain/kotlin/io/peekandpoke/klang/audio_engine/SamplePreloader.kt`

**Responsibilities**

- Track which `SampleRequest`s are already sent to backend.
- Load PCM once, send to backend once.
- Handle concurrent requests for the same sample.
- Provide a simple async API.

**API sketch**

```kotlin
class SamplePreloader(
    private val samples: Samples,
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun ensureLoaded(
        playbackId: String,
        requests: Set<SampleRequest>,
        signals: KlangPlaybackSignals? = null,
    )

    fun clear() // optional (on player shutdown)
}
```

**Key behaviors**

- For each `SampleRequest`:
    - If already sent → skip.
    - If loading in progress → await completion.
    - Else → load PCM, send `Sample.Complete` (or `NotFound`), mark as sent.

---

## 2) Add concurrency-safe request tracking

**Data structures**

- `sent = mutableSetOf<SampleRequest>()`
- `inFlight = mutableMapOf<SampleRequest, Deferred<Unit>>()`
- Use a lock or `Mutex` to guard concurrent access.

**Rule**
All requests should share the same in‑flight job so duplicates don’t trigger redundant loads.

---

## 3) Wire preloader into `KlangPlayer`

**Add field**

- `val samplePreloader = SamplePreloader(...)`

**Constructor dependencies**

- Use `options.samples`
- Use `::sendControl`
- Use `playbackScope` and `playbackFetcherDispatcher`

**Shutdown**

- On `KlangPlayer.shutdown()`, clear preloader cache if needed.

---

## 4) Update `StrudelPlaybackController` to use preloader

**Replace `preloadSamples()` logic**

- Instead of loading and sending samples directly, collect `SampleRequest`s and call:
    - `player.samplePreloader.ensureLoaded(playbackId, requests, signals)`
- Emit existing signals in the preloader (see next step), so the controller no longer emits them.

---

## 5) Move Preloading signals into preloader

**Signals to emit**

- `PreloadingSamples(count, samples)`
- `SamplesPreloaded(count, durationMs)`

**When**

- Only emit when there are new, previously-unsent requests.
- If all requests are already sent, return immediately and **do not emit** (prevents UI noise).

---

## 6) Handle “preload gate” for one‑shot

To reduce delay on repeated plays:

- If `ensureLoaded` finds all samples already sent, it should return immediately.
- Controller should then start playback without waiting.

---

## 7) Keep backend request path consistent

Backend already requests samples via `Feedback.RequestSample`.
Ensure the preloader **does not interfere** with the existing request flow:

- The preloader is proactive.
- Backend can still request missing samples; those requests should call into the preloader or a shared “ensure” path.

---

## 8) Update call sites

- Any place in playbacks that previously used internal `samplesAlreadySent` should:
    - remove that cache,
    - rely on the central preloader.

---

## 9) Acceptance tests / checks

- First play: preload happens, signals emitted.
- Second play same sample: **instant** playback (no preload delay).
- Different sample: preload happens once per sample.
- Backend `RequestSample` still works.

---

## Optional enhancements (future)

- Expose `isLoaded(request)` in preloader.
- Add `preload(bank, sound)` API for UI hover preload.

---

If you want, I can also provide a proposed class diagram or a concrete sequence of code edits to match your current
modules.
