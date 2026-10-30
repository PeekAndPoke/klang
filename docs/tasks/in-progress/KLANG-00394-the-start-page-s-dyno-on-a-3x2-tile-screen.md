# KLANG-00394: The start page's dyno on a 3x2 tile screen

> **Status (2026-10-30): IN PROGRESS.** Asked for by the maintainer, 2026-10-30

## Why this exists

The maintainer (2026-10-30): "On the StartPage replace the Dynamo Test popup with a 3x2 tile screen." The dyno's test
and result stood in a panel in the dialog's look under the gauges; the visuals put their displays on the plate's tiles
as screens cut into them.

## Decide before implementing

- The tile: three by two under the gauges, where the Motor's plate in the visuals has its scope and spectrum. One tile
  for both (`PlateComposition.SCREEN` in the start composition; the visuals' `motorBoard` is the start composition), so
  the start page always shows it, dark until the dyno.
- The screen: the visuals' kind (black, cut corners, an inset shadow), as large as the tile less its margin.
- "Make Music Now" and "Enter fullscreen" on the screen's foot.

## The work

1. The start composition gets the 3x2 tile; the visuals' Motor's plate uses it.
2. The start page draws the test and the result on a screen laid on that tile; the panel goes.

## Acceptance

- The dyno's test and result read on the screen at an 800 px high window, nothing cut or overlapping.
- "Make Music Now" leads on, with the jingle.

## History

- 2026-10-30: created.
- 2026-10-30: built; checked in a headless browser (no WebGL there, so the tile itself shows only in a real browser).
