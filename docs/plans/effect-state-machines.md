# Effect lifecycles as state machines

> Decided with the maintainer 2026-09-18, during the Katalyst work (`../tasks/katalyst-dsl.md`,
> step 5c). Performance first, readability a close second: the model must cost what the flags cost
> today and read as a state machine.

## 1. The shape

Every effect that has a lifecycle (off, active, draining, fading out, crossfading) owns ONE
instance of each of its states, created with the effect, and a `state` field that points at the
current one. A transition is a pointer swap after an `enter(...)` call on the target; the state
machine itself adds no allocation after construction, on any thread. (That is a statement about
the STATES. What an effect allocates for other reasons, a `Compressor` per engage, two filter
banks per material change, is that effect's own debt and its identity spec does not see it.)

```kotlin
class KatalystDelayEffect : KatalystEffect {
    // Resources that outlive a state live on the effect: the ring, its tail ceiling, the silent
    // input buffer. The ring is handed back by release()/retire() from ANY state.
    var delayLine: DelayLine? = null
        private set
    private val activeTail = TailCeiling()

    // The base is a nested sealed class (a sealed class cannot be inner); the subclasses are
    // inner, so they reach the effect's resources by name. Dispatch is virtual and no `when`
    // over the states remains, so `sealed` is documentation of a closed set, nothing more.
    // The methods are the EVENTS the host raises, not only process and hasTail.
    private sealed class State {
        abstract fun process(ctx: KatalystContext)
        abstract fun hasTail(): Boolean
        abstract fun deactivate(line: DelayLine)   // the owner's off-config
    }

    private inner class Off : State() { fun enter() { ... } ... }
    private inner class Active : State() { fun enter() { state = this } ... }   // owns NOTHING
    private inner class Draining : State() {
        private var remaining = 0.0                 // dies with the state, so it lives here
        fun enter(remaining: Double) { this.remaining = remaining; state = this }
        ...
    }

    private val off = Off()
    private val active = Active()
    private val draining = Draining()
    private var state: State = off

    override fun process(ctx: KatalystContext) = state.process(ctx)
    override fun hasTail(): Boolean = state.hasTail()
}
```

As built on 2026-09-19 (Katalyst 5c-1, the template). The class KDoc of the effect carries the
transition table, states times events; the next conversions copy that table first.

## 2. The three rules that keep it as fast as the flags

The rule of thumb for every `State.process()`, in three lines (maintainer, 2026-09-18):

1. copy everything the loop needs into locals (knobs, resources, the state the loop advances);
2. run the per-sample loop on locals only;
3. write the locals the loop advanced back to their fields, once, after the loop.


1. **One dispatch per block.** The host calls `state.process(ctx)` once per block. That is one
   virtual call, the price of the `when (state)` on an enum it replaces. The state's method
   reads everything the block needs into locals before the loop runs, wherever the loop lives
   (for the delay it lives in `DelayLine`, not in the state); the loop never reads `this.state`,
   a state field or an outer field. This is the same discipline the
   SVF and the resonators already follow for their coefficients, and on Kotlin/JS it matters more
   than on the JVM, so it is not optional.
2. **A datum belongs on a state only if it DIES with that state.** The delay's countdown dies
   with `Draining`, so it moved there. The delay's tail ceiling LOOKED like `Active`'s, but it
   survives `Active -> Draining -> Active` (the ring still holds that content; the ceiling is
   frozen across the drain, a conservative bound while Draining and at the moment of return
   wherever the ring decays; the exceptions are recorded in the effect's KDoc, their one home),
   so it
   stays a resource on the effect beside the ring and the
   silent input buffer. The live knobs stayed on the `DelayLine`, where the DSP reads them and `configure` writes
   them onto whatever line `ensureRing` returns: a copy on `Active` would have been a second
   source of truth. `Active` ended up carrying no data at all, and that is the correct answer, not a
   smell. Data-less states are plain `inner class`es too, not `object`s, so every state is
   written the same way. **An outer member a state reads stays `private`.** Measured with `javap`
   in step 5c-1 (Kotlin 2.3.10, JVM target 17): a private outer member reached from an inner
   class costs a synthetic `access$getX$p`, a STATIC call; making it `internal` replaces that
   with `getX$module()`, a VIRTUAL call. Neither is a plain field load, only `@JvmField` would
   be, and that cannot be written in `commonMain`. On Kotlin/JS both spellings compile to plain
   instance properties. So there is nothing to buy with `internal` (an earlier version of this
   plan said otherwise), and rule 1 is the rule that pays: V8 does not reliably hoist a mutable
   property read out of a per-sample loop.
   **An arm of an event that is the SAME from every state stays on the effect and does not
   dispatch.** The delay's `configure` on-arm is identical from Off, Active and Draining, so it
   stays on the effect and ends in `active.enter()`; only the off-arm dispatches
   (`state.deactivate(line)`). That is why the common case's `configure`, an orbit whose delay is
   off and never had a ring, compiles to the same bytecode before and after; its `process` trades
   a null-check return for an empty virtual call, the same cost by another mechanism.
   **Recorded exception (Katalyst 5c-5, 2026-09-19):** since the tail-ceiling fix the delay's
   on-arm is no longer identical from every state: a return from Draining hands the drain's
   feedback to the tail ceiling (`resume`, and above 1 a re-measure). It is written as ONE
   reference compare, `if (state === draining)`, on the effect's arm rather than a dispatch
   (`state.activate(line)`), because the arm stays otherwise shared and the compare is cheaper
   than a virtual call on every configure. A conversion that copies this plan copies the rule,
   not the exception: an arm that differs in MORE than one state-specific line dispatches.
3. **`enter(...)` is the only way in, and the only thing that INITIALISES the state's own
   data.** `enter` sets `state = this` and initialises the fields that die with the state. The
   state's own `process` may ADVANCE them (the delay's countdown); nothing outside the state
   writes them at all. So a state can never carry a previous life's values: the delay's
   countdown used to survive `Draining -> Active` (never read there, but carried), and that
   cannot be written any more. The one exception is the field initializer
   `private var state: State = off`, benign when a fresh resource equals a reset one; say so at
   that line.

### What the delay taught, stated for the delay

These are TRUE OF THE DELAY, where they were built and mutation-checked. They are not general
laws: two earlier versions of this section stated them for every effect and were wrong for three
of the four (review ledger, 2026-09-19).

- `Active.enter()` does not reset what outlives the state, the ring and the tail ceiling. A delay
  that returns mid-drain would otherwise report no tail while it audibly rings, the orbit would
  be deactivated, and the echo train would stop dead. Row: "a delay that returns mid-drain keeps
  the ring AND the tail ceiling" (`hasTail()` never false, the mix bit-identical to an effect
  that stayed Active on silent sends; both halves mutation-checked separately).
