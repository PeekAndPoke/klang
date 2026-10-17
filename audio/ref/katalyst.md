# Audio: the Katalyst chain and its two hosts

The laws in force for the orbit bus and the output, as of 2026-09-29. One home per rule; where a class KDoc
holds the full text, this file names it and gives the short form. How each law was found and measured is in
`audio/ref/memory-history.md` (the Katalyst 5b and 5c entries of 2026-09-19 and 2026-09-20, phase 3 step 12 of
2026-09-28) and in the archived records `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` and
`docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`.

## One chain type, two positions

- **The chain** is a `KatalystChain` built by `KatalystChainBuilder` from a wire `KatalystDsl`
  (`audio_be/.../cylinders/katalyst/`). `KatalystDsl.classic` is `body, vowel, delay, reverb, phaser, compressor,
  gain`, with `duck` declared last and run OUTSIDE the list in the cross-orbit pass (so a ducked orbit is ducked
  after its own fader). `Eq` is a stage type that the classic chain does not carry.
- **The orbit host** is `Cylinder`: every orbit is born with the classic chain, a pattern declares another with
  `katalyst(...)`. `Katalyst(k => k.classic())` is a bit-exact no-op (`chainFor` hands back `classicChain`).
- **The output host** is `MasterBus` (`audio_be/.../master/`): `master(Katalyst(k => ...))`, off with
  `master(Katalyst())`. It keeps three rules of its own (its class KDoc): a chain that arrives before the engine's
  first block is adopted at full weight; it never deactivates or resets on silence; its tail keeps the engine alive
  (`isRinging`). Nothing fills its slots (`applyParams(null)`), and a duck stage is built there but inert.
- **One registry fork per playback** (`KatalystRegistry`) serves both positions. Two hosts still duplicate chain
  plumbing: `docs/tasks/future/one-chain-host.md`.

## Where a knob comes from

- **The orbit's param state is the `katalystParams` map of the voice that owns the orbit**: the orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate closes or it is cut
  (lifecycle step 5, maintainer 2026-10-07; newest = the latest onset, on a tie the voice created later). The
  block's offers are committed once, after the voices rendered (`Cylinder.offer`, `Cylinder.commitOwner` from
  `Cylinders.processAndMix`), so render order decides nothing. An orbit without a `Sounding` voice keeps the
  settings it last applied. Every chain, the born-with one included, reads
  its knobs from there; the voice has no bus fields any more. Voices that need independent bus effects go on
  different orbits. The chain re-resolves only when the owner's map instance changes (`KatalystChain.resolvedFrom`).
- **A knob is a `KatalystKnobDsl`** (`audio_bridge/.../KatalystKnobDsl.kt`, the knob expressions task, 2026-10-12): a
  `Constant`, a `Param` (`Kat.param`, `Kat.slot.*`), a modulator (`Kat.sine`, `saw`, `ramp`, `tri`, `square`,
  `perlin`, `berlin`), or the Ignitor's arithmetic and `range` over any of these; never an Ignitor node. `KatalystKnob` compiles each
  knob once at chain build (`KatalystKnobProgram`: the Ignitor's laws on a scalar, constant subtrees folded). A knob
  over slots only is evaluated when the owner's map changes, never per block. A knob that holds a modulator is ticked
  once per processed block at the top of `KatalystChain.process`, so an ownerless orbit, the master, a leaving chain
  and every `parallel` branch modulate; its clocks run over the blocks the chain processes (paused while the orbit
  sleeps, not reset), and the n-th noise of a chain is seeded by its position. A slot leaf that reads a non-finite
  value unsets the whole knob, and an unset knob's clocks pause. The build-time knobs (`lookahead`, `distort`'s
  `shape` and `oversample`) are numbers, and an `eq` section takes a number or a `Kat.param` only (decision Q2, E2).
- **A non-finite knob is the declared OFF state** (`KatalystKnob.written` is false for it). This includes a
  non-finite `wet`, which turns the stage off for the whole orbit: not a pure improvement over the old field path,
  accepted so that "non-finite is unset" holds on every knob. Guard: `KatalystSlotResolverSpec`.
