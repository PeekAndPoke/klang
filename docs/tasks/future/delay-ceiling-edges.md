# The delay's tail ceiling: two edges on a changing owner

Status: **future, correctness, small; not planned.** Carried 2026-09-28 out of the Katalyst DSL record when it was
archived ([`../../tasks-archive/2026-09/20260928-katalyst-dsl.md`](../../tasks-archive/2026-09/20260928-katalyst-dsl.md),
section 9, found in the step 5c-5 review of 2026-09-19). Both are pre-existing and both live in
`audio_be/src/commonMain/kotlin/cylinders/katalyst/KatalystDelayEffect.kt` and the closed-form tail ceiling it asks
(`audio_be/src/commonMain/kotlin/effects/TailCeiling.kt`, home of `CEILING_MAX`), which decides when an orbit's delay
has nothing left to say.

## 1. A lengthened tap can re-reach content the ceiling has written off (open)

At a low feedback the ceiling rightly says "no tail" once the short tap has passed a burst, but a new owner with a
longer `delay.time` reads the same ring further back. Example: a delay at 0.03 s and feedback 0 takes a burst at
-1 dBFS; two windows later a quiet owner arrives at 0.18 s, and the orbit resets over a ring that would still emit
-1 dBFS. Live and on return; the old and the new ceiling agree.

Arguably right to cut (at feedback 0 it is a stale re-emission), but a reset in the middle of it is a cut.
Candidate: re-measure the ceiling on a time lengthening, as the self-oscillating return already does.

## 2. A live self-oscillating delay that falls to a tame feedback decays from `CEILING_MAX` (candidate)

The ceiling saturates at `CEILING_MAX` (1e6) while `softCap` bounds every cell at the `cap`, so after a fall from
self-oscillation to a tame feedback the hold is about twice the ring's real tail at 0.99. Saturating the delay's
ceiling at the cap would align it with the drain path. Idle hold only, never audio: this costs CPU on an orbit that
is already silent, nothing a listener hears.

## Guard, when either is done

A render row per case in the delay's spec (`KatalystDelayEffectSpec`), mutation-checked (`/review-loop`).
