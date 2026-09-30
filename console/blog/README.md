# Blog pages

The blog posts in `docs/blog/<YYYY-MM-DD-slug>/index.md`, rendered to flat HTML and served next to the SPA:

- `src/jsMain/resources/blog/<slug>/index.html`: one page per post, with the post folder's web assets copied beside
  it: `png`, `jpg`, `jpeg`, `gif`, `svg`, `webp`, `avif`, `html` (interactive figure files), `wav`, `mp3`, `ogg`,
  `flac`, `mp4`, `webm`, `pdf`, `json`, `css`, `js`. Sources such as `make_fig.py` and `graphs.dot` stay in
  `docs/blog`; a link to one becomes its GitHub page.
- `src/jsMain/resources/blog/index.html`: every post as a card, newest first by `(date, slug)`, with its hero image,
  title, subtitle, date, tags and summary (clamped to four lines by CSS; the full text is in the markup). The hero is
  a web asset file of the post's own folder, a bare file name; anything else prints a WARNING and the card shows no
  image.

A post whose `status` is not `published` carries a DRAFT tag on its card and on its page; it is built and listed
like the rest. The post contract (front matter, the allowed HTML, links) is `docs/blog/howto-write-a-post.md`.

The pages share `blog.css` (the whitepaper's tokens, which are the palette of `KlangLookAndFeel`, the Chakra Petch
and Share Tech Mono faces), inlined into each page by the build. Code blocks are coloured by the build as the
whitepaper colours its code by hand: keywords (`--spark`), strings (`--code-str`), comments (`--code-cm`), one small
regex per language in `build.py` (`HIGHLIGHT`), no highlighting library. A `text` block stays plain and unlabelled. The templates are `post.template.html` and
`index.template.html`. The fonts come from Google Fonts; nothing else is loaded from outside.

## Rebuild

One time:

    pip install markdown-it-py PyYAML

Then:

    python3 console/blog/build.py            # build and write
    python3 console/blog/build.py --check    # build in memory, run every guard, write nothing

`--check` exits 1 on a guard failure and also when the committed output is stale (it lists every file that
would be written or removed as `STALE`), so it serves as a pre-commit check. Then commit `src/jsMain/resources/blog/`. The folder belongs to the build: a post or an asset that is gone from
`docs/blog` is removed from it. Files are written only when their bytes change, so a rerun with no post changed
writes nothing. The build writes and deletes only in `src/jsMain/resources/blog/` of the checkout it sits in: before
it touches anything it checks that the output folder resolves to exactly that path (not a link, not anywhere else),
and before each write or delete that the file's real path lies inside it (so a link planted inside the folder is not
written through); it refuses otherwise. `console/deploy-finzo.sh` uploads the folder to the site root with the rest of
`src/jsMain/resources/`, so the blog lives at `/blog/`.

## What the build refuses

The build prints every problem with its file and line and writes nothing when:

- a required front-matter field is missing or empty (`title`, `subtitle`, `date`, `slug`, `tags`, `summary`,
  `authors`, `status`), or `tags`/`authors` is not a list;
- the slug or the date does not match the folder name, or two posts share a slug;
- the body has HTML other than `<a id="..."></a>` anchors and the interactive figure block of the guide's section 7
  (whose frame file must be in the post folder);
- an image is not a web asset of the post folder, or a post folder holds a file named `index.html` (it would replace
  the rendered page);
- a link or image resolves to nothing: a missing file, a post that does not exist, a `#anchor` that is neither a
  heading id (GitHub's rule: `## References` is `#references`) nor an `<a id>` on the target page, a site-absolute
  path, a path out of the repository;
- a `references` entry in the front matter has no `<a id>` anchor in the body;
- a code block has no language, is indented instead of fenced, or carries a tag the build does not know (it knows
  `kotlin`, `klangscript`, `sh`, `html`, `text`), or is tagged `javascript` in a post that is not named in
  `JAVASCRIPT_POSTS` (the posts hold KlangScript, which only looks like JavaScript);
- a draft's page or index card was rendered without its DRAFT tag.

## How links are rewritten

- `../<YYYY-MM-DD-slug>/index.md#x` (another post) becomes `../<slug>/#x`; the folder form `../<YYYY-MM-DD-slug>/`
  does the same.
- A web asset of the post's own folder stays as it is (it is copied next to the page).
- A file under `src/jsMain/resources/` (the whitepaper) becomes its deployed address, `../../klang-whitepaper.html`;
  an anchor into an HTML page there is checked against the page's `id`s.
- Any other repository file or folder becomes its GitHub page, `https://github.com/PeekAndPoke/klang/blob/main/<path>`.
- Full URLs pass through.
