# KLANG-00396: Ultra and Kraft 0.117.0: the bump, and klang's workarounds replaced by what Ultra now has

> **Status (2026-10-30): IN PROGRESS.** Step 1 (the bump) done; steps 2 to 9 next.
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
   frame clock.
3. **The bulb's law as a stream** (ULTRA-00264): `BulbStutter`'s `SwappableSmoothing` goes; `smooth()` takes a
   `Stream<Smoothing?>`.
4. **Diagnostics sampled on a clock** (ULTRA-00267): `KlangDiagnostics`' tick on `sampled(every)` where it fits.
5. **Learn writes its filters with `urlParam`/`replaceUri`** (ULTRA-00263): `LearnTracksColumn` and
   `TutorialsListPage` drop their path-writing workaround, if the overview's Back behaviour stays as decided.
6. **The highlight canvas follows its editor** (ULTRA-00262): `CodeMirrorHighlightBuffer` on
   `app.followElementSize(view.dom)` instead of its own `ResizeObserver`, if it keeps the one-resize-per-frame guard.
7. **The Looks page asks before leaving** (ULTRA-00266): its own `beforeunload` listener to
   `guardLeaving(beforeUnload = true)`, if a draft is the only thing it guards.
8. **Specs on the app's router** (ULTRA-00243): `LearnPagesSpec` navigates with `router.navToUri`/`awaitUri` instead
   of writing `window.location.hash`.
9. **Drag in the Looks page** (ULTRA-00265): a look dragged onto a slot, beside pick and place; for the maintainer to
   see.

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

## History

- 2026-10-30: created; step 1 started.
- 2026-10-30: step 1 committed: Ultra and Kraft 0.117.0.
