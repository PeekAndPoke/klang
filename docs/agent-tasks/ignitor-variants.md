# Ignitor Variants — Dispatch on `soundIndex`

> **Status (2026-05-22)**: PLANNED — not yet implemented.
> Owner: user-driven; this doc is the design checkpoint before coding.

## Goal

Let a single user-defined ignitor expose multiple variants of "the same sound",
selectable per note via the existing `name:n` mini-notation and `.n(...)`
pattern. Example target syntax:

```klangscript
let variant1 = Osc.sine()
let variant2 = Osc.saw()
let combined = Osc.variants(variant1, variant2)

note("a b c:2 d:2").sound(combined)
```

`a` and `b` → variant 0 (`Osc.sine()`).
`c:2` and `d:2` → wrap into variant `2.mod(2) = 0` → also sine.
With three variants `c:2` selects variant index 2. With one variant everything
collapses to variant 0. Same model as sample banks (`bd:0`, `bd:1`, ...).

## Existing infrastructure (no changes needed)

The `:n` data flow is already plumbed end-to-end for samples; we just need to
make ignitors consume it.

| Layer       | Field                                                                               | Source                                                                                  |
|-------------|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| sprudel     | `SprudelVoiceData.soundIndex: Int?`                                                 | parsed from `name:n` split, `.n(...)`, value reinterpretation (`sprudel/lang_tonal.kt`) |
| bridge wire | `VoiceData.soundIndex: Int?`                                                        | copied across the comm-link (`audio_bridge/VoiceData.kt:28`)                            |
| BE          | `IgnitorRegistry.createExciter(name, data, freqHz)` ignores `data.soundIndex` today | `audio_be/.../IgnitorRegistry.kt:41`                                                    |

The samples path consumes `soundIndex` via `SampleRequest.index`
(`audio_bridge/VoiceData.kt:223`); the ignitor path is the gap we close.

## Design

### A new DSL node: `IgnitorDsl.Variants`

A pure dispatch/selection node — does not itself produce audio, descends into
one child based on `soundIndex` at voice-activation time.

```kotlin
@Serializable
@SerialName("variants")
data class Variants(val children: List<IgnitorDsl>) : IgnitorDsl {
    override fun collectParams(out: MutableList<Param>) {
        children.forEach { it.collectParams(out) }
    }
}
```

Param collection unions across all variants so the param-slot UI shows every
slot a variant could read.

### Carrying `soundIndex` through the runtime walker

`soundIndex` is invariant during a single `toExciter` call, so it rides on the
per-call `IgnitorBuildCache` rather than threading through every recursive
call:

```kotlin
internal class IgnitorBuildCache(val soundIndex: Int = 0) { ... }

fun IgnitorDsl.toExciter(
    oscParams: Map<String, Double>? = null,
    soundIndex: Int = 0,
): Ignitor = buildIgnitor(oscParams, IgnitorBuildCache(soundIndex))
```

In `buildIgnitor`, Variants is handled in the leaf-like dispatch block (no
cache entry on the Variants node itself):

```kotlin
if (this is IgnitorDsl.Variants) {
    require(children.isNotEmpty()) { "Osc.variants(...) must have at least one child" }
    val pick = cache.soundIndex.mod(children.size)  // floorMod via Kotlin stdlib
    return children[pick].buildIgnitor(oscParams, cache, accumulatedMod)
}
```

`Int.mod(Int)` (Kotlin stdlib ≥ 1.5) is the multiplatform equivalent of
`Math.floorMod` — returns a result with the sign of the divisor, so always
non-negative here.

### Voice-lifetime correctness — `maxReleaseSec`

`IgnitorDsl.maxReleaseSec()` walks the tree at scheduling time (before
`toExciter` runs) to extend the amp ADSR over ignitor-internal Adsr nodes
(`audio_bridge/IgnitorDsl.kt:1356`, used at
`audio_be/voices/VoiceFactory.kt:207`).

Conservative resolution for variants: **take the max across all children**.
The voice may live longer than the picked variant needs, but extra silent
tail is harmless. The alternative — pick the right child first using
`soundIndex` — would require passing `soundIndex` into `maxReleaseSec` too,
which is doable but couples extra surface for negligible gain.

```kotlin
is IgnitorDsl.Variants -> children.maxOfOrNull { it.maxReleaseSec() } ?: 0.0
```

### Frontend identity / registration

The FE `IgnitorRegistry` (`klang/.../IgnitorRegistry.kt`) keys announcements
on `IgnitorDsl` equality. `Variants` is a plain data class — equals/hashCode
generated, `uniqueId()` works without changes.

### Nesting is a feature, not a bug

Variants anywhere in the tree all dispatch on the same `soundIndex` (it's
read from the cache, not threaded). User-visible behaviour:

