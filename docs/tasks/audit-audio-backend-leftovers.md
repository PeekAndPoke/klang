# Audio backend audit: the leftovers

Status: **open, correctness, small.** Carved out 2026-09-27 when the audio backend audit campaign
closed (brief: `docs/tasks-archive/2026-09/20260927-audio-backend-audit.md`, ledger:
`docs/audio-audit/`). The engine redesign rewrites most of what the campaign would have audited, and
new tests are mutation-checked under `/review-loop` Standard 2. What the rewrite does not fix on its
own is listed here, each item re-checked against the tree on 2026-09-27.

The three maintainer calls the audit parked (the `GuitarClickHuntTest` runtime, the two meanings of
`attackSeconds`, `VoiceTestHelpers` bypassing `VoiceFactory`) are not repeated here; they live in
[`future/audit-parked-decisions.md`](future/audit-parked-decisions.md). The `cut(0)` question (F19)
lives in [`future/cut-group-semantics.md`](future/cut-group-semantics.md).

## 1. Parity specs pinned to a clock production no longer produces

Since `IgniteRenderer` gained `+ ctx.offset` (2026-08-27), a voice's first block lands on
`voiceElapsedFrames == 0`. These specs still set `voiceElapsedFrames = -offset` for their
mid-block-onset rows, and most say in a comment that this is the production shape:

- `audio_be/src/commonTest/kotlin/filters/EqCoreSpec.kt:544`
- `audio_be/src/commonTest/kotlin/ignitor/EqIgnitorSpec.kt:645-646`
- `audio_be/src/commonTest/kotlin/ignitor/ConstantFoldParitySpec.kt:73-74`
- `audio_be/src/commonTest/kotlin/ignitor/IgnitorDslOptimizerRenderSpec.kt:80`
- `audio_be/src/commonTest/kotlin/ignitor/SinePartialBankSpec.kt:303` (new since the audit named
  the first four: the pattern is being copied)

They pass, because both sides of each A/B get the same context, but a negative clock clamps any
envelope in them to zero, so those rows compare near-silence and teach the wrong contract. Fix:
re-pin to `offset = 37, voiceElapsedFrames = 0`, re-run, and mutation-check any envelope-bearing row
that only starts exercising its envelope after the change. Origin:
`docs/tasks-archive/2026-08/20260831-voice-elapsed-frames-offset-mismatch.md`.

## 2. The JS audio thread has no tests

`audio_jsworklet` is the thread every browser sample passes through, and it has zero tests. The
command drain runs there (`port.onmessage` into `dispatcher.handle(cmd)`). F22 (chunked samples
losing their metadata at the worklet boundary) was found by hand. Decide what a worklet spec can
reach from Kotlin/JS without a real `AudioWorkletGlobalScope`, and guard at least the message
decoding and the sample hand-over.

## 3. Unbounded `Int` counters: finish the sweep

The audit's §6b fixed the dangerous one (`cursorFrame`, now `Double`, guarded by
`LongRunningTimelineSpec`). The rest of the first scan was never checked. At 48 kHz an `Int` per-block
counter is safe for years, a per-sample one overflows in 12.4 hours of uptime:

- `PlaybackEngine.kt:38` `quietBlocks`, `master/MasterBus.kt:139` `silentBlocks`,
  `cylinders/Cylinder.kt:325` `silentBlockCount`: reset when sound returns; check the never-audible
  path, and whether each counts blocks or samples.
- `voices/Voice.kt:381` `idCounter`: 2^31 voices, low risk, still unbounded; check what a wrapped or
  negative id breaks.

Per site: prove wrap-safety by inspection and write the argument in a comment, or seed the counter
near `Int.MAX_VALUE` in a spec and step across.

## 4. Cut groups hard-cut

`voices/VoiceScheduler.kt:638`: `// TODO: Use a fade out / release phase instead of hard cut?` A
choked voice is removed mid-sample, a known and untested click source. Decide by ear whether the
teardown fade the voice already has (`TeardownFadeRenderer`) should run here, then guard it in
`VoiceSchedulerSoloCutSpec`.

## 5. Delete the wasm stub

`audio_be/src/wasmJsMain/kotlin/AudioProcessingWasm.kt` is self-declared "pseudo-code logic"
(`return 0`, `return true`, a module-level `var phase`), wired to nothing and not compiled (the
`wasmJs` target in `audio_be/build.gradle.kts` is commented out). Delete it; check whether the rest
of `wasmJsMain` (`index_wasmJs.kt`, `wasm-processor-glue.js`) has any reason left to exist.

## Not carried over

- The unaudited subsystems (`effects/`, `filters/`, `ignitor/`, `master/`, root and lifecycle, the
  rest of `katalyst/`). If the net under code the redesign keeps (the reverb, the phaser, the delay
  line, the master stage) is ever in doubt, run the audit's protocol on that one file; the method
  is in the archived brief §4.
- F8 (three `EnvelopeTest` cases that cannot reach their named path): their subject,
  `EnvelopeRenderer`, retired with the voice strip.
- `ScratchBuffers.release()` going negative: guarded since (`unbalancedReleases`).
