// config.devtool = false;
config.devtool = "source-map";

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
