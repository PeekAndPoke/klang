# Projekt Klangbuch — The Code-Music Canon Klang Could Be

> Status: Draft — 2026-05-03
> Codename: **Projekt Klangbuch**. Internal handle only — user-facing branding is unsettled.
> This is future / parking work. Not a build plan. Not a commitment to ship. Not a date.
> Companion: `projekt-klangbuch-rollen.md` for the role taxonomy this depends on.

---

## Purpose

Capture the strategic shape of the Klang platform — the corpus thesis, the hard
constraints that protect Klang from the legal traps that would explode the Motör,
and the sequencing against the current 12-month plan — so the thinking is parked,
not lost.

---

## The thesis in one line

Live-music-coding is missing what every other music genre takes for granted: a
corpus of canonical, citable, reusable songs to draw from. **Projekt Klangbuch
is the proposal that Klang become that corpus** — not a host-everyone's-files
platform, but the missing canon for code-as-music.

---

## The gap

Pop, rock, electronic, hip-hop, jazz — every modern music genre sits on decades
of recorded source material. Sample-based genres literally couldn't exist without
the corpus. Producers don't start from a blank page — they start from "the breaks."

Live coders today open empty editors. There is no canon. No "Funky Drummer" of
klangscript, no "Amen break" of patterns. Strudel, Hydra, TidalCycles each have a
handful of demos and a few community gists. That's it.

The platform value isn't *more remixes* — it is **legible lineage**. When someone
makes something good on Klang, you can read its ancestry like a Wikipedia citation
chain. No audio-stem platform can offer this, because their medium is binary.
Klang's medium is text.

`BuiltInSongs.kt` (~12 entries today) is the proto-Klangbuch. The platform vision
is: that file, but as a living, browseable, citable canon — and the song count
crossing two orders of magnitude over time.

---

## Defensible niche

- **Audio/stem layer** (SoundCloud / BandLab / Splice) — Klang loses. Decade head-start, license deals, millions of
  users.
- **Loop/sample layer** (Splice) — Klang loses. Sample library = licensing = money.
- **Code-as-music canon** — *only* layer where Klang is the natural home, not a tourist. Competitors (Strudel, Hydra,
  TidalCycles, Sonic Pi) have not built remix-as-citation as a first-class feature.

Code-as-canon is the **spine of the platform, not the skin.** Most users (Hörer —
see roles memo) will never touch the Klangbuch directly. The tuner is not a
Klangbuch entry. A recorder lesson is not a Klangbuch entry. Songs and reusable
building blocks are. Don't let elegance pull every part of the platform into a
corpus shape.

---

## The hard rule: no user-uploaded audio in Das Klangbuch

This is load-bearing, not a v1 nicety. The split that protects Klang from the
takedown inbox:

| Surface                                                   | Policy                                                                   | Why                                                                                          |
|-----------------------------------------------------------|--------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **The editor** (running klangscript locally / in browser) | Anything goes. `import("https://...")` works for any URL the user types. | Runtime, not publication. User's responsibility.                                             |
| **Das Klangbuch** (the published canon)                   | **No external URLs. No user-uploaded audio. Built-in sample bank only.** | The canon is what creates publisher exposure. Bound the surface; the takedowns can't follow. |

The moment user-uploaded audio enters the canon, Klang becomes a hosting platform
under DMCA — registered agent, takedown workflow, repeat-infringer policy, an
inbox that scales with success. The traceable citation graph makes it *worse*,
not better: every reference is a legal trail attached to the URL.

Klang stays in cover-song / mechanical-license territory (well-trodden legally)
*only* if the canon is code-against-built-in-samples-only.

The built-in sample bank becomes a curation problem PaP controls:

- CC0 / public-domain recordings
- Original recordings PaP commissions
- Curated guest packs under explicit licensing (later, when there's reason)

Finite. Controllable. Legally clean. Also a brand asset — the Klang sample bank
has a *sound* that's identifiable.

---

## Library shape, not network shape

- **Library**: PaP curates a small high-quality canon. Editorial weight. Users browse, run, sample, attribute. The
  platform has *taste*.
- **Network**: users publish, follow, like, comment. Corpus emerges from activity. The platform has *traffic*.

**Library first. Network later.** An empty network is dead. A small curated
library with 50 great entries is alive. Once the library has gravity, opening
contribution makes sense.

This matches the founder's stated posture: *not* "look what I built with Claude
over the weekend" but **"Claude and I spent a year together — this is what we
achieved."** That posture is library-shape.

---

## Sequencing — against month 5/12

Sound First holds. Engine, editor, UI come before any platform infrastructure.

| Phase                         | What happens                                                                                                                                                                                           |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Now → launch (month 5–12)** | Engine. Editor. UI. PaP-led seed canon of ~30–60 entries running on built-in samples. Karsten as Archivar-in-chief; composer friends as beta testers. **No user publishing. No accounts. No git.**     |
| **Post-launch year 1**        | If real engagement, build simplest possible save backend (Postgres + a `songs` table is enough). Watch what users actually share before designing remix mechanics.                                     |
| **Post-launch year 2+**       | If a community has formed and remix-by-citation is what users are asking for, revisit storage architecture. Could be git, could be Postgres + content-addressed table — depends on observed mechanics. |

The trap to avoid: building infrastructure because it's intellectually elegant,
before knowing whether anyone wants to remix anything. Cathedral before parishioners.

---

## What month 12 needs (and doesn't)

| Needed at launch                                                                     | Not needed at launch  |
|--------------------------------------------------------------------------------------|-----------------------|
| Engine that sounds great                                                             | Remix-by-hash imports |
| Editor approaching flawless                                                          | Discovery feed        |
| Appealing UI                                                                         | User publishing       |
| ~30–60 curated Klangbuch entries on built-in samples                                 | Account systems       |
| Clear "no external assets in the canon" contributor doc                              | Git infrastructure    |
| Roles visible enough that Project Disco viewers can locate themselves in the picture | Real-time co-edit     |

---

## Open questions (deferred to later conversations)

- **What "sharing" actually means** — share-link with code-in-URL? embeddable player? something else? *(Next
  conversation.)*
- **What "remixing" actually means** — fork the whole song? cite a snippet inline? compose from N citations? *(Next
  conversation.)*
- **Storage** — derived from the answers above. Until those settle, the storage question is premature.
- **Klangbauer as a fourth role** — see roles memo.
- **Contributor-gate wording** — "Become an Archivar of Das Klangbuch" reads stiff in mixed-language form. Open.
- **User-facing brand vocabulary** — entirely unsettled. *Klangbuch* is the codename, not necessarily the product name.

---

## What this memo is not

- Not a build plan.
- Not a green-light for infrastructure work.
- Not a commitment to a launch shape.

A parking spot for *what kind of platform Klang could be*, so a future-Karsten or
future-Claude can pick up the thread without rerunning this conversation.
