#!/usr/bin/env python3
"""Builds the engine health page into src/jsMain/resources: the DSP probes' results with a light per item.

    python3 console/dev-status/engine_health.py [--run docs/audio-audit/<date>] [--out FILE]

Reads the newest run folder under docs/audio-audit (the JSON the probes of KLANG-00420 wrote: stage-probe.json,
stage-cost.json, hot-path-lint.json, dead-knobs.json, and the later ones as they are built) and the judgement in
docs/audio-audit/verdicts.json, and renders engine-health.template.html with the shared hud.css. The page is flat
HTML with its data inline, no library. The register of the probes is audio/ref/verification.md, "The probes".

A light is a RULE over a measurement, never the measurement itself:

  green   nothing notable measured
  yellow  a lead: something notable, not judged yet
  red     bad on its face (a stage that never recovers from a poison sample, a dead knob), or a confirmed defect
  grey    accepted by decision, with the reason
  blue    a measurement without a rule (the cost table)

Not a rule on purpose: a huge finite sample leaving a stage after a poison one (1e30 passes a linear stage, an
infinity leaves as SAFE_MAX). That is the Motor's numerical contract, the same for 89 of 165 stages, so it is in the
rows and lights nothing.

The rules are the functions `stage_probe_items`, `lint_items`, `knob_items` below. A verdict overrides a rule:
verdicts.json maps "<probe>/<item id>" to {"status": "confirmed" | "accepted" | "disproved" | "fixed", "note": "...",
"ticket": "KLANG-00000"}; confirmed is red, accepted is grey, disproved and fixed are green. A verdict whose item is
gone is printed as a WARNING. Standard library only.
"""
import argparse
import datetime as dt
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
AUDIT = os.path.join(ROOT, 'docs', 'audio-audit')
OUT = os.path.join(ROOT, 'src', 'jsMain', 'resources', 'klang-engine-health.html')

SAMPLE_RATE = 48000.0
POISON = ('nan', '+inf', '1e30')
VERDICT_LIGHT = {'confirmed': 'red', 'accepted': 'grey', 'disproved': 'green', 'fixed': 'green'}
RANK = {'red': 3, 'yellow': 2, 'green': 1, 'blue': 0, 'grey': 0, 'none': 0}

PLANNED = [
    ('aliasing', 'Aliasing meter', 'How much energy does a source or a shaper put where no harmonic belongs?'),
    ('clicks', 'Click detector', 'Does a knob jump or a stage switching on or off put a discontinuity into the signal?'),
    ('framing', 'Block-framing checker', 'Does a voice sound the same wherever the block borders fall?'),
]


def num(x):
    """A JSON number, or a non-finite one the probes wrote as a string."""
    return float(x) if isinstance(x, str) else x


def fmt(x, digits=3):
    x = num(x)
    if x != x:
        return 'NaN'
    if x in (float('inf'), float('-inf')):
        return 'inf' if x > 0 else '-inf'
    if x == 0:
        return '0'
    a = abs(x)
    return f'{x:.{digits}g}' if (a >= 1e5 or a < 1e-3) else f'{x:.{digits}f}'.rstrip('0').rstrip('.')


def blank(x):
    """A fact that does not exist for this row (no gain without a peak, no deepest value in an all-zero tail)."""
    v = num(x)
    return '' if (v != v or v in (float('inf'), float('-inf'))) else fmt(x)


def seconds(frames):
    return 'never' if frames < 0 else f'{frames / SAMPLE_RATE:.3f} s'


def load(run, name):
    path = os.path.join(run, name)
    if not os.path.exists(path):
        return None
    with open(path, encoding='utf-8') as fh:
        return json.load(fh)


# ── The rules ───────────────────────────────────────────────────────────────────────────────────────────────


