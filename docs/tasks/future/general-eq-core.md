# One general EQ core: every filter is an EQ section, and sprudel speaks `.eq()`

Status: **future, not planned; needs a design round before it can be sized.** Opened 2026-09-29 by the maintainer.
If it is built, it is a SHAPE change (sprudel's filter doors go), so by the V1 sorting rule
([`../_v1-scope.md`](../_v1-scope.md)) it would have to land before the tutorials are written against `lpf`.

## The idea (maintainer, 2026-09-29)

Remove sprudel's filter doors (`lpf`, `hpf`, `bpf`, `notch`) in favour of one `.eq()`, the same door the Ignitor trees use.
For that, **the EQ must be able to handle every combination of filters**, so the optimizer can always put them
together. The result is **one general EQ core**, used by the Ignitor trees, by sprudel and by the bus alike, and more
flexible than today's fixed filter set: any number of sections, in any order, of any kind.

It reopens a decision: on 2026-08-31 the maintainer decided "no `band` and no `tap` on sprudel in V1"
([`../../tasks-archive/2026-09/20260927-filter-unification.md`](../../tasks-archive/2026-09/20260927-filter-unification.md),
section C6). That decision had two reasons. The first: the voice strip had no `EqCore` in it, so a sprudel `tap` would
have read a different signal than an Ignitor `tap` (D9's static tier had not landed). The strip retired in phase 3 step
9 and every voice is its Ignitor tree now, so that reason is gone. The second still stands and this task must answer
it: a `tap`'s defining property is its list position, and a one-per-voice sprudel `tap` at a position nobody chose is
a weaker `tap` (the record says that reasoning "stands unchanged for whenever this re-opens"). Section 4's section
lists, written in the author's order, are the answer to try.

## 1. Where we stand (2026-09-29, read from the tree)

- **`EqCore`** (`audio_be/src/commonMain/kotlin/filters/EqCore.kt`): N serial second-order **TPT-SVF** sections in
  one `process()` call, section kinds `LOWPASS`, `HIGHPASS`, `BANDPASS`, `NOTCH`, `RAW_TAP`, `BELL`. Mono, one instance
  per channel. **Snap-only**: coefficients take effect at once; any smoothing is the surface's job. Driven by the
  per-voice `EqIgnitor` and by the orbit's `KatalystEqEffect` (so the bus and the master `eq` already share it).
- **`IgnitorDsl.Eq`** with `IgnitorDsl.EqSection` (`audio_bridge/.../IgnitorDsl.kt`): each section carries `freq` and
  `q` (the bell also `db`, the raw tap `gain`), nothing else. The script `EqBuilder`
  (`klangscript-libs/.../stdlib/EffectBuilders.kt`) offers `band` and `tap` only; lowpass, highpass, bandpass and notch
  sections exist only as what the optimizer fuses, so on the door they would be new on both surfaces.
- **The optimizer** (`audio_bridge/.../IgnitorDslOptimizer.kt`, rule R1) fuses consecutive filter nodes into one
  `Eq`, and REFUSES a filter that carries any of: a non-zero `analog` (the saturating SVF branch), a cutoff envelope
  (`env`), the per-voice `humanize` lane, a non-constant `passes`. It decides once, at registration, so a `Param`
  that a note could switch on counts as on. That is why `classic()`'s filters never fuse today.
- **Sprudel's filter doors** fill fixed slots of the `classic()` tail
  (`audio_bridge/.../IgnitorDslClassic.kt`: `onepole -> crush -> coarse -> distort -> highpass -> bandpass -> notch ->
  lowpass -> tremolo -> adsr`), with per-filter `freq`, `q`, `env` and its ADSR stages and curves, `passes` (lowpass and
  highpass), and the voice's `analog`. In use: `lpf` in 12 of the 14 built-in songs; about 175 filter calls across them, bare mapper calls such as
  `superimpose(bpf(195))` included (`lpf` 68, `hpf` 86, `bpf` 16, `notch` 5; grep, 2026-09-29).
- The topology is the SAME on both sides: `Ignitor.svf` and `EqCore` compute the same SVF coefficients
  (`computeSvfCoeffs`), and parity rows pin them. So generalising the core is extending one filter, not replacing one.

## 2. Part A: the general core (every filter feature becomes a section feature)

Each refusal of the optimizer names a feature a section must learn. Sketch, per feature:

| feature | what a section needs | notes |
|---|---|---|
| cutoff envelope (`env`, attack, decay, sustain, release, curves) | an optional envelope per section, the same law and defaults as the filter envelope today, the slot-layer fill included (a stage knob written without `env` gets the default depth) | `SvfCoeffSweep` carries a swept filter's coefficients with PER-SAMPLE steps inside each block; a snap-only core recomputed per block cannot do that without a zipper (unified-eq D9 tier 3 records it as an audible regression class). So the section loop must carry the sweep's per-sample steps: a ramp tier in `EqCore`, the unified-eq plan's ramp API (D9, cut from V1 on 2026-08-31). One answer for voice and bus |
| `analog` (the saturating branch) | a per-section saturation flag or amount | the state-dependent branch of `Ignitor.svf`'s lowpass and highpass taps moves into the section loop; nonlinear, so parity with today is pinned per sample, not by superposition |
| `passes` (cascade) | nothing: expand to N sections of one kind and cutoff at build, each with its ladder q | the Butterworth ladder (`butterworthQLadder`) sets the per-pass q; the expanded sections must share ONE humanization (today a cascade shares one `FilterHumanization` and draws once; one draw per section would shift the rng order and break render identity); a `passes` that a note writes becomes a build-time decision (see section 4) |
| `humanize` (per-voice tolerance and drift lane) | a per-section drift lane reference | today it is `FilterHumanization` (`audio_be/.../ignitor/FilterHumanization.kt`): a fixed per-voice tolerance plus an `AnalogDrift` walk per node, stepped per block (`blockDriftMultiplier`); a section carries the same |
| **the stage gate** (an unset section is not built) | a per-section gate in the `Eq` build, PER KIND as today: an SVF section is skipped when its `freq` is unset; the one-pole section when its freq is at or below 0.0 or unset (its slot default is `0.0`, not unset). The gate is leaf-only, as today: a cutoff that is not a `Param` or `Constant` builds the section unconditionally (`buildTimeKnobValue`), and the gate runs BEFORE the section's rng draws (today a gated-off filter never reaches `filterHumanization`), so a gated section shifts no random stream. When every section is gated, the build returns the inner (pass-through), not an empty core. The per-filter draw order is pinned by `IgnitorFilterKnobsSpec`; a fused section keeps its draws in front of its knobs | today the runtime skips an unset filter STAGE (`gatedOffWhenUnset`, `audio_be/.../ignitor/IgnitorDslRuntime.kt`, the `GATE ROW` comments of the four SVF filters and of `onepole`), while the `Eq` arm builds every section, and an unset (NaN) cutoff clamps to 1000 Hz (`clampSvfCutoff`). Without the gate, fusing `classic()`'s bandpass and notch would put a 1000 Hz bandpass and notch on every plain `sound("saw")` |
| one-pole (`onepole`) | a first-order section kind | one step toward [`onepole-highpass-door.md`](onepole-highpass-door.md). The pattern's `onepole` sits in front of `crush`, `coarse` and `distort`, which are present at registration, so it is adjacent to an instrument's own `eq` (which sits right before it) already at registration, and adjacent to `classic()`'s own filters only under note-on fusion, when crush, coarse and distort are skipped |

With all of that, the optimizer's refusals go away and **any run of filters with nothing nonlinear between them
fuses into one `EqCore`**, and a section nobody wrote costs nothing. What stays unfusable is structural: a node with more than one
consumer, a nonlinearity between two filters (distort, crush), a tap that must read a specific signal.

**Guards the core needs** (mandatory tier, every row mutation-checked): per feature, a parity row of a section against
today's `Ignitor.svf` with the same feature (bit-identical where the math is the same, a stated bound where the
evaluation order changes); a render-identity run over the built-in corpus before and after the fusion lifts
(a one-off whole-corpus render, `docs/plans/signal-flow-redesign.md` section 12, wall clock pinned); a guard that an unwritten `classic()` builds no section (the default envelope is the only stage it builds); a
benchmark of the fused `classic()` tail against today's.

## 3. Part B: sprudel speaks `.eq()`

The door, in the house door shape (a configure lambda, last and optional, on a builder). A sketch; the section
builders' shape is open (today the Ignitor filter keeps `env` and the envelope stages on a builder of their own, and
one shape per concept asks the section to match):

