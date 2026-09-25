# The engine as a signal graph; sprudel's layout as one built-in graph

Status: FUTURE, a long-term vision (maintainer, 2026-09-25). Tackled after the current engine
redesign workstream (`docs/tasks/builtin-instruments.md` phase 3 and the signal-flow plan) is fully
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
  `VoiceData` and retires the Pipeline DSL. After that the voice strip is gone and every voice is an
  Ignitor tree, so part 1 of the idea needs only the auto-attach and the tag.
- One law per effect, shared by every host (`TremoloCore`, `CrushCore`, `DistortionCore`,
  `EnvelopeCore`, `SvfCoeffSweep`): a Katalyst stage and a master stage can already run the same code.
  Part 2 needs exactly this.
- The door shapes are one shape per concept across the Ignitor, Katalyst and Master DSLs (section 3b
  of the phase 3 record): the surfaces are already nearly the same.
- The signal-flow plan section 7 made the Katalyst chain an instrument-like list whose reverb and
  delay are insert stages; the master's `MasterStageDsl` has the same model.

## 3. Open questions (to settle when this plan starts, not now)

- **Where `.sprudel()` lives.** The maintainer's first thought: a KlangScript extension function on
  `IgnitorDsl` (which needs extension functions and type annotations in KlangScript), or a Kotlin-side
  extension on `IgnitorDsl`, which needs no new language feature. The Kotlin side is the smaller step
  (stone rule: complexity is the enemy); script extension functions can follow as their own feature.
- **The tag.** How an Ignitor tree says "already has the sprudel chain": a marker node, or a field on
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
  graph node can host; the master limiter's lookahead stays master-only today (a deliberate
  exception in the guardrails) and needs a rule in a general graph.
- **Cost.** Today's fixed routing is cheap. A general graph must stay allocation-free per block, keep
  the per-playback cleanup rule ("nothing allocates without a way to clean it up"), and stay inside the
  block budget on Node.js, the shipping target.
- **The wire.** Only seconds cross it, never cycles (stone); a graph declaration is a new wire value
  (a sealed `@WireName` hierarchy, not an enum).

## Links

- `docs/plans/signal-flow-redesign.md` sections 5 (built-in instruments) and 7 (the Katalyst).
- `docs/tasks/builtin-instruments.md` (phase 3: `classic()`, the door shapes, the shared cores).
- `docs/tasks/katalyst-dsl.md`, `docs/tasks/master-dsl-followups.md`.
