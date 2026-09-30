#!/usr/bin/env python3
#
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later
#
"""Builds the blog into src/jsMain/resources/blog: one page per post and the index.

    python3 console/blog/build.py            # build and write
    python3 console/blog/build.py --check    # build in memory, run every guard, write nothing; exit 1 on a
                                             # guard failure or when any output file would change

Reads every docs/blog/<YYYY-MM-DD-slug>/index.md (front matter + CommonMark/GFM body, the contract is
docs/blog/howto-write-a-post.md), renders it with the templates and blog.css next to this file, and
writes src/jsMain/resources/blog/<slug>/index.html with the post's web assets copied beside it, plus
src/jsMain/resources/blog/index.html. The output folder belongs to the build: whatever it did not
produce is removed. Files are written only when their bytes change, so a rerun is a no-op.
Needs markdown-it-py and PyYAML (see README.md).
"""
import datetime as dt
import html
import os
import re
import sys
from urllib.parse import unquote

import yaml
from markdown_it import MarkdownIt

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(ROOT, 'docs', 'blog')
OUT = os.path.join(ROOT, 'src', 'jsMain', 'resources', 'blog')
# The one folder the build may write into and delete from, fixed to this script's location so no
# reassignment of ROOT or OUT can point the writes or the cleanup anywhere else.
OWNED_OUT = os.path.realpath(os.path.join(HERE, '..', '..', 'src', 'jsMain', 'resources', 'blog'))
# Only these files of a post folder are copied next to the page; sources (make_fig.py, graphs.dot)
# stay in docs/blog.
WEB_ASSETS = {'png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'avif', 'html', 'wav', 'mp3', 'ogg', 'flac', 'mp4',
              'webm', 'pdf', 'json', 'css', 'js'}
SITE_ROOT = os.path.join(ROOT, 'src', 'jsMain', 'resources')
GITHUB = 'https://github.com/PeekAndPoke/klang'
POST_DIR = re.compile(r'^(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)$')
REQUIRED = ('title', 'subtitle', 'date', 'slug', 'tags', 'summary', 'authors', 'status')
ANCHOR_OPEN = re.compile(r'^<a id="([A-Za-z0-9_.:-]+)">$')
ANCHOR_LINE = re.compile(r'^(?:\s*<a id="[A-Za-z0-9_.:-]+"></a>\s*)+$')
FIGURE = re.compile(
    r'^<figure class="klang-figure">\s*'
    r'<iframe src="([A-Za-z0-9_.-]+\.html)" title="([^"<>]+)" loading="lazy"></iframe>\s*'
    r'<figcaption>([^<>]+)</figcaption>\s*'
    r'</figure>\s*$')
DRAFT_MARK = 'class="draft-tag"'


class Errors:
    def __init__(self):
        self.items = []

    def add(self, where, msg):
        self.items.append(f'{where}: {msg}')


def is_web_asset(name):
    return '.' in name and name.rsplit('.', 1)[1].lower() in WEB_ASSETS


def esc(s):
    return html.escape(str(s), quote=True)


def strip_license(text):
    """Drops the leading license comment of a template or stylesheet, so the pages do not carry it."""
    m = re.match(r'\s*(<!--.*?-->|/\*.*?\*/)\s*', text, re.S)
    if m and 'SPDX-License-Identifier' in m.group(1):
        return text[m.end():]
    return text


def slugify(text, seen):
    """GitHub's heading id rule: lowercase, drop everything but letters, digits, '_', '-' and space,
    spaces become '-', repeats get -1, -2, ..."""
    base = re.sub(r'[^\w\- ]', '', text.strip().lower()).replace(' ', '-')
    slug, n = base, 0
    while slug in seen:
        n += 1
        slug = f'{base}-{n}'
    seen.add(slug)
    return slug


def inline_text(token):
    parts = []
    for c in token.children or []:
        if c.type in ('text', 'code_inline'):
            parts.append(c.content)
        elif c.type in ('softbreak', 'hardbreak'):
            parts.append(' ')
    return ''.join(parts)