def stage_probe_items(rows):
    """One item per stage and knob set, over its signals."""
    groups = {}
    for r in rows:
        groups.setdefault((r['family'], r['stage'], r['knobs']), []).append(r)
    items = []
    for (family, stage, knobs), rs in groups.items():
        red, yellow = [], []
        for r in rs:
            sig, poison = r['signal'], r['signal'] in POISON
            dc_end, gain = num(r['dcEnd']), num(r['gainDb'])
            if r['finiteAgainFrames'] < 0:
                red.append(f'{sig}: the output never becomes finite again (latched)')
            elif r['nonFiniteCount'] > 0 and not poison:
                red.append(f'{sig}: {r["nonFiniteCount"]} non-finite samples from clean input')
            elif r['nonFiniteCount'] > 1:
                yellow.append(f'{sig}: the poison leaves as {r["nonFiniteCount"]} non-finite samples, '
                              f'finite again after {seconds(r["finiteAgainFrames"])}')
            if r.get('crossed') and r['finiteAgainFrames'] >= 0:
                yellow.append(f'{sig}: the poison crossed into the clean channel')
            if r['subnormalCount'] > 0:
                yellow.append(f'{sig}: {r["subnormalCount"]} subnormal samples')
            if dc_end == dc_end and abs(dc_end) > 1e-5:
                yellow.append(f'{sig}: still at {fmt(dc_end)} ten seconds after the input ended')
            elif r['tailFrames'] < 0:
                yellow.append(f'{sig}: the tail outlives the 10 s window')
            elif r['zeroFrames'] < 0:
                yellow.append(f'{sig}: silent, but never exactly 0 (sinks to {fmt(r["minTailAbs"])})')
            if not poison and gain == gain and gain > 24:
                yellow.append(f'{sig}: peak {fmt(gain, 3)} dB above the input')
        light = 'red' if red else 'yellow' if yellow else 'green'
        items.append({
            'id': f'{family}/{stage}/{knobs}', 'group': f'{family} {stage}', 'title': knobs, 'light': light,
            'reasons': red + yellow,
            'columns': ['signal', 'peak', 'gain dB', 'dc at end', 'non-finite', 'finite again', 'tail', 'exact zero',
                        'subnormal', 'deepest'],
            'rows': [[r['signal'], fmt(r['peak']), blank(r['gainDb']), fmt(r['dcEnd']), r['nonFiniteCount'],
                      seconds(r['finiteAgainFrames']) if r['nonFiniteCount'] else '',
                      seconds(r['tailFrames']), seconds(r['zeroFrames']), r['subnormalCount'], blank(r['minTailAbs'])]
                     for r in rs],
        })
    return items


def cost_items(rows):
    """No rule: the cost table is a measurement. One item per row, with a bar."""
    top = max((num(r['netNsPerSample']) for r in rows), default=1.0) or 1.0
    items = []
    for r in sorted(rows, key=lambda r: -num(r['netNsPerSample'])):
        net = num(r['netNsPerSample'])
        items.append({
            'id': f'{r["family"]}/{r["stage"]}/{r["knobs"]}', 'group': 'by cost, the dearest first',
            'title': f'{r["family"]}  {r["stage"]}  {r["knobs"]}',
            'light': 'blue', 'reasons': [], 'bar': max(0.0, net) / top, 'value': f'{fmt(net)} ns',
            'columns': ['net ns per sample', 'share of real time', 'times a sine', 'raw ns (min .. max)', 'against',
                        'build', 'bytes per block'],
            'rows': [[fmt(net), f'{100 * num(r["realtimeShare"]):.3f} %', fmt(r['ratioToSine']),
                      f'{fmt(r["nsPerSample"])} ({fmt(r["nsPerSampleMin"])} .. {fmt(r["nsPerSampleMax"])})',
                      r['baseline'], f'{fmt(r["buildMicros"])} us', fmt(r['netAllocBytesPerBlock'])]],
        })
    return items


def lint_items(doc):
    """One item per finding; an allocation behind a guard is the permitted pattern (green), a waiver is grey."""
    items = []
    for f in doc['findings']:
        guarded = f['rule'] == 'alloc-guarded'
        light = 'grey' if f['waived'] else 'green' if guarded else 'yellow'
        items.append({
            'id': f'{f["file"]}:{f["line"]}:{f["rule"]}', 'group': os.path.basename(f['file']),
            'title': f'{f["line"]}  {f["rule"]}', 'light': light,
            'reasons': [f['message']] + (['behind a guard: the resize-on-shape-change pattern'] if guarded else []),
            'columns': ['file', 'line', 'rule', 'finding'], 'rows': [[f['file'], f['line'], f['rule'], f['message']]],
        })
    return items


