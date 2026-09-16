# Ignitor graph optimizer: open items

Status: **future, decide by measurement.** The catalogue of what the optimizer
(`audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`) deliberately does NOT claim, each
item with its reason and its trap, plus the two halves of the arithmetic-fold plan that stayed
open when the rest was archived (`docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md`).
The rules that exist: R1, serial filter fusion into one `EqCore` pass; R2, the arithmetic fold
into `Affine`. The promise every entry must respect: only adjacent nodes combine, only under
linear algebra, never across a nonlinear node, never absorbing a shared subtree, and the
optimized graph renders within `OPTIMIZER_PARITY` of the authored one (the guards are named in
the archive record). Every new rule brings rows to `IgnitorDslOptimizerSpec` first, red, and is
mutation-checked.

Recommended order, 2026-09-16, after the measurements of that week: item 1 (pitch-mod walls) is
the one worth revisiting, measured first the way the guitar rig was; items 2 to 4 only if a
current song writes the shape; the rest is recorded so nobody redoes the reasoning.

## 1. Pitch-mod nodes are walls, but they vanish at runtime

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


Measure first: the current song's guitar and vibes voices with and without the wall, on node
(`audio_benchmark`, a pair of inline rows as `guitar-rig*` did it), before writing the
mod-threading proof.

## 2. Parallel tap fusion

> **Re-specified NON-PARITY by C2 (filter unification, 2026-08-24):** the bandpass family is
> unity-peak now (EqCore RAW_TAP, the ignitor svf kernel and SvfBPF all scale the v1 tap by
> the stored k), so R2's acceptance criterion is NO LONGER bit-parity with the legacy
> Plus/Times graph — both sides are normalised, and a fused tap must match the NORMALISED
> unfused chain. The old coupled behaviour is not an oracle for anything any more.

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


Worth it only if a current song writes the shape: grep the builtin songs for
`.add(` over a `.bandpass(` of the same source before starting.

## 3. Merging adjacent `Eq` nodes (only together with item 2)

`Eq(Eq(x, s1), s2)` arises from `.eq(e => e.band(a)).lowpass(b).eq(e => e.band(c))` and similar. Merging the
section lists looks trivially safe for serial sections, and is.

**It is NOT safe when `s2` contains a `RawTap`.** A tap reads the input of ITS OWN Eq. In the
nested form that input is the inner Eq's OUTPUT; after merging it would be the outer input `x`.
Different sound, no error. So this rule must refuse when the outer section list contains any tap,
or reproduce the inner chain for the tap's source, which is not free.


## 4. One-pole sections

`onepole()` (the one-pole lowpass) never fuses, because `EqCore` has no one-pole section type and
substituting an SVF would change the sound. Adding `ONEPOLE_LP` / `ONEPOLE_HP` section types is
mechanical; note that `OnePoleHPF` carries a documented cutoff bias that is deliberate raw-engine
character and must be reproduced exactly, not "fixed".


## 5. Step 5 of the arithmetic-fold plan, the build-time half

The per-block half is BUILT (2026-09-15, inside step 4b): a block-constant zero multiplier or
divisor is a dead branch in `Times`, `Affine`, `Div`, the scalar doors and an `EqCore` tap; a
Param at zero costs nothing upstream per block. What stayed open is the BUILD-time half: not
constructing the dead subtree at all when `Osc.param` values are known at voice build. Building
runs once per note, so this pays only where a switched-off stage has an expensive build; the
supersaw's phase pool (candidate phase sets scored at note-on) is exactly that. Two things to
settle first: a build-time measurement of such a voice, and the draw-order question round 2 of
step 4b raised (a subtree that is not built does not take its build-time draws, so every pool
built after it draws differently than with the branch live; acceptable, since the branch being
off IS a different instrument, but it must be the same on the authored and the optimized side).
The dropped identity nodes (`add(Constant(0))`, `mul(Constant(1))`) buy nothing measurable and
are not worth a rule on their own; `mul(Double)` already short-circuits `1.0` in the engine.

## 6. Step 6, the close

The census of one guitar note (see the archive record) predates the maintainer's removal of the
guitars' EQs and the bass grind from Der Schmetterling; redo it on the current song before the
next optimizer decision. The device before/after (the Fairphone 4) is the maintainer's at the
next deploy; the JVM and node numbers are in the archive record.

## 7. Refinements recorded and not taken

- Merging two nested Affines, algebraically or as one fused runtime pass:
  `docs/tasks/future/affine-chain-fusion.md` (last in line, more complexity than gain).
- A literal reciprocal composing with a following literal run (`div(4).mul(2)` as one node):
  needs `SAFE_MIN`/`SAFE_MAX` in the bridge to reproduce the runtime's guard; the expression form
  costs one guarded divide per block.
- Optimizer rounds to a fixpoint: not needed, the post-order pass already reaches it (the fuzz's
  idempotence law); see the chain-fusion note.

## 8. Not to do

### `analog > 0` filters

Permanently excluded for `Lowpass`/`Highpass`: a non-zero analog switches on the state-dependent
saturating branch, which is character `EqCore` does not implement, and the house rule is that the
Motor stays raw.

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

### Variants

Each variant subtree is rewritten independently, which is correct, but sections are never shared
between variants even when identical. Harmless; noted only so nobody assumes otherwise.

### Cross-`Eq` section deduplication

Two identical sections in one list (the song really does write `.lowpass(5250).lowpass(5250)`)
are kept as two sections, correctly: cascading two identical filters is a steeper slope, not a
redundancy. Do NOT "optimize" this away.


## Measurement note

Before claiming a win from any of the above, read the D0 comparability rule in
`audio_benchmark/src/commonMain/kotlin/EffectBenchmark.kt`: the chained benchmark rows render a
source inside the timed step while the EqCore rows only copy, so raw row ratios overstate the win
substantially. Subtract the source baseline. This has already caused two wrong numbers to reach
user-facing docs.

