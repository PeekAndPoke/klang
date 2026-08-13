# Klang — strategic decision log

Dated decisions with their reasoning and what was explicitly ruled out. Newest first.
Status vocabulary: **DECIDED** · **UNDER CONSIDERATION** · **RULED OUT**.

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
