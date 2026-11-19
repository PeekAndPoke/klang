# KLANG-00468: Cylinder amp step 2: a Katalyst optimizer that fuses adjacent eq stages

> **Status (2026-11-19): DONE.** Klang-Main-Dev: built, reviewed clean in round 2, committed
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

Step 2 of KLANG-00466 (the guitar amp on the cylinder). After KLANG-00467 (step 1) every chain filter is an `eq`
stage of its own. A cabinet written as `eq(...)`, `lowpass(...)`, `highpass(...)` is then three stages, each with
its own effect object, its own knob read per block and its own pair of filter banks. The maintainer (2026-11-18):
the Katalyst "should have an optimizer that combines filters, just like the ignitor does".

The voice's optimizer (`IgnitorDslOptimizer`, rule R1) fuses adjacent filters into one `Eq`. This step gives the
chain the same: adjacent `eq` stages become one.

## Read first

- `audio_bridge/src/commonMain/kotlin/IgnitorDslOptimizer.kt`: the header and rule R1. Mirror its promises and its
  tone (what it merges, what it refuses and why, what it leaves alone).
- `audio_be/src/commonMain/kotlin/ignitor/IgnitorRegistry.kt`, `store`: where the voice's optimizer runs, and how
  a failure is caught and counted without ever being rethrown.
- `audio_bridge/src/commonMain/kotlin/KatalystDsl.kt`: `KatalystDsl`, `KatalystStageDsl.Eq`, and the three stages
  that hold chains of their own (`Parallel`, `OversampleBy`, `OversampleAtLeast`).
- `audio_be/src/commonMain/kotlin/katalyst/KatalystEqEffect.kt`, the class KDoc: a knob that moves makes the stage
  crossfade its WHOLE bank to a freshly zeroed one. This is why rule 3 below exists.
- `audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt`, `EqSection.RawTap`: a tap reads its EQ's INPUT. This is why
  rule 2 below exists.
