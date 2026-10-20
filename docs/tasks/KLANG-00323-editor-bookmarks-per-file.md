# KLANG-00323: Editor: bookmarks per file

> **Status (2026-10-21): DESIGNED.** Two questions for the maintainer (below) before it starts.

## Why this exists

The maintainer's request (2026-10-21): bookmarks as in IntelliJ, per file first. Ctrl+Shift+Number sets a bookmark,
Ctrl+Number goes there, overriding an existing bookmark asks first, and the gutter shows them: the line number
highlighted, and the bookmark's number beside it if there is space.

## Depends on / unblocks

Nothing.

## Decide before implementing

1. **Saved where?** (maintainer) (a) only while the song is open; (b) per song in this browser (local storage, keyed
   by the song), surviving a reload. Recommendation: (b), since songs are worked on over days; (a) is the smaller
   first step.
2. **The keys.** Ctrl+1 to Ctrl+9 switch browser tabs in Chrome and Firefox; whether a page may take them over has to
   be checked in the maintainer's browser. If the browser keeps them: Alt+Number to go, Alt+Shift+Number to set?
   (maintainer). The digit is read from the key's position (`event.code`, `Digit1` ...), so Shift+1 works on a German
   layout, where it types `!`.
3. **Setting on a line that holds the same number** removes it (IntelliJ's toggle). Setting a number that sits on
   another line asks "Bookmark 3 is on line 42. Move it here?" in a small confirm (Fomantic, as the app's other
   prompts). A line holds one bookmark; setting another number there replaces it without asking.

## The work

1. A CodeMirror `StateField` with a `RangeSet` of bookmark points (0 to 9), mapped through every change, so a
   bookmark moves with its line; a deleted line's bookmark lands on the line that takes its place.
2. Gutter: a `lineNumberMarkers` marker that highlights the line number and shows the bookmark's digit when the
   gutter is wide enough.
3. Keymap: set, toggle, go (scroll into view, cursor to the line's start), and the confirm for a move.
4. Persistence per the answer to 1.
5. Tests (light tier, mutation-checked): the bookmark logic (set, toggle, move, map through changes) as a plain
   function; the gutter and the keys by hand.

## Acceptance

The specs green and mutation-checked; a hand test of setting, moving with the prompt, toggling, jumping, and a
bookmark following its line while text is typed above it.

## Out of scope

Bookmarks across songs (IntelliJ's global list), named bookmarks, and a bookmark panel.

## History

- 2026-10-21: created; designed the same day (maintainer's request); two questions open.
