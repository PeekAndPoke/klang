# A room reverb: the room's size in its timing

Status: **future, new class of reverb, not started.** Raised by the maintainer 2026-09-30 while
designing [`../stereo-reverb.md`](../stereo-reverb.md): "when on the left channel there is a sound
and the room is big, should the reverb on the right channel be delayed depending on the room size?"
Parked on purpose: it needs heavy tuning by ear.

## The physics

Two delays tell the ear where a sound is and how big the room is:

- **The head.** A sound from the left reaches the right ear up to about 0.7 ms later. `Reverb`
  already has an echo of this: the right tank runs 23 samples (about 0.5 ms) behind the left.
- **The room.** The first reflections arrive after the direct sound by the extra path they travel:
  a few milliseconds in a small room, 20 to 50 ms and more in a hall, and later on the side facing
  the far wall. This gap (pre-delay) and the pattern of the first reflections (early reflections) are
  how the ear judges a room's size, before the tail says how live it is.

## What `Reverb` does today

No pre-delay and no early reflections. The tail comes out of the combs after one revolution of the
shortest comb, about 25 to 37 ms at every `size` (tunings 1116 to 1617 samples at 44.1 kHz), in both
ears alike. `size` sets the comb feedback, so it changes how long the room rings, not how big it
sounds at the start. A small room and a hall answer at the same moment.

## Shape of the idea

- **Early reflections**: a tapped delay line per side, the taps and their levels derived from a room
  size (and perhaps a source position), in front of the diffuse tail. Left and right get different
  patterns, the far side later.
- **A diffuse tail**: today's Freeverb network, or a feedback delay network if the new class wants
  one.
- **A cheap step on the way**: a pre-delay that grows with `size`. One delay line, no new knob. Still
  a sound change for every song with a reverb, so it belongs with this design, not before it.

## To decide when it starts

- New class next to `Reverb`, or a front stage for it.
- Whether size alone drives the timing, or a second musical knob appears (door shape per
  `/dsl-design` §2; both DSLs; the same word on orbit and master).
- Cost: the reverb is already the heaviest single unit per sample.
- Tuning by ear on songs with both small and big rooms, before the tutorials lock the sound.
