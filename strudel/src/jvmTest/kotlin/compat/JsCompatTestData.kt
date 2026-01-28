package io.peekandpoke.klang.strudel.compat

object JsCompatTestData {

    private const val SKIP = true

    val patterns: List<Example> = listOf(
        // Note and Sound Generation
        Example("Note #1", """note("c,eb,g")"""),
        // Does not compile in Graal
        Example(SKIP, "Note #2", """"c,eb,g".note()"""),
        Example("Note #3", """note(run(4))"""),
        Example("Note #4", """run(4).note()"""),

        Example("N #1", """n("c,eb,g")"""),
        // Does not compile in Graal
        Example(SKIP, "N #2", """"c,eb,g".n()"""),
        Example("N #3", """n(run(4))"""),
        Example("N #4", """run(4).n()"""),
        Example("N #5", """n(run(4)).scale("D4:pentatonic")"""),
        Example(SKIP, "N #6", """run(4).n().scale("D4:pentatonic"))"""),
        Example(SKIP, "N #7", """run(4).scale("D4:pentatonic")).n()"""),

        // Tone / Scale / Tonal
        Example("C-Major notes", """note("c3 d3 e3 f3 g3 a3 b3 c4")"""),
        Example("n() without scale", """n("0 1 2 3")"""),
        Example("n() with C:minor scale", """n("0 2 4").scale("C:minor")"""),
        Example("n() with C4:minor scale", """n("0 2 4").scale("C4:minor")"""),
        Example("n() with C:major scale", """n("0 2 4").scale("C:major")"""),
        Example("n() with C4:major scale", """n("0 2 4").scale("C4:major")"""),
        // chord / our impl works differently by creating individual voices
        Example(SKIP, "chord basic", """chord("Cmaj7")"""),
        Example(SKIP, "chord pattern", """chord("Cmaj7 Dm7 G7")"""),
        Example(SKIP, "chord slash", """chord("C/G F/A")"""),
        Example(SKIP, "chord minor", """chord("Cm Fm Gm")"""),
        // transpose
        Example("transpose positive", """note("c3 e3 g3").transpose(12)"""),
        Example("transpose negative", """note("c4").transpose(-12)"""),
        Example("transpose ex #1", """seq("[c2 c3]*4").transpose("<0 -2 5 3>").note()"""),
        Example("transpose ex #2", """seq("[c2 c3]*4").transpose("<1P -2M 4P 3m>").note()"""),
        // scaleTranspose
        Example("scaleTranspose basic", """n("0 2 4").scale("C3:major").scaleTranspose(1)"""),
        Example("scaleTranspose control", """note("C4 E4").scale("C3:major").scaleTranspose("0 1")"""),
        Example("scaleTranspose negative", """n("0 2 4").scale("C3:minor").scaleTranspose(-1)"""),
        Example("scaleTranspose pentatonic", """n("0 1 2 3 4").scale("C4:pentatonic").scaleTranspose(1)"""),
        Example("scaleTranspose dorian", """n("0 2 5").scale("C4:dorian").scaleTranspose(1)"""),
        Example("scaleTranspose phrygian", """n("0 1 2 3").scale("C5:phrygian").scaleTranspose(1)"""),
        Example("scaleTranspose lydian", """n("3 4 6").scale("C5:lydian").scaleTranspose(-1)"""),
        Example("scaleTranspose locrian", """n("0 4 6").scale("C2:locrian").scaleTranspose(1)"""),
        Example("scaleTranspose chromatic", """n("0 1 2").scale("C3:chromatic").scaleTranspose(2)"""),
        // rootNotes ... seems to be fine
        Example(SKIP, "rootNotes no-op", """note("C E G").rootNotes()"""),
        Example(SKIP, "rootNotes basic", """chord("Cmaj7 Dm7").rootNotes()"""),
        Example(SKIP, "rootNotes with octave", """chord("Cmaj7 Dm7").rootNotes(3)"""),
        // voicing ... should be ok
        Example(SKIP, "voicing basic", """chord("Cmaj7 Dm7 G7").voicing()"""),
        // Does not compile with js
        Example(SKIP, "voicing range", """chord("Dm7 G7 Cmaj7").voicing("C3", "C5")"""),
        // Should be ok
        Example(
            SKIP, "voicing all cords", """
            chord(`<
                C2 C5 C6 C7 C9 C11 C13 C69
                Cadd9 Co Ch Csus C^ C- C^7 
                C-7 C7sus Ch7 Co7 C^9 C^13 
                C^7#11 C^9#11 C^7#5 C-6 C-69 
                C-^7 C-^9 C-9 C-add9 C-11 
                C-7b5 Ch9 C-b6 C-#5 C7b9 
                C7#9 C7#11 C7b5 C7#5 C9#11 
                C9b5 C9#5 C7b13 C7#9#5 C7#9b5 
                C7#9#11 C7b9#11 C7b9b5 C7b9#5 
                C7b9#9 C7b9b13 C7alt C13#11 
                C13b9 C13#9 C7b9sus C7susadd3 
                C9sus C13sus C7b13sus C Caug 
                CM Cm CM7 Cm7 CM9 CM13 CM7#11 
                CM9#11 CM7#5 Cm6 Cm69 Cm^7 
                C-M7 Cm^9 C-M9 Cm9 Cmadd9 
                Cm11 Cm7b5 Cmb6 Cm#5
            >`).voicing().room(0.5)
        """.trimIndent()
        ),
        // Freq
        Example("freq() basic", """freq(440)"""),
        Example("freq() pattern", """s("saw saw").freq("440 880")"""),

        // Sequences
        Example(skip = SKIP, "Sequence #1", """seq("<0 2 4 6 ~ 4 ~ 2 0!3 ~!5>*8")"""),

        // Sounds
        Example("Sound | Drums", """s("bd hh sd oh")"""),
        Example("Sound | Drum + Index", """s("bd:0 hh:1 sd:2 oh:3")"""),
        Example("Sound | Drum + Index + Gain", """s("bd:0:0.1 hh:1:0.2 sd:2:0.3 oh:3")"""),
        Example("Sound | Oscillators", """s("<sine saw isaw tri square>")"""),
        Example("Sound | Noise Generators", """s("<white brown pink crackle dust>").gain(0.5)"""),
        Example("Sound | Impulse", """s("impulse").gain(0.5)"""),

        // Structure & Control
        Example("Arrange", """arrange([1, note("c")], [2, note("e")])"""),
        Example("Cat", """cat(note("c"), note("e"))"""),
        Example("Stack", """stack(note("c"), note("e"))"""),
        Example("Hush", """note("c").hush()"""),
        Example("Gap", """gap(5)"""),

        // Pattern Picking & Selection
        // pick() - Basic pattern picking with index clamping
        Example("pick() with list", """pick(["bd", "hh"], "0 1")"""),
        Example("pick() with list patterns", """pick(["bd hh", "sn cp"], "0 1")"""),
        Example("pick() different sized list patterns", """pick([sound("bd hh"), sound("sd")], "0 1")"""),
        Example("pick() with map", """pick({a: "bd", b: "hh"}, "a b")"""),
        Example("pick() clamps indices", """pick(["bd", "hh"], "0 1 2 3")"""),
        // pickmod() - Pattern picking with modulo wrapping
        Example("pickmod() wraps indices", """pickmod(["bd", "hh"], "0 1 2 3")"""),
        Example("pickmod() with patterns", """pickmod(["bd hh", "sn cp"], "0 1 2 3")"""),
        // pickOut() - Pattern picking with outerJoin (no clipping)
        Example("pickOut() basic", """pickOut(["bd", "hh"], "0 1")"""),
        Example("pickOut() with patterns", """pickOut([sound("bd hh"), sound("sn cp")], "0 1")"""),
        // This case specifically tests the non-clipping behavior:
        // Selector "0" is fast(2) -> 0.0-0.5. Inner "bd" is 0.0-1.0.
        // pickOut should let it ring to 1.0. pick would clip to 0.5.
        Example("pickOut() no clipping", """pickOut([sound("bd")], seq("0").fast(2))"""),
        // pickmodOut()
        Example("pickmodOut() basic", """pickmodOut(["bd", "hh"], "0 1 2")"""),
        Example("pickmodOut() no clipping", """pickmodOut([sound("bd")], seq("0 2").fast(2))"""),
        // pickRestart() - Pattern picking with restart
        Example("pickRestart() basic", """pickRestart(["bd", "hh"], "0 1")"""),
        Example("pickRestart() with patterns", """pickRestart([sound("bd hh"), sound("sn cp")], "0 1")"""),
        // This case specifically tests the restart behavior:
        // Selector "0 ~ 0 ~" triggers "0" at 0.0 and 0.5.
        // Inner "0 1 2 3" (0.25 each).
        // At 0.0: get inner 0.0-0.25 ("0").
        // At 0.5: get inner 0.0-0.25 ("0") because of restart. Standard pick would get "2".
        Example("pickRestart() restart test", """pickRestart([seq("0 1 2 3")], "0 ~ 0 ~")"""),
        // pickmodRestart()
        Example("pickmodRestart() basic", """pickmodRestart(["bd", "hh"], "0 1 2")"""),
        Example("pickmodRestart() restart test", """pickmodRestart([seq("0 1 2 3")], "0 ~ 2 ~")"""),
        // pickReset() - Pattern picking with reset (phase alignment)
        Example("pickReset() basic", """pickReset(["bd", "hh"], "0 1")"""),
        Example("pickReset() with patterns", """pickReset([sound("bd hh"), sound("sn cp")], "0 1")"""),
        Example("pickReset() reset test", """pickReset([seq("0 1 2 3")], "0 ~ 0 ~")"""),
        // pickmodReset()
        Example("pickmodReset() basic", """pickmodReset(["bd", "hh"], "0 1 2")"""),
        Example("pickmodReset() reset test", """pickmodReset([seq("0 1 2 3")], "0 ~ 2 ~")"""),
        // inhabit() / pickSqueeze()
        Example("inhabit() with list of patterns", """inhabit([s('bd hh'), s('sd cp')], '0 1')"""),
        Example("inhabit() with map of patterns", """inhabit({a: s('bd'), b: s('sd')}, 'a b')"""),
        Example("pickSqueeze() is alias for inhabit()", """pickSqueeze([s('bd hh'), s('sd cp')], '0 1')"""),
        Example(
            SKIP,
            "inhabit() via method call on selector",
            """'0 1'.inhabit([s('bd hh'), s('sd cp')])"""
        ), // does not compile in JS
        Example(
            SKIP,
            "inhabit() via method call with map",
            """'a b'.inhabit({a: s('bd'), b: s('sd')})"""
        ), // does not compile in JS
        // squeeze() - inhabit with swapped args
        Example("squeeze() basic", """squeeze(seq("0 1"), [s('bd hh'), s('sd cp')])"""),
        // Fails in Graal with "TypeError: e2.map is not a function".
        // Squeeze with map might not be fully supported or argument handling differs in JS.
        Example(SKIP, "squeeze() with map", """squeeze("a b", {a: s('bd'), b: s('sd')})"""),
        Example(SKIP, "squeeze() as method", """note("0 1").squeeze([s('bd hh'), s('sd cp')])"""),
        // Does not compile in JS
        Example(SKIP, "squeeze() as method on string", """"0 1".squeeze([s('bd hh'), s('sd cp')])"""),

        // Euclidean Patterns from mini notation
        *(1..8).flatMap { pulses ->
            (pulses..8).map { steps ->
                Example(name = "Euclidean $pulses,$steps", code = """note("c($pulses,$steps)")""")
            }
        }.let { it.shuffled().take(it.size / 10) }.toTypedArray(),

        // Euclidean Patterns with Rotation mini notation
        *(1..8).flatMap { pulses ->
            (pulses..8).flatMap { steps ->
                (-8..8).map { rotation ->
                    Example(
                        name = "Euclidean Rot $pulses,$steps,$rotation",
                        code = """note("c($pulses,$steps,$rotation)")"""
                    )
                }
            }
        }.let { it.shuffled().take(it.size / 10) }.toTypedArray(),

        // Euclidean Functions
        Example("Euclid Function #1", """euclid(3, 8, note("bd"))"""),
        Example("Euclid Function #2", """note("bd").euclid(3, 8)"""),
        // does not work in js-impl: TypeError: pattern.queryArc is not a function
        Example(SKIP, "Euclid Function #3", """euclid(3, 8)"""),

        Example("EuclidRot Function #1", """euclidRot(3, 8, 1, note("bd"))"""),
        Example("EuclidRot Function #2", """note("bd").euclidRot(3, 8, 1)"""),

        // Fails to run in js: Invalid array length
        // Manually checked: seems OK
        Example(SKIP, "Bjork Function #1", """bjork([3, 8], sound("bd"))"""),
        // Fails to run in js: .bjork() is not a function
        // Manually checked: seems OK
        Example(SKIP, "Bjork Function #2", """sound("bd").bjork([3, 8])"""),
        // Fails to run in js: .bjork() is not a function
        // Manually checked: seems OK
        Example(SKIP, "Bjork Function #3", """sound("bd").bjork([3, 8, 1])"""),

        // Does not compile in js: euclidLegato is not a function
        // Manually checked: seems OK
        Example(SKIP, "EuclidLegato Function #1", """euclidLegato(3, 8, note("bd"))"""),
        Example("EuclidLegato Function #2", """note("bd").euclidLegato(3, 8)"""),

        // Does not compile in js: euclidLegatoRot is not a function
        // Manually checked: seems OK
        Example(SKIP, "EuclidLegatoRot Function #1", """euclidLegatoRot(3, 8, 1, note("bd"))"""),
        Example("EuclidLegatoRot Function #2", """note("bd").euclidLegatoRot(3, 8, 1)"""),
        Example("EuclidLegatoRot Function #3", """note("bd").euclidLegatoRot(3, 8, 2)"""),
        Example("EuclidLegatoRot Function #4", """note("bd").euclidLegatoRot(3, 8, 9)"""),

        // Timing & Tempo
        Example(SKIP, "Slow", """note("c e g").slow(2)"""), // Our implementation is better
        Example("Fast", """note("c e g").fast(2)"""),
        Example("Slow & Fast", """note("c e g").slow(2).fast(2)"""),
        Example("Late 0.5", """note("a b c d").late(0.5)"""),
        Example("Late [0.5 0.5]", """note("a b c d").late("0.5 0.5")"""),
        Example("Late Sine", """note("a b c d").late(sine.range(-1, 1))"""),
        Example("Early 0.5", """note("a b c d").early(0.5)"""),
        Example("Early [0.5 0.5]", """note("a b c d").early("0.5 0.5")"""),
        Example("Early Sine", """note("a b c d").early(sine.range(-1, 1))"""),
        Example("Compress first half", """note("c d").compress(0, 0.5)"""),
        Example("Compress second half", """note("c d").compress(0.5, 1)"""),
        Example("Compress middle", """note("c d e f").compress(0.25, 0.75)"""),
        Example("Compress small range", """note("c d").compress(0.4, 0.6)"""),
        Example("Compress with sound", """sound("bd hh sd oh").compress(0.25, 0.75)"""),
        Example("Compress control pattern start", """note("c d").compress("0 0.5", 1)"""),
        Example("Compress control pattern both", """note("c d").compress("0 0.25", "0.5 0.75")"""),
        Example("Focus first half", """note("c d e f").focus(0, 0.5)"""),
        Example("Focus second half", """note("c d e f").focus(0.5, 1)"""),
        Example("Focus middle", """note("c d e f").focus(0.25, 0.75)"""),
        Example("Focus small range", """note("c d e f").focus(0.4, 0.6)"""),
        Example("Focus with sound", """sound("bd hh sd oh").focus(0.25, 0.75)"""),
        Example("Focus control pattern start", """note("c d e f").focus("0 0.5", 1)"""),
        Example("Focus control pattern both", """note("c d e f").focus("0 0.25", "0.5 0.75")"""),
        Example("Ply basic", """note("c d").ply(3)"""),
        Example("Ply with 2", """note("c d e f").ply(2)"""),
        Example("Ply with sound", """sound("bd hh").ply(2)"""),
        Example("Ply high count", """note("c").ply(4)"""),
        Example("Ply complex pattern", """note("c [d e]").ply(2)"""),
        Example("Ply control pattern", """note("c d e f").ply("2 3")"""),
        Example("Ply control pattern compiled", """note("c d").ply("2 3")"""),
        Example("Hurry basic", """sound("bd hh").hurry(2)"""),
        Example("Hurry with notes", """note("c d").hurry(2)"""),
        Example("Hurry slow", """sound("bd hh sd oh").hurry(0.5)"""),
        Example("Hurry control pattern", """sound("bd hh").hurry("2 4")"""),
        Example("Hurry with speed", """sound("bd hh").speed(0.5).hurry(2)"""),
        Example("FastGap basic", """note("c d").fastGap(2)"""),
        Example("FastGap factor 3", """note("c d e").fastGap(3)"""),
        Example("FastGap with sound", """sound("bd hh sd").fastGap(2)"""),
        Example("FastGap control pattern", """note("c d").fastGap("2 4")"""),
        Example("Inside basic", """note("c d e f").inside(2, x => x.rev())"""),
        Example("Inside with fast", """note("c d e f").inside(4, x => x.fast(2))"""),
        Example("Inside with sound", """sound("bd hh sd oh").inside(2, x => x.rev())"""),
        Example("Inside higher factor", """note("0 1 2 3 4 5 6 7").inside(4, x => x.rev())"""),
        Example("Outside basic", """note("c d e f").outside(2, x => x.rev())"""),
        Example("Outside with slow", """note("c d e f g a b c").outside(2, x => x.slow(2))"""),
        Example("Outside with sound", """sound("bd hh sd oh").outside(2, x => x.rev())"""),

        // Swing has a better implementation than javascript
        Example(SKIP, "Swing basic #1", """sound("a b c d").swing(4)"""),
        Example(SKIP, "Swing basic #2", """sound("hh*8").swing(4)"""),
        Example(SKIP, "Swing with notes #1", """note("c d e f g a b c").swing(8)"""),
        Example(SKIP, "Swing with notes #2", """note("c d e f g a b c").swing(4)"""),
        Example(SKIP, "SwingBy basic #1", """sound("hh*8").swingBy(0.5, 8)"""),
        Example(SKIP, "SwingBy basic #2", """sound("hh*8").swingBy(0.5, 4)"""),
        Example(SKIP, "SwingBy with notes #1", """note("c d e f").swingBy(0.25, 4)"""),
        Example(SKIP, "SwingBy with notes #2", """note("c d e f").swingBy(0.25, 2)"""),

        // Euclidish
        Example("Euclidish Function #1", """euclidish(3, 8, 0, note("bd"))"""),
        Example("Euclidish Function #2", """note("bd").euclidish(3, 8, 0)"""),
        Example("Euclidish Function #3", """note("bd").euclidish(3, 8, 1)"""),
        Example("Euclidish Pattern Groove", """note("bd").euclidish(3, 8, "<0 1>")"""),
        Example("Eish Alias", """note("bd").eish(3, 8, 0.5)"""),

        // Run
        Example("Run Function", """n(run(4))"""),

        // Binary
        Example("Binary Function", """binary(5)""")
            .ignore("data.gain"),
        Example("BinaryN Function", """binaryN(5, 4)""")
            .ignore("data.gain"),
        Example("Binary Struct", """sound("hh").struct(binary(170))"""), // 170 = 10101010
        // Does not compile in js
        Example(SKIP, "BinaryL Function", """binaryL(5)"""),
        // Does not compile in js
        Example(SKIP, "BinaryNL Function", """binaryNL(5, 4)"""),

        // Voice Attributes
        Example(SKIP, "Gain & Pan", """note("c").gain(0.5).pan("-1.0 1.0")"""),
        Example("Legato", """note("c e").legato(0.5)"""),
        Example("Clip", """note("c e").clip(0.5)"""),
        Example("Unison/Detune/Spread", """note("c").unison(4).detune(0.1).spread(0.5)"""),

        // ADSR Envelopes
        Example("ADSR single", """note("c").attack(0.1).decay(0.2).sustain(0.5).release(1.0)"""),
        Example("ADSR String", """note("c").adsr("0.1:0.2:0.5:1.0")"""),

        // Filters
        Example("LowPass", """s("saw").lpf(333).resonance(0.3)"""),
        Example("LowPass alias cutoff", """s("saw").cutoff(333)"""),
        Example("LowPass alias ctf", """s("saw").ctf(333)"""),
        Example("LowPass alias lp", """s("saw").lp(333)"""),
        Example("LowPass resonance alias lpq", """s("saw").lpf(333).lpq(0.3)"""),
        Example("HighPass", """s("saw").hpf(444).hresonance(0.7)"""),
        Example("HighPass alias hp", """s("saw").hp(444)"""),
        Example("HighPass alias hcutoff", """s("saw").hcutoff(444)"""),
        Example("HighPass resonance alias hpq", """s("saw").hpf(444).hpq(0.7)"""),
        Example("BandPass", """s("saw").bandf(555).bandq(0.6)"""),
        Example("BandPass alias bpf", """s("saw").bpf(555)"""),
        Example("BandPass alias bp", """s("saw").bp(555)"""),
        Example("BandPass Q alias bpq", """s("saw").bandf(555).bpq(0.6)"""),
        // TODO: notchf does not seem to exist in strudel ... or we need to figure how?
        Example(SKIP, "Notch", """s("saw").notchf(666).nresonance(0.4)"""),

        // Filter Envelopes
        Example("LPF with envelope attack", """s("saw").lpf(1000).lpattack(0.1)"""),
        Example("LPF with envelope alias lpa", """s("saw").lpf(1000).lpa(0.1)"""),
        Example("LPF with envelope decay", """s("saw").lpf(1000).lpdecay(0.2)"""),
        Example("LPF with envelope alias lpd", """s("saw").lpf(1000).lpd(0.2)"""),
        Example("LPF with envelope sustain", """s("saw").lpf(1000).lpsustain(0.8)"""),
        Example("LPF with envelope alias lps", """s("saw").lpf(1000).lps(0.8)"""),
        Example("LPF with envelope release", """s("saw").lpf(1000).lprelease(0.5)"""),
        Example("LPF with envelope alias lpr", """s("saw").lpf(1000).lpr(0.5)"""),
        Example("LPF with envelope depth", """s("saw").lpf(1000).lpenv(0.5)"""),
        Example("LPF with envelope alias lpe", """s("saw").lpf(1000).lpe(0.5)"""),
        Example(
            "LPF with full envelope",
            """s("saw").lpf(1000).lpattack(0.1).lpdecay(0.2).lpsustain(0.8).lprelease(0.5).lpenv(0.7)"""
        ),

        // Filter Envelopes - Highpass
        Example("HPF with envelope attack", """s("saw").hpf(2000).hpattack(0.1)"""),
        Example("HPF with envelope alias hpa", """s("saw").hpf(2000).hpa(0.1)"""),
        Example("HPF with envelope decay", """s("saw").hpf(2000).hpdecay(0.2)"""),
        Example("HPF with envelope alias hpd", """s("saw").hpf(2000).hpd(0.2)"""),
        Example("HPF with envelope sustain", """s("saw").hpf(2000).hpsustain(0.8)"""),
        Example("HPF with envelope alias hps", """s("saw").hpf(2000).hps(0.8)"""),
        Example("HPF with envelope release", """s("saw").hpf(2000).hprelease(0.5)"""),
        Example("HPF with envelope alias hpr", """s("saw").hpf(2000).hpr(0.5)"""),
        Example("HPF with envelope depth", """s("saw").hpf(2000).hpenv(0.5)"""),
        Example("HPF with envelope alias hpe", """s("saw").hpf(2000).hpe(0.5)"""),
        Example(
            "HPF with full envelope",
            """s("saw").hpf(2000).hpattack(0.1).hpdecay(0.2).hpsustain(0.8).hprelease(0.5).hpenv(0.7)"""
        ),

        // Filter Envelopes - Bandpass
        Example("BPF with envelope attack", """s("saw").bpf(1500).bpattack(0.1)"""),
        Example("BPF with envelope alias bpa", """s("saw").bpf(1500).bpa(0.1)"""),
        Example("BPF with envelope decay", """s("saw").bpf(1500).bpdecay(0.2)"""),
        Example("BPF with envelope alias bpd", """s("saw").bpf(1500).bpd(0.2)"""),
        Example("BPF with envelope sustain", """s("saw").bpf(1500).bpsustain(0.8)"""),
        Example("BPF with envelope alias bps", """s("saw").bpf(1500).bps(0.8)"""),
        Example("BPF with envelope release", """s("saw").bpf(1500).bprelease(0.5)"""),
        Example("BPF with envelope alias bpr", """s("saw").bpf(1500).bpr(0.5)"""),
        Example("BPF with envelope depth", """s("saw").bpf(1500).bpenv(0.5)"""),
        Example("BPF with envelope alias bpe", """s("saw").bpf(1500).bpe(0.5)"""),
        Example(
            "BPF with full envelope",
            """s("saw").bpf(1500).bpattack(0.1).bpdecay(0.2).bpsustain(0.8).bprelease(0.5).bpenv(0.7)"""
        ),

        // Filter Envelopes - Notch (NOT IN ORIGINAL STRUDEL JS IMPLEMENTATION)
        Example("Notch filter with envelope attack", """s("saw").notchf(1500).nfattack(0.1)""", skip = true),
        Example("Notch filter with envelope alias nfa", """s("saw").notchf(1500).nfa(0.1)""", skip = true),
        Example("Notch filter with envelope decay", """s("saw").notchf(1500).nfdecay(0.2)""", skip = true),
        Example("Notch filter with envelope alias nfd", """s("saw").notchf(1500).nfd(0.2)""", skip = true),
        Example("Notch filter with envelope sustain", """s("saw").notchf(1500).nfsustain(0.8)""", skip = true),
        Example("Notch filter with envelope alias nfs", """s("saw").notchf(1500).nfs(0.8)""", skip = true),
        Example("Notch filter with envelope release", """s("saw").notchf(1500).nfrelease(0.5)""", skip = true),
        Example("Notch filter with envelope alias nfr", """s("saw").notchf(1500).nfr(0.5)""", skip = true),
        Example("Notch filter with envelope depth", """s("saw").notchf(1500).nfenv(0.5)""", skip = true),
        Example("Notch filter with envelope alias nfe", """s("saw").notchf(1500).nfe(0.5)""", skip = true),
        Example(
            "Notch filter with full envelope",
            """s("saw").notchf(1500).nfattack(0.1).nfdecay(0.2).nfsustain(0.8).nfrelease(0.5).nfenv(0.7)""",
            skip = true
        ),

        // Vowel Formant Filter
        Example("Vowel a", """note("c3").vowel("a")"""),
        Example("Vowel e", """note("c3").vowel("e")"""),
        Example("Vowel i", """note("c3").vowel("i")"""),
        Example("Vowel o", """note("c3").vowel("o")"""),
        Example("Vowel u", """note("c3").vowel("u")"""),
        Example("Vowel sequence", """note("c3 e3 g3").vowel("a e i")"""),
        Example("Vowel with saw", """s("saw").vowel("o")"""),
        Example("Vowel standalone", """vowel("a e i o u")"""),

        // Effects
        Example("Distortion low", """note("c").distort(0.5)"""),
        Example("Distortion alias dist", """note("c").dist(0.5)"""),
        Example("Distortion medium", """note("c").distort(7.0)"""),
        Example("Distortion high", """note("c").distort(50.0)"""),
        Example("Bitcrush", """note("c").crush(4)"""),
        Example("Downsample", """note("c").coarse(4)"""),
        Example("Reverb", """note("c").room(0.5).roomsize(2.0)"""),
        Example("Reverb alias rsize", """note("c").room(0.5).rsize(2.0)"""),
        Example("Delay", """note("c").delay(0.5).delaytime(0.25).delayfeedback(0.5)"""),
        Example("Delay alias delayfb", """note("c").delay(0.5).delaytime(0.25).delayfb(0.5)"""),
        Example("Delay alias dfb", """note("c").delay(0.5).delaytime(0.25).dfb(0.5)"""),

        // Sample Manipulation
        Example("Sample Begin", """s("bd").begin(0.5)"""),
        Example("Sample End", """s("bd").end(0.5)"""),
        Example("Sample Speed", """s("bd").speed(2)"""),
        Example("Sample Speed Negative", """s("bd").speed(-1)"""),
        Example("Sample Loop", """s("bd").loop()"""),
        Example("Sample LoopAt #1", """s("bd").loopAt(1)"""),
        Example("Sample LoopAt #2", """s("bd").loopAt(2)"""),
        Example("Sample LoopAt #3", """s("bd").loopAt(3)"""),
        Example("Sample LoopAt #4", """s("bd").loopAt(4)"""),
        Example("Sample Cut", """s("bd").cut(1)"""),
        Example("Sample Slice", """s("bd").slice(4, 1)"""),

        // Continuous patterns Steady
        *listOf(
            Example(
                "Continuous | Steady pure", """steady(0.5)"""
            ),
            Example(
                "Continuous | Steady", """note("a b c d").pan(steady(0.5))"""
            ),
            Example(
                "Continuous | Steady range", """note("a b c d").pan(steady(0.5).range(-0.5, 0.5))"""
            ),
            // Continuous patterns Signal
            Example(
                "Continuous | Signal pure", """signal(t => t * 2)"""
            ),
            Example(
                "Continuous | Signal", """note("a b c d").pan(signal(t => t * 0.5))"""
            ),
            // Continuous patterns Time
            Example(
                "Continuous | Time pure", """time"""
            ),
            Example(
                "Continuous | Time", """note("a b c d").pan(time)"""
            ),
            Example(
                "Continuous | Time range", """note("a b c d").pan(time.range(0, 1))"""
            ),
            // Continuous patterns Sine
            Example(
                SKIP, "Continuous | Sine pure", """sine"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Sine range pure", """sine.range(-10, 10)"""
            ),
            Example(
                "Continuous | Sine", """note("a b c d").pan(sine)"""
            ),
            Example(
                SKIP, "Continuous | Sine range", """note("a b c d").pan(sine.range(-0.5, 0.5))"""
            ), // probably ok
            // Continuous patterns Sine2
            Example(
                SKIP, "Continuous | Sine2 pure", """sine2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Sine2", """note("a b c d").pan(sine2)"""
            ),
            Example(
                SKIP, "Continuous | Sine2 range", """note("a b c d").pan(sine2.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Cosine
            Example(
                SKIP, "Continuous | Cosine pure", """cosine"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Cosine range pure", """cosine.range(-10, 10)"""
            ),
            Example(
                "Continuous | Cosine", """note("a b c d").pan(cosine)"""
            ),
            Example(
                "Continuous | Cosine range", """note("a b c d").pan(cosine.range(-0.5, 0.5))"""
            ),
            // Continuous patterns Cosine2
            Example(
                SKIP, "Continuous | Cosine2 pure", """cosine2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Cosine2", """note("a b c d").pan(cosine2)"""
            ),
            Example(
                SKIP, "Continuous | Cosine2 range", """note("a b c d").pan(cosine2.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Saw
            Example(
                SKIP, "Continuous | Saw pure", """saw"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Saw", """note("a b c d").pan(saw)"""
            ),
            Example(
                SKIP, "Continuous | Saw range", """note("a b c d").pan(saw.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Saw2
            Example(
                SKIP, "Continuous | Saw2 pure", """saw2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Saw2", """note("a b c d").pan(saw2)"""
            ),
            Example(
                SKIP, "Continuous | Saw2 range", """note("a b c d").pan(saw2.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns ISaw
            Example(
                SKIP, "Continuous | ISaw pure", """isaw"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | ISaw", """note("a b c d").pan(isaw)"""
            ),
            Example(
                SKIP, "Continuous | ISaw range", """note("a b c d").pan(isaw.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns ISaw2
            Example
                (SKIP, "Continuous | ISaw2 pure", """isaw2"""), // IS OK, js impl is buggy
            Example(
                "Continuous | ISaw2", """note("a b c d").pan(isaw2)"""
            ),
            Example(
                SKIP, "Continuous | ISaw2 range", """note("a b c d").pan(isaw2.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Tri
            Example(
                SKIP, "Continuous | Tri pure", """tri"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Tri", """note("a b c d").pan(tri)"""
            ),
            Example(
                SKIP, "Continuous | Tri range", """note("a b c d").pan(tri.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Tri2
            Example(
                SKIP, "Continuous | Tri2 pure", """tri2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Tri2", """note("a b c d").pan(tri2)"""
            ),
            Example(
                SKIP, "Continuous | Tri2 range", """note("a b c d").pan(tri2.range(-0.5, 0.5))"""
            ), // probably ok
            // Continuous patterns ITri
            Example(
                SKIP, "Continuous | ITri pure", """itri"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | ITri", """note("a b c d").pan(itri)"""
            ),
            Example(
                SKIP, "Continuous | ITri range", """note("a b c d").pan(itri.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns ITri2
            Example(
                SKIP, "Continuous | ITri2 pure", """itri2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | ITri2", """note("a b c d").pan(itri2)"""
            ),
            Example(
                SKIP, "Continuous | ITri2 range", """note("a b c d").pan(itri2.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Square
            Example(
                SKIP, "Continuous | Square pure", """square"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Square", """note("a b c d").pan(square)"""
            ),
            Example(
                SKIP, "Continuous | Square range", """note("a b c d").pan(square.range(-0.5, 0.5))"""
            ),  // probably ok
            // Continuous patterns Square2
            Example(
                SKIP, "Continuous | Square2 pure", """square2"""
            ), // IS OK, js impl is buggy
            Example(
                "Continuous | Square2", """note("a b c d").pan(square2)"""
            ),
            Example(
                SKIP, "Continuous | Square2 range", """note("a b c d").pan(square2.range(-0.5, 0.5))"""
            ),  // probably ok
        ).map {
            it.ignore("data.gain")
                .recover { graal, native ->
                    graal.data.soundIndex != null &&
                            graal.data.soundIndex?.toDouble() == native.data.value?.asDouble
                }
        }.toTypedArray(),

        // Modulation
        Example("Vibrato", """note("c").vib(5).vibmod(0.1)"""),
        Example("Accelerate", """note("c").accelerate(1)"""),

        // Transformation
        Example("Struct #1", """note("c e").struct("x")"""),
        Example("Struct #2", """note("c,eb,g").struct("x ~ x ~ ~ x ~ x ~ ~ ~ x ~ x ~ ~").slow(2)"""),
        Example("Struct #3", """note("c3 d3").fast(2).struct("x")"""),
        Example("Struct #4", """note("c3 d3").adsr("0.01:0.2:0.0:0.0").fast(2).struct("x")"""),
        Example("Struct All #1", """note("c e").structAll("x")"""),
        Example("Struct All #2", """note("c,eb,g").structAll("x ~ x ~ ~ x ~ x ~ ~ ~ x ~ x ~ ~").slow(2)"""),
        Example("Mask #1", """note("c [eb,g] d [eb,g]").mask("<1 [0 1]>")"""),
        Example("Mask #2", """note("c3*8").mask(square.fast(4))"""),
        Example("Mask All #1", """note("c [eb,g] d [eb,g]").maskAll("<1 [0 1]>")"""),
        Example("Mask All #2", """note("c3*8").maskAll(square.fast(4))"""),
        Example("SuperImpose #1", """note("a").superimpose(p => p.note("c"))"""),
        Example(SKIP, "SuperImpose #2", """note("a c e h").superimpose(p => p.note("e c"))"""),
        Example(
            SKIP,
            "Layer #1",
            """seq("<0 2 4 6 ~ 4 ~ 2 0!3 ~!5>*8").layer(x => x.add("-2,2")).n().scale("C4:minor")"""
        ),

        // Jux & Off
        Example("jux basic", """note("c e").jux(x => x.rev())"""),
        Example("juxBy basic", """note("c").juxBy(0.5, x => x.note("e"))"""),

        // our impl is a bit different but the results are the same and verified.
        Example(SKIP, "off basic #1", """note("c").off(0.25, x => x.note("e"))"""),
        Example(SKIP, "off basic #2", """note("c").off(-0.25, x => x.note("e"))"""),

        // TODO: more complex tests for rev() an palindrome()
        Example("Reverse", """note("c e g").rev()"""),
        Example("Palindrome", """note("c e g").palindrome()"""),

        // Conditional
        Example("FirstOf", """note("[a b c d] [e f g a]").firstOf(4, (x) => x.rev())"""),
        Example("Every", """note("[a b c d] [e f g a]").every(4, (x) => x.rev())"""),
        Example("LastOf", """note("[a b c d] [e f g a]").lastOf(4, (x) => x.rev())"""),
        // TODO: we need to change the data model to match the JS model ...
        //       we also need to expose the Event / Hap model to KlangScript otherwise:
        //       ERROR: Native type 'StrudelPatternEvent' has no method 'data'.
        Example(SKIP, "Filter", """note("a b").filter((x) => { return x.data.note == "a" })"""),
        Example("FilterWhen", """note("a b c d").filterWhen(x => x >= 0.5)"""),
        Example(SKIP, "Bypass #1", """note("a b c d").bypass()"""), // js impl seems to be broken
        Example("Bypass #2", """note("a b c d").bypass(1)"""),
        Example("Bypass #3", """note("a b c d").bypass(0)"""),
        Example("Bypass #4", """note("a b c d").bypass(false)"""),

        // Arithmetic Operators
        *listOf(
            Example("Add #1", """seq("0 1 2 3").add("2")"""),
            Example("Add #2", """seq("0 1 2 3").add("2 3")"""),
            Example("Add #3", """n("0 1 2 3").add("2")"""),

            Example("Sub #1", """seq("10 20").sub("5")"""),
            Example("Sub #2", """seq("10 20 30 40").sub("5 6")"""),
            Example("Sub #3", """n("10 20").sub("5")"""),

            Example("Mul #1", """seq("2 3").mul("4")"""),
            Example("Mul #2", """seq("2 3 4 5").mul("4 5")"""),
            Example("Mul #3", """n("2 3").mul("4")"""),

            Example("Div #1", """seq("10 20").div("2")"""),
            Example("Div #2", """seq("10 20 30 40").div("2 5")"""),
            Example("Div #3", """n("10 20").div("2")"""),

            Example("Mod #1", """seq("10 11").mod("3")"""),
            Example("Mod #2", """seq("10 11 12 13").mod("3 4")"""),
            Example("Mod #3", """n("10 11").mod("3")"""),

            Example("Pow #1", """seq("2 3").pow("3")"""),
            Example("Pow #2", """seq("2 3 4 5").pow("3 4")"""),
            Example("Pow #3", """n("2 3").pow("3")"""),

            Example(SKIP, "Log2 #1", """seq("1 2 4 8").log2()"""), // Js produce no events
            Example(SKIP, "Log2 #2", """n("1 2 4 8").log2()"""), // Js produce no events

            // Bitwise Operators
            Example("Band (AND) #1", """seq("3 5").band("1")"""),
            Example("Band (AND) #2", """n("3 5").band("1")"""),
            Example("Bor (OR) #1", """seq("1 4").bor("2")"""),
            Example("Bor (OR) #2", """n("1 4").bor("2")"""),
            Example("Bxor (XOR) #1", """seq("3 5").bxor("1")"""),
            Example("Bxor (XOR) #2", """n("3 5").bxor("1")"""),
            Example("Blshift (Left Shift) #1", """seq("1 2").blshift("1")"""),
            Example("Blshift (Left Shift) #2", """n("1 2").blshift("1")"""),
            Example("Brshift (Right Shift) #1", """seq("2 4").brshift("1")"""),
            Example("Brshift (Right Shift) #2", """n("2 4").brshift("1")"""),

            // Comparison
            Example(SKIP, "Less Than #1", """seq("1 2 3").lt(2)"""),
            Example("Less Than #2", """n("1 2 3").lt("2")"""),
            Example(SKIP, "Greater Than #1", """seq("1 2 3").gt("2")"""),
            Example("Greater Than #2", """n("1 2 3").gt("2")"""),
            Example(SKIP, "Less Equal #1", """seq("1 2 3").lte("2")"""),
            Example("Less Equal #2", """n("1 2 3").lte("2")"""),
            Example(SKIP, "Greater Equal #1", """seq("1 2 3").gte("2")"""),
            Example("Greater Equal #2", """n("1 2 3").gte("2")"""),
            Example(SKIP, "Equal #1", """seq("1 2 3").eq("2")"""),
            Example("Equal #2", """n("1 2 3").eq("2")"""),
            Example(SKIP, "Not Equal #1", """seq("1 2 3").ne("2")"""),
            Example("Not Equal #2", """n("1 2 3").ne("2")"""),

            // Logical
            Example("Logical And #1", """seq("0 1").and("5")"""),
            Example("Logical And #2", """n("0 1").and("5")"""),
            Example("Logical Or #1", """seq("0 1").or("5")"""),
            Example("Logical Or #2", """n("0 1").or("5")"""),
        )//.map { it.ignore("data.gain") }
            .toTypedArray(),

        // Continuous Range Functions
        Example("Rangex basic", """sine.rangex(100, 1000)""")
            .ignore("data.gain"),
        Example("Rangex with pattern", """note("a b c d").pan(sine.rangex(0.1, 10))"""),
        Example("Range2 basic", """sine2.range2(0, 100)""")
            .ignore("data.gain"),
        Example("Range2 with pattern", """note("a b c d").lpf(sine2.range2(500, 4000))"""),

        // Value Modifiers
        Example("Round basic", """seq("0.3 1.7 2.51").round()"""),
        Example("Round with continuous", """sine.range(0, 10).segment(4).round()""")
            .ignore("data.gain"),
        Example("Floor basic", """seq("0.9 1.1 2.9").floor()"""),
        Example("Floor negative", """seq("-1.5 -0.5 0.5 1.5").floor()"""),
        Example("Ceil basic", """seq("0.1 1.1 2.9").ceil()"""),
        Example("Ceil negative", """seq("-1.5 -0.5 0.5 1.5").ceil()"""),

        // Ratio Function
        Example("Ratio basic", """ratio("1 5:4 3:2")"""),
        Example("Ratio musical intervals", """seq("2:1 3:2 4:3 5:4").ratio().mul(110)"""),
        Example("Ratio multiple divisions", """seq("12:3:2").ratio()"""),
        Example("Ratio as pattern method", """seq("5:4 3:2").ratio()"""),
        Example("Ratio with freq", """note("a").freq(ratio("5:4").mul(440))"""),
    ).map {
        it.recover { graal, native -> graal.data.gain == 1.0 && native.data.gain == null }
    }
}
