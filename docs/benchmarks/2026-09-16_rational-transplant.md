# Musical time transplant: fixed 32.32 in a Long, Rational, CycleTime

> One-off transplant run 2026-09-16 for the blog post `docs/blog/2026-05-29-the-triplet-that-never-drifted/`.
> No timing of the Rational generations was ever recorded, so the three representations of musical time were
> copied verbatim into `audio_benchmark` for one run on each platform and deleted again (scaffolding rule):
>
> - `FixedRational`: `strudel/src/commonMain/kotlin/math/Rational.kt` at `54beec47` (2026-01-13), the 32.32
>   fixed point in a `Long` value class of 2026-01-08 (`302cf72d`), as it stood after the same-day Rational2 revert.
> - `RationalLong` (JVM) and `RationalBigInt` (JS): `common/src/jvmMain/kotlin/math/Rational.kt` and
>   `common/src/jsMain/kotlin/math/Rational.kt` at `e57b2cf0` (`b7b31bbf^`, the last commit before the deletion of
>   2026-06-11), the numerator/denominator generation of 2026-02-05 (`7ed31a91`) split into platform actuals with
>   the BigInt hot path on 2026-03-16 (`09c6e8d9`).
> - `CycleTime`: `common/src/commonMain/kotlin/math/CycleTime.kt` at HEAD (`a6eaa652`), unchanged in its arithmetic
>   since 2026-05-30 (`21c6711a`, the day after `13e3be4b` introduced it with a 2^13 grid).
>
> The copies had only `actual`, the kotlinx serialization annotation and the class name changed. Each operation
> runs behind a SAM interface (a Kotlin `(Int) -> Unit` lambda boxes its argument on the JVM, and fifteen loops
> inlined into one function exceed HotSpot's huge-method limit and stay interpreted); the "empty op" row is that
> harness floor, so the cost of an operation is its row minus the floor. The accumulators of the `plus` walk are
> fields of a holder class so that a value class stays unboxed. Run with `./gradlew :audio_benchmark:jvmRun` and
> `:jsNodeProductionRun` with a temporary `main` that ran only this benchmark.

## JVM

```
=== Musical time transplant: fixed 32.32 Long (Jan 8) vs Rational Long (Mar 16) vs CycleTime (May 29) ===
Platform: JVM 17.0.17 (Amazon.com Inc.) / Linux / AMD Ryzen 9 PRO 7940HS w/ Radeon 780M Graphics (16 cores)
Warmup 100000 ops; 300000 ops x 5 trials (median). ns per operation.

  op                         fixed32.32/Long   Rational/Long   CycleTime
  (empty op, the harness floor: 3.11 ns)
  plus (walk 1/4,1/3,1/8,1/2)           2.00             25.03        3.21
  compareTo                             2.38              4.53        3.68
  times 3                               3.59              6.20        3.20
  step map (t-a)/size+c                 3.69             23.21        3.40
  from Double                           3.74             14.16        3.22
  sink=1049999
```

## Node

```
=== Musical time transplant: fixed 32.32 Long (Jan 8) vs Rational BigInt (Mar 16) vs CycleTime (May 29) ===
Platform: JS / Node.js/24
Warmup 100000 ops; 300000 ops x 5 trials (median). ns per operation.

  op                         fixed32.32/Long   Rational/BigInt   CycleTime
  (empty op, the harness floor: 2.82 ns)
  plus (walk 1/4,1/3,1/8,1/2)          18.43             92.16        5.74
  compareTo                            45.17             13.97        5.93
  times 3                              34.10             62.44        5.00
  step map (t-a)/size+c                79.50            186.06        5.35
  from Double                          38.01            113.38        5.38
  sink=1049999
```

## The benchmark source (deleted with the transplant; JVM variant, the JS one differs only in the class name)

```kotlin
package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_benchmark.FixedRational.Companion.toRational
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.ultra.common.toFixed
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

// TRANSPLANT SCAFFOLDING for docs/blog/2026-05-29-the-triplet-that-never-drifted. Deleted after one run.
private var transplantSink: Int = 0

/** A SAM interface, not a Kotlin lambda: an Int parameter through `(Int) -> Unit` boxes on the JVM. */
private fun interface TimedOp {
    fun run(i: Int)
}

/** Not inline on purpose: fifteen inlined loops in one function exceed HotSpot's huge-method limit and stay interpreted. */
private fun medianNsPerOp(warmup: Int, iterations: Int, trials: Int, op: TimedOp): Double {
    for (i in 0 until warmup) { op.run(i) }
    val ns = DoubleArray(trials) {
        val mark = TimeSource.Monotonic.markNow()
        for (i in 0 until iterations) { op.run(i) }
        mark.elapsedNow().toDouble(DurationUnit.NANOSECONDS) / iterations
    }
    ns.sort()
    return ns[trials / 2]
}

/** Accumulators live in fields, so a value class stays unboxed and no captured-var Ref is allocated per step. */
private class Accs {
    var r = RationalLong.ZERO
    var f = FixedRational.ZERO
    var c = CycleTime.ZERO
}

/**
 * The five timing operations a sprudel query spends its time in, on three representations of musical time:
 * the 32.32 fixed point in a Long (2026-01-08), the numerator/denominator Rational (Long, 2026-03-16
 * to 2026-06-11) and CycleTime (2026-05-29, fixed point ticks in a Double). ns per operation, median of trials.
 */
fun runRationalTransplantBenchmark() {
    val warmup = 100_000
    val iterations = 300_000
    val trials = 5
    val floor = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> transplantSink += i and 1 })
    val doubles = doubleArrayOf(0.002, 0.3333333333333333, 1.75, 2.001, 0.125, 5.666666666666667, 0.2, 7.0)

    // --- Rational (Long) ---
    val rSteps = arrayOf(RationalLong.create(1L, 4L), RationalLong.create(1L, 3L), RationalLong.create(1L, 8L), RationalLong.create(1L, 2L))
    val rVals = Array(8) { RationalLong.create((it * 7 + 1).toLong(), (it % 3 + 2).toLong()) }
    val rThree = RationalLong(3)
    val rStart = RationalLong.create(1L, 4L)
    val rSize = RationalLong.create(1L, 3L)
    val rCycle = RationalLong(5)
    val acc = Accs()
    val rPlus = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> acc.r = acc.r + rSteps[i and 3] })
    val rCmp = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> transplantSink += rVals[i and 7].compareTo(rVals[(i + 3) and 7]) })
    val rTimes = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if ((rVals[i and 7] * rThree).isNaN) transplantSink++ })
    val rStep = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (((rVals[i and 7] - rStart) / rSize + rCycle).isNaN) transplantSink++ })
    val rOf = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (RationalLong(doubles[i and 7]).isNaN) transplantSink++ })
    transplantSink += acc.r.toInt()

    // --- FixedRational (32.32 in a Long) ---
    val fSteps = arrayOf(0.25.toRational(), (1.0 / 3.0).toRational(), 0.125.toRational(), 0.5.toRational())
    val fVals = Array(8) { ((it * 7 + 1).toDouble() / (it % 3 + 2)).toRational() }
    val fThree = FixedRational(3)
    val fStart = 0.25.toRational()
    val fSize = (1.0 / 3.0).toRational()
    val fCycle = FixedRational(5)
    val fPlus = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> acc.f = acc.f + fSteps[i and 3] })
    val fCmp = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> transplantSink += fVals[i and 7].compareTo(fVals[(i + 3) and 7]) })
    val fTimes = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if ((fVals[i and 7] * fThree).isNaN) transplantSink++ })
    val fStep = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (((fVals[i and 7] - fStart) / fSize + fCycle).isNaN) transplantSink++ })
    val fOf = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (FixedRational(doubles[i and 7]).isNaN) transplantSink++ })
    transplantSink += acc.f.toInt()

    // --- CycleTime ---
    val cSteps = arrayOf(CycleTime.ofSubdivision(1, 4), CycleTime.ofSubdivision(1, 3), CycleTime.ofSubdivision(1, 8), CycleTime.ofSubdivision(1, 2))
    val cVals = Array(8) { CycleTime.ofSubdivision(it * 7 + 1, it % 3 + 2) }
    val cStart = CycleTime.ofSubdivision(1, 4)
    val cSize = 1.0 / 3.0
    val cCycle = CycleTime.ofCycleIndex(5)
    val cPlus = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> acc.c = acc.c + cSteps[i and 3] })
    val cCmp = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> transplantSink += cVals[i and 7].compareTo(cVals[(i + 3) and 7]) })
    val cTimes = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if ((cVals[i and 7] * 3).ticks < 0.0) transplantSink++ })
    val cStep = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (((cVals[i and 7] - cStart).divBy(cSize) + cCycle).ticks < 0.0) transplantSink++ })
    val cOf = medianNsPerOp(warmup, iterations, trials, TimedOp { i -> if (CycleTime.ofCycles(doubles[i and 7]).ticks < 0.0) transplantSink++ })
    transplantSink += acc.c.cycleIndex()

    println("=== Musical time transplant: fixed 32.32 Long (Jan 8) vs Rational Long (Mar 16) vs CycleTime (May 29) ===")
    println("Platform: ${platformInfo()}")
    println("Warmup $warmup ops; $iterations ops x $trials trials (median). ns per operation.")
    println()
    println("  op                         fixed32.32/Long   Rational/Long   CycleTime")
    println("  (empty op, the harness floor: ${floor.toFixed(2)} ns)")
    fun row(name: String, f: Double, r: Double, c: Double) {
        println("  " + name.padEnd(27) + f.toFixed(2).padStart(15) + r.toFixed(2).padStart(18) + c.toFixed(2).padStart(12))
    }
    row("plus (walk 1/4,1/3,1/8,1/2)", fPlus, rPlus, cPlus)
    row("compareTo", fCmp, rCmp, cCmp)
    row("times 3", fTimes, rTimes, cTimes)
    row("step map (t-a)/size+c", fStep, rStep, cStep)
    row("from Double", fOf, rOf, cOf)
    println("  sink=$transplantSink")
    println()
}
```
