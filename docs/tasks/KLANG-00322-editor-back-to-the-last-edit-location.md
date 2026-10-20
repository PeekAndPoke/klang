# KLANG-00322: Editor: back to the last edit location

> **Status (2026-10-21): DESIGNED.** After KLANG-00321; no open question.

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
3. **Which edits count:** transactions with a user event (`input`, `delete`, `undo`, `redo`, `move.drop`), and the
   editor tools' writes (an edit the user asked for). Loading a song or replacing the whole document clears the list.
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

## History

- 2026-10-21: created; designed the same day (maintainer's request and question).
