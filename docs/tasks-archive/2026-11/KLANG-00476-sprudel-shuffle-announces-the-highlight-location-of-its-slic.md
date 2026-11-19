# KLANG-00476: sprudel: shuffle announces the highlight location of its slice count

> **Status (2026-11-19): DONE.** shuffle lights up its slice count, commit 1422c7569

## Why this exists

The maintainer, 2026-11-19: "the sprudel .shuffle() function. It does not announce the code highlight locations yet."
The shuffled events kept the locations of their own atoms, but the slice count `n` never lit up, while `fast` and
`ply` add the location of their factor to every event.

## Decide before implementing

Nothing; the fix follows `TempoModifierPattern` and `ply`'s `withFactorLocation`.

## The work

- `ShufflePattern` prepends the innermost location of the `n` event it samples at the start of each cycle to every
  event of that cycle. A cycle whose `n` is a rest, or the default `n` without a location, adds nothing.
- The class KDoc no longer says the permutation changes every cycle: it is seeded by `n` and the context's seed, not
  by the cycle.
- `ShuffleLocationSpec` (next to `PlyLocationSpec`): `shuffle(2)`, `shuffle("<2 4>")` over two cycles, a rest in
  `n`, and the mapper form `apply(shuffle(2))`.

## Acceptance

- `:sprudel:jvmTest` green (3532 tests before the rest case was added; `ShuffleLocationSpec` ran).
- Mutation-checked: dropping the `prependLocation` turns the spec red; taking the location from cycle 0 instead of
  the sampled cycle turns the control-pattern and rest cases red.
- One review round at rung 2, clean; its two MINORs (the KDoc sentence, the rest case) applied.

## Open, not part of this ticket

- The door's KDoc (`shuffle` in sprudel's random functions) says "plays them in a new random order each cycle", but
  the order is the same for every cycle with the same `n`. Either the behaviour or the text is wrong; the maintainer
  decides.
- `scramble` goes through `bite` and `irand`, not `ShufflePattern`; whether it announces its `n` was not checked.

## History

- 2026-11-19: created.
- 2026-11-19: done in commit 1422c7569.
- 2026-11-19: DONE. shuffle lights up its slice count, commit 1422c7569
