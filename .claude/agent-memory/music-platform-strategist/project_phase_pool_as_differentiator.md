---
name: phase-pool-as-differentiator
description: Prior-art verdict on the unison phase pool (novel combination, one genuinely novel element) and its strategic role — de-risks the metal flagship, is the marquee A/B demo, and is the warm outreach asset for contributor recruitment
metadata:
  type: project
---

# The unison phase pool as a strategic asset

Karsten, 2026-08-19: *"we have made a geniune invention with the phasepool which leads us toward rock and metal
guitars... something noone has done before (or at least this is what i assume)."*
Technical record: `/home/gerk/.claude/projects/-opt-dev-peekandpoke-klang/memory/project_unison_phase_pool.md`.

## What the invention actually is (stated precisely, so the claim can be tested)

Best-of-M **rejection sampling of unison phase vectors**, scored against a **target band** of resulting
fundamental amplitude (not a maximum, not random), cached in a **reusable pool** keyed by
(orbit, unisonCount, sideAtten) so the search cost amortizes and stays real-time viable.
Per-waveform-family bands (saw 0.30–0.55@5 / tri 0.40–0.65@16 / sine 0.50–0.80@40), tuned by ear.
Measured: onset holes **15.8% → 0.8%** (P0), **0/120 holes** steady state, 0.95 dB sd.

## Prior-art check (WebSearch, 2026-08-19)

**Established and thoroughly worked ground — do NOT claim novelty on any of this:**
- Per-note phase randomization in unison (Szabo's JP-8000 supersaw thesis; Serum's per-osc `RAND`; universal).
- Phase *spread* controls (Omnisphere Unison "Phase Control": five options from in-phase to 180° out).
- Retrigger vs. free-running oscillator phase. Universal since analog.
- **1/√N statistical gain compensation** for unison level loss — this is what professional plugin DSP developers
  propose when asked *exactly this question* (KVR DSP forum thread "Compensating for unison phase issues").
- **Crest-factor phase optimization** (Schroeder 1970; Van der Ouderaa iterations) — the nearest *algorithmic*
  relative. Choosing the phases of a multisine to control peak-to-RMS. **Different objective** (crest factor,
  not fundamental amplitude), **different domain** (measurement excitation signals), **different method**
  (analytic/iterative optimization, not best-of-M sampling into a cache).

**Found NOWHERE in the search:**
- Scoring candidate phase *vectors* by resulting **fundamental amplitude**.
- Selecting into a **band with both a floor and a ceiling** rather than maximizing or randomizing.
- A **precomputed, keyed, reusable pool** of vetted phase vectors for real-time note onsets.
- Treating "onset holes" as a *measurable defect with a rate* and driving it to zero deterministically.

The KVR thread is the strongest single piece of evidence: professional plugin developers, on exactly this
problem, converge on statistical gain compensation and **explicitly never propose measuring the fundamental or
selecting phase sets**.

## VERDICT: **Novel combination of known parts, with one element that qualifies as a genuine small invention.**

The components are all known. The *combination* (banded rejection sampling + keyed pool for real-time onsets)
turned up no prior art. The element I would defend as genuinely inventive is **the banded target**: the insight
that *maximizing* the fundamental is wrong because K=1 is phase-locked-thin, so the target needs a ceiling as
well as a floor. That is non-obvious — the naive engineering instincts are "maximize" or "randomize and
gain-compensate", and the latter is demonstrably what the professional field does.

⚠️ **Epistemics caveat, load-bearing:** absence of evidence in a web search is weak evidence of absence.
Serum, Diva, Pigments, Omnisphere and Vital are closed source. Any of them could do something similar and
nobody outside would know. **Never claim "nobody has done this before" in public** — it is unfalsifiable, and it
invites exactly one reply from a DSP person: "actually, X already does that."

## How to apply strategically

1. **It de-risks the metal flagship, which was the worst-engine-fit genre.** Note the precise mechanism, and do
   not conflate two different problems: the phase pool fixes **reliability** (notes that arrived hollow or did
   not ring, concentrated at low pitch = exactly where downtuned rhythm guitar lives). The on-record
   *"too machine-perfect to be a band"* critique is about **expressiveness** (identical timing and dynamics).
   These are complementary axes. A real guitarist does not produce 16% dead notes, so fixing holes moves
   *toward* realism; and per the Master Boot Record positioning, the expressiveness axis does not need fixing
   at all, because deliberately dehumanized is a legitimate destination.
2. **The A/B is the marquee asset, and it is structurally unmatchable.** `.phasePool(on/off)` toggled live in an
   open song page, with the code visible, is a demo **no plugin company can offer, because their unison logic is
   a black box**. This is the strongest single expression of the "the song must be good enough that the code is
   worth opening" thesis found all session, and the measured numbers (15.8% → 0.8%) make it credible rather than
   merely impressive.
3. **Marquee moment for the metal flagship's dissection tutorial.** Highest-stakes "listen for" A/B in the whole
   library. ⚠️ But the flux ruling still binds: teach **intent + ear** ("hear the dead notes disappear"), never
   the band arithmetic, `drawTries`, or pool internals.
4. **Lead with the measurement, not the novelty claim.** "We measured 16% of low-pitch unison onsets coming out
   hollow; here is the fix; here is 0%" is unattackable and more impressive to the audience that matters than
   "nobody has done this before."
5. **IP posture: AGPL publication is fine here, arguably good.** (Strategic frame, not legal advice.) The
   invention is published, so it cannot be a trade secret — but **publication is defensive**: it is prior art,
   so nobody else can patent it and fence Klang in later. A solo AGPL project will never out-litigate a plugin
   company; not being blockable is worth far more than being able to block. **The real moat is not the algorithm
   but the integration** — a competitor could copy the maths and get slightly better unison; they cannot copy
   "an open song page where you toggle it and hear it."
6. **A phase-pool BLOG POST IS ALREADY DRAFTED** (confirmed by Karsten 2026-08-19) and already follows the
   measurement-first framing above. Publication timing not yet set.
   **Timing view: this is the one asset that arguably does NOT need to wait for the "presentable" gate.** That
   gate exists to protect first impressions *of the platform*; an engine write-up is judged as engineering
   writing, not as a product demo, and publishing it early accrues the "what does Klang give back" credit
   *before* any outreach, so first contact later is warm instead of cold.
   ⚠️ **The condition:** the moment the post links to a live demo or song page it becomes a product impression
   and the gate re-applies. So publish it as engineering writing (plots, audio examples, the measurements) and
   hold the "come and try it" call-to-action back until the gate opens.
7. **It is the warm outreach vehicle for contributor recruitment.** [[reference_contributor_prospects]] already
   asks "what does Klang give back?" and answers "publishable engine work." This is exactly that: concrete,
   measured, novel-ish, and interesting to the Strudel / Tidal / Glicol / Elementary people. It converts a cold
   recruitment ask into "here is something we learned, does it help you", which the list's own ground rules say
   converts far better. Write it up **with the measurements**.
