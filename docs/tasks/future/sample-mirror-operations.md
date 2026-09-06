# Sample mirror: operations notes and licensing audit

> **Status: reference, the mirror is live** (built 2026-08-17/18, committed). Restored 2026-09-06
> from session memory during the memory housekeeping because no repo document carried the
> operational and licensing notes.

The app loads all samples from the maintainer's mirror instead of raw.githubusercontent.com
(429 rate limits). `SampleCatalogue` is MIRROR-ONLY: origin URLs live solely in
`SampleMirror.originManifests` (jvmMain tool).

- Base URL: `https://klang-assets.finzo.de/samples` (`SampleCatalogue.MIRROR_BASE`).
  Overrides: JS `window.__KLANG_SAMPLE_BASE__`, CLI env `KLANG_SAMPLE_BASE`.
- Staging tree: sibling repo `/opt/dev/peekandpoke/peekandpoke.github.io/klang/`. The big set
  directories (uzu-drumkit, tidal-drum-machines, dirt-samples, vcsl, mridangam, piano) are
  git-ignored there; only `felixroos/gm` stays committed.
- Tooling: `./gradlew runSampleMirror` (`SampleMirrorMain.kt`, delta-only, `--verify` mode);
  upload via `console/deploy-samples-finzo.sh` (rsync); CORS `*` via `klang/.htaccess` because
  the app origin (klang.finzo.de) differs from the mirror origin. Fast rebuild:
  `console/bootstrap-samples-from-git.sh` (git clones plus a VCSL blob-filtered sparse checkout).
  Keep its excluded/alias tables in sync with `SampleMirrorMain.kt`.
- Mirrored manifests carry NO `_base`; entries resolve relative to the manifest URL (fallback
  base in `SampleIndexLoader`; a manifest `_base` always wins for origin manifests).
- The uzu-drumkit `brk` bank (Amen break) is deliberately EXCLUDED (`SampleMirror.excludedKeys`);
  todepond's alias file is replaced by our own inlined table.
- Offline test fixture: `klang/src/jvmTest/fixtures/sample-cache` (mirror-URL keys, own README).
- Size at completion: 4530 set files plus 869 GM files, 2.8 GB.

## Upstream drift (a rebuild is not a backup; keep a copy of the deployed mirror)

1. `vcsl.json` has 8 entries with the pre-reorganisation path (missing `Membranophones/`
   prefix), 404 for everyone upstream; our mirror serves them at the manifest path.
2. The GM index had 2 mangled variant names (`fible`, `fil e`) from @strudel/soundfonts, never
   downloadable; fixed in the sibling repo's `felixroos/gm/index.json` plus 2 re-extracted files.

## Licensing audit (2026-08-17, not legal advice)

- uzu-drumkit: Unlicense (clean, minus `brk`).
- VCSL: CC0 (clean).
- piano: Salamander Grand Piano, Alexander Holm, CC BY 3.0 (attribution in CREDITS.MD, the
  credits page and the whitepaper).
- mridangam: yaxu/mrid, Arthur Carabott, performed by Harishankar V Menon, CC BY-SA (attribution
  added; ShareAlike carries to the mirror).
- Dirt-Samples and ritchse/tidal-drum-machines: NO license. The maintainer accepted the risk and
  mirrored the referenced subset only; `ATTRIBUTION.md` in the mirror tree has a takedown contact.
- GM soundfont chain: webaudiofontdata, MIT (defensible).

Optional follow-ups: ask ritchse for a grant; ask yaxu to pin the CC BY-SA version.
