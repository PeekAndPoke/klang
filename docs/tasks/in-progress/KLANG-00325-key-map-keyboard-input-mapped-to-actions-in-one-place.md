# KLANG-00325: Key map: keyboard input mapped to actions in one place

> **Status (2026-10-25): IN PROGRESS.** Step 1 (the core, in the module `workshop`), step 2's first draft (`OnKey`,
> the hints, on the song page's Play / Update / Stop) and the editor's keys overlay are built and committed for
> v0.7.5; the overlay step's review stopped at the safety valve after two rounds (both MAJORs fixed), round 3 runs
> after the release. Next: the maintainer's Mac test, then step 3 (the editor on the table).
>
> **Part of:** KLANG-00332 (UX comfort)

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

## Plan 2026-10-25 (with the maintainer)

The maintainer: "a reusable mechanism for other projects that can move into Kraft. Also the keymap must be
user-configurable. And the hints that show when a user holds Ctrl or a similar configurable key or key-combination."
The inventory that day: no central key manager in Klang, Kraft or Ultra; every key wired where it acts (the table in
"Why this exists"); `KlangKeyBindings` is two helpers.

**Where: the module `workshop`** (maintainer, 2026-10-25: "separate package, what would you call it workshop"). One
Gradle module for what Klang builds for Kraft before it moves there; the key manager is its first package,
`io.peekandpoke.klang.workshop.keys`. It depends on Kraft and Ultra only, never on a Klang module, so the build keeps
Klang out of it and the move is a copy. Klang's modules depend on it.

**The core (workshop, bound for Kraft):**
- **Actions:** an id, a title, a group. A component asks for an action, never a raw key; the hint shows the key that is
  set.
- **Key chords:** written as text (`"Mod-Shift-Enter"`, `Mod` is Ctrl on a PC, Cmd on a Mac). Letters and symbols match
  the typed character (Ctrl+Z stays undo on AZERTY), digits the key's position (`event.code`).
- **Keymap:** a preset per platform (PC, Mac), the user's keys on top; the result is the one table everything reads.
- **Checks:** two actions on one key in one scope, or a key Chrome never hands to a page (Ctrl+N, Ctrl+T, Ctrl+W,
  Ctrl+Tab, each also with Shift), is an error: at start-up for the presets, and when the user changes a key, shown to
  them as the clash.
- **The key manager,** a Kraft service like the modals manager: one listener on the document, a stack of registrations
  (the newest wins, the one before takes over when it goes: a modal's Esc over the page's), each with its own handle so
  it unregisters cleanly out of order.
- **The user's keys:** a small JSON of changes against the preset, so a preset update does not wipe them. The core
  names a storage interface; the app gives it one.
- **Kraft side:** `OnKey(Play) { ... }` registers while its component is mounted and draws a small hint with its key
  next to the element while the hints show.
