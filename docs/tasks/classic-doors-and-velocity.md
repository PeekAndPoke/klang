# The doors of an instrument, and velocity as touch

Status: **design question, open. Not started. Direction since 2026-10-08: a stage after the instrument (§7).** Raised by the maintainer 2026-10-08, out of the production research
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
2. **It places them**, all of them, in one fixed order (the pitch envelope on the source since pitch pipeline step 1,
   then the retired voice strip's order: onepole, crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo,
   adsr), as the instrument's last call.

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

## 7. The maintainer's direction: a stage after the instrument (2026-10-08)

> "It somehow feels as if `.classic()` on the ignitor is the wrong spot. ... So maybe something like a post-ignitor
> stage, which defaults to 'classic' but can be overwritten ... The instrument produces only the sound, think of a
> guitar which does guitar stuff. Filter and effects sit after the instrument, not inside it. ... I think we have
> discovered the inherent design flaws of strudel / tidal by rebuilding it and now thinking about how to untangle the
> situation."

**The diagnosis.** In Strudel a sound is a sample name and every effect is a field on the note, so the field set
became the architecture. The voice-side symptom is the doors baked into every voice. The bus-side symptom is bus
effects set per note, which forces "the newest sounding voice owns the orbit's settings". The 2026-09 redesign
([`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md) §2) settled WHERE the DSP runs: per note when
it needs the note (pitch, onset, its own signal alone), on the bus when it works on a sum. That rule stands. What it
left tangled is WHO authors what: `classic()` makes the instrument's author append the pattern's doors to their own
sound.

**The model:**

```
pitch doors -> instrument -> [post-instrument stage] -> channel -> bus (Katalyst) -> master
(wrap it)      the sound      per note, the doors;       gain, pan    shared
                              default: classic
```

- **The instrument** only makes the sound: the guitar does guitar things. It exposes its own knobs (Kokon's bass:
  `sub`, `harmonics`) and may read velocity as touch. It never calls `classic()`.
- **The post-instrument stage** runs per note, so several instruments on one orbit is no conflict (a default bus
  chain per note would be, as the maintainer noted). It defaults to `classic`: the doors, with velocity in its amp.
  A pattern can replace it with another chain, or with none for an instrument that is already a complete patch with
  its own filter and envelope. Double placement becomes a choice, never an accident.
- **The pitch doors are the exception.** Vibrato, the pitch envelope, fm and accelerate must reach the oscillators
  inside the instrument, so they wrap it, not follow it. This ties in with
  [`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md).

**Why it is not the retired Pipeline DSL** (`docs/retired-names.md`, 2026-09-27). That was a second DSL with its own
renderers and 30 `VoiceData` fields. The post-instrument stage is an Ignitor tree written as a function `x => x...`,
like Kokon's rigs. The frontend composes `post(instrument)` and the engine receives one tree, as today. It is part 1 of
[`../plans/future/signal-graph-engine.md`](../plans/future/signal-graph-engine.md) (".sprudel() attached
automatically"), made replaceable. It may need no engine change at all; check the registry and the build cache for the
cost of one tree per (instrument, stage) pair.

**What it gives the guitar.** A real guitar's amp is fed by the SUM of its strings, which is why a power chord
growls (intermodulation). Der Schmetterling's guitar distorts per note, cleaner than real. With this split, the amp
can sit per note (synth-like), or on the bus with a `saturate` stage (research K1, the real guitar).

**A name**, at the level of Ignitor and Katalyst, the maintainer's call:
- `Piston`, in the engine's own family: the ignitor sparks, each note's piston does the work, the pistons sum in the
  cylinder, and the Katalyst treats the exhaust.
- `Articulation`, plain language for what the player does to each note.

The coordinator leans `Piston`: musicians have no common word for this stage, and Ignitor and Katalyst are house
names at the same level.

**Open for the design round:**
1. Who picks the stage: the pattern, the instrument's registration (a complete patch declares "none"), or both, with
   the pattern overriding?
2. Velocity sensitivity of the default amp: a knob on the stage (as synths have), so an instrument that uses velocity
   for touch is not also scaled by it twice, unless the author wants "louder and dirtier".
3. The `endsInClassic()` tag, the editor's slot diagnostics, and the auto-attach: they read "does this tree carry the
   doors". In the new model they read "which stage is attached".
4. The migration: every authored instrument that ends in `.classic()` today (the built-in songs) drops the call, and
   the stage is attached instead; byte identity is the acceptance.
5. `pregain` (§5) falls out of this: touch is velocity, read by the instrument.

## 8. One level higher (maintainer, 2026-10-08): this question belongs to the machine

> "Fiddling around with `.classic()` at the current layer will not help, we will run into the same issue again and
> again."

The per-note stage after the instrument is one node kind of a machine declared OUTSIDE the patterns, next to buses,
groups, sends and the master: [`../plans/future/signal-graph-engine.md`](../plans/future/signal-graph-engine.md) §6.
This task is no longer designed on its own. Its sections 1 to 7 are input to that design round, and its small items
(the wrong `pregain` KDoc of §6) can still be fixed any time.

## 9. Where it is recorded

- The research, gap G6: [`../plans/aaa-production-tricks.md`](../plans/aaa-production-tricks.md) §3 and §11.
- The decision this would revise: [`../plans/signal-flow-redesign.md`](../plans/signal-flow-redesign.md) §6
  (2026-09-18), including what was tried and deleted then.
