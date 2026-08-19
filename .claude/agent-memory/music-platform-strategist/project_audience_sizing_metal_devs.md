---
name: audience-sizing-metal-devs
description: Researched 2026-08-19 — sizing the three-ring target audience (programmer x rock/metal fan x current-or-former musician), with the age x region cut for 90s-raised DE/Nordic devs, and the finding that audience size is NOT Klang's binding constraint
metadata:
  type: project
---

# Audience sizing: programmers x metal x musicians

Researched 2026-08-19 across three rounds of Karsten's questions: (1) "how many rock and metal fans are programmers
as well, what percentage?" (2) "by age group — people who grew up in the 90s in Europe (Germany, Scandinavia)."
(3) "how many of these are actual musicians themselves or at least were for some time in their life." Motivation:
the Motör branding (umlaut, engine metaphor, raw-not-safe) deliberately leans rock/metal, so this is effectively a
target-audience sizing exercise for the aesthetic bet already made.

## Measured numbers (with quality labels)

**MEASURED — what developers listen to:**
- Stack Overflow 2022 Pulse survey, coding music, multi-select: rock **32%**, electronic/dance 27%, classical 25%,
  pop 23%, lo-fi 22%, hip hop 20%, **metal 18%**, jazz 17%, silence 19%. (Sample size not published.)
- Liquid Web 2025 study, n=1,000 developers: rock **42%**, rap/hip-hop 37%, pop 37%, EDM 21%.
- Stack Overflow 2019, free-text *primary* coding genre: **3.2%** named some form of metal.
- JetBrains survey (cited secondhand via DZone 2019, primary not verified): **17%** listen to heavy metal while coding.
- 63–88% of developers listen to music while working at least some of the time.

The 3.2% / 17% / 18% spread is **entirely a question-format artifact**: single-choice primary vs. multi-select
affinity. Do not quote them as conflicting.

**MEASURED — general population baseline:**
- YouGov US: heavy metal liked by ~**15% of men, ~5% of women** → ~10% of adults. UK hard rock/metal ~10% overall
  (14% men / 6% women).
- Statista US: rock/indie **45%** listen, metal ~**19%** listen.
- YouGov n=1,000 US adults (Apr–May 2023): classic rock net popularity **+70**, the single best-liked genre.

**MEASURED — population denominators:**
- SlashData Q1 2025: **47.2M developers worldwide** (36.5M professional), up 50% from 31M in 2022.
- Live coding scene proxies: TOPLAP Discord **~2,700 members**; Sonic Pi **~11.9k** GitHub stars (and 10,000+ UK
  classrooms via the Raspberry Pi Foundation); TidalCycles/Tidal **~2.8k** stars.
- Metal density: Finland **995 bands/million**, Sweden 536, Iceland 529, Norway 406. Germany/US lead in absolute
  band count.

**VIBE / weak evidence — do not lean on:** the 2007 Warwick (Cadwallader & Campbell) gifted-teen study where a third
of gifted 11–18s rated heavy metal top-five. Suggestive of the archetype, says nothing about programmers.

**MEASURED — the age cut (tests the "90s cohort" hypothesis):**
- YouGov US live-music poll, Aug 2023: **hard rock peaks at 20% in the 35–54 bracket** vs 14% (18–34) and 13% (55+).
  **Heavy metal 14% in 35–54 vs 10% overall.** Classic rock instead rises monotonically with age (17/24/33%).
  So hard rock and metal show a genuine mid-life *hump*, not a nostalgia artifact — but it is a ~1.3–1.5x
  over-index, not a 3x one.
- **The mechanism is measured, not folklore:** Stephens-Davidowitz / NYT Spotify analysis — musical taste
  crystallises at **13–16 for men** (favourite song released when they were on average **14**); early 20s are only
  **half** as formative as early teens. A man who was 14 in 1993–1998 was 14 during the metal/alt-rock boom.
- ⚠️ **Cuts both ways over time.** The same crystallisation mechanism means the metal-heavy cohort *ages out*.
  In 10 years the 35–50 bracket is men who were 14 in 2003–2008 (metalcore/emo), then hip-hop-formed cohorts.
  Metal is not dying (18–34 hard rock still 14%; Gen Z metalcore/deathcore skews young), but the specific
  *90s flavour* is cohort-bound. Do not hard-code Klang's identity to a cohort that is 42 now and 52 next
  platform generation.

