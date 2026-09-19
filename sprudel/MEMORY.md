# Sprudel — Memory

## The `pregain` door: the other level word (2026-09-19)

- **`pregain(amount)` is exactly `oscp("pregain", amount)`**, one key of the oscParams bag, and it
  lives next to `gain` in `lang_dynamics_level.kt` because a reader looking for a level word looks
  there. All four forms (pattern, String, the callable object, the `PatternMapperFn` chain), plus
  the accessor object, `@scope voice`, `@category dynamics`.
- **It takes a control pattern**, which is the idiom the plan named: `.pregain("1 0.7 0.8")` is
  play harder, get dirtier, on an instrument that places the slot in front of a nonlinearity.
- **On a CLIPPING shape it needs a gentle drive to be worth anything**, which the door's KDoc now
  says because it is the opposite of the intuition: in hard saturation the slot moves neither the
  tone nor the level (a clipper holds both, measured at 0.006 shape distance and 0.999 level ratio
  at `distort(2)`), so there the level has to come from `gain`. The WAVEFOLDERS (`fold`,
  `linearfold`, `sineshaper`) are the exception: they never saturate, so `pregain` is the fold
  depth and the strongest tone knob at any drive, and on `fold` turning it down makes the sound
  louder. The per-shape numbers and the chosen example drive are in `audio/MEMORY.md`.
- **It has the numeric lift, the mapper form and an accessor, and needed no new machinery for
  any of them.** `analog`, `duty` and `onepole` are the precedent: an `oscp`-backed door reads its
  own slot back through `_mapNumericField(read = { it.oscParams?.get(name) })` exactly as a
  field-backed door reads a field. It uses `_liftOrReinterpretNumericalField` (like `gain`) rather
  than the String lift those three use, because the value is always a number and the numeric lift
  leaves the slot untouched for a control value that is not one.
- **The mapper on an UNSET slot is a no-op**, the general 2026-09-07 rule, the same as
  `gain(mul(x))`. Recorded, not worked around; the entry below has the longer note on what a
  mapper-spelled trim costs.
- **What it does NOT do** is the part worth remembering: it writes a slot, and a slot does what
  the instrument's tree wires it to. No built-in instrument places `pregain` yet (that is phase 3
  of the signal-flow plan), so on today's sounds the door is inert, bit for bit. That is the
  design, not a gap: a bare sine has no drive. The KDoc says so and one of its examples shows it.
- Guards: `LangPregainSpec`, rows in `LangControlRestSpec`, `LangFieldAccessorsSpec`,
  `SprudelScopeSpec`, `CallInfoTest` and `FreqAccessorIntelSpec`. What the slot DOES is
  audio_be's (`PregainSlotRenderSpec`, `VoicePregainWireSpec`); nothing in sprudel can hear it.

## `gain` is the one level word; `velocity` folds at the wire (2026-09-19)

- **`postgain` is retired**, every form of it (the pattern door, the string door, the accessor
  object, the mapper chain). It was a SECOND multiplier at the same point in `SendRenderer`, so it
  said nothing `gain` does not say. One word per concept: `gain` is the level at which the voice
  leaves, and a song that used both now folds them by multiplication. `LangPostGainSpec` is gone,
  the `postgain` rows left `LangDynamicsSpec`, `LangControlRestSpec`, `LangFieldAccessorsSpec`,
  `SprudelScopeSpec`, `CallInfoTest` and `FreqAccessorIntelSpec`.
- **`velocity` stays a sprudel word and stops at the wire.** The door, the `vel` alias, the field
  and the accessor are unchanged; `SprudelVoiceData.toVoiceData` multiplies it into `gain`
  (`foldedGain()`) and `VoiceData` has no `velocity` field any more. Both unset gives `null`, so the
  wire stays sparse and the engine's own `?: 1.0` answers; otherwise `(gain ?: 1.0) * (velocity ?: 1.0)`,
  the same operands in the same order the voice factory used, so an unaccented voice keeps its bits.
  A non-finite value reads as unset PER OPERAND, before the product: substituting after it would
  turn `gain(0.5).velocity(NaN)` into a NaN the engine reads as 1.0, full level where the author
  asked for half. Two operands that both read as unset give `null`, the same answer as writing
  neither. Guard: `WireGainFoldSpec` (the four null/non-null combinations, the set ones by raw
  bits and the both-unset one asserting `null`, plus the non-finite operands and a patterned row).
