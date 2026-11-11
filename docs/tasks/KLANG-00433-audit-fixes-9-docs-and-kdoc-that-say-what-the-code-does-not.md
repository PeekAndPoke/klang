# KLANG-00433: Audit fixes 9: docs and KDoc that say what the code does not do, dead code, and the tests the audit found missing

> **Status (2026-11-11): PAUSED.** Landed; nine decisions pending with the maintainer.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L1-09 | (read only) | The reference pages and several KDocs describe a wire that is not the one in the code | not challenged; the auditor's reading in `layer-1-wire.md` |
| L2-08 | (read only) | Docs and comments that say what the code does not do | not challenged; the auditor's reading in `layer-2-voice-lifetime.md` |
| L3-15 | (read only) | Texts that say what the code does not do | not challenged; the auditor's reading in `layer-3-sources.md` |
| L4-12 | (read only) | Comments that say what the code does not do | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L5-12 | (read only) | Dead code and stale statements in this layer | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L6-08 | (read only) | Three ref pages and two KDocs describe a cylinder manager that no longer exists | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L7a-08 | (read only) | Ref pages and KDoc that say what the code does not do | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7b-10 | (read only) | The docs of this layer say several things the code does not do | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L8-10 | (read only) | Dead and stale things at the edges | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L9-14 | (read only) | Dead and half-wired things in the shell | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-15 | (read only) | The ref pages and KDocs of this layer that say what the code does not | not challenged; the auditor's reading in `layer-9-engine-shell.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

Nothing here changes the sound; each item is a code change or a removal the rules leave to the maintainer.

1. **`kotlin.Long` on the wire (L1-09).** The wire codec's processor lists `kotlin.Long` among its pass-through
   scalars, so a `Long` field would build and be stripped by the structured clone on JS. The KDoc says so now.
   Options: (a) refuse `Long` by name in `WireCodecProcessor`, as it refuses a stdlib `Map` (one line and an error
   text; a KSP change); (b) leave it, the stone rule bans `Long` on the wire. Recommendation: (a).
2. **`VoiceScheduler.scheduleVoice` (L2-08).** It promotes before it prefetches, `scheduleVoices` the other way round;
   the result is the same today. It has no production caller (only specs, about ten). Options: (a) swap the two lines
   so both read the same; (b) remove it and let the specs call `scheduleVoices(listOf(...))`. Recommendation: (b).
3. **The Ignitor phaser's feedback (L4-12).** `PhaserIgnitor` never sets `PhaserCore.feedback`, which stays at 0.5: the
   phased path peaks at about +6 dB and dips to about -3.5 dB, and no door reaches it. The KDocs say so now. Options:
   (a) leave it (documented); (b) a `feedback` knob on the Ignitor phaser door; (c) feedback 0 for the Ignitor phaser
   (a sound change). Recommendation: (a), unless the maintainer wants the knob.
4. **`endsInClassic` (L5-12).** No production caller; its KDoc names it as the tag the `.sprudel()` auto-attach of
   KLANG-00216 will ask for, and five tests pin it. Options: (a) keep it for KLANG-00216; (b) remove it with its
   specs. Recommendation: (a).
