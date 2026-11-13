# KLANG-00444: The look editor keeps a change at once: no Apply, Reset back to how the look was when the page opened

> **Status (2026-11-13): IN PROGRESS.** Built; in review.

## Why this exists

The maintainer (2026-11-13): "In the theme editor ... i think the apply button is not needed. We automatically
apply and reset goes back to the state of the theme as it was when the page was opened. Reset is only clickable when
something on the theme has changed."

Until now the look editor held a draft beside the look: a change showed at once but reached the look only with
**Apply**. The drafts lived in a private global of the page (`Drafts`), were lost with the tab, and so the browser
asked before a reload while one was pending. That was KLANG-00422's open point D2.

## The work

- **A change is kept in the look at once** (`LooksPage.keep`): the look on the shelf, and worn where it is the look
  worn. `Apply`, the `Drafts` object and its `beforeunload` question are gone. This settles KLANG-00422's D2: there
  are no drafts to store.
- **Reset goes back to how the look was when this visit of the page first showed it** (`LookBaselines`, per look,
  for the page's life), and can be pressed only while the look differs from that. A look made during the visit (a
  duplicate, a pasted one) is remembered as it was made. A role's own undo beside its label goes back to the same.
- **The line under the name** counts the changes since the page was opened ("3 changes since you came, kept as you
  go"); it is no warning any more.
- **Wear it** only wears; it had applied the draft first.

## What follows from it

- Leaving the page makes the changes final: the baseline lives with the page. A duplicate made before is the way to
  keep the old state.
- Editing the look that is worn changes the app for good as one goes; editing another look still shows only while
  the page is open.
- Every move of a knob writes the user's looks to the browser's storage. Not measured as a problem.
- The name is kept cleaned (`KlangLookText.cleanName`: trimmed, "Mine" when empty) while the field shows what was
  typed.

## Not tested

- `LooksPage` has no spec: the wiring (`keep`, `reset`, the key row) is read, not run. `LookBaselines` has its own.

## Acceptance

- The maintainer finds the editor right without Apply.
- `LookBaselinesSpec` green and mutation-checked.

## History

- 2026-11-13: created.
- 2026-11-13: built: `LooksPage` keeps a change at once, `LookBaselines` for Reset, `Drafts` and Apply gone. Review
  round 1 (reviewer-opus-high): CLEAN, 3 MINOR (KLANG-00383, 00408 and 00389 still spoke of drafts; a spec clue) and
  1 of 2 NIT applied (`select` takes the look as the shelf holds it); the other, the worn look's values put on
  twice per edit, left. 4 of 4 mutants killed. Waiting for the maintainer's try.
