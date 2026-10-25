# KLANG-00335: Ignitor parts and instruments in 3D: look at a guitar from all angles

> **Status (2026-10-25): IDEA.** The maintainer's idea, thinking aloud, 2026-10-25. "Very out there", held for a
> longer time already. Nothing decided.

## The idea, in the maintainer's words

"The ignitors ... imagine we could specify "parts" or similar (just wrapping existing dsp wiring with a name). And
then when you f.e. look at the guitars in Schmetterling ... It would sooo cool to display the instruments in 3D, that
they can be rotated and looked at from all angles, like players in a video game. The step inside would be somehow
represented, filters, distort etc. And the SLOTS would also need special visuals. Then someone could say: "Hey look
at my new guitar!" and you would see something that looks like an alien machine."

## Notes from the conversation

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
  across KlangScript, Kotlin and the wire). "Sound first" puts it after the engine streams; the Three.js extension
  in the app (`externals/kraftThreejsExt`) is a possible start.

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
