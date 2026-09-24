# Application skeleton: Klang node / federation

Scaffolding only. No prose. Every sentence of the final text is written by the maintainer.

- Built: 2026-09-24, repo HEAD `0f1b774f` on `engine-redesign` (working tree had uncommitted changes).
- Targets: NLnet (Open Internet Stack calls: Restack or open call), deadline 2026-11-03. Prototype Fund
  (software infrastructure, data security), window 2026-10-01 to 2026-11-30.
- Markers: `[ME]` only the maintainer can fill. `[VERIFY]` unsure or conflicting, check before use.
  `[Qn]` = verbatim quote in `evidence/maintainer-quotes.md`. `WP §n` = `docs/whitepaper/klang-whitepaper.html`
  section n. `FED` = `docs/tasks/future/federated-song-sharing.md`.
- Numbers come from `evidence/git-numbers.txt` (script: `scripts/git-counts.sh`) unless another source is named.
- Scope warning for the whole application: the node / federation is **unbuilt**. WP Part II header: "Nearly
  everything from here on is design, not code." WP §13: "Everything in Part II is unbuilt."

---

## 1. Proposal title (3 to 5 options, short)

- "Klang nodes: a federated registry for creative code" 
- "Pinned, licensed, credited: federated sharing for live-coded music"
- "A citation network for music code, without metrics"
- "Klang federation v1: protocol, license lattice, reference node"
- `[ME]` own wording; FED title for reference: "Federated song sharing, multi-server import / export"

## 2. Abstract (limit 1,000 chars)

Note: 1,000 chars total. Pick one bullet per line below, max.

- **Problem**
  - Live coders start from empty editors; no shared corpus of patterns. Source: WP §11 "The observation".
  - Today KlangScript imports only resolve registered libraries, no server-qualified names. Source: WP §11 code
    sample; FED "The idea".
  - No accounts, no server today; songs live in browser storage. Source: WP §10 last paragraph.
- **Insight**
  - Medium is text, not audio; lineage can be exact, machine-readable. Source: WP §11.
  - Reframe: a federated package registry, not a distributed filesystem. Source: FED "Reframe"; WP §11.
  - Licensing as mechanism: license gates importability, restrictions accumulate, attribution automatic. Source:
    WP §11 "Licensing is the mechanism"; FED "Licensing & copyright".
  - "The protocol records; it does not score." Source: WP §11 box; FED "No scores".
- **What gets built** (see §3 milestones)
  - Protocol spec v1: 5 endpoints + `/.well-known/klang`. Source: WP §12 "What a node actually serves".
  - License lattice + tag-time check. Source: FED "The license algebra".
  - Reference server (`klangd` is the name used in the WP discovery example `[VERIFY]` naming), client import
    resolution + hash verification, conformance suite, operator docs + reference instance `klang.art`.
- **Key properties**
  - Immutable tags, content addressing, flatten-at-tag-time closure, `@latest` edit-time only. Source: WP §11 table.
  - Anyone can run a node; home server vendors pinned closure. Source: WP §12 "Anyone can run a node".
  - No telemetry; play counts structurally impossible. Source: WP §11 box; FED "Evidence without metrics".
  - AGPL v3; engine licence does not reach songs. Source: WP §12; `LICENSE`; `AUTHORS.MD`.

## 3. Budget and tasks (limit 4,000 chars)

Notes:
- `[ME]` hourly rate, total amount, and the NLnet amount bracket for the chosen call `[VERIFY on nlnet.nl]`.
- `[ME]` hours per milestone. No estimates given here on purpose.
- Existing assets the milestones build on: KlangScript `import` with `peekandpoke/super-song@v1.0` form (WP §11,
  FED "The idea"); sandboxed interpreter, no FS / network / eval (WP §3); CycleTime as exact cycle unit for
  mixtape windows (FED "Mixtapes"); KSP-generated wire codec with schema hash as prior art for golden fixtures
  (blog `docs/blog/2026-06-07-sixty-seven-microseconds-to-385-nanoseconds`).
- Sequencing constraint the maintainer set: sound first, then tutorials, then launch (CLAUDE.md rules register,
  guideline "Sound first", 2026-07-03; `docs/tasks/_priorities.md`). `[ME]` state how the grant timeline fits.

