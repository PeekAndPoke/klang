/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads one JFR recording of `runSongBenchmark --args=songs` and prints, per song, where the render time and the
 * allocations go. Usage: java JfrSongProfile.java <file.jfr> <audioSecondsSong1> <audioSecondsSong2>
 */
public class JfrSongProfile {

    static final String RB = "io.peekandpoke.klang.audio_be.KlangAudioRenderer.renderBlock";
    static final String COMPILE = "io.peekandpoke.klang.SongBenchmark.compile";

    static String name(RecordedFrame f) {
        return f.getMethod().getType().getName() + "." + f.getMethod().getName();
    }

    static boolean contains(List<RecordedFrame> frames, String m) {
        for (RecordedFrame f : frames) {
            if (name(f).equals(m)) return true;
        }
        return false;
    }

    static boolean anyEngine(List<RecordedFrame> frames) {
        for (RecordedFrame f : frames) {
            if (name(f).contains("GraphCensus") || name(f).contains("SongBenchmark")) return false;
        }
        for (RecordedFrame f : frames) {
            if (name(f).contains(".audio_be.")) return true;
        }
        return false;
    }

    static final Pattern BUILD = Pattern.compile(
        "(voices\\.VoiceFactory|IgnitorDslRuntimeKt|IgnitorDslOptimizer|KatalystChainBuilder|IgnitorRegistry\\.optimized)");
    static final Pattern SCHED = Pattern.compile(
        "\\.(voices\\.VoiceScheduler|SampleStore|PlaybackEngineDispatcher\\.(handle|process|drain|accept|on)|voices\\.VoiceLimits|voices\\.SoloTracker|voices\\.PlaybackCtx)");
    // shared cores: never decide, keep walking towards the caller
    static final Pattern TRANSPARENT = Pattern.compile(
        "^(java\\.|jdk\\.|kotlin\\.|sun\\.|io\\.peekandpoke\\.klang\\.audio_be\\.(utils\\.|filters\\.|effects\\.|DistortionCore|EnvelopeCore|CrushCore|Oversampler|ShapingFuncs|AdsrCurveMath|KnobGlide|Crossfade|DistortionShape|StereoBuffer|TailRelease)|io\\.peekandpoke\\.klang\\.common\\.)");
    static final Pattern SOURCE = Pattern.compile(
        "ignitor\\.(.*(Sine|Saw|Wave|Unison|Partial|Noise|Karplus|Pluck|Impulse|SampleIgnitor|Dust|Crackle|Drift|Pulse|Pulze|Square|Tri|Ramp|Oscill|PhasePool|Berlin|Perlin|Brown|Pink|White|Zaw|Zamp|Stack|_noise_laws|KarplusString)[^.]*)\\.");

    static String area(List<RecordedFrame> frames, boolean render) {
        if (!render) {
            return "benchmark harness (outside renderBlock)";
        }

        for (RecordedFrame f : frames) {
            String n = name(f);
            if (BUILD.matcher(n).find()) return "build of voices and chains";
        }

        for (RecordedFrame f : frames) {
            String n = name(f);
            if (SCHED.matcher(n).find()) return "scheduling";
            if (TRANSPARENT.matcher(n).find()) continue;
            if (n.contains(".audio_be.katalyst.")) return "Katalyst stages";
            if (n.contains(".audio_be.ignitor.")) {
                return SOURCE.matcher(n).find() ? "sources" : "per-note stages";
            }
            if (n.contains(".audio_be.voices.Voice_channelKt") || n.contains(".audio_be.cylinders.") || n.contains(".audio_be.HouseStage")
                || n.contains(".audio_be.master.") || n.contains(".audio_be.PlaybackGain")) {
                return "mixing (voice channel, cylinders, house stage)";
            }
            if (n.contains(".audio_be.voices.")) return "per-note stages";
            if (n.contains(".audio_be.ChainHost") || n.contains(".audio_be.ChainSwap")) return "Katalyst stages";
            if (n.contains(".audio_be.")) return "engine loop (renderer, engine, dispatcher)";
            return "other: " + n;
        }
        return "unclassified";
    }

