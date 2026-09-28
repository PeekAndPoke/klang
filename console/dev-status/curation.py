"""Hand-maintained half of the dev-status pages (the other half is read from the docs by build.py).

Update it whenever the pages are rebuilt: build.py lists the open docs it finds no entry for
(it guesses their area and state meanwhile) and the entries that name docs that no longer exist.
Everything here is judgement, not data: areas, real states (the doc's own status line can be
stale), notes, the dashed "related" edges, and the guessed completion dates of the mission log.
"""

# Docs left off the pages: index files, and short-lived working docs not worth a node.
EXCLUDE={
    'docs/tasks/_priorities.md', 'docs/tasks/_v1-scope.md', 'docs/tasks/by-ear/README.md',
    'docs/plans/phase3-step12-master-as-katalyst.md',
}

# Areas of the open docs (ids are file names without .md). Order is the order of the lanes.
AREAS={
'engine':['signal-flow-redesign','builtin-instruments','katalyst-dsl','katalyst-master-configure-doors','effect-state-machines','knob-glide','transition-times','oversampling-regions','block-framing-invariance','unified-eq','signal-graph-engine','dsl-kotlin-surface-parity','audit-audio-backend-leftovers','audit-parked-decisions'],
'sound':['bugfix-master-limiter-surge','bugfix-ignitor-non-finite-pitch-amount','master-dsl-followups','idea-master-saturation','voice-takeover','pluck-release-tail','envelope-shape-followups','cut-group-semantics','ducking-unfinished','new-oscillators','flanger-chorus','ir-to-modal-table-extraction','phoneme-singing','analog-drift-ratio-tuning','c3-depth-migration-flags','soundfont-variant-curation','soundfont-zone-selection','sample-mirror-operations','sine-partial-banks'],
'perf':['svf-coefficient-cache-never-engages','optimize-affine-chain-fusion','optimize-constant-control-fast-path','ignitor-optimizer-open-items','optimizer-on-the-frontend','high-performance-audio-backend','zig-motor-one-engine','first-run-spike-v2','browser-benchmark-page','reduce-js-bundle-size'],
'realtime':['realtime-playback-controller','playback-layer-decomposition','live-voice-modulation','midi-latency-optimizations','worklet-clock-divergence'],
'analysis':['realtime-analysis','realtime-analysis-ui','realtime-analytics-meters','auto-mix-advisor'],
'sprudel':['sprudel-sound-doors-compound','sprudel-sound-function-surface','sprudel-function-testing','sprudel-test-coverage-and-review','mini-notation-legato','mini-notation-tweaks-followups','n-pattern-add-noop','string-slot-readers','silent-shape-discard-on-error'],
'script':['klangscript-intellisense','klangscript-named-args-docs-polish','klangscript-native-object-operators','klangscript-union-types','editor-local-symbol-completion','stored-lambda-type-inference','bugfix-native-interop-function-values','runtime-errors-in-the-editor','sprudel-ui-tools','ui-vintage-look-inspiration'],
'launch':['tutorial-curriculum','tutorial-fix-and-through-line','tutorial-master-plan','blog-optimization-series','federated-song-sharing','copyright-audit-07-control-vocabulary-legal-review'],
}

# Real state of an open doc. Missing: "future" under docs/tasks/future/, else "open".
STATES={
'active':['signal-flow-redesign','builtin-instruments','katalyst-dsl','effect-state-machines','knob-glide','sprudel-test-coverage-and-review'],
'next':['oversampling-regions','tutorial-fix-and-through-line','tutorial-curriculum','blog-optimization-series'],
'blocked':['voice-takeover','copyright-audit-07-control-vocabulary-legal-review','realtime-analysis'],
'byear':['analog-drift-ratio-tuning','c3-depth-migration-flags'],
'finished':['copyright-audit-00-overview'],
'reference':['sine-partial-banks','sample-mirror-operations','midi-latency-optimizations','string-slot-readers','soundfont-variant-curation'],
'future':['transition-times','signal-graph-engine','zig-motor-one-engine','realtime-analysis-ui','realtime-analytics-meters','pluck-release-tail'],
}

# Docs whose header claims a status the history contradicts (a red dot on the map).
STALE={'signal-flow-redesign','katalyst-dsl'}

