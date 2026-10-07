# The engine's output is floating point

Status: **done 2026-10-07** (maintainer, 2026-10-07: "do this now, on this branch", `correctness-fixes`).

## What and why

The engine's output contract was 16-bit PCM in a `ShortArray`: `MasterStage.process` clipped and interleaved every
sample through `pcm16`, and `KlangAudioRenderer.renderBlock` / `PlaybackEngineDispatcher.renderBlock` handed that
`ShortArray` to every host. The browser's audio thread then turned it back into `Float32` for Web Audio. So every
sample heard in the browser was truncated to 16 bits without dither, for nothing: Web Audio takes floats. Everything
under one 16-bit step (1 / 32767, about 3.05e-5, -90.3 dBFS) was lost, and truncation toward zero added an error of up
to one step that follows the signal. `Short` is also on the stone rule's banned list.

## What we built

- **The output is a `StereoBuffer` of doubles.** `MasterStage.process(mix, out: StereoBuffer)` runs the DC blockers
  and the limiter on `mix` in place as before, then `clipOutput` writes `clipSample` of each sample into `out`. The
  clip rule is the old one without the quantisation: in `[-1, 1]` a sample passes untouched, above 1 it is 1.0,
  anything else (below -1, and NaN, which cannot arrive because the house limiter's ring stores non-finite samples as
  0.0) is -1.0. `mix` keeps the unclipped doubles.
- **Why two channels, not interleaved.** Both shapes cost the same number of copies: one loop at the master, one loop
  at each edge. Two channels is the engine's own layout (`StereoBuffer`) and Web Audio's (one `Float32Array` per
  channel), so the worklet's copy is two stride-1 loops and the master's loop has no index arithmetic. The only
  interleaved consumers are the 16-bit edges, and they need a conversion loop anyway, so the interleave folds into
  it for free. Doubles, not floats, because the 16-bit edge must reproduce the old integers exactly, and those were
  computed from the double.
- **The browser** (`KlangAudioWorklet`) copies `left` into output 0 and `right` into output 1 with `toFloat()`.
  No 16-bit step, no allocation (the buffer is preallocated in `Ctx` as before).
- **The 16-bit edge** has one home, `audio_be/src/commonMain/kotlin/_pcm16_edge.kt`: `pcm16(sample): Int` and
  `writePcm16(source, frames, bytes)`, which writes interleaved little-endian 16-bit bytes, `Int` arithmetic, no
  `Short`, no allocation. Its users: `JvmAudioBackend` (the `SourceDataLine`, replacing the `ShortBuffer` view) and
  `WavFileWriter` (whose header's 16-bit fields are now two bytes from an `Int`; its `channels` and
  `bitsPerSample` parameters became constants, since the data it writes is always stereo 16-bit).
- **`KlangOfflineRenderer.render`'s `onBlock`** hands `(out: StereoBuffer, frames: Int)`; the buffer is reused per
  block. The benchmarks (`IgnitorBenchmark`, `KlangBenchmark`, `SongBenchmark`) render into a `StereoBuffer`.

## The one value that moves in a WAV

The old `pcm16` put a mix sample of exactly -1.0 on -32767 and every sample below -1 on -32768. The float clip writes
-1.0 for both, so no edge can tell them apart. The edge (`pcm16`) keeps the case that happens bit-identical: a negative
clip, which the 20:1 house limiter lets through on a mix far too loud for it, still lands on -32768. A mix sample of
exactly -1.0 now lands one count lower, on -32768. Every other mix value gives the same integer as before
(`Pcm16EdgeSpec`: a literal table, a dense sweep over [-1.5, 1.5] in 1e-4 steps, and 22,000 random values, all
against the retired rule restated in the spec).

## Proof

- `OutputClipSpec` (audio_be): in-range samples pass bit for bit, including 1e-6 and 1e-300 (the old output made them
  0); the clamps and the non-finite rows; the loop writes each channel into its own and leaves `mix` untouched; a 1e-5
  sine through the real `MasterStage` reaches the output.
- `Pcm16EdgeSpec` (audio_be): the edge against the retired rule, and `writePcm16`'s byte layout, literal.
- `WavFileWriterSpec` (klang jvmTest): a two-block file, header and data, byte for byte.
- Mutation checks (mandatory tier), 11 mutants, 11 red: the clip's upper bound made exclusive, the clip quantising to
  16 bits again, the clip's last branch to 0.0, the loop's right channel reading left, `process` skipping the clip,
  the edge's -1 boundary back to the retired one, the edge rounding instead of truncating, the byte order swapped in
  `writePcm16` and in the WAV header, the WAV data size counted in frames, the offline `onBlock` reporting the
  interleaved count. Not covered by any spec: the worklet's copy loop and `JvmAudioBackend` (neither has a test
  source; the worklet's is `docs/tasks/audit-audio-backend-leftovers.md` §2(b)).
- The existing specs that read the 16-bit output now read the floats: an "audible" bar of 200 counts became 0.006
  (about -44 dBFS), "exact silence" became exact 0.0 (stricter, and green), the Katalyst render rows in the root
  `jvmTest` keep their 16-bit counts through `pcm16`.
- Runs on the day: `:audio_be:jvmTest` 2198 tests, `:audio_be:jsBrowserTest` 2103, `:klang:jvmTest` 33, `:klang:jsTest`,
  all green; `:jsBrowserProductionWebpack`, `:audio_jsworklet:assemble` and `:audio_benchmark:assemble` build.
- Coordinator's 18-song corpus, 2026-10-07: raw-mix and 16-bit hashes identical before and after (baseline rendered
  on HEAD 347a7c76 before the change, after on the final tree), and the new edge's bytes equal the old rule's on all
  18 songs. (The raw mix is untouched by construction: the clip writes to the output, never to `mix`.)

## Left open

- A NaN guard in the clip (`else if (sample < -1.0) -1.0 else 0.0`, one more compare on the out-of-range path only)
  would turn an impossible NaN into silence instead of a full-scale click. Proposed, not done: a sound decision.
- `klangscript/.../NativeInterop.kt` still converts to `Short` / `ShortArray` for Kotlin interop (out of scope).
