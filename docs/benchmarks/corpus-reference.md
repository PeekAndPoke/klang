# The corpus reference

What the corpus sounds like, one line per song: the first 16 hex digits of a SHA-256 of the engine's output
after the master, and the peak. Written by `./gradlew runSongBenchmark --args=corpus-accept`, checked by
`./gradlew runSongBenchmark --args=corpus` (and `console/full-check.sh`), never edited by hand. A commit that
changes the sound on purpose carries its new reference, so this file's history lists every deliberate sound
change. The method is in `src/jvmMain/kotlin/CorpusCheck.kt`.

Accepted on 2026-10-13, tree `305b6614`.

| song | hash | peak |
|---|---|---|
| Final Synthasy VII Prelude | f91ffdccb17b59e3 | 0.3866 |
| Kokon | eff99e5e3d78dca1 | 0.7487 |
| Die Kirschblüte | a18405ffedce68aa | 0.7038 |
| Der Schmetterling | 07a31c45f7b077c9 | 0.7593 |
| Gestrandet | 09ee233fd4a79694 | 0.0550 |
| Seltsamere Dinge | 7e832e7e800d6336 | 0.9217 |
| Stein um Stein | 6dfae9e37f91e993 | 0.9066 |
| Remix: Echo um Echo | b2875ce90a8bdc7f | 0.4931 |
| Greensleeves | bac90329a1dd4b4d | 0.4896 |
| A Synth Worth Lying For | 852e15e20976fc7e | 0.9220 |
| The Synthsale Piper's Last Rave | 6c614aa345afbdac | 0.9064 |
| Synthsturm | 9d388ab23f98b3d2 | 0.8641 |
| The Synthsale Piper's Farewell | 55cbbd8c4b93e528 | 0.3457 |
| Smalltown Synth | 9643b2874b42c492 | 0.9108 |
| Drunken Synthlor | d274594ba5b2fdc0 | 0.8508 |
| frozen derSchmetterling_2026_09_25 | 31108000e4bbdb24 | 0.9249 |
| frozen strangerThings_2026_07_03 | bdc0904443f2e42a | 0.6663 |
| frozen piece derSchmetterling_2026_09_16 | 0fc662a01aaecd03 | 0.9144 |
