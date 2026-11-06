# KLANG-00417: Parameter parity audit across every DSL surface

> **Status (2026-11-06): IN PROGRESS.** Split out of KLANG-00137 (master dsl followups) section 1 (maintainer, 2026-11-06): "the parity audit should get its own ticket and should be worked on when all the dsp restructuring is done". Starts after the DSP restructuring, so the audit reads the surfaces in their final shape and runs once.
>
> **Part of:** KLANG-00357 (Effects: the Katalyst and the master)

## Why this exists

**The rule (maintainer, 2026-08-02):** *"The same params must be available in the sprudel DSL, Ignitor, Master etc.
and they need to mean the same thing. This is important for developer experience."*

The master DSL work proved this is not theoretical: `roomSize` silently meant a ~1 s tail on a cylinder and ~12.5 s on
the master, because sprudel divided by 10 and the master did not, and `roomFade`, the parameter that actually set the
tail, existed on only one of them. Both were fixed for reverb and delay; nothing has audited the rest.

`_v1-scope.md` names this audit as the one item of KLANG-00137 that gates V1 (a renamed or rescaled knob changes what
a tutorial teaches). Running it after the restructuring keeps that: it lands before the tutorials lock the surface,
but after the shape changes that would otherwise move the knobs it checked.

## What the audit covers

Per effect and per source knob: does the same concept have the same **name**, the same **scale** and the same
**availability** on every surface it could sensibly appear on: sprudel per voice, sprudel per cylinder, the Ignitor, the
Katalyst on a cylinder and at the output, and both doors of each (KlangScript and Kotlin; KLANG-00148 (dsl kotlin
surface parity) is the two-door gate, this audit is the across-surfaces one).

The asymmetries found so far, all resolved, as the shape of what to look for:

- `room` (cylinder) against `wet` (master): one thing, two words. Resolved 2026-08-24 and 2026-09-07: both doors say `wet`.
- `room(size)` 0..10 against `room(fade)` 0..1, `fade` making `size` inert: resolved 2026-09-16 (KLANG-00208 (reverb
  naming unification)), `fade` is gone.
- `damp` on the master only: resolved 2026-09-16, `lowpass` (Hz) is the one damping knob, `reverb` the one door word.
- `delaycap` not a `delay()` slot: resolved 2026-09-07, `cap` is the fourth slot.
- `roomDim` and `iResponse` stored and never read: resolved 2026-09-16, removed end to end.

A recorded difference that is not a parity gap: `lookahead` is a knob of the Katalyst `compressor` and `limiter` at
any position (phase 3 step 12 decision (a), C2) but not of sprudel's `compressor()`, because it is fixed per chain (it
sizes a ring) while a sprudel door writes slots on the running chain. The authored `limiter()` defaults to lookahead 0
against the house limiter's 5 ms; `KlangScriptKatalystDoorParitySpec`'s `limiter` row pins the numbers. The history
of the earlier exception is in KLANG-00137 section 1 and KLANG-00225 (master limiter lookahead) sections 3 and 4.

## Decide before implementing

- **What "the DSP restructuring is done" means when this starts:** which of the KLANG-00354 (The Motor's shape) and
  KLANG-00357 children must have landed first. Ask the maintainer at that point.
- **The form of the result:** a table per stage (concept, surfaces, name, scale, default), the gaps as their own
  numbered items, each fixed or recorded as a deliberate exception with its reason in the KDoc.
- **What pins it afterwards:** the door parity specs that exist (`KlangScriptKatalystDoorParitySpec` and its
  siblings) extended to the gaps found, so a later door cannot drift silently.

## The work

1. List every surface and its knobs from the code (the doors, the wire slots, the backend stages), not from the docs.
2. The table, and the gaps.
3. Each gap fixed or recorded; a rename or rescale is a shape change and goes to the maintainer first.
4. The pins.

## Acceptance

- Every concept that appears on more than one surface has one name, one scale and one meaning there, or a recorded
  exception with its reason.
- The parity specs cover the gaps that were found.

## History

- 2026-11-06: created from KLANG-00137 section 1 (the maintainer: its own ticket, after the DSP restructuring).
- 2026-11-06: IN PROGRESS.
