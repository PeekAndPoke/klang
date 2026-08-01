# Federated song sharing — multi-server import / export

> **Future / post-launch.** Captured 2026-08-01 from a design conversation — broad picture, not yet
> specified, deliberately parked ("sound first": engine → tutorials → launch come first). Sits next to
> the other far-future docs in `future/`.

## The idea (the sketch, as discussed)

KlangScript can already import files from other songs:

```klangscript
import {bass} from "peekandpoke/super-song@v1.0"
```

The vision: **anyone can run a Klang server instance**, and imports become server-qualified:

```klangscript
import {bass} from "klang.art:peekandpoke/super-song@v1.0"
```

- Imported files can themselves import from other servers → transitive, multi-hop imports.
- The user's home server keeps a **cache** of cross-server imports.
- **Versioning is song-level:** tagging a version on a song stamps *all* files of that song with the same version.
  `@latest` = an import without a version.
- Every Klang server is also obligated to provide **S3-like blob storage** for binary data (samples, rendered audio).

## Reframe: this is a federated *package registry*, not a distributed filesystem

The blob layer underneath is an object store (see §Blob layer); everything interesting above it is registry mechanics —
a much better-solved problem (npm / Go modules / Deno) than general DFS.

## Design cornerstones (agreed in the conversation)

### 1. Immutable tagged versions + content addressing

- A tagged version is **immutable, forever** — republishing `@v1.0` is an error, period (npm learned this via left-pad;
  Go enforced it from day one).
- Every file/blob is stored and referenced **by content hash**; a version tag resolves to a **manifest of hashes**.

Consequence: the caching layer mostly *disappears*. A tagged import is fetched once and cached forever — no invalidation
protocol, no coherence questions. Hash addressing means the home server can fetch content from anywhere (origin, mirror,
another cache) and **verify it locally** — the IPFS insight without needing IPFS.

### 2. Kill the many-hops problem structurally: flatten at tag time

Don't solve transitive imports with caching heroics. When `super-song@v1.0` is tagged, the **origin server resolves the
full transitive closure right then** and writes it into the version manifest — a lockfile, every transitive import
pinned to concrete version + hash. An importing server then needs exactly **one** conversation to learn everything it
must fetch; each blob is one verifiable request from wherever is cheapest. Hops never cascade at import time.

### 3. `@latest` — edit-time convenience only

Two decisions here:

- **Published songs pin everything.** An unpinned import contradicts the project's non-moving-target principle
  ([[caricature sound model]]) — a song that sounded right yesterday must sound identical today. `@latest` is resolved
  in the editor (which can offer updates); tagging/publishing pins.
- **Resolution is pull, not push** (at least initially): conditional GET (ETag) + a short TTL (minutes) on `@latest` —
  the Go module-proxy model. No subscription registry, no webhook retry queues, no dead-server bookkeeping. Since
  `@latest` then only ever affects a WIP editing session, a minutes-stale cache is fine. If real-time change
  notification is ever wanted, **WebSub** is the standardized form of the originally-sketched webhook scheme — an
  optimization later, not the foundation.

### 4. Full edge caching — the CDN payoff

Because tagged content is immutable and hash-addressed, manifests and blobs ship with
`Cache-Control: public, max-age=31536000, immutable` — **cache invalidation, the hardest CDN problem, doesn't exist in
this design**. Consequences:

- **Tiny origins are viable.** A hobbyist server on a home connection survives a viral song — the CDN eats the traffic;
  the origin serves each blob once. This is what makes "everyone runs an instance"
  credible rather than a bandwidth trap.
- Only `resolve(@latest)` is dynamic — a few bytes of JSON with a short TTL. The entire heavy path (manifests, song
  files, samples) is static.
- **The CDN is not a trusted party.** Clients verify content hashes anyway, so any CDN, mirror, or another Klang
  server's cache can serve blobs. Edge caching and federation are the same mechanism seen from two angles; the hash
  check keeps everyone honest.
- Samples (the big binaries, the expensive part of hosting music) ride the same immutable path and cache best of all.

## Minimal server API