# ---- code highlighting ----------------------------------------------------------------------------
# Three token kinds, as the whitepaper colours its code by hand: kw (keyword), st (string), cm (comment).
# Deliberately small: one regex per language, no parsing; a Kotlin string sees one level of ${...}, no deeper.

def words(names):
    return r'\b(?:' + '|'.join(names.split()) + r')\b'


C_COMMENTS = r'//[^\n]*|/\*.*?(?:\*/|\Z)'
QUOTED = r'"(?:\\.|[^"\\\n])*"?|\'(?:\\.|[^\'\\\n])*\'?'
KOTLIN_QUOTED = r'"(?:\\.|\$\{[^}\n]*\}|[^"\\\n])*"?|\'(?:\\.|[^\'\\\n])*\'?'
HIGHLIGHT = {
    'kotlin': re.compile(
        rf'(?P<cm>{C_COMMENTS})|(?P<st>"""(?:.*?)(?:"""|\Z)|{KOTLIN_QUOTED})'
        r'|(?P<kw>' + words('as break catch class constructor continue do else false finally for fun if import in '
                            'interface is null object package return super this throw true try typealias val var '
                            'when while')
        # soft keywords count only in front of another word: `data class`, `private val`, not `val data:` or
        # `inner is Eq`
        + '|' + words('abstract actual annotation by companion const crossinline data enum expect external final '
                      'infix inline inner internal lateinit noinline open operator override private protected '
                      'public reified sealed suspend tailrec value vararg')
        + r'(?=[ \t]+(?!(?:as|in|is)\b)[A-Za-z_]))', re.S),
    # the keyword table of klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt
    'klangscript': re.compile(
        # its "..." and '...' may span lines, like its backtick strings
        rf'(?P<cm>{C_COMMENTS})|(?P<st>"(?:\\.|[^"\\])*"?|\'(?:\\.|[^\'\\])*\'?|`(?:\\.|[^`\\])*`?)'
        r'|(?P<kw>' + words('as break const continue do else export false for from if import in let null return '
                            'true while') + ')', re.S),
    'sh': re.compile(
        rf'(?P<cm>(?:^|(?<=\s))#[^\n]*)|(?P<st>"(?:\\.|[^"\\\n])*"?|\'[^\'\n]*\'?)'
        r'|(?P<kw>' + words('case do done elif else esac export fi for function if in local return then while')
        + ')', re.M),
    'html': re.compile(r'(?P<cm><!--.*?(?:-->|\Z))|(?P<st>"[^"\n]*")|(?P<kw></?[A-Za-z][\w-]*>?|/?>)', re.S),
}
# Tags that render as plain text: `text` for formulas, output and diagrams, carrying no label.
PLAIN = {'text'}
# Post folders that quote real JavaScript on purpose. Everything else tagged javascript is KlangScript.
JAVASCRIPT_POSTS = frozenset()


def highlight(content, lang):
    rx = HIGHLIGHT.get(lang)
    if rx is None:
        return esc(content)
    parts, pos = [], 0
    if lang == 'kotlin' and content.lstrip().startswith('*'):
        # a KDoc quoted without its opening /** is a comment up to its */
        end = content.find('*/')
        pos = len(content) if end < 0 else end + 2
        parts.append(f'<span class="cm">{esc(content[:pos])}</span>')
    for m in rx.finditer(content, pos):
        parts.append(esc(content[pos:m.start()]))
        parts.append(f'<span class="{m.lastgroup}">{esc(m.group())}</span>')
        pos = m.end()
    parts.append(esc(content[pos:]))
    return ''.join(parts)


def check_code_block(post, token, where, errors):
    if token.type == 'code_block':
        errors.add(where, 'indented code block: use a fenced block with its language (```kotlin, ```klangscript, '
                          '```sh, ```html, ```text)')
        return
    lang = fence_lang(token)
    if not lang:
        errors.add(where, 'code block without a language: tag it kotlin, klangscript, sh, html or text')
    elif lang == 'javascript':
        if post['folder'] not in JAVASCRIPT_POSTS:
            errors.add(where, 'code block tagged javascript: the posts hold KlangScript, tag it klangscript (a post '
                              'that quotes real JavaScript is named in JAVASCRIPT_POSTS in console/blog/build.py)')
    elif lang not in HIGHLIGHT and lang not in PLAIN:
        errors.add(where, f'code block tagged "{lang}", which the build does not know: use kotlin, klangscript, sh, '
                          f'html or text')


