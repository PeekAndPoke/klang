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

Accepted on 2026-11-06, tree `88e2eb91d-dirty`.

| song | code | hash | peak |
|---|---|---|---|
| Final Synthasy VII Prelude | 6fef86c8f5b39523 | 8476798fd53deab6 | 0.3875 |
| Kokon | 8ee0cf44bc09c461 | 2c20c429e8ea53e2 | 0.7447 |
| Die Kirschblüte | db76c546476008ce | c1064f4c803f4f81 | 0.7459 |
| Der Schmetterling | f419fd946dd51ef4 | d12bce52adbacbd3 | 0.7451 |
| Gestrandet | a5424878277ca92c | 7acc692002af30e3 | 0.0695 |
| Seltsamere Dinge | 8ac4ee429e216874 | dd00564c2c987379 | 0.7510 |
| Stein um Stein | 788bf52d51dead9c | fe591e17597da62f | 0.7605 |
| Remix: Echo um Echo | 3dd63fdaef403ad3 | 236e7939d6ab097d | 0.4881 |
| Greensleeves Chant | 16579cad1833540e | 31eecb0993e7af12 | 0.4578 |
| A Synth Worth Lying For | 852c94175774f739 | 4184ab87d3fe2752 | 0.7555 |
| The Synthsale Piper's Last Rave | 152941900caceb8f | 105de81e127eeb33 | 0.8952 |
| Synthsturm | e951b133312d65cc | 58bc6fd7755762a7 | 0.8540 |
| The Synthsale Piper's Farewell | 4b9caa4a49e4bf59 | fa2534f7e00c3c95 | 0.3457 |
| Smalltown Synth | dc6e828e4b09ee88 | 28609e132bc7447e | 0.9108 |
| Drunken Synthlor | f076f371d34fff8c | d954297da688a42c | 0.8508 |
| frozen derSchmetterling_2026_11_03 | f9e47711f59cf9ce | 44186af93e2fdce1 | 0.7446 |
| frozen strangerThings_2026_11_03 | 8ac4ee429e216874 | dd00564c2c987379 | 0.7510 |
| frozen piece derSchmetterling_2026_09_16 | 0b2014f8923cc377 | d2c9f30da07ee1e9 | 0.9143 |