- `audio_be/src/commonMain/kotlin/katalyst/KatalystRegistry.kt`, and
  `apps/studio/src/jvmTest/kotlin/OptimizerSongParitySpec.kt` (the voice optimizer's proof over the built-in songs).

## Decide before implementing

Decisions D8 and D9 of KLANG-00466 are this step's. Confirmed by the maintainer on 2026-11-19 ("please go ahead", after both were laid
out with the plan's choices).

## The work

### 1. The pass (`audio_bridge`, new file `KatalystDslOptimizer.kt`)

```kotlin
fun KatalystDsl.optimize(): KatalystDsl
```

A pure rewrite from a chain to a chain. One rule.

**R1, adjacent `eq` stages become one.** Walking a stage list in written order, a stage `b` is merged into the
stage directly before it, `a`, when ALL of this holds:

1. both are `KatalystStageDsl.Eq`;
2. no section of `b` is an `IgnitorDsl.EqSection.RawTap`;
3. every knob of every section of `a` and of `b` is an `IgnitorDsl.Constant`.

The merged stage is `Eq(sections = a.sections + b.sections)`, in that order. The walk then tries to merge the
next stage into the merged one, so a run of any length ends as one stage.

Why each clause:

- **Clause 2.** A tap is a boost of what goes INTO its EQ. In `b` that is what `a` put out; merged, it would be
  what went into `a`. A tap in `a` is fine: it reads the same input before and after.
- **Clause 3.** A section knob that is a slot (`Kat.param`) can be moved by a pattern while the chain runs, and
  the EQ stage answers a move by crossfading its whole bank of sections to a fresh one. Merged, a move of one
  knob would restart every section of the merged stage. With numbers only, nothing in the stage can ever move.

With both clauses the merged chain renders the SAME BITS as the written one: the sections run in the same order
on the same samples. That is the pass's promise, stricter than the voice optimizer's margin, and it is why the
pass needs no kill switch.

Also:

- **It walks into chains inside chains**: every branch of a `Parallel` and the body of an `OversampleBy` or an
  `OversampleAtLeast` is optimized the same way. Write the `when` over `KatalystStageDsl` exhaustively, so a new
  stage that holds chains must decide here (the chain builder's `lastDuck` is the model).
- **It never merges across another stage**, whatever that stage is: a `gain`, a `times`, a `duck`. A stage it
  does not merge is a wall, as an `Affine` is for the voice's R1.
- **It never moves a stage and never drops one.** An `eq` with no sections merges like any other (it holds no tap
  and no slot); a lone empty `eq` stays where it is.
- **A chain with nothing to merge is returned as the same instance** (`optimize() === this`), and so is a nested
  chain that did not change.
- **KDoc** in the manner of `IgnitorDslOptimizer.kt`: the rule, the two refusals with their reasons, the promise,
  and what is deliberately left (gains as walls, slots, taps).

### 2. The hook (`audio_be`, `KatalystRegistry.register`)

Store the optimized chain. Mirror `IgnitorRegistry.store`: the call sits in a `try`, a failure falls back to the
chain as written and is counted where a test can read it, and nothing is ever rethrown.

- The same registry serves the cylinders and the output (`master(...)`), so both are covered.
- `KatalystDsl.classic` holds no `eq` stage, so it comes back as the same instance. A cylinder decides by CONTENT
  whether a registered chain is the one it was born with (`ChainHost.chainFor`); a spec row pins that the classic
  chain is unchanged by the pass.
- Chains that tests and benchmarks hand straight to `KatalystChainBuilder` stay as written. That is intended.

### 3. The tests

**`audio_bridge` (commonTest), `KatalystDslOptimizerSpec`** (the voice's `IgnitorDslOptimizerSpec` is the pattern):

| Row | Pins |
|---|---|
| two adjacent `eq` stages of numbers | one stage, the sections in written order |
| five in a row | one stage |
| a cabinet as step 1's doors write it (`eq` with three bands, `lowpass` with three passes, `highpass` with two) | one stage of eight sections, in order |
| `eq`, `gain`, `eq`; the same with `distort`, `times`, `duck` between | not merged |
| `b` holds a `tap` | not merged; `a` holds a `tap` and `b` does not: merged |
| a slot knob in `a`; a slot knob in `b` | not merged, each |
| an `eq` run inside a `Parallel` branch, inside an `OversampleAtLeast` body, and inside a region inside a branch | merged there |
| a chain with nothing to merge; `KatalystDsl.classic` | the same instance |
| an empty `eq` beside a full one | merged; a lone empty one stays |
| the pass applied twice | the second run returns the first run's instance |

**`audio_be` (commonTest, `katalyst/`), `KatalystDslOptimizerRenderSpec`:** for each chain below, build the
written chain and the optimized one with `KatalystChainBuilder`, run both over the same stereo input (left and
right different, a few hundred blocks of 128 frames, noise and a saw), and require every sample to be equal bit
for bit:

- the cabinet above;
- a preamp's tail, `highpass(100)` then `lowpass(5500)`, behind a `distort`;
- the cabinet inside `oversampleAtLeast(4)`;
- a `bands` split whose band starts with an `eq`.

One more row for the count: the cabinet's pipeline holds one EQ effect after the pass and three before.

**`audio_be`, `KatalystRegistrySpec`:** a registered chain is found in its optimized form; a chain the pass
changes nothing in is found as the instance that was registered.

**The built-in songs** (`apps/studio`, jvmTest): mirror `OptimizerSongParitySpec` for chains. Every chain a
built-in song declares (today the master chains, with their `bands`) is rendered as written and optimized and
must be equal bit for bit. Its kind is "for the future": it goes red only when a song no longer compiles or the
pass breaks, never because a song was edited.

**Mutation checks** (mandatory tier):

| Mutant | Must turn red |
|---|---|
| clause 2 removed | the tap row, and a render row with a tap in `b` (add one) |
| clause 3 removed | the slot rows |
| the wall ignored (merging across a `gain`) | the wall rows and a render row |
| the sections joined as `b + a` | the order row and the render rows |
| no walk into regions | the nested rows |
| the hook stores the chain as written | the registry row |

### 4. The docs, in the same change

- `audio/ref/katalyst.md`: the pass, its rule and its two refusals.
- `audio/MEMORY.md`: its section in place, one History line.
- The KDoc of `KatalystStageDsl.Eq`: adjacent stages of numbers are merged at registration.
- If KLANG-00204 (ignitor optimizer open items) lists what the voice's pass leaves, add a line there or in a
  sibling note for what this pass leaves: gains as walls, slot-carrying stages.

## Out of this step

- Folding a gain into a neighbour, reordering linear stages, merging stages that hold slots, dropping empty
  stages. Each would break the bit-for-bit promise or needs a measurement first.
- A kill switch.

## Acceptance

- `:audio_bridge:jvmTest`, `:audio_be:jvmTest` and `:apps:studio:jvmTest` green, read with test-digest and
  `--expect` naming the new specs.
- The render spec is bit for bit, with no tolerance. If a row cannot be made exact, stop and report what differs
  and where; do not add a margin.
- The mutation campaign's table in the report.
- A review round with zero CRITICAL and zero MAJOR findings, code and audio.

## Found by review: a chain's stages before its first block

The audio review of round 1 found that a chain processed before anything configured it ran with every stage off: on a
cylinder only an owner's commit configures the chain, so under a first voice that does not claim (past its gate when it
arrives, or fading) a declared chain ran dry for that block, and the owner a block later faded each stage in from dry.
Written as three `eq` stages they faded one after another, merged as one they faded together, so the optimizer's
bit-for-bit promise failed there (measured up to 0.12 on a 0.5 noise); without any optimizer a declared `eq` of numbers
did not run at all for that first voice. The maintainer decided on 2026-11-19: "yes 1 this needs to be fixed".

The fix: a chain that nothing configured since it was built or reset configures itself from its authored defaults at
the top of its first processed block (`KatalystChain.process`), and an idle install resets the incoming chain, since a
cached one comes back retired. A host that configures first (a claiming owner, the output, a swap) never reaches it, so
a claiming first voice renders as before; guards `CylinderChainFirstBlockSpec` and the late-apply rows of
`KatalystDslOptimizerRenderSpec`, the laws in `audio/ref/katalyst.md`, "How a stage switches".

## History

- 2026-11-18: created, as step 2 of KLANG-00466.
- 2026-11-19: IN PROGRESS. Klang-Main-Dev: step 2 starts, D8 and D9 confirmed 2026-11-19

## Done (2026-11-19)

Built by an Opus worker, coordinated and checked by Klang-Main-Dev.

- The pass: `KatalystDsl.optimize()` (`audio_bridge/src/commonMain/kotlin/KatalystDslOptimizer.kt`), one linear walk,
  the rule and its two refusals as above; "all numbers" is asked through `IgnitorDsl.EqSection.childNodes()`. It runs
  in `KatalystRegistry.register`; a failure stores the chain as written and counts it.
- The engine fix found by review (section above): `KatalystChain` configures itself on its first processed block when
  nothing configured it since build or reset, and `Cylinder.install` resets the incoming chain. A cylinder from the
  warehouse shelf starts its first life without it (its born-with chain is classic, all off at its defaults): stated in
  the KDoc and in `audio/ref/katalyst.md`, not changed.
- Checks: `:audio_be:jvmTest` 3231 (run again by the coordinator), `:audio_be:jsBrowserTest` 3054,
  `:audio_bridge:jvmTest` 197, `:audio_bridge:jsBrowserTest` 336 (before the last round's KDoc edits),
  `:apps:studio:jvmTest` 172 (run again by the coordinator), 0 failed. 30 mutants killed in four campaigns by the worker;
  the reviewers ran their own.
- Reviews: round 1 (`reviewer-fable-medium`, code and audio) NOT CLEAN, one MAJOR each (the knob table pinned half
  the knobs; the first-block state); round 2 (`reviewer-fable-high`, code and audio) CLEAN, 6 MINOR and 7 NIT applied.
- Measured by the audio reviewer on a real cylinder, with the fix against without: the classic chain identical in
  four cases (a late first voice then a claim, the same on an adopted cylinder, after a deactivation reset, a claiming
  first voice). The amp chain under a late first voice equals a claiming first voice; without the fix the first ten
  blocks were dry (differences up to 0.62).
- JS allocation probe: 0 B per block on all 93 bus stage rows. It cannot see the one-off first-block configure nor a
  cylinder-level path.
- Not done: no one has listened. The allocation check of `full-check` was not run (it writes into `docs/benchmarks/`),
  nor the full song corpus render. No built-in song merges anything today (their chains sit at the output; Kokon's
  band split is refused for its tap), so the first real merges come with step 3.
- Proposed for two skill files, not written (they need the maintainer's word): a clause in
  `.claude/skills/dsl-design/door-shapes.md` (the chain filter row: adjacent `eq` stages of numbers are merged at
  registration) and a bullet in `.claude/skills/review-loop/audio-constraints.md` (the pass has no kill switch and
  refuses on purpose).
- 2026-11-19: DONE. Klang-Main-Dev: built, reviewed clean in round 2, committed
