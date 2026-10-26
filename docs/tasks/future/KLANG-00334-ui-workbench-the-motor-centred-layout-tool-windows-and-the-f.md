# KLANG-00334: UI workbench: the Motor-centred layout, tool windows and the first-visit trail

> **Status (2026-10-25): IDEA.** Design conversation with the maintainer, 2026-10-25; canvas https://claude.ai/artifact/Gvk4hUJkSWiZfphMrCrnXT

The maintainer is digesting the canvas. Nothing here is decided yet: the log below collects the maintainer's
thinking aloud, the leanings are where the conversation stands, and the decisions come later.

## Why this exists

Prepare the UI for a backend without making it look like "a lame SPA with menu and pages". Everything is up for
debate; the one fixed point is the Motor component, which stays in the bottom left of the page.

## The maintainer's thoughts (thinking aloud, not commands)

Kept in order, close to the maintainer's words.

1. (2026-10-25) "I do not want it to look like a lame spa with menu and pages ... we need something more fresh."
2. (2026-10-25) The PlayerMiniStats have one spot left (the empty place right of the gauges, there only to keep the
   middle gauge centred). It might be a button that opens a nice menu to navigate.
3. (2026-10-25) Open: what goes in the left column, do we keep it, could the help popups show there instead of over
   the code, could the param tools live there, or is it a project and file view for the day a song has several files.
4. (2026-10-25) Tool windows as in modern IDEs: where would they live? A good home for the docs and the param tools.
5. (2026-10-25) "I would center it around the Motor Component that stays in the bottom left of the page."
6. (2026-10-25) Likes layout A of the canvas (the workbench, the tool strip on the right).
7. (2026-10-25) This needs a full design round for all UI elements, for a consistent feel.
8. (2026-10-25) Open: does the Motor button carry song things only (with the tool strip on the right), or both,
   maybe through tabs. Not sure.
9. (2026-10-25) Likes the tool window at the bottom and the docs view on the right, but the right one should not
   shrink the editor: a fly-in from the right.
10. (2026-10-25) Introducing the UI to new people: elements not yet used make "visual noise", sequenced so that not
    too many things fight for attention at once. First the play button, then the Motor button (leading to another
    song, the built-in ones for now). "If we do this in a clever way the people will explore the ui by themselves."
11. (2026-10-25) Likes the little rivets in the corners of the Motor button's drawer: every popup should have them.
12. (2026-10-25) The trail means writing the code so that it can be tested by unit tests.
13. (2026-10-25) Something similar for the tutorials: unlock new tutorials like a tech tree in a game.
14. (2026-10-25) Asked for a kitchen sink of all UI elements (canvas board "Kitchen sink"), and for the canvas to
    live in the repo too (`docs/design/2026-10-25-ui-workbench-canvas/`).
15. (2026-10-25) "Out there": Ignitor parts and instruments shown in 3D, rotatable like players in a video game.
    Its own ticket: KLANG-00335 (Ignitor parts and instruments in 3D).
16. (2026-10-25) The Motor's gauges deserve their own section in the kitchen sink and should be able to carry icons.
17. (2026-10-25) A turnable knob like the volume knob might come in variants: with a label (top or bottom) or an icon.
18. (2026-10-25) Likes most things having a somewhat "roundish" surface, like the volume knob or the Motor button.
19. (2026-10-25) Should the white buttons get a silverish colour instead? They stick out now.
20. (2026-10-25) How would other pages look in this design, the tutorials page for example: menu, content?
21. (2026-10-25) To make it a nice visual experience the start page needs something, not sure what.
22. (2026-10-25) Asked to try, on one screen, KLANGMOTOR removed from the bottom left and written rotated at the
    bottom of the right strip (canvas board A2).
23. (2026-10-25) Likes KLANGMOTOR rotated in the strip (it needed padding at the bottom): it makes the Motor more
    compact without the text, "also less marketingish which is good".
24. (2026-10-25) To start: a `/debug/ui-kitchen-sink` page that demos all the elements, each element its own Kraft
    component. Asked: how do we keep the design canvas and the code aligned over time, and how do settings reach a
    component: flat props, or an options builder in the more complicated cases?
