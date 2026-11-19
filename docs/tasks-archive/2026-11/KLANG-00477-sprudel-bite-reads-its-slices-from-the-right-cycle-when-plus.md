# KLANG-00477: sprudel: bite reads its slices from the right cycle, when plus bite works

> **Status (2026-11-19): DONE.** bite reads its slices from the right cycle, commit e6806668c

## Why this exists

The maintainer, 2026-11-19: "I would like to be able to use this form when("<0 1>", x => x.bite(2, "1 0")) so we
need to figure out the issue between when and bite first... and the misbehaviour of bite." The form is meant to
replace the `shuffle("<1!80 2!48 1!112 2!32>")` hack in Der Schmetterling, which swaps the halves of a cycle only
because `shuffle(2)` happens to pick the order `[1, 0]` under seed 0 (KLANG-00476 found that the order ignores the
cycle).

Found by probing:

- `when("<0 1>", x => x.bite(2, "1 0"))` leaked swapped events into the cycles where the condition is 0.
- `bite("<1 2>", "<0 [1 0]>")` played the `n = 1` cycle twice at double speed.
- `scramble("<2 4>")` played 2 slices instead of 4 in its `n = 4` cycle (it plays through `bite`).

## The cause

`when` was innocent: it keeps the source events of the cycles where the condition is on and hands only those to
the transform. `bite` built each slice as `zoom(i/n, (i+1)/n).fast(steps of the indices)` and queried it over the
index event's own span. For any index event after the first, `fast` maps that span to a later cycle of the zoomed
slice (in `"1 0"` the second half of cycle `c` read cycle `2c + 1`), so `bite` read slices from the wrong cycles.
`when` empties the off cycles, so the wrong reads became audible there. A source that repeats every cycle hid it,
and every existing `bite` spec and compat example had one. Patterned indices with uneven steps sped whole cycles up.

## The work

- `applyBite` (sprudel's structural window functions): `n` stays the outer bind (the result's steps are still
  `n`'s), the index events bind inside it, and each one squeezes its slice into its own whole, keeping the cycle it
  sits in (Strudel's `bite`, a cycle-keeping focus, `_focusKeepingCycle`, the mapping of the `focus` door).
- The slice number is `index mod n` (Strudel's `(i/n) mod 1`) instead of `index mod (the source's steps)`, and a
  source without steps is no longer silent.
- `when` and `scramble` are unchanged.
- Specs: `LangBiteSpec` (a source that changes per cycle, a source slowed over two cycles, a patterned `n` with
  patterned indices, and the maintainer's `when` form); `LangScrambleSpec` (a patterned `n`). Three new
  `JsCompatTestData` examples with sources that change per cycle.

## Acceptance

- `:sprudel:jvmTest` green: 3540 tests, `LangBiteSpec` 15 after the last case.
- Mutation-checked: dropping the cycle from the focus turns `LangBiteSpec` red; restoring the old
  `fast(steps of the indices)` turns exactly the new `bite` cases red (the `when` case by its own values) and the new
  `scramble` case red. The slowed-source case came with the review's minor batch and was not checked separately.
- One review round at rung 2, clean; from its minors the wrong KDoc pointer, a missing brace pair and the
  slowed-source case were applied.
- NOT verified: the three compat examples. `JsCompatTests` only run on a GraalVM JVM, and none is installed on the
  agents' machine, so all 486 compat cases skip there. The reviewer's reading of Strudel predicts they match.

## Open, not part of this ticket

- A note that crosses a slice edge keeps its whole: in a swap, its tail at the start of the swapped half does not
  sound again, and its head rings past the half. Strudel clips the whole to the index event and sounds the tail
  again. The `shuffle` hack behaves like Klang's `bite` here. The maintainer decides.
- Continuous indices that are not segmented (`bite(4, irand(4))`) now depend on how the query is chunked; Strudel
  is silent for them.
- A negative index over a source that changes per cycle may read a different cycle than Strudel; a compat
  example on a GraalVM machine would settle it.
- `bite` and `scramble` do not light up their `n` or indices in the live highlighting (KLANG-00476 did this for
  `shuffle`).
- `shuffle`'s order ignores the cycle and the seed `seed()` sets (KLANG-00476).

## History

- 2026-11-19: created.
- 2026-11-19: done in commit e6806668c.
- 2026-11-19: DONE. bite reads its slices from the right cycle, commit e6806668c
