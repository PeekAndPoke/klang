# KLANG-00465: Apps layout: studio, notstrom and common

> **Status (2026-11-18): IN PROGRESS.** Steps 1 to 4 are done: the shared module, notstrom, its scripts, and the studio as `:apps:studio`. Open: the first deploys (the maintainer's), others' files that still name the old place, and the engine work with Klang-Main-Dev.

## Why this exists

The maintainer, 2026-11-18: "It is time to show this to some friends. I have the domain notstrom.band." The band gets
an app of its own, and the repository a place for apps: a top-level `apps/`, the studio (today the root project's
`src/`) in `apps/studio`, the band in `apps/notstrom`, what both need in `apps/common`.

## Decided (the maintainer, 2026-11-18)

- Packages: everything from `src/` goes from `io.peekandpoke.klang` to `io.peekandpoke.klang.studio`; notstrom lives
  in `io.peekandpoke.klang.notstrom`. Common code for the apps (the built-in songs and jingles, and what else turns
  out to be shared) goes to `apps/common`; its package is `io.peekandpoke.klang.apps.common` (the agent's choice:
  `io.peekandpoke.klang.common` is the `common` module's).
- Notstrom: the band plays Der Schmetterling with the real engine, and walks onto the stage before the song starts.
  Its site is behind basic auth for now (an `.htaccess`).
- Servers: both on the same host. Studio: `/www/htdocs/w0057ac0/klang/studio`, still served as klang.finzo.de
  ("basically staging"; klang.art once a stable version is reached). Notstrom: `/www/htdocs/w0057ac0/klang/notstrom`,
  served as notstrom.band (DNS and the domain entry are set up).
- The order of the steps is the agent's to choose.

## The work

1. `apps/common`: the built-in songs and jingles with `Song`, out of the studio; and the live music for the band (what
   reads a playing song ahead and feeds the live act), taking what it needs as parameters.
2. `apps/notstrom`: a start screen (a browser allows sound only after a click; the strobe warning and its switch are
   there), the walk-in, then the song. The worklet and the figures are laid beside the page by the build, not copied
   into the source tree.
3. Scripts: `console/build-notstrom.sh`, `console/deploy-notstrom.sh` (versions and a `current` link as the
   studio's, the `.htaccess` with the basic auth).
4. The move: `src/` to `apps/studio` as a Gradle project of its own, the packages renamed, the root's web config with
   it, every path and command that names the old place followed (tools, scripts, skills, docs), `console/build-studio.sh`,
   `console/build-all.sh`, the studio's new server folder in `console/deploy-finzo.sh`. The worklet no longer lands in
   the source tree there either.

## What steps 1 to 3 turned out to be

- `apps/common`: the songs and jingles, the script engine's set-up, the live music for the band (what reads a
  playing song ahead), and plain helpers with specs: the browser check the two apps share, the machine's score, the
  picture quality's choice, the release's name.
- `apps/notstrom`: start screen, a check for a secure address, the studio's warning on a phone or outside Chrome, a
  sound test (the engine's dynamometer) and a picture test (the stage drawn unseen) with a combined score, the two
  options (no stroboscope, fullscreen) above "Start the show", the walk-in beginning by itself, the song without an
  end, and a drawer on mouse moves (fullscreen, oversampling, frames a second, volume, the release). Oversampling
  2x where the machine holds its rate, never on a dense display, stepping down by itself if it struggles.
- The figures are asked for with the release's name (`?v=`), which every app must pass, and cached for long.
- Open, for the engine's owners: there is no pause (a hold on the audio backend that the wake-on-click respects);
  the band still compiles the song a second time (KLANG-00450's other half), to be built together with
  Klang-Main-Dev after step 4 (the maintainer, 2026-11-18).
- Not verified without a server and ears: the caching rules, the deploy script's run, and the sound itself.

## What step 4 turned out to be

- The studio is `:apps:studio` (`apps/studio/`, packages `io.peekandpoke.klang.studio...`); the root project builds
  nothing. Its JVM tests and programs run in the repository's root folder, so their relative paths mean what they
  meant. 171 JVM tests and 253 browser tests, none lost; the production distribution holds the files it held.
- No stored document depends on a class's package (plain data classes and enums, no polymorphism), so the rename
  needed no pinned names; the one stored section without a text pin (`LAST_OPENED`) got one in `RecentsSpec`.
- The worklet and `version.json` come from the build (as in notstrom); the development bundle is named `studio.js`.
- Daily commands: `./gradlew :apps:studio:jsBrowserDevelopmentRun -t`, `:apps:studio:jvmTest`,
  `:apps:studio:jsBrowserTest`, `console/build-studio.sh`, `console/build-all.sh`.
- `console/deploy-finzo.sh` uploads to `/www/htdocs/w0057ac0/klang/studio`: the host's document root for
  klang.finzo.de is switched once to that folder's `current`.
- Still naming the old place, in files of others: `console/blog/**` and `console/dev-status/**`, two links in the
  blog post of 2026-08-12 (the docs lint's two problems), the root `CLAUDE.md` (the Credits page's path), the IDE's
  two run configurations.

## Acceptance

- `apps/notstrom` builds to a folder that plays the band with sound from a plain web server at a domain's root.
- The studio builds and passes its specs from `apps/studio`; nothing a user has stored in the browser is lost by the
  rename (stored documents keep their names).
- No generated file lands in a source tree.
- The whole-repo docs lint and the ticket check are at 0 problems; every tool and script names the new places.

## History

- 2026-11-18: created.
- 2026-11-18: steps 1 to 3 landed (b6079a2f3); step 4, the studio as `:apps:studio`, landed after two clean review rounds.
