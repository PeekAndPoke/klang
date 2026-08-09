# Mutation-Check Plan — voices/{VoiceLifecycleTest, VoicePipelineTest, VoiceCompressorSpec}

Repo: `/opt/dev/peekandpoke/klang`. This is a PLAN only — no production/test files were edited, no build was run.

---

## io.peekandpoke.klang.audio_be.voices.VoiceLifecycleTest

Production files it exercises: `audio_be/src/commonMain/kotlin/voices/Voice.kt` (render lifecycle, `Voice.Envelope`),
`audio_be/src/commonTest/kotlin/voices/VoiceTestHelpers.kt` (voice construction — bypasses `VoiceFactory`),
`audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt` (gateEndFrame/release test only),
`audio_be/src/commonMain/kotlin/voices/strip/ignite/IgniteRenderer.kt`, `strip/filter/FilterPipelineBuilder.kt`,
`strip/filter/AudioFilterRenderer.kt` (NoOpFilter passthrough), `strip/send/SendRenderer.kt` (always runs, never
asserted on — see Notes), `strip/BlockContext.kt`, `engines/PipelinePreset.kt` / `audio_bridge/PipelineDsl.kt` (Modern
preset, hardcoded via the test helper).

### 1. "voice does not render before startFrame"

- CLAIM: When the queried block ends at or before the voice's `startFrame`, `render()` returns `true` (voice continues,
  pending) and writes no audio into the block buffer.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:103`
    - OLD: `        if (blockEnd <= startFrame) return true`
    - NEW: `        if (blockEnd < startFrame) return true`
- SUSPICION: SUSPECT — boundary degeneracy. Test uses `startFrame=100, endFrame=200, blockStart=0, blockFrames=100` so
  `blockEnd == startFrame == 100` exactly. With the flipped operator the early return no longer fires, but the
  fallthrough computes `vStart=max(0,100)=100`, `vEnd=min(100,200)=100`, `length=0` — a zero-length render that still
  leaves the buffer untouched and still returns `true` at the function's tail (line 124). Both the correct and the
  mutated code paths converge on the same observable result for this exact input; the test cannot tell them apart.

### 2. "voice does not render after endFrame"

- CLAIM: When the queried block starts at or after the voice's `endFrame`, `render()` returns `false` and the buffer is
  left untouched.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart > endFrame) return false`
- SUSPICION: LIKELY-RED — `blockStart=100, endFrame=100` are exactly equal. The mutated guard no longer fires, so
  execution falls through to `vStart=100, vEnd=min(200,100)=100, length=0` and hits the unconditional `return true` at
  the end of the function — flipping the result from `false` to `true`. `result shouldBe false` fails.

### 3. "voice starting at block boundary renders full block"

- CLAIM: When the voice's `startFrame` equals the queried block's start, the entire block is rendered with audio.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:108`
    - OLD: `        val length = vEnd - vStart`
    - NEW: `        val length = vEnd - vStart - 1`
- SUSPICION: LIKELY-RED — with `startFrame=0, endFrame=100, blockStart=0, blockFrames=100`, `length` becomes 99 instead
  of 100, so `ctx.voiceBuffer[99]` is left at its default `0.0`. `ctx.voiceBuffer.all { it == 1.0 }` fails.

### 4. "voice ending at block boundary renders full block"

- CLAIM (as implied by the name): When the voice's `endFrame` equals the queried block's end, the entire block is
  rendered with audio.
- MUTATION: same production line as claim #3 — `Voice.kt:108`, `vEnd - vStart` → `vEnd - vStart - 1`.
- SUSPICION: LIKELY-RED (same arithmetic as #3: `ctx.voiceBuffer[99]` stays `0.0`). **But note:** the test body is
  byte-for-byte identical to #3 (`startFrame=0, endFrame=100, blockStart=0, blockFrames=100`) — it does not actually
  construct an end-boundary scenario (e.g. a block whose end lands exactly at a voice `endFrame` that is *not* also the
  block's own length). See Notes — name does not match what the test does.

### 5. "voice starting mid-block renders partial buffer"

- CLAIM: When the voice starts partway through the queried block, samples before `startFrame` stay silent and samples
  from `startFrame` onward within the block carry audio.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:107`
    - OLD: `        val offset = vStart - ctx.blockStart`
    - NEW: `        val offset = vStart - ctx.blockStart - 1`
- SUSPICION: LIKELY-RED — with `startFrame=50, blockStart=0`, `offset` becomes 49 instead of 50, so index 49 gets
  written with audio (`1.0`). `ctx.voiceBuffer.take(50).all { it == 0.0 }` fails.

