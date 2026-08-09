package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic, test-only characterization of retained long-session state. */
public final class LongSessionCharacterizationTest {
    private static final int SAMPLES_PER_EVENT = 32;
    private static final int[] EVENT_COUNTS = new int[]{0, 100, 500, 1000, 1500};

    private LongSessionCharacterizationTest() { }

    public static void main(String[] args) throws Exception {
        assertBoundedState();
        characterizeScaling();
        assertResetCleanupAndReuse();
        stressConcurrentLifecycle();
        System.out.println("LongSessionCharacterizationTest passed");
    }

    private static void characterizeScaling() throws Exception {
        AeProjectSnapshot project = projectSnapshot();
        System.out.println("LONG_SESSION eventCount,retainedSamples,scalarSlots,sessionReviewMs,"
                + "panelRefreshColdMs,panelRefreshCachedMs,guidanceMs,reportMs,csvMs,csvBytes,resetMs");
        for (int count : EVENT_COUNTS) {
            List<TransientEvent> events = events(count);
            long retainedSamples = (long) count * SAMPLES_PER_EVENT;
            long scalarSlots = retainedSamples * ChannelRole.values().length;
            SessionMonitor.Snapshot monitor = new SessionMonitor().snapshot();

            long started = System.nanoTime();
            SessionReview review = SessionReview.build(events, monitor);
            double reviewMs = elapsedMillis(started);

            AeTunerPanel panel = panelWithEvents(project, events);
            Object overviewController = field(panel, "overviewController");
            double overviewColdMs = timedInvokeOnEdt(panel, "refreshUi");
            Object cachedReview = field(overviewController, "cachedEventReview");
            long cachedRevision = ((Long) field(overviewController,
                    "cachedEventReviewRevision")).longValue();
            double overviewCachedMs = timedInvokeOnEdt(panel, "refreshUi");
            require(field(overviewController, "cachedEventReview") == cachedReview,
                    "Unchanged event revision must reuse the controller cached SessionReview");
            require(((Long) field(overviewController,
                    "cachedEventReviewRevision")).longValue() == cachedRevision,
                    "Controller cached review revision changed without a new event");

            started = System.nanoTime();
            Method guidanceMethod = overviewController.getClass().getDeclaredMethod(
                    "sessionGuidanceText", AeProjectSnapshot.class, List.class);
            guidanceMethod.setAccessible(true);
            String guidance = (String) guidanceMethod.invoke(
                    overviewController, project, events);
            double guidanceMs = elapsedMillis(started);
            require(guidance.length() > 0, "Guidance text must not be empty");

            SessionDiagnostics diagnostics = SessionDiagnostics.build(
                    System.nanoTime() - 1000000000L, System.nanoTime(), events,
                    17, 3, 0L, 0, 12.5, 20.0);
            assertDiagnostics(diagnostics, count, retainedSamples);

            started = System.nanoTime();
            MapEstimateCollector collector = new MapEstimateCollector();
            collector.configure(project);
            MapEstimateSuggestion map = MapEstimateSuggestion.build(project, collector, 20, 115.0);
            MapBlendSuggestion blend = MapBlendSuggestion.build(project, events);
            String report = MapPredictReportBuilder.build(
                    "Long-session characterization", events, 100.0, 20, 115.0,
                    diagnostics, map, blend,
                    new EnumMap<ChannelRole, String>(ChannelRole.class),
                    new EnumMap<ChannelRole, Double>(ChannelRole.class),
                    review, started);
            double reportMs = elapsedMillis(started);
            require(report.contains("Plugin version: " + AeTunerPlugin.VERSION),
                    "Report lost plugin identity");
            require(report.contains("Java runtime:"), "Report lost Java runtime identity");

            double csvMs = 0.0;
            long csvBytes = 0L;
            if (!events.isEmpty()) {
                File first = File.createTempFile("ae-long-session-", ".csv");
                File second = File.createTempFile("ae-long-session-repeat-", ".csv");
                try {
                    started = System.nanoTime();
                    EventCsvWriter.write(first, events);
                    csvMs = elapsedMillis(started);
                    csvBytes = first.length();
                    EventCsvWriter.write(second, events);
                    require(java.util.Arrays.equals(digest(first), digest(second)),
                            "CSV content changed independently of evidence");
                } finally {
                    first.delete();
                    second.delete();
                }
            }

            SessionDiagnostics differentTiming = SessionDiagnostics.build(
                    System.nanoTime() - 1000000000L, System.nanoTime(), events,
                    17, 3, 0L, 0, 999.0, 888.0);
            String timingReport = MapPredictReportBuilder.build(
                    "Long-session characterization", events, 100.0, 20, 115.0,
                    differentTiming, map, blend,
                    new EnumMap<ChannelRole, String>(ChannelRole.class),
                    new EnumMap<ChannelRole, Double>(ChannelRole.class),
                    review, System.nanoTime());
            require(evidenceBody(report).equals(evidenceBody(timingReport)),
                    "Export timing changed evidence, recommendations, or proposal content");

            started = System.nanoTime();
            invokeOnEdt(panel, "resetSession");
            double resetMs = elapsedMillis(started);
            assertPanelReset(panel);
            panel.disposePanel();

            System.out.println("LONG_SESSION " + count + "," + retainedSamples + "," + scalarSlots
                    + "," + format(reviewMs) + "," + format(overviewColdMs)
                    + "," + format(overviewCachedMs) + "," + format(guidanceMs)
                    + "," + format(reportMs) + "," + format(csvMs) + "," + csvBytes
                    + "," + format(resetMs));
        }
    }

