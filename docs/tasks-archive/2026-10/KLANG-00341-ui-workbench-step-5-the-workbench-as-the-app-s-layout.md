# KLANG-00341: UI workbench, step 5: the workbench as the app's layout

> **Status (2026-10-26): DONE.** the maintainer: "Close the two tickets", 2026-10-26
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

Step 4 (KLANG-00340) built the shell in the lab (`/debug/workbench`). Now the app's pages move into it (KLANG-00334
item 25): one Motor in the corner across the pages, the column above it showing where you are, the Motor's drawer as
the one place for "where to go".

## Decide before implementing

- **The drawer replaces the icon bar** (the maintainer, 2026-10-26). The sidebar's icon bar (Songs, Tutorials, Docs,
  More) goes; the column shows the current area's list (the songs, the tutorial filters, the doc sections) under a
  small heading naming the area; areas change through the Motor's drawer.
- **All at once** (the maintainer, 2026-10-26). Every page `MenuLayout` mounts today moves into the workbench;
  `MenuLayout` and the lab route go. The start page, the tour and the not-found page keep their layouts for now (the
  Motor gliding from the start page into its corner is a later step).
- **The strip holds the Motor tool window** (the warehouse stats) for now. Docs and the param tools come with the
  tool windows step (KLANG-00334 work item 4); a Keys window waits for the key manager's overview
  (Klang-Main-Dev's, KLANG-00325). No bottom panel until it has content (the analysis of KLANG-00196).
- **The drawer's search field stays a field** until the search window step binds `klang.search-everywhere`.
- **One Motor across the pages, for real** (found in review round 1): the router drew the whole route, layouts
  included, in a block keyed by the path, and the page just left fading over it, layouts and all. So every page change
  mounted a new workbench and a new Motor (as it did with `MenuLayout`), and an open or pinned tool window closed. Now
  the router draws the layouts once while the route stays under the same ones; only the page inside is keyed by the
  path and fades. A route under other layouts (the start page, the tour) still changes and fades as a whole.
- **The Motor without a title for good:** the workbench writes KLANGMOTOR up its strip, so the title slot and its
  click to the warehouse stats go; the build tooltip it carried becomes the build lines in the Motor tool window.
- **The search field's `Mod K` hint goes** until the search window step makes the key do something.
- **Known look change:** `MenuLayout` let the editor's glow paint 100px over the sidebar (a clip-window trick) and lit
  the screen's left edge; the workbench's content area clips at its edge, and the edge light goes with the old layout.

## The work

1. `AppArea` (`layouts/`): the area a route belongs to (songs, learn, docs, samples, MIDI, resources, credits, dev
   status, other), one mapping for the column and the drawer's current entry.
2. `SidebarMenu` becomes the column: no icon bar, no Motor, the area from the route (no state of its own), a heading
   naming the area; the lab's menu item goes.
3. `WorkbenchLayout` (`layouts/`): `KlangWorkbench` with the column, `Motoer(menu)`, the page as the content, the
   Motor tool window (the build and the warehouse stats), the drawer (the destinations with the current one marked,
   the search field, the look picker), the mark.
4. `nav.kt`: the `MenuLayout` block mounts in `WorkbenchLayout`; `MenuLayout`, `WorkbenchLabPage` and
   `Nav.workbenchLab` go.
5. The pages that sized themselves to the window (`CodeSongPage`, `SamplesLibraryPage`: `100vh`) take the content's
   height, so they share it with a bottom panel later.
6. `RouterWithTransitions` keeps the layouts mounted while their pages change (the decision above).
7. The Motor loses its title path (its only caller draws it without); the build it showed moves into the Motor tool
   window.
8. The review loop (the app module has no browser tests; the klangui shell has), the app compiled, a look in the
   browser.

## Acceptance

- Every former `MenuLayout` page shows in the workbench: the Motor bottom left, the area's list above it, the page in
  the middle; the drawer's destinations reach every area and mark the current one.
- The song page's editor fills the content's height, its header stays put.
- Review clean; the app compiles.

## Review and checks (2026-10-26)

- **Round 1** (level 1, blind): clean, 3 MINOR, 2 NIT, all taken: the shared list of the smaller areas headed
  "More"; the remounting on every page change fixed in the router (the decision above); the Motor's dead title path
  removed, the build shown in the Motor tool window; two stale comments; the `Mod K` hint for a key nothing handles.
- **Round 2** (level 1, two phases, the router change as new code): 2 MAJOR, 1 MINOR, 3 NIT, all fixed. The MAJORs,
  both from the router change: the content's scroller kept its scroll across pages (a list scrolled down opened the
  next page at its bottom), now the scrollers around the page go up when the path changes; the new page wrapper
  shrank the pages of the flex-row layouts (not found, start, tour, kitchen sink) to their content, now it takes the
  full width (ledger row, the lesson in `/kraft-knowhow`). The MINOR: a page fading out painted over a floating tool
  window, now the page is its own stacking context. The NITs: a key per fading page, an unread field, an import.
