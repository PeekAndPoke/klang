# Layer 1: the wire

**What I read** (in full unless noted)

- The brief's reading list: `audio/CLAUDE.md`, `audio/MEMORY.md` (the sections on the wire, the laws, the guardrails and
  the open threads in full; the History only by search), `audio/ref/data-model.md`, `audio/ref/architecture.md`,
  `audio/ref/numerical-safety.md`, `.claude/skills/review-loop/audio-constraints.md`, the leads in KLANG-00420,
  `hot-path-lint.txt` (the rows of my files).
- `audio_bridge/src/commonMain/kotlin/`: `VoiceData.kt`, `ScheduledVoice.kt`, `RealtimeVoice.kt`, `MonoSamplePcm.kt`,
  `SampleMetadata.kt`, `SampleRequest.kt`, `infra/KlangCommLink.kt`, `WireFormat.kt`, `WireName.kt`, `WireShared.kt`,
  `DistortionShapes.kt`, `LfoShapes.kt`, `AdsrCurves.kt`, `KlangPatternEvent.kt` (the gate part),
  `constants/MasterLimiterDefaults.kt`, `constants/RangeDefaults.kt`.
- `audio_bridge/src/jsMain/kotlin/WireCodecSupport.kt`.
- The generator, `audio-wire-codec-ksp/src/main/kotlin/WireCodecProcessor.kt`, in full.
- The generated codec, `.build-claude/audio_bridge/generated/ksp/js/jsMain/kotlin/io/peekandpoke/klang/audio_bridge/wire/WireCodecGenerated.kt`
  (2756 lines, built 2026-11-06 12:12, fifteen minutes older than the last commit under `audio_bridge/src/commonMain`;
  its hash equals the pinned one). Read as a sample: `ScheduledVoice`, `VoiceData`, `RealtimeVoice`, `KatalystDsl`, the
  `IgnitorDsl` dispatch, `Cmd` dispatch, `Cleanup`, `RegisterIgnitor`, `Sample.Chunk`, `SampleMetadata`, `AdsrDef`.
- `common/src/*/kotlin/infra/KlangMap.kt` (all three), `KlangRingBuffer.kt`, `KlangMinHeap.kt` (push, pop, `removeWhen`).
- The receiving side: `audio_be/src/jsMain/kotlin/WorkletContract.kt`, `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt`,
  `audio_be/src/jvmMain/kotlin/JvmAudioBackend.kt`, `audio_be/src/jsMain/kotlin/JsAudioBackend.kt` (lines 60 to 235 and
  320 to 395), `PlaybackEngineDispatcher.kt`, `SampleStore.kt`, `voices/VoiceScheduler.kt` (lines 30 to 743),
  `voices/VoiceFactory.kt`, `voices/voice_channel.kt` (the mix), `ignitor/SampleIgnitor.kt` (`generate`),
  `PlaybackGain.retarget`, `SoloTracker.record`, `Cylinders.cylinderFor`, the `Param` arm of `IgnitorDslRuntime.kt`,
  `IgnitorRegistry.register` and `createExciter`, `HouseStage.process` and the `DcBlocker` (to size finding L1-01).
- The sending side, as far as it decides what a wire field can hold: `sprudel/.../SprudelVoiceData.kt` (`toVoiceData`,
  `foldedGain`), `SprudelPatternEvent.gateSeconds`, `SprudelVoiceValue` (`div`, `asDouble`),
  `klang/.../KlangPatternScheduler.kt` (the parts that build `ScheduledVoice` and send), `KlangPlayer.sendControl`,
  `AnnounceOnceRegistry.kt`, `InlineDslRegistrar.kt` (the KDoc), `ContinuousPlayback.kt`, `SamplePreloader.kt` (first
  120 lines), `src/jsMain/.../KlangCodePlaybackCtrl.kt` (the play path).
- Tests: `audio_bridge/src/jsTest/kotlin/WireCodecRoundTripSpec.kt` (lines 300 to 545 in full, the rest by title),
  `WireSharedSpec.kt` and `IgnitorDslWireCodecSpec.kt` (titles), `KlangMapSpec.kt` (the NaN row),
  `VoiceGainWireSpec.kt` (the NaN rows), `InlineDslRegistrarTest.kt` (first 170 lines).

**What I did not**

- `IgnitorDsl.kt` (3366 lines), `KatalystDsl.kt`, the builders, `IgnitorDslClassic.kt`, the optimizer: only searched.
  I did not check every node's field for a unit or a default.
- `BodyMaterials.kt`, `VowelBands.kt` (the tables and `slotIndexAt`), and the constants files other than the two named:
  I only checked that every `const val` in `constants/` has a reader outside that folder (all have). I did not compare
  a constant's value with what its readers do. The brief's question "defaults that differ between the bridge constants
  and their readers" is therefore NOT answered by this report.
- `audio_fe` (how a `SampleRequest` is resolved), `WarmupRunner`, `OfflineBackend`, `KlangAudioRenderer`, `KlangTime`,
  the analyzer buffers, `Voice.kt` beyond the lines that read a wire field, the Katalyst readers of `katalystParams`.