```klangscript
let inner = Osc.variants(Osc.sine(), Osc.square())
let outer = Osc.variants(
    inner.lowpass(2000),
    inner.distort(0.4)
)
```

For `soundIndex = 0`: outer picks branch 0 (lowpass), inner inside it picks
branch 0 (sine) → "sine through lowpass". For `soundIndex = 1`: outer picks
branch 1 (distort), inner inside picks branch 1 (square) → "square through
distort". This is intentional — the index is a single switching axis the
user composes against.

## Step-by-step changes

| Step | What                                                                                 | Files                                                              |
|------|--------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| 1    | Add `IgnitorDsl.Variants(children: List<IgnitorDsl>)` sealed-interface variant       | `audio_bridge/.../IgnitorDsl.kt`                                   |
| 2    | Extend `maxReleaseSec()` with a `Variants` branch (max across children)              | `audio_bridge/.../IgnitorDsl.kt:1356`                              |
| 3    | Add `soundIndex` to `IgnitorBuildCache`; add `soundIndex` param to `toExciter`       | `audio_be/.../ignitor/IgnitorDslRuntime.kt:28,39`                  |
| 4    | Dispatch branch for `Variants` in `buildIgnitor` (alongside leaves)                  | `audio_be/.../ignitor/IgnitorDslRuntime.kt:65`                     |
| 5    | Forbid `Variants` from `buildRaw` (it should never reach there) — `error(...)` guard | `audio_be/.../ignitor/IgnitorDslRuntime.kt:148`                    |
| 6    | Pass `data.soundIndex ?: 0` at the registry boundary                                 | `audio_be/.../ignitor/IgnitorRegistry.kt:41`                       |
| 7    | Add `Osc.variants(vararg children: IgnitorDsl): IgnitorDsl` factory                  | `klangscript/.../stdlib/KlangScriptOsc.kt`                         |
| 8    | Tests (see below)                                                                    | new files under `audio_be/src/commonTest` + `audio_bridge/...Test` |

## Tests

### Unit — DSL layer (audio_bridge)

- `IgnitorDslSerializationTest` — `Variants(listOf(Sine(), Sawtooth()))` round-trips through kotlinx-serialization JSON.
- `IgnitorDslMaxReleaseSecTest` — variants with mixed Adsr children returns the max release of any child.
- `getParamSlots` — Variants exposes the union of all child param slots.

### Runtime — buildIgnitor (audio_be)

- `Variants(a, b)` with `soundIndex = 0` produces an Ignitor whose output matches building `a` alone.
- Same DSL with `soundIndex = 1` matches building `b` alone.
- `soundIndex = 2` with 2 children → wraps to 0.
- `soundIndex = -1` with 2 children → wraps to 1 (floor-mod semantics).
- Nested variants: outer picks branch i, inner-of-branch-i picks branch i.
- Empty children list → clear failure (`require(...)` at build time).

### Integration — IgnitorRegistry (audio_be)

- Register a Variants DSL, call `createExciter(name, VoiceData{soundIndex=N}, freqHz)` for N = 0, 1, 2, -1 and verify
  the produced Ignitor's first-block output matches the expected variant's reference output.

### End-to-end — klangscript

- Parse + evaluate `let v = Osc.variants(Osc.sine(), Osc.saw()); note("a a:1").sound(v)` and assert each event has
  `sound = Osc(Variants(...))` with `soundIndex` ∈ {null, 1} as expected.

## Edge cases — decisions

| Case                     | Decision                                       | Rationale                                                           |
|--------------------------|------------------------------------------------|---------------------------------------------------------------------|
| `note("a")` with no `:n` | `soundIndex = null` → `?: 0` → variant 0       | Intuitive default; matches single-variant case                      |
| `c:99` with 3 children   | `99.mod(3) = 0` (wrap)                         | Same as sample banks; least-surprise consistency                    |
| `c:-1` (via arithmetic)  | `(-1).mod(3) = 2` (Kotlin floor-mod)           | Negative indices wrap from the end                                  |
| `Osc.variants()` empty   | `require(children.isNotEmpty())` at build time | Always-throws path, user typo not silent fallback                   |
| Nested variants          | All dispatch on the same `soundIndex`          | Feature: lets users compose multi-axis variant graphs from one knob |
| Variant ADSR mismatch    | Voice lifetime = max across all children       | Conservative; silent tail safer than clipped tail                   |

## Out of scope (future)

- Per-variant probability weights (`Osc.variants(a -> 0.7, b -> 0.3)`).
- Independent dispatch axes (each Variants node reading a different attribute).
- Variant naming for UI (`Osc.variants("clean" to a, "dirty" to b)`) — current
  design uses positional indices to match sample-bank conventions.
- Crossfading between variants (continuous parameter rather than discrete pick).

If any of these become real needs, the existing `Variants` node can be
extended without breaking the discrete-index API.