- `Off.enter()` forgets the RECORD of what the unit held (`activeTail.reset()`); the unit itself
  is untouched. Forget that, and the next life's `hasTail()` reads the previous life's ceiling
  and holds the orbit long past its due, for ever at a feedback of 1 or more. Row: "a life that
  ended in Off starts the next one with an empty ceiling".
- Entering Off has a precondition, a contract on `Off.enter`: the caller has already zeroed the
  ring or handed it back, because `Off.hasTail()` answers a hardcoded false.
- A drain runs on its own countdown. Row: "the countdown a drain runs on is its own".
- The identity spec: every reachable cell of the transition table points at one of the three
  preallocated instances, compared with `shouldBeSameInstanceAs` and an identity COLLECTION (a
  list plus `none { it === state }`, never a Kotlin `Set`, which compares with `equals`), through
  a seam `internal val currentState: Any get() = state`. It proves the STATES are not
  re-allocated, nothing more.

### What each remaining conversion must work out for itself

A template states REQUIREMENTS per effect. A MECHANISM waits for that effect's own step, written
with every host file open, and where it changes what a listener hears it is decided with the
maintainer and recorded in `../tasks/katalyst-dsl.md` BEFORE the step is briefed.

- **Copy the shape, not the condition.** Each effect's predicates are its own. The reverb's
  OFF-ARM test ("already silent", now its `Active.deactivate`) carries `|| !remaining.isFinite()`: a non-finite countdown there means a
  POISONED network, and the reset is the only exit such an orbit ever gets. Its countdown-end
  test is a plain `<= 0.0`. A transliterated delay condition pins that orbit for the life of the
  playback.
