package io.peekandpoke.klang.script.docs

data class DocSection(
    val title: String,
    val description: String,
    val examples: List<DocExample>,
)

data class DocExample(
    val title: String? = null,
    val code: String,
)

val klangScriptDocSections: List<DocSection> = listOf(

    // ==========================================
    // 1. Hello KlangScript
    // ==========================================
    DocSection(
        title = "Hello KlangScript",
        description = "Welcome to KlangScript! Type code, see results. Every example is interactive — edit and run it yourself.",
        examples = listOf(
            DocExample(
                title = "Hello World",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("Hello, KlangScript!")
                    |console.log("Ready to make some music.")
                """.trimMargin(),
            ),
            DocExample(
                title = "Numbers and strings",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("BPM:", 120)
                    |console.log("2 + 3 =", 2 + 3)
                    |console.log("Note:", "C4")
                    |print("print() also works!")
                """.trimMargin(),
            ),
            DocExample(
                title = "Comments",
                code = """
                    |import * from "stdlib"
                    |
                    |// This is a single-line comment
                    |console.log("This runs")
                    |
                    |/* This is a block comment.
                    |   It can span multiple lines. */
                    |
                    |// console.log("This is commented out — it won't run")
                    |console.log("Done!")
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 2. Variables & Constants
    // ==========================================
    DocSection(
        title = "Variables & Constants",
        description = "Use 'let' for values that change and 'const' for values that don't. KlangScript supports numbers, strings, booleans, and null.",
        examples = listOf(
            DocExample(
                title = "let and const",
                code = """
                    |import * from "stdlib"
                    |
                    |let bpm = 120
                    |const title = "My First Beat"
                    |
                    |console.log(title, "at", bpm, "BPM")
                    |
                    |bpm = 140
                    |console.log("Tempo changed to", bpm, "BPM")
                """.trimMargin(),
            ),
            DocExample(
                title = "All value types",
                code = """
                    |import * from "stdlib"
                    |
                    |const tempo = 128
                    |const swing = 0.65
                    |const key = "A minor"
                    |const active = true
                    |let solo = null
                    |
                    |console.log(`Track: ${"$"}{key}, ${"$"}{tempo} BPM, swing: ${"$"}{swing}`)
                    |console.log("active:", active, "solo:", solo)
                """.trimMargin(),
            ),
            DocExample(
                title = "Reassignment and mutation",
                code = """
                    |import * from "stdlib"
                    |
                    |let volume = 80
                    |console.log("Volume:", volume)
                    |
                    |volume = 100
                    |console.log("Volume after boost:", volume)
                    |
                    |let unset
                    |console.log("Uninitialized let:", unset)
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 3. Operators & Expressions
    // ==========================================
    DocSection(
        title = "Operators & Expressions",
        description = "Operators transform values. Arithmetic, comparison, logical, ternary, and assignment operators — plus increment/decrement and short-circuit evaluation.",
        examples = listOf(
            DocExample(
                title = "Arithmetic",
                code = """
                    |import * from "stdlib"
                    |
                    |const bpm = 120
                    |const beatLength = 60 / bpm
                    |console.log("Beat length:", beatLength, "seconds")
                    |
                    |console.log("8 semitones up: 2 **", 8, "=", 2 ** 8)
                    |console.log("Bar position: 17 % 4 =", 17 % 4)
                    |console.log("Halftime:", bpm / 2, "BPM")
                """.trimMargin(),
            ),
            DocExample(
                title = "Comparison and ternary",
                code = """
                    |import * from "stdlib"
                    |
                    |const velocity = 95
                    |console.log("loud?", velocity > 80)
                    |console.log("exact hit?", velocity == 100)
                    |console.log("not silent?", velocity != 0)
                    |console.log("in range?", velocity >= 1 && velocity <= 127)
                    |
                    |const label = velocity >= 90 ? "forte" : "piano"
                    |console.log("Dynamic:", label)
                """.trimMargin(),
            ),
            DocExample(
                title = "Logical operators and short-circuit",
                code = """
                    |import * from "stdlib"
                    |
                    |const hasKick = true
                    |const hasSnare = false
                    |console.log("both?", hasKick && hasSnare)
                    |console.log("either?", hasKick || hasSnare)
                    |console.log("no snare?", !hasSnare)
                    |
                    |// Short-circuit: use || for defaults
                    |const name = null || "default preset"
                    |console.log("Preset:", name)
                    |
                    |// Falsy values: 0, "", null, false
                    |console.log(0 || "zero is falsy")
                    |console.log("" || "empty string is falsy")
                """.trimMargin(),
            ),
            DocExample(
                title = "Assignment operators and increment",
                code = """
                    |import * from "stdlib"
                    |
                    |let vol = 50
                    |vol += 20
                    |console.log("after += 20:", vol)
                    |vol -= 10
                    |console.log("after -= 10:", vol)
                    |vol *= 2
                    |console.log("after *= 2:", vol)
                    |vol /= 4
                    |console.log("after /= 4:", vol)
                    |vol %= 7
                    |console.log("after %= 7:", vol)
                    |
                    |let step = 0
                    |console.log("prefix ++:", ++step)
                    |console.log("postfix ++:", step++)
                    |console.log("after postfix:", step)
                    |console.log("prefix --:", --step)
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 4. Strings & Template Literals
    // ==========================================
    DocSection(
        title = "Strings & Template Literals",
        description = "Template literals with \${...} let you embed expressions in strings. KlangScript provides rich string methods for searching, transforming, and splitting text.",
        examples = listOf(
            DocExample(
                title = "Template literals",
                code = """
                    |import * from "stdlib"
                    |
                    |const note = "C"
                    |const octave = 4
                    |const velocity = 100
                    |
                    |console.log(`Note: ${"$"}{note}${"$"}{octave}`)
                    |console.log(`Velocity: ${"$"}{velocity} / 127 = ${"$"}{Math.round(velocity * 100 / 127)}%`)
                    |console.log(`Is loud: ${"$"}{velocity > 80 ? "yes" : "no"}`)
                """.trimMargin(),
            ),
            DocExample(
                title = "String methods — queries",
                code = """
                    |import * from "stdlib"
                    |
                    |const note = "C#4"
                    |console.log("length:", note.length())
                    |console.log("starts with C:", note.startsWith("C"))
                    |console.log("ends with 4:", note.endsWith("4"))
                    |console.log("# at index:", note.indexOf("#"))
                    |console.log("char at 0:", note.charAt(0))
                """.trimMargin(),
            ),
            DocExample(
                title = "String methods — transforms",
                code = """
                    |import * from "stdlib"
                    |
                    |const raw = "  c4 d4 e4 f4  "
                    |const cleaned = raw.trim()
                    |console.log("trimmed:", cleaned)
                    |console.log("upper:", cleaned.toUpperCase())
                    |console.log("lower:", "LOUD".toLowerCase())
                    |console.log("split:", cleaned.split(" "))
                    |console.log("replace:", cleaned.replace("c4", "g4"))
                    |console.log("slice 0-2:", cleaned.slice(0, 2))
                    |console.log("repeat:", "kick ".repeat(4))
                    |console.log("concat:", "hello".concat(" world"))
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 5. Arrays
    // ==========================================
    DocSection(
        title = "Arrays",
        description = "Arrays are ordered collections with Kotlin-style methods. Build note sequences, filter data, and reshape lists.",
        examples = listOf(
            DocExample(
                title = "Creating and accessing",
                code = """
                    |import * from "stdlib"
                    |
                    |const notes = ["C4", "E4", "G4", "B4"]
                    |console.log("size:", notes.size())
                    |console.log("first:", notes.first())
                    |console.log("last:", notes.last())
                    |console.log("[2]:", notes[2])
                    |
                    |notes[2] = "G#4"
                    |console.log("after update:", notes)
                """.trimMargin(),
            ),
            DocExample(
                title = "Array methods — query",
                code = """
                    |import * from "stdlib"
                    |
                    |const scale = ["C", "D", "E", "F", "G", "A", "B"]
                    |console.log("contains E:", scale.contains("E"))
                    |console.log("indexOf G:", scale.indexOf("G"))
                    |console.log("isEmpty:", scale.isEmpty())
                    |console.log("isNotEmpty:", scale.isNotEmpty())
                    |
                    |const empty = []
                    |console.log("[] isEmpty:", empty.isEmpty())
                """.trimMargin(),
            ),
            DocExample(
                title = "Array methods — transform",
                code = """
                    |import * from "stdlib"
                    |
                    |const pattern = ["kick", "hat", "snare", "hat"]
                    |console.log("reversed:", pattern.reversed())
                    |console.log("first 2:", pattern.take(2))
                    |console.log("skip 2:", pattern.drop(2))
                    |console.log("middle:", pattern.subList(1, 3))
                    |console.log("joined:", pattern.joinToString(" > "))
                """.trimMargin(),
            ),
            DocExample(
                title = "Array methods — mutate",
                code = """
                    |import * from "stdlib"
                    |
                    |const seq = ["kick"]
                    |seq.add("hat")
                    |seq.add("snare")
                    |seq.add("hat")
                    |console.log("built:", seq)
                    |
                    |seq.removeAt(1)
                    |console.log("after removeAt(1):", seq)
                    |seq.removeFirst()
                    |console.log("after removeFirst:", seq)
                    |seq.removeLast()
                    |console.log("after removeLast:", seq)
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 6. Objects
    // ==========================================
    DocSection(
        title = "Objects",
        description = "Objects group named values with dot and bracket access. Use Object.keys(), Object.values(), and Object.entries() to inspect them.",
        examples = listOf(
            DocExample(
                title = "Object basics",
                code = """
                    |import * from "stdlib"
                    |
                    |const track = { name: "Lead", note: "C4", velocity: 100 }
                    |console.log(track.name, "plays", track.note)
                    |
                    |// Bracket access with a variable
                    |const field = "velocity"
                    |console.log("velocity:", track[field])
                """.trimMargin(),
            ),
            DocExample(
                title = "Object utility methods",
                code = """
                    |import * from "stdlib"
                    |
                    |const mixer = { kick: 80, snare: 70, hat: 50, bass: 90 }
                    |console.log("channels:", Object.keys(mixer))
                    |console.log("levels:", Object.values(mixer))
                    |
                    |const entries = Object.entries(mixer)
                    |for (let i = 0; i < entries.size(); i++) {
                    |  const pair = entries[i]
                    |  console.log(`  ${"$"}{pair[0]}: ${"$"}{pair[1]}`)
                    |}
                """.trimMargin(),
            ),
            DocExample(
                title = "Nested objects and the in operator",
                code = """
                    |import * from "stdlib"
                    |
                    |const song = {
                    |  title: "Groove",
                    |  verse: { bars: 8, bpm: 120 },
                    |  chorus: { bars: 4, bpm: 130 }
                    |}
                    |
                    |console.log("has verse?", "verse" in song)
                    |console.log("has bridge?", "bridge" in song)
                    |console.log("verse bars:", song.verse.bars)
                    |console.log("chorus bpm:", song.chorus.bpm)
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 7. Control Flow
    // ==========================================
    DocSection(
        title = "Control Flow",
        description = "Control flow lets your code make decisions and repeat actions. In KlangScript, if/else is an expression that returns a value.",
        examples = listOf(
            DocExample(
                title = "if/else as expression",
                code = """
                    |import * from "stdlib"
                    |
                    |const velocity = 95
                    |const dynamic = if (velocity >= 100) {
                    |  "fortissimo"
                    |} else if (velocity >= 80) {
                    |  "forte"
                    |} else if (velocity >= 50) {
                    |  "mezzo"
                    |} else {
                    |  "piano"
                    |}
                    |
                    |console.log(`Velocity ${"$"}{velocity} = ${"$"}{dynamic}`)
                """.trimMargin(),
            ),
            DocExample(
                title = "for and while loops",
                code = """
                    |import * from "stdlib"
                    |
                    |// Build a pattern with a for loop
                    |const steps = []
                    |for (let i = 0; i < 8; i++) {
                    |  const hit = if (i % 2 == 0) { "KICK" } else { "hat" }
                    |  steps.add(hit)
                    |}
                    |console.log("pattern:", steps.joinToString(" "))
                    |
                    |// Double tempo with while
                    |let bpm = 60
                    |while (bpm < 200) {
                    |  bpm *= 2
                    |}
                    |console.log("doubled past 200:", bpm)
                """.trimMargin(),
            ),
            DocExample(
                title = "do-while",
                code = """
                    |import * from "stdlib"
                    |
                    |// do-while always executes at least once
                    |let attempts = 0
                    |let value = 0
                    |do {
                    |  value += 7
                    |  attempts++
                    |} while (value % 3 != 0)
                    |
                    |console.log(`Found ${"$"}{value} after ${"$"}{attempts} attempts`)
                """.trimMargin(),
            ),
            DocExample(
                title = "break and continue",
                code = """
                    |import * from "stdlib"
                    |
                    |// continue: skip even steps
                    |const oddSteps = []
                    |for (let i = 0; i < 10; i++) {
                    |  if (i % 2 == 0) { continue }
                    |  oddSteps.add(i)
                    |}
                    |console.log("odd steps:", oddSteps)
                    |
                    |// break: find first match
                    |const notes = ["C4", "D4", "E4", "F#4", "G4"]
                    |let sharp = "none"
                    |for (let i = 0; i < notes.size(); i++) {
                    |  if (notes[i].indexOf("#") >= 0) {
                    |    sharp = notes[i]
                    |    break
                    |  }
                    |}
                    |console.log("first sharp:", sharp)
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 8. Functions & Closures
    // ==========================================
    DocSection(
        title = "Functions & Closures",
        description = "Arrow functions are the only function syntax in KlangScript. They support closures, currying, and higher-order patterns.",
        examples = listOf(
            DocExample(
                title = "Arrow functions — expression vs block",
                code = """
                    |import * from "stdlib"
                    |
                    |// Expression body (implicit return)
                    |const double = x => x * 2
                    |const add = (a, b) => a + b
                    |
                    |// Block body (explicit return)
                    |const describe = (note, oct) => {
                    |  const name = `${"$"}{note}${"$"}{oct}`
                    |  return `Note: ${"$"}{name}`
                    |}
                    |
                    |console.log(double(64))
                    |console.log(add(3, 4))
                    |console.log(describe("C", 4))
                """.trimMargin(),
            ),
            DocExample(
                title = "Closures",
                code = """
                    |import * from "stdlib"
                    |
                    |const makeCounter = () => {
                    |  let count = 0
                    |  return () => {
                    |    count += 1
                    |    return count
                    |  }
                    |}
                    |
                    |const beat = makeCounter()
                    |console.log("beat:", beat(), beat(), beat(), beat())
                """.trimMargin(),
            ),
            DocExample(
                title = "Higher-order functions and currying",
                code = """
                    |import * from "stdlib"
                    |
                    |// Higher-order: pass a function as argument
                    |const apply = (fn, value) => fn(value)
                    |const octaveUp = n => n + 12
                    |console.log("C4 + octave:", apply(octaveUp, 60))
                    |
                    |// Currying: function returns function
                    |const transpose = semitones => note => note + semitones
                    |const upFifth = transpose(7)
                    |console.log("C4 up a fifth:", upFifth(60))
                    |console.log("E4 up a fifth:", upFifth(64))
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 9. Imports & the Standard Library
    // ==========================================
    DocSection(
        title = "Imports & the Standard Library",
        description = "The standard library provides Math, console, and more. Use import/export to organize code into modules.",
        examples = listOf(
            DocExample(
                title = "Wildcard import and Math",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("sqrt(144):", Math.sqrt(144))
                    |console.log("abs(-7):", Math.abs(-7))
                    |console.log("floor(3.9):", Math.floor(3.9))
                    |console.log("ceil(3.1):", Math.ceil(3.1))
                    |console.log("round(3.5):", Math.round(3.5))
                    |console.log("pow(2, 10):", Math.pow(2, 10))
                """.trimMargin(),
            ),
            DocExample(
                title = "Trigonometry",
                code = """
                    |import * from "stdlib"
                    |
                    |// Generate a sine wave sample
                    |const steps = 8
                    |for (let i = 0; i < steps; i++) {
                    |  const angle = i * 2 * 3.14159 / steps
                    |  const value = Math.round(Math.sin(angle) * 100) / 100
                    |  console.log(`step ${"$"}{i}: sin = ${"$"}{value}`)
                    |}
                    |
                    |// Clamp a value between min and max
                    |const raw = 150
                    |const clamped = Math.min(Math.max(raw, 0), 127)
                    |console.log("clamped:", clamped)
                """.trimMargin(),
            ),
            DocExample(
                title = "Selective and aliased imports",
                code = """
                    |import * from "stdlib"
                    |
                    |// Selective import from a custom module
                    |// (Modules use export to control their public API)
                    |
                    |// Here's how export/import works with custom libraries:
                    |// Library "music-utils" would contain:
                    |//   let midiToFreq = (note) => 440 * Math.pow(2, (note - 69) / 12)
                    |//   let freqToMidi = (freq) => 69 + 12 * (Math.log(freq / 440) / Math.log(2))
                    |//   export { midiToFreq, freqToMidi }
                    |//
                    |// Then: import { midiToFreq as m2f } from "music-utils"
                    |
                    |// For now, let's use Math from stdlib:
                    |const midiToFreq = (note) => Math.round(440 * Math.pow(2, (note - 69) / 12))
                    |console.log("C4 (60):", midiToFreq(60), "Hz")
                    |console.log("A4 (69):", midiToFreq(69), "Hz")
                    |console.log("C5 (72):", midiToFreq(72), "Hz")
                """.trimMargin(),
            ),
        ),
    ),

    // ==========================================
    // 10. Method Chaining & Putting It All Together
    // ==========================================
    DocSection(
        title = "Method Chaining & Putting It All Together",
        description = "Chain methods for fluent, expressive code. Combine everything you've learned into a complete program.",
        examples = listOf(
            DocExample(
                title = "String chaining",
                code = """
                    |import * from "stdlib"
                    |
                    |const raw = "  Kick, Hat, Snare, Hat  "
                    |const instruments = raw.trim().toLowerCase().replace(",", " >")
                    |console.log("chain:", instruments)
                    |
                    |const notes = raw.trim().split(", ")
                    |console.log("parsed:", notes)
                    |console.log("count:", notes.size(), "instruments")
                """.trimMargin(),
            ),
            DocExample(
                title = "Array + string chaining",
                code = """
                    |import * from "stdlib"
                    |
                    |const melody = ["c4", "e4", "g4", "e4", "c4"]
                    |
                    |// Reverse and join
                    |const retrograde = melody.reversed().joinToString(" ")
                    |console.log("retrograde:", retrograde)
                    |
                    |// Take a slice and format
                    |const motif = melody.take(3).joinToString(" > ")
                    |console.log("motif:", motif)
                    |
                    |// Build display string
                    |const display = `Melody (${"$"}{melody.size()} notes): ${"$"}{melody.joinToString(", ")}`
                    |console.log(display)
                """.trimMargin(),
            ),
            DocExample(
                title = "Beat Machine — capstone",
                code = """
                    |import * from "stdlib"
                    |
                    |// === Beat Machine ===
                    |const config = { bpm: 128, bars: 2, stepsPerBar: 8 }
                    |const totalSteps = config.bars * config.stepsPerBar
                    |const beatLen = Math.round(60000 / config.bpm)
                    |
                    |console.log(`Beat Machine: ${"$"}{config.bpm} BPM, ${"$"}{config.bars} bars`)
                    |console.log(`Step duration: ${"$"}{beatLen}ms`)
                    |
                    |// Build patterns using functions
                    |const every = (n) => (step) => step % n == 0
                    |const onBeat = every(config.stepsPerBar / 4)
                    |const offBeat = (step) => step % 4 == 2
                    |
                    |// Generate the sequence
                    |const output = []
                    |for (let i = 0; i < totalSteps; i++) {
                    |  let hit = "."
                    |  if (onBeat(i)) { hit = "KICK" }
                    |  if (offBeat(i)) { hit = "hat" }
                    |  if (i % config.stepsPerBar == 4) { hit = "SNARE" }
                    |  output.add(hit)
                    |}
                    |
                    |// Display bar by bar
                    |for (let bar = 0; bar < config.bars; bar++) {
                    |  const start = bar * config.stepsPerBar
                    |  const barSteps = output.subList(start, start + config.stepsPerBar)
                    |  console.log(`Bar ${"$"}{bar + 1}: ${"$"}{barSteps.joinToString(" | ")}`)
                    |}
                    |
                    |console.log(`Total steps: ${"$"}{output.size()}`)
                """.trimMargin(),
            ),
        ),
    ),
)