- The commonTest specs of `audio_bridge` (`CatalogueIndexSpec`, `AdsrCurvesSpec`, `KatalystDefaultsSyncSpec`).
- I ran nothing but `grep`, `git log` and the lint's `--help`. Nothing here was executed; every trace is by reading.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|------|---------|-------|
| Hot-path lint: "the diagnostics message built on the audio thread (`PlaybackEngineDispatcher.kt:308`)" | CONFIRMED | `emitDiagnostics` (`PlaybackEngineDispatcher.kt:292` to `336`) builds an `ArrayList`, one `CylinderState` per rented cylinder of every rendering engine (`:356`) and one `Diagnostics` every `diagnosticsEveryFrames` (960 frames at 48 kHz, so every 8th block, about 47 a second). On JS the worklet then encodes it into JS objects and posts it (`KlangAudioWorklet.kt:208` to `211`). Periodic, not per block. See L1-06. |
| Hot-path lint, the rule question (once per note, or periodic, on the audio thread) | NOT MINE to decide | One fact for the answer: on JS every inbound message is decoded on the audio thread (`KlangAudioWorklet.kt:132` to `135`, `port.onmessage` runs on the worklet's thread between blocks), so "per message" is a third class next to "per note" and "periodic". |
| Dead-knob finder: 13 keys no door writes, reachable by raw `ignp` / `katp` | BY DESIGN at the wire | The slot maps carry any string key (`VoiceData.kt:40`, `:67`); the wire validates no key. Whether each of the 13 should have a door is layers 3 to 7. |
| Stage probe 1 to 13 | NOT MINE | Stage behaviour. Two of them size my findings: lead 1 (the duck latches on a NaN for good) and lead 8 (shimmer) are what a NaN that enters through L1-01 or L1-02 meets downstream. |
| Block-framing 1 to 3, clicks 1 to 8, aliasing 1 to 7, cost 1 to 9 | NOT MINE | None is caused by what crosses the wire or how it is decoded. |

## Findings

### L1-01 A NaN `pan` is the one typed wire number read raw: the voice writes NaN into its cylinder

- **Severity**: major
- **Tag**: safety
- **Where**: `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt:363` (`pan = data.pan ?: 0.5`),
  `audio_be/src/commonMain/kotlin/voices/voice_channel.kt:48` to `49`, `:86` to `87`, `:127` to `128`;
  the sender `sprudel/src/commonMain/kotlin/SprudelVoiceData.kt:924` (`pan = pan`),
  `sprudel/src/commonMain/kotlin/lang/lang_dynamics_level.kt:258`.
- **What**: `audio/ref/numerical-safety.md` ("Non-finite inputs") states the rule: a non-finite value a pattern writes
  reads as unset, at each reader once, because "a NaN that gets into a voice ... reaches the cylinder mix, where
  nothing scrubs it". `gain` follows it (`VoiceFactory.kt:136`), `cull` does (`Voice.kt:213`), `solo` does
  (`SoloTracker.kt:79`), every slot does (`IgnitorDslRuntime.kt:417`). `pan` does not. Input: `VoiceData.pan = NaN`.
  `VoiceFactory.kt:363` hands NaN to `Voice`. `voice_channel.kt:48`: `voice.pan.coerceIn(0.0, 1.0)` returns NaN (both
  of `coerceIn`'s compares are false for NaN). `:49` `panAngle = NaN`. `:86` `gainL = cos(NaN) * effectiveGain = NaN`,
  `:87` the same for the right. `:100` to `101` add `signal * NaN` to `outL` and `outR` for every frame the voice renders
  (the ramped path, `:127` to `135`, the same). Expected: the voice plays at centre (0.5). Actual: the cylinder's mix
  buffer is NaN for the whole life of the voice. From there it is other layers' ground, but the size is known from the
  probes: the house DC blocker recovers (`flushState`, `LowPassHighPassFilters.kt:470`) and the house limiter stores a
  non-finite sample as 0.0 (`HouseStage.kt:156` to `158`), so at the least the WHOLE output, every playback, is silent
  while that voice sounds; a duck on the path stays NaN for good (stage probe lead 1). An infinite pan is harmless
  (`coerceIn` gives 0 or 1).
- **Reach**: `note("c3").pan("NaN")` in sprudel: a string atom parses to NaN (`SprudelVoiceValue.kt:182`,
  `toDoubleOrNull`; the project's own record KLANG-00309 says `"NaN"` and `"Infinity"` both parse), `panMutation`
  stores it, `toVoiceData` passes it. Also any Kotlin caller of `pan(Double.NaN)`, and a realtime voice.
- **Settle it**: copy `VoiceGainWireSpec`'s row "a NaN gain reads as unset" for pan: build a voice from
  `VoiceData.empty.copy(pan = Double.NaN)`, render one block, assert the cylinder mix is finite and bit-equal to the
  unset-pan render. It is red today if this finding is right.
- **Confidence**: high for the engine half (traced line by line); medium for the sprudel atom (read, not run).

### L1-02 A non-finite `freqHz` on a sample voice makes every sample after the first NaN

- **Severity**: major
- **Tag**: safety
- **Where**: `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt:112`, `:178` to `181`;
  `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:97` to `109` (and the drift path `:66` to `83`);
  the sender `sprudel/src/commonMain/kotlin/SprudelVoiceData.kt:915`, `lang/lang_tonal_note.kt:362`.
- **What**: Input: a sample voice (`sound` is no registered instrument) with `VoiceData.freqHz = NaN`.
  `VoiceFactory.kt:178`: `targetPitchHz = NaN`. `:179`: `(NaN / base).coerceIn(1/32, 32)` is NaN (`coerceIn` again).
  `:181`: `rate = NaN`. `SampleIgnitor.generate`, clean path: frame 0 reads `pcm[0]` (playhead 0, `frac` 0), then
  `:109` `ph += rate` makes the playhead NaN. Frame 1: `:97` `ph < 0.0 || ph >= stopFrame` is false for NaN, `:100`
  `base = NaN.toInt() = 0`, `:101` `0 >= pcmMax` false, `:104` `frac = NaN - 0.0 = NaN`, `:107`
  `buffer = a + (b - a) * NaN = NaN`. Every later frame the same. The NaN runs through `classic()` and the channel into
  the cylinder, as in L1-01. The same result without any NaN on the wire when the sample's own `pitchHz`
  (`Cmd.Sample.Complete.pitchHz` / `Chunk.pitchHz`, frontend metadata) is 0 and the voice's `freqHz` is 0 (0/0), or
  when `pitchHz` itself is NaN. An infinite `freqHz` is harmless (the ratio clamps to 32).
  Expected by the stated rule: a non-finite `freqHz` reads as unset, the sample's own pitch. The oscillator branch
  (`:144`, `freqHz ?: 0.0`) passes the NaN on to the tree; `audio/MEMORY.md` says the sources gate a non-finite
  frequency to silence, which I did not verify per source (see Suspicions).
- **Reach**: `s("bd").freq("NaN")` (the `freq` door writes `freqHz` raw, `lang_tonal_note.kt:362`). A sample whose
  metadata carries pitch 0 played with `freq(0)`.
- **Settle it**: a `VoiceFactory` spec: one sample voice with `freqHz = Double.NaN` over a 100-frame PCM, render one
  block, assert every sample finite. Mutant for the fix: remove the guard, the spec goes red on frame 1.
- **Confidence**: high for the engine trace; medium for the sprudel atom.

### L1-03 "Announce once" and "the fork dies with the engine" contradict each other when a playback is started again

- **Severity**: minor (the app does not reach it today; the library API does)
- **Tag**: bug
- **Where**: `klang/src/commonMain/kotlin/AnnounceOnceRegistry.kt:44` to `57` (the `announced` set, never cleared),
  `klang/src/commonMain/kotlin/ContinuousPlayback.kt:31` (one registrar for the life of the playback object) and
  `:64` to `75` (`start` after `stop` is allowed), `KlangPatternScheduler.kt:239` to `249`;
  `audio_be/src/commonMain/kotlin/PlaybackEngineDispatcher.kt:83` to `94`, `:117` to `120`, `:133` to `140`, `:367` to
  `388`; `voices/VoiceScheduler.kt:69` (the fork is a field of the engine's scheduler).
  The contract: `KlangCommLink.kt` `RegisterKatalyst` KDoc ("Sent once per unique chain ... the same send-once
  contract as `RegisterIgnitor`"), `InlineDslRegistrar.kt` KDoc ("Both sides free it when the playback stops").
- **What**: Input: a `ContinuousPlayback` whose pattern uses an inline instrument (`sound(Ignitor(...))`) or an inline
  chain (`master(Katalyst(...))`); `start()`, `stop()`, wait until the engine has gone idle, `start()` again.
  First start: `announceAll` sends `RegisterIgnitor(name, dsl)`, the engine's fork stores it. Stop: `Cleanup`, the
  engine is `Stopped`, and `disposeDrainedEngines` (`:367`) removes and disposes it once idle; the fork goes with it.
  Second start: `SetGain` creates a fresh engine (`:187` to `188`), `announceAll` finds the name already in
  `announced` (`AnnounceOnceRegistry.kt:50`, `add` returns false) and sends nothing. `ScheduleVoices` arrives:
  `prefetchSampleSound` (`VoiceScheduler.kt:538`) does not find the name in the fork and asks the frontend for a SAMPLE
  of that synthetic name; at promotion `VoiceFactory.kt:114` to `115` reads `isOsci = false`, `isSample = true`,
  `getSample` is null, `makeVoice` returns null, `soundlessVoices++`. Every note of the inline instrument is silent;
  an inline master or cylinder chain is never found. The frontend side does NOT free its gate at the stop, contrary to
  the KDoc. The same happens when the second start meets the old engine in `Releasing` (`:117` to `120` detaches it and
  makes a fresh one). It does not happen when the restart is quick (the `Stopped` engine resumes, its fork intact),
  which would make it look intermittent.
- **Reach**: any caller that restarts a playback object: `ContinuousPlayback.start()` after `stop()`. The app itself
  drops the playback at stop and makes a new one with a new registrar (`KlangCodePlaybackCtrl.kt:236`, `:296` to
  `311`), so the editor's Play does not reach it. `KlangRealtimeVoicePlayback` holds one registrar too
  (`KlangRealtimeVoicePlayback.kt:32`); whether its engine can be stopped and reused I did not read.
- **Settle it**: a JVM spec over the real dispatcher (the `KlangPlaybackSyncSpec` rig): play a pattern with an inline
  Ignitor, stop, render blocks until `activePlaybackIds` is empty, start, render: assert `soundlessVoiceCount() == 0`
  and a non-silent block.
- **Confidence**: medium (every step read; not run; the realtime playback's lifecycle not read).

### L1-04 One NaN `startTime` in the queue can hide voices pushed after it until they are late, and they are dropped

- **Severity**: minor
- **Tag**: safety
- **Where**: `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt:72` (the heap's `less`), `:405` to `418`
  (no check at push), `:558` to `598` (the only non-finite guard, at the pop);
  `common/src/commonMain/kotlin/infra/KlangMinHeap.kt` (`before`, a tie falls back to push order).
- **What**: `less = a.startTime < b.startTime` is false both ways against a NaN, so the heap treats a NaN voice as a
  tie with every other and orders it by push number. That is not a strict weak order, and the heap property breaks.
  Input, pushed in this order: starts 5.0, NaN, 9.0, 0.6. The array is `[5.0, NaN, 9.0]`; 0.6 lands at index 3 under
  the NaN at index 1; `before(0.6, NaN)` is a tie and 0.6 was pushed later, so it stays. The root is 5.0.
  `promoteScheduled` peeks 5.0 and breaks until second 5 (`:560`). The 0.6 voice is not promoted at 0.6. At second 5
  the root pops, the NaN surfaces and is dropped by the guard at `:594`, then 0.6 surfaces, fails
  `absoluteStartSec >= nowSec` and is dropped as late (`droppedVoices++`). Expected: the NaN voice alone is dropped
  (the comment at `:594` says "a non-finite start is dropped like a late one"). Actual: a valid voice is lost too.
  Before the drop, a NaN event's `master`, `katalyst` and `solo` are still applied (`:571` to `579`; the solo is
  refused by `SoloTracker.record`'s own guard).
- **Reach**: internal only today: the pattern scheduler's times come from fixed-point cycles through the tempo map
  (`KlangPatternScheduler.kt:663` to `698`), and `legato` is guarded (`SprudelPatternEvent.kt:49`). A second frontend
  (MIDI file, a sequencer) is where it would arrive.
- **Settle it**: a `VoiceScheduler` spec scheduling starts 5.0, NaN, 9.0, 0.6 in one batch and rendering to second 1:
  the 0.6 voice must be active. Cheapest fix to test against: refuse a non-finite `startTime` at `scheduleVoices`.
- **Confidence**: high for the heap order (worked by hand from `push` and `before`); I did not read `siftUp` line by
  line, only `before` and the push.

### L1-05 A non-finite `playbackStartTime` on a playback's first voice makes the epoch NaN: every voice is dropped

- **Severity**: minor
- **Tag**: safety
- **Where**: `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt:491` to `506`, `:558` to `598`.
- **What**: Input: the first `ScheduledVoice` of a playback has `playbackStartTime = NaN`. `:498`
  `latency = maxOf(0.0, nowSec - NaN) = NaN` (`Math.max` on both platforms). `:499` `epoch = NaN + NaN`. `:503` sends
  `PlaybackAnchored(epochSec = NaN)` to the frontend, whose scheduler stores it (`KlangPatternScheduler.kt:306` to
  `308`). From then on `absoluteStartSec = epoch + head.startTime` is NaN for every voice: each is popped and dropped
  at `:594`, for the whole playback, until a `Cleanup` drops the context. With `-Infinity` the same (`+Inf + -Inf`).
  With `+Infinity`: `latency = 0`, `epoch = +Inf`, `:560` breaks for ever, nothing is promoted and the heap grows
  until the stop. Expected by the rule: a non-finite epoch request reads as unset, which here means "now".
- **Reach**: internal only: the sender is `requestedEpochSec` of the frontend's clock. Named because one field poisons
  a whole playback and its clock on the frontend, and one `isFinite` at `:498` closes it.
- **Settle it**: a dispatcher spec: `ScheduleVoices` with `playbackStartTime = Double.NaN`, render; assert the voice
  is active and the `PlaybackAnchored` epoch is finite.
- **Confidence**: high.

### L1-06 The diagnostics message is built, encoded and posted on the audio thread about 47 times a second

- **Severity**: minor
- **Tag**: realtime
- **Where**: `audio_be/src/commonMain/kotlin/PlaybackEngineDispatcher.kt:292` to `361`, `:434`;
  `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt:207` to `213`; `audio_be/src/jsMain/kotlin/WorkletContract.kt:46` to `48`.
- **What**: Per emission, on the audio thread: one `ArrayList`, one `CylinderState` per rented cylinder of every
  rendering engine (a song on 8 cylinders: 8 objects; two playbacks: 16), one `Diagnostics`. On JS the worklet then
  runs `encode_KlangCommLink_Feedback`: one JS object for the message, one array, one object per cylinder state, one
  object of 27 fields for `WarehouseStats` (the Kotlin snapshot is cached, its wire copy is rebuilt each time), then
  `postMessage`'s structured clone of all of it. About 47 times a second (960 frames at 48 kHz rounds up to 8 blocks),
  playing or idle. The only field the frontend needs at that rate is `backendNowMs` (its clock runs on it); the
  cylinder list and the warehouse numbers are gauges.
- **Reach**: every session, always.
- **Settle it**: the allocation check (`E-` cases) with the diagnostics emission stubbed out against the same case
  with it on; the difference per block times 8 is the cost per emission.
- **Confidence**: high that it allocates and when; the byte count is not measured.

### L1-07 The schema hash does not cover an enum's entries, the slot-key vocabulary, the catalogue index spaces or the codec's own format

- **Severity**: minor
- **Tag**: consistency
- **Where**: `audio-wire-codec-ksp/src/main/kotlin/WireCodecProcessor.kt:110` to `112`, `:167`, `:277` to `295`,
  `:423`, `:461` to `462`; the generated `WireCodecGenerated.kt:2484` to `2497`; `WorkletContract.kt:62` to `70`.
- **What**: The hash is `String.hashCode()` of: for a class, its codec id and each constructor parameter's name and
  type name with nullability; for a sealed type, its leaves' wire names. It moves on any added, removed, renamed or
  retyped field and on any wire-name change. It does not move on:
  1. **An enum's entries.** An enum is encoded as its ordinal (`:423`) and its signature is only its qualified name
     (`typeSig`, `:293`). `AdsrCurve` is on the wire this way (in `AdsrDef.Std`, inside every `Sample.Chunk`'s
     `meta`). Append an entry: a fresh frontend sends ordinal 6, a stale worklet's `AdsrCurve.entries[6]` throws in
     `port.onmessage` and the sample never completes. Reorder two entries: the wrong curve, silently. `AdsrCurves`'
     KDoc says "append only", but only a convention holds it.
  2. **The slot keys.** All voice doors and all cylinder knobs travel as string keys in the two maps. A renamed slot
     (`"lpf.freq"`) between two builds leaves the hash where it is, and the door silently does nothing on the stale
     side.
  3. **The catalogue index spaces** (`DistortionShapes`, `LfoShapes`, `AdsrCurves`, `BodyMaterials`, `VowelBands`):
     an appended shape arrives at a stale worklet as an out-of-range index and reads as the fallback, silently.
  4. **The codec's format**: the `#t` key, a `Set` as an array, an enum as an ordinal. A generator change that alters
     the encoding of unchanged types does not move the hash.
  5. **A unit or a meaning**: a field that keeps its name and type but changes what it measures.
  In production the worklet URL carries the bundle's hash (`JsAudioBackend.kt:105` to `110`), so the skew needs a
  cache that ignores the query, or the dev path, where the URL is plain. On a mismatch `requireSchema` throws on every
  message on BOTH sides: the worklet throws per command, and the main thread throws per feedback, about 47 times a
  second (the diagnostics), `BackendReady` included, so nothing is shown to the user and the player is never handed
  out (not traced past `klangPlayer()`'s await; see Suspicions).
- **Reach**: a developer's stale worklet; a user behind a cache that drops the query string.
- **Settle it**: in the processor, fold each enum's entry names into `typeSig` and a hash of the catalogue name lists
  and of `IgnitorDsl.Slots`' key names into the signature; the pinned-hash row then moves when an entry is appended.
  A processor test with a two-entry enum that gains a third shows today's hash unmoved.
- **Confidence**: high for what the hash covers; medium for the user-visible behaviour on a mismatch.

### L1-08 The round-trip spec never crosses a structured clone, several wire shapes have no row, and the receiving side has no test

- **Severity**: minor
- **Tag**: test
- **Where**: `audio_bridge/src/jsTest/kotlin/WireCodecRoundTripSpec.kt` (every row is `decode(encode(x)) shouldBe x`
  in one realm), `WireSharedSpec.kt:26` (the only `structuredClone`), `audio_be/src/jsMain/kotlin/WorkletContract.kt`,
  `audio_jsworklet/src/jsMain/kotlin/KlangAudioWorklet.kt` (no test source set).
- **What**: The real hop is `encode`, `postMessage` (structured clone), `decode`. Only `WireSharedSpec` goes through
  the clone. In `WireCodecRoundTripSpec` a value that the encoder passes through as a Kotlin object (a class
  instance, a `Long`, a `KlangMap`) would round-trip in the test and arrive broken in the worklet, where the clone
  strips the prototype. Mutants that stay green:
  - `encExpr` for a `Set` returns the Kotlin `Set` itself (and `decExpr` reads it back): no row has a non-null `tags`.
  - `wireDecodeSet` returns an empty set: the same.
  - `encode_VoiceData` passes `v.ignitorParams` through un-encoded and the decoder takes it as it is: equal in one
    realm, broken across the clone.
  - `requireSchema` removed, or `stamp` writing a constant: nothing tests `WorkletContract`.
  - The unknown-tag `throw` replaced by a default leaf: no row sends an unknown `#t`.
  No row for: an EMPTY non-null slot map, `ReplaceVoices` with `afterTimeSec = null`, `Feedback.SampleReceived`,
  `Cmd.Sample.Complete` (generated and on the schema, never sent on JS, `MonoSamplePcm` has no `equals`), a non-finite
  scalar field (`gain`, `pan`, a time), a key such as `"__proto__"` or `"1"` (see L1-10).
  The whole spec is jsTest: it runs only where the browser tests run. `audio/CLAUDE.md`'s "Build & Test" names only
  the three `jvmTest` tasks. The missing worklet tests are a known open thread (KLANG-00308 section 2).
- **Reach**: internal only.
- **Settle it**: give `WireCodecRoundTripSpec` the `hop` of `WireSharedSpec` in its one `roundTrip` helper, add the
  rows named, run the mutants above.
- **Confidence**: high.

### L1-09 The reference pages and several KDocs describe a wire that is not the one in the code

- **Severity**: minor
- **Tag**: doc
- **Where and what**:
  - `audio/ref/data-model.md:3`: "All types ... are `@Serializable`". None is (no `@Serializable` under
    `audio_bridge/src/commonMain`); the codec is the KSP one.
  - `data-model.md:13`: `playbackId` "unique ID for deduplication / replace". `ScheduledVoice.isDuplicate`
    deliberately ignores it (`ScheduledVoice.kt:29` to `35`).
  - `data-model.md` "MonoSamplePcm": `pcm: FloatArray`, `anchor: Int?`, `loop`, a `data class`. Code: `pcm: DoubleArray`,
    a plain class (`MonoSamplePcm.kt:20` to `24`); `anchor: Double`, non-null (`SampleMetadata.kt:10`).
  - `data-model.md` "SampleRequest": four non-null fields, `note: Double?`. Code: all four nullable, `note: String?`
    (`SampleRequest.kt:21` to `30`).
  - `audio/ref/architecture.md:74` and `:160`: 16 cylinders. Code: `MAX_CYLINDERS = 255`, ids fold by `%` and keep
    their sign, so 509 distinct cylinders per playback (`Cylinders.kt:41`, `:54` to `57`, `:222`).
  - `architecture.md` Cmd table: no `StartRealtimeVoice`, `StopRealtimeVoice`, `SetGain`; `Sample.Complete` "PCM bytes".
  - `VoiceData.kt:116` to `117`: "The backend registers the chain but does not read it yet (Katalyst step 1)". It
    reads it: `VoiceScheduler.kt:576`.
  - `KlangCommLink.kt` `toChunks` KDoc and `JsAudioBackend.kt:189` to `191`: "one chunk is about 512 KB (64 * 1024
    frames)". The one caller passes `128 * 1024` (`JsAudioBackend.kt:357`): 1 MB a chunk, so the stated bound on the
    audio thread's worst hiccup is half the real one.
  - `audio/ref/numerical-safety.md`: "`GraphCensus` reads the bag unguarded". It guards now (`GraphCensus.kt:311`,
    `:323`, `:363`, `:376`).
  - `InlineDslRegistrar.kt` KDoc: "Both sides free it when the playback stops". The frontend side does not (L1-03).
  - `WireCodecProcessor.kt:74` to `76`: `kotlin.Long` is a pass-through scalar. A Kotlin/JS `Long` is an object; the
    structured clone would strip it. No wire type has one today (searched the generated file), the stone rule bans it,
    and the generator is the place that could refuse it by name, as it refuses a stdlib `Map`.
- **Reach**: readers of the docs; the next auditor.
- **Settle it**: read each line against the file named.
- **Confidence**: high.

### L1-10 A slot map does not arrive the same on JS and on the JVM for two kinds of key

- **Severity**: note
- **Tag**: consistency
- **Where**: `audio_bridge/src/jsMain/kotlin/WireCodecSupport.kt:62` to `68` (encode into a plain `{}`), `:76` to `85`
  (`Object.keys`); the JVM passes the `KlangMap` by reference (`JvmAudioBackend.kt:84` to `85`, no codec).
- **What**: (a) Key `"__proto__"`: `o["__proto__"] = 1.0` on an object literal calls the prototype setter, which
  ignores a number, so the entry is lost on JS and kept on the JVM. (b) Keys that look like array indices (`"0"`,
  `"12"`): `Object.keys` lists them first, ascending, so the decoded map's order is not the sent order, against
  `KlangMap`'s stated contract ("insertion order") and against the spec row that pins order (which uses dotted names
  only). Equality is by content, so the replace dedup is not affected, and I found no reader whose result depends on
  the order of a slot map. No audible effect traced.
- **Reach**: `ignp("__proto__", 1)`, `ignp("0", 1)`.
- **Settle it**: two rows in `WireCodecRoundTripSpec`. A JS `Map` on the wire (the clone carries a `Map`) or an
  object made with `Object.create(null)` closes (a); only the `Map` closes (b).
- **Confidence**: high for the JS semantics; that nothing reads the order is a search, not a proof.

### L1-11 Both rings drop a message when full and no sender reads the result

- **Severity**: note
- **Tag**: safety
- **Where**: `common/src/commonMain/kotlin/infra/KlangRingBuffer.kt:18` to `26`; `klang/src/commonMain/kotlin/KlangPlayer.kt:330`;
  `audio_be/src/jsMain/kotlin/JsAudioBackend.kt:359`; every `feedback.send(` in `audio_be` (`PlaybackEngineDispatcher.kt:320`,
  `SampleStore.kt:98`, `:105`, `VoiceScheduler.kt:503`, `WarmupRunner.kt:305`).
- **What**: `send` returns false on a full ring (8191 entries; 32767 for the sample upload ring) and nobody looks.
  A lost `Cleanup` leaves an engine rendering for ever; a lost `SampleReceived` leaves the preloader waiting with no
  timeout (`SampleStore.kt:47` to `48` says so itself); a lost upload chunk leaves a sample `Partial` for good and
  never requested again. On JS the control ring is drained in the same call that fills it and the feedback ring every
  block, so only the upload ring can fill (more than 32767 chunks queued: not in ordinary use). On the JVM the
  feedback ring fills in about 175 s if the frontend's reader stalls (47 diagnostics a second), after which a
  `RequestSample` can be the message that is dropped.
- **Reach**: a stalled JVM frontend; not ordinary use.
- **Settle it**: count the refused sends in a field and put it on the diagnostics; a spec that fills the ring and
  reads the count.
- **Confidence**: high for the mechanism, low for it happening.

## Performance candidates

| Where | What | Rate | Measured cost | Expected gain | Risk to the sound |
|-------|------|------|---------------|---------------|-------------------|
| `PlaybackEngineDispatcher.kt:292` to `361` | L1-06: send `backendNowMs` at the fast rate and the gauges (cylinder list, warehouse) a few times a second or only on change; reuse one message object on the JVM | periodic, 47/s | not measured | most of the engine's steady idle allocation on JS, if the allocation check agrees | none |
| `VoiceData.tags` (`VoiceData.kt:136`), `WireCodecSupport.kt:38` to `55` | No file in `audio_be` reads `tags` (searched), yet each voice's set is encoded, cloned and rebuilt as a `LinkedHashSet` on the audio thread; a Kotlin/JS hash set pays the stdlib hashing the `KlangMap` guardrail names | per note, when a pattern uses `.tag` | not measured | one set and its entries per tagged voice | none (the UI reads tags from the frontend's own signal, `KlangPatternScheduler.kt:680` to `689`) |
| `SampleStore.kt:72`, `:90`, `:92`; `VoiceScheduler.kt:538` to `540`; `VoiceFactory.kt:162` | The store is a stdlib `mutableMapOf<SampleRequest, ...>` read on the audio thread: one `SampleRequest` allocated and one data-class hash over three strings at schedule time and again at activation, per sample voice. The guardrail "a map the engine reads by key on Kotlin/JS is a `KlangMap`" is not applied here | per note (twice) | not measured (the guardrail's own figure: 40 B per stdlib hash operation on V8, more for a data-class key) | two allocations and two string-hash passes per sample voice | none |
| `ScheduledVoice.playbackId`, `.playbackStartTime` (`ScheduledVoice.kt:15`, `:23`) | Both repeat per voice what the command carries once: the id is the `Cmd`'s, and only the first voice's `playbackStartTime` is ever read (`VoiceScheduler.kt:492` to `499`). The clone copies a string and a double per voice | per note | not measured | small; also removes the case where a voice's id differs from its command's (the context and the `PlaybackAnchored` take the voice's id, the routing the command's) | none |
| `WireCodecSupport.kt:77` | `Object.keys(o)` allocates an array per decoded slot map, two per voice | per note | not measured | one array per map | none |
| `Cmd.Sample.Chunk.data` over `postMessage` (`WorkletContract.kt:37`, `JsAudioBackend.kt:357`) | Each 1 MB chunk is cloned (copied on the main thread, materialised on the audio thread) and then copied again by `copyInto` (`SampleStore.kt:172`). A transfer list (`postMessage(msg, [data.buffer])`) removes the clone's copy; the chunk is a fresh `copyOfRange`, so it is free to transfer | per sample upload | not measured | about half of the audio thread's work per chunk | none |
| `IgnitorRegistry.kt:105` to `118` | `dsl.optimize()` runs on the audio thread for every `RegisterIgnitor` (in `port.onmessage` on JS, in the audio loop on the JVM) | per change (a live edit with a new inline instrument) | not measured | the optimizer pass leaves the audio thread if the frontend sends the optimized tree beside the authored one | none if the same optimizer runs; the wire grows |
| `VoiceScheduler.kt:290` to `304` | `dedupAgainstActive`: a `BooleanArray`, a filtered list, a boxed index per incoming voice, and `VoiceData.equals` (two map walks with a closure each on JS) for every pair with an equal start | per `ReplaceVoices` | not measured | small | none |

## Suspicions

- **A command that arrives after its playback's `Cleanup` revives or creates an engine that nothing stops.**
  `engineFor` (`PlaybackEngineDispatcher.kt:83` to `94`) resumes a `Stopped` engine and creates a missing one for
  `RegisterIgnitor`, `RegisterKatalyst`, `StartRealtimeVoice` and `ScheduleVoices`. `KlangPatternScheduler.cleanUpBackend`
  (`:329` to `333`) LAUNCHES the `Cleanup` on the fetcher's dispatcher; if a fetch round in flight sends its
  `RegisterIgnitor` and `ScheduleVoices` after it, the engine is `Playing` again with voices nobody will clean: a few
  notes after Stop and an engine that renders for ever. `SetGain`'s KDoc knows this class for itself only. Settle: read
  `stop()`'s ordering of the job cancel against the launch, or a spec that sends `Cleanup` then `ScheduleVoices` and
  asserts the engine is disposed some blocks later.
- **A NaN `freqHz` on an oscillator voice** goes to the tree as it is (`VoiceFactory.kt:144`). `audio/MEMORY.md`
  says the sources gate a non-finite frequency; I did not check each source, and stages that read `Freq` (key
  tracking, `fm`) are not sources. Settle: the stage probe with `freqHz = NaN` on every registered sound.
- **An exception in `handle` or `renderBlock` ends the audio for the session on both platforms.** The JVM loop has no
  catch (`JvmAudioBackend.kt:80` to `116`: the coroutine leaves through `finally`); a throw from
  `AudioWorkletProcessor.process` makes the browser stop calling it. `renderBlock` has a `finally` for the clock and
  no catch. I found no concrete throw path from a wire value (the optimizer is caught, the PCM allocation is caught),
  so this is not a finding. A chunk whose `chunkOffset + data.size` exceeds the first chunk's `totalSize` would throw
  in `copyInto` (`SampleStore.kt:172`), which needs two interleaved uploads of one `SampleRequest` with different
  PCM; whether the preloader can do that I did not read.
- **The same PCM may be stored once per note.** `SampleRequest` includes `note` and is the key of the frontend's
  `sent` set (`SamplePreloader.kt:39`) and of the backend's store (`SampleStore.kt:72`), and on JS each upload is its
  own copy. A melodic line over one sample file would then hold one `DoubleArray` per distinct note, for the life of
  the backend (the store never evicts). Settle: read `audio_fe`'s resolution of a request, then watch
  `sampleBytes` in the diagnostics while playing one sample at twelve notes.
- **`VoiceData.equals` with a NaN field** in `ScheduledVoice.isDuplicate`: I believe the JVM and Kotlin/JS agree
  (NaN equals NaN in a data class), and `KlangMapSpec` pins it for the maps; for the nullable `Double` fields I did
  not find a row.
- **On a schema mismatch the app may hang without a message** (L1-07): `BackendReady` cannot be decoded, and
  `klangPlayer()` awaits it. Settle: read the await for a timeout, or change the constant in a dev build.
- **Onset rounding at a large clock origin**: `VoiceFactory.onsetFrame` floors `(startTime - backendStart) * sampleRate`
  after `startTime` was formed as `epoch + relative`. A comment at `VoiceScheduler.kt:525` speaks of an origin near
  1.8e9 seconds; at that size a double's step is 2.4e-7 s, 0.011 frames at 48 kHz, enough to move a voice that sits
  exactly on a frame boundary one frame early. If both clocks really start near zero (as `performance.now()` and the
  worklet's frame clock suggest) this is nothing. Settle: print `clock.startTimeSec` on both platforms.

## Decisions worth revisiting

- **A stop does not close the gate of a timeline voice.** `VoiceScheduler.cleanup` (`:214` to `229`) releases only
  HELD realtime voices; a timeline voice, and a fixed-gate realtime voice, plays to its scheduled gate end. With an
  ordinary note that is a fraction of a second. With a long gate it is not a stop: `note("c3").legato(100000)`, a slow
  tempo, or a `gateEndTime` / `gateDurSec` of `+Infinity` from another frontend, which `VoiceFactory.kt:91` turns into
  `Int.MAX_VALUE` frames (12.4 hours at 48 kHz), exactly the horizon `heldGateHorizonSec` exists to avoid for held
  voices. The engine stays `Stopped` and sounding for that long. Releasing every open gate at `cleanup` (as for held
  voices) would make Stop mean stop and keep every release tail. I did not trace `Voice` past the factory, so this is
  an argument, not a finding.
- **The typed wire numbers are sanitised by their readers, one by one.** That is the stated design ("each reader
  substitutes at its own read, once"), and L1-01 and L1-02 are what it costs: two readers were missed. The engine has
  two entry points for a voice (`scheduleVoices`, `startRealtimeVoice`). One function there that reads the six numbers
  of `VoiceData` (`freqHz`, `gain`, `pan`, `solo`, `cull`, and the three times of the message) and turns a non-finite
  one into `null` would be the one place the brief asks about, is not a clamp (every finite value passes), and runs
  per note. The slot maps can keep their per-reader rule, where NaN is a declared value (`SLOT_UNSET`).
- **The JVM never runs the codec.** A command reaches the JVM engine by reference, so the offline renderer and the
  JVM specs cannot see a codec fault, a key that the JS object drops (L1-10), or a chunking fault (the `meta` that
  was lost on JS only, recorded at `SampleStore.kt:161` to `167`, is the precedent). The round trip needs only JS
  objects, but a JVM `encode`/`decode` pair over a plain map tree, used by the offline renderer in a test mode, would
  let the corpus render cover the wire.

## Listening tasks

None from this layer.

## Test truth

- **Pinned well**: every `IgnitorDsl` and Katalyst node's fields through the codec (about 130 rows), NaN as a slot
  value and as a `Param` default, the identity of a shared node across a real structured clone, the identity table's
  lifetime (a failed decode leaves nothing), the schema hash's value, the slot maps' order for dotted keys, the wire
  object's JSON against a golden, `Boolean?` false against null, the announce-once gate, `KlangMap`'s NaN equality on
  both platforms, a NaN `gain` at the factory.
- **Not pinned**: everything in L1-08; a non-finite `pan`, `freqHz`, `startTime`, `gateEndTime`, `playbackStartTime`,
  `gateDurSec` at the engine's door (L1-01, L1-02, L1-04, L1-05); a restart of a playback with an inline instrument
  (L1-03); a full ring (L1-11); a schema mismatch.
- **Mutations that would survive** (each named in its finding): the `Set` passed through un-encoded; `wireDecodeSet`
  returning an empty set; a slot map passed through un-encoded; `requireSchema` removed; the unknown-tag `throw`
  replaced by a default; `pan = data.pan ?: 0.5` replaced by any guard or none (no NaN row); the NaN guard at
  `VoiceScheduler.kt:594` kept but the heap fed a NaN (no row with a NaN among finite starts); an appended
  `AdsrCurve` entry (the hash row stays green).

## For the audit ruleset

- What worked: starting from the brief's question "one place per field" and walking EVERY field of `VoiceData` and of
  the two voice messages to its first read. The two major findings are the two fields that walk did not find a guard
  for. A field table (field, first reader, what a NaN, an infinity and a negative do there) should be a required
  artefact of a wire audit.
- What worked: reading the sender far enough to say whether a bad value is reachable (`toVoiceData`, the string
  atom). Without it every finding here would read "internal only". The brief should say that a layer's auditor may
  read one step outside the layer for reach, and must say so.
- What did not: the scope was too wide for one reader at full depth. I traded the constants ("defaults that differ")
  and the two big DSL files for the receiving side, and say so above. A wire audit should be split: the messages and
  their readers; the DSL node types, their units and defaults.
- What did not: nothing could be run. Three findings (L1-01, L1-02, L1-05) would each be settled by a ten-line spec.
  An auditor allowed one scratch spec file under the build lock (reviewers already may build) would hand over
  confirmed findings instead of traces.
- The probes do not look at the wire at all (no probe feeds a non-finite TYPED field or a malformed message). A
  small "wire poison" probe, each `VoiceData` number and each message time at NaN, both infinities and a huge value,
  through the real dispatcher, would have found L1-01, L1-02, L1-04 and L1-05 mechanically.
- The generated codec in `.build-claude/` can be older than the source. The brief should tell the auditor to compare
  its timestamp and its hash with the pinned one before trusting a sample, as done above.