- **Round 3** (level 2, two phases): clean; every earlier fix holds. Its NIT left as it is: a page fading out that
  switches between the inner and the outer layer (two navigations within 300 ms, across layouts) starts its fade
  again; a fix would add more than it saves.
- **Tests:** none new (the app module has no browser tests; the shell's behaviour is specced in klangui). The app
  compiles. For the maintainer's look: the Motor steady across pages, a pinned tool window staying, the song page's
  height, the start page's blur.

## History

- 2026-10-26: created; the maintainer chose the drawer over the icon bar and the switch-over all at once.
- 2026-10-26: round 1 (level 1, blind): clean, 3 MINOR, 2 NIT, all taken; the remounting behind its MINOR 2 fixed
  in the router, which is new code, so round 2 runs (one level down from a clean round, the floor).
- 2026-10-26: round 2 found two MAJORs in the router change, fixed; round 3 (level 2) clean. Committed.
- 2026-10-26: the maintainer's look: the Motor steady across pages, the pinned window stays, the editor fills the
  height. Asked for: the spectrum higher and stronger (now 1.25rem above the scope's top, opacity 0.85); Esc closes a
  tool window flying in (it takes the focus when opened from the strip, the focus goes back on Esc or its close
  button; a pinned one stays). Reviewed (one round at level 1, clean; its three MINORs applied: the close button gives
  the focus back, a pin change moves the focus with the window, the spec checks the pinned window's focus).
  Mutation-checked: no focus in, Esc ignored, the focus not back, a pinned window closing on Esc, the close button
  dropping the focus, no refocus after a pin, an unfocusable pinned frame. The batch's baseline went red on a real
  bug: unkeyed, the pinned window took over the strip's element when it appeared, so the remembered toggle was
  detached; the workbench's parts that come and go now carry keys (mutant: both keys removed, red).
- 2026-10-26: the maintainer: the scope still covered the interesting part of the spectrum; it moved down onto the
  spectrum's border from blue to green (the frame 13.25rem again). And with a tool window pinned the editor showed
  double scrollbars: the workbench's content area scrolled sideways where `MenuLayout`'s scroller had clipped
  (`overflow-x: hidden`, "no horizontal scrollbar from shadows poking the right edge"); it clips again. A new row
  guards that a wide page never widens the workbench (mutant: the centre without `min-width: 0`, red). No review
  round for this: it restores the old layout's own rule.
- 2026-10-26: the scope back up under the gauges, the spectrum on the frame's floor (all heights kept, the
  maintainer's idea). The maintainer: "Yes this is good".
- 2026-10-26: Esc and Alt+M through the key manager (KLANG-00334 items 39, 40), on Klang-Main-Dev's actions
  (82022580): `KlangCloseKey` answers `PanelClose` while a panel that closes on Esc is open and shows its pill
  "Close: Esc" (the tool window flying in, the Motor's drawer); Esc from inside a panel closes that panel through its
  own handler; Alt+M toggles the drawer, its hint on the menu button, the focus in the search field. The maintainer
  tried it: "Esc works for closing. Esc for jumping back into the editor also works. Alt+M also work." Reviewed in two
  rounds, both with a MAJOR in the guard against what sits in front without a key layer (first missing, then too
  broad: the hover docs), both fixed, the second round's reconcile confirming every fix; mutation-checked (14
  mutants); ledger rows and a `/kraft-knowhow` lesson. The safety valve stopped the loop after two rounds; the
  maintainer chose stacking instead (the newest registration on a key answers, declarative only), asked of
  Klang-Main-Dev; the guard stays until then as marked scaffolding.
- 2026-10-26: DONE. the maintainer: "Close the two tickets", 2026-10-26