- **Why at the wire and not at the `velocity()` door.** `velocity(p)` read as `gain(mul(p))` does
  NOTHING on an event whose gain is unset, because a mapper on an unset field leaves it unset
  (`_mapNumericField`, decided 2026-09-07), and it would make `.velocity(0.7).gain(0.5)`
  order-dependent. Folding once, at the boundary, has neither problem.
- **The rounding note.** A voice that never used `postgain` is BIT-IDENTICAL: the product moved to
  the other side of the wire with its operands and their order intact. A voice that used both
  changes by floating-point rounding only, because `(s·p)·(g·c)` became `s·((g·p)·c)` and the
  literal folds in the songs re-associate a product. Measured over 309,867 onset events of every
  song text in the repo: 89.4 % bit-identical, worst relative deviation 2.3e-16.
- **A trim spelled as a mapper is not the door it replaced.** Where a song's level lived in a
  separate lambda from its `gain` (the `*_arrange` functions of Der Schmetterling), the migration
  wrote `gain(mul(P))`. That goes through `_mapNumericField` and `_appLeft`, not the `_outerJoin`
  the retired door used, and the three differences are worth knowing before writing another one:
  a REST in the control pattern DROPS the event instead of leaving the field alone; a control that
  changes inside an event FRAGMENTS it into parts sharing one whole, of which only the first is an
  onset (so playback, which schedules onsets only, does not hear it); and on an event whose gain is
  UNSET it is a silent no-op, which is what makes it safe only when a `gain(...)` runs upstream on
  every event. That last one bites anyone who imports an exported `*_arrange` without its
  `*_shape`: the trim then does nothing and the part plays at full level.
- Signal-flow plan section 6; `pregain` and the Katalyst's unity `gain` stage are the next step.

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
  aliases (`lpq`, `lpe`, `lpadsr`, `notchf`, `nfenv`, ...) are GONE.
  The long names `lowpass`/`highpass`/`bandpass` stay as constants. Compound objects have NO bare
  read: children only, one rule for every compound. `adsrCurves` is an object with the setter
  only (name slots, no readers); the singular `adsrCurve` went from sprudel and the ignitor door.
  Filter curve objects wait for engine fields (`docs/tasks/filter-envelope-configuration.md`).

- **Compound effects are objects with named slots (batch E).** `reverb(wet, size, lowpass)`,
  `delay(wet, time, feedback, cap)`, `phaser(rate, wet, center, sweep, floor)`,
  `tremolo(depth, sync, shape, skew, phase)`, `distort(amount, shape, oversample)`,
  `crush(amount, oversample)`, `coarse(amount, oversample)`: `reverb(lowpass = 3000)` sets one slot,
  `reverb(size = mul(2))` maps it on its own value, `lpf(reverb.lowpass)` reads it. Every per-knob
  door and alias (`roomWet`, `rsize`, `delayfb`, `ph`, `tremsync`, `dist`, `crushos`, ...) is
  GONE from both doors. Slots apply in declaration order inside one
  call, so `reverb(lowpass = 4000, size = reverb.lowpass)` reads the old lowpass: chain two calls for that. A slot name that is also a top-level symbol (`lowpass`, the
  `lpf` alias) shows two property variants under one docs symbol; intel tests filter on
  `owner == null`. Tutorials name the object in `teaches`/`previews` (`reverb`, `delay`), not the
  slots: the curriculum lint reads call names.