    private static void assertDiagnostics(SessionDiagnostics diagnostics,
                                          int eventCount,
                                          long retainedSamples) {
        require(diagnostics.elapsedSeconds >= 0.0, "Elapsed time must be nonnegative");
        require(diagnostics.totalEvents == eventCount, "Diagnostic event total mismatch");
        require(diagnostics.acceptedEvents + diagnostics.rejectedEvents == diagnostics.totalEvents,
                "Accepted and rejected totals are incoherent");
        require(diagnostics.retainedSamples == retainedSamples, "Retained sample total mismatch");
        int expected = eventCount == 0 ? 0 : SAMPLES_PER_EVENT;
        require(diagnostics.minimumSamplesPerEvent == expected,
                "Minimum samples/event mismatch");
        require(diagnostics.maximumSamplesPerEvent == expected,
                "Maximum samples/event mismatch");
        require(Math.abs(diagnostics.meanSamplesPerEvent - expected) < 0.0001,
                "Mean samples/event mismatch");
        String text = diagnostics.toReportText();
        require(text.contains("Session elapsed:") && text.contains("Detector buffers now: ring 17")
                        && text.contains("active event 3 sample(s)"),
                "Diagnostic presentation omitted elapsed or detector state");
        require(text.contains("Plugin version: " + AeTunerPlugin.VERSION)
                        && text.contains("Java runtime:"),
                "Diagnostic presentation omitted runtime identity");
    }

