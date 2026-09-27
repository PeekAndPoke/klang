#!/usr/bin/env python3
"""Builds the dev-status pages into src/www: the topic map and the mission log.

    python3 console/dev-status/build.py

Reads every Markdown file in docs/tasks, docs/plans and docs/tasks-archive (title, status line,
length, last commit, the links between them), joins it with the judgement in curation.py, and
renders the two templates with the shared hud.css. The pages are flat HTML; d3 comes from cdnjs,
the fonts from Google Fonts. Deployed by console/deploy-finzo.sh with the rest of src/www.
"""
import datetime as dt
import glob
import json
import os
import re
import runpy
import subprocess
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'src', 'www')
C = runpy.run_path(os.path.join(HERE, 'curation.py'))
TODAY = dt.date.today()


def stem(path):
    return os.path.basename(path)[:-3]


def clean(s):
    s = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', s)
    return s.replace('**', '').replace('`', '').replace('> ', '').strip('> _*|').strip()


def last_commit(path):
    return subprocess.run(['git', 'log', '-1', '--format=%ad', '--date=short', '--', path],
                          capture_output=True, text=True, cwd=ROOT).stdout.strip()


# ---- read the docs --------------------------------------------------------------------------------

def read_docs():
    os.chdir(ROOT)
    files = sorted(glob.glob('docs/tasks/**/*.md', recursive=True) + glob.glob('docs/plans/**/*.md', recursive=True)
                   + glob.glob('docs/tasks-archive/**/*.md', recursive=True))
    files = [f for f in files if f not in C['EXCLUDE']]
    stems = {stem(f): f for f in files}
    nodes, edges = [], {}
    for f in files:
        txt = open(f, encoding='utf-8').read()
        lines = txt.splitlines()
        title = next((l[2:].strip() for l in lines if l.startswith('# ')), stem(f))
        status = ''
        for l in lines[1:40]:
            if re.search(r'status', l, re.I) or re.match(r'>\s*\*\*', l) or re.match(r'\*\*Status', l):
                status = clean(l)
                break
        if not status:
            status = next((clean(l) for l in lines[1:15] if l.strip() and not l.startswith('#')), '')
        head = '\n'.join(lines[:30])
        prio = next((p for p in ('MUST', 'SHOULD', 'NICE') if re.search(r'\b' + p + r'\b', head)), None)
        nodes.append(dict(id=stem(f), path=f, title=title, status=status[:400], prio=prio, lines=len(lines),
                          date=last_commit(f), archived=f.startswith('docs/tasks-archive/')))
        for other in stems:
            if other == stem(f):
                continue
            e = re.escape(other)
            n = (len(re.findall(r'(?<![\w-])' + e + r'\.md', txt)) + len(re.findall('`' + e + '`', txt))
                 + len(re.findall(r'/' + e + r'(?![\w-])', txt)))
            if n:
                edges[(stem(f), other)] = n
    return nodes, [dict(source=a, target=b, count=c) for (a, b), c in edges.items()]


# ---- join with the curation ------------------------------------------------------------------------

def keyword_area(n):
    key = (n['id'] + ' ' + n['title']).lower()
    for area, words in C['KW']:
        if any(w in key for w in words):
            return area
    return None


def curate(nodes, edges):
    area = {i: a for a, ids in C['AREAS'].items() for i in ids}
    state = {i: s for s, ids in C['STATES'].items() for i in ids}
    ids = {n['id'] for n in nodes}
    warnings = []
    for n in nodes:
        if n['archived']:
            continue
        if n['id'] not in area:
            area[n['id']] = keyword_area(n) or 'engine'
            warnings.append(f"new open doc, no area in curation.py (guessed {area[n['id']]}): {n['path']}")
    for i in sorted(set(area) - ids):
        warnings.append(f'curation.py names a doc that is not open any more: {i}')
    neighbours = {}
    for e in edges:
        neighbours.setdefault(e['source'], []).append(e['target'])
        neighbours.setdefault(e['target'], []).append(e['source'])
    for n in nodes:
        if n['archived']:
            short = n['id'][9:]
            a = C['ARCHIVE_AREAS'].get(short) or keyword_area(n)
            if a:
                area[n['id']] = a
    for n in nodes:
        if n['archived'] and n['id'] not in area:
            votes = Counter(area[m] for m in neighbours.get(n['id'], []) if m in area)
            area[n['id']] = votes.most_common(1)[0][0] if votes else 'engine'
    for n in nodes:
        n['area'] = area[n['id']]
        if n['archived']:
            n['month'] = n['path'].split('/')[2]
            n['state'] = 'wont' if n['id'] in C['WONT'] else ('superseded' if n['id'] in C['SUPER'] else 'done')
        else:
            n['state'] = state.get(n['id'], 'future' if '/future/' in n['path'] else 'open')
        n['note'] = C['NOTES'].get(n['id'], '')
        n['stale'] = n['id'] in C['STALE']
    known = {(e['source'], e['target']) for e in edges}
    related = []
    for a, b, why in C['RELATED']:
        if a not in ids or b not in ids:
            warnings.append(f'related edge names a missing doc: {a} / {b}')
        elif (a, b) not in known and (b, a) not in known:
            related.append(dict(source=a, target=b, why=why))
    return related, warnings