# ---- markdown ---------------------------------------------------------------------------------------

def fence_lang(token):
    return token.info.strip().split()[0] if token.info.strip() else ''


def code_block(content, lang):
    if lang and lang not in PLAIN:
        return (f'<div class="code" data-lang="{esc(lang)}"><pre><code class="language-{esc(lang)}">'
                f'{highlight(content, lang)}</code></pre></div>\n')
    return f'<div class="code"><pre><code>{esc(content)}</code></pre></div>\n'


def make_md():
    md = MarkdownIt('commonmark', {'html': True}).enable(['table', 'strikethrough'])

    def fence(self, tokens, idx, options, env):
        return code_block(tokens[idx].content, fence_lang(tokens[idx]))

    def indented(self, tokens, idx, options, env):
        return code_block(tokens[idx].content, '')

    def table_open(self, tokens, idx, options, env):
        return '<div class="tablewrap"><table>\n'

    def table_close(self, tokens, idx, options, env):
        return '</table></div>\n'

    md.add_render_rule('fence', fence)
    md.add_render_rule('code_block', indented)
    md.add_render_rule('table_open', table_open)
    md.add_render_rule('table_close', table_close)
    return md


# ---- read ---------------------------------------------------------------------------------------------

def read_post(folder, errors):
    path = os.path.join(SRC, folder, 'index.md')
    rel = os.path.relpath(path, ROOT)
    text = open(path, encoding='utf-8').read()
    lines = text.split('\n')
    if not lines or lines[0].strip() != '---':
        errors.add(f'{rel}:1', 'no front matter (the file must start with a --- line)')
        return None
    try:
        end = next(i for i in range(1, len(lines)) if lines[i].strip() == '---')
    except StopIteration:
        errors.add(f'{rel}:1', 'front matter is not closed with a --- line')
        return None
    try:
        fm = yaml.safe_load('\n'.join(lines[1:end])) or {}
    except yaml.YAMLError as e:
        errors.add(f'{rel}:1', f'front matter is not valid YAML: {e}')
        return None
    ok = True
    for key in REQUIRED:
        v = fm.get(key)
        if v is None or (isinstance(v, (str, list)) and not v):
            errors.add(f'{rel}:1', f'front matter misses the required field "{key}"')
            ok = False
    if not ok:
        return None
    for key in ('tags', 'authors'):
        if not isinstance(fm[key], list) or not all(isinstance(x, str) and x for x in fm[key]):
            errors.add(f'{rel}:1', f'front matter "{key}" must be a list of words')
            ok = False
    for key in ('title', 'subtitle', 'summary', 'slug', 'status'):
        if not isinstance(fm[key], str):
            errors.add(f'{rel}:1', f'front matter "{key}" must be text')
            ok = False
    date = fm['date']
    if isinstance(date, str):
        try:
            date = dt.date.fromisoformat(date)
        except ValueError:
            pass
    if not isinstance(date, dt.date):
        errors.add(f'{rel}:1', f'front matter "date" must be YYYY-MM-DD, got {fm["date"]!r}')
        ok = False
    if not ok:
        return None
    m = POST_DIR.match(folder)
    if fm['slug'] != m.group(2):
        errors.add(f'{rel}:1', f'slug "{fm["slug"]}" does not match the folder suffix "{m.group(2)}"')
    if date.isoformat() != m.group(1):
        errors.add(f'{rel}:1', f'date {date.isoformat()} does not match the folder date {m.group(1)}')
    refs = fm.get('references') or []
    if not isinstance(refs, list) or not all(isinstance(r, dict) and r.get('id') for r in refs):
        errors.add(f'{rel}:1', 'front matter "references" must be a list of entries with an "id"')
        refs = []
    return dict(folder=folder, rel=rel, lines=lines, offset=end + 1, body='\n'.join(lines[end + 1:]),
                title=fm['title'].strip(), subtitle=fm['subtitle'].strip(), date=date.isoformat(),
                slug=fm['slug'], tags=fm['tags'], summary=' '.join(fm['summary'].split()),
                authors=fm['authors'], status=fm['status'].strip(), hero=fm.get('hero'),
                references=[str(r['id']) for r in refs])


