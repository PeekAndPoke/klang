# KLANG-00417: Parameter parity audit across every DSL surface

> **Status (2026-11-06): DONE.** Steps 1 to 4 done the same day (the audit, the table, every gap fixed or recorded, the pins). Started 2026-11-06 (maintainer: "please start with KLANG-00417 ... backward compatibility is not a concern, but update any of the build-in songs, jingles and tutorials"). Split out of KLANG-00137 (master dsl followups) section 1 the same day ("the parity audit should get its own ticket and should be worked on when all the dsp restructuring is done"); the maintainer's start answers the first question below.
>
> **Part of:** KLANG-00357 (Effects: the Katalyst and the master)

## Why this exists

**The rule (maintainer, 2026-08-02):** *"The same params must be available in the sprudel DSL, Ignitor, Master etc.
and they need to mean the same thing. This is important for developer experience."*

The master DSL work proved this is not theoretical: `roomSize` silently meant a ~1 s tail on a cylinder and ~12.5 s on
the master, because sprudel divided by 10 and the master did not, and `roomFade`, the parameter that actually set the
tail, existed on only one of them. Both were fixed for reverb and delay; nothing has audited the rest.

`_v1-scope.md` names this audit as the one item of KLANG-00137 that gates V1 (a renamed or rescaled knob changes what
a tutorial teaches). Running it after the restructuring keeps that: it lands before the tutorials lock the surface,
but after the shape changes that would otherwise move the knobs it checked.

## What the audit covers

Per effect and per source knob: does the same concept have the same **name**, the same **scale** and the same
**availability** on every surface it could sensibly appear on: sprudel per voice, sprudel per cylinder, the Ignitor, the
Katalyst on a cylinder and at the output, and both doors of each (KlangScript and Kotlin; KLANG-00148 (dsl kotlin
surface parity) is the two-door gate, this audit is the across-surfaces one).

The asymmetries found so far, all resolved, as the shape of what to look for:

- `room` (cylinder) against `wet` (master): one thing, two words. Resolved 2026-08-24 and 2026-09-07: both doors say `wet`.
- `room(size)` 0..10 against `room(fade)` 0..1, `fade` making `size` inert: resolved 2026-09-16 (KLANG-00208 (reverb
  naming unification)), `fade` is gone.
- `damp` on the master only: resolved 2026-09-16, `lowpass` (Hz) is the one damping knob, `reverb` the one door word.
- `delaycap` not a `delay()` slot: resolved 2026-09-07, `cap` is the fourth slot.
- `roomDim` and `iResponse` stored and never read: resolved 2026-09-16, removed end to end.

A recorded difference that is not a parity gap: `lookahead` is a knob of the Katalyst `compressor` and `limiter` at
any position (phase 3 step 12 decision (a), C2) but not of sprudel's `compressor()`, because it is fixed per chain (it
sizes a ring) while a sprudel door writes slots on the running chain. The authored `limiter()` defaults to lookahead 0
against the house limiter's 5 ms; `KlangScriptKatalystDoorParitySpec`'s `limiter` row pins the numbers. The history
of the earlier exception is in KLANG-00137 section 1 and KLANG-00225 (master limiter lookahead) sections 3 and 4.

## Decide before implementing

- **What "the DSP restructuring is done" means when this starts:** answered 2026-11-06 by the maintainer's start.
  A rename or rescale may break old songs (no backward compatibility); the built-in songs, the jingles and the
  tutorials move with it, in the same change.
- **The form of the result:** a table per stage (concept, surfaces, name, scale, default), the gaps as their own
  numbered items, each fixed or recorded as a deliberate exception with its reason in the KDoc.
- **What pins it afterwards:** the door parity specs that exist (`KlangScriptKatalystDoorParitySpec` and its
  siblings) extended to the gaps found, so a later door cannot drift silently.

## The work

1. List every surface and its knobs from the code (the doors, the wire slots, the backend stages), not from the docs.
2. The table, and the gaps.
3. Each gap fixed or recorded; a rename or rescale is a shape change and goes to the maintainer first.
4. The pins.

## The audit (2026-11-06)

Steps 1 and 2: three read-only passes over the code (the bus stages, the voice shaping, the sources), each surface read
from its door, its slot and the backend that reads it. The full tables, one per stage with every knob, its name and
position on each door, the slot key, the unit, the default and its home, are the appendix below, verbatim from the
passes (their file:line references are as of 0cf2f5f83). The gaps, merged and numbered once:

### A. Fixed without asking (no name, scale or sound changes): the KDoc and the plumbing

- **A1. False KDoc.** The duck's `attack` called "how fast the duck closes" on the Katalyst doors and in
  `BusEffectDefaults` (it is the recovery: the duck closes at once, `Ducking`); an unset `reverb.lowpass` called "no
  damping" (the engine damps at `DEFAULT_DAMP`; also in `/dsl-design` §4); pluck `brightness` called the burst's
  hardness (it is the loop's one-pole, the burst never reads it); `sndDust(tail)` called a length (an amplitude
  exponent); `voices` "1 to 16" (the engine takes 0 to 256); `pickPosition` "1 = neck" against "1 = nut"; the "wire
  spelling" `phaserDepth` / `bodyMix` / `vowelMix` (sprudel-internal, off the wire); the four sprudel filters' "without
  `env` the filter rests at `freq`" (a written stage knob fills the depth); "the pattern-level `.spread()`" (11 times, no
  such door); "density on dust/crackle" (crackle reads `chaos`); `sndSuperPluck`'s colon string and "default 5-string"
  (8); the `unison(5, 0.3)` spread example; "only the floor default differs" (`Phaser`); "Default: 0.0" on the distort
  and drive overloads; `Slots.vibrato` "rate and semitones" (range and phase too); the bus distort called "the
  Ignitor's `distort`" (the fused law, decision D2); the delay "self-oscillates" at feedback 1 (the echoes hold);
  `onepole(hz)` (the parameter is `freq`); `ignitorParam`'s incomplete slot list; `door-shapes.md`'s retired limiter
  order; the oscillator defaults "copies of" an `audio_be` file that no longer exists.
- **A2. One home for a default.** Literals where a constant exists or should: the source defaults (in up to five places;
  the engine's `voicesDefault` 7.0 against 8.0 everywhere else, unreachable today), the filter `q` 0.707, `passes`,
  the script phaser's center and sweep, the distort and drive nodes' 0.5, `Compressor`, `Ducking` and `Phaser`
  literals. The two defaults-sync specs that compare each constant with itself compare the real copies instead.
- **A3. sprudel writes the source slot keys through `IgnitorDsl.Slots`**, not as string literals, so a rename on the
  Ignitor side cannot disconnect a door silently.
- **A4. sprudel's `phaser.*`, `body.*` and `vowel.*` accessors read the slots**, as the delay, reverb, compressor and
  duck accessors do (a `katp` write changes the sound, the accessor did not see it).
- **A5. The recorded exceptions in `/dsl-design` §4** gain what is deliberate but was written only in a KDoc: the
  distort's two laws (D2), the envelope's slot and node defaults (no door reaches the node's), the curve calls (sprudel
  keeps the current curve on an omitted stage, the Ignitor builds afresh), `resetSamples` / `flankSamples` (a time in
  samples, named so), `eq` meaning equality in sprudel (no sprudel equalizer door), `comp` (a sprudel alias), the stage
  data classes' own parameter order (no door exposes it), material and vowel as a number.

### B. For the maintainer: names, scales, defaults that a user would trip on

Listed with a recommendation each; **decided 2026-11-06, the maintainer took every recommendation:** B1 rename to
`release` (slot `duck.release`, every door); B2 rate first everywhere (sprudel `tremolo(rate, depth, shape)`); B3 an
unset tremolo rate is 5 Hz on every surface (one constant); B4 the phaser `floor` defaults to 1 everywhere (the voice
phaser too); B5 a one-knob door's parameter takes the knob's word (`gain(gain)`, `pan(pan)`, ...); B6 every source
answers the pattern's slots (the noise doors and the plucks opened); B7 `unison(pan)` removed (stereo width is its own
future ticket); B8 `sndSquare(duty)` added and the plain `snd*` doors' dead `params` removed. B9 is left to the audit's
judgement, each item fixed or recorded. The songs, the jingles and the tutorials follow every change.

- **B1. The duck's `attack` is its release** (KLANG-00290 (engine follow ups) item 22): rename to `release`, slot
  `duck.release`, on every door.
- **B2. The tremolo's order:** sprudel `tremolo(depth, rate, shape)`, both Ignitor doors `tremolo(rate, depth, ...)`.
- **B3. The tremolo's unset rate:** the slot's 0 (a static dip, no wobble) against the node's 5 Hz; no constant.
- **B4. The phaser's `floor`:** 0 on a voice (a crossfade), `PHASER_FLOOR` 1 on a bus (additive); equal numbers sound
  different.
- **B5. `amount` beyond distort:** sprudel's `gain`, `pregain`, `pan`, `velocity`, `legato` / `clip`, `solo`,
  `density`, `analogSpread`, `transpose` take `amount`; the Katalyst says `gain(gain)`; the rule keeps `amount` for the
  distort drive.
- **B6. Sealed and open sources:** `Ign.square()` and `Ign.supersaw()` answer the pattern's slots, the noise doors and
  both plucks seal theirs (`unison(4)` on a custom superpluck does nothing); no reason recorded.
- **B7. `unison(pan)` writes `panSpread`, which nothing reads** (the tutorial says so).
- **B8. The pulses:** `sndPulze(duty)` is the aliased raw pulse; the clean PWM pulse (`square` / `pulse`) reads the same
  `duty` slot but `sndSquare` has no `duty`; the plain `snd*` doors' `params` argument is dead (its value ignored).
- **Left as is (review of A, 2026-11-06):** the engine's `analogSpreadDefault` (1.0) is a literal twin of the
  `SuperSaw` node's `analogSpread` default with no slot and no constant, so no guard covers it; `Ducking`'s own
  constructor default depth (0.5) differs from `DUCK_DEPTH` (0.0), but the duck writer always hands a depth.
- **B9. Smaller words:** the bus fader `gain.gain` with no sprudel door under sprudel's voice-`gain` word; sprudel `penv`
  with no long name beside the Ignitor's `pitchEnvelope` (as `lowpass` sits beside `lpf`); the flat slots `octaves` and
  `rate`; no sprudel door for `zawtooth`, `impulse`, `perlin`, `berlin`; `sndDust` without `bipolar`; `sndNoise`
  against its colour siblings; the builder knob `centerJitter` writing `centerJitterScale`; the node `Pulze` (wire
  `"pulze"`) being the rounded pulse while the sound `pulze` is `RawPulze`.

### The fixes

- **B1 DONE 2026-11-06:** the duck's `attack` is `release` on every door and in the engine: sprudel's
  `duck(cylinder, depth, release)` and its accessor, `duck(release = ...)` on both Katalyst doors, the stage field, the
  slot `duck.release`, `Katalyst.slot.duck.release`, `DUCK_RELEASE_SECONDS` (its KDoc now says it is the recovery),
  `Ducking.releaseSeconds` (its knob slot `KNOB_RELEASE`), `DuckSettings.releaseSeconds`, the duck writer. The wire
  schema hash moved (-209384306 to 591120935). No song or tutorial used the knob. KLANG-00290's item 22 is closed by
  it.
- **B2 and B3 DONE 2026-11-06:** sprudel's tremolo is `tremolo(rate, depth, shape)` on every form (the pattern, the
  string, the mapper, the call form), rate first as on both Ignitor doors and as `vibrato`; a bare call reinterprets
  the pattern's values as the rate, as `vibrato` does, and `depth` stays the switch. `TREMOLO_RATE_HZ` (5 Hz) is the
  slot `tremolo.rate`'s default and the Ignitor node's, one constant. The tremolo editor tool writes the call in the
  new order. The KDoc examples, the skill's reference, the specs and the benchmark song follow; no built-in song, jingle
  or tutorial called the tremolo positionally, and the corpus is identical (no song set a depth without a rate). The
  spec of an untyped receiver whose variants disagree took the FM as its example (its order difference is recorded);
  the tremolo's variants agree now.
- **B4 DONE 2026-11-06:** the voice phaser's `floor` defaults to `PHASER_FLOOR` (1.0, additive) as on a cylinder and
  at the master: the Ignitor node, the backend's two `Ignitor.phaser` helpers, the builder and script knob KDocs, the
  skill's Ignitor reference. `Phaser`'s KDoc no longer claims the floor default differs. The corpus is identical (no
  song relied on the voice default). The shimmer keeps its own floor default 0.0: it has no bus twin to agree with.
- **B5 DONE 2026-11-06:** a one-knob sprudel door's parameter takes its knob's word: `gain(gain)`, `pregain(pregain)`,
  `pan(pan)`, `velocity(velocity)` and `vel`, `legato(legato)` and `clip`, `solo(solo)`, `density(density)` and `d`;
  `transpose(interval)` (it takes semitones or an interval name, so the word says what the value is); the oscillator
  builders' `analogSpread(analogSpread)` on both doors (not `spread`, which on the same builders is the unison
  detune). Every form follows (the pattern, the string, the mapper, the object's
  invoke), with the `@param` and `@tool` lines. The time shifts and arithmetic (`late`, `early`, `add`, `sub`,
  `juxBy`) keep `amount`: no sound knob, not in the decision. `amount` is now the distort drive's word only among the
  sound doors. No song or tutorial named the parameter, so positional calls are untouched.
