# Feature Plan: Field Accessors — point-free read/route/transform of voice-data fields

## Goal

Let a sprudel pattern read, route, and transform its own voice-data fields **declaratively** — as
pattern combinators, never imperative per-event callbacks. A field name (`gain`, `lpf`, `freq`, …)
becomes a first-class **accessor** that is simultaneously:

- a **pattern** you can read and combine — `lpf(freq)`, `gain.add(perlin.mul(0.1))`
- its own **setter** — `gain(1.0)` (today's behaviour, via `invoke`)
- a **field identity** a generic op writes to — `set(gain, …)`, `clear(gain)`, `humanize(gain, …)`

### Motivating use cases

1. **Wind whistles a melody** — use the note's resolved frequency as a filter cutoff over noise:
   ```
   s("white").note("c e g a").lpf(freq).lpq(18)
   ```
2. **`humanize(field, lo, hi)`** — a thin helper, equivalent to:
   ```
   humanize(gain, 0.9, 1.1)   ≡   set(gain, gain.mul(perlin.range(0.9, 1.1)))
   ```
3. **Field arithmetic / routing through arbitrary nesting**:
   ```
   note("a b c d").gain(1.0).mul(gain, "0.8 0.6")     // gain field × an alternating pattern
   set(lpf, hpf.add(freq.div(10)))                    // nested field reads
   ```

### Non-negotiable: stay in pattern-land

Strudel/sprudel revolve around **patterns and cycles**. An earlier candidate —
`lpf(event => event.freqHz)` — was **rejected**: a per-event lambda re-introduces imperative
programming and is an idiomatic break (see *Rejected alternatives*). Every operand here is a
**pattern**; the field accessor is a pattern leaf bound late to the event stream.

---

## User-facing surface

| Form                                                    | Meaning                                                             |
|---------------------------------------------------------|---------------------------------------------------------------------|
| `gain`, `lpf`, `freq`, … (bare)                         | the accessor — a pattern reading that field from the bound event    |
| `gain(1.0)` / `gain("0.8 0.6")` / `gain(sine.range(…))` | set the field (today's setter, via `invoke`)                        |
| `myPattern.set(gain, x)`                                | generic setter; `x` may be a value, pattern, or accessor expression |
| `myPattern.mul(gain, x)` / `add` / `sub` / `div` …      | field-targeted arithmetic (reuses existing combinators)             |
| `myPattern.copy(freq, lpf)`                             | route one field's value into another (source kept)                  |
| `clear` (sentinel) — `gain(clear)` / `set(gain, clear)` | unset a field (`field := null`)                                     |
| `myPattern.humanize(gain, 0.9, 1.1)`                    | sugar: `set(gain, gain.mul(perlin.range(0.9, 1.1)))`                |

`move` = `copy` + `clear`; deferred (compose it yourself for now).

All accessor expressions are ordinary pattern trees built from the existing combinator library
(`add`, `mul`, `range`, `perlin`, `sine`, mini-notation strings, …). Nothing new in the math.

---

## Core mechanism: late binding via `QueryContext`

The accessor is **unbound** when constructed. It gets bound to the host pattern's event stream at
**query time**, through the `QueryContext` that is already threaded through every query
(`SprudelPattern.QueryContext`, `SprudelPattern.kt:102` — a typed, copy-on-write key/value map).

### 1. A context key for the bound event

```kotlin
// SprudelPattern.QueryContext.Companion
val boundVoiceKey = Key<SprudelVoiceData>("boundVoice")
```

### 2. The accessor is a context-reading pattern leaf

Like `time`/`sine` read from *t*, an accessor reads from `ctx.boundVoice` and emits the field into the
`value` register. **Unbound → silence.**

```kotlin
// conceptual: KlangProperty as a SprudelPattern leaf
override fun queryArcContextual(from, to, ctx): List<SprudelPatternEvent> {
    val voice = ctx.getOrNull(boundVoiceKey) ?: return emptyList()   // unbound → nothing
    val v = read(voice) ?: return emptyList()                        // field → SprudelVoiceValue
    return listOf(continuousEvent(from, to, value = v))             // continuous-style, value-only
}
```

### 3. Bind at control-pattern sampling — one localized change

Every setter already samples its control at each source event's onset
(`_applyControl`, `SprudelPattern.kt:1157` → `control.sampleAt(sampleTime, ctx)`). The entire feature
turns on binding the source event there:

```kotlin
val boundCtx = ctx.update { set(boundVoiceKey, event.data) }
val controlEvent = control.sampleAt(sampleTime, boundCtx)
```

Because `sampleAt` recurses with the same `ctx`, **every accessor leaf — at any nesting depth — sees
the same `boundVoice`.** That is what makes `set(lpf, hpf.add(freq.div(10)))` work: each accessor leaf
independently reads the one bound event. No structural substitution, no depth limit.

### Worked trace — `set(gain, gain.add(perlin.mul(0.1)))`

1. `set` outer-joins over the host. For each event it binds `boundVoice = event.data`, then
   `EXPR.sampleAt(onset, boundCtx)`.
2. `EXPR = add(gainLeaf, perlin.mul(0.1))`. `add`'s `_innerJoin` threads `boundCtx` to both sides.
3. `gainLeaf` reads `boundVoice.gain` (= 1.0); `perlin` samples at the onset → `p`; `add` → `1.0 + p*0.1`.
4. `set` reads the resulting `value` and `gain.write(event.data, …)`.

**Read-before-write is structural**: `_applyControl` samples the control *before* the combiner mutates
the event, so `gain(gain.add(0.1))` reads the old value then writes the new — no feedback loop.

---

## The value model — carrying non-scalar fields (`KlangValue`)

`SprudelVoiceValue` (the `value` register) is today `Num | Text | Bool | Seq | Pattern`
(`SprudelVoiceValue.kt:18`). Scalar fields (gain, pan, cutoff, note, …) round-trip through
`Num`/`Text`/`Bool` trivially. **Non-scalar** fields — `sound: SoundValue?`,
`pipeline: PipelineValue?`, and future ones — cannot: stringifying on read loses the structure and
can't be reconstructed on write.

### Shared marker interface

Introduce a marker in `audio_bridge` (the layer that owns these types):

```kotlin
// audio_bridge/.../KlangValue.kt
/** Marker for authoring-layer voice-field values the register can carry opaquely (round-trips losslessly). */
interface KlangValue

sealed interface SoundValue : KlangValue { /* Named, Osc */ }
sealed interface PipelineValue : KlangValue { /* Named, Dsl */ }
// future complex field types implement KlangValue → automatically carriable
```

### Register carries it via a new variant

```kotlin
// SprudelVoiceValue
data class Ref(val value: KlangValue) : SprudelVoiceValue {
    override val asBoolean get() = true
    override val asString get() = value.toString()   // display / lossy-string fallback only
    override val asDouble get() = null               // arithmetic on a Ref yields null (no-op)
    override val asInt get() = null
    override fun isTruthy() = true
}
```

- **Round-trip** is lossless: the actual `SoundValue`/`PipelineValue` object is carried.
- **Arithmetic** on a `Ref` returns `null` (the existing operators already null-guard on `asDouble`),
  so `mul(sound, 2)` is a harmless no-op rather than a crash.
- **Routing** between same-typed complex fields works; cross-type writes fail soft (see below).

### Typed read/write per accessor

Each accessor carries a typed read/write that boxes/unboxes through the register:

```kotlin
val gain = KlangProperty(
    name = "gain",
    read = { it.gain?.let(SprudelVoiceValue::Num) },
    write = { d, v -> d.gain = v?.asDouble },
)
val sound = KlangProperty(
    name = "sound",
    read = { it.sound?.let(SprudelVoiceValue::Ref) },
    write = { d, v -> d.sound = (v as? SprudelVoiceValue.Ref)?.value as? SoundValue ?: d.sound },
)
```

Type-mismatched writes (`set(gain, sound)`) coerce to `null`/no-op — never throw (project rule: coerce
user-facing values, don't `require`).

---

## `KlangProperty` — the three-role accessor type

One object, three facets:

```kotlin
class KlangProperty(
    val name: String,
    val read: (SprudelVoiceData) -> SprudelVoiceValue?,
    val write: (SprudelVoiceData, SprudelVoiceValue?) -> Unit,
    private val setterApply: (SprudelPattern, List<SprudelDslArg<Any?>>) -> SprudelPattern,
) : SprudelPattern {
    // role 1 — pattern leaf: query reads ctx.boundVoice via `read` (see mechanism above)
    // role 2 — setter:  invoke(amount) -> setterApply(...)   (native-object `invoke` operator)
    // role 3 — field id: read/write/name consumed by set/clear/copy/field-arithmetic
}
```

- **Role 1 (pattern)** lets `freq`/`gain` appear as control patterns: `lpf(freq)`, `gain.add(…)`.
- **Role 2 (setter)** preserves `gain(1.0)`: `invoke` routes to the *existing* `applyGain` — fully
  backward-compatible. Depends on the **`invoke` native-object operator** from
  `klangscript-native-object-operators.md` (Step 1–3, `invoke` only is enough).
- **Role 3 (field id)** is what `set(gain, …)`, `clear(gain)`, `copy(a, b)`, `mul(gain, …)` consume.

### KlangScript registration & the name-collision

Today `gain` is a `@KlangScript.Function`. It becomes a `@KlangScript.Constant val gain =
KlangProperty(...)` that is **callable via `invoke`**, subsuming the old function. The receiver-based
extension methods stay untouched:

- keep `SprudelPattern.gain(amount)`, `String.gain(amount)`, `PatternMapperFn.gain(amount)` (member
  resolution — `myPattern.gain(0.5)` is unaffected)
- replace only the **global factory** `fun gain(amount): PatternMapperFn` with the `KlangProperty`
  constant whose `invoke(amount)` returns the same `PatternMapperFn`

So `gain(0.5)` (now `invoke`) and `myPattern.gain(0.5)` (member) coexist, and bare `gain` resolves to
the accessor. This is per-field, enabling incremental migration.

---

## Files to change

1. **`klangscript/.../runtime/*`** — land the `invoke` native-object operator
   (`klangscript-native-object-operators.md`, Steps 1–3, `invoke` subset). *(prerequisite)*
2. **`audio_bridge/.../KlangValue.kt`** *(new)* — marker interface; `SoundValue`/`PipelineValue`
   implement it.
3. **`sprudel/.../SprudelVoiceValue.kt`** — add `Ref(KlangValue)` variant + `of()` handling.
4. **`sprudel/.../SprudelPattern.kt`** — `boundVoiceKey`; bind `boundVoice` in `_applyControl`
   (and confirm `_innerJoin`/`_outerJoin`/`sampleAt` thread `ctx` unchanged — they do).
5. **`sprudel/.../KlangProperty.kt`** *(new)* — the three-role accessor type (the context-reading leaf).
6. **`sprudel/.../lang/lang_field_accessors.kt`** *(new)* — `@KlangScript.Constant` accessor values
   for the v1 field set; `set`, `clear` sentinel, `copy`, field-arithmetic overloads, `humanize`.
7. **`sprudel/.../lang/lang_dynamics.kt` / `lang_filters.kt` / `lang_tonal.kt` …** — migrate the v1
   fields' global factory → `KlangProperty` (members untouched).
8. **Tests** — see below.

---

## Phasing

- **Phase 0 — KlangScript `invoke`.** Native-object `invoke` operator. Unblocks callable accessors.
- **Phase 1 — core + scalar fields.** `boundVoiceKey` + binding; `KlangProperty` leaf;
  `set`/`clear`/`copy` + field-arithmetic; `humanize`. Curated numeric/string field set:
  `gain, velocity, pan, postgain, freq, lpf/cutoff, hpf, bpf, lpq/resonance, attack, decay, sustain,
  release`. Ships the two motivating use cases.
- **Phase 2 — complex fields.** `KlangValue` + `Ref`; accessors for `sound`, `pipeline`.
- **Phase 3 — completeness.** Migrate remaining setters to `KlangProperty`; optionally fold into the
  full Symbol unification (`{get, set, invoke}`) from the operators plan's "Future Direction".

---

## Edge cases & guards

- **Unbound accessor → silence.** `gain.add(2)` used outside a setter/`set` context yields nothing
  (no `boundVoice`). Fail-soft, no throw.
- **Self-reference.** `gain(gain.add(0.1))` — read-before-write holds (control sampled before mutate).
  Guard with a spec.
- **Binding scope / leakage.** `boundVoice` is set only on the `ctx` handed to `sampleAt`; it must not
  leak into the host's own query. A nested setter inside a control expression re-binds its own event
  (innermost wins). Pin both with specs.
- **Type coercion.** Numeric ops on `Ref`/`Text`-non-numeric → `null` (no-op). Cross-type writes →
  no-op. Never throw on user input.
- **12-cycle rule.** Test accessor + control patterns across ≥12 cycles (timing bugs compound).

---

## Tests

- `LangFieldAccessorSpec` — `set`/`copy`/`clear`/field-arithmetic against known voice data.
- `lpf(freq)` wind-whistle: assert each event's `cutoff == freqHz` after `note(...)`.
- `humanize(gain, lo, hi)` ≡ `set(gain, gain.mul(perlin.range(lo, hi)))` (structural / value equality).
- Nested binding: `set(lpf, hpf.add(freq.div(10)))` resolves all three leaves per event.
- Self-reference read-before-write; unbound-accessor-is-silence; binding-no-leak.
- `Ref` round-trip: `copy(sound, sound)`-style identity; arithmetic-on-`Ref` is a no-op.
- Dual-language (Kotlin builder == KlangScript) for the accessor expressions.
- JVM + JS.

---

## Rejected alternatives

1. **Per-event callback** — `lpf(event => event.freqHz)`. Powerful but an **imperative idiomatic
   break**; invites arbitrary per-event logic against the pattern/cycle ethos. Also genuinely clashes
   at the type level: an arrow fn and a `PatternMapperFn` both erase to arity-1 `Function1`
   (`NativeInterop.kt:110`), indistinguishable in a setter's value-slot. Rejected.
2. **Accessor-method ops** — `gain.get()`/`gain.set()`. Rejected: `gain.set()` has no pattern
   receiver. Ops live on the pattern (`set(field, …)`), field as argument.
3. **Force-stringify complex values** — lossy; can't reconstruct `SoundValue`/`PipelineValue` on write.
   Replaced by `KlangValue` + `Ref`.

---

## Open decisions

1. **Field-arithmetic naming** — reuse `mul`/`add`/… with a field-first overload (`mul(gain, "0.8 0.6")`,
   recommended — disambiguated by the `KlangProperty` arg type), or distinct names.
2. **`copy` naming** — `copy` is overloaded (data-class `copy`); `route`/`link` read clearer. Recommend
   confirming before build.
3. **Accessor canonical names** — DSL names (`freq`, `lpf`, `cutoff`) canonical, storage names
   (`freqHz`) as aliases (recommended).
4. **`KlangValue` strictness** — `Ref(KlangValue)` (typed, recommended) vs `Ref(Any)` (looser).
5. **v1 field set** — confirm the Phase-1 list above.
