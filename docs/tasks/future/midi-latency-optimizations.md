# MIDI / realtime latency: what is left to win

Status: **good enough for now (2026-08-31), recorded so it is not re-derived.** Written after the
first round of work on the realtime path, which removed the largest software term. Nothing here is
scheduled. Different axis from `high-performance-audio-backend.md`, which is about CPU throughput.

Measured feel after the first round: roughly 15 to 25 ms key-to-ear, dominated by the output
device. Noticeable to the player, not to a listener.

## Read the number before touching anything

`KlangPlayer` logs this once at startup:

```
[KlangPlayer] Diagnostics: sampleRate=…, baseLatency=Xms, deviceLatency=Yms, measuredLatency=Zms
```

`measuredLatency` is `baseLatency + deviceLatency + the 5 ms house limiter lookahead`. Real
key-to-ear is that plus ~1.5 ms for the block hop. **Which of the terms is big decides which item
below is worth doing**, and they differ by an order of magnitude in price.

## Already done, do not redo

- **The 10 ms poll is gone.** `AudioBackend.pump()`, called from `KlangPlayer.sendControl`, drains
  the control ring synchronously instead of leaving it for `JsAudioBackend`'s `setTimeout`. This
  was the single biggest software term and it also removed the jitter, which is worse than the
  mean. `pump()` **drains** the ring, it does not bypass it (the ring is still the JVM thread
  handoff, the startup queue, and the ordering guarantee).
- **`RingBuffer.prepend()` for realtime events: rejected.** It inverts note-on/note-off into
  hanging notes, can put a `StartRealtimeVoice` ahead of the `RegisterIgnitor` that names its
  sound (silent note), and does not address drain *timing* anyway. If a priority lane is ever
  justified, the safe shape is a second ring drained first, FIFO within itself.

## The items, most valuable first

### 1. The output device is usually the biggest term, and it is not our code

Confirmed by ear 2026-08-31: a Bluetooth headset adds clearly audible latency, a cable removes it.
On Linux the PipeWire/PulseAudio quantum sets the rest. A native app on the same stack pays most of
this too, so it is not a browser tax. **Check this before spending a day in Kotlin.** See also the
`Bluetooth latency detection` note: Chrome reports 0 for `outputLatency` on Bluetooth, so the
displayed figure understates reality there.

### 2. The 5 ms house limiter lookahead, on the live path only

The largest fixed term we own. `MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS`. Maintainer, after
playing: dropping it to ~1 ms on the live path "should feel pretty real".

**It must not go global.** Latency is felt by the performer, not the listener, so it is a
*playability* property, not an audio-quality one. Lowering the constant would trade every
listener's transients (the limiter contributes ~0 dB during a transient without lookahead, and the
hard clip does the work, which is the documented "knock") for one player's feel. So this needs a
**per-path master**, and `MasterStage` is per-dispatcher, which means it rides on item 3.

Two traps when it happens:
- `MasterDefaultsSyncSpec` asserts `AUTHORED_LIMITER_LOOKAHEAD_SECONDS < HOUSE_…` and
  `HOUSE_ATTACK == HOUSE_LOOKAHEAD`. 1 ms is fine; 0 breaks the first assertion.
- `JsAudioBackend` hardcodes the constant into the reported `outputLatencyMs`. If the value becomes
  per-path, that read has to move with it or every audio-aligned visual starts lying.

### 3. Two AudioContexts: playback (deep buffer) + interactive (shallow)

The vehicle for item 2, and the answer if `baseLatency` turns out to be the big term. Assessed in
depth 2026-08-31 and deferred behind measurement, not rejected.

Routing rule that makes it work: per-playback commands go to their channel, `SYSTEM_PLAYBACK_ID`
commands **broadcast** to all channels. Without the broadcast, `SamplePreloader.sent` reports
"already sent" and the second backend silently has no samples. Model it as **N channels where the
platform picks N**: JS creates two, JVM stays at one (two `SourceDataLine`s is worse than one small
buffer), so `KlangOfflineRenderer` is untouched.

Hazards, worst first:
1. **The safety brick stops being a guarantee.** `MasterStage` is per-dispatcher and clips to
   Int16, so two independent limiters sum in the OS mixer. Two full-scale streams make +6 dB.
2. **Two clocks.** `BackendClockSync` documents "one backend clock, one offset", and both backends
   emit `Diagnostics` under `SYSTEM_PLAYBACK_ID`, so they are indistinguishable at the merge point.
   Feed it two drifting clocks and UI highlights wander off the music: silent, and horrible to
   debug. The sync must follow the **playback** channel only. Tag feedback with its source at the
   receive site.