**MEASURED — the region cut (Germany + Nordics):**
- Germany, AWA/Statista 2024: **10.3% of Germans 14+ "very much enjoy" hard rock/heavy metal = 7.25M people**;
  stable ~10% across 2022–2024. Rock+metal = **48% of all German vinyl sales**.
- Festivals: **Wacken 85,000** attendees (2023 edition sold out in 6 hours; ~30% travel from abroad),
  **Summer Breeze 40,000**.
- Nordic density: Finland **995 metal bands/million** (5,558 bands / 5.58M people), Sweden 536, Iceland 529,
  Norway 406. In Finland metal is mainstream — radio play, taught in music schools.
- Developer density in the same places: Stack Overflow 2025 (n=49,019) country distribution — **Germany 8.6%**
  (second only to the US at 20.4%), Sweden 1.7%, Denmark 0.9%, Finland 0.7%, Norway 0.7% (Nordics ≈ 4.0% combined
  from only ~27M people). SlashData: Western Europe **~9.5M** developers, Germany **~837k** software engineers.
- **The two densities genuinely multiply in the DACH + Nordics corridor.** This is the one place where the
  region half of Karsten's hypothesis is strongly supported.

**MEASURED — developer age distribution:**
- Stack Overflow 2025 (n=49,019): 18–24 **18.7%**, 25–34 **33.6%**, 35–44 **26.9%**, 45–54 **12.8%**, 55–64 5.3%,
  65+ 1.9%. The **35–50 window ≈ 34–35%** of developers.
- SlashData: the developer population **is ageing** — 18–24 fell 33%→23% (2022→2025), 35–44 rose 22%→26%.
  The age cell Karsten is pointing at is *growing*, not shrinking.

**MEASURED — the musician ring (base rates):**
- **German National Cohort (NAKO), Berlin-Mitte, n=6,717 adults, median age 51:** **53% had been musically active
  at least once in life** (56.1% women / 43.9% men); **23.5% active in the last 12 months**; musical activity
  peaked at ages **11–20 (75%** of the ever-active group). → **~30 percentage points of German adults are LAPSED
  musicians** — a larger group than the currently-active one. This is the single most useful number in the file.
- German MIZ (first representative amateur-music survey): **19%** of the population aged 6+ makes music as a
  hobby = **14 million people**.
- US Gallup/NAMM: **52–54% of households** have someone who plays; **37%** of individuals say they play.
- UK Music for All: 28% currently play (up from 21% in 2005).
- **Fender (2019): 90% of new guitarists quit within the first year.** CEO Andy Mooney: *"we don't have a problem
  attracting new entrants, we have a member retention issue."* The lapsed-musician pool is continuously refilled.

**INFERRED — is musicianship enriched among programmers?** The "coders are secretly musicians" claim is
*everywhere* in tech writing (HuffPost, Atlassian, Coding Horror, TheServerSide) and the proposed mechanism is
abstract pattern recognition rather than maths. **No hard data was found — no developer survey asks the question.**
Treat as **VIBE**; apply at most a 1.0–1.2x enrichment and never cite it as fact.

**INFERRED — is musicianship enriched among metal fans?** Metal-studies scholarship is consistent that the
subculture is unusually *participatory* (fans become musicians and vice-versa; guitar culture is central), but
**no percentage exists**. Treat as soft evidence; apply ~1.1–1.3x.

## Fermi estimate, both directions

**Direction A — what share of programmers are rock/metal affine?** Metal at identity level: **12–20%, central ~15%**
(bracketed by three independent surveys). Rock broadly: **35–45%**. *Confidence: medium-high.*

**Direction B — what share of rock/metal fans program?** Developers are ~47.2M of ~5.7B adults ≈ 0.8%. Metal fandom
skews male and 18–45, which are the coding-dense demographics, so apply a 2–3x demographic lift: **~1.5–2.5%
globally**, **~4–6% in dev-dense Western countries**. *Confidence: low-medium — an inference chain, not a
measurement.*

