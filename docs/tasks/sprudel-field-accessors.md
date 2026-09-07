# Sprudel field accessors: a field name that reads, sets, and takes a mapper

Rewritten 2026-09-06 after a design session; the previous draft (context-key binding, generic
`set`/`copy`/`clear` ops, a `KlangValue`/`Ref` register variant) is superseded and not binding.

## Status

- 2026-09-06: pilot on `freq` green, reviewed (two rounds), heard. Greensleeves built on it.
- 2026-09-07: batch one, fourteen accessors: `gain, velocity, pan, postgain, lpf, hpf, bpf, lpq,
  hpq, bpq, attack, decay, sustain, release`, on the `FieldAccessor` base.
- 2026-09-07: batch two, the effects file: 24 accessors (`distort, distos, crush, crushos, coarse,
  coarseos, roomWet, roomsize, roomfade, roomlp, roomdim, delayWet, delaytime, delayfeedback,
  phaser, phaserWet, phaserFloor, phasercenter, phasersweep, tremolosync, tremolodepth,
  tremoloskew, tremolophase, delaycap`) and their 20 aliases as constants of the canonical object
  (`val rsize: RoomSize = RoomSize`), so an alias sets and reads exactly like the original and
  the editor shows it as the canonical type. Found and fixed on the way: `crushos` and `coarseos`
  parsed their value with `toIntOrNull()` on a `Double`'s string and never wrote the field from a
  numeric argument. Because those calls were inert in shipped songs, the affected calls
  (ATruthWorthLyingFor, StrangerThings, IrishLamentTechno, Sakura, and the frozen benchmark
  snapshots in `src/jvmMain/kotlin/FrozenSongs.kt`) are pinned to `1` with a dated
  comment so the tuned sound is unchanged; MAINTAINER DECISION per song whether to raise them.
  The batch-one aliases `vel`, `lowpass`, `highpass`, `bandpass` became constants too.
  Benchmark note: the song benchmark rung `3 +coarse(2,os4)` (`SongBenchmarkCases.kt`) measured
  coarse WITHOUT oversampling before 2026-09-07 despite its name; tables from before that date
  (`docs/benchmarks/2026-08-19_*`) are not comparable on that rung.
- 2026-09-07: batch three, 36 accessors and 22 aliases across sample (`begin, end, speed,
  loopBegin, loopEnd, cut`), synthesis (`fmh, fmattack, fmdecay, fmsustain`), vowel (`vowelWet,
  vowelFloor`), body (`bodyWet, bodyFloor`), tonal (`legato, vibrato, vibratoMod, pattack, pdecay,
  prelease, penv, pcurve, panchor, accelerate`), the notch addons (`notchf, nresonance, nfattack,
  nfdecay, nfsustain, nfrelease, nfenv`) and the filter envelopes (`lpe, lpx, hpe, hpx, bpe`).
  Tonal helpers had inline update lambdas; each became a named `<name>Update` value shared by the
  mapper branch and the lift. Remaining numeric setters: dynamics leftovers (`unison, spread,
  panSpread, density, duckAttack, duckDepth`, the compressor knobs), `fmenv` (its factory returns a
  pattern, not a mapper), `orbit` and `duckOrbit` (Int routing fields). String and boolean setters
  (`note, n, sound, bank, scale, vowel, body, unit, loop, *shape, *curve`) are out of scope: the
  value register carries text, but "apply a mapper to a name" has no use case yet.
- 2026-09-07: batch E, the compound effects, after the `adsr` pilot: `room(wet, size, fade,
  lowpass, dim)`, `delay(wet, time, feedback, cap)`, `phaser(rate, wet, center, sweep, floor)`,
  `tremolo(depth, sync, shape, skew, phase)`, `distort(amount, shape, oversample)`,
  `crush(amount, oversample)`, `coarse(amount, oversample)`; each an object with one child per
  numeric slot and the setter as `invoke`. The 24 batch-two accessors and their 20 aliases are
  gone with the per-knob doors (guard `LangRetiredDoorsSpec`). The corpus (12 builtin songs, the
  frozen benchmark snapshots, two tutorials, the golden corpus, docs and skills) was migrated by
  script (`scratchpad/migrate_e.py`, not kept): adjacent calls on one object merged into one
  named call. Collateral the script hit and that was reverted: the array method `.size()` in two
  strategy docs and `Files.size` in `SampleMirrorMain.kt`. Found on the way: the tutorial lint
  reads call names, so `teaches`/`previews` name the object now; duplicate Kotest titles appear
  when two alias tests collapse onto the canonical call; a slot name that is also a top-level
  symbol (`lowpass`) gives the docs symbol two property variants.
