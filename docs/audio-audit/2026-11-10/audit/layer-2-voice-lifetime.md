# Layer 2: voice creation and lifetime

**What I read**

- In full: `audio_be/src/commonMain/kotlin/voices/Voice.kt`, `VoiceScheduler.kt`, `VoiceFactory.kt`, `VoiceLimits.kt`,
  `teardown_fade.kt`, `voice_channel.kt`, `PlaybackCtx.kt`, `_sample_envelope_defaults.kt`; `VoiceWatch.kt`;
  `PlaybackEngine.kt`; `BackendClock.kt` (`RenderClock`); `utils/fade_to_zero.kt`, `utils/retain_in_order.kt`;
  `common/.../infra/KlangMinHeap.kt`; the command routing, `cleanup`, `cleanupHard` and `renderBlock` of
  `PlaybackEngineDispatcher.kt`.
- Skimmed (code without the KDoc): `voices/SoloTracker.kt`, `voices/SoloRamp.kt`.
- Outside the layer, only as far as a lead or a trace needed it: `EnvelopeCore.kt` (the law's header, `EnvelopeDeclick`),
  `ignitor/IgnitorEnvelopes.kt` lines 95 to 160 (the amplitude envelope's `generate`), `AdsrCurveMath.kt` lines 1 to 80,
  `ignitor/Ignitor.kt` lines 90 to 112 (`blockStartValue`), `ignitor/MemoizingIgnitor.kt` lines 115 to 140,
  `IgniteContext.updateOffsetAndLength`, the `fill(` call sites of `ignitor/`, `audio_bridge/.../KlangTime.kt` (js and jvm),
  `ScheduledVoice.isDuplicate`, `sprudel/.../SprudelPatternEvent.kt` lines 43 to 51, the stop path of
  `klang/.../KlangPatternScheduler.kt`.
- Docs: `audio/CLAUDE.md`, `audio/ref/voice-synthesis.md` (Samples, VoiceScheduler, lifetime, culling, the rng), the
  `Voice` class KDoc, KLANG-00279 (the lines on `Pending` and the cut), the leads in KLANG-00420, `clicks.txt` (the
  voice rows), `framing.txt` (legend and the voice rows), `stage-cost.txt` (the voice rows), `hot-path-lint.txt` (the
  rows of this layer), the constants in `audio_bridge/.../constants/EnvelopeDefaults.kt` and `VoiceCullingDefaults.kt`.
- Specs: the test NAMES of `VoiceLifecycleSpec`, `VoiceCullingSpec`, `VoiceSchedulerRemovalSpec`,
  `VoiceSchedulerPlaybackSpec`, `TeardownFadeSpec`, `ZeroLengthWindowSpec`, `RealtimeVoiceSpec`, and the bodies of
  three `RealtimeVoiceSpec` rows. Two small Python calculations (no file written): the seconds round trip of L2-03 and
  the one-pole model of L2-01.

**What I did not**

- `audio/MEMORY.md`, `audio-constraints.md` and `docs/audio-backend-file-map.md` beyond their first screen (the tool
  output was cut; I did not page through them). A finding below may therefore touch a decision recorded there; I say
  where I suspect that.
- `audio/ref/effect-state-machines.md` (only the rule the `Voice.State` KDoc quotes from it).
- `Cylinder.offer` and `Cylinders.checkIn` (who owns the cylinder), `SampleStore`, `IgnitorRegistry.createExciter`
  and the whole tree build, `SampleIgnitor`, `MasterBus`, `TailRelease`, `PlaybackGain`.
- The bodies of the specs (except three rows). The "Test truth" section is therefore about what the names and the
  rigs show, and its mutants are proposals, not results.
- Nothing was run through the engine. Every number of mine is from reading the code or from a model of its law.

## The probes' leads in this layer

| Lead | Verdict | Cause |
|------|---------|-------|
| Clicks 5: `adsr.on=0` fades a voice's end but not its start (sine kink 69.5, tri jump 109, supersine -26 dB, berlin -20 dB) | CONFIRMED | `Voice.renderStages` (`Voice.kt:437-453`) has two stages after the tree, the teardown fade and the cut fade, and both act on the END. Nothing acts on the onset. With the envelope off no envelope is built (`audio/ref/off-values.md:34`), so the first sample is the source's value at its start phase: a sine starts at 0 with a broken slope (the kink), a tri and a random-phase stack start off zero (the jump). I found no place that calls the unguarded start a decision. See L2-05. |
| Clicks 6: the supersine's default-adsr note end has a kink at its last frame (9.4, -68.5 dB); the sine is clean | CONFIRMED, and the sine's clean reading is a coincidence | The envelope's raw level reaches exact 0 on the last rendered frame, but `classic()` runs the 1 ms de-click one-pole on the gain (`IgnitorEnvelopes.kt:142-151`, `EnvelopeDeclick` in `EnvelopeCore.kt:188-202`), which lags the falling ramp. The voice has no teardown fade (`VoiceFactory.needsTeardown`, `VoiceFactory.kt:316`) and is `Done` at `endFrame` (`Voice.kt:392`), so the smoothed gain is cut where it stands. The sine reads clean because the probe's sine is at a zero crossing there: 110 Hz times (2 s gate + 0.05 s release) = 225.5 cycles. See L2-01. |
| Clicks 8: `teardown_fade.kt`'s KDoc names about 3.3e-3 left on the last frame at a 50 ms release; the sine shows none (a stale KDoc?) | DISPROVED (the KDoc is right) | A model of the law (exponential release at `ADSR_EXP_K` 3, 2400 frames, one-pole at 1 ms, 48 kHz) leaves 3.31e-3 on the last frame. The sine hides it by the zero crossing above. What IS stale in that KDoc: it points to "the scope note in `AdsrCurveMath`", and that file holds no such note (L2-08). |
| Clicks 7 (second half): the cut and teardown fades read kink 10 to 20, -61 to -70 dB | BY DESIGN | A linear 4 ms ramp with two corners: `CUT_FADE_SECONDS` and `TEARDOWN_FADE_SECONDS` (`EnvelopeDefaults.kt:141,158`), whose KDoc leaves the curve to the maintainer's ear. Both ramps reach exact 0 on the last rendered frame (checked by hand at 48 kHz and 44.1 kHz, `Voice.kt:525-540`, `teardown_fade.kt:65-111`). |
| Framing 3: cuts and voices identical at every offset, no voice wrote before its onset, same random draws | CONFIRMED for what it measured, with a hole | `Voice.render` takes its window from integral frames (`VoiceFactory.onsetFrame` floors, `VoiceFactory.kt:55`), so the result holds. The cut sweep places the cut 4096 + k frames after the onset; a cut LESS than one block after the victim's onset is not in the sweep and is framing dependent (L2-02). The probe calls `Voice.render` with frames, so it never passes the seconds round trip of L2-03. |
| Hot-path lint: `Voice.kt:658` (`AudioBuffer(` in `RenderContext.voiceBufferAt`) | CONFIRMED, harmless | Once per rate per scheduler, on the first block a voice hands over at that rate; kept for the context's life. It is inside the render call. It could be made with the context. |
| Hot-path lint, the rule question (once per note inside render) | MINE in part | The whole voice build runs inside the render call for every timeline voice (L2-06). |
| Stage cost 3 (adsr 4.1 to 4.5 ns on a held note), framing 1 (the filter envelope), the aliasing leads | NOT MINE | The tree. |

## Findings

### L2-01 A voice that ends in its own envelope is cut on the de-click's lag: a step at the last frame that grows as the release gets shorter

- **Severity**: major
- **Tag**: sound
- **Where**: `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt:302-303` (`treeLifetime`: the lifetime is exactly
  the release), `:316` (`needsTeardown`: no fade when the tree ends in its envelope); `voices/Voice.kt:392` (`Done` at
  `endFrame`); `ignitor/IgnitorEnvelopes.kt:142-151` and `EnvelopeCore.kt:188-202` (the one-pole on the gain);
  `audio_bridge/.../constants/EnvelopeDefaults.kt:113` (`ENV_DECLICK_SECONDS` = 0.001).
- **What**: the envelope law lands p = 1 on the last rendered frame, where the raw level is exactly 0
  (`EnvelopeCore.kt:29-32`). `classic()` then smooths the gain with a one-pole of 1 ms. A one-pole that follows a
  falling ramp stands above it by about slope times tau, so on the voice's last frame the applied gain is not 0. The
  voice renders no further frame, so the output steps from that gain times the signal to nothing. Model of the law
  at 48 kHz, sustain 1, gain left on the last frame:

  | release | exponential (the default curve) | linear |
  |---------|-------------------------------|--------|
  | 200 ms | 7.9e-4 | 5.0e-3 |
  | 50 ms (default) | 3.3e-3 (-50 dB) | 2.0e-2 |
  | 20 ms | 9.2e-3 | 5.0e-2 |
  | 10 ms | 2.2e-2 (-33 dB) | 9.9e-2 (-20 dB) |
  | 5 ms | 6.8e-2 (-23 dB) | 0.20 |
  | 2 ms | 0.27 | 0.43 |

  At the default it is the -68.5 dB burst the click table shows for the supersine (row 183). At a release of 5 to
  10 ms, which a tight bass or a synth drum uses, every note ends on a step of 2 to 20 percent of its level. The
  teardown fade exists for exactly this class and is switched off for these voices, because the envelope is taken to
  end the voice at zero; with the smoother on, it does not.
- **Reach**: every `classic()` voice with the envelope on (the default of every built-in sound and of the sample
  instrument), most audible with `release(0.01)` or shorter, with `adsrCurves` linear, on low or smooth sources.
- **Settle it**: click probe rows "note end, release 0.005" for tri, supersine and a sine at a frequency that does not
  land on a zero crossing (for example 113 Hz); or a spec that renders such a voice and asserts the last rendered
  sample against the one before it. A mutant that sets `needsTeardown` to always true should make the step vanish.
- **Confidence**: medium. The mechanism is read end to end and the 50 ms figure agrees with the KDoc and with the
  click table; the other rows are my model, not a render. A decision recorded in `audio/MEMORY.md` may already
  accept the 50 ms case; I did not find one for short releases.

### L2-02 A cut kills a victim that has not rendered yet, even when the victim's onset lies before the cutter's: whether a note sounds depends on the block border

- **Severity**: minor
- **Tag**: bug (block framing)
- **Where**: `voices/Voice.kt:326-329` (`Pending` to `Done` on a cut), `voices/VoiceScheduler.kt:543-607`
  (`promoteScheduled` promotes every voice of the block before the block renders), `:623-637` (the cut sweep).
- **What**: voices A (onset frame 970) and B (onset 1080), same cut group, block [960, 1088). `process` promotes A,
  then B, before anything renders. B's activation calls `A.cutOff(1080)`; A is `Pending`, so it is `Done` and removed.
  A never sounds. Expected by the cut's own law ("fades from the cutting voice's onset"): A plays frames 970 to 1079
  and fades over 4 ms from 1080. Now move both by 40 frames: A at 1010 (block [960, 1088)), B at 1120 (the next
  block). A renders its first block as `Sounding`, B's activation finds it `Sounding`, and A fades from 1120. The same
  two notes, the same distance, a different result: A is there (about 6 ms of it) or absent. KLANG-00279 line 77
  reasons "it has not sounded"; that is true of the block, not of the timeline.
  A second shape of the same rule: a `Pending` voice whose onset is LATER than the cutter's is also killed, when the
  cutter is activated after it (a realtime voice of the same playback and group, or a second `ScheduleVoices`
  command in one drain carrying an earlier voice). Not traced to a sender.
- **Reach**: two voices of one cut group less than 128 frames (2.7 ms) apart: a flam, a small `late` or `nudge`
  between two layers, a very fast roll. Onsets on the same frame are consistent (the first pushed never sounds).
- **Settle it**: a framing case "cut k frames after the onset, k = 1..127" swept over the 128 offsets, or one spec:
  the two notes above at both alignments, assert that A's frames before B's onset are equal.
  `VoiceSchedulerRemovalSpec` ("a cut that finds a voice not yet rendered removes it") pins the present behaviour.
- **Confidence**: high (read end to end; not run).

### L2-03 The seconds round trip places a block-aligned onset one frame BEFORE the cursor: about half of all live realtime notes and first notes start with `voiceElapsedFrames` = 1

- **Severity**: minor
- **Tag**: bug / consistency
- **Where**: `voices/VoiceFactory.kt:51-56` (`onsetFrame`: `floor((startTime - backendStartTimeSec) * sampleRate)`),
  `BackendClock.kt:54` (`secAt`: `startTimeSec + frame / sampleRate`), `voices/VoiceScheduler.kt:332-341` (the realtime
  stamp), `:497-499` (the epoch), `:594` (the late check, in seconds), `:588-593` (the contract stated).
- **What**: a realtime voice is stamped `nowSec = secAt(cursorFrame)` and its onset is then recomputed as
  `floor((nowSec - startTimeSec) * sampleRate)`. That is `floor(f / sr * sr)` with a rounding in between, and it is
  `f - 1` whenever the product falls a hair under `f`. Live, `startTimeSec` is the wall clock in seconds
  (`KlangAudioWorklet.kt:126`, `JvmAudioBackend.kt:36`: about 1.8e9), where a Double resolves 2.4e-7 s, about 0.011
  frames at 48 kHz. Counted over 200000 block-aligned cursors: `f - 1` in 99732 cases at 48 kHz and 99988 at 44.1 kHz
  with a start of 1.8e9; in 12280 and 14044 cases with a start of 0.0 (the offline and spec value). The same holds
  for the first timeline note of a playback (epoch = now, `startTime` 0) and for any timeline onset that lies on a
  block border.
  The voice is admitted (the late check compares seconds, and `nowSec >= nowSec` holds), `startFrame` is
  `blockStart - 1`, and in its first block `Voice.ignite` hands the tree `voiceElapsedFrames = 1` (`Voice.kt:513`).
  The scheduler's comment says the DSP is written against "a voice's first generate() has voiceElapsedFrames == 0;
  the scheduler guarantees it here". It does not. What follows from it, as far as I traced: the envelope starts at
  position 1 (with a 10 ms attack the first gain is 1/480 of the way instead of 0), `elapsed()` and `progress()` are a
  frame ahead, a cut's fade starts one frame early, the tap floor (`startFrame + blockFrames`) puts a same-drain
  note-off at frame 127 of the block instead of the border. All small. Away from block borders the same noise moves
  any onset that lies exactly on a frame by one frame, on live backends only, so a live render and an offline render
  of one song differ by a sample here and there.
- **Reach**: every `StartRealtimeVoice` (keyboard, MIDI), every playback's first note, live.
- **Settle it**: `RealtimeVoiceSpec` with `setBackendStartTime(1.8e9 + 0.123)` and an assertion on `Voice.startFrame`
  against the cursor (every spec sets 0.0 or 1000.0 today). The fix is a design choice: hand the realtime path the
  frame it already has instead of going through seconds, or round where a frame is meant.
- **Confidence**: high for the arithmetic (computed), high for the path (read), medium for "nothing worse follows"
  (I did not read every reader of `voiceElapsedFrames`).

### L2-04 A gate that ends before the onset can hand the tree a NEGATIVE window length for one block

- **Severity**: minor (major if a node that fills its window is on the path, see below)
- **Tag**: safety
- **Where**: `voices/Voice.kt:391-409` (`advance`), `:418-426` (the window), `:496-516` (`ignite`);
  `ignitor/Ignitor.kt:104-112` (`blockStartValue` guards `length == 0` only); the `buffer.fill(x, ctx.offset,
  ctx.windowEnd)` sites (`ParamIgnitor.kt:33`, `ConstantIgnitor.kt:33`, `FreqIgnitor.kt:17`, `_arithmetic_laws.kt:178,
  187,209`, `Ignitor.kt:268,276`, `Ignitors.kt:870,1948,2738,2869`).
- **What**: sprudel folds `legato` into the gate and passes a negative one on purpose
  (`SprudelPatternEvent.kt:43-51`). Take a block starting at frame 0, an onset at 100, a gate of -24 frames, a release
  of 0: `gateEndFrame` = 76, `endFrame` = 76. `advance`: 0 >= 76 is false, the block ends after the onset, so
  `Sounding`; 0 >= 76 is false, so it stays. `renderStages`: `vStart` = 100, `vEnd` = min(128, 76) = 76, `offset` =
  100, `length` = -24, `windowEnd` = 76. The tree is called with that window. Loops of the form `offset until
  windowEnd` do nothing, and the voice is `Done` at the next block. But the window is not the zero-length one the
  code prepared for (`ZeroLengthWindowSpec`, ledger E5): `blockStartValue` tests `length == 0`, so with -24 a
  non-constant knob renders nothing and then reads `tmp[ctx.offset]`, whatever the scratch pool held; and
  `DoubleArray.fill(value, 100, 76)` throws `IllegalArgumentException` on the JVM and on Kotlin/JS. An exception in
  render leaves `renderBlock` through its `finally` and reaches the host.
  The general condition: `-o < gate + release < 0` in frames, where `o` is the onset's offset in its block (0 to 127).
- **Reach**: `legato` slightly negative with `release(0)` on a short note, for example
  `note("c").legato(-0.01).release(0)` with a note under about 0.26 s at 48 kHz, on the four notes in five whose
  onset lies more than 24 frames into a block. Contrived, and reachable from a door.
- **Settle it**: a spec through the real `VoiceFactory`: `startTime` at frame 100, `gateEndTime` 24 frames earlier,
  `adsr.release` 0, render block 0, for `sine`, `supersaw` and one authored tree with a constant read at audio rate.
  Expect: no exception, nothing written. The fix is one line in `renderStages` or `advance` (a window that is not
  positive renders nothing).
- **Confidence**: high that the negative window reaches the tree (traced with numbers); low that a BUILT-IN sound has
  a filling node on that path (I did not trace a tree), so the crash is a suspicion and the stale read is harmless
  as far as I can see (no sample is produced and the voice ends).

### L2-05 With the envelope off the engine guards a voice's end and not its start

- **Severity**: minor
- **Tag**: sound (a decision is needed)
- **Where**: `voices/Voice.kt:437-453`, `voices/teardown_fade.kt:41-112`.
- **What**: lead 5, confirmed above. The teardown fade is "the voice's own teardown guard" for a tree without an
  amplitude envelope at its root; there is no counterpart at the onset, so such a voice starts on the source's first
  sample: a tri jumps (-13.7 dB burst in the click table), a random-phase stack jumps (supersine -26 dB), a sine
  breaks its slope (-52.6 dB). The same holds for an authored instrument that does not end in `classic()`. The
  argument for leaving it: the author switched the envelope off, and a drum or a pluck wants its first sample. The
  argument against: the engine already decided that a bare end is a defect worth 4 ms of fade, and a start is the
  louder of the two edges.
- **Reach**: `adsrOff` (sprudel) on any tonal source; a bare authored tree.
- **Settle it**: a maintainer decision, then either one sentence in `teardown_fade.kt` and `voice-synthesis.md`
  ("the start is the author's") or an onset guard. A listening task below.
- **Confidence**: high.

### L2-06 The whole voice build runs inside the render call, in the block that holds the note's onset

- **Severity**: minor (it is the lint's open rule question; major if "no allocation per note in render" is the answer)
- **Tag**: realtime
- **Where**: `voices/VoiceScheduler.kt:423-427` (`process` calls `promoteScheduled`), `:600-605`, `:614-665`
  (`activateVoice`), `voices/VoiceFactory.kt:71-286`, `:318-378`.
- **What**: a timeline voice waits in the heap until the block that contains its onset; `process`, which is the
  render call, then builds it. Per note, from this layer alone: `ScheduledVoice.copy` (`:600`), `VoiceOrigin.Timeline`,
  `ActiveVoice`, a bound reference `::getCompleteSample` (`:645`, a new object per call), `Random`, `IgniteContext`,
  `VoiceLimits`, `Voice`, its two state objects; for a sample also `asSampleRequest()` twice (`:539` and
  `VoiceFactory.kt:162`), `SampleIgnitor`, and with a sample envelope a copy of the slot map plus a helper object
  (`_sample_envelope_defaults.kt:31,70`). On top comes the tree build (not my layer), which is the large part. An
  eight-note chord of a unison stack builds eight trees inside one 2.67 ms block. The scheduler already knows better
  for the master chain: `masterBus.prepare` is called at schedule time "between render blocks, and not in the block
  that lands it" (`:413-414`).
- **Reach**: every timeline note. Realtime notes are built between blocks.
- **Settle it**: time `promoteScheduled` in the song benchmark (max per block, not the mean), on Kotlin/JS. A fix
  shape that keeps the rng's order: build in heap order one block ahead, between blocks.
- **Confidence**: high for where it runs; no measurement of its cost exists in the tables.

### L2-07 Nothing bounds a stopped playback's voices: a long gate or a long release keeps sounding, and keeps the engine, after stop

- **Severity**: minor
- **Tag**: leak / lifetime
- **Where**: `voices/VoiceScheduler.kt:214-229` (`cleanup` releases held realtime voices only),
  `PlaybackEngine.kt:185-193` (the hold counts only blocks WITHOUT an active voice), `:300-304` (`isIdle`),
  `klang/.../KlangPatternScheduler.kt:283-284,330-334` (stop sends `Cmd.Cleanup` and nothing else).
- **What**: after stop, a timeline voice plays its whole gate and its whole release. The gate is bounded only by
  the Int frame count (12.4 h); the release is whatever finite number was written (`release(1e6)` passes raw; only a
  non-finite one reads as unset). While such a voice lives, `quietBlocks` stays 0, the 20 s hold never starts, and
  the engine is never idle. The bound that decision (j) built for endless CYLINDER tails has no counterpart for a
  voice. For ordinary music it means: a pad held over four slow bars keeps sounding at full sustain for the rest of
  its gate after the user pressed stop. `RealtimeVoiceSpec` ("Cmd.Cleanup lets an active TIMELINE voice ring out its
  full gate") pins that, so the ordinary case is a decision (see "Decisions worth revisiting"); the unbounded case is
  the finding.
- **Reach**: any long note at stop; `release` with a large value.
- **Settle it**: a spec: one voice with gate 60 s, `Cmd.Cleanup` after 1 s, count the blocks until `isIdle`.
- **Confidence**: high.

### L2-08 Docs and comments that say what the code does not do

- **Severity**: note
- **Tag**: doc / dead
- **Where and what**:
  - `audio/ref/voice-synthesis.md:109-110`: "when a sample voice is due but the PCM isn't loaded yet, sends
    `Feedback.RequestSample` to frontend and delays activation". The code drops the voice and counts it
    (`VoiceFactory.kt:163`, `VoiceScheduler.kt:650-651`); nothing is delayed.
  - `voices/Voice.kt:143-146` (`endFrame` KDoc) and `:280-287` (`releaseGate`): "an authored NEGATIVE release passes
    through resolve() unclamped, making endFrame < gateEndFrame". There is no `resolve()` any more, and
    `treeLifetime` floors the lifetime at 0 (`VoiceFactory.kt:302-303`), so `endFrame >= gateEndFrame` for every voice
    the factory builds. The `coerceAtLeast(0.0)` at `:287` can only act on a `Voice` a spec builds by hand.
  - `voices/VoiceScheduler.kt:326-331`: the comment opens with "The cursor still points at the block ALREADY rendered"
    and then says the opposite (the clock is the next block). The first sentence is the pre-B1 state.
  - `voices/VoiceScheduler.kt:42-46`: `REALTIME_HELD_GATE_SEC` is "10 h"; at 48 kHz the effective horizon is
    `Int.MAX_VALUE / 2 / 48000` = 6.2 h, at 44.1 kHz 6.8 h (`:397-398`). The constant never applies at a common rate.
  - `voices/teardown_fade.kt:38-39`: "See the scope note in `AdsrCurveMath`": that file has no note on the residual.
  - `voices/VoiceScheduler.kt:306-313` against `:405-418`: `scheduleVoice` promotes before it prefetches the sample,
    `scheduleVoices` prefetches first. The result is the same today (a missing sample is dropped either way).
- **Settle it**: reading.
- **Confidence**: high.

### L2-09 No voice limit, and `replaceVoices` does quadratic work with deep compares on the audio thread

- **Severity**: note
- **Tag**: realtime / performance
- **Where**: `voices/VoiceScheduler.kt:109` (the active list, no cap anywhere in the layer), `:290-304`
  (`dedupAgainstActive`), `audio_bridge/.../ScheduledVoice.kt:34-35`.
- **What**: there is no stealing and no cap: at 1000 voices every one renders, and the only signal is the headroom
  gauge. The list work itself stays linear (render pass, cut sweep per cutting voice, the realtime solo walk). The
  one exception is a live edit: `dedupAgainstActive` allocates a `BooleanArray`, a filtered list and a lambda per
  incoming voice, and compares each incoming voice with each active one. `isDuplicate` tests `startTime` first, so
  the deep `VoiceData` compare (maps included) runs only on equal start times, which is exactly the chord and the
  stack. It runs where commands are drained, on the audio thread between blocks.
- **Reach**: a live edit of a dense song.
- **Settle it**: the allocation benchmark with a `ReplaceVoices` of 500 voices against 500 active.
- **Confidence**: high for the code; the cost is not measured.

## Performance candidates

| Where | What | Per | Measured cost | Expected gain | Risk to the sound |
|-------|------|-----|---------------|---------------|-------------------|
| `voice_channel.kt:48-49,86-87,127-128` | `cos` and `sin` of the pan angle and the pan clamp, recomputed for every voice in every block, from two `val`s of the voice. Compute `cos(panAngle)` and `sin(panAngle)` once at construction. | block, per voice | none in the tables; two trig calls per 128 frames is roughly 0.2 to 0.4 ns per sample, against 1.29 ns for the whole "everything off" saw voice (`stage-cost` row 199) | up to a fifth of the bare voice overhead | none (the same two numbers) |
| `VoiceScheduler.kt:423-427`, `VoiceFactory.makeVoice` | Build the voice one block ahead, between blocks, in heap order (L2-06). | note | not measured | removes the build spike from the onset block | none if the order of `coreRandom` draws and of the cut sweep is kept; a sample that arrives in that last block would be missed |
| `Voice.kt:417-488` | A voice with `gain` exactly 0 (the hand mute) runs its whole tree for a product of zero until its gate and cull end. It could check in at the cylinder and return. | block, per muted voice | the voice's full cost (5.82 ns per sample for a default saw voice, row 112) | all of it, for muted voices | none for the output; the voice's rng draws at render time would not happen, which is inside its own stream |
| `voice_channel.kt:150-186` | The peak for the cull is a second pass over the block, on every block of every release (and until first heard). It could be folded into the mix loop of the blocks that measure. | block, per releasing voice | not measured | small | none |
| `teardown_fade.kt:65-111` | Runs its ten lines of setup (two `floor`, one `ceil`, a division) on every block of the voice, though it acts on the last 4 ms only. An early return on `blockStart + windowEnd <= fadeStart` is possible. | block, per bare voice | not measured | small | none |
| `VoiceScheduler.kt:456,462` | `isSoloed` and `isReleased` each walk the tracker with String compares, per voice per block, while any solo entry is live. Resolve the source's slot once per voice at activation, or per block per source. | block, per voice, only under a solo | not measured | small, grows with voices times solo entries | none |
| `VoiceScheduler.kt:645`, `:539`, `VoiceFactory.kt:162` | `::getCompleteSample` bound per note; `asSampleRequest()` built twice per sample note. | note | not measured | two small objects per note | none |
| `Voice.kt:525-540` | A `Fading` voice renders its tree for the whole last block although the ramp is 0 after `ceil(fadeEnd) - 1`. | once per cut | up to 127 frames | negligible | none |

Pooling: `Voice`, `VoiceLimits`, the two state objects, `IgniteContext` and `ActiveVoice` could be pooled (about seven
small objects per note). The tree is the large allocation and is not in this layer; pooling the shell alone is
probably not worth the "returned dirty" risk it brings (`heard`, `culled`, the gain multiplier latch, the fade window
would all need a reset).

## Suspicions

- **A resumed playback and the replace dedup.** `cleanup` keeps the ringing voices in `active`; a resume gets a
  fresh epoch on the same scheduler. If `startTime` counts from the new start again, a `ReplaceVoices` after the
  resume can match a new voice against a still-ringing voice of the OLD run with the same relative time and data
  (`VoiceScheduler.kt:290-304`) and drop the new one. Needs: what `startTime` is relative to on the frontend. Settle
  with a spec: schedule a 10 s note at 0, cleanup, schedule again, replace with the same list, count voices.
- **A non-finite `startTime` in the heap.** `less` is `a.startTime < b.startTime` (`VoiceScheduler.kt:72`); a NaN is
  "equal" to everything, which is not an ordering, so a NaN entry inside the heap can leave a later voice above an
  earlier one. The earlier one is then promoted late and dropped as late. Needs a frontend that sends a NaN time.
- **The negative-window crash of L2-04** on a built-in sound.
- **`peak < VOICE_CULL_FLOOR` after a stage that amplifies on the cylinder.** Documented as a known class on the
  constant. I did not measure how loud a culled tail can be after a cylinder `distort` or a compressor with make-up;
  the documented list names `wet` above 1 and resonant bodies, not a drive stage, which can lift -100 dB by much more.
- **Cut order between the realtime and the timeline path** (the second shape in L2-02).

## Decisions worth revisiting

- **Stop lets timeline voices play their full gate.** Pinned by `RealtimeVoiceSpec`. Held realtime voices are
  released at stop because they "would otherwise ring at full sustain"; a timeline pad with ten seconds of gate left
  does the same thing for ten seconds. Releasing every open gate at `cleanup` (the same `releaseGate` call, the tails
  still ring out in full) would make stop mean stop for the held part and keep "no hard cut, ever". It would also
  bound L2-07's gate half.
- **A cut ends a `Pending` voice at once** (L2-02). The simpler law is the one the fade already has: the cut takes
  effect at the cutter's onset. A `Pending` victim whose onset is at or after that onset is `Done`; one whose onset
  is before it goes to `Fading` (the state already carries the window, and `advance` already handles a fade end
  before the first render).
- **The realtime path goes frame to seconds to frame** (L2-03). The scheduler has the cursor frame in hand and
  converts it twice. Carrying the onset frame beside the seconds would remove the rounding and the question.
- **No teardown fade for a voice that ends in its envelope** (L2-01). The rule "a fade on top would change its last
  frames" was written for an envelope that ends at zero. With the de-click on it does not; either the lifetime gets
  a few time constants more, or the smoother is bypassed in the last frames, or the fade runs for everyone.

## Listening tasks

- `note("c2").s("tri").release(0.005)` and the same with `adsrCurves` linear, slow and solo: is there a tick at each
  note end (L2-01)? Compare with `release(0.05)`.
- `adsrOff` on `tri`, `supersine` and `sine` at a low pitch: is the start's click wanted (L2-05)?
- Two hats in one cut group a millisecond or two apart (a flam): does the first hit come and go from bar to bar
  (L2-02)?
- The 4 ms linear cut fade on a low sine against a longer or smoother one: the constant's KDoc leaves this to the ear.
- Stop during a long pad: does the rest of the gate after stop feel like a tail or like a stop that did not work?

## Test truth

What is pinned (by the names and rigs I read):

- Every cell of the transition table has a row in `VoiceLifecycleSpec`: `Pending` to the block of the onset, a block
  ending exactly on the onset, the gate border, the end border, onset and gate and end in one block, the kill from
  every state, the note-off per state, the cut per state, a second cut, a note-off while fading, a fade end on a block
  start, a fade that outlives the voice's own end.
- The cull: the window in frames at several block sizes, never in the gate, never before heard, the hand mute, the
  inverted gain, a NaN window, the reset by an audible block (`VoiceCullingSpec`).
- The teardown fade's exact zero, also at a fractional end and for a voice shorter than the fade (`TeardownFadeSpec`);
  the integral onset as a tripwire (`ZeroLengthWindowSpec`).
- The realtime path: the start and stop stamps, the tap, the double stop, release 0, cleanup of held voices.

What is not:

- **The seconds round trip at a realistic start time.** Every rig sets `setBackendStartTime(0.0)` (two use 1000.0).
  A mutant that replaces `floor` in `onsetFrame` with a round would very likely stay green and would fix L2-03; a
  mutant that subtracts one frame from every realtime onset would probably be caught only by the "attack at position
  0" row, and only through a 3 percent level bar.
- **A cut less than a block after the victim's onset** (L2-02): the one row on it pins the kill.
- **A negative window** (L2-04): `ZeroLengthWindowSpec` covers zero; `EnvelopeLawSpec` covers the negative gate in
  the law, not through `Voice.render`.
- **The envelope's last frame with the de-click on** (L2-01): `TeardownFadeSpec` asserts exact silence for the bare
  path and for "the envelope-after-amp control"; I did not see a row that asserts the last frame of a default
  `classic()` voice.
- **`peak < VOICE_CULL_FLOOR`**: I expect a mutant to `<=` to survive (a peak exactly on the floor is not a case a
  spec builds). Harmless, named for completeness.
- **The held horizon above 48 kHz**: no rig at 96 kHz; a mutant that drops the `minOf` in `heldGateHorizonSec` would
  survive.
- **Stop with a long timeline gate and the engine's idle time** (L2-07).

All of these are proposals from reading; none was run.

## For the audit ruleset

- "Read first" costs more than a reader has when the files are long: `audio/MEMORY.md` and `audio-constraints.md`
  did not fit one tool result. A per-layer extract of the decisions that touch the layer (ten lines) would have
  let me rule L2-01 and L2-07 in or out as known.
- The leads were the best entry point. Two of three in this layer changed meaning once the code was read (the
  "stale KDoc" was a probe coincidence). A rule worth writing: a probe row that reads clean must say why it could
  not have been masked (here: the sine's phase at the event).
- The most productive question was "which unit is this value in, and where was it last rounded": it found L2-03 and
  L2-04. The brief's list has "units"; it should add "every conversion between seconds and frames, at the values the
  live clock really has".
- A small calculation outside the engine (a ten-line model of a law, a rounding count) settled two points. The rules
  should say that this is allowed and that the result must be marked as a model, not a render.
- "Trace to a concrete input and a concrete wrong output" worked as the bar between a finding and a suspicion. It
  breaks down at a layer border: L2-04 is traced to the border of my layer and its consequence lies in the next one.
  The ruleset needs a way to hand such a half-trace to the neighbouring layer's reader.
- The specs were only read by name. A rule that asks for test truth should either give the time to read the bodies
  or accept "proposed mutants" as their own, weaker category.