**The lift finding (important and slightly deflating):** developer metal affinity (~15%) vs. all adults (~10%) looks
like a 1.5x lift, but compared to the right reference class — **men aged 25–45** — the baseline is already ~15–20%
and the lift **mostly vanishes (~1.0–1.3x)**. The metalhead-programmer is real as an archetype but is **not** a
strong statistical correlation beyond gender and age composition. Do not claim programmers are unusually metal.

## Cross-genre comparison (added 2026-08-19, round 4)

⚠️ **Methodological caveat that shapes the whole table: every developer-genre number available is
*coding-context*, not identity.** Both SO 2022 ("favourite music to code to") and Liquid Web 2025 ("most
frequently listen to while coding") ask what plays in the background at work. This **inflates instrumental
genres** (classical, lo-fi, soundtracks, electronic, and oddly metal — the "vokills" are unintelligible so they
function as texture) and **deflates lyric-forward genres** (pop, hip-hop, country). No survey anywhere asks
developers what music they *identify with*. Confidence: medium. Adjust mentally before ranking.

| Genre | Dev share (coding ctx) | General population | Maker density | Engine fit |
|---|---|---|---|---|
| **Electronic / techno / house** (⚠️ aggregate — unbundled below) | **27%** SO, 21% EDM LW | DE 14–19: **61.3%**; DE = world's biggest electronic market (IMS 2026); 12% of DE artist streaming | **Very high** — you cannot be an electronic musician without being a producer; fan/maker boundary is thinnest here | **Native.** Patterns, loops, synthesis, no recording, no vocals needed |
| **Lo-fi / ambient** | **22%** SO | Barely registers as a general genre | **High** within a small base; almost exclusively bedroom-made; aesthetic *is* loops + texture | **Native.** Low-stakes, forgiving, loop-shaped |
| **Hip-hop / rap** | 20% SO, **37%** LW | US 37–40%; DE 14–19: **79.6%**; German-language rap = **23% of DE streaming revenue** (2023) | **Very high, and youngest.** BeatStars **10M creators** ($400M+ paid out); SoundCloud 14M new tracks/month, 71% indie/unsigned | **Strong for beats** (mini-notation ≈ step sequencer) but **vocals are the genre's core and Klang cannot sing** |
| **Rock / metal / punk** | Rock **32%** SO / **42%** LW; metal 18% SO | US rock/indie 45%, metal 19%; DE rock/pop **72.2%**, hard rock/metal 10.3% | **High but band-shaped** — needs other people, gear, space. Guitar: ~50M players worldwide (0.7%), US 26.3M; Fender: **90% quit in year one** | **Weak-to-medium.** Riff timing, guitar timbre and the "too machine-perfect to be a band" problem (on record from the Gemini genre review) |
| **Jazz** | 17% SO | Bottom-5 in **21 of 21 countries** (Statista); US 18%; DE 4.75M "highly interested", 1.5% market share | **Very high per capita** (jazz fandom is disproportionately players) but a tiny base | **Medium.** Improvisation is philosophically close to live coding; swing feel and harmony are hard |
| **Classical** | **25%** SO | DE ~12% of teens, 2.2% market share; 10% of Germans attend opera yearly | **High but *reproductive*** — DE has 60,000+ amateur choirs, 810 amateur orchestras (30,300 players), 25% of amateur musicians sing in a choir. They perform written works, they do not compose | **Weak.** Notation-centric, expressive performance, not pattern-native |
| **Pop** | 23% SO, **37%** LW | US 40%, DE very high | **Low.** Largest fandom, thinnest maker culture | **Weak.** Vocal-centric |

### Unbundling "electronic" — techno as its own lane (added 2026-08-19, round 6)

Karsten caught that row 1 aggregated three different things. Techno is **not** a subset to be handled by a
generic electronic flagship; it is the strongest single-subgenre case Klang has.

**Is techno/rave still a thing? Emphatically yes, arguably in a second peak (MEASURED):**
- **Beatport genre ranking 2025: techno / tech house / house are #1, #2, #3** (techno and tech house trade the
  top spot depending on the cut), then deep house, DnB, electronica, melodic house & techno, trance,
  progressive house, indie dance.
- **Berlin techno entered Germany's national UNESCO intangible-cultural-heritage inventory on 2024-03-13**
  (campaign by Rave the Planet, filed Nov 2022).
