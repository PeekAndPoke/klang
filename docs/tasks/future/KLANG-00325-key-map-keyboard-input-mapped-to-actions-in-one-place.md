# KLANG-00325: Key map: keyboard input mapped to actions in one place

> **Status (2026-10-21): IDEA.** The PC keys are decided (interview with the maintainer, 2026-10-21); the Mac table
> and the design draft below wait for the maintainer's look, then DESIGNED.

## Why this exists

The maintainer (2026-10-21): "At some point we also have to wrap the keyboard input and map them to the effects... a
key map." Then, after the bookmarks' German Ctrl+Shift+7 clash with CodeMirror's comment key (KLANG-00323 review round
2): "can we route this and the other shortcuts CodeMirror has through the keymap", with an interview on every
shortcut. Today each key is wired where it acts: CodeMirror's `basicSetup` brings 7 keymaps (about 70 bindings on
Windows and Linux) that we can only add to, KLANG-00322 and KLANG-00323 add their own, `DslEditorExtension` handles
Ctrl-click and Ctrl-hover, `klangui`'s `KlangKeyBindings` answers undo and redo for the mini-notation editor, and the
song page has no keys at all.

## What the key map is

- **Actions** with a name and what they do. The names follow IntelliJ's action names where one exists (the maintainer
  looks them up after the interview).
- **Presets** per platform, detected once: PC (Windows, Linux) and Mac, each with its usual keys (maintainer).
- **Bindings** from a key to an action, per scope: the editor (while it has focus) and the app (the page, components).
- **One table** that everything reads: the editor's CodeMirror keymap, the app's keys, the help page (KLANG-00324),
  the hints, and later keys a user changes.
- **Matching:** letters and symbols by the typed character (Ctrl+Z stays undo on a French layout), digits by key
  position (`event.code`, so Ctrl+Shift+7 is bookmark 7 on every layout, as KLANG-00323 does).
- **Checked at start-up:** two bindings of one key in one scope and preset, or a binding on a key Chrome keeps from
  every page (Ctrl+N, Ctrl+T, Ctrl+W, Ctrl+Tab, each also with Shift), is an error, and a browser test runs the check.

## The PC preset (decided, interview 2026-10-21)

Source of CodeMirror's commands: the installed packages (commands 6.11.1, search 6.6.0, autocomplete 6.20.3, language
6.13.1, lint 6.9.7, view 6.43.14). "Ours" is a command we write; "was" is today's key.