# ---- links ------------------------------------------------------------------------------------------

def resolve_link(post, href, posts_by_folder, where, errors, pending):
    """Returns the href for the rendered page. Records cross-post anchors in `pending` for phase 2."""
    if re.match(r'^[a-z][a-z0-9+.-]*:', href, re.I):
        return href
    if href.startswith('#'):
        pending.append((post['folder'], href[1:], where))
        return href
    if href.startswith('/'):
        errors.add(where, f'site-absolute link "{href}" (use a relative link or a full URL)')
        return href
    path, _, anchor = href.partition('#')
    path = unquote(path)
    target = os.path.normpath(os.path.join(SRC, post['folder'], path))
    frag = f'#{anchor}' if anchor else ''
    if not (target == ROOT or target.startswith(ROOT + os.sep)):
        errors.add(where, f'link "{href}" leaves the repository')
        return href
    if not os.path.exists(target):
        errors.add(where, f'link "{href}" resolves to nothing ({os.path.relpath(target, ROOT)} does not exist)')
        return href
    rel_blog = os.path.relpath(target, SRC)
    parts = rel_blog.split(os.sep)
    if not rel_blog.startswith('..') and len(parts) == 1 and parts[0] in posts_by_folder:
        parts = [parts[0], 'index.md']  # a post's folder is its page
    if not rel_blog.startswith('..') and len(parts) == 2 and parts[1] == 'index.md' and parts[0] in posts_by_folder:
        other = posts_by_folder[parts[0]]
        if anchor:
            pending.append((other['folder'], anchor, where))
        if other['folder'] == post['folder']:
            return frag or '#'
        return f'../{other["slug"]}/{frag}'
    if not rel_blog.startswith('..') and len(parts) == 2 and parts[0] == post['folder'] and is_web_asset(parts[1]):
        if anchor:
            errors.add(where, f'link "{href}" carries an anchor into an asset, which the build cannot check')
        return href  # an asset of this post, copied next to the page
    # a source file of the post (make_fig.py) or any other repository file: its GitHub page
    site_rel = os.path.relpath(target, SITE_ROOT)
    if not site_rel.startswith('..') and os.path.isfile(target) and not site_rel.startswith('blog' + os.sep):
        # deployed at the site root, the page sits at /blog/<slug>/
        if anchor:
            if not target.endswith('.html'):
                errors.add(where, f'link "{href}" carries an anchor into a file the build cannot check')
            elif anchor not in page_ids(target):
                errors.add(where, f'anchor "#{anchor}" does not resolve to an id in {os.path.relpath(target, ROOT)}')
        return '../../' + site_rel.replace(os.sep, '/') + frag
    kind = 'tree' if os.path.isdir(target) else 'blob'
    return f'{GITHUB}/{kind}/main/{os.path.relpath(target, ROOT).replace(os.sep, "/")}{frag}'


_PAGE_IDS = {}


def page_ids(path):
    """The id attributes of a hand-written page of the site (the whitepaper), for anchor checks."""
    if path not in _PAGE_IDS:
        text = open(path, encoding='utf-8', errors='replace').read()
        _PAGE_IDS[path] = set(re.findall(r'\sid=["\']([^"\']+)["\']', text))
    return _PAGE_IDS[path]


def line_of(post, token, needle):
    """Best file line for an inline problem: the first line of the block that contains the needle."""
    if token.map:
        lo, hi = token.map
        for i in range(lo, hi):
            if needle and needle in post['lines'][post['offset'] + i]:
                return post['offset'] + i + 1
        return post['offset'] + lo + 1
    return post['offset'] + 1


