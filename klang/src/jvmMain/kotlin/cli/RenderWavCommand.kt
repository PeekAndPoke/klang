package io.peekandpoke.klang.audio_engine.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * CLI command: render sprudel code to a WAV file at full CPU speed.
 *
 * Takes a [compilePattern] function to decouple from the sprudel module.
 */
class RenderWavCommand(
    private val compilePattern: (code: String) -> CompileResult?,
    private val samples: Samples? = null,
) : CliktCommand(name = "klang:record:wav") {

    data class CompileResult(
        val pattern: KlangPattern,
        val customIgnitors: List<Pair<String, IgnitorDsl>> = emptyList(),
    )

    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "Render sprudel code to a WAV file at full CPU speed (offline, no real-time playback)"

    private val code by option("--code", "-c", help = "Sprudel code to render")
    private val file by option("--file", "-f", help = "Path to a .sprudel file")
    private val output by option("--output", "-o", help = "Output WAV file path").default("output.wav")
    private val cycles by option("--cycles", help = "Number of cycles to render").int().default(4)
    private val rpm by option("--rpm", help = "Tempo in RPM").double().default(30.0)
    private val sampleRate by option("--sample-rate", help = "Sample rate in Hz").int().default(48_000)
    private val blockSize by option("--block-size", help = "Block size in frames").int().default(512)
    private val tail by option("--tail", help = "Extra seconds for reverb/delay tails").double().default(2.0)

    override fun run() {
        // Resolve source code
        val sprudelCode = when {
            code != null -> code!!
            file != null -> {
                val f = File(file!!)
                if (!f.exists()) {
                    echo("Error: File not found: $file", err = true)
                    return
                }
                f.readText()
            }

            else -> {
                echo("Error: Provide --code or --file", err = true)
                return
            }
        }

        // Compile
        echo("Compiling pattern...")
        val compileResult = compilePattern(sprudelCode)
        if (compileResult == null) {
            echo("Error: Failed to compile pattern", err = true)
            return
        }

        val cps = rpm / 60.0
        echo("Rendering $cycles cycles at $rpm RPM (cps=${"%.3f".format(cps)}) to $output")
        echo("  sample rate: $sampleRate, block size: $blockSize, tail: ${tail}s")
        if (compileResult.customIgnitors.isNotEmpty()) {
            echo("  custom sounds: ${compileResult.customIgnitors.joinToString(", ") { it.first }}")
        }

        // Render to WAV
        val wav = WavFileWriter(output, sampleRate)
        wav.open()

        val result = try {
            val renderer = KlangOfflineRenderer(sampleRate = sampleRate, blockFrames = blockSize)

            runBlocking {
                renderer.render(
                    pattern = compileResult.pattern,
                    cycles = cycles,
                    cyclesPerSecond = cps,
                    tailSec = tail,
                    customIgnitors = compileResult.customIgnitors,
                    samples = samples,
                    onBlock = { samples, count -> wav.writeBlock(samples, count) },
                )
            }
        } finally {
            wav.close()
        }

        val durationStr = "%.2f".format(result.durationSec)
        val fileSize = File(output).length()
        val sizeStr = "%.1f".format(fileSize / 1024.0)
        val speedStr = "%.1f".format(result.durationSec / (result.elapsedMs / 1000.0))

        echo("Done! ${durationStr}s of audio, ${sizeStr} KB, rendered in ${"%.0f".format(result.elapsedMs)}ms (${speedStr}x real-time)")
    }
}
