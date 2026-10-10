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
   Ignitor `Ignitor.sine().sprudel()`; for an authored instrument (`let myInst = Ignitor.saw()`,
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

## 6. The layout lives outside the patterns (maintainer, 2026-10-08)

> "I think we have to reason one level higher. The idea of writing patterns that shape everything is cute and a
> simple starting point to make some music. But it falls short at closer inspection ... So the real challenge is how
> do we define the entire setup of busses, groups, master etc. outside of the patterns. The cool idea of the patterns
> is, that everything is stateless. But in reality you need a state: the setup of the 'machine': busses and their
> wiring until in the end everything leaves the master. The strudel setup is just one special case of it ... So
> having only one code file is probably not going to work anymore. We need another file, that defines the entire
> layout ... which would also be a step toward all of the AAA tricks we can currently not do. Fiddling around with
> `.classic()` at the current layer will not help, we will run into the same issue again and again."

How it surfaced: the production research ([`../aaa-production-tricks.md`](../aaa-production-tricks.md)) found four
structural gaps, and three of them are this one: no routing (G3), no automation of bus and master knobs (G2), the
doors bolted onto the instrument ([`../../tasks/classic-doors-and-velocity.md`](../../tasks/classic-doors-and-velocity.md),
G6). Each fix tried at the pattern layer ran into the next.

### 6.1 Precedent: Strudel inherited a convention, not a necessity

Tidal sends its events to SuperDirt, which runs on SuperCollider. SuperCollider's server is a general node graph,
with synths, groups, audio buses and arbitrary routing. SuperDirt's "orbits" (each with its own global effects, all
summed to the output) are one fixed CONVENTION that SuperDirt laid on top of that general graph. Strudel copied the
convention and lost the graph underneath. DAWs split the same way: the arrangement (clips, notes, automation) is one
thing, the mixer (tracks, inserts, groups, returns, sidechains, master) another. Klang today has SuperDirt's
convention welded into the engine; this plan puts the general graph back under it.

### 6.2 Two kinds of code

- **The machine** (name open): the layout, declared once and stateful while it runs.
  - The instruments and the per-note stage after each one (the doors, default `classic`; see the task above).
  - The buses and their Katalyst chains, groups (sub-sums), sends and returns with levels, and sidechain references
    (section 5).
  - The master.
  - Which knobs the patterns may move, by name.

  It is an immutable value like every DSL value (stone rule); the engine holds its running state (tails, followers).
  Changing it is a structural swap with a crossfade (`ChainSwap`, generalised).
