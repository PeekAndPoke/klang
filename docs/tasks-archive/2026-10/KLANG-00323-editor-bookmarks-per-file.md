# KLANG-00323: Editor: bookmarks per file

> **Status (2026-10-21): DONE.** Hand-tested by the maintainer 2026-10-21: the bookmarks work and look right (the digit box, the yes/no modal).

## Why this exists

The maintainer's request (2026-10-21): bookmarks as in IntelliJ, per file first. Ctrl+Shift+Number sets a bookmark,
Ctrl+Number goes there, overriding an existing bookmark asks first, and the gutter shows them: the line number
highlighted, and the bookmark's number beside it if there is space.

## Depends on / unblocks

Nothing.

## Decide before implementing

Decided by the maintainer, 2026-10-21:

1. **Kept in the browser, per song, surviving a reload,** behind a storage layer of its own: "maybe we should
   [have] a 'Storage' for that. Dedicated source dir, we will have more of these i guess as an abstraction for f.e.
   loading a song." So: a new `src/jsMain/kotlin/storage/` holds everything the app keeps in the browser, one place
   that names every key. The song's code, title and rpm (today three `persistInLocalStorage` streams built in
   `CodeSongPage`, keyed `song-$v-$songId-...`) move there, the bookmarks join them under the same scheme
   (`song-$v-$songId-bookmarks`), and `MidiInstrumentStorage` and `TutorialStorage` move in as they are. The editor
   (`klangscript-ui`) knows no storage: it takes the bookmarks it starts with and reports each change; the page
   wires the two.
2. **The keys: suppress the browser's default where possible.** Ctrl+Shift+Number sets, Ctrl+Number goes, and both
   always call `preventDefault`. The digit is read from the key's position (`event.code`: `Digit0` to `Digit9`, and
   `Numpad0` to `Numpad9`), so Shift+1 works on a German layout, where it types `!`.
3. **Chrome only, for now.** If Chrome keeps one of the keys from the page, the hand test shows it, and the key is
   decided again then.
