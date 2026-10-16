# The corpus reference

What the corpus sounds like, one line per song: the first 16 hex digits of a SHA-256 of the song's code
(its text, its rpm and the built-in modules it imports), of the engine's output after the master (hash), and
the peak. Written by `./gradlew runSongBenchmark --args=corpus-accept`, checked by
`./gradlew runSongBenchmark --args=corpus` (and `console/full-check.sh`), never edited by hand. The method is
in `src/jvmMain/kotlin/CorpusCheck.kt`.

A `frozen` row whose sound differs is red. A live song whose code is unchanged but whose sound moved is red: the
engine or its inputs moved it. A live song whose code moved is information only, because songs move freely.
`corpus-accept` refuses while any row is red; `corpus-accept-engine` accepts a deliberate engine sound change.
Since a red row can only be written by `corpus-accept-engine`, this file's history lists every deliberate
engine sound change that moved a frozen text or an unedited live song. An engine move on a live song edited in
the same change rides along as INFO, which is why a neutrality proof reads "the corpus GREEN with no live song
edited (or accept first)".

Accepted on 2026-10-15, tree `92e58d54-dirty`.

| song | code | hash | peak |
|---|---|---|---|
| Final Synthasy VII Prelude | f286893a239c01cc | f91ffdccb17b59e3 | 0.3866 |
| Kokon | abd3d040477b1365 | eff99e5e3d78dca1 | 0.7487 |
| Die Kirschblüte | 77725fe200a5c902 | a18405ffedce68aa | 0.7038 |
| Der Schmetterling | 9c5fcba19887fe43 | 07a31c45f7b077c9 | 0.7593 |
| Gestrandet | a962895a572fc098 | 09ee233fd4a79694 | 0.0550 |
| Seltsamere Dinge | 064dc0ef445ff932 | 7e832e7e800d6336 | 0.9217 |
| Stein um Stein | 8ffa5142260feb48 | 6dfae9e37f91e993 | 0.9066 |
| Remix: Echo um Echo | 42978eab3f4bf3ba | b2875ce90a8bdc7f | 0.4931 |
| Greensleeves | d7390440bfa6e83e | bac90329a1dd4b4d | 0.4896 |
| A Synth Worth Lying For | dd43f145b709cb30 | 852e15e20976fc7e | 0.9220 |
| The Synthsale Piper's Last Rave | 754f5bd86e3aee07 | 6c614aa345afbdac | 0.9064 |
| Synthsturm | 74464ed7811defad | 9d388ab23f98b3d2 | 0.8641 |
| The Synthsale Piper's Farewell | 4b9caa4a49e4bf59 | 55cbbd8c4b93e528 | 0.3457 |
| Smalltown Synth | dc6e828e4b09ee88 | 9643b2874b42c492 | 0.9108 |
| Drunken Synthlor | f076f371d34fff8c | d274594ba5b2fdc0 | 0.8508 |
| frozen derSchmetterling_2026_09_25 | aa6418c9c6178d5f | 31108000e4bbdb24 | 0.9249 |
| frozen strangerThings_2026_07_03 | 11791dbfb0a20972 | bdc0904443f2e42a | 0.6663 |
| frozen piece derSchmetterling_2026_09_16 | b8ddc1e61b78b614 | 0fc662a01aaecd03 | 0.9144 |
