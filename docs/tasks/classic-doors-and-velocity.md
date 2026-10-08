# The doors of an instrument, and velocity as touch

Status: **design question, open. Not started.** Raised by the maintainer 2026-10-08, out of the production research
([`../plans/aaa-production-tricks.md`](../plans/aaa-production-tricks.md), gap G6). To be designed after the engine
tidy-up. If it changes what scripts write, it is a shape change and lands before the tutorials (the V1 sorting rule,
[`_v1-scope.md`](_v1-scope.md)).

## 1. What the maintainer asked

**Common language first** (2026-10-08): "I think it is important to use the common language, because otherwise we
have to do a translation step every time." In synths and DAWs:

- **velocity** means touch: how hard a note is struck, per note. The instrument decides what it changes: the amp by
  default, often brightness, attack or drive as well.
- **gain** is the fader.
- **drive** or **input** is the level into a nonlinearity, a design knob.

Ours today (decided 2026-09-18, [`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md) §6):

- sprudel's `velocity` is multiplied into `gain` before the wire. It is an accent on the fader; the instrument never
  sees it.
- `pregain` is a per-note slot the instrument places, usually in front of its first nonlinearity. It is our touch
  under a guitar-amp word: "the pregain word came from my guitar & rock background, so I am not sure if it is the
  correct word here. And pregain is somehow too narrow a word."

**The deeper question** (2026-10-08): "we have to reconsider the whole `.classic()` approach ... because what if I want
to use velocity in my authored instrument, but also want to expose every other knob to sprudel or other patterns."

## 2. What `classic()` is today (read from the tree)

`classic()` (`audio_bridge/.../IgnitorDslClassic.kt`) does two jobs at once:

1. **It exposes the doors.** Every sprudel knob becomes a `<door>.<param>` slot the pattern fills, and a stage whose
   gating slot is unset is not built.
2. **It places them**, all of them, in one fixed order (the retired voice strip's order: onepole, crush, coarse,
   distort, highpass, bandpass, notch, lowpass, tremolo, adsr), as the instrument's last call.

What is already loose:

- **The slots are script-visible** (`Ign.slot.lpf.freq`, `Ign.slot.adsr.attack`, ...). An author can already place
  one door by hand by reading its slots.
- **The engine no longer asks** whether a tree ends in `classic()`. `endsInClassic()` is a tag for the editor and for
  the future auto-attach of `.sprudel()` ([`../plans/future/signal-graph-engine.md`](../plans/future/signal-graph-engine.md),
  part 1).

The conflict: an author who places a door themselves (velocity before their drive, or their own lowpass inside the
cab) and still wants the rest gets that door TWICE, once by hand and once from `classic()`. Two lowpasses read the
same slot, or velocity is applied as the square of itself.

## 3. Shapes to weigh

- **(A) `classic()` with a configure that leaves doors out:** `x.classic(c => c.without(...))`. The author says which
  doors they placed. Explicit, small; `classic()` stays the one preset.
- **(B) Doors as building blocks, `classic()` the default order of them.** Each door is a plain function `x => x` that
  reads its own slots (`door.lowpass`, `door.adsr`, `door.velocity`, ...), and `classic()` is
  `through(door.onepole, ..., door.adsr, door.velocity)`. An author writes any subset in any order, with their own
  stages in between: `x.through(door.distort, cab, door.lowpass, door.adsr)`. It uses the `through()` that Kokon
  already uses. It must carry what `classic()` does beyond reading slots: the gate, the filter envelope's slot-layer
  fill, and the compound-door fill rule.
- **(C) B as the foundation, A as sugar** over it.
- **(D) Detection: `classic()` skips a door whose slots the tree already reads.** Not recommended: it is the "code
  that predicts another walk's outcome" of 2026-09-18 (`Variants` reads a union, conditional arms skip children), the
  magic that was built and deleted then.

The coordinator's lean, 2026-10-08: (B), perhaps with (A) as sugar. No magic, plain functions, and it is the
direction of `.sprudel()` as a frontend preset.

## 4. Velocity inside that model

- `velocity` becomes a slot (`Ign.slot.velocity`, default 1.0, linear, raw) and stops folding into `gain`. MIDI key
  velocity writes the same slot.
- `door.velocity` is the amp: a multiplier at the end, where a synth's VCA sits. In the default `classic()` order it
  comes last, so every song that uses velocity today keeps its sound (velocity is a post-instrument level now; only
  floating-point association changes, so the parity spec takes a tolerance, not bit identity).
- An instrument that wants touch reads `Ign.slot.velocity` where it wants it (in front of its drive, in a cutoff, in an
  attack) and leaves `door.velocity` out, or keeps it for "louder and dirtier", as a real amp does.
- A bare tree, one with no doors, ignores velocity as it ignores every other door. Today velocity works on a bare
  tree; that is the one behaviour change. Check the corpus for bare instruments played with `velocity`.

## 5. `pregain`

The maintainer has no preference yet. If velocity is touch, `pregain`'s per-note job is velocity read by the
instrument, and per-event drive is `distort(...)` per event. That argues for retiring the word: one word per concept,
and a replaced word is removed (`docs/retired-names.md`). It is used 11 times in the built-in songs, mostly as
`saw.mul(Ign.slot.pregain)`.

## 6. Found on the way

- The `pregain` KDoc in `sprudel/src/commonMain/kotlin/lang/lang_dynamics_level.kt` says "every built-in SYNTH sound
  places the slot (samples do not)". The engine disagrees: `IgnitorRegistry.SAMPLE_INSTRUMENT` is
  `builtInVoice(Sample)`, which is `Sample.pregain().classic()`. The KDoc is wrong; fix it with this task, or earlier
  if `pregain` stays.

## 7. Where it is recorded

- The research, gap G6: [`../plans/aaa-production-tricks.md`](../plans/aaa-production-tricks.md) §3 and §11.
- The decision this would revise: [`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md) §6
  (2026-09-18), including what was tried and deleted then.
