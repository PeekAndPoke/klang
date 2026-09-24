# Funding: evidence and scaffolding

Material for the NLnet (deadline 2026-11-03) and Prototype Fund (window 2026-10-01 to 2026-11-30) applications for
the Klang node / federation idea. Built 2026-09-24 at `0f1b774f` on `engine-redesign`. Assembled by Claude Opus 5.5
(session `dbad704f-33a2-421c-8bea-da8abfbb673c`). **Scaffolding only: the maintainer writes every sentence of the
application.**

## Files

| File | What it is |
|------|------------|
| `application-skeleton.md` | NLnet form sections 1 to 9 as telegraph bullets with sources, `[ME]` and `[VERIFY]` marks |
| `design-decisions-skeleton.md` | Chapters 0 to 9: decisions, iterations, evidence, rejected agent proposals |
| `gaps.md` | Where evidence is thin, missing or contradictory, plus the urgent transcript backup |
| `evidence/maintainer-messages.txt` | Every typed maintainer message, 2026-08-15 to 2026-09-24, deduplicated, UTC |
| `evidence/maintainer-answers.md` | The maintainer's 72 answers to multiple-choice questions, with the agent's question |
| `evidence/maintainer-quotes.md` | 125 curated quotes, IDs `[P1]` .. `[F4]`, each verified verbatim by script |
| `evidence/git-numbers.txt` | Saved output of `scripts/git-counts.sh` |
| `scripts/extract_maintainer_messages.py` | Regenerates the messages and answers files from the local transcripts |
| `scripts/build_quotes.py` | Holds the quote list; fails if any quote is not verbatim in the messages file |
| `scripts/git-counts.sh` | Every git number used here |

## Reproduce

```bash
# 1. maintainer words (exclude the session that built this folder)
python3 docs/funding/scripts/extract_maintainer_messages.py \
  ~/.claude/projects/-opt-dev-peekandpoke-klang dbad704f-33a2-421c-8bea-da8abfbb673c
# 2. quotes register (verifies every excerpt)
python3 docs/funding/scripts/build_quotes.py
# 3. git numbers
docs/funding/scripts/git-counts.sh > docs/funding/evidence/git-numbers.txt
```

To add a quote: append a row to `QUOTES` in `build_quotes.py`, rerun step 2.

## Rules used when building this

- Only the maintainer's own typed messages and multiple-choice answers count as their words. Excluded: tool
  results, sub-agent traffic, `/compact` prompts, command output. Messages over 2,600 chars are truncated and
  marked as pastes.
- Quotes are verbatim, typos kept. Never paraphrased as a quote.
- Numbers only from a named source; conflicts go to `gaps.md` instead of being picked silently.
- The evidence files keep agent question text verbatim, em-dashes included (deliberate deviation from the no
  em-dash rule: they are records, not our prose).

## Transcript sessions covered

Session, first and last maintainer message (UTC), number of maintainer messages. Sessions with no typed
messages are omitted. Raw files: `~/.claude/projects/-opt-dev-peekandpoke-klang/<id>.jsonl` (local, private).

| Session | First | Last | Messages |
|---------|-------|------|----------|
| `eec0ca94-f270-46e0-9314-d1af7525de45` | 2026-08-15 | 2026-08-24 | 49 |
| `33180650-9f15-4cc6-b6d4-ad10a42d7c21` | 2026-08-19 | 2026-08-21 | 69 |
| `127f0cb2-2ec9-4a8a-85dc-21e128d7ba5f` | 2026-08-20 | 2026-08-31 | 120 |
| `8b490939-1e17-4f63-8357-cd8afd4bd1ee` | 2026-08-21 | 2026-08-31 | 62 |
| `40204f0e-66da-45b7-9915-33cfb06ee228` | 2026-08-22 | 2026-08-24 | 26 |
| `34da42ea-3401-4de6-a694-34cff8a18389` | 2026-08-25 | 2026-08-25 | 1 |
| `e42d2d00-f5be-40f6-b117-6b550b0042ba` | 2026-08-27 | 2026-09-10 | 260 |
| `928ac1d3-7d47-4c4c-8839-8efba7f202b9` | 2026-08-28 | 2026-08-28 | 12 |
| `c7c72e06-791d-4822-8f93-8dd5a8539406` | 2026-08-30 | 2026-08-31 | 15 |
| `f67a088b-3cb7-4497-b8c2-0b25b14109c8` | 2026-08-31 | 2026-08-31 | 8 |
| `a2280bec-ce0a-4047-927b-25bc7a32d958` | 2026-08-31 | 2026-09-07 | 14 |
| `6e733561-b386-46dd-b702-db962f9f989b` | 2026-08-31 | 2026-08-31 | 14 |
| `9e97ee12-eaf1-481e-bf47-e51666259b07` | 2026-08-31 | 2026-08-31 | 4 |
| `8996d818-45e2-42a6-95c0-b76f83dc6045` | 2026-08-31 | 2026-08-31 | 1 |
| `8621d509-9c3f-4f89-b1b4-8a4c08e54ad2` | 2026-08-31 | 2026-08-31 | 1 |
| `743ece1a-3a76-4242-8a86-2acbb786bc6e` | 2026-09-05 | 2026-09-07 | 75 |
| `94ce85bb-52c3-416f-8b84-05ea7b5ce41f` | 2026-09-06 | 2026-09-10 | 50 |
| `f6fba18e-29b1-4ad5-b2b0-6a2b68fbc36d` | 2026-09-07 | 2026-09-07 | 6 |
| `291d20c8-6917-4ee1-9605-e138bfdabbe1` | 2026-09-07 | 2026-09-07 | 3 |
| `7c212058-4cb5-4bf1-842c-1b905a20c165` | 2026-09-07 | 2026-09-08 | 5 |
| `9b67b237-684c-4561-8194-d3010570ccc3` | 2026-09-08 | 2026-09-08 | 5 |
| `ffb74c9a-2b90-4532-9c6f-3b3b4ebc6dfa` | 2026-09-08 | 2026-09-09 | 13 |
| `23487087-3340-476a-a087-bdad83c41124` | 2026-09-09 | 2026-09-09 | 3 |
| `2d7a84cc-f293-495a-b3c0-5725b171edfa` | 2026-09-09 | 2026-09-09 | 2 |
| `9f5d38a5-8756-44bb-b7a9-570f9429c4c9` | 2026-09-10 | 2026-09-10 | 7 |
| `2b9d5146-bb91-421f-8521-3abde0cee4d4` | 2026-09-14 | 2026-09-24 | 233 |
| `9d12eb31-63a2-42c8-af9f-613662097450` | 2026-09-15 | 2026-09-15 | 8 |
| `c5121dc8-63bd-4407-a40f-7f2b09c6d865` | 2026-09-15 | 2026-09-16 | 14 |
| `351cbd90-a102-4860-8794-cbff5b7be475` | 2026-09-16 | 2026-09-16 | 16 |

Total: 1,096 messages, 72 answers.

## Google Docs

The skeletons are Markdown. In Google Docs, turn on Tools → Preferences → "Enable Markdown", then use
Edit → "Paste from Markdown" so headings, lists and tables come through. `[VERIFY]` the menu names in your
Docs version.
