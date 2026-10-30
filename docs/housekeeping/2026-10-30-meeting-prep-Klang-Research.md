# Meeting prep, Klang-Research, Friday 2026-10-30 (MC-00017)

The first meeting for this session. It speaks for its own work and advises on klang's rules; Klang-Main-Dev decides
them.

## Item 1: last week's action items

None of mine: this session was not at the meeting of 2026-10-23. Klang's topic 2 items are Klang-Main-Dev's.

## Item 2: the week in my work

1. **Worked on:** the UI with the maintainer at the screen, about 220 commits since 2026-10-23: the song page on new
   parts (KLANG-00349), Learn on the workbench (KLANG-00370), the Sprudel tool panel (KLANG-00374), the look editor
   (KLANG-00382), diagnostics as metrics (KLANG-00385), the visuals and Klang Amp, the start page's plate and dyno,
   Gradle 9.7.1 (KLANG-00348), and today the scope's smoke (KLANG-00391 to KLANG-00394).
2. **Achieved:** one frame stream for the whole UI that rests while the window has no attention (the "Spine",
   `src/jsMain/kotlin/spine/UiWork.kt`, on Kraft's `WindowController`, 2a7a4fdf7, d0a4372ee), a gauge that draws its
   dial once and only turns the needle (`klangui/src/jsMain/kotlin/comp/KlangGauge.kt`, d329990b9), and plate tiles
   that come in one by one from a cache (eb8534eba).
3. **Obstacle, headless browsers:** the Spine stops everything in a window without focus, and headless Chrome has
   none: screenshots showed a frozen UI. Fix: CDP `Emulation.setFocusEmulationEnabled`. Any project that pauses on
   blur needs this in its browser checks.
4. **Obstacle, WebGL:** headless Chrome here has no WebGL, so the three.js plate could not be seen. It works under a
   virtual display: `xvfb-run google-chrome --headless=new --use-angle=swiftshader --enable-unsafe-swiftshader`
   (found today). Useful for every project with a canvas or three.js.
5. **Obstacle, the shared tree:** three of this week's incidents came from two sessions in one checkout (item 3).
6. **Lesson:** fine displays show what a normal screen hides. A texture built from square blocks showed as a grid on
   a MacBook (dd897fc0c: irregular facets instead). Check visuals at 2x device pixels
   (`Emulation.setDeviceMetricsOverride`, `deviceScaleFactor: 2`).
7. **Lesson:** tuning a look with the maintainer goes in small commits, each with a screenshot check. A fast loop,
   but most of these commits had no review round (item 4 below).
8. **Ultra:** ULTRA-00269 (batching rAF) and ULTRA-00270 (ThreeJs pause and clock) came out of the Spine; the key
   manager (KLANG-00325) has Ultra's notes for its move into Kraft.
9. **Worth a look for other projects:** the Spine (2.), the canvas scope with its own smoke layer
   (`src/jsMain/kotlin/comp/Oscilloscope.kt`), and the screenshot recipe (3., 4., 6.).

## Item 3: klang's incidents

Split agreed with Klang-Main-Dev: it writes its 12 lines (2026-10-23 to 2026-10-26), I write my two (2026-10-28), and
I give my view on the four shared-tree lines.

**My two lines:**

- 2026-10-28, `KlangNoticeModalSpec` failed once in a full run (a key heard twice), green alone: **none yet**. If it
  happens again, look for a spec that leaves a key manager or a modal behind.
- 2026-10-28, 649198205 ran only `:klangui:jsTest`, and the app's `LearnPagesSpec` went red: **rule**, home
  `/console-tools` (the test-digest section): a change in `klangui` also runs the app's `:jsTest`, because the app's
  specs use klangui's parts. The row in the incidents file is one cell with no other columns; I'll fix it after the
  meeting.

**The four shared-tree lines:** I agree with Klang-Main-Dev's proposals:

- The staged deletion in 70770094 (my `git rm` was left staged in the shared index): **rule** in `/agent-fleet`'s
  shared-checkout block. Commit with the paths on the commit (`git commit -- <paths>`), and stage nothing ahead of a
  commit. One detail from my side: `git add -N` (intent-to-add, for new files) also writes to the shared index. I now
  run it in the same `&&` chain as the commit, with nothing in between.
- The review diff taken during a mutation run: **none**. review-pack after a campaign's end, or inside its lock, already
  covers it.
- `@codemirror/search` without its lock: **none**. The build fails loudly, and item 7's dependency rule will cover
  it.
- An edit left uncompiled while I held the build for Gradle 9: **rule** in `/agent-fleet`, for both sessions. While
  the other session holds the build, nothing uncompilable goes into the shared tree; such work waits in a worktree or
  the scratchpad.

## Item 4: what I load, and the rules I used or missed

- **At start:** the root `CLAUDE.md`. On demand: `/kraft-knowhow`, `/ultra-libs-knowhow`, `/code-style`,
  `/console-tools`, `/review-loop`, `/mission-control-link`, and once each `/tutorial-writing` (KLANG-00371) and
  `/klang-music-writing` (the jingles).
- **Used every day:** commit by path with the trailer, the build lock, the ticket tool, a mutation check for every
  new spec (e.g. `traceY`, KLANG-00391), no em-dashes, Credits ("Inspired by" in the Schmetterling jingle), the
  incidents file (my two lines), and requests to Ultra.
- **Missed:** "commit completed, reviewed steps". The visual tuning commits of the last days (the scope's glow and
  smoke, the Klang Amp layout, the start page's screen, today's facets and gauges) had screenshot checks and specs
  where there was logic, but no review round. A question for Klang-Main-Dev: a light lane for look tuning
  done at the maintainer's word (no review round, a screenshot and the specs for any logic), like Ultra's light lane?
- **Missed:** I ran only the module's tests for 649198205 (item 3).

## Item 5: top-rung review rounds this week

- **KLANG-00344, round 2 at level 3** (2026-10-26, two phases, still the round-keyed ladder): clean, 2 MINOR and 6 NIT,
  all applied. Its tokens were not recorded in the ticket. The step's MAJOR (needles freezing short of a steady
  reading) was found in round 1 at level 2.
- The rest of my week's rounds ran at levels 1 and 2 (KLANG-00348, 00349, 00370, 00374). Their MAJORs came at level 1
  or 2 (KLANG-00348's sprudel guard, KLANG-00349's knob text reset). My data adds nothing to "is one Opus max enough
  at the top": the one top round found no MAJOR to compare.

## Item 6: the tool look-out (KLANG-00329)

- **Logged, 2026-10-29:** the visuals moved into their own package (`io.peekandpoke.klang.visuals`): imports fixed by
  a script, then two compile rounds. IntelliJ Move (to package) and Move Members would have saved about ten minutes.
- **Not logged at the time, added now (the commits checked in git):** merging `Frames` into `UiWork` (2a7a4fdf7,
  10 files) and deleting `ScopeGlow` with its spec and `MotorReadings`' signal (15576a747, 10 files) were done by grep
  and hand edits. Refactor Move Members and Safe Delete would have fit.
- **My leaning:** Refactor Move first, because it touches the most files by hand. Reformat Code only after its
  differences from `/code-style` are listed (none recorded by me this week).