- **Hints:** shown while a key or combination is held a moment (300 ms, maintainer 2026-10-25) with no other key; gone on release or on
  any other key, so Ctrl+Z does not flash them. The trigger is set **in code, not by the user, for now** (maintainer,
  2026-10-25); Klang sets Ctrl (Windows moves the focus to Chrome's menu on a lone Alt).

**Klang's part:**
- The actions and the two presets (the tables above).
- **Storage:** the app's storage as it is (maintainer, 2026-10-25: "use the storage system we already have and store
  in localStorage. Later when we have a server it will live there"): a `KeymapStorage` beside `OutputVolumeStorage`,
  its key in `StorageKeys`.
- The editor on the table: CodeMirror handles its keys while it has focus and lets the rest through, so Play works
  while typing.
- The settings page, with KLANG-00324: every action with its key, record a new key, the clashes shown, back to the
  preset.

**Open, the maintainer not sure yet (2026-10-25); the coordinator's defaults, to look at again in step 3:**
- The editor's setup: `basicSetup`'s extensions copied without its keymaps (CodeMirror's docs suggest it), so no hidden
  CodeMirror key can clash again (the German Ctrl+Shift+7 of KLANG-00323).
- Split line: CodeMirror's `splitLine` as it is.
- The Mac preset: the draft above as it is; the rows marked "check" are checked on a Mac when one is at hand.

## The work

1. **The core** in `workshop`: actions, chords and their matching, the keymap with presets and user keys, the checks,
   the key manager and its stack, the storage interface; browser tests of the matching (German and AZERTY rows), the
   stack, the checks and the user keys over a preset.
2. **`OnKey` and the hints,** on the Play / Update button first. **Stop after the first draft so the maintainer can
   look at the code** (maintainer, 2026-10-21); then Stop.
3. **The editor on the table:** the copied setup, the ours-commands above, KLANG-00322's and KLANG-00323's keys and the
   mini-notation editor's undo and redo from the table (`KlangKeyBindings` goes), Alt+Shift+click; a test that every
   CodeMirror command the table names exists in the installed packages; the editor's overlay of its keys.
4. **The settings page** with KLANG-00324, and `KeymapStorage`.
5. **The move into Kraft** when it has proven itself; ULTRA-00239 (Esc on modals) built on it there, Klang's
   `YesNoModal` Esc onto it. Kraft's own modals take the layers with the move, not as instructions for each app
   (maintainer's question, 2026-10-25): `FadingModal` opens a key layer in its `init` (`openKeyLayer()`), so every
   Kraft modal holds the page's keys back by default, as its dimmer holds back the mouse; Esc dismisses and OkCancel
   answers with Ctrl+Enter through the same actions. To decide then: whether popups and toasts open layers too (a
   popup opened from a modal today belongs to the root layer, see "Kraft parity").

**Open after step 1's review round 1 (2026-10-25), for later steps:**
- **Layers:** page keys still act under a modal (Ctrl+Shift+Enter plays behind the bookmark question). The stack is per
  action; a modal needs a layer that blocks the keys below it. Decide with step 5 (ULTRA-00239).
- **Kraft parity for the move:** of Kraft's managers only the modals manager is an interface with an implementation;
  the popups and toasts managers are classes, as `KeysManager` is (it adds its listeners in its constructor);
  `Component.keys` is a generic name on every component. Settle when it moves. Also for the move: the stage's
  `visibleBox` treats `overflow-x: clip` with a visible `overflow-y` as clipping both axes, and cuts an absolute or
  fixed anchor by ancestors that are not its containing block; no anchor in Klang is either (2026-10-25). Layers
  follow the component tree, not the stack on screen: a component drawn on an app-wide stage (a popup opened from
  inside a modal) belongs to the root layer and waits while the modal is open. A modal's Esc follows the rule that a
  plain key belongs to a text field, so a future modal with an input would not close on Esc from inside it (the old
  hand-made listener did); no modal has one today.
- **Matching by position:** a symbol with Shift and a digit match by the key's position (one rule each, so one key
  never triggers two chords), so on a French keyboard Stop (Ctrl+Shift+.) is the key at the US period, which shows `:`;
  the hint still says `.`. A layout-aware label waits for the settings page.

## Places for keys and hints (survey 2026-10-25)

The maintainer (2026-10-25): the rollout waits for Klang-Research's new UI layout (the UI kitchen sink, KLANG-00334);
this list is an informal input for its designs. In order of benefit:

1. **The code tools** (the ADSR, distort, scale ... modals and popovers, `ToolButtonBar`): Esc cancels, Ctrl+Enter
   updates, a key for Reset; hints on their buttons. They open a key layer, as `YesNoModal` does.
2. **The playable examples** in the docs and tutorials (`PlayableCodeExample`): Play / Update, Stop and Reset with the
   song page's keys. A page holds several, so the one whose editor has the focus (else the one last played) answers:
   a small focus scope.
3. **`YesNoModal`**: done (Ctrl+Enter yes, Esc closes, "Esc Close" in the header's corner).
4. **The song page's other controls:** Reset to original (behind the yes or no question), copy the code
   (Ctrl+Shift+C), rpm up and down, the volume knob (louder, quieter, mute) with a hint on the knob.
5. **Tutorials** (`TutorialPage`, `TourPage`): next and previous lesson, Mark as completed, All tutorials.
6. **Search fields** (samples library, lexikon, docs): "/" or Ctrl+K focuses the page's search, Esc clears it.
7. **Navigation** (the sidebar): jump to Songs, Docs, Tutorials, Samples, Lexikon with a two-key chord; a page's own
   keys overview could show app-wide while Ctrl is held.
8. **The REPL** (`KlangScriptReplComp`): Run with Ctrl+Enter, Clear.
9. **The MIDI playground:** mostly settings; only with a play or record flow.

What the designs can plan for: a button with a key gets its hint by `OnKey` inside it (the stage places the hint, so
no room is needed beside the button); a whole box (a modal, a panel) can carry a key in a corner (`InsideEnd`); an
area can show an overview of its keys in a corner while Ctrl is held (as the editor's keys and bookmarks do).

## Acceptance

Tests green and mutation-checked (light tier); the maintainer's hand test of the PC preset in Chrome, key by key from
the table; the Mac preset checked on a Mac when one is at hand.

## Out of scope

A user-set hints trigger (set in code for now), keys stored on a server (localStorage until there is one), the clipboard
history (KLANG-00326), navigate back and forward (KLANG-00327).

## Depends on / unblocks

This ticket is the epic "keyboard shortcuts", klang's first (maintainer, 2026-10-21): its tickets carry a
`Part of` line naming it.

After KLANG-00323 (bookmarks, done and committed). Unblocks KLANG-00324 (the shortcuts page reads the table).

**Moves to Kraft once stable** (maintainer, 2026-10-25, agreed with Ultra): the key map is built here first, and the
mechanism moves to Kraft when it has proven itself. Kraft's Esc for modals (ULTRA-00239) will be built on top of it.
Klang keeps its own dialogs (`YesNoModal`) for clear control over their styles, also after Ultra 0.115.0 gave Kraft's
OkCancel real buttons and focus; their Esc stays ours until the key map carries it.

## History

- 2026-10-21: created as an idea (maintainer's note).
- 2026-10-21: the interview: the PC preset decided key by key, presets per platform, the component idea; the Mac table
  and the design draft written for the maintainer's look. The order with KLANG-00324 turned round: the key map first.
- 2026-10-21: the first epic (maintainer: "adopt this gradually"); KLANG-00324, KLANG-00326 and KLANG-00327 are part
  of it.
- 2026-10-25: next after the Ultra 0.115.0 bump (maintainer); built here, moves to Kraft once stable, Kraft's Esc
  on top; Klang keeps its own dialogs.
- 2026-10-25: part of the epic UX comfort (KLANG-00332), as an epic of its own inside it.
- 2026-10-25: planned with the maintainer: a reusable key manager in the new module `workshop`, bound for Kraft;
  keys the user can change, kept in localStorage; the hints trigger set in code; DESIGNED.
- 2026-10-25: DESIGNED.
- 2026-10-25: step 1 built in `workshop` with the pilot on the song page's Play / Update / Stop; review round 1: 2
  MAJOR (AltGr, a symbol on two chords) and 9 MINOR fixed, Klang's own hint with the keyboard icon.
- 2026-10-25: review round 2 clean (6 MINOR fixed, two waiting for the maintainer); OnKey split into the
  `OnKeyController` (the machinery, Kraft `FormController` style) and a slim renderer (maintainer's request); the hint
  stage (maintainer: "I would also go for the hint stage"): hints drawn on one fixed layer, placed by
  `KeyHintLayout` (Below, Above, Over, Start, End; pushed apart, flipped, kept in the window). The look settled with
  the maintainer by eye: each key in its own key cap, compact signs drawn as vector icons (`⇧` `↵`), no keyboard icon,
  12 px in 20 px caps on an opaque frame ("Ok this is great now"); no hint on a disabled button (`showHint`).
- 2026-10-25: the maintainer's decisions: Mac Option rule (a): on a Mac a chord with Option reads the key's position
  whatever Option types there (a sign `∆`, an ASCII sign like the German `@`, a dead key), so Cmd+Option+L works on
  every Mac layout, while Alt on a PC (plain letters) keeps matching by its letter; decided by platform after the
  overlay step's reviews (round 1 MINOR, round 2 MAJOR: a non-ASCII test missed `@` and dead keys). The Mac skips the
  AltGr reading (macOS has no AltGr), which also settles the Firefox doubt below; `workshop/` is Apache-2.0 like Ultra ("Same license as ultra,
  but we can also relicense once it moves"), with `workshop/CLAUDE.md` and a line in the root licensing row; the
  missing `--klang-bg-popup` added to `klang.css`; hints of disabled buttons hidden with `showHint`; the hint delay
  300 ms. Not verified, for a Mac at hand: whether Firefox on macOS reports Option as AltGraph; matching no longer
  depends on it (the Mac skips the AltGr reading), at most the hints hide when Option joins a held Cmd.
- 2026-10-25: the editor keys overlay, ahead of step 3 (maintainer: "show an overlay with the key binding bag of the
  editor"): `KeyActionGroup` and `KeyBindings.overview` (one row for bookmarks 0 to 9), `KeyBindingsOverlay` in
  `workshop`, `KlangEditorKeyBindings` display-only in scope `editor` (CodeMirror still acts on these keys), the panel
  in the editor's top-right corner, 18 px left of the measured minimap ("great"). Review of the step: 1 MAJOR (the
  clipping walk took the body for a clipping box, so hints vanished on a scrolled page), fixed with the MINORs; the
  next round at level 3.
- 2026-10-25: IN PROGRESS.
- 2026-10-25: v0.7.5 released with step 1, the pilot and the overlay (maintainer). The overlay step's round 3 clean;
  its MINORs fixed with the next step: `canFire` by platform, Ctrl+Alt+Space under AltGr, a page fading out after a
  navigation answers no keys (`inert` on the router's previous layer), the Kraft-parity note.
- 2026-10-25: layers (maintainer): `openLayer()` returns a `LayerHandle` with `close()`, the root layer is always there
  and cannot be closed, the top layer wins, closing in any order is deterministic; a modal opens one with
  `openKeyLayer()`. `YesNoModal` on it: Ctrl+Enter answers yes (hint on the yes button), Esc closes ("Esc Close" in
  the header's corner while Ctrl is held, the maintainer's choice), the page's keys wait meanwhile. Placements
  `InsideStart` and `InsideEnd` (maintainer: build them too).
- 2026-10-25: next, the maintainer's idea: while Ctrl+Shift is held, a panel of the song's bookmarks (digit, file,
  line, the line's code), left of the editor's keys overview (both show; the Ctrl hints stay while Shift joins). It
  needs a new pattern, `OnHoldKey`: a held modifier combination starts and ends a hold, as the hints' trigger does
  today; the hints become one hold among others.
- 2026-10-25: the layers step's review round 1 (level 2) clean; its MINORs fixed with the maintainer's OK: the clash
  KDocs name the top layer, a component's layer goes on top only when it mounts (one that never mounts never covers
  the page), workshop tests for a component's layer and the overview under a layer or inside an inert part, room in
  the modal's header for "Esc Close".
- 2026-10-25: the bookmarks panel shows on the hints' key, not on Ctrl+Shift (maintainer: "Ctrl+1 would jump there so
  it would not make sense to show them on Ctrl+Shift"): in the editor's corner, left of the keys overview, each
  bookmark with its jump key, its line and the code on it. `OnHoldKey` (a hold of any modifier combination) is not
  needed for it and stays an idea for later.
- 2026-10-25: the bookmarks panel hand-tested by the maintainer ("Bookmarks preview works fabulous"); its review round 1
  (level 1) clean, its MINORs to the maintainer. The rollout to more places waits for Klang-Research's new UI layout
  (maintainer); the survey above went to Klang-Research as an informal hint (its KLANG-00334, item 31).
- 2026-10-25: keys in a row, part (a) (the maintainer through Klang-Research's KLANG-00334): a modifier tapped on its
  own twice (`"Shift Shift"`) triggers its action, in the top layer, also while typing; a click, a wheel turn, the
  window's blur or another key between ends the row. Bindings show as groups of key caps, one per step. Klang's
  `klang.search-everywhere` (Ctrl+K, Shift Shift) is defined for the new UI's search window; nothing handles it yet.
  Chord sequences (`"Mod-K Mod-S"`) stay unreadable until part (b). Review: round 1 (level 1) one MAJOR (Shift+click
  twice counted as two taps), fixed; round 2 (level 2) clean; 14 mutants killed. The MINORs wait for the maintainer.