- **Delay and reverb are insert-style**: the feed is the orbit mix at the stage's position times the owner's one
  `wet`, the return is added to the mix, the dry stays. A stage runs when its wet is WRITTEN (a written 0 included)
  or authored above 0 (`stageAskedFor` in `KatalystChainBuilder.kt`); an authored 0 nobody writes rents nothing.
- **The orbit's group fader** is the `gain` stage (`gain.gain`, unity on the classic chain and bit-transparent
  there). It scales dry and returns alike.

## How a stage switches and how a knob changes

Every stage switches without a click, and the first switch after construction or `reset` acts at once until one
block has run (the `KnobGlide` snap), so a stage set on an orbit's first block renders as it always did. `reset`
and `retire` stay a synchronous hard cut, and `Cylinder` calls them only on a silent orbit or at the shelf.

| stage | switch on / off | knob changes |
|---|---|---|
| body, vowel, orbit EQ | `KatalystFilterSwap`: TWO banks, a linear crossfade over `BANK_CROSSFADE_SECONDS` (0.05 since 2026-10-17, by ear; 0.02 before), counted in samples. A change during a fade is refused by the swap and PARKED by the host as a config, latest wins, installed on the landing block | a changed material or vowel is a new bank (the bank in service is never retuned; the morph was rejected by ear, 2026-09-20). Body and vowel `wet` and `floor` move the blend IN PLACE (knob expressions step 5c): the dry and wet coefficients glide per sample on every bank the swap holds, over 50 ms, one block when modulated, no install; a mix at 0 bypasses once the blend has landed there, the landing zeroes the bands |
| delay | Off, Active, Draining state machine (`KatalystDelayEffect`). EVERY off (an owner change, a `katp` time below the gate, an unset or non-finite time) stays Active while the wet glide takes the feed to 0, and the block the glide lands in drains on its own, no further off-config needed (engine follow-up 42, maintainer Q39, 2026-10-13: "fade, do not cut"; knob expressions step 5b built it for a modulated time). On an owner change the new owner's first 50 ms reach the old ring at a falling level, accepted. A return from Draining glides the wet up from 0; from Off it snaps, except on a modulated time, whose every return, the first activation included, glides up from 0 | `time`: `DelayLine` crossfades the old tap to the new over 50 ms, a change mid-crossfade is parked; `feedback`: glides per block and ramps per sample; `wet`: per-sample level glide |
| reverb | the same state machine and the delay's feed fade on every off, and its glide from 0 out of Off for a modulated size (`KatalystReverbEffect`) | `size` glides on the normalized 0..1 axis; `lowpass` (damping) does not glide, measured inaudible; `wet` as the delay's |
| compressor | a linear blend with dry over `KNOB_GLIDE_SECONDS`; ON fades a reset instance in; the instance is built with the effect, never on the audio thread | `threshold`, `ratio` (as its inverse), `knee` glide per sample (`Compressor.processGliding`) as one move when it only adds reduction; a move whose NET effect at 0 dBFS (a fixed loud reference, never the signal) is less reduction (threshold up, ratio down, knee narrower, or a mix that relaxes on balance) releases on the same shared fraction at the compressor's release time constant, never faster than the glide (`CompressorKnobMove`, by ear 2026-10-17); a modulated knob stays outside the move; `attack`, `release` apply at once |
| phaser | the wet and floor coefficients glide to identity; the cascade is dropped only on the landing | `wet`, `floor` per sample; `center`, `sweep` per block with alpha continuous across the seam (`PhaserCore.prepareBlock`); `rate` at once |
| duck | a weight rides the reduction to 0 dB (`gain = 1 + w * (g - 1)`), then lets go of the source orbit | `depth` per sample; `attack` at once; a `duck.orbit` switch has no mechanism (open: `docs/tasks/future/duck-orbit-switch-click.md`) |
| distort | the compressor's law: a linear blend with the dry mix over `KNOB_GLIDE_SECONDS`; ON fades reset cores in; with oversampling the dry runs through the phase twins, an unshaped round trip of the same oversampler (2026-10-10); oversampling is 2x unless the author names a factor | `amount` glides linear in the amount over `KNOB_GLIDE_SECONDS`, the drive ramped per sample within a block; `shape` and `oversample` are fixed with the chain |
| gain | never off | per-sample glide over `KNOB_GLIDE_SECONDS` |
| arithmetic (`plus`, `minus`, `times`, `div`, `pow`, `min`, `max`, `abs`; 2026-10-11) | never off, memoryless: the Ignitor's per-sample law (`_arithmetic_laws.kt`) on every sample; an unset operand is the operator's identity, an unset cap or floor passes the bus | the operand glides per sample over `KNOB_GLIDE_SECONDS`, the law taken with the operand at each sample; a divisor glides as its reciprocal (a level) and divides again once settled; a cap's or a floor's first operand after unset arrives at once, and a cleared one lets go at the next block (`KatalystArithmeticEffect`) |
| parallel | none of its own: each branch is a chain whose stages switch as above | none of its own: the orbit's param state reaches every branch's stages (`KatalystParallelWriter`) |