- **Each conversion answers three questions in its own terms and pins each answer with a row
  that can fail:** (1) what outlives its states, and which `enter` must therefore not touch it;
  (2) what RECORD of a finished life must be forgotten, and where (for the reverb it is the tail
  ceiling AND the size glide, both forgotten in `Off.enter()`; the glide used to be forgotten on
  the way OUT of Off behind a state test on the ON arm, and forgetting it on the way IN is
  equivalent because nothing reads or advances it while Off, and keeps the ON arm uniform; for body and vowel it is the HOST's config cache, which lives
  outside the swap, and a cache that survives a fade-out to Off makes the identical material
  silently never re-install; for the compressor it is the envelope and the knobs, or the
  instance; the gain has no terminal state); (3) what its Off precondition is (for an insert:
  the stage has reached identity, wet weight 0 or gain reduction 0 dB, or the host guarantees
  silence; entering Off early IS the click). That describes the END state: in each effect's
  first, identity commit today's early entry into Off is kept bit for bit. A row written as "`hasTail()` is false" is vacuous
  wherever `hasTail()` is a constant. (4) which state data are REFERENCES (the swap's old pair and
  its fade position, the compressor's instance, the cylinder's outgoing chain), and which event of
  that state drops them on the way out; that event then dispatches to the state. The delay has
  none (its state data are `Double`s: the countdown and, since 5c-5, the drain's feedback), which is why its `reset()` and `release()` may enter
  Off without dispatching; a copy of that shape for the swap's `clear()` would keep two dead
  banks alive. (Since 5c-11 the swap's `clear()` while Crossfading is a REFUSAL rather than a
  dispatch, and the dead-bank question moved with it: the host parks a config, which holds no
  bank at all.)
- **REQUIREMENT for every re-entry (an owner that comes back while the stage is on its way
  out): the output is continuous across it, never a jump, never a restart that steps.** For the
  delay and the reverb that is met by identity with "stayed active" (see above). For the filter
  swap and the compressor the mechanism is OPEN and is not this plan's to settle. (Settled in their
  steps: 5c-6 for the swap, 5c-7 for the compressor, both by turning the fade around from the
  weight it has at that sample, on the same banks or instance.)
- **Decided 2026-09-19 (complexity rule): no identity-only commit where the lifecycle is trivial.**
  The compressor effect's whole lifecycle today is one nullable field (about ten executable
  lines), and the gain's is a snap flag and a ramp; converting either to state classes now would
  multiply the code and change nothing. Each gets its states in 5c, in the same step that adds its
  switch-off behaviour, where the states model something real; that step is then a sound change
  accepted on identity for the edges HEAD already has.
- **The filter swap and the compressor convert in TWO commits each.** First the lifecycle they
  have TODAY as states (identity, accepted on the full list below); then the switch-on and
  switch-off glides as a SOUND CHANGE under the 5c listening checkpoint. What those glides do
  was decided with the maintainer on 2026-09-19 and is recorded in `../tasks/katalyst-dsl.md`
  ("how every orbit stage switches and changes"): always glide from the current state to the
  target, the first initialisation instant; body and vowel first merge into one resonator bank.
  Whatever the law, the oracle is an identity computed from a reference and the decided law,
  never a step threshold taken from a run of the code under test.
- **The gain** has three lifecycle situations today, not two: fresh (nothing multiplied since
  construction or reset: EVERY `configure` before the first `process` snaps, and it is `process`
  that leaves this situation; a chain arriving through `beginFade` can see two configures before
  its first block, and the identity harness scripts exactly that), settled, ramping. `currentGain` outlives
  all of them. The identity conversion keeps the snap; the parked finding of
  `signal-flow-redesign.md` §11 (a fader through exactly 0 on a dry orbit can step) is a
  separate behaviour change; its candidates, one of them a cylinder change, stay in §11.

## 3. Where it applies, in order

