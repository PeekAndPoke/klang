# KLANG-00335: Ignitor parts and instruments in 3D: look at a guitar from all angles

> **Status (2026-10-25): IDEA.** The maintainer's idea, thinking aloud, 2026-10-25. "Very out there", held for a
> longer time already. Nothing decided.
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## The idea, in the maintainer's words

"The ignitors ... imagine we could specify "parts" or similar (just wrapping existing dsp wiring with a name). And
then when you f.e. look at the guitars in Schmetterling ... It would sooo cool to display the instruments in 3D, that
they can be rotated and looked at from all angles, like players in a video game. The step inside would be somehow
represented, filters, distort etc. And the SLOTS would also need special visuals. Then someone could say: "Hey look
at my new guitar!" and you would see something that looks like an alien machine."

What a part is (the maintainer, 2026-10-25): "With part in the ignitors i would for example mean "Pre-Amp",
"Cab4x12" ...". A part is a named piece of the instrument at the level of the real thing's components: the pre-amp,
the cabinet, the pedal, the pickup. Not a single filter or clipper; those are the stages inside a part.

## Notes from the conversation

- **The parts exist in the song already, as plain functions:** Der Schmetterling has `pickupSingle`,
  `pickupHumbucker`, `pickupNeck`; `pedalScreamer`, `pedalFuzz`, `pedalBoost`; `preampClean`, `preampCrunch`,
  `preampHighGain`; `powerPushPull`, `powerClassA`; `cab4x12`, `cab1x12`, `cabCombo` (each with a `...Stock`
  preset that passes the signal through). Today they are anonymous to the engine and the UI; a part would carry
  the name ("Cab 4x12") and its kind (cabinet) along with the wiring.
- **Two levels to draw:** the part as a module of the machine (its kind sets the shape: a cabinet looks like a
  cabinet), and the stages inside it as what you see when you look closer or open it.
- **A hook that exists already:** Der Schmetterling's guitar rig is written as a chain of named stages (string,
  pickup, pedal, preamp, tone stack, power amp, cab), each a function of the signal with presets such as
  `pickupHumbucker` or `pedalScreamer`. A "part" would give such a stage a name the engine and the UI can see,
  without new DSP: it wraps existing wiring. The engine stays the horse (root rules): a part is a label on the wire,
  never DSP of its own.
- **What a part would carry:** its name, the Ignitor or Katalyst blocks inside it, and its slots (the values one can
  turn). The 3D view draws the parts as modules of one machine, the blocks inside as visible stages (a filter, a
  clipper), and the slots as controls with their own look.
- **The UI home:** a tool window or a full view of the workbench (KLANG-00334 (UI workbench)); the param tools of a
  slot could open from the 3D view.
- **The fun part:** every instrument looks different because its wiring is different, so a new instrument is
  something to show around.
- **Wait for:** the DSL work names this before anything is drawn (`/dsl-design`: one word, the door shape, parity
  across KlangScript, Kotlin and the wire). "Sound first" puts it after the engine streams; Kraft's three.js addon
  (its members since 0.117.0, ULTRA-00246) is a possible start.

## Decide before implementing

- The word for a part, and whether it lives in KlangScript only or crosses the wire.
- How slots are discovered (the knob expressions of Katalyst already describe them).
- 3D or a 2.5D drawing first.

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-25: created from the maintainer's thinking aloud during the KLANG-00334 design conversation.
- 2026-10-25: what a part is: "Pre-Amp", "Cab4x12" (the maintainer); the song's existing stage functions listed.
- 2026-10-27: part of KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail), the epic grouping.
