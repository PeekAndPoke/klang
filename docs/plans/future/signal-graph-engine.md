# The engine as a signal graph; sprudel's layout as one built-in graph

Status: FUTURE, a long-term vision (maintainer, 2026-09-25). Tackled after the current engine
redesign workstream (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md` phase 3 and the signal-flow plan) is fully
finished: "finishing the current workstream fully does not hurt. It lays a lot of foundations."

## 1. The idea, in the maintainer's words

> We currently do all of this `.classic()` stuff to make built-in oscs work with sprudel. This means
> we are building stuff into the backend to accommodate sprudel specifics.

Two parts:

1. **Sprudel's voice chain becomes a frontend preset, attached automatically.** Sprudel's `sound()`
   resolves what the sound is. For a built-in name ("sine", "saw", "supersaw", ...) it sends the
   Ignitor `Osc.sine().sprudel()`; for an authored instrument (`let myInst = Osc.saw()`,
   `note("a").sound(myInst)`) it attaches `.sprudel()` at the end when it is not there yet. A tag
   on the tree tells whether it is. `.sprudel()` is today's `classic()` chain.
2. **The engine layout itself is sprudel-specific and should become configurable.** Cylinders,
   Katalysts and the master are one fixed signal graph. The Katalyst effects and the master effects
   should be the same thing. The user can configure any graph: "having multiple cylinders, then push
   some through katalyzers, combine some into a new sub-sum, push through another katalyzer, maybe
   even split and join again." Sprudel's setup (voices into orbits, orbit chains, one master) is
   then just a built-in special case.

Why: the backend as lean as possible, first to reduce complexity, second so the engine is truly
configurable and no frontend's shape is baked into it. It is the stone rule "the engine is the
horse" taken one level further: not only DSP, but also the routing belongs to the engine as a
general mechanism, and a frontend chooses a routing.

## 2. What the current workstream already lays down

- `IgnitorDsl.classic()` (phase 3 step 5): the whole sprudel voice chain written once as an Ignitor
  tree, every stage gated on its slot, slots named `<door>.<param>`. This IS `.sprudel()` under
  another name; the rename is a small decision for when this plan starts.
- Step 6 re-registers the built-ins on `classic()` and switches the strip off for them; step 9 cuts
  `VoiceData` and retires the Pipeline DSL (both done 2026-09-27). The voice strip is gone and every voice is an
  Ignitor tree, so part 1 of the idea needs only the auto-attach and the tag.
- One law per effect, shared by every host (`CrushCore`, `DistortionCore`,
  `EnvelopeCore`, `SvfCoeffSweep`): a Katalyst stage and a master stage can already run the same code (since
  phase 3 step 12 they ARE the same stage: the output runs a `KatalystChain`). The tremolo has no law of its own any
  more since 2026-09-29: it is composed from the oscillators.
  Part 2 needs exactly this.
- The door shapes are one shape per concept across the Ignitor and Katalyst DSLs (`.claude/skills/dsl-design/door-shapes.md`,
  once section 3b of the phase 3 record; the Master DSL merged into the Katalyst in phase 3 step 12): the surfaces are already
  nearly the same.
- The signal-flow plan section 7 made the Katalyst chain an instrument-like list whose reverb and
  delay are insert stages; since phase 3 step 12 the master is that same chain at the output position
  (`master(Katalyst(k => ...))`).

## 3. The first step when this plan starts: the fixed-layout inventory (maintainer, 2026-09-27)

Before any design: identify EVERYTHING that assumes today's fixed layout, then think deeply about how to make
it configurable and how it still fits a language like sprudel, which will always assume a fixed layout. The
maintainer has ideas for that; they are not written here yet.

The framing so far: there are two kinds of processing. PER-VOICE (the Ignitor tree: it knows the note's
frequency, gate and lifetime, built at note start, gone with the note) and SHARED (processing a summed signal,
no per-note context, living as long as the playback). "Katalyst" (the orbit chain) and "Master" are not two
kinds: they are two positions in the fixed graph. The long-run shape is NAMED nodes that events can configure;
sprudel's bus doors writing `katp` slots that an orbit's chain reads by name are an early, orbit-bound form of it.

Seed list of fixed-layout assumptions (known so far; the inventory completes it):
- `KlangPatternEvent`'s `sound`, `master` and `katalyst` properties and `InlineDslRegistrar.announceAll`
  (section 3 below; a candidate shape: the event announces its own inline DSLs into one generic sink over a
  sealed `InlineDsl` wire type, the maintainer's visitor idea, name to be decided);
- the orbit concept: voices summed per orbit, the cylinder per orbit, sends, ducking and the compressor by orbit;
- ~~the Katalyst (per orbit) and Master (one) chains as separate types, DSLs, registries and `Cmd.Register*` kinds~~
  DONE 2026-09-28 (phase 3 step 12, `docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`): one chain type
  (`KatalystDsl`, `KatalystChain`), one registry fork per playback, one `Cmd.RegisterKatalyst`, one swap law
  (`ChainSwap`). What is left of it is two HOSTS of that chain, `Cylinder` and `MasterBus`, with duplicated plumbing:
  `docs/tasks/future/one-chain-host.md`;
- `katp` and the orbit parameter state; the sprudel doors that write it (`reverb`, `delay`, `compressor`,
  `duck`, `phaser`, `body`, `vowel`) and `orbit(...)`, `katalyst(...)`, `master(...)`;
- the per-voice `classic()` chain itself (sprudel's fixed voice layout, attached as a tag since phase 3 step 10).
- two POSITION-bound features a merged "effect chain" type must keep expressible (maintainer and coordinator,
  2026-09-27: Katalyst and Master are the same thing, a shared chain, named only for where it sits): the master
  limiter's lookahead (a final safety stage, and until phase 3 step 12 C2 master-only; in a graph, a property of the node before the
  output; since step 12 decision (a) only the house limiter's 5 ms in `MasterStage` is fixed, an authored
  `lookahead` runs at any position and makes that node late) and ducking (a side-chain edge from another bus, not
  a chain property);
- the duck machinery, to be replaced by a bus reference plus a follower (section 5): the `duck` stage with its
  own DSP (`Ducking`, `KatalystDuckEffect`), the second pass in `Cylinders.processAndMix` that runs it after every
  orbit, `duckCylinderId`, the envelope handover `takeOver`, `ChainSwap`'s duck events (`processDuck`,
  `ownerClaimed`, the duck data on Fading: the first thing to remove once ducking is composition), and the
  rule that `duck` is inert at the output position (phase 3 step 12).

## 3. Open questions (to settle when this plan starts, not now)

- **Where `.sprudel()` lives.** The maintainer's first thought: a KlangScript extension function on
  `IgnitorDsl` (which needs extension functions and type annotations in KlangScript), or a Kotlin-side
  extension on `IgnitorDsl`, which needs no new language feature. The Kotlin side is the smaller step
  (stone rule: complexity is the enemy); script extension functions can follow as their own feature.
- **The tag.** ANSWERED in phase 3 step 10 (2026-09-26): structural, `IgnitorDsl.endsInClassic()` (the root is
  the `Adsr` whose `on` is the slot `adsr.on`); the auto-attach becomes one line in sprudel's sound resolution.
  The question as first written: how an Ignitor tree says "already has the sprudel chain": a marker node, or a field on
  the tree. It must survive the wire and immutability (DSL values are immutable at construction).
- **Double stages on authored instruments.** An authored instrument with its own `adsr` or `lowpass`
  gets `.sprudel()` appended. Its classic stages are gated on their slots, so they stay silent until
  a pattern writes them, but the classic amplitude envelope then sits after the author's own (the
  "double envelope" caveat in the phase 3 record, section 6). Decide which classic stages an authored
  instrument gets, or whether the author opts out.
- **Graph shape and its DSL.** Nodes (sources, buses, effect chains, sums, splits), how a pattern
  addresses a bus (today: `orbit`), how a graph is declared and replaced at runtime, and what the
  sprudel default graph looks like written in that DSL.
- **One effect chain type.** The Katalyst and the master chain merged into one chain type that any
  graph node can host. DONE for the type in phase 3 step 12 (2026-09-28); the lookahead rule is settled there too
  (decision (a): the house limiter's lookahead is the only fixed one, an authored one runs anywhere, nothing
  compensates). Open: one HOST type for the chain (`docs/tasks/future/one-chain-host.md`).
- **Cost.** Today's fixed routing is cheap. A general graph must stay allocation-free per block, keep
  the per-playback cleanup rule ("nothing allocates without a way to clean it up"), and stay inside the
  block budget on Node.js, the shipping target.
- **The wire.** Only seconds cross it, never cycles (stone); a graph declaration is a new wire value
  (a sealed `@WireName` hierarchy, not an enum).
- **The event's inline DSL properties** (noted by the maintainer, 2026-09-27). `KlangPatternEvent` exposes
  `sound`, `master` and `katalyst`: the inline DSL values a playback announces once to the backend
  (`InlineDslRegistrar.announceAll`, one `Cmd.Register*` per value, then the voice carries only a name). They
  are the three levels of today's FIXED graph (voice instrument, orbit chain, master chain) spelled out as
  named properties. "OK-ish for now"; a configurable graph revises them, e.g. into one generic list of
  inline graph-node references per event, announced the same way.

## 5. Bus references: ducking as composition (maintainer, 2026-09-27)

Recorded only; nothing is built for it before this plan starts.

**The idea.** One bus can reference another bus's output, and a node reads it. A gain modulator that reads the
level of another bus IS ducking; it needs no duck feature. Three general parts:
- a **reference**: a signal source "the output of bus X", an edge of the graph;
- a **follower**: an envelope detector turning audio into a control signal (peak or RMS, attack, release). Today
  it is hidden inside `Ducking`: instant attack, exponential release, sidechain sensitivity 2.0;
- a **knob that accepts a signal**: a bus stage's knob is already an `IgnitorDsl` expression.

Ducking becomes roughly `gain(1 - depth * follow(bus(1), release = 0.2))`, and the same parts give sidechain
compression (a compressor's detector reads another bus), a filter that opens with the drums, a reverb that ducks
its wet under the vocal, and the whole playback pumping under the kick at the output position.

**The maintainer's instinct: a reference reads the referenced bus's PREVIOUS output block.** What that settles:
- **Order:** none is needed. Every bus runs in any order, because what it reads is already complete. Today's duck
  pass runs in map order, so two orbits ducking each other read one already-ducked output; that known limitation
  of the fixed engine is left as it is, and this is its fix.
- **Cycles:** defined without a special case, one block of delay per loop. Feedback gain in a loop is the
  author's (the Motor stays raw).
- **Position:** any node at any position reads any bus, the output included; the inert duck at the output goes.
- **Rate:** the ordering half collapses. The reference still delivers a full block of samples, so the follower
  runs per sample. The READING side still needs knobs that take a per-sample signal (a bus stage's knob is resolved
  from the slots when the chain instance changes, and a knob change glides; no knob follows a signal), so that half
  remains an engine feature to build.
- **Latency:** fixed and small, one block of 128 frames (2.67 ms at 48 kHz, 2.9 ms at 44.1 kHz; the block size is
  pinned). A duck reacts one block after its trigger, inside the attack of a typical sidechain compressor, where
  today's instant-attack duck reacts on the trigger's own sample. No built-in song or frozen piece calls `.duck(` (grep,
  2026-09-27; only `KatalystDoorFillRenderSpec` does), so the change moves no corpus row. Should aligned ducking
  ever be wanted, delaying the ducked bus by one block restores it, the same trade as a lookahead (phase 3 step 12
  C2). A referenced bus with a lookahead adds its own latency to the edge.

**Open when the plan starts:** the tap point (before or after the referenced bus's own chain; today after, so a
kick's reverb tail also holds the duck), the follower's shape and defaults, the names.

## Links

- `docs/plans/signal-flow-redesign.md` sections 5 (built-in instruments) and 7 (the Katalyst).
- `docs/tasks-archive/2026-09/20260928-builtin-instruments.md` (phase 3, archived 2026-09-28: `classic()`, the door shapes, the shared cores).
- `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md`, `docs/tasks/master-dsl-followups.md`, `docs/tasks/future/one-chain-host.md`.
- `docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md` (the master became a Katalyst at the output).
