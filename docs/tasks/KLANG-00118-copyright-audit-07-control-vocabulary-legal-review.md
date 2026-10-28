# KLANG-00118: Copyright Audit 07 — Control-vocabulary legal review

> **Status (2026-10-20): NOT STARTED.**
>
> **Part of:** KLANG-00364 (Out in the world: legal, sharing, outreach)

> **BLOCKED — awaiting external IP counsel. Not startable internally; do not rewrite preemptively.**

**Bucket C · 🟡 lawyer decision · NOT a code task (until counsel advises)**

> **UPDATE (2026-06-24) — task 08 (Tidal comparison) materially narrows this.** A verified comparison
> against Tidal Cycles `Params.hs` (GPL, Copyright 2021 — *predates Strudel*) shows the bulk of the
> vocabulary, **including the short aliases**, originates in Tidal, not Strudel. Confirmed in Tidal:
> the base names (cutoff/resonance/hcutoff/hresonance/bandf/bandq/gain/pan/velocity/note/n/sound/bank/
> begin/end/speed/coarse/crush/cut/distort/accelerate/legato/sustain/attack/decay/release/room/size/
> delay/delaytime/delayfeedback/orbit/vowel/shape) **and** the aliases `lpf`/`hpf`/`bpf`/`ctf`/`lpq`/`hpq`/
> `sz`/`s`/`delayfb`/`delayt`/`sus`/`clip`, **and** the `:`-compound concept itself (`grp`+`wordsBy(==':')`,
> with `sound → s:n`). → **Strong prior-art defense: most of the "looks copied from Strudel" vocabulary is
> actually shared Tidal/SuperDirt community terminology.**
>
> **What still traces to Strudel specifically (the narrowed question for counsel):**
> - bare short forms NOT in Tidal: `lp`/`hp`/`bp`, `res`, `vel`, `comp`, `d`, `uni`, `rsize`, `dist`;
> - the **multi-field (>2) `:`-orderings** that match Strudel's `controls.mjs` and have no Tidal `grp`
    > equivalent: `lpf → cutoff:resonance:lpenv`, `hpf → …:hpenv`, `bpf → …:bpenv`, `adsr`,
    > `compressor → threshold:ratio:knee:attack:release`, `reverb → room:size:fade:lp:dim`,
    > `tremolo → depth:rate:shape:skew:phase`, `delay → delay:delaytime:delayfeedback`;
> - the pitch/filter-envelope families (`penv`/`lpenv`/`*adsr`) — Strudel-era.
    > (Motor-engine extras like `distortshape`/`*os`/`body`/`vowelMix`/`unison` are Klang-original — no issue.)
    > So the genuine Strudel-only residue is **the specific multi-field colon orderings + a few short forms**, not
    > the vocabulary as a whole. See `docs/tasks-archive/2026-06/20260624-copyright-audit-08-*` for the full evidence.

## Context

This is the one genuine judgment call. The DSL's **control-parameter alias clusters** and the **compound
`:`-separated field orderings** reproduce Strudel's `controls.mjs` closely. Individual names are not
copyrightable, and much of this vocabulary predates Strudel (it comes from **Tidal Cycles / SuperDirt**), but
the *reproduced-as-a-set* aliases and the *arbitrary field orderings* are where a copyright argument, if any,
would live. This needs an IP lawyer's eye before relying on it in a commercial product — **do not rewrite
preemptively** (compatibility with the Strudel/Tidal vocabulary is a deliberate product feature).

- **Ours:** `sprudel/src/commonMain/kotlin/lang/lang_filters_*.kt`, `lang_effects_*.kt`,
  `lang_dynamics_*.kt`, `lang_sample_*.kt`, `lang_continuous_*.kt`, `lang_tonal_*.kt`
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
  Note 2026-09-16: the colon packing was already gone (C0), and the reverb door is now `reverb(wet, size, lowpass)`; the
  word `room` and the `fade`/`dim` slots left the surface (KLANG-00208 (reverb naming unification)). Recorded as a fact for
  counsel, not as a legal conclusion.
