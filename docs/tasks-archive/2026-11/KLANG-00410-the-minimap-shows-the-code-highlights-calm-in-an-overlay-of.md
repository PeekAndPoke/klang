# KLANG-00410: The minimap shows the code highlights, calm, in an overlay of its own

> **Status (2026-11-05): DONE.** Built, reviewed in three rounds and mutation-checked (commit 57857e314); the maintainer's OK (2026-11-05: "the highlights seem to work nicely in the mimimap", then "all good like this").

## Why this exists

The maintainer (2026-11-05): "Can we make the code-highlights also show in the minimap? Maybe same principle, our own
overlay? [...] how do we structure this in code, that the minimap is 'self-contained' including the highlights? [...]
I would not show them as 'hectic'... maybe static with a very short fade in and out".

## How it works today

- **The editor's highlights.** `CodeMirrorHighlightBuffer` (`src/jsMain/kotlin/codemirror/`) turns the voice events
  of a playing song into timed highlights, drawn by PixiJS on one canvas over the editor. It filters by the file shown,
  caps the highlights per event, rate-limits a location, and pauses while a visual covers the window.
- **The minimap.** `@replit/codemirror-minimap` 0.5.2, set up by `klangMinimap()` in `klangscript-ui`'s
  `CodeMirrorComp.kt`. It draws the document on a canvas of its own and exports only the `showMinimap` facet: no hook
  into its drawing, no line-to-pixel query. We create the element it lives in (`MinimapConfig.create`).

## Decided (maintainer, 2026-11-05)

- **D1, the structure: the minimap is self-contained.** A `KlangMinimap` in `klangscript-ui` holds the minimap
  extension and its highlight overlay. Its only input is "this stretch of the document sounds now, for this long", and
  "clear". It knows nothing of playback.
- **D2, one scheduler.** The highlight buffer stays the one place that turns voice events into timed highlights; when
  it shows a highlight in the editor it hands the same range to the minimap. The file filter, the per-event cap, the
  rate limit and the visual's pause hold for both.
- **D3, the look: calm.** No pulse. A sounding stretch is a soft band in the highlight gold over its columns, fading in
  over about 100 ms, steady while the note sounds, fading out over about 250 ms. A line lit again before it has faded
  stays lit, so a busy line glows steadily instead of flickering. Its own draw loop stops when nothing is lit.
- **D4, the strength.** The strength knob of KLANG-00411 dims the minimap's bands too: one setting for how loud the
  highlights are.

## The work

1. `KlangMinimap` in `klangscript-ui`: the extension (`klangMinimap()` moves in), an overlay canvas in the minimap's
   element, `light(fromLine, fromColumn, toLine, toColumn, durationMs)`, `clear()`, `strength`.
2. The line-to-pixel rule of the package (its `canvasStartAndEndIndex`, 0.5.2) repeated in one small pure function,
   guarded by a spec, so a package update that moves the lines turns a test red.
3. `CodeMirrorComp` exposes the minimap; `KlangCodeEditorComp` hands it to the highlight buffer, which lights it with
   every highlight it shows and clears it with its own `cancelAll()`.

## Built with one addition (2026-11-05)

- **Folded code.** The package draws a folded range on one row: a line that starts in a fold joins the row above it
  (its `computeLinesState`). `KlangMinimap.FoldedLines` repeats that from `foldedRanges` (taken by their start, as the
  package's cursor takes them), kept per editor state. A line hidden in a fold gets no band. The line a fold ends on
  gets none either: its rest shows on the fold's row, but at other columns, a small gap accepted.
- **Strength as opacity.** The overlay canvas's opacity is the strength; the bands are drawn opaque on it, so two
  stretches overlapping on one line show as one, no brighter.
- **The rate limit** counts from the minimap's light, so lines scrolled out of the editor are limited too. The cap on
  marks is the editor's alone.

## Acceptance

- A playing song lights its sounding lines in the minimap, calm, at the right rows while the editor scrolls.
- Nothing is drawn while nothing sounds; the minimap's own drag and click still work.
- Specs: the line-to-pixel rule, the fade and the merge of a line lit again.

## History

- 2026-11-05: created from the maintainer's request; design D1 to D4 agreed.
- 2026-11-05: built. Review round 1: one MAJOR (folded code put the bands on the wrong rows), fixed with
  `FoldedLines`; round 2 clean but for minors (folds by start, a scrolled fold case, more unit cases), folded in.
  Mutation-checked.
- 2026-11-05: DONE. Built, reviewed in three rounds and mutation-checked (commit 57857e314); the maintainer's OK (2026-11-05: "the highlights seem to work nicely in the mimimap", then "all good like this").
