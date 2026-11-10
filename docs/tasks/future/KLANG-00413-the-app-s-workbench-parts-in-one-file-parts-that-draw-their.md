# KLANG-00413: The app's workbench parts in one file, parts that draw their own label, the left column's rule

> **Status (2026-11-10): IDEA.** The shape agreed with the maintainer; D4 and D5 still open.

## Why this exists

The maintainer (2026-11-05): "let us think about a general mechanism how pages register 'things' in the bottom tools
(which we should already have), the right menu, the left menu."

## How it works today

A page has one entry point since KLANG-00409: `PageWorkbench(this) { column { }; tool(...) { }; bottomTabs(...) }`,
which fills `WorkbenchSlots` while the page is mounted. On unmount it empties a slot only while the slot still holds
the very list it put there, so a page change is right in either order of mount and unmount. That part stays.

What the app gives is spread over three files:

| Side | What the page gives | What else is there, and where it is written |
|------|---------------------|---------------------------------------------|
| Left, the column over the Motor | `column { }`: the whole column, or nothing | No column from the page: `SidebarMenu`, which picks by area with an `if` (Learn: its tracks, else the app map) |
| Right, the strip's tool windows | `tool(...)`, after the app's | The Motor tool window, written inline in `WorkbenchLayout` |
| Bottom, the panel's tabs | `bottomTabs(...)`, before the app's | Visuals, MIDI and Console, fixed in `BottomTools`, with the panel's title, icon and count |

A part is drawn from data: a tab's `title` and `count`, a tool's `icon` and `chip`, the panel's `count`. So the
Console's tab is built again with every change of the unseen errors (`ConsoleTool.tab(unseenErrors)`), and the layout
has to carry that number for it.

## Proposal

The simplest shape that keeps how things work today (the maintainer, 2026-11-10).

- **`AppWorkbench`, one file for the app's parts.** The Motor tool window and the Visuals, MIDI and Console tabs,
  with the bottom panel's title and icon. `BottomTools` and the inline Motor tool fold into it. The layout calls it
  when it draws and adds the page's parts, in today's order: on the right the app's first, at the bottom the page's
  first. Nothing of the app's goes through `WorkbenchSlots`.
- **A part can draw its own label.** The maintainer: "a Tool or a Bottom should have a render callback to draw
  itself in the sidebar or the bottom tools tab bar. [...] The tool should be able to decide by itself." So
  `KlangWorkbench.Tool`, `Tab` and `Bottom` each get an optional label callback, for the key in the strip and the
  tab in the panel's header. Without one, the workbench draws the title and the icon as today, so most parts stay
  one line. The Console's tab draws its own red pill from the log; the tab itself never changes, and
  `AppWorkbench` is a constant list. `count` and `chip` go from the data classes.
- **What stays data.** The `id` (the kept layout and `showBottom` find a part by it). The `title` as a plain name:
  the tool window's header, the tooltip and the `aria-label` use it, and specs find a tab by it. The walkthrough's
  `beaconStep`: the workbench draws the beacon around whatever the label is.
- **"Open at the Console while errors wait"** (`Bottom.count` with `countTab` today) is behaviour, not drawing: the
  layout tells the panel which tab its key opens, or none.
- **The left column: one rule, in the layout.** The page's column, else the area's default, else the navigation
  menu. The area's default is one exhaustive `when (area)` in `WorkbenchLayout` (Learn: its heading and
  `LearnTracksColumn`); `SidebarMenu` keeps only the navigation menu. Read from the route as today, so every page
  of Learn gets the column without registering, and one instance lives across them.
- **`PageWorkbench` stays as it is.**

### Considered and dropped (2026-11-10)

- **Parts owned by a `WorkbenchOwner`, a page removing only its own.** It pays only when several things register
  side by side below a page; with one page at a time, today's guard is already right. It comes back when a real
  case asks for it (the help panel or the editor registering its own parts).
- **The app's parts registered into the slots.** They would be registered again with every change of a count; a
  part that draws its own label needs no registering at all.
- **Learn's pages registering the tracks column themselves.** Three pages to touch (the list, a track, the
  resources), a new page can forget it without anyone noticing, and the column could be built again between pages.
- **A sort order per part.** It becomes a numbering kept across files.
- **An area scope for tool windows and tabs.** No case yet.

## Decisions

- **D1, the left side. Decided** (the maintainer, 2026-11-10): the navigation menu by default, and a page can
  replace it with its own column, as the song page does. Amended the same day: an area can set another default
  (Learn), decided in the layout, not by its pages.
- **D2, a page cannot hide the app's parts. Decided** by the shape: the layout always adds the app's.
- **D3, the order. Decided** (2026-11-10): as today and as written, no sort order per part.
- **D4, ids.** Proposed: a spec checks that the app's ids are unique; a page part with an app part's id is a bug,
  logged once, the app's kept.
- **D5, showing a part by a key.** Today `showBottom(tabId)` (Alt+Q) and `showTool(id)`. Proposed: one `show(id)`
  for either, so any part can have a key or a link that opens it.

## The work

1. The label callbacks on `KlangWorkbench.Tool`, `Tab` and `Bottom` in `klangui`, with the default drawing; `count`
   and `chip` go; the key's "open at this tab". `KlangWorkbenchSpec` follows.
2. `AppWorkbench`: the Motor tool window, the three tabs and the panel's title and icon move in; the Console's tab
   and the panel's key draw the unseen errors themselves; `BottomTools` goes.
3. The left column's rule in `WorkbenchLayout`; `SidebarMenu` keeps only the menu. `LearnPagesSpec` keeps passing.
4. The ids' spec (D4) and `show(id)` (D5), once decided.

## History

- 2026-11-05: created from the maintainer's question, after KLANG-00410 to 00412.
- 2026-11-10: D1 decided: the navigation menu by default, a page replaces it with its own column.
- 2026-11-10: a mechanism with owners per mount agreed, then reviewed: the app's parts change (the Console's
  count), so they cannot be registered once, and with one page at a time owners guard nothing new.
- 2026-11-10: rewritten to the simplest shape: `AppWorkbench`, parts that draw their own label, the left column's
  rule in the layout. Owners dropped.
