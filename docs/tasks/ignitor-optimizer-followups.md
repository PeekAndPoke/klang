# Ignitor graph optimizer — what it does NOT claim yet

The optimizer (`audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`) deliberately ships
covering the common case reliably rather than every case. This is the catalogue of what it leaves
on the table, each with the reason and the trap to watch for. Ordered by expected value.

**The invariant every entry below must respect:** nothing ever moves. Only adjacent fusible
filters collapse. That is stricter than the maths requires, because a gain multiply commutes with
a linear filter on paper but `k * lowpass(x)` and `lowpass(k * x)` do not produce the same bits,
and bit-identity is the hard promise. Any future rule that reorders is a different project with a
different guarantee.

## 1. R2 — parallel tap fusion (biggest win, not implemented)

`Plus(base, Times(Bandpass(source, f, q, analog = 0), gain))` where the base chain reads the same
`source` is exactly a `RawTap` section, and `EqCore` already implements RAW_TAP. Without this
rule, any song that hand-built a parallel boost bank keeps paying separate `Plus`/`Times`/
`Bandpass` nodes plus a `MemoizingIgnitor` copy per extra consumer.

Der Schmetterling's guitar is the live example: it still ships the hand-built
`signal.add(signal.bandpass(...).mul(...))` form in the repo (the maintainer has a `.tap()`
migration in progress locally, but song files are the maintainer's to commit). Until either
R2 lands or that migration is committed, that song pays the unfused parallel bank — and every
other song with the shape does too. R2 is what makes them all fuse with zero edits.

Preconditions, all mandatory and all learned the hard way:
- match the tap source by REFERENCE identity (`===`), never structural equality: two structurally
  equal noise nodes are independently phased and independently seeded, so a structural match
  would fuse a tap of B onto A, audibly wrong and invisible to a structural spec;
- only from a LEFT-NESTED `Plus` spine, because IEEE addition is not associative;
- `gain` must be structurally `Constant`/`Param`, never an expression: the section resolves it
  once per block while the `Times` node multiplies per sample, so an LFO gain would become a
  staircase (this is documented on `.tap()` itself);
- `Plus`, `Times` and `Bandpass` must each be refcount-1.

## 2. Merging adjacent `Eq` nodes — WITH A REAL TRAP

`Eq(Eq(x, s1), s2)` arises from `.eq().band(a).lowpass(b).eq().band(c)` and similar. Merging the
section lists looks trivially safe for serial sections, and is.

**It is NOT safe when `s2` contains a `RawTap`.** A tap reads the input of ITS OWN Eq. In the
nested form that input is the inner Eq's OUTPUT; after merging it would be the outer input `x`.
Different sound, no error. So this rule must refuse when the outer section list contains any tap,
or reproduce the inner chain for the tap's source, which is not free.

## 3. One-pole sections

`warmth()` / `onePoleLowpass()` never fuse, because `EqCore` has no one-pole section type and
substituting an SVF would change the sound. Adding `ONEPOLE_LP` / `ONEPOLE_HP` section types is
mechanical; note that `OnePoleHPF` carries a documented cutoff bias that is deliberate raw-engine
character and must be reproduced exactly, not "fixed".

## 4. `analog > 0` filters

Permanently excluded for `Lowpass`/`Highpass`: a non-zero analog switches on the state-dependent
saturating branch, which is character `EqCore` does not implement, and the house rule is that the
Motör stays raw.

`Bandpass`/`Notch` are a different case, and the reason matters because the obvious relaxation
is a trap. At the ignitor level `saturate = analogVal > 0.0 && (mode == LOWPASS || mode ==
HIGHPASS)` (`IgnitorFilters.kt`), so `analog` contributes NOTHING to a bandpass or notch output.
It is nonetheless read every block, unconditionally — and for an EXPRESSION-backed analog that
read is a full scratch render which advances LFO phase and consumes the voice RNG stream.
Dropping it therefore breaks bit-identity and shifts every later draw, which is the bug class
this workstream already shipped once.

So: only a structurally BLOCK-CONSTANT analog on Bandpass/Notch could ever be safe to relax,
plus a proof that no read is lost. Block-constant, not literally `Constant`: a `Param`-backed
analog is equally free of scratch render, state and RNG (it reports a control-rate scalar, which
is exactly why `EqIgnitor.Section.isStatic` groups `ParamIgnitor` with `ConstantIgnitor` and why
`SvfIgnitor` caches on `is ParamIgnitor`). Do not relax it on the grounds that "analog does
nothing here".

## 5. Pitch-mod nodes are walls, but they vanish at runtime (a real missed win)

`x.lowpass(a).vibrato(5, 0.2).lowpass(b)` emits TWO Eqs, because the optimizer treats `Vibrato`
as an opaque node. But `Vibrato` never becomes an Ignitor: `buildIgnitor` absorbs it into
`accumulatedMod` and bubbles it to the source, so at RUNTIME the two filters are adjacent and
could have been one Eq. Same for `Accelerate`, `PitchEnvelope`, `PitchMod` and `Fm`.

Fusing across them looks bit-safe on inspection — the fused form threads the mod through
`inner.withMod()` while section params stay `noMod()`, which is the shape `EqIgnitorSpec`
already pins — but "looks safe" is not the standard here, and it is not claimed. Anyone
implementing it must prove the mod-threading equivalence with a rendered parity row per
pitch-mod node type, not by reading the builder.

⚠ And it carries the trap that already bit this pass once with `OptimizerHint`: a pitch-mod
node that vanishes at runtime is refcount-1 EVEN WHEN THE NODE BELOW IT IS SHARED, so a guard
that refcounts `original.filterInner()` would wave a shared subtree straight through and fork
it. Any see-through rule must require EVERY unwrapped link to be exclusively owned, not just
the last — see the `while (originalInner is OptimizerHint && ...)` loop in the optimizer.

## 6. `passes` expansion

Not applicable yet — the field does not exist. When `passes` lands (plan D6), the rule "R1 learns
to expand `passes = N` into N sections in the SAME commit that adds the field" is the protection
against a window where a `passes = 2` filter fuses as one section and quietly loses 6 dB/octave.

## 7. Variants

Each variant subtree is rewritten independently, which is correct, but sections are never shared
between variants even when identical. Harmless; noted only so nobody assumes otherwise.

## 8. Cross-`Eq` section deduplication

Two identical sections in one list (the song really does write `.lowpass(5250).lowpass(5250)`)
are kept as two sections, correctly: cascading two identical filters is a steeper slope, not a
redundancy. Do NOT "optimize" this away.

## Measurement note

Before claiming a win from any of the above, read the D0 comparability rule in
`audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`: the chained benchmark rows render a
source inside the timed step while the EqCore rows only copy, so raw row ratios overstate the win
substantially. Subtract the source baseline. This has already caused two wrong numbers to reach
user-facing docs.
