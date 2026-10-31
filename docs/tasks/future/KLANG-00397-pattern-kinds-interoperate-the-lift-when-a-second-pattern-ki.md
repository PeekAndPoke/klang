# KLANG-00397: Pattern kinds interoperate: the lift when a second pattern kind exists

> **Status (2026-10-31): IDEA.** Split off KLANG-00210 phase 4 (maintainer, 2026-10-31: the new pattern kind is not needed for the DSP work)
>
> **Part of:** KLANG-00354 (The Motor's shape: signal flow and the graph)

## Why this exists

KLANG-00210 (signal flow redesign) planned it as its phase 4, "when the second pattern kind exists, not before". The DSP
work does not need it, so it left that plan (maintainer, 2026-10-31) and waits here for a second pattern kind (a pad
controller, sheet music, tab).

## The work

As KLANG-00210 section 8 describes it: `KlangPattern.toSprudel()` lifts each foreign event into a
`SprudelPatternEvent` (whole and part from start and duration, data from `toVoiceData()`), and `PatternLike` accepts a
`KlangPattern` by lifting at the door, so `drums.reverb(...)` works on a foreign pattern. The contract a foreign kind
meets is the twelve-cycle rule (each event exactly once, in the query whose window contains its onset, with its full
duration; any sub-cycle window answered consistently), and `cps` must reach the lift.

## Acceptance

A foreign pattern kind takes sprudel's doors through the lift, with the twelve-cycle rule checked by a spec.

## History

- 2026-10-31: created.
