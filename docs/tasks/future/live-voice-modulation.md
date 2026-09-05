# Live modulation of sounding voices (turn a knob, hear the note change)

Status: **future / concept — parked deliberately, not started.** Raised 2026-08-29 while mapping the KeyLab's
knobs in the MIDI playground; maintainer decision the same day: this is a real feature with its own design
round, not a follow-on to knob binding.

## The gap

A control value is read **once, at note onset**, and baked into the voice. So a knob shapes the *next* note and
can never touch one already ringing. The obvious thing a player will try — hold a chord, sweep the filter —
does nothing today.

Maintainer, 2026-08-29: *"in the long run this is what everyone will try … hold a note and fiddle with the
controls to change a playing sound on the fly. But this needs a whole new concept for updating running voices.
We will not go there right now."*

## Why it is a concept, not a patch

Three separate things have to change, and none of them is local:

1. **Ownership.** A control value would become something a voice *reads* per block instead of something it was
   *handed* at ignite. That needs lifetime rules: who owns the value, what happens when the voice ends, and
   what a scheduled pattern note does with a control that is still moving.
2. **The wire.** It knows `start` and `stop`. Updating a voice that has already started is a new path.
3. **Smoothing.** Every parameter that becomes mutable mid-note needs a ramp decision, or it clicks.

## Reminders for whoever picks this up

- Do not scope this as an extension of the MIDI playground. The playground is only where the gap became
  visible.
- The once-per-note rule is load-bearing elsewhere — sprudel control signals (`gain(saw.range(…))`) depend on
  it, and several songs are written against it. Any change here is a *sound* change to existing material, so
  it needs the maintainer's ear, not just a green test suite.
