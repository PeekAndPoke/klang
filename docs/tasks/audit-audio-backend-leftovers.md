# Audio backend audit: the leftovers

Status: **§1 follow-up, §3 and §5 done 2026-10-07; §2 and §4 open for the maintainer**, investigated, with the
findings and a proposal each in "What was done" at the end (§2: what a worklet spec can reach, and its cost; §4: the
cut-group hard cut today and what the teardown fade would change). Carved out 2026-09-27 when the audio backend audit campaign
closed (brief: `docs/tasks-archive/2026-09/20260927-audio-backend-audit.md`, ledger:
`docs/audio-audit/`). The engine redesign rewrites most of what the campaign would have audited, and
new tests are mutation-checked under `/review-loop` Standard 2. What the rewrite does not fix on its
own is listed here, each item re-checked against the tree on 2026-09-27.

The three maintainer calls the audit parked (the `GuitarClickHuntTest` runtime, decided 2026-09-27 and done; the two meanings of
`attackSeconds`, `VoiceTestHelpers` bypassing `VoiceFactory`) are not repeated here; they live in
[`future/audit-parked-decisions.md`](future/audit-parked-decisions.md). The `cut(0)` question (F19)
lives in [`future/cut-group-semantics.md`](future/cut-group-semantics.md).

## 1. Parity specs pinned to a clock production no longer produces

> **DONE 2026-09-29.** All seven negative-clock rows re-pinned to `voiceElapsedFrames = 0` (the production clock of
> a mid-block onset), their comments corrected, `IgnitorDslOptimizerRenderSpec`'s helper advancing the clock by the
> frames each window rendered, and a not-silence floor on each onset row (each shown red by a mutation: a silent saw,
> a silent sine, a zeroed input). None of the rows has an envelope, so the old clock was output-inert rather than
> clamping anything; the fix corrects the contract the specs teach. **Follow-up found in review (confirmed by
> mutation):** the other parity rows of `EqCoreSpec` (`assertChainParity`) and the 10 `assertDslParity` call sites of
> `EqIgnitorSpec` have no silence floor and stay green on silence (a zeroed input, a silent saw). They compare two
> implementations, so a floor there is cheap; not done yet.

Since `IgniteRenderer` gained `+ ctx.offset` (2026-08-27), a voice's first block lands on
`voiceElapsedFrames == 0`. These specs still set `voiceElapsedFrames = -offset` for their
mid-block-onset rows, and most say in a comment that this is the production shape:

- `audio_be/src/commonTest/kotlin/filters/EqCoreSpec.kt:544`
- `audio_be/src/commonTest/kotlin/ignitor/EqIgnitorSpec.kt:627-628`
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

- `PlaybackEngine.kt:46` `quietBlocks`, `master/MasterBus.kt:193` `silentBlocks`,
  `cylinders/Cylinder.kt:279` `silentBlockCount`: reset when sound returns; check the never-audible
  path, and whether each counts blocks or samples. (Line numbers refreshed 2026-09-28; `quietBlocks` now saturates at
  the tail hold. Two per-sample counters arrived with phase 3 step 12 and are bounded by construction:
  `ChainSwap`'s drain `ageFrames`, restarted by each drain and capped at 20 s, and `TailRelease.releasedFrames`,
  which ends with its release, 4.5 s.)
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

## What was done (2026-10-07)

### §1 follow-up: silence floors on the remaining parity rows (done)

The floor sits in each helper, so every call site carries it. `EqCoreSpec.assertChainParity`: the oracle's finite
peak must exceed 0.1 % of the rendered input's finite peak (relative, so the denormal row's 1e-14 impulse passes;
that low because the extreme-clamp row's q-200 bandpass is still ringing up, 0.6 % of the input over the four
blocks). `EqIgnitorSpec.assertDslParity`: the chained side's finite peak must pass 0.01 at every voice frequency.
Mutations (one build-lock call each, `cp` restore, `cmp` verified): the input zeroed in `EqCoreSpec`: all 12
`assertChainParity` rows red on the floor ("the parity render is (near) silence"), plus the onset row on its own
floor; `toExciter` returning silence: all 10 `assertDslParity` rows red on the floor (and three other rows on their
own assertions).