# ---- render one post -----------------------------------------------------------------------------------

def render_post(post, md, posts_by_folder, errors, pending):
    tokens = md.parse(post['body'])
    ids, heading_ids = set(), set()
    for i, t in enumerate(tokens):
        where = f'{post["rel"]}:{line_of(post, t, None)}'
        if t.type == 'html_block':
            m = FIGURE.match(t.content.strip() + '\n')
            if m:
                src = m.group(1)
                if not os.path.isfile(os.path.join(SRC, post['folder'], src)):
                    errors.add(where, f'figure frame "{src}" is not a file in the post folder')
            elif not ANCHOR_LINE.match(t.content.strip()):
                errors.add(where, 'HTML block that is neither an <a id="..."></a> anchor nor the figure block '
                                  'of howto-write-a-post.md section 7: ' + t.content.strip().split('\n')[0][:80])
            for a in re.findall(r'<a id="([^"]+)"></a>', t.content):
                ids.add(a)
            continue
        if t.type in ('fence', 'code_block'):
            check_code_block(post, t, where, errors)
            continue
        if t.type == 'heading_open':
            hid = slugify(inline_text(tokens[i + 1]), heading_ids)
            t.attrSet('id', hid)
            ids.add(hid)
            continue
        if t.type != 'inline':
            continue
        kids = t.children or []
        for k, c in enumerate(kids):
            if c.type == 'html_inline':
                m = ANCHOR_OPEN.match(c.content)
                if m and k + 1 < len(kids) and kids[k + 1].type == 'html_inline' and kids[k + 1].content == '</a>':
                    ids.add(m.group(1))
                elif c.content == '</a>' and k > 0 and ANCHOR_OPEN.match(kids[k - 1].content or ''):
                    pass
                else:
                    errors.add(f'{post["rel"]}:{line_of(post, t, c.content)}',
                               f'inline HTML "{c.content}" (only <a id="..."></a> anchors are allowed)')
            elif c.type == 'link_open':
                href = c.attrGet('href') or ''
                w = f'{post["rel"]}:{line_of(post, t, unquote(href).split("#")[0] or href)}'
                c.attrSet('href', resolve_link(post, href, posts_by_folder, w, errors, pending))
            elif c.type == 'image':
                src = c.attrGet('src') or ''
                w = f'{post["rel"]}:{line_of(post, t, src)}'
                if re.match(r'^[a-z][a-z0-9+.-]*:', src, re.I) or src.startswith('/') or '/' in src:
                    errors.add(w, f'image "{src}" is not a file in the post folder')
                elif not os.path.isfile(os.path.join(SRC, post['folder'], unquote(src))):
                    errors.add(w, f'image "{src}" does not exist in the post folder')
                elif not is_web_asset(src):
                    errors.add(w, f'image "{src}" is not a web asset type, the build does not copy it')
                c.attrSet('loading', 'lazy')
    for r in post['references']:
        if r not in ids:
            errors.add(f'{post["rel"]}:1', f'reference "{r}" in the front matter has no <a id="{r}"></a> in the body')
    post['ids'] = ids
    return md.renderer.render(tokens, md.options, {})


# ---- pages --------------------------------------------------------------------------------------------

def template(name):
    return strip_license(open(os.path.join(HERE, name), encoding='utf-8').read())


def fill(tpl, values):
    """One pass over the template: an inserted value is never scanned again, so a post may contain __FILE__."""
    def value(m):
        if m.group(1) not in values:
            raise SystemExit(f'template placeholder {m.group(0)} has no value')
        return values[m.group(1)]
    return re.sub(r'__([A-Z]+)__', value, tpl)


def hero_ok(post):
    hero = str(post['hero'] or '')
    return (bool(hero) and '/' not in hero and '\\' not in hero and not hero.startswith('.') and is_web_asset(hero)
            and os.path.isfile(os.path.join(SRC, post['folder'], hero)))


