// More patience with a busy browser (2026-11-21). The specs of this module run in ChromeHeadless on its one thread,
// and a heavy spec (the trumpet player's routes over many seeds) holds that thread for seconds; while the machine is
// busy with other builds and browsers, longer than Karma's default of 5 s for an answer to its ping. Karma then
// dropped the browser before any result was written ("Disconnected ... reconnect failed before timeout of 2000ms
// (ping timeout)"), about every second run that day. A spec that really hangs still ends the run: after two minutes
// without any activity.
config.set({
    pingTimeout: 60000,
    browserDisconnectTimeout: 30000,
    browserNoActivityTimeout: 120000,
});
