# Effect lifecycles as state machines

> Decided with the maintainer 2026-09-18, during the Katalyst work (`../tasks/katalyst-dsl.md`,
> step 5c). Performance first, readability a close second: the model must cost what the flags cost
> today and read as a state machine.

## 1. The shape

Every effect that has a lifecycle (off, active, draining, fading out, crossfading) owns ONE
instance of each of its states, created with the effect, and a `state` field that points at the
current one. A transition is a pointer swap after an `enter(...)` call on the target; nothing is
allocated after construction, on any thread.

```kotlin
class KatalystDelayEffect : KatalystEffect {
    // Shared resources outlive states, so they live on the effect: the line is kept from
    // Active into Draining, and handed back only from Draining.
    private var delayLine: DelayLine? = null

    // The base is a nested sealed class (a sealed class cannot be inner); the subclasses are
    // inner, so they reach the effect's resources by name. Dispatch is virtual, so the sealed
    // modifier only buys an exhaustive `when` where a host wants one.
    private sealed class State {
        abstract fun process(ctx: KatalystContext)
        abstract fun hasTail(): Boolean
    }

    private inner class Off : State() { ... }
    private inner class Active : State() {
        var feedback = 0.0
        var cap = 0.0
        fun enter(feedback: Double, cap: Double) { ... }
        override fun process(ctx: KatalystContext) {
            val line = delayLine ?: return   // read once per block, into locals
            val fb = feedback
            ...                              // the per-sample loop touches locals only
        }
    }
    private inner class Draining : State() {
        var remaining = 0.0
        fun enter(remaining: Double) { ... }
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

## 2. The three rules that keep it as fast as the flags

The rule of thumb for every `State.process()`, in three lines (maintainer, 2026-09-18):

1. copy everything the loop needs into locals (knobs, resources, the state the loop advances);
2. run the per-sample loop on locals only;
3. write the locals the loop advanced back to their fields, once, after the loop.


1. **One dispatch per block.** The host calls `state.process(ctx)` once per block. That is one
   virtual call, the price of the `when (state)` on an enum it replaces. The per-sample loop lives
   inside the state's method and never reads `this.state`, a state field or an outer field: every
   value it needs is copied into a local at the top of the method. This is the same discipline the
   SVF and the resonators already follow for their coefficients, and on Kotlin/JS it matters more
   than on the JVM, so it is not optional.
2. **States carry the data of that state only.** `Draining` owns `remaining`; `Active` owns the
   live knob values. Resources that outlive a state (a rented `DelayLine`, a reverb unit, the two
   filter banks of a swap) stay on the effect, reached through the `inner` class's outer
   reference, read once per block into a local. Data-less states are plain `inner class`es too,
   not `object`s, so every state is written the same way. An outer field a state reads is
   `internal`, not `private`: Kotlin/JVM emits a synthetic accessor for a private outer member
   reached from an inner class, a real call in the interpreter and C1 tiers; `internal` makes it a
   plain field load. On Kotlin/JS the outer is reached through a stored property, and V8 does not
   reliably hoist a mutable property read out of a per-sample loop, which is why rule 1 is
   mandatory rather than a preference.
3. **`enter(...)` is the only way in.** A transition is `state = draining.also { it.enter(x) }`
   spelled through a private `transitionTo`-style helper per effect if it reads better; `enter`
   resets the target's fields, so a state never carries a previous life's values. No transition
   allocates; a spec proves it by asserting state-instance identity across a full cycle of
   transitions (`===` before and after).

## 3. Where it applies, in order

| effect | states | today |
|---|---|---|
| `KatalystDelayEffect`, `KatalystReverbEffect` | Off, Active, Draining | private enum plus a `when`; the template, convert first |
| `KatalystFilterSwap` (body, vowel, eq) | Off, Engaged, Crossfading, FadingOut | two nullable filter pairs plus `active`; FadingOut is step 5c's crossfade on switch-off |
| `KatalystCompressorEffect` | Off, Active, ReleasingOut | nullable instance; ReleasingOut is the ramp on switch-off |
| `KatalystGainEffect` | Settled, Ramping | fields |
| `Cylinder` chain swap | Idle, Pending, Fading, Draining | five fields (`outgoing`, `draining`, `duckingOut`, `duckFadingIn`, `pendingKey`) |
| voice strips (phase 3 of the signal-flow plan) | per strip, same shape | build-time gate plus per-block guards |

The master bus chain swap already shares `Crossfade` with the cylinder and follows when the
cylinder's `SwapState` proves out.

## 4. Sequencing and acceptance

One effect per review round, inside Katalyst step 5c (crossfade on every switch), the delay
first as the template. Each conversion is accepted on:

- the frozen songs byte-identical (`8d79b9fc…` for Der Schmetterling, the Seltsamere Dinge hash
  of 2026-09-18);
- the offline render wall time of the frozen song within run-to-run noise of the flag version,
  measured three times each on the same machine;
- an identity spec: every state instance is the same object across a full transition cycle;
- the effect's own spec unchanged in its assertions (the lifecycle contract does not move, only
  its spelling).

## 5. What we do not do

- No state objects shared between effects, no generic state-machine framework, no interface
  with `onEnter`/`onExit` pairs: three inner classes and a field are the whole mechanism
  (complexity stone rule).
- No data on the states that the per-sample loop reads through the state pointer.
- No conversion of an effect that has no lifecycle (the phaser and the duck stay as they are
  until step 5c gives them a switch-off ramp).