| # | Milestone | Scope | Deliverable | Size |
|---|-----------|-------|-------------|------|
| M1 | Protocol spec v1 | 5 endpoints + discovery doc; manifest schema (`schemaVersion`, files, pinned closure, hashes, license, `requires`); additive evolution rule; capability vs policy split; security-only retirement escape hatch | Versioned spec document; JSON schemas; golden example manifests | `[ME: hours]` |
| M2 | License lattice + tag-time check | Small lattice (FED example: CC0, CC BY, CC BY-SA); effective constraints of closure; refuse incompatible publish; NC decision (FED lean: launch without NC) | Library + tests; decision record on the license set | `[ME: hours]` |
| M3 | Reference server | Resolve / manifest / blob / versions / well-known; S3-compatible blob store behind hash keys (FED candidates: Garage, MinIO, SeaweedFS); `410 Gone` tombstones; vendoring of pinned closure on cross-server import | Deployable server, AGPL | `[ME: hours]` |
| M4 | Client import resolution + hash verification | Server-qualified import syntax `klang.art:user/song@v1.0` in KlangScript; resolve, fetch, verify locally; edit-time `@latest`, publish pins; auto credits roll from closure | KlangScript + editor changes, tests | `[ME: hours]` |
| M5 | Conformance suite | Golden wire fixtures per API version, runnable against third-party servers (FED cites Matrix federation-tester / ACME model) | Test suite + runner + docs | `[ME: hours]` |
| M6 | Operator docs + reference instance | Install guide; policy config (registration, BYO samples, quotas, license floor); `klang.art` as first instance, no special protocol status | Docs, running instance | `[ME: hours]` |
| M7? | `[ME]` optional: soak period + fresh-eyes review before v1 freeze | FED "Forever-compatibility": "Ship v1 only after a soak period + fresh-eyes review" | Review report, frozen v1 | `[ME: hours]` |

## 4. Comparison with existing efforts (limit 4,000 chars)

Notes: claims about other projects below are general knowledge unless a Klang doc is cited. Mark each `[VERIFY]`
against the project's own docs before submitting.

| Effort | What it does | What Klang borrows (source) | What differs |
|--------|--------------|-----------------------------|--------------|
| Go modules / module proxy / sumdb | Server-in-name import paths; immutable versions; proxy cache; checksum DB `[VERIFY]` | Identifier is the fetch address; proxy/cache model; sumdb idea "for later"; Go 1 compat promise as freeze model (FED "Prior art"; WP §11, §14) | Music assets + samples; license lattice at tag time; attribution as output; federation of peers, not one proxy |
| npm / crates.io | Central registries; semver; lockfiles `[VERIFY]` | Immutable tags ("npm learned this via left-pad") (WP §11 table); `requires` like npm `engines` (FED "Versioning") | No central registry; flatten closure at tag time; license gates import |
| Deno / JSR | URL imports + cache + lockfile `[VERIFY]` | Ergonomics of the import shape (FED "Prior art") | Same as above |
| IPFS | Content-addressed P2P storage `[VERIFY]` | "the IPFS insight without needing IPFS" (FED §1) | Plain HTTP + CDN; no DHT; hashes verify, any mirror may serve |
| ActivityPub / Mastodon, Matrix | Federated social / messaging; instance operators `[VERIFY]` | Operator liability model (e-mail / Mastodon, WP §12); `/.well-known` discovery like Matrix (WP §12 table) | No social layer in core: no follows, likes, feeds (FED "No scores"); ActivityPub only "if/when a social layer is wanted" (FED "Prior art") |
| Freesound | Sample library with CC licences `[VERIFY]` | Licence-per-asset idea; FED notes Freesound restricts NC (FED §3 warning) | Code + samples, composable imports, automatic credits from closure |
| Strudel / Tidal sharing | `[VERIFY]` how Strudel shares today (URL-encoded code? pastebins?); Tidal: `[VERIFY]` | Lineage Tidal → Strudel → Sprudel (CREDITS.MD; WP App. A) | Versioned, pinned, licensed imports across servers |
| Webmention / WebSub / Atom | Verified backlinks; push; feeds `[VERIFY]` | Backlinks as evidence without counters; `versions(name)` as Atom feed; WebSub optional push (FED "Evidence without metrics", "Watching a song") | Used as-is, not reinvented |

