# KLANG-00396: Ultra and Kraft 0.117.0: the bump, and klang's workarounds replaced by what Ultra now has

> **Status (2026-10-31): IN PROGRESS.** Steps 1 to 9 done; the Looks page's drag waits for the maintainer's look.
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
9. **Drag in the Looks page** (ULTRA-00265): a look dragged onto a slot (a swap from a slot, as the click's), or from
   a slot onto the bench, beside pick and place; the place under the pointer gets the dashed ring, a chip follows
   the pointer. Every placement, click or drop or a new look, goes through one `equip`: nothing stays in hand, and a
   waiting slot that is filled waits no more. Open for the maintainer: the whole tile is the drag's handle on touch
   (`touch-action: none`), so a swipe that starts on a tile drags rather than scrolls; Kraft's advice is a grip.

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

## History

- 2026-10-30: created; step 1 started.
- 2026-10-30: step 1 committed: Ultra and Kraft 0.117.0.
- 2026-10-31: steps 2 to 9 reviewed in two rounds (the second clean), committed.
