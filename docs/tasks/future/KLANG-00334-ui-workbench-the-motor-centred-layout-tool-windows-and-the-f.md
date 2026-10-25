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