- **Why not extend them?** Collect arguments `[ME]`; candidate facts:
  - Needs: licence algebra checked at publish; closure vendoring made lawful by licence gate (WP §12 "the two
    designs are the same design"); cycle-window mixtape entries (WP §11 "One consequence").
  - `[ME]` whether a Go-proxy-compatible or OCI-registry-compatible layout was considered. No evidence found.

## 5. Technical challenges (limit 4,000 chars)

| Challenge | Why hard (source) | Current lean / plan (source) | Status |
|-----------|-------------------|------------------------------|--------|
| Freezing v1 | "Every exposed field is a forever-promise" (FED "Forever-compatibility"); "must be right the first time" (WP §12) | Ship v1 narrower than comfortable; additive evolution; feature detection over version sniffing (FED; WP §12) | open |
| Identity + migration | Server-in-name couples identity to home server, "the email problem" (WP §11 "Genuinely open"; FED "Open questions") | "Probably accept for v1, like Go did" (FED) | open |
| Automated licence compatibility | Combined work must satisfy all inputs; per-axis least-permissive (FED §3) | Tiny lattice; check at tag time; manifest carries chosen + effective terms (FED §3) | open: exact set, NC yes/no, lawyer pass (FED "Open questions"; copyright-audit task 07 in CLAUDE.md stone rule "Licensing") |
| Closure / vendoring storage | Home server keeps durable copy of pinned closure, not evictable cache (FED §2) | Hash-keyed S3-compatible store; dedup free by hash (FED "Blob layer") | open: quotas, costs `[ME]` |
| Old API versions forever | Every server is also a client; unattended instances never upgrade (FED; WP §12) | Immutability makes old responses static bytes; public conformance suite (FED; WP §12) | open |
| Reproducibility across engine versions | "A song that sounded right yesterday must sound identical today" (WP §11 table); engine still changes sound on purpose [D3] | Manifest `requires` block; pinned imports. `[ME]`: engine version pinning not specified anywhere found. `[VERIFY]` | **gap**: no design found for engine-version pinning |
| Related engine facts | Block size pinned to 128 frames "it is a tone parameter" (CLAUDE.md guardrail); seeded per-voice RNG for bit-identical reproduction [M11], commit `d3b77cc4` (2026-08-20) | | |
| Moderation / liability for samples | Uploads = moderation burden; takedown vs immutability (FED "Takedown vs immutability") | Operator policy as "liability dial"; blobs revocable (`410 Gone` + tombstone), tags immutable (FED; WP §12) | open |
| Trust / name squatting / hostile code | Hashes protect integrity not intent; imported KlangScript is someone else's code (FED "Open questions") | Sandboxed interpreter is the boundary: no FS, network, eval (WP §3) | partly built (interpreter), unaudited `[VERIFY]` |
| Private / unlisted songs | Auth across servers (FED "Open questions"; WP §11) | none yet | open |

## 6. Ecosystem and engagement (limit 2,000 chars)

- **Dependencies** (WP App. A "Platform & tools"; CREDITS.MD): Kotlin Multiplatform, KSP, Coroutines, Serialization,
  Ktor, Gradle, Kotest; CodeMirror 6 + Lezer; PixiJS, Three.js; Kraft + Ultra (maintainer's own OSS libs).
  Federation candidates: Garage / MinIO / SeaweedFS (FED "Blob layer").
- **Lineage / communities**: Tidal Cycles (Alex McLean), Strudel, Switch Angel (CREDITS.MD; WP App. A).
  TOPLAP, Algorave, ICLC: `[ME]` any contact so far? No evidence found in repo. WP §2 cites Algorave "more than
  eighty cities" `[VERIFY source link in WP]`.
- **User groups** `[ME]`: live coders, synth / sound-design learners (tutorial curriculum:
  `docs/tasks/tutorial-curriculum.md`), educators `[ME]`.
- **Honest current adoption**
  - Pre-alpha (WP header "Pre-Alpha"; WP §13).
  - Demo: klang.finzo.de; repo github.com/PeekAndPoke/klang (WP header). `[VERIFY]` repo public? stars? visitors?
  - One copyright holder (AUTHORS.MD). No external contributors found in git authors (evidence/git-numbers.txt).
  - "Klang has been a two-hands-and-one-AI project so far" (WP §14).
  - No usage metrics exist and none are planned for the federation (WP §11). `[ME]` any demo traffic numbers?
- **Engagement plan** `[ME]`: WP §14 "An invitation" lists ways in (argue with §11, write songs, tutorials,
  instruments, sample library).

## 7. Relevant background (limit 2,000 chars)

- `[ME]` professional background, years, prior OSS (Kraft, Ultra: WP App. A "Kraft & Ultra, PeekAndPoke").
- Project facts (all from `evidence/git-numbers.txt` unless noted):
  - First commit 2025-12-20 (`996af21a`); 2,083 commits reachable from HEAD at `0f1b774f`; 69 merges; 40 tags
    (`v0.1.0` .. `v0.3.15`).
  - Kotlin: 290,354 lines in 1,428 tracked `.kt` files, of which 138,886 in test source sets; 712 test files.
    `[VERIFY]` method: raw `wc -l`, blank and comment lines included. Conflicting figures elsewhere, see
    `gaps.md` G6.
  - Largest modules: sprudel 95,413; audio_be 85,760; klangscript 28,361 lines.
  - 25 blog posts (`docs/blog/`); 156 archived task docs; 11 plans; white paper (2026-08-09, updated 2026-09-10).
  - Measured engineering results (each with its blog post as evidence):
    - worklet decode 67 µs → ~385 ns (`docs/blog/2026-06-07-...`; `docs/history/2026-Q2.md`) `[VERIFY]` diary says ~400 ns
    - voice copies cut to one per event, golden of 2,822 events (`docs/blog/2026-06-06-twenty-allocations-per-note`)
    - super-osc onset holes 16 % → 0, never-ring 6/120 → 0 (DEV-DIARY 2026-08-12; `docs/blog/2026-08-12-the-fundamental-lottery`)
    - fused EQ: maintainer observed "Massive 33% reduction in cpu time on this machine" [PF3] vs agent's D0
      projection ~3 to 5 % of song CPU (evidence/maintainer-answers.md 2026-08-19T11:05) `[VERIFY]` which to cite
- Licensing done: AGPL v3 + SPDX headers across tree, `tones/` MIT (commit `eaa3c78d`, 2026-06-24; `LICENSE`).

## 8. Other funding

- `[ME]` none / list.
- `[ME]` Prototype Fund eligibility conditions `[VERIFY on prototypefund.de]`.

## 9. AI disclosure

Facts only. Wording `[ME]`. Check NLnet's current generative-AI policy text before writing `[VERIFY on nlnet.nl]`.

- **Tools used, with first evidence**
  - 2026-01-05: tonal.js port to Kotlin "heavy use of \"Junie\" for the port and gemini for cleanup and
    refactorings" (DEV-DIARY 2026-01-05).
  - 2026-01-06: KlangScript interpreter "making heavy use of Claude-Agent" (DEV-DIARY 2026-01-06).
  - First `Co-Authored-By` Claude trailer: `25082f79`, 2026-01-29, Claude Sonnet 4.5.
  - Models in trailers: Opus 5 (1M) 194, Fable 5.1 157, Fable 5 65, Opus 5 22, Sonnet 4.5 3, Opus 5.5 (1M) 3.
- **Share of commits with an AI trailer**: 444 of 2,083. 441 of them in 2026-08 and 2026-09.
  - `[VERIFY]` this **understates** AI use: diary records agent use from January, trailers only became routine in
    August 2026. `[ME]` state the real share honestly; see `gaps.md` G4.
- **Public statements already made**: WP App. A "A significant amount of Klang's code was written together with
  Claude"; WP §14 "two-hands-and-one-AI project".
- **AI-written prose in the repo** (relevant to "own words" rule):
  - Tutorials: model chosen for writing [A1]; generated corpus wiped 2026-08-15 (`92f6d54f`), rewritten
    "hand-written and certified" per DEV-DIARY 2026-08-15 `[VERIFY]` "hand-written" vs [A1].
  - Blog series: "The writing must be done by Fable" [A3].
  - White paper: revised by agents in session `928ac1d3-7d47-4c4c-8839-8efba7f202b9` [F4]. `[ME]` who drafted it.
  - DEV-DIARY v0.3.0 to v0.3.8 entries: written by agent, commit `3d7baebe` (2026-09-07, Opus 5 trailer).
  - FED doc: "Captured 2026-08-01 from a design conversation" (FED header); committed `9871556b`; no transcript.
  - This skeleton: assembled by Claude Opus 5.5 in session `dbad704f-33a2-421c-8bea-da8abfbb673c`, 2026-09-24.
- **Division of labour** (evidence: design-decisions skeleton ch. 0 and ch. 9; quotes register)
  - Maintainer: architecture, DSL shape, naming, every by-ear verdict, review regime, priorities, go / no-go.
  - Agents: implementation, reviews, measurements, drafts of plans and docs, option lists for decisions.
- **Logs that exist**
  - 35 local Claude Code transcripts, `~/.claude/projects/-opt-dev-peekandpoke-klang/*.jsonl`, 2026-08-15 to
    2026-09-24 (1.3 GB folder incl. HTML renders `session-*.html`). `combined_transcripts.html`: **not present**.
  - Extracted: 1,096 maintainer messages (`evidence/maintainer-messages.txt`), 72 multiple-choice answers
    (`evidence/maintainer-answers.md`), 125 verified quotes (`evidence/maintainer-quotes.md`).
  - **No transcripts before 2026-08-15.** Dec 2025 to mid-Aug 2026 has git + diary only. See `gaps.md` G1 to G3.
  - Git history with trailers; `.claude/skills/` (review loop, agent fleet, DSL design) as the documented process.
