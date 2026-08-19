# Klang — strategic decision log

Dated decisions with their reasoning and what was explicitly ruled out. Newest first.
Status vocabulary: **DECIDED** · **UNDER CONSIDERATION** · **RULED OUT**.

---

## 2026-08-19 — Direction: one flagship song per in-scope genre, plus dissection tutorials

**Status: DIRECTION / UNDER CONSIDERATION.** ⚠️ **Explicitly long-term and NOT a near-term commitment.**
Karsten's own sequencing, verbatim: *"long term thing, basic tutorials first, but this could be deep dive
learning 'tracks' later."* The basic concept ladder (`docs/tasks/tutorial-curriculum.md`) keeps absolute
priority. Do not let this reorder the tutorial quarter.

**The idea.** One flagship song per in-scope genre (electronic, hip-hop, lo-fi, rock/metal/punk), each paired
with a detailed tutorial that takes that song apart, so a new user has real material to work with from day one.

### Why it is strategically dense (it closes four loops at once)

1. **Listener-side inventory.** A flagship *is* the "interesting artifact" the two-sided model needs, and the
   bar it must clear is not "beats Spotify" but "good enough that the code is worth opening."
2. **Proof of range.** A credible flagship in a genre is the strongest possible answer to genre-typecasting,
   far stronger than a tutorial jingle, because it is a whole finished song.
3. **The listener→maker ladder made concrete.** Scratch's 17–30% measured remix rate comes from exactly this
   shape: a finished artifact plus a visible inside. The dissection tutorial is the "see inside" button written
   out in prose.
4. **It is the lapsed-musician moment, precisely.** Per the persona work in
   `project_audience_sizing_metal_devs.md`: you do not *teach* this person music, they already learned it once.
   You hand them a finished song and let them open the hood. "You still know how this goes" is the whole pitch,
   and a flagship-plus-dissection is the most literal expression of it available.

### Structural value: it supplies the missing second axis for the curriculum

The current ladder is **concept-based** (A/B/C tracks, one topic per lesson, strict taught-so-far vocabulary).
A flagship dissection is **genre-based and song-anchored** — it works *backwards* from a finished piece instead
of forwards from primitives. These are **two axes, not competitors**: the concept ladder builds the vocabulary,
the genre deep-dives show the vocabulary assembled at full scale. It also scales up the established authoring
workflow (see `feedback_tutorial_workflow`: Claude frames, Karsten polishes the jingle, Claude works backwards
from it) from jingle-sized to song-sized.

### Cautions

- **Non-native genres inherit the contributor dependency** recorded in the genre-lanes decision below. A
  flagship techno or hip-hop track needs a genre-native maker, so those flagships sit behind the same gate.
- **A mediocre flagship is worse than no flagship**, because it *is* the platform's calling card in that genre.
  A weak techno flagship actively teaches visitors that Klang cannot do techno. Better to ship a genre with no
  flagship than with a bad one.
- Scope risk: song-sized tutorials are much larger than lesson-sized ones. Do not start one until the concept
  ladder is complete.

---

## 2026-08-19 — Genre lanes for the catalogue: founder owns rock/metal/punk; jazz, pop and classical are out for now

**Status: DECIDED** (Karsten, 2026-08-19), following the three-round audience-sizing research saved at
`.claude/agent-memory/music-platform-strategist/project_audience_sizing_metal_devs.md`.

**The decision.** Under the two-sided model (makers produce the catalogue, listeners consume it), genre lanes are
assigned as follows:

1. **Karsten personally owns the rock / metal / punk lane.** It is simultaneously the *hardest* genre for a
   pattern engine to render convincingly and his native musical identity. The founder takes the hard genre.
2. **Electronic, hip-hop and lo-fi (priority genres 1 to 3) need genre-native contributors.** Karsten:
   *"i need to look out for people that grew up in other genres and hope they contribute good first material."*
   **AMENDED same day (see below): techno is a SPLIT lane, not contributor-only.**
3. **Jazz, pop and classical are OUT for now.** Not ruled out permanently, deprioritised.
4. **Pop is explicitly contingent on vocals.** Karsten: *"If at some point we also allow for recording vocals pop
   might see a comeback."*

### Reasoning

- **The founder-owns-the-hard-genre move is efficient.** Rock/metal has the weakest engine fit on the table
  (riff timing, guitar timbre, the "too machine-perfect to be a band" critique) but the maximum identity value,
  and identity is a listener-side asset. It is also the one lane where taste cannot be delegated: judging whether
  synth-rock reads as *deliberately* machine-made rather than as *failing* to be a band is a by-ear call only
  someone inside the genre can make. Ties directly to the Master Boot Record finding
  (`reference_contributor_prospects.md`, Tier 5): MBR's "100% Dehumanized" aesthetic proves the destination is
  legitimate, so the job is to land there on purpose.
- **Jazz, pop and classical earn their exclusion on the data.** Jazz is bottom-five in 21 of 21 countries.
  Classical's large amateur base is *reproductive* (performing written works), the wrong muscle for a generative
  pattern engine. Pop has the largest listener share of all but is vocal-centric, and pop without vocals is just
  instrumental music.
