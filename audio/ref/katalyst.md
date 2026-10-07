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
| body, vowel, orbit EQ | `KatalystFilterSwap`: TWO banks, a linear crossfade over `BANK_CROSSFADE_SECONDS` (0.02). A change during a fade is refused by the swap and PARKED by the host as a config, latest wins, installed on the landing block | a changed material or vowel is a new bank (the bank in service is never retuned; the morph was rejected by ear, 2026-09-20) |
| delay | Off, Active, Draining state machine (`KatalystDelayEffect`) | `time`: `DelayLine` crossfades the old tap to the new over 50 ms, a change mid-crossfade is parked; `feedback`: glides per block and ramps per sample; `wet`: per-sample level glide |
| reverb | the same state machine (`KatalystReverbEffect`) | `size` glides on the normalized 0..1 axis; `lowpass` (damping) does not glide, measured inaudible; `wet` as the delay's |
| compressor | a linear blend with dry over `KNOB_GLIDE_SECONDS`; ON fades a reset instance in; the instance is built with the effect, never on the audio thread | `threshold`, `ratio` (as its inverse), `knee` glide per sample (`Compressor.processGliding`); `attack`, `release` apply at once |
| phaser | the wet and floor coefficients glide to identity; the cascade is dropped only on the landing | `wet`, `floor` per sample; `center`, `sweep` per block with alpha continuous across the seam (`PhaserCore.prepareBlock`); `rate` at once |
| duck | a weight rides the reduction to 0 dB (`gain = 1 + w * (g - 1)`), then lets go of the source orbit | `depth` per sample; `attack` at once; a `duck.orbit` switch has no mechanism (open: `docs/tasks/by-ear/duck-orbit-switch-click.md`) |
| gain | never off | per-sample glide over `KNOB_GLIDE_SECONDS` |

`KnobGlide` (`audio_be/.../KnobGlide.kt`, its KDoc is the contract) moves one knob linearly over
`KNOB_GLIDE_SECONDS` (0.05) in whole 128-frame blocks, lands bit-exactly, restarts from where the knob stands on a
new target, and ignores a non-finite target. Two traps for the next knob:

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
  Its open edges (a lengthened tap, a feedback cut on a changing owner): `docs/tasks/future/delay-ceiling-edges.md`.
- **A chain swap is `ChainSwap`** (`audio_be/.../ChainSwap.kt`), at both positions: the leaving chain's INPUT
  ramps down over `Crossfade.XFADE_SECONDS` (0.06) while the arriving chain ramps up, then the leaving chain drains
  at full weight until it has no tail, capped at `ChainSwap.MAX_DRAIN_SECONDS` (20 s), after which `TailRelease`
  releases it. A request during a fade or drain is parked by the host, latest wins (worst case about 24.5 s;
  whether a request should wait for the drain is open: `docs/tasks/by-ear/chain-swap-request-during-drain.md`).
  Chains of different latency are placed so their weights meet at the output (`KatalystChain.latencyFrames`).
- **`TailRelease` is the one release law** (`audio_be/.../TailRelease.kt`): exponential from exactly 1, 60 dB per
  `RT60_SECONDS` (3 s), retired under `FLOOR_DB` (-90 dB), 4.5 s in all.
- **A stopped playback is never hard-cut** (`PlaybackEngine.isIdle` KDoc is the home): finite tails ring out in
  full; only when an endless tail is present (`KatalystChain.sustainsItself`, a delay at `|feedback| >= 1`) is the
  engine's whole output released, `MAX_TAIL_HOLD_SECONDS` (20 s) after the last note.
- **Lookahead**: the Katalyst `compressor` and `limiter` take a build-time `lookahead`, at most
  `Compressor.MAX_LOOKAHEAD_SECONDS` (0.05), fixed per chain; the orbit or playback runs late by it and nothing
  compensates, by the author's choice. The house limiter is not a Katalyst stage (`MasterStage`; `audio/MEMORY.md`, "House stage").

## Writing a stage lifecycle: the state-machine template

The plan is `docs/plans/effect-state-machines.md`; the delay (`KatalystDelayEffect`) is the template, and the
reverb, the filter swap and the compressor copy it. What every copy must keep:

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

`KatalystBodyEffect` and `KatalystFormantEffect` are intentional un-deduped twins: change one, mirror the other.
Both run on `filters/ResonatorBank.kt`; the gain rules live in `LowPassHighPassFilters.bodyBand` / `vowelBand` (the
vowel's operand order is load-bearing). `body.material` and `vowel.vowel` carry an INDEX into
`BodyMaterials.names` / `VowelBands.names` (audio_bridge): append only, never reorder, the index is the wire
encoding. Both tables hand out one shared band list per entry, which `configure` compares by identity. Guard:
`CatalogueIndexSpec`.
