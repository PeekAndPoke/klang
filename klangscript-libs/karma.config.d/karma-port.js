// Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
// SPDX-License-Identifier: AGPL-3.0-or-later
//
// A fixed Karma port for this module's browser tests (2026-10-19). Several projects on this machine run Kotlin/JS
// browser tests, and on Karma's default port one project's ChromeHeadless could connect to another project's server
// (a spross run reported two klang suites as its own). Every project has its own range; klang's is 10031 to 10060,
// one port per module (list them with: grep -r "port:" --include=karma-port.js .). A new module with browser tests
// takes the next free port in the range.
config.set({ port: 10042 });