- **Hard-techno revival is Gen Z + TikTok driven (2025):** Sara Landry, Novah, Nicolas Julian; per IMS 2026 the
  share of tracks above **180 BPM has risen three years running**, **schranz uploads +83%** in 2025, speed
  garage hashtag **+147% YoY**, 5.7bn electronic-music creations on TikTok.
- **Festival scale:** Awakenings (NL) **80,000** from 80+ countries; Nature One (DE) **65,000** in 2025, up from
  50,000 in 2024; Time Warp (Mannheim) in its 32nd year.
- **Boiler Room: 5.16M YouTube subscribers, ~283M viewers/month**; Solomun's Tulum set ~**74M views**, Carl Cox
  Ibiza **65M+**. → The "watch a person operate machines" format already has a colossal audience. This is the
  closest existing analogue to "watch code make music."
- Germany is the **world's biggest electronic market** (IMS 2026); global electronic industry **$15.1bn**, +7%.

**Honest counter-signals (MEASURED):**
- **Clubsterben is real.** Watergate closed 2024 after 22 years; Renate hit lease expiry then a fire; SchwuZ
  filed for bankruptcy 2024. **Berlin Club Commission: 43% of clubs affected by rising commercial rents.**
- **Techno is an event culture, not a streaming culture.** In Germany **73% of German-artist streaming success
  goes to rappers; electronic captures only 12%.** → A techno flagship buys *scene legitimacy*, not stream
  counts. Judge it on a different metric than a lo-fi track.

**Two cohorts, which is rare and valuable:** the 90s originals (Love Parade ran 1989–2003, peaking at
**1.5 million** attendees in 1999; Tresor and Mayday both founded 1991) *and* the current Gen Z hard-techno
wave. Techno is one of very few genres where the 35–50 and under-25 cohorts are both live, in different
subgenres.