# Assessments shown in the details panel.
NOTES={
'signal-flow-redesign':'Header still says "not started". Phase 1 (Katalyst, steps 5b and 5c) and phase 3 steps 1 to 10 have landed; 129 commits since 2026-09-17.',
'katalyst-dsl':'Header still says "not started", but the Katalyst 5b and 5c steps (orbit fields off the wire, fades, glides, bank crossfade) have landed.',
'builtin-instruments':'Phase 3 of the signal-flow redesign. Step 9 (the voice strip and Pipeline DSL retire) done 2026-09-27; step 12 takes the master swap.',
'effect-state-machines':'Every per-effect row done; the chain-swap state is folded into phase 3 step 12.',
'unified-eq':'Largely shipped (D2b, D4, D5 marked shipped). Worth a check of what remains.',
'sine-partial-banks':'Shipped 2026-09-07; kept in plans/ on purpose as the reference.',
'block-framing-invariance':'Plan not started as a whole, but P4 and P5 were done 2026-08-31 (per _v1-scope).',
'realtime-analysis':'Umbrella for the analysis tasks, gated on the UI structure decision.',
'voice-takeover':'Blocked on a design decision (2026-09-08). Would also remove the limiter surge at its source.',
'oversampling-regions':'Scheduled after the signal-flow redesign completes. Holds the oversampler quality findings since 2026-09-27.',
}

# Related docs that do not link to each other yet: (a, b, why). Dashed on the map.
RELATED=[
('ducking-unfinished','audit-parked-decisions','both hold the attackSeconds naming question (F17a)'),
('ducking-unfinished','katalyst-dsl','duck is a Katalyst orbit stage'),
('mini-notation-legato','mini-notation-tweaks-followups','tweaks are today\'s per-note route to legato'),
('mini-notation-legato','realtime-playback-controller','MIDI recording needs a way to write a held note'),
('live-voice-modulation','realtime-playback-controller','MIDI knobs acting on sounding voices'),
('live-voice-modulation','knob-glide','knob changes that glide'),
('stored-lambda-type-inference','klangscript-intellisense','editor type knowledge'),
('stored-lambda-type-inference','klangscript-union-types','editor type knowledge'),
('stored-lambda-type-inference','editor-local-symbol-completion','local symbols in the editor'),
('klangscript-native-object-operators','bugfix-native-interop-function-values','native interop'),
('bugfix-ignitor-non-finite-pitch-amount','builtin-instruments','phase 3 slotted these knobs'),
('bugfix-ignitor-non-finite-pitch-amount','audit-audio-backend-leftovers','the same silent-failure class'),
('soundfont-variant-curation','soundfont-zone-selection','soundfont playback'),
('soundfont-variant-curation','sample-mirror-operations','sample and soundfont data'),
('ui-vintage-look-inspiration','realtime-analysis-ui','UI look and structure'),
('ui-vintage-look-inspiration','sprudel-ui-tools','editor tool look'),
('browser-benchmark-page','blog-optimization-series','benchmarks as blog material'),
('browser-benchmark-page','first-run-spike-v2','measuring on the phone'),
('analog-drift-ratio-tuning','builtin-instruments','drift lives in classic() now'),
('c3-depth-migration-flags','builtin-instruments','filter envelope law'),
('new-oscillators','phoneme-singing','a formant oscillator'),
('flanger-chorus','oversampling-regions','one DSP core per effect'),
('flanger-chorus','katalyst-dsl','an orbit effect candidate'),
('transition-times','knob-glide','how long a change should take'),
('transition-times','effect-state-machines','fade-in and fade-out states'),
('pluck-release-tail','envelope-shape-followups','envelope ownership follow-ups'),
('n-pattern-add-noop','sprudel-function-testing','untested call forms'),
('silent-shape-discard-on-error','runtime-errors-in-the-editor','errors that should reach the editor'),
('worklet-clock-divergence','midi-latency-optimizations','realtime timing'),
('worklet-clock-divergence','playback-layer-decomposition','playback timing'),
]

# ---- Archive ----

# Archived docs that were closed as won't implement, and ones closed without being built.
WONT={'20260820-resonator-swing','20260824-fractional-pitch-input','20260908-klangscript-caret-as-power-wont-implement'}
SUPER={'20260219-strudel-dsl-documentation','20260227-klang-blocks-take-1','20260927-audio-backend-audit','20260927-code-quality-review',
 '20260927-engine-tuning-profile','20260927-pipeline-dsl-coefficient-exposure','20260927-audio-pipeline-open-topics',
 '20260927-ignitor-dsl-open-items','20260927-mini-notation-extensions'}

