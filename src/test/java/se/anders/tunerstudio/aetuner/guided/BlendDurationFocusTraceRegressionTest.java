package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Permanent presentation contract for bounded live/frozen Blend attempt traces. */
public final class BlendDurationFocusTraceRegressionTest {
    private BlendDurationFocusTraceRegressionTest() { }

    public static void main(String[] args) throws Exception {
        boundedTraceRetainsPhysicalResponseMarkers();
        driverChartKeepsPredictionDiagnosticOnly();
        sessionKeepsAcceptedGhostHistorySeparateFromAuthority();
        System.out.println("BlendDurationFocusTraceRegressionTest passed");
    }

    private static void boundedTraceRetainsPhysicalResponseMarkers() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        for (int i = 0; i < 320; i++) {
            double seconds = i * 0.01;
            samples.add(sample(seconds, 8.0 + Math.min(20.0, i * 0.15),
                    48.0 + i * 0.08, 52.0 + i * 0.09));
        }

        LiveSample hold = samples.get(90);
        LiveSample low = samples.get(120);
        LiveSample high = samples.get(190);
        BlendDurationFocusTrace trace = BlendDurationFocusTrace.build(
                samples, hold, low, high,
                73.5, 58.0, 68.0, 82.0,
                true, "VALID");

        require(trace.hasData(), "bounded trace lost display data");
        require(trace.seconds.length == 96,
                "trace must remain capped at exactly 96 points for a 320-sample attempt");
        require(Math.abs(trace.seconds[0]) < 1.0e-9,
                "trace did not preserve the first sample as t=0");
        require(Math.abs(trace.durationSeconds() - 3.19) < 0.011,
                "trace did not preserve the final sample");
        require(Math.abs(trace.pedalSettledSeconds - 0.90) < 0.011,
                "pedal-settle marker timing drifted");
        require(Math.abs(trace.responseLowSeconds - 1.20) < 0.011,
                "physical 20-percent marker timing drifted");
        require(Math.abs(trace.responseHighSeconds - 1.90) < 0.011,
                "physical 80-percent marker timing drifted");
        require(Math.abs(trace.catchupSeconds - trace.responseHighSeconds) < 1.0e-9,
                "legacy catchup alias no longer maps to the physical 80-percent marker");
        require(Math.abs(trace.physicalLateMapKpa - 73.5) < 1.0e-9
                        && Math.abs(trace.responseLowMapKpa - 58.0) < 1.0e-9
                        && Math.abs(trace.responseHighMapKpa - 68.0) < 1.0e-9,
                "physical response levels were not retained for presentation");
        require(trace.frozen && "VALID".equals(trace.outcome),
                "frozen result identity was not retained");
        require(Math.abs(trace.predictionTargetKpa - 82.0) < 1.0e-9,
                "prediction target diagnostic context was not retained");
    }

    private static void driverChartKeepsPredictionDiagnosticOnly() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019BlendDurationFocus.java");
        require(source.contains("HOLD / MEASURE")
                        && source.contains("MAP 20%")
                        && source.contains("MAP 80%")
                        && source.contains("Physical 20→80")
                        && source.contains("PREDICTION / FALLBACK — DIAGNOSTIC ONLY"),
                "Blend Driver view no longer teaches the physical 20-to-80 response boundary");
        require(source.contains("Scale physicalMapScale")
                        && !physicalScaleBlock(source).contains("fallbackMap")
                        && !physicalScaleBlock(source).contains("predictionTarget"),
                "prediction/fallback data can again control the physical MAP chart scale");
        require(source.contains("Prediction stays visible as diagnostic context")
                        && source.contains("AeUiTheme.focusMuted(), 1.4f, true, 0.45f"),
                "prediction context is no longer visually secondary to measured MAP");
    }

    private static String physicalScaleBlock(String source) {
        int start = source.indexOf("private static Scale physicalMapScale");
        int end = source.indexOf("    }\n\n    private static final class PlotArea", start);
        require(start >= 0 && end > start,
                "could not inspect physical MAP chart scale implementation");
        return source.substring(start, end);
    }

    private static void sessionKeepsAcceptedGhostHistorySeparateFromAuthority() throws Exception {
        String source = read("src/main/java/se/anders/tunerstudio/aetuner/guided/BlendDurationGuidedSession.java");
        require(source.contains("previousAcceptedFocusTrace = lastAcceptedFocusTrace;")
                        && source.contains("lastAcceptedFocusTrace = acceptedFocusTrace;"),
                "accepted event history no longer shifts current/previous ghost traces");
        require(source.contains("return previousAcceptedFocusTrace;")
                        && source.contains("return lastAcceptedFocusTrace;"),
                "ghost selection no longer distinguishes the frozen accepted result from the next attempt");
        require(source.contains("Presentation-only bounded traces")
                        && source.contains("never participate in capture"),
                "trace cache boundary is no longer explicitly presentation-only");
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static LiveSample sample(double seconds, double tps, double map, double fallback) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.EFFECTIVE_MAP, map + 0.5);
        values.put(ChannelRole.RPM, 2600.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
