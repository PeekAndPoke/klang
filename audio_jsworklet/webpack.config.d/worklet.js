// Since Kotlin 2.4 the generated config writes the source maps itself (a SourceMapDevToolPlugin, devtool false);
// a devtool on top writes klang-worklet.js.map twice and webpack fails. Set one only when Kotlin did not.
if (!(config.plugins || []).some(p => p && p.constructor && p.constructor.name === "SourceMapDevToolPlugin")) {
    config.devtool = "source-map";
}

config.output = config.output || {};

// AudioWorkletGlobalScope has no `window` (Safari will choke if webpack assumes it)
config.output.globalObject = "(typeof self !== 'undefined' ? self : globalThis)";
config.output.iife = true;

// Worklet bundle must be self-contained: no runtime chunk, no split chunks.
config.optimization = config.optimization || {};
config.optimization.runtimeChunk = false;
config.optimization.splitChunks = false;

// Log for debugging
console.log('[Webpack][AudioWorklet] config applied');