- **The reverb is `reverb`, not `room` (2026-09-16).** One word on every surface: the sprudel door, the
  master builder (`r.size().lowpass()`), the wire (`VoiceData.reverb`/`reverbSize`/`reverbLowpass`,
  `SprudelVoiceData.reverbFx` as the group, mirroring `delayFx`) and the engine (`Reverb.size`/`lowpass`).
  `fade` was `size / 10` with an override and is gone (`fade = x` is `size = 10x`); `dim` was never read;
  the master's `damp` went because `lowpass` spans the same range. A third positional argument is now
  `lowpass`. `docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`.

- **A rest in a setter's control pattern leaves the field untouched (2026-09-16).** `_liftNumericField` and
  `_liftStringField`, behind every per-field setter and compound slot, write nothing on an event where the
  control has no event (a `~`), so a value an earlier call set survives: `gain(0.5).gain("<0.8 ~>")` keeps
  0.5 in the rest cycle. Before, the setter was called with null and most setters cleared the field. The
  numeric helper also skips a control value that is not a number; numeric slots that sit on the STRING
  helper (`orbit`/`o`/`cylinder`, the `adsr` stages) still clear on a non-number like `x` (known asymmetry).
  Not covered: the structural `_lift` / `_liftData` family (`degradeByWith`, `undegradeByWith`, `unit`,
  `loop`, `adsrOn`) and `hurry`'s `fast`, where a rest still drops the notes. Guard: `LangControlRestSpec`,
  one test per setter and per compound slot (187), mutation-checked.

- **The bus doors also write the orbit's slot state (2026-09-18, Katalyst step 5a).** `katalystParams`
  is the `oscParams` twin on the other host: `oscp` fills the voice's instrument, `.katp(name, value)`
  the chain its orbit runs, and the two namespaces never cross. Until the voice fields leave the wire
  (step 5b-3) every bus door writes BOTH: its own voice fields and the matching `<stage>.<knob>` slots,
  filling the stage's companions per the compound-door rule (`/dsl-design` §4, which is that rule's
  one home). **Since 2026-09-19 (step 5b-1) the SLOT is the one that reaches the orbit**: every
  chain reads the map, the one a cylinder is born with included, so a bus door and its `katp` slot
  are the same knob and a `katp("reverb.size", 8)` needs no declaration first. The fields still
  carry the per-voice send AMOUNTS (`delay`, `reverb`) until step 5b-2 and are inert for everything
  else. The rule's one home is the `katp` door's KDoc. Guard: `LangKatalystParamSpec`,
  mutation-checked (one deletion per door family). `docs/tasks/katalyst-dsl.md` §9, step 5a.