    private static void assertBoundedState() throws Exception {
        AeEventDetector detector = new AeEventDetector();
        for (int i = 0; i < 1200; i++) {
            detector.addPassiveSample(sample(i * 8000000L, i * 0.008, 1800.0,
                    10.0, 50.0, false, 0.0, 0.0));
        }
        require(detector.getRingSampleCount() <= 400, "Detector ring exceeded 400 samples");

        detector.resetSession();
        TransientEvent completed = null;
        for (int i = 0; i < 800 && completed == null; i++) {
            boolean active = i >= 20;
            LiveSample sample = sample(i * 8000000L, i * 0.008, 1800.0,
                    active ? 20.0 : 10.0, 50.0 + Math.min(i, 30), active,
                    active ? 2.0 : 0.0, active ? 1.0 : 0.0);
            completed = detector.addSample(sample, 1.5, true);
        }
        require(completed != null, "Active event did not close at its duration bound");
        require(completed.getSamples().size() <= 600,
                "Realistic 125 Hz event retained more than the 4.75 s capture bound");
        require(detector.getActiveSampleCount() == 0, "Closed active event retained detector samples");
        int boundedEventSamples = completed.getSamples().size();

        RecommendationHistory history = new RecommendationHistory();
        SessionReview emptyReview = SessionReview.build(new ArrayList<TransientEvent>(),
                new SessionMonitor().snapshot());
        for (int i = 0; i < 150; i++) {
            history.observe("characterization state " + i, emptyReview,
                    new ArrayList<TransientEvent>(), new SessionMonitor().snapshot(),
                    new EnumMap<ChannelRole, String>(ChannelRole.class), i);
        }
        require(history.size() == 100, "Session Guidance must remain bounded to 100 entries");

        MapEstimateCollector collector = new MapEstimateCollector();
        collector.configure(projectSnapshot());
        for (int i = 1; i <= 2000; i++) {
            collector.addSample(sample(i * 50000000L, i * 0.05, 1800.0,
                    20.0, 70.0, false, 0.0, 0.0));
        }
        long[][] counts = collector.copyCounts();
        require(counts.length == 4 && counts[0].length == 4,
                "MAP Estimate storage must retain fixed table dimensions");
        require(collector.getAcceptedSamples() == 2000L,
                "MAP Estimate accepted counter lost samples during bounded accumulation");
        System.out.println("LONG_SESSION_BOUNDS ringMax=400,realisticBoundedEventSamples="
                + boundedEventSamples + ",guidanceMax=100,mapCells="
                + (counts.length * counts[0].length));
    }

    private static void assertResetCleanupAndReuse() throws Exception {
        List<TransientEvent> retained = events(1000);
        AeTunerPanel panel = panelWithEvents(projectSnapshot(), retained);
        Object plot = field(panel, "plotPanel");
        Method setEvent = plot.getClass().getDeclaredMethod("setEvent", TransientEvent.class);
        setEvent.setAccessible(true);
        setEvent.invoke(plot, retained.get(retained.size() - 1));

        AeEventDetector detector = (AeEventDetector) field(panel, "eventDetector");
        detector.addPassiveSample(sample(100000000L, 0.1, 1800.0, 5.0, 50.0, false, 0.0, 0.0));
        RecommendationHistory history = (RecommendationHistory) field(panel, "recommendationHistory");
        history.observe("pre-reset", SessionReview.build(retained, new SessionMonitor().snapshot()),
                retained, new SessionMonitor().snapshot(),
                new EnumMap<ChannelRole, String>(ChannelRole.class), 1L);
        setField(panel, "sampleRateHz", Double.valueOf(99.0));
        setField(panel, "samplesInWindow", Integer.valueOf(42));
        setField(panel, "lastCsvExportMillis", Double.valueOf(12.0));
        setField(panel, "lastReportExportMillis", Double.valueOf(15.0));
        long previousStart = ((Long) field(panel, "sessionStartedNano")).longValue();

        invokeOnEdt(panel, "resetSession");
        assertPanelReset(panel);
        require(!history.toDisplayText().contains("pre-reset"),
                "Reset retained a pre-reset Session Guidance entry");
        require(((Long) field(panel, "sessionStartedNano")).longValue() >= previousStart,
                "Reset did not restart diagnostic session time");

        TransientEvent reusable = detectorEvent(detector);
        require(reusable != null && !reusable.getSamples().isEmpty(),
                "Detector could not capture a new event after reset");
        panel.disposePanel();
    }