**Regional split within electronic:** techno's heartland is **Germany (Berlin epicentre post-Wall), Belgium, the
Netherlands, Italy, UK**; **EDM festival-mainstage skews US** (EDC's audience heavily under 25); **DnB is a UK
resurgence among college-age listeners**; trance sits further down Beatport.

**→ Recommendation: break techno out as its own lane with its own flagship slot.** Reasons: (1) **best engine
fit of any subgenre, full stop** — 4/4 kick, 16-step hats, filtered stabs, a resonant filter opening over 32
bars is *literally* sprudel patterns plus signal modulation, with no vocals, no recording, no acoustic
caricature and therefore no engine handicap; (2) **algorave is literally rave culture** — the live-coding
scene's own event format is named after it, so this is lineage, not adjacency; (3) Germany is the heartland,
Karsten's home market, and the biggest electronic market on earth.

**ANSWERED 2026-08-19 — techno is SEMI-NATIVE and is now a SPLIT LANE.** Karsten: *"Yes i was exposed to techno
and euro-dance in the 90's and have some clue of how this music sounds and works but i have no idea about the
modern variant."* → **90s techno + eurodance = founder-reachable; modern techno = contributor-dependent.**
Full reasoning and the flagship sequencing are in `.claude/vision/decisions.md` (amendment to the genre-lanes
entry). Headlines: a deliberately-90s flagship is cohort-targeted, not dated (the metal and 90s-techno
flagships hit the *same* cohort, since that cohort's taste crystallised during both booms); much of the current
hard-techno wave is itself 90s revivalism (schranz is a 90s German subgenre), so the gap is smaller than it
feels; **eurodance is a tutorial genre, not a flagship** (comedy converts worse than craft — a flagship must
make someone want to open the code); and **never gate a flagship on closing the modern gap**, because listening
buys recognition, not the by-ear production judgement that is the entire reason the founder owns a lane.
**Sequencing: metal → 90s techno → (contributors: modern techno, hip-hop, lo-fi).**

**Contributor impact (good news for the dependency loop, point 9):** algorave / Strudel / TidalCycles people are
**techno-adjacent by default** — it is the scene's native genre. So "find an electronic-native contributor" and
"find a live-coding contributor" are **the same search, not two**. That collapses two dependencies into one and
materially de-risks the loop.

**Supporting maker-economy numbers (MEASURED):** ~3M people actively use music-production software (2024);
6.1M independent artists use plugin-based tools (+38% since 2022); IMS 2026 — creator-technology tools hit
**63M monthly active users** and $333M revenue (+651% since 2023); global electronic industry **$15.1B**, +7%.

## The two-sided model (Karsten's reframe, 2026-08-19 — ADOPTED)

**Karsten's framing, verbatim:** *"the split between coders and 'just' music lovers is fine. Klang is intended to
be the coding platform, but users can also build full songs and this would attract the bigger portion of people as
listeners."*

This resolves the maker-funnel-is-small problem rather than fighting it. Two audiences, different sizes,
different acquisition channels:

- **Makers** = the coder × genre-affine × musician funnel sized above (order 10^4–10^6). Small, high-engagement,
  they produce the catalogue. Reached through **developer** channels.
- **Listeners** = everyone who likes the genre a given song is in. Orders of magnitude bigger. Reached through
  **the music itself**. → Every general-population genre share in the table above now doubles as a listener-market
  estimate.

**Why "full songs, not loops" is the load-bearing decision:** a loop is not consumable content; a song is. This is
a real divergence from Strudel/Tidal culture, which is performance- and snippet-centric. The 14 built-in songs are
already the seed inventory.

**Precedent for the listener→maker ladder (MEASURED):**
- **Scratch is the closest structural analogue** and it validates "see inside": 7M+ users, 10M projects,
  **2.6M remixes**; **30%+ of recently shared projects were remixes** (Mar 2021); ~17% of a 1.27M-project sample
  carried remix status. A platform whose whole premise is openable artifacts sustains a ~17–30% remix rate.
- **BandLab: 100M+ registered users, 15M tracks uploaded/month**, fork-and-remix built in. This is the size of the
  prize *if the maker bar drops below code* — relevant to how klangblocks is positioned.
- Participation inequality (90-9-1 / 1% rule): classic ratio is 90% lurk / 9% edit / 1% create, but modern data
  varies widely (small communities ~33% creators; 10k–50k communities ~20%). Use as a range, not a law.
- **Algorave audience research (soft but pointed): non-coders gave the MOST positive responses to projected
  screens.** Visible code is not merely tolerated by non-programmers, it is actively enjoyed as spectacle.
  Caveat from the same literature: whether they parse meaning is doubtful; the mystique may be doing the work.

**The trap, stated honestly (MEASURED, and brutal):** "listeners will come for the music" assumes the catalogue
competes with all recorded music. **106,000 new tracks are uploaded to streaming services every day** (Luminate
2025, up 7% from 99,000 in 2024). **88%** of the 253M ISRC'd songs have under 1,000 streams; ~121M were streamed
0–10 times; **86.88% of Spotify's catalogue earns nothing.** Suno alone generates ~7M songs/day. Music is the most
oversupplied good on earth.

**The resolution — and this is the key insight:** the listener side does **not** compete on "is this a good song."
It competes on **"is this an interesting artifact."** Nobody opens a Scratch project because it is the best game
ever made. **The realistic bar is therefore: the song must be good enough that the code is worth opening** — not
good enough to beat Spotify. That is roughly an order of magnitude easier. The actual failure mode is not
"not Spotify-grade," it is **"so obviously inert that nobody is curious about the machine that made it."**
→ This is exactly why the Master Boot Record finding in [[reference_contributor_prospects]] matters: if Klang's
synth-rock is heard as *deliberately* machine-made rather than *failing* to be a band, the quality bar moves.

**Launch-gate implication (connects to the "stable and presentable" gate):** "presentable" means different things
per side. For makers: the editor holds up, the tutorial ladder reaches first sound fast. For listeners:
**there must already BE a catalogue** — a song-sharing platform with 14 songs is not a listener product. Classic
cold-start, and the resolution is that the founder's own catalogue is the seed. **Reframes
[[project_disco_strategy]]: the drop cadence is not marketing, it is inventory production.**

## The sweet-spot funnel — DE/Nordic developers, 35–50, metal-affine, current-or-former musicians

Four multiplicative steps, each ±30%. Chain (central path):

| Step | Rate | Result |
|---|---|---|
| DE + Nordic developers (all ages) | SlashData DE ~837k prof. + Nordics via SO country ratio | **1.2–1.6M** |
| × aged 35–50 | 34–35% (SO 2025) | **420k–560k** |
| × metal-affine | 20–25% (DE base 10.3% × 1.35 age × 1.5 male skew ≈ 21%; converges with dev surveys' 17–18%) | **85k–140k** |
| × **ever** a musician | 55–70% (NAKO 53% + participation enrichment) | **≈ 50k–100k** |
| × **currently** a musician | 25–35% | **≈ 20k–50k** |

**Headline: the innermost ring is ~35,000–120,000 people, central estimate ~65,000.** Widen the geography and it
grows fast: **~350k** across Western Europe, **~1–1.5M** worldwide.

**Verdict on the hypothesis:** *directionally supported, with one correction and one warning.* The age hump is
real and measured (not nostalgia); the taste-crystallisation mechanism explains exactly why; and the DE/Nordic
region effect is the strongest part of the whole thesis. **Correction:** the age over-index is ~1.3–1.5x, not the
several-fold effect the phrasing implies. **Warning:** it is a moving target that ages with the cohort.

## The conclusion that actually matters

Absolute sizes: metal-affine developers ≈ **7M** worldwide; rock-affine ≈ **19M**; developers who already listen to
music while working ≈ **30–41M**. Currently active live coders across *all* tools and genres: order **10^4**,
maybe 10^5 with lurkers.

**That is a three-order-of-magnitude conversion gap. Audience size is not Klang's binding constraint at any
plausible launch scale** — 0.1% of the metal-affine dev population is 7,000 people, already larger than the entire
TOPLAP Discord. The binding constraint is the **"wait, I could make this" moment**: converting a developer who
*consumes* music daily into one who *makes* it.

## How to apply

1. **Recruit through the coding side, never the metal side.** ~15% of coders like metal; only ~1–2% of metal fans
   code. Metal press, festivals and r/Metal are a bad funnel; developer channels with metal *flavor* are a good one.
   This independently validates [[project_disco_strategy]]'s platform tiers (dev Twitter/X, HN, GitHub, YT Shorts).
   The Tier 5 artists in [[reference_contributor_prospects]] are the deliberate exception — the ask there is
   borrowed reach, not conversion.
2. **The branding is a signal, not a filter — but the *output* can become a filter.** Umlaut/engine branding reads
   as irreverent-tech even to non-metalheads. The actual narrowing risk is demo material: if everything Klang
   publishes sounds like synth-metal, the other ~85% conclude the tool only does that. The 55+ genre spread in
   [[project_tutorial_master_plan]] is the correct hedge and now has a second, independent justification.
3. **Geography multiplies.** The Nordics + DACH corridor is simultaneously metal-dense and developer-dense, and is
   Karsten's home market. Best place for the first real-world push.
4. **Reframe the top of the funnel** as "the 30–41M developers who already listen to music while working" — every
   one has a daily relationship with music and none is being invited to make any.
5. **THE LAPSED MUSICIAN IS THE CORE PERSONA — write it into [[project_disco_strategy]]'s messaging.** ~30pp of
   German adults made music once and stopped; 75% of ever-musicians were active as *adolescents*; Fender says 90%
   of beginners quit in year one and calls it a retention problem, not an attraction problem. The highest-affinity
   Klang user is therefore not a beginner and not a live coder: they are someone who **already knows how a riff is
   supposed to sound and already thinks in structures**, who quit because of friction (band logistics, practice
   time, gear, nowhere to be bad in private) rather than lack of interest or talent.
   → **Positioning language: not "learn to make music" — they already did that. Closer to "you still know how this
   goes."** Lead with friction removal (no band, no room, no gear, no schedule, nobody watching), not with
   instruction. This also fits [[feedback_design_for_adults]] exactly.
6. **The 35–50 age cell is growing, which buys time.** SlashData shows developers ageing (35–44: 22%→26% in three
   years). But per the crystallisation mechanism the *90s-metal* flavour of that cell will drift; treat "90s metal
   dev" as a beachhead, never as the permanent identity of the product.
7. **Genre priority under the two-sided lens (revised ranking).**
   **1st Electronic/techno/house** — high on all three axes (dev share, maker density, native engine fit), and
   Germany is the world's biggest electronic market. The volume audience.
   **2nd Hip-hop — the biggest mover, and the non-obvious call.** In the maker-only analysis it ranked low
   because *Klang cannot sing*. Under the two-sided lens it jumps, because beat-making is the one genre where
   **the instrumental IS the finished product** — the entire BeatStars economy (10M creators, $400M+ paid out)
   is instrumentals sold as products. A missing vocal is a "type beat" waiting for a rapper, a feature rather
   than a defect. Highest maker density, youngest cohort, and no live-coding tool currently speaks to it.
   **3rd Lo-fi/ambient** — best listener-hours-per-unit-effort of anything on the list (passive-listening
   audience is colossal), cheapest to make sound good, most forgiving of machine-ness.
   **4th Rock/metal** — weakest engine fit, but **maximum identity value, and identity is a listener-side
   asset.** Its job is not volume, it is being the reason anyone remembers which platform this was.
   **5th Jazz** — tiny listener base but the most musician-dense fandom on the list, so it converts
   listener→maker at an unusually high rate per view.
   **6th Pop** — biggest listener share of all, but Klang cannot sing and pop without vocals is just
   instrumental. Realistic role is pop-adjacent instrumentals, not pop.
   **7th Classical** — its amateur base is large but *reproductive* (performing written works), which is the
   wrong muscle for a generative pattern engine.
8. **GENRE LANES DECIDED 2026-08-19** (full entry in `.claude/vision/decisions.md`): Karsten personally owns
   **rock/metal/punk** (hardest engine fit + his native identity + the only lane where the by-ear call cannot be
   delegated). **Electronic, hip-hop, lo-fi need genre-native contributors.** **Jazz, pop, classical are out for
   now**; pop is contingent on *recording* vocals (⚠️ distinct from the planned phoneme `sing()` synthesis
   feature — do not conflate, phoneme singing does not unlock pop).
   → **This makes contributor recruitment the critical path for the top-priority catalogue lanes**, which is a
   promotion from "nice to have." See the dependency loop below.
9. **The three-way dependency loop (named 2026-08-19).** Catalogue needs contributors → contributors need a
   presentable platform → presentable-*for-listeners* needs a catalogue. **How it breaks:** Karsten's metal
   catalogue plus the genre-spread tutorial library is the seed that *proves range* before genre-native
   contributors arrive; and the first contributors are **makers recruited through developer channels** (where the
   research says the funnel works: ~15% of coders are metal-affine vs ~1–2% of metal fans coding), **not
   listeners**. Only the listener side truly requires catalogue depth, and it is the last of the three to open.
10. **"Look out for people" operationally, without touching the sound-first phase order:** extend Tier 1–4 of
   [[reference_contributor_prospects]] with **genre-native** entries (electronic/techno, beatmaker/hip-hop,
   lo-fi) rather than only tool-makers. List-keeping is already the sanctioned pre-gate activity, so widening
   the list's *axis* costs nothing and violates nothing. Best hunting grounds per the recruit-through-coding
   finding: the Strudel/TidalCycles community (already electronic-native and code-native — the overlap is
   pre-solved), Sonic Pi's education circle, and the demoscene/chiptune world (machine-made music is the
   native aesthetic, and the audience already codes).
11. **FLAGSHIP SONG PER GENRE + DISSECTION TUTORIALS — direction set 2026-08-19** (full entry in
   `.claude/vision/decisions.md`). ⚠️ **Long-term, NOT near-term.** Karsten's sequencing: basic concept ladder
   first, flagship deep-dive "tracks" later. One flagship song per in-scope genre, each with a tutorial that
   takes it apart. Closes four loops simultaneously: listener-side inventory, proof-of-range against
   genre-typecasting, the listener→maker conversion ladder made literal (cf. Scratch's 17–30% remix rate), and
   the exact "you still know how this goes" moment for the lapsed-musician persona. Supplies the curriculum's
   **missing second axis**: concept-based ladder (forwards from primitives) vs. genre-based song-anchored deep
   dives (backwards from a finished piece) — two axes, not competitors. Scales up
   [[feedback_tutorial_workflow]] from jingle-sized to song-sized.
   **Cautions:** non-native-genre flagships inherit the contributor dependency (point 9); and **a mediocre
   flagship is worse than none**, because it *is* the calling card for that genre.
12. **Blocks are the listener→maker bridge.** Scratch's measured 17–30% remix rate comes from a platform where
   "see inside" shows *blocks*, not text. If a listener lands on a Klang song page, the block view is plausibly
   the lower-friction door and the code view the deeper one. Strategic observation only, not a technical call.
