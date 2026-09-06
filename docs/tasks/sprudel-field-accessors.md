# Sprudel field accessors: a field name that reads, sets, and takes a mapper

Rewritten 2026-09-06 after a design session; the previous draft (context-key binding, generic
`set`/`copy`/`clear` ops, a `KlangValue`/`Ref` register variant) is superseded and not binding.

## Status

Pilot on ONE field, `freq`. Nothing else until the pilot is green, reviewed and heard.

## Goal

A sprudel pattern can read its own voice-data fields and feed them back into other fields,
declaratively, in pattern-land. A field name is one object with three roles:

| Form                                   | Meaning                                                          |
|----------------------------------------|------------------------------------------------------------------|
| `freq(440)`, `freq("440 880")`         | today's setter, unchanged                                        |
| `freq(mul(perlin.seg(4).range(0.95, 1.05)))`  | a MAPPER argument: apply this mapper to the field                |
| `bpf(freq)`                          | bare `freq` is the mapper "read freq into the value register"    |
| `bpf(freq.mul(2))`                   | the accessor composes with the existing chained mapper forms     |

Two doors, one text: every line above is valid KlangScript and valid Kotlin, character for
character, and produces the same events.

### Motivating use cases

1. **The wind whistles a melody.** Pink noise through a bandpass whose cutoff follows the note:
   ```
   note("c e g a").bpf(freq).sound("pink").bpq(2.0)
   ```
   Built-in song: Greensleeves (traditional, public domain). "Blowing in the Wind" was the
   first idea and is under copyright, so it stays a local experiment.
2. **A novice violin player struggling with intonation.** The pitch wobbles a few percent around
   the note:
   ```
   note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05)))
   ```
   This is the pilot's acceptance example.
3. **Field arithmetic.** `freq(add(50))` (shift by 50 Hz), `bpf(freq.mul(2))` (bandpass one
   octave above the note), `freq(freq.add(50))` (same as `freq(add(50))`).

### Non-negotiable: stay in pattern-land

No per-event lambdas on the script surface (`lpf(event => event.freqHz)` stays rejected). Every
operand is a pattern or a pattern mapper; the accessor is sugar over machinery that already exists,
and voice-data internals stay hidden.

## The design

### D1. A mapper argument to a setter means "apply this mapper to my field"

Today a `PatternMapperFn` handed to a setter is silently dropped: `toListOfPatterns` returns
`null` for a function value, the setter still joins against the resulting empty control, and the
field is CLEARED. Verified 2026-09-06 with a scratch spec:

| Expression                                  | Result today          |
|---------------------------------------------|-----------------------|
| `note("c e").freq(add(50))`                 | `freqHz == null`      |
| `note("c e").gain(0.8).gain(mul(0.5))`      | `gain == null`        |
| `note("[a,c,e]").apply(gain(0.5))`          | all three 0.5 (fine)  |

So the slot is free and its current meaning is a bug. New meaning, in `_liftNumericField`, when
the single argument is a mapper:

```kotlin
// SprudelPattern._mapNumericField(mapper, read, update), called from applyFreq / applyBpf
return this.reinterpretVoice { it.copy(value = read(it)?.asVoiceValue()) }        // field -> value
    .let(mapper)                                                                   // the user's mapper
    .reinterpretVoice { it.update(it.value?.asDouble).copy(value = null) }         // value -> field
```

`reinterpretVoice` hands the source event's data to the lambda uncloned, so both passes `copy`.

Properties:

- **One chain, no join.** There is no pairing of control events to source events, so chords and
  stacks are correct by construction (`note("[a,c,e]").freq(mul(...))` wobbles each note on its
  own). No query context, no per-event allocation beyond two reinterpret passes.
- **The value register is the read channel.** Patterns were write-only; `note()` and `sound()`
  consume the value register and leave it `null`. The read step refills it, the write step drains
  it. Clearing after the write restores the state the chain was in.
- **Structure is whatever the mapper makes.** `freq(fast(2))` is legal and means what it says.
- **The mapper applies to the FIELD, not to the value register.** `"440 880".freq(mul(2))` reads
  `freqHz` (unset) and yields nothing; the reinterpret form is `"440 880".freq()`.

### D2. The accessor is a provider of the read mapper, not a pattern and not a function

```kotlin
/** Hands out a pattern mapper. Field accessors implement it. */
fun interface PatternMapperProvider {
    fun mapper(): PatternMapperFn
}

object Freq : PatternMapperProvider {
    override fun mapper(): PatternMapperFn = { p -> p.reinterpretVoice { it.copy(value = it.freqHz?.asVoiceValue()) } }
}
```

