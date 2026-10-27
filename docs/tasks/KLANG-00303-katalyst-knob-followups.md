# KLANG-00303: Katalyst knobs: follow-ups

> **Status (2026-10-20): NOT STARTED.**
>
> **Part of:** KLANG-00357 (Effects: the Katalyst and the master)

> Carried out of KLANG-00302 (katalyst knob expressions)
> when that task closed (2026-10-12). Nothing here blocks anything; each item waits for a song that asks for it.
> The V8 items live in KLANG-00290 (engine follow ups) (39 to 41), the listening question in
> KLANG-00313 (delay reverb owner change cut), the duck's orbit switch in
> KLANG-00247 (duck orbit switch click).

## 1. E1, a Katalyst EQ section hierarchy (decision Q2)

The `eq` sections stay `IgnitorDsl.EqSection`, shared with the voice and with `bands`, and each section knob takes a
number or a `Kat.param` (E2, the maintainer's choice, "keep shared, slots only"); an expression or a modulator there
is a script error on the script door (`toEqSectionKatalystKnob` in `klangscript-libs/.../KatalystBuilders.kt`) and an
`IllegalArgumentException` at build time on the typed Kotlin door (Q49; the one helper `eqSectionKnobOrNull` in
`audio_bridge/.../EffectBuilders.kt`, since the typed-doors rollout S3a). E1 is a section hierarchy of the
Katalyst's own on `KatalystKnobDsl`, with `eq` and `bands` split per host: its own task when a song wants a swept bus
EQ.

## 2. A `seed` knob on `Kat.perlin` and `Kat.berlin` (step 5a)

A knob noise's seed is fixed per position in its chain (`KATALYST_KNOB_NOISE_SEED` + the chain-wide ordinal,
`KatalystNoiseOrdinals`; the maintainer's rule, 2026-10-12): two noise knobs in one chain move independently and every
build sounds the same, but two ORBITS whose chains put a noise at the same position walk the same noise. A `seed`
knob on the two doors is a door decision for when a song wants otherwise (both doors, a door-parity row).

## 3. A tape-style delay tap (decision Q9)

A modulated delay `time` steps through `DelayLine`'s 50 ms tap crossfade, a change mid-crossfade parked: "not a
chorus". A tap that glides its read position (a varispeed read, pitch moving with the time) is a feature of its own;
its nearest neighbour is KLANG-00232 (flanger chorus), whose core is a modulated read on
`DelayLine`.

## 4. `blend(wet)` taking a `Kat.param` (decision Q14)

The Katalyst's `blend` takes a plain number for `wet`, a recorded asymmetry (`audio/MEMORY.md`, the `blend` KDoc in
`KatalystBuilders.kt`). `blend` is door sugar, not a stage: it appends a `parallel` of two `gain` stages, whose knobs
are already `KatalystKnobDsl` with a writer, a glide and the per-block tick. Since step 4's arithmetic, lifting Q14 is
a door-only change: the dry branch's gain `Constant(1.0).minus(wet)`, the effect branch's gain `wet`, on both doors
with a door-parity row. Since the typed-doors rollout S6 (KLANG-00314 (kotlin typed doors)) the Kotlin door is a typed member,
`KatalystBuilder.blend(wet: Double, branch)` in `audio_bridge`, plain because the script takes a plain number; lifting
Q14 therefore also changes that public Kotlin signature to `wet: KatalystKnobDsl` (`blend(0.3.kat) { ... }`), its NaN
guard reading only a `Constant`, as the Ignitor's does, and its callers move to `.kat`.

## History

- 2026-10-20: numbered KLANG-00303 (was `docs/tasks/katalyst-knob-followups.md`).
- 2026-10-27: part of KLANG-00357 (Effects: the Katalyst and the master), the epic grouping.
