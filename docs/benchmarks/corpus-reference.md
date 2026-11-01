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

Accepted on 2026-11-01, tree `1b98559e1-dirty`.

| song | code | hash | peak |
|---|---|---|---|
| Final Synthasy VII Prelude | 01cb117fe4edc1e0 | 8476798fd53deab6 | 0.3875 |
| Kokon | f84927eb36e91e88 | e0fb9820d4d23e73 | 0.7664 |
| Die Kirschblüte | 1701665b809e9c61 | 14b385ebf24f1f5c | 0.7459 |
| Der Schmetterling | 461f490e8e7b358f | f98387bb84adb9f4 | 0.7518 |
| Gestrandet | a5424878277ca92c | 77db888833a1cef1 | 0.0695 |
| Seltsamere Dinge | e46e69b6c7656cb9 | 675e2d6deb69c636 | 0.7510 |
| Stein um Stein | b98d8a48fa262fe7 | 6a43f1f384deea5d | 0.7609 |
| Remix: Echo um Echo | 3fc60ffe8bb2be10 | c11fe59933a5ae4e | 0.4931 |
| Greensleeves Chant | 16579cad1833540e | 77c222b0c3c67ee5 | 0.4578 |
| A Synth Worth Lying For | cb10099d79a66b7e | 9adc0236e36ec305 | 0.7559 |
| The Synthsale Piper's Last Rave | c211f68535874a42 | 94ea6b283d36f21c | 0.8953 |
| Synthsturm | 74464ed7811defad | 6a9e77aef79be597 | 0.8542 |
| The Synthsale Piper's Farewell | 4b9caa4a49e4bf59 | 8c6714174476f7ff | 0.3457 |
| Smalltown Synth | dc6e828e4b09ee88 | 28609e132bc7447e | 0.9108 |
| Drunken Synthlor | f076f371d34fff8c | 412580f1bdd07d07 | 0.8508 |
| frozen derSchmetterling_2026_09_25 | ad38dddae123a069 | 0a6577be2b1f8f4a | 0.9246 |
| frozen strangerThings_2026_07_03 | bd29f1f5289c3292 | 84cb8742d2ac3c7f | 0.6984 |
| frozen piece derSchmetterling_2026_09_16 | 816b24cab402d05a | 2914218ed0f62dd1 | 0.9143 |