- `compressor → threshold:ratio:knee:attack:release` is dictated by the Web Audio `DynamicsCompressorNode` →
  a fact, closer to Bucket A.

## Defenses to raise with counsel

- Individual parameter names are not copyrightable (short words / method of operation).
- The vocabulary largely **predates Strudel** (Tidal Cycles / SuperDirt community vocabulary) — Strudel is not
  the author of most of it.
- Interoperability / compatibility necessity (users coming from Strudel expect these names) — functional, not
  expressive, choice.

## Also for counsel: Claude in AUTHORS.MD (filed 2026-10-28, not decided)

The maintainer would like to name Claude in `AUTHORS.MD`, "if there are not legal issues", and to say there who
does what. Filed here until counsel can answer; nothing is changed yet.

**The legal picture as far as we know it (no legal advice):** an AI cannot hold copyright (UrhG §2(2) and §7: a
personal intellectual creation of a human; in the US, the Copyright Office and Thaler v. Perlmutter, 2025), and
Anthropic's terms pass any rights it might have in the outputs to the user. Listing Claude would give Claude and
Anthropic nothing. `AUTHORS.MD` is, by its own words, the authoritative list of copyright holders behind every
header's "The Klangmotor Authors", so Claude under "Copyright holders" would name a holder that cannot hold, and
could suggest parts of the code without a human author. That matters most for the planned commercial or dual
license, which needs a clean chain of title.

**The proposal:** the holders list stays human; below it, two sections:

```markdown
## Who does what

- **Karsten J. Gerber**: the vision and the design decisions, the sound (every
  tuning judged by ear), the songs, the direction of every step, and the final
  review of what goes in.
- **Claude** (Anthropic's AI, working as a team of agent sessions): most of the
  code with its tests and mutation checks, code reviews, technical consulting
  (DSP, CSS, Kotlin/JS performance), UI design drafts, research into papers and
  prior art, first drafts of docs, tutorials and blog posts, the tools and the
  process (console tools, hooks, review loops), and the book-keeping of tickets,
  diary and history; always at the maintainer's direction.

## AI assistance

Claude is credited here as a collaborator, not as a copyright holder: an AI
cannot hold copyright, and the rights in the work stay with the holders above.
Commits Claude wrote or co-wrote carry a `Co-Authored-By` trailer (complete
since August 2026). More in `CREDITS.MD`, "AI Assistance".
```

**Questions for counsel:** does this wording keep the chain of title clean for a dual license; how AI-assisted
code is best described (the share, the human direction and selection); whether the commits before August 2026,
which carry no trailer, need a statement (`docs/funding/gaps.md` G4). **Open with the maintainer:** the honest
share of AI-written code (G4), and whether naming Claude is a deliberate exception to the credits rule that
commercial products are not credited (2026-10-10; `CREDITS.MD` already has its "AI Assistance" section).

## Plan

1. Package the evidence above (the matching alias/field-order list + the divergences) for an IP lawyer.
2. Get a written read on whether the alias-set + field-order reproduction is a concern for a commercial
   license, given the Tidal/SuperDirt prior art.
3. **Only if counsel flags it:** plan a divergence pass (rename/reorder the idiosyncratic clusters) — but weigh
   against the product cost of breaking Strudel compatibility.

## Done when

Counsel has given a written opinion on the control vocabulary, and (if needed) a follow-up divergence task is
filed. Until then this is the primary open legal question for commercialization.

## History

- 2026-10-20: numbered KLANG-00118 (was `docs/tasks/copyright-audit-07-control-vocabulary-legal-review.md`).
- 2026-10-27: part of KLANG-00364 (Out in the world: legal, sharing, outreach), the epic grouping.
- 2026-10-28: not in Q4: no lawyer yet (the maintainer, in the Q4 planning).
- 2026-10-28: the idea of naming Claude in AUTHORS.MD filed here for counsel, with the proposal and its questions.
