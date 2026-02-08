Here is the integration plan. We need to thread the needle from the platform-specific `JsAudioBackend` up to the common
`KlangPlayer` so your frontend can access it.

### 1. The Strategy

We will introduce an abstraction for the visualization buffer so code in `commonMain` can talk about it without knowing
it is a JS `Float32Array`.

1. **`VisualizerBuffer`**: An `expect/actual` alias (`Float32Array` on JS, `FloatArray` on JVM).
2. **`AudioBackend.Visualizer`**: An interface exposed by the backend.
3. **`KlangPlayer.visualizer`**: A public property that exposes the active backend's visualizer.
4. **`JsAudioBackend`**: Inserts the `AnalyserNode` and implements the interface.

### 2. Code Changes

#### Step 1: Define the Buffer Abstraction

We need a common type for the data array to avoid copying. Add this to your common definitions (e.g., `index_common.kt`
or a new file).

```kotlin
// ... existing code ...
): KlangPlayer

/**
 * Platform-optimized buffer for visualization data.
 * JS: Float32Array (Zero-copy with Web Audio)
 * JVM: FloatArray
 */
expect class VisualizerBuffer

expect fun createVisualizerBuffer(size: Int): VisualizerBuffer
```

**Implementation for JS:**

```kotlin
// (Create this file if it doesn't exist, or add to existing js definitions)
import org.khronos.webgl.Float32Array

actual typealias VisualizerBuffer = Float32Array

actual fun createVisualizerBuffer(size: Int): VisualizerBuffer = Float32Array(size)
```

**Implementation for JVM:**

```kotlin
// (Create or add to existing)
actual typealias VisualizerBuffer = FloatArray

actual fun createVisualizerBuffer(size: Int): VisualizerBuffer = FloatArray(size)
```

#### Step 2: Update the `AudioBackend` Interface

Expose the capability to visualize.

```kotlin
// ... existing code ...
val blockSize: Int,
)

interface Visualizer {
    /**
     * Fills the [out] buffer with current time-domain data (waveform).
     * Values range from -1.0 to 1.0.
     */
    fun getWaveform(out: VisualizerBuffer)

    /**
     * Fills the [out] buffer with frequency data.
     * Values typically range between 0 and 255 (or normalized depending on platform).
     */
    fun getFft(out: VisualizerBuffer)

    val fftSize: Int
}

val visualizer: Visualizer? get() = null

suspend fun run(scope: CoroutineScope)
}
```

#### Step 3: Wire it into `JsAudioBackend`

This is the "Performant" part. We insert the `AnalyserNode` into the graph and pass calls through.

```kotlin
// ... existing code ...
class JsAudioBackend(
    private val config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    // Create AnalyserNode. 2048 is a good balance for 60fps visualization.
    private var analyser: AnalyserNode? = null

    override val visualizer = object : AudioBackend.Visualizer {
        override val fftSize: Int = 2048

        override fun getWaveform(out: VisualizerBuffer) {
            // Zero-copy fill on JS
            analyser?.getFloatTimeDomainData(out)
        }

        override fun getFft(out: VisualizerBuffer) {
            analyser?.getFloatFrequencyData(out)
        }
    }

    private val sampleUploadBuffer = KlangRingBuffer<KlangCommLink.Cmd.Sample.Chunk>(8192 * 4)

    override suspend fun run(scope: CoroutineScope) {
// ... existing code ...
        val ctx = AudioContext(contextOpts)

        // 1. Resume Audio Context (Browser policy usually requires this on interaction)
// ... existing code ...
        try {
            // 2. Load the compiled DSP module
            // This file "dsp.js" must contain the AudioWorkletProcessor registration
            ctx.audioWorklet.addModule("klang-worklet.js").await()

            // Setup Analyser
            analyser = ctx.createAnalyser().apply {
                fftSize = 2048
            }

            // 2. Create the Node (this instantiates the Processor in the Audio Thread)
            // We need to explicitly request 2 output channels, otherwise it defaults to 1 (Mono)
            val nodeOpts = jsObject<AudioWorkletNodeOptions> {
                outputChannelCount = arrayOf(2)
            }

            node = AudioWorkletNode(ctx, "klang-audio-processor", nodeOpts)

            // CONNECT: Worklet -> Analyser -> Destination
            node.connect(analyser!!)
            analyser!!.connect(ctx.destination)

            // 3. Send Command
            if (ctx.state == "suspended") {
// ... existing code ...
```

#### Step 4: Expose it via `KlangPlayer`

The player needs to hold a reference to the active backend so the UI can find it.

```kotlin
// ... existing code ...
// Shared communication link and backend
val commLink = KlangCommLink(capacity = 8192)
private var backendJob: Job? = null

// Hold reference to active backend/visualizer
private var _activeBackend: AudioBackend? = null

/**
 * Access the visualizer of the current audio backend.
 * Returns null if backend is not running or doesn't support visualization.
 */
val visualizer: AudioBackend.Visualizer?
get() = _activeBackend?.visualizer

// Thread-safe state management
private val lock = KlangLock()
// ... existing code ...
init {
    // Start the audio backend when the player is created
    backendJob = scope.launch {
        val backend = backendFactory(
            AudioBackend.Config(
                commLink = commLink.backend,
                sampleRate = options.sampleRate,
                blockSize = options.blockSize,
            ),
        )

        _activeBackend = backend

        launch(backendDispatcher.limitedParallelism(1)) {
            backend.run(this)
        }
    }
}
// ... existing code ...
fun shutdown() {
    lock.withLock {
        // Stop all active playbacks (use toList() to avoid concurrent modification)
        _activePlaybacks.toList().forEach { it.stop() }
        _activePlaybacks.clear()

        // Shutdown the backend
        backendJob?.cancel()
        backendJob = null
        _activeBackend = null
    }
}
}
```

### 3. Usage in Frontend (Vue/React/Plain JS)

Now your frontend can perform a highly optimized render loop:

```kotlin
// In your UI component logic
val player = klangPlayer(...)
val fftSize = 2048

// Allocate ONCE. Reuse forever.
val waveformData = createVisualizerBuffer(fftSize)

fun renderLoop() {
    window.requestAnimationFrame {
        val vis = player.visualizer
        if (vis != null) {
            // This is zero-allocation, zero-copy
            vis.getWaveform(waveformData)

            // Draw 'waveformData' to Canvas...
        }
        renderLoop()
    }
}
```