A handful of boring HTTP endpoints; everything except #0 and #1 is immutable → CDN/HTTP-cacheable as-is. All versioned
endpoints live under a **URL version prefix** (`/v1/…`):

0. `/.well-known/klang` — the **one unchanging discovery endpoint** (RFC 8615): a tiny JSON document reporting supported
   API versions, server software, base URLs. Short TTL. (Precedent: Matrix's
   `/.well-known/matrix/*` + `/versions`.)
1. `/v1/resolve(name, "latest") → version` — the other non-cacheable one (short TTL)
2. `/v1/manifest(name, version) → { schemaVersion, files, pinned transitive closure, hashes, requires }`
3. `/v1/blob(hash) → bytes` — song files *and* samples (version-agnostic content; least likely to ever need a v2)
4. `/v1/versions(name) → list`

## Versioning — two distinct axes

**Axis 1 — protocol/API version, in the URL** (`/v1/`, `/v2/`). Composes perfectly with immutable caching: a v2 URL is a
*different* URL, so cached v1 responses never conflict and never need purging — old clients keep reading v1 from the
edge forever. Clients learn what a server speaks from
`/.well-known/klang`.

**Axis 2 — capability requirements, in the manifest.** The motivating case: a far-future native (Zig)
backend with VST plugin support — a song relying on a specific VST must say so, so a client can answer
"can I play this song / use this imported bass?" from the manifest alone, *before* fetching any blob. A `requires`
block, like npm's `engines`:

```json
"requires": {
  "backend": "native",
  "plugins": [{ "id": "org.surge.xt", "versions": ">=1.3", "hash": "..." }]
}
```

Prefer **graceful degradation** over hard failure where possible (import the patterns, stub/silence the plugin-dependent
orbit) — a product decision to make per capability.

**The rule that ties both axes to never-expiring caches: manifests evolve additively.** Each manifest carries its own
`schemaVersion`; new fields are only ever added, never reinterpreted; readers ignore unknown fields. A manifest tagged
in 2027 is byte-identical in 2035. The URL version bumps only on a genuinely breaking shape change — which should be
nearly never.

## Forever-compatibility

**All servers must be able to consume older APIs "forever" — a version upgrade should almost never retire a feature.**
In a federation this cuts both directions: every server is also a *client* of every other server, so it's "keep speaking
`/v1/`" as much as "keep serving `/v1/`" — the network will always contain unattended instances that never upgraded. The
oldest living API version is the federation's lingua franca (SMTP/DNS/HTTP-style ossification — a feature, not a bug, at
federation scale).

Consequently the API surface, though small, must be **very, very well designed**. Working rules:

- **Immutability makes old APIs nearly free.** Old-version manifest/blob responses are static bytes; the forever-burden
  concentrates on the two tiny dynamic endpoints (discovery + resolve).
- **Every exposed field is a forever-promise.** Start v1 *narrower* than feels comfortable — additive evolution means
  yes-later is always possible, unsaying never is. Ship v1 only after a soak period + fresh-eyes review, then freeze it
  Go-1-compatibility-promise style.
- **Feature detection over version sniffing.** `/.well-known/klang` lists *capabilities*, not just version numbers;
  clients ask "does this server do X?", never "is this server new enough?". URL versions become rare coarse epochs;
  evolution happens through fine-grained additive feature flags.
- **Define the escape hatch up front.** The only permissible retirement is a security hazard, with a multi-year horizon
  announced in the discovery doc. "Forever, minus security" is keepable; unqualified "forever" invites the first
  violation to destroy trust in all of it.
- **Enforce it with a public conformance suite** — golden wire fixtures (house style) covering every API version ever
  shipped, runnable by any third-party server implementation (the Matrix federation-tester / ACME model). Turns "well
  designed" into a regression test.

## Licensing & copyright — the federation's legal model

**Every published asset and song carries a mandatory license from a small, well-defined set** (chosen at publish, stored
in the manifest). This does three jobs at once:

### 1. Liability distributes to operators (the Mastodon/e-mail model)

Each server operator is the gatekeeper for accounts and answers copyright takedowns for what their instance hosts. A bad
server dies here and there; the network shrugs — the idea survives.

