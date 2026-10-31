# KLANG-00400: The Looks page opens on the look last selected, the Motor menu's plus just goes there

> **Status (2026-10-31): NOT SCHEDULED.** Decided with the maintainer; parked for KLANG-00399 (the timing sync).
> Builds on the Looks page's uncommitted drag-and-drop-only rework (KLANG-00396 step 9), which lands first or with it.

## Why this exists

The "+" on an empty slot of the look picker at the foot of the Motor menu opens the Looks page with that slot
"waiting" (`Nav.looksSlot`, `LooksPage.Props.slot`, `target`): the slot gets a dashed focus border, and the next look
clicked on the bench is put there. The waiting cannot be cancelled, and a click that elsewhere only selects equips
here. Found in the review of KLANG-00396 step 9.

The maintainer (2026-10-31): "Just go to the page. Do not select the empty slot." And: "If we want to store the last
selected theme in the storage then we show that one as selected. If there is none we show the currently active theme
as selected. One theme always has to be selected so the users knows what they are working on."

## Decide before implementing

- Nothing: decided above. The old `/looks/slot/{slot}` links may simply open the Looks page.

## The work

1. The waiting slot goes: `target`, `Props.slot`, the waiting hint and border, the bench click that fills a slot.
   The picker's "+" (`WorkbenchLayout`, `KlangLookPicker.onEdit`) navigates to the Looks page, no slot.
2. The look last selected on the Looks page is stored (a small storage in `src/jsMain/kotlin/storage/`, like the
   others there). Opening the page selects it; none stored, or a look deleted since, selects the look worn. A look
   is always selected.
3. Optional (the maintainer, 2026-10-31): after the page's "+", the hint line says "A new look, copied from '...'"
   until the next action. No toast: the app has none, and the hint line is the page's quiet place for such words.
4. Specs: the page opens on the stored look; on the worn one without one or with a deleted id; the "+" in the
   picker leaves no slot waiting.

## Acceptance

- The Motor menu's "+" opens the Looks page with the look last selected (else the worn one) selected, no slot waiting.
- A click on a look only selects it, everywhere on the page.

## History

- 2026-10-31: created from the maintainer's decision (Klang-Research).