def draft_tag(post):
    return '' if post['status'] == 'published' else f'<span {DRAFT_MARK}>Draft</span>'


def meta_line(post):
    tags = ''.join(f'<li>{esc(t)}</li>' for t in post['tags'])
    return (f'<time datetime="{post["date"]}">{post["date"]}</time>'
            f'<ul class="tags">{tags}</ul>'
            f'<span class="authors">by {esc(", ".join(post["authors"]))}</span>')


def post_page(post, body, css):
    return fill(template('post.template.html'), dict(
        CSS=css, TITLE=esc(post['title']), SUMMARY=esc(post['summary']), DRAFT=draft_tag(post),
        META=meta_line(post), BODY=body.rstrip('\n')))


def index_page(posts, css):
    cards = []
    for p in posts:
        hero = ''
        if hero_ok(p):
            hero = (f'<img class="hero" src="{esc(p["slug"])}/{esc(p["hero"])}" alt="" loading="lazy">')
        cards.append(
            f'<li class="card">\n'
            f'  {hero}\n'
            f'  <div class="card-body">\n'
            f'    <div class="meta">{draft_tag(p)}{meta_line(p)}</div>\n'
            f'    <h2><a href="{esc(p["slug"])}/">{esc(p["title"])}</a></h2>\n'
            f'    <p class="subtitle">{esc(p["subtitle"])}</p>\n'
            f'    <p class="summary">{esc(p["summary"])}</p>\n'
            f'  </div>\n'
            f'</li>')
    return fill(template('index.template.html'), dict(CSS=css, COUNT=str(len(posts)), CARDS='\n'.join(cards)))


# ---- build ------------------------------------------------------------------------------------------

def build(errors):
    """Returns {output path relative to OUT: bytes}."""
    folders = sorted(d for d in os.listdir(SRC) if os.path.isdir(os.path.join(SRC, d)))
    for d in folders:
        if not POST_DIR.match(d):
            errors.add(f'docs/blog/{d}', 'folder name is not YYYY-MM-DD-slug')
        elif not os.path.isfile(os.path.join(SRC, d, 'index.md')):
            errors.add(f'docs/blog/{d}', 'post folder without index.md')
    posts = []
    for d in folders:
        if POST_DIR.match(d) and os.path.isfile(os.path.join(SRC, d, 'index.md')):
            p = read_post(d, errors)
            if p:
                posts.append(p)
    slugs = {}
    for p in posts:
        if p['slug'] in slugs:
            errors.add(p['rel'], f'slug "{p["slug"]}" is also used by {slugs[p["slug"]]}')
        slugs[p['slug']] = p['rel']
    by_folder = {p['folder']: p for p in posts}
    md = make_md()
    css = strip_license(open(os.path.join(HERE, 'blog.css'), encoding='utf-8').read()).strip('\n')
    pending, bodies = [], {}
    for p in posts:
        bodies[p['folder']] = render_post(p, md, by_folder, errors, pending)
    for folder, anchor, where in pending:
        if anchor not in by_folder[folder]['ids']:
            target = '' if where.startswith(by_folder[folder]['rel']) else f' in {by_folder[folder]["rel"]}'
            errors.add(where, f'anchor "#{anchor}" does not resolve to a heading or <a id> anchor{target}')

    out = {}
    for p in posts:
        out[f'{p["slug"]}/index.html'] = post_page(p, bodies[p['folder']], css).encode('utf-8')
        for name in sorted(os.listdir(os.path.join(SRC, p['folder']))):
            src = os.path.join(SRC, p['folder'], name)
            if name == 'index.html':
                errors.add(f'docs/blog/{p["folder"]}/index.html', 'a post asset may not be named index.html '
                                                                  '(it would replace the rendered page)')
            elif name != 'index.md' and is_web_asset(name) and os.path.isfile(src):
                with open(src, 'rb') as f:
                    out[f'{p["slug"]}/{name}'] = f.read()
        page = out[f'{p["slug"]}/index.html'].decode('utf-8', 'replace')
        if (DRAFT_MARK in page.split('<article', 1)[0]) != (p['status'] != 'published'):
            errors.add(p['rel'], 'the rendered page does not carry the DRAFT tag its status asks for')
    posts.sort(key=lambda p: (p['date'], p['slug']), reverse=True)
    index = index_page(posts, css)
    for p, card in zip(posts, index.split('<li class="card">')[1:]):
        if (DRAFT_MARK in card) != (p['status'] != 'published') or f'href="{p["slug"]}/"' not in card:
            errors.add(p['rel'], 'the index card does not carry the DRAFT tag its status asks for')
    out['index.html'] = index.encode('utf-8')
    return posts, out