    static class Song {
        String label;
        Instant from, to;
        double audioSeconds;
        long samples, renderSamples, truncated;
        Map<String, Long> self = new HashMap<>();
        Map<String, Map<String, Long>> selfArea = new HashMap<>();
        Map<String, Long> areas = new HashMap<>();
        Map<String, Long> renderSelf = new HashMap<>();
        Map<String, Map<String, Long>> renderSelfArea = new HashMap<>();
        double allocAll, allocRender;
        Map<String, Double> allocSite = new HashMap<>();
        Map<String, Double> allocClass = new HashMap<>();
        Map<String, Double> allocArea = new HashMap<>();
        double gcPauseMs;
        long gcCount;
    }

    public static void main(String[] a) throws Exception {
        Path p = Path.of(a[0]);
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        // song boundaries: the compile of each song (KlangScript execute under SongBenchmark.compile)
        List<Instant> compiles = new ArrayList<>();
        Instant last = null;
        events.sort(Comparator.comparing(RecordedEvent::getStartTime));
        for (RecordedEvent e : events) {
            if (!e.getEventType().getName().equals("jdk.ExecutionSample")) continue;
            RecordedStackTrace st = e.getStackTrace();
            if (st == null) continue;
            if (contains(st.getFrames(), COMPILE)) {
                if (last == null || e.getStartTime().toEpochMilli() - last.toEpochMilli() > 5000) compiles.add(e.getStartTime());
                last = e.getStartTime();
            }
        }
        System.out.println("compile clusters at " + compiles);
        Instant end = events.get(events.size() - 1).getStartTime();
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < compiles.size(); i++) {
            Song s = new Song();
            s.label = "song " + (i + 1);
            s.from = compiles.get(i);
            s.to = i + 1 < compiles.size() ? compiles.get(i + 1) : end;
            s.audioSeconds = Double.parseDouble(a[1 + i]);
            songs.add(s);
        }
        String renderThread = null;
        Map<String, Long> settings = new TreeMap<>();
        for (RecordedEvent e : events) {
            String t = e.getEventType().getName();
            if (t.equals("jdk.ActiveSetting")) {
                String en = e.getString("name");
                if (en.equals("period") || en.equals("throttle")) settings.merge(e.getLong("id") + " " + en + "=" + e.getString("value"), 1L, Long::sum);
            }
            Song s = null;
            for (Song x : songs) if (!e.getStartTime().isBefore(x.from) && e.getStartTime().isBefore(x.to)) s = x;
            if (s == null) continue;
            if (t.equals("jdk.ExecutionSample")) {
                RecordedStackTrace st = e.getStackTrace();
                if (st == null) continue;
                List<RecordedFrame> fr = st.getFrames();
                boolean render = contains(fr, RB) || (st.isTruncated() && anyEngine(fr));
                if (render && renderThread == null) renderThread = e.getThread("sampledThread").getJavaName();
                String th = e.getThread("sampledThread").getJavaName();
                if (renderThread != null && !th.equals(renderThread)) continue;
                s.samples++;
                if (st.isTruncated()) s.truncated++;
                String leaf = fr.isEmpty() ? "?" : name(fr.get(0));
                String ar = area(fr, render);
                s.areas.merge(ar, 1L, Long::sum);
                if (render) {
                    s.renderSamples++;
                    s.renderSelf.merge(leaf, 1L, Long::sum);
                    s.renderSelfArea.computeIfAbsent(leaf, k -> new HashMap<>()).merge(ar, 1L, Long::sum);
                }
                s.self.merge(leaf, 1L, Long::sum);
            } else if (t.equals("jdk.ObjectAllocationSample")) {
                RecordedStackTrace st = e.getStackTrace();
                double w = e.getLong("weight");
                s.allocAll += w;
                if (st == null) continue;
                List<RecordedFrame> fr = st.getFrames();
                if (!(contains(fr, RB) || (st.isTruncated() && anyEngine(fr)))) continue;
                s.allocRender += w;
                String site = "?";
                for (RecordedFrame f : fr) {
                    String n = name(f);
                    if (!(n.startsWith("java.") || n.startsWith("jdk.") || n.startsWith("kotlin."))) {
                        site = n + ":" + f.getLineNumber();
                        break;
                    }
                }
                String cls = e.getClass("objectClass").getName();
                s.allocSite.merge(site + "  [" + cls + "]", w, Double::sum);
                s.allocClass.merge(cls, w, Double::sum);
                s.allocArea.merge(area(fr, true), w, Double::sum);
            } else if (t.equals("jdk.GarbageCollection")) {
                s.gcCount++;
                s.gcPauseMs += e.getDuration("sumOfPauses").toNanos() / 1e6;
            }
        }
        System.out.println("render thread " + renderThread);
        System.out.println("settings " + settings.keySet().stream().filter(x -> x.contains("period")).limit(40).toList());
        for (Song s : songs) {
            double secs = (s.to.toEpochMilli() - s.from.toEpochMilli()) / 1000.0;
            System.out.printf(Locale.ROOT, "%n===== %s  window %.1f s  audio %.1f s  samples %d (render %d, %.1f %%)  truncated stacks %d%n",
                s.label, secs, s.audioSeconds, s.samples, s.renderSamples, 100.0 * s.renderSamples / s.samples, s.truncated);
            System.out.println("-- areas, share of the render thread's samples");
            s.areas.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).forEach(en ->
                System.out.printf(Locale.ROOT, "  %6.2f %%  %6.2f %% of render  %s%n", 100.0 * en.getValue() / s.samples,
                    en.getKey().startsWith("benchmark") ? Double.NaN : 100.0 * en.getValue() / s.renderSamples, en.getKey()));
            System.out.println("-- top 30 methods by self time inside renderBlock (share of render samples; area by caller walk)");
            int[] i = {0};
            s.renderSelf.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(30).forEach(en -> {
                Map<String, Long> ar = s.renderSelfArea.get(en.getKey());
                String areas = ar.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                    .map(z -> z.getKey() + " " + Math.round(100.0 * z.getValue() / en.getValue()) + "%").reduce((x, y) -> x + ", " + y).orElse("");
                System.out.printf(Locale.ROOT, "  %2d  %6.2f %%  %-100s  %s%n", ++i[0], 100.0 * en.getValue() / s.renderSamples, en.getKey(), areas);
            });
            System.out.println("-- top 15 methods by self time OUTSIDE renderBlock on the same thread (harness, census, compile)");
            Map<String, Long> outside = new HashMap<>(s.self);
            s.renderSelf.forEach((k, v) -> outside.merge(k, -v, Long::sum));
            outside.entrySet().stream().filter(x -> x.getValue() > 0).sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(15).forEach(en ->
                System.out.printf(Locale.ROOT, "        %6.2f %% of all  %s%n", 100.0 * en.getValue() / s.samples, en.getKey()));
            System.out.printf(Locale.ROOT, "-- allocation (JFR ObjectAllocationSample weights, an estimate): all threads %.1f MB, inside renderBlock %.1f MB = %.0f KB per second of audio (%.0f B per 128-frame block)%n",
                s.allocAll / 1e6, s.allocRender / 1e6, s.allocRender / 1e3 / s.audioSeconds, s.allocRender / (s.audioSeconds * 48000.0 / 128.0));
            System.out.printf(Locale.ROOT, "-- GC in the window: %d collections, %.1f ms of pauses (%.3f %% of the window)%n", s.gcCount, s.gcPauseMs, 100.0 * s.gcPauseMs / 1000.0 / secs);
            System.out.println("-- render allocation by area");
            s.allocArea.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).forEach(en ->
                System.out.printf(Locale.ROOT, "  %6.1f %%  %8.1f KB/s audio  %s%n", 100.0 * en.getValue() / s.allocRender, en.getValue() / 1e3 / s.audioSeconds, en.getKey()));
            System.out.println("-- render allocation by class, top 12");
            s.allocClass.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(12).forEach(en ->
                System.out.printf(Locale.ROOT, "  %6.1f %%  %8.1f KB/s audio  %s%n", 100.0 * en.getValue() / s.allocRender, en.getValue() / 1e3 / s.audioSeconds, en.getKey()));
            System.out.println("-- render allocation by first engine frame, top 25");
            s.allocSite.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(25).forEach(en ->
                System.out.printf(Locale.ROOT, "  %6.1f %%  %8.1f KB/s audio  %s%n", 100.0 * en.getValue() / s.allocRender, en.getValue() / 1e3 / s.audioSeconds, en.getKey()));
        }
    }
}
