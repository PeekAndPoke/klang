# KLANG-00413: The workbench's parts from three places: the app, an area, a page, on the left, the right and the bottom

> **Status (2026-11-10): IDEA.** The mechanism agreed with the maintainer; D2 and D5 still open, D4 in part.

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

Discussed with the maintainer on 2026-11-10; the mechanism and D1 to D3 are agreed, D4 and D5 are proposals.

- **Two scopes.** *The app*, on every page: one file, `AppWorkbench`, with the Motor tool window and the Visuals,
  MIDI and Console tabs; `BottomTools` and the inline Motor tool fold into it. *A page*: `PageWorkbench`, as today.
  No area scope: its only case, Learn's tracks column, becomes a page's column (D1). One is added when a real case
  asks for it.
- **Every part has an owner.** In `layouts/WorkbenchSlots`, beside the three slots:

  ```kotlin
  /** Who put a part into the workbench: the app, or one mount of a page. Compared by identity. */
  class WorkbenchOwner private constructor(val scope: Scope, val label: String) {
      enum class Scope { App, Page }

      companion object {
          val app = WorkbenchOwner(scope = Scope.App, label = "app")

          /** A new owner for each mount; the label (the page's uri) is only for logs. */
          fun page(label: String) = WorkbenchOwner(scope = Scope.Page, label = label)
      }
  }

  class WorkbenchPart<T>(val owner: WorkbenchOwner, val part: T)

  val column     = StreamSource<List<WorkbenchPart<DIV.() -> Unit>>>(emptyList())
  val tools      = StreamSource<List<WorkbenchPart<KlangWorkbench.Tool>>>(emptyList())
  val bottomTabs = StreamSource<List<WorkbenchPart<KlangWorkbench.Tab>>>(emptyList())

  fun removeAll(owner: WorkbenchOwner) { /* each slot, filterNot { it.owner === owner } */ }
  ```

  The name says it is about the workbench: songs and users will have owners of their own once the server comes.
- **An owner per mount, not the uri.** Two live instances of a page can share a uri (a page mounted again under the
  same address); removing by uri would take the newer one's parts. The uri is only the label.
- **A page adds and removes only its own.** `PageWorkbench` makes a `WorkbenchOwner.page(uri)` on mount, adds its
  parts, and calls `removeAll(owner)` on unmount, in whatever order the page change brings them. The whole-list
  `===` checks of today go. The `[inert]` guard stays: the copy fading out registers nothing.
- **The layout resolves; `KlangWorkbench` stays unaware.** `WorkbenchLayout` turns the owned lists into plain
  `List<Tool>` and `List<Tab>` (D3, D4) and passes them on as today; `klangui` never learns about owners.
- **The left side** uses the same shape: the newest column a page gave, else the navigation menu (D1).

## Decisions

- **D1, the left side. Decided** (the maintainer, 2026-11-10): "it shows the navigation menu by default and a page
  can register another view to replace it, like the CodeSongPage does." One column, no area scope on the left:
  `SidebarMenu` keeps only the app map, and Learn's pages without a column of their own (the tutorials list, the
  resources page) register `LearnTracksColumn` with `column { }`, as `TutorialPage` registers its own.
- **D2, a page cannot hide the app's parts.** Proposed: no hiding. A page part with an app part's id is a bug: logged,
  the app's kept. The app's parts are on every page, as the maintainer asked for the bottom tools ("always present",
  KLANG-00409).
- **D3, the order. Decided** (2026-11-10): by scope, then as written. On the right the app's first, then the page's;
  at the bottom the page's first, then the app's (the most specific next to where you work). Within a scope, the
  order the owner lists them in. No sort order per tool: it would become a numbering kept across files; it comes
  when a page needs a part between two of the app's.
- **D4, ids. Decided in part** (2026-11-10): two page parts with one id, the newest wins (the page just opened). The
  kept layout remembers tool windows and tabs by id, so the app's ids stay unique: a spec checks them.
- **D5, showing a part by a key.** Today only the bottom panel (`showBottom(tabId)`, Alt+Q). Proposed: one
  `show(id)` for a tool window or a tab, so any part can have a key or a link that opens it.

## The work (once decided)

1. `WorkbenchOwner` and `WorkbenchPart` in `WorkbenchSlots`, with `removeAll`; the layout's resolving (D3, D4),
   with specs: a page change in either order keeps the new page's parts, the newest wins, the order by scope.
2. `PageWorkbench` on owners.
3. `AppWorkbench`: the Motor tool window and the three tabs move in; `BottomTools` goes.
4. The left side (D1): Learn's pages register `LearnTracksColumn`; `SidebarMenu` keeps only the app map.
5. The ids' spec (D4) and `show(id)` (D5).

## History

- 2026-11-05: created from the maintainer's question, after KLANG-00410 to 00412.
- 2026-11-10: D1 decided: the navigation menu by default, a page replaces it with its own column.
- 2026-11-10: the mechanism agreed: parts owned by a `WorkbenchOwner` per mount, newest wins, order by scope; no
  area scope; D3 decided, D4 in part.
