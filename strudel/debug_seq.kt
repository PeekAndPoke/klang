import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.initStrudelLang
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq

fun main() {
    initStrudelLang()

    // Test 1: seq() with multiple string patterns
    val pattern = seq("c d", "e f", "g a")

    // Query first cycle
    val cycle0 = pattern.queryArc(0.0, 1.0).filter { it.isOnset }
    println("Cycle 0 events: ${cycle0.size}")
    cycle0.forEach { event ->
        println("  Note: ${event.data.note}, Value: ${event.data.value}")
    }

    // Test 2: Pattern extension
    val pattern2 = note("c e").seq("g a")
    val cycle0_2 = pattern2.queryArc(0.0, 1.0).filter { it.isOnset }
    println("\nCycle 0 (pattern extension) events: ${cycle0_2.size}")
    cycle0_2.forEach { event ->
        println("  Note: ${event.data.note}, Value: ${event.data.value}")
    }
}
