# KLANG-00323: Editor: bookmarks per file

> **Status (2026-10-21): DESIGNED.** The maintainer's answers are in (below); starts after KLANG-00322.

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
   another line asks "Bookmark 3 is on line 42. Move it here?" in a small confirm (Fomantic, as the app's other
   prompts). A line holds one bookmark; setting another number there replaces it without asking.
5. **Stored as line numbers;** a stored line past the end of the song is dropped on load.

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

## History

- 2026-10-21: created; designed the same day (maintainer's request); two questions open.
- 2026-10-21: the maintainer's answers: kept per song in the browser behind a storage dir, the browser's default suppressed, Chrome only.
