# KLANG-00379: Configurable side chains: ducking generalised (Motor V1.5)

> **Status (2026-10-28): IDEA.** the maintainer, 2026-10-28: Motor V1.5
>
> **Part of:** KLANG-00354 (The Motor's shape: signal flow and the graph)

## Why this exists

The maintainer, 2026-10-28: the Motor V1 is the engine without full configurability; the fully configurable engine (KLANG-00216) is V2; "V1.5 could contain configurable side chains, f.e. to generalize ducking". Ducking is built and wired but never used or tested end to end (KLANG-00168), and it is the one side chain the Motor has: a fixed source orbit pushing down a target. A general side chain lets any signal drive any stage's knob (a compressor's detector, a filter's cutoff, a gate).

## Decide before implementing

- The surface: a side chain as a Katalyst stage's input, named by orbit (sprudel's `duck` doors read as a special case of it).
- Where it runs in the block order, so a source renders before the stage that listens to it (the signal flow of KLANG-00210 and KLANG-00216 decides much of this).
- What happens to `duck` and KLANG-00168: generalised away, or kept as the short form.

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-28: created in the Q4 planning with the maintainer.