**Takedown vs immutability:** tags stay immutable, but blobs are **revocable** — origin serves
`410 Gone` + a tombstone reason, its CDN is purged, propagation to other servers' copies is best-effort. Deletion is a
legal/social act, not a technical guarantee (like the web at large); liability sits with whoever keeps serving *after
notice*.

### 2. Licenses legalize the server-death fix (durable vendoring)

Content addressing already solves the *trust* half of server death (any mirror serves blobs verifiably); licensing
solves the *legality* of the availability half: on cross-server import, the home server keeps a **durable copy of the
pinned transitive closure** (not an evictable cache — the Go-module-proxy model). Every song is then self-sufficient on
its home server; a dead server kills only its own songs and future-version discovery, never downstream dependents.

The mechanically-enforced rule that makes this lawful: **the license gates importability.**
Redistribution-permitting license → importable. All-rights-reserved → stream-only; the import button doesn't exist.
Permission and mechanics are the same thing.

### 3. The license algebra — restrictions accumulate

Combining assets: the result is neither "most" nor "least permissive" as a pick — a combined work must satisfy **all**
input licenses simultaneously, so restrictions accumulate (least-permissive-wins *per axis*: one BY-SA input forces
share-alike on the whole; one NC input forces non-commercial on the whole). Solved problem — the Creative Commons
compatibility chart. Design moves:

- A **tiny license lattice** so compatibility is machine-checkable: e.g. `CC0 → CC BY → CC BY-SA`.
- **Checked at tag time**: the server computes the effective constraints of the transitive closure and refuses an
  incompatible publish ("contains BY-SA material — cannot tag as CC0"). Manifest carries the author's chosen license
  *and* the effective combined terms.
- **Auto-generated credits**: nearly every CC license demands attribution, and the pinned closure IS the attribution
  list — the manifest doubles as a machine-generated credits roll (the CREDITS.MD habit at protocol level).
- **The attribution graph is a citation network** (science-style): because attribution is mechanical (the pinned closure
  is ground truth, not self-reported), the graph is honest by construction. Repeated imports = citations; community
  structure is visible in the graph itself. But per §No-scores below: the graph is a **credits record, not a scoring
  system** — the protocol records, it never ranks.