- **A body material and a vowel are INDICES, and `.katalyst(dsl)` REPLACES (2026-09-18, Katalyst
  step 5a-2).** Two cleanups that retired the step-5a rule above it. (1) The door replaces like
  `sound()` and `master()`: `x.katalyst(A).katalyst(B)` is `x.katalyst(B)`, `KatalystAppend` and
  `KatalystDsl.plus` are gone, and each door stamps ONE `KatalystValue.Dsl` instance onto every event
  (cheaper than the memo it replaced). `KatalystBuilder.classic()` appends its block at most
  once per builder. (2) There is no string slot: `body.material` and `vowel.vowel` carry the INDEX of
  a name in `BodyMaterials.names` / `VowelBands.names` (0 = `none`), so `body(material = "wood")` on
  a pattern reaches a declared chain like every other knob and the song text did not move. The doors
  convert through the one shared `BodyMaterials.indexOf` / `VowelBands.indexOf`. `KatalystDsl.classic`
  carries `body.material`, `vowel.vowel`, `body.wet` AND `vowel.wet` as UNSET `Param` slots, because
  a 0.0 wet is a SET wet that the engine never substitutes for, which made a material-only
  `body("wood")` run dry on a declared chain (round 1 of this step's review). Since 5a-3 the DOOR
  fills those two as well, so the engine's substitution is the NaN rule for a raw `katp` write only.
  Guards: `CatalogueIndexSpec` (audio_bridge, the conversion both ways and its edges), the
  `LangKatalystParamSpec` body/vowel index rows, `KatalystClassicMatchesUntouchedVoiceSpec` (what the
  chain installs against what the wire carries, both spellings) and `KatalystDoorFillRenderSpec`
  (the render, byte-identical).
  `docs/tasks/katalyst-dsl.md` §9, step 5a-2.

- **`oscParams` and `katalystParams` are one class, `ParamBag`, mutable and single-owner
  (2026-09-18).** They were immutable-replace (`map + (k to v)` per write, a fresh map per slot);
  with a dozen bus doors writing slots after Katalyst 5b that is the "twenty allocations per note"
  class the June work removed. They are now `ParamBag` fields (`sprudel/ParamBag.kt`) with the same
  contract as the `Svd*` groups: `putOscParam` / `putKatalystParam` write ONE name into the bag the
  event already owns, `oscParamsOrNew()` / `katalystParamsOrNew()` hand a whole-stage fill the bag
  once, `copy()` is the per-event deep copy `clone()` makes, `merge` builds a fresh bag as
  `mergeSvdAdsr` does and `mergeFrom` folds into the receiver's own (last writer wins per name). The
  copying helpers (`withOscParam`, `withOscParams`, `mergeOscParamsFrom`, `putOscParams`,
  `putOscParamsFrom`) went with the old storage: nothing called them, and a copy helper over a
  mutable bag is a second way to own one. `toVoiceData` hands the wire a `toMap()` COPY of each: the
  wire value outlives the pattern event (the backend holds `Voice.katalystParams` for the whole life
  of the voice and gates its re-resolve on the map's IDENTITY), and the boundary already allocates a
  `VoiceData`. The bag also carries the fill rule's one method, `setOrDefault` (below); the rule
  itself lives in `/dsl-design` §4.
  Guards: `ParamBagSpec`, `LangKatalystParamSpec` ("a bus door call on an already-cloned voice
  allocates no map", "toVoiceData hands the wire a COPY of both maps"), the golden, mutation-checked.

- **A compound door fills per param, at the door (2026-09-16 for the sends, 2026-09-18 for the rest,
  Katalyst step 5a-3).** The rule and its two kinds of stage live in `/dsl-design` §4, its one home;
  do not restate them here. What the sprudel side does: each per-slot setter writes its own slot with
  `ParamBag.set`, the stage's fill then writes the companions with `setOrDefault(name, null, CONST)`,
  and the constants come from `audio_bridge/constants/`. Six of the seven bus doors also fill their
  VOICE fields with the same constants; `duck` does not, because `VoiceFactory` already supplies both
  its numbers and writing them out would change what every existing song sends. Byte-identical for
  every existing song, because the engine was already substituting exactly those constants for an
  unset field (`VoiceFactory`, `Voice.Compressor.fromParams`, `FilterDef.Body`/`Formant`'s null
  floor); the engine keeps them as the NaN rule for a raw `katp` write, not as a second fill. Guards:
  `ParamBagSpec`, `LangKatalystParamSpec`, the per-door lang specs, `KatalystDoorFillRenderSpec`
  (renders: body, compressor, duck). `docs/tasks/katalyst-dsl.md` §9 step 5a-3.

- **Accessor objects carry the script name** (`object gain`, `object adsr`), the `val` twins are
  gone: one declaration per concept in both doors. `"ClassName"` is suppressed at file level in
  the lang files for this. Alias constants read `val vel: velocity = velocity`.

- **One Kotlin door per accessor.** The 139 unannotated factories (`fun gain(...)`, the alias
  `fun vel(...)`) are gone; `val gain: Gain` plus `operator fun invoke` is the Kotlin call form
  (`apply(gain(0.5))` still compiles, through the invoke convention). The `val` stays unannotated,
  the `@KlangScript.Object` registers the script name.

- **Compound pilot: `adsr` is an object with children.** `adsr.attack/.decay/.sustain/.release`
  read the slots, `adsr(attack = mul(2))` maps one slot, and the single doors `attack()`,
  `decay()`, `sustain()`, `release()` are GONE from both doors.
  Sakura's `adsr("0.1:1:1:0.1")` never meant four values (no colon form exists; the string went to
  the attack slot and the mini-notation kept 0.1); rewritten as `adsr(0.1, 1, 1, 0.1)` per the
  maintainer, which changes those three noise beds.

- **Field accessors, batch four**: `unison, spread, panSpread, density, orbit, duckorbit,
  duckattack, duckdepth, compressor, fmenv, analog, duty, onepole` and eight aliases. The numeric
  sweep is complete: every numeric single-field setter is an accessor (84 objects after the
  adsr pilot removed the four stage objects, plus the four `adsr.*` children; 54 alias constants).

- **Field accessors, batch three**: 36 objects and 22 aliases across sample, synthesis, vowel,
  body, tonal, notch and the filter envelopes. Tonal's inline update lambdas became named
  `<name>Update` values.

- **Field accessors, batch two** (effects): 24 objects, 20 alias constants (`val rsize: RoomSize =
  RoomSize`). Bug found by the new rows and fixed: `crushOversampleMutation` and
  `coarseOversampleMutation` used `toString()?.toIntOrNull()`, which is `null` for `"2.0"`, so
  `crushos(2)` never wrote its field from a number. Now `asDoubleOrNull()?.toInt()`.

- **Field accessors, batch one** (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`): fourteen objects on the
  new `FieldAccessor` base (`lang.kt`): `gain, velocity, pan, postgain (retired 2026-09-19), lpf, hpf, bpf, lpq, hpq,
  bpq, attack, decay, sustain, release`. Recipe in `ref/dsl-conventions.md`. Null rule decided:
  on the mapper path a `null` result leaves the field unchanged (`_mapNumericField`).
  Specs: `LangFieldAccessorsSpec` (two rows per accessor, both doors), `FreqAccessorIntelSpec`.

## Recent Work (2026-09-06)

- **Field accessors, pilot on `freq`** (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`). Two rules:
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

- `tag(name)` (`lang_structural_tag.kt`): semantic event tags for visualizations/analysis.
  Tags live in `SprudelVoiceData.tags: Set<String>?` (unique, NO ordering guarantee), accumulate by
  chaining (`.tag("a").tag("b")`), union through `merge()`/`mergeFrom()`, and are copied into engine
  `VoiceData.tags` by `toVoiceData()` — they cross the wire (deliberate; analysis tools may use them).
  The wire-codec KSP gained general `Set<T>` support for this (`WireCodecProcessor` + `wireEncodeSet`/
  `wireDecodeSet`).
- ⚠️ The tag argument is a LITERAL — deliberately NOT routed through the lift helpers, which would
  parse `"guitar 1"` as mini-notation into two events. `reinterpretVoice { }` is the literal path.
- `LangTagSpec` includes a merge-overlay test because the `SprudelVoiceDataSpec` mergeFrom==merge
  oracle cannot see a SYMMETRIC bug in the shared `mergeTags` helper (mutation-verified).
- `ref/dsl-conventions.md` rewritten to current reality: the old delegate API (`@SprudelDsl`,
  `dslFunction`, init sentinels) is gone; plain `fun` + `@KlangScript.Function`. (`ref/dsl-addons.md`
  was rewritten in the same pass and deleted 2026-09-07 with the addons split.)

## Current Status

- **Features**: ~263 / 303 implemented (~87%)
- **Tests**: All JVM tests passing ✅
- **KDoc**: All `@KlangScript.Function` items fully documented across all `lang_*.kt` files ✅

## Recent Work (2026-02)

- Full KDoc pass on all `lang_*.kt` files
- `@sample` replaced with fenced ` ```KlangScript ``` ` blocks (348 occurrences)
- KSP: `MethodTooLargeException` fixed by chunking generated map; `@alias` tag support added
- `press()` / `pressBy()` implemented with `_innerJoin` control pattern support
- `SprudelDocsPage` smart search (`category:`, `tag:`, `function:` prefixes + logical AND)

## Lessons Learned

**The addons split is gone (2026-09-07).** `lang/addons/`, the package
`io.peekandpoke.klang.sprudel.lang.addons` and the `addon` doc tag are retired. The split asked
"does the original Strudel have this?", and sprudel diverged far enough that the answer stopped
describing anything a reader could use: it only made them guess which of two directories a function
sits in. Every DSL file now lives in `lang/` as `lang_<group>_<subgroup>.kt`, none over ~700 lines
(`docs/tasks-archive/2026-09/20260907-sprudel-lang-file-reorganisation.md`).

**Immutable at construction, mutable at runtime, both deliberate.** Every combinator returns a new
pattern (the project-wide DSL principle, see `audio/MEMORY.md` Architecture Decisions and
`docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`). The query/render path uses mutable single-owner
`SprudelVoiceData` on purpose (leaf clone ~17x faster). Do not "fix" either side toward the other.

**`.scale()` applies exactly once per chain** (decided 2026-08-20 on Der Schmetterling):
`resolveNote()` (`lang/lang_tonal_note.kt`) consumes the note index on first resolution (writes
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
- `morse(text)`

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
- `flipSign()`, `oneMinusValue()`, `not()`

### Bitwise Operators

- `bitAnd()`, `bitOr()`, `bitXor()`, `bitShl()`, `bitShr()`

### Comparison & Logic

- `lt()`, `gt()`, `lte()`, `gte()`, `eq()`, `eqt()`, `ne()`, `net()`
- `and()`, `or()`

### Audio Effects — Filters

- `lpf(freq, q, passes, env, attack, decay, sustain, release)` / `lowpass`; readers `lpf.freq/.q/.passes/.env/.attack/.decay/.sustain/.release`
- `hpf(...)` / `highpass` the same; `bpf(freq, q, env, attack, decay, sustain, release)` / `bandpass`
- `notch(freq, q, env, attack, decay, sustain, release)`; readers `notch.*`
- `vowel()`

### Audio Effects — Filter Envelopes

- The envelope of each filter is its own slots (`lpf(env = 24, attack = 0.01, decay = 0.3, sustain = 0.2)`);
  the old stage doors and `lpadsr`/`hpadsr`/`bpadsr`/`nfadsr` are retired (2026-09-07)

### Audio Effects — Pitch Envelope

- `penv(amount, attack, decay, release, curve, anchor)` / `pamt`; readers `penv.*`

### Audio Effects — Waveshaping / Distortion

- `distort(amount, shape, oversample)`, `crush(amount, oversample)`, `coarse(amount, oversample)`;
  readers `distort.amount`, `distort.oversample`, `crush.*`, `coarse.*` (2026-09-07, batch E)

### Audio Effects — Tremolo / AM

- `tremolo(depth, sync, shape, skew, phase)`; readers `tremolo.depth/.sync/.skew/.phase`

### Audio Effects — Dynamics & Panning

- `velocity()`, `compressor()`
- `jux()`, `juxBy()` / `juxby`

### Audio Effects — Reverb

- `reverb(wet, size, lowpass)`; readers `reverb.wet/.size/.lowpass` (`iresponse`/`ir` removed 2026-09-16, never read)

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

- `gain()`, `pregain()`, `pan()`, `legato()` / `clip()`
- `vibrato(rate, depth)` / `vib`; readers `vibrato.rate`, `vibrato.depth`
- `accelerate()`, `unison(voices, spread, pan)` / `uni`, `density()` / `d` (`detune()` and `spread()` are gone)
- `adsr()` (its stages are slots and `adsr.*` children; the single doors were removed 2026-09-07)
- `onepole()` (Klang extension; formerly `warmth`, now Hz)
- `velocity()`
- FM synthesis: `fm(env, h, attack, decay, sustain)`; readers `fm.*`
- Pitch envelope: `penv(amount, attack, decay, release, curve, anchor)`

### System Functions

- `hush()`, `pure()`, `nothing`

### Architectural

- Rational number time coordinates (exact arithmetic, no float drift)
- Mini-notation parser cache