5. **`Ignitor.withGain`, `octaveUp`, `octaveDown` (L5-12).** No caller in main code; about ten rows of
   `IgnitorsTest` build with `withGain`, and `IgnitorOctaveShiftSpec` tests the two octave shifts (a first removal
   of those two broke that spec's compile and was put back byte for byte). Options: (a) move them to the test sources
   (and drop `IgnitorOctaveShiftSpec`'s rows for the two, which test nothing the engine uses); (b) leave them.
   Recommendation: (a), in a change that touches those specs anyway.
6. **`GraphCensus` and the gate (L5-12).** The census models the build gate for the pitch stages only; every other
   gated stage counts as built, set or not, so the song benchmark's `work` and `traffic` columns count stages a
   classic voice does not build. The KDoc says what it does. Options: (a) model the gate for every gated stage (the
   benchmark columns move, the sound does not); (b) leave it. Recommendation: (a), with the next benchmark change.
7. **The worklet's `KlangTime` (L9-14).** The worklet sets its clock's frame every block, and nothing reads that
   clock after the one call at start-up (which reads frame 0); the clock would also stand still from block 0 to
   block 1 (its setter takes the first non-zero frame as its start). Options: (a) drop the per-block update and the
   worklet time source's frame counting (`KlangTime.updateCurrentFrame`, a public function in `audio_bridge`);
   (b) keep it and fix the off-by-a-block. Recommendation: (a).
8. **The Katalyst `distort` stage's DC pole (KLANG-00432 follow-up).** It keeps the fixed `HOUSE_DC_BLOCK_COEFF`
   (0.999), so its corner moves with the rate (7.0 Hz at 44.1 kHz, 15.3 Hz at 96 kHz), while the house stage holds
   7.64 Hz at every rate (`houseDcBlockCoeff`). The KDoc says so now. Options: (a) use `houseDcBlockCoeff` (the same
   at 48 kHz, so no corpus move; a sound change at other rates); (b) leave it. Recommendation: (a).
9. **Compressor's deferred items.** `Compressor`'s KDoc pointed to a home-directory memory file for two deferred
   items (a linear-domain rewrite, RMS smoothing of the peak detector). The pointer is gone and the items are in the
   KDoc; whether they belong in KLANG-00437 (optimizations backlog) is the maintainer's call.

### Decided (maintainer, 2026-11-11)

1. Refuse `kotlin.Long` in the wire-codec processor: "yes". Owed.
2. `VoiceScheduler.scheduleVoice`: "remove it". Owed.
3. The Ignitor phaser's fixed feedback: "leave it like this, write a followup task for adding the door": KLANG-00439.
4. `endsInClassic`: "ok keep it for now, but update KLANG-00216 that this ticket needs to check if endsInClassic() is
   still needed". Done (KLANG-00216, open questions).
5. `withGain`, `octaveUp`, `octaveDown`: "no keep them and check if KlangScript/Kotlin doors are available". Checked:
   `octaveUp` and `octaveDown` have both doors (`IgnitorDsl.octaveUp()` in `audio_bridge`, the KlangScript extension
   in `KlangScriptIgnitorExtensions.kt`); `withGain` is the runtime form of the doors' `mul`, and has no door of its
   own name.
6. `GraphCensus` models the gate: "as recommended". Owed.
7. The worklet's `KlangTime`: "drop it". Owed.
8. `KatalystDistortEffect` uses `houseDcBlockCoeff`: "as recommended". Owed.
9. The compressor's two deferred items: moved to KLANG-00437.

The maintainer (2026-11-11): "Ok to editing audio/CLAUDE.md": the four lines are corrected. The `flushDenormal` line in
`audio-constraints.md` waits for a word of its own.

## Notes

What landed, per item (2026-11-11, the fix worker). Every item was read against the code as it is after KLANG-00425
to KLANG-00432. No change moves the sound: a dead flag is removed, everything else is text (KDoc, comments, ref pages, a test name,
two probe labels).

Proof (test-digest, all exit 0): `:audio_be:jvmTest` 3146 tests, `:audio_be:jsBrowserTest` 2968,
`:audio_bridge:jvmTest` 160, `:audio_bridge:jsBrowserTest` 299, `:klangscript-libs:jvmTest` 1130, all green;
`:audio_jsworklet:compileKotlinJs` builds. No new spec, so no mutant: the one test change is a name. Corpus before
against after: all 18 sound hashes identical (3 frozen, 15 live); no song edited between the runs.

### L1-09, the wire

| Item | Outcome |
|---|---|
| `data-model.md`: "all types are `@Serializable`" | FIXED: none is; the KSP codec from the `@WireFormat` types |
| `data-model.md`: `playbackId` "for deduplication / replace" | FIXED: `isDuplicate` does not compare it |
| `data-model.md`: `MonoSamplePcm` (`FloatArray`, data class, `anchor: Int?`) | FIXED: `DoubleArray`, a plain class, `anchor: Double`, loop in seconds |
| `data-model.md`: `SampleRequest` (non-null, `note: Double?`) | FIXED: four nullable fields, `note: String?` |
| `architecture.md`: 16 cylinders (twice) | FIXED: 255 ids, signed, 509 per playback |
| `architecture.md` Cmd table: no `StartRealtimeVoice`, `StopRealtimeVoice`, `SetGain`; `Sample.Complete` "PCM bytes" | FIXED |
| `VoiceData.katalyst`: "registers the chain but does not read it yet" | FIXED: read at promotion |
| `toChunks` KDoc and `JsAudioBackend`: 512 KB a chunk | FIXED (`toChunks`: the default and the one caller's 1 MB); `JsAudioBackend` ALREADY TRUE (KLANG-00431) |
| `numerical-safety.md`: `GraphCensus` reads the bag unguarded | FIXED: it guards as the build does |
| `InlineDslRegistrar`: "both sides free it" | ALREADY TRUE (KLANG-00431) |
| `WireCodecProcessor`: `kotlin.Long` a silent pass-through | FIXED (the KDoc says so); the refusal is Decide 1 |

### L2-08, voice lifetime

| Item | Outcome |
|---|---|
| `voice-synthesis.md`: a missing sample "delays activation" | FIXED: requested at schedule time, dropped and counted soundless at its start |
| `Voice.endFrame` and `releaseGate`: a negative release through `resolve()` | FIXED: `treeLifetime` floors it; the floor holds for a hand-built `Voice` |
| `VoiceScheduler`: "the cursor still points at the block ALREADY rendered" | FIXED |
| `REALTIME_HELD_GATE_SEC`: "10 h" | FIXED: the effective horizon is shorter at every common rate (6.2 h at 48 kHz); the bound applies below about 29.8 kHz |
| `teardown_fade.kt`: "the scope note in `AdsrCurveMath`" | ALREADY TRUE (KLANG-00427 removed it) |
| `scheduleVoice` promotes before it prefetches | DECIDE 2 |
| L2-05 decision (KLANG-00427): the start is the author's | FIXED: one sentence in `teardown_fade.kt` and `voice-synthesis.md` |

### L3-15, the sources

| Item | Outcome |
|---|---|
| "band-limited" on the naive shapes | ALREADY TRUE (KLANG-00430) |
| `OscillatorTuning` under the ignitor files | `voice-synthesis.md` ALREADY TRUE (KLANG-00430); `docs/audio-backend-file-map.md` FIXED |
| `sample-management.md`: `FloatArray`, `decode(bytes, mimeType)`, `addBank`, `UrlCache.get/put`, `AssetLoader.load`, the decoder paths, the pending sample voice | FIXED: the page says the API that is there |
| `AudioSample.kt`: widened in `SampleIgnitor` | FIXED: widened in the decoders |
| `MonoSamplePcm`: "sampleRate matches renderer sampleRate" | FIXED: the playhead converts |
| `SampleIgnitor`: "Perlin noise drift" | FIXED: the `AnalogDrift` lane (Ornstein-Uhlenbeck) |
| "center-dominant" at side attenuation 0.1 (`Ignitors.kt`) | FIXED: center-weighted, nearly flat at the default |
| `PhasePools`: "at most about 4k trig-loop iterations per note" | FIXED: the prefix's 2048 plus at least one top-up, up to 16384 |
| `IgnitorsTest` "supersaw - more voices increases energy" | FIXED: renamed to what it asserts, "one voice and five voices both sound" |

### L4-12, the per-note stages

| Item | Outcome |
|---|---|
| KLANG-00156 E3 "latent: no production door passes an env here" | NOT MINE: another ticket's record; the line is stale since 2026-09-25 (L4-02) |
| `IgnitorFilters`: the one-poles "snapshot state into a local" | FIXED: the loops use the fields |
| The diode polynomial "at least about 1.0" | ALREADY TRUE (KLANG-00432) |
| The Ignitor phaser "unit-magnitude", no feedback named | FIXED (the KDocs name the fixed 0.5); the door is Decide 3 |
| `LowPassHighPassFilters`: "kept the per-sample `when(mode)`" | FIXED: hoisted to one loop per tap |
| `flushDenormal` | `numerical-safety.md` FIXED (`flushState`); `.claude/skills/review-loop/audio-constraints.md` NOT MINE (a skill, wording proposed to the maintainer) |

### L5-12, the Ignitor build

| Item | Outcome |
|---|---|
| `endsInClassic` without a production caller | DECIDE 4 |
| `Ignitor.withGain`, `octaveUp`, `octaveDown` | DECIDE 5 (test-only callers; a first removal of the octave shifts broke `IgnitorOctaveShiftSpec` and was put back) |
| `ResourceWarehouse`: "a sub-pool's real depth is 1" | FIXED: a region grows it at its first note |
| `ResourceWarehouse`: "32x is legal" | FIXED: a region stops at 16x |
| "only one build-time consumer reads the sample rate" (`IgnitorDslRuntime`, `IgnitorRegistry`) | FIXED: the drift lane, the tremolo's edges, the declick's settle, a region's rate |
| `voice-synthesis.md:51` (L5-05) | ALREADY TRUE (KLANG-00429) |
| `GraphCensus` counts unbuilt gated stages | DECIDE 6 (its KDoc says so) |

### L6-08, the cylinder manager

| Item | Outcome |
|---|---|
| `effects-mixing.md`: 16 buses, `ducking.applySidechain`, "cylinder gain" | FIXED |
| `architecture.md`: 16 cylinders | FIXED (with L1-09) |
| `docs/audio-backend-file-map.md`: `Map<cylinderId, Cylinder>` | FIXED |
| `audio/CLAUDE.md`: `mixIntoCylinder` "(gain, pan, sends, peak)" | NOT MINE (a CLAUDE.md, wording proposed) |
| `katalyst.md`: a grace of "10 blocks" | FIXED: 10 visits, about 10 x N blocks for N rented |
| `Cylinder.kt`: "a visit is `tryDeactivate`, once per block" | FIXED |

### L7a-08, the Katalyst chain

| Item | Outcome |
|---|---|
| `katalyst.md`: "a non-finite knob is the declared OFF state" | FIXED: never a write; +Infinity per stage (size, time, duck depth) |
| `katalyst.md`: "compares and stores the SUBSTITUTED values" | FIXED: or stores the arriving values and compares with `sameKnob` (also in `numerical-safety.md`) |
| The law table has no `delay.cap` | FIXED: at once |
| `KatalystDsl`: an arithmetic operand "a `Constant` or a `Param`" | FIXED: an expression too, a modulator included |
| `knob-glide.md`: attack and release "measured at the floor" | FIXED: the release; the attack row is above it |
| The clicks probe calls the `min` door's stage "a cap" | FIXED in the probe (`_click_cases.kt`); the audit's `clicks.txt` keeps its words |

### L7b-10, the bus stages

| Item | Outcome |
|---|---|
| `effects-mixing.md`: compressor attack and release "(ms)" | ALREADY TRUE (KLANG-00428) |
| `effects-mixing.md`: the delay "fixed-size", "multi-tap", feedback "(0-1)" | FIXED |
| `numerical-safety.md`: a NaN "latches the cylinder's effects" | FIXED: no stage latches (the duck since KLANG-00425); the mix carries it on |
| `numerical-safety.md`: "No outstanding gaps" | FIXED: none known at that review; the later ones are in the sections above |
| `Phaser.floor`: "steps the dry gain with no smoothing" | FIXED: ramped per sample; also "unit magnitude" in the class KDoc |
| `Ducking`: the 1 ms floor "matches" the compressor's | FIXED: the compressor's is 0.1 ms |
| `Compressor`: a pointer to a home-directory memory file | FIXED: dropped, the items stay in the KDoc (Decide 9) |
| `DelayLine`: "bounded to +-1"; a cap at or below 0 | FIXED: bounded by the cap; the line reads such a cap as 1.0 |
| `Reverb`: "a FINITE input can never drive the state non-finite", all-pass poison implies comb poison | FIXED: below about 1e307 |
| `Reverb`: the bias figure | FIXED: with the feedback loop, 7.8e-18 and 6.3e-17 |
| The limiter without lookahead lets the onset through | FIXED: said on `KatalystBuilder.limiter` and the `Compressor` stage |

### L8-10, the edges

| Item | Outcome |
|---|---|
| `KlangAudioWorklet.isPlaying` | REMOVED (never written; the early return was unreachable) |
| `PlaybackEngineDispatcher`: "per-engine master gain in D6" | FIXED: the engine's own bus |
| The worklet names `KlangAudioRenderer` | FIXED: the dispatcher |
| `Compressor`'s memory pointer | FIXED (with L7b-10) |
| `effects-mixing.md` and `architecture.md`: the block, "master limiter", no DC blockers or gain | FIXED: the dispatcher's block, the house limiter, its knee, `PlaybackGain`, the voice buffer a `DoubleArray` |
| `HouseStage`: "identical to the old inline post-chain" | FIXED: dropped |
| One output channel drops the right | FIXED: said at the copy |

### L9-14, the shell, dead and half-wired

| Item | Outcome |
|---|---|
| `isPlaying` | REMOVED (with L8-10) |
| The worklet's `KlangTime`, updated every block and unread | DECIDE 7 |
| The `Phase.Disposed` arms | ALREADY TRUE (reachable since KLANG-00431: a failed engine stays filed, disposed) |
| `AudioBackendContext`: `performanceTimeMs` "transitional (D5)" | FIXED: dropped |

### L9-15, the shell's docs

| Item | Outcome |
|---|---|
| `KlangTime`: "not epoch-based"; `architecture.md` the two clocks | FIXED (the common KDoc and the page) |
| `architecture.md`: `Cleanup` "stop all voices, reset state", three commands missing | FIXED |
| `sample-management.md` | FIXED (with L3-15); `audio/CLAUDE.md`'s `FloatArray` NOT MINE (wording proposed) |
| `docs/audio-backend-file-map.md`: `VoiceScheduler` "diagnostics emit" | FIXED |
| `audio/MEMORY.md`: "one report per 20 ms" | FIXED: 8 blocks, 21.3 ms at 48 kHz |
| `JvmAudioBackend`: "about 21 ms of silence" | ALREADY TRUE (KLANG-00431) |
| `JsAudioBackend`: the chunk size | ALREADY TRUE (KLANG-00431) |
| `InlineDslRegistrar` | ALREADY TRUE (KLANG-00431) |

### Also in scope (from the other tickets)

| Item | Outcome |
|---|---|
| `KlangScriptKatalystSlots`: `reverb.lowpass` "unset (undamped)" | FIXED: unset keeps the room's fixed damping, a set value is the -3 dB corner in Hz; the sibling door texts (`KatalystDsl`, the script `reverb`, sprudel's) were already right |
| `Compressor` header's memory pointer | FIXED (L7b-10) |
| `sample-management.md`, `SampleIgnitor`, `architecture.md` `Cleanup` | FIXED (above) |
| `numerical-safety.md` "Closed gaps": `flushDenormal` | FIXED |
| L2-05 (KLANG-00427 decision) | FIXED (above) |
| `KatalystDistortEffect`'s fixed DC pole | FIXED (the KDoc and `effects-mixing.md` say its corner moves with the rate); DECIDE 8 |
| KLANG-00425 N2: `numerical-safety.md` names the four values | ALREADY TRUE (KLANG-00425 wrote the section) |

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.
- 2026-11-11: IN PROGRESS. Fix worker started.
- 2026-11-11: PAUSED. Landed; nine decisions pending with the maintainer.