- **The song** (the patterns): what is played, stateless, a pure function of time, as now. It addresses the
  machine BY NAME and writes two kinds of things:
  - **notes into an input** (`.to("guitars")` instead of `orbit(3)`), with their per-note values (pitch, velocity,
    the per-note stage's doors);
  - **automation of the machine's knobs**: a control lane that writes `hall.reverb.wet` or `master.eq.freq` over
    time, independent of any note. That retires "the newest sounding voice owns the orbit's settings": every knob has
    ONE value over time, and patterns are its automation. It also gives the master its slots (G2) and lets a filter
    rise sweep a whole group.
- **A library** (natural, not required): instruments and rigs shared between songs and machines. Today Kokon copies
  Der Schmetterling's rig stages by hand.

### 6.3 What stays true

- **No machine file means the default machine:** sprudel's orbits, each running the `classic` per-note stage and a
  `classic` Katalyst, summed into the master. Every song written today plays unchanged; byte identity is the
  acceptance. The one-file song stays the way in, for tutorials and first sounds.
- **Structure never depends on an event value** (signal-flow plan §3, rule 2): events choose inputs and write knobs,
  and never create nodes.
- **The engine is the horse** (stone): routing is an engine mechanism, and sprudel's layout is one machine a frontend
  sends. A MIDI controller, a sequencer and a tracker frontend address the same machine.
- **Only seconds cross the wire** (stone): the machine is a new wire value (a sealed `@WireName` hierarchy), and
  automation arrives as timed control events in seconds.
- **The cost rules** of section 3: allocation-free per block, per-playback cleanup, the block budget on the Fairphone
  and on Node.js. References read the previous block (section 5), so the graph needs no evaluation order and allows
  cycles.

### 6.4 What it unlocks (from the research)

- Shared reverb and delay returns with send levels. Parallel (New York) compression. Group buses: the drums through
  one compressor, the guitar strings into one amp (the real power-chord growl).
- Sidechain anything: section 5's reference, a follower and a knob that takes a signal.
- Automation on any bus and on the master: filter rises, the build-up highpass, volume rides.
- An Ignitor tree as a node on a bus (research section 4.0 (b)): a node kind of the machine.
- The per-note stage choice: a node kind too. This is where the doors and velocity question gets its answer.
- Stem-style mastering: the groups ARE the stems.

### 6.5 Open, for the design round

1. **The name** of the machine: candidates:
   - `Motor`: the author assembles their own Klangmotor from Ignitors, Cylinders and Katalysts; the house metaphor
     completes itself.
   - `Rig`: what Kokon already calls a guitar's chain; a musician's word for a whole setup.
   - `Studio`, `Patch`.
2. **Files:** a project of several files with imports, or one file with two sections as the smaller first step.
   What exists: an `import ... from "name"` resolves through `LibraryLoader.loadLibrary(name)`, which returns a
   registered library's KlangScript SOURCE (`KlangScriptEngine.kt`), so a user file registered as a library looks like
   a small step. Not verified beyond reading that path (2026-10-08).
3. **The pattern doors that write bus knobs today** (`reverb`, `delay`, `compressor`, `duck`, `phaser`, `body`,
   `vowel`): do they become automation of the input's bus, and what is the rule when two patterns write one knob?
4. **V1 or after:** the default machine keeps tutorials possible without it. But if a bus door's meaning changes
   from "this note's orbit setting" to "automation", that is a shape change, and shape changes land before the
   tutorials (`docs/tasks/_v1-scope.md`).
5. **Section 3's inventory** is still the first step, now with a target in view.

### 6.6 The name, and the bridge from a pattern into the Motor (maintainer, 2026-10-08)

**The name is `Motor`** (maintainer: "Motor would be a nice name here"). The author assembles their own Motor from
Ignitors, Cylinders and Katalysts, and the Klangmotor runs it. The user can build any layout. The defaults are lanes,
each starting with an instrument, routed through its effects to the master, so that every sprudel door finds its
stage. "The layout becomes essentially fixed. Unused stages are basically skipped." That second sentence is the
gate law already in force: a stage at its off value is not built.

**The maintainer's sketch: the bridge on the language level.**

```javascript
note("a").compressor(...)                                     // an error: no bus defined
note("a").bus("abc").compressor(...)                          // an error if "abc" has no compressor stage, or it works
note("a").bus("abc").compressor("c1", ...).compressor("c2", ...)  // two compressors on "abc": they need names
```

"Also `.bus()` is probably not the correct naming here. This is more like an injection / input point into the
machine. This is a very rough sketch but it might work."

**The coordinator's refinements (2026-10-08, for the design round):**

1. **Resolve a door along the note's path.** `.compressor(...)` looks along the path from the note's entry point
   through its groups to the master. It errors in three cases:
   - none is found: "track 'abc' reaches no compressor";
   - exactly one is found: that stage;
   - several are found: an error that lists their names, so the author picks one.

   The default Motor has one of each classic stage per lane, so every sprudel door resolves and every song today
   plays unchanged.
2. **Names are part of the Motor, not of the call.** A stage is named where it is declared (default: its kind), and a
   call picks one by name. The candidates are a `name` argument (`compressor(threshold = -20, name = "glue")`), a
   full address (`set("abc.glue.threshold", -20)`), or both. KlangScript forbids mixing positional and named
   arguments in one call, which argues against a positional name first.
3. **Two time meanings, decided by where the stage sits.**
   - A door on the PER-NOTE stage (`lpf`, `adsr`, `distort`) is the note's own value, as today.
   - A door on a SHARED stage (a lane's, a group's or the master's Katalyst) is AUTOMATION: a timed write that holds
     until the next one, like a MIDI CC.

   Continuous pattern signals arrive as ramps (a target and seconds; the knob glide already moves a knob to a
   target). The later write in time wins, and equal times go by stack order. This replaces the newest-voice
   ownership of a bus.
4. **Automation without notes:** a lane of knob values with no note attached (`"<-20 -12>".set("abc.glue.threshold")`,
   spelling open). The note-attached door is sugar for a write at the note's onset.
5. **Errors in the frontend, at evaluation.** Sprudel holds the Motor value, so the editor can underline
   `compressor` on a track that reaches none. This closes the item left open on 2026-09-18 (signal-flow plan §6):
   "a construction that makes 'this instrument does not listen to that door' impossible to write by accident".
6. **The entry point's word.**
   - `track` is the common language (Ableton: MIDI and audio tracks, group tracks, return tracks, the master, sends),
     so the Motor would declare tracks, groups, returns and the master, and a pattern would write
     `note("a").track("bass")`. `to`, `track` and `input` are all free in sprudel today (grep, 2026-10-08).
   - It needs one decision: "cylinder is the word, orbit a sprudel alias" (maintainer, 2026-10-07). Is the user's
     word `track`, with Cylinder the engine's word for what runs it, or `cylinder` everywhere? `orbit(n)` stays
     sprudel's alias for the default Motor's numbered lanes.
7. **Instrument per track or per note.** A DAW track has one instrument; sprudel picks one per note
   (`s("bd sd")`). Both can hold: a track may declare its instrument, and a note's `sound()` overrides it unless the
   track forbids that. To decide.

### 6.7 How we get there: a prototype first, outside the codebase (maintainer, 2026-10-08)

> "Before we do any implementation here in the codebase we need to build a prototype, with graphical display of the
> motor to visually check. Then we construct events and send them into the motor, to see which errors they produce and
> how they are routed. Basically a 'debugger' of sorts. I want to keep this out of the main codebase, so we can test the
> design in quick iterations, where the code quality does not matter."

**The rule:** no Motor code in the repository until the design has been played with in the prototype. The prototype is
throwaway, and its job is to find out whether the design holds. What survives goes into this plan, not code.

**What it shows**, as the coordinator sketched it on 2026-10-08:
- **A Motor drawn as a graph:**
  - tracks, each an instrument, its per-note stage and its Katalyst stages;
  - groups, returns and the master;
  - sends with their levels, and sidechain references (section 5) as dashed edges;
  - stages that nothing writes, drawn as skipped.
- **An event console:** write events in a sprudel-like mini syntax (`note("a").track("bass").lpf(800)
  .compressor(threshold = -20)`, a note-free `set("bass.glue.threshold", ...)`) and send them in.
- **The resolution trace, per event:**
  - the note's path through the graph, highlighted;
  - every door resolved to its stage, per-note or automation (6.6 item 3);
  - the errors: no such track, no such stage on the path, an ambiguous stage with its candidates.
- **An automation timeline per knob:** the timed writes, which writer won where two collide, and the ramps.
- **Example Motors to test the design against:**
  - the default sprudel layout, which must route every door exactly as today;
  - a Kokon-style band: guitar strings into one amp group, a shared hall return;
  - a deep-house setup: the kick referenced by the bass's duck, pads into hall and delay returns, a master with glue,
    clipper and limiter.

**Where it lives:** outside the repository, a single-page prototype (HTML and JavaScript). It is published privately as
an artifact so it can be opened on any device, and its source is kept in a folder next to the repo, not in it.

**Built: Motor Lab v0, 2026-10-08.**
- **Source:** `/opt/dev/peekandpoke/klang-labs/motor/`, with its own local git and no remote. `node test.js` checks the
  routing rules.
- **Published privately:** https://claude.ai/artifact/DR6VcnEd7kaikuMTDLuAuU
- **What it holds:**
  - the maintainer's first layout, 8 tracks "0" to "7" with the classic chain, all into the master, and a number
    becoming a string (`orbit(0)` is `track("0")`);
  - a sketch band with groups, returns and a wired duck;
  - every sprudel door mapped to its slot keys (`doors.js`, read from the repo the same day).

**Findings so far:**
1. **Stage names can repeat across the nodes of one path:** `glue` on the drums group and on the master. "Pick a
   stage by name" is then still ambiguous, so a name may be qualified with its node (`"drums.glue"`).
2. **A track with the full classic chain carries its own bus effects.** In a band layout, `.reverb()` on such a
   track lands on the track's own reverb, never on a shared hall. The lab therefore has `classicNote()`: the per-note
   doors without the classic bus chain. Which of the two the default Motor's tracks use decides what `.reverb()`
   means.
3. **The duck is the only door whose value is wiring:** `duck(orbit = n)` picks its sidechain source per note. The lab
   shows it as a warning in the default Motor (legacy) and refuses it where the Motor wires the duck.

### 6.8 Continuous automation: tweens (maintainer, 2026-10-08)

> "Advanced topic would be how to send continuous automation for e.g. a filter instead of doing it per event ...
> something like 'tween the lpf freq from 100 to 10000 Hz over 10 s along this curve' ... but this can be built
> regardless of the stuff above, it should only add more sophistication, not new functionality."

**A tween is one automation write with a duration and a curve:** a start value, a target, the seconds, and a curve
index from a catalogue (linear, exponential, the s-curve; the envelope's `AdsrCurves` are a natural start). It crosses
the wire as seconds (stone rule). The engine moves the knob along it per block, or per sample where the knob takes a
signal, the KnobGlide machinery with a duration and a shape. A plain write is a tween of zero seconds, so tweens refine
the automation of 6.6 and add nothing beside it. The frontend turns a cycle-based pattern (`saw.slow(8)`) into tweens,
and the backend never learns cycles.

### 6.9 `parallel` and `serial`: branches side by side, the twin of a chain (maintainer, 2026-10-09)

**BUILT 2026-10-10**, all five steps (`serial`, `parallel` on both hosts, `bands`, `blend`), merged through PR #87:
`docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md`. The text below is the design as decided.

Raised while listening to the Katalyst `distort` stage on Kokon's master ("now we are distorting the hats and the bass
drum") and asking whether people saturate only some bands. They do: multiband saturation on the master, or more
often saturation per group. The maintainer's operator:

```javascript
signal.split(
  x => x.bandpass().distort(),
  x => x...,
  x => x...
).shape().limiter()
```

`through(a, b, c)` runs a signal through stages in series; `split(a, b, c)` runs it through branches side by side and
sums them. With the two a chain becomes a small graph, and many separate features become one-liners.

**Back pocket for the tutorials** (maintainer: "We need to keep these things in the back-pocket for later
tutorials"):

| trick | with `split` |
|---|---|
| multiband saturation | `split(low => low, mid => mid.distort(0.3), high => high)` (with flat bands, see `bands` below) |
| exciter | `split(x => x, x => x.highpass(3000).distort(0.4).gain(0.1))` |
| parallel ("New York") compression | `split(x => x, x => x.compressor(-30, 10).gain(0.5))` |
| parallel saturation, any wet/dry | `split(x => x.gain(0.7), x => x.distort(0.5).gain(0.3))` |
| bass harmonics on any bass | `split(x => x, x => x.lowpass(120).distort(0.5, "rectify").highpass(90))` |

**It is the general form of every `wet` knob** (maintainer): `signal.split(x => x.effect().mul(wet), x => x.mul(1 - wet))`.
That is a LINEAR crossfade, right for a correlated branch (a distortion or a filter of the same signal: constant
level). For a decorrelated branch (a reverb, a chorus) the right law is equal power, `cos` and `sin` of `wet * pi / 2`,
or the mix dips by about 3 dB in the middle. That is why the engine's stages carry two wet laws (`WetDryMix`,
correlated and decorrelated branches). A `wet` knob hides that choice; `split` makes the author choose. A helper could
keep the classic form with the right law per kind (`x.blend(wet, y => y.reverb(...))`, a sketch).

**Cost.** Memory is small: on a voice the input subtree is shared (the memo) and each branch takes a scratch buffer
while it renders; on a bus each branch takes one stereo block buffer (128 x 2 x 8 bytes, about 2 KB). What costs is
each branch's CPU, which a built-in `wet` stage pays too.

**What it must get right:**
1. **Flat bands.** `x.bandpass()` per branch does not reconstruct the input: the bands overlap and leave dips and
   bumps. A frequency split needs complementary filters (Linkwitz-Riley crossovers: two chained 2nd-order
   Butterworths per band; the bands sum to the input in level, only the phase turns). Hence a `bands(...)` helper next
   to `split`.
2. **Branch latency.** A branch with oversampling or a lookahead arrives late, and summed with an undelayed branch it
   combs. The split delays the faster branches to the slowest (the engine knows each stage's latency,
   `KatalystLatentEffect`). This happens on a voice TODAY: Kokon's Screamer pedal sums a clean branch with
   `distort(0.35, "soft", 2)`, whose 2x oversampler is 4 samples late, a comb with a first notch near 6 kHz before its
   lowpass.

**The shape of `bands`, open** (maintainer: "it needs better params structure"). Two candidates:

```javascript
// A: left to right like the spectrum, band, cut, band, cut, band: bands cannot overlap or leave gaps
x.bands(b => b.band(low => low).cut(120).band(mid => mid.distort(0.3)).cut(6000).band(high => high))

// B: the crossovers first, the processors by position; a band left out passes untouched
x.bands([120, 6000], [low => low, mid => mid.distort(0.3)])
```

**A it is (maintainer, 2026-10-09):** "I lean towards A too, as it is in the spirit of the rest of the DSLs. And as I
said, I do not like the pythony parallel arrays at all." B is rejected.

**A's rules (maintainer and coordinator, 2026-10-09; settle the details with `/dsl-design` when it is built):**
- **It reads the spectrum from the bottom up.** Each `cut(f)` closes the band below it: `band(low).cut(120)
  .band(mid).cut(6000).band(high)` is 0 to 120 Hz, 120 to 6000 Hz, and 6000 Hz to the Nyquist frequency.
- **One builder type, no alternating types** (maintainer: "I would not overcomplicate it with something like an
  alternating builder type").
- **`band(...).band(...)` without a cut between them sums.** Both processors run on the same band and their outputs
  add, through the same split-and-join mechanism (maintainer). It is a sum, so `band(x => x).band(x => x)` is twice the
  band (+6 dB), the same as with the operator itself; the KDoc says so.
- **A band nobody processes passes untouched.** That covers two cuts in a row, a chain that starts with a cut, and one
  that ends with a cut: `b.cut(120).band(mid => mid.distort(0.3)).cut(6000)` processes only 120 to 6000 Hz.
- **Cuts are coerced, never refused** (maintainer: "I like the coercion idea to at least the previous value, this
  makes sense"). A cut below the one before it is raised to it, which leaves a zero-width band, silent rather than
  wrong. It is one rule for a literal cut and for one a pattern moves, and never a silent sort: sorting would hand each
  processor a band other than the one it was written for. The stone rule agrees: coerce user-reachable inputs, never
  `require()` them.
- **The bands sum flat:** Linkwitz-Riley crossovers, with the phase alignment that three or more bands need.

**Credits when it lands** (the credits rule, 2026-10-09): Linkwitz-Riley crossovers (Siegfried Linkwitz and Russ Riley,
1976) for `bands`; SuperCollider and SuperDirt as the precedent of the general graph under the orbit convention (6.1).

**Decided (maintainer, 2026-10-09):**
- **The names are `parallel` and `serial`**, the mixing vocabulary ("serial compression", "parallel compression"). The
  maintainer: "parallel is a good name but not in the same spirit as through ... so I would suggest as pair parallel /
  serial". `through` is RETIRED for `serial`, the same behaviour, removed and not deprecated (one word per concept).
  It migrates 6 calls in the built-in songs (Kokon 4, Der Schmetterling 2), both doors (Ignitor and Katalyst), its
  parity spec, the two writing references and a benchmark case, before the tutorials (a shape change), with an entry in
  `docs/retired-names.md`.
- **`parallel` SUMS its branches.** It does not average: a crossover's bands add up to the input only as a sum; dry/wet
  stays plain arithmetic (`mul(wet)`, `mul(1 - wet)`); adding a branch never changes the others. The KDoc says that two
  identity branches give +6 dB.
- **An empty `parallel()` returns the signal unchanged** (maintainer), a deliberate definition (an empty sum would be
  silence), and the same as `serial()` (today's `through()`). One branch is that branch's output.

**The proposed scope, in build order** (2026-10-09, open points below):
1. `serial`: the rename, both hosts.
2. `parallel` on the Ignitor: the sum, empty is identity, branches ALIGNED BY LATENCY (an oversampled branch is 4 to 6
   samples late).
3. `parallel` on the Katalyst: a stage holding branches of stages. A branch's tail and latency count for the chain,
   and one block buffer per branch is allocated at build. This makes "distort only the mids on the master" possible.
4. `bands`, built on `parallel`, both hosts.
5. Optional: a dry/wet helper, `x.blend(0.1, y => y.distort(0.5))` = `parallel(y => y.mul(0.9), y => y.distort(0.5).mul(0.1))`.

**Every step ships something to hear** (maintainer, 2026-10-09: "definitely needs tutorials / recipes for that ...
especially since I have to experience this first hand, which I never did"): a recipe per step in the writing
references (`.claude/skills/klang-music-writing/ref/`), and listening material for the maintainer: a `bands` with
nothing processed against the dry (the all-pass, level flat), the same with a crossover summed naively (the hump at
the cut), parallel saturation at a few blends, the Kokon master with mids-only distortion. Material for the tutorial
quarter as well (the back-pocket table above). The credits land with the code that uses them (Linkwitz and Riley with
`bands`).

Out of scope: the Motor routing, sprudel pattern doors, removing the stages' own `wet` knobs (to reconsider once
`parallel` has proven itself), moving cuts, a better oversampler.

**Open before building** (taken as the coordinator's leans when the work started, 2026-10-10, reversible by the
maintainer; the task: `docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md`):
- **The crossover of `bands`.** With Linkwitz-Riley crossovers, a `bands` with nothing processed is an ALL-PASS, not the
  input: flat in level, the phase turned around each cut, so the waveform and its peaks change. The alternatives:
  complementary by subtraction (`high = x - low`, the sum is exactly the input, but the upper band's slope is soft and
  bumpy), or linear phase (clean, but milliseconds of latency). The coordinator's lean: Linkwitz-Riley, the standard
  of multiband tools, with the all-pass documented.
- **The helper's name and law.** Not `wet` (a knob on many stages; a door of the same word blurs the two in
  completion) and not `mix` (the Ignitor's crossfade, an alias of `lerp`); `blend` proposed. Linear law only (right
  for distortion and filters); equal power, for reverb-like branches, when a song asks.
- **Step 3 now, or after the Motor Lab:** `parallel` on the bus is local and simple; the coordinator's lean is to
  build it directly and keep the lab for the routing questions.

**The operator's name, the earlier search** (maintainer: "it needs a nicer name"), superseded by the decision above. `bands` stays as it is (maintainer, 2026-10-09:
"bands is fine as a name"). The coordinator had proposed sprudel's `layer` and `superimpose`, which mean the same on
patterns; rejected: "Sprudel should not be the naming source here." The name comes from the engine's own vocabulary or
the common language of audio, to be found with `/dsl-design`.

**Where it lives, and the order:**
- On the Ignitor it is nearly free: sugar over `plus` with a shared input, plus the latency alignment.
- On the Katalyst it is STRUCTURAL: a chain becomes a tree. That raises how a pattern addresses a stage inside a
  branch, tails and swaps per branch, and the buffer per branch. This is the local half of the Motor, so it is decided
  with the maintainer (the complexity rule).
- Proposed order: try `split` and `bands` in the Motor Lab first, then the Ignitor, then the Katalyst as the first
  real piece of the Motor. A `distort(..., band(...))` option would then never need to exist.

## Links

- `docs/plans/signal-flow-redesign.md` sections 5 (built-in instruments) and 7 (the Katalyst).
- `docs/tasks-archive/2026-09/20260928-builtin-instruments.md` (phase 3, archived 2026-09-28: `classic()`, the door shapes, the shared cores).
- `docs/tasks-archive/2026-09/20260928-katalyst-dsl.md`, `docs/tasks/master-dsl-followups.md`, `docs/tasks/future/one-chain-host.md`.
- `docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md` (the master became a Katalyst at the output).
