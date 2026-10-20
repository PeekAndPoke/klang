# KLANG-00097: Ignitor Variants — Dispatch on `soundIndex` (completed 2026-05-22)

> **Status (2026-05-22): DONE.**

Let a single user-defined ignitor expose multiple variants of "the same sound",
selectable per note via the existing `name:n` mini-notation and `.n(...)`
pattern. Same selection mechanism that picks sample variants in a sample bank
(`bd:0`, `bd:1`, …) — now extended to ignitors.

```klangscript
let combined = Osc.variants(Osc.sine(), Osc.saw())
note("a b c:1 d:1").sound(combined)   // a/b → sine, c:1/d:1 → saw
```

The `:n` data flow was already plumbed end-to-end for samples
(`SprudelVoiceData.soundIndex` → wire `VoiceData.soundIndex`) — the ignitor
side just never consumed it. This change closes that gap and also makes
`note("a:n")` parse the colon (it didn't before, only `s("bd:n")` did).

## What shipped

### Audio bridge

`audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt`:

- New sealed-interface node `IgnitorDsl.Variants(children: List<IgnitorDsl>)`
  with `@SerialName("variants")`. `collectParams` unions across all children
  so the param-slot UI sees every slot a variant could read.
- `maxReleaseSec()` gains a `Variants` branch — `children.maxOfOrNull { … } ?: 0.0`.
  Conservative: the voice may live longer than the picked variant needs, but
  silent tail is harmless. Avoids threading `soundIndex` into the scheduling-time
  walker.

### Audio backend — runtime dispatch

`audio_be/src/commonMain/kotlin/ignitor/IgnitorDslRuntime.kt`:

- `IgnitorBuildCache` now carries `val soundIndex: Int = 0`. The per-call cache
  was the natural home — `soundIndex` is invariant during one tree walk, so it
  rides on the cache rather than threading through every recursive call.
- `toExciter(oscParams, soundIndex = 0)` — new optional param.
- `buildIgnitor` dispatches `Variants` in the leaf-like prologue (no cache entry
  on the `Variants` node itself):

  ```kotlin
  if (this is IgnitorDsl.Variants) {
      require(children.isNotEmpty()) { "Osc.variants(...) must have at least one child" }
      val pick = cache.soundIndex.mod(children.size)
      return children[pick].buildIgnitor(oscParams, cache, accumulatedMod)
  }
  ```
  Kotlin stdlib `Int.mod(Int)` is the multiplatform floor-mod equivalent —
  negative indices wrap from the end, overflow wraps to zero.
- `buildRaw` gains an `error("Variants … must be absorbed in buildIgnitor")`
  guard so the exhaustive `when` stays exhaustive and any future regression
  fails loudly.

### Audio backend — registry wiring

`audio_be/src/commonMain/kotlin/ignitor/IgnitorRegistry.kt`:

- `createExciter` passes `data.soundIndex ?: 0` into `toExciter`. Missing `:n`
  on a note → variant 0 (intuitive default; matches single-variant case).

### KlangScript factory

`klangscript/src/commonMain/kotlin/stdlib/KlangScriptOsc.kt`:

- New `Osc.variants(vararg children: IgnitorDsl): IgnitorDsl` factory in a
  fresh "Dispatch / Selection" section after `constant`.

### Sprudel — `note("a:n")` parsing

`sprudel/src/commonMain/kotlin/lang/lang_tonal.kt`:

- `noteMutation` mirrors `soundMutation`: `name:index[:gain]` splits when `[1]`
  parses as an integer, otherwise the full string is preserved. The guard
  keeps backward-compat for non-numeric suffixes like `"C4:minor"`.
- `applyNote` empty-args branch clears `soundIndex` only when the source
  carried a `value` (strudel-style value→note re-interp). The old unconditional
  wipe was nuking the `c:2`-derived `soundIndex` before it reached the event.
  JsCompat for `note(run(4))` is preserved because those events still carry
  `value`, so the clear still fires there.

## Usage

```klangscript
// Pick variant 0 or 1 by note suffix
let combined = Osc.variants(Osc.sine(), Osc.saw())
note("a b c:1 d:1").sound(combined)

// .n("0 1") drives the index from a separate pattern
note("c d e f").n("0 1").sound(combined)

// Shared post-chain — filter wraps whichever variant got picked
let lead = Osc.variants(Osc.saw(), Osc.supersaw()).lowpass(100)

// Nested variants dispatch on the same soundIndex — single switching axis
let inner = Osc.variants(Osc.sine(), Osc.square())
let outer = Osc.variants(
    inner.lowpass(2000),
    inner.drive(0.4),
)
// soundIndex = 0 → sine through lowpass; soundIndex = 1 → square through drive
```

### Edge cases (by design)

| Case                    | Behaviour                                         |
|-------------------------|---------------------------------------------------|
| `note("a")` (no `:n`)   | `soundIndex = null` → variant 0                   |
| `c:99` with 3 children  | `99.mod(3) = 0` (wraps, same as sample banks)     |
| `c:-1` (via arithmetic) | `(-1).mod(2) = 1` (Kotlin floor-mod from the end) |
| `Osc.variants()` empty  | `require(...)` throws at build time               |
| Nested `Variants`       | All dispatch on the same `soundIndex` — feature   |
| Mixed-release variants  | Voice lifetime = max across children              |

## Verification

- `:audio_bridge:jvmTest` / `:audio_be:jvmTest` / `:klangscript:jvmTest` —
  green on JVM. `:audio_bridge:compileTestKotlinJs` /
  `:audio_be:compileTestKotlinJs` / `:klangscript:compileTestKotlinJs` — green.
- `:sprudel:jvmTest` — 4175 tests, all green.

New tests added:

- `audio_bridge/src/commonTest/kotlin/IgnitorDslSerializationTest.kt` — round-trip
  for `Variants` (primitives / composed children / nested), `collectParams`
  union, `maxReleaseSec` max-across-children + empty-list.
- `audio_be/src/commonTest/kotlin/ignitor/IgnitorDslRuntimeTest.kt` — dispatch
  for `soundIndex` 0 / 1, wrap on overflow (2), floor-mod on negative (−1),
  nested `Variants` correlated dispatch, empty-children throws.
- `audio_be/src/commonTest/kotlin/ignitor/IgnitorRegistryTest.kt` — end-to-end
  via `VoiceData{soundIndex}` for null / 0 / 1 / 2 / −1 vs reference outputs.
- `sprudel/src/commonTest/kotlin/lang/LangNoteSpec.kt` — `note("a b c:2 d:2")`
  → `soundIndex = [null, null, 2, 2]`; `note("a:1:0.5")` → `soundIndex=1, gain=0.5`.

Updated tests:

- `sprudel/src/commonTest/kotlin/lang/parser/MiniNotationParserSpec.kt` — three
  existing tests for `"bd:1"` / `"bd:1:0.5"` / `"bd:1:0.5*2"` updated to assert
  the new split behaviour (`note="bd"`, `soundIndex=1`, …). The
  `"C4:minor"` test still passes unchanged because `"minor"` isn't an `Int`.

## Out of scope (future)

- Per-variant probability weights (`Osc.variants(a -> 0.7, b -> 0.3)`).
- Independent dispatch axes (each `Variants` node reading a different attribute).
- Variant naming for UI (`Osc.variants("clean" to a, "dirty" to b)`) — current
  design uses positional indices to match sample-bank conventions.
- Crossfading between variants (continuous parameter rather than discrete pick).

If any of these become real needs, the existing `Variants` node can be
extended without breaking the discrete-index API.

## History

- 2026-10-20: numbered KLANG-00097 (was `docs/tasks-archive/2026-05/20260522-ignitor-variants.md`).