### §3: the Int counter sweep (done, by proof; no counter needed a fix)

Each site now carries its argument in a comment, checked on the code:

- `PlaybackEngine.quietBlocks` counts blocks and saturates: `min(quietBlocks + 1, maxTailHoldBlocks)` on every path,
  a never-audible one included.
- `MasterBus.silentBlocks` counts blocks; every path of `updateTailState` resets it or lets it reach
  `TAIL_CHECK_INTERVAL_BLOCKS` (10), where it resets.
- `Cylinder.silentBlockCount` counts cleanup visits; once it reaches the grace every path leaves it at most the grace
  (reset on a tail, held at the grace while a voice plays, reset on deactivation), so an orbit silent forever, a muted
  one with notes included, never counts past it.
- `Voice.idCounter` turns negative after 2^31 ids and repeats only after 2^32. Its one reader, `VoiceLease`, compares
  ids for equality between voices co-active on one orbit and has no sentinel id (`VoiceLeaseSpec` already pins an
  owner with id -1), so neither a negative id nor a repeat can pass for a live owner.

No spec was seeded: three counters are bounded by construction and the fourth is equality-only, so a spec near
`Int.MAX_VALUE` would need a test hook into a private counter to restate the comment.

### §5: the wasm stub (done)

`git rm` of all of `audio_be/src/wasmJsMain/`: `AudioProcessingWasm.kt` (the stub), `index_wasmJs.kt` (a header and
a package line, nothing else) and `resources/wasm-processor-glue.js` (a processor whose only import was the stub's
`generateSineWave`). Nothing referenced them (grep over the repo: the task docs, the archived audit brief, and the
first-sound blog's architecture explorer, which is history and keeps its words). The source set was never compiled.
Left for the maintainer: the commented-out `wasmJs { }` block in `audio_be/build.gradle.kts` (lines 24 to 28), the
last trace of the 2025-12-26 attempt; delete it, or keep it as the bookmark for
`docs/tasks/future/high-performance-audio-backend.md`.

### §2: what a worklet spec can reach (investigated, not built)

What is guarded today: `WireCodecRoundTripSpec` (`audio_bridge` jsTest) proves the generated codec symmetric over
plain JS objects, a `Sample.Chunk` included; `SampleChunkRoundTripSpec` (`audio_be` commonTest) proves the splitter
against the reassembler, `meta` included (the F22 guard). Neither crosses a real port, and nothing touches the
envelope or the worklet's own code.

What is not: (a) `WorkletContract` (`audio_be` jsMain): the `#v` schema stamp, the mismatch error, and the trip
through a real `MessagePort` (structured clone of the encoded object: a `DoubleArray`, NaN, nulls, maps). (b) The
glue in `KlangAudioWorklet`: `port.onmessage` into `dispatcher.handle`, the per-channel copy of the engine's
floats into the output `Float32Array`s, the silenced warmup blocks, the cursor advance, the feedback drain into `port.sendFeed`.

What a Kotlin/JS spec can reach without an `AudioWorkletGlobalScope`:

- (a) all of it. `audio_be`'s JS tests run in headless Chrome (Karma), which has `MessageChannel`: `port1.sendCmd(cmd)`
  and `decodeCmd` on `port2.onmessage` is the production path minus the thread hop, structured clone included. A
  spec there would send one command of each family (a scheduled voice, a chunked sample with loop, adsr and anchor,
  a Katalyst registration with `SLOT_UNSET`), assert equality after the trip, and assert the mismatch error on a
  wrong `#v`. Mutation targets: the stamp dropped, the F22 revert (`meta` not read) seen across the real port.
  Cost: about two hours, one spec file in a new `audio_be/src/jsTest`, no production change.
- (b) not as the code stands. `KlangAudioWorklet` extends the external `AudioWorkletProcessor` (defined only in the
  worklet scope, so the ES2015 class fails at module load without it), reads the global `sampleRate`, and
  `audio_jsworklet` is an executable with no test source set; a global shim would test the shim. The proposal: move
  the `Ctx` body (render, convert, warmup, cursor, feedback drain) and the message handler into a plain class in
  `audio_be` jsMain (a block pump taking the output `Float32Array`s and a `MessagePort`), leave `KlangAudioWorklet`
  a shell of about 20 lines, and spec the pump over a `MessageChannel`: schedule a voice through the port, render
  blocks, assert the output equals the dispatcher's float samples (non-silent, L and R apart),
  the warmup blocks exactly 0, and the feedback arriving on the other port. Cost: half a day, the move mechanical
  (about 80 lines), one spec. The JS suite runs whole (`--tests` does not filter JS), a few seconds more.

Noticed on the way, done since: the worklet rendered into a `ShortArray`, so every browser sample was quantized to
16 bits before Web Audio's float path. Since 2026-10-07 the engine's output is floating point and the worklet copies
it straight into its outputs (`docs/tasks-archive/2026-10/20261007-float-output.md`).

### §4: the cut-group hard cut (investigated, not built; decide by ear)

The path today (`VoiceScheduler.activateVoice`, the TODO at line 638): when a voice with a cut group is promoted,
every active voice of that scheduler (one per playback) in the same group is removed from `active` on the spot.
Promotion runs in `process` at the START of the block in which the new voice begins (`absoluteStartSec <
blockEndSec`), before anything renders. So the choked voice's last sample is the last sample of the previous
block, at whatever level it had: a step to zero, the click. And it is cut up to 127 frames (2.6 ms at 48 kHz) BEFORE
the new voice's onset, a short gap in the group.

What running `TeardownFadeRenderer` there would change:

- The fade is 4 ms (`VCA_OFF_TEARDOWN_FADE_SECONDS`: 192 frames at 48 kHz, 176 at 44.1), a linear ramp to exact
  zero. Run at the cut, the choked voice keeps sounding until the new onset and fades over 4 ms after it: the choke
  lands on the onset frame instead of up to a block early, and the two overlap for 4 ms. An acoustic hat choke takes
  longer than that, so it would still read as "stopped dead".
- Mechanism: set the voice's `endFrame` to `onset + 4 ms` (the way `releaseGate` moves it), so the fade gets its full
  window (the new end is always ahead of the block being rendered). It is not a reuse as is: the fade stage sits only
  in the pipeline of a voice whose tree does not end in its own envelope (`BuiltIgnitor.endsInEnvelope`), and it must
  not be appended to every voice, because it would also shape the last 4 ms of every natural release (renders
  change). So a cut needs either a cut-only fade stage or a flag the fade reads. Also: the fade never starts before
  the voice's midpoint, so a voice younger than about 8 ms at the cut gets a shorter ramp; a culled voice (silent
  already) can still be dropped at once.
- CPU: the choked voice renders 1 to 3 more blocks per cut (onset offset plus 192 frames). A 16th-note hat at 120 bpm
  chokes 8 times a second: at most 24 extra voice-blocks a second against about 375 blocks a second per voice slot.
  Negligible.
- What else moves: a voice that stays in `active` longer keeps renewing its orbit lease, so for those blocks it can
  stay the orbit's owner (the zombie guardrail in `audio/MEMORY.md`: when a voice leaves `active` decides who owns the
  orbit's bus settings). On an orbit shared with other voices the mix can change; on a hat's own orbit it does not.
- The release phase the TODO also names is not the answer: a voice's own release can be long (an open hat's sample
  tail), which defeats the choke.

The guard, once decided, belongs in `VoiceSchedulerSoloCutSpec`: the choked voice's last rendered sample is 0 and
the step into the cut is bounded, at an onset mid-block. `docs/tasks/future/cut-group-semantics.md` holds the rest of
the cut questions (`cut(0)` and friends); this one could be settled with them.

