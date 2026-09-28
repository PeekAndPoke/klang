# Affine chain fusion: two nested Affines as one pass

Status: **future, last in line.** Maintainer, 2026-09-16: "one of the optimizations you only do
when there is nothing else to be done"; the hunch is more complexity for little gain, and the
numbers so far agree. Parked here so the reasoning is not redone.

## The shape

After rule R2 (`docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md`, steps 1, 2 and 4b) an authored chain
`x.add(a).mul(m).add(b).mul(k)` lowers to two nested `Affine` nodes, since nothing merges across
an addition. `mul(2).add(2).mul(2).add(2)` is two passes over the block where one would do.

## Why not the algebra

Merging the two into `Affine(x, pre = p1, mul = m1·m2, add = m2·(a1 + p2) + a2)` is exact in
algebra and off by rounding of the INTERMEDIATE terms scaled by `m2` in floating point. When the
intermediate cancels (`x.mul(1e9).add(-1e9)` on a signal near 1) the output is small and that
deviation is unbounded relative to it; the block-scale parity oracle cannot pass it and should
not. This is the one fold in the plan with no bounded relative error, which is why R2 stops at an
addition.

## Why not the runtime fusion either, yet

The precise alternative: an `AffineIgnitor` whose inner is another `AffineIgnitor` with
block-constant coefficients collects the stages and applies them per sample in ONE loop. Bit for
bit the chain's arithmetic, the intermediate pass gone, the dead-branch rule carrying over (a zero
multiplier in any stage means nothing below it renders), no DSL node, no wire change, no optimizer
rule, no oracle change; the guard would be a parity spec with the nested form as its oracle, like
`OversamplerDecimatorParitySpec`.

What it saves is one loop's L1 loads and stores plus the loop bookkeeping per fused pair (the
buffer is 1 KB and L1-resident on the desktop and on the Fairphone 4 alike, so this is not memory
bandwidth). A 128-sample arithmetic pass was about 0.2 µs of the 44 µs guitar voice on node, under
1 %, and the ratio is about the same on the phone (everything is slower there, the JS bounds
checks and the A55's load-store latency included, but so is the whole voice). The Schmetterling
census has level knobs between filters and shapers, not add-mul-add-mul chains, so for the song
the payoff rounds to zero.

## If it is ever picked up

1. Two `audio_benchmark` rows first: the nested pair (`saw.add(0.1).mul(2).add(0.2).mul(0.5)`)
   against one hand-built `IgnitorDsl.Affine` with the same numbers, node and JVM; that is the
   ceiling of any fusion. Under a percent of a plain voice: leave it here with the number.
2. Otherwise: the runtime fusion above, rows first, the nested form as the bit-exact oracle,
   mutations on the stage order and the dead-branch cut, the usual review loop.

## Not needed: optimizer rounds

The idea of running the pass in rounds until the tree stops changing (identity comparison) was
considered alongside. The pass is a single post-order rewrite, so a rule at a node already sees
its rewritten children, and the fuzz's idempotence law (`optimized.optimize() shouldBe
optimized`, 1500 trees) shows one pass reaches the fixpoint for every current rule. Rounds only
add power for a rule that changes something below or beside the node it fires on; none does. If
such a rule arrives: a round loop with a small limit and the existing never-throw fallback, since
two rules undoing each other would otherwise spin on the registration path.
