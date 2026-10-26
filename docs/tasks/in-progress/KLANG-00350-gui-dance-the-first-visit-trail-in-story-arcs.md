# KLANG-00350: Gui-dance: the first-visit trail in story arcs

> **Status (2026-10-26): IN PROGRESS.** started with the maintainer
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-26: "Can we work on the User Guidance? ... Funny Gui-dance ... a dance through the gui. We
need to somehow define an order and then also remember which action was already done. The play button on the code
Song page is the first item. Then the motor button. Then immediately the "Songs" button. We might also need to
possibility to highlight "Motor" button again to guide somewhere else... so we need "story arcs" and only when you
completed an arch, we will store that one as completed. So when you somehow close the page in the middle you will
start over when you come back."

It is the epic's first-visit trail (KLANG-00334, canvas board E and items 10 and 12): one gold "try me" lamp at a
time, lit by context, retired by one use; elements not used yet stay quiet, so new people explore the UI by
themselves.

## Decided

- **Story arcs:** an arc is a named, ordered list of steps; a step is the id of a thing to use ("song.play"). Arcs
  run one after the other, in their order.
- **One lamp:** only the current step of the current arc glows, with the existing `beacon` (gold means "try me" and
  nothing else). Using it moves the arc on; using something out of order counts for nothing.
- **Remembered per arc:** an arc is stored as done (in the browser) only when its last step is used. A page left in
  the middle of an arc starts that arc over on the next visit.
- **Steps are ids, not places:** a later arc can light the Motor button again.
- **Testable** (epic item 12): the machine is plain Kotlin, no UI in it, specced on its own; the components only ask
  "am I lit" and report "I was used".
- **The first arc:** Play on the song page, the Motor button, "Songs" in the drawer.
- **Where it lives:** the machine (`GuiDance`) in klangui, as UI machinery; the arcs and their store
  (`KlangGuiDance`) in the app.

## Decide before implementing

- Later arcs: what they show (Docs, the param tool, Analysis, from the epic's order).

## The work

1. The machine (`GuiDance`): arcs, the lit step, "used", the done arcs stored; its spec.
2. The beacon on the Motor button and on a drawer entry.
3. The first arc wired: the song page's Play, the Motor button, the drawer's Songs.
4. The review loop; the maintainer's look.

## Review and checks (2026-10-26)

- **The machine and the first arc, round 1** (level 2, with the start page's and the song page's open work):
  clean; 3 MINOR applied (the trail's credit, John M. Carroll's training wheels and minimalist instruction, in
  `CREDITS.MD` and on the Credits page; and two of the other work's), 2 NIT applied (`isLit` dropped, unused; this
  ticket's "where it lives" decided).
- **Mutation-checked:** 6 mutants over the machine (any step counting, never kept, kept halfway, no restart, the arcs
  out of order, the lit step not published) and 2 over the beacons (the Motor button's while open, the drawer's
  ignored), all killed for the right reason.

## Acceptance

- On a first visit Play glows; after Play the Motor button; after it Songs in the drawer; then nothing.
- Leaving in the middle starts the arc over; a finished arc does not come back.
- The machine's spec green and mutation-checked; the review loop ends clean.

## Credits

The trail builds on John M. Carroll's training-wheels interfaces and minimalist instruction: credited with the
machine and the first arc (2026-10-26) in `CREDITS.MD` and on the Credits page, *"Training Wheels in a User
Interface"* (Carroll and Carrithers, Communications of the ACM, 1984) and *The Nurnberg Funnel* (Carroll, MIT Press,
1990).

## History

- 2026-10-26: created.
- 2026-10-26: the design with the maintainer: story arcs, one lamp, an arc stored only when finished.