- 2026-09-07: batch four, the last numeric group: `unison, spread, panSpread, density` (unison
  oscillator params, read from `oscParams`), `orbit, duckorbit` (Int routing fields), `duckattack,
  duckdepth`, `compressor` (its first slot, the threshold; the other knobs have no single-field
  door) and `fmenv` (whose global door builds a control pattern, so its `invoke` returns a
  pattern), plus `analog, duty, onepole` from the oscillator addons. Aliases `uni, voices, d, o, duck,
  duckatt, comp, fmmod`. Every numeric single-field setter in sprudel is now an accessor; only
  string and boolean setters remain outside. Engine gaps found here: `panSpread` has no engine
  stage (documented reserved); `density` is the dust grain rate, not a unison knob;
  `duckattack` is the recovery time (the duck-down is instant).

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
    @KlangScript.Invoke
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

- DONE (batch one, 2026-09-07): `gain, velocity, pan, postgain, lpf, hpf, bpf, lpq, hpq, bpq,
  attack, decay, sustain, release`, each `object X : FieldAccessor({ it.field })` with the setter
  as `invoke`; compound setters (`lpf(freq, q, passes)`) keep their signature on `invoke`, only
  the first parameter takes a mapper. Specs: `LangFieldAccessorsSpec` (one mapped row and one
  read row per accessor, both doors, 12 cycles), `FreqAccessorIntelSpec` (all objects).
- DONE (batch two, 2026-09-07): the effects file, 24 accessors and 20 alias constants; alias
  rule: `@KlangScript.Constant val <alias>: <Obj> = <Obj>`, the alias factory stays for Kotlin.
- DONE (batch three, 2026-09-07): sample, synthesis, vowel, body, tonal, notch addons, filter
  envelopes: 36 accessors, 22 aliases.
- DONE (batch four, 2026-09-07): dynamics leftovers, routing fields, compressor threshold, fmenv.
  Phase 2 numeric sweep complete: 88 accessors, 54 alias constants.