def existing_files():
    found = {}
    for base, dirs, files in os.walk(OUT):
        dirs.sort()
        for f in sorted(files):
            full = os.path.join(base, f)
            found[os.path.relpath(full, OUT).replace(os.sep, '/')] = full
    return found


def guard_output_dir():
    """Refuses to write or delete anywhere but src/jsMain/resources/blog of this checkout."""
    if os.path.islink(OUT) or os.path.realpath(OUT) != OWNED_OUT:
        raise SystemExit(f'refusing to write: the output folder {OUT} is not the real folder {OWNED_OUT}')


def guard_inside(path, allow_root=False):
    """Refuses a write or a delete whose real path leaves the output folder (a link planted inside it)."""
    real = os.path.realpath(path)
    if not (real.startswith(OWNED_OUT + os.sep) or (allow_root and real == OWNED_OUT)):
        raise SystemExit(f'refusing to touch {path}: its real path {real} is outside {OWNED_OUT}')


def sync(out, write):
    """Makes OUT hold exactly `out`. Returns (written, removed) path lists. With write=False it only
    compares, and touches nothing."""
    if write:
        guard_output_dir()
    have = existing_files()
    written, removed = [], []
    for rel in sorted(out):
        full = os.path.join(OUT, rel)
        if rel in have:
            with open(full, 'rb') as f:
                if f.read() == out[rel]:
                    continue
        written.append(rel)
        if write:
            guard_inside(full)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, 'wb') as f:
                f.write(out[rel])
    for rel in sorted(set(have) - set(out)):
        removed.append(rel)
        if write:
            guard_inside(os.path.dirname(have[rel]), allow_root=True)  # removing a file link removes the link
            os.remove(have[rel])
    if write:
        for base, dirs, files in os.walk(OUT, topdown=False):
            if base != OUT and not os.listdir(base):
                os.rmdir(base)
    return written, removed


def main():
    check = '--check' in sys.argv[1:]
    unknown = [a for a in sys.argv[1:] if a != '--check']
    if unknown:
        raise SystemExit(f'unknown argument(s): {unknown}\nusage: build.py [--check]')
    errors = Errors()
    posts, out = build(errors)
    if errors.items:
        for e in errors.items:
            print('ERROR', e, file=sys.stderr)
        print(f'{len(errors.items)} error(s); nothing written', file=sys.stderr)
        sys.exit(1)
    for p in posts:
        if p['hero'] and not hero_ok(p):
            print(f'WARNING {p["rel"]}: hero "{p["hero"]}" is not a web asset file in the post folder, '
                  'the card shows none')
    written, removed = sync(out, write=not check)
    drafts = sum(p['status'] != 'published' for p in posts)
    stream = sys.stderr if check else sys.stdout
    for rel in written:
        print(f'{"STALE" if check else "wrote"} src/jsMain/resources/blog/{rel}', file=stream)
    for rel in removed:
        print(f'{"STALE (would be removed)" if check else "removed"} src/jsMain/resources/blog/{rel}', file=stream)
    print(f'{len(posts)} posts ({drafts} drafts), {len(out)} files; {len(written)} changed, {len(removed)} removed')
    if check:
        if written or removed:
            print('--check: all guards passed, but the output is stale; run python3 console/blog/build.py',
                  file=sys.stderr)
            sys.exit(1)
        print('--check: all guards passed, the output is up to date')


if __name__ == '__main__':
    main()