- **B6 DONE 2026-11-06:** every source door answers the pattern's slots where the author sets nothing: the noise
  doors (`whitenoise`, `brownnoise`, `perlin`, `berlin`, `dust`, `crackle`) and both plucks default to the node's
  `Slots.*` params on the Kotlin and the script door, as the oscillators did; an authored value stands. The slot
  defaults equal the sealed constants they replace, so nothing sounds different unless a pattern sets the slot (the
  corpus is identical). `unison(4)` now reaches a custom `superpluck`, `sndDust(0.5)` a custom `dust`. A risk the
  review found, for B9's flat slots: a raw `ignp("rate", x)` now also moves every perlin and berlin left at its
  default (a perlin used as a modulator included); no door writes `rate`, `octaves`, `persistence` or `bipolar`, so
  only a raw write reaches them, and an author who wants a fixed modulator gives it a value.
- **B7 DONE 2026-11-06:** `unison(voices, spread)` and `uni(voices, spread)`: the reserved `pan` knob, its accessor
  `unison.pan` and its unread `panSpread` key are gone (the Strudel-compatibility mapping that wrote it too). The
  `spread` KDoc says what the engine reads (the whole width, lowest copy to highest); its example no longer claims "a
  third of a semitone apart". Stereo width is KLANG-00419 (stereo width for the unison copies). One dev pattern named
  `pan`; no song or tutorial did.
- **B8 DONE 2026-11-06:** `sndSquare(duty)` writes the `duty` slot the clean (band-limited) pulse reads, as
  `sndPulze(duty)` does for the raw one; both KDocs now say which pulse is which. The plain doors `sndSine`, `sndSaw`,
  `sndTri`, `sndRamp`, `sndZamp`, `sndPink` lose their dead `params` argument (its value was ignored, only its
  structure lifted) and take none. `LangControlRestSpec`'s `fresh` rows for them became a `sndSquare(duty)` row.
  KLANG-00189 (sprudel sound doors compound) item 3 is settled by it. No song or tutorial passed an argument.
- **B9, recorded 2026-11-06:** each smaller word is recorded as it is, with its reason:
  the bus fader `gain.gain` has no sprudel door, and the Katalyst word is the stage's own (`k.gain(gain)`), no voice
  knob; sprudel keeps `penv` without a long name, because no alias is added for its own sake (`sprudel/CLAUDE.md`);
  the flat slots `octaves` and `rate` stay flat, because a rename to `perlin.*` is a shape change for the maintainer
  (the B6 risk above stands: a raw `ignp("rate", x)` reaches every perlin and berlin left at its default); no
  sprudel doors for `zawtooth`, `impulse`, `perlin`, `berlin`, because `sound("zawtooth")` and the others reach them
  by name; `sndDust` stays without `bipolar`, a raw `ignp("bipolar", 1)` reaches it; `sndNoise` keeps its name
  against its colour siblings (`sndBrown`, `sndPink`), a rename for its own sake; the builder knob `centerJitter`
  keeps writing the node field `centerJitterScale`, a wire rename for no sound; the node `Pulze` (wire `"pulze"`) is
  the rounded pulse `Ign.square` builds while the sound `pulze` is `RawPulze`, both KDocs say which pulse is which
  (B8) and a rename would move the wire hash for no sound.
- **A1 to A5 DONE 2026-11-06:**
  A1, the false KDoc: the unset `reverb.lowpass` is the engine's fixed damping (sprudel's door and `/dsl-design` §4);
  the pluck `brightness` is the loop's one-pole (every surface), `pickPosition` the burst's width and place, 0 a
  short burst at the bridge, 1 the whole string (both surfaces, from `KarplusString.excite`); `sndDust(tail)` an
  amplitude exponent; `voices` "default 8, up to 256"; `sndSuperPluck` without the colon string, default 8 strings;
  the "wire spelling" `phaserDepth` / `bodyMix` / `vowelMix` gone (`KatalystDsl`, `BusEffectDefaults`, `Phaser`,
  the filters' note); the four sprudel filters say a stage knob alone sweeps 7 semitones; "the pattern-level
  `.spread()`" names `unison(spread = ...)`, in semitones lowest copy to highest (11 sites); density is dust's
  impulse rate, crackle reads `chaos`; the distort, drive, crush and coarse overloads say "required here" and the
  wire node's default; `Slots.vibrato` names its five slots; the bus distort names the fused law and D2 (both
  Katalyst doors); `DELAY_FEEDBACK` says the echoes hold at 1; `onepole(freq)`; `ignitorParam` lists the slots the
  library writes; `door-shapes.md` marks the Master limiter order retired; the `SuperSaw` KDoc no longer names an
  `audio_be` copy.
  A2, one home: the noise slots read `CRACKLE_CHAOS_DEFAULT`, `NOISE_TILT_DEFAULT`, `BROWN_LEAK_DEFAULT`,
  `DUST_TAIL_DEFAULT`, `DUST_BIPOLAR_DEFAULT`; the engine's source fallbacks (`Ignitors`) read the constant or the
  `IgnitorDsl.Slots` default, so the unset voice count is 8 there too (was 7, unreachable from a door); the classic
  filter slots read `FILTER_Q` and `FILTER_PASSES`; the `Distort` and `Drive` nodes `DISTORT_AMOUNT`; `Compressor`
  the `COMPRESSOR_*` constants; `Ducking` `DUCK_RELEASE_SECONDS`; `Phaser` `PHASER_WET` and `PHASER_FLOOR`. The
  script doors keep their literal defaults (KSP), equal to the constants. `SuperOscDefaultsSyncSpec`,
  `WaveOscDefaultsSyncSpec` and `NoiseDefaultsSyncSpec` compared a constant with itself; they now pin the real
  copies: the node each `Ign.*` door builds against the `Slots` param, and the engine's fallbacks against the slot
  defaults.
  A3: sprudel's doors write the flat source slots through `IgnitorSlotKeys` (`sprudel/_ignitor_slot_keys.kt`), the
  names read once from `IgnitorDsl.Slots`, as the classic keys are.
  A4: the `phaser.*`, `body.*` and `vowel.*` accessors and their mapper reads read the slots (`katalystParams`), as
  delay, reverb, compressor and duck do; the voice fields are still written beside them.
  A5: `/dsl-design` §4's recorded asymmetries gain the distort's two laws, the envelope's slot and node defaults,
  the curve calls, `resetSamples` / `flankSamples`, `eq`, `comp`, the stage data classes' order and material and
  vowel as a number.

