# Sample-cache test fixture

Committed offline fixture for `KlangOfflineRendererSampleTest`.

This is a pre-populated `DiskUrlCache` (see `audio_fe`): filenames are
`sha256(url) + "." + extension` of the mirror urls the test would otherwise download —
`index/` holds the set manifests, alias and GM soundfont index, `samples/` the few wav
files the test actually plays. With the fixture present, the test runs fully offline
and deterministically.

To regenerate (e.g. after `SampleCatalogue.MIRROR_BASE` or the manifests change):

1. Delete `index/` and `samples/` here.
2. Run `KlangOfflineRendererSampleTest` once with network — it re-caches from the mirror.
3. Commit the new files.
