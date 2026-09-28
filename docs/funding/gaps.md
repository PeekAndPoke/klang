# Evidence gaps

Where the evidence for a decision or a claim is thin, missing or contradictory. Built 2026-09-24.
Ordered by how much each one matters for the NLnet "own words / not mostly generated" test.

## Transcripts

- **G1. No transcripts before 2026-08-15.** Local transcripts cover 2026-08-15 to 2026-09-24 (35 sessions).
  Dec 2025 to mid-Aug 2026 has git, DEV-DIARY and docs only. Affected decisions: own interpreter (1.1), named
  params (1.3), Strudel → Sprudel (2.1), time representation (2.2), mutable voice data (2.6), Motor naming and
  DSL lineage (4.1), warmth quarter and caricature model (5), codec and early perf work (6.5), licensing.
  - Likely cause `[VERIFY]`: Claude Code deletes local transcripts after a retention period (setting
    `cleanupPeriodDays`, default 30 days). Pattern fits: the oldest surviving session `eec0ca94` was last written
    2026-08-24, about 31 days ago.
  - **Action now**: back up `~/.claude/projects/-opt-dev-peekandpoke-klang/` and raise `cleanupPeriodDays` in
    `~/.claude/settings.json`. The oldest sessions (eec0ca94, 33180650, 8b490939, 40204f0e) are next to go.
    `evidence/maintainer-messages.txt` already preserves the words, not the agent side.
  - `[ME]` other sources for early decisions: claude.ai chat exports? JetBrains Junie / AI Assistant history?
    Gemini history? (DEV-DIARY 2026-01-05 names Junie and Gemini.)
- **G2. The federation design conversation is missing.** FED says "Captured 2026-08-01 from a design
  conversation"; committed as `9871556b` ("breadcrumbs") with no AI trailer. No transcript for that date. This is
  the grant's core. Also: FED references "strategist memory" entries, so the strategist agent may have been part of
  it (`.claude/vision/`). `[ME]` where did it happen, what did you write, can it be exported?
- **G3. `combined_transcripts.html` does not exist** in the transcripts folder. Only `session-*.html` renders.
- **G4. AI trailers understate AI use.** 444 of 2,083 commits carry `Co-Authored-By`; 441 are from Aug and Sep
  2026. The diary records agent use from 2026-01-05. Commits before August carry no trailer. Do not present 444 as
  the AI share. `[ME]` give an honest estimate and say how you got it. Since 2026-09-24 the trailer is a
  rule in the CLAUDE.md register, so the count is reliable from August on and becomes complete from here.
- **G5. Messages in the extract that are not the maintainer's words.** Pastes of agent text inside user messages,
  e.g. 2026-08-29T11:03 (another agent's summary, truncated in the extract), 2026-09-09T14:51 (quotes the agent's
  question), 2026-09-18T04:41 (quotes the agent's two options). Quote only the maintainer's own lines.
  `/compact` prompts were excluded wholesale (their authorship is mixed).

## Numbers that disagree

- **G6. Lines of code.** Four figures in circulation:
  - ~230,000 lines of Kotlin, "as of August 2026" (WP App. B).
  - >430,000 LoC (maintainer, 2026-09-10T08:57 [A5]).
  - 161,364 lines (blog `docs/blog/2026-09-10-off-the-end-of-the-instrument` subtitle).
  - 290,354 lines in tracked `.kt` files, raw `wc -l` (this run, `evidence/git-numbers.txt`).
  Pick one method (e.g. `cloc`, code lines only, generated sources excluded), state it, use it everywhere.
- **G7. Worklet decode**: ~385 ns (`docs/history/2026-Q2.md`, blog title) vs ~400 ns (DEV-DIARY 2026-06-07).
- **G8. Time representations**: 4 steps (maintainer [S8]) vs "five representations" (blog 2026-05-29).
- **G9. Unified EQ gain**: 33 % CPU reduction "on this machine" (maintainer [PF3], observation) vs ~3 to 5 % of
  song CPU (agent's D0 projection, `ANS 2026-08-19T11:05`). Different scopes; find the benchmark file in
  `docs/benchmarks/` or the blog `2026-08-20-eleven-loops-one-pass` before quoting either.
- **G10. Built-in song count**: 15 files in `src/commonMain/kotlin/builtinsongs/`; WP says "More than a dozen".
  Some files may be helpers. `[VERIFY]`

## Authorship of docs

- **G11. DEV-DIARY**: entries v0.3.0 to v0.3.8 written by an agent (`3d7baebe`, 2026-09-07). Earlier entries
  committed without trailers; some read agent-written (2026-01-31 "Build status: ✅"). Run
  `git log -p --follow DEV-DIARY.MD` and mark each entry `[ME]` or agent before citing it as your record.
- **G12. White paper**: first commit 2026-08-09; revised by agents (session `928ac1d3`, 2026-08-28; session
  `9f5d38a5`, 2026-09-10). Your direction is on record ([F1], [F4]); the drafting is not. `[ME]`
- **G13. Tutorials**: "hand-written and certified" (DEV-DIARY 2026-08-15) vs model chosen to write them [A1].
  Clarify what "hand-written" meant.
- **G14. Blog posts**: written by Fable on your instruction [A3]. Not usable as "own words" evidence; usable as
  measurement records.

## Decisions with weak evidence

- **G15. Agent side of rejected proposals** (design-decisions ch. 9) is inferred from your replies, except where
  the answers file shows the agent's question. Check each row in the session transcript.
- **G16. `(Recommended)` unknown** where you picked a non-recommended option without the label showing
  (e.g. `ANS 2026-09-16T13:17` size ceiling "Keep it at 10"; `ANS 2026-09-23T11:15` "Keep it optional"). The
  extract keeps only the picked label. Look up the full option list in the session.
- **G17. Engine-version pinning** for reproducible published songs: no design found anywhere. Reviewers will ask.
- **G18. Adoption**: no usage data in the repo. Repo visibility, stars, demo traffic: `[ME]`.
- **G19. Git identities**: 35 commits by "Tenkars Berger" and 30 by "Karsten Gerber", same email as
  "PeekAndPoke". `[ME]` explain if a reviewer checks authorship.
- **G20. Community contact** (TOPLAP, Algorave, ICLC, Strudel maintainers): nothing in the repo. `[ME]`
