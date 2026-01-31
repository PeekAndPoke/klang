package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Helper function to create test events with the new part/whole structure.
 *
 * For most tests, creates events where part == whole (normal events).
 * For continuous patterns, pass whole = null.
 */
fun testEvent(
    begin: Rational,
    end: Rational,
    data: StrudelVoiceData,
    whole: TimeSpan? = TimeSpan(begin, end), // Default: part == whole
    sourceLocations: SourceLocationChain? = null,
): StrudelPatternEvent {
    return StrudelPatternEvent(
        part = TimeSpan(begin, end),
        whole = whole,
        data = data,
        sourceLocations = sourceLocations
    )
}

/**
 * Helper function to create clipped test events (part != whole).
 */
fun testClippedEvent(
    partBegin: Rational,
    partEnd: Rational,
    wholeBegin: Rational,
    wholeEnd: Rational,
    data: StrudelVoiceData,
    sourceLocations: SourceLocationChain? = null,
): StrudelPatternEvent {
    return StrudelPatternEvent(
        part = TimeSpan(partBegin, partEnd),
        whole = TimeSpan(wholeBegin, wholeEnd),
        data = data,
        sourceLocations = sourceLocations
    )
}

/**
 * Helper function to create continuous test events (whole = null).
 */
fun testContinuousEvent(
    begin: Rational,
    end: Rational,
    data: StrudelVoiceData,
    sourceLocations: SourceLocationChain? = null,
): StrudelPatternEvent {
    return StrudelPatternEvent(
        part = TimeSpan(begin, end),
        whole = null,
        data = data,
        sourceLocations = sourceLocations
    )
}
