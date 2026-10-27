# KLANG-00366: Brushed metal on chosen parts: the start page's strokes as a flat finish

> **Status (2026-10-27): SCHEDULED.** after the bottom panel's tabs
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "could we have the brushed iron look on the backgrounds similar to the start page. But
not as a 3d thing ... we have perlin noise currently, but could we create something similar that looks like the
brushed metal?" Then: "Not sure if this will be too much, so maybe just select elements", and "Try something please".

## Decided (2026-10-27)

- A finish only chosen parts get, as the grain is only on the buttons and the caps. Proposed to start with: the strip,
  the Motor's bay under the column, the tool windows' headers. Left smooth: the column's text, the editor, the help
  and the Lexikon's content, dialogs.
- The strokes are the start page's hand-brushed ones (`brushed()` in `MotorBackground.kt`: strokes of their own
  length fading at their ends, a second layer at another angle, Perlin pressure), painted once into a tile that
  repeats without a seam, laid over a part in `overlay` like the grain. The function moves to klangui, so the start
  page and the parts share it.
- Each look sets its strength (a new look role); 0 turns it off.

## Decide before implementing

- Which parts, judged by eye in the browser; the strength per look.

## The work

1. klangui: the brushed tile (painted once, seamless) and the finish as a style; the strength as a look role.
2. The chosen parts get it; the start page uses the shared function.
3. Specs where they hold something (the tile's seam), the review loop; the maintainer's look in the browser.

## Acceptance

- The chosen parts read as brushed metal in every look; the text and the code stay calm; no visible seam.

## History

- 2026-10-27: created from the maintainer's request.
