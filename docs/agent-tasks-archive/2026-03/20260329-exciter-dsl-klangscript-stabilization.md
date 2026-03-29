# ExciterDsl + KlangScript Integration — Stabilization & Next Steps

**Date**: 2026-03-29
**Status**: In progress

## What Was Done This Session

### 1. ExciterDsl sealed interface refactor

- `ExciterDsl` changed from `sealed class` to `sealed interface`
- Each subtype implements `collectParams()` directly — compiler enforces it for new types
- `voices` param changed from `Int` to `ExciterDsl.Param` — fully discoverable
- `gain` param removed from all oscillator types — users compose with `.mul()`
- `freq` param added to all oscillators — `Osc.sine(5)` = 5 Hz LFO
- `ExciterDsl.Constant(value)` added — explicit values that oscParams can never override
- `ExciterDsl.Param(name, default)` remains for named, overridable parameter slots
- `mul` and `div` now take `ExciterDsl` (not `Double`) — consistent with `plus`/`times`
- `PerlinNoise` and `BerlinNoise` exciters added
- Voice count cap removed (`coerceIn(1, 32)` → uncapped)

### 2. KlangScript Osc API

- `KlangScriptOsc` object with factory methods: `Osc.sine()`, `Osc.saw()`, `Osc.supersaw()`, etc.
- `KlangScriptExciterDslExtensions` — extension methods on ExciterDsl: `.lowpass()`, `.adsr()`, `.mul()`, etc.
- `ExciterDslLike` typealias — all params accept `Number` or `ExciterDsl`
- `toExciterDsl()` converts Numbers to `Constant` (not `Param` — deliberate)
- `Osc.register(name, tree)` sends `Cmd.RegisterExciter` to backend, returns string name
- `Osc.param(name, default)` for named overridable params
- `Osc.constant(value)` for locked values

### 3. Bridge & Backend

- `Cmd.RegisterExciter(playbackId, name, dsl)` added to `KlangCommLink`
- `WorkletContract` encode/decode via kotlinx serialization
- `KlangAudioWorklet` and `JsAudioBackend`/`JvmAudioBackend` handlers
- `ExciterRegistry` changed to parent-chain (child delegates to parent for missing keys)
    - Fixes: re-registering a sound during live playback now takes effect immediately

### 4. KlangScriptEngine improvements

- `val attrs: MutableTypedAttributes` on engine — app sets registrar callback
- `registerExtensionMethodWithEngine()` — native methods can access engine at call time
- `NativeExtensionMethod.invoker` signature expanded with `engine` parameter

### 5. KSP improvements

- Default parameter support — generates arity-dispatching code (`if (args.size >= N)`)
- Type alias resolution — KSP resolves typealiases to underlying types
- `@file:Suppress("UNCHECKED_CAST")` on generated files

### 6. Other fixes

- CodeMirror highlight overlay horizontal scroll fix (translateX on scroll)
- `@codemirror/language` version dedup fix (6.12.2 → 6.12.3)
- KSP `expect`/`actual` → `kspCommonMainMetadata` refactor (both sprudel and klangscript)
- Float32Array precision test fix (`plusOrMinus` for JS)

## What Needs Stabilization

### Tests needed

- [ ] ExciterDsl serialization tests for new types: `Constant`, `PerlinNoise`, `BerlinNoise`
- [ ] ExciterDsl serialization test for `freq` param on oscillators
- [ ] KlangScript integration tests: `Osc.sine(5)`, `Osc.sine().lowpass(1000)`, `Osc.register()`
- [ ] Test that `Constant` values are NOT overridden by oscParams
- [ ] Test that `Param` values ARE overridden by oscParams
- [ ] Test `resolveFreq` — freq=0 uses voice freqHz, freq=5 overrides
- [ ] JS test pass for all new exciter types

### Code quality

- [ ] Remove unused `ExciterDsl.octaveUp()`/`octaveDown()` builder extensions (if not already)
- [ ] Verify `PlayableCodeExample.kt` also wires up `createEngineAttrs` (currently only CodeSongPage does)
- [ ] KDoc on `ExciterDsl.Constant`
- [ ] KDoc on `resolveFreq` helper in Exciters.kt

### Known issues

- [ ] `Osc.register()` in REPL (`KlangScriptReplComp`) doesn't have engine attrs wired — needs same treatment as
  CodeSongPage
- [ ] Super oscillator factory methods in `KlangScriptOsc` don't accept `freq` param yet

---

## Next Feature: Per-Playback Numerical Attributes (CPS etc.)

### Goal

Let the frontend push named numerical values (like `cps`) to the audio backend per-playback. Exciters can read these
values at audio rate for tempo-synced effects.

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

### Implementation steps

1. **`audio_bridge/KlangCommLink.kt`** — add `Cmd.SetAttribute(playbackId, name: String, value: Double)`

2. **`audio_be/voices/PlaybackCtx.kt`** — add `val attributes: MutableMap<String, Double>`

3. **`audio_be/exciter/ExciteContext.kt`** — add `var attributes: Map<String, Double>` (ref to PlaybackCtx map)

4. **`audio_be/voices/VoiceScheduler.kt`** — handle `Cmd.SetAttribute`, update PlaybackCtx

5. **`audio_bridge/ExciterDsl.kt`** — add `ExciterDsl.Attribute(name: String)` node type

6. **`audio_be/exciter/ExciterDslRuntime.kt`** — `Attribute` → runtime exciter that reads `ctx.attributes[name]` per
   block

7. **`klangscript/KlangScriptOsc.kt`** — add `Osc.cps()` → `ExciterDsl.Attribute("cps")`, `Osc.attr(name)` → generic

8. **`klang/KlangPlayback.kt`** — add `updateAttributes(attrs: Map<String, Double>)` that sends `Cmd.SetAttribute`

9. **Sprudel integration** — `SprudelPlayback` calls `playback.updateAttributes(mapOf("cps" to cps))` on start and
   whenever CPS changes

### Usage in KlangScript

```javascript
let pad = Osc.register("syncPad",
    Osc.supersaw()
        .tremolo(Osc.cps(), 0.5)    // tremolo synced to playback speed
        .lowpass(2000)
        .adsr(0.01, 0.3, 0.5, 0.5)
)
note("c3 e3 g3").sound(pad)
```

### Properties

- Per-playback isolated (different playbacks can have different CPS)
- Live-updating (CPS changes propagate immediately, no recompile needed)
- General-purpose (not just CPS — any named value the frontend wants to push)
- `Osc.attr("name")` reads at audio rate (per block via scratch buffer)