`KnobGlide` (`audio_be/.../KnobGlide.kt`, its KDoc is the contract) moves one knob linearly over
`KNOB_GLIDE_SECONDS` (0.05) in whole 128-frame blocks, lands bit-exactly, restarts from where the knob stands on a
new target, and ignores a non-finite target. A knob whose expression holds a modulator (`Kat.sine` and the rest) glides
over ONE block instead, chosen per knob when the chain is built (knob expressions step 5b, D2; the per-stage rules are
in `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, "5b built"). Two traps for the next knob:

- The owner re-applies its settings EVERY block, so a retarget to the same value must be free; a helper that
  restarts on every call never lands.
- A knob retargeted every block converges like a one-pole with tau about 49 ms: a low-pass on the knob (-3 dB at
  3.2 Hz). A glided knob patterned faster than a few Hz loses depth.

The glide time itself is an open question for the maintainer's ear: `docs/tasks/future/transition-times.md`.

## When an orbit may stop, and when a chain leaves

- **An orbit deactivates only when** its silence grace has run (`silentBlocksBeforeTailCheck`, 10 blocks), its
  chain reports no tail, AND no voice has checked in on it (`Cylinder.checkIn`, owner or not, tails included; one
  block of grace). A muted voice keeps its orbit while it plays, so the fader glides and a phaser sweep continues;
  a culled voice has ended and keeps nothing.
  `tryDeactivate` clears the orbit's mix buffer (a stale buffer once leaked into a room).
- **The tail question** is `KatalystChain.hasTail()`, answered by `TailCeiling` compares (`effects/TailCeiling.kt`),
  never by a scan (`Reverb.hasTail()` has no production caller). The ceiling may only err towards holding longer.
  Every ceiling is held to `SILENCE_FLOOR`, except a reverb whose chain drains after a swap: that one answers against
  `Reverb.TAIL_FLOOR`, its comb level whose output is about -90 dBFS (two floors on purpose, maintainer 2026-10-17:
  the cell floor held a draining room about 20 dB past inaudible, and ordinary playback stays as it was).
  Its open edges (a lengthened tap, a feedback cut on a changing owner): `docs/tasks/future/delay-ceiling-edges.md`.
- **A chain swap is `ChainSwap`** (`audio_be/.../ChainSwap.kt`), at both positions: the leaving chain's INPUT
  ramps down over `Crossfade.XFADE_SECONDS` (0.06) while the arriving chain ramps up, then the leaving chain drains
  at full weight until it has no tail, capped at `ChainSwap.MAX_DRAIN_SECONDS` (20 s), after which `TailRelease`
  releases it; while it drains it measures its tails every `TAIL_MEASURE_SECONDS` (0.5 s), so a room's ceiling
  follows what the room holds. A request during a fade, drain or release is parked by the host, latest wins, and
  tells the swap (`requestWaiting`): a drain or a release is then CUT, the leaving output faded by a raised cosine
  over `ChainSwap.CUT_SECONDS` (0.15 s), and a request during the fade cuts where the fade ends; the request lands
  about 0.2 s after it was made (maintainer, 2026-10-17: `docs/tasks-archive/2026-10/20261017-chain-swap-request-during-drain.md`).
  Chains of different latency are placed so their weights meet at the output (`KatalystChain.latencyFrames`).
- **`TailRelease` is the one release law** (`audio_be/.../TailRelease.kt`): exponential from exactly 1, 60 dB per
  `RT60_SECONDS` (3 s), retired under `FLOOR_DB` (-90 dB), 4.5 s in all.
- **A stopped playback is never hard-cut** (`PlaybackEngine.isIdle` KDoc is the home): finite tails ring out in
  full; only when an endless tail is present (`KatalystChain.sustainsItself`, a delay at `|feedback| >= 1`) is the
  engine's whole output released, `MAX_TAIL_HOLD_SECONDS` (20 s) after the last note.
- **Lookahead**: the Katalyst `compressor` and `limiter` take a build-time `lookahead`, at most
  `Compressor.MAX_LOOKAHEAD_SECONDS` (0.05), fixed per chain; the orbit or playback runs late by it and nothing
  compensates, by the author's choice. The house limiter is not a Katalyst stage (`MasterStage`; `audio/MEMORY.md`, "House stage").
  The `distort` stage's oversampling delays the same way, by the oversampler's group delay rounded (3 frames at 2x, 4
  at 4x, 5 at 8x; `Oversampler.groupDelaySamples`, the IIR half-band's low-frequency delay since 2026-10-10). Both are `KatalystLatentEffect`s, and the chain sums them
  (`KatalystChain.latencyFrames`). A `parallel` stage (2026-10-10, `KatalystParallelEffect`) matches its branches in phase, so the sum does not comb:
  each branch gets an unshaped round trip of every oversampler it lacks (a phase twin) and a ring for the lookahead it
  lacks; the stage reports the union of the oversamplers plus the longest lookahead as its latency.

## Writing a stage lifecycle: the state-machine template

The plan is `audio/ref/effect-state-machines.md`; the delay (`KatalystDelayEffect`) is the template, and the
reverb, the filter swap and the compressor copy it. What every copy must keep:

- **The tail question has THREE askers, and a new stage answers all three** (2026-10-09, the `distort` stage's review,
  two escapes): the orbit's deactivation (it scans the mix AFTER the chain, so an insert's residue is seen without
  asking), the chain swap (`ChainSwap` retires the leaving chain at the end of its input ramp unless `hasTail()`, NO
  scan), and the master (`MasterBus.isRinging` asks `hasTail()` only for a chain whose `declaresTail` is true). A
  stage whose state can put audio into a SILENT input (a DC blocker's decay, a ring, a delay line) answers
  `hasTail()` from that state in O(1) AND joins `KatalystChain.declaresTail`. The "insert, so false" argument covers
  the first asker only.
- **A build-time option multiplies the behaviour rows** (the same review): a stage with a structural choice (the
  oversampling factor, a lookahead) runs every switching and gliding row at each value of it, not only at the default;
  the `distort` stage's amount glide clicked only with oversampling on, where no row ran.
- **A glide is linear in what it holds, so the output must lie between its endpoints' outputs** (2026-10-11, the
  arithmetic stages' review, a CRITICAL): a knob whose law has a pole or a jump (a divisor: `x / v` near `v = 0`, and
  the house's `div(0)` is silence) invents values the author never wrote when it glides linearly, up to a gain of
  about +300 dB on a flip from -1 to 1 at 48 kHz (a sample on the `safeDiv` floor). Glide the quantity the output IS
  linear or monotonic in (for a divisor its reciprocal, the level), and give each such knob a row that glides through
  and into the pole, bounds the output AND proves the move is a glide (a neighbour-step bound, a hand-computed ramp
  value): a bound alone is passed by a one-block step (round 2).
- **A config cache over several knobs gets a row per knob, one knob changed at a time** (2026-10-11, knob
  expressions step 2, a MAJOR): a stage that skips `configure` when its knobs equal the last applied ones must show
  that each knob alone reaches the instance; rows that move one knob or all of them together leave the other compares
  unbound (`KatalystCompressorEffectSpec`, "each of the five knobs alone"). A compare against another knob's stored
  slot fails safe (writes every block) and is caught by an "unchanged config writes nothing" row instead.
- **A knob made per-block reaches every setter behind it, so read each one for a "settings change only" contract**
  (2026-10-12, knob expressions step 5b, a MAJOR): the lookahead compressor's `attackSeconds` re-primed its box
  smoother on a length change, documented "not something to call per block"; a modulated attack then stepped the gain
  by up to 0.69 in one sample. Before a knob moves every block, list the setters it reaches and what each does on a
  change (a reprime, a reset, a rent, a coefficient rebuild); a side effect that was fine at a `katp` event is a click
  or a cost every block.
- **A gate that asks "is the stage wanted" answers a non-finite value too** (the same step, a MAJOR): a predicate that
  keeps a stage on for a moving knob (`KatalystKnob.isModulating`) must also require a finite value, or a NaN reaches
  a stage that its unmodulated twin keeps off (the duck's depth, the sends' wet).
- **A fix's rows go through the production builder and cover every entry state** (the same step, round 2, two
  MAJORs): rows that build the effect by hand with the fix's flag set leave the builder's wiring of that flag unbound
  (a mutant setting it `false` stayed green); and a fix for an edge "in both directions" gets a row per state the
  stage can return FROM (Off and Draining), not only the one the first row happened to reach. A stage with a swap
  counts the swap's states as entry states too: settled, fading to a pair, fading to DRY, a word parked, off (step 5c,
  a recurrence: a word arriving inside a fade to dry had no row).

- A private sealed `State` with one preallocated instance per state; `enter` is the only way in and the only place
  a state's own data is initialised. A datum belongs to a state only if it dies with that state (the tail ceiling
  survives Active to Draining to Active, so it lives on the effect).
- Every door into a terminal state establishes the SAME precondition (the duck's `endLife` settles its weight at
  0 through `KnobGlide.settleAt`, whatever the door). A handover carries glide state across (`KnobGlide.carryOver`).
- A stage that caches its config compares and stores the SUBSTITUTED values, never the raw input (a NaN is never
  equal to itself, and a cache keyed on it rebuilds every block).
- The permanent guards per conversion: an identity spec that counts state objects by identity (not `equals`), and
  one behaviour row per non-obvious decision (e.g. "a delay that returns mid-drain keeps the ring AND the tail
  ceiling"). Stages with no state of their own (gain, phaser, duck) use `KnobGlide` alone; do not add state classes
  that copy it.

- **The phaser's breakpoint axis is settled** (Katalyst 5c-9): no axis dominates, so linear Hz with a continuous
  alpha stands; the next frequency knob need not re-open it (residue numbers in the `KatalystPhaserEffect` KDoc).

## Body and vowel

One class, two kinds since engine tidy-up step 12 (a): `KatalystResonatorEffect` with `ResonatorKind.BODY` or
`.VOWEL` (the kind supplies the unset wet and floor), one writer (`KatalystResonatorWriter`). A chain declares a body
stage and a vowel stage, two instances with their own slots and banks, so a vowel sung through a body runs both. The
DSP is `filters/ResonatorBank.kt` (the bands and the dry/wet blend in one mono bank); the gain rules live in
`LowPassHighPassFilters.bodyGain` / `vowelGain` (the vowel's operand order is load-bearing). `body.material` and
`vowel.vowel` carry an INDEX into `BodyMaterials.names` / `VowelBands.names` (audio_bridge): append only, never
reorder, the index is the wire encoding. `ResonatorTables` builds one table per index once and SHARES one instance
between indices whose rows are equal (`bass:a`, `bass:ei`, `bass:au`), so the stage's compare by reference keeps a
switch among aliases free. A change installs into the pooled pair the swap does not hold (two pairs, built at the
stage's first install), from zero state: no allocation per change, and a sounding bank never retunes. Only the table
is a bank's identity (knob expressions step 5c, Q8 (a)): a `wet` or `floor` change with the same table glides the
blend of the banks that sound in place (`ResonatorBank.glideBlend`: the pair `dryGain`, `wetGain`, linear in the
output, per sample over 50 ms, or one block when either knob holds a modulator), on the pair fading out too, so a
moving wet does not restart the resonance unless the mix lands at 0 (below); a call with another table carries its mix
through the swap as one word. A mix at or below 0 bypasses once the blend has landed there, and the landing block
zeroes the band state, so a return starts the bands from rest under a wet coefficient from 0. The stage is offered a `ResonatorConfig` by reference (a
double crossing a call boxes on V8). Guards: `CatalogueIndexSpec`,
`ResonatorTablesSpec`, `ResonatorBankSpec`, `KatalystResonatorEffectSpec`, `KatalystResonatorAllocationSpec`,
`KatalystModulatedStagesSpec` (the in-place blend, step 5c).