def knob_items(doc):
    sections = {k: {(mp, key) for mp, key, *_ in v} for k, v in doc['findings'].items()}
    waived = {(w['map'], w['key']): w['reason'] for w in doc.get('waivers', [])}
    items = []
    for k in doc['keys']:
        at = (k['map'], k['key'])
        reasons, light = [], 'green'
        if at in sections.get('a', ()) or at in sections.get('c', ()):
            light, reasons = 'red', ['a door writes it and no engine code reads it (or a near miss of a read key)']
        elif at in sections.get('b', ()):
            light, reasons = 'yellow', ['no door writes it: only a raw ignp / katp reaches it']
        elif at in sections.get('e', ()):
            light, reasons = 'yellow', ['declared in Slots, placed by no engine code']
        if at in waived:
            light, reasons = 'grey', reasons + [f'waived: {waived[at]}']
        items.append({
            'id': f'{k["map"]}/{k["key"]}', 'group': k['map'], 'title': k['key'], 'light': light, 'reasons': reasons,
            'value': f'W{len(k["writers"])} R{len(k["readers"])}',
            'columns': ['side', 'kind', 'file', 'line'],
            'rows': [['writes', s['kind'], s['file'], s['line']] for s in k['writers']] +
                    [['reads', s['kind'], s['file'], s['line']] for s in k['readers']],
        })
    return items


def aliasing_items(rows):
    """One item per source or shaper and knob set, over its pitches. A quality question, so never red by rule: yellow
    when the energy off the harmonics is within 60 dB of the harmonics at any pitch."""
    groups = {}
    for r in rows:
        groups.setdefault((r['family'], r['stage'], r['knobs']), []).append(r)
    items = []
    for (family, stage, knobs), rs in groups.items():
        notes = sorted({r['note'] for r in rs if r['note']})
        judged = [r for r in rs if r['measurable'] and r['kind'] == 'measured' and not any('by design' in n for n in notes)]
        reasons, light = list(notes), 'blue'
        if judged:
            worst = max(judged, key=lambda r: num(r['nonHarmonicReHarmonicsDb']))
            level = num(worst['nonHarmonicReHarmonicsDb'])
            light = 'yellow' if level > -60 else 'green'
            if light == 'yellow':
                reasons.insert(0, f'at {fmt(worst["pitchHz"], 5)} Hz the energy off the harmonics is {fmt(level)} dB re the '
                                  f'harmonics (strongest at {fmt(worst["strongestHz"], 5)} Hz)')
        items.append({
            'id': f'{family}/{stage}/{knobs}', 'group': f'{family} {stage.split(":")[0]}', 'title': f'{stage}  {knobs}',
            'light': light, 'reasons': reasons,
            'value': f'{fmt(max(num(r["nonHarmonicReHarmonicsDb"]) for r in rs), 3)} dB' if all(r['measurable'] for r in rs) else 'n/m',
            'columns': ['pitch Hz', 'fundamental dBFS', 'harmonics dB', 'off harmonics dB re fund.', 're harmonics',
                        'dBFS', 'strongest Hz', 'at dB', 'cents'],
            'rows': [[fmt(r['pitchHz'], 5), blank(r['fundamentalDbfs']), blank(r['harmonicDb']), blank(r['nonHarmonicDb']),
                      blank(r['nonHarmonicReHarmonicsDb']), blank(r['nonHarmonicDbfs']), blank(r['strongestHz']),
                      blank(r['strongestDb']), blank(r['offsetCents'])] if r['measurable'] else
                     [fmt(r['pitchHz'], 5), 'not measurable this way', '', '', '', '', '', '', ''] for r in rs],
        })
    return items


def click_items(rows):
    """One item per knob change or voice edge. The reference is the probe's own 50 ms glide control (kink about 2.5,
    burst about -86 dB): yellow when the kink is over 5 times the steady signal's AND the burst is above -80 dB. A row
    whose sensitivity is under 3 cannot see a click (a bright or noisy signal) and gets no light."""
    items = []
    for r in rows:
        kink, jump, burst, sens = num(r['kinkRatio']), num(r['jumpRatio']), num(r['burstAbsDb']), num(r['sensitivity'])
        reasons, light = [], 'green'
        if r['family'] == 'control':
            light, reasons = 'blue', ["a control: it shows the meter's range"]
        elif sens < 3:
            light, reasons = 'blue', [f'blind here: a 10 percent step would read only {fmt(sens)} (the signal is too bright)']
        elif kink > 5 and burst > -80:
            light = 'yellow'
            reasons = [f'the kink is {fmt(kink)} times the steady signal, the burst above 5 kHz {fmt(burst)} dB re the signal '
                       f'({fmt(r["kinkOffsetFrames"] / SAMPLE_RATE * 1000)} ms after the event)']
            if jump > 10 and burst > -30:
                reasons.append('in the class of a hard step (the control reads jump 52, burst -18 dB)')
        items.append({
            'id': f'{r["family"]}/{r["stage"]}/{r["knob"]}/{r["change"]}', 'group': f'{r["family"]} {r["stage"]}',
            'title': f'{r["knob"]}  {r["change"]}', 'light': light, 'reasons': reasons, 'value': f'x{fmt(kink, 3)}',
            'columns': ['jump x', 'kink x', 'kink after', 'burst dB re signal', 'burst dB re steady', 'level before',
                        'after', 'rise ms', 'settle ms', 'sensitivity'],
            'rows': [[fmt(jump), fmt(kink), f'{fmt(r["kinkOffsetFrames"] / SAMPLE_RATE * 1000)} ms', fmt(burst),
                      fmt(r['burstRelDb']), fmt(r['levelBefore']), fmt(r['levelAfter']), blank(r['riseMs']),
                      blank(r['settleMs']), fmt(sens)]],
        })
    return items


