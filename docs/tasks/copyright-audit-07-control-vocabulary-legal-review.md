# Copyright Audit 07 — Control-vocabulary legal review

**Bucket C · 🟡 lawyer decision · NOT a code task (until counsel advises)**

## Context

This is the one genuine judgment call. The DSL's **control-parameter alias clusters** and the **compound
`:`-separated field orderings** reproduce Strudel's `controls.mjs` closely. Individual names are not
copyrightable, and much of this vocabulary predates Strudel (it comes from **Tidal Cycles / SuperDirt**), but
the *reproduced-as-a-set* aliases and the *arbitrary field orderings* are where a copyright argument, if any,
would live. This needs an IP lawyer's eye before relying on it in a commercial product — **do not rewrite
preemptively** (compatibility with the Strudel/Tidal vocabulary is a deliberate product feature).

- **Ours:** `sprudel/src/commonMain/kotlin/lang/lang_filters.kt`, `lang_effects.kt`, `lang_dynamics.kt`,
  `lang_sample.kt`, `lang_continuous.kt`, `lang_tonal.kt`, `lang/addons/lang_effects_addons.kt`
  (the `@alias` tags + the `parts.getOrNull(0..n)` compound packing).
- **Theirs (AGPL):** `/opt/dev/strudel/packages/core/controls.mjs` — `registerControl([...], aliases…)`.

## Evidence to hand to counsel

Alias clusters that match:

- `lpf ← cutoff, ctf, lp` ↔ `registerControl(['cutoff','resonance','lpenv'],'ctf','lpf','lp')`
- `hpf ← hcutoff, hp`; `resonance/res/lpq`; `roomsize/rsize/sz/size`; `delayfeedback/delayfb/dfb`;
  `phaser/ph/phc/phd/phs`; tremolo/`trem…` family — all match.

Compound `:`-field orderings that match (the ordering is arbitrary expression):

- `s → name:n:gain` ↔ `['s','n','gain']`
- `lpf → cutoff:resonance:lpenv`; `hpf → hcutoff:hresonance:hpenv`; `bpf → bandf:bandq:bpenv`;
  `delay → delay:delaytime:delayfeedback`; `phaser → phaserrate:phaserdepth:phasercenter:phasersweep`.
- Divergences where Klang already did its own thing: `distort → distort:distortShape:distortOversample`
  (Strudel `['distort','distortvol','distorttype']`), `room → room:size:fade:lp:dim` (Strudel `['room','size']`).
- `compressor → threshold:ratio:knee:attack:release` is dictated by the Web Audio `DynamicsCompressorNode` →
  a fact, closer to Bucket A.

## Defenses to raise with counsel

- Individual parameter names are not copyrightable (short words / method of operation).
- The vocabulary largely **predates Strudel** (Tidal Cycles / SuperDirt community vocabulary) — Strudel is not
  the author of most of it.
- Interoperability / compatibility necessity (users coming from Strudel expect these names) — functional, not
  expressive, choice.

## Plan

1. Package the evidence above (the matching alias/field-order list + the divergences) for an IP lawyer.
2. Get a written read on whether the alias-set + field-order reproduction is a concern for a commercial
   license, given the Tidal/SuperDirt prior art.
3. **Only if counsel flags it:** plan a divergence pass (rename/reorder the idiosyncratic clusters) — but weigh
   against the product cost of breaking Strudel compatibility.

## Done when

Counsel has given a written opinion on the control vocabulary, and (if needed) a follow-up divergence task is
filed. Until then this is the primary open legal question for commercialization.
