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

## Tags

`EarTraining("Ear training")` joined `Mixing` and `Frequencies`. The Klippel listening test
carries it alone; the Sara Carter video carries it as well, because its second half is exactly
that. The rule from the videos note still holds: only tags a resource actually carries.

## Adding a website

Append a `Resource` with `ResourceSource.Website(url, imageUrl)` to `allResources`. Author and
author URL are required in spirit as much as for videos: a company page credits the company.

The preview image is ours to make, not the site's to serve: most pages offer no `og:image`, and
a hotlinked one goes stale or vanishes. Take a headless screenshot at 16:9 and ship it as a
static asset next to `klang-icon.png`:

```bash
google-chrome-stable --headless=new --hide-scrollbars --window-size=1280,720 \
    --screenshot=page.png "<url>"
convert page.png -resize 800x450 -quality 82 \
    src/jsMain/resources/images/resources/<resource id>.jpg
```

and reference it as `/images/resources/<resource id>.jpg`. Static resources are served from the
root, the same way the favicon is. The globe placeholder remains the fallback for an entry
without an image.

## Verification note

Same situation as on 2026-09-08: Gradle was held by another session (a `VoiceCullingSpec` run),
so the five files were type-checked through the IDE (`get_file_problems`, clean) and not compiled
or rendered. First person with a running dev server: look at `/resources`, check that the Klippel
card shows the globe and opens in a new tab, and that the YouTube card still swaps to the player.
