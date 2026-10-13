# A delay or reverb switched off by an owner change cuts its feed in one sample

Status: **decided (maintainer, 2026-10-13, Q39): fade, do not cut, so (b) below: every switch-off fades the feed
over the wet glide.** Not started; it needs the plumbing named under "The cost" (an orbit without an owner must
still re-apply its off-config), and it moves the frozen piece `derSchmetterling_2026_09_16` slightly (re-accept
after). Engine follow-up 42. Before the decision: Carried 2026-10-12 out of the Katalyst
knob expressions record when it was archived
([`../../tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`](../../tasks-archive/2026-10/20261012-katalyst-knob-expressions.md),
the 5b record and its review rounds 1 and 2). The next action is a decision, best made by ear.

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
