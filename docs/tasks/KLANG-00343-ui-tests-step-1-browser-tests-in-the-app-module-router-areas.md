# KLANG-00343: UI tests, step 1: browser tests in the app module (router, areas, workbench layout)

> **Status (2026-10-26): SCHEDULED.** The maintainer: "Yes please open a ticket for the ui tests"
>
> **Part of:** KLANG-00342 (UI tests: the app's screens under test)

## Why this exists

The app module has no browser tests (KLANG-00342). This step sets them up and covers what step 5 of the UI workbench
(KLANG-00341) built without tests.

## Decide before implementing

- **The setup** follows klangui's `build.gradle.kts`: the `com.google.devtools.ksp` and `io.kotest` plugins, a
  `jsTest` source set with `Deps.Test.jsTestDeps()` and Kraft's testing module. The app's `binaries.executable()`
  stays; check that Karma runs beside it before writing specs.
- **The router specs mount the router with a small route table of their own** (two layouts, a few pages), not the
  app's `nav.kt`, so they test the router's behaviour, not the app's pages.

## The work

1. The test setup in the root `build.gradle.kts`; one trivial spec proves it runs (`test-digest --expect`).
2. `RouterWithTransitions`: a page change under the same layouts keeps the layout's component (its state survives);
   a change to other layouts mounts them anew; the scrollers around the page go up on a path change, not on a
   query change; the page wrapper keeps the page's full width in a flex-row layout.
3. `AppArea.of`: every route of `nav.kt` maps to its area (the tutorials before the docs, whose paths they share).
4. `WorkbenchLayout`: the drawer marks the current area and a pick navigates.
5. The review loop; mutation checks (light tier, the router's rows by deletion mutants).

## Acceptance

- The app module's browser tests run in the build and `test-digest` sees them.
- The rows above are green and mutation-checked.

## History

- 2026-10-26: created.
