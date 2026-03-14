Below is a **task description for a coding agent** based on your preferred API (two methods, no mode flags). It’s ready
to drop into `docs/agent-tasks/`.

---

# Task: Add Deferred‑based Sample Preloader API (Two Methods)

## Goal

Replace the current “mode flag” approach with **two separate methods** on `SamplePreloader`:

1) **Initial preload**: returns a `Deferred<Unit>` that completes when the backend confirms it has received samples. The
   caller decides if/when to time out.

2) **Lookahead preload**: fire‑and‑forget, does not wait for backend confirmation.

---

## Requirements

### A) SamplePreloader API

Add two methods:

```kotlin
fun ensureLoadedAsync(
    playbackId: String,
    requests: Set<SampleRequest>,
    signals: KlangPlaybackSignals? = null,
)

fun ensureLoadedDeferred(
    playbackId: String,
    requests: Set<SampleRequest>,
    signals: KlangPlaybackSignals? = null,
): Deferred<Unit>
```

**Semantics**

- `ensureLoadedDeferred`:
    - loads + sends samples if missing
    - completes when backend **acknowledges receipt**
    - completes immediately if all samples are already sent
- `ensureLoadedAsync`:
    - triggers loading if missing
    - does not wait for backend ack

---

### B) Backend acknowledgement

Add a feedback message from backend to frontend confirming sample receipt.

**Options (pick one)**

- Per-sample: `Feedback.SampleReceived(req)`
- Batch: `Feedback.SamplesReceived(playbackId, requests)`

Per‑sample is simpler and matches existing request flow.

**Frontend**

- Feed backend acks into SamplePreloader so it can complete the corresponding deferred(s).

---

### C) In‑flight de‑duplication

Ensure concurrent requests for the same `SampleRequest` share one in‑flight job:

- Keep a `Map<SampleRequest, Deferred<Unit>>`
- If already in flight, reuse it
- If already sent, return a completed Deferred

---

### D) Signals

- `PreloadingSamples` and `SamplesPreloaded` are emitted **only** when new samples are actually loaded.
- For lookahead calls, `signals = null` to avoid UI noise.

---

## Integration points

### 1) StrudelPlaybackController initial preload

Replace current preload call with:

```kotlin
val deferred = samplePreloader.ensureLoadedDeferred(...)
deferred.await() // caller can wrap with timeout
```

Caller controls timeout (e.g., in controller):

```kotlin
withTimeoutOrNull(1000) { deferred.await() }
```

### 2) Lookahead & backend-requested samples

Replace with:

```kotlin
samplePreloader.ensureLoadedAsync(...)
```

---

## Acceptance Criteria

- Initial preload waits for backend ack (or caller timeout).
- Lookahead does not block playback.
- Repeat playbacks do not re‑load the same samples.
- Duplicate concurrent preload requests are coalesced.
- Signals are emitted only for actual load events.

---

If you want, I can also provide a short test checklist for the new behavior.