25. (2026-10-25) The Motor moving from the start page into its corner means the Motor lives first-class in the
    layout, not on single pages; the area above it always keeps its space free at the bottom so nothing overlaps,
    and the left panel scrolls when its content is too tall.
26. (2026-10-25) Every element gets its representation in the theme. Suggests a completely new set of variables
    ("but we can also do otherwise").
27. (2026-10-25) Users can fully customize the colours, and someday share them. "Imagine someone making a youtube
    video of this with their own look ... pretty neat!"
28. (2026-10-25) A song's own look: not for now, but it could simply be a theme override later, "or maybe prepare
    it already".
29. (2026-10-25) In the very long run: another kind of script (also KlangScript) for visual coding, that changes
    how the UI looks when certain musical events happen. Future idea, to keep in mind while building the theme:
    KLANG-00336 (visual script).
30. (2026-10-25) Step 1 built (KLANG-00337): likes the kitchen sink page and the sample looks (Default, Copper,
    Cream on black). The names `--kl-` and `KlangLook` are fine.
31. (2026-10-25) Input from Klang-Main-Dev at the maintainer's request (informal, take what fits): where keys and
    their hints would help, by benefit: the code tools (Esc, Ctrl+Enter, Reset; they open a key layer), the playable
    examples (Play/Update, Stop, Reset, the focused one answers), the song page's controls, tutorials (next,
    previous, mark completed), search fields ("/" or Ctrl+K, Esc clears), navigation, the REPL. What a design can
    count on: an `OnKey` inside a button draws its hint on its own layer (no room needed beside it); a box carries a
    key in a corner (`InsideEnd`, as the yes/no modal's "Esc Close"); an area shows its keys in a corner while Ctrl
    is held. The full list: KLANG-00325, section "Places for keys and hints (survey 2026-10-25)". The rollout waits
    for this layout (maintainer).
32. (2026-10-25) "Maybe in a final step we can think about our own icon set as svgs ... not sure how hard this is on
    the browser to render many svgs." Notes: a line icon is a few paths; a sprite (`<symbol>` defined once, each use one
    `<use>`) costs one element per use; drawn in `currentColor` the icons follow the theme's ink (silver caps,
    copper, cream). What costs is SVG filters (blur) and animating many at once. Our own drawing, or an open set
    (Lucide is ISC) credited per the credits rule. Today's icons are Font Awesome's through Fomantic.
33. (2026-10-25) The Motor button's drawer shows a theme choice: "Default", "Copper", "Cream" and "Custom" (dashed,
    coming later). Plan: in the drawer's step, the three sample looks of the kitchen sink become built-in looks in
    `klangui`, the drawer gets a LOOK row (the active one pressed, its lamp on), and the choice is remembered in the
    browser (later in the account).
34. (2026-10-25) The drawer's look buttons read as a new button type (a domed tile with a swatch), liked. The Motor
    button loses its lamp: while the drawer is open it is pressed in and its dots lit (in the "on" colour, since gold
    means "try me" only); that is signal enough.
35. (2026-10-25) Selected is blue: "down here everything is nice and blue except for the gauges". The open Motor
    button's dots and the active drawer item's lamp are blue on the canvas; `KlangRole.LampOn` defaults to the accent
    blue (was green).
36. (2026-10-25) Likes the canvas's oscilloscope in blue (today white, `Oscilloscope.strokeColor`) and the spectrum
    with more columns (28 on the canvas, today 16, `Spectrumeter.numBuckets`). For the step that switches the Motor
    over: the trace's colour as a role, the column count raised.
