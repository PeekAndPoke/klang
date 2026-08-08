# Block size parity — one render quantum everywhere, and the browser is the blueprint

> Status: **FIXED**, 2026-08-07. Branch `master-dsl`.
> The other half of "the WAV sounds different from the browser"; see
> [sample-voice-onset-quantization](20260807-sample-voice-onset-quantization.md) for the timing half.

## The problem

Three surfaces ran three different DSP block sizes:

| Surface            | Block   | Set by                                                         |
|--------------------|---------|----------------------------------------------------------------|
| Browser worklet    | **128** | Chrome — the Web Audio render quantum. We do not get a choice. |
| JVM live backend   | **512** | `KlangPlayer.Options.blockSize`                                |
| Offline WAV render | **512** | `KlangOfflineRenderer` default + `--block-size`                |

`KlangPlayer.Options.blockSize` was also **silently ignored on JS** — `JsAudioBackend` never forwards it; the worklet
reads its own quantum out of the output buffer (`outputs[0][0].length`). So the option looked authoritative and wasn't.

## Why this is a sound bug, not a latency knob

Block size is a **tone parameter**. Several parts of the engine update once per block and derive their rate from it:

| Thing                                        | Where                       | Effect of a bigger block                             |
|----------------------------------------------|-----------------------------|------------------------------------------------------|
| `driftUpdateRate = sampleRate / blockFrames` | `VoiceFactory.kt`           | **analog drift time constants** — 375 Hz vs 93.75 Hz |
| SVF cutoff smoothing / `FilterModRenderer`   | `LowPassHighPassFilters.kt` | coarser filter movement granularity                  |
| `oldestAllowedSec = now - 5 * blockDuration` | `VoiceScheduler.kt`         | late-voice drop window: 13 ms vs 53 ms               |
| `MasterBus` chain crossfade start            | `MasterBus.kt`              | swap rounds to the current block                     |

The analog drift one is the headline. Drift was **tuned by ear in the browser** at 128 frames
(see [analog drift tuning](../tasks-archive/2026-06/20260617-analog-drift-coefficient-tuning.md)), so the 512-frame
offline render was running the drift layer at a quarter of the rate it was voiced for. A WAV was never going to sound
like what you heard.

## The fix

One canonical constant, and everything we control follows the browser:

```kotlin
// audio_be/.../AudioBackendContext.kt
const val RENDER_QUANTUM_FRAMES: Int = 128
```

| File                      | Change                                                                                      |
|---------------------------|---------------------------------------------------------------------------------------------|
| `AudioBackendContext.kt`  | new `RENDER_QUANTUM_FRAMES` + the doc explaining *why* it's a tone parameter                |
| `KlangPlayer.kt`          | `Options.blockSize` default `512` → `RENDER_QUANTUM_FRAMES` (fixes the JVM backend)         |
| `KlangOfflineRenderer.kt` | `blockFrames` default `512` → `RENDER_QUANTUM_FRAMES`                                       |
| `RenderWavCommand.kt`     | `--block-size` default `512` → `RENDER_QUANTUM_FRAMES`, help text says it changes the SOUND |
| `KlangAudioWorklet.kt`    | `console.warn` if the browser's quantum ever differs from the constant                      |

The worklet **keeps detecting** its quantum rather than asserting the constant — the browser hands it the output buffer
and we must render into whatever size it gives. The warning is there because if that ever changes (Chrome has floated a
`renderSizeHint`), offline renders silently stop matching live playback, and that is exactly the class of bug this task
fixes.

`blockFrames` stays a *parameter* on the renderer/context rather than becoming a hard constant, so component-level DSP
specs can still size their own buffers (`CrushRendererSpec`, `DelayLineSpec`,
`EnvelopeDeclickSpec` use 512 locally and are unaffected — they test one component, not the engine render quantum).

## Test fallout — worth knowing

Two `KlangOfflineRendererTest` cases compared `blocks.first()` between two renders to prove the audio differed. At 512
frames the first block reached past the master limiter's 5 ms (`LIMITER_LOOKAHEAD_SECONDS`, 240 frames @ 48 k) lookahead
delay; at 128 frames it does not, so block 0 is silence in **both** renders and the comparison failed.

Fixed by comparing the whole render instead of the first block — strictly stronger, and no longer coupled to how block
size relates to limiter latency. Compared **block-by-block** via
`ShortArray.contentEquals`, deliberately not by flattening: flattening boxes ~100k `Short`s per render and this spec
also runs on Kotlin/JS, where boxed types are banned outright.

`KlangOfflineRendererSampleTest` had `blocksPerCycle = 48_000 / 512` hardcoded → now derived from
`RENDER_QUANTUM_FRAMES`.

## Guard

`klang/src/commonTest/kotlin/BlockSizeParitySpec.kt` pins the three facts that must not drift: the live player's
default, the offline renderer's **actual emitted block length** (behavioural — that is the path that writes WAVs), and
the constant itself against the literal 128.

Both defaults are read from real objects, never compared against a named constant that is itself defined as
`RENDER_QUANTUM_FRAMES` — that shape is a tautology which only fails if someone edits the constant's own initializer,
and stays green for the edit that actually reintroduces the bug (`val blockSize: Int = 512`). The first version of this
guard had exactly that flaw; review caught it.

Mutation-checked: setting either default to a literal 512 turns exactly one case red.

## Benchmark caveat (pre-existing, surfaced by this change)

`SongBenchmark.peakSkipBlocks` excludes the start of each pass so cold-start allocation doesn't become the reported
peak. It is now a duration (`PEAK_SKIP_SECONDS`) rather than a block count, so it means the same ~0.35 s at any block
size. But it only ever covered **start-of-song** allocation: an orbit gated in by `filterWhen` (Seltsamere Dinge brings
orbits 4 and 5 in at cycles 16 and 28)
allocates its ~7.68 MB cylinder deep inside the measured window, and no start-of-pass skip can reach it. Those spikes
land in `peakBlockRtf`, and because `audioUsPerBlock` is now 4× smaller they read 4× higher than in the 512-frame
baselines.

The real fix is a robust peak (99th percentile, or drop the top N blocks per pass) rather than a time gate. Not done
here — it changes what the metric means, which deserves its own decision.

## Cost

The offline render now does 4× as many block iterations. Per-sample DSP work is unchanged, so the overhead is per-block
bookkeeping only. Renders are somewhat slower — that is the price of the render being the thing you actually heard.

## Still open

- **Sample rate is still not matched.** Offline is fixed at 48 kHz; the browser takes whatever the device gives
  (`resolveBestSampleRate` asks for 48 k and accepts the answer). On a 44.1 kHz device every rate-derived coefficient
  differs. Not fixable from our side — but the worklet logs its actual rate, and `--sample-rate` should be set to match
  it for a true A/B.
- **Drift is still block-rate derived rather than absolute.** Now that everything runs at 128 this is consistent, but it
  stays fragile: a future host with a different quantum re-opens the whole problem. The robust fix is to give
  `AnalogDrift` a fixed update rate and sub-step it. Not done — the constant buys correctness now without re-tuning
  anything by ear.
- **RNG is unseeded** (`AnalogDrift`, noise ignitors, supersaw jitter), so no two renders are ever identical.
  Deliberate — accepted as musically fine, explicitly out of scope for these fixes.
