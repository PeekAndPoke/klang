# Tutorials — fix the current set + build the through-line

> **Stub — not started (2026-07-04).** Priority: **MUST** — this is the core of Q3 Act 2 ("the quarter
> of tutorials") and comes **first**, before any expansion. Expansion is a separate doc
> (`tutorial-master-plan.md`), done *after* this.

## Context — what exists

38 tutorials have shipped, produced by the `tutorial-factory` skill:

- `src/commonMain/kotlin/pages/docs/tutorials/` — `TutorialRegistry.kt` + the `tut_*.kt` files.
- Build log: `src/jsMain/kotlin/pages/docs/tutorials/GENERATION_LOG.md`.

Per the Q3 plan, the problem is quality and coherence, not coverage: many tutorials have
**ugly-sounding examples** and don't reach the **core** of what they teach, and the path from the basics
(a plain saw) up to a fully crafted sound **isn't a coherent through-line** — it's a scattered set of demos.

## Two workstreams

1. **Fix the current set.** Audit all 38. For each: make the example *actually musical* (sound first)
   and make sure it teaches its core concept clearly. Fix the ugliest-sounding ones first.
2. **Build the through-line.** Design a coherent spine — plain saw → … → fully crafted sound — where each
   tutorial is a clear step along that line. Re-sequence / re-frame the existing tutorials onto the spine;
   fill only the gaps the spine needs (big expansion is the other doc).

## Method (sound first)

- Lean on the `/klang-music-writing` and `/klang-music-recording` skills + the tutorial factory to keep
  examples musical.
- Workflow: **Claude frames, the user polishes the jingle, Claude works backwards** from the polished
  jingle (feedback `tutorial_workflow`).
- Quality bar (feedback memories): per-orbit effects, `chord()` + `voicing()` syntax, pan 0.0–1.0,
  musicality over complexity (`tutorial_quality`); content for **adults that kids also enjoy**, not the
  reverse (`design_for_adults`); complex topics span **multiple** tutorials across difficulty levels,
  not one dense tutorial (`tutorial_series`).

## Out of scope (→ `tutorial-master-plan.md`)

The large concept/showcase **expansion** backlog (euclid, struct/mask, swing, the sometimes-family,
orbits, pattern-math, FM / super-osc masterclasses, the "Make a…" showcases). That comes *after* this
fix-first pass lands.

## Links

- `tutorial-master-plan.md` — the expansion backlog (the "then expand" half).
- `mini-notation-extensions.md` — the Phase-2 attribute-blocks tutorial slots into the spine.
- Q3 plan: `../history/2026-Q3.md` (Act 2).
- Registry `TutorialRegistry.kt`; build log `GENERATION_LOG.md`.
