# KLANG-00322: Editor: back to the last edit location

> **Status (2026-10-23): IN PROGRESS.** Hand test passed; the maintainer's addition (any cursor move ends the walk) built and reviewed; waits for its hand test.

## Why this exists

The maintainer's request (2026-10-21): IntelliJ's Ctrl+Shift+Backspace, which jumps to where you last edited. The
question with it: when are many keypresses one edit?

## Depends on / unblocks

Nothing. Independent of KLANG-00321 and KLANG-00323.

## Decide before implementing

1. **One edit place, by distance, not time.** The list holds edit places, newest last. A user edit within
   `NEAR_LINES` (2) of the newest place REPLACES it (the place moves to the edit's end); an edit further away PUSHES
   a new place. A typing burst, a fix two lines up, and a rename across one line therefore stay one place; going to
   another part of the song and typing makes a new one. Time plays no part: a pause in the same spot is still the
   same spot. (CodeMirror's undo history groups by time, 500 ms, and adjacency; that is right for undo, not for
   navigation.)
2. **Places follow the text:** every change maps each place forward (`ChangeDesc.mapPos`), so typing above a place
   does not move it off its line.
3. **Which edits count:** every document change; a change of the selection alone is none. Loading a song or replacing
   the whole document clears the list. *(Changed 2026-10-21 in review round 1, coordinator's decision. It said:
   transactions with a user event (`input`, `delete`, `undo`, `redo`, `move.drop`), and the editor tools' writes.
   Common commands slipped through that list: toggle comment sends no user event at all, move line sends `move.line`,
   indent `indent`, transpose `move.character`. The editor's only changes of its own are `setCode`, which clears, and
   the tools' writes, which count anyway.)*
4. **Going back:** Ctrl+Shift+Backspace (Cmd+Shift+Backspace on a Mac) moves the cursor to the newest place that is
   not where the cursor already is, and each further press goes one place older. Any new edit ends the walk. The list
   keeps 30 places; no forward step, as in IntelliJ by default.
5. **Not saved:** the list lives as long as the editor (one song open).

## The work

1. A CodeMirror `StateField` with the places and the walk index, updated by the rule above
   (`klangscript-ui/.../codemirror/`), and a keymap entry.
2. The rule as a plain function (merge or push, map, step back) in common code, so it is tested on the JVM without
   CodeMirror.
3. Tests (light tier, mutation-checked): a burst on one line is one place; an edit three lines away is a new one;
   text inserted above moves a place with its line; going back skips the cursor's own place and walks older; a new
   edit ends the walk; the cap drops the oldest.

## Acceptance

The specs green and mutation-checked; a hand test: edit at the top of a song, then at the bottom, then press
Ctrl+Shift+Backspace twice: the cursor goes to the bottom edit's place only if it moved away from it, then to the top.

## Out of scope

Places across songs (one list per editor for now), and a forward step.

## Done 2026-10-21

Built as designed, with one change of place: the rule lives in `klangscript-ui`'s `jsMain` and is tested in
ChromeHeadless, not in common code on the JVM ("The work" 2), because `klangscript-ui` is JS-only (the coordinator's
brief). It still holds no CodeMirror. Review round 1 changed point 3 (every change is an edit, see there) and how an
edit at several places merges; both are in the text below. Two points the design left open are decided below
("Decided while building"); a third came from the coordinator: a selection move, such as KLANG-00321's "go to
definition", is no edit.

- **The rule, `EditPlaces`** (`klangscript-ui/.../codemirror/EditPlaces.kt`): an immutable data class with the places
  (offsets, newest last) and the walk index. `changed(mapPos, ranges, lineOf)` maps every place, ends the walk, and
  replaces the newest place when it lies within `NEAR_LINES` (2) of any one changed range, or pushes otherwise; the
  place goes to the last range's end, and the newest `MAX_PLACES` (30) are kept. `back(cursor, lineOf)` returns the
  target offset and the walk index, or null; `walkedTo(index)` records the step. No CodeMirror in it.
- **The wiring, `EditPlacesExtension`** (same folder): a `StateField` and a keymap entry, `Mod-Shift-Backspace`
  (Ctrl+Shift+Backspace, Cmd+Shift+Backspace on a Mac). Every transaction that changes the document is an edit, and
  each range it changed reaches the rule on its own. A transaction that changes only the selection (a step back, "go
  to definition") leaves the places as they are. The `clear` effect empties the list. The binding sets
  `preventDefault`, so the key never reaches the browser, also when there is no place to go back to.
- **Tool commits** count as every change does; `DslEditorExtension` is unchanged.
- **Song load**: `KlangScriptEditorComp.setCode` (the song page's reset to the original, a code example's Reset; it
  does nothing when the code is unchanged) carries `clear`. Opening a song builds a new page and editor (the page reads
  its song when it is constructed), so the list starts empty; not checked in the browser.
- **Externals** (`klangjs/.../codemirror/ext/`): `ChangeSet.mapPos` added; `keymap` is now the facet it is in
  CodeMirror (`external val keymap: Facet<Array<KeyBinding>>`, used as `keymap.of(...)`). It was declared as a function,
  which would have thrown on its first call; nothing called it. No `@file:JsModule` change.
- **Tests**: `klangscript-ui` has a `jsTest` source set now: the `ksp` and `io.kotest` plugins and the `jsTest`
  dependency block of `klangscript/build.gradle.kts` (its lines 10, 11 and 66 to 72), nothing else.
  - `EditPlacesSpec`, the rule: one row per point of "The work" 3, and three from the reviews (an edit at several
    places does not swallow the newest place between them, with the near range first or last; going back skips a place
    one line from the cursor too; a change with no range only maps and ends the walk).
  - `EditPlacesExtensionSpec`, the wiring, on a real `EditorState` and `EditorView` in ChromeHeadless: a change with and
    without a user event, and a selection move; the real commands toggle comment (javascript language) and move line
    down; an edit at several places, around the newest place and with the near range first; the clear; the key by a
    synthetic keydown, with `preventDefault`.
  - `setCode`'s `clear` itself is under no test (a component with a mounted editor); it is on the hand-test list.

**Decided while building.**

1. *"Where the cursor already is" uses the same distance.* Going back skips every place within `NEAR_LINES` of the
   cursor, at every step, not only the newest place at the exact offset. A place is a spot defined by that distance, so
   a cursor one line off is still at it, and two places that the mapping moved close together are not two steps.
2. *An edit at several places* (several cursors, a "replace all") merges when the newest place is near any one of its
   ranges, and its place is the last range's end. (Built first as one span from the first range to the last, which
   swallowed a place lying between two cursors far apart; fixed in review round 1.)

**Not covered, for the record.** A select-all and paste is an edit of the whole document: it is not a `setCode`, so it
does not clear the list; every old place maps to the document's start or end and the paste merges or pushes as any
edit. Undoing a `setCode` (Ctrl+Z after Reset) is an edit, so it records one place at the document's end.

**Review round 1** (1 MAJOR, 4 MINOR, all fixed):
- MAJOR: common commands were not counted (toggle comment sends no user event; move line, indent and transpose send
  `move.line`, `indent`, `move.character`). Point 3 now counts every change, and the `toolEdit` mark is gone.
- MINOR: an edit at several places swallowed the newest place between its ranges (decided point 2); its row also
  covers the span that was untested before.
- MINOR: the skip within `NEAR_LINES` of the cursor was untested (only the exact offset); a row with the cursor one
  line off now pins it.
- MINOR: `setCode`'s `clear` is under no test; kept as a hand test.
- Leftovers: `goBack` is private; `setCode`'s KDoc says it is for resets and returns early on unchanged code.

**Review round 2** (clean: no CRITICAL or MAJOR; a MINOR batch, all applied):
- The "near any one range" rule was pinned only for a near range that comes last (`takeLast(1)` survived): a step with
  the near range first, in the rule's row. The coordinator's example (cursors on 37 and 3 after a place on 39) gives
  the ranges in document order, 3 then 37, so the near one is last there; the step uses a place on line 3 and cursors
  on 4 and 30 instead.
- "Every range reaches the rule" was pinned only against first-only (last-only survived): a wiring row with cursors on
  lines 19 and 40 around a place on 20, expecting `[40]`.
- NIT: the empty-ranges guard now ends the walk too, as the KDoc says; a rule row calls it directly.
- Leftover: the field maps the changes first and applies a step back after. The step's effect carries only the walk
  index now (`Step(target, walkIndex)`, `walkedTo`). No row can tell this apart: no transaction carries both a change
  and a step, and the effect is private.

**Mutation check** (light tier, `console/tools/mutate`, 2026-10-21, after review round 2): 25 mutants, all KILLED for
their own row's message, in one campaign of `:klangscript-ui:jsBrowserTest --rerun`; restored byte-identical, final run
green. (The first build's 17 and round 1's 22 were all KILLED as well; the mutants of the old event list and the tool
mark went with that code.)

| Mutant | Row | Verdict |
|---|---|---|
| never merge (`kept = mapped`) | a burst on one line is one place | KILLED |
| three lines below merges (`fromLine - NEAR_LINES - 1`) | an edit three lines away is a new place | KILLED |
| three lines above merges (`toLine + NEAR_LINES + 1`) | an edit three lines away is a new place | KILLED |
| `NEAR_LINES = 1` | an edit three lines away is a new place (two lines merge) | KILLED |
| no mapping (`mapped = places`) | text inserted above moves a place with its line | KILLED |
| no skip (`isNear` inverted) | going back skips the cursor's own place and walks older | KILLED |
| the walk never goes older (`start = places.size - 1`) | going back skips the cursor's own place and walks older | KILLED |
| an edit keeps the walk (`walkIndex = walkIndex`) | a new edit ends the walk | KILLED |
| the cap drops the newest (`take` for `takeLast`) | the cap drops the oldest | KILLED |
| `MAX_PLACES = 31` | the cap drops the oldest | KILLED |
| one span from the first range to the last | an edit at several places (around the newest place) | KILLED |
| only the first range is compared | an edit at several places (the near range last) | KILLED |
| only the last range is compared (round 2) | an edit at several places (the near range first) | KILLED |
| the place at the first range's end | an edit at several places (around the newest place) | KILLED |
| skip only the exact offset (`places[index] != cursor`) | going back skips a place one line from the cursor too | KILLED |
| the empty-ranges guard keeps the walk (round 2) | a change with no range only maps and ends the walk | KILLED |
| only `input` and `move` events count | wiring: a change without a user event | KILLED |
| the same mutant | wiring: toggle comment | KILLED |
| `move.line` not counted | wiring: move line down | KILLED |
| only the first changed range reaches the rule | wiring: an edit at several places | KILLED |
| only the last changed range reaches the rule (round 2) | wiring: merges by its first range too | KILLED |
| a selection move counted | wiring: a selection move | KILLED |
| the clear ignored | wiring: replacing the whole document empties the list | KILLED |
| `preventDefault = false` | wiring: the key steps back and is never left to the browser | KILLED |
| another key (`Mod-Alt-Backspace`) | wiring: the key steps back and is never left to the browser | KILLED |

**Waits for** the maintainer's hand test (the acceptance above; Reset empties the list; on a Mac, that
Cmd+Shift+Backspace never opens the browser's dialog for clearing the history).

## After the hand test (2026-10-23)

The maintainer's hand test passed, with one addition: "moving the cursor in any way resets the stack pointer, meaning
the next Ctrl+Shift+Backspace goes again to the youngest edit location. This include keyboard cursor movements and
cursor movements by mouse". Point 4 ("Any new edit ends the walk") therefore reads: any new edit or cursor move ends
the walk.

- **Built:** `EditPlaces.cursorMoved()` ends the walk; the field calls it for every transaction that sets a selection,
  except the step back's own move (it carries `walked` and is handled first). Focus changes carry no selection, and
  CodeMirror reports a browser selection only when it differs, so neither ends a walk by accident (reviewer, checked
  against CodeMirror's source).
- **Tests:** a rule row (a cursor move ends the walk) and a wiring row (the step's own move keeps the walk; the real
  `cursorLineDown` command and a `select.pointer` selection each start over from the newest place); 17 tests green.
- **Mutation check:** 3 mutants, all KILLED for their own row (the reset taken out; `cursorMoved` keeping the walk;
  the step's own move resetting), restored byte-identical, final run green.
- **Review round 1** (Opus, blind): clean, 4 NITs; the KDoc line on why `goBack` is internal applied, the rest kept
  (the click row sends a hand-built pointer selection, not a real mousedown: a real click is the hand test's).

**Waits for** a second hand test of the addition: walk back twice, click or press an arrow key, press again: the
cursor goes to the youngest edit.

## History

- 2026-10-21: created; designed the same day (maintainer's request and question).
- 2026-10-21: IN PROGRESS. Started: a worker builds the edit places and Ctrl+Shift+Backspace.
- 2026-10-21: built: `EditPlaces` (the rule) and `EditPlacesExtension` (field, key), tool commits and `setCode` wired, `klangscript-ui` got a `jsTest` source set; 10 tests green, 17 mutants KILLED. Waits for the hand test.
- 2026-10-21: review round 1 (1 MAJOR, 4 MINOR) fixed: every change is an edit (point 3 changed), an edit at several places merges range by range, the near-cursor skip has a row; 13 tests green, 22 mutants KILLED. Waits for the hand test.
- 2026-10-21: review round 2 clean (no CRITICAL or MAJOR); its MINOR batch applied: the near range first, last-only ranges, the guard ends the walk, the changes mapped before a step; 15 tests green, 25 mutants KILLED. Waits for the hand test.
- 2026-10-21: IN PROGRESS. Built and reviewed (round 2 clean, the MINOR batches applied, 25 mutants KILLED); waits for the maintainer's hand test.
- 2026-10-23: hand test passed; addition built: any cursor move ends the walk (maintainer). Review round 1 clean, 3 mutants KILLED. Waits for the hand test of the addition.
