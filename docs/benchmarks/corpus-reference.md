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

Accepted on 2026-11-11, tree `fe49e8ab7`.

| song | code | hash | peak |
|---|---|---|---|
| Final Synthasy VII Prelude | 6fef86c8f5b39523 | ab7a8993d24eb522 | 0.3875 |
| Kokon | a88adbe8133d7f33 | fe15f22b89d038f3 | 0.7458 |
| Die Kirschblüte | ed701e4365d387e0 | eeaa45cface1e379 | 0.7651 |
| Der Schmetterling | 0317ad2559028a27 | 4cc8f298c766249e | 0.7533 |
| Gestrandet | 423f8ace04602267 | 05e887b286a8fdea | 0.0750 |
| Seltsamere Dinge | cb1ed1e79b2ac4f7 | 197aad68e34693e1 | 0.7456 |
| Stein um Stein | a3f0c513a1b09d10 | 3fef9e4879dfa999 | 0.7608 |
| Remix: Echo um Echo | 042bc3cb5dbf8d8a | 14bef83326a31217 | 0.4934 |
| Greensleeves Chant | d2e476d62ce27027 | cce77a99feee2fb9 | 0.4758 |
| A Synth Worth Lying For | 5a79d38edfd76b78 | c379d080ea520c9d | 0.7555 |
| The Synthsale Piper's Last Rave | b690a443cd5b5175 | 63c9352711c64ef7 | 0.8953 |
| Synthsturm | 1981d31bc1fc8b5e | a1cf375c34d8cf8e | 0.8277 |
| The Synthsale Piper's Farewell | b98858db3c3cb245 | 477bb421e751aaad | 0.3545 |
| Smalltown Synth | ab78a3a5ae167bd8 | 72062239c9789ab1 | 0.9109 |
| Drunken Synthlor | f076f371d34fff8c | be2e08bd5ad83869 | 0.8806 |
| frozen derSchmetterling_2026_11_03 | f9e47711f59cf9ce | 02d59112a75f0c92 | 0.7451 |
| frozen strangerThings_2026_11_03 | 8ac4ee429e216874 | 197aad68e34693e1 | 0.7456 |
| frozen piece derSchmetterling_2026_09_16 | 0b2014f8923cc377 | 70ea7aab555be549 | 0.8993 |
