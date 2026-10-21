# KLANG-00324: Learning section: the editor's keyboard shortcuts

> **Status (2026-10-21): DESIGNED.** After KLANG-00325 (the key map), whose table the page reads.
>
> **Part of:** KLANG-00325 (keyboard shortcuts)

## Why this exists

The maintainer's request (2026-10-21): a help page in the learning section that explains the editor's keyboard
shortcuts. The editor gained several this month (go to definition, back to the last edit, bookmarks), and
CodeMirror's own (comment a line, move a line, multiple cursors, search) are useful and nowhere explained.

## Depends on / unblocks

After KLANG-00325 (the key map): the page lists its table. KLANG-00321, KLANG-00322 and KLANG-00323 add keys to it.

## Decide before implementing

1. **Where:** a third card on the manuals page (`src/jsMain/kotlin/pages/docs/DocsPage.kt`, next to Lexikon and
   Tutorials), opening a page of its own (`Nav.manualsEditorShortcuts`, `$manualsBase/editor-shortcuts`).
2. **One source:** the page lists the key map's table (KLANG-00325), every action with its key in the viewer's
   preset, so the page cannot drift from the code. The keys the browser handles (cut and copy a line) and the mouse
   (Ctrl+click, Alt+Shift+click) are in the table too.
3. **CodeMirror's commands as installed:** KLANG-00325's test checks that every CodeMirror command the table names
   exists in the installed packages; this page adds no test of its own for that.
4. **Mac:** the page shows the viewer's preset (PC or Mac, detected), as the key map does.
5. **Voice:** in-app text, as the other manual pages (rules register: tutorials and in-app text have their own
   style); short, one line per key, grouped (moving around, editing, finding, Klang's own).

## The work

1. The page and its card, the route, reading the key map's table.
2. A hand check by the maintainer: every key on the page does what it says, in Chrome.

## Acceptance

The page reachable from the manuals page and listing the table; the maintainer's hand check.

## Out of scope

Configurable keys and a link from the editor to the page (later ideas). The overlay of the editor's shortcuts while
Alt is held belongs to KLANG-00325.

## History

- 2026-10-21: created; designed the same day (maintainer's request).
- 2026-10-21: the list shaped for KLANG-00325 (the key map, the maintainer's note).
- 2026-10-21: after the key map interview the order turned round: KLANG-00325 first, and this page reads its table.
