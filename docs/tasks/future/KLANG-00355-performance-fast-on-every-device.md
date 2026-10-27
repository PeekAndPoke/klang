# KLANG-00355: Performance: fast on every device

> **Status (2026-10-27): IDEA.** An epic: its progress is its children's statuses (`ticket list --epic KLANG-00355
> --deep`).

## Why this exists

Klang should start fast and play without dropouts on a phone as well as on a desktop. The optimizer, the bundle, the first run and, much later, a native or Wasm backend all serve that goal. Sound first: the hard performance work comes after the engine, the tutorials and the launch (guideline, 2026-07-03).

## What belongs here

Speed and size: the Ignitor graph optimizer, sprudel's query fast paths, the JS bundle, the first-run spike, the benchmark page, native and Wasm backends (KLANG-00129, with the Zig Motor under it). Not here: correctness (KLANG-00353) or the engine's shape (KLANG-00354), unless the change is made for speed.

## Children

Derived, never listed by hand: `python3 console/tools/ticket/ticket.py list --epic KLANG-00355 --deep`. At the start
(2026-10-27): KLANG-00104, KLANG-00106, KLANG-00146, KLANG-00178, KLANG-00203, KLANG-00204, KLANG-00205, KLANG-00129.

## History

- 2026-10-27: created in the epic grouping (the maintainer's ask; the grouping agreed with Klang-Research).
