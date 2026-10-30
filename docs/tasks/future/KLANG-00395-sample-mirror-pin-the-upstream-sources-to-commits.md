# KLANG-00395: Sample mirror: pin the upstream sources to commits

> **Status (2026-10-30): IDEA.**
>
> **Part of:** KLANG-00356 (Sound sources: instruments, samples, soundfonts)

## Why this exists

From the weekly agent meeting of 2026-10-30 (MC-00017, items 3d and 7). The incident line of 2026-10-23 said the sample
manifests load from an unpinned `main`; checked at the meeting, the app does not: since 2026-08-18 (`3979f9ba`) it loads
every sample from our mirror, `klang-assets.finzo.de` (`SampleCatalogue.default`, KLANG-00182). What still reads an
unpinned upstream is the tooling that builds the mirror:

- `SampleMirror.originManifests` (`src/jvmMain/kotlin/SampleMirrorMain.kt`): six manifests on
  `raw.githubusercontent.com/.../main/...` (uzu-drumkit, and five from felixroos/dough-samples).
- `console/bootstrap-samples-from-git.sh`: `git clone --depth 1` of each upstream's default branch.

So a mirror run takes whatever upstream `main` holds that day: a changed, moved or removed sample would reach every
song that uses it after the next upload, and nobody would see it in a diff here. The meeting's dependency rule 4 (the
maintainer, 2026-10-30, "1 to 4 sound good", MC-00035): external data we fetch falls under the same rule as code, a
pinned source and a refresh only with the maintainer's OK.

## Decide before implementing

- **Pin or copy:** pin each upstream to a commit sha (the manifest URL and the clone take the sha), or treat our mirror
  as the source of truth and keep a copy of the deployed tree (KLANG-00182 already says "a rebuild is not a backup").
  Both together are possible: the shas record where the mirror came from.
- **Who refreshes:** a new sha only with the maintainer's OK, like a version bump.

## The work

- The six manifest URLs and the bootstrap clones on recorded shas, in one table both read (they must stay in step,
  KLANG-00182).
- The mirror tool's `--verify` reports when the deployed manifest differs from the pinned one.
- KLANG-00182 notes the shas and how to move one.

## Acceptance

- No mirror tool or script reads an upstream branch; each source names a commit.
- A dry run of the mirror at the pinned shas changes nothing on the deployed mirror.

## History

- 2026-10-30: created.
