# KLANG-00366: Brushed metal on chosen parts: the start page's strokes as a flat finish

> **Status (2026-10-27): IN PROGRESS.** a first try, for the maintainer's look in the browser
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
- Later (the maintainer, 2026-10-27: "We could make the strength of the brush a theme property or maybe even the
  decoration + strength... but keep this in mind for later"): the strength is a look role already
  (`KlangRole.Brushed`); a look choosing its decoration as well (brushed, grain, plain, and what may come) waits.

## The work

1. klangui: the brushed tile (painted once, seamless) and the finish as a style; the strength as a look role.
2. The chosen parts get it; the start page uses the shared function.
3. Specs where they hold something (the tile's seam), the review loop; the maintainer's look in the browser.

## Acceptance

- The chosen parts read as brushed metal in every look; the text and the code stay calm; no visible seam.

## History

- 2026-10-27: created from the maintainer's request.
- 2026-10-27: IN PROGRESS, moved before the tabs at the maintainer's word ("Try something please"). A first try:
  `KlangBrushedMetal` (klangui) paints the start page's strokes into a seamless 1024 x 256 tile at the app's start;
  `--kl-brushed` (`KlangRole.Brushed`, 0.5) its strength; on the strip, the Motor's bay and the tool windows' headers.
  `overlay` as the grain uses it showed next to nothing on the dark grounds, so the tile holds white and black strokes
  on clear. The strokes a copy of the start page's for now.
- 2026-10-27: the maintainer on the first try: "too much", "it does not tile", and "strange, when it is only on a
  few elements". Second try: on every ground around the work (the workbench, the column, the strip, the tool
  windows), never the code; 0.18; the tile 1024 x 1024 so the narrow strip shows no repeat, painted 64 rows a turn.
- 2026-10-27: the maintainer: "It might work like this [...] I have to get used to it". Kept at 0.18 for now.
- 2026-10-27: on the songs page's large empty ground "a bit too much... needs to be more subtle": 0.1.
- 2026-10-27: "Or maybe the inner content on this page should not be brushed only the sides": the frame (the column,
  the strip, the tool windows) brushed, the middle plain.
- 2026-10-27: the maintainer on the frame-only finish at 0.1: "yes much better like this". Committed with the
  bottom panel's tabs (KLANG-00365), reviewed with them. Open: the start page taking its strokes from
  `KlangBrushedMetal`, and a look choosing its decoration (later).
- 2026-10-27: IN PROGRESS.