### 6. "voice ending mid-block renders partial buffer"

- CLAIM: When the voice's `endFrame` falls inside the queried block, samples up to `endFrame` carry audio and the
  remainder of the block stays silent.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:106`
    - OLD: `        val vEnd = minOf(blockEnd, endFrame)`
    - NEW: `        val vEnd = maxOf(blockEnd, endFrame)`
- SUSPICION: LIKELY-RED — with `endFrame=50, blockEnd=100`, `vEnd` becomes 100 instead of 50, so the voice renders the
  full block instead of clipping at 50. `ctx.voiceBuffer.takeLast(50).all { it == 0.0 }` fails (all `1.0` instead).

### 7. "voice spanning multiple blocks renders correctly"

- CLAIM: A voice spanning multiple blocks (`startFrame=0, endFrame=300`) renders full-block audio in every block it
  fully covers, and reports completion (`false`) once the queried block starts at/after `endFrame`.
- MUTATION: same production line as claim #3 — `Voice.kt:108`, `vEnd - vStart` → `vEnd - vStart - 1`.
- SUSPICION: LIKELY-RED — every one of the three full-coverage blocks (`ctx1`, `ctx2`, `ctx3`) is 100 samples long; the
  off-by-one leaves the last index of each block at `0.0`, failing `ctx1.voiceBuffer.all { it == 1.0 }` already on the
  first block.

### 8. "voice with single-frame duration works"

- CLAIM: A voice with a 1-frame duration (`endFrame = startFrame + 1`) renders exactly one sample of audio, at index
  `startFrame`, and silence everywhere else.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:108`
    - OLD: `        val length = vEnd - vStart`
    - NEW: `        val length = vEnd - vStart + 1`
- SUSPICION: LIKELY-RED — with `startFrame=50, endFrame=51`, `length` becomes 2 instead of 1, so `ctx.voiceBuffer[51]`
  (asserted `shouldBe 0.0`) gets written with `1.0` instead. Fails.

### 9. "voice with zero-duration (startFrame == endFrame) does not render"

- CLAIM: A voice whose `startFrame` equals its `endFrame` (zero duration) still reports itself as continuing (`true`)
  but renders no audio at all.
- MUTATION: same production line as claim #8 — `Voice.kt:108`, `vEnd - vStart` → `vEnd - vStart + 1`.
- SUSPICION: LIKELY-RED — with `startFrame=endFrame=50`, `length` becomes 1 instead of 0, so `ctx.voiceBuffer[50]` gets
  written with `1.0`. `ctx.voiceBuffer.all { it == 0.0 }` fails.

### 10. "gateEndFrame triggers release phase"

