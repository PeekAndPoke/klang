# The Videos page: a shelf of external tutorial videos

Status: DONE 2026-09-08. Maintainer side quest: "in the ui i want to add an additional page with
useful tutorial videos i found online. We will diaply the videos as cards. The videos will have
tags. We will also need to tell 'external' content from our own content (should this ever be a
thing). The page will be mounted in the main menu under the '...' and the above 'Credits'."

Shipped in one sitting, with one round of maintainer feedback on the link styling. This document
is the record of the shape and of the two traps the next person will otherwise walk into.

## What is there

| File | Role |
|---|---|
| `src/commonMain/kotlin/pages/videos/VideoModel.kt` | `Video`, `VideoTag`, `VideoOrigin`, `VideoSource` |
| `src/commonMain/kotlin/pages/videos/VideoRegistry.kt` | `allVideos`, the shelf itself, newest find on top |
| `src/jsMain/kotlin/pages/VideosPage.kt` | the page: search, tag filters, cards, in place player |
| `src/jsMain/kotlin/nav.kt` | `Nav.videos` = `/videos`, mounted in the `MenuLayout` block |
| `src/jsMain/kotlin/layouts/SidebarMenu.kt` | `State.Videos`, the "Videos" item (film icon) above "Credits" |

Model in `commonMain` next to the tutorials model, page in `jsMain` next to the other pages. The
same split the tutorials use, for the same reason: the content is data, the rendering is not.

## The decisions, and why

**External stays visibly external.** `VideoOrigin` is `External` or `Klang`. Every card names its
author (the field is not nullable: we credit people, ours included), carries an origin label, and
always offers the way out to the platform the video lives on. `Klang` renders today (a gold label)
even though nothing uses it yet, so the day we make our own video the shelf does not need a change.

**`VideoSource` is sealed, not a URL string.** `YouTube(videoId)` knows four things: `platform`,
`watchUrl`, `thumbnailUrl` and `embedUrl(autoplay)`. A second platform is a new implementation of
that interface, never new fields on `Video`. This is the wire-types-over-strings habit applied to
a UI model: the id is the truth, every URL is derived from it.

**`VideoTag` is its own vocabulary, deliberately not `TutorialTag`.** The shelf collects general
audio and production craft, which is cut along different lines than the Klang curriculum
(`Frequencies` is a video tag; the curriculum has no such lesson axis). Two entries so far,
`Mixing` and `Frequencies`. Rule for the enum: only tags a video actually carries, so no filter
button can ever match nothing.

**Playing happens in the page, not in a new tab.** Clicking the still swaps it for the embedded
player, one at a time (`playingId`), so two soundtracks can never collide. The embed goes through
`youtube-nocookie.com`, so no YouTube tracking cookie is set until a viewer chooses to press play,
and `autoplay=1` is passed only on that click, which the browser counts as a user gesture.

## Two traps

**1. `a { color: gold !important }` is global.** `src/jsMain/resources/css/klang.css:171` paints
every anchor gold with `!important`. A plain inline `css { color = ... }` loses to it. The
"YouTube" link therefore sets its color as `put("color", "${laf.textPrimary} !important")`. Any
future link on any page that must not be gold needs the same treatment. (The author link in the
card meta is left gold on purpose: it is a link to a channel and reads as one.)

**2. YouTube stills are 4:3, videos are 16:9.** `hqdefault.jpg` is the only size that reliably
exists, and it is 480x360 with letterbox bars. The card puts it in an `aspect-ratio: 16 / 9` box
with `object-fit: cover`, which crops exactly the bars away. Do not "fix" this by switching to
`maxresdefault.jpg`: that one is missing for plenty of videos and would leave holes in the grid.

## Adding a video

Append to `allVideos` in `VideoRegistry.kt`. The title and channel come straight from YouTube:

```bash
curl -s "https://www.youtube.com/oembed?url=<watch url, percent encoded>&format=json"
```

Take `title` and `author_name` verbatim (a channel's own name is not ours to tidy up, hyphens and
all), write a description in our own words, pick tags (add a new `VideoTag` entry if none fits),
and keep the id a readable slug. The one thing to watch: if a channel name or title carries an
em-dash, our own prose around it still has to stay em-dash free (`/code-style` §22).

## Deliberately not built

- **No origin filter.** With nothing but external content it would be a control that does nothing.
  The label already tells the two apart; the filter is a five-line addition the day it earns its place.
- **No duration, no publish date, no view counts.** All of it would have to be fetched or hand
  maintained, and it would go stale. The shelf is a curated list, not a feed.
- **No inline transcript or notes.** If a video needs Klang-specific commentary, that commentary is
  a tutorial, not a caption.

## Verification note

The page was type-checked through the IDE (`get_file_problems`, clean on all five files) rather
than compiled: the maintainer had `:jsBrowserDevelopmentRun` running, which owns the Gradle lock,
and it runs without `--continuous`, so it does not pick up Kotlin changes by itself. Nobody has
seen the page render yet at the time of writing. First person to restart the dev server: look at
the card grid at `/videos` and at the play swap.

The step was left uncommitted at hand-off because a `TutorialScope` to `TutorialDepth` rename was
in flight in the same working tree, touching `SidebarMenu.kt` alongside this change.