37. (2026-10-25) Likes the "search everywhere" window (canvas board C). Opened maybe as in IntelliJ, Shift then Shift
    again, which needs a new feature in the key bindings. Proposed: Mod K as the plain chord (the key manager has it
    today), and Shift Shift as a second way in: a tap binding (a modifier pressed and released with no other key
    between; two within about 300 to 400 ms), simpler than general sequences. The key manager is Klang-Main-Dev's
    (KLANG-00325). Passed to Klang-Main-Dev on the maintainer's word (2026-10-25): "keys in a row", in parts:
    detecting them (the delay accepted between) and showing the shortcut; it designs and builds it there.
    Part (a) landed (f106dcef, Klang-Main-Dev's word): `KlangKeyBindings.SearchEverywhere` (`klang.search-everywhere`)
    fires on Mod K and on Shift tapped twice (each tap within 300 ms, the two within 400 ms; a click, a wheel turn or
    another key between cancels), in the top key layer only. Nothing handles it yet: the search window's step binds
    it with `keys.handle(...)` while its host is mounted, or `onKeyController(action = ...)`, which also gives the hint.
    Chord sequences (Ctrl K, then Ctrl S) follow as part (b).
38. (2026-10-25) The yes/no popup's buttons sat oddly (left aligned; right aligned "would also be strange"). Tried: a
    key row across the dialog's foot, edge to edge, the keys sharing the width, the main one on the right in silver
    (`KlangDialog`); caps printed under the keys were tried and rejected. And everywhere: buttons carry verbs that say what happens
    ("Keep", "Move"), not "Yes" and "No". Settled with the maintainer: no printed caps (a key's hint comes from the
    key manager, an `OnKey` in the button), and only the top pair of rivets (at the foot, and lifted over the key
    row, they looked off). "OK thats good."
39. (2026-10-26) "As a general principle: when there is enough space we show the keyboard shortcut pills always."
    First place: the tool window's header, "Close: Esc" left of the pin. The pill still comes from the key manager
    (item 38: no caps printed by hand), as a variant whose hint always shows, not only while the hints are held;
    asked of Klang-Main-Dev on the maintainer's word (2026-10-26).
40. (2026-10-26) A key for the Motor button; in the Motor's drawer, arrow keys and Enter. Arrow keys between buttons
    are nothing the browser does by itself, so it needs a mechanism, and that mechanism belongs in the general key
    manager, not in the menu. The maintainer (2026-10-26): parked for now, but kept: "Goal should be that everything
    can be done with keys only, probably very good for live coding." Klang-Main-Dev will build the arrows (one tab
    stop, arrows, Home/End, Enter) into the key manager once the plan is agreed. The Motor button's key: Alt+M (the
    maintainer's OK), with its own `OnKey` hint.

## Where the conversation stands (leanings, not decisions)

- **Layout A.** Left column: the project (files, imports, sounds in use, versions) above the Motor. Centre: a slim
  transport bar and the editor, a bottom panel for wide things (analysis, problems, console). Right: a strip of tool
  window buttons (docs, param tool, problems, orbits, keys).
- **Tool windows fly in** over the editor from the right, the editor keeps its width; a pin docks one beside the
  editor. The bottom panel stays docked. (Shown on the canvas since 2026-10-25.)
- **Three places, one job each** (proposed, the maintainer has not answered yet): the Motor button is "where to go"
  (songs, learn, docs, samples, MIDI, credits, later community and account; plus search on Mod K); the song header's
  menu is "this song" (save, copy, rename, files, record, versions, share); the right strip is "about the code under
  the cursor". The drawer's search may also find song actions.
- **The first-visit trail** (canvas board E): one lamp at a time, lit by context rather than a timer, pulsing once
  per cycle in time with the song, no arrows or coach marks, retired by one use or by being ignored, gold means "try
  me" and nothing else glows gold, reduced motion respected. Order on the canvas: Play, Motor button, Songs in the
  drawer, Docs, param tool, Analysis.
- **Rivets on every popup** (the maintainer, item 11): a candidate rule for the design round. The kitchen sink
  already draws the dialog, the context menu, the hover docs and the drawer with them.
- **A testable trail** (item 12): the trail is a pure state machine (steps, what lights a step, what retires it,
  the stored progress) with no UI in it, specced on its own; the components only read "is this lamp lit" and
  report "this was used". The same machine could drive the tutorial tech tree (item 13): a tutorial unlocks when
  the ones before it are done, as the tracks and the completion state of the tutorials pages already suggest.
