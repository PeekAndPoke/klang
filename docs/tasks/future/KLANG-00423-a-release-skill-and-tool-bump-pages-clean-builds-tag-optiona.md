# KLANG-00423: A release skill and tool: bump, pages, clean builds, tag, optional deploy

> **Status (2026-11-10): IDEA.** Waits for the frontend refactoring (KLANG-00422); four questions for the maintainer.

## Why this exists

The maintainer (2026-11-10): "we have a release skill already? If not we need to create one. It needs to do the
following things:

1. bump the version in the root gradle.properties to what the user specifies
2. update: Topic Map, Mission Log, Engine Health, plus two new pages: Engine Cpu Performance / Allocations, Sprudel
   Performance ... these pages must be built from data automatically, this means we need automation to first gather
   the data and then build the pages. The performance pages might also include a history of previous runs and
   comparison and trend graphs.
3. clean js audio backend, rebuild js audio backend. It is important to clear it first otherwise it might not
   rebuild correctly: `./gradlew :audio_jsworklet:clean` and then
   `./gradlew :audio_jsworklet:jsBrowserProductionWebpack`
4. build the web frontend (for now only this one, as there is no server yet) via `./gradlew :jsBrowserDistribution`
5. optional "param" to also deploy to finzo via `./console/deploy-finzo.sh` or no deploy if not given"

There is no release skill (checked 2026-11-10: 19 skills, none about releasing). It waits until the frontend
refactoring (KLANG-00422) is done (the maintainer, the same day).

## What exists

| Step | Exists | Missing |
|---|---|---|
| 1. The version | `VERSION_NAME` in `gradle.properties` (0.8.12.2 on 2026-11-10), the one place that holds it | the bump |
| 2. Topic Map, Mission Log | `console/dev-status/build.py` | its `curation.py` is hand judgement: the build warns for every ticket it does not know |
| 2. Engine Health | `console/dev-status/engine_health.py`, from the newest probe run in `docs/audio-audit/<date>/` | running the probes first |
| 2. The two performance pages | not yet: KLANG-00424 | the data, the history, the pages |
| 3. The worklet | the two Gradle tasks | nothing |
| 4. The web frontend | `:jsBrowserDistribution` | nothing |
| 5. The deploy | `console/deploy-finzo.sh` | nothing |

## What the maintainer's list leaves out (proposed additions)

1. **A commit before the build.** `deploy-finzo.sh` names the release after the commit in the build's
   `version.json` and marks it `-dirty` when the tree has uncommitted changes: the bump and the pages built anew are
   committed before step 4.
2. **A gate before anything is built:** `console/full-check.sh`, `ticket check`, `docs-lint`. A red check stops the
   release.
3. **A git tag** on the release commit. The tags stop at `v0.8.9`.
4. **Which user builds.** The agents build into `.build-claude/`, the deploy script uploads `./build/dist/...`, the
   maintainer's own: an agent that builds and deploys would upload the maintainer's last build. The script follows
   the build root, or the tool hands it over. Whether the agents' user has the ssh key for finzo is not known.
5. **The performance numbers belong to a machine:** each run records where it ran, a history compares only runs of
   one machine. The benchmarks take time: a switch to use the last run's data.
6. **The card previews** (`console/dev-status/previews.sh`) when a page's look changed; the two new pages get cards
   on the Dev status page.
7. **Release notes or a line in the diary,** drafted from the commits since the last tag.
8. **The samples** are a deploy of their own (`console/deploy-samples-finzo.sh`): the skill asks whether they changed.

## Proposal

Two halves, by who can do what (the maintainer, 2026-11-10: "I need to run the script and gradle build [...] the
console/deploy-finzo.sh points to my output folders not to claude's. So ideally we will have a
console/prepare-release.sh or similar that does all the mechanical steps. Maybe we still need the skill for the
things that claude can do before the script, like updating the version, setting a tag etc.").

- **The skill, `/release <version>`, an agent's half, before the script:** the gate, the bump, the data and the
  pages with what needs judgement (the curation's warnings, the notes), the commit, the tag. It ends by naming the
  script to run.
- **The script, `console/prepare-release.sh [deploy]`, the maintainer's half:** it checks first that the tree is
  clean and that HEAD carries the tag of the version in `gradle.properties` (so it cannot build something the skill
  did not prepare), then cleans and builds the worklet, builds the frontend, and with `deploy` runs
  `console/deploy-finzo.sh`. It stops at the first failure and says where.

Why the split is right, checked on 2026-11-10: an agent can build (its distribution is in
`.build-claude/_root/dist/js/productionExecutable`, the same files for the same commit), but it cannot deploy: the
agents' user has no `finzo` host in its ssh setup, and `deploy-finzo.sh` uploads `./build/dist/...`, the
maintainer's folder. One more reason for one builder: the worklet's production copy lands in the source tree
(`src/jsMain/resources/klang-worklet.js`), which both users share.

## Decide before implementing

- **D1, who runs a release. Decided** (2026-11-10): an agent prepares (the skill), the maintainer builds and
  deploys (the script).
- **D2, the tag:** only made, or made and pushed.
- **D3, the version in the songs' browser keys.** A song's code, title, tempo and bookmarks are kept under
  `song-<version>-<songId>-...` (`StorageKeys`, used by `CodeSongPage`): after a bump, what a user changed in a song,
  and a user's own new song, no longer show. Meant (a built-in song starts fresh when a release changes it), or to
  be changed; KLANG-00422 turns the songs' storage into a data class anyway.
- **D4, without the performance pages first?** The skill can ship with the three pages there are and take the new
  ones up when KLANG-00424 has them.

## The work

1. `console/prepare-release.sh`: its checks (a clean tree, the tag on HEAD), the worklet cleaned and built, the
   frontend built, the deploy on request.
2. The skill: the gate, the bump, the pages, the commit, the tag; a line in the console-tools skill and in
   `CLAUDE.md`.
3. One real release with both, its result checked by hand.

## Acceptance

- `/release <version>` leaves a committed, tagged tree; `console/prepare-release.sh` builds from it a
  `version.json` that names that commit, not `-dirty`.
- With `deploy`, the live site serves that version; without it, nothing leaves the machine.
- A red gate or a failed build stops it, and it says where.

## History

- 2026-11-10: created from the maintainer's list; waits for KLANG-00422.
- 2026-11-10: split in two: a skill for an agent's part, `console/prepare-release.sh` for the maintainer's (D1).