| effect | states | today |
|---|---|---|
| `KatalystDelayEffect` | Off, Active, Draining | CONVERTED 2026-09-19 (Katalyst 5c-1), the template |
| `KatalystReverbEffect` | Off, Active, Draining | CONVERTED 2026-09-19 (Katalyst 5c-2); its off-arm test, `|| !remaining.isFinite()`, lives in `Active.deactivate` |
| `KatalystFilterSwap` (body, vowel, eq) | first commit CONVERTED 2026-09-19 (Katalyst 5c-4): Off, Engaged, Crossfading; every event dispatches, `clear` included, because the outgoing pair (a reference) belongs to Crossfading. Second commit DONE 2026-09-19 (Katalyst 5c-6), a sound change: `clear` fades to dry and enters Off only on landing, `reset` stays the synchronous hard cut; **Third commit DONE 2026-09-20 (Katalyst 5c-11), the maintainer's own model after they rejected the morph by ear:** TWO banks, the one in service and one outgoing, and a change arriving mid-fade is REFUSED by the swap (`set` and `clear` are no-ops while `Crossfading`, and `settled` is the precondition a caller reads) and PARKED BY THE HOST as the CONFIG it came as, one slot, latest wins, re-offered from the host's own `process` after `swap.process`. Parking a config and not a pair is what keeps a stage at two banks. The fade is `BANK_CROSSFADE_SECONDS` (20 ms), its own constant; `KNOB_GLIDE_SECONDS` keeps every level and dynamics glide. A `fresh` snap flag outside the states keeps the first initialisation instant. **The precedent worth naming: a state machine may REFUSE an event, and the refusal belongs in the table.** The 10-bank pool of 5c-6, its drop rule and its overflow parking are retired |
| `KatalystCompressorEffect` | CONVERTED 2026-09-19 (Katalyst 5c-7) together with its switch-off: Off, Engaged, Fading (`out = dry + w * (compressed - dry)`, w linear over 50 ms); a return turns the fade around on the same instance; `reset` enters Off without dispatching (Fading holds only numbers); a `fresh` snap flag keeps the first initialisation instant | was a nullable instance |
| `KatalystGainEffect` | DONE 2026-09-19 (Katalyst 5c-8) WITHOUT state classes: fresh, ramping and settled are exactly `KnobGlide`'s snap flag, countdown and rest, and the gain has no Off and no switch-off, so states would copy the helper (the complexity rule above); the four questions are answered in its KDoc | a `KnobGlide` plus the unity skip |
| `ResonatorBank` | RETIRED 2026-09-20 with the morph it was written for. 5c-10 gave the bank a morph and no state; the maintainer listened and rejected it (travelling resonances are an audible sweep), so `morphTo`, the capacity preallocation, `BaseSvf.retune` and `resetState` are gone and the bank is a plain parallel bank again. The output crossfade carries every material change | nothing: the bank has no lifecycle of its own |
| `KatalystPhaserEffect`, `KatalystDuckEffect` | DONE 2026-09-20 (Katalyst 5c-9) WITHOUT state classes: the phaser's situations are `KnobGlide`'s snap flag, countdown and rest plus `Phaser`'s own `engaged` latch next to the cascade it guards; the duck's Off is `ducking` being null, the field `Cylinders` already reads through `duckCylinderId`. The four questions are answered in each KDoc, each pinned by a row | had no fades of their own |
| `Cylinder` chain swap | Idle, Pending, Fading, Draining | five fields (`outgoing`, `draining`, `duckingOut`, `duckFadingIn`, `pendingKey`) |
| voice strips (phase 3 of the signal-flow plan) | per strip, same shape | build-time gate plus per-block guards |

The master bus chain swap already shares `Crossfade` with the cylinder and follows when the
cylinder's `SwapState` proves out.

## 4. Sequencing and acceptance

One effect per review round, inside Katalyst step 5c (crossfade on every switch), the delay
first as the template. Each conversion is accepted on:

- (a) a ONE-OFF effect-level harness that drives the effect through every edge of its table
  with deterministic input and compares RAW BITS per block between HEAD in a throwaway worktree
  and the final tree, which localises a difference to a block instead of a song; with ONE
  deliberate mutation on the tree side that makes the digests differ, so the harness is shown to
  be able to fail (for the delay: halving the countdown changed 1106 of 4460 lines);
- (b) minimal synth-only render rows with a peak floor and an engagement control, plus the
  built-in songs that call the stage, wall clock pinned (`timeOfDay`, `sinOfDay`), both sides
  rendered from the same song text, for enough cycles to REACH every call site of the stage (a
  call inside a late `arrange` section is not reached by 64 cycles; signal-flow plan §12; the
  frozen-song hashes this list used to name were retired with Katalyst 5a-3);
- (c) a counter in each `enter`, added and removed, that MEASURES which edges the rows drive
  instead of assuming it;
- (d) the permanent contracts the conversion worked out for itself (section 2): the identity
  spec, its re-entry row, its "a finished life leaves no record" row, and whatever else its
  three questions produced, each mutation-checked;
- (e) three timed renders each side on the same machine, within run-to-run noise;
- (f) the effect's own spec unchanged in its assertions (the lifecycle contract does not move,
  only its spelling).

Both one-off harnesses are migration fixtures and are deleted with the step; the record names
every scripted lifecycle, every render row and every song, because the next conversion copies
that list and the fixtures are gone. `audio_benchmark` has no case for an orbit stage (only the DSP cores),
so a conversion that wants a benchmark number adds the case first.

## 5. What we do not do

- No state objects shared between effects, no generic state-machine framework, no interface
  with `onEnter`/`onExit` pairs: a few inner classes and a field are the whole mechanism
  (complexity stone rule).
- No data on the states that the per-sample loop reads through the state pointer.
- No conversion of an effect that has no lifecycle. (The phaser and the duck got their ramps in
  5c-9 and stayed as they were, which spends this line.)
- **A stage whose own `process` does not run every block cannot use it as the "first
  initialisation is instant" tick** (5c-9): the duck runs in a later pass and not at all while it
  is off, so its window is the ORBIT's block, handed to it by `KatalystChain.process`. And a snap
  that sets a GLIDE's value is not always enough: the phaser's breakpoint also lives in the DSP's
  own field, so the first initialisation has to write it there too.