    private static void assertPanelReset(AeTunerPanel panel) throws Exception {
        @SuppressWarnings("unchecked")
        List<TransientEvent> events = (List<TransientEvent>) field(panel, "capturedEvents");
        require(events.isEmpty(), "Reset retained captured events");
        require(((Integer) field(panel, "acceptedEvents")).intValue() == 0
                        && ((Integer) field(panel, "rejectedEvents")).intValue() == 0,
                "Reset retained event counters");
        require(((Double) field(panel, "sampleRateHz")).doubleValue() == 0.0
                        && ((Integer) field(panel, "samplesInWindow")).intValue() == 0,
                "Reset retained sample-rate state");
        require(Double.isNaN(((Double) field(panel, "lastCsvExportMillis")).doubleValue())
                        && Double.isNaN(((Double) field(panel, "lastReportExportMillis")).doubleValue()),
                "Reset retained export timing state");
        AeEventDetector detector = (AeEventDetector) field(panel, "eventDetector");
        require(detector.getRingSampleCount() == 0 && detector.getActiveSampleCount() == 0,
                "Reset retained detector state");
        MapEstimateCollector collector = (MapEstimateCollector) field(panel, "mapEstimateCollector");
        require(collector.getAcceptedSamples() == 0L, "Reset retained MAP Estimate samples");
        RecommendationHistory history = (RecommendationHistory) field(panel, "recommendationHistory");
        require(history.size() <= 1,
                "Reset retained more than the immediate post-reset baseline guidance entry");
        Object plot = field(panel, "plotPanel");
        require(field(plot, "event") == null, "Reset retained event-preview reference");
        require(field(panel, "previousSample") == null, "Reset retained latest sampling reference");
        require(((JTextArea) field(panel, "latestEventText")).getText().contains("Session reset"),
                "Reset did not replace latest-event text with reset status");
        require(!((SessionMonitor) field(panel, "sessionMonitor")).snapshot().hasData(),
                "Reset retained session-monitor counters");
    }

