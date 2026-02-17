// Remove UMD wrapper for AudioWorklet
// AudioWorklets must execute code immediately when loaded via addModule()
// The UMD wrapper prevents execution by wrapping code in a factory function

config.output = config.output || {};

// CRITICAL: Force 'self' as the global object.
// Default is 'window', which crashes in AudioWorkletGlobalScope in Safari.
// config.output.globalObject = "self";

// Don't wrap in library export
config.output.library = undefined;
config.output.libraryTarget = undefined;

// Ensure it's a simple IIFE that executes immediately
config.output.iife = true;

// For Kotlin 2.x / newer Webpack, sometimes optimization settings interfere
// Ensure we don't have side effects that might be tree-shaken incorrectly
config.optimization = config.optimization || {};
config.optimization.sideEffects = true;

// Log for debugging
console.log('[Webpack] AudioWorklet config applied:');
console.log('  - Library export: disabled');
console.log('  - Output format: IIFE (executes immediately)');