```
note("c2 e2").sound("saw").eq(e => e.highpass(80).lowpass(800, q = 1.2, env = 24, decay = 0.2).band(300, 1, 3))
```

`lpf`, `hpf`, `bpf`, `notch` are then removed, not deprecated (one word per concept; a replaced surface is removed),
on both doors (KlangScript and Kotlin) with a door-parity spec. Open for the design round: whether single-section
shorthands survive as builder spellings only, and how a section's knob is patterned (below).

## 4. Ideas: how the EQ settings reach the backend per note and voice

The rules the design must keep are the signal-flow plan's two structural rules (section 3): **slots are the only
per-event channel**, and **structure is declared once and never depends on an event value**. A section LIST is
structure. Options 1 and 2 below bend the second rule and need the maintainer's word on it; option 3 keeps it. The
same plan's section 5 says that someone who wants a second lowpass writes an instrument; this idea reverses that. Three ways, not decided:

1. **A fixed-shape EQ block in `classic()`.** `classic()` places one `eq` stage with a fixed number of section slots
   (say eight): `eq.0.kind`, `eq.0.freq`, `eq.0.q`, `eq.0.db`, `eq.0.env`, ... A `kind` of "off" is the stage's off
   value, so unwritten sections are not built. The pattern fills slots exactly as today; the wire needs nothing new.
   Cost: a hard limit and wide slot names; the kind is an index slot, like the body's `material`.
