# KLANG-00383: The look editor's drag and its leave guard

> **Status (2026-10-28): SCHEDULED.** waits for Kraft: the drag (ULTRA-00265, the maintainer with Ultra, by Friday) and the leave guard (ULTRA-00266)
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

KLANG-00382 built the look editor (`LooksPage`) with two parts left for Kraft:

- **The drag.** The design: "drag a look onto a slot (a swap when it came from a slot), or onto the bench to unequip"
  (the maintainer, 2026-10-28: "Like equipping in a video game"). Kraft's dnd works with the mouse only, with inline
  highlight styles and a drop that does not know its source; its rework is ULTRA-00265, which the maintainer takes on
  with Ultra ("The DnD will need until friday").
- **Asking before leaving with a draft not applied.** Kraft's router cannot hold a navigation away from a page: its
  middleware runs for the target route and can only proceed or redirect, and `BackNavigationTrap` hears only Back.
  The request is ULTRA-00266 (`guardLeaving { target -> Proceed | Stop | Ask }`, scoped to the component, with an
  optional `beforeunload`). Meanwhile the editor keeps a draft when it is left (`Drafts` in `LooksPage.kt`) and the
  browser asks before a reload or a closed tab loses one.

## Decide before implementing

1. With the guard: still keep drafts on leaving, or ask and drop them? Keeping loses nothing; asking is what the design
   said first. Perhaps both: ask, with "Keep the draft" as the default answer.

## The work

1. **The drag**, on Kraft's reworked dnd: a look onto a slot (swap when it came from a slot), onto the bench to
   unequip; the drop target lit while a look hovers it, in the focus blue as "in hand" is. Pick and place stays, the
   keyboard's and the touch screen's way. Lands in `LooksPage.renderEquipped`, `renderBench`, through
   `KlangLookShelf.place` and `unequip`, which already do the moves.
2. **The leave guard**, on ULTRA-00266: per the decision above; the `beforeunload` listener in `Drafts` goes over to
   the guard's own flag.

## Acceptance

- A look dragged from the bench onto a slot is equipped there, the look there goes to the bench; dragged between two
  slots, they swap; dragged onto the bench, its slot is empty. The drawer shows the result at once.
- Pick and place works as before.
- Leaving the editor with a draft not applied does what the decision says, for a link, the drawer, and Back.

## History

- 2026-10-28: created.
