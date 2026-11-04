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

Accepted on 2026-11-03, tree `6ada80833-dirty`.

| song | code | hash | peak |
|---|---|---|---|
| Final Synthasy VII Prelude | 6fef86c8f5b39523 | 8476798fd53deab6 | 0.3875 |
| Kokon | b115fee4a521164f | 7a6fca3ccab16212 | 0.7340 |
| Die Kirschblüte | db76c546476008ce | df2f7aeab50f3f04 | 0.7459 |
| Der Schmetterling | f9e47711f59cf9ce | 4c9c76fcdd20c954 | 0.7446 |
| Gestrandet | a5424878277ca92c | 77db888833a1cef1 | 0.0695 |
| Seltsamere Dinge | 8ac4ee429e216874 | 7614cf7fcd0354e2 | 0.7510 |
| Stein um Stein | 788bf52d51dead9c | 44a6622cb3f4543c | 0.7605 |
| Remix: Echo um Echo | 3dd63fdaef403ad3 | c8ddaa321923da92 | 0.4881 |
| Greensleeves Chant | 16579cad1833540e | 77c222b0c3c67ee5 | 0.4578 |
| A Synth Worth Lying For | 852c94175774f739 | 209725b3be141562 | 0.7555 |
| The Synthsale Piper's Last Rave | 152941900caceb8f | f75c6449340d9c06 | 0.8952 |
| Synthsturm | e951b133312d65cc | 224b0ae5659ca732 | 0.8540 |
| The Synthsale Piper's Farewell | 4b9caa4a49e4bf59 | 8c6714174476f7ff | 0.3457 |
| Smalltown Synth | dc6e828e4b09ee88 | 28609e132bc7447e | 0.9108 |
| Drunken Synthlor | f076f371d34fff8c | 412580f1bdd07d07 | 0.8508 |
| frozen derSchmetterling_2026_11_03 | f9e47711f59cf9ce | 4c9c76fcdd20c954 | 0.7446 |
| frozen strangerThings_2026_11_03 | 8ac4ee429e216874 | 7614cf7fcd0354e2 | 0.7510 |
| frozen piece derSchmetterling_2026_09_16 | 0b2014f8923cc377 | c8a76ab6268a6436 | 0.9143 |
