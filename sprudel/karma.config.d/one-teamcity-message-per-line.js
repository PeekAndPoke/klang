// Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
// SPDX-License-Identifier: AGPL-3.0-or-later
//
// WHY THIS FILE EXISTS (2026-09-27)
//
// What breaks without it: Kotlin's Gradle plugin reads the browser test results as TeamCity
// service messages (##teamcity[testStarted ...]). Its Karma reporter writes them with no newline
// between them, so a module's whole run arrives as ONE line of about 250 bytes per test. The
// plugin drops any line over 1 MB ("too long teamcity service message") and a test result is
// lost: the task goes red with an empty failure, or, with the override flag, silently misses one.
//
// Why this module: its test count. sprudel crossed the limit at about 3600 tests on 2026-09-27;
// audio_be was at about 75 to 90 % of it. Both modules carry this same file.
//
// What it does: it wraps Kotlin's own reporter so that every chunk it writes ends in a newline.
// Nothing else changes: the same messages, in the same order, each now on its own line, which
// the plugin's parser reads exactly as before.
//
// Why not the alternatives: `kotlin.ignore.tcsm.overflow` only hides the lost result, and moving
// specs to jvmTest drops browser coverage.
//
// When it can go: when a newer Kotlin plugin ends each service message with a newline itself.
// Check a raw `jsBrowserTest` output: if the messages are already on separate lines without this
// file, delete it.
;(function (config) {
    const util = require('util');
    const KOTLIN_REPORTER = 'karma-kotlin-reporter';
    const LINED_REPORTER = 'karma-kotlin-reporter-lined';
    const kotlinReporter = require('kotlin-web-helpers/dist/karma-kotlin-reporter.js')['reporter:' + KOTLIN_REPORTER][1];

    function LinedKotlinReporter(baseReporterDecorator, config, emitter) {
        kotlinReporter.call(this, baseReporterDecorator, config, emitter);
        const write = this.write;
        this.write = function () {
            const msg = util.format.apply(null, arguments);
            write.call(this, msg.endsWith('\n') ? msg : msg + '\n');
        };
    }

    LinedKotlinReporter.$inject = kotlinReporter.$inject;

    config.plugins = config.plugins || [];
    config.plugins.push({['reporter:' + LINED_REPORTER]: ['type', LinedKotlinReporter]});
    config.reporters = (config.reporters || []).map(r => r === KOTLIN_REPORTER ? LINED_REPORTER : r);
})(config);