def framing_items(rows):
    """One item per case and sweep, over its 128 offsets. Identical or rounding is green; a difference the records
    call by design is grey with the quote; anything else that differs is a lead."""
    items = []
    for r in rows:
        w, cls = r['worst'], r['class']
        reasons, light = [], 'green'
        if cls in ('small', 'audible'):
            by_design = r['known'].startswith('by design')
            light = 'grey' if by_design else 'yellow'
            reasons.append(f'at offset {w["offset"]} the note differs by {fmt(w["maxRelDb"])} dB re its peak '
                           f'({fmt(w["maxAbsDiff"])} absolute), first in the {w["region"]} block; it {w["extent"]}')
            if r['known']:
                reasons.append(r['known'])
        elif cls == 'rounding':
            reasons.append(f'rounding only: {fmt(w["maxRelDb"])} dB re the peak')
        if r['rngDiffers']:
            light = 'yellow'
            reasons.append(f'the voice drew a different number of random values at {r["rngDiffers"]} offsets')
        shown = [w] + [d for d in r['differing'] if d['offset'] != w['offset']]
        items.append({
            'id': f'{r["family"]}/{r["stage"]}/{r["knobs"]}/{r["sweep"]}', 'group': f'{r["family"]} {r["stage"]}',
            'title': f'{r["sweep"]}  {r["knobs"]}', 'light': light, 'reasons': reasons,
            'value': cls if cls in ('identical', 'rounding') else f'{fmt(w["maxRelDb"], 3)} dB',
            'columns': ['offset', 'class', 'max dB re peak', 'max abs', 'at frame', 'from event', 'rms dB', 'first diff',
                        'region', 'extent', 'late in the hold'],
            'rows': [[d['offset'], d['class'], blank(d['maxRelDb']), fmt(d['maxAbsDiff']), d['maxDiffFrame'],
                      d['maxDiffFromEvent'], blank(d['rmsRelDb']), d['firstDiffFrame'], d['region'], d['extent'],
                      fmt(d['lateMaxAbs'])] for d in shown],
        })
    return items


# ── The page ────────────────────────────────────────────────────────────────────────────────────────────────


def probe(pid, title, question, items, note=''):
    counts = {c: sum(1 for i in items if i['light'] == c) for c in ('red', 'yellow', 'green', 'grey', 'blue')}
    worst = max((i['light'] for i in items), key=lambda c: RANK[c], default='none')
    order = {'red': 0, 'yellow': 1, 'grey': 2, 'green': 3, 'blue': 4}
    if not all(i['light'] == 'blue' for i in items):
        items = sorted(items, key=lambda i: (order[i['light']], i['group'], i['title']))
    return {'id': pid, 'title': title, 'question': question, 'note': note, 'built': True, 'light': worst,
            'counts': counts, 'items': items}


def apply_verdicts(probes, verdicts):
    seen = set()
    for p in probes:
        for item in p['items']:
            v = verdicts.get(f'{p["id"]}/{item["id"]}')
            if not v:
                continue
            seen.add(f'{p["id"]}/{item["id"]}')
            item['auto'] = item['light']
            item['light'] = VERDICT_LIGHT[v['status']]
            item['verdict'] = v
    for key in sorted(set(verdicts) - seen):
        print(f'WARNING: verdict for an item that is gone: {key}', file=sys.stderr)