2. **A list-valued slot.** The voice carries the section list as one value (`VoiceData.ignitorParams` gains a structured
   entry, or a sealed `@WireName` `EqSectionData` list next to it). `classic()`'s `eq` stage is ONE node whose sections
   are resolved at voice build (note-on), the way a filter envelope's slots are resolved today. The tree's shape does
   not change, only one node's content, so the slot rule holds if "a slot may hold a list" is accepted. Cost: a list
   per event on the wire (sizes to measure against the codec benchmark), and the build must decide fusion per shape.
3. **Registered EQ values by name.** An `Eq(e => ...)` value is registered like an Ignitor or a Katalyst chain
   (per-playback registry fork, freed with the playback, per the housekeeping rule); the event carries its NAME, and
   the knobs travel as slots (`eqp("cut.freq", sine.range(200, 2000))`, sections named in the builder:
   `e.lowpass(name = "cut", ...)`). `.eq("<bright dark>")` patterns between shapes. Mirrors the Katalyst playbook and
   keeps the wire small. Cost: a second registry and a naming step for anything patterned.

**Patterning a knob.** Today `lpf(sine.range(200, 2000))` patterns the cutoff per event. With `.eq()` a knob needs an
address: an index (`eq.0.freq`) in option 1, a section name in option 3; option 2 needs one of the two on top.

**When fusion is decided.** Today the optimizer decides at registration, conservatively. With per-note section lists
the natural moment is the voice build (note-on): the stages a note does not write are not built, so the tree a voice
actually runs is known there. Deciding there means caching optimized trees by their resolved shape (a build-cache key
that covers the section list), measured before it is trusted on the audio thread.

## 5. Ideas: together with `classic()` on an authored instrument

