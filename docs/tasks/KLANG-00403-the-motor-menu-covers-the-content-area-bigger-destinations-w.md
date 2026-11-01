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

- **Where it opens:** over the whole content area of the workbench's centre, inset a little, as one riveted panel. The
  column (with the Motor and its menu button, lit while the menu is open) and the tool strip stay. The bottom panel
  stays under it, so the MIDI tool (KLANG-00402) keeps playing while one browses the menu.
- **The header:** "Where to", a wide search field (it takes the focus on open, as today), "Close: Esc" and the close
  button.
- **The destinations:** the same eight (`AppArea`, Account still "later"), in four columns, each about 100 px high
  with its icon, its label in large type, its second line and its lamp; the current one pressed, its lamp on.
- **Below, two halves:**
  - **Pick up where you left off:** the last opened songs (six, title, RPM and when), the tutorial track in progress
    with its next lesson and a progress bar, and the docs pages last read.
  - **Loadouts:** the look loadout (the eight slots of `KlangLookShelf`, larger, each with three colour dots and its
    name; "Edit looks"), and the MIDI instruments' loadout (KLANG-00402; "Edit on the MIDI page").
- **Searching:** with text in the field, the lower part becomes the results in four columns: songs, docs, samples,
  actions.

## What it needs that does not exist yet

- **A record of what was opened:** the last songs, the last docs pages. Global state, so in the Spine (KLANG-00389),
  saved in its storage; a page reports what it opened.
- **The tutorial in progress:** `TutorialStorage` knows the finished lessons; the next one per track is derived.
- **The MIDI loadout:** KLANG-00402.
- **Search over docs, samples and actions:** check what the drawer's search covers today before building the
  columns.

## Decide before implementing

- **D1:** the menu over the content only (proposal; the bottom panel stays usable) or over the bottom panel too.
- **D2:** what "last opened" holds: songs only, or songs, lessons and docs pages (proposal: all three, as drawn).
- **D3:** the look picker's place: in the menu as drawn, and also on the Looks page (unchanged).

## The work

1. `KlangDrawer` gets the large layout and `KlangWorkbench` places it over the centre (both in `klangui`, with the
   kitchen sink's entry updated).
2. The record of what was opened, in the Spine; "Pick up where you left off" reads it.
3. The loadouts' section (looks now; MIDI with KLANG-00402).
4. The search results in columns.

## Acceptance

- The menu opens over the content area, takes the focus in the search field, closes on Esc and on a pick, and gives
  the focus back as today.
- A song opened shows first under "last opened" the next time the menu opens, also after a reload.
- Every look and every destination is reachable by keyboard; the destinations keep their `aria-current`.

## History

- 2026-11-01: created from the maintainer's ask; the mockup on a design canvas, built on a screenshot of today's
  drawer (the brass look) and the look roles of `KlangRole`.
