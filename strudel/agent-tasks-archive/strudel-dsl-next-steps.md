Here is the updated implementation plan, enriched with details about the original JavaScript implementation logic to
ensure maximum compatibility.

### **Task: Implement Advanced Pattern Operations Batch**

**Objective:** Implement `stepcat`, `within`, `chunk`, `echo`, and `echoWith` in `lang_structural.kt` and add
compatibility tests.

**Target File:** `strudel/src/commonMain/kotlin/lang/lang_structural.kt`

---

#### **1. `stepcat` (Weighted Concatenation)**

Concatenates patterns where each segment has a defined duration relative to the others. It scales the result to fit
exactly **one cycle**.

* **Signature:** `stepcat(vararg pairs: Pair<Number, Pattern>)` (and overload for just `Patterns` assuming weight 1).
* **Aliases:** `timeCat`, `timecat`, `s_cat`.
* **JS Implementation Detail:** In JS, `timeCat` (or `stepcat`) takes a list of `[duration, pattern]` pairs (or just
  patterns). It effectively constructs a sequence where each pattern takes up the specified duration, and then speeds up
  the *entire* sequence so that the total sum of durations fits into exactly 1 cycle.
    * *Example:* `timeCat([1, "a"], [3, "b"])` creates "a" (duration 1) then "b" (duration 3). Total duration = 4. It
      then applies `.fast(4)` (or `.setCycleDuration`) to make it fit in 1 cycle. The result is "a" taking the first 25%
      and "b" taking the remaining 75%.
* **Kotlin Implementation Logic:**
    1. Parse arguments into `(duration, pattern)` pairs (defaulting duration to 1 if missing).
    2. Use the existing `arrange` logic (which creates a sequence of defined lengths *without* squashing).
    3. Calculate `totalDuration = sum(durations)`.
    4. Apply `.fast(totalDuration)` to the result of `arrange` to compress it into 1 cycle.

#### **2. `within` (Conditional Time Selection)**

Applies a function to events occurring within a specific time window of the cycle, leaving other events untouched.

* **Signature:** `within(start: Double, end: Double, transform: (Pattern) -> Pattern)`
* **JS Implementation Detail:** The JS implementation conceptually splits the pattern into two parts:
    1. The part *inside* the window (`start` to `end`).
    2. The part *outside* the window.
       It applies the transformation function *only* to the inside part, then stacks it back with the unchanged outside
       part. This is often implemented using masking logic.
* **Kotlin Implementation Logic:**
    1. Create a "window" pattern representing the time range: `val window = s("x").compress(start, end)`.
    2. Isolate inner events using `maskAll`: `val inner = this.maskAll(window)`.
        * *Note:* Use `maskAll` to preserve original event structures that might just partially overlap.
    3. Isolate outer events using `bypass`: `val outer = this.bypass(window)`.
        * *Note:* `bypass` returns silence if the condition matches, effectively keeping "everything else".
    4. Apply the transform to the inner part: `val transformed = transform(inner)`.
    5. Combine them: `StackPattern(listOf(outer, transformed))`.

#### **3. `chunk` (Time-based Segmentation)**

Divides the pattern into `n` parts and applies a function to one part per cycle (cycling through parts).

* **Signature:** `chunk(n: Int, transform: (Pattern) -> Pattern)`
* **Aliases:** `slowchunk`, `slowChunk`.
* **JS Implementation Detail:** `chunk(4, func)` is a higher-order pattern that behaves like a `slowcat`.
    * Cycle 0: Applies `func` to the 1st quarter of the pattern (0.0-0.25).
    * Cycle 1: Applies `func` to the 2nd quarter (0.25-0.5).
    * ...
    * Cycle 3: Applies `func` to the 4th quarter.
      It is effectively: `slowcat( (0..n).map { i -> pat.within(i/n, (i+1)/n, transform) } )`.