3. Diagnostics aggregate **per field**: headroom `min` (the worse one glitches), voices sum,
   cylinders concatenate.
4. Analyzer composite is contained (only `Oscilloscope.kt` and `Spectrumeter.kt` consume
   `getAnalyzer()`). FFT sums in linear power. The summed waveform is a picture, not a
   measurement: the contexts are not sample-aligned.
5. Doubled warmup, JIT (separate JS realms), and `SampleStore`.

And the honest limit: **the split buys buffer depth, not CPU isolation.** Both render threads still
compete for the same cores, and the interactive one has the tighter deadline.

### 4. Numeric `latencyHint`

`AudioContextOptions.latencyHint` now accepts a **number of seconds** as well as the three presets
(the external was typed `String?`, so this used to be unreachable). Somewhere between
`"interactive"` and `"playback"` there may be a value where songs stop stuttering and the keys stay
immediate, which would make item 3 unnecessary. One line at `JsAudioBackend`, then read the console.

Two traps. It is **seconds**, so `0.02` is 20 ms and `20` asks for a twenty-second buffer. And
raising it costs more than output buffering: the browser renders several quanta per device callback
and the worklet's `port.onmessage` only runs *between* callbacks, so a bigger buffer also coarsens
command delivery, re-adding as note-on quantization exactly what `pump()` removed. The sweep is
therefore not a pure latency-versus-stability trade; the shallow end is worth more than it looks.

### 5. Sub-block onset for realtime voices (needs a timestamp)

Worth 0 to 2.7 ms, and the real argument is **jitter**, not the mean: every note currently lands on
a block grid, so identical playing scatters onsets across one block.

The machinery exists. `Voice.render` clips a voice into the block by
`offset = startFrame - blockStart`, and the pattern path depends on it for groove. The realtime
path cannot use it because `StartRealtimeVoice` carries no timestamp by design, and the worklet has
no sub-block clock to recover one from (`currentTime` advances once per quantum). So this needs a
timestamp from the frontend, which re-introduces the FE↔BE clock coupling the realtime path was
built to avoid. `MidiConnector` already captures `evt.timeStamp`.

**Not** worth doing by deferring voice creation to the top of the render loop: `startRealtimeVoice`
already stamps `cursorFrame + blockFrames`, which is the *correction* for the cursor being stale
(commands drain between blocks), not an added block. A fresh-voice list would compute the same
frame. Work moves, timing does not.

### 6. A second ring for sample uploads

Not latency on its own, but it keeps `pump()` bounded. Today a `Sample.Complete` arriving means
`toChunks` runs its multi-megabyte split inside the `sendControl` call stack. Route `Cmd.Sample` to
its own ring, let `pump()` drain only musical commands, and leave the timer draining samples at its
current rate. No new ordering hazard: chunks already go through `sampleUploadBuffer` one per tick,
so PCM already arrives after voices queued behind it, and the backend is built for that
(`requestIfMissing`, and the preloader awaits `SampleReceived`).

While there: chunking originally existed to keep kotlinx-serialization decode off the audio thread.
That cost is gone (KSP wire codec). What remains per chunk is one structured clone plus the
`copyInto` in `SampleStore.addSample`, linear in chunk size, so chunk size is now a **bound on the
worst-case audio-thread hiccup**, not a throughput limit. If loads feel slow, raise how many chunks
are forwarded per tick, not the chunk size.

## Tried and removed: probing the hardware sample rate

A startup probe (bare `AudioContext()` to learn the device's native rate, warn when it differs from
the context rate, so a hidden resampler would be visible) was built and **removed the same day**,
before it ever shipped.

It does not work where it matters. At app init the context is deliberately left suspended (no user
gesture yet), and a suspended context has not opened an output stream, so its `sampleRate` is the
browser's current *default-device* value rather than the rate the stream will negotiate. A Bluetooth
headset that comes up at 44.1 kHz when the stream finally opens reads as 48 kHz at probe time: no
warning, in precisely the case the probe existed to catch. It also warns falsely if the output
device is switched between init and play. On top of that it added a third live `AudioContext` to
startup, with an un-awaited `close()`, on a path that already leaks one per failed start.

If this is ever wanted, it has to be measured **after** the real context is running and resumed,
not from a probe at init.
