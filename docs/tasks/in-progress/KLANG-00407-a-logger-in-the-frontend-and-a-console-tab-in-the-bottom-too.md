# KLANG-00407: A Logger in the frontend and a Console tab in the bottom tools

> **Status (2026-11-03): IN PROGRESS.** Steps 2, 3 and 5 built, with areas; step 4 in part (uncaught errors, rejected
> promises; the engine's messages wait for D7); the ignore list waits for the maintainer's look in Chrome and Firefox

## Why this exists

The maintainer, 2026-11-02: "we should have a 'Logger' in the frontend and a 'Console' panel in the bottom tools. The
logger will have multiple 'listeners', one writing to the browser console (how can we preserve to original code
location) and one into the console log buffer (spine), which is shown in tools tab."

Today the frontend writes straight to the browser's console: 64 `console.log` / `warn` / `error` / `info` / `debug`
calls in `src/jsMain` and `klangui` (counted 2026-11-02), and more in `audio_fe` and `audio_jsworklet`. What they say is
seen only with the browser's developer tools open, and a user, or the maintainer at a phone or a tablet, never sees
it. A console inside the app shows the app's own messages where its tools are.

## The design (a proposal)

```
   call site:  log.warn("Could not keep the play code", e)
                    |
                 Logger  (Spine)  -- a record: level, source, message, error, time
                    |
        +-----------+----------------------+
        |                                  |
  browser console listener          buffer listener
  (DevTools, at the call site)      ConsoleLog (Spine): the last N records, a stream
                                           |
                                    Console tab (bottom tools, every page)
```

- **`Logger` in the Spine** (`spine/Logger.kt`): one per source (`Logger("MidiRig")`, a tag), with the levels `debug`,
  `info`, `warn`, `error`. A record carries its level, its source, its message, an error when there is one, and its
  time. The Logger hands each record to its listeners; the app's start sets them.
- **The browser console listener**: writes to `console.debug/info/warn/error`, so the developer tools show what they
  show today.
- **The buffer listener**: keeps the records in `ConsoleLog`, a Spine area: a ring of the last N records (N about
  1000, a decision), published as a stream. Bounded, by the rule that nothing grows without a way to clean it up.
- **The Console tab**: a tab of the bottom panel on every page, as the MIDI tool is (KLANG-00402, step 4b: closed until
  opened on a page with no panel of its own). The records newest last, each with its time, its level (a lamp in the
  level's colour), its source and its message; filters by level and by source; Clear; Copy (all shown, as text, for a
  report); it follows the newest unless the user scrolled up. The strip key wears a count of errors not seen yet.
- **What else reaches the buffer** (a decision, D3): the browser's uncaught errors (`window.onerror`) and rejected
  promises (`unhandledrejection`); the engine's own messages (the worklet's, which reach the frontend as feedback
  today only in part).

## The original code location in the browser console

The maintainer's question. A Logger that calls `console.warn` from inside itself makes the developer tools show the
Logger's own line beside every message, not the line that logged. The ways around it (all UNVERIFIED in this
project's Kotlin/JS build and its source maps; step 1 checks them in Chrome and Firefox before the design is fixed):

1. **Inline functions.** `Logger.warn` as a Kotlin `inline fun`: the compiler copies its body into the caller, so the
   `console.warn` call stands in the caller's code and the tools show the caller's line, mapped back to the Kotlin
   source by the source map. The other listeners are called from the same inlined body. The cost: every call site
   carries a few more lines of generated code.
2. **Bound console functions.** The Logger hands out `console.warn.bind(console, "[MidiRig]")` as its `warn`, and the
   call site calls it: the browser counts the bound function as the console's own, so the line shown is the caller's.
   The buffer listener then needs its own call (a bound function cannot fan out), so the call site writes two calls,
   or a small wrapper loses the location again.
