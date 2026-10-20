# KLANG-00324: Learning section: the editor's keyboard shortcuts

> **Status (2026-10-21): DESIGNED.** After KLANG-00323 (bookmarks), so the page lists them from the start.

## Why this exists

The maintainer's request (2026-10-21): a help page in the learning section that explains the editor's keyboard
shortcuts. The editor gained several this month (go to definition, back to the last edit, bookmarks), and
CodeMirror's own (comment a line, move a line, multiple cursors, search) are useful and nowhere explained.

## Depends on / unblocks

After KLANG-00321 (done), KLANG-00322 and KLANG-00323, whose keys it lists.

## Decide before implementing

1. **Where:** a third card on the manuals page (`src/jsMain/kotlin/pages/docs/DocsPage.kt`, next to Lexikon and
   Tutorials), opening a page of its own (`Nav.manualsEditorShortcuts`, `$manualsBase/editor-shortcuts`).
2. **One source for Klang's own keys:** the bindings Klang adds (go to definition and docs on Ctrl-click, the
   Ctrl underline, the context menu, back to the last edit, bookmarks, completion on Ctrl+Space) are listed in one
   Kotlin list next to the editor (`klangscript-ui`), and the keymaps use the same constants (`EditPlacesExtension.KEY`
   is the first), so the page cannot drift from the code. The list is shaped as actions and their bindings, so the
   key map of KLANG-00325 (keyboard input mapped to actions in one place) grows out of it.
3. **CodeMirror's keys as installed:** the page lists the useful ones of CodeMirror's standard keymaps (undo and
   redo, comment, move and copy a line, delete a line, indent, select the next match, multiple cursors, search and
   replace, go to line, fold, the matching bracket), each checked against the keymap source of the installed
   `@codemirror/*` packages, not from memory. A browser test checks that every listed CodeMirror binding exists in
   the installed keymaps, so an upgrade that moves one fails the build (a check for the future).
4. **Mac:** keys are shown as "Ctrl" with "Cmd" on a Mac, read from the platform once.
5. **Voice:** in-app text, as the other manual pages (rules register: tutorials and in-app text have their own
   style); short, one line per key, grouped (moving around, editing, finding, Klang's own).

## The work

1. The shortcut list (point 2) and the keymaps on its constants.
2. The page and its card, the route.
3. The browser test of point 3 (`klangscript-ui` `jsTest`, added by KLANG-00322).
4. A hand check by the maintainer: every key on the page does what it says, in Chrome.

## Acceptance

The test green and mutation-checked (light tier); the page reachable from the manuals page; the maintainer's hand
check.

## Out of scope

Configurable keys, a shortcut overlay inside the editor, and a link from the editor to the page (a later idea).

## History

- 2026-10-21: created; designed the same day (maintainer's request).
- 2026-10-21: the list shaped for KLANG-00325 (the key map, the maintainer's note).