- **⚠️ STANDING WARNING — the danger of NC (non-commercial), keep this note:** NC is notoriously ambiguous (is a
  donation-funded stream commercial? an ad-supported tutorial?), it **fragments the commons** — NC assets can never mix
  into BY/BY-SA works, a one-way valve splitting the network into two incompatible pools — and it chills exactly the
  remix behavior the citation network is meant to reward (ccMixter's pain; Freesound restricts it). Strong lean: launch
  **without** NC.
- **State explicitly at publish: Klang's own AGPL does not reach user songs** — songs are user content, not derivative
  works of the engine. Users must see this in writing or the license picker scares them.

### 4. AI-assistance disclosure

No settled industry hierarchy exists yet. Prior art to anchor on: **C2PA / Content Credentials**
(signed provenance assertions incl. AI flags — borrow the vocabulary, skip the weight), **EU AI Act Art. 50**
(machine-readable marking of AI-generated content, applicable from Aug 2026 — the field is compliance-shaped, not just
philosophy), **DDEX** draft AI-involvement metadata for music, and the **US Copyright Office** line (human authorship
required; AI-assisted-with-meaningful-human-contribution is copyrightable — the legally load-bearing distinction).

Proposal: a deliberately coarse, **self-declared four-step ladder per asset** in the manifest:
`human` → `ai-assisted` (AI as tool, human authored) → `ai-cocreated` (substantial AI generation, human
curated/arranged) → `ai-generated` (autonomous, minimal human input).

- **Display, don't compute** — unlike licenses there is NO algebra: a song shows its components' tags; collapsing to one
  "effective level" would be false precision.
- **Copyright interlock:** purely `ai-generated` material may carry *no copyright at all* (US doctrine) → a CC license
  on it is void, effectively public domain. The AI tag and the license field talk to each other.
- **Enforcement is the citation network itself:** misdeclaring costs the one currency the graph mints — credibility.
  Framing is disclosure, not stigma (this project's own songs are proudly co-created; `ai-cocreated` is a description,
  not a confession).
- **AI ladder × citation network = a natural experiment.** Correlating AI-level tags with imports shows empirically *how
  much AI is the right fit* — revealed preference by remixers, not opinion war; per asset type, per community, over
  time. Caveat from science: citations measure **reusability, not quality** (methods papers are the most-cited; sample
  packs/presets are Klang's methods papers, full songs its monographs) → stratify by asset type, read plays/likes
  alongside.
- **On (dis)honesty:** (a) lies are directionally predictable (toward whatever the community rewards) → systematic bias
  blurs adjacent rungs, aggregate trends survive; (b) the asymmetry favors truth — a declaration is frozen in an
  immutable manifest forever while provenance forensics only improves; (c) **Klang can attest, not just ask**: the
  editor IS the authoring environment and knows typed-over-hours vs generated-in-one-prompt — opt-in session-based
  attestation ("tool-attested") beats pure self-declaration, an honest-author badge the dishonest can't fake.

## No scores — credits, not reputation (the leaves live in the real world)

**The regress that forced this decision (2026-08-01):** import-based citations reward the *roots*
(samples, presets, tools — the "methods papers"); finished songs are sink nodes. An earlier draft answered with
curation-as-citable-edges (mixtapes/setlists/tutorial references as manifests, faceted reputation, curator PageRank) —
but that only pushes the can down the road: who honors the mixtape maker, the DJ? Any in-system honor mechanism recurses
forever. **The only termination we see right now is outside the system** (not provably the only way — but every
in-system variant re-imports the problem).

The resolution, stated as principle:

- **The protocol records; it does not score.** The citation graph is credits + provenance + license compliance — a
  permanent honest record. **No likes, no follower counts, no play counts, no leaderboards, no computed reputation, no
  algorithmic feed. None of the social-media machinery.**
- **The humans are the roots AND the leaves.** The real roots are not in the machine — they are the people interacting
  with it. **North star: give people a way to play, express themselves, collaborate, learn, find ideas — we provide the
  machine — and if all works out, it flows back into the real world: a mixtape, a new song from existing parts, a live
  DJ set.** An instrument wants you to leave with music, not stay scrolling.
- **If you want a mixtape, you make your own** — the original meaning of a mixtape: made by hand, for someone. Its honor
  was never in metrics. (A mixtape *can* be just another manifest — mechanism stays possible; nothing counts it.)
- **Discovery = following credits, not being fed.** You find music the way you find papers: love a song → open its
  credits roll → follow the reference to whoever made that bass → browse their catalog. Pull, not push. A server's
  catalog is a library, not a feed.
- **Third parties may build social/ranking layers on the open graph** — federation working as intended — but it's not
  ours, not in the core, and the core never depends on it.
- **Nice dissolution:** citation cartels attack *scores*; with no scores in the core there is nothing to attack.
  External ranking services inherit honest data and own their own gaming problems. (This closes the cartel open-problem
  from the earlier draft.)
- The AI-ladder × citations natural experiment survives — as **research on an open dataset**, not a product feature.
- **Inert to virality (the external-hijack answer).** Link-spamming songs on outside social media can't be prevented —
  but social media's destructive loop needs an *in-system accumulator* (likes, play counts, trending) to close the
  feedback. A viral wave landing here hits a credits page; nothing inside moves or re-amplifies. You can't stop the
  wave; you refuse to build the resonance chamber.
- **Effort is the filter.** The share button's pathology is effortlessness — participation priced at zero is worth zero
  (the "hollow kick" chase). Here you cannot "share" a song, only *make* something with it: an import, a set, a
  deliberate mixtape. Effort filters for care — limits breed creativity, applied to the social layer.
- **The Probenraum model.** The federation is a network of rehearsal rooms, not a stage — doors, not spotlights. You
  make music, exchange ideas; end-listeners are not rewarded by design, *in here*. Real-world value flows stay
  real-world: the jogging mixtape pays respect and yields music; the dancer pays respect to the DJ/live-coder and bought
  a ticket. The system never tries to capture those flows — no capture, no optimization pressure, no rot.
- **Watching a song = RSS, not "follow".** "Tell me when a song I like has a new version" is a legitimate need and NOT
  social machinery — the distinction is *pull-by-interested-party* vs *push-to-audience*. Mechanism already exists:
  `versions(name)` served as an **Atom feed** makes every feed reader a notification client (zero new protocol);
  in-system, an account watch list reuses the same `resolve(@latest)` polling the home server does for imports (WebSub
  as optional push). **Invariant: the subscription list lives on the subscriber's side — the author cannot see it. No
  follower count exists anywhere**; nothing to display, optimize, or be tortured by. Notifications yes, audience metrics
  no.

### Evidence without metrics — backlinks, not counters

How does a songwriter get evidence their work lands in mixtapes, without counters? Split by verifiability:

- **Published citations are verifiable evidence.** A published mixtape manifest citing your song is an
  honest-by-construction public fact. Discovery via **Webmention-style verified backlinks** (W3C precedent): citing
  server pings the cited server → cited server *fetches the manifest and checks the claim* before believing it.
  Presented as a **list of works, never a number** — "your song appears in: …" — evidence with texture (you can listen
  to the mixtape you're in). The integer is a slot machine; the list is meaning.
- **Publication is consent to be cited; private stays private.** An unpublished jogging mixtape is a book on a home
  shelf — the author has no claim to know. Unseen use flows back through the real world (the termination point, working
  as designed).
- **Play counts are structurally impossible — not just undesirable.** Edge caching means origins never see most fetches;
  vendored copies play from other servers; offline renders play from a WAV. The play event is client-side and invisible
  to every server. Counting plays would require phone-home telemetry in the player — **surveillance built into the
  instrument**, the actual root of the metrics rot. The architecture already forbids it; keep it that way.
- **Fraud analysis (why unverifiable metrics may not exist at all):** a faked play count is invisible, unauditable,
  punishes honest operators — worse than no metric (any operator can edit their own database). Faked *citations* require
  publishing sybil mixtapes: public, permanent, attributable artifacts — graffiti signed with your own name, burning
  your server's standing under operator liability. And the final lock: **no payout function** — no leaderboard, no
  trending, no algorithm to game. Fraud requires a reward; no-scores deleted the reward. Faking isn't hard — it's
  pointless.

### Mixtapes over infinite songs — the cycle window

Klang songs are infinitely long (patterns cycle forever), so a mixtape entry cannot just reference a song — it must
specify a **cycle window**: `super-song@v1.0 [cycles 128..192)`. Consequences:

- **Making a mixtape is unavoidably authorship** — choosing *which* cycles are THE cycles is the DJ picking the break;
  sampling culture at the pattern level. Effortless consumption doesn't exist even in principle; the format demands a
  decision.
- **A mixtape is sheet music, not a tape** — playback re-performs the songs live through the engine (per-voice
  drift/jitter make each pass a fresh performance). Want the frozen jogging copy? The offline WAV renderer already
  exists (`record.sh` / KlangOfflineRenderer) — score and recording, cleanly separated.
- **Technically free:** cycles are the engine's native time unit (`CycleTime`) — the window is exact by construction.
- **Exposed song params → mixtape overrides (the `feel` idea).** Songs already carry author-defined knobs (Der
  Schmetterling: `let feel = 20.0 // 0.0 .. ice | 100.0 .. fire`). Formalize: authors **explicitly expose** chosen
  params (not every `let`) with default + range + meaning; exposed params go **into the manifest** so a mixtape editor
  renders sliders without executing the song. A mixtape entry becomes
  `(song@version, cycleWindow, overrides{feel: 63})` — not just *which* cycles but *how*: the DJ at +8 pitch, a
  conductor's marking on a fixed score. **Exposing a param is granting interpretive license** — the author defines the
  space of legitimate interpretations, the mixtape picks a point in it; the song stays a non-moving target (the exposed
  knobs are the sanctioned degrees of freedom, everything else sealed). Immutability composes: version + window +
  overrides = a fully reproducible *interpretation* that still breathes through the analog drift. → A mixtape is now a
  real artifact class: **score, window, and interpretation**.

Aligned house values: behind-glass, sound-first, design-for-adults, limits-breed-creativity (strategist memories
`project_behind_glass_principle`, `project_sound_first`, feedback
`design_for_adults`, `caricature_sound_model`).

## Instance policy — operator feature toggles

Server owners can switch features on/off per instance — e.g. **bring-your-own sample library**, open vs invite-only
registration, upload quotas/max blob size, allowed license floor, (far-future) VST hosting on the native backend.

- **Policies live in `/.well-known/klang` next to protocol capabilities** — two kinds of "what does this server do":
  *capabilities* = what the server software speaks; *policies* = what this operator permits. Clients adapt the UI from
  the discovery doc (no "upload sample" button on a no-BYO server). Precedent: Mastodon instance rules / Matrix
  capabilities.
- **Policy is the operator's liability dial** — the concrete mechanism for the gatekeeper model:
  curated-samples-only ≈ near-zero copyright exposure; BYO enabled = accepted moderation burden. Risk appetite becomes a
  config file.
- **Unifies with the manifest `requires` block:** "can I play this?" (client capability), "can I import this?" (license
  gate), and "can this song live on this server?" (policy match) are all one operation — match manifest requirements
  against a server's capabilities + policies.
- **Subtlety: uploads ≠ mirroring.** Vendored closure copies of foreign BYO samples arrive via import, not upload — a
  no-BYO-uploads server still lawfully hosts vendored copies (the license permits it), unless the operator *also*
  tightens the import license floor (a "CC0-only" instance).

## Blob layer

Content-hash-keyed blobs behind an **S3-compatible object store** — candidates from the storage discussion: **Garage**
(AGPL, Rust, built for small geo-distributed self-hosted clusters — best philosophical fit), **MinIO** (AGPL, default
choice, community console gutted 2025), **SeaweedFS**
(Apache 2.0, excels at many small files). Hash-as-key makes cross-server dedup and verification free. True POSIX DFS
(CephFS etc.) is not needed.

## Prior art to steal from

- **Go modules** — the closest match: server-in-name (`klang.art/peekandpoke/super-song`), so the identifier *is* the
  fetch URL — self-describing, federation for free, no central registry. Plus the proxy/cache model and the
  checksum-database (sumdb) idea for later.
- **Deno / JSR** — URL imports + local cache + lockfile; the ergonomics of the import shape.
- **WebSub** — standardized push notification, if ever needed.
- **ActivityPub / Matrix** — only if/when a social layer (follows, discovery, notifications) is wanted; not needed for
  imports.

## Open questions (parked with the design)

- **Identity & migration** — server-in-name couples identity to the home server (the email problem). Probably accept for
  v1, like Go did. Separate naming layer only if it ever hurts.
- **Trust / name squatting** — hashes protect *integrity*, not *intent*; imported KlangScript is code someone else
  wrote. The sandboxed interpreter is the security boundary — state that explicitly and keep it true (no FS/network
  escape from song code).
- **License-on-publish** — largely resolved into §Licensing above (mandatory license from a small lattice, checked at
  tag time). Still open: the exact license set (NC yes/no), the default selection, and a lawyer pass over the
  operator-liability + vendoring model (touches memory
  `project_licensing`).
- **Private / unlisted songs** — auth story for non-public imports across servers.
- **Relation to Klangbuch export** — exported parts must stay arrangement-free (feedback
  `klangbuch_parts_arrangement_free`); the importable unit here should follow the same rule.
- **Flagship instance** — `klang.art` is registered (strategist memory
  `project_brand_architecture_klang_motor`) and is the natural reference server.

## Links

- `future/high-performance-audio-backend.md`, `future/ir-to-modal-table-extraction.md` — sibling far-future docs.
- `.claude/vision/projekt-klangbuch.md` — the export/import product thinking this extends.
- Memory: `project_licensing`, `feedback_klangbuch_parts_arrangement_free`,
  `feedback_caricature_sound_model` (non-moving target), `project_sound_first` (why this is parked).
