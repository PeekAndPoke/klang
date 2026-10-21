# KLANG-00326: Editor: paste from the clipboard history (multi-clipboard)

> **Status (2026-10-21): IDEA.** The maintainer's note during the key map interview (KLANG-00325).

## Why this exists

The maintainer (2026-10-21): "Future Idea can we also have a multi-clipboard like IntelliJ, when Ctrl+Shift+V opens a
small dialog to select what to paste from the copy/cut history (100 entries)".

## The idea

- Every copy and cut in the editor is remembered, newest first, up to 100 entries (Ctrl+C and Ctrl+X with nothing
  selected copy or cut the whole line, CodeMirror's line-wise copy, so those count too).
- Ctrl+Shift+V opens a small dialog listing the entries; picking one pastes it at the cursor.
- The key goes into the key map (KLANG-00325) as a prepared action. Chrome's own Ctrl+Shift+V is "paste as plain
  text"; the editor suppresses it while it has focus.

## Decide before implementing

1. Only what was copied inside Klang, or also the system clipboard (reading it needs the browser's permission)?
2. Kept for the session only, or in local storage (the `storage/` package of KLANG-00323)?

## History

- 2026-10-21: created as an idea (the maintainer's note in the KLANG-00325 interview).