- **Step 4, the pins, DONE 2026-11-06:** every fix above carries its own spec rows (the door parity specs, the
  defaults-sync specs, the classic tail and render parity, the field accessors, the tool binding). The one comparison
  no spec made, a sprudel door against its Ignitor or Katalyst twin of the same name, is
  `DoorOrderParitySpec` (sprudel jvmTest): for every pair in the two generated registries (the twin on `IgnitorDsl`,
  `KatalystBuilder`, `KatalystKnobDsl`, or a builder whose knob shares the door's name, `adsr`, `range`, `analog`;
  51 pairs and more), every
  parameter both take sits at the same position, so a positional call means the same on both. It pins the order, not
  the words (those are the parity specs'). The recorded exceptions are keyed by door and twin with the twin order
  they expect (today the FM's on the Ignitor): one that drifts fails, and so does one that stops differing.
  Mutations: a swapped sprudel door, a drifted FM order and a stale waiver, all KILLED.

### Checked and consistent

delay, reverb (the size divided by 10 in one place), compressor, limiter, body, vowel, the bus phaser, the duck apart
from `attack`, the master (the same chain and doors as a cylinder), `analog`, the unison and pluck orders, the noise
knob names, the sample slot keys, `ignp` / `ignitorParam`, `Ign.slot` and `Ignitor.slot`; the recorded differences
(the eq expression refusal, the Katalyst `lookahead`, `cap` and `floor` on the builders, FM's positional order, Q29's
`duty(width)` against `sndPulze(duty)`, the sample slots Kotlin-only).

## Acceptance

- Every concept that appears on more than one surface has one name, one scale and one meaning there, or a recorded
  exception with its reason.
- The parity specs cover the gaps that were found.

## Appendix: the inventory (2026-11-06, verbatim from the three passes)

### KLANG-00417 parity inventory: the bus (Katalyst) family

Read from the code on 2026-11-06 (main at 0cf2f5f83). Read-only; nothing edited, no Gradle.

Abbreviations:
- **KK** = Katalyst Kotlin door, `audio_bridge/src/commonMain/kotlin/KatalystBuilders.kt` (`KatalystBuilder`), stage data classes in `KatalystDsl.kt` (`KatalystStageDsl`), slots in `KatalystDslSlots.kt`.
- **KS** = Katalyst KlangScript door, `klangscript-libs/src/commonMain/kotlin/stdlib/KatalystBuilders.kt` (`script*` variants), slots `KlangScriptKatalystSlots.kt` (same instances as `KatalystDsl.Slots`).
- **SP** = sprudel cylinder door, `sprudel/src/commonMain/kotlin/lang/*.kt`.
- **IG** = Ignitor per-voice twin (Kotlin `audio_bridge/.../IgnitorDsl.kt`, script `klangscript-libs/.../KlangScriptIgnitorExtensions.kt`).
- **BED** = `audio_bridge/src/commonMain/kotlin/constants/BusEffectDefaults.kt`; **MLD** = `MasterLimiterDefaults.kt`; **FD** = `FilterDefaults.kt`.
- "bare default" = what a KK/KS stage carries for an omitted knob (the touched constant). "classic slot default" = the untouched value in `KatalystDslSlots.kt` (zero / `SLOT_UNSET` / constant / unity), deliberately different, documented once in `KatalystDsl.classic` KDoc (KatalystDsl.kt:122-185).
- Master: `master(Katalyst(...))` (SP `lang_master.kt:56`, `:95`) runs the SAME `KatalystDsl` at the output; every stage below is available there with the same knobs. Two documented differences: no `Param` slot is filled at the output (katp reaches cylinders only) and `duck` is inert (lang_master.kt:40-43, KatalystDsl.kt:45-46).

---

#### delay

| knob | KK (name, pos) | KS | SP `delay(...)` (pos) | slot | IG twin | unit/scale | default (home) |
|---|---|---|---|---|---|---|---|
| wet | `wet` #0 | `wet` #0 | `wet` #0 | `delay.wet` | none | send amount 0..1 | 0.25 `DELAY_WET` (BED:65); classic slot 0.0 |
| time | `time` #1 | `time` #1 | `time` #1 | `delay.time` | none | seconds | 0.25 `DELAY_TIME_SECONDS` (BED:68); classic 0.0 |
| feedback | `feedback` #2 | `feedback` #2 | `feedback` #2 | `delay.feedback` | none | linear, >1 allowed | 0.3 `DELAY_FEEDBACK` (BED:71); classic 0.0 |
| cap | builder `cap(...)` in `configure` (KatalystBuilders.kt:388) | builder `cap` (KS:1048) | `cap` #3 | `delay.cap` | none | level | 1.0 `DELAY_CAP` (BED:74); classic 1.0 |

Refs: KK KatalystBuilders.kt:111-118, data class KatalystDsl.kt:357-362; KS KatalystBuilders.kt:270-280; SP lang_effects_delay.kt:118-135, fill :41-48 (every setter fills, no name knob). Backend: `KatalystDelayWriter` KatalystSlotWriters.kt:108-159 (gate on wet via `stageAskedFor`, values raw).

#### reverb

| knob | KK | KS | SP `reverb(...)` | slot | IG | unit/scale | default |
|---|---|---|---|---|---|---|---|
| wet | `wet` #0 | `wet` #0 | `wet` #0 | `reverb.wet` | none | send 0..1 | 0.25 `REVERB_WET` (BED:79); classic 0.0 |
| size | `size` #1 | `size` #1 | `size` #1 | `reverb.size` | none | authored 0..10, /10 in ONE place `Reverb.normalizeSize` (audio_be effects/Reverb.kt:560) called from `KatalystReverbWriter` (KatalystSlotWriters.kt:205) | 5.0 `REVERB_SIZE` (BED:85); classic 0.0 |
| lowpass | `lowpass` #2 (nullable) | `lowpass` #2 | `lowpass` #2 | `reverb.lowpass` | none | Hz; unset = `DEFAULT_DAMP` 0.5 (Reverb.kt:599, :398-408) | null / `SLOT_UNSET`, never filled |

Refs: KK KatalystBuilders.kt:124-128, KatalystDsl.kt:379-384; KS :293-303; SP lang_effects_reverb.kt:147-162, fill :41-46.

#### phaser

| knob | KK | KS | SP `phaser(...)` | slot | IG `.phaser(...)` | unit | bus default | IG default |
|---|---|---|---|---|---|---|---|---|
| wet | `wet` #0 | `wet` #0 | `wet` #0 | `phaser.wet` | `wet` #0, REQUIRED | 0..1, gate 0.01 | 0.0 `PHASER_WET` (BED:93) | node 0.5 literal (IgnitorDsl.kt:2272), unreachable via doors |
| rate | `rate` #1 | `rate` #1 | `rate` #1 | `phaser.rate` | `rate` #1, REQUIRED | Hz | 0.0 `PHASER_RATE_HZ` (BED:90) | node 0.5 literal (IgnitorDsl.kt:2271), unreachable via doors |
| center | `center` #2 | `center` #2 | `center` #2 | `phaser.center` | `center` #2 | Hz | 1000 `PHASER_CENTER_HZ` | same constant (IgnitorDsl.kt:3221); KS literal 1000.0 (KlangScriptIgnitorExtensions.kt:552) |
| sweep | `sweep` #3 | `sweep` #3 | `sweep` #3 | `phaser.sweep` | `sweep` #3 | Hz | 1000 `PHASER_SWEEP_HZ` | same constant |
| floor | builder `floor` (KatalystBuilders.kt:395) | builder `floor` (KS:1054) | `floor` #4 | `phaser.floor` | builder `floor` (EffectBuilders.kt:246) | min dry coeff 0..1, same `WetDryMix` law p=2 | **1.0** `PHASER_FLOOR` (BED:102) | **0.0 literal** (IgnitorDsl.kt:2275) |

Refs: KK KatalystBuilders.kt:131-141, data class KatalystDsl.kt:399-405 (ctor order rate, wet, ...); KS :318-330; SP lang_effects_modulation.kt:159-178, fill :61-88; IG Kotlin IgnitorDsl.kt:3217-3227, script KlangScriptIgnitorExtensions.kt:548-563. Backend law: effects/Phaser.kt:14-32, ignitor/IgnitorEffects.kt:500-503, KatalystPhaserWriter KatalystSlotWriters.kt:270-288.

#### compressor

| knob | KK | KS | SP `compressor(...)` / `comp(...)` | slot | IG | unit | default |
|---|---|---|---|---|---|---|---|
| threshold | #0 | #0 | #0 | `compressor.threshold` | none | dBFS | -20 `COMPRESSOR_THRESHOLD_DB` (BED:107) |
| ratio | #1 | #1 | #1 | `compressor.ratio` | none | n:1 | 4 (BED:110) |
| knee | #2 | #2 | #2 | `compressor.knee` | none | dB | 6 (BED:113) |
| attack | #3 | #3 | #3 | `compressor.attack` | none | s, closing | 0.003 (BED:116) |
| release | #4 | #4 | #4 | `compressor.release` | none | s, opening | 0.1 (BED:119) |
| lookahead | #5, plain Double | #5, plain Double | absent (recorded) | none (not a slot) | none | s, <=0.05 | 0 (BED:125) |

All five classic slots `SLOT_UNSET` (KatalystDslSlots.kt:80-86). Refs: KK :144-160, KatalystDsl.kt:435-442; KS :355-370; SP lang_dynamics_compressor.kt:188-200, fill :37-43; engine fallback KatalystSlotWriters.kt:363-367 (same constants).

#### limiter (Katalyst only; a compressor preset)

| knob | KK/KS pos | default (MLD) |
|---|---|---|
| threshold | #0 | -1 `LIMITER_THRESHOLD_DB` (MLD:21) |
| ratio | #1 | 20 (MLD:24) |
| knee | #2 | 2 (MLD:32) |
| attack | #3 | 0.001 `AUTHORED_LIMITER_ATTACK_SECONDS` (MLD:53) |
| release | #4 | 0.1 (MLD:35) |
| lookahead | #5 | 0 `AUTHORED_LIMITER_LOOKAHEAD_SECONDS` (MLD:47) |

KK :167-181, KS :404-419. No sprudel twin (recorded, door-shapes.md:33). Appends a `compressor` stage, so an unset slot falls back to COMPRESSOR_* not LIMITER_* (KS KDoc :378-381, true).

#### duck

| knob | KK | KS | SP `duck(...)` | slot | IG | unit | default |
|---|---|---|---|---|---|---|---|
| cylinder | #0 | #0 | #0 (name knob, only it fills) | `duck.cylinder` | none | cylinder index | `SLOT_UNSET` |
| depth | #1 | #1 | #1 | `duck.depth` | none | 0..1 | 0.0 `DUCK_DEPTH` (BED:130) |
| attack | #2 | #2 | #2 | `duck.attack` | none | s; **actually the RELEASE / recovery** (effects/Ducking.kt:26-34) | 0.1 `DUCK_ATTACK_SECONDS` (BED:133) |

Refs: KK :196-200, KatalystDsl.kt:493-497; KS :468-477; SP lang_dynamics_cylinder.kt:353-363, fill :258-263; engine fallback KatalystSlotWriters.kt:605.

#### body

| knob | KK | KS | SP `body(...)` | slot | IG | unit | default |
|---|---|---|---|---|---|---|---|
| wet | #0 | #0 | #0 | `body.wet` | none | insert mix 0..1 | 0.5 `BODY_WET` (BED:138); classic `SLOT_UNSET` |
| material | #1 (index knob, or String overload :88) | #1 (name, index or slot) | #1 (NAME only; a number is read as the string "5.0", index 0) | `body.material` (index into `BodyMaterials`) | none | catalogue index | `SLOT_UNSET` (off) |
| floor | builder `floor` (:374) | builder `floor` (KS:1029) | #2 | `body.floor` | none | min dry 0..1 | 0.4 `BODY_FLOOR` (BED:151) |

Refs: KK :79-92, KatalystDsl.kt:312-316 (ctor order material, wet, floor); KS :219-227; SP lang_effects_body.kt:178-191, fill :37-49, material conversion :58-74.

#### vowel

Same shape as body: KK `vowel(wet, vowel, configure{floor})` :95-108; KS :247-255; SP `vowel(wet, vowel, floor)` lang_effects_vowel.kt:169; slots `vowel.wet` (`SLOT_UNSET` classic), `vowel.vowel` (index into `VowelBands`), `vowel.floor`; defaults `VOWEL_WET` 0.5 (BED:156), `VOWEL_FLOOR` 0.2 (BED:164). No IG twin.

#### distort (bus) vs distort (voice)

| knob | KK `distort` | KS | SP `distort(...)` (VOICE) | slot | IG `.distort(...)` | unit | default |
|---|---|---|---|---|---|---|---|
| amount | #0 | #0 | #0 | none on the bus; voice slot `distort.amount` (IgnitorDslClassic.kt:124) | #0, REQUIRED | drive `10^(amount*1.2)` | bus 0.5 `DISTORT_AMOUNT` (BED:173); IG nodes literal 0.5 (IgnitorDsl.kt:2145, :2213) |
| shape | #1, NAME only, fixed with chain | #1 name only | #1 name | voice slot `distort.shape` | #1 name / index / slot | catalogue | `soft` |

Law: the bus stage = `DistortionCore`, no soft cap (KatalystDsl.kt:453-455), the same as classic's `IgnitorDsl.Distort` node that sprudel's voice `distort` writes; the Ignitor DOOR `x.distort(...)` builds `Shape(Drive(...))` with a `softCap` (IgnitorDsl.kt:3185; audio_be ignitor/IgnitorEffects.kt:42-50, :62-71), kept by decision D2 (IgnitorDsl.kt:2186-2195). No SP cylinder twin (recorded, door-shapes.md:38).

#### eq

| knob | KK `eq{ band / tap }` | KS | SP | slot | IG `.eq{ band / tap }` | unit | default |
|---|---|---|---|---|---|---|---|
| band(freq, q, db) | `KatalystEqBuilder.band` EffectBuilders.kt:311 | KS EffectBuilders.kt:331 | none (`eq` in sprudel is the equality compare, lang_arithmetic_compare.kt:150) | none in classic; `Kat.param` only | `EqBuilder.band` EffectBuilders.kt:292 | Hz, Q, dB | q 0.707 `FILTER_Q`, db 0 `EQ_BAND_DB` (FD:18,24); KS literals (recorded FD:12-13) |
| tap(freq, q, gain) | :321 | :341 | none | none | :296 | Hz, Q, linear | gain 1 `EQ_TAP_GAIN` (FD:27) |

Sections are the same `IgnitorDsl.EqSection` on voice and bus (KatalystDsl.kt:500-527). Kat expressions refused on a section (recorded Q49, EffectBuilders.kt:300-307).

#### gain (fader) / pregain

| concept | KK | KS | SP | slot | IG | unit | default |
|---|---|---|---|---|---|---|---|
| bus group fader | `gain(gain)` :210 | `gain(gain = 1.0)` :504 | none (only `katp("gain.gain", x)`, lang_katalyst.kt:190-205) | `gain.gain` (KatalystDslSlots.kt:90) | n/a | linear | 1.0 identity, literal by design |
| voice level | n/a | n/a | `gain(amount)` lang_dynamics_level.kt:66 (voice) | voice field | `times` | linear | none |
| voice pregain | n/a | n/a | `pregain(amount)` :199 | flat `pregain` slot | `pregain()` | linear | 1 |

---

#### GAPS

1. **`duck.attack` is a release, named and documented as an attack.** Backend: `Ducking` has an instantaneous attack and the knob sets the release coefficient, "Named 'attack' for strudel compatibility" (audio_be effects/Ducking.kt:26-34, :42-43; KatalystDuckEffect.kt:67). SP says so (lang_dynamics_cylinder.kt:322-323, :346). KK/KS/constants say the opposite: "how fast the duck closes" (KatalystDsl.kt:490, KS KatalystBuilders.kt:465, BED:132). Same word means "closing time" on `compressor`/`limiter` (KS:345, :393) and every envelope. **Severity: shape gap a user trips on + false KDoc on three sites.** Fix: by §5 (one word, one meaning) rename to `release` on every surface and the slot `duck.release` (shape change, maintainer first; Strudel compat is the only stated reason). At minimum, today: correct the three KDocs to "recovery time after the source stops".

2. **Phaser `floor` default differs between voice and bus: 0.0 vs 1.0.** IG node `floor = Constant(0.0)` literal (IgnitorDsl.kt:2275, KDoc :2262) vs `PHASER_FLOOR` 1.0 (BED:102) on KK/KS/SP. Same `WetDryMix` law, so `Ign...phaser(0.3, 0.5)` is a crossfade (dry ~0.79) and `k.phaser(0.3, 0.5)` / `.phaser(0.3, 0.5)` is additive (dry 1.0). Recorded only in KDocs (effects/Phaser.kt:39-41, IgnitorDsl.kt:2262-2265), not in dsl-design §4's list of deliberate asymmetries, and the voice value is a literal, not a constant (§4: "never a literal default per host"). **Severity: user trip** (moving a phaser between voice and bus changes the sound at equal numbers). Fix: maintainer picks; either one default (the bus's 1.0, additive, matches sprudel and the touched constant) or record it in §4 with its reason and give it a constant (e.g. `PHASER_VOICE_FLOOR`) in `constants/`.

3. **Phaser `wet`/`rate` IG node defaults 0.5/0.5 vs bus 0/0** (IgnitorDsl.kt:2271-2272 vs BED:90,93). Unreachable through either IG door (both REQUIRED, IgnitorDsl.kt:3218-3219, KlangScriptIgnitorExtensions.kt:550-551), so cosmetic; but the KDoc "Default: 0.5" (IgnitorDsl.kt:2260) advertises it and effects/Phaser.kt:41 claims "only the floor default differs", which is **false** as written. Fix: drop the defaults from the node (required like the doors) or read the constants; correct Phaser.kt:41.

4. **Bus fader slot `gain.gain` sits in the namespace of sprudel's VOICE `gain`.** D4 (§4): "a slot is `<door>.<param>`; the namespace is the sprudel door's name". Sprudel `gain(amount)` (lang_dynamics_level.kt:66) writes a voice field, not `gain.gain`, and there is no sprudel door for the cylinder fader (KatalystDsl.kt:235, :549 recorded "no sprudel door yet"). A user reading `gain.gain` next to `.gain(x)` will think they are one knob. **Severity: user trip (moderate).** Fix: either a sprudel cylinder door for the fader under its own word (and the slot renamed to it, e.g. `fader`) or record the exception; maintainer.

5. **Parameter word of the level doors: SP `gain(amount)` / `pregain(amount)` vs KK/KS `gain(gain)`.** The units rule says "`amount` is the distort drive only" (dsl-design §4, units paragraph). Only bites on a named call (`gain(amount = 0.5)` vs `k.gain(gain = 0.5)`). **Cosmetic.** Fix: sprudel one-knob doors take the knob word (`gain(gain)`? or a neutral `value`), maintainer, together with `pan`/`velocity` (outside this family).

6. **Distort: one word, two laws on the voice side.** Bus `k.distort` and sprudel's voice `distort` (classic's `IgnitorDsl.Distort`) are `DistortionCore`, no soft cap; the Ignitor DOOR `x.distort(...)` is `Shape(Drive(...))` with `softCap` (IgnitorDsl.kt:3185; IgnitorEffects.kt:62-71). Deliberate and recorded (decision D2 option A, IgnitorDsl.kt:2186-2195). But KatalystDsl.kt:445-446 says "the Ignitor's `distort` at the bus position, one DSP and one word on both hosts" and KS:422 "the voice's `distort` ... same knobs, same scales, same shapes" without naming WHICH voice law. **Severity: deliberate and recorded; KDoc misleading.** Fix: KDoc names the law ("classic's fused distort; the Ignitor `distort` door adds a soft cap, D2").

