# KLANG-00313: A delay or reverb switched off by an owner change cuts its feed in one sample

> **Status (2026-10-16): DONE.**

Status: **DONE (2026-10-16, engine follow-up 42).** Decided by the maintainer on 2026-10-13 (Q39):
"fade, do not cut", so (b) below: every switch-off fades the feed over the wet glide. Built without new plumbing: the
off-config only STARTS the fade, and the block the wet glide lands in moves the stage on into the drain by itself
(`Active.process` in `KatalystDelayEffect` and `KatalystReverbEffect`), so "The cost" below is paid by the stage, not
by `Cylinder`: an orbit without an owner, and the leaving chain of a swap, drain like any other. The bleed of the NEW
owner's first 50 ms into the OLD owner's ring or room is accepted with the decision and recorded in both KDocs. The
corpus did NOT move: the prediction below (`frozen piece derSchmetterling_2026_09_16`) was made before Q20 moved that
piece's bass off guitar 3's orbit (2026-10-16), and the corpus reaches no switch-off of a fed delay or reverb at all
(a mutant that never switches off leaves it green), so it is no evidence here. The evidence for the fade is
`KatalystSwitchOffFadeSpec` and the listening pairs:
`tmp/listening/delay-reverb-fade/` (an owner change that switches the reverb off, a `katp` delay time below the gate).
The record: item 42 of KLANG-00290 (engine follow ups). Before the
decision: carried 2026-10-12 out of the Katalyst knob expressions record when it was archived
(KLANG-00302 (katalyst knob expressions),
the 5b record and its review rounds 1 and 2).
Heard 2026-10-18 (maintainer): "both after versions ok".

## What it is

When an orbit's delay or reverb switches off, the stage stops feeding its ring or room. Since knob expressions step
5b there are two ways it does that:

- **A modulated `delay.time` or `reverb.size` crossing its gate** fades the feed out over the wet glide, then drains;
  every return glides the feed up from 0. Built in 5b's review rounds 1 and 2 (an LFO through the gate cut and
  restarted the feed twice a cycle, and the ring repeated the edge; the restart edge, measured with the glide-up
  removed: 0.4 on the delay, 0.0104 on the reverb, DC 0.5, wet 0.8).
- **Every other switch-off cuts the feed in one sample**, as before 5b: an owner change to a voice whose settings leave
  the delay or reverb off (the orbit keeps its chain, the new owner's slots switch the stage off), a `katp` that sets the time or size below its gate, an unset time. The ring or room rings on from what it
  holds; the edge is in what was last written.

## Why it was not changed in 5b

A first version faded every switch-off. It moved one corpus song (`frozen piece derSchmetterling_2026_09_16`, peak
0.9144 to 0.9145, a reverb switched off at an owner change), and on an owner change a fade feeds the NEW owner's
first 50 ms into the OLD owner's ring or room: a bleed across owners. Step 5b's contract was "a chain without a
modulator renders as before", so the fade was narrowed to the modulated gates.

## The decision: cut or bleed

- **(a) Leave the cut.** The edge is written once, at the switch, and the combs (reverb) or the feedback (delay)
  carry it; on most handovers the old owner's sound has already released.
- **(b) Fade every switch-off** over the wet glide: no edge, but the new owner's first 50 ms (or one block, for a
  modulated wet) reach the old ring or room at a falling level.
- **(c) Fade only a `katp` switch-off**, keep the cut on an owner change: no bleed, the `katp` edge gone.

The cost of (b) and (c): the fade rests on the off-config being re-applied every block (the delay's KDoc, "The
contract"). The tick of a modulated knob provides that; for an unmodulated one only `Cylinder.commitOwner` does, and
it returns early on an orbit without an owner, so a fade whose voice stops offering would land at wet 0 and never
drain without new plumbing that re-applies the off-config.

To listen: the corpus song above at its owner changes, and a delay with feedback whose time a pattern moves below
`MIN_ACTIVE_DELAY_SECONDS` (10 ms) and back.

## Open: the mirror edge (for the maintainer, review round 1 of item 42)

The switch-ON of an unmodulated stage still snaps: out of Off its feed steps from 0 to the full wet in one sample, so an
empty ring takes a full-wet step that every echo repeats (a modulated time or size glides up from 0 there). Example:
`stack(note("c3").s("saw").adsr(0.01, 0.1, 1.0, 0.2), note("~ g4").s("sine").delay(0.5, 0.25, 0.6)).orbit(0)`: the
sine takes the orbit while the saw is held, and the delay switches on with a step of the saw in its ring. Glide it up from 0 too, or keep the snap (a song whose delay never changes stays
identical to a constant feed)?

## History

- 2026-10-20: numbered KLANG-00313 (was `docs/tasks-archive/2026-10/20261016-delay-reverb-owner-change-cut.md`).
