# KLANG-00325: Key map: keyboard input mapped to actions in one place

> **Status (2026-10-21): IDEA.** The maintainer's note; not designed yet. KLANG-00324 lays its first stone.

## Why this exists

The maintainer (2026-10-21): "At some point we also have to wrap the keyboard input and map them to the effects... a
key map." Today each key is wired where it acts: the editor's CodeMirror keymaps (KLANG-00322's
`EditPlacesExtension.KEY`, KLANG-00323's bookmarks), the Ctrl-click and Ctrl-hover handling in
`DslEditorExtension`, and nothing at page or app level. A key map puts all of them in one place.

## The idea

- **Actions** with a name and what they do ("go to the last edit", "set bookmark 3", later "play / stop").
- **Bindings** from a key (by `event.code`, with Ctrl shown as Cmd on a Mac) to an action, per scope: the editor, a
  page, the whole app; a conflict between two bindings in one scope is an error at start-up.
- **One registry** that everything reads: CodeMirror's keymap, page and app shortcuts, the help page (KLANG-00324),
  and later keys the user changes.
- The browser keeps some keys from pages (Chrome: Ctrl+T, Ctrl+W, Ctrl+N and a few more); the registry knows them.

## Depends on / unblocks

After KLANG-00324, whose shortcut list is built as actions and bindings so this grows out of it.

## History

- 2026-10-21: created as an idea (maintainer's note).
