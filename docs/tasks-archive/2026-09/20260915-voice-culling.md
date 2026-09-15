# Voice culling — terminate voices whose output has gone (and will stay) inaudible

Status: **planned / ready to build.** Created 2026-07-04 (design worked out 2026-07-03 during the orbit-body
work). Sound-preserving, all-platform CPU win (helps JVM, JS, and any future native/Wasm backend by doing
*less work*). Self-contained; resumable cold. Engine work-stream — belongs in the Q3 nice-to-have track.

## One-line goal

A voice keeps rendering the full oscillator + filters + body + pedal chain until its scheduled frame lifetime
ends — even after it has decayed to silence. Cull it once its **actual output** stays below an audibility
floor, so dense sections stop paying for inaudible tails. **Ignitor-agnostic** (measures real output, infers
nothing from the ADSR) and **sound-preserving** at the default.

## Why (the motivating finding)

`Voice.render` returns `false` **only** when `ctx.blockStart >= endFrame` (`audio_be/.../voices/Voice.kt`) —
voices die purely by their scheduled frame lifetime, with **no amplitude-based termination**. Many voices —
especially percussive `sustain = 0` synths (all of Der Schmetterling's guitars/lead/bass) — decay to true
silence well before their gate+release lifetime ends, and then keep rendering the whole per-voice chain for
nothing. In dense sections that wasted work is a meaningful slice of the peak-block cost. Culling it is 100 %
inaudible at the default floor. (Complements the orbit-level body/vowel move — that cut per-voice effect cost;
this cuts the *number* of voices rendered.)

## Design — measure real output, gate on a per-voice lifetime fraction

Do **NOT** infer "will it be loud again?" from the ADSR — a custom ignitor can shape its amplitude however it
likes (internal envelope, LFO, sequenced gate). Instead measure the voice's **actual output** and let the
patch declare *when* culling is allowed via a param.

```kotlin
// in Voice.render, after the pipeline loop:
val lifeFrac = elapsedFrames.toDouble() / voiceLifetimeFrames        // 0..1 over startFrame..endFrame
if (blockCtx.voiceOutputPeak < AUDIBILITY_FLOOR) silentBlocks++ else silentBlocks = 0
if (lifeFrac >= cullAfter && silentBlocks >= SILENT_BLOCKS_TO_CULL) return false   // scheduler swap-removes it
```

- **The signal is the voice's real output peak**, measured in `SendRenderer` — which already reads every
  final-output sample of the voice (post-VCA), so it's ~zero extra cost. Ignitor-agnostic (works for any
  synthesis), and it also catches a post-VCA filter still *ringing* above the floor (ring > floor → counter
  resets → not culled).
- **`cullAfter`** — a new per-voice param (`0..1`, default ~`0.1`): the earliest fraction of the voice's
  scheduled life at which silence-culling may fire. `0` = cull as soon as silent; `1` = never cull. This is
  the author's escape hatch for drones, on/off-gated sounds, and sounds with an intended silent lead-in —
  they set it high. Nothing is assumed about how amplitude is produced.
- **The loud→silent→loud case** (a voice that goes quiet mid-life then returns) is now the author's explicit
  call via `cullAfter`, not a fragile ADSR guess. The default `0.1` + a −80 dB floor is safe for ordinary
  voices because anything genuinely swelling/returning crosses −80 dB almost immediately.
- **Constants** (tune-by-ear): `AUDIBILITY_FLOOR ≈ 1e-4` (−80 dB), `SILENT_BLOCKS_TO_CULL ≈ 3` (~32 ms @
  48k/512) so a momentary dip never culls.

## Why it's sound-preserving (at the default)

- Culls only after the voice's own output is < −80 dB continuously for ~32 ms — inaudible in a mix peaking
  near 0 dB; the existing 0.5 ms amp de-click (`EnvelopeRenderer`) means the cut point is near-zero-slope
  (no click).
- **Reverb/delay tails are untouched** — they live on the cylinder bus (`cylinder.reverbSendBuffer`), which
  keeps ringing after the voice stops; culling a silent voice only stops future ~zero sends. No tail
  truncation.
- Does not touch `superimpose` decorrelation, `analog` drift, or any audible voice — it removes only work that
  produces silence. `cullAfter` lets any sound opt out without special-casing the engine.

## Naming (open)

`cullAfter` reads cleanly (fraction of life). Alternatives: `cullEarliest` (original), `cullFrom`, or the
inverse `keepAlive` (where `keepAlive(1)` = never cull). User's call — trivial to rename.

## Files to change

- `audio_bridge/.../VoiceData.kt` — add `cullAfter: Double?` (engine default ~0.1 when null).
- `sprudel` DSL — a `.cullAfter(x)` modifier (+ alias, name TBD) writing that field.
- `audio_be/.../voices/strip/BlockContext.kt` — add `var voiceOutputPeak: Double`.
- `audio_be/.../voices/strip/send/SendRenderer.kt` — track the block's peak `|output|` into `voiceOutputPeak`
  (inside the loop it already runs; ~2 lines).
- `audio_be/.../voices/Voice.kt` — `silentBlocks` field + the lifetime-fraction cull check in `render()`
  (uses `startFrame`/`endFrame` it already holds; NO ADSR dependency).
- A tuning-consts file — `AUDIBILITY_FLOOR`, `SILENT_BLOCKS_TO_CULL`, `CULL_AFTER_DEFAULT`.
- (Diagnostics) surface active-vs-culled voice counts so the benchmark can measure the win.

## Verification

- **Measure the win** with the `runSongBenchmark` harness (`SongBenchmark*.kt`): add active/culled voice
  counters, run the frozen Der Schmetterling before/after → RTF delta + the fraction of voice-blocks that were
  silent tails. Expected: a meaningful cut given `sustain=0` + long decays.
- **Specs:** a voice that goes silent after its `cullAfter` fraction terminates within ~K blocks; a voice with
  `cullAfter=1` (or one silent *before* its `cullAfter` point) is NEVER culled; a `loud→silent→loud` voice
  under the default is documented behaviour (author raises `cullAfter`); a released voice's cylinder reverb
  tail keeps ringing.
- **Click-hunt harness** (`GuitarClickHuntTest.kt`) gains a heavy-decay setup — assert no click at the cut.
- Correctness (not byte-identical by design): offline-render a decay-heavy case with culling ON vs OFF and
  assert the sample-wise difference is everywhere below −80 dB (removed energy is sub-floor).
- `./gradlew :audio_be:jvmTest` green; by-ear check by the user (raw-engine philosophy).

## Effort

Small–medium. Localized to `SendRenderer` (measure), `Voice.render` (decide), `VoiceData` + sprudel DSL (the
param), tuning consts, and diagnostics. No scheduler change — the existing swap-remove on a `false` return
handles termination.

## Optional follow-up (only if the numbers demand it)

Per-orbit hard voice cap with quietest-voice stealing (fade-out steal) — bounds worst-case CPU even when many
voices are simultaneously *audible*. More invasive; defer until the culling numbers show it's needed.

## References

- Memory: `project_song_cpu_benchmark` (the benchmark + Der Schmetterling CPU findings), `project_sound_first`.
- Benchmark harness: `src/jvmMain/kotlin/SongBenchmark*.kt`, `FrozenSongs.kt`, `runSongBenchmark` gradle task.
