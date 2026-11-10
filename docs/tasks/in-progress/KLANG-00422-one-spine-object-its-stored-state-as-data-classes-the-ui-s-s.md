# KLANG-00422: One Spine object, its stored state as data classes, the UI's state moved in

> **Status (2026-11-10): IN PROGRESS.** Step 1 started: the Spine as one object.
>
> **Part of:** KLANG-00389 (The Spine: one data layer under the UI)

## Why this exists

The audit KLANG-00421 (the UI's wiring) mapped how state reaches the screen: 43 global places keep state, 8 of them
the Spine's objects; 17 UI files name a storage object; 8 files hand-write JSON. The maintainer's goal (2026-11-10):
"imagine some day we have the server. I want all state to be central, so that it could be sent to the server and
stored there. Then the user switches to another computer and finds the ui in the exact same state."

This ticket holds the work that follows from the map. The map itself, with every file: KLANG-00421.

## Decided (the maintainer, 2026-11-10)

- **One Spine.** "There should only be one global object if at all and components will find what they need through
  `spine.xyz`." A class, built once in `index.kt`, registered on the Kraft app with `setAttribute` as Kraft does for
  `popups`, reached from a component as `spine`.
- **No hand-written serialization.** What is stored is a data class round-tripped by kotlinx.serialization (Slumber's
  codec is JVM code; the data classes live in `commonMain` so a server can share them). A special type gets a custom
  serializer, as a plug-in of the library.
- **No KSP for the UI.**
- **The widgets know nothing of storage.** "The ui widgets should be agnostic of storage and the storing should be
  handled in the app. Think of klangui as reusable components in any context." So `klangui` keeps owning the look
  (`KlangTheme`, `KlangLookShelf`): the Spine stores and restores it. The tools' auto-update switch
  (`KlangToolAutoUpdate`) stops storing itself: the app feeds and stores it.
- **What stays on this machine for now:** the workbench's layout, the output volume, the MIDI loadout and the laptop
  key layout. Everything else that is stored follows the user.
- **Talk between siblings:** "we should use the method that fits the feature. I think there is no general rule": the
  Spine (state, or a command others care about) or the parent (props and callbacks) are the two sensible ways, as
  guidance, not as a rule.

## Decide before implementing

- **D1, the theme as an app attribute.** The maintainer asked (2026-11-10): "Could we also register KlangTheme as
  `Component<*>.theme` to get rid of the global object?" It can: `klangui` gives a `KraftApp.Builder` function that
  registers it, as `popups` does, and components read `theme`. 45 of the 50 files that name `KlangTheme` are
  component files; the other five need it handed in (`CodeMirrorTheme`, `BookmarksExtension`, `DslEditorExtension`,
  `KlangToolInfoHelpers`, `index.kt`). To check first: render helpers outside a component class that name it, and
  that the theme writes CSS variables on the document's root, so it stays one per page whatever holds it. The pattern
  is at work already: the workshop registers the key manager this way (`keys(...)`, `Component<*>.keys`), and nine
  files outside the workshop reach it so.
- **D2, the unsaved look drafts** (`Drafts` in `LooksPage`, a private global, lost when the tab closes): into the
  Spine as stored state, or left as they are.
- **D3, `MarkdownDisplay.cache`** in `klangui` grows with every markdown text drawn: a bound, or a cache per component.

## The work

Each step leaves the app working and is reviewed on its own. `console/tools/ui-wiring` shows the numbers before and
after.

1. **The Spine as one object.** A `Spine` class with the eight objects of `spine/` as its parts (`player`,
   `playbacks`, `motor`, `console`, `work`, `visuals`, `recents`, `midi`), registered on the app; one part per
   change, callers moved from `Player.x` to `spine.player.x`. Code that is no component (key bindings, start-up) gets
   it handed in.
2. **One stored section.** First apply kotlinx.serialization's compiler plug-in to the app module (declared
   `apply false` in the root project today, so a `@Serializable` class there gets no serializer). Then a small
   generic part of the Spine that loads, saves and streams one serializable data class under one key, with a version. Ultra's `persistInLocalStorage(key, serializer)` already does most of it:
   look at it first.
3. **The sections, one at a time,** each with a one-time reader of its old keys (a check that goes when the old keys
   are gone):
   - the editor's settings (`EditorSettings`, four keys), first, as the pattern;
   - the workbench's layout, the recents, the keymap (`KeymapJson` goes), the walkthroughs (`KlangGuiDance`'s two
     keys), the tutorials finished, the metal's seed;
   - the looks (`LookStorage` goes into `spine.looks`, `KlangLookText` as a serializer), the tools' switch;
   - the songs (`SongStorage`, today built by the song page), the MIDI play code and setup.
4. **The state held in the UI moves in:** `VisualsScreen`'s companion (`shown`, `open`, the tiles, the rain),
   `LearnSearch`, `KlangampDeck`, `KlangGuiDance`, and the top-level values `version` and `fs`.
5. **KLANG-00413 on top** (the app's workbench parts, parts that draw their own label), which ends the values
   handed down by hand (the Console's count, the Motor's pause).
6. **The tool as a check:** no stateful global outside `spine/` (and the agreed ones in the libraries), no browser
   storage outside the Spine's storage code, no hand-written JSON; a list of agreed exceptions beside it.

## Acceptance

- A component's lasting state comes from `spine` or its props; `ui-wiring` finds no storage object named by the UI.
- Every stored section is one data class with a serializer and round-trips in a spec.
- A user's stored data from before the change is read once and kept.

## History

- 2026-11-10: created from KLANG-00421's proposal and the maintainer's decisions of that day.
- 2026-11-10: IN PROGRESS. Step 1 started: the Spine as one object.
