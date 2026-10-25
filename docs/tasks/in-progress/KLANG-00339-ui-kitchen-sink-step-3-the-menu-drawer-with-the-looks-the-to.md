# KLANG-00339: UI kitchen sink, step 3: the menu drawer with the looks, the tool window, the context menu

> **Status (2026-10-25): IN PROGRESS.** Step 3 of the UI workbench, the maintainer's go 2026-10-25
>
> **Part of:** KLANG-00334 (UI workbench)

## Why this exists

Step 3 of the UI workbench after KLANG-00337 (roles, lamp, button, riveted panel) and KLANG-00338 (gauge, knob). The
popups and windows of layout A: the Motor button's drawer with the looks (KLANG-00334 items 2, 33, 34, 35), the tool
window that flies in or is pinned (item 9), the context menu with rivets (item 11). The canvas: board "Part: Menu
drawer", screen A's tool window, the kitchen sink's popups.

## Decide before implementing

- **Built from parts, not drawn again.** The drawer is a `KlangPanel`; its entries and the look buttons are
  `KlangButton`s with a new tall shape (`Tile`); the swatch button wraps a tile. Nothing new paints a cap.
- **The look is chosen in the drawer and remembered in the browser** (a new storage key `klang-look`, the preset's
  id); the three sample looks of the kitchen sink move into `klangui` as the built-in presets.
- **Selected is blue, gold is "try me"** (items 34, 35): an active entry's lamp is the "on" lamp.
- **Inline on the page in this step.** Positioning (the drawer above the Motor, the menu at the pointer, the window
  over the editor) comes with the shell; here each part renders where it is placed.
- **Keys:** the menu takes the arrow keys and Esc as a menu must (ARIA). The app's own keys (Mod K, Alt 1) are for
  Klang-Main-Dev's key manager once the parts are settled.

## The work

1. `KlangLooks` (`klangui/feel/`): the built-in presets (Default, Copper, Cream: an id, a name, the role values, the
   swatch colours); `KlangTheme.choose(preset)` puts one on the user's layer. `LookStorage` (app) keeps the choice;
   `main()` restores it.
2. `KlangButton.Shape.Tile`: a tall button, its lamp at the top left.
3. `KlangSwatchButton`: a tile with a colour swatch and a name. `KlangLookPicker`: the LOOK row (the presets as
   swatch buttons, the active one pressed, "Custom" later).
4. `KlangSearchField`: a sunken field with a search icon and a key cap hint.
5. `KlangDrawer`: the riveted panel with its title and close button, an optional search field, the destinations as
   tiles (label, sub line, lamp, "later"), and a footer slot (the look picker).
6. `KlangMenu`: a riveted `menu` of `menuitem`s with key hints, arrow keys between them, Esc to close.
7. `KlangToolWindow`: the header (title, chip, pin, close) and the content; flying in (a shadow, the fly-in) or pinned.
8. `KlangCard` (a flat plate, no rivets: rivets mean a popup) and `KlangCaption` (a title and a small note). The
   maintainer, 2026-10-25: the kitchen sink's cells should be such a component, not drawn by the page. They serve
   later for the tutorial and sample cards (today `ui.card`).
9. The page: a section for each, every cell a card with its caption, the sample looks replaced by the look picker.
10. Specs for each part; the review loop; mutation checks (light tier).

## The knob's ring after a click (2026-10-25)

The maintainer found that a knob clicked after a button shows no ring, while one clicked first does. The cause is the
browser's `:focus-visible` rule (Chrome and Chromium alike): the knob focuses itself with `focus()` on pointerdown
(its `preventDefault` stops the browser's own focus-on-click, so a drag selects no text), and a focus from code
inherits the previous element's judgement. First the ring was shown once a key turned the knob (a `keyed` state);
then the maintainer chose the plainer way: the knob's ring is drawn on any `:focus`, a focused knob being the one the
arrows turn. No state of the knob's own; the browser's focus is the only truth. The maintainer: "Yes focus now
works."

## Acceptance

- The page shows the drawer (with the look picker that changes the look and keeps it over a reload), the menu, the
  tool window in both modes, the swatch buttons and the search field.
- The menu is usable by keyboard; every part is announced as what it is.
- Specs green and mutation-checked; the review loop ends on a clean round.

## Review and checks (2026-10-25)

- **Round 1** (level 1, blind): 1 MAJOR, 5 MINOR. The MAJOR: the menu could not be reached by Tab (every item at
  `tabindex=-1`); fixed with a roving tab stop, Tab closes the menu (ledger row, the lesson in `/kraft-knowhow`).
  MINORs fixed: the close icon off centre (Fomantic's icon margin), the page's headings shrunk, no reduce-motion
  guard for the fly-in, the unused `width` of drawer and menu, `data-pinned` read only by a spec.
- **The maintainer's change between the rounds:** the knob's ring on any `:focus` (see above).
- **Round 2** (level 2, two phases): clean; every round 1 fix holds. Its MINORs and NITs applied as one batch: a
  spec for the tab stop whose item turns unavailable, a duplicate knob spec removed, a test name and a comment
  corrected, the empty wrappers of drawer and menu removed.
- **Mutation-checked**, each killed for the right reason: `chosen` always the first preset, the swatch unchecked,
  the drawer's current lamp off, its later entries enabled, the menu's arrows onto a disabled item, Tab staying open,
  no tab stop, the tab stop unguarded, the window never floating, the picker never pressed, `current` unannounced,
  the fly-in's stilling rule with one class, the search input lost, the knob's ring on `:focus-visible` only.
- **Tests:** klangui's 88 (the 17 specs of steps 1 to 3 among them) and StorageKeysSpec, green; the app compiles.

## History

- 2026-10-25: created and started.
- 2026-10-25: built, reviewed (two rounds, the second clean), mutation-checked, committed (46de2977).
- 2026-10-25: `KlangDialog` added at the maintainer's request (the yes/no popup's buttons sat oddly): a key row across
  the foot, edge to edge, verbs on the keys, the keys bound through the key manager (printed caps were tried and
  rejected), only the top pair of rivets (at the foot, and lifted over the key row, they looked off).
  `KlangPanel(bottomRivets)`. Reviewed (one round, clean; its MINORs and NITs applied: long verbs wrap in a key, the
  dialog named by its title and described by its text, the strip's corner, the KDoc on key order and key layers),
  mutation-checked (the main key not silver, the keys unbound, the dialog named by its text). `YesNoModal` moves onto
  it later (Klang-Main-Dev's).
