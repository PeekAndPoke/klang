# KLANG-00367: The editor's font size, larger and smaller

> **Status (2026-10-27): SCHEDULED.** after the brushed metal
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "we need a way to increase / decrease the font-size in the editor."

## Decide before implementing

- Proposed: a setting in the song page's Settings tool window beside EVT and the hover help, the size on a small LCD
  with a key for smaller and one for larger (or the `KlangValueKnob` of the RPM), in px steps, kept in the browser
  with the other editor settings (`EditorSettings`).
- Proposed: keys for it in the key map (KLANG-00325), with Ctrl+wheel over the code as well. Not Ctrl+plus and
  Ctrl+minus alone: those are the browser's zoom, which users rely on.
- Which editors follow it: proposed the app's code editor everywhere it is used (the song page, the tutorials' code
  examples), as the hover setting does; the docs REPL and the MIDI playground as the maintainer decides.
- The size's range (proposed 10 to 28 px) and the default (today's size).

## The work

1. The setting in `EditorSettings`, a storage key pinned in `StorageKeysSpec`.
2. The editor's font size from the setting, the minimap, the highlights' canvas and the gutter following it.
3. The control in the Settings tool window; the keys in the key map.
4. Specs, mutation checks, the review loop; the maintainer's look in the browser.

## Acceptance

- The code's font size can be made larger and smaller on the song page, and a reload keeps it.
- The highlights, the bookmarks and the gutter stay in line at every size.

## History

- 2026-10-27: created from the maintainer's request.