    private static void stressConcurrentLifecycle() throws Exception {
        final AeTunerPanel panel = panelWithEvents(projectSnapshot(), events(100));
        @SuppressWarnings("unchecked")
        java.util.Map<String, ChannelRole> subscriptions =
                (java.util.Map<String, ChannelRole>) field(panel, "subscribedChannels");
        subscriptions.put("TPSValue", ChannelRole.TPS);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread callback = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    for (int i = 0; i < 300; i++) {
                        panel.setCurrentOutputChannelValue("TPSValue", i % 25);
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                }
            }
        }, "long-session-callback");
        callback.start();
        for (int i = 0; i < 20; i++) {
            invokeOnEdt(panel, "refreshUi");
            if ((i % 5) == 0) invokeOnEdt(panel, "resetSession");
        }
        callback.join(5000L);
        require(!callback.isAlive(), "Callback/reset/refresh stress deadlocked");
        require(failure.get() == null, "Concurrency stress failed: " + failure.get());
        invokeOnEdt(panel, "reconnect");
        panel.disposePanel();
        panel.disposePanel();
        require(!panel.isRefreshTimerRunning(), "Close lifecycle left duplicate/running timer activity");
    }

    private static AeTunerPanel panelWithEvents(AeProjectSnapshot project,
                                                List<TransientEvent> source) throws Exception {
        final AtomicReference<AeTunerPanel> result = new AtomicReference<AeTunerPanel>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { result.set(new AeTunerPanel()); }
        });
        AeTunerPanel panel = result.get();
        setField(panel, "projectSnapshot", project);
        ((MapEstimateCollector) field(panel, "mapEstimateCollector")).configure(project);
        @SuppressWarnings("unchecked")
        List<TransientEvent> retained = (List<TransientEvent>) field(panel, "capturedEvents");
        retained.addAll(source);
        int accepted = 0;
        for (TransientEvent event : source) if (event.isAccepted()) accepted++;
        setField(panel, "acceptedEvents", Integer.valueOf(accepted));
        setField(panel, "rejectedEvents", Integer.valueOf(source.size() - accepted));
        setField(panel, "eventRevision", Long.valueOf(source.size()));
        @SuppressWarnings("unchecked")
        EnumMap<ChannelRole, String> names =
                (EnumMap<ChannelRole, String>) field(panel, "channelNames");
        names.put(ChannelRole.MAP_PRED_ACTIVE, "isMapPredictionActive");
        names.put(ChannelRole.FALLBACK_MAP, "fallbackMap");
        names.put(ChannelRole.EFFECTIVE_MAP, "effectiveMap");
        names.put(ChannelRole.MAP_PRED_RESET_CNT, "predTimerResetCnt");
        return panel;
    }

    private static List<TransientEvent> events(int count) {
        List<TransientEvent> events = new ArrayList<TransientEvent>(count);
        for (int i = 0; i < count; i++) {
            List<LiveSample> samples = new ArrayList<LiveSample>(SAMPLES_PER_EVENT);
            long base = (long) i * 10000000000L;
            for (int sample = 0; sample < SAMPLES_PER_EVENT; sample++) {
                double ratio = sample / (double) (SAMPLES_PER_EVENT - 1);
                samples.add(sample(base + sample * 20000000L,
                        i * 10.0 + sample * 0.02, 1700.0 + (i % 4) * 500.0,
                        5.0 + ratio * 15.0, 50.0 + ratio * 30.0,
                        sample >= 2 && sample < 24, sample >= 2 ? 3.0 : 0.0, 1.0));
            }
            boolean accepted = (i % 5) != 0;
            events.add(new TransientEvent(i + 1, accepted,
                    accepted ? "MAP Predict event" : "Rejected",
                    accepted ? "coherent synthetic held opening" : "synthetic rejection",
                    samples, true));
        }
        return events;
    }

    private static TransientEvent detectorEvent(AeEventDetector detector) {
        TransientEvent result = null;
        for (int i = 0; i < 500 && result == null; i++) {
            boolean active = i >= 10 && i < 40;
            result = detector.addSample(sample(i * 10000000L, i * 0.01, 1800.0,
                    active ? 20.0 : 10.0, 50.0 + Math.min(i, 30), active,
                    active ? 2.0 : 0.0, active ? 1.0 : 0.0), 1.5, true);
        }
        return result;
    }

    private static LiveSample sample(long nano,
                                     double seconds,
                                     double rpm,
                                     double tps,
                                     double map,
                                     boolean prediction,
                                     double tpsDot,
                                     double reset) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, 80.0);
        values.put(ChannelRole.EFFECTIVE_MAP, prediction ? 80.0 : map);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, reset);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, tpsDot);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.IGNITION_ON, 1.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, 1.0);
        values.put(ChannelRole.LAMBDA, 1.0);
        return new LiveSample(nano, seconds, values, tpsDot, 0.0);
    }

    private static AeProjectSnapshot projectSnapshot() {
        double[] rpm = new double[]{600.0, 1300.0, 1700.0, 2450.0};
        double[] tps = new double[]{0.0, 13.5, 20.0, 33.5};
        double[][] table = new double[][]{
                {35.0, 40.0, 45.0, 50.0},
                {70.0, 75.0, 80.0, 85.0},
                {80.0, 85.0, 90.0, 95.0},
                {95.0, 100.0, 105.0, 110.0}
        };
        return new AeProjectSnapshot("Long-session characterization",
                new double[]{0.0, 10.0}, new double[]{0.0, 20.0},
                new double[][]{{0.0, 0.0}, {0.0, 0.0}}, rpm,
                new double[]{1.5, 1.5, 1.5, 1.5}, 0.0, 0.0,
                new double[]{-20.0, 20.0, 80.0}, new double[]{1.0, 1.0, 1.0},
                false, true, "Synthetic wall model", false, true, true, true,
                new double[][]{{0.8}}, new double[][]{{0.8}}, rpm, tps, table,
                new double[]{600.0, 2450.0, 4350.0, 6200.0},
                new double[]{0.26, 0.26, 0.18, 0.18});
    }

    private static double timedInvokeOnEdt(final Object target, final String name) throws Exception {
        final long[] elapsed = new long[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                long started = System.nanoTime();
                try {
                    invoke(target, name);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                elapsed[0] = System.nanoTime() - started;
            }
        });
        return elapsed[0] / 1000000.0;
    }

    private static void invokeOnEdt(final Object target, final String name) throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try { invoke(target, name); }
                catch (Throwable ex) { failure.set(ex); }
            }
        });
        if (failure.get() != null) throwAsException(failure.get());
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException ex) {
            throwAsException(ex.getCause());
            return null;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void throwAsException(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new RuntimeException(failure);
    }

    private static double elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1000000.0;
    }

    private static String evidenceBody(String report) {
        int start = report.indexOf("MAP ESTIMATE DRAFT");
        int end = report.indexOf("\nReport preparation duration before file write:");
        require(start >= 0 && end > start, "Report evidence boundaries missing");
        return report.substring(start, end);
    }

    private static byte[] digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return digest.digest();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
