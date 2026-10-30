// Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
// SPDX-License-Identifier: AGPL-3.0-or-later
//
// WHY THIS FILE EXISTS (2026-10-30)
//
// What breaks without it: Kotest on JS runs the whole suite inside one "Executor" step and only
// then hands each result to mocha, so the browser sends Karma nothing while the specs run. Karma
// gives up on a browser that is silent for browserNoActivityTimeout (30 s by default):
// "Disconnected (0 times), because no message in 30000 ms", before any spec has reported.
//
// Why this module: its suite. sprudel's Executor step took 42 s on 2026-10-30 (2,993 tests,
// Chrome 156), past the default; the other modules stay under it.
//
// What it does: it lets the browser stay silent for up to 5 minutes. A spec that really hangs
// still fails, only later.
//
// When it can go: when Kotest reports each result as it finishes, or the suite runs well under
// 30 s again. Check the "Executor" entry's time in a raw run's output.
config.set({ browserNoActivityTimeout: 300000 });
