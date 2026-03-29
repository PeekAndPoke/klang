# ExciterDsl — Open Items

## Per-Playback Numerical Attributes (CPS etc.)

Let the frontend push named numerical values (like `cps`) to the audio backend per-playback.
Exciters read these at audio rate for tempo-synced effects.

### Design

```
Frontend                          Bridge                          Backend
─────────                         ──────                          ───────
KlangPlayback.updateAttributes()
  → Cmd.SetAttribute(playbackId, name, value)
                                  → WorkletContract encode/decode
                                                                  → PlaybackCtx.attributes[name] = value
                                                                  → ExciteContext.attributes (ref to PlaybackCtx map)

ExciterDsl.Attribute("cps")       → at render time reads from ctx.attributes["cps"]
```

### Steps

1. `audio_bridge/KlangCommLink.kt` — add `Cmd.SetAttribute(playbackId, name: String, value: Double)`
2. `audio_be/voices/PlaybackCtx.kt` — add `val attributes: MutableMap<String, Double>`
3. `audio_be/exciter/ExciteContext.kt` — add `var attributes: Map<String, Double>`
4. `audio_be/voices/VoiceScheduler.kt` — handle `Cmd.SetAttribute`
5. `audio_bridge/ExciterDsl.kt` — add `ExciterDsl.Attribute(name: String)` node type
6. `audio_be/exciter/ExciterDslRuntime.kt` — runtime exciter reads `ctx.attributes[name]`
7. `klangscript/KlangScriptOsc.kt` — `Osc.cps()` → `ExciterDsl.Attribute("cps")`, `Osc.attr(name)`
8. `klang/KlangPlayback.kt` — `updateAttributes(attrs: Map<String, Double>)`
9. Sprudel integration — `SprudelPlayback` calls `playback.updateAttributes(mapOf("cps" to cps))`

### Usage

```javascript
let pad = Osc.register("syncPad",
    Osc.supersaw().tremolo(Osc.cps(), 0.5).lowpass(2000).adsr(0.01, 0.3, 0.5, 0.5)
)
note("c3 e3 g3").sound(pad)
```

---

## splitAndJoin Operator

Split a signal into parallel branches, process each independently, sum and normalize.

### Design

```javascript
Osc.supersaw().splitAndJoin(
    x => x.octaveUp(),
    x => x.octaveDown(),
    x => x.detune(7)
)
// Desugars to: (branch0 + branch1 + branch2) / 3
```

Each branch gets its own copy of the source tree (independent state).
Future `splitSignalAndJoin()` would share the same source buffer (true signal split).

### Implementation

- KlangScript: vararg arrow functions, call each with self, collect ExciterDsl results
- Register manually (like `Osc.register()`) since it needs to invoke KlangScript lambdas
- Result: `Plus(Plus(b0, b1), b2).div(Constant(numBranches))`

---

## Additional Arithmetic

- `exp()` — `e^self` for exponential curves (dB-to-linear, envelope shaping)
- `abs()` — absolute value
- `neg()` — negate signal (flip polarity)

Each needs: ExciterDsl subtype, runtime Exciter extension, KlangScript extension method, collectParams.

---

## Minor Items

- KSP: nested type alias resolution only one level deep (latent — no chained aliases exist today)
- `Osc.register()` casts without validation — wrong arg types produce ClassCastException instead of user-friendly error
