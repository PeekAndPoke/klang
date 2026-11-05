# KLANG-00413: The workbench's parts from three places: the app, an area, a page, on the left, the right and the bottom

> **Status (2026-11-05): IDEA.** A first proposal, for the maintainer to decide D1 to D5.

## Why this exists

The maintainer (2026-11-05): "let us think about a general mechanism how pages register 'things' in the bottom tools
(which we should already have), the right menu, the left menu."

## How it works today

A page has one entry point since KLANG-00409: `PageWorkbench(this) { column { }; tool(...) { }; bottomTabs(...) }`,
which fills `WorkbenchSlots` while the page is mounted. What the app and an area give is spread over three files:

| Side | What the page gives | What else is there, and where it is written |
|------|---------------------|---------------------------------------------|
| Left, the column over the Motor | `column { }`: the whole column, or nothing | No column from the page: `SidebarMenu`, which picks by area with an `if` (Learn: its tracks, else the app map) |
| Right, the strip's tool windows | `tool(...)`, after the app's | The Motor tool window, written inline in `WorkbenchLayout`; the fullscreen key and the mark at the strip's foot |
| Bottom, the panel's tabs | `bottomTabs(...)`, before the app's | Visuals, MIDI and Console, fixed in `BottomTools` |

So a page registers in one place, but the app's own parts live in three, and an area's parts are hidden in an `if`.
The left side also works differently from the other two: it is replaced, not added to.

## Proposal

- **One shape for all.** `WorkbenchParts` (today's `PageWorkbench.Parts`): a column, tool windows, bottom tabs.
- **Three scopes, one per kind of owner.**
  - *The app*, on every page: one file, `AppWorkbench`, with the Motor tool window and the Visuals, MIDI and Console
    tabs. `BottomTools` and the inline Motor tool fold into it.
  - *An area*, on every page of it (Learn, Songs, Docs, ...): `AreaWorkbench`, a part list per `AppArea`, read from the
    route, so no mount is needed. Learn's tracks column moves here out of `SidebarMenu`'s `if`.
  - *A page*: `PageWorkbench`, as today.
- **The layout only merges.** No part is written in `WorkbenchLayout` any more; it reads the three scopes and puts
  them together in a fixed order (D3).

## Decide before implementing

- **D1, the left side.** (a) It stays one column: the most specific wins (the page's, else the area's, else the app
  map). (b) It becomes a stack of sections, each scope adding its own (the song page's "This song" under an area's
  section, say). Proposed: (a); sections only when a real case asks for them.
- **D2, can a page hide the app's parts?** Proposed: no. The app's parts are on every page, as the maintainer asked
  for the bottom tools ("always present", KLANG-00409).
- **D3, the order.** Proposed, as today: on the right the app's first, then the area's, then the page's; at the
  bottom the page's first, then the area's, then the app's (the most specific next to where you work).
- **D4, ids.** The kept layout remembers tool windows and tabs by id, so ids stay unique across the scopes; a spec
  checks the app's and the areas' parts, and the merge logs a clash from a page.
- **D5, showing a part by a key.** Today only the bottom panel (`showBottom(tabId)`, Alt+Q). Proposed: one
  `show(id)` for a tool window or a tab, so any part can have a key or a link that opens it.

## The work (once decided)

1. `WorkbenchParts` as the shared shape; `PageWorkbench` uses it.
2. `AppWorkbench`: the Motor tool window and the three tabs move in; `BottomTools` goes.
3. `AreaWorkbench`: Learn's column moves in; `SidebarMenu` keeps only the app map.
4. The ids' spec (D4) and `show(id)` (D5).

## History

- 2026-11-05: created from the maintainer's question, after KLANG-00410 to 00412.