# ---- mission log guesses ---------------------------------------------------------------------------

def timeline(nodes):
    D = dt.date.fromisoformat
    buckets, items = {}, []

    def group(n):
        if n['id'] in C['FAR']:
            return 'far'
        if n['state'] == 'byear':
            return 'byear'
        if n['id'].startswith(('bugfix-', 'audit-')) and n['state'] == 'open':
            return 'bugs'
        return ('future' if n['state'] == 'future' else 'open', n['area'])

    for n in nodes:
        e = dict(id=n['id'], title=n['title'], area=n['area'], state=n['state'], lines=n['lines'],
                 path=n['path'], status=n['status'])
        if n['archived']:
            s = n['id'][:8]
            e['date'], e['kind'] = f'{s[:4]}-{s[4:6]}-{s[6:8]}', 'archived'
        elif n['state'] == 'reference':
            e['kind'] = 'reference'
        else:
            e['kind'] = 'projected'
            if n['id'] in C['ETA_FIXED']:
                e['date'] = C['ETA_FIXED'][n['id']]
            else:
                buckets.setdefault(group(n), []).append(e)
        items.append(e)
    for g, es in buckets.items():
        lo, hi = map(D, C['ETA_RANGES'][g])
        es.sort(key=lambda x: (x['lines'], x['id']))
        for i, e in enumerate(es):
            e['date'] = (lo + dt.timedelta(days=round((hi - lo).days * (i + 0.5) / len(es)))).isoformat()
    for e in items:
        if e['kind'] == 'projected':
            t = D(e['date'])
            if t < TODAY:
                t = TODAY + dt.timedelta(days=7)
                e['date'] = t.isoformat()
            spread = dt.timedelta(days=round(max((t - TODAY).days / 30.4, 0.5) * 0.35 * 30.4))
            e['lo'] = max(t - spread, TODAY + dt.timedelta(days=3)).isoformat()
            e['hi'] = (t + spread).isoformat()
    return dict(now=TODAY.isoformat(), items=items, milestones=C['MILESTONES'])


# ---- render ----------------------------------------------------------------------------------------

def render(template, data, target):
    html = open(os.path.join(HERE, template), encoding='utf-8').read()
    css = open(os.path.join(HERE, 'hud.css'), encoding='utf-8').read()
    html = html.replace('/*__HUD_CSS__*/', css).replace('__DATA__', json.dumps(data, ensure_ascii=False))
    with open(os.path.join(OUT, target), 'w', encoding='utf-8') as f:
        f.write(html)
    print(f'wrote src/www/{target} ({len(html) // 1024} KB)')


def main():
    nodes, edges = read_docs()
    related, warnings = curate(nodes, edges)
    os.makedirs(OUT, exist_ok=True)
    render('topic-map.template.html',
           dict(nodes=nodes, edges=edges, related=related, generated=TODAY.isoformat()), 'klang-topic-map.html')
    render('mission-log.template.html', timeline(nodes), 'klang-mission-log.html')
    states = Counter(n['state'] for n in nodes)
    print(f"{sum(not n['archived'] for n in nodes)} open, {sum(n['archived'] for n in nodes)} archived, "
          f'{len(edges)} links, {len(related)} related; ' + ', '.join(f'{k} {v}' for k, v in sorted(states.items())))
    for w in warnings:
        print('WARNING', w)


if __name__ == '__main__':
    main()
