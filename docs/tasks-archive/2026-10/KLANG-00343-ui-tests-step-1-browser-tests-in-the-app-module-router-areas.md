# KLANG-00343: UI tests, step 1: browser tests in the app module (router, areas, workbench layout)

> **Status (2026-10-26): DONE.** the app's browser tests set up; the router, AppArea and the spectrum under test, loosely; the workbench layout deferred to KLANG-00342
>
> **Part of:** KLANG-00342 (UI tests: the app's screens under test)

## Why this exists

The app module has no browser tests (KLANG-00342). This step sets them up and covers what step 5 of the UI workbench
(KLANG-00341) built without tests.

## Decide before implementing

- **The setup** follows klangui's `build.gradle.kts`: the `com.google.devtools.ksp` and `io.kotest` plugins, and
  Kraft's testing module in `jsTest` (Kotest's engine and assertions come from the root's `commonTest`). The app's `binaries.executable()`
  stays; check that Karma runs beside it before writing specs.
- **Loose, not tight** (the maintainer, 2026-10-26: "I think a lot of things might change while we implement the
  layout. So please do not do it too tightly at the moment"): the specs guard behaviour that outlives the layout work
  (the router keeps a layout across its pages, the spectrum's arithmetic, `AppArea`'s rules), not today's route table
  or today's workbench. The full route-to-area table and the workbench's drawer (item 4 below) wait until the layout
  settles.
- **Kraft and Ultra feedback** (the maintainer: "whenever you find something in TestBed or KQuery that would need
  improvements tell it too Ultra"): collected under "Notes" and sent to Ultra.
- **The router specs mount the router with a small route table of their own** (two layouts, a few pages), not the
  app's `nav.kt`, so they test the router's behaviour, not the app's pages.

## The work

1. The test setup in the root `build.gradle.kts`; one trivial spec proves it runs (`test-digest --expect`).
2. `RouterWithTransitions`: a page change under the same layouts keeps the layout's component (its state survives);
   a change to other layouts mounts them anew; the scrollers around the page go up on a path change, not on a
   query change; the page wrapper keeps the page's full width in a flex-row layout.
3. `AppArea.of`: every route of `nav.kt` maps to its area (the tutorials before the docs, whose paths they share).
4. `WorkbenchLayout`: the drawer marks the current area and a pick navigates. **Deferred** until the layout settles,
   to KLANG-00342.
5. The Motor's spectrum (KLANG-00344): `spectrumColumns` (whole pixels per column, the rest half on either side, a
   pointer over the padding names no column) and `rangeLabel` (Hz, kHz, the mixed case), both pure functions.
6. The review loop; mutation checks (light tier, the router's rows by deletion mutants).

## Acceptance

- The app module's browser tests run in the build and `test-digest` sees them.
- The rows above are green and mutation-checked.

## Notes

- With KSP on the root project, `configureJvmTests` makes the root's `jvmTest` depend on every KSP task of the
  root, `kspTestKotlinJs` too (the review, round 1): a JVM-only run compiles the app's JS main first, and a JS compile
  error fails it. sprudel lives with the same; accepted for now. Narrowing the matcher in `Deps.configureJvmTests` to
  the JVM and common KSP tasks would cut it for both.
- With the Kotest plugin on the root project its JVM specs run by full class name only (`--tests
  io.peekandpoke.klang.SomeSpec`, no wildcards), as in every module with the plugin (`/console-tools`); a wildcard now
  finds nothing. Checked: `KatalystBodyNonFiniteWetSpec` runs by its full name.
- The app's webpack tweaks (`01-filenames.js`, `02-html-config.js`) apply to the app's bundle only: Karma's test
  bundle has no `output` section, and the chunk names failed the test build there.
- For Ultra (Kraft): `TestBed.preact(appSetup)` builds the `KraftApp` but does not hand it to the test, so a spec
  cannot reach the app's router to navigate; the spec builds its own `Router` and passes it to the component. A
  `Router`'s strategy adds its window listeners (`hashchange`; `popstate` and `click`) in `init` and never removes them:
  `disable()` silences a router but each spec's router stays registered. A `dispose()` would let a spec clean up.

## Review and checks (2026-10-26)

- **Round 1** (level 1, blind): clean, 2 MINOR and 2 NIT, applied as one batch: `AppAreaSpec` on routes of its own
  (it pinned the app's routes); the KSP coupling of the root's `jvmTest` noted above; the setup line corrected.
- **Mutation-checked**, 9 mutants, each killed for the right reason: the layouts keyed per page, the scrollers not
  reset, a query change scrolling, the page not full width, docs before tutorials, no fallback to Other, the spectrum's
  padding all on the left, 1 kHz in Hz.
- **Tests:** the app's browser tests, 10 rows in 3 specs, green; the app's dev bundle still builds its page; a root
  JVM spec runs by its full name.

## History

- 2026-10-26: created.
- 2026-10-26: IN PROGRESS. started
- 2026-10-26: DONE. the app's browser tests set up; the router, AppArea and the spectrum under test, loosely; the workbench layout deferred to KLANG-00342