def main():
    p = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    p.add_argument('--run', default=None, help='the run folder (default: the newest docs/audio-audit/<date>)')
    p.add_argument('--out', default=OUT)
    a = p.parse_args()
    runs = sorted(d for d in glob.glob(os.path.join(AUDIT, '20[0-9][0-9]-[0-9][0-9]-[0-9][0-9]')) if os.path.isdir(d))
    run = a.run or (runs[-1] if runs else None)
    if not run:
        sys.exit('engine-health: no run folder under docs/audio-audit')

    raw = []
    doc = load(run, 'stage-probe.json')
    if doc is not None:
        raw.append(('stage-probe', 'Stage probe',
                    'One stage alone under eight signals, three of them poison: does it stay finite, recover, fall silent?',
                    stage_probe_items(doc), 'Each stage alone, fixed knobs, no host; the per-note stages in mono.'))
    doc = load(run, 'hot-path-lint.json')
    if doc is not None:
        raw.append(('hot-path-lint', 'Hot-path lint',
                    'Does render code hold what the engine rules forbid: boxed types, allocation, capturing lambdas?',
                    lint_items(doc), f'{doc["hot_functions"]} functions count as render code, in {doc["files"]} files.'))
    doc = load(run, 'dead-knobs.json')
    if doc is not None:
        raw.append(('dead-knobs', 'Dead-knob finder',
                    'Does every slot key a door writes have an engine reader, and the reverse?',
                    knob_items(doc), 'Source text only; keys inside song and tutorial text are not read.'))
    doc = load(run, 'stage-cost.json')
    if doc is not None:
        raw.append(('stage-cost', 'Stage cost table',
                    'What does each source, stage and voice cost alone, in nanoseconds per sample?',
                    cost_items(doc), 'JVM only; the browser ranks differently. Under 10 percent between two runs is noise.'))

    doc = load(run, 'aliasing.json')
    if doc is not None:
        raw.append(('aliasing', 'Aliasing meter', PLANNED[0][2], aliasing_items(doc),
                    'Steady tones, analog 0, sources bare; the floor is about -165 dB. Yellow is a question for ears.'))
    doc = load(run, 'clicks.json')
    if doc is not None:
        raw.append(('clicks', 'Click detector', PLANNED[1][2], click_items(doc),
                    'A 110 Hz tone, one change at a time; a 50 ms glide reads kink 2.5 and -86 dB, a hard step 3618 and -18 dB.'))
    doc = load(run, 'framing.json')
    if doc is not None:
        raw.append(('framing', 'Block-framing checker', PLANNED[2][2], framing_items(doc),
                    'The production voice path, the onset, the gate close and a cut at each of the 128 offsets in a block.'))
    probes = [probe(*r) for r in raw]
    verdicts_path = os.path.join(AUDIT, 'verdicts.json')
    verdicts = {}
    if os.path.exists(verdicts_path):
        with open(verdicts_path, encoding='utf-8') as fh:
            verdicts = {k: v for k, v in json.load(fh).items() if not k.startswith('_')}
    apply_verdicts(probes, verdicts)
    probes = [dict(probe(q['id'], q['title'], q['question'], q['items'], q['note'])) for q in probes]
    built = {q['id'] for q in probes}
    for pid, title, question in PLANNED:
        if pid not in built:
            probes.append({'id': pid, 'title': title, 'question': question, 'note': 'Not built yet (KLANG-00420).',
                           'built': False, 'light': 'none', 'counts': {}, 'items': []})

    data = {'run': os.path.basename(run), 'built': dt.date.today().isoformat(), 'verdicts': len(verdicts),
            'probes': probes}
    template = open(os.path.join(HERE, 'engine-health.template.html'), encoding='utf-8').read()
    css = open(os.path.join(HERE, 'hud.css'), encoding='utf-8').read()
    html = template.replace('/*__HUD_CSS__*/', css).replace('__DATA__', json.dumps(data).replace('</', '<\\/'))
    with open(a.out, 'w', encoding='utf-8') as fh:
        fh.write(html)
    for q in probes:
        c = q['counts']
        tally = ', '.join(f'{c[k]} {k}' for k in ('red', 'yellow', 'green', 'grey', 'blue') if c.get(k))
        print(f'{q["light"]:<7}{q["title"]}: {tally or "not built"}')
    print(f'wrote {os.path.relpath(a.out, ROOT)} ({os.path.getsize(a.out) // 1024} KB) from {os.path.relpath(run, ROOT)}')


if __name__ == '__main__':
    main()