- **The kitchen sink** (item 14) lists every element with the file it lives in today, or "new"; its last cell names
  what is still to draw (MIDI panels, the REPL, the mini notation panels, completion, bookmarks).

- **Drawn for items 16 to 22** (2026-10-25): the kitchen sink has a gauge section (icons for cylinders, heat and
  voices; offline, online, too hot; sizes), knob variants (label on top or below, value, icon on the cap with the
  pointer as a dot, dark or silver caps), white beside brushed silver, flat beside domed. Board F puts a tutorial on
  the workbench: The Klang Path's two strands (sound, pattern) and three stages as a tech tree in the left column,
  lamps for done, next, open and locked. Board G proposes the start page as starting the Motor: a silver key is the
  click the browser needs before it plays audio, then the dyno run of today in the new gauges, then the Motor
  glides into its corner and the workbench opens around it. Board A2 tries the title in the right strip.

- **One shell for every page** (item 25): today the Motor sits inside `SidebarMenu` (`MenuLayout`), the start page
  has `MotorBackgroundLayout` with gauges of its own, and the tour and the not-found page use `FullscreenLayout`;
  switching layouts mounts a new Motor. One shell layout for all routes keeps one Motor alive across pages, with
  modes (ignition: centred and large; workbench: in its corner), and that is what lets it glide into the corner.
  The left column is a flex column: the content scrolls (`min-height: 0`, its own scrollbar), the Motor never
  shrinks and never gets covered.
- **The theme** (items 26, 27): a new set of variables, in three layers: a small palette (the raw colours), roles
  that every element reads (surface raised, pressed, domed; metal light, mid, dark; text, label; lamp on; "try
  me"; gauge glow; warning, error), and component values only where a role does not fit. A user edits the palette
  and the roles; gradients such as silver or the domed cap are derived from them, so a copper or a cream theme
  works without new gradients. The theme is data (a `KlangLookAndFeel`-like data class, saved as a small JSON
  file with a name and an author), never CSS text or URLs, so a shared theme cannot inject anything. The theme
  editor's live preview is the kitchen sink; it warns when a text colour falls under 4.5:1 contrast. "Gold means
  try me" becomes a role, so it can be recoloured but stays one role. The old `--klang-*` variables become aliases
  of the new roles while the pages move over (scaffolding), then go.
- **Prepared for later** (items 28, 29): the theme is resolved as a stack of layers (app default, the user's
  theme, a song's override, what a visual script sets while playing), and its roles can be read and set by name.
  The song layer and the script layer stay empty until they are wanted; the stack costs nothing now and spares a
  rebuild then.

## Decide before implementing

- The Motor button: "where to go" only, or song things too (item 8).
- Transport (play, stop, cycle, RPM): stays with the song, or moves into the Motor.
- Tool windows: dock and fly-in only, or floating windows too (leaning: no floating, complexity is the enemy).
- Tutorials and docs pages: plain pages, or the same workbench with the tool windows.
- The trail and the existing Tour page: replace, or keep the tour as an optional guided version.
- Where trail progress lives: the browser now, the account with the backend.

## The work

1. The design round: a style-sheet canvas of every element in every state (buttons, knobs, sliders, gauges, fields,
   tabs, menus, tool window headers, the drawer, dialogs, lamps, key caps; type scale, colour roles, radii, rivets,
   light from above as in KLANG-00207 (UI vintage look inspiration)), then a design system, then `klangui` components.
2. A Kraft prototype of layout A on a lab route, with the real Motor and editor.
3. The menu button in `PlayerMiniStats` and the drawer.
4. The tool windows: docs and param tools move out of the popups over the code (KLANG-00090 (sprudel UI tools)).
5. The trail.

## Acceptance

Not set while this is an idea.

## Credits when it lands

The trail builds on John M. Carroll's training-wheels interfaces and minimalist instruction; check the exact
references when it lands (`CREDITS.MD` and the Credits page).

## History

- 2026-10-25: created from the design conversation; the canvas holds boards A to E and the shared parts.
- 2026-10-25: the kitchen sink board; the canvas source copied to `docs/design/2026-10-25-ui-workbench-canvas/`.