- **The pop contingency is real but distant.** ⚠️ Two different doors, do not conflate them: the planned
  phoneme-based `sing()` robot-singing feature is *synthesis*, while *recording* vocals is audio input, storage
  and a much bigger platform step. Phoneme singing does not unlock pop on its own.

### Consequence flagged (see the dependency loop below)

Assigning the top three priority genres to contributors who do not exist yet, while contributor outreach is
gated behind the undefined "stable and presentable" bar, creates a three-way dependency loop. Named and
addressed in the same session; the break is that **Karsten's metal catalogue plus the genre-spread tutorial
library is the seed that proves range before genre-native contributors arrive**, and the first contributors are
**makers recruited through developer channels, not listeners**.

### AMENDMENT, same day — techno is a SPLIT lane

Karsten, 2026-08-19: *"Yes i was exposed to techno and euro-dance in the 90's and have some clue of how this
music sounds and works but i have no idea about the modern variant."*

- **90s-flavour techno and eurodance: FOUNDER-REACHABLE.** The by-ear judgement exists for that flavour.
- **Modern techno (hard techno, current Berlin sound, the Gen Z wave): stays CONTRIBUTOR-DEPENDENT.**

**Why this is more than a technicality.** Per the taste-crystallisation research (men's taste locks at 13–16),
a German man who was 14 between 1993 and 1998 was 14 during the Love Parade peak *and* the metal boom. **The
metal flagship and a 90s-techno flagship therefore target the exact same cohort**, which is coherence, not
scatter. A deliberately 90s-flavoured flagship is *authentic and cohort-targeted*, not dated — and the
Master Boot Record principle applies verbatim: **deliberate reads differently from failing.** Faked-modern
would be the actual risk.

**Supporting observation:** much of the current hard-techno wave *is* 90s rave revivalism (schranz is literally
a 90s German subgenre; hoover stabs and breakbeat-hardcore signifiers are back). The distance between "90s
flavour" and "current" is smaller than it feels from inside the gap.

**Eurodance ruling: tutorial genre, NOT a flagship.** It is superbly pattern-friendly (supersaw leads,
four-on-floor, piano stabs, simple diatonic loops — Klang's supersaw work is already strong) and the wink fits
the Motör brand humour. But a flagship's job is to make someone want to **open the code**, and comedy is a
weaker conversion hook than awe: cheese invites "ha" then scroll, craft invites "how did they do that." Use
eurodance where the wink is an asset (a supersaw/chord-stab lesson) and keep the flagship slot for straight,
serious 90s techno — which is also the better engine fit (less melodic content, more pattern, texture and
filter movement).

**Do NOT gate a flagship on closing the modern-techno gap.** It is the smallest knowledge gap on the board
(weeks of listening: Beatport techno top 100, Boiler Room, HÖR) but listening buys *recognition*, not
*production feel*. The whole reason the founder owns metal is that the by-ear call cannot be delegated; making
a modern-techno flagship would mean making that call without the ear. Close the gap opportunistically because
it is cheap and informs engine work, never as a prerequisite. (Also: the "Sound first" phase order means this
is not the moment for a listening project.)

### Flagship sequencing (recommended, 2026-08-19)

1. **Rock/metal** — first, as already decided. Fully native, identity-defining, only Karsten can judge it.
2. **90s techno** — the realistic second. Semi-native, *best engine fit of any genre* (no vocals, no recording,
   no acoustic caricature, therefore no handicap), same target cohort. Note: because the engine has no handicap
   here, this is plausibly the **cheapest flagship to make genuinely excellent** — if the metal flagship proves
   slow, techno overtaking it in order is a legitimate outcome, not a failure.
3. **Modern techno, hip-hop, lo-fi** — contributor territory.
4. **Eurodance** — tutorial/demo material, no flagship slot.

### Not decided / still open

- No outreach yet. The launch-phase gate from `reference_contributor_prospects.md` stands unchanged, and the
  "Sound first" phase order is not being pushed.
- Whether **lo-fi** could be a *second founder lane* rather than a contributor lane. It is arguably the least
  scene-fluency-dependent genre on the board (mostly production aesthetic, not scene literacy). Still open;
  worth a future round.

---

## 2026-08-13 — The audio engine is named "Klangmotör"

**Status: DECIDED** (Karsten, 2026-08-13). Codebase rename executed the same day; diary and history files
deliberately excluded so they preserve the old name as a historical record.

**The decision.** The audio engine is **Klangmotör**, replacing **"Klang Audio Motör"**. The ASCII spelling
**`klangmotor`** is the sanctioned identifier form — domains, handles, package names, search — and is *the same name
undressed*, not a second brand. The full brand reading becomes **"Klangmotör by Klang.art"**.

### Reasoning

1. **Inner-voice first-impression primacy (decisive).** A new brand name is always subvocalized — an unknown token
   can't be skimmed by shape recognition, it must be phonologically decoded. So the awkwardness of "Klang Audio Motör"
   was not an occasional cost paid in the founder's daily speech; it was paid **once by every new reader**, at exactly
   the moment they decide whether the thing feels coherent. *(The strategist initially mis-weighted this by anchoring
   on the founder's own speech habits; Karsten corrected it, and the correction is what settled the decision.)*
2. **The one-word / one-stress law.** Strong brands are single orthographic tokens with one unambiguous stress.
   Multi-word names get abbreviated by users — Native Instruments → NI, Teenage Engineering → TE — and you lose
   control of your own name. "Klang Audio Motör" was already collapsing to "the Motör" in practice. Word-count is the
   law; syllable-count is the symptom.
3. **Unresolved stress was friction.** KLANG audio motör / klang AUDIO motör / klang audio moTÖR — the reader had to
   choose. "Klangmotör" resolves automatically to German compound initial stress: KLANG-mo-tör.
4. **"Audio" was semantic dead weight.** "Klang" already said sound; the inner voice did work and got nothing back.
5. **It did not parse as a name.** "Klang Audio Motör" had the grammar of a product *category* — [brand] [category]
   [type], like "Bosch Audio Motor" — so first-contact readers never experienced it as a proper noun.
6. **The old name was a weak wordmark.** Three near-descriptive words (sound + audio + motor), crowded and hard to
   protect — a finding already on record from the 2026-07-09 brand session. The speakability fix and the trademark fix
   pointed the same direction.
7. **The ö earns its keep as a name-signal.** Beyond the standing "Motör always with ö" convention, the umlaut does
   visual work at first contact: it flips the parse from "sound motor" (description) to "Klangmotör" (proper noun).
8. **Behind-glass compatibility.** One word can be a badge on a gauge, splash or status bar; six syllables cannot.
   "Motör" survives as a live morpheme, so the Cylinder / Injection / Ignitor / Katalyst vocabulary still hangs off a
   named keystone, with "the Motör" as natural dev-facing shorthand.

### Ruled out

- **RULED OUT — "Klangmotor" without the umlaut, as the name.** In German loudspeaker engineering, *"Motor"* is the
  actual technical term for a driver's magnet-and-voice-coil assembly. "Klangmotor" therefore reads to a German audio
  person as a transparent, near-generic compound: "the sound-producing drive unit of a speaker." Descriptive in
  exactly our sector, and it positions the software as a speaker part. Also breaks the standing ö convention.
  *Retained only as the ASCII identifier spelling.*
- **RULED OUT — bare "Motör" as the engine name.** Would have removed the Klang-root repetition, but walks back into
  the Motörhead exposure recorded 2026-07-09 (The 2015 Kilmister Trust; active digital-music licensing). Standalone
  Motör + music sector + umlaut is the exact signal that forced "Motör Hits" → "Klang Hits". Absorbed as the second
  morpheme of a German compound, behind glass, never an app-store title, the risk is materially lower.
- **RULED OUT — keeping "Klang Audio Motör."**

### Accepted trade-off

The Klang root now appears twice in "Klangmotör by Klang.art". Judged acceptable — it reads as one family, which is
what a house brand wants.

### Still open (clearance; none of it blocking)

1. **DPMA / EUIPO register lookup** for "Klangmotor" / "Klangmotör" — not done. Those registers are JS-driven and were
   not directly queryable; all evidence below is open-web only.
2. **The one same-name artifact found is dead.** "Klangmotor," a 2014 tablet-app *portfolio concept* for
   stroke-patient fine-motor rehabilitation by Sammy Schuckert (Design Lead at IBM since 2019; his site's last post is
   2018). No distribution, no app store presence, no company, no trademark surfaced, twelve years dormant. Under
   §4 MarkenG an unregistered mark requires genuine *Verkehrsgeltung* — acquired market recognition — which a
   never-distributed concept does not have. **Assessed as not blocking.**
3. **ASCII domain/handle sweep for `klangmotor`** — .com/.de/.art plus GitHub, Twitter, Bluesky, YouTube. Unverified;
   needs whois, not search. **Decide first whether it is needed at all** — the engine is behind glass and klang.art is
   the anchor, so a dedicated engine domain may be purely defensive.
4. Minor search noise only: "klangmotorik," a dormant MySpace artist.

### How to apply

- Write **Klangmotör** in all prose, UI, marketing and documentation. The ö is non-negotiable in display text — this
  *extends* the "Motör always with ö" convention rather than replacing it.
- Use **`klangmotor`** wherever ASCII is required (URLs, handles, package/namespace identifiers). Sanctioned, not a
  violation of the ö convention.
- **Division of labor between the two marks:** Klang.art is the mark that carries consumer equity and should aspire to
  the opaque/coined standard (Ableton, Zalando, Nintendo — names that mean nothing, which is a large part of why they
  are protectable). Klangmotör is the *engine* name, where transparency is an asset because it tells a curious
  developer what the thing is. Do not judge the engine name by consumer-brand criteria.
