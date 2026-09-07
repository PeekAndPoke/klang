# Sprudel — Memory

## Recent Work (2026-09-07)

- **Batch G, the last compounds.** `compressor(threshold, ratio, knee, attack, release)`,
  `unison(voices, spread, pan)`, `duck(orbit, depth, attack)`, `vibrato(rate, depth)`,
  `penv(amount, attack, decay, release, curve, anchor)`, `fm(env, h, attack, decay, sustain)`,
  `vowel(vowel, wet, floor)`, `body(material, wet, floor)`: objects with a child per numeric slot;
  the name slots `vowel` and `material` have no readers (`docs/tasks/future/string-slot-readers.md`).
  Retired: `vibratoMod`, the `p*` pitch stage doors, `fmenv/fmmod/fmh/fmattack/fmdecay/fmsustain`,
  `duckorbit/duckattack/duckdepth`, `voices/spread/panSpread`, `vowelWet/vowelFloor`,
  `bodyWet/bodyFloor`; `comp`, `uni`, `vib`, `pamt` stay as aliases. Every compound head keeps its
  value on a control gap and reinterprets on a bare call (the old `compressor()` no-op is gone). KSP
  now qualifies the owner in generated calls, because a slot named like its object (`vowel(vowel)`)
  shadowed it. The ignitor builders keep their own `voices`/`spread`; the migration skipped those.

- **Filters are objects with named slots (batch F).** `lpf(freq, q, passes, env, attack, decay,
  sustain, release)`, `hpf` the same, `bpf(freq, q, env, attack, decay, sustain, release)`,
  `notch` the same; `lpf(q = mul(2))` maps, `hpf(lpf.freq.div(2))` reads. Per-knob doors and
  aliases (`lpq`, `lpe`, `lpadsr`, `notchf`, `nfenv`, ...) are GONE (`LangRetiredDoorsSpec`).
  The long names `lowpass`/`highpass`/`bandpass` stay as constants. Compound objects have NO bare
  read: children only, one rule for every compound. `adsrCurves` is an object with the setter
  only (name slots, no readers); the singular `adsrCurve` went from sprudel and the ignitor door.
  `lang_effects_addons.kt` no longer exists. Filter curve objects wait for engine fields
  (`docs/tasks/filter-envelope-configuration.md`).

- **Compound effects are objects with named slots (batch E).** `room(wet, size, fade, lowpass,
  dim)`, `delay(wet, time, feedback, cap)`, `phaser(rate, wet, center, sweep, floor)`,
  `tremolo(depth, sync, shape, skew, phase)`, `distort(amount, shape, oversample)`,
  `crush(amount, oversample)`, `coarse(amount, oversample)`: `room(fade = 0.3)` sets one slot,
  `room(size = mul(2))` maps it on its own value, `lpf(room.lowpass)` reads it. Every per-knob
  door and alias (`roomWet`, `rsize`, `delayfb`, `ph`, `tremsync`, `dist`, `crushos`, ...) is
  GONE from both doors (`LangRetiredDoorsSpec`); `tremolo` moved out of the addons file. Slots
  apply in declaration order inside one call, so `room(dim = 3000, lowpass = room.dim)` reads the
  old dim: chain two calls for that. A slot name that is also a top-level symbol (`lowpass`, the
  `lpf` alias) shows two property variants under one docs symbol; intel tests filter on
  `owner == null`. Tutorials name the object in `teaches`/`previews` (`room`, `delay`), not the
  slots: the curriculum lint reads call names.

- **Accessor objects carry the script name** (`object gain`, `object adsr`), the `val` twins are
  gone: one declaration per concept in both doors. `"ClassName"` is suppressed at file level in
  the lang files for this. Alias constants read `val vel: velocity = velocity`.

- **One Kotlin door per accessor.** The 139 unannotated factories (`fun gain(...)`, the alias
  `fun vel(...)`) are gone; `val gain: Gain` plus `operator fun invoke` is the Kotlin call form
  (`apply(gain(0.5))` still compiles, through the invoke convention). The `val` stays unannotated,
  the `@KlangScript.Object` registers the script name.