7. **False KDoc: "wire spelling on the voice: `phaserDepth` / `bodyMix` / `vowelMix`"** (KatalystDsl.kt:391; BED:92, :137, :155; audio_be filters/LowPassHighPassFilters.kt:232; effects/Phaser.kt:25-26 uses `phaserDepth`/`phaserFloor` as knob names). Those are sprudel-internal `SprudelVoiceData` fields (SprudelVoiceData.kt:233, :250, :645) that no longer cross the wire (SP's own KDoc says so, lang_effects_modulation.kt:49-51, lang_effects_body.kt:32-35). **Cosmetic, false claim.** Fix: delete the parentheticals; optionally rename the sprudel fields to the slot words (`phaserWet`, `bodyWet`, `vowelWet`) for §5.

8. **False KDoc: unset `reverb.lowpass` "means no damping".** lang_effects_reverb.kt:32 and dsl-design SKILL.md:373-374 ("unset means off ... inventing a constant for it would damp every room"). The engine applies `DEFAULT_DAMP` 0.5 when unset (effects/Reverb.kt:398-408, :596-599: "equivalent to a lowpass at half the Nyquist"). KatalystDsl.kt:373-376 and KS:290-291 state it correctly. **Cosmetic, false claim** (in the rule's own home). Fix: "unset means the engine's fixed damping, a sample-rate relation with no wire constant, so there is nothing to fill".

9. **Sprudel accessors of phaser/body/vowel read voice FIELDS, the other bus doors read SLOTS.** `phaser.wet`/`.floor` read `phaserDepth`/`phaserFloor` (lang_effects_modulation.kt:222, :234), `body.wet`/`.floor` and `vowel.wet`/`.floor` read fields (lang_effects_body.kt:229-233, lang_effects_vowel.kt:220-224); delay, reverb, compressor, duck read `katalystParams`. A `katp("phaser.wet", x)` therefore changes the sound but not what `phaser.wet` reads. The duck had exactly this and was moved to slots in 5b-3 (lang_dynamics_cylinder.kt:244-247). **Minor, a user trip only with `katp`.** Fix: read the slots, as the duck does.

10. **Material/vowel as a number: KK/KS take an index, SP does not.** KS `body(material = 3)` is index 3 (KS:207-210); SP `body(material = 5)` reads the string "5.0", index 0, off (lang_effects_body.kt:58-60, recorded there). **Cosmetic, recorded.** Fix (optional): SP converts through the same `catalogueIndex` so a number means the index on both.

11. **`eq` means equality in sprudel.** `SprudelPattern.eq(other)` is the 0/1 compare (lang_arithmetic_compare.kt:150); on IG/KK/KS `eq` is the equalizer. No sprudel cylinder EQ door exists (only via `katalyst(Katalyst(k => k.eq(...)))` plus `Kat.param`), and the word is taken if one is ever wanted. **Cosmetic now; blocks a future door.** Fix: record; decide the word only if a sprudel EQ door is wanted.

12. **Alias only on one surface: SP `comp` = `compressor`** (lang_dynamics_compressor.kt:272, :290). KK/KS have no `comp`. **Cosmetic.** Fix: keep as sprudel heritage or add the Katalyst alias; one note in door-shapes.md.

13. **Stage data-class order differs from door order:** `KatalystStageDsl.Phaser(rate, wet, ...)` (KatalystDsl.kt:399-405), `Body(material, wet, floor)` (:312-316), `Vowel(vowel, wet, floor)` (:335-339), `IgnitorDsl.Phaser(inner, rate, wet, ...)` (IgnitorDsl.kt:2269-2276), against the wet-first doors. Only engine-level positional construction is affected. **Cosmetic.** Fix: reorder the constructors to the door order (wire field order permitting), or leave.

14. **Literal DSP defaults beside the constants:** `Compressor(thresholdDb = -20.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.003, releaseSeconds = 0.1)` (audio_be effects/Compressor.kt:61-65), `Ducking(attackSeconds = 0.1)` and its fallback (Ducking.kt:39, :57), `Phaser.depth = 0.0`, `floor = 1.0` (Phaser.kt:85, :102), IG `Drive`/`Distort` amount `Constant(0.5)` (IgnitorDsl.kt:2145, :2213, BED:170-172 says they are "the same 0.5"). Values all match today. **Cosmetic.** Fix: read the constants (§4 "live in ONE place").

15. **Minor KDoc drift:** BED:70 "at or above 1.0 the delay self-oscillates" vs KatalystDsl.kt:347-350 "at exactly 1.0 the echoes neither fade nor grow". IgnitorEffects.kt:53, :72, :90 say `amount` "Default: 0.0 (bypass)" where the wire node default is 0.5. door-shapes.md:34 and :99-102 still describe the retired Master `limiter(threshold, ratio, knee, attack, lookahead, release)` order, which is NOT the current Katalyst order (`..., release, lookahead`). **Cosmetic.**

#### Checked, consistent

- **delay**: names, order (wet, time, feedback; cap on builder vs SP #3, recorded rule door-shapes "secondary knobs on the builder", sprudel has no builder), seconds, defaults from BED on KK, KS, SP and the engine fallback; any knob names the stage.
- **reverb**: names, order, 0..10 size scale converted once (`Reverb.normalizeSize`), defaults, lowpass in Hz and never filled; master identical.
- **compressor**: names, order, units (dB, s), defaults from BED on all four (KK, KS, SP fill, engine fallback); `lookahead` absent from SP recorded (ticket, §4).
- **limiter**: Katalyst-only preset, recorded; KK and KS identical order and constants.
- **duck**: names, order, `depth` 0..1, defaults, name-knob fill (cylinder only); only the `attack` meaning is a gap (1).
- **body / vowel**: names, wet-first order, `floor` on builder (recorded), index slots via one `indexOf`, defaults, name-knob fill.
- **phaser (bus)**: KK, KS, SP and slots agree in name, order, Hz units and defaults; the gaps are against the Ignitor (2, 3).
- **eq**: same sections, words and defaults on voice and bus; expression refusal recorded (Q49); KS literals recorded (FD:12-13).
- **wet as send (delay, reverb) vs wet as mix (body, vowel, phaser)**: same word, documented on the SP doors (lang_effects_body.kt:124-127).
- **master**: same chain type and doors as a cylinder; no-fill and inert duck documented.
- **Slots**: KK and KS slot handles are the same instances (KlangScriptKatalystSlots.kt:83-217); 27 knobs, the count in KatalystDsl.kt:70 is right.

### KLANG-00417 parity inventory: per-voice shaping and modulation

Read from the code on 2026-11-06 (tree at `0cf2f5f83` plus the working-tree state). Read-only: no repo file edited,
no Gradle, no state-changing git.

Abbreviations for paths:

- `SP/` = `sprudel/src/commonMain/kotlin/lang/`
- `SW` = `sprudel/src/commonMain/kotlin/_classic_slot_params.kt` (the one sprudel-to-slot writer, `classicSlotParams`)
- `AB/` = `audio_bridge/src/commonMain/kotlin/` (`AB/C/` its `constants/`)
- `IC` = `AB/IgnitorDslClassic.kt` (slot groups and `classic()`)
- `ID` = `AB/IgnitorDsl.kt` (nodes, `Slots`, Kotlin doors)
- `EB` = `AB/EffectBuilders.kt` (Ignitor builders, both doors)
- `KS/` = `klangscript-libs/src/commonMain/kotlin/stdlib/` (`KSX` = `KS/KlangScriptIgnitorExtensions.kt`)
- `BE/` = `audio_be/src/commonMain/kotlin/`

"Slot default" is the `classic()` slot's default (what a sprudel door leaves unset reads). "Node default" is the
Ignitor data-class field default (what a hand-built node, or a door that leaves the field alone, gets).

---

#### 1. Filters: lpf / hpf / bpf / notch

| knob | sprudel door (positional) | slot key | Ignitor Kotlin | Ignitor KlangScript | Katalyst twin | unit / scale | default (where) |
|---|---|---|---|---|---|---|---|
| cutoff | `lpf(freq, ...)` pos 0; `hpf`, `bpf`, `notch` same; long names `lowpass`, `highpass`, `bandpass` | `lpf.freq`, `hpf.freq`, `bpf.freq`, `notch.freq` | `lowpass(freq, q, configure)` pos 0 (required); `highpass`, `bandpass`, `notch` same | same, pos 0 (required) | `eq` sections `eqLowpass(freq, q)` etc. (no filter stage of its own) | Hz | slot `SLOT_UNSET` = no filter (`IC:75,91`); node 2000 / 200 / 1000 / 1000 literals (`ID:1594,1704,1753,1798`); eq sections the same literals (`ID:1862...`) |
| resonance | `q` pos 1 | `<door>.q` | `q` pos 1, default `FILTER_Q` (`ID:2948`) | `q` pos 1, default literal `0.707` (`KSX:296,311,337,398`) | eq section `q` = `FILTER_Q` | Q, 0.707 Butterworth | slot literal `0.707` (`IC:76,92`); node `FILTER_Q` (`AB/C/FilterDefaults.kt:18`) |
| cascade | `passes` pos 2 (lpf, hpf only) | `lpf.passes`, `hpf.passes` | builder `passes(n)` (FilterBuilder only, `EB:143`) | builder `passes` (`KS/EffectBuilders.kt:124`) | none | count, rounded, 1..16 (`coercePasses`, both: `SW:259`, `ID` `coercedPasses`) | slot literal `1.0` (`IC:77`); node/builder `FILTER_PASSES` (`EB:110`) |
| env depth | `env` pos 3 (lpf/hpf), pos 2 (bpf/notch) | `<door>.env` | builder `env(semitones)` (`EB:152,177`) | builder `env` (`KS/EffectBuilders.kt:150,191`) | none | semitones, `freq * 2^(env/12 * e)` | slot `SLOT_UNSET`, slot-layer fill to `FILTER_ENV_DEPTH_SEMITONES` = 7 when a stage is written (`BE/ignitor/IgnitorDslRuntime.kt:1006-1026`); door fill the same constant (`ID` `fillFilterEnvelope`); node 0 |
| env stages | `attack, decay, sustain, release` pos 4..7 (lpf/hpf), 3..6 (bpf/notch), one at a time | `<door>.attack` ... `<door>.release` | builder `adsr(attack, decay, sustain, release, configure)`, all four at once (`EB:155,180`) | same (`KS/EffectBuilders.kt:163,199`) | none | s, s, share 0..1, s | `FILTER_ENV_*` 0.01 / 0.1 / 1.0 / 0.1 on slot, node and door fill (`AB/C/FilterEnvelopeDefaults.kt:35-44`, `IC:79-82`) |
| env curves | `lpfCurves(attack, decay, release)` etc., setter only, omitted or unknown keeps current | `lpfCurves.attack` ... (`ModEnvelopeCurvesSlots`) | inside `adsr(..., e => e.curves(a, d, r))` on `ModAdsrBuilder` (`EB:81-99`), omitted = `MOD_ENV_CURVE` | same (`KS/EffectBuilders.kt:98`) | none | AdsrCurves index / name | `MOD_ENV_CURVE` exponential everywhere (`AB/C/EnvelopeDefaults.kt:48`, `IC:202-204`) |
| analog / humanize | the voice-wide `analog(character)` door (`SP/lang_synthesis_ignitorparam.kt:208`), routed to every filter by `classic()` with `humanize = true` (`IC:449-505`) | flat `analog` | builder `analog(character)`, `humanize(on)` | same | none | character 0..10 | `Slots.analog` 0.0 |

Refs, sprudel: `SP/lang_filters_lpf.kt:150-176`, `SP/lang_filters_hpf.kt:146`, `SP/lang_filters_bpf.kt:132`,
`SP/lang_filters_notch.kt:137`; writer `SW:250-277` (envelope written only when the cutoff is set and one of the five
envelope knobs is). Ignitor Kotlin: `ID:2946,2973,3003,3029`; script `KSX:293,308,334,395`.

#### 2. Amplitude envelope

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | Katalyst | unit | default |
|---|---|---|---|---|---|---|---|
| attack / decay / sustain / release | `adsr(attack, decay, sustain, release)` pos 0..3, each optional (`SP/lang_dynamics_adsr.kt:98`) | `adsr.attack` ... `adsr.release` | `adsr(attack, decay, sustain, release, configure)` all four REQUIRED (`ID:3142`) | same, required (`KSX:423`) | none (compressor attack/release are a different concept) | s, s, level 0..1, s | slot `VOICE_ADSR_*` 0.01 / 0.1 / **1.0** / **0.05** (`AB/C/EnvelopeDefaults.kt:77-93`, `IC:164-167`); node 0.01 / 0.1 / **0.7** (`ADSR_SUSTAIN_LEVEL`) / **0.3**, attack, decay, release LITERALS (`ID:2027-2030`) |
| curves | `adsrCurves(attack, decay, release)` setter (`SP/lang_dynamics_adsr.kt:266`) | `adsrCurves.*` | `adsr(..., e => e.curves(a, d, r))` (`EB:41-59`) | `curves` (`KS/EffectBuilders.kt:73`) | none | curve index / name | `AdsrCurve.Default` exponential both sides |
| switch | `adsrOn(flag)` / `adsrOff()` (`SP/lang_dynamics_adsr.kt:407,448`) | `adsr.on` | none (node field only, recorded, `ID` `Adsr.on` KDoc) | none | none | flag | 1.0 |
| declick | none (classic uses constant `ENV_DECLICK_SECONDS`) | none (flat `declick` slot exists, `Slots.declick`) | builder `declick(seconds)` (`EB:62`) | `declick` (`KS/EffectBuilders.kt:89`) | none | s | 0 on authored adsr, 0.001 constant in classic |

#### 3. Levels: gain, velocity, pan, pregain (postgain does not exist)

| knob | sprudel | slot / wire | Ignitor Kotlin | Ignitor KlangScript | Katalyst twin | unit | default |
|---|---|---|---|---|---|---|---|
| gain | `gain(amount)` (`SP/lang_dynamics_level.kt:66`) | wire field `VoiceData.gain` (not a slot) | none (arithmetic `mul`) | none (`mul`) | `k.gain(gain = 1.0)` (`AB/KatalystBuilders.kt:210`, script `KS/KatalystBuilders.kt:503`), slot `gain.gain` (`AB/KatalystDslSlots.kt:90`) | linear, 1 unity | unset reads 1.0 (`BE/voices/VoiceFactory.kt:136`) |
| velocity | `velocity(amount)` / `vel` (`SP/lang_dynamics_level.kt:396,480`) | folded into `gain` before the wire | none | none | none | linear multiplier | none |
| pan | `pan(amount)` (`SP/lang_dynamics_level.kt:293`) | wire field `pan` | none | none | none | 0 left, 0.5 centre, 1 right, coerced (`BE/voices/voice_channel.kt:48`) | 0.5 literal (`BE/voices/VoiceFactory.kt:363`) |
| pregain | `pregain(amount)` writes flat slot `pregain` (`SP/lang_dynamics_level.kt:133,199`) | `pregain` | `.pregain()` places the slot (`ID:2591`) | `.pregain()` (`KSX:979`) | none | linear, 1 unity | `Slots.pregain` 1.0 |
| postgain | does not exist on any surface | | | | | | |

#### 4. Distortion family: distort / shape, crush, coarse

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | Katalyst twin | unit | default |
|---|---|---|---|---|---|---|---|
| distort drive | `distort(amount, shape)` pos 0 (`SP/lang_effects_distortion.kt:69`) | `distort.amount` | `distort(amount, shape = soft)` pos 0, amount required (`ID:3185`); `drive(amount)` (`ID:3097`) | `distort(amount, shape = "soft")` (`KSX:518`); `drive` (`KSX:485`) | `k.distort(amount = DISTORT_AMOUNT, shape = "soft")` (`AB/KatalystBuilders.kt:188`, `KS/KatalystBuilders.kt:446`) | `10^(amount*1.2)` gain into the shaper on all three (`BE/DistortionCore.kt:240`) | slot 0.0 = off (`IC:124`); node `Distort`/`Drive` 0.5 LITERAL (`ID:2145,2213`); Katalyst `DISTORT_AMOUNT` 0.5 (`AB/C/BusEffectDefaults.kt:173`) |
| distort LAW | fused `IgnitorDsl.Distort`: no soft cap, gated off as a unit (`ID:2211`, `IC:440`) | | `Shape(Drive(...))`: soft cap, shaper keeps shaping at amount <= 0 (`BE/ignitor/IgnitorEffects.kt:59-72`) | same as Kotlin | fused law, no soft cap (`AB/KatalystDsl.kt:451`) | | |
| shape | `shape` pos 1, name, per note | `distort.shape` (index) | `shape` (index or name) | name, number or slot | `shape` name only, fixed with the chain | DistortionShapes index | `soft` everywhere |
| crush | `crush(bits)` (`SP/lang_effects_distortion.kt:180`) | `crush.bits` | `crush(bits)` (`ID:3196`) | `crush(bits)` (`KSX:530`) | none | bits, `2^bits` levels, below 1 passes | slot 0.0 off (`IC:105`); node 8.0 |
| coarse | `coarse(factor)` (`SP/lang_effects_distortion.kt:254`) | `coarse.factor` | `coarse(factor)` (`ID:3202`) | `coarse(factor)` (`KSX:534`) | none | sample-hold factor, <= 1 off | slot 0.0 off (`IC:114`); node 4.0 |

#### 5. Tremolo

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | Katalyst | unit | default |
|---|---|---|---|---|---|---|---|
| depth | `tremolo(depth, rate, shape)` **pos 0** (`SP/lang_effects_modulation.kt:369`) | `tremolo.depth` | `tremolo(rate, depth, configure)` **pos 1** (`ID:3249`) | `tremolo(rate, depth, configure)` **pos 1** (`KSX:571`) | none | 0..1 | slot 0.0 = off (`IC:143`); node 0.5 |
| rate | pos 1 | `tremolo.rate` | **pos 0**, required | **pos 0**, required | none | Hz | slot **0.0** = a static gain, no wobble (`IC:144`; gate `BE/ignitor/IgnitorDslRuntime.kt:2045`); node **5.0** literal (`ID:2312`); no constant |
| shape | `shape` pos 2, name | `tremolo.shape` (index) | builder `shape(name or index)` (`EB:266,272`) | builder `shape` (`KS/EffectBuilders.kt:384`) | none | LfoShapes | sine |
| range | none, by decision (`IC:137-140`) | none | builder `range(from, to)` (`EB:260`) | `range` (`KS/EffectBuilders.kt:371`) | none | -1..1 language | (-1, 0) `TREMOLO_RANGE_*` |

#### 6. Vibrato

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | unit | default |
|---|---|---|---|---|---|---|
| rate | `vibrato(rate, semitones, rangeFrom, rangeTo, phase)` pos 0, alias `vib` (`SP/lang_tonal_pitchmod.kt:129,281`) | `vibrato.rate` | `vibrato(rate, semitones, configure)` pos 0 (`ID:3294`) | same (`KSX:675`) | Hz | `VIBRATO_RATE_HZ` 5.0 on slot AND node (`AB/C/PitchModDefaults.kt:29`, `IC:231`) |
| semitones | pos 1, the switch | `vibrato.semitones` | pos 1 | pos 1 | semitones | slot 0.0 (switch); node `VIBRATO_SEMITONES` 0.25 |
| range | `rangeFrom`, `rangeTo` pos 2, 3 | `vibrato.rangeFrom/To` | builder `range(from, to)` (`AB/IgnitorBuilders.kt:572`) | `range` (`KS/EffectBuilders.kt:409`) | -1..1 | `VIBRATO_RANGE_*` (-1, 1) both |
| phase | pos 4 | `vibrato.phase` | builder `phase` (`AB/IgnitorBuilders.kt:575`) | `phase` (`KS/EffectBuilders.kt:424`) | cycles | `VIBRATO_PHASE` 0 both |

No Katalyst twin.

#### 7. Pitch envelope, accelerate

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | unit | default |
|---|---|---|---|---|---|---|
| depth | `penv(semitones, attack, decay, sustain, release)` pos 0 (`SP/lang_tonal_pitchmod.kt:443`) | `penv.semitones` | **`pitchEnvelope`**`(semitones, configure)` (`ID:3322`) | **`pitchEnvelope`** (`KSX:718`) | semitones | slot 0.0 switch; node 0.0 |
| stages | pos 1..4, one at a time | `penv.attack` ... | builder `adsr(a, d, s, r, configure)` (`EB:205`) | `adsr` (`KS/EffectBuilders.kt:232`) | s, s, share, s | `PITCH_ENV_*` 0.01 / 0.1 / 0 / 0 on slot and node (`AB/C/PitchEnvelopeDefaults.kt:33-45`) |
| curves | `penvCurves(attack, decay, release)` (`SP/lang_tonal_pitchmod.kt:603`) | `penvCurves.*` | `adsr(..., e => e.curves(...))` | same | curve | `MOD_ENV_CURVE` |
| accelerate | `accelerate(semitones)` (`SP/lang_tonal_pitchmod.kt:723`) | flat `accelerate` | `accelerate(semitones)` (`ID:3306`) | same (`KSX:686`) | semitones over the gate, held | 0.0 |

#### 8. FM

| knob | sprudel | slot | Ignitor Kotlin | Ignitor KlangScript | unit | default |
|---|---|---|---|---|---|---|
| depth | `fm(depth, ratio, attack, decay, sustain, release)` **pos 0** (`SP/lang_synthesis_fm.kt:202`) | `fm.depth` | `fm(modulator, ratio, depth, configure)` **pos 2** (`ID:3168`) | same (`KSX:639`) | Hz | slot 0.0 switch; node 0.0 |
| ratio | pos 1 | `fm.ratio` | pos 1 | pos 1 | multiple of the note | `FM_RATIO` 1.0 both |
| envelope | pos 2..5, one at a time | `fm.attack` ... | builder `adsr(a, d, s, r)`, no curves lambda (`EB:237`) | `adsr` (`KS/EffectBuilders.kt:257`) | s, s, share, s | `FM_ENV_*` 0 / 0 / 1 / 0 both |
| curves | none | none | none | none | | `MOD_ENV_CURVE` fixed (`AB/C/EnvelopeDefaults.kt:34-36`) |

Positional order: recorded deliberate asymmetry (`/dsl-design` section 4, `SKILL.md:421`; door KDoc `SP/lang_synthesis_fm.kt` "a recorded asymmetry").

#### 9. onepole, legato/clip, solo, cut

| knob | sprudel | slot | Ignitor Kotlin / script | unit | default |
|---|---|---|---|---|---|
| onepole | `onepole(freq)` (`SP/lang_synthesis_ignitorparam.kt:404`) | flat `onepole` | `onepole(freq)` (`ID:3058`, `KSX:323`) | Hz, 0 off | slot 0.0; node 2000 literal |
| legato / clip | `legato(amount)`, `clip(amount)` (`SP/lang_tonal_note.kt:283,338`) | pattern time, no slot | none (not an engine knob) | duration factor | 1.0 |
| solo | `solo(amount)` (`SP/lang_structural_mute.kt:275`) | playback, no slot | none | 0..1 | 0.95 |
| cut | `cut(group)` (`SP/lang_sample_playback.kt:371`) | voice choke | none | group id | none |

All sprudel-only and sensibly so (pattern or playback concepts).

---

#### GAPS

1. **Tremolo positional order differs across doors.** Sprudel `tremolo(depth, rate, shape)` (`SP/lang_effects_modulation.kt:369-372`) vs Ignitor `tremolo(rate, depth, ...)` on both doors (`ID:3249-3252`, `KSX:571-574`). `tremolo(0.6, 4)` is depth 0.6 at 4 Hz in a pattern and rate 0.6 Hz at depth 4 in an instrument. The script KDoc acknowledges it (`KSX:566`, "Rate first, like every Ignitor LFO door; the pattern door is `tremolo(depth, rate, shape)`"), but `/dsl-design` §4's list of recorded asymmetries names only `lookahead` and FM's order. Inside sprudel the two LFO doors also disagree: `vibrato(rate, semitones, ...)` leads with the rate, `tremolo(depth, rate, ...)` with the depth. **Severity: a shape gap people will trip over.** **Fix:** either swap sprudel to `tremolo(rate, depth, shape)` (rate first, the Ignitor's order and sprudel's own vibrato order; a positional-call sweep of songs and tutorials, so the maintainer decides), or record it in §4 next to FM with the same reason (sprudel kept its order so no positional call changes). The FM precedent points to recording it, but V1 tutorials are the reason to decide now.

2. **An unset tremolo rate means no wobble in sprudel and 5 Hz on the node.** Slot `tremolo.rate` defaults to 0.0 (`IC:144`), and the gate builds a rate-0 tremolo as a static gain (`BE/ignitor/IgnitorDslRuntime.kt:2045-2046`). The node's default is the literal 5.0 (`ID:2312`). There is no constant for either. So `tremolo(0.6)` in a pattern quietly lowers the level without any wobble, while the vibrato's unset rate is `VIBRATO_RATE_HZ` = 5 on both slot and node (`IC:231`). The sprudel KDoc does not say this (`SP/lang_effects_modulation.kt:358`, "@param rate LFO rate in Hz."). It is recorded only in the `TremoloSlots` KDoc (`IC:133-134`, "NOT the node's 5.0"). **Severity: a trap, and it goes against "defaults are the same on every surface".** **Fix:** add `TREMOLO_RATE_HZ = 5.0` to `AB/C/TremoloDefaults.kt` and use it for the slot and the node, as vibrato does. That changes the sound only of patterns that set a depth and no rate, which today give a static gain. At minimum, the sprudel KDoc should say "unset: 0, no wobble".

3. **One concept with two door words: the pitch envelope.** Sprudel `penv` (`SP/lang_tonal_pitchmod.kt:443`) vs Ignitor `pitchEnvelope` on both doors (`ID:3322`, `KSX:718`, wire `pitch-envelope`). The filters have both words in sprudel (`lpf`/`lowpass`, `hpf`/`highpass`, `bpf`/`bandpass`), and vibrato has `vibrato`/`vib`, but the pitch envelope has no long name in sprudel and no `penv` in the Ignitor. **Severity: cosmetic, though it hurts discoverability.** **Fix:** give sprudel the long name `pitchEnvelope` as its second door, the `lowpass` pattern, so every sprudel short word has the engine's word next to it; or record why it doesn't.

4. **The `amount` word is used beyond the distort drive.** §4 says "`amount` is the distort drive only" (`SKILL.md:434`), but sprudel's `gain(amount)`, `pregain(amount)`, `pan(amount)`, `velocity(amount)` (`SP/lang_dynamics_level.kt:66,199,293,396`), `legato`/`clip(amount)` and `solo(amount)` all name their one parameter `amount`. The Katalyst twin of gain says `k.gain(gain = ...)` (slot `gain.gain`, `AB/KatalystBuilders.kt:210`). So `gain(amount = 0.5)` and `k.gain(gain = 0.5)` are one concept with two parameter words. **Severity: cosmetic** (positional use is the norm; named calls fail). **Fix:** name the parameter after the knob, as the Katalyst and Q29 (`sndPulze(duty)`) do: `gain(gain)`, `pan(pan)` or `pan(position)`, `velocity(velocity)`, `pregain(pregain)`. Or record one-knob doors as exempt from the rule.

5. **The distort law differs between the Ignitor door and the other two surfaces.** Sprudel `distort` (through `classic()`'s fused `Distort`, `IC:440`) and the Katalyst `distort` (`AB/KatalystDsl.kt:451`) run `shape(x*10^(1.2a))` with no soft cap and gate off as a unit. The Ignitor door `distort` is `Shape(Drive)`: it has a soft cap and keeps shaping at amount <= 0 (`BE/ignitor/IgnitorEffects.kt:59-72`). Same word, same scale, different output above ±1. This is decision D2, recorded in code (`ID:2211` KDoc, `IgnitorEffects.kt:66-71`) but not in §4's list. **Severity: deliberate and recorded in KDoc.** **Fix:** add it to §4's recorded asymmetries, or retire the capped door law once the songs that depend on it (Der Schmetterling, Sandsturm, A Truth Worth Lying For) are re-checked.

6. **The amp envelope's defaults differ between the slot and the node.** Slot: sustain 1.0, release 0.05 (`VOICE_ADSR_*`). Node: sustain 0.7, release 0.3 (`ID:2027-2030`). This is recorded and deliberate (`AB/C/EnvelopeDefaults.kt:56-60`, `IC:157-160`). No door shows the node's default (the Ignitor doors require all four stages), so only a hand-built `Adsr` meets it. **Severity: deliberate and recorded.** **Fix:** none. Optionally, move the node's literal 0.01 / 0.1 / 0.3 into constants (cosmetic).

7. **Literal defaults on a host where a constant exists** (§4: "Never a literal default per host"). Same value today, so cosmetic:
   - filter `q` 0.707: `IC:76,92` and `KSX:296,311,337,398` (should be `FILTER_Q`);
   - filter `passes` 1.0: `IC:77` (should be `FILTER_PASSES`);
   - the Ignitor script `phaser` center and sweep 1000.0: `KSX:552-553` (should be `PHASER_*_HZ`);
   - the `Distort` and `Drive` node amount 0.5: `ID:2145,2213` (should be `DISTORT_AMOUNT`).

   **Fix:** use the constants.

8. **(Cross-family, for the bus auditor) The phaser's `floor` default differs between voice and bus.** The Ignitor voice phaser node has floor 0.0, a crossfade (`ID:2275`); the sprudel and Katalyst phaser have `PHASER_FLOOR` = 1.0, additive (`AB/C/BusEffectDefaults.kt:102`). Their rate and wet defaults also differ (node 0.5 and 0.5 against 0.0 and 0.0). So `Ign.saw().phaser(0.5, 0.5)` crossfades while `phaser(0.5, 0.5)` in a pattern adds. This is acknowledged in the `Phaser` KDoc but not recorded as an exception. **Severity: you would trip over it.** **Fix:** decide whether one floor default fits both; if not, record it in §4.

9. **Curve-call meaning differs slightly.** On sprudel, `adsrCurves`/`lpfCurves`/`penvCurves` keep the current curve for an omitted stage or an unknown name (`SP/lang_dynamics_adsr.kt:191-201`). On the Ignitor, `curves(...)` resets an omitted stage or unknown name to the default (`EB:41-59,81-99`). The difference only shows when you chain calls. **Severity: cosmetic, documented on both sides.** **Fix:** none, or a line in §4.

### False or stale KDoc claims

- `SP/lang_filters_lpf.kt:106`, `SP/lang_filters_hpf.kt:106`, `SP/lang_filters_bpf.kt:97`, `SP/lang_filters_notch.kt:98`: "without `env` the filter rests at `freq`". This is **false** when a stage knob is written. `lpf(800, decay = 0.3)` sweeps by `FILTER_ENV_DEPTH_SEMITONES` = 7 through the slot-layer fill (`BE/ignitor/IgnitorDslRuntime.kt:1006-1026`). The Ignitor node KDoc documents this correctly.
- `AB/KatalystDsl.kt:445`: the Katalyst distort is "the Ignitor's `distort` at the bus position, one DSP and one word on both hosts". This is **misleading**: the law is the fused `DistortionCore` (sprudel's), not the Ignitor door's capped `Shape(Drive)` (gap 5). The next sentence states the right law.
- `ID:294`: `Slots.vibrato` is described as "`vibrato.rate` and `vibrato.semitones` (the switch)". This is **stale**: `rangeFrom`, `rangeTo` and `phase` are slots too since 7c.
- `BE/ignitor/IgnitorEffects.kt:53,90`: "Default: 0.0 (bypass)" for `distort`/`drive` `amount`. The Ignitor-typed overloads have no default, and the DSL node defaults to 0.5. This is **misleading**.
- Checked and true: the pregain KDoc's "about 4x at 0.5, 16x at 1, 250x at 2" (10^0.6, 10^1.2, 10^2.4); penv's "attack 0.01, decay 0.1, sustain 0, release 0"; FM's "attack 0, decay 0, sustain 1, release 0"; the script `pitchEnvelope`'s "`adsr(0.01, 0.1, 0, 0)`".

---

#### Checked, consistent

- **Filter words and scales:** `freq` (Hz), `q`, `passes` (rounded and coerced 1..16 in both places), `env` (semitones), the four stages (s, s, share, s) and their `FILTER_ENV_*` defaults, and the compound fill (door fill and slot-layer fill both use 7 semitones). bpf/notch have no `passes` on either surface. The curve default is `MOD_ENV_CURVE`. The door shape (flat in sprudel, builder on the Ignitor) is by design (KLANG-00314 S3a).
- **Amp envelope words** `attack`, `decay`, `sustain`, `release`, curves `attack/decay/release`, exponential by default.
- **crush.bits, coarse.factor:** the same words, scale and off rule on every surface.
- **distort `amount`/`shape`:** the same words and the same `10^(1.2a)` drive on all three surfaces (only the law differs, gap 5).
- **Vibrato:** the same words, the same positional order (rate, semitones), the same defaults on slot and node.
- **Pitch envelope:** the same param words (`semitones` and stages) and `PITCH_ENV_*` defaults (only the door word differs, gap 3).
- **accelerate(semitones):** the same everywhere.
- **FM:** the same words and units (Hz, ratio, s); the order difference is recorded; no surface has curves (consistent, though it is the one modulation envelope without them).
- **onepole(freq):** Hz on every surface.
- **pregain:** sprudel writes the slot, and both Ignitor doors place it.
- **gain and velocity:** linear, 1 unity, on voice and Katalyst (only the parameter word differs, gap 4).
- **pan:** sprudel-only, 0..1, coerced. legato, clip, solo and cut are sprudel-only pattern or playback concepts.
- **Slot exposure:** the script `Ignitor.slot` exposes every `classic()` slot group in this family (`KS/KlangScriptIgnitorSlots.kt:152-242`, `KS/KlangScriptIgnitorClassicSlots.kt`).
- **Pins:** the existing `*DoorParitySpec`s (Filter, Envelope, Vibrato, PitchMod, Waveshaper, Effect, Classic) pin script against Kotlin only. Nothing pins sprudel against Ignitor positional order or defaults, which is where gaps 1, 2 and 8 live.

### KLANG-00417 parity inventory: sound sources

Read from the code on 2026-11-06 (main, working tree). Read-only; nothing edited, no Gradle.

Abbreviations for the paths:

- `snd_basic` = `sprudel/src/commonMain/kotlin/lang/lang_synthesis_snd_basic.kt`
- `snd_super` = `sprudel/src/commonMain/kotlin/lang/lang_synthesis_snd_super.kt`
- `unison` = `sprudel/src/commonMain/kotlin/lang/lang_dynamics_unison.kt`
- `ignp` = `sprudel/src/commonMain/kotlin/lang/lang_synthesis_ignitorparam.kt`
- `playback` / `loopf` / `slice` = `sprudel/src/commonMain/kotlin/lang/lang_sample_{playback,loop,slice}.kt`
- `csp` = `sprudel/src/commonMain/kotlin/_classic_slot_params.kt`
- `Dsl` = `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt`
- `KB` = `audio_bridge/src/commonMain/kotlin/IgnitorBuilders.kt` (Kotlin builder members)
- `Ign` = `audio_bridge/src/commonMain/kotlin/Ign.kt` (Kotlin factories)
- `Classic` = `audio_bridge/src/commonMain/kotlin/IgnitorDslClassic.kt`
- `Tun` = `audio_bridge/src/commonMain/kotlin/constants/OscillatorTuning.kt`
- `KSI` = `klangscript-libs/src/commonMain/kotlin/stdlib/KlangScriptIgnitor.kt`
- `SB` = `klangscript-libs/src/commonMain/kotlin/stdlib/IgnitorBuilders.kt` (script knobs)
- `KSS` = `klangscript-libs/src/commonMain/kotlin/stdlib/KlangScriptIgnitorSlots.kt`
- `Defs` = `audio_be/src/commonMain/kotlin/ignitor/IgnitorDefaults.kt` (built-in sound registry)
- `BE` = `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt`
- `KS` = `audio_be/src/commonMain/kotlin/ignitor/KarplusString.kt`
- `VF` = `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt`

Two structural facts that shape every table below:

1. **Source slots are flat** (`voices`, `spread`, `duty`, ...), not `<door>.<param>`. Their names live in
   `Dsl.Slots` (Dsl:239-285), but sprudel writes them as **string literals** at each door (snd_basic:268,309,390,443,
   444,498; snd_super:58-61,170-175,270-271,...; unison:22,32,42; ignp:160,272), not through `IgnitorDsl.Slots`
   as the classic slots do (`ClassicSlotKeys`, csp:160-224).
2. **Built-in sounds open the slots, authored sounds differ by door**: `Defs` places `Slots.*` on every built-in
   (Defs:51-184). The Kotlin and script oscillator doors keep the node's open slots (`analog`, `duty`, `voices`,
   `spread`), the noise doors and the pluck doors seal theirs to literals (Ign:135-165, 229-263; KSI:255-329).

---

#### 1. The sound choice and the oscillator kinds

| concept | sprudel door | sound name(s) (Defs) | Ignitor Kotlin | Ignitor KlangScript | refs |
|---|---|---|---|---|---|
| sine | `sndSine(params)` (B8: none now) | `sine`, `sin` | `Ign.sine(freq, configure)` | `Ignitor.sine(freq, configure)` | snd_basic:33; Defs:51-53; Ign:53; KSI:127 |
| saw | `sndSaw(params)` (B8: none now) | `sawtooth`, `saw` | `Ign.saw` | `Ignitor.saw` | snd_basic:72; Defs:55-57 |
| square (rounded pulse, reads `duty`) | `sndSquare(params)` (no duty; B8: `sndSquare(duty)`) | `square`, `sqr`, `pulse` -> node `Pulze` (wire `"pulze"`) | `Ign.square` -> `Pulze` | `Ignitor.square` | snd_basic:111; Defs:59-63; Dsl:608-609; Ign:71-74 |
| raw pulse | `sndPulze(duty)` | `pulze` -> node `RawPulze` (wire `"raw-pulze"`) | `Ign.pulze` -> `RawPulze` | `Ignitor.pulze` | snd_basic:388; Defs:64-66; Dsl:635-636; Ign:124-127 |
| triangle | `sndTri(params)` (B8: none now) | `triangle`, `tri` | `Ign.tri` | `Ignitor.tri` | snd_basic:150; Defs:68-70 |
| ramp / raw ramp | `sndRamp(params)`, `sndZamp(params)` (B8: none now) | `ramp`, `zamp` | `Ign.ramp`, `Ign.zamp` | same | snd_basic:189,228; Defs:73-77 |
| raw saw | **none** | `zawtooth`, `zaw` | `Ign.zawtooth` | `Ignitor.zawtooth` | Defs:80-82; Ign:98 |
| impulse | **none** | `impulse` | `Ign.impulse` | `Ignitor.impulse` | Defs:84-85; Ign:115 |
| super osc | `sndSuperSaw/Sine/Square/Tri/Ramp(voices, spread)` | `supersaw`, `supersine`, `supersquare`/`supersqr`/`superpulse`, `supertri`, `superramp` | `Ign.supersaw(freq, configure)` ... | `Ignitor.supersaw` ... | snd_super:268,327,386,445,504; Defs:93-133 |
| pluck | `sndPluck(feedback, brightness, pickPosition, stiffness)` | `pluck`, `ks`, `string` | `Ign.pluck(freq, configure)` | `Ignitor.pluck` | snd_super:50; Defs:162-172 |
| superpluck | `sndSuperPluck(voices, spread, feedback, brightness, pickPosition, stiffness)` | `superpluck` | `Ign.superpluck` | `Ignitor.superpluck` | snd_super:160; Defs:174-184 |
| sample | `s`/`sound(name)`, `bank(name)`, `n(index)` | resolved by the engine | `IgnitorDsl.Sample` leaf, no door | no door | lang_tonal_sound.kt:94,142,188; lang_tonal_note.kt:229; Dsl:1079 |

The plain `snd*` doors carry a `params: PatternLike?` that only lifts structure: `_liftOrReinterpretStringField(args)
{ copy(sound = ...) }` ignores the value (snd_basic:20-21 and siblings; `SprudelPattern.kt:1104-1115`).

#### 2. Oscillator knobs

| knob concept | sprudel door (positional) | slot key | Ignitor Kotlin (builder member, param) | Ignitor KlangScript | unit / scale | default (where) | refs |
|---|---|---|---|---|---|---|---|
| freq | `freq(hz)` (pattern pitch) | wire `freqHz` | factory arg `freq` (Hz, default `Freq` = note) | factory arg `freq` | Hz | the note | lang_tonal_note.kt:422; Ign:53-127; KSI:116 |
| analog | `analog(character)` | `analog` | `.analog(character)` on every osc + pluck | `analog(character)` | unitless character, 0 ideal, 1-8 usual, 10 strong | 0.0: `Slots.analog` (Dsl:240), BE `analogDefault` (BE:117) | ignp:202; KB:72,106,...; SB:66-67 |
| analogSpread | none | none (Constant) | `.analogSpread(amount)` | `analogSpread(amount)` | 0..1 drift-lane blend | 1.0 node literal (Dsl:415), BE:118 | KB:94,238; SB:166 |
| phase | none | none (Constant) | `.phase(phase)` | `phase(phase)` | cycles, wraps | 0.0 node literal | KB:69...; SB:61 |
| duty | `duty(width)`; `sndPulze(duty)` | `duty` | `.duty(width)` on `square` and `pulze` | `duty(width)` | 0..1 share high | 0.5: `Slots.duty` (Dsl:247), BE `dutyDefault` (BE:120) | ignp:299; snd_basic:388-390; KB:154,169; SB:272-273,299-300 |
| flank shape | none | none | `.flankSamples(Double)`, `.riseFlank`, `.fallFlank` (square only) | same | SAMPLES; fractions | `PULSE_*` Tun:230-236 | KB:175-181; SB:309-320 |
| saw/ramp flyback | none | none | `.resetSamples(Double)`, `.shapeMax(Double)` | same | SAMPLES; fraction | `SAW_*`/`RAMP_*` (Tun) | KB:196-199,214-217; SB:344-349 |
| sine banks | none | none | `fundamental(gain)`, `harmonics(count, rolloff)`, `octaves(count, rolloff)`, `suboctaves(count, rolloff)`, `partial(ratio, gain, phase)` | same words | counts, exponents | node literals (Dsl:401-413) | KB:75-91; SB:79-153 |
| voices | `unison(voices, ...)`, `sndSuper*(voices, ...)` | `voices` | `.voices(voices)` | `voices(voices)` | count, coerced to [0, 256] | 8.0: `Slots.voices` (Dsl:285); `Ign.superpluck` literal 8.0 (Ign:256); BE fallback **7.0** (BE:119) | unison:22,81; snd_super:170,270; KB:229; SB:401-403; `_resource_bounds.kt:64,71` |
| spread | `unison(_, spread, _)`, `sndSuper*(_, spread)` | `spread` | `.spread(spread)` | `spread(spread)` | total detune width in SEMITONES, outer voice to outer voice (BE:3030-3053, `-spread/2..+spread/2`) | 0.2: `Slots.spread` (Dsl:282); `Ign.superpluck` 0.2 (Ign:257); BE `detuneDefault` 0.2 (BE:121) | unison:32,88; KB:232; SB:406-408 |
| unison pan (removed, B7) | `unison(_, _, pan)` | `panSpread` (unread) | none | none | 0..1, reserved | none | unison:42,76,123; `VoiceData.kt:39`; `SprudelVoiceData.kt:67` |
| super character | none | none | `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool(on, kMin, kMax, drawTries, poolSize, refreshEvery, selection, warmup)` | same | various | `SUPER*_` constants (Tun) | KB:241-276; SB:425-472 |

#### 3. Noise kinds and their knobs

| knob concept | sprudel door | slot key | Ignitor Kotlin | Ignitor KlangScript | unit / scale | default (where) | refs |
|---|---|---|---|---|---|---|---|
| white noise | `sndNoise(color)` | `color` | `Ign.whitenoise(color = 0.0.ign)` | `Ignitor.whitenoise(color = 0.0)` | tilt -1..1, 0 flat | 0.0: `Slots.color` (Dsl:244), Ign:135, KSI:255, `NOISE_TILT_DEFAULT` (Tun:260, used only by BE:127) | snd_basic:266-268 |
| brown noise | `sndBrown(leak)` | `leak` | `Ign.brownnoise(leak = 0.02.ign)` | `Ignitor.brownnoise(leak = 0.02)` | per-sample white leak k | 0.02: Dsl:249, Ign:138, KSI:263, `BROWN_LEAK_DEFAULT` (Tun:267, BE:128) | snd_basic:307-309 |
| pink noise | `sndPink(params)` (B8: none now) | none | `Ign.pinknoise()` | `Ignitor.pinknoise()` | no knobs | n/a | snd_basic:349 |
| perlin / berlin | **none** (`s("perlin")` + `ignp`) | `rate`, `octaves`, `persistence` | `Ign.perlin(rate, octaves, persistence)`, `Ign.berlin(...)` | same | rate x144 cells/s; fBm octaves 1..8; falloff 0..1 | 1.0 / 1.0 / 0.5: Dsl:281,251,252; Ign:145-147; KSI:277-281; BE:122-125 | Defs:149-155 |
| dust density | `sndDust(density, tail)`; `density(amount)` / `d(amount)` | `density` | `Ign.dust(density, tail, bipolar)` | `Ignitor.dust(density, tail, bipolar)` | 0..1 of a 200/s ceiling | 0.2: Dsl:246, Ign:159, KSI:314, BE:121 (no constant) | snd_basic:441-444; unison:170,202; BE:1590-1596 |
| dust tail | `sndDust(_, tail)` | `tail` | `dust(_, tail, _)` | same | amplitude EXPONENT (`amp^tail`), 1 uniform | 1.0: Dsl:284, Ign:160, KSI:315, `DUST_TAIL_DEFAULT` (Tun:271) | snd_basic:444; BE:1585-1587,1611-1631 |
| dust bipolar | **none** | `bipolar` | `dust(_, _, bipolar)` | same | flag > 0.5 | 0.0: Dsl:241, Ign:161, KSI:316, `DUST_BIPOLAR_DEFAULT` (Tun:274) | Dsl:711-722 |
| crackle | `sndCrackle(chaos)` | `chaos` | `Ign.crackle(chaos = 1.5.ign)` | `Ignitor.crackle(chaos = 1.5)` | map drive ~1 sparse .. 2 dense | 1.5: Dsl:243, Ign:165, KSI:329, `CRACKLE_CHAOS_DEFAULT` (Tun:243, BE:126) | snd_basic:496-498 |

#### 4. Pluck and superpluck (Karplus) knobs

| knob concept | sprudel door (positional) | slot key | Ignitor Kotlin | Ignitor KlangScript | unit / scale | default (where) | refs |
|---|---|---|---|---|---|---|---|
| feedback | `sndPluck(feedback, ...)` pos 0; `sndSuperPluck(_, _, feedback, ...)` pos 2 | `feedback` | `.feedback(feedback)` | `feedback(feedback)` | loop coefficient 0.9..0.999, not a time | 0.996: Dsl:248, Ign:235,258, BE:131 | snd_super:58,172; KB:521,548; SB:932 |
| brightness | pos 1 / pos 3 | `brightness` | `.brightness(brightness)` | `brightness(brightness)` | 0..1, the LOOP one-pole coefficient (KS:13-14,144,189); not used by the burst (KS:44-60) | 0.5: Dsl:242, Ign:236,259, BE:132 | snd_super:39,59,173; KB:523,550; SB:936-938,973 |
| pickPosition | pos 2 / pos 4 | `pickPosition` | `.pickPosition(pickPosition)` | same | 0..1, burst length and placement (KS:44-60) | 0.5: Dsl:253, Ign:237,260, BE:133 | snd_super:40,60,174; SB:941,978 |
| stiffness | pos 3 / pos 5 | `stiffness` | `.stiffness(stiffness)` | same | 0..1 (coerced to 0.99) allpass | 0.0: Dsl:283, Ign:238,261, BE:134 | KS:192-195 |
| voices, spread (superpluck) | pos 0, 1 | `voices`, `spread` | `.voices`, `.spread` | same | as section 2 | 8 / 0.2, but SEALED in `Ign.superpluck` (Ign:256-257) | snd_super:170-171 |
| analog | `analog(character)` | `analog` | `.analog(character)` (left open) | same | character | 0.0 | KB:533,560 |

#### 5. Samples

| knob concept | sprudel door | slot key | Ignitor Kotlin | Ignitor KlangScript | unit / scale | default | refs |
|---|---|---|---|---|---|---|---|
| begin | `begin(pos)` | `begin` | `Slots.sample.begin` | none (recorded) | 0..1 of length | UNSET | playback:54; Classic:299-308; VF:172 |
| end | `end(pos)` | `end` | `Slots.sample.end` | none | 0..1 | UNSET | playback:138; VF:173 |
| speed | `speed(rate)` | `speed` | `Slots.sample.speed` | none | multiplier | 1.0 | playback:223; VF:174 |
| loop | `loop(flag = true)` | `loop` | `Slots.sample.loop` | none | flag | 0.0 | loopf:50; VF:175 |
| loop region | `begin`/`end` when looping (no `loopBegin`/`loopEnd` door anywhere) | n/a | n/a | n/a | | | VF:186-205 |
| cut | `cut(group)` | wire field | none | none | int group | none | playback:371 |
| n / index | `n(index)`, `"bd:2"` | wire `soundIndex` | `Ign.variants(...)` reads it | `Ignitor.variants(...)` | int, floor-mod | 0 | lang_tonal_note.kt:229; KSI:505-532 |
| bank | `bank(name)` | wire field | none | none | name | none | lang_tonal_sound.kt:188 |
| slice/splice | `slice(n, index)`, `splice(n, index)` | begin/end | none | none | | | slice:54,120 |

The script door has no sample slots: recorded (KLANG-00237 step 8, archived; KLANG-00148 lines 52-57).

#### 6. Raw slot writes

| door | sprudel | Ignitor | refs |
|---|---|---|---|
| write one slot | `ignitorParam(slot, value)`, `ignp(slot, value)`; slot is a name or an `Ignitor.param`/`Ignitor.slot.*` object | `Ign.param(name, default, description)` / `Ignitor.param(...)`; `Ign.slot` / `Ignitor.slot` | ignp:76,121; Ign:32,268; KSI:470; KSS |

---

#### GAPS

Severity: **[shape]** a user would trip on it; **[cosmetic]** wording or internal; **[recorded]** deliberate and
already written down.

1. **[shape] The pluck `brightness` is described two ways, and one is false.** sprudel: "Lowpass cutoff (0 = dark,
   1 = bright)" (snd_super:39,69,94,115,149,185,210,235). Kotlin and script: "Initial-burst brightness / pick
   hardness" (KB:523,550; SB:936,973). The engine uses it ONLY as the loop one-pole coefficient
   (`lpAlphaOf(brightness)`, KS:13-14,144,189); `excite()` takes no brightness (KS:44-60). Fix: the Ignitor KDoc says
   what sprudel says (the loop lowpass, how fast the highs die), with the 0.01 floor.

2. **[shape] Duplicated source defaults, up to five literals per knob, against "defaults live in ONE place".** Each
   source default sits in `Dsl.Slots` as a literal (Dsl:240-285), again in the Kotlin factories (Ign:135-165,
   235-261), again in the script factories (KSI:255-329) and again in the engine fallbacks (BE:117-134). Constants
   exist for five of them (Tun:243,260,267,271,274) but only BE uses them. One value already differs: the engine's
   `voicesDefault` is 7.0 (BE:119), everywhere else 8.0 (Dsl:285, Ign:256, SB:401). It cannot be reached from the DSL
   today (the runtime always passes `voices`, `IgnitorDslRuntime.kt:1402-1437`). Fix: a constant per source knob
   in `audio_bridge/constants/` (Tun or a new `SourceDefaults.kt`), read by `Slots`, both factories and BE. Pin it
   with one row per knob (Slots default == factory default == constant).

3. **[shape] Open or sealed depends on the door, not on the knob.** On an authored sound `Ign.square()` and
   `Ign.supersaw()` answer the pattern's `duty`, `voices`, `spread` (node defaults are `Slots.*`). `Ign.whitenoise()`,
   `brownnoise`, `perlin`, `dust`, `crackle` and both plucks seal their knobs (Ign:135-165,229-263; KSI:255-329), so
   `sndNoise(0.5)` or `unison(4)` on a custom `Ignitor.superpluck()` does nothing. It is documented (KSS:24-30,
   Ign:225-228,247-248) but no reason is given for why the plucks and noises differ from the oscillators. Fix: decide
   one policy (maintainer) and record the reason in the KDoc, or open them like the oscillators.

4. **[shape] `sndPulze(duty)` gives the aliased pulse; sprudel has no door for the rounded one with a duty.**
   `pulze` is `RawPulze` (Defs:64-66), and the clean PWM pulse (`square`/`pulse`, Defs:59-63) reads the same `duty`
   slot, but `sndSquare` takes only the dead `params` (snd_basic:111). The sprudel KDoc does not say raw
   (snd_basic:379), unlike `sndZamp` (snd_basic:220). On the Ignitor the clean one is `Ign.square { it.duty(..) }`.
   Fix: `sndSquare(duty)` by the Q29 rule (the knob word), and "raw, aliased" in `sndPulze`'s KDoc.

5. **[shape] The dead `params` parameter on the plain `snd*` doors.** `sndSine`, `sndSaw`, `sndSquare`, `sndTri`,
   `sndRamp`, `sndZamp`, `sndPink` take `params: PatternLike?` ("Optional pattern-like parameter",
   snd_basic:27,66,...). The value is ignored and only its structure is lifted (snd_basic:20-21;
   `SprudelPattern.kt:1104-1115`). `sndSine(4)` looks like a knob and is not. Fix: remove it, or give it the knob the
   sound reads (`duty` for square, see 4).

6. **[shape] `unison(pan)` writes the slot `panSpread`, which nothing reads.** The door word is `pan` and so is the
   accessor `unison.pan`, but the key is `"panSpread"` (unison:42,46,123). No `Slots` entry, no Ignitor knob, no
   reader (`VoiceData.kt:39`, `SprudelVoiceData.kt:67`). The door's KDoc says "Reserved, not read yet"
   (unison:76,130). That is a door word and a slot key that differ (D4), plus a knob that does nothing. Fix: remove
   the parameter until a reader exists ("complexity is the enemy"), or, if it stays, make the key match the door
   word and put the slot in `Slots`.

7. **[shape] The unit of `spread` is written only on sprudel, and the Ignitor KDoc says "frequency".** The engine
   reads it as the total width in semitones, outer voice to outer voice (BE:3030-3053, then `.semitones()`
   BE:2365). sprudel `unison` says "Detune spread in semitones" (unison:75). `snd*` says "Detune spread between
   voices" (snd_super:148,...). The Ignitor says "Unison frequency spread between the voices" (Dsl:770,829,890,949,
   1009; KB:231; SB:406,...). The `unison` example "five voices, a third of a semitone apart" (unison:63) is wrong:
   0.3 is the outer-to-outer width, about 0.075 between neighbours. Fix: one sentence on every surface, "total
   detune width in semitones, outer voice to outer voice".

8. **[cosmetic, false KDoc] "the pattern-level `.spread()`" does not exist.** No sprudel `spread` door exists; it
   is `unison(spread = ...)` or `sndSuper*(_, spread)`. The claim appears at Dsl:770,829,890,949,1009 and
   SB:406,515,625,733,843,963. Fix: name the real doors.

9. **[shape] `sndDust(tail)` is documented as "Impulse tail length". It is an amplitude exponent.** See
   snd_basic:433,452,465,476 against BE:1585-1587,1611-1631, Dsl:714 and KSI:309. Fix: use the Ignitor's text.

10. **[shape] `density(amount)` and other stray `amount` words.** §4 says "`amount` is the distort drive only". The
    word also appears in `density(amount)` / `d(amount)` (unison:202,215,251,265,285,298,320), `pregain(amount)`
    (lang_dynamics_level.kt:199) and the builder knob `analogSpread(amount)` (KB:94,238,...; SB:167,...). The script
    named argument shows it to users. The Q29 pattern allows an inner word that differs (`duty(width)`), but it
    names what the value IS. Fix (maintainer): `density(<word>)` (for example `density(density)` as `sndDust` has
    it), `pregain(level)`, `analogSpread(blend)`. Outside this family, but the same finding: `legato(amount)`,
    `clip(amount)`, `transpose(amount)` (lang_tonal_note.kt:283,338; lang_tonal_scale.kt:217).

11. **[cosmetic, false KDoc] "density ... dust / crackle".** Crackle reads `chaos` (Defs:158). Wrong at Defs:40
    and KSS:55. The `density` String and mapper variants say "the oscillator density" or "oscillator or noise
    density" (unison:206,212,243,248,256,262,289,295,310,317), but only dust reads it, as the door's own main KDoc
    says (unison:185-186). Fix: "dust's impulse rate".

12. **[cosmetic, false KDoc] `sndSuperPluck`'s colon string and its default.** The KDoc says "optionally configures
    parameters via `"voices:detune:feedback:brightness:pickPosition:stiffness"`" (snd_super:136). The door parses
    no colon string, and `detune` is not the word (`spread`). The example `// default 5-string` (snd_super:142) is
    wrong: the default is 8 (Dsl:285). Fix: delete the colon sentence, and say 8.

13. **[cosmetic, false KDoc] `voices` "Number of oscillators (1 to 16)"** (snd_super:262,321,380,439,498). The engine
    takes 0 to 256 (`_resource_bounds.kt:64-79`). Fix: "default 8, up to 256" (the `unison` KDoc's "Typically 1 to
    16" is fine).

14. **[cosmetic] `pickPosition`: "1 = neck" (sprudel, snd_super:40,70,...) and "1 = nut" (SB:941,978).** The
    engine: 0 is a short burst at the start, 1 is the whole length (KS:44-60). Fix: one wording on both surfaces.

15. **[cosmetic] Units in names: `resetSamples`, `flankSamples`** (KB:175,196,214; SB:309,344,374). These are times
    in samples, with the unit in the name, against the provisional units rule (time in seconds, unit in the KDoc).
    They are tied to the sample grid on purpose (aliasing), so this is probably an exception. Fix: record it in their
    KDoc (the rule it departs from, and why), or ask the maintainer.

16. **[cosmetic] The pulse naming has one word with two meanings at the node and wire layer.** The sound `pulze`,
    `sndPulze`, `Ign.pulze` and `OscPulzeBuilder` are the RAW pulse, node `RawPulze`, wire `"raw-pulze"`
    (Dsl:635-636). The node `IgnitorDsl.Pulze`, with wire name `"pulze"`, is the ROUNDED one built by `Ign.square`
    (Dsl:608-609; Ign:71-74). A third type, `IgnitorDsl.Square`, wire `"square"`, is a fixed 50% pulse kept for
    internal use (Dsl:491-495). §5 asks for one word per concept down to the wire. Fix (maintainer, it changes the
    wire hash): rename the node `Pulze` to match its door (`square`), or record it.

17. **[cosmetic] The builder word and the node field differ: `centerJitter` writes `centerJitterScale`** (KB:250,
    309,368,427,486). §5 asks for one word. Fix: rename the node field (wire hash) or record it.

18. **[cosmetic] Flat source slots whose words collide.** The flat `octaves` slot (perlin fBm layers, default 1,
    Dsl:251) shares its word with the sine builder's `octaves(count, rolloff)` (partials at 2f, 4f, ...; default 0,
    Dsl:407; KB:82). A user who follows the `Slots` KDoc pattern (Dsl:226-238) with `x.octaves(Ign.slot.octaves)`
    adds an octave partial. The flat `rate` (perlin walk speed, Dsl:281) sits next to `vibrato.rate` and
    `tremolo.rate` (Hz), so `ignp("rate", 5)` reaches only perlin. Fix (maintainer): namespace the multi-knob source
    slots (`perlin.rate`, `perlin.octaves`, ...) by D4, or record that the source slots stay flat. KLANG-00289
    decided flat only for the one-knob doors (its decision 10).

19. **[shape, no pin] The source slot keys are literals in sprudel.** sprudel writes `"voices"`, `"spread"`,
    `"duty"`, `"feedback"`, ... as strings (structural fact 1), while the classic and sample slots go through
    `IgnitorDsl.Slots` (csp:160-224). A rename on the Ignitor side would silently disconnect the sprudel door. Fix:
    read the names from `IgnitorDsl.Slots` (`ClassicSlotKeys` style), or add a spec row per `snd*` door that the
    key it writes equals the matching `Slots.*` name.

20. **[cosmetic] Availability gaps at the sprudel door** (each sound plays via `s("...")` plus `ignp`):
    - No `sndZaw`/`sndZawtooth` and no `sndImpulse`, though `zawtooth`/`zaw` and `impulse` are built-ins
      (Defs:80-85), and `sndZamp` exists for the raw ramp.
    - No `snd*` for `perlin`/`berlin` (three slots, Defs:149-155).
    - `sndDust` has no `bipolar`, which the Ignitor door and the slot have (Dsl:241,717; Ign:161; KSI:316).
    - `sndNoise` is the white noise; its siblings are named by colour (`sndBrown`, `sndPink`), so `sndWhite` would
      be the parallel word.

    Fix: add them where they make sense (`sndDust(density, tail, bipolar)` is the cheap one), or record that they
    are left out.

21. **[cosmetic, stale] The guard specs pin a constant against itself, and their KDoc is false.**
    `SuperOscDefaultsSyncSpec` (audio_be commonTest, around line 75) and `WaveOscDefaultsSyncSpec` (lines 20-23)
    say the node defaults are hand copies of `internal` constants in `audio_be/.../OscillatorTuning.kt`. That file
    no longer exists: the constants live in `audio_bridge/constants/OscillatorTuning.kt`, and the nodes use them
    directly (Dsl:64 import; Dsl:475-477). Dsl:763-764 makes the same false claim. These are scaffolding whose work
    is done. Meanwhile the defaults that ARE duplicated (gap 2) have no pin. Fix: delete or repoint the two specs at
    gap 2, and correct the Dsl:763 KDoc.

22. **[cosmetic, stale KDoc] `onepole(hz)`.** The sprudel door is `onepole(freq)` (ignp:388), but Dsl:318-319 and
    KSS:182 say `onepole(hz)`. `ignitorParam`'s KDoc lists "Slots used elsewhere in this library are `analog`,
    `onepole` and `density`" (ignp:46), far from complete. The real list is
    `voices, spread, duty, color, leak, tail, chaos, feedback, brightness, pickPosition, stiffness, panSpread`, plus
    the classic and sample slots. Also `freq(hz)` (lang_tonal_note.kt:422) puts the unit in the parameter name,
    against the units rule.

23. **[recorded] The sample playback slots `begin`, `end`, `speed`, `loop` are Kotlin-only** (`IgnitorDsl.Slots.sample`,
    no `Ignitor.slot.sample`, and no `Ignitor.sample()` door). Recorded in KLANG-00237 step 8 (archived) and
    KLANG-00148 lines 52-57 with its reason: the engine reads them at the playhead, before any tree. Revisit only if
    `Ignitor.sample()` lands.

24. **[recorded] `duty(width)` against `sndPulze(duty)`**: the Q29 rule (the `snd*` door takes the knob word, the
    knob keeps its inner word). It is consistent on all three surfaces (ignp:299; snd_basic:388; KB:154,169; SB:273,300).

#### Checked, consistent

- **`analog`**: one word, the parameter `character`, the same character scale and default 0 on sprudel
  (ignp:202), on every Kotlin and script builder (KB, SB) and in the engine (BE:117). The Q25 rename landed.
- **`voices` / `spread` order**: `unison(voices, spread, pan)` (pan removed, B7), `sndSuper*(voices, spread)` and
  `sndSuperPluck(voices, spread, ...)` all lead with voices, then spread, and so does the builder knob list.
- **The pluck positional order** matches the builder knob list on every surface: `sndPluck(feedback, brightness,
  pickPosition, stiffness)`, superpluck with `voices, spread` in front. `feedback` (formerly the pluck `decay`) and
  `leak` (formerly the brown `depth`) say the decided words everywhere (KLANG-00289 decision 7).
- **Noise knob names** are the same on sprudel, the slot, the Kotlin factory and the script factory: `color`,
  `leak`, `chaos`, `density`, `tail`. So are the positional orders `dust(density, tail, bipolar)` and
  `perlin(rate, octaves, persistence)`.
- **Default values**: every surface agrees except the unreachable `voicesDefault` 7.0 (gap 2). `duty` 0.5,
  `density` 0.2, `tail` 1, `bipolar` 0, `chaos` 1.5, `color` 0, `leak` 0.02, `feedback` 0.996, `brightness` 0.5,
  `pickPosition` 0.5, `stiffness` 0, `rate` 1, `octaves` 1, `persistence` 0.5, `spread` 0.2, `voices` 8.
- **The Kotlin and script factory shapes**: `(freq, configure)` on every oscillator, and the same knob set per
  builder (the script variants call the Kotlin members, SB). The plucks on the script door build on the Kotlin door
  (KSI:427-428,442-443).
- **The sample slots**: the sprudel writer reads the names from `Slots.sample` (csp:132-135,221-224), and the engine
  reads the same objects (VF:172-175).
- **`ignitorParam` / `ignp`**: the same door under both names, taking a name or a slot object (ignp:76-155).
  `Ign.param` and `Ignitor.param` agree.
- **`Ign.slot` and `Ignitor.slot`** are the same objects (Ign:32; KSS). The source slots the script lists equal
  `IgnitorDsl.Slots`, except `sample` (gap 23, recorded).


## History

- 2026-11-06: created from KLANG-00137 section 1 (the maintainer: its own ticket, after the DSP restructuring).
- 2026-11-06: IN PROGRESS (maintainer: start now; no backward compatibility, the songs, jingles and tutorials follow).
- 2026-11-06: the audit recorded (A and B); the maintainer decided B1 to B8, every recommendation.
- 2026-11-06: B1 to B8, A1 to A5 and B9 done; step 4 (the pins) done; DONE.
- 2026-11-06: DONE.
