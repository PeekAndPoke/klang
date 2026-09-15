# The Resources page: the video shelf, generalised to videos and links

Status: DONE 2026-09-15. Maintainer request: "in the ui we added the video section lately. I would
like to make this one more general into a 'resources' section that contains videos and other
useful links, like this one https://www.klippel.de/listeningtest/".

Supersedes `20260908-videos-page.md`, whose decisions all carry over. This note records only what
changed.

## What is there now

| File | Role |
|---|---|
| `src/commonMain/kotlin/pages/resources/ResourceModel.kt` | `Resource`, `ResourceTag`, `ResourceOrigin`, `ResourceSource` |
| `src/commonMain/kotlin/pages/resources/ResourceRegistry.kt` | `allResources`, the shelf itself, newest find on top |
| `src/jsMain/kotlin/pages/ResourcesPage.kt` | the page: search, tag filters, cards, in place player for videos |
| `src/jsMain/kotlin/nav.kt` | `Nav.resources` = `/resources` (the `/videos` route is gone, not redirected) |
| `src/jsMain/kotlin/layouts/SidebarMenu.kt` | `State.Resources`, the "Resources" item (bookmark icon) above "Credits" |

## The one new decision

**The kind of a resource lives in its source, not on the resource.** `ResourceSource` is still
sealed, but it now has a nested `Video` sub-interface. `YouTube` is a `Video` and knows how to
embed itself; `Website(url, imageUrl?)` is a plain source that only knows the way out. The card
does a `when` on the source: a video gets the still with the play overlay and the in place player,
a website gets an image (or a globe placeholder on the card background when it has none) that is
itself the link, opening in a new tab. No `kind` enum, no nullable `embedUrl`: a resource that
cannot be played has no method that claims otherwise.

`Website.platform` is derived from the URL's host with a leading `www.` dropped, so the "way out"
link in the card footer says `klippel.de` without anyone typing it a second time.

The shared fields on every source were renamed to what they mean for both kinds: `watchUrl` is
`url`, `thumbnailUrl` is `imageUrl` (non-null on `Video`, nullable on the base).

## Filters live in the URL

Follow-up 2026-09-15, maintainer: "the selected tags are not highlighted but they should be. Also
the search and the tags must be synced as url params."

`search` and `tags` are `urlParam` / `urlParams` delegates, the way `TutorialsListPage` and
`KlangScriptLibraryDocsPage` do it: `?search=hear&tags=Mixing,EarTraining`. Tag names are the
enum names, matched case insensitively, and unknown names are dropped so a stale link never
selects a tag that no longer exists. An empty value removes the parameter from the URL.

The highlight bug: a button that is always `basic` never shows the gold style. The house pattern
is `ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }`,
used by the tutorials list and the sprudel editor tools. The lexikon tag row had the same defect
and got the same one-line fix.

## Tags

`EarTraining("Ear training")` joined `Mixing` and `Frequencies`. The Klippel listening test
carries it alone; the Sara Carter video carries it as well, because its second half is exactly
that. `Tone("Tone")` came with the Jim Lill channel (added 2026-09-15 on the maintainer's
request): a Nashville session guitarist's "Tested: where does the tone come from in ..." series,
guitar, amp, cab, mic, preamp, one variable at a time. The rule from the videos note still
holds: only tags a resource actually carries.

**A YouTube channel is a `Website`, not a `Video`.** It has nothing to embed, so it takes the
plain link door. `Website.platform` derives `youtube.com` from the URL, which is what the card
footer should say. Its preview is drawn like the Klippel one: the channel's running question as
a signal chain with a gold question mark, because the channel avatar would be a hotlinked
Google image that goes stale.

## Adding a website

Append a `Resource` with `ResourceSource.Website(url, imageUrl)` to `allResources`. Author and
author URL are required in spirit as much as for videos: a company page credits the company.

The preview image is ours to make, not the site's to serve: most pages offer no `og:image`, and
a hotlinked one goes stale or vanishes. A screenshot was the first attempt and it was rejected:
a shrunken web page says nothing about what the page does. The picture must show the idea. For
the Klippel test that is a clean sine (gold) over a softly saturated one (accent blue), labelled
A and B: a blind A/B test between clean and distorted, drawn in the Klang palette.

The image is an SVG under `src/jsMain/resources/images/resources/<resource id>.svg`, 800x450 so
it fills the card's 16:9 box without cropping, referenced as `/images/resources/<id>.svg`. Static
resources are served from the root, the same way the favicon is. The globe placeholder remains
the fallback for an entry without an image.

## Verification note

Same situation as on 2026-09-08: Gradle was held by another session (a `VoiceCullingSpec` run),
so the five files were type-checked through the IDE (`get_file_problems`, clean) and not compiled
or rendered. First person with a running dev server: look at `/resources`, check that the Klippel
card shows the globe and opens in a new tab, and that the YouTube card still swaps to the player.
