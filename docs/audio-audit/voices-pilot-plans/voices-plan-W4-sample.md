# Mutation-Check Plan — voices W4 (sample/synth voice specs)

Scope: `SampleVoiceSpecificTest.kt`, `SampleVoiceRenderTest.kt`, `SynthVoiceTest.kt`. 30 test cases total. No build/test
commands were run; all verdicts below are derived by hand-tracing the production code against each test's fixture
values.

Key fact established up front: every `SampleIgnitor` constructed in these three specs uses the default
`analog = 0.0`, so `AnalogDrift.active` is always `false`. **The entire "Analog drift path" branch in
`SampleIgnitor.generate()` (lines 44–70, the wow/flutter loop) is dead code from the point of view of these specs** —
only the "Clean digital path" branch (lines 71–97) ever executes. This matters for every mutation below: the clean-path
line and its drift-path duplicate are textually identical, so OLD snippets are disambiguated by line number + a note,
not by textual uniqueness alone.

---

## io.peekandpoke.klang.audio_be.voices.SampleVoiceSpecificTest

Production files it exercises: `ignitor/SampleIgnitor.kt`, `voices/Voice.kt`, `voices/strip/ignite/IgniteRenderer.kt`,
`voices/strip/filter/EnvelopeRenderer.kt`, `voices/strip/pitch/VibratoRenderer.kt`, `voices/strip/pitch/FmRenderer.kt`,
`voices/strip/pitch/PitchPipelineBuilder.kt`, `voices/strip/filter/FilterPipelineBuilder.kt`,
`voices/strip/filter/AudioFilterRenderer.kt` (via `NoOpFilter`), `voices/strip/BlockContext.kt`,
`voices/strip/send/SendRenderer.kt` (always appended, unexercised by assertions).

### 1. "SampleVoice plays back sample data correctly"

- CLAIM: Playing a constant-valued sample at rate=1.0 from playhead 0, with no loop and an always-on envelope,
  reproduces the underlying PCM value exactly at every output sample (no attenuation, offset, or corruption is
  introduced by the interpolation/copy path).
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:91` (clean-digital-path occurrence — the identical
  text at line 64 is the unreachable analog-drift-path duplicate, dead in these tests)
    - OLD: `                        buffer[idxOut] = (a + (b - a) * frac)`
    - NEW: `                        buffer[idxOut] = (a + (b - a) * frac) * 0.8`
- SUSPICION: LIKELY-RED — assertion is exact (`ctx.voiceBuffer.all { it == 0.5 } shouldBe true`); 0.5*0.8=0.4 fails
  immediately.

### 2. "SampleVoice with rate > 1 plays faster"

- CLAIM: `rate=2.0` doubles the per-sample playhead advance, so the 50-frame block covers the full 100-sample ramp —
  output rises from ~0.0 to ~0.98 across the block.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:95` (clean path; line 69 is the unreachable
  drift-path duplicate)
    - OLD: `                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate`
    - NEW: `                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate * 0.5`
- SUSPICION: LIKELY-RED — halves the effective rate (2.0→1.0), so `voiceBuffer[49]` lands near pcm-index 49 (~0.49)
  instead of ~98 (~0.98); tolerance is only `±0.03`.

### 3. "SampleVoice with rate < 1 plays slower"

- CLAIM: `rate=0.5` halves the per-sample playhead advance, so a 100-frame block only covers half the 100-sample ramp —
  `voiceBuffer[99]` reads ~0.50, not the ramp's end value.
- MUTATION: same mutation as claim #2 (`SampleIgnitor.kt:95`, `rate * 0.5` on the no-phaseMod branch).
- SUSPICION: LIKELY-RED — effective rate becomes 0.25; at i=99 playhead only reaches ~24.75 (pcm≈0.247) vs expected
  0.50±0.02.

### 4. "SampleVoice performs linear interpolation"

- CLAIM: At a non-integer `rate=1.5`, output samples are linearly interpolated between adjacent PCM samples rather than
  nearest-neighbour or truncated.
