# KLANG-00396: Ultra and Kraft 0.117.0: the bump, and klang's workarounds replaced by what Ultra now has

> **Status (2026-10-31): IN PROGRESS.** Steps 1 to 10 done; the Looks page's drag reworked after the maintainer's
> look (drag and drop only, no select on a drop, no ring while dragging); its long press on touch waits for
> ULTRA-00287 (step 9).
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-30: "ultra v0.117.0 is out and online. Please update and adopt all the changes. Ask Ultra
Main for what is included". Most of 0.117.0 came from klang's own requests (ULTRA-00243, 00246, 00262 to 00270), and
klang's code carries a workaround or a note for each. What it brings: ultra's `CHANGELOG.md`, section 0.117.0, and
Ultra-Main-Dev's summary of 2026-10-30 (no npm version moved; no `kraft:dnd` artifact, drag and drop is in Kraft
core).

## Decide before implementing

- **The maintainer's word covers the bump** (the dependency rule of 2026-10-30, MC-00035: a version bump needs the
  maintainer's OK). `kotlin-js-store/yarn.lock` needs no npm change (Ultra-Main-Dev: none moved); the build checks it.
- **Breaking changes that reach klang:** none of `ResponsiveController`, the old Semantic UI drag and drop, or a
  file-level `val router` in a TestBed spec is used here. The one that does: klang's own three.js declarations in
  Kraft's packages (`externals/kraftThreejsExt/`, `Vector2`, `Texture`, `CanvasTexture`, `TextureWrapping`,
  `TextureFilter`, `createVector2`, `createCanvasTexture`) now exist in Kraft itself, so they go with the bump.
- **Kept, not in 0.117.0:** ULTRA-00279 (pointer test helpers, modifiers in `keyDown`), ULTRA-00284 (a resting frame
  clock), ULTRA-00285 (key layers for modals and popups). The workshop's key manager (KLANG-00325) has nothing to
  adopt yet.
- **Drag in the Looks page** (ULTRA-00265's use) is a new feature, not a replacement; it comes last, in its own step,
  and the maintainer sees it.

## The work

1. **The bump:** `Deps.kt` to 0.117.0; `externals/kraftThreejsExt/` deleted, the imports on Kraft's; `UiWork.window`
   on `WindowController.default` (the constructor is deprecated); Kraft registers that one in every app, TestBed's
   too, so `index.kt` no longer names it. `KlangWorkbenchSpec` hands its test to TestBed's new block in a lambda.
2. **The three.js loop rests** (ULTRA-00270): `MotorBackground`'s `ThreeJs(paused = UiWork.resting)`, on the shared
   frame clock; a resize while it rests draws the plate once by hand (Kraft: a `setSize` clears the canvas).
3. **The bulb's law:** not on `smooth(Stream<Smoothing?>)` (ULTRA-00264). The lamp steps its law by hand inside its
   own frame (`SwingingLamp.advanceTo`, with its spec), one clock for the whole light; `SwappableSmoothing`, kept for
   the stream version and never used, is deleted.
4. **Diagnostics:** no `sampled()` (ULTRA-00267): it samples one stream, and the metrics' registry is none. The KDoc
   says so.
5. **Learn's filters:** they stay navigations, so Back undoes a choice (the column's design); `replaceUri` under hash
   routing works now (ULTRA-00263), so only the comments that blamed it change.
6. **The highlight canvas follows its editor** (ULTRA-00262): `CodeMirrorHighlightBuffer` on Kraft's
   `application.followElementSize(view.dom)` (a resize once per frame), its own `ResizeObserver` gone.
7. **The Looks page's leave warning** stays app-wide: Kraft's `guardLeaving(beforeUnload = true)` (ULTRA-00266) holds
   only while its page is mounted, and the drafts outlive the page. The KDoc says so.
8. **Specs on the app's router** (ULTRA-00243): `LearnPagesSpec` goes to a place with `router.navToUri` and
   `router.awaitUri` inside TestBed's scope, instead of writing `window.location.hash`.