Why not `Freq : PatternMapperFn` (a `Function1`): Kotlin would then have the member
`invoke(SprudelPattern)` next to the setter extension `invoke(hz)`, and `Freq(pattern)` would
silently pick the member and mean "read" while `freq(pattern)` means "set". The provider keeps
one meaning per spelling in every door. Bonus: `PatternMapperProvider` is not a `kotlin.*` type,
so the KSP supertype emission (which drops `kotlin.*` ancestors) carries it into the editor's
type of `freq`. Object symbols needed that emission added (D3); a `kotlin.Function1` supertype
would have needed the drop rule changed as well.

Why not a pattern leaf bound through the `QueryContext`: it needs a per-event context copy in
`_applyControl` (a cost paid by every setter in every query), and any join inside the control
expression rebinds the event for its own control side, so `"1 2".mul(freq)` or
`sine.range(200, freq)` read the wrong event. Rejected on both counts.

Why not a provider whose pattern is re-joined by time: `sampleAt(onset)` returns the first event
at a tick, so every chord note reads the first note's field. Rejected.

Why not a per-event `VoiceReader` with its own arithmetic: correct, but a second operator algebra.
The provider needs only first-step twins (D4) and then lives in the existing mapper library.

### D3. The setter role goes through the KlangScript `invoke` operator

```kotlin
@KlangScript.Library("sprudel")
@KlangScript.Object("freq")
object Freq : PatternMapperProvider {
    override fun mapper(): PatternMapperFn = ...
    @KlangScript.Method(name = "invoke")
    operator fun invoke(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = freq(hz, callInfo)
}
val freq: Freq = Freq   // Kotlin door, unannotated
```

- `@KlangScript.Object("freq")` rather than a `@Constant`: the analyzer renders an `invoke`
  signature with the receiver type's display name, and only the object route displays as `freq`
  (a constant of type `Freq` rendered `Freq(hz: ...)`). Object symbols carried no supertypes in
  the analyzer before; the KSP processor now emits them (one line, guarded by the intel spec).
- The existing top-level `fun freq(hz, callInfo)` keeps its body and loses only its
  `@KlangScript.Function` annotation (an `@Object` and a `@Function` of the same top-level name
  collide in KSP). In Kotlin a call `freq(440)` still resolves to the function, `Freq(440)` to the
  member; both set.
- `SprudelPattern.freq(...)` and `String.freq(...)` are untouched.
- Runtime dispatch (verified in `Interpreter.evaluateCall`): a `NativeObjectValue` callee is only
  ever dispatched to the registered `invoke` extension, through the same spec-aware path as a
  member call, so named args, defaults and errors (`freq.invoke`) behave like `Master(...)`.
- Editor: `resolveCallable` falls back to the `invoke` member of the identifier's type, so
  `freq(` gets a signature; `freq.` offers what is registered on `PatternMapperProvider`.

### D4. First-step operators on the provider

The chained mapper forms (`PatternMapperFn.mul`, `.add`, ...) are registered on `Function1`. A
provider is not one, so the first step of a chain needs a twin that unwraps:

```kotlin
@KlangScript.Function
fun PatternMapperProvider.mul(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = mapper().mul(factor, callInfo)
```

After one step the value is a `PatternMapperFn` and the whole library composes. Pilot set:
`add`, `sub`, `mul`, `div`. Others on demand, one line each.

### D5. What is deliberately not a thing

- A provider is not a pattern: `seq(freq, 440)`, `stack(freq)` are not offered.
- `PatternMapperFn`-typed parameters (`apply`, `superimpose`, `firstOf`) do not accept a provider.
  "value := freq" as a bare transform has no use case; if one appears it is the same unwrap.
- No generic `set`/`copy`/`clear`/`humanize`: `set(gain, x)` is `gain(x)`, `copy(freq, bandf)` is
  `bpf(freq)`, `mul(gain, x)` is `gain(mul(x))`. One word per concept. `clear` and `humanize`
  wait for a song that needs them.
- No `KlangValue`/`Ref` register variant: routing `sound` or `pipeline` between fields has no
  use case.

### D6. Order matters, and that is documented, not engineered around

An accessor reads what the chain has set so far. `note("c e").bpf(freq)` works;
`bpf(freq).note("c e")` writes nothing into `bpf` because `freqHz` is unset when it reads.
Same rule as every outer join. One sentence in the KDoc of the accessor.

