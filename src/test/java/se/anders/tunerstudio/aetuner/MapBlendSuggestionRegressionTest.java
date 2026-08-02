package se.anders.tunerstudio.aetuner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class MapBlendSuggestionRegressionTest {
    private MapBlendSuggestionRegressionTest() { }

    public static void main(String[] args) {
        oneEventMustNotCreatePasteReadyProposal();
        consistentEventsChangeOnlyTheirActualRpmPoint();
        fiveTightEventsCreateHighConfidence();
        repeatedStabsRemainDiagnosticOnly();
        highSpreadEvidenceMustBeWithheld();
        System.out.println("MapBlendSuggestionRegressionTest passed");
    }

    private static void oneEventMustNotCreatePasteReadyProposal() {
        List<EventSummary> events = new ArrayList<EventSummary>();
        events.add(event(1, 1500.0, 0.20, false));
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(snapshot(), events);
        require(!suggestion.isAvailable(), "One event created a paste-ready proposal");
        require(suggestion.getDisplayText().contains("1500 RPM point"),
                "Actual table RPM point was not shown");
        require(suggestion.getDisplayText().contains("Confidence: INSUFFICIENT"),
                "Single-event confidence was not withheld");
        require(suggestion.getDisplayText().contains("One event cannot create"),
                "Single-event limitation was not explained");
    }

    private static void consistentEventsChangeOnlyTheirActualRpmPoint() {
        List<EventSummary> events = new ArrayList<EventSummary>();
        events.add(event(1, 1480.0, 0.20, false));
        events.add(event(2, 1510.0, 0.21, false));
        events.add(event(3, 1530.0, 0.22, false));
        events.add(event(4, 1495.0, 0.23, false));
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(snapshot(), events);
        require(suggestion.isAvailable(), "Consistent per-RPM evidence was not eligible");
        String[] values = suggestion.getCopyPasteBlock().split("\\n");
        require(values.length == 4, "Copy block did not preserve the real four-point axis");
        require("0.30".equals(values[0]), "Unvisited 1000 RPM point changed");
        require("0.24".equals(values[1]), "1500 RPM point did not use retained median plus margin");
        require("0.30".equals(values[2]), "Unvisited 2000 RPM point changed");
        require("0.30".equals(values[3]), "Unvisited 2500 RPM point changed");
        require(suggestion.getDisplayText().contains("No interpolation or smoothing is applied"),
                "Unsupported-point preservation was not explicit");
        require(suggestion.getDisplayText().contains("Confidence: MEDIUM"),
                "Four consistent events should create medium confidence");
    }

    private static void fiveTightEventsCreateHighConfidence() {
        List<EventSummary> events = new ArrayList<EventSummary>();
        events.add(event(1, 1990.0, 0.20, false));
        events.add(event(2, 2000.0, 0.21, false));
        events.add(event(3, 2010.0, 0.22, false));
        events.add(event(4, 2020.0, 0.23, false));
        events.add(event(5, 1980.0, 0.24, false));
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(snapshot(), events);
        require(suggestion.isAvailable(), "Five tight events were not eligible");
        require(suggestion.getDisplayText().contains("Confidence: HIGH"),
                "Five tight events did not create high confidence");
        require(suggestion.getDisplayText().contains("high-confidence RPM points: 1"),
                "High-confidence point count was not reported");
    }

    private static void repeatedStabsRemainDiagnosticOnly() {
        List<EventSummary> events = new ArrayList<EventSummary>();
        events.add(event(1, 1500.0, 0.20, true));
        events.add(event(2, 1500.0, 0.20, false));
        events.add(event(3, 1500.0, 0.21, false));
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(snapshot(), events);
        require(!suggestion.isAvailable(), "Repeated stab was used to satisfy minimum evidence");
        require(suggestion.getDisplayText().contains("multiple detector bursts / repeated stab x1"),
                "Repeated-stab rejection was not listed per RPM point");
    }

    private static void highSpreadEvidenceMustBeWithheld() {
        List<EventSummary> events = new ArrayList<EventSummary>();
        events.add(event(1, 2000.0, 0.10, false));
        events.add(event(2, 2000.0, 0.25, false));
        events.add(event(3, 2000.0, 0.45, false));
        events.add(event(4, 2000.0, 0.65, false));
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(snapshot(), events);
        require(!suggestion.isAvailable(), "High-spread evidence created a paste-ready proposal");
        require(suggestion.getDisplayText().contains("Confidence: LOW"),
                "High-spread evidence was not marked low confidence");
        require(suggestion.getDisplayText().contains("withheld because catch-up range is too wide"),
                "High-spread rejection reason was not explicit");
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "Per-RPM test",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0,
                new double[0], new double[0],
                false, true, "Wall Wetting", false, true,
                false, false,
                new double[0][0], new double[0][0],
                new double[]{1000.0, 1500.0, 2000.0, 2500.0},
                new double[]{10.0},
                new double[][]{{50.0, 50.0, 50.0, 50.0}},
                new double[]{1000.0, 1500.0, 2000.0, 2500.0},
                new double[]{0.30, 0.30, 0.30, 0.30});
    }

    private static EventSummary event(int index, double rpm, double catchupSeconds, boolean repeatedStab) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        long base = index * 10000000000L;
        samples.add(sample(base, rpm, 20.0, 50.0, 80.0, true, true, 0.0));
        samples.add(sample(base + 20000000L, rpm, 20.0, 50.0, 80.0, true, true, 1.0));
        samples.add(sample(base + 40000000L, rpm, 20.0, 50.0, 80.0, true, true, 1.0));
        samples.add(sample(base + 100000000L, rpm, 20.0, 60.0, 80.0, true, false, 1.0));
        if (repeatedStab) {
            samples.add(sample(base + 250000000L, rpm, 20.0, 62.0, 80.0, true, true, 2.0));
            samples.add(sample(base + 290000000L, rpm, 20.0, 64.0, 80.0, true, true, 2.0));
        }
        long catchNano = base + 40000000L + Math.round(catchupSeconds * 1000000000.0);
        samples.add(sample(catchNano, rpm, 20.0, 78.0, 80.0, true, false, repeatedStab ? 2.0 : 1.0));
        samples.add(sample(catchNano + 50000000L, rpm, 20.0, 80.0, 80.0, false, false,
                repeatedStab ? 2.0 : 1.0));
        return new EventSummary(index, true, "MAP Predict event", "synthetic", samples, true);
    }

    private static LiveSample sample(long nano,
                                     double rpm,
                                     double tps,
                                     double map,
                                     double fallback,
                                     boolean prediction,
                                     boolean trigger,
                                     double resetCounter) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(tps));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(fallback));
        values.put(ChannelRole.EFFECTIVE_MAP, Double.valueOf(fallback));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(prediction ? 1.0 : 0.0));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(resetCounter));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, Double.valueOf(trigger ? 1.0 : 0.0));
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, Double.valueOf(trigger ? 2.0 : 0.0));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(1.0));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(1.0));
        values.put(ChannelRole.IGNITION_ON, Double.valueOf(1.0));
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, Double.valueOf(1.0));
        return new LiveSample(nano, nano / 1000000000.0, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