- CLAIM: Once `gateEndFrame` is reached, the voice enters its release phase: amplitude stays at sustain level through
  gate-end and then decreases monotonically afterward.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:79-82`
    - OLD:
      ```
                      val relPos = absPos - gateEndPos
                      val p = (relPos / relRateDen).coerceAtMost(1.0)
                      val omp = 1.0 - p
                      val shape = when (releaseCurve) {
      ```
    - NEW:
      ```
                      val relPos = absPos - gateEndPos
                      val p = (relPos / relRateDen).coerceAtMost(1.0)
                      val omp = p
                      val shape = when (releaseCurve) {
      ```
- SUSPICION: LIKELY-RED — this inverts the release curve. At gate-end (`p=0`), `omp` becomes `0` instead of `1`, so
  `shape≈0` and `atGateEnd` collapses to `~0.0` instead of `1.0`. `atGateEnd shouldBe 1.0` fails immediately. This test
  has real per-sample buffer-value assertions and is one of the best-targeted tests in the file.

### 11. "voice with startFrame > endFrame handles edge case"

- CLAIM: With an invalid configuration (`startFrame=100 > endFrame=50`), `render()` still returns `true` without
  crashing, even though the internal `length` computation goes negative.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:106`
    - OLD: `        val vEnd = minOf(blockEnd, endFrame)`
    - NEW: `        val vEnd = maxOf(blockEnd, endFrame)`
- SUSPICION: SUSPECT — the only assertion is `result shouldBe true`. With
  `blockStart=0, blockFrames=200, startFrame=100, endFrame=50`, neither `Voice.kt:102` nor `:103` fires for either the
  original or the mutated arithmetic, so execution always falls through to the unconditional `return true` at the tail
  of the function. The mutation flips `vEnd` from `50` (giving a negative `length=-50`, an empty Kotlin `IntRange`) to
  `200` (giving `length=100`, an actual 100-sample render) — a real behavioral change — yet `result` is `true` either
  way. Kotlin's `0 until negativeLength` doesn't throw, so there is also no crash to catch the bug indirectly. This test
  cannot detect any single-line arithmetic mutation to `vStart`/`vEnd`/`offset`/`length` short of one that throws.

### 12. "voice queried far before start returns true"

- CLAIM: A block queried far before the voice's `startFrame` (10000 vs. 0-100) still returns `true` (voice hasn't
  started, remains pending).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:103`
    - OLD: `        if (blockEnd <= startFrame) return true`
    - NEW: `        if (blockEnd <= startFrame) return false`
- SUSPICION: LIKELY-RED — branch-swap (not operator-flip, since `startFrame=10000` is far from `blockEnd=100`, so an
  operator flip alone wouldn't move the needle the way it did in claim #1). With the branch swapped, the guard now
  returns `false`; `result shouldBe true` fails.

### 13. "voice queried far after end returns false"

- CLAIM: A block queried far after the voice's `endFrame` (10000 vs. 0-100) reports the voice as finished (`false`).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart >= endFrame) return true`
- SUSPICION: LIKELY-RED — the guard now returns `true` for `blockStart=10000 >= endFrame=100`; `result shouldBe false`
  fails.

### 14. "SampleVoice lifecycle works same as SynthVoice"

- CLAIM: A `SampleVoice` (using `SampleIgnitor` instead of a synth oscillator) follows the same `startFrame`/`endFrame`
  lifecycle rules as a `SynthVoice` — silent before start, producing positive-valued audio once started.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:107`
    - OLD: `        val offset = vStart - ctx.blockStart`
    - NEW: `        val offset = vStart - ctx.blockStart - 1`
- SUSPICION: LIKELY-RED — with `startFrame=50, blockStart=0`, `offset` becomes 49, so index 49 (part of the
  asserted-silent `take(50)`) receives sample audio instead of staying `0.0`.
  `ctx.voiceBuffer.take(50).all { it == 0.0 }` fails.

### 15. "voice at exact block boundaries handles edge cases"

- CLAIM: Voice lifecycle at exact block boundaries — query ending exactly at `startFrame`, query starting exactly at
  `startFrame`, query starting exactly at `endFrame` — is each handled correctly (silent-and-continuing / full-render /
  finished).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart > endFrame) return false`
- SUSPICION: LIKELY-RED — targets `ctx4` (`blockStart=200==endFrame=200`). Unlike the `:103` boundary case in claim #1,
  this comparison's fallthrough is not degenerate: with the guard defeated, execution reaches the unconditional
  `return true` at the tail, flipping `ctx4.render(...)` from the expected `false` to `true`. **Note:** `ctx3` in this
  test (comment: "ends exactly at endFrame") uses byte-for-byte the same `blockStart=100, blockFrames=100` as `ctx2` (
  "starts exactly at startFrame") — it does not actually query an end-boundary case. See Notes.

### 16. "voice with very long duration works"

- CLAIM: Voice lifecycle logic remains correct at very large frame offsets (up to 1,000,000 frames) — renders `true`
  throughout the voice's duration and `false` once past `endFrame`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart > endFrame) return false`
- SUSPICION: LIKELY-RED — `ctx4` has `blockStart=1_000_000 == endFrame=1_000_000`; same exact-equality argument as claim
  #15 applies. `ctx4.render(...)` flips from `false` to `true`.

### 17. "gateEndFrame can equal startFrame (immediate release)"

- CLAIM: A voice whose gate ends immediately at its `startFrame` (zero attack/sustain time) starts directly in its
  release phase and still renders successfully.
- MUTATION: same production lines as claim #10 — `EnvelopeRenderer.kt:79-82`, `val omp = 1.0 - p` → `val omp = p`
  (release branch).
- SUSPICION: SUSPECT — the test body contains exactly one assertion, `voice.render(ctx) shouldBe true`, which is
  governed purely by `Voice.kt`'s lifecycle guards (`startFrame=100 < endFrame=200`, well inside range) and is
  completely insensitive to how `EnvelopeRenderer` shapes the release curve. The in-code comment ("Should start in
  release phase immediately... exact values depend on envelope calculation") explicitly acknowledges the untested part.
  No buffer/level is ever read.

### 18. "gateEndFrame after endFrame is handled correctly"

- CLAIM: When `gateEndFrame` is set beyond `endFrame` (release trigger scheduled after the voice would already end), the
  voice still terminates exactly at `endFrame`, ignoring the out-of-range `gateEndFrame`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart > endFrame) return false`
- SUSPICION: LIKELY-RED — same production line as claims #13/#15/#16 (different instantiation:
  `blockStart=100==endFrame=100` here). `ctx2.render(...)` flips from `false` to `true`.

---

## io.peekandpoke.klang.audio_be.voices.VoicePipelineTest

Production files it exercises: `audio_be/src/commonMain/kotlin/voices/Voice.kt`,
`voices/strip/filter/FilterPipelineBuilder.kt`,
`voices/strip/filter/{CrushRenderer,CoarseRenderer,DistortionRenderer,TremoloRenderer,StripPhaserRenderer,FilterModRenderer,EnvelopeRenderer,AudioFilterRenderer}.kt`,
`voices/strip/BlockContext.kt`, `engines/PipelinePreset.kt`, `audio_bridge/PipelineDsl.kt` (stage order),
`voices/strip/send/SendRenderer.kt` (always runs, never asserted on), `voices/VoiceTestHelpers.kt` (`SpyFilter` /
`TunableSpyFilter`).

### 1. "pipeline executes main filter"

- CLAIM: The voice pipeline invokes the configured main filter's `process()` exactly once per `render()` call.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:63-64`
    - OLD:
      ```
                  is StageDsl.Filter ->
                      add(AudioFilterRenderer.of(mainFilter))
      ```
    - NEW:
      ```
                  is StageDsl.Filter -> {
                      add(AudioFilterRenderer.of(mainFilter))
                      add(AudioFilterRenderer.of(mainFilter))
                  }
      ```
- SUSPICION: LIKELY-RED — `spyMainFilter.processCalls.size` becomes 2. `shouldBe 1` fails. This is a genuinely
  meaningful check (it does confirm the filter is wired into the pipeline exactly once), even though it says nothing
  about what the filter does to the audio.

### 2. "pipeline with crush renders successfully"

- CLAIM: A voice configured with a nonzero crush (bit-crush) amount renders successfully (doesn't break voice
  lifecycle).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:49`
    - OLD: `                if (crush.amount > 0.0) {`
    - NEW: `                if (crush.amount > 100.0) {`
- SUSPICION: SUSPECT — the only assertion is `result shouldBe true`, which is controlled entirely by `Voice.kt`'s
  lifecycle guards (default `startFrame=0, endFrame=1000`, block `0-100`) and is completely independent of whether
  `CrushRenderer` is ever added to the pipeline. With `crush.amount=4.0`, this mutation silently removes crush
  processing from the voice and the test still passes.

### 3. "pipeline with distortion renders successfully"

- CLAIM: A voice configured with a nonzero distortion amount renders successfully.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:59`
    - OLD: `                if (distort.amount > 0.0) {`
    - NEW: `                if (distort.amount > 100.0) {`
- SUSPICION: SUSPECT — same reasoning as claim #2: `distort.amount=0.5` no longer reaches the `> 100.0` bar,
  `DistortionRenderer` is never added, `result` is still `true`.

### 4. "pipeline with multiple pre-filters renders successfully"

- CLAIM: A voice with both crush and coarse waveshapers active renders successfully.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:54`
    - OLD: `                if (coarse.amount > 1.0) {`
    - NEW: `                if (coarse.amount > 100.0) {`
- SUSPICION: SUSPECT — `coarse.amount=2.0` no longer passes; `CoarseRenderer` is silently dropped. `result` unaffected.

### 5. "tremolo renders successfully"

- CLAIM: A voice with tremolo modulation renders across two consecutive blocks without error, and its internal LFO phase
  state carries over correctly between the two `render()` calls.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/TremoloRenderer.kt:36`
    - OLD: `            val gain = 1.0 - (depth * (1.0 - lfoNorm))`
    - NEW: `            val gain = 1.0 + (depth * (1.0 - lfoNorm))`
- SUSPICION: SUSPECT — the test body has **zero assertions**; it only calls `voice.render(ctx)` twice. Any non-crashing
  change to the tremolo gain formula (even a sign flip that turns attenuation into boost) is invisible. This is stronger
  toothlessness than claims #2-4: it doesn't even check the lifecycle boolean.

### 6. "phaser renders successfully with defaults"

- CLAIM: When `phaser.center`/`phaser.sweep` are `0.0`, the pipeline substitutes the documented default of `1000.0` Hz,
  and the voice renders across two consecutive blocks without error.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:77`
    - OLD: `                            center = if (phaser.center > 0) phaser.center else 1000.0,`
    - NEW: `                            center = if (phaser.center > 0) phaser.center else -1000.0,`
- SUSPICION: SUSPECT — zero assertions in the test body (same pattern as claim #5). The test's own comment ("Should
  default to 1000.0") documents an expectation that is never actually checked.

### 7. "voice with no effects renders successfully"

- CLAIM: A voice with all optional waveshapers explicitly disabled (`crush=0, coarse=0, distort=0`) still renders
  successfully.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:59`
    - OLD: `                if (distort.amount > 0.0) {`
    - NEW: `                if (distort.amount >= 0.0) {`
- SUSPICION: SUSPECT — doubly so: (a) `result shouldBe true` is a pure lifecycle check, unrelated to effect wiring (same
  as #2-4); (b) this specific mutation is additionally neutral even in isolation — flipping to `>= 0.0` makes
  `DistortionRenderer(0.0, ...)` get *added* at `amount=0.0`, but `DistortionRenderer.render()` has its own internal
  guard (`if (amount <= 0.0) return`) that makes it a no-op anyway, so even a spy-based test on pipeline contents
  wouldn't see a difference in rendered audio.

### 8. "voice with all effects enabled renders successfully"

- CLAIM: A voice with crush, coarse, distort, tremolo, and phaser all simultaneously active renders successfully.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:72`
    - OLD: `                if (phaser.depth > 0.0) {`
    - NEW: `                if (phaser.depth > 100.0) {`
- SUSPICION: SUSPECT — `phaser.depth=0.5` no longer passes; `StripPhaserRenderer` is silently dropped from a pipeline
  meant to exercise "all effects enabled." `result shouldBe true` is unaffected.

### 9. "filter modulation updates cutoff before filter processes"

- CLAIM: When a filter modulator is attached, its envelope-driven cutoff update is applied to the filter **before** the
  main filter's audio processing runs for that block (`FilterMod` stage precedes `Filter` stage).
- MUTATION: `audio_bridge/src/commonMain/kotlin/PipelineDsl.kt:33-37`
    - OLD:
      ```
                  StageDsl.FilterMod,
                  StageDsl.Crush,
                  StageDsl.Coarse,
                  StageDsl.Distort,
                  StageDsl.Filter(),
      ```
    - NEW:
      ```
                  StageDsl.Filter(),
                  StageDsl.Crush,
                  StageDsl.Coarse,
                  StageDsl.Distort,
                  StageDsl.FilterMod,
      ```
- SUSPICION: SUSPECT — this reorders the pipeline so the main filter processes the block *before* `FilterModRenderer`
  updates the cutoff, directly contradicting the test's own name. Yet all three assertions still pass:
  `cutoffHistory.size shouldBe 1` (still called once, just later), `cutoffHistory[0] shouldBeGreaterThan baseCutoff`
  (the value is computed from `calculateControlRateEnvelope(ctx.blockStart, ...)`, independent of when in the pipeline
  it runs), and `processCalls.size shouldBe 1` (still called once, just earlier). `SpyFilter.process()` is a pure stub
  that ignores the filter's cutoff state, so nothing in the recorded data can distinguish "cutoff set then processed"
  from "processed then cutoff set." Textbook "test name promises more than the assertions check."

### 10. "envelope is applied after main filter"

- CLAIM: The ADSR envelope (VCA) stage processes audio strictly **after** the main filter stage (Modern preset places
  `Vca` last).
- MUTATION: `audio_bridge/src/commonMain/kotlin/PipelineDsl.kt:31-40`
    - OLD:
      ```
              val modern: PipelineDsl = PipelineDsl(
                  listOf(
                      StageDsl.FilterMod,
                      StageDsl.Crush,
                      StageDsl.Coarse,
                      StageDsl.Distort,
                      StageDsl.Filter(),
                      StageDsl.Tremolo,
                      StageDsl.Phaser,
                      StageDsl.Vca(),
                  )
              )
      ```
    - NEW:
      ```
              val modern: PipelineDsl = PipelineDsl(
                  listOf(
                      StageDsl.Vca(),
                      StageDsl.FilterMod,
                      StageDsl.Crush,
                      StageDsl.Coarse,
                      StageDsl.Distort,
                      StageDsl.Filter(),
                      StageDsl.Tremolo,
                      StageDsl.Phaser,
                  )
              )
      ```
- SUSPICION: SUSPECT — moving `Vca` to the front means the envelope now runs *before* the filter, the exact opposite of
  the claim. The test's only assertion, `spyMainFilter.processCalls.size shouldBe 1`, is identical to claim #1's
  assertion and cannot detect ordering at all — the filter is still called exactly once regardless of where in the chain
  it sits. Confirmed toothless duplicate.

### 11. "voice renders correct number of samples"

- CLAIM: The voice renders exactly the number of samples corresponding to the queried block length (100 samples for a
  100-frame block).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:108`
    - OLD: `        val length = vEnd - vStart`
    - NEW: `        val length = vEnd - vStart - 50`
- SUSPICION: SUSPECT — the test body is `voice.render(ctx)` with **no assertions whatsoever**, not even on the return
  value. A mutation that renders half the expected samples (or zero, or a crash-free garbage value) is completely
  invisible. The most toothless test in this spec — its name is a direct, specific, and entirely unchecked claim.

### 12. "voice starting mid-block renders partial buffer"

- CLAIM (per name): Only the portion of the buffer at/after `startFrame` carries audio when the voice starts mid-block.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:107`
    - OLD: `        val offset = vStart - ctx.blockStart`
    - NEW: `        val offset = vStart - ctx.blockStart - 1`
- SUSPICION: SUSPECT — unlike `VoiceLifecycleTest`'s identically-named test (claim #5 above), this version only asserts
  `result shouldBe true`; it never reads `ctx.voiceBuffer`. The off-by-one offset — which genuinely breaks "partial
  buffer" correctness — leaves the lifecycle boolean unchanged, so the test passes regardless. Weaker duplicate of a
  properly-checked test elsewhere in the same package.

### 13. "voice ending mid-block renders partial buffer"

- CLAIM (per name): Audio is clipped at `endFrame` when the voice ends mid-block.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:106`
    - OLD: `        val vEnd = minOf(blockEnd, endFrame)`
    - NEW: `        val vEnd = maxOf(blockEnd, endFrame)`
- SUSPICION: SUSPECT — same gap as claim #12: only `result shouldBe true` is checked. The mutation turns a
  correctly-clipped partial render into a full over-run past `endFrame`, and the boolean stays `true` either way.

### 14. "voice before startFrame returns true without rendering"

- CLAIM: When queried before its `startFrame`, the voice returns `true` **and writes no audio** ("without rendering").
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:103`
    - OLD: `        if (blockEnd <= startFrame) return true`
    - NEW: `        if (blockEnd <= startFrame) return false`
- SUSPICION: LIKELY-RED for the given mutation (`blockEnd=100 <= startFrame=100`, branch-swap flips `result` to `false`,
  `result shouldBe true` fails) — **but** the "without rendering" half of the claim is never checked (no buffer
  assertion), so a mutation that broke *only* the silence guarantee (e.g. corrupting `offset`/`length` while leaving the
  early-return intact) would sail through undetected. Partial coverage gap noted even though this specific mutation is
  caught.

### 15. "voice after endFrame returns false"

- CLAIM: When queried at/after its `endFrame`, the voice reports itself finished (`false`).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart >= endFrame) return true`
- SUSPICION: LIKELY-RED — `blockStart=100 >= endFrame=100`; mutated guard returns `true`, `result shouldBe false` fails.

---

## io.peekandpoke.klang.audio_be.voices.VoiceCompressorSpec

Production files it exercises: `audio_be/src/commonMain/kotlin/voices/Voice.kt` (`Voice.Compressor.fromStringConfig`),
`audio_be/src/commonMain/kotlin/effects/Compressor.kt` (`Compressor.parseSettings`). Does **not** touch
`VoiceScheduler`, `SendRenderer`, or any DSP application of the compressor (that happens at
`cylinders/Cylinder.kt:181-216`, outside this spec's scope).

### 1. "fromStringConfig returns null for null input"

- CLAIM: Passing `null` as the config string returns `null` (no compressor object is constructed).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:212`
    - OLD: `                } ?: return null`
    - NEW: `                } ?: return Compressor(0.0, 0.0, 0.0, 0.0, 0.0)`
- SUSPICION: LIKELY-RED — `fromStringConfig(null)` now returns a non-null `Compressor(0.0,...)` instead of `null`.
  `shouldBe null` fails.

### 2. "fromStringConfig returns null for invalid input"

- CLAIM: Malformed config strings ("invalid", "", "abc:def" — none of which split into a 5-part or 2-part all-numeric
  list) all yield `null`.
- MUTATION: `audio_be/src/commonMain/kotlin/effects/Compressor.kt:274`
    - OLD: `                else -> null`
    - NEW: `                else -> CompressorSettings(0.0, 1.0, 6.0, 0.003, 0.1)`
- SUSPICION: LIKELY-RED — all three malformed inputs (`parts.size` of 0 or 1) now resolve to a non-null default
  `CompressorSettings`, which `fromStringConfig` wraps into a non-null `Compressor`. `shouldBe null` fails for all three
  assertions in the test.

### 3. "fromStringConfig parses full format"

- CLAIM: The 5-part colon-separated format `"threshold:ratio:knee:attack:release"` is parsed positionally in that exact
  order.
- MUTATION: `audio_be/src/commonMain/kotlin/effects/Compressor.kt:260-261`
    - OLD:
      ```
                      ratio = parts[1],
                      kneeDb = parts[2],
      ```
    - NEW:
      ```
                      ratio = parts[2],
                      kneeDb = parts[1],
      ```
- SUSPICION: LIKELY-RED — for input `"-20:4:6:0.003:0.1"`, `ratio` becomes `6.0` instead of `4.0`.
  `c.ratio shouldBe 4.0` fails (and `c.kneeDb shouldBe 6.0` would coincidentally still pass with value `4.0`... no —
  `kneeDb` becomes `4.0`, so `c.kneeDb shouldBe 6.0` also fails). Clean catch either way.

### 4. "fromStringConfig parses short format (threshold:ratio only)"

- CLAIM: The 2-part short format `"threshold:ratio"` parses `threshold`/`ratio` positionally and defaults
  `kneeDb=6.0, attackSeconds=0.003, releaseSeconds=0.1`.
- MUTATION: `audio_be/src/commonMain/kotlin/effects/Compressor.kt:269`
    - OLD: `                    kneeDb = 6.0,`
    - NEW: `                    kneeDb = 3.0,`
- SUSPICION: LIKELY-RED — `c.kneeDb shouldBe 6.0` fails (`3.0` returned instead). This is the only default-value
  assertion in the test; each of the three other defaults (`attackSeconds`, `releaseSeconds`) is equally well-guarded by
  its own `shouldBe` — a symmetric mutation on either would also go red.

---

## Notes

### Verdict on the three known holes

1. **`VoiceScheduler.kt` (407 lines) has no spec — CONFIRMED.** None of the three specs under review reference
   `VoiceScheduler` at all — they call `voice.render(ctx)` directly via `VoiceTestHelpers`, bypassing scheduling
   entirely. A repo-wide check (`grep -rln "VoiceScheduler(" audio_be/src/`) shows the class is only ever *constructed*
   in production code (`audio_be/src/commonMain/kotlin/PlaybackEngine.kt`); no file under `audio_be/src/commonTest/`
   constructs one. (`SampleStoreSpec.kt` mentions `VoiceScheduler` only in a doc comment.) Every method on it —
   `scheduleVoice`, `scheduleVoices`, `replaceVoices`, `dedupAgainstActive`, `process`, `promoteScheduled`,
   `ensureEpoch`, `cleanup`/`cleanupHard` — is untested.

2. **`strip/send/SendRenderer.kt` has zero test references — CONFIRMED, with a nuance.** No test in the repo imports
   `SendRenderer` or asserts on cylinder output (`mixBuffer`, `delaySendBuffer`, `reverbSendBuffer`) — confirmed via
   `grep -rn "cylinder|mixBuffer|delaySend|reverbSend|SendRenderer" audio_be/src/commonTest/kotlin/voices/*.kt` (zero
   hits) and `grep -rln "SendRenderer" audio_be/src/commonTest/` (zero hits). *However*, `SendRenderer` is **not dead
   code from these tests' point of view** — `Voice.kt:81` unconditionally appends `SendRenderer(voice = this)` to every
   voice's pipeline, so it executes on literally every `voice.render(ctx)` call in both `VoiceLifecycleTest` and
   `VoicePipelineTest` (all 33 combined test cases). It just runs blind: equal-power pan law (`cos`/`sin` of
   `panAngle`), `postGain` application, `gainMultiplier` interaction, and the `sendToDelay`/`sendToReverb` threshold
   gates are all exercised at the code-coverage level but have no assertion anywhere that could catch a broken pan law,
   a dropped `postGain` multiply, or an inverted delay/reverb send gate.

3. **solo/mute and cut/choke have zero tests — CONFIRMED.**
   `grep -rniln "solo|\.cut\b|cutGroup|choke" audio_be/src/commonTest/` returns nothing. In production,
   `VoiceScheduler.process()` computes `activeSoloSourceIds`/`maxSoloAmount`/`soloMuteRamp`/
   `targetGain = 1.0 - maxSoloAmount*0.95` (lines ~262-301) and `promoteScheduled()` implements cut-group hard-kill by
   iterating `active` and removing any voice whose `voice.cut == cut` (lines ~382-392) — both entirely unexercised,
   consistent with hole #1 (no `VoiceScheduler` spec at all). Additionally, `Voice.gainMultiplier`/`setGainMultiplier`
   (the mechanism solo/mute rides on) is never set to anything but its default `1.0` by any of the three specs, and
   `SendRenderer`'s `effectiveGain = voice.gain * voice.gainMultiplier` (the point where a solo/mute ramp would actually
   become audible) is unchecked per hole #2.

### Other production behavior with no test claim in this trio (coverage-hole list)

- `Voice.Ducking` (sidechain) — carried on `Voice` but applied at `cylinders/Cylinder.kt`, not in the voice-strip
  pipeline; out of scope for these specs but worth flagging as *entirely* untested by anything at the `Voice`/
  `VoiceScheduler` layer.
- `Voice.Compressor`'s actual DSP application — also lives in `cylinders/Cylinder.kt:181-216`; `VoiceCompressorSpec`
  only tests string parsing (consistent with its own name), never the runtime effect.
- `Voice.FilterModulator.drift` (`AnalogDrift`, driven by `analog > 0`) — never exercised; `VoicePipelineTest`'s one
  filter-modulation test builds `Voice.FilterModulator` directly with `drift = null` (the default).
- `PipelinePreset.Pedal` (`PipelineDsl.pedal`) — never referenced by any of the three specs; only `Modern` is exercised
  (hardcoded into `VoiceTestHelpers.createVoice`).
- `VoiceFactory.kt` (563 lines: gain = `baseGain * velocity`, legato/clip duration math, sample loop/pitch-ratio
  resolution, `perVoiceCutoffOffsetMul` randomization, ignitor-vs-sample branch, ADSR merge-with-sample-meta) is
  **entirely bypassed** by all three specs — `VoiceTestHelpers.createVoice` hand-rolls its own parallel pipeline
  construction instead of calling `VoiceFactory.makeVoice()`. This is not a raw coverage gap (there is a separate
  `VoiceFactoryFilterOrderSpec.kt` in the same test package that does exercise `VoiceFactory`), but it means none of the
  37 test cases planned here say anything about whether `VoiceFactory`'s wiring is correct.
- `Voice.Fm` (FM synthesis) — `fm` stays `null` (default) in every test case in this trio.
- `calculateControlRateEnvelope` / `envelopeLevelAtPosition` in `strip/EnvelopeCalc.kt` are dead from this trio's
  perspective for the *envelope* itself (`EnvelopeRenderer` has its own inline per-sample calc); they're only reachable
  via `FilterModRenderer`, which is touched once (test #9 in `VoicePipelineTest`) but only for the ordering claim, not
  for verifying the shared envelope-shape math itself.

### Tests whose name doesn't match what they do

- `VoiceLifecycleTest` **"voice ending at block boundary renders full block"** — byte-for-byte identical
  setup/assertions to the preceding **"voice starting at block boundary renders full block"**; does not construct an
  end-boundary scenario at all.
- `VoiceLifecycleTest` **"voice at exact block boundaries handles edge cases"** — its 3rd sub-case (`ctx3`, comment
  "ends exactly at endFrame") uses the exact same `blockStart=100, blockFrames=100` as `ctx2` ("starts exactly at
  startFrame"); the end-boundary case it claims to check is never actually queried (that scenario is instead covered,
  correctly, by `ctx4`, which the comment mislabels as "starts exactly at endFrame").
- `VoicePipelineTest` **"envelope is applied after main filter"** — assertion (`processCalls.size shouldBe 1`) is
  identical to the unrelated **"pipeline executes main filter"** test; no ordering is checked anywhere.
- `VoicePipelineTest` **"filter modulation updates cutoff before filter processes"** — the "before" in the name is never
  verified; see claim #9 above.
- `VoicePipelineTest` **"voice renders correct number of samples"** — zero assertions; no sample count is ever read or
  compared.
- `VoicePipelineTest` **"tremolo renders successfully"** and **"phaser renders successfully with defaults"** — zero
  assertions in the body; "renders successfully" isn't checked even via a return-value assertion (both call
  `voice.render(ctx)` and discard the result).
- `VoicePipelineTest` **"voice starting mid-block renders partial buffer"** / **"voice ending mid-block renders partial
  buffer"** — despite the name, no buffer content is ever inspected; only the lifecycle boolean is checked (contrast
  with the properly-checked, identically-named tests in `VoiceLifecycleTest`).

### Test cases that could not be mapped to a mutation

None — every one of the 37 test cases (18 + 15 + 4) got a concrete proposed mutation, even where the SUSPICION is that
the mutation (or any mutation targeting that claim) will not actually be caught.
