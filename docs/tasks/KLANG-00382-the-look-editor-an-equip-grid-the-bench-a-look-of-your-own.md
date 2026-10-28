# KLANG-00382: The look editor: an equip grid, the bench, a look of your own

> **Status (2026-10-28): NOT STARTED.** planned and designed; the design waits for the maintainer's look
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-28: "where could we introduce the themes? I would say its own tour, where the motor button
lights up again, then 2-3 themes and then the custom button. Mean we would need the theme editor first. Can you plan,
draft, design the theme editor?"; then "In the theme editor we should have a 4x2 grid here the buttons are displayed
and the user can drag and drop them arround, putting their own themes into the slots and moving the built-in themes to
the "bench". Like equipping in a video game."

The epic's items 26 and 27 (KLANG-00334): "Users can fully customize the colours, and someday share them." The looks
are data already (`KlangRole`, `KlangLookPreset`, a stack of layers in `KlangTheme`); the drawer's "Custom" waits
for this.

The design: the canvas "Klang Look Editor" (https://claude.ai/artifact/XXWyrY6jJZkrefbJPerCRR, private to the
maintainer), three artboards: the editor (interactive: pick and place in the grid works), the drawer's LOOK row once
equipped, the Looks walkthrough.

## The design in words

- **The equip grid:** 4x2 slots, what the Motor drawer's LOOK row shows, in that order. A slot holds a built-in look
  or one of the user's own, or nothing: in the drawer an empty slot is a quiet dashed "+" that opens the editor with
  that slot ready to be filled (the maintainer, 2026-10-28: "Clicking on an empty slot should also go to the theme
  editor").
- **The bench:** every look not equipped, built-in or own, and "New look". A look in a slot goes to the bench when
  another takes its place; a built-in can wait there too.
- **Moving:** drag a look onto a slot (a swap when it came from a slot), or onto the bench to unequip; or pick and
  place, as games equip: click a look (it is "in hand", a dashed ring in the focus blue, never the trail's gold), then
  click a slot or the bench. Pick and place is also the keyboard's and the touch screen's way.
- **Editing:** the look clicked last is shown on the right: its name, "Built-in, read only" or "Your look", and its
  roles in groups (grounds and inks, metal, lamps and accent, gauges and scope, the spectrum, texture). A built-in is
  read only: "Duplicate to edit". A role that follows another (`ScrollThumbHover` follows `Accent`) shows so, dimmed,
  until it is set.
- **Live:** the whole app takes the look being edited (a theme layer), and a preview of the Motor (gauges, the lamp,
  the scope, the spectrum) sits beside the roles.
- **Share:** "Copy as text" and "Paste a look": a name, an author and the roles' values by their CSS names, checked
  role by role on the way in (`KlangRole.accepts`); never CSS text, never a URL.
- **The drawer:** the LOOK row shows the eight equipped; "Custom" goes, and a small "Edit" key (a pencil) at the
  right of the LOOK heading opens the editor, so the drawer grows by nothing (the maintainer, 2026-10-28: "we have to
  be careful with the Motor menu it has already grown vertically quite a bit"); not a button but in the code players'
  copy key's style, a pencil and a word in the label's ink, brighter on hover ("like the "copy to clipboard" style").

## Decide before implementing

1. Where the editor lives: a page in the workbench's content area (`/looks`, recommended: it needs the room) or a tool
   window.
2. Which roles it shows: all of them, grouped (recommended), the following ones folded under "More"; or a chosen few.
3. A colour's control: the browser's colour input and a hex field (recommended for now, no alpha), or a picker of our
   own (a later step).
4. Where the looks are kept: the browser for now, as the chosen look is (recommended); the backend later.
5. The drag: Kraft's dnd works with the mouse only, without keys, with inline highlight styles and a drop that does not
   know its source; the requests are ULTRA-00265 (not scheduled; Ultra offers to let the rework grow in `workshop/`
   first). Recommended: pick and place first, Klang's own (it is also the keyboard's and touch's way); the drag after,
   on Kraft's dnd as it is or on its rework.
6. A look's name: how long it may be, so it fits a drawer tile (about 80 px at 11 px). The maintainer, 2026-10-28:
   "We should restict the length of the name to 8 chars?", then "Not sure we will see". Options: 8 characters, or
   10 with a counter; either way the tile cuts a name that still overflows with "...", and the editor and the tile's
   tooltip show it whole (a character count cannot promise a width: "WWWWWWWW" is wider than "Asphalt").

## The work

1. **Data:** a user's own look (`KlangLookPreset` with an id, a name, an author, `own = true`); the loadout (8 slots of
   look ids or empty) and the own looks, kept in the browser; the drawer reads the loadout. A spec for a look round
   trip (copy as text, paste) and its role-by-role check.
2. **The editor page:** the layout of the design; the roles in groups, a role's control by its kind (colour, length,
   amount); "Duplicate to edit", rename, delete (a yes/no question); the look being edited as a live theme layer.
3. **The equip grid:** pick and place, by mouse, keys and touch; then the drag.
4. **Share:** copy as text, paste a look.
5. **The Looks walkthrough** (Gui-dance, KLANG-00350), after the editor exists: the Motor button, then Copper, Sun and
   Asphalt lit in turn in the drawer, then the "Edit" key; a minute's rest before it, as after every walkthrough.

## Acceptance

Not set yet.

## History

- 2026-10-28: created.
