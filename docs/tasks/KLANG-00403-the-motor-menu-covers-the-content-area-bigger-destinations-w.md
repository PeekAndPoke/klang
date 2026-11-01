# KLANG-00403: The Motor menu covers the content area: bigger destinations, what was last opened, the loadouts

> **Status (2026-11-01): DESIGNED.** A clickable mockup (a design canvas, "Motor Menu Redesign"); nothing built.

## Why this exists

The maintainer, 2026-11-01: "i would like to redesign the motor menu. When it is open it would like it cover the
entire middle content area. Then we have more space, to f.e. show 'last opened songs' or similar. Also more place for
the loadouts etc. Please redesign. The button like 'Songs' can then also be bigger."

Today (KLANG-00339, `KlangDrawer` in `klangui`, placed by `KlangWorkbench`): a 300 px panel laid over the column,
its foot just above the Motor. It holds "Where to", the search field, eight destinations as small tiles in two
columns, and the look row (seven looks and an empty slot).

## The design

- **Where it opens:** over everything right of the column: the content, the bottom panel, the tool windows and the
  tool strip, inset a little, as one riveted panel. Only the column stays, with the Motor and its menu button, lit
  while the menu is open: the menu grows out of the Motor. So it is always the same size, whatever is pinned or open
  (the maintainer, 2026-11-01: "it will always look the same and that is what you want from a navigation hub...
  should go into your hands without the need to search for anything everytime"). It changes with the window only.
- **A real overlay:** the page under it stays mounted (the maintainer: "we cannot unmount the underlying screen,
  otherwise f.e. a song would stop play"). While the menu is open, what it covers is `inert` (no focus, no clicks, no
  screen reader behind it) and may rest its drawing (`UiWork`, KLANG-00389), but plays on: a song, the MIDI rig
  (KLANG-00402), whose held notes keep sounding.
- **A modal of the workbench** (the maintainer, 2026-11-01: "Basically this menu is a new kind of Modal i assume.
  Then it would also react to back navigation, or it would close automatically on uri change"). It behaves as a
  modal: the focus stays inside while it is open, what it covers is `inert`, Esc closes it. Not Kraft's modal stack:
  that covers the whole window, and the column with the Motor must stay. Two rules more:
  - **Back closes it and stays on the page** (the maintainer, 2026-11-01: "as long as we have the back navigation
    trap armed"): the open menu arms Kraft's `trapBackNavigation`, as `FadingModal` does (and with it the visuals,
    `VisualsScreen`): its callback closes the menu and lets the navigation through, which only takes the trap's own
    entry. Closed any other way (Esc, the button, a pick), the trap goes back over its entry itself. A phone's back
    gesture does the same.
  - **Any other change of the URI closes it too:** a pick, a link inside it, Forward. As `FadingModal` watches the
    router's page, the menu does; on a pick, check the order of closing and navigating so no empty entry of the trap
    stays in the history.
  - A dialog open (its own key layer) keeps Shift Shift and Alt+M: the menu does not open over a dialog.
- **Its keys:** the menu button, Alt+M (`KlangKeyBindings.MotorMenu`, today's), and Shift Shift or Ctrl+K / Cmd+K
  (`KlangKeyBindings.SearchEverywhere`, declared for "the search over everything" and never bound until now: the
  maintainer, 2026-11-01, "now we also know what 'shift + shift' does ... it opens this menu"). Each opens it with the
  focus in the search field; Esc closes it and gives the focus back.
- **The header:** "Where to", a wide search field (it takes the focus on open, as today), "Close: Esc" and the close
  button.
- **The destinations:** the same eight (`AppArea`, Account still "later"), in four columns, each about 100 px high
  with its icon, its label in large type, its second line and its lamp; the current one pressed, its lamp on.
- **Below, two halves:**
  - **Pick up where you left off:** the last opened songs (six, title, RPM and when) and the tutorial track in
    progress with its next lesson and a progress bar (D1).
  - **Loadouts:** the look loadout (the eight slots of `KlangLookShelf`, larger, each with three colour dots and its
    name; "Edit looks"), and the MIDI instruments' loadout (KLANG-00402; "Edit MIDI", the same pattern).
- **Searching:** with text in the field, the lower part becomes the results in four columns: songs, docs, samples,
  actions.

## What it needs that does not exist yet

- **A record of what was opened:** the last songs and lessons (D1). Global state, so in the Spine (KLANG-00389),
  saved in its storage; a page reports what it opened.
- **The tutorial in progress:** `TutorialStorage` knows the finished lessons; the next one per track is derived.
- **The MIDI loadout:** KLANG-00402.
- **Search:** the field searches nothing today (`WorkbenchLayout` keeps the text, nothing reads it). The index over
  songs, docs, samples and actions is its own ticket, KLANG-00405; until then the field stays as it is.

## Decide before implementing

- **D1, decided: songs and tutorials** (the maintainer, 2026-11-01: "I would only add the Songs and Tutorial to the
  'recently open'... For the docs and such we might think about a bookmark system", KLANG-00404).
  - **Counts:** a song opened in the editor (a built-in or yours, also without playing it); a tutorial lesson
    opened (the track's "continue" is its next unfinished lesson, from `TutorialStorage`).
  - **Does not count:** docs pages (bookmarks instead, KLANG-00404), the area pages, Credits, Dev Tools, the Looks
    page, the MIDI page.
  - **Shown in groups, as drawn:** songs (six), then the track in progress, each in the same place every time.
  - **The record:** one Spine area (`Recents`), entries of kind, id, title and time, the newest first, capped per
    kind, saved in the Spine's storage; a page reports what it opened.
- **D2, decided** (the maintainer, 2026-11-01: "yes look picker is ok like this"): the look picker in the menu as
  drawn, and on the Looks page as today.

## The work

1. `KlangDrawer` gets the large layout; `KlangWorkbench` places it over everything right of the column, the rest
   `inert` under it; `SearchEverywhere` bound to open it (all in `klangui`, the kitchen sink's entry updated).
2. The record of what was opened, in the Spine; "Pick up where you left off" reads it.
3. The loadouts' section (looks now; MIDI with KLANG-00402).
4. The search results in columns, once KLANG-00405 has the index.

## Acceptance

- The menu opens over everything right of the column at the same size whatever is pinned or open, from the button,
  Alt+M, Shift Shift and Ctrl+K; takes the focus in the search field, closes on Esc and on a pick, and gives the
  focus back as today.
- A song playing under the open menu plays on; nothing under it takes a click or the focus.
- Back closes the menu and stays on the page; any other change of the URI closes it too; after a pick, Back goes to
  the page before it (no entry of the trap left over).
- A song opened shows first under "last opened" the next time the menu opens, also after a reload.
- Every look and every destination is reachable by keyboard; the destinations keep their `aria-current`.

## History

- 2026-11-01: created from the maintainer's ask; the mockup on a design canvas, built on a screenshot of today's
  drawer (the brass look) and the look roles of `KlangRole`. The same day: a real overlay over the mounted page,
  Shift Shift opens it, and it covers the tool windows and the bottom panel too, so it always looks the same (the
  maintainer's word). The modal rules: Back closes it (Kraft's back navigation trap), any change of the URI too. D2 decided
  (the look picker as drawn); D1 decided: songs and tutorials, docs to bookmarks (KLANG-00404).