# Area keywords for archived docs, first match wins (checked against file name and title).
KW=[('launch',['tutorial','blog','copyright','video','resources-page','website','landing','whitepaper','licens','docs-page','credits','launch','funding']),
 ('script',['klangscript','editor','intellisense','codemirror','completion','named-arg','statement','caret','number-methods','interop','libs-split','ui-tool','uitool','dsl-documentation','docs-registry']),
 ('analysis',['analysis','analytics','meters','oscilloscope']),
 ('realtime',['midi','realtime','playback','live-update','note-off','clock','latency','scheduler','solo']),
 ('perf',['perf','benchmark','optimiz','cache','allocation','bundle','warmup','spike','pool','fast-path','jitter','cpu','fold']),
 ('sprudel',['sprudel','strudel','mini-notation','pattern','euclid','tweak','degrade','chord','voicing','tonal','scale','accessor','legato','arrange','mininotation','klang-blocks','cycle']),
 ('engine',['katalyst','master','pipeline','ignitor','wire','codec','cylinder','orbit','per-playback','block-framing','signal-flow','filter-unification','unification','warehouse','engine','voice-data','voicedata','audit','kotlin-surface','configure-lambda','ksp']),
 ('sound',['reverb','delay','filter','envelope','adsr','supersaw','oscillator','osc','pluck','sample','soundfont','drift','unison','resonator','body','vowel','limiter','compressor','distort','crush','tremolo','phaser','fm','pitch','swing','voice','instrument','exciter','noise','eq','gain','sound','karplus','bell','drum','guitar','piano','synth'])]

# Area overrides for archived docs whose name misleads the keywords (id without the date prefix).
ARCHIVE_AREAS={'master-dsl':'engine','klang-audio-master-configuration':'sound','mini-notation-extensions':'sprudel',
 'TimeManipulationImplementationAnalysis':'sprudel','filter-frequency-param-naming':'sound','fe-code-highlight-improvements':'script',
 'ui-error-display':'script','klang-blocks-take-1':'script','fractional-pitch-input':'sound','block-size-parity':'engine',
 'project-restructuring':'engine','audio-be-module-review':'engine','engine-dsl-design-record':'engine','ai-spending':'launch',
 'motor-branding-rename':'launch','expanded-oscilloscope':'analysis','klang-renderer-with-limiter':'sound'}

# ---- Mission log guesses (dates are ISO strings) ----

# The far-future docs, placed in the last range.
FAR={'zig-motor-one-engine','high-performance-audio-backend','signal-graph-engine','federated-song-sharing'}

# Fixed guesses for single docs.
ETA_FIXED={
 'signal-flow-redesign':'2026-11-05','builtin-instruments':'2026-10-28','katalyst-dsl':'2026-10-20','effect-state-machines':'2026-10-24',
 'knob-glide':'2026-10-15','sprudel-test-coverage-and-review':'2026-12-20','oversampling-regions':'2026-12-05',
 'tutorial-fix-and-through-line':'2027-01-20','tutorial-curriculum':'2027-02-15','tutorial-master-plan':'2027-03-20','blog-optimization-series':'2027-05-10',
 'voice-takeover':'2027-01-10','realtime-analysis':'2027-04-01','copyright-audit-07-control-vocabulary-legal-review':'2027-06-15',
 'bugfix-master-limiter-surge':'2026-11-10','katalyst-master-configure-doors':'2026-11-20','master-dsl-followups':'2026-12-10',
}

# Ranges for the rest: a doc is spread evenly inside the range of its group, bigger docs later.
# Group keys: "byear", "bugs" (open bugfix-/audit- docs), "far", or (state, area) with state "open" or "future".
ETA_RANGES={
 'byear':('2026-10-05','2026-10-25'),
 'bugs':('2026-10-08','2026-11-30'),
 ('open','engine'):('2026-11-01','2026-12-20'),('open','sound'):('2026-11-10','2027-01-31'),
 ('open','sprudel'):('2026-12-01','2027-02-28'),('open','script'):('2027-01-10','2027-03-31'),
 ('open','realtime'):('2027-02-01','2027-04-30'),('open','analysis'):('2027-03-01','2027-05-31'),
 ('open','launch'):('2027-01-15','2027-03-31'),('open','perf'):('2027-04-01','2027-06-30'),
 ('future','engine'):('2027-03-01','2027-09-30'),('future','sound'):('2027-02-01','2027-10-31'),
 ('future','sprudel'):('2027-03-01','2027-08-31'),('future','script'):('2027-04-01','2027-09-30'),
 ('future','realtime'):('2027-05-01','2027-11-30'),('future','analysis'):('2027-05-01','2027-10-31'),
 ('future','launch'):('2027-06-01','2027-12-31'),('future','perf'):('2027-06-01','2027-12-31'),
 'far':('2028-01-15','2028-10-31'),
}

# Milestones drawn on the mission log (guesses, labelled as such on the page).
MILESTONES=[
 dict(date='2026-11-05',label='Engine redesign complete',note='Phase 3 through step 12, Katalyst DSL, knob glide (guess)'),
 dict(date='2027-01-31',label='Tutorial quarter',note='Tutorials fixed and the through-line built (guess)'),
 dict(date='2027-04-01',label='Launch window',note='After the tutorials, per the priorities order (guess)'),
 dict(date='2027-07-01',label='Performance push',note='Hard performance work comes after launch (guess)'),
 dict(date='2028-06-01',label='Native engine era',note='Zig Motor, native and Wasm backends (far future, guess)'),
]
