# KLANG-00342: UI tests: the app's screens under test

> **Status (2026-10-26): SCHEDULED.** An epic, opened on the maintainer's word (2026-10-26): "There should probably be
> an epic that will get many children over time"

## Why this exists

klangui's components have browser specs (Kraft's `TestBed` in headless Chrome), but the app module (`src/`: the pages,
the layouts, the router) has none. Step 5 of the UI workbench (KLANG-00341) showed the cost: the router remounting
the layout on every page change, the lost scroll reset and the shrunk pages were all found by a reviewer reading
code, none by a test. The more the app moves into the workbench, and the more it can be done with keys only
(KLANG-00334 item 40), the more of it should be under test.

## Decide before implementing

- **The same setup as klangui** (KSP for Kotest's spec discovery on JS, the Kotest plugin, Kraft's testing module):
  plain Gradle, no new machinery.
- **What a UI test guards:** behaviour a user meets (where the focus goes, what a key does, what stays mounted, what
  a route shows), not values or looks. A look is the maintainer's eye; a value-echo is no test (`/review-loop`).
- **Later, when the shell has settled:** a few end-to-end smoke runs in a real browser against the dev build (start
  the Motor, open a song, play, switch pages). Which tool, and whether at all, is decided then.

## The work

Each child is one step; this epic collects them.

1. KLANG-00343: browser tests in the app module, first for the router, the areas and the spectrum's arithmetic.
2. Later, once the layout settles (the maintainer, 2026-10-26: "please do not do it too tightly at the moment"): the
   workbench layout (the drawer marks the current area, a pick navigates), deferred from KLANG-00343.

## Acceptance

An epic: it closes when the maintainer says the app is covered well enough for now.

## History

- 2026-10-26: created, with its first child KLANG-00343.