- **Compound pilot: `adsr` is an object with children.** `adsr.attack/.decay/.sustain/.release`
  read the slots, `adsr(attack = mul(2))` maps one slot, and the single doors `attack()`,
  `decay()`, `sustain()`, `release()` are GONE from both doors (`LangRetiredDoorsSpec`).
  Sakura's `adsr("0.1:1:1:0.1")` never meant four values (no colon form exists; the string went to
  the attack slot and the mini-notation kept 0.1); rewritten as `adsr(0.1, 1, 1, 0.1)` per the
  maintainer, which changes those three noise beds.

- **Field accessors, batch four**: `unison, spread, panSpread, density, orbit, duckorbit,
  duckattack, duckdepth, compressor, fmenv, analog, duty, onepole` and eight aliases. The numeric
  sweep is complete: every numeric single-field setter is an accessor (84 objects after the
  adsr pilot removed the four stage objects, plus the four `adsr.*` children; 54 alias constants).

- **Field accessors, batch three**: 36 objects and 22 aliases across sample, synthesis, vowel,
  body, tonal, the notch addons and the filter envelopes. Tonal's inline update lambdas became
  named `<name>Update` values. Addon accessors live in `lang.addons` and carry the `addon` tag.

- **Field accessors, batch two** (effects): 24 objects, 20 alias constants (`val rsize: RoomSize =
  RoomSize`). Bug found by the new rows and fixed: `crushOversampleMutation` and
  `coarseOversampleMutation` used `toString()?.toIntOrNull()`, which is `null` for `"2.0"`, so
  `crushos(2)` never wrote its field from a number. Now `asDoubleOrNull()?.toInt()`.

- **Field accessors, batch one** (`docs/tasks/sprudel-field-accessors.md`): fourteen objects on the
  new `FieldAccessor` base (`lang.kt`): `gain, velocity, pan, postgain, lpf, hpf, bpf, lpq, hpq,
  bpq, attack, decay, sustain, release`. Recipe in `ref/dsl-conventions.md`. Null rule decided:
  on the mapper path a `null` result leaves the field unchanged (`_mapNumericField`).
  Specs: `LangFieldAccessorsSpec` (two rows per accessor, both doors), `FreqAccessorIntelSpec`.

## Recent Work (2026-09-06)

