# KLANG-00340: UI workbench, step 4: the shell (Motor column, tool strip, tool windows, bottom panel)

> **Status (2026-10-25): IN PROGRESS.** Step 4 of the UI workbench, the maintainer's go 2026-10-25
>
> **Part of:** KLANG-00334 (UI workbench)

## Why this exists

Layout A of KLANG-00334, assembled from the parts of steps 1 to 3 (KLANG-00337, KLANG-00338, KLANG-00339): the Motor
in the bottom left, the column above it scrolling on its own (item 25), the tool strip on the right with KLANGMOTOR
written up its foot (items 22, 23), tool windows flying in over the content or pinned beside it (item 9), the bottom
panel (item 9), the Motor button opening the drawer with the destinations and the looks (items 2, 33 to 35).

## Decide before implementing

- **A lab first.** The shell is a component, `KlangWorkbench`, shown on `/debug/workbench`; the app's pages keep today's
  layouts. Once the maintainer has tried it, the next step makes it the router layout for the app's pages, so one
  Motor lives across them (item 25: the Motor first-class in the layout); a page then hands the shell its parts (the
  column's content, its tool windows) instead of passing them as here.
- **The Motor without its title** (item 23): the Motor gets `showTitle`; without it the frame is shorter. Its
  warehouse stats, shown today by a click on the title, move into a tool window ("Motor").
- **The menu button in the fifth spot** (item 2): `PlayerMiniStats` draws it where the empty place is today, when a
  caller gives it a click; the volume knob's size, so the heat gauge stays in the middle. Pressed while the drawer
  is open, no lamp (item 34).
- **The Motor stays today's** (gauges, scope, spectrum): switching its parts to `KlangGauge` and `KlangKnob`, the blue
  trace and more spectrum columns (item 36) is a step of its own.
- **Gold is "try me", selected is blue**; the strip's open tool is pressed.

## The work

1. `Motor(showTitle, menu)`; `PlayerMiniStats(menu)`: the menu button (`KlangMenuButton`, a small domed round
   button with the nine dots), drawn when a `KlangWorkbench.Menu` is given.
2. `KlangIconToggle` (klangui): the strip's small icon button, off, on (pressed), with its name for screen readers.
3. `KlangWorkbench` (klangui, `comp/`, so it has browser tests; the app fills its slots): the column (its content
   scrolls, the Motor below it never moves), the content, the bottom panel (shown or hidden, docked), the strip (one
   toggle per tool window, the bottom panel's toggle, the rotated KLANGMOTOR at its foot), the open tool window
   (flying in over the content, or pinned beside it, each with its own pin), the drawer above the Motor (destinations from the app's routes, the look picker). The drawer takes the focus when it
   opens, Esc closes it, the focus goes back to the menu button.
4. `/debug/workbench`: the shell with sample parts (a project list, a content page, Docs, Keys and Motor tool
   windows, an analysis panel), linked from the side menu beside the kitchen sink.
5. Specs; the review loop; mutation checks (light tier).

## Acceptance

- On `/debug/workbench` the Motor sits bottom left and never moves; the column above it scrolls; tool windows open
  from the strip, fly in or pin; the bottom panel toggles; the Motor button opens the drawer, whose destinations
  navigate and whose looks change and keep the look.
- Specs green and mutation-checked; the review loop ends on a clean round.

## Review and checks (2026-10-25)

- **Round 1** (level 1, blind): 1 MAJOR, 9 more, all fixed. The MAJOR: Esc closed the drawer only with the focus
  already in it, and nothing put it there; closing dropped the focus to the body. Now the drawer takes the focus when
  it opens and gives it back when it closes (ledger row, the lesson in `/kraft-knowhow`). The others: the Motor gets a
  menu only with a drawer, each tool window keeps its own pin, a tool leaving the list closes, the drawer scrolls on
  a short window, the strip is no nav landmark, the menu button's size is a parameter with its own spec,
  `KlangIconToggle` split out and used for the pin, the work list matched to the code.
- **Round 2** (level 2, two phases): clean; every round 1 fix holds. Its MINORs and the NIT applied as one batch: the
  strip a labelled group (a toolbar promises arrow keys it does not have yet), the drawer bounded by the column
  instead of the window (it held only for a Motor without its title), the focus goes back only when it was lost
  with the drawer, a drawer leaving the props closes, a full focusable selector with the drawer itself as fallback,
  the component's name in this ticket.
- **Mutation-checked**, each killed for the right reason: the drawer keeps no focus, the focus not returned, the pin
  shared, a menu without a drawer, a stale tool reopening, the bottom panel never toggling, a tool window never
  closing, the menu button's size ignored, the toggle never on; from the batch: the focus taken back after a pick,
  the drawer staying open when it leaves, no fallback focus, the selector without links. The drawer-leaving row first
  SURVIVED: its `eventually` passed on the old DOM before the redraw; it now waits for a sign of the new render
  (the lesson in `/kraft-knowhow`).
- **Tests:** klangui's 104, green.
- **For the maintainer's eye** on `/debug/workbench`: the shorter spectrum of the Motor without its title, the menu
  button's alignment beside the gauges.

## History

- 2026-10-25: created and started.
- 2026-10-25: built, reviewed (two rounds, the second clean), mutation-checked, committed (8f535680).
- 2026-10-26: the maintainer on the menu button: "The button aligns well".
- 2026-10-26: the maintainer: the Motor without its title is too cramped, the gauges need room on top, the spectrum
  had none. Now 0.75rem above the gauges and a floor under the scope (the frame 11.5rem); the spectrum rises from the
  floor to the scope's top, as with the title. A layout constant judged by eye, so no review round. The maintainer:
  "Please continue with next steps"; the spectrum is judged with a song playing in the next step.