- MUTATION: same as claim #1 (`SampleIgnitor.kt:91`, corrupt the interpolation formula).
- SUSPICION: SUSPECT — **the test body has zero assertions.** It only comments the expected values ("At playhead=0.0:
  sample[0] = 0.0", etc.) and calls `voice.render(ctx)` with nothing checked afterward. No production mutation, however
  severe, can turn this test red unless it throws.

### 5. "SampleVoice without looping stops at end"

- CLAIM: With `isLooping=false`, once the playhead's integer part reaches the sample's last valid index (`pcmMax`),
  output silences — frame 25 (mid-sample) has audio, frame 75 (past the 50-sample ramp) is silent.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:85` (clean-path occurrence; line 58 is the
  unreachable drift-path duplicate)
    - OLD: `                    if (base >= pcmMax) {`
    - NEW: `                    if (base >= pcmMax - 30) {`
- SUSPICION: LIKELY-RED — silence now kicks in 30 frames early (base≥19 instead of ≥49); at frame 25 (base=25) the voice
  is already silenced, breaking `(ctx.voiceBuffer[25] > 0.0) shouldBe true`.

### 6. "SampleVoice with explicit looping wraps correctly"

- CLAIM: With `loopStart=0, loopEnd=50, isLooping=true`, once the playhead reaches `loopEnd` it wraps modulo
  `loopLength` back to `loopStart`, so frames 50–99 repeat the same 0.0→0.49 ramp as frames 0–49.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:76` (clean-path occurrence; line 49 is the
  unreachable drift-path duplicate)
    - OLD: `                if (isLooping && loopLength > 0.0 && ph >= loopEnd) {`
    - NEW: `                if (isLooping && loopLength > 0.0 && ph > loopEnd) {`
- SUSPICION: LIKELY-RED — with integer-stepped playhead (rate=1.0), `ph` hits exactly `50.0`; the strict `>` skips the
  wrap for that frame, so `voiceBuffer[50]` reads pcm[50]≈0.505 instead of the wrapped pcm[0]≈0.0, well outside `±0.02`.

### 7. "SampleVoice with stopFrame ends early"

- CLAIM: `stopFrame` is a hard cutoff independent of sample length — once `ph >= stopFrame` (50.0), output silences even
  though the underlying 100-sample buffer still has data.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:81` (clean-path occurrence; line 54 is the
  unreachable drift-path duplicate)
    - OLD: `                if (ph < 0.0 || ph >= stopFrame) {`
    - NEW: `                if (ph < 0.0 || ph >= stopFrame + 30) {`
- SUSPICION: LIKELY-RED — pushes the effective stop to frame 80; frame 75 (previously silenced) now plays real ramp data
  (~0.76), breaking the exact `ctx.voiceBuffer[75] shouldBe 0.0`.

### 8. "SampleVoice playhead advances correctly"

- CLAIM (implied by name/setup, not by the body): starting the playhead at 10.0 causes the next 10 rendered frames to
  read sample indices 10–19.
- MUTATION: same production code as claim #2 (`SampleIgnitor.kt:95` rate multiplier) would be the natural target.
- SUSPICION: SUSPECT — **zero assertions.** The body ends with the comment "(Can't directly verify playhead without
  access to private field)" and checks nothing. Any mutation to the playhead-advance logic survives.

### 9. "SampleVoice with vibrato modulates playback rate"

- CLAIM (implied): a `Voice.Vibrato` on a sample voice modulates the sample's effective playback rate via
  `IgniteContext.phaseMod`, producing time-varying playback speed.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:95`
    - OLD: `                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate`
    - NEW: `                ph += if (phaseMod != null) rate else rate` *(drops the phaseMod factor entirely — vibrato
      has no effect on sample playback)*
- SUSPICION: SUSPECT — **zero assertions**; comment only says "Output will have time-varying playback speed." The test
  cannot detect the vibrato-to-playhead wiring being severed.

### 10. "SampleVoice with FM modulates playback rate"

- CLAIM (implied): a `Voice.Fm` on a sample voice modulates the sample's effective playback rate via
  `IgniteContext.phaseMod`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:48`
    - OLD: `            val fmMult = 1.0 + (modSignal / freqHz)`
    - NEW: `            val fmMult = 1.0 - (modSignal / freqHz)` *(inverts FM modulation direction)*
- SUSPICION: SUSPECT — **zero assertions**; comment only. Same class of gap as claim #9.

### 11. "SampleVoice getBaseFrequency returns sample base pitch"

- CLAIM (per name): `SampleVoice`/`SampleIgnitor` exposes a `getBaseFrequency()` that returns the sample's base pitch,
  used for FM depth calculation.
- MUTATION: N/A — **no `getBaseFrequency` method exists anywhere in production** (`grep -rn "getBaseFrequency"` across
  `audio_be/src/commonMain` returns zero hits). The test only calls `voice.render(ctx)` and comments "If this renders
  without error, getBaseFrequency works" — it never calls anything of the kind.
- SUSPICION: SUSPECT — vestigial/stale test name from what was likely a pre-refactor API; the claim in the name is
  unfalsifiable by the current test body.

### 12. "SampleVoice with envelope modulates sample output"

- CLAIM: A `Voice.Envelope` with `attackFrames=100, attackCurve=Linear` ramps the VCA gain from ~0 to a substantial
  level (>0.6) monotonically across a 100-frame block, multiplying the (constant 1.0) sample signal down during attack.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:98`
    - OLD: `                            AdsrCurve.Linear -> p`
    - NEW: `                            AdsrCurve.Linear -> p * 0.3`
- SUSPICION: LIKELY-RED — at i=99, p≈0.99 so the mutated gain tops out ≈0.297 (before declick lag, which only lowers it
  further), failing `(ctx.voiceBuffer[99] > 0.6) shouldBe true`. (Same production line as SynthVoiceTest claim #7.)

### 13. "SampleVoice handles sample end boundary"

- CLAIM: Starting the playhead near the sample's end (45 of 50), output has real audio for the first few frames then
  silences mid-block once the playhead crosses `pcmMax`.
- MUTATION: same mutation as claim #5 (`SampleIgnitor.kt:85`, `pcmMax - 30`).
- SUSPICION: LIKELY-RED — at frame index 2 (ph=47), the mutated guard (`base >= 19`) silences it immediately, breaking
  `(ctx.voiceBuffer[2] > 0.0) shouldBe true`.

### 14. "SampleVoice with negative playhead is handled"

- CLAIM: With playhead starting at -10.0, frames where `ph < 0` are guarded to output silence (not a negative-index PCM
  read); once `ph` reaches 0 (frame 10 of 20), playback resumes.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:81` (clean-path occurrence)
    - OLD: `                if (ph < 0.0 || ph >= stopFrame) {`
    - NEW: `                if (ph < -20.0 || ph >= stopFrame) {`
- SUSPICION: LIKELY-RED, but by an unintended mechanism — for `ph` in `[-20, 0)` the guard no longer fires, so the code
  falls into `pcm[base]` with a negative `base` (e.g. `pcm[-10]`). On a JVM target this throws
  `ArrayIndexOutOfBoundsException` (test errors → red); on a JS target a negative index typically yields `undefined`→
  `NaN`, which still fails the exact `ctx.voiceBuffer[0] shouldBe 0.0` check. Either way the test goes red, but not via
  the intended "wrong value" signal — worth noting for whoever applies this mutation that a crash, not a value mismatch,
  is the observed failure mode.

### 15. "SampleVoice preserves playhead across renders"

- CLAIM: The `SampleIgnitor`'s internal `playhead` is mutable state that persists across separate `voice.render()` calls
  (and separate `RenderContext` instances) — a second render continues from where the first left off rather than
  restarting.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:99`
    - OLD: `        playhead = ph`
    - NEW: `        // playhead = ph`
- SUSPICION: LIKELY-RED — without persisting `ph`, the second `render()` call restarts from the original `playhead`
  (0.0), so `secondValue` (≈pcm[0]≈0.0) is no longer greater than `firstValue` (≈pcm[99]≈0.497), failing
  `(secondValue > firstValue) shouldBe true`.

### 16. "SampleVoice with all modulations renders correctly"

- CLAIM (implied): a sample voice with vibrato + accelerate + FM + loop + envelope all active renders without error and
  (per the name) "correctly."
- MUTATION: any of the above (e.g. claim #6's loop-wrap off-by-one) would be a representative choice.
- SUSPICION: SUSPECT — **zero assertions**, comment-only ("Should render successfully with all features enabled"). This
  is a smoke/crash test only; "renders correctly" in the name is not backed by any check of correctness.

---

## io.peekandpoke.klang.audio_be.voices.SampleVoiceRenderTest

Production files it exercises: `ignitor/SampleIgnitor.kt`, `voices/Voice.kt`, `voices/strip/ignite/IgniteRenderer.kt`,
`voices/strip/filter/EnvelopeRenderer.kt` (always-on default envelope), `voices/strip/filter/FilterPipelineBuilder.kt`,
`voices/strip/BlockContext.kt`.

### 1. "render with rate > 1 (faster)"

- CLAIM: At `rate=2.0`, output sample `i` (for `i` in 0..4, all landing on exact integer PCM positions) equals
  `sample.pcm[i*2]` to within `0.0001` — i.e. the playhead advances by exactly `rate` samples per output frame, and
  beyond that the sample is exhausted (index 5 is silent).
- MUTATION: same mutation as `SampleVoiceSpecificTest` claim #2 (`SampleIgnitor.kt:95`, halve the effective rate on the
  no-phaseMod branch).
- SUSPICION: LIKELY-RED — with the mutated effective rate ≈1.0, `voiceBuffer[1]` reads pcm≈1/9≈0.111 instead of the
  expected `pcm[2]`≈0.222; the `±0.0001` tolerance is tight and catches this immediately at `i=1`. (Note: the "expected"
  values here are read directly from `sample.pcm[i*2]`, not hand-computed constants — this is a legitimate independent
  check since integer playhead positions need no interpolation, not a tautology.)

### 2. "render loop (explicit)"

- CLAIM: With `loopStart=0, loopEnd=5` on a 10-sample ramp, output frames 0–4 equal `pcm[0..4]` exactly, and frames 5–9
  repeat the *exact same* 5-value sequence bit-for-bit (list equality, no tolerance) — a stronger version of
  `SampleVoiceSpecificTest`'s tolerance-based loop test.
- MUTATION: same mutation as `SampleVoiceSpecificTest` claim #6 (`SampleIgnitor.kt:76`, `ph > loopEnd` instead of
  `ph >= loopEnd`).
- SUSPICION: LIKELY-RED — at the frame where `ph` hits exactly `5.0`, the strict `>` skips the wrap, so `voiceBuffer[5]`
  reads `pcm[5]`≈0.556 instead of the wrapped `pcm[0]`=0.0. `values.subList(5, 10) shouldBe expectedSegment` is an exact
  list-equality check, so this is one of the least-suspect, most-discriminating assertions across all three files.

---

## io.peekandpoke.klang.audio_be.voices.SynthVoiceTest

Production files it exercises: `voices/Voice.kt`, `voices/strip/ignite/IgniteRenderer.kt`,
`voices/strip/pitch/PitchPipelineBuilder.kt`, `voices/strip/pitch/VibratoRenderer.kt`,
`voices/strip/pitch/FmRenderer.kt` (in the "all modulations" test), `voices/strip/pitch/PitchEnvelopeRenderer.kt` (in
the "all modulations" test), `voices/strip/pitch/AccelerateRenderer.kt` (in the "all modulations" test),
`voices/strip/filter/EnvelopeRenderer.kt`, `voices/strip/filter/FilterPipelineBuilder.kt`,
`voices/strip/filter/AudioFilterRenderer.kt` (via `NoOpFilter`), `voices/strip/BlockContext.kt`.

### 1. "SynthVoice with constant signal produces constant output"

- CLAIM: An Ignitor that writes 1.0 to every sample in its assigned range has its output pass through the full default
  pipeline (no pitch mods, `NoOpFilter`, always-on envelope) unchanged — every rendered sample is exactly 1.0.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/ignite/IgniteRenderer.kt:34`
    - OLD: `        signalCtx.length = ctx.length`
    - NEW: `        signalCtx.length = ctx.length - 1`
- SUSPICION: LIKELY-RED — the Ignitor now only writes indices `[0, 98]`; index 99 is left at the `AudioBuffer`'s
  zero-initialized default (`DoubleArray` default 0.0) since `BlockContext.length` — used by the downstream
  `EnvelopeRenderer` — is unaffected by this change. `ctx.voiceBuffer.all { it == 1.0 }` fails at index 99.

### 2. "SynthVoice with silence signal produces no output"

- CLAIM: An Ignitor that writes 0.0 to every sample produces an all-zero output buffer.
- MUTATION: same mutation as claim #1 (`IgniteRenderer.kt:34`, `ctx.length - 1`).
- SUSPICION: SUSPECT — this is the toothlessness pattern "assertion reads a value that equals the buffer's own default."
  The identical mutation turns claim #1's test red but leaves this one green: the silence Ignitor writes 0.0 to indices
  `[0,98]`, and index 99 is left at its *already-zero* default — both the correct and the broken run produce
  `all { it == 0.0 } == true`. This test cannot detect any bug where the Ignite/Filter/Envelope stage under-writes,
  skips, or no-ops — only additive leaks (e.g. a stray `+ epsilon` somewhere downstream) would be caught here.

### 3. "SynthVoice with ramp signal produces ramping output"

- CLAIM: A signal that ramps from 0.0 to ~0.9 over a 10-frame block renders with `voiceBuffer[0]≈0.0` and
  `voiceBuffer[9]≈0.9`.
- MUTATION: same mutation as claim #1 (`IgniteRenderer.kt:34`, `ctx.length - 1`).
- SUSPICION: LIKELY-RED — the mutated `signalCtx.length` (9) is read by `TestIgnitors.ramp` itself as its own
  denominator (`(i - ctx.offset).toDouble() / ctx.length`), so it writes indices `[0,8]` with values `i/9`, leaving
  index 9 unwritten (default 0.0). `ctx.voiceBuffer[9] shouldBe (0.9 plusOrMinus 0.01)` fails clearly (0.0 vs 0.9).

### 4. "SynthVoice passes pitch modulation to signal"

- CLAIM: When `Voice.Vibrato.depth > 0`, the pitch pipeline writes per-sample multipliers into the shared
  `freqModBuffer`, and `IgniteRenderer` forwards that buffer (non-null) to the Ignitor as `IgniteContext.phaseMod`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/ignite/IgniteRenderer.kt:36`
    - OLD: `        signalCtx.phaseMod = if (ctx.freqModBufferWritten) ctx.freqModBuffer else null`
    - NEW: `        signalCtx.phaseMod = if (ctx.freqModBufferWritten) null else ctx.freqModBuffer`
- SUSPICION: LIKELY-RED — inverts the condition, so an active vibrato (which sets `freqModBufferWritten=true`) now
  yields `phaseMod=null`; `receivedPhaseMod.shouldNotBeNull()` fails.

### 5. "SynthVoice without pitch modulation passes null to signal"

- CLAIM: When vibrato depth is 0.0 (and no other pitch modulator is active), the pitch pipeline is empty,
  `freqModBuffer` is never written, and `IgniteContext.phaseMod` stays null.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:34`
    - OLD: `    if (vibrato.depth > 0.0) {`
    - NEW: `    if (vibrato.depth >= 0.0) {`
- SUSPICION: LIKELY-RED — `depth=0.0` now satisfies `>= 0.0`, so `VibratoRenderer` is added even though "vibrato is
  off"; it writes `1.0` into `freqModBuffer` and sets `freqModBufferWritten=true`, so `phaseMod` becomes non-null.
  `(receivedPhaseMod == null) shouldBe true` fails.

### 6. "SynthVoice getBaseFrequency returns freqHz"

- CLAIM (per name): a `getBaseFrequency()` accessor on `Voice`/Ignitor returns the voice's `freqHz`.
- MUTATION: N/A — **no `getBaseFrequency` method exists in production** (same finding as `SampleVoiceSpecificTest` claim
  #11). The test body only calls `voice.render(ctx)` and asserts nothing.
- SUSPICION: SUSPECT — vestigial/stale test name; unfalsifiable as written.

### 7. "SynthVoice with envelope modulates signal output"

- CLAIM: Same as `SampleVoiceSpecificTest` claim #12 — a `Linear` attack over 100 frames ramps the VCA gain
  monotonically from ~0 to >0.6 across the block.
- MUTATION: same mutation as `SampleVoiceSpecificTest` claim #12 (`EnvelopeRenderer.kt:98`, `p * 0.3`).
- SUSPICION: LIKELY-RED — identical reasoning: mutated gain tops out ≈0.297, failing
  `(ctx.voiceBuffer[99] > 0.6) shouldBe true`.

### 8. "SynthVoice with filter affects signal output"

- CLAIM (per name): applying a filter to a synth voice changes its rendered output.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/AudioFilterRenderer.kt:23`
    - OLD: `        filter.process(ctx.audioBuffer, ctx.offset, ctx.length)`
    - NEW: `        // filter.process(ctx.audioBuffer, ctx.offset, ctx.length)`
- SUSPICION: SUSPECT — doubly toothless. (a) The test explicitly passes `filter = VoiceTestHelpers.NoOpFilter`, whose
  `process()` "does nothing" by design (per its own doc comment) — so even correctly wired, this test cannot show a
  filter "affecting" anything. (b) The test body has **zero assertions** — it only calls `voice.render(ctx)`. The
  mutation above (dropping the `process()` call entirely) is unobservable to this test no matter what filter is passed,
  because nothing is ever checked.

### 9. "SynthVoice with all modulations renders correctly"

- CLAIM (implied): a synth voice with vibrato + accelerate + pitch envelope + FM + envelope all active renders without
  error and "correctly."
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:48` (same as `SampleVoiceSpecificTest`
  claim #10) or any of the pitch-pipeline mutations above would be representative.
- SUSPICION: SUSPECT — **zero assertions**; smoke/crash test only, despite "renders correctly" in the name.

### 10. "SynthVoice signal receives correct buffer parameters"

- CLAIM: For a voice spanning frames [0,100) rendered against a block that exactly covers it, the Ignitor receives
  `ctx.offset == 0` and `ctx.length == 100` — i.e. `Voice.render()`'s clipping arithmetic (`vStart`, `offset`) is
  correct for the simple full-overlap case.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:107`
    - OLD: `        val offset = vStart - ctx.blockStart`
    - NEW: `        val offset = vStart - ctx.blockStart + 1`
- SUSPICION: LIKELY-RED — `receivedOffset shouldBe 0` fails (gets 1).

### 11. "SynthVoice with partial block renders correct length"

- CLAIM: For a voice spanning frames [50,150) rendered against a block [0,100), only the overlapping 50 frames are
  handed to the Ignitor (`ctx.length == 50`) — i.e. `Voice.render()` correctly clips `length` to the block/voice
  intersection.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:108`
    - OLD: `        val length = vEnd - vStart`
    - NEW: `        val length = vEnd - vStart - 1`
- SUSPICION: LIKELY-RED — `receivedLength shouldBe 50` fails (gets 49).

### 12. "SynthVoice tracks elapsed frames across multiple renders"

- CLAIM: `IgniteContext.voiceElapsedFrames` is derived fresh each block as `blockStart - startFrame` (not accumulated
  statefully), so three renders at absolute block-starts 0, 100, 200 report elapsed frames 0, 100, 200 respectively.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/ignite/IgniteRenderer.kt:35`
    - OLD: `        signalCtx.voiceElapsedFrames = ctx.blockStart - startFrame`
    - NEW: `        signalCtx.voiceElapsedFrames = ctx.blockStart - startFrame + 1`
- SUSPICION: LIKELY-RED — exact-equality checks (`elapsedFrames[0] shouldBe 0`, etc.) catch the constant +1 offset
  immediately.

---

## Notes

**Coverage holes (production behaviour these three specs never exercise):**

- **`SampleIgnitor`'s analog-drift branch is entirely dead code here.** All 16+2 sample-voice tests construct
  `SampleIgnitor` with the default `analog = 0.0`, and `VoiceTestHelpers.createSampleVoice()` doesn't even expose an
  `analog` parameter. Lines 44–70 of `SampleIgnitor.kt` (the wow/flutter loop, including `AnalogDrift.nextMultiplier()`
  interaction with playhead advance) have zero coverage from this trio of specs.
- **`VoiceFactory.kt` is completely bypassed.** All three specs construct `Voice` directly via `VoiceTestHelpers`,
  hand-building the pitch/filter pipeline and `BlockContext`. The actual production path — `VoiceFactory.makeVoice()`
  mapping a `ScheduledVoice`/`VoiceData` to a `Voice` (sample-metadata → `SampleIgnitor` construction,
  `PipelineRegistry` lookup, `driftUpdateRate`, filter `combine()`) — has no coverage from these files.
- **`Voice.render()`'s lifecycle early-returns are untested.** Every test in all three specs uses a voice whose
  `endFrame` (default 1000) comfortably contains the render block, and blocks always start at/after `startFrame`.
  Nothing exercises `if (ctx.blockStart >= endFrame) return false` (voice fully expired) or
  `if (blockEnd <= startFrame) return true` (block entirely before voice start) — the two boundary conditions that make
  voice culling/scheduling correct.
- **Release-phase interaction with sample playback is untested.** No test combines a `gateEndFrame < endFrame` (i.e. an
  actual release window) with `SampleIgnitor`'s own `stopFrame`/`isLooping` — the two independent "when does this voice
  go quiet" mechanisms are never exercised together.
- **`Voice.Compressor`, `Voice.Ducking`, `Voice.Delay`, `Voice.Reverb`, `Voice.Phaser` (top-level raw fields),
  `Voice.FilterModulator`, `body`/`vowel` orbit resonators, `cut` groups, and `gainMultiplier`/`setGainMultiplier`** are
  all either defaulted-off or entirely absent from every test in these three specs. `SendRenderer` (always appended to
  the pipeline in `Voice`'s init) is likewise never targeted by any assertion.
- **`AccelerateRenderer` and `PitchEnvelopeRenderer`** are only reached inside two "with all modulations renders
  correctly" smoke tests (one per spec) that assert nothing — effectively zero real coverage of glide/pitch-envelope
  correctness from these files.

**Tests whose name promises more than the assertions check (9 of 30, 30%, have zero assertions):**

- `SampleVoiceSpecificTest`: "performs linear interpolation", "playhead advances correctly", "with vibrato modulates
  playback rate", "with FM modulates playback rate", "getBaseFrequency returns sample base pitch", "with all modulations
  renders correctly".
- `SynthVoiceTest`: "getBaseFrequency returns freqHz", "with filter affects signal output", "with all modulations
  renders correctly".

All nine are smoke tests in practice (pass iff `render()` doesn't throw) despite names that describe specific, checkable
behaviour ("modulates playback rate", "affects signal output", "returns sample base pitch").

**Stale API reference:** `getBaseFrequency` (referenced in two test names) does not exist anywhere in
`audio_be/src/commonMain` — confirmed via repo-wide grep. Both tests are leftovers from an API that was presumably
renamed or removed during a refactor; the test bodies were never updated to match (or drop) the claim in their names.

**Duplicated hot-loop caveat:** `SampleIgnitor.generate()` implements the interpolation/loop/stop logic twice (once for
the analog-drift path, once for the clean path) for performance (branch-free hot loop). Every mutation targeting the
clean path has a byte-for-byte identical twin in the drift path at a different line number; disambiguation in this plan
is by line number and an explicit note, not by textual uniqueness of the OLD snippet alone.
