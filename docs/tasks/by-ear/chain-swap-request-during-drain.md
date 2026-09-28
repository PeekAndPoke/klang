# A chain request while the old chain drains: wait, or not?

Status: **open question for the maintainer's ear** (phase 3 step 12, decision (g), 2026-09-27: "kept as is for step
12, recorded as an open question for the maintainer's ear later"). Opened as a file 2026-09-28 when the step 12
plan was archived (`docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md`, section 0 row (g) and
section 8 (g)).

## What it is

Since step 12 the orbit and the master swap their effect chain by one law, `ChainSwap`
(`audio_be/src/commonMain/kotlin/ChainSwap.kt`): the leaving chain's input ramps down over 60 ms while the arriving
chain ramps up, then the leaving chain DRAINS (rings out on silence) until it has no tail. A new request that
arrives during the fade or the drain is REFUSED by the swap and parked by the host (`Cylinder`, `MasterBus`),
latest wins; it lands when the swap is idle again.

## Why it is open

A drain can be long: a size-10 room rings for about 12.5 s. Since decision (i) the drain is capped at
`ChainSwap.MAX_DRAIN_SECONDS` (20 s) plus the exponential release (about 4.5 s), so the worst-case wait for a
parked request is about 24.5 s. In live coding that is latency: a second edit of an orbit's chain, or of the
master, inside a long ring-out is not heard until the ring-out ends. Nobody has listened for it.

## The options (from the plan)

- **Keep** (today's behaviour): the edit waits, the ring-out is never cut.
- **Cut the drain** when a new request arrives: the edit lands at once; the old tail needs its own fade or it
  clicks.
- **Allow more than one leaving chain** (a list of draining chains): the edit lands at once and every tail rings
  out; more state, and the "at most two chains at once" bound goes.

## How to listen

A song with a big room (or a long delay) on one orbit, then two chain edits a few seconds apart; the same at the
master (`master(Katalyst(k => k.reverb(0.3, 10)))`, then two quick changes). Does the second edit feel late?
