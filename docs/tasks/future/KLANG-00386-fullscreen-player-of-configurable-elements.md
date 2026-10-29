# KLANG-00386: Fullscreen player of configurable elements

> **Status (2026-10-29): NOT SCHEDULED.** An idea from the maintainer, 2026-10-29.

## Why this exists

The visuals (the Motor's plate, Klangamp) lay out the same elements in different ways: the gauges, the volume knob, the
scope, the spectrum, a playlist. The maintainer, 2026-10-29: "This could be a cool fullscreen player as well. Built out
of configurable elements, small visualizations, debug instruments etc... please note down as an idea."

So: a fullscreen player whose face is made of elements the user picks and places. Small visualizations (scope,
spectrum, meters), the Motor's instruments (heat, cylinders, voices, the diagnostics' graphs from KLANG-00385), a
playlist, transport keys.

## Decide before implementing

- How a layout is described: a grid of tiles (the plate's tiles suggest one), slots per element, or free placement.
- Where layouts are kept: presets in code, the user's own in storage, shareable?
- Which elements come first; whether debug instruments sit beside the music ones or on a layer of their own.
- How it relates to the visuals in the Visuals tab: the visuals become presets of this player, or stay apart.

## The work

To be planned once decided.

## Acceptance

To be written with the plan.

## History

- 2026-10-29: created from the maintainer's idea while Klangamp was being built.
