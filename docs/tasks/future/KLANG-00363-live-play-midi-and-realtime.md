# KLANG-00363: Live play: MIDI and realtime

> **Status (2026-10-27): IDEA.** An epic: its progress is its children's statuses (`ticket list --epic KLANG-00363
> --deep`).

## Why this exists

Playing Klang live: a MIDI keyboard, a knob that changes a ringing note, latency low enough to play in time, and the playback stack in the frontend that carries it.

## What belongs here

Realtime and MIDI: the playback layer's decomposition, the realtime playback controller with the MIDI connector, latency, live voice modulation. Not here: the engine's signal graph (KLANG-00354).

## Children

Derived, never listed by hand: `python3 console/tools/ticket/ticket.py list --epic KLANG-00363 --deep`. At the start
(2026-10-27): KLANG-00158, KLANG-00159, KLANG-00169, KLANG-00160.

## History

- 2026-10-27: created in the epic grouping (the maintainer's ask; the grouping agreed with Klang-Research).
