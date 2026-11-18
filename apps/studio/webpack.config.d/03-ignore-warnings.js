// Ktor 3.6 loads Node's `net` and `dgram` through `import(<expression>)` in ktor-network, behind a
// `process.release.name === 'node'` check, so the browser never takes that path. Webpack still warns
// ("Critical dependency: the request of a dependency is an expression") and the dev server shows it as
// "Compiled with problems". Ignore exactly that warning from exactly that module; every other one still shows.
config.ignoreWarnings = config.ignoreWarnings || [];
config.ignoreWarnings.push({
    module: /ktor-ktor-network\.mjs$/,
    message: /Critical dependency: the request of a dependency is an expression/,
});