- **Field accessors, pilot on `freq`** (`docs/tasks/sprudel-field-accessors.md`). Two rules:
  1. A MAPPER argument to a setter applies to the setter's own field: `freq(mul(2))`,
     `freq(add(50))`, `bpf(freq)`. Implemented by `_mapNumericField(mapper, read, update)`:
     read the field into the value register, run the mapper, write the value register back,
     drain it. One chain, no join, so chords map each note on its own. Before this a mapper
     argument was silently dropped and the field CLEARED (`toListOfPatterns` returned null).
  2. Bare `freq` is an object (`@KlangScript.Object("freq") object Freq : PatternMapperProvider`)
     whose `mapper()` reads the frequency into the value register; its `@Invoke` member is the
     setter, so `freq(440)` is unchanged. `PatternMapperProvider` is deliberately NOT a
     `PatternMapperFn`: a `Function1` member `invoke(SprudelPattern)` next to the setter would give
     `Freq(pattern)` and `freq(pattern)` opposite meanings in Kotlin. First-step twins
     (`PatternMapperProvider.add/sub/mul/div`) unwrap once; after that the mapper library composes.
  - Rejected on the way, with reasons in the plan: a `QueryContext` key binding the source event
    (per-event context copy, and any join inside the control rebinds); a provider re-joined by
    time (`sampleAt` gives every chord note the first note's field); `Freq : PatternMapperFn`.
  - KSP: object symbols now carry their supertypes (`KlangScriptProcessor`, object docs), so
    `freq.mul(2)` resolves in the editor. Guard: `FreqAccessorIntelSpec`.
  - Found on the way: arithmetic evaluates a continuous control once per query arc
    (`seq("1 1 1").mul(sine)` is flat), `docs/tasks/sprudel-arithmetic-continuous-controls.md`.
    Use `perlin.seg(4)` until that is decided.

## Recent Work (2026-08-20)

- `tag(name)` addon (`lang_structural_addons.kt`): semantic event tags for visualizations/analysis.
  Tags live in `SprudelVoiceData.tags: Set<String>?` (unique, NO ordering guarantee), accumulate by
  chaining (`.tag("a").tag("b")`), union through `merge()`/`mergeFrom()`, and are copied into engine
  `VoiceData.tags` by `toVoiceData()` — they cross the wire (deliberate; analysis tools may use them).
  The wire-codec KSP gained general `Set<T>` support for this (`WireCodecProcessor` + `wireEncodeSet`/
  `wireDecodeSet`).
- ⚠️ The tag argument is a LITERAL — deliberately NOT routed through the lift helpers, which would
  parse `"guitar 1"` as mini-notation into two events. `reinterpretVoice { }` is the literal path.
- `LangTagSpec` includes a merge-overlay test because the `SprudelVoiceDataSpec` mergeFrom==merge
  oracle cannot see a SYMMETRIC bug in the shared `mergeTags` helper (mutation-verified).
- `ref/dsl-conventions.md` + `ref/dsl-addons.md` rewritten to current reality: the old delegate API
  (`@SprudelDsl`, `dslFunction`, init sentinels) is gone; plain `fun` + `@KlangScript.Function`.

## Current Status

- **Features**: ~263 / 303 implemented (~87%)
- **Tests**: All JVM tests passing ✅
- **KDoc**: All `@SprudelDsl` items fully documented across all `lang_*.kt` files ✅

## Recent Work (2026-02)

- Full KDoc pass on all `lang_*.kt` files
- `@sample` replaced with fenced ` ```KlangScript ``` ` blocks (348 occurrences)
- KSP: `MethodTooLargeException` fixed by chunking generated map; `@alias` tag support added
- `press()` / `pressBy()` implemented with `_innerJoin` control pattern support
- `SprudelDocsPage` smart search (`category:`, `tag:`, `function:` prefixes + logical AND)

## Lessons Learned

**Immutable at construction, mutable at runtime, both deliberate.** Every combinator returns a new
pattern (the project-wide DSL principle, see `audio/MEMORY.md` Architecture Decisions and
`docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`). The query/render path uses mutable single-owner
`SprudelVoiceData` on purpose (leaf clone ~17x faster). Do not "fix" either side toward the other.

**`.scale()` applies exactly once per chain** (decided 2026-08-20 on Der Schmetterling):
`resolveNote()` (`lang/lang_tonal.kt`) consumes the note index on first resolution (writes
`note`/`freqHz`, clears `value` and the step source), so a later `.scale()` finds no index and is
inert for pitch. Consequences: exported Klangbuch parts carry NO scale, not even a default (a
part-level default would flatten a song-level scale journey such as `<e4:minor!48 e5:minor!16>`);
unscaled parts still play chromatically; an importer's own `.scale()` works because it is first.
KDoc backlog: `scale()` should say "later .scale() calls do not re-pitch".

**Klangbuch parts are arrangement-free.** When a song is rewritten into `export` form, patterns,
shapes and parts (shape x pattern, fully voiced) are exported as-is; arrangement timing
(`filterWhen`, `early`, `late`, anything time-gating) lives only at the `stack(...)` song level.
An importer wants a part ready to play and can add gating, but cannot remove one chained in.
`slow`/`fast` are intrinsic to the voice and stay in the part.

**`_innerJoin` is mandatory** for any DSL function accepting pattern arguments — static values work
without it, but control patterns (e.g. `pressBy("<0 0.5>")`) silently break without it.

**Test across ≥12 cycles** — timing bugs compound and only surface after several cycles.

**`whole` is always non-nullable** — ignore any old comments suggesting otherwise.

**Step-based, not cycle-based** — `take(2)` takes 2 steps (events), not 2 cycles. Get this wrong
and all time-manipulation functions produce incorrect output.

**`repeatCycles(n)` repeats each cycle n times** (not truncation). Source cycle index =
`floor(output_cycle / n)`. E.g. `slowcat("a","b").repeatCycles(2)` → a a b b a a b b …

**`extend(n)` = `fast(n)`** (speeds up). It is NOT an alias for `slow()`.

**`iter(n)` is implemented via slowcat** of time-shifted patterns, not a custom pattern class:

```kotlin
val patterns = (0 until n).map { i ->
    val shift = i.toRational() / n.toRational()
    TimeShiftPattern.static(source, -shift)
}
return applyCat(patterns)
```

---

## Completed Features

### Factory Functions

- `stepcat()` / `timeCat()` / `timecat()` / `s_cat()`
- `within(start, end, transform)`
- `chunk(n, transform)` / `slowchunk()` / `slowChunk()`
- `echo(times, delay, decay)` / `stut()`
- `echoWith(times, delay, transform)` / `stutWith()` / `stutwith()` / `echowith()`

### Time Manipulation

- `pace(n)` / `steps(n)`
- `take(n)`, `drop(n)`, `extend(n)`
- `iter(n)`, `iterBack(n)`
- `repeatCycles(n)`

### Mini-Notation

- `:n` sample number, `-` rest, `@n` elongation, `!n` replication
- `?` random removal, `|` random choice
- `(pulses, steps)` / `(pulses, steps, rotation)` Euclidean rhythm
- `euclidish()` / `eish()`
- Mini-notation parser cache

### Pattern Creation

- `cat()`, `seq()`, `mini()`, `stack()`, `arrange()`
- `polyrhythm()`, `polymeter()`, `polymeterSteps()`
- `fastcat()`, `slowcat()`, `slowcatPrime()`
- `stackLeft()`, `stackRight()`, `stackCentre()`, `stackBy()`
- `run(n)`, `binary(n)`, `binaryN()`, `binaryL()`, `binaryNL()`
- `sequenceP()`, `pure(value)`, `silence`, `rest`, `nothing`

### Time Modification

- `fast()` / `density()`, `slow()` / `sparsity()`
- `early()`, `late()`, `rev()`
- `euclid()`, `euclidRot()` / `euclidrot`, `bjork()`
- `euclidLegato()`, `euclidLegatoRot()`
- `compress()`, `focus()`, `zoom()`
- `gap()`, `fastGap()` / `densityGap`
- `swingBy()`, `swing()`
- `ply()`, `plyWith()`, `plyForEach()`
- `hurry()`
- `loopAt()`, `loopAtCps()`

### Pattern Transformation

- `revv()`, `struct()`, `structAll()`, `mask()`, `maskAll()`
- `superimpose()`, `layer()`, `apply()`
- `jux()`, `juxBy()`
- `off()`, `within()`, `applyN()`, `when()`
- `brak()`, `inv()` / `invert()`

### Pattern Operations (Medium Priority)

- `palindrome()`, `linger()`, `ribbon()` / `rib`
- `inside()`, `outside()`
- `press()`, `pressBy()`
- `fastchunk()` / `fastChunk`, `chunkInto()` / `chunkinto`, `chunkBackInto()` / `chunkbackinto`, `chunkBack()` /
  `chunkback`

### Pattern Picking & Selection

- `chooseWith()`, `chooseInWith()`, `choose()`, `chooseOut()`, `chooseIn()`, `choose2()`
- `chooseCycles()`, `randcat()`
- `wchoose()`, `wchooseCycles()`, `wrandcat()`
- `pick()`, `pickmod()`, `pickF()`, `pickmodF()`
- `pickOut()`, `pickmodOut()`
- `pickRestart()`, `pickmodRestart()`
- `pickReset()`, `pickmodReset()`
- `inhabit()` / `pickSqueeze()`, `inhabitmod()` / `pickmodSqueeze()`
- `squeeze()`, `bite()`

### Tonal Functions

- `note()`, `n()`, `freq()`, `scale()`, `sound()` / `s()`, `bank()`
- `transpose()`, `scaleTranspose()`
- `chord()`, `voicing()`, `rootNotes()`
- 112 chord types, all 90 Strudel chord notations

### Arithmetic & Math

- `add()`, `sub()`, `mul()`, `div()`, `mod()`, `pow()`, `log2()`
- `round()`, `floor()`, `ceil()`

### Bitwise Operators

- `bitAnd()`, `bitOr()`, `bitXor()`, `bitShl()`, `bitShr()`

### Comparison & Logic

- `lt()`, `gt()`, `lte()`, `gte()`, `eq()`, `eqt()`, `ne()`, `net()`
- `and()`, `or()`

### Audio Effects — Filters

- `lpf(freq, q, passes, env, attack, decay, sustain, release)` / `lowpass`; readers `lpf.freq/.q/.passes/.env/.attack/.decay/.sustain/.release`
- `hpf(...)` / `highpass` the same; `bpf(freq, q, env, attack, decay, sustain, release)` / `bandpass`
- `notch(freq, q, env, attack, decay, sustain, release)` (addon); readers `notch.*`
- `vowel()`

### Audio Effects — Filter Envelopes

- The envelope of each filter is its own slots (`lpf(env = 24, attack = 0.01, decay = 0.3, sustain = 0.2)`);
  the old stage doors and `lpadsr`/`hpadsr`/`bpadsr`/`nfadsr` are retired (2026-09-07, `LangRetiredDoorsSpec`)

### Audio Effects — Pitch Envelope

- `penv(amount, attack, decay, release, curve, anchor)` / `pamt`; readers `penv.*`

### Audio Effects — Waveshaping / Distortion

- `distort(amount, shape, oversample)`, `crush(amount, oversample)`, `coarse(amount, oversample)`;
  readers `distort.amount`, `distort.oversample`, `crush.*`, `coarse.*` (2026-09-07, batch E)

### Audio Effects — Tremolo / AM

- `tremolo(depth, sync, shape, skew, phase)`; readers `tremolo.depth/.sync/.skew/.phase`

### Audio Effects — Dynamics & Panning

- `velocity()`, `postgain()`, `compressor()`
- `jux()`, `juxBy()` / `juxby`

### Audio Effects — Reverb

- `room(wet, size, fade, lowpass, dim)`; readers `room.wet/.size/.fade/.lowpass/.dim`; `iresponse()` / `ir`

### Audio Effects — Delay

- `delay(wet, time, feedback, cap)`; readers `delay.wet/.time/.feedback/.cap`

### Audio Effects — Phaser

- `phaser(rate, wet, center, sweep, floor)`; readers `phaser.rate/.wet/.center/.sweep/.floor`

### Audio Effects — Duck / Sidechain

- `duck(orbit, depth, attack)`; readers `duck.*`

### Audio Effects — Other

- `orbit()` / `o`

### Sample Manipulation

- `begin()`, `end()`, `speed()`, `loop()`
- `loopBegin()` / `loopb`, `loopEnd()` / `loope`
- `loopAt()`, `loopAtCps()`
- `cut()`, `slice()`, `splice()`

### Continuous Signals

- `steady()`, `signal()`, `time`
- `sine`, `sine2`, `cosine`, `cosine2`
- `saw`, `saw2`, `isaw`, `isaw2`
- `tri`, `tri2`, `itri`, `itri2`
- `square`, `square2`
- `perlin`, `perlin2`, `berlin`, `berlin2`
- `range()`, `rangex()`, `range2()`
- `rand`, `rand2`, `irand()`, `randL()`, `brand`, `brandBy()`
- `segment()` / `seg`, `toBipolar()`, `fromBipolar()`
- `round()`, `floor()`, `ceil()`, `ratio()`

### Random & Seeding

- `seed()` / `withSeed()`, `randrun()`, `shuffle()`, `scramble()`

### Conditional & Probabilistic

- `sometimesBy()`, `sometimes()`, `often()`, `rarely()`, `almostAlways()`, `almostNever()`, `always()`, `never()`
- `degradeBy()`, `degrade()`, `undegradeBy()`, `undegrade()`
- `someCyclesBy()`, `someCycles()`
- `firstOf()` / `every()`, `lastOf()`
- `filter()`, `filterWhen()`, `bypass()`

### Noise Generators

- `white` / `whitenoise`, `pink` / `pinknoise`, `brown` / `brownnoise`
- `dust`, `crackle`

### Synthesis Parameters

- `gain()`, `pan()`, `legato()` / `clip()`
- `vibrato(rate, depth)` / `vib`; readers `vibrato.rate`, `vibrato.depth`
- `accelerate()`, `unison(voices, spread, pan)` / `uni`, `density()` / `d` (`detune()` and `spread()` are gone)
- `adsr()` (its stages are slots and `adsr.*` children; the single doors were removed 2026-09-07)
- `onepole()` (Klang extension; formerly `warmth`, now Hz)
- `velocity()`, `postgain()`
- FM synthesis: `fm(env, h, attack, decay, sustain)`; readers `fm.*`
- Pitch envelope: `penv(amount, attack, decay, release, curve, anchor)`

### Arithmetic Addons (Non-Strudel)

- `flipSign()`, `oneMinusValue()`, `not()`

### Structural Addons (Non-Strudel)

- `morse(text)`

### System Functions

- `hush()`, `pure()`, `nothing`

### Architectural

- Rational number time coordinates (exact arithmetic, no float drift)
- Mini-notation parser cache
