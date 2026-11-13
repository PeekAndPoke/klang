// A fixed Karma port for this module's browser tests (2026-10-19). Several projects on this machine run Kotlin/JS
// browser tests, and on Karma's default port one project's ChromeHeadless could connect to another project's server
// (a spross run reported two klang suites as its own). Every project has its own range; klang's is 10031 to 10060,
// one port per module (list them with: grep -r "var port" --include=karma-port.js .). A new module with browser tests
// takes the next free port in the range.
// A port that is taken fails the run (KLANG-00441, from ultra's ULTRA-00230): Karma would move on to the next port,
// which belongs to another module, and the crossover is back. The probe runs in a child process, because this file
// is read synchronously.
;(function () {
    var port = 10047;
    var probe = "require('net').createServer().once('error', function () { process.exit(1); })" +
        ".listen(" + port + ", function () { process.exit(0); });";

    try {
        require('child_process').execFileSync(process.execPath, ['-e', probe]);
    } catch (e) {
        throw new Error('Karma port ' + port + ' of klang module "workshop" is in use: another build on this machine ' +
            'runs these browser tests, or a Karma server was left behind. Wait for it, or stop the process that ' +
            'holds the port.');
    }

    config.set({ port: port });
})();
