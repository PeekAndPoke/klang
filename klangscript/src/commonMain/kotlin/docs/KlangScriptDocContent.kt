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

    // 1. Variables
    DocSection(
        title = "Variables",
        description = "Use 'let' for mutable variables and 'const' for constants. Supported types: numbers, strings, booleans, and null.",
        examples = listOf(
            DocExample(
                title = "let and const",
                code = """
                    |import * from "stdlib"
                    |
                    |let x = 10
                    |const name = "KlangScript"
                    |let flag = true
                    |let empty = null
                    |
                    |x = x + 5
                    |console.log(name, "x =", x, "flag =", flag, "empty =", empty)
                """.trimMargin(),
            ),
            DocExample(
                title = "Number types",
                code = """
                    |import * from "stdlib"
                    |
                    |const pi = 3.14159
                    |const negative = -42
                    |const result = pi * negative
                    |console.log("pi =", pi, "result =", result)
                """.trimMargin(),
            ),
        ),
    ),

    // 2. Operators
    DocSection(
        title = "Operators",
        description = "Arithmetic, comparison, logical, and ternary operators work like JavaScript.",
        examples = listOf(
            DocExample(
                title = "Arithmetic and assignment",
                code = """
                    |import * from "stdlib"
                    |
                    |let a = 10
                    |console.log("a + 3 =", a + 3)
                    |console.log("a * 2 =", a * 2)
                    |console.log("a % 3 =", a % 3)
                    |a += 5
                    |console.log("after a += 5:", a)
                """.trimMargin(),
            ),
            DocExample(
                title = "Comparison and ternary",
                code = """
                    |import * from "stdlib"
                    |
                    |const x = 7
                    |console.log("x > 5:", x > 5)
                    |console.log("x == 7:", x == 7)
                    |console.log("x != 3:", x != 3)
                    |const label = x > 5 ? "big" : "small"
                    |console.log("label:", label)
                """.trimMargin(),
            ),
            DocExample(
                title = "Logical operators",
                code = """
                    |import * from "stdlib"
                    |
                    |const a = true
                    |const b = false
                    |console.log("a && b:", a && b)
                    |console.log("a || b:", a || b)
                    |console.log("!b:", !b)
                """.trimMargin(),
            ),
        ),
    ),

    // 3. Control Flow
    DocSection(
        title = "Control Flow",
        description = "if/else, while, do-while, and for loops with break and continue.",
        examples = listOf(
            DocExample(
                title = "if / else",
                code = """
                    |import * from "stdlib"
                    |
                    |const score = 85
                    |if (score >= 90) {
                    |  console.log("A")
                    |} else if (score >= 80) {
                    |  console.log("B")
                    |} else {
                    |  console.log("C")
                    |}
                """.trimMargin(),
            ),
            DocExample(
                title = "for and while loops",
                code = """
                    |import * from "stdlib"
                    |
                    |let sum = 0
                    |for (let i = 1; i <= 5; i += 1) {
                    |  sum += i
                    |}
                    |console.log("sum 1..5 =", sum)
                    |
                    |let n = 1
                    |while (n < 32) {
                    |  n = n * 2
                    |}
                    |console.log("first power of 2 >= 32:", n)
                """.trimMargin(),
            ),
            DocExample(
                title = "break and continue",
                code = """
                    |import * from "stdlib"
                    |
                    |for (let i = 0; i < 10; i += 1) {
                    |  if (i % 2 == 0) { continue }
                    |  if (i > 7) { break }
                    |  console.log("odd:", i)
                    |}
                """.trimMargin(),
            ),
        ),
    ),

    // 4. Functions
    DocSection(
        title = "Functions",
        description = "Arrow functions, closures, and higher-order functions.",
        examples = listOf(
            DocExample(
                title = "Arrow functions",
                code = """
                    |import * from "stdlib"
                    |
                    |const add = (a, b) => a + b
                    |const greet = (name) => {
                    |  return "Hello, " + name + "!"
                    |}
                    |console.log(add(3, 4))
                    |console.log(greet("World"))
                """.trimMargin(),
            ),
            DocExample(
                title = "Closures and higher-order functions",
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
                    |const counter = makeCounter()
                    |console.log(counter(), counter(), counter())
                """.trimMargin(),
            ),
        ),
    ),

    // 5. Arrays
    DocSection(
        title = "Arrays",
        description = "Array literals, index access, and Kotlin-style methods like .size(), .first(), .reversed().",
        examples = listOf(
            DocExample(
                title = "Basics",
                code = """
                    |import * from "stdlib"
                    |
                    |const nums = [10, 20, 30, 40]
                    |console.log("size:", nums.size())
                    |console.log("first:", nums.first())
                    |console.log("last:", nums.last())
                    |console.log("[1]:", nums[1])
                """.trimMargin(),
            ),
            DocExample(
                title = "Mutation and methods",
                code = """
                    |import * from "stdlib"
                    |
                    |const arr = [1, 2, 3]
                    |arr.add(4)
                    |console.log("after add:", arr)
                    |console.log("reversed:", arr.reversed())
                    |console.log("joined:", arr.joinToString(" - "))
                    |console.log("contains 2:", arr.contains(2))
                """.trimMargin(),
            ),
        ),
    ),

    // 6. Objects
    DocSection(
        title = "Objects",
        description = "Object literals with dot and bracket access. Use Object.keys(), Object.values(), Object.entries().",
        examples = listOf(
            DocExample(
                title = "Object literals",
                code = """
                    |import * from "stdlib"
                    |
                    |const person = { name: "Alice", age: 30 }
                    |console.log(person.name, "is", person.age)
                    |
                    |const key = "name"
                    |console.log("bracket access:", person[key])
                """.trimMargin(),
            ),
            DocExample(
                title = "Object utility methods",
                code = """
                    |import * from "stdlib"
                    |
                    |const config = { tempo: 120, key: "C", mode: "major" }
                    |console.log("keys:", Object.keys(config))
                    |console.log("values:", Object.values(config))
                    |console.log("entries:", Object.entries(config))
                """.trimMargin(),
            ),
        ),
    ),

    // 7. Strings
    DocSection(
        title = "Strings",
        description = "Template literals with \${...} interpolation and string methods.",
        examples = listOf(
            DocExample(
                title = "Template literals and methods",
                code = """
                    |import * from "stdlib"
                    |
                    |const name = "klangscript"
                    |console.log(`Hello ${"$"}{name.toUpperCase()}!`)
                    |console.log("length:", name.length())
                    |console.log("starts with 'klang':", name.startsWith("klang"))
                    |console.log("split:", name.split("s"))
                """.trimMargin(),
            ),
            DocExample(
                title = "String manipulation",
                code = """
                    |import * from "stdlib"
                    |
                    |const s = "  Hello, World!  "
                    |console.log("trimmed:", s.trim())
                    |console.log("replaced:", s.trim().replace("World", "KlangScript"))
                    |console.log("upper:", s.trim().toUpperCase())
                """.trimMargin(),
            ),
        ),
    ),

    // 8. Imports
    DocSection(
        title = "Imports",
        description = "Use 'import * from \"stdlib\"' to load the standard library, or selectively import symbols.",
        examples = listOf(
            DocExample(
                title = "Wildcard import",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("sqrt(144) =", Math.sqrt(144))
                    |console.log("max(3, 7) =", Math.max(3, 7))
                    |console.log("sin(0) =", Math.sin(0))
                """.trimMargin(),
            ),
            DocExample(
                title = "Using stdlib",
                code = """
                    |import * from "stdlib"
                    |
                    |console.log("PI approx:", Math.round(Math.pow(Math.sqrt(10), 2)))
                    |console.log("abs(-5):", Math.abs(-5))
                """.trimMargin(),
            ),
        ),
    ),
)
