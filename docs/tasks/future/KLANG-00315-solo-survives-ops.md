# KLANG-00315: Solo survives the ops after it

> **Status (2026-10-20): IDEA.**
>
> **Part of:** KLANG-00359 (Sprudel: the pattern language's surface)

Status: **wanted (maintainer, 2026-10-16: "solo survives ops"), design open, not started.** Split out of engine
follow-up 43 (KLANG-00290 (engine follow ups), review round 2), which
shipped with the docs saying what is true today.

## The problem

`.solo()` keeps a source soloed through control events on a 1/8-cycle grid (`SoloPattern`). A structural op AFTER
`.solo()` filters those control events like any other, so the solo lapses in the op's gaps and, since item 43 removed
the 2 s hold, the rest of the mix swells back up in every gap:

```
note("c e g c").struct("x ~").solo()   // whole: solo last covers the rests
note("c e g c").solo().struct("x ~")   // gaps: the others swell to about 85 % in every rest, then duck again
```

The same with `mask`, `degradeBy` (random pumping), `filterWhen`, `when`, `compress`/`fastGap`, segment, bind/pick,
probably euclid and swing. The time transforms (`slow`, `fast`, `late`, `rev`, repeat) carry the control events
already; after `.solo().slow(n)` the grid is n/8 of a cycle.

## Two designs (found in item 43's round 2)

1. **Per-op pass-through:** each filtering op lets solo control events through untouched. Sprudel has no central
   filter helper (each op class filters in its own `queryArcContextual`), so about ten classes change, each with a
   row. The test must be solo control (`control == true && solo != null`): `master(...)` and `katalyst(...)` events
   are control events too and must keep today's behaviour.
2. **Coverage out of band:** `SoloPattern` reports its coverage through the query context, and only the time
   transforms map it. One mechanism instead of many edits, but a new indirection: by the stone rule "complexity is
   the enemy", consult the maintainer before choosing it.

Decide when sprudel's structure is next worked on.

## History

- 2026-10-20: numbered KLANG-00315 (was `docs/tasks/future/solo-survives-ops.md`).
- 2026-10-27: part of KLANG-00359 (Sprudel: the pattern language's surface), the epic grouping.