- **`classic()`'s four filter stages become one `eq` stage** at the same place (after `distort`, before `tremolo`).
  The written section order is the signal order, so the fixed `highpass -> bandpass -> notch -> lowpass` order becomes
  the author's choice (more flexible; the migration writes today's order explicitly).
- **The instrument's own `.eq()` and the pattern's `eq` could fuse, IF fusion is decided at note-on (section 4).**
  An authored instrument that places its own static EQ in its tree (`Ignitor.saw().eq(e => e.band(300, 1, 2)).classic()`)
  runs it before `classic()`. Today the optimizer runs at registration on the registered tree, where `onepole`,
  `crush`, `coarse` and `distort` are always present; a stage is skipped only at voice build (the runtime returns
  the inner of a gated-off stage). So only when fusion moves to the build do the two `Eq` nodes become adjacent for a
  note that writes none of those stages, and fuse into ONE core per voice. That is the flexibility win: the
  instrument's character and the note's shaping cost one filter pass.
- **Placing the pattern's EQ elsewhere.** An author who wants the pattern's EQ at another position, or without the
  rest of `classic()`, places the slot group directly (`x.eq(Ignitor.slot.eq)`, a sketch), the way a tree can already
  place single slots. A tree without `classic()` and without that slot does not hear the pattern's `.eq()`, the same
  rule the filter doors follow today.
- **Knobs the instrument declares** (`e.lowpass(freq = Ignitor.param("cut", 800))`) stay fillable from the pattern
  (`ignp("cut", ...)`), as today.
- **The bus and the master** already run `EqCore` through the Katalyst `eq` (static, crossfaded on change). With the
  general core, a bus section could gain the envelope or drift too; whether a BUS wants a per-note envelope is a design
  question (a bus has no note), so keep the bus static unless a use appears.

## 6. Migration (if built)

Every built-in song (12 of 14 use `lpf`, about 175 filter calls in total), the tutorials (their own session), the Lexikon,
`.claude/skills/klang-music-writing/ref/sprudel-reference.md` and `ignitor-reference.md`, the `/klang-music-writing` skill, the editor's filter tools
(`sprudel-ui-tools.md`), and the retired list (`docs/retired-names.md`). Render identity of the corpus through the migration is
the acceptance (`classic()`'s order written out explicitly keeps every song identical where the math is unchanged, within the
stated bounds of Part A's parity rows elsewhere).

## 7. Open questions for the design round

1. Option 1, 2 or 3 in section 4 (or a mix: a fixed block for the common case, registered shapes for the rest).
2. How a single section's knob is addressed and patterned (index, name, or both).
3. Whether `lpf`-style shorthands survive as builder spellings (`e.lpf(800)`) or only the long names.
4. The shape of `EqCore`'s ramp tier (Part A's envelope row): reuse `SvfCoeffSweep`'s per-sample steps per section,
   and one API for the voice and the bus (unified-eq D9). A per-block recompute without steps is not an option: it
   zippers.
5. Where fusion is decided (registration or note-on) and what the per-shape build cache costs.
6. Order of work: Part A (the core, pure engine, no surface change) can ship before Part B, and is worth doing even if
   Part B is declined. Its fusion win on its own is limited while fusion is decided at registration: a Param `passes`
   keeps `classic()`'s lowpass and highpass unfused until fusion moves to note-on (question 5).

## Links

- The fusion optimizer and its refusals: `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt` (R1).
- The core: `audio_be/src/commonMain/kotlin/filters/EqCore.kt`; the bus adapter `KatalystEqEffect.kt`.
- The plan the core came from: [`../../plans/unified-eq.md`](../../plans/unified-eq.md) (D9, the ramp API).
- The earlier decision this reopens: the filter-unification record, section C6 (archived, linked above).
- Neighbours: [`onepole-highpass-door.md`](onepole-highpass-door.md),
  [`svf-resonator-class-collapse.md`](svf-resonator-class-collapse.md),
  [`envelope-shape-followups.md`](envelope-shape-followups.md), [`../oversampling-regions.md`](../oversampling-regions.md).
