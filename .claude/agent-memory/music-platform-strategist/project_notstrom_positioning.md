---
name: notstrom-positioning
description: NOTSTROM's strategic purpose (advertising vehicle for Klang), the "Band as Code" line, the no-streaming scarcity mechanic, merch rules and the domain verdict
metadata:
  type: project
---

# NOTSTROM positioning (settled 2026-08-21)

Karsten stated the purpose and the constraints directly in this session. Full decision log entry, including
the reversal audit, in `.claude/vision/decisions.md` under 2026-08-21.

## What NOTSTROM is FOR (his words)

*"The goal is not even to sell the music or the band in the first place. If everything goes well, and I am able
to come up with 10 good songs, then this is advertising for Klang."*

NOTSTROM is a **marketing vehicle for Klang**, not a music business. This is the load-bearing fact and it
settles most downstream questions.

**Why it matters:** it converts every NOTSTROM question into an advertising question, and advertising's cardinal
sin is **unattributed reach**. Anything that grows NOTSTROM's name recognition without carrying Klang's is
building equity in the wrong asset.

**How to apply:** when any NOTSTROM surface is proposed, ask "does an impression of this name Klang?" If not,
change it or drop it.

## The three stated constraints

1. **No Spotify, no YouTube.** Deliberate policy, not an unfinished task. The music is hearable only inside
   Klang or in "live concert mode."
   - ⚠️ **The trap this creates:** exclusivity raises the bar on the destination. Telling people "you can only
     hear it here" is a promise about *here*. A thin page under an exclusivity stance reads worse than a thin
     page without one. Scarcity is only a hook if arriving is a reward.
   - Side benefit: no distribution materially reduces the pending "Fleischwolf" title-reuse risk (no DSP
     metadata collision, no royalty pool).
2. **Explicit framing on the page.** No reveal is being staged, no "is this a real band?" ambiguity. The
   machine-nature is stated from the first line. This is the Master Boot Record principle executed correctly:
   deliberate reads differently from failing.
3. **The 10-good-songs gate**, set by Karsten himself. A catalogue-scale bar. It is the right gate for **merch**
   and the wrong gate for the **domain**. Decouple them.

## "Band as Code" (positioning line, 2026-08-21)

Karsten: *"I would totally market it as 'Band as Code'."*

**Strengths.** It sits in an established idiom family (Infrastructure as Code, Docs as Code), so it lands
instantly for developers with zero explanation. It is honest, short, memorable, searchable, and low-competition.
Most importantly it is the **bridge sentence**: the single phrase that connects a listener-side artifact to a
maker-side platform, which makes it the highest-value sentence in the whole NOTSTROM concept.

**⚠️ Caution: it is developer-legible, not musician-legible.** The lapsed-musician persona (see
[[project_audience_sizing_metal_devs]], "you still know how this goes") has no "X as Code" idiom and may read it
as cold or technical. It also names a *method*, not a *feeling*. **Treat it as a developer-channel line that
needs a warmer twin for musician channels.** Same artifact, two doors.

**Placement:** stronger under the Klang anchor than on its own domain, because its job is conversion and
conversion wants zero friction plus correct attribution. Related slogan state in [[motor-slogans]].

## Domain verdict (revised, 2026-08-21)

The strategist's first answer was "no, and probably not `.band` ever." **Karsten defeated two of the five
objections** (explicit copy killed the framing/comparison-set argument; the no-streaming stance killed the
implicit-promise trap) and the verdict was changed to a split:

- **Register a domain: yes**, as a *positioning asset*, never as defence.
- **`klang.art/notstrom` is canonical.** The second domain is a redirect. This follows from his own advertising
  premise, not from strategist preference.
- **Trigger: the day `klang.art/notstrom` goes live, not before.** The one surviving hard rule is that a live
  domain pointing at nothing is worse than no domain. The original trigger (3D concert scene running end to
  end) was **too high**; the 3D scene is the upgrade, not the gate.
- **TLD ranking:** `.de` if free (probably taken, crowded German generator namespace), then `.art` (signals
  house membership, cheap), then `.band`, then `.live`. Rejected: `.energy`/`.power` (the pun walks *into* the
  German-generator search collision), `.dev`/`.sh`/`.codes` (pre-sorts toward devs, repels the lapsed
  musician), `.rocks` (tonally light, brand is stark), `.music` (restricted, and he is not a music business),
  `notstr.om` (fails spoken aloud, reads as a `.com` typo).
- **Governing principle:** a redirect's only job is transmission fidelity, so cleverness in a redirect domain is
  wasted cleverness. Do not over-deliberate; ranks 2 to 4 are outcome-equivalent.
- **Standing rule that survived:** never buy a domain for name protection. A domain confers no naming rights;
  use and filings do.

## Merch rules

- **Front of the garment: the wordmark only. NOTSTROM. Nothing else.** A band shirt is a tribal signal and it
  works *because* people who do not know, do not know. A printed URL is the most reliable way to make a metal
  shirt uncool. Mechanism, not style quibble.
- The garment carries the name, the internet carries the explanation.
- Anything beyond the wordmark goes on the **back neck or inner tag**, the period-correct home for label and
  pressing marks. That is where `klang.art` or "Band as Code" belongs.
- ⚠️ **Merch creates discovery pressure on a name chosen when there was none.** "Notstrom" is a German common
  noun; the query today returns emergency generators, UPS systems and electrical contractors. The requirement is
  **owning the search result**, which is a content and page problem, not a domain-purchase problem.
- Merch waits for a tribe. Nobody buys a shirt for a band they have heard once.

## Sequencing

Nothing here overrides [[project_sound_first]]. Order: `klang.art/notstrom` page → register the redirect the
same day → 3D concert scene as the upgrade → songs accumulate toward 10 → merch only once a tribe exists.
