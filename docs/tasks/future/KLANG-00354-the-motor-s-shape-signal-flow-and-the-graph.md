# KLANG-00354: The Motor's shape: signal flow and the graph

> **Status (2026-10-27): IDEA.** An epic: its progress is its children's statuses (`ticket list --epic KLANG-00354
> --deep`).

## Why this exists

How the Motor is put together: who owns a setting, how a signal flows from a voice through its chain to the master, and whether the engine becomes a configurable signal graph. The plans here change the engine's shape, not what one song sounds like today.

## What belongs here

The signal flow redesign, the signal graph engine, shared chain plumbing, the pitch pipeline in the Ignitor tree, oversampling regions, and the DSL wiring that follows the shape. Not here: edge bugs of today's engine (KLANG-00353), speed work (KLANG-00355), the frontend's playback stack (KLANG-00363).

## Children

Derived, never listed by hand: `python3 console/tools/ticket/ticket.py list --epic KLANG-00354 --deep`. At the start
(2026-10-27): KLANG-00210, KLANG-00216, KLANG-00243, KLANG-00245, KLANG-00214, KLANG-00311.

## History

- 2026-10-27: created in the epic grouping (the maintainer's ask; the grouping agreed with Klang-Research).
