# KLANG-00421: The UI's wiring audit: a map of how state flows, one Spine, a state model that can roam

> **Status (2026-11-10): IN PROGRESS.** The tool, the six traces and the map are done (work steps 1 to 3) and the
> proposal is with the maintainer (step 4). Two review rounds, neither clean; the findings are fixed, see "Reviews".

## Why this exists

The maintainer (2026-11-10), before KLANG-00413 (the app's workbench parts) is built: "I need you to audit the ui.
Not so much the details in each view but the general structure. My goal is to keep the code that builds the ui
clean and understandable and open for new additions. Most important is how data flows through the ui. [...]
Everything that is not temporary component state should flow from the spine to the components / page through
streams. [...] Think of a control cabinet where each cable is nicely arranged and held and not loosely hanging
through the air."

The goal behind it (the same day): "imagine some day we have the server. I want all state to be central, so that
it could be sent to the server and stored there. Then the user switches to another computer and finds the ui in the
exact same state."

KLANG-00389 (the Spine) holds a sweep of 2026-10-29: who holds state. It is a list of holders, counted by hand, and
it has drifted (40 stream sources then, 44 now; 107 subscriptions then, 136 now). This audit adds the cables between
the holders and makes the count repeatable.

## The maintainer's directions (2026-11-10)

- **One Spine.** "There should only be one global object if at all and components will find what they need through
  `spine.xyz` instead of multiple global objects. We could also register the spine in the app's properties like the
  `Component<*>.popups` etc are done."
- **No hand-written serialization.** "Things like KeymapJson which defines its own json mapping. We should not do
  this, but rather [...] use Slumber or kotlinx serialization [...] to get a descriptive data model, that mostly
  consists of data classes that can be easily understood and round-tripped. It is also fine to write custom
  serializers / deserializers but as 'plugins' for kotlinx-serialization / slumber."
- **No KSP for the UI** (asked and answered the same day): the UI's trouble is where state lives, not how much code
  it takes; the storage objects' one repeating shape is covered by a plain generic class. Looked at again if the
  map shows many equal shapes a generic class cannot cover.
- **Lateral talk is allowed where it is needed** ("cases where components need to communicate laterally, like the
  page and the tools"), but it gets a named way.

## What the audit says about each piece of state

| Kind | Examples | Goes to a server |
|---|---|---|
| Follows the user | settings, looks, the keymap, recents, the workbench's layout, tutorial progress | yes |
| Belongs to this machine | the MIDI devices chosen, permissions granted | no |
| Lives for this session | the engine's status, the Motor's readings, the Console's log, the song that plays | no |
| Temporary component state | a hover, an open dropdown | no; it stays out of the Spine |

Which kind a piece is, is often the maintainer's call: the map lists a proposal for each.

## Facts found before the start

- The app module has kotlinx.serialization (core and json) on `commonMain` already; no file in `src/jsMain`,
  `src/commonMain`, `klangui` or `klangscript-ui` uses `@Serializable`.
- Slumber's codec is JVM code (62 files under `jvmMain`, 5 shared ones with annotations and helpers). So the client
  round-trips with kotlinx.serialization, and the data classes live in `commonMain` for a server to share.
- Hand-written JSON in six files: `KeymapJson`, `LookStorage`, `RecentsStorage`, `WorkbenchLayoutStorage`,
  `VersionController`, `KlangLookText`. Other settings are single values under keys of their own (`EditorSettings`).
- Nine global objects in `spine/`, twelve storage objects in `spine/storage/` and `storage/`.

## The work

1. **A tool that lists every cable**, `console/tools/ui-wiring/`: per UI file, the streams it reads and from which
   holder, state it holds itself, browser storage it touches, lateral references, global objects, raw DOM events,
   hand-written JSON. A table and a graph file. It reads text, so a stream handed on through a prop is traced by
   hand.
2. **Six flows traced by hand**, from where the value is kept to where it is drawn: the highlights' strength, the
   Console's unseen errors, the song that plays, a page's bottom tabs, the Motor's pause under a visual, the editor
   and the Quick help.
3. **The map**: the structure as a cabinet (storage, Spine, layout, pages, components, `klangui`), the cables by
   kind, the loose ones marked and listed with file and line; for each holder its kind (above), how it is kept
   today, and its place on the future `spine.` tree.
4. **The proposal**: a few rules (which cable between which rails, the named way for lateral talk), what breaks
   them today, ranked, and an order of work. A rule the maintainer decides can become a check of the tool.

## Results (2026-11-10)

The map, as a page: `docs/housekeeping/2026-11-10-ui-wiring-map.html` (published for the maintainer as an artifact).
The tool's full report of that day: `docs/housekeeping/2026-11-10-ui-wiring-report.md`. Run the tool again for
today's numbers: `python3 -I console/tools/ui-wiring/ui_wiring.py --out <dir>`.

**The numbers.** 323 Kotlin files in six source roots (the app's `jsMain` and `commonMain`, `klangui`,
`klangscript-ui`, sprudel's UI, the workshop). 42 global places keep state; 8 of them are the Spine's objects, 12 are
storage objects. 201 `subscribingTo`, 135 of them in the app. 17 UI files name a storage object. 8 files hand-write
JSON. 3 files outside the storage folders touch the browser's storage.

**Six flows traced by hand.**

| Value | Its path today | Verdict |
|---|---|---|
| The song that plays | `Playbacks.songLine` subscribed by `StatusBar` and `MenuPickUp`; stop is the call `Playbacks.song.stop()` | The model case: stream down, call up |
| The highlights' strength | a browser key read by `EditorSettings`, a storage object that also holds the stream and the setter; `KlangCodeEditorComp` and `SongSettings` subscribe | Around the Spine: the UI talks to storage |
| The Console's unseen errors | `ConsoleLog.state` mapped in `WorkbenchLayout`, handed to `BottomTools.with`, from there into the panel's `count` and `ConsoleTool.tab(count)` | Carried by hand over three hops; KLANG-00413's label callbacks end it |
| The Motor's pause under a visual | `VisualsScreen.shown` and `open` in a component's companion; `WorkbenchLayout` combines `open` with `UiWork.resting` and hands `paused` down; `KlangCodeEditorComp` reads `open` itself | State held in the UI, read from two layers in two ways |
| A page's bottom tabs | `PageWorkbench` writes `WorkbenchSlots`, `WorkbenchLayout` subscribes | Lateral, with a named way; drawing code, need not roam |
| The editor and the Quick help | `CodeSongPage` makes a `QuickHelpPanel` (three stream sources) and holds a reference to its child, the editor; callbacks one way, the reference the other, the tab opened through the global `WorkbenchSlots.workbench` | Three ways for one conversation; only the global tracker is lateral |

**The loose cables**, each with its files in the tool's report:

- The UI names a storage object: `EditorSettings` (6 files), `TutorialStorage` (6), `LookStorage.save` (4),
  `WorkbenchLayoutStorage`, `SeedStorage`; `CodeSongPage` builds its own `SongStorage`.
- State kept in the app outside the Spine, 15 holders and 18 stream sources: `KlangGuiDance` (read by 10 files, and it
  writes its two keys itself), `VisualsScreen`'s companion, `LearnSearch`, `KlangampDeck`, `Drafts` in `LooksPage`
  (unsaved look drafts, a private global kept until the tab closes), `QuickHelpPanel`'s sources, the top-level values
  `version`, `fs`, `keysManager`, `motorBackgroundRef`. Harmless among them: three pages' `pagesDrawn` counters and
  `MotorBackground`'s one-entry surface cache.
- State owned by a library: `KlangTheme` and `KlangLookShelf` (the look, used by 51 files: 20 in the app, 31 in the
  libraries, most of them sprudel's tool windows), `KlangToolAutoUpdate` (the tools' auto-update switch, a 19th
  browser key, read by 25 files), `KlangUiToolRegistry`, and `MarkdownDisplay.cache`, which grows with every markdown
  text drawn and is never trimmed.
- Hand-written JSON: `KeymapJson`, `LookStorage`, `RecentsStorage`, `WorkbenchLayoutStorage`, `MidiLoadoutStorage`,
  `VersionController`, `KlangLookText`; `MidiInstrumentStorage` only reads two old keys, to carry them over once.
- Settings as loose single values: the editor's four keys, the walkthroughs' two, one key per finished tutorial, four
  per song with the app's version inside the key.
- Component references that cross between siblings: `motorBackgroundRef` (the start page) and the workbench tracker
  in `WorkbenchSlots` (`QuickHelpPanel`). The other seven files with a reference point from a parent to its own child.
- Not covered: the 175 `by value` of components were only sampled (most are hovers and open flags), and a stream
  handed on through a prop.

## The proposal (for the maintainer to decide)

**The state's kinds and homes** are a table on the map page: what follows the user (the editor's settings, the looks,
the keys, the songs, the tutorials finished, the recents, the walkthroughs, the metal's seed, the MIDI play code, the
tools' auto-update switch), what could go either way (the workbench's layout, the output volume, the MIDI loadout and
key layout, the unsaved look drafts), and what lives for the session.

**Rules.**

1. One Spine: a class built once in `index.kt` and registered on the Kraft app with `setAttribute`, as Kraft does for
   `popups`; components reach it as `spine`. No other global object holds state.
2. Down by stream, up by call: a component subscribes to the Spine and changes it by calling it; it keeps no copy.
3. Storage belongs to the Spine: only its storage code touches the browser's storage; the UI names no storage object.
4. What is stored is a data class: annotated for kotlinx.serialization, in `commonMain`, one document per section
   with a version; a special type gets a custom serializer; no hand-written JSON.
5. Libraries get their data through props; they never see the Spine.
6. Siblings talk through the Spine (state, or a command others may care about) or through their parent (props and
   callbacks); a component reference points only from a parent to its own child.
7. A component's own state is what may be lost; what a user would miss after a reload belongs in the Spine.

Rules 1, 3 and 4 can become checks of the tool.

**An order of work.** (1) The Spine as one object, the eight objects moved in one by one. (2) One generic stored
section over a serializable data class, the sections moved one at a time, each with a one-time reader of its old
keys; the editor's settings first. (3) The state held in the UI moves in. (4) KLANG-00413 on top. (5) The tool as a
check, with a list of agreed exceptions.

**Open decisions.** The kind of the "either way" rows. Whether the libraries keep owning the look and the tools'
switch (the Spine only stores and restores them; far less work) or get them as input (rule 5 to the letter). Whether
rule 6 names the right two ways. Where the work is tracked: steps 1 to 3 fit KLANG-00389.

## Reviews

- Round 1 (general-purpose, opus): 1 MAJOR, 11 MINOR. The MAJOR: the tool skipped `private object`, so `Drafts` in
  `LooksPage` was missed and the map said the drafts were component state. Also: four false holders (a function's
  locals read as the object's state), `MarkdownDisplay.cache` and the 19th key missed, counts off. All fixed.
- Round 2 (reviewer-high): 1 MAJOR, 5 MINOR, 1 NIT. The MAJOR: sprudel's UI was not among the roots, so the map
  called the tools' auto-update switch unused and proposed deleting it; it is read by 25 files. Also: a template
  with a lambda unbalanced the braces, one fix not pinned by a test, a count mixing scopes. All fixed: sprudel and
  the workshop added to the roots, the string scanner rewritten (templates, nested strings, char literals), 16 tests,
  8 of 8 mutants killed.
- Two rounds not clean: by the review loop, the maintainer decides how to go on.

## Acceptance

- The tool's counts can be run again and match a hand count on three files.
- The map and the proposal are with the maintainer; a reviewer has checked the map against the code.

## History

- 2026-11-10: created.
- 2026-11-10: the tool, the traces, the map and the proposal; two review rounds, their findings fixed.