4. **Setting on a line that holds the same number** removes it (IntelliJ's toggle). Setting a number that sits on
   another line asks first, in a small confirm. A line holds one bookmark; setting another number there replaces it
   without asking. (Built as Klang's own yes or no modal, `YesNoModal`, after the maintainer's hand test: "Move
   bookmark 3?", "Bookmark 3 is on line 42. Move it to line 50?", Move and Keep.)
5. **Stored as line numbers;** a stored line past the end of the song is dropped on load.

Decided by the maintainer during the reviews, 2026-10-21:

6. **Kept in the browser only, never stored with the code:** later they span several files.
7. **A deleted line takes its bookmark with it;** an undo does not bring it back.
8. **`klangscript-ui` compiles to ES2015,** as the app does.
9. **The keys are read from their position in one small handler** until the key map (KLANG-00325) takes them over;
   setting from the keypad is not supported (Shift turns its digits into the arrow and page keys).

## The work

1. A CodeMirror `StateField` with a `RangeSet` of bookmark points (0 to 9), mapped through every change, so a
   bookmark moves with its line; a deleted line's bookmark lands on the line that takes its place.
2. Gutter: a `lineNumberMarkers` marker that highlights the line number and shows the bookmark's digit when the
   gutter is wide enough.
3. Keymap: set, toggle, go (scroll into view, cursor to the line's start), and the confirm for a move.
4. The storage layer of point 1, and the page wiring.
5. Tests (light tier, mutation-checked): the bookmark logic (set, toggle, move, map through changes) as a plain
   function; the gutter and the keys by hand.

## Acceptance

The specs green and mutation-checked; a hand test of setting, moving with the prompt, toggling, jumping, and a
bookmark following its line while text is typed above it.

## Out of scope

Bookmarks across songs (IntelliJ's global list), named bookmarks, and a bookmark panel.

## Done 2026-10-21

Built as designed, with one change of shape ("The work" 1): the state field holds the plain rule's value (offsets,
mapped through every change with CodeMirror's `mapPos`, as a `RangeSet` maps its points), and the `RangeSet` of gutter
markers is derived from it, so the rule stays plain Kotlin and is tested on its own. Points the design left open are
decided below ("Decided while building") and wait for the maintainer's nod.

- **The rule, `Bookmarks`** (`klangscript-ui/.../codemirror/Bookmarks.kt`): an immutable data class, the bookmarks by
  digit, each on its line's anchor (the first non-whitespace character, the line's end on a blank line).
  `set(digit, at, doc)` toggles off, asks (`SetResult.AskToMove`) or sets, replacing the line's other bookmark; `put`
  is the set without a question (the move after a yes); `mapped(mapPos, deletions, oldDoc, newDoc)` maps a change and
  puts every bookmark on the anchor of the line it reached; `moved(moves, oldDoc, newDoc)` follows a move of lines,
  with `lineBlocks` and `lineMoves` working out the moves as CodeMirror's move line takes them; `load(lines, doc)` drops
  a line past the end, a digit outside 0 to 9, and a second bookmark on a digit or a line; `lines(doc)` gives the
  host's form, `Bookmarks.Line(digit, line)`. It reads the document through a four-member interface, `Bookmarks.Doc`.
  No CodeMirror in it.
- **The wiring, `BookmarksExtension`** (same folder): the field (started from the host's bookmarks with
  `StateField.init`), one `keydown` handler at `Prec.highest`, the gutter markers
  (`lineNumberMarkers.compute([field, "doc"])`), the history's side (`invertedEffects`, `isolateHistory` through a
  `transactionExtender`), and a listener that reports each change of the bookmarks' lines.
  - The keys are read from the key's position (`event.code`) in one small function, `digitOf`: Ctrl+Shift with
    `Digit0` to `Digit9` sets, Ctrl with those or `Numpad0` to `Numpad9` goes; no Alt (AltGr), no Meta. A handled key
    returns true, so CodeMirror keeps it from the browser and from the editor's own key bindings, also for a go to a
    digit that is not set. Ctrl on a Mac too (Cmd+digit is the tab switch there). Setting from the keypad does not
    work, since Shift turns its digits into the arrow and page keys. The key map (KLANG-00325) takes these keys over.
  - `Prec.highest` puts the handler before the keymaps: a German Ctrl+Shift+7 types `/`, and `Ctrl-/` is the comment
    key, which CodeMirror's keymap tries before any key position.
  - A move line (user event `move.line`) moves the bookmarks with their lines (`lineMoves`). Its direction comes from
    the change: moving down, CodeMirror inserts the jumped line where the block starts. Its undo and redo carry the
    moves back as an effect, and it is an undo step of its own (`isolateHistory` "full"), so a later edit never joins
    it.
  - The undo and redo of a reset carry `keepLines` again. Nothing else goes into the history: a deleted line takes its
    bookmark with it, and an undo does not bring it back (maintainer, 2026-10-21).
  - Asking for a move first takes the keyboard from the editor (`contentDOM.blur()`), so Enter does not type into the
    song and a second key does not ask again; the answer gives it back, unless the editor is gone by then.
- **The keys**: no constants for them in the editor; the shortcuts page (KLANG-00324) and the key map (KLANG-00325)
  add what they need (review round 3).
- **The gutter** (the maintainer's hand tests, 2026-10-21): every line number keeps room on its right for a bookmark's
  box, so the code does not move when the first bookmark is set. A bookmarked line's cell is a badge (the number
  bold, gold on an accent tint); the digit stands in a small cyan box in the room, a little apart from the number,
  centred in the line and in the box, on every line whatever the song's length. The look is the bookmarks' own
  `EditorView.theme` (in `BookmarksExtension`), so only editors with bookmarks get the room. The marker, a Kotlin
  subclass of `GutterMarker`, draws, so it takes the place of CodeMirror's number for that line.
  - The CSS: `.cm-lineNumbers .cm-gutterElement` gets `padding-right: calc(3px + 1.4em)` (was CodeMirror's 3px). The
    digit (`.cm-bookmark-digit`) is `display: inline-block; box-sizing: border-box; width: 1.3em; height: 1.3em;
    line-height: calc(1.3em - 2px); margin-left: 0.35em; margin-right: calc(-1 * (1.3em + 0.35em)); border: 1px solid
    cyan; border-radius: 3px; color: cyan; font-size: 0.8em; text-align: center; vertical-align: middle`. Its right
    margin undoes its width and its gap, so it takes no width of its own and the number stays right-aligned where it
    is on every other line; its ems are of its own 0.8em size, so box and gap fit in the 1.4em room. The badge
    (`.cm-bookmark`) is unchanged.
- **The editor's props** (`KlangScriptEditorComp`, passed through `KlangCodeEditorComp`): `bookmarks: BookmarkOptions?`,
  with `initial` (read once, as the code is), `onChanged(lines)`, and `askToMove(move, answer)`, where `move` is
  `BookmarkOptions.Move(digit, fromLine, toLine)` and the host calls `answer(yes)` once; the editor moves on a yes and
  takes the focus back on either answer. Null (the default) leaves an editor without bookmarks.
- **The confirm**: Klang's own yes or no modal, `YesNoModal` (`klangui/.../comp/YesNoModal.kt`, a Kraft
  `FadingModal`; the maintainer's hand test, 2026-10-21: Kraft's `OkCancelModal` showed pale text on white). The
  song page asks "Move bookmark 3?", "Bookmark 3 is on line 42. Move it to line 50?", with "Move" and "Keep".
  - Its API: `Tag.YesNoModal(handle, headline: String, content: FlowContent.() -> Unit, yesText: String,
    noText: String, onResult: (yes: Boolean) -> Unit)`. The answer is decided at once (the yes button; the no button,
    Esc or the start of any close, a click beside it included, decide no) and `onResult` gets it exactly once when
    the modal is gone (its close handler, or its unmount when the modals manager takes it away without one). Until
    then the keyboard stays in the modal, so a key typed while it fades does not reach the editor; the editor takes it
    back with the answer.
  - Its look is the card look klang.css gives every `.ui.modal` (as the code tools' modal), a quiet basic no button,
    and the app's main-answer button for yes (`ui positive button`, gold), with a focus ring in the text colour
    (klang.css). Both are real `<button>`s; the yes button takes the keyboard when the modal opens, so Enter or Space
    answers yes. The modal itself is focusable (`tabindex="-1"`), so a click on its text keeps the keyboard in it.
  - klang.css now themes Semantic's `.ui.modal > .header`, `> .content` and `> .actions` (transparent, the theme's
    text colour, a divider above the actions), so every Semantic modal is themed, not white.
  - The editor still blurs before it asks.
- **The storage layer**, `src/jsMain/kotlin/storage/`: `SongStorage(version, songId, defaultCode, defaultTitle,
  defaultRpm)` holds the four `persistInLocalStorage` streams of a song (`code`, `title`, `rpm`, and `bookmarks`, a map
  digit to line stored as `{"3":42}`); `MidiInstrumentStorage` (with `MidiInstrument`) and `TutorialStorage` moved in
  unchanged except for their keys, which they now take from `StorageKeys`. `StorageKeys` names every key the app
  stores; it lives in `src/commonMain/kotlin/storage/` so `StorageKeysSpec` runs on the root `:jvmTest` (the root
  project has no JS test setup, and adding one is Gradle wiring). `CodeSongPage` builds a `SongStorage` and wires the
  bookmarks; when it writes is unchanged (the code on Play and on a reset, title and rpm on each change).
- **Tutorials** (`PlayableCodeExample`), the REPL and the Midi Playground get no bookmarks: their examples are short
  and reset, and nothing there would keep them. Their Ctrl+digit keys reach the browser as before.
- **Externals** (`klangjs/.../codemirror/ext/`, no `@file:JsModule` change): `Facet.compute`, `StateField.init`,
  `RangeSet.of`, `GutterMarker`, `lineNumberMarkers`, `Prec.highest`, `invertedEffects`, `isolateHistory`,
  `EditorState.transactionExtender` and the annotation types; `Transaction.isUserEvent` is a method now (it was a
  function-typed value, unused, which would lose its `this` in JS).
- **Build**: `klangscript-ui` compiles to ES2015 like the app (`compilerOptions { target.set("es2015") }`), so its
  browser tests run the same JS shape the app ships and Kotlin can extend CodeMirror's classes (maintainer,
  2026-10-21).
- **Tests**: `BookmarksSpec` (the rule, 15 rows: set and replace; toggle; ask and move; following the line; a blank
  line's bookmark; an indented line's Enter and a deletion inside it; a paste over a line; a deletion that keeps the
  end of the line; a deleted line's landing, also below a blank line; an empty line deleted both ways; a deletion from
  inside one line to inside a later one; two on one line; a moved line; line blocks and moves; load), `BookmarksExtensionSpec` (the wiring on a real `EditorView`, 20 rows: set, go and `preventDefault`
  with a German layout; the keypad's go, and keys left to the browser; `basicSetup` with a German Ctrl+Shift+7; a
  French layout; the toggle; the question with a no and a yes; the keyboard taken while asking and given back; a
  yes after the song got shorter; following typed lines and an Enter at the line's
  start; a blank line with real Enters; the real Enter at columns 0 and 4 of an indented line; a deletion from the line
  above into a bookmarked line's text; CodeMirror's delete line, a select-and-delete, and a delete line below a blank
  line; a paste over a whole indented line; the real move line up and down with undo and redo, an empty line, and two
  cursors with one on the first line; a move line followed by a Backspace, undone and redone; the undo and redo of a
  reset; the gutter, with each digit after its number; the start
  from the host's bookmarks; the whole-document replacement), and `StorageKeysSpec` (the
  keys, byte for byte, a check for the future).
- **Mutation-checked, round 1** (light tier, `console/tools/mutate`): 29 mutants, 29 KILLED with the expected message
  (the rule 11, the wiring 15, the keys 3). One mutant (a no that moves) first died for another reason (the test
  pressed again before checking the no); the row checks the no first since, and the mutant died again for the right
  reason.
- **Mutation-checked, round 2** (after review round 1; the old mutants re-aimed at the changed code, and new ones for
  each new row): 44 mutants, 44 KILLED with the expected message.
  - The rule (21): the line neighbour on a put, the put at the cursor instead of the line's text, the toggle, moving
    without asking, no mapping, the anchor at the line start instead of its text, a displaced bookmark landing as for
    a deleted line, a deleted line not seen, the last-line guard, the collision winner (survival, then position), what
    a change names as lost (a displaced winner, a dropped one), the restore, the line moves (ignored, a range to a
    line's start, touching ranges, the jumped line, a block at the end), the load's end check and duplicate digit.
  - The wiring (23): the go to the line's start, `preventDefault` only for a set digit, the go key's modifier, the
    reporter forgetting its last report, a no that moves, the question's line, no blur, no focus back, the
    shorter-song guard, `assoc` -1, the deletions not passed, a move line not seen, its direction, the undo of a move,
    of a reset and of a delete not stored, the restore not applied, the restore not mapped through a joined undo
    step, the gutter unsorted, the digit always shown, the gutter class, the initial bookmarks, `keepLines` ignored.
  - Three first died for another reason and were run again after a fix: two hit the fake document's index before its
    line check (the fake's `lineText` checks the line too now), and the unsorted gutter threw inside a key event,
    which the browser reports as uncaught and which ended the whole run (the gutter row starts from the host's
    bookmarks now, so the error lands in the test).
- **Mutation-checked, round 3** (after review round 2; `console/tools/mutate`, one lock call, each mutant with its
  spec's `--tests` filter): 22 mutants, 22 KILLED with the expected message. The rule (8): no re-anchoring, a deleted
  line's bookmark landing after the deletion point instead of the text after it, the anchor's deletion mapped from
  the anchor instead of its start, no next line after the deletion, its last-line guard, no mapping, a deleted line not
  seen, never displaced. The wiring (14): no `Prec.highest`, the digit from `event.key`, the keypad setting, no
  keypad go, Alt allowed, Meta allowed, `preventDefault` only for a set digit, the move line not isolated in the
  history, its direction from the first change, its direction from the selection, the undo of a move and of a reset
  not stored, the marker's class. Two first died for another reason or survived and were run again after a fix: the
  deleted-line mutant was re-aimed at round 2's exact behaviour (its first form broke an earlier clue of the same row),
  and the history row moved the cursor between the move and the Backspace, which ends an undo step by itself (the row
  presses Backspace right after the move now). The guard against focusing a gone editor has no row: CodeMirror's
  `focus()` on a destroyed view only focuses a detached element, which does nothing a test can see.
- **Not under a test**: the song page's wiring (the storage streams, the confirm) and `setCode`'s `keepLines` (a
  mounted component); both are on the hand-test list.

**Decided while building**, then settled by the reviews and the maintainer:

1. *Where a bookmark goes when its anchor is deleted.* When the deletion took the whole line (it started before the
   line and reached its end, as CodeMirror's delete line does from the end of the line above), to the line of the
   text right after the deletion: the line that takes its place, also when a blank line is above. A line break is
   not that text: a deletion that ends at a line's end lands on the next line, one that ends inside a line on that
   line, now joined to where the deletion started. Otherwise to the
   line where the deletion that took the anchor started (a paste over the line's text keeps it on the paste's first
   line).
2. *Two bookmarks that land on one line keep one* (a line deleted next to a bookmarked line, two lines joined): the one
   whose anchor survived wins, and between two of a kind the one that was higher up. The other is gone, also after an
   undo (maintainer, 2026-10-21).
3. *A reset to the original code* (`setCode`) keeps the bookmarks on their line numbers and drops one past the new end,
   as a reload would; its undo and redo keep them on their lines too.
4. *Bookmarks are kept in the browser only, never stored with the code* (maintainer, 2026-10-21: later they span
   several files). The song page stores its code on Play and the bookmarks on every change, so after edits without
   Play and a reload, the bookmarks keep the line numbers of the edited version.
5. *`klangscript-ui` compiles to ES2015* (maintainer, 2026-10-21), and the gutter marker is a plain Kotlin subclass of
   `GutterMarker` (it was built on the prototype while the module's tests compiled to ES5).

**Review round 1** (3 MAJOR, 3 MINOR, the leftovers; all fixed):

- MAJOR 1, Enter at the start of an indented bookmarked line left the bookmark on the new blank line (basicSetup's
  Enter deletes the indentation after the cursor, and the bookmark sat on the line's first character), and a deletion
  from inside line 9 into line 10 sent line 10's bookmark to old line 11. Fixed as the coordinator decided: a bookmark
  holds on to its line's first non-whitespace character with `assoc` 1. One part differs from the decision, on
  purpose: a bookmark counts as displaced when a deletion takes its character (as `MapMode.TrackAfter` has it, not
  `TrackDel`, which misses a deletion that starts at the character), and only when the deletion took its whole line
  does it land per decided point 1; otherwise it goes to the line where the deletion was. With `TrackDel` and point 1
  for every displaced bookmark, the reviewer's own case (deleting `ne 9\nli`) still lands on old line 11, and a
  deletion from a line's indentation into its text would send the bookmark to the next line.
- MAJOR 2, a moved line (Alt+Up, Alt+Down) counted as deleted: the move line event now moves the bookmarks with their
  lines, and the undo and redo of a move carry the moves.
- MAJOR 3, the undo of a reset collapsed every bookmark: `invertedEffects` carries `keepLines` into the undo and the
  redo. The related case 9 is built too, as the same mechanism: every change stores what it displaced or dropped, and
  its undo puts those back (an undo of a delete line brings back the bookmark it dropped, and one undo of two quick
  line deletes brings back both).
- MINOR 4, the keys are CodeMirror key bindings now (the custom handler, `digitOf` and `keyName` are gone), at
  `Prec.highest`; setting from the keypad is not supported (its row lost that claim).
- MINOR 5, the confirm left the keyboard in the editor: the editor blurs before it asks. Focusing the Yes button is
  not possible without changing Kraft (its buttons are `div`s).
- MINOR 6, test gaps: the gutter's sort (a higher digit above a lower one), the focus after an answer, the
  shorter-song guard; each has a row and a mutant.
- Leftovers: braces on the page's title persist; `Bookmarks.placed()` the function is `put` now; the digit has its own
  colour.

**Review round 2** (4 MAJOR, 3 MINOR; the maintainer decided to simplify, fix, and run round 3):

- MAJOR 1, on a German layout Ctrl+Shift+7 ran the comment key and set nothing (CodeMirror's keymap tries the typed
  `/` before the key's position): the 20 key bindings are gone; one `keydown` handler at `Prec.highest` reads the digit
  from `event.code`.
- MAJOR 2, a bookmark on a blank line walked down with each Enter once the line had text: every bookmark is put back
  on the anchor of its line after each change.
- MAJOR 3, Alt+Down on an empty line read as a move up (the delete and the insert touch, so the change has no pure
  insertion first): the direction comes from where the change starts. Not from the selection, as the triage proposed:
  CodeMirror leaves a block on the first line where it is when moving up and drops its ranges from the new selection,
  so with two cursors (lines 1 and 3) the ranges no longer pair up and the move reads as down; the reviewer's own
  case shows it, and a row with two cursors guards it.
- MAJOR 4, a move line joined in the history with a quick Backspace undid by line number only, and the redo threw: a
  `transactionExtender` gives every move line `isolateHistory` "full".
- MINOR 5, below a blank line a deleted line's bookmark landed on the blank line: it lands on the line of the text
  right after the deletion.
- MINOR 6, a paste ending in a line break over an indented bookmarked line sent the bookmark on or dropped it: it goes
  to the line where the deletion that took its anchor started.
- MINOR 7, an undo's `restore` overrode a later set or toggle: `restore` is removed (maintainer). A deleted line takes
  its bookmark with it.
- Surplus: `MapMode` and `mapPos` with a mode removed from the externals (and the effect `map` spec, unused without
  `restore`). Leftover: an answer after the editor is gone does not focus it.
- A cost of putting bookmarks back on their line's anchor, accepted: after a join (Backspace at a bookmarked line's
  start), the bookmark is on the joined line's anchor, so an undo of the join leaves it on the first part.

**Review round 3**: clean (no CRITICAL, no MAJOR); the reviewer judged the change-based move direction and the cost
of re-anchoring sound. Its two MINORs were applied as one batch, without a further round:

- MINOR 1, two corners of a deleted line's landing: an empty bookmarked line deleted as CodeMirror's delete line does
  landed on the line above (a select-and-delete of it landed right), and a deletion from inside line 8 to inside
  line 11 sent line 10's bookmark one line past the joined line 8. The text right after a deletion is now taken
  literally (`textAfter`): its end, or the next line's start when it ends at a line's end. Two rows, four mutants
  (no end-of-line arm, no last-line guard, the guard flipped, round 2's rule), all KILLED with the expected message.
- MINOR 2, surplus: the key constants `SET_KEY_PREFIX` and `GO_KEY_PREFIX` are gone; nothing read them.

**Not foreseen, for the maintainer.**

- *One key lives outside the storage layer*: `klang-tool-auto-update` (`KlangToolAutoUpdate` in `klangui`), which
  cannot reach the app's code; `StorageKeys` names it in its KDoc.

## History

- 2026-10-21: created; designed the same day (maintainer's request); two questions open.
- 2026-10-21: the maintainer's answers: kept per song in the browser behind a storage dir, the browser's default suppressed, Chrome only.
- 2026-10-21: IN PROGRESS. Started: a worker builds the storage layer and the bookmarks.
- 2026-10-21: built: the storage layer, the bookmarks rule and wiring, the gutter and the confirm; specs green, 29 of 29 mutants killed; four points decided while building and three not foreseen wait for the maintainer (Done).
- 2026-10-21: review round 1 (3 MAJOR, 3 MINOR, leftovers) fixed: bookmarks hold on to their line's text, move line and the history keep them, the keys are CodeMirror bindings, the confirm takes the keyboard; 44 of 44 mutants killed (Done, "Review round 1").
- 2026-10-21: review round 2 (4 MAJOR, 3 MINOR) fixed after the maintainer's call to simplify: keys by position in one handler, bookmarks back on their line's anchor after each change, move line isolated in the history, `restore` removed, ES2015 and a plain marker subclass; bookmarks stay in the browser only (Done, "Review round 2").
- 2026-10-21: review round 3 clean; its two MINORs applied as one batch (a deleted line lands on the text right after the deletion; the unused key constants removed); 4 of 4 mutants killed (Done, "Review round 3").
- 2026-10-21: the maintainer's hand test: the digit goes right of the line number, always shown, in room kept free on every line; the bookmark question in Klang's own themed yes or no modal (`YesNoModal`, klangui), and klang.css themes Semantic's modal content and actions; 5 of 5 mutants killed.
- 2026-10-21: the maintainer's look request and the look review (clean, its MINORs as one batch): the digit in a small cyan box right of the number, in the bookmarks' own editor theme; the yes or no modal answers once when it is gone, in the app's card look with the gold main-answer button; Semantic's modal header themed too; 2 of 2 mutants killed.
- 2026-10-21: DONE. Hand-tested by the maintainer 2026-10-21: the bookmarks work and look right (the digit box, the yes/no modal).
- 2026-10-25: a go centres its line in the editor, as the step back to the last edit does (maintainer, KLANG-00325's
  session); a browser test and its mutant (KILLED).
