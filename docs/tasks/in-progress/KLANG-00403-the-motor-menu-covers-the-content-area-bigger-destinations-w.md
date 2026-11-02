# KLANG-00403: The Motor menu covers the content area: bigger destinations, what was last opened, the loadouts

> **Status (2026-11-02): IN PROGRESS.** Steps 1 and 2 done (the menu over everything right of the column, its keys,
> its back navigation, the large layout with the looks; what was last opened, now with the song that plays at its top,
> KLANG-00406); the menu fills the workbench with its own left side (the performance graphs). Left: step 3's MIDI
> part (waits for KLANG-00402), step 4 (waits for KLANG-00405's index).

## Why this exists

The maintainer, 2026-11-01: "i would like to redesign the motor menu. When it is open it would like it cover the
entire middle content area. Then we have more space, to f.e. show 'last opened songs' or similar. Also more place for
the loadouts etc. Please redesign. The button like 'Songs' can then also be bigger."

Today (KLANG-00339, `KlangDrawer` in `klangui`, placed by `KlangWorkbench`): a 300 px panel laid over the column,
its foot just above the Motor. It holds "Where to", the search field, eight destinations as small tiles in two
columns, and the look row (seven looks and an empty slot).

## The design

- **Where it opens:** over everything right of the column: the content, the bottom panel, the tool windows and the
  tool strip, edge to edge and square (the maintainer, 2026-11-01: "cover 'everything' except the left side. No
  padding and not rounded corners"), one riveted panel. Only the column stays, with the Motor and its menu button, lit
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
   **Done (2026-11-01):**
   - `KlangWorkbench`: the menu in a frame of its own (`KlangWorkbenchMenu`, mounted only while open) over
     `workbenchRight`, edge to edge. It arms Kraft's `trapBackNavigation` (Back closes it, the page stays), watches the
     router (a move to another page closes it, as `FadingModal` does) and opens a key layer: the keys outside it wait,
     the Motor's included; in it Esc, Alt+M (closes) and Shift Shift or Ctrl+K (back to the search field; since 2026-11-02 they close it, the maintainer's word). While it is
     open the centre, a pinned tool window, the strip and the column's list are `inert`; the Motor stays. The menu's
     content gets a `MenuHandle`: `close`, and `leaveTo(uri)`, which writes the page with `router.replaceUri` in the
     trap's place, so Back from there leads to the page the menu opened on. `OnKey(SearchEverywhere)` sits by the
     Motor while there is a menu.
   - `KlangDrawer`: the search in the header row, the tiles in four columns with an icon each (`Entry.icon`;
     `AppArea.icon` in the app), the halves `pickUp` (its caller in step 2) and `loadouts` (the look picker; so the
     looks of step 3 are in).
   - `CodeToolModal` opens a key layer, as the app's other modals do, so the menu cannot open behind it.
   - Specs: `KlangWorkbenchSpec` (inert, the keys and the key layer, Back, a pick's history, a move closing it, Esc
     giving the trap's entry back; the history rows on path routing, as the app routes), `KlangDrawerSpec` (the header,
     the halves, the icons), `CodeToolModalSpec`. 223 `klangui` specs and 86 app specs green.
   - Review: round 1 (level 1) one MAJOR, fixed: the column's list stayed live, so a page picked there pushed past the
     trap's entry (escape ledger row); MINORs: `CodeToolModal`'s layer fixed, the Gui-dance step on the key and the
     early `pickUp` slot rejected. Round 2 (level 2, `reviewer-high`) clean; its MINORs applied (a spec for the code
     tool's layer, two long lines).
   - Mutation-checked (light tier, 12 mutants, all KILLED after two spec corrections): the parts not covered, the
     column's list not inert, the search key opening nothing, the open menu's search key not refocusing, Alt+M not
     closing, no key layer (survived first: its spec's key sat in the inert content, moved into the Motor), Back not
     closing, `leaveTo` pushing, a router move not closing (survived first under hash routing, where the change also
     fires a popstate; the history specs now route by path), the code tool's layer gone, the halves never split, the
     icon not hidden.
   - Seen in the second campaign, in two of its eight runs: `KlangNoticeModalSpec` with a page key answered twice; two
     reruns of the whole suite green. Unexplained; watch it.
2. The record of what was opened, in the Spine; "Pick up where you left off" reads it.
   **Done (2026-11-01):**
   - `spine/Recents`: the Spine's record of the songs (six kept) and lessons (three kept) opened, the newest first, one
     entry per thing; `record` is the pure rule. Kept by `spine/storage/RecentsStorage` (`klang-recents`, JSON; an
     entry that cannot be read is left out), the first store in the Spine's own storage; the Dev Storage page lists
     it as "Last opened". `SpineStorageSpec` (JVM) keeps the rule that only `spine/` imports `spine.storage`.
   - The song page reports its song (again on a new title or tempo); the tutorial page its lesson with the track it
     was read in (the main path when none). A page fading out (inert) reports nothing.
   - `MenuPickUp` in the menu's left half: the songs as tiles (title, RPM, when: "just now", "5 min ago", "yesterday"),
     and "Continue learning": the last lesson's track, its next lesson not done and a progress bar; all done leads to
     the track's page. With nothing opened yet it says so.
   - The lesson tile and its progress bar are new elements: the bar under the text at the tile's whole width, inside
     its padding (the maintainer, 2026-11-01: "below the text full width while respecting the padding of the button"),
     filled in a new look role, `Progress` (`--kl-progress`, follows the selected lamp, `LampOn`). Both in the kitchen
     sink (the menu's cell, and a cell with each state: nothing yet, begun, part done, all done) and in the look editor
     (a "Progress bar" card under Lamps, accent and focus), at the maintainer's word: "these new elements need to go
     into the kitchen sink and theme editor as well".
   - Specs: `RecentsSpec` (the order of opening, also with a clock set back; the counts; the codec), `MenuPickUpSpec`
     (when, which track, a gone lesson passed over, a gone song not offered, where a pick leads), a `LearnPagesSpec`
     case (a lesson recorded with its track), `SpineStorageSpec` (JVM). 15 mutants killed (light tier).
   - Review: round 1 (level 1) clean; its MINORs applied as one batch: the order of opening instead of the clock, a
     lesson or song gone from the app passed over, a cleared title recorded as the song's own, the count of lessons
     in the tile's text (a progressbar role inside a button is lost to a screen reader), the samples from the track
     itself. Declined: a spec for the song page's report and its inert guard (the page needs the code editor and the
     player in a TestBed; the guard is the line its `fillWorkbench` already uses on the same page).
   - Not yet: the Dev Storage page's reset leaves the list in memory until a reload (the Spine's reset through its
     areas is KLANG-00389's).
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
- 2026-11-01: IN PROGRESS.
- 2026-11-02: step 2 has the song that plays at its top (KLANG-00406), the half's heading removed (the maintainer: "it does not
  serve any purpose"); the menu fills the workbench with its own left side, the performance graphs (c72880d46).
- 2026-11-02: Shift Shift and Ctrl+K close the open menu, as Alt+M does; the Motor's button shows Shift Shift beside Alt+M in its hint.
