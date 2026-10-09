# `parallel`, `serial` and `bands`: branches side by side, and frequency bands

Status: **in progress since 2026-10-10, on the branch `parallel-serial-bands` (worktree
`klang-worktrees/parallel-serial-bands`).** Step 1 started. The design, the decisions and the reasons live in
[`../../plans/future/signal-graph-engine.md`](../../plans/future/signal-graph-engine.md) §6.9; this file holds the steps.

## Why

Listening to the Katalyst `distort` stage on Kokon's master, the maintainer heard the glue, and the hats and the kick
distorted with it. Engineers solve that two ways: saturating only some bands, or saturating per group. One operator,
`parallel`, gives multiband saturation, the exciter, parallel compression, parallel saturation and any dry/wet as
one-liners (the plan's back-pocket table, also material for the tutorials).

## Decided (maintainer, 2026-10-09)

- `parallel` and `serial` are the names. `through` retires for `serial`: the same behaviour, removed, not deprecated.
- `parallel` SUMS its branches; an empty `parallel()` returns the signal unchanged; one branch is that branch's output.
- `bands` keeps its name: `b.band(...).cut(f).band(...)`, read from the bottom up, one builder type. `band().band()`
  sums both processors on one band, an unprocessed band passes untouched, and a cut below the one before it is
  coerced up to it.
- Every step ships something to hear: recipes in the writing references and listening material for the maintainer.
- The credits land with the code that uses them (Linkwitz and Riley with `bands`).

## Steps

1. **`serial`:** the rename on both hosts (Ignitor and Katalyst). This migrates the built-in songs (Kokon 4 calls,
   Der Schmetterling 2), both doors, the parity spec, the two writing references and a benchmark case, and adds an
   entry in `docs/retired-names.md`.
2. **`parallel` on the Ignitor:** the sum law, the empty identity, and branches ALIGNED BY LATENCY (an oversampled
   branch is 4 to 6 samples late; Kokon's Screamer pedal shows the comb this avoids).
3. **`parallel` on the Katalyst:** a stage holding branches of stages. A branch's tail and latency count for the chain,
   and one block buffer per branch is allocated at build. This makes "distort only the mids on the master" possible.
4. **`bands`, on both hosts,** built on `parallel`.
5. **Optional:** a dry/wet helper, `x.blend(wet, y => ...)`.

## Defaults taken when the work started (coordinator, 2026-10-10; the maintainer may reverse any of them)

The maintainer asked for the steps to run in a loop, stopping only at a real decision. These three were open; the
coordinator took its leans so the loop could start:

- **The crossover:** Linkwitz-Riley. An untouched `bands` is then an all-pass: flat in level, the phase turned at each
  cut, which is documented. Complementary by subtraction and linear phase stay the alternatives.
- **The helper:** `blend`, linear law only, built last.
- **Step 3:** built directly, not in the Motor Lab first.