## Pilot: `freq`

### Files

| File                                            | Change                                                                                          |
|-------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `sprudel/.../lang/lang.kt`                      | `PatternMapperProvider`                                                                         |
| `sprudel/.../SprudelPattern.kt`                 | `_mapNumericField(mapper, read, update)`: the read, map, write chain                            |
| `sprudel/.../lang/lang_helpers.kt`              | `singleMapperOrNull()`: provider or mapper argument detection, guarded by `patternMapper`      |
| `sprudel/.../lang/lang_filters.kt`              | `applyBpf` takes the mapper branch too, so a field can be read into another (`bpf(freq)`)      |
| `sprudel/.../lang/lang_tonal.kt`                | `object Freq`, `val freq`, `Freq.invoke`; annotation moved off the top-level `fun freq`         |
| `sprudel/.../lang/lang_arithmetic.kt`           | `PatternMapperProvider.add/sub/mul/div` first-step twins                                         |
| specs (below)                                   |                                                                                                 |
| `sprudel/MEMORY.md`, `sprudel/ref/dsl-conventions.md` | the mapper-argument rule and the accessor shape                                            |

### Specs (sprudel timing core: mandatory mutation tier)

1. Violin: `note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05)))` over 12 cycles, every event's
   `freqHz` within 5 percent of its note's frequency and not all equal.
2. Chord: `note("[a,c,e]").freq(mul(2))` doubles each note on its own.
3. Regression guard for the silent clear: `note("c e").freq(add(50))` adds 50 Hz.
4. Read into another field: `note("c e g a").bpf(freq)` gives `bandf == freqHz` per event.
5. Provider first step: `note("c e").bpf(freq.mul(2))`.
6. Self reference: `freq(freq.add(50))` equals `freq(add(50))`.
7. Order: `bpf(freq).note("c e")` leaves `bpf` unset.
8. Value register drained: after `freq(mul(2))`, `value == null`.
9. Setter unchanged: `freq(440)`, `freq("440 880")`, `"440 880".freq()` as before (existing specs).
10. Door parity: the script text and the Kotlin text of 1, 4 and 5 produce equal events.
11. Intel: `freq(` shows a signature, `freq.` offers `mul`, hover on `freq` shows the accessor doc.

### Acceptance by ear

The violin line in the editor (heard 2026-09-06, works), then Greensleeves whistled by the wind
(`src/commonMain/kotlin/builtinsongs/Greensleeves.kt`).

## Phase 2 (after the pilot)

- More fields, one object each: `gain, velocity, pan, lpf, hpf, bpf` (with their aliases as
  `val cutoff = lpf`-style constants), `lpq, hpq, bpq, attack, decay, sustain, release`. Compound
  setters (`lpf(freq, q, passes)`) keep their signature on `invoke`; only the first parameter takes
  a mapper.
- The same chain in `_liftStringField` and `_applyControlFromParams` for string and control fields.
- Provider twins on demand.
- A provider or mapper handed to a setter WITHOUT the mapper branch (`lpf(freq)` today) is still
  silently dropped and the field cleared or kept, with no diagnostic; the analyzer cannot flag it
  (`PatternLike` is `Any`). Either every setter gets the branch, or `toListOfPatterns` reports it.
- Settle what a null write means. `freqUpdate` clears the field on `null`; a `voiceSetter` such as
  `bpfMutation` ignores `null` and keeps the old value. The control path has had this asymmetry
  all along; the mapper path inherits it (`stack(note("c e"), s("hh*4")).bpf(800).bpf(freq)`
  keeps 800 on the hats). Decide once before more fields get the mapper branch.
- `note` as a string field: decide whether an accessor makes sense.

## Review checklist mapping (`/dsl-design`)

1. Immutable: `Freq` is a stateless object; every operation returns a new mapper or pattern.
2. Door shape: not a builder door; construction input (`hz`) on `invoke`, no `configure`.
3. Both doors, same text, door-parity spec.
4. Parity: the accessor reads exactly the field its setter writes (`freq` and `freqHz`).
5. One word per concept: `freq` is the setter, the accessor and the mapper target; the replaced
   top-level function annotation is removed, not deprecated.
6. Coerce: a non-numeric value writes `null`; a mapper that yields nothing writes `null`; never throws.
7. No wire change.
8. No frontend DSP, no cycles over the wire.
9. KDoc on `freq` (constant) and `Freq.invoke` feeds the editor popup.