* **Kotlin Implementation Logic:**
    1. Generate a list of `n` patterns.
    2. For each index `i` from `0` to `n-1`:
        * Calculate normalized start/end: `start = i / n`, `end = (i + 1) / n`.
        * Create the variant: `this.within(start, end, transform)`.
    3. Pass this list to `applyCat` (which implements `slowcat` logic).

    * *Dependency:* Requires `within` to be implemented first.

#### **4. `echo` / `stut` (Echo Effect)**

Superimposes delayed and decayed versions of the pattern.

* **Signature:** `echo(times: Int, delay: Double, decay: Double)`
* **Aliases:** `stut`.
* **JS Implementation Detail:** `stut` creates `times` copies of the pattern.
    * Copy `i` (where `i` goes from 0 to `times-1`) is shifted later by `delay * i`.
    * The gain (velocity) of copy `i` is multiplied by `decay^i`.
    * All copies are stacked together.
* **Kotlin Implementation Logic:**
    1. Create `times` layers.
    2. For each `i` in `0 until times`:
        * Take `this`.
        * Apply `.late(delay * i)`.
        * Apply `.gain(decay.pow(i))` (or multiply existing gain).
    3. Return `StackPattern` of these layers.

#### **5. `echoWith` / `stutWith` (Custom Echo)**

Superimposes versions of the pattern modified by a recursive function application.

* **Signature:** `echoWith(times: Int, delay: Double, transform: (Pattern) -> Pattern)`
* **Aliases:** `stutWith`, `stutwith`, `echowith`.
* **JS Implementation Detail:** `stutWith` is the generalized version of `stut`. Instead of just decaying gain, it
  applies an arbitrary function `transform`.
    * Crucially, the function is applied **cumulatively** (recursively).
    * Layer 0: `original`
    * Layer 1: `transform(original).late(delay)`
    * Layer 2: `transform(transform(original)).late(delay * 2)`
* **Kotlin Implementation Logic:**
    1. Initialize a list of patterns with `this` as the first element.
    2. Iterate `times - 1` times.
    3. In each step, take the *previous* modified pattern (before delay was applied in the previous concept, but simpler
       to just re-apply transform to base accumulator).
    4. Actually, simpler recursive approach:
        * `var current = this`
        * `val layers = mutableListOf(current)`
        * `repeat(times - 1) { current = transform(current); layers.add(current.late(delay * (it + 1))) }` -> **Wait**,
          careful with order.
        * Correct Logic:
            * Layer 0: `p`
            * Layer 1: `transform(p).late(delay)`
            * Layer 2: `transform(transform(p)).late(delay * 2)`
    5. Return `StackPattern(layers)`.

---

### **Testing Plan**

Add the following cases to `strudel/src/jvmTest/kotlin/compat/JsCompatTestData.kt`.

```kotlin
// ... existing code ...
Example(
    name = "stepcat (timeCat) basic",
    code = """
                timeCat([1, "c"], [2, "e"], [1, "g"])
            """.trimIndent()
),
Example(
    name = "stepcat with aliases",
    code = """
                stack(
                    stepcat([1, "c"], [1, "d"]),
                    s_cat([1, "e"], [3, "f"])
                )
            """.trimIndent()
),
Example(
    name = "within basic",
    code = """
                n("0 1 2 3").within(0.5, 1, x => x.rev())
            """.trimIndent()
),
Example(
    name = "within with overlap",
    code = """
                // "0 1 2 3" -> "0" is 0-0.25, "1" is 0.25-0.5. 
                // Window 0.2-0.3 cuts into "0" and "1".
                n("0 1 2 3").within(0.2, 0.3, x => x.add(12))
            """.trimIndent()
),
Example(
    name = "chunk basic",
    code = """
                n("0 1 2 3").chunk(4, x => x.add(12))
            """.trimIndent()
),
Example(
    name = "echo / stut",
    code = """
                n("0").stut(4, 0.5, 0.5)
            """.trimIndent()
),
Example(
    name = "echoWith / stutWith",
    code = """
                n("0").stutWith(4, 0.125, x => x.add(2))
            """.trimIndent()
),
// ... existing code ...
```