- WON'T IMPLEMENT (maintainer, 2026-09-07): string and boolean setters (`note, n, sound, bank,
  scale, vowel, body, unit, loop, *shape, *curve`). "Apply a mapper to a name" has no use case,
  and the maintainer expects it never will.
- 2026-09-07, compound pilot on `adsr` (maintainer decision): `adsr` is an object whose children
  `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release` are the slot accessors and whose
  call form is the setter; a mapper on a named slot applies to that slot only. The single doors
  `attack()`, `decay()`, `sustain()`, `release()` were REMOVED from both doors (one word per
  concept; guard `LangRetiredEnvelopeDoorsSpec`, renamed `LangRetiredDoorsSpec` in batch E). Shape: `@KlangScript.Object("adsr") object Adsr`
  with `@KlangScript.Property val attack: FieldAccessor = FieldAccessor { it.attack }`, and the
  stage helpers private. The same shape is the plan for `lpadsr`, `hpadsr`, `bpadsr` and the
  compressor, see `docs/tasks/sprudel-accessors-compound-slots.md`.
- 2026-09-07: the unannotated Kotlin factories (`fun gain(...)`, `fun rsize(...)`, 139 of them)
  were removed; the object's `invoke` is the Kotlin door through the invoke convention, so each
  name has exactly one Kotlin form. Then the objects took the script name itself (`object gain`,
  `object adsr`) and the 85 `val` twins went too: one declaration per concept, the Kotlin naming
  convention suppressed at file level.
- DONE (batch E, 2026-09-07): the seven compound effects as objects with slot children; per-knob
  doors and aliases removed. Next: batch F, the filters (`lpf/hpf(freq, q, passes, env, attack,
  decay, sustain, release)`, `bpf`/`notch` without passes; retiring `lpq/lpx/lpe/lpadsr`, the
  `hp*`, `bp*`, `nf*` families, `nresonance`, `notchq`), then batch G (`compressor`, `vibrato`,
  `penv`, `fm`, `duck`, `vowel`, `body`, `unison`). The `snd*` sound doors are a separate
  discussion (idea: `object Snd { object supersaw { fields } }`).
- OPEN (maintainer, 2026-09-07): `adsrCurve` (the singular, one curve name for all stages) is to
  be REMOVED; `adsrCurves(attack, decay, release)` becomes an object with fields like the other
  compounds. Same for the filter envelope curve siblings, which should be called `lpCurves`,
  `hpCurves`, `bpCurves` rather than `lpadsrCurves`. Note: no `lp/hp/bpadsrCurves` door exists in
  sprudel today (only `adsrCurves` and `adsrCurve` in `lang_dynamics.kt`); the ignitor side is to
  be checked. Open question for the shape: the slots are curve NAMES (strings), and string
  accessors are won't-implement, so either the object gets children that read the name anyway
  (a first string reader) or it carries the setter only. Batch F item. Sakura uses
  `adsrCurve("scurve")` once and moves to `adsrCurves("scurve", "scurve", "scurve")` or a
  shorter form to be decided.
- OPEN: the remaining compound doors (above); a diagnostic when a mapper reaches a setter without
  the branch (today the value is dropped), and for a compound object used as a value (`pan(adsr)`
  is accepted by `PatternLike` and writes nothing useful, since `Adsr` is not a `FieldAccessor`);
  the engine gaps listed above.
- Engine gaps the examples exposed (2026-09-07, batch three review), documented as "reserved" in
  the object KDocs: `pcurve` is not read by `PitchEnvelopeRenderer`; `loopBegin`/`loopEnd` are not
  read by `VoiceFactory` (it loops between `begin` and `end`); negative `speed` is silence, not
  reverse (the sample docs claimed reverse). `nfenv` is in semitones; the addon file's examples
  said `nfenv(3000)`.
- The same chain in `_liftStringField` and `_applyControlFromParams` for string and control fields.
- Provider twins on demand.
- A provider or mapper handed to a setter WITHOUT the mapper branch (`room(freq)` today) is still
  silently dropped and the field cleared or kept, with no diagnostic; the analyzer cannot flag it
  (`PatternLike` is `Any`). Either every setter gets the branch, or `toListOfPatterns` reports it.
- Null write: SETTLED 2026-09-07. On the mapper path a `null` result leaves the field unchanged
  for every setter (`_mapNumericField` skips the update). The control path keeps its historic
  per-setter behaviour, not touched: most mutations are `field = it?.asDoubleOrNull()` and CLEAR
  on `null` (gain, pan, velocity, postgain, the envelope fields, the three Q fields, `freqUpdate`);
  the three cutoff mutations (`lpfMutation`, `hpfMutation`, `bpfMutation`) early-return and KEEP.
- `note` as a string field: decide whether an accessor makes sense.

## Teaching (owed, recorded 2026-09-07)

The surface shipped before any lesson about it, which is the wrong way round for a headline
feature: nothing in the tutorial ladder mentions that a knob can read a field or take a mapper.
Recorded as slot **A9** in `docs/tasks/tutorial-curriculum.md` (ladder row, a second hand-off
block at the top, and a full obligations-register entry with the traps). Summary of what that
entry pins down, so this side stays honest:

- The lesson is the sequel to "Signals Move the Knobs" (A5): outside shapes move a knob there, the
  pattern's own numbers move it here.
- `bpf`/`bpq` and the `mul`/`add` mappers are taught NOWHERE in the corpus today, so the headline
  example needs them introduced as previews.
- Greensleeves cannot be pasted in as the finale: it also uses `chord()`/`voicing()`, `filterWhen`,
  `perlin.seg()` and `late()`, none of which are taught yet. The whistle line alone is the finale.
- The lesson must never say "every knob": string and boolean setters are won't-implement, and the
  compound-door fields have no accessor yet (`sprudel-accessors-compound-slots.md`).
- The engine gaps found during the sweep (`panSpread`, `density`, `duckattack`, `pcurve`,
  `loopBegin`/`loopEnd`, negative `speed`) are listed there as never-teach.
- A Lexikon entry for the concept is owed too (`LexikonData.kt`, Pattern domain).

The order rule (D6) is the part most likely to bite a reader, and it is the part that will read as
a bug report if the lesson does not get to it first.

## Review checklist mapping (`/dsl-design`)

1. Immutable: `Freq` is a stateless object; every operation returns a new mapper or pattern.
2. Door shape: not a builder door; construction input (`hz`) on `invoke`, no `configure`.
3. Both doors, same text, door-parity spec.
4. Parity: the accessor reads exactly the field its setter writes (`freq` and `freqHz`).
5. One word per concept: `freq` is the setter, the accessor and the mapper target; the replaced
   top-level function annotation is removed, not deprecated.
6. Coerce: a non-numeric value writes `null`; a mapper that yields nothing leaves the field
   unchanged (decided 2026-09-07, `_mapNumericField` KDoc); never throws.
7. No wire change.
8. No frontend DSP, no cycles over the wire.
9. KDoc on `freq` (constant) and `Freq.invoke` feeds the editor popup.