3. **The developer tools' ignore list.** Chrome hides frames from scripts on its ignore list (the source map's
   `x_google_ignoreList`, or the user's own setting), and shows the first frame outside it: the Logger's file listed
   there, the caller's line shows. It depends on the browser and on a source map setting.
4. **The location in the message.** The Logger reads `Error().stack` and prints the caller's file and line in the
   message. Works everywhere, costs a stack trace per message (fine for `warn` and `error`, too much for `debug` on a
   hot path), and the line is the generated JavaScript's unless the stack is mapped back.

Proposal: 1 (inline), checked first; 3 as the fallback in Chrome; 4 kept for the buffer's records of `warn` and
`error`, so the Console tab can show where a message came from (the file and line, mapped or not, a decision).

**Spike, 2026-11-02 (way 1, checked in this project's development build):** a throwaway `inline fun spikeWarn`
calling `console.warn`, called from another file, built once and removed. The compiler does copy the body into the
caller: the generated JavaScript has the `console.warn` inside the caller's function (marked "Inline function ...
call"). But the source map maps that line to the inline function's own Kotlin line (`SpikeLog.kt:12`), not to the
caller's (`SpikeCaller.kt:10`). So with source maps on, as the developer tools have them, the location shown is the
Logger's, not the caller's; only the frame's function name and the generated JavaScript's line are the caller's.
**Way 1 alone does not keep the caller's location.** Ways 2 and 3 are behaviour of the developer tools, still
UNVERIFIED here: the bound function (the call stands in the caller's code, so its mapped line is the caller's; the
buffer then needs its own call), and the ignore list (the Logger's file listed in the source map's
`x_google_ignoreList`, which webpack's source map plugin can write; Chrome then shows the first frame outside it, the
caller; Firefox's support to be checked). Way 4 works everywhere at the cost of a stack per message.

## Decide before implementing

- **D1, the location**: DECIDED 2026-11-02 by the maintainer: the ignore list. The Logger stays a plain function that
  fans out to its listeners; its file is listed in the source map's `x_google_ignoreList` (webpack's
  `SourceMapDevToolPlugin`, option `ignoreList`, set in `webpack.config.d`), so the developer tools show the first
  frame outside it: the caller. Checked in Chrome and Firefox before the Logger is built.
- **D2, the size of the buffer** and what leaves it first (the oldest; proposal: 1000 records, `debug` dropped first).
  TAKEN AS PROPOSED 2026-11-02, open to change.
- **D3, what the buffer takes besides the app's Logger**: the uncaught errors and rejected promises (proposal: yes),
  the engine's worklet messages (proposal: yes, through the feedback the worklet already sends), the libraries' own
  `console` calls (proposal: no; wrapping `console` itself hides every caller's location). IN PART 2026-11-02: the
  uncaught errors and the rejected promises are in (the Console only; Chrome's harmless ResizeObserver notice left
  out); the worklet's messages not yet.
- **D4, the levels the browser console listener writes** in a release build (proposal: `info` and above; `debug` only
  in a development build or when switched on in the Console tab). TAKEN AS PROPOSED 2026-11-03, open to change:
  `BuildMode` reads webpack's mode; a development build writes every level, a release `info` and up. The switch in
  the Console tab is not built.
- **D5, where the Console tab lives**: every page's bottom panel as the MIDI tool (proposal), or the Dev Tools only.
  TAKEN AS PROPOSED 2026-11-02: opened from its key while the key counts errors, the panel opens at the Console.
- **D6, the existing `console` calls**: moved to the Logger in this ticket, all 64 and the engine's (proposal), or as
  they are touched. TAKEN 2026-11-03 for the page's own code (34 files). Left as they are: the engine's own lines
  (`audio_be`, `audio_jsworklet`; part of them run in the worklet, another JS realm), which wait for D7;
  `KlangBenchmark` (its console lines are its report), KlangScript's `console` (the user's script output), `workshop`
  (no Klang dependency), and `StartPage.kt` and `KlangCodePlaybackCtrl.kt` (another session's work), to move when they
  are free.
- **D7, the engine's messages** (the maintainer, 2026-11-03: "which backend message would we log, to not overwhelm
  the ui?"). PROPOSAL, not built: log a change of state or a summary, never a stream. `BackendReady` once (info: the
  rate, the latency); a playback's start at debug at most; samples as one summary per song and a warning per sample
  that fails, not each request; `Diagnostics` never as such, only its transitions (an overload began; it ended, how
  long, how many blocks late: warn); the player failing to start (error). With it, identical records in a row fold
  into one line with a count in the Console.

## The work

1. A spike: the four ways of keeping the location, each tried in Chrome and Firefox with this build's source maps;
   the result recorded here, and D1 decided with the maintainer.
2. `Logger` and its listeners in the Spine (the browser console, the buffer), `ConsoleLog`; specs for the routing of
   records, the levels, the ring's bound.
3. The Console tab, its filters, Clear, Copy, the follow, the strip key's count of errors.
4. The uncaught errors, the rejected promises and the engine's messages into the buffer (D3).
5. The existing `console` calls moved to the Logger (D6).

## Where it stands (2026-11-03)

- **The Logger** (`common`, so `klangui` and `audio_fe` can use it too): `common/src/commonMain/kotlin/logging/`
  (`KlangLog`, `Logger`, `LogRecord`, `LogBuffer`) and `common/src/jsMain/kotlin/logging/BrowserConsoleListener.kt`.
  Every `logging/` directory of a Kotlin source set is on the source maps' ignore list
  (`webpack.config.d/04-log-ignore-list.js`); the built map lists both files beside Kotlin's own entry. Webpack
  writes the standard key `ignoreList`, not `x_google_ignoreList`. **To check by the maintainer:** after a dev server
  restart, DevTools in Chrome and in Firefox show `[app/App] The app is on the page` at `KlangStudioComponent.kt`, not at
  `BrowserConsoleListener.kt` or `KlangLog.kt`.
- **The Console** (`spine/ConsoleLog.kt`, `comp/ConsoleTool.kt`): a tab beside the MIDI tool on every page
  (`layouts/BottomTools.kt`); where a page has no panel of its own, the two make a panel "Tools" (a toolbox). Its
  key and the tab's title count the errors not seen yet; showing the tab sees them.
- **Areas** (the maintainer, 2026-11-03: "categories of message ... like ui, backend etc"): each record has an
  area, `app`, `ui`, `editor`, `engine`, `midi`, `storage` (`LogArea`; the engine's is the audio engine, apart from
  a server side to come), beside its finer source; the Console filters by both.
- **Taken as proposed, open to change:** D2 (1000 records, `debug` dropped first), D3 in part (uncaught errors and
  rejected promises, into the Console only, since the browser prints them itself; the worklet's messages not yet),
  D5 (every page's bottom panel), D4 and D6 (2026-11-03, see below).

## Acceptance

- A message logged anywhere in the frontend shows in the browser console at its own line of code, and in the Console
  tab with its time, level and source.
- The Console tab is in the bottom tools on every page, and an error shows on its key until it is seen.
- The buffer never holds more than its bound.
- An uncaught error in the frontend shows in the Console tab.

## History

- 2026-11-02: created from the maintainer's ask; to be taken up when Klang-Research's open work (KLANG-00402 step 5,
  KLANG-00406's hand test, KLANG-00389's open steps) is done.
- 2026-11-02: the spike on way 1 (inline): the call moves into the caller, the source map still points at the Logger; D1 open.
- 2026-11-02: D1 decided, the ignore list; in progress.
- 2026-11-02: the Logger, the ignore list, the Console tab and the uncaught errors built; the browsers' check waits.
- 2026-11-03: areas, D4 and D6 taken; D7 proposed.
- 2026-11-02: IN PROGRESS.
