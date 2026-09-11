package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.model.TransientEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Pins review/copy/write reuse of one immutable TPS AE table model. */
public final class AeTableSuggestionMemoizationRegressionTest {
    private AeTableSuggestionMemoizationRegressionTest() { }

    public static void main(String[] args) {
        sameWorkingTuneAndEventRevisionReusesSuggestion();
        System.out.println("AeTableSuggestionMemoizationRegressionTest passed");
    }

    private static void sameWorkingTuneAndEventRevisionReusesSuggestion() {
        AeTableSuggestion.clearMemoizedForTest();
        AeProjectSnapshot firstTune = snapshot("memo-1");
        List<TransientEvent> events = new ArrayList<TransientEvent>();

        AeTableSuggestion first = AeTableSuggestion.build(firstTune, events);
        AeTableSuggestion second = AeTableSuggestion.build(firstTune,
                new ArrayList<TransientEvent>(events));
        require(first == second,
                "same Working Tune/event revision rebuilt immutable TPS AE suggestion");
        require(AeTableSuggestion.uncachedBuildCountForTest() == 1L,
                "same evidence revision performed more than one full TPS AE build");

        TransientEvent event = event(1, 1.0);
        events.add(event);
        AeTableSuggestion afterEvent = AeTableSuggestion.build(firstTune, events);
        require(afterEvent != first,
                "new completed TPS AE event did not invalidate suggestion cache");
        require(AeTableSuggestion.uncachedBuildCountForTest() == 2L,
                "event revision did not cause exactly one new TPS AE build");

        AeTableSuggestion copiedList = AeTableSuggestion.build(firstTune,
                new ArrayList<TransientEvent>(events));
        require(copiedList == afterEvent,
                "eventsSnapshot-style list copy defeated TPS AE memoization");
        require(AeTableSuggestion.uncachedBuildCountForTest() == 2L,
                "copied list with same last event rebuilt TPS AE suggestion");

        AeProjectSnapshot secondTune = snapshot("memo-2");
        AeTableSuggestion changedTune = AeTableSuggestion.build(secondTune, events);
        require(changedTune != afterEvent,
                "different Working Tune identity reused stale TPS AE suggestion");
        require(AeTableSuggestion.uncachedBuildCountForTest() == 3L,
                "Working Tune identity change did not rebuild exactly once");
    }

    private static AeProjectSnapshot snapshot(String configuration) {
        return new AeProjectSnapshot(
                configuration,
                new double[]{2.0, 4.0, 6.0, 10.0, 12.0},
                new double[]{10.0, 20.0, 30.0},
                new double[][]{
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50}
                },
                new double[]{1000.0, 4000.0}, new double[]{1.0, 1.5},
                0.0, 0.0, new double[0], new double[0],
                true, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static TransientEvent event(int index, double seconds) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(seconds, 8.0, 1.00));
        samples.add(sample(seconds + 0.10, 20.0, 1.05));
        return new TransientEvent(index, true,
                "Guided TPS AE event", "memoization fixture", samples, false);
    }

    private static LiveSample sample(double seconds, double tps, double lambda) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2500.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, 70.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        values.put(ChannelRole.TPS_TO, 20.0);
        values.put(ChannelRole.AE_ADD_MS, 0.8);
        values.put(ChannelRole.WALL_WETTING_PW, 0.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, 50.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