| Action | Key | Command | Was |
|---|---|---|---|
| Standard text keys (arrows, Ctrl+Left/Right, Home/End, Ctrl+Home/End, PageUp/Down, Backspace/Delete, Ctrl+Backspace/Delete, Enter, Ctrl+A; Shift selects) | unchanged | standardKeymap | |
| Move line up / down | Ctrl+Shift+Up / Down | moveLineUp / moveLineDown | Alt+Up / Down |
| Duplicate line | Ctrl+D | copyLineDown | Shift+Alt+Down |
| Cut / copy the line (nothing selected) | Ctrl+X / Ctrl+C | the browser's cut and copy; CodeMirror copies line-wise and pastes such a line above the cursor's line | same |
| Start new line (below, indented) | Shift+Enter | insertBlankLine | Ctrl+Enter |
| Split line (the cursor stays) | Ctrl+Enter | splitLine (IntelliJ indents the tail, CodeMirror does not: decide) | none |
| Indent / unindent | Tab / Shift+Tab | indentMore / indentLess | Ctrl+] / Ctrl+[ (Tab left the editor) |
| Leave the editor by keyboard | Esc, then Tab within 2 s | CodeMirror's tab focus mode (view 6.43.14 `handlers.keydown`) | Tab |
| Line comment | Ctrl+/ | toggleComment | same |
| Block comment | Ctrl+Shift+/ | ours: wraps the selection in `/* */`; with nothing selected writes `/**/` and puts the cursor in the middle | Shift+Alt+A |
| Reformat (indentation for now) | Ctrl+Alt+L | indentSelection; with nothing selected the whole document | Ctrl+Alt+\ |
| Select next occurrence | Alt+J | selectNextOccurrence | Ctrl+D |
| Unselect the last occurrence | Alt+Shift+J | ours | none |
| Select all occurrences | Ctrl+Alt+Shift+J | selectSelectionMatches | Ctrl+Shift+L |
| Expand selection (enclosing syntax; every range of a multi-selection) | Ctrl+I | selectParentSyntax | same |
| Add cursor above / below | Ctrl+Alt+Up / Down | addCursorAbove / addCursorBelow | same |
| Add cursor with the mouse | Alt+Shift+click | `clickAddsSelectionRange` | Ctrl+click |
| Select line | Alt+L | selectLine | same |
| Matching bracket | Ctrl+Shift+M | cursorMatchingBracket | Ctrl+Shift+\ |
| Back to one cursor | Esc | simplifySelection | same |
| Find | Ctrl+F | openSearchPanel | same |
| Replace | Ctrl+R | ours: the search panel with the cursor in the replace field (Chrome's reload suppressed while the editor has focus) | Ctrl+F |
| Next / previous match | F3 / Shift+F3 | findNext / findPrevious | also Ctrl+G / Ctrl+Shift+G |
| Go to line | Ctrl+G | gotoLine | Ctrl+Alt+G |
| Undo / redo | Ctrl+Z / Ctrl+Y and Ctrl+Shift+Z | undo / redo | Ctrl+Shift+Z on Linux only |
| Completion: open / accept / close | Ctrl+Space / Enter and Tab / Esc | startCompletion / acceptCompletion / closeCompletion | Enter only |
| Next / previous problem | F2 / Shift+F2 | nextDiagnostic / previousDiagnostic | F8 (next only) |
| Go to definition | Ctrl+B, and Ctrl+click | ours (KLANG-00321); Ctrl+click no longer adds a cursor | Ctrl+click |
| Quick documentation | Ctrl+Q | ours: the hover's docs popup for the name at the cursor | hover only |
| Back to the last edit | Ctrl+Shift+Backspace | KLANG-00322 | same |
| Set / go to bookmark n | Ctrl+Shift+n / Ctrl+n (by position; go also on the keypad) | KLANG-00323 | same |
| Play / Update (one button) | Ctrl+Shift+Enter | app scope, the song page's button | none |
| Stop | Ctrl+Shift+. | app scope | none |
| Show the hints | hold Alt | the key map (below) | none |

**Prepared, no key today:** fold / unfold, fold all / unfold all (folding stays in the gutter), the problems panel,
navigate back / forward (KLANG-00327), paste from the clipboard history (KLANG-00326).

**Dropped:** copy line up (Shift+Alt+Up), delete line (Ctrl+Shift+K; Ctrl+X cuts the line), the syntax jump
(Alt+Left / Right stay free for the browser and later file switching), undo / redo selection (Ctrl+U / Alt+U).

**Not asked, kept unless the maintainer says otherwise:** Ctrl+M toggles the tab focus mode for good (CodeMirror's
accessibility key; with Tab indenting it is the lasting way to make Tab move the focus).

**German layouts:** Ctrl+/ there is Ctrl+Shift+7, which is bookmark 7; no second comment key for now (maintainer:
"Not now"). Ctrl+Numpad / still comments.

## The Mac preset (draft, for the maintainer to check against IntelliJ's Mac keymap)

The rule: Cmd where the PC has Ctrl, Option where it has Alt, and the Mac's own keys where they differ. CodeMirror's
Mac keys come from its source; the IntelliJ Mac keys are from memory, and the rows marked "check" are the least sure.

| Action | Mac key |
|---|---|
| Standard text keys | macOS's (Option+Left/Right by word, Cmd+Left/Right line start and end, Cmd+Up/Down document start and end, Option+Backspace, Cmd+Backspace, Ctrl+A/E/K and the other Emacs keys: CodeMirror's `mac` bindings) |
| Move line up / down | Cmd+Shift+Up / Down |
| Duplicate line | Cmd+D |
| Cut / copy the line | Cmd+X / Cmd+C |
| Start new line / split line | Shift+Enter / Cmd+Enter |
| Indent / unindent | Tab / Shift+Tab |
| Line / block comment | Cmd+/ / Cmd+Option+/ |
| Reformat | Cmd+Option+L |
| Select next occurrence / unselect / all | Ctrl+G / Ctrl+Shift+G / Ctrl+Cmd+G |
| Expand selection | Option+Up (IntelliJ; check against macOS's paragraph move) or Cmd+I |
| Add cursor above / below | Cmd+Option+Up / Down (check) |
| Add cursor with the mouse | Option+Shift+click |
| Select line | Ctrl+L (CodeMirror's Mac key) |
| Matching bracket | Ctrl+M (check) |
| Find / replace | Cmd+F / Cmd+R |
| Next / previous match | Cmd+G / Cmd+Shift+G |
| Go to line | Cmd+L (check: Chrome's address bar key) |
| Undo / redo | Cmd+Z / Cmd+Shift+Z |
| Completion | Ctrl+Space (CodeMirror also Option+` and Option+I) |
| Next / previous problem | F2 / Shift+F2 |
| Go to definition | Cmd+B, Cmd+click |
| Quick documentation | F1 (check; Ctrl+J in older keymaps) |
| Back to the last edit | Cmd+Shift+Backspace |
| Bookmarks | Ctrl+Shift+n / Ctrl+n (Cmd+digit is Chrome's tab switch) |
| Play / Update, Stop | Cmd+Shift+Enter, Cmd+Shift+. |
| Show the hints | hold Option |

## Components announce their keys (the maintainer's idea, 2026-10-21; "needs some more thought")

The maintainer's sketch: a component announces its key while it is mounted, for example the Play button:

```kotlin
ui.circular.white.button {
    OnKey { /* action */ }
    onClick { /* action */ }
    +"Play"
}
```

`OnKey` registers with the key map on mount and unregisters on unmount, and renders a small, absolutely positioned
hint with its key while the hints are shown. The key map keeps a list per key: the last registration wins, and when it
unregisters the previous one takes effect again (useful for overlays: a modal's Esc over the page's). The editor
announces its bindings the same way, and holding Alt shows a small overlay with all the editor's shortcuts.

Refinements the coordinator proposed (to settle in the draft):
- A component announces an **action**, not a key (`OnKey(Play) { ... }`); the preset supplies the key, so the hint
  shows the platform's key and later the user's own.
- Unregistering removes **that registration** (a handle), not the key, so the stack stays right when registrations
  come and go out of order.
- The editor and the app share the table. CodeMirror handles its keys while the editor has focus and lets the others
  through, so Play works while typing; the start-up check covers the presets, the stack is for deliberate overrides.
- The hints: shown while Alt alone is held, hidden on release or on any other key (so Alt+J and Alt+Shift+click do not
  flash them). Chrome on Windows moves the focus to its menu on a lone Alt; whether a page can stop that is a hand
  test.

## Decide before implementing

1. Where the table lives: `klangui` (both the editor module and the app depend on it; `KlangKeyBindings` there is
   the first stone and goes into the table).
2. The editor's setup: `basicSetup`'s extensions copied without its keymaps (CodeMirror's docs suggest exactly this),
   the keymap built from the table.
3. Split line: CodeMirror's `splitLine` as it is, or ours indenting the tail as IntelliJ does.
4. The Mac table above.
5. The `OnKey` API and the hint look (first draft, reviewed by the maintainer, step 3).

## The work

1. The table: actions, the two presets, matching, the registry with its stack, the start-up check; a browser test of
   the check, the matching (German and AZERTY rows) and the stack.
2. The editor on the table: the copied setup, the ours-commands above, KLANG-00322's and KLANG-00323's keys from the
   table, Alt+Shift+click; a test that every CodeMirror command the table names exists in the installed packages.
3. `OnKey` and the hints, the Play / Update button first. **Stop after the first draft so the maintainer can look at
   the code** (maintainer, 2026-10-21), then Stop and the editor's overlay.
4. KLANG-00324's page reads the table.

## Acceptance

Tests green and mutation-checked (light tier); the maintainer's hand test of the PC preset in Chrome, key by key from
the table; the Mac preset checked on a Mac when one is at hand.

## Out of scope

User-changed keys (the table is shaped for them), the keys of other pages (the mini-notation editor keeps its undo and
redo, read from the table), the clipboard history (KLANG-00326), navigate back and forward (KLANG-00327).

## Depends on / unblocks

This ticket is the epic "keyboard shortcuts", klang's first (maintainer, 2026-10-21): its tickets carry a
`Part of` line naming it.

After KLANG-00323 (bookmarks, done and committed). Unblocks KLANG-00324 (the shortcuts page reads the table).

## History

- 2026-10-21: created as an idea (maintainer's note).
- 2026-10-21: the interview: the PC preset decided key by key, presets per platform, the component idea; the Mac table
  and the design draft written for the maintainer's look. The order with KLANG-00324 turned round: the key map first.
- 2026-10-21: the first epic (maintainer: "adopt this gradually"); KLANG-00324, KLANG-00326 and KLANG-00327 are part
  of it.
