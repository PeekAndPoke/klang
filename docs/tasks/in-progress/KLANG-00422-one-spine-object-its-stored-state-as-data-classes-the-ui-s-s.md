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

## Progress

- **Step 1, slice 1 (2026-11-10): done.** `Spine` exists (`spine/Spine.kt`): built once in `index.kt` (a private
  value, no global to reach for), registered with `spine(...)`, reached as `Component<*>.spine`. Three parts are in:
  `spine.work` (`UiWork`), `spine.motor` (`MotorReadings`), `spine.visuals` (`VisualState`).
- **Step 1, slice 2 (2026-11-10): done.** `spine.console` (`ConsoleLog`) and `spine.recents` (`Recents`). `index.kt`
  makes the Console first, installs it as the log's listener, and hands it to the Spine, so it hears everything from
  the start; a Spine built without one (a spec's) gets a Console that is not installed.
- **Step 1, slice 3 (2026-11-10): done.** `spine.midiSettings` (`MidiSettings`).
- **Step 1, slice 4 (2026-11-10): done, and step 1 with it.** `spine.player` (`Player`), `spine.playbacks`
  (`Playbacks`) and `spine.klangamp` (`KlangampDeck`, from the visuals: it holds state and plays on the deck, so it
  came along, ahead of step 4). No object in `spine/` keeps state any more. What changed in shape:
  - **Building a Spine starts nothing.** `Player` no longer asks for the samples when it is made: the app calls
    `preloadSamples()` right after building its Spine, a spec's Spine downloads nothing.
  - **`Playbacks` is one stage on one engine.** Its deck, its previews, the MIDI rig and every code playback get the
    stage and the engine through their constructors; a page asks `spine.playbacks.ctrl()` for a controller's
    builder. `MidiRig` and `RealtimePlaybackCtrl` know the engine only as a `PlaybackEngine`.
  - **`Player.createEngine(...)`** used no state of the player and is in its companion.
- **A pattern from slice 1.** A `Tag` function cannot reach the Spine, so a default argument that read a global
  (`paused: Stream<Boolean> = UiWork.resting`) becomes a nullable parameter and prop, and the component falls back to
  the Spine's stream itself. A part's constants and pure helpers go into its companion, so they are still read by
  the class's name. A spec that draws such a component registers a Spine in `TestBed.preact(appSetup = { ... })`.
- **Steps 2 and 3, the first section (2026-11-10): the pattern, and the editor's settings on it.**
  - **The generic part is the workshop's** (`workshop/src/.../storage/`, shared code, heading for Ultra; its table
    of where each piece goes is in `workshop/CLAUDE.md`): `StreamSource.persist(storage)`, `DocumentStorage` (one
    data class as one versioned JSON document under one key), the two pure merge functions, `KeyValueStore` with
    the browser's and one in memory.
  - **A write is a merge** (the maintainer, 2026-11-10: "we need to merge incoming data into existing data instead
    of fully overwriting"). Only the top-level fields that changed are put over what the store holds. So two tabs
    that change different settings both keep theirs, an older build beside a newer one leaves the newer one's
    fields alone, and a server can merge the same patch later. Two writers of one field: the later one wins. A list
    is one field: sections where two writers add entries (looks, songs) are modelled as entries by id when they
    move in.
  - **What cannot be read is never lost:** a document that does not decode, or is read only in part, is put aside
    as it was (the key with `-unreadable` or `-newer` after it); where that copy cannot be kept, nothing is written
    over the document in that run. The storage page shows these as "Put aside".
  - **What the merge does not cover, accepted** (the maintainer, 2026-11-10: the app will ask for a reload when a
    newer version is out, "so let us accept these overlaps"; "these conflicts should rarely happen"; old fields
    piling up is "fine for now, we might fix this when we see that the ui runs the latest version and bulk write
    instead"). A write cannot remove a field, only set it, so fields of old builds pile up in a document; an entry
    inside a map or list field does go, the field being written whole. A removal elsewhere is not followed. Each
    section that moves in checks these against its own data (the keys' reset to default, the songs).
  - **Only what was chosen is kept.** A new document holds the fields that differ from what an empty document
    reads as: the data class's own defaults, so a field left out reads back the same (for
    defaults that are constants: one that follows another field or the moment, a seed, reads back as it computes
    then, and such a section writes it once). What was kept before
    the section existed moves in the same way, and an old key that was kept counts as chosen also where it says
    what the default says. A field nobody chose stays out of the document, so a default of the class that changes
    later reaches everyone who never chose (as the editor's one-key-each storage did, and
    as the first-visit look does). A field once written stays chosen, also when set back to the default's value.
  - **No storage, no stop** (the maintainer, 2026-11-10: "we need to handle when local storage is not available.
    Same for serialization errors"). Where the browser gives no storage (the site's data blocked), the stores are
    memory for that visit and the log says so; a document that cannot be read gives the section's default; a value
    that cannot be written is logged and the app goes on with it.
  - **Three scopes** (`spine/storage/SectionStores.kt`; the maintainer, 2026-11-10: with several tabs open,
    "navigating around and opening / closing tools should not influence the other tab"):

    | Scope | Kept in | Other tabs | Other computers | For example |
    |---|---|---|---|---|
    | The user | the browser's storage, later a server too | yes | yes | settings, looks, keys, songs, tutorial progress, recents |
    | This machine | the browser's storage | yes | no | the output volume, the MIDI setup |
    | This tab | the tab's own storage (`sessionStorage`) | no | no | which tools are open or pinned, the panel's tab and height, the visual shown, Learn's search |

    The workbench's layout is the tab's, with the last layout of any tab kept per machine as a new tab's start.
    A section also says whether it follows a change from elsewhere at once (settings: yes; a song open in two
    tabs: no, and what the second tab shows is decided when the songs move in).
  - **Klang's side is wiring:** `Stored<T>` (`spine/storage/Stored.kt`: the default, every value in its range, the
    log, `part { }`, `update { }`, `reset()`), `SectionStores`, and `Settings` with the editor's section and the
    reader of its four old keys. `Spine.inBrowser` gives the app the browser's stores; a `Spine()` keeps its
    sections in memory, so a spec never reaches the browser's storage through one.
  - **The editor's settings:** `state.EditorSettings`, a `@Serializable` data class in `commonMain`;
    `spine.settings.editor`, the user's, following. The old object with its four keys and hand parsing is gone.
  - **The build:** kotlinx.serialization's compiler plug-in on the app module and the workshop; the workshop has a
    JVM target for its shared code; the app's shared code uses the workshop.
  - **For Ultra:** nothing sent of this yet. The maintainer (2026-11-11): the workshop's pieces (`persist(storage)`,
    the documents and their merging) stay here, "so we can battle test them before moving them into ultra". The
    note that a listener which publishes from inside its own notification makes later listeners see the older value
    last goes with `persist` when it moves.

- **Step 3, five more sections (2026-11-10):** the workbench's layout, the output volume, what was opened last,
  the lessons done, the metal's seed. Each is a part of the Spine that builds its own section from the stores it is
  handed (`Workbench`, `Player`, `Recents`, `Tutorials`, `Seeds`), with a reader of its old key or keys; the five
  storage objects with their hand-written JSON are gone, and no UI file names a tutorial or seed storage any more.

  | Section | Data class | Scope | Key | Follows another tab |
  |---|---|---|---|---|
  | The workbench's layout | `BenchLayout` | this tab, and the machine's last | `klang-workbench` (in both storages) | no |
  | The output volume | `AudioOutput` | this machine | `klang-output` | no, as before: a tab's volume is its own while it runs |
  | What was opened last | `LastOpened` | the user | `klang-last-opened` | yes |
  | The lessons done | a map, slug to done | the user | `klang-lessons-done` | yes |
  | The random seeds | `RandomSeeds` | the user | `klang-seeds` | yes |

  - **The tab's layout begins as the machine's last.** `Stored` takes a `start`: where nothing is kept under the
    key, the section begins with it and keeps it at once, so a tab that was never arranged by hand still has its
    own layout after a reload. Every arrangement is written twice, to the tab and as the machine's last. The
    machine's last is merged like any section: it is the last pinning of one tab beside the last panel height of
    another.
  - **The bench knows nothing of storage:** `BenchLayout` is the app's class, mapped to and from
    `KlangWorkbench.Layout` by two pure functions (`layouts/BenchLayouts.kt`). A height that is the bench's own is
    kept as null, so a changed default of the bench reaches everyone who never dragged the panel.
  - **The lessons done are a section that is no data class** (the user's keys became the second): the document's fields are the slugs, so the
    merge works per lesson and two computers each keep theirs. A mark taken back is `false`, since a write cannot
    remove a field.
  - **The seed is written when it is made** (its default is no constant): `spine.seeds.metal` makes it when first
    asked for.
  - **Stricter than before on what was kept before:** an old list of recents with one entry that cannot be read is
    not read at all (the old reader left that entry out and kept the rest); the old text stays under its key.
  - **The storage page:** a part covers the new key and the old ones; "Reset everything" also forgets what a tab
    keeps for itself (`Spine.resetAllStored()`), which is in no list of the browser's keys.
  - **A part read without a listener:** Ultra's `distinct()` gives the last value it passed on, which with no
    listener is the one it was made with, so `spine.recents.entries()` read stale. `Stored.part` now gives the
    part as it is when asked. Sent to Ultra-Main-Dev as a bug request on 2026-11-11 (the maintainer's word): ULTRA-00302,
    which also covers `cutoff()`, read the same way. Fixed on Ultra's master (bb30ad30, 2026-11-11), not released
    yet. With the Ultra version that has it: `Stored.part` goes back to plain `distinct()`; and the same release
    makes a value written by a handler during a publish win (ULTRA-00307), which is the fault `persist` writes
    first to avoid, so its comment and the note for Ultra are to be read again then.
  - **Left for their own slices:** the keymap (done, below), the looks and the tools' switch, the songs, the MIDI
    play code and setup (with the laptop keys' layout), and the walkthroughs, which move with `KlangGuiDance` in
    step 4.

- **Step 3, the keymap (2026-11-11):** the keys the user gave Klang's actions are a stored section, `UserKeys`
  (`spine.userKeys`), the user's, under `klang-keys`; the old `klang-keymap` is read once. `KeymapStorage` is gone,
  `KeymapJson` keeps only its reader for the old key.
  - **A field per action id** (`Map<String, List<String>?>`), so two tabs that change different actions both keep
    theirs.
  - **"Back to the defaults" is `null`:** a write cannot remove a field, and the key manager always saves all the
    user's keys, so an action kept before and missing now is written as `null`. Every tab reads that the same way.
  - **The key manager follows its store:** the workshop's `KeymapStore` has `watch` (a no-op by default), and
    `KeysManager` takes the keys it is told when they differ from its own, without writing them back. So a key
    changed in another tab works at once, and the storage page's reset puts the defaults back at once (before, the
    keys stayed in memory and were written back with the next change).
  - **An old text with nothing readable in it** stays under the old key; no document is made up.
  - **Seen by the reviewer, in the machinery of the earlier slices, not changed:** `DocumentStorage.watch` shows
    the text the browser's event carries, not what the store holds by then. A tab that writes between another
    tab's write and the arrival of its event then shows the older text until its next change or a reload, though
    the store holds both. One of the rare overlaps of two tabs the maintainer accepted; the cure, if wanted, is to
    read the store when the event arrives.

- **Step 3, the looks and the tools' switch (2026-11-11):** `spine.looks` keeps the look worn, the equipped slots
  and the looks of the user's own; `spine.settings.tools` keeps the code tools' auto-update switch. `LookStorage`
  is gone, and `klangui` no longer stores anything itself.

  | Section | Data class | Scope | Key (old key read once) | Follows another tab |
  |---|---|---|---|---|
  | The look worn and the slots | `LookChoice` | the user | `klang-look-choice` (`klang-look`, the slots of `klang-looks`) | no |
  | The own looks | a map from a look's id to `OwnLook` | the user | `klang-own-looks` (the looks of `klang-looks`) | no |
  | The code tools | `ToolSettings` | the user | `klang-tools` (`klang-tool-auto-update`) | yes |

  - **The widgets stay the owners, as decided:** `KlangTheme`, `KlangLookShelf` and `KlangToolAutoUpdate` are
    `klangui`'s and know nothing of storage. The Spine ties itself to them once, at the app's start
    (`looks.attach(firstVisit)`, `settings.attachTools()`, both called in `index.kt`): what is kept goes onto the
    shelf, the theme and the switch, what they become is kept, and a reset on the storage page shows at once. A
    Spine that is never attached (a spec's) leaves the widgets alone.
  - **A tab keeps its look until it is loaded again** (the maintainer, 2026-11-11: "Keep the look per tab until
    reload"): the two look sections do not follow another tab. A look chosen, made, changed or deleted elsewhere
    shows after a reload; what this tab writes is still merged field by field, so it does not undo the other
    tab's. The tools' switch follows, as the other settings do.
  - **The own looks are a field per look**, so two tabs that each change or delete a different look both keep
    theirs; a deleted look is `null`.
  - **A new look's id is `own-` and a random UUID** (the maintainer, 2026-11-11: "Yes use the stdlib uuid";
    `kotlin.uuid.Uuid`, in `KlangLookShelf.newId`). Before, it was the next free `own-N` on the tab's own shelf: two
    tabs that do not follow each other gave two looks the same id, and the later replaced the earlier for good; and
    a deleted look's id was given again. Looks made before keep their counted ids. The unused `com.benasher44:uuid`
    line is gone from `Deps.kt`.
  - **The slots are one field** (a list): of two tabs that arrange them, the later one wins. Slots never arranged
    are kept as `null`, so a change of the looks equipped by default reaches everyone who never arranged theirs.
    On the storage page the slots are now with "The look worn", which asks before a reset as "Your own looks" does.
  - **A change the shelf makes is kept, never shown back** (review round 1, MAJOR): the page that deletes or
    changes the look worn tells the theme itself. A look kept as worn that the page does not have (its looks reset,
    a built-in look of a newer build) stays kept, and the page wears the first-visit look meanwhile.
  - **A look the shelf holds as it is kept is not rewritten** (review round 2): a value this build cannot take (a
    role of a newer build) is not on the shelf, and a page that merely starts no longer drops it from the document.
    A look changed on this page is kept as this page holds it.
  - **The old text of the looks holds two sections' worth**, so it is forgotten only once both are kept (review
    round 1, MAJOR): a document that could not be written leaves it for the next start. Old keys still there when
    both documents are kept (a write that failed once and was made good later) go at the next start.
  - **The look worn is kept when told** (`looks.wear(look)`, where `LookStorage.save` was called); the theme is
    still changed by the widget or the page that lets the user choose.
  - **No hand-written JSON in the app:** `OwnLook` holds a look's values by CSS variable; `klangui` builds the look
    from those parts and puts right what a look may not hold (`KlangLookText.own`, the same rules a pasted look
    goes through). `KlangLookText` itself stays for copy and paste.
  - **Stricter on what was kept before:** an old text of the looks with an entry that is no object is not read at
    all and stays under its key (the old reader left the entry out).
  - **D2, the look drafts, is still open** and untouched.

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

1. **The Spine as one object. Done** (see Progress). A `Spine` class with the eight objects of `spine/` as its
   parts (`player`, `playbacks`, `motor`, `console`, `work`, `visuals`, `recents`, `midiSettings`, and `klangamp`
   beside them), registered on the app; one part per
   change, callers moved from `Player.x` to `spine.player.x`. Code that is no component (key bindings, start-up) gets
   it handed in.
2. **One stored section.** First apply kotlinx.serialization's compiler plug-in to the app module (declared
   `apply false` in the root project today, so a `@Serializable` class there gets no serializer). Then a small
   generic part of the Spine that loads, saves and streams one serializable data class under one key, with a version. Ultra's `persistInLocalStorage(key, serializer)` already does most of it:
   look at it first.
3. **The sections, one at a time,** each with a one-time reader of its old keys (a check that goes when the old keys
   are gone):
   - the editor's settings (`EditorSettings`, four keys), first, as the pattern: **done**;
   - the workbench's layout, the output volume, the recents, the tutorials finished, the metal's seed: **done**;
   - the keymap: **done** (`KeymapJson` stays as the old key's reader);
   - the walkthroughs (`KlangGuiDance`'s two keys, with step 4);
   - the looks and the tools' switch: **done** (`spine.looks`, `spine.settings.tools`);
   - the songs (`SongStorage`, today built by the song page), the MIDI play code and setup.
4. **The state held in the UI moves in:** `VisualsScreen`'s companion (`shown`, `open`, the tiles, the rain),
   `LearnSearch`, `KlangGuiDance`, and the top-level values `version` and `fs` (`KlangampDeck` moved with step 1).
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
- 2026-11-10: step 1, slice 1: the `Spine` class and its registration; `UiWork`, `MotorReadings` and `VisualState`
  moved in. One review round (no behaviour change found; 2 MINOR, 4 NIT, fixed), 176 tests green, 2 of 2 mutants
  of `SpineSpec` killed.
- 2026-11-10: IN PROGRESS. Step 1 started: the Spine as one object.
- 2026-11-10: step 1, slice 2: `ConsoleLog` and `Recents` moved in; five specs on a Spine of their own. One review
  round (no behaviour change found; 2 MINOR, 7 NIT, fixed), 3 of 3 mutants killed.
- 2026-11-10: step 1, slices 3 and 4: `MidiSettings`, then `Player`, `Playbacks` and `KlangampDeck` moved in; step 1
  is done. One review round (reviewer-high: clean, no behaviour change; 4 MINOR, 8 NIT, applied), 179 tests green,
  4 of 4 mutants killed.
- 2026-11-10: steps 2 and 3, the first section: the stored-section pattern and the editor's settings on it. Review
  round 1 (reviewer-high): 1 MAJOR (a document that could not be read, or of a newer Klang, was written over), 7
  MINOR, 6 NIT. Round 2: 1 MAJOR (an older build still dropped a newer build's field), 2 MINOR, 3 NIT. The
  maintainer consulted: a write becomes a merge, the generic part goes to the workshop, three scopes. Rebuilt so;
  15 of 15 mutants of the workshop's storage killed. Round 3 (reviewer-max): 6 MAJOR, 8 MINOR, 9 NIT. The
  maintainer consulted again: no design change, the rare overlaps of two tabs or two builds are accepted (see the
  Progress entry), a browser without storage and a value that cannot be read or written need a fallback. Fixed: a
  value cleaned on read was written back with the next change; a blocked storage stopped the app; a field added
  inside a nested object made every older document look read in part; a change from elsewhere is now put aside as
  one found at the start. Round 4 (reviewer-max): 1 MAJOR (the first write and the move from the old keys kept
  every field, so a default changed later would no longer reach a user who never chose), 1 MINOR, 4 NIT. Fixed: a
  new document holds only what differs from the default. 17 of 17 new mutants killed over both rounds. Round 5
  (reviewer-max): 1 MAJOR (the new document was measured from the caller's default and read back with the class's
  own: where they differ, a wrong value after a reload), 1 MINOR, 2 NIT. Fixed: measured from what an empty
  document reads as; the old keys kept count as chosen. Round 6 (reviewer-max): CLEAN, 2 MINOR and 2 NIT applied.
  23 of 23 mutants killed over rounds 4 to 6.
- 2026-11-10: step 3, five more sections: the workbench's layout (the tab's, a new tab from the machine's last), the
  output volume, what was opened last, the lessons done, the metal's seed. Review round 1 (reviewer-high): CLEAN, 4
  MINOR and 6 NIT; applied but one (no spec draws the app's `WorkbenchLayout`, so its two lines that hand the layout
  to the bench and back are untested, as before; the mapping itself is). 37 of 37 mutants killed.
- 2026-11-11: the `distinct()` fault sent to Ultra (ULTRA-00302); the workshop's storage pieces stay here to be
  battle tested (the maintainer).
- 2026-11-11: step 3, the keymap: `UserKeys`, a field per action, `null` for "back to the defaults"; the key
  manager follows its store. One review round, clean (2 MINOR, applied); 11 of 11 mutants killed.
- 2026-11-11: step 3, the looks and the tools' switch: `spine.looks` (the look worn with the slots, the own looks a
  field each), `spine.settings.tools`; the look per tab until reload, a new look's id a UUID (both the maintainer).
  Review round 1 (reviewer-high): NOT CLEAN, 2 MAJOR (a deleted worn look put on the first-visit look and stayed
  kept as worn; a failed write in the one-time reading lost the own looks), 3 MINOR, 1 NIT; fixed. Round 2
  (reviewer-high): CLEAN, 3 MINOR applied. 39 of 39 compiling mutants killed (one of 40 did not compile and was rewritten).
