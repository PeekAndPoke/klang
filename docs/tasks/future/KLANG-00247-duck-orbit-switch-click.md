# KLANG-00247: A `duck.orbit` switch onto a sounding source clicks

> **Status (2026-10-20): IDEA.**
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

**Moved to `future/` 2026-10-14 (maintainer, C9): ducking and the sidechain need their own work, maybe after the
configurable Motor work (KLANG-00216 (signal graph engine)); see also KLANG-00168 (ducking unfinished).**

Status: **open for the maintainer, measured, not fixed.** Carried 2026-09-28 out of the Katalyst DSL record when it
was archived (KLANG-00236 (katalyst dsl),
section 9, step 5c; measured in the 5c-9 review, 2026-09-20). The next action is a decision, best made by ear.

## The measurement

Moving a ducker's sidechain to a louder orbit steps the reduction by -40.8 to -19.0 dB HF against a floor of
-52.8 dB. The already-sounding sub-case (0.2 to 0.9) is no better than the silent one, so the old argument ("it
steps by exactly as much as that orbit's own onset would") bounds the magnitude but not the audibility: an
orbit-to-orbit switch brings no new sound into the mix to mask the drop. The other direction is at the floor.

## The decision: what a sidechain switch MEANS

- **(a) Leave it.** The ducker's instantaneous attack is its character ("the Motor stays raw").
- **(b) Blend the two sidechain sources over the glide** (`KNOB_GLIDE_SECONDS`), one extra envelope read per sample
  while switching. Keeps the duck working through the switch. **The record's recommendation, if it is to be fixed.**
- **(c) Glide the reduction to 0 dB, switch, glide back.** An audible dip, but no new machinery.

Since Katalyst knob expressions step 5b (2026-10-12) a MODULATED `duck.orbit` (a `Kat` modulator on the duck's
orbit knob) reaches the same switch, as a `katp` move does.

## Corners recorded next to it (pre-existing, not fixed, listed so nobody reads them as new)

- A duck handover does not carry the arriving chain's `duck.depth`, so two chains with different depths on one
  orbit step the reduction by the difference.
- A takeover onto a duck whose sidechain orbit is not yet rented freezes the reduction until that orbit sounds.
- The two-block owner liveness can answer "ducks" for an owner that died on the swap block, and the successor's
  reset then steps, as any owner change on a classic duck does.
- A continuous mid-ramp handover would need a second ramp the crossfade cannot express.

The ducking design as a whole is being rethought as composition (bus references, a gain modulator reading another
bus): KLANG-00216 (signal graph engine) section 5 and
KLANG-00168 (ducking unfinished). If that lands first, this decision may dissolve.

Code: `audio_be/src/commonMain/kotlin/cylinders/katalyst/KatalystDuckEffect.kt`, the handover in
`Cylinder.handOverDuck`.

## History

- 2026-10-20: numbered KLANG-00247 (was `docs/tasks/future/duck-orbit-switch-click.md`).
- 2026-10-27: part of KLANG-00353 (The Motor: correct at the edges), the epic grouping.
