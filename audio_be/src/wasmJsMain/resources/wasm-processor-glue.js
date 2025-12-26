// This import will be resolved by Webpack against the generated Kotlin Wasm output
import init, {generateSineWave} from './dsp.mjs';

class WasmBridgeProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.ready = false;

        // Initialize the Wasm module
        init().then(() => {
            console.log("Wasm Module Loaded!");
            this.ready = true;
        });
    }

    process(inputs, outputs, parameters) {
        if (!this.ready) return true;

        const output = outputs[0];
        const channel = output[0]; // Left channel

        if (!channel) return true;

        // 1. Call Kotlin Wasm to get samples
        // This creates a new Float32Array in Wasm memory and copies it to JS
        const samples = generateSineWave(channel.length);

        // 2. Copy to Web Audio Buffer
        // Optimized: channel.set(samples);
        for (let i = 0; i < channel.length; i++) {
            channel[i] = samples[i];
        }

        // Copy to Right channel too if it exists
        if (output[1]) {
            output[1].set(channel);
        }

        return true;
    }
}

registerProcessor('wasm-processor', WasmBridgeProcessor);