9. **Drag in the Looks page** (ULTRA-00265): a look dragged onto a slot (a swap from another slot), or from a slot
   onto the bench; the place under the pointer lights up, a chip follows the pointer. Every placement, a drop, a
   bench look clicked onto the waiting slot or a new look, goes through one `equip`, and a waiting slot that is
   filled waits no more. Open for the maintainer: the whole tile is the drag's handle on touch
   (`touch-action: none`), so a swipe that starts on a tile drags rather than scrolls; Kraft's advice is a grip.
   The maintainer, 2026-10-31: "We could initiate the drag with a long press?" Kraft's dnd cannot yet (no hold
   before the drag, nothing that holds off the browser's pan): asked of Ultra as ULTRA-00287 (IDEA). Until it lands
   the whole tile stays the handle; then the tile goes to `touch-action: pan-y` and hands the hold to `startDrag`.
   The maintainer, 2026-10-31, after a look: "the drop should not call select()", and no ring while dragging: the
   look under the pointer brightens, an empty slot or the bench gets a faint blue wash. Then: "remove the "in hand"
   logic completely. Say somewhere "use drag and drop"": equipping is drag and drop only, a click shows a look for
   editing (the gold ring), the hint says "Use drag and drop"; the drawer's "+" keeps its waiting slot, which a bench
   look clicked fills. The keyboard loses its way to equip (pick and place was it).
10. **The screen stays awake while something plays** (ULTRA-00275; the maintainer, 2026-10-31: whenever a song plays,
    not only in the visuals; "these kinds of wirings should always be done through the stream"). `KlangPlayer.playbacks`
    is its active playbacks, their one store (changed under its lock), and `KlangPlayer.playing` says whether a cyclic
    playback is among them (the MIDI keyboard's always-on voices do not count); the app's `Player.playing` follows the
    current player's; `ScreenAwake`, mounted once at the app's root, mounts Kraft's `WakeLockComponent` while it says
    so. The MIDI page holds its own, always (the maintainer, 2026-10-31: "On the midi page we can have a dedicated
    wakelock always"): a keyboard over USB or Bluetooth never touches the screen.

## Acceptance

- The app, klangui and the workshop compile on 0.117.0; every module's tests green; `WorkshopDomainSpec` green.
- No klang file declares a type in a Kraft package.
- Each replaced workaround's behaviour holds (its spec, or a screenshot where it is a look).

## Review and checks

- **Step 1:** every module compiles on 0.117.0 with no deprecation warning; every module's tests green (15794 tests,
  `KlangWorkbenchSpec`, `LearnPagesSpec`, `WorkshopDomainSpec` among them) except `:sprudel:jsBrowserTest`, which
  hangs before its first spec identically on 0.116.0 (not this change; Klang-Main-Dev hunts it). Review round 1
  (level 1): clean, 2 MINOR applied (this ticket's step 1 line, KLANG-00335's pointer to the deleted extension);
  1 NIT left for step 9 (LooksPage's "the drag waits for Kraft's dnd").
- **Steps 2 to 9:** everything compiles; the app's 85 browser tests green (`LearnPagesSpec`'s 12). In the built app
  (CDP mouse events): a bench look dragged onto slot 1 lands there, the chip and the ring show mid-drag; at
  `/looks/slot/7` a look dropped on the waiting slot 8 fills it and the hint leaves "Slot 8 waits". Not seen in a
  browser: the plate resting, the highlight canvas resizing.
- **Review round 1** (level 1): 1 MAJOR (a drop left the waiting slot set, so the next bench look picked took that slot
  from the look just dropped; the same cause in `pickEmpty` with a look in hand): fixed with one `equip`, a drop also
  selects its look as a click does. 1 MINOR for the maintainer (the tiles' `touch-action`), 3 NIT applied (the
  resize's manual render only while resting, two KDocs).
- **Review round 2** (level 2, the delta and the code it touches): clean. 1 MINOR applied (pre-existing: a deleted look
  stayed in hand, and a slot clicked next took its id), 1 NIT applied (a new look on the waiting slot through
  `equip`), 1 NIT left (a bench look clicked with a slot look in hand while a slot waits fills the waiting slot).
- **Step 10:** the app's and klang's tests green (153; `ScreenAwakeSpec`, `KlangPlayerPlaybacksSpec` on a real player,
  JVM and JS); 5 mutants killed (always awake, never awake, a stop not emitted, the realtime voices counted, the
  realtime voices not stored). Not seen on a device: a phone kept awake. Review round 1 (level 1): 2 MINOR applied (a
  second copy of the playbacks beside the stream, now the one store; the core without a test), 3 NIT applied, 1 for the
  maintainer (the MIDI page: its own lock, always). An older bug found on the way: KLANG-00398. Round 2 (level 1, the
  delta and the MIDI page): clean; 5 NIT applied (KDocs, the lock's handlers, the MIDI page's lock below its heading).

## History

- 2026-10-30: created; step 1 started.
- 2026-10-30: step 1 committed: Ultra and Kraft 0.117.0.
- 2026-10-31: steps 2 to 9 reviewed in two rounds (the second clean), committed.
- 2026-10-31: step 10, the wake lock, reviewed in two rounds (the second clean), committed.
- 2026-10-31: step 9's touch question answered by the maintainer (a long press); asked of Ultra as ULTRA-00287.
