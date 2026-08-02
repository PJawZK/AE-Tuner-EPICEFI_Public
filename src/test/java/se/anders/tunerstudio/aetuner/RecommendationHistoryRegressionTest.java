package se.anders.tunerstudio.aetuner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Deterministic checks for session-only recommendation transitions. */
public final class RecommendationHistoryRegressionTest {
    private RecommendationHistoryRegressionTest() { }

    public static void main(String[] args) {
        repeatedRefreshMustNotDuplicateEntries();
        displayedRecommendationMustBeAuthoritative();
        channelResolutionChangeMustCreateEntry();
        runningTriggerFaultAndClearMustCreateTransitions();
        resetMustClearTemporaryHistory();
        System.out.println("RecommendationHistoryRegressionTest passed");
    }

    private static void repeatedRefreshMustNotDuplicateEntries() {
        RecommendationHistory history = new RecommendationHistory();
        SessionMonitor monitor = new SessionMonitor();
        List<EventSummary> events = new ArrayList<EventSummary>();
        EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
        SessionReview review = SessionReview.build(events, monitor.snapshot());

        require(history.observe(review, events, monitor.snapshot(), selected, 1000L),
                "Initial recommendation must be recorded");
        require(!history.observe(review, events, monitor.snapshot(), selected, 1500L),
                "Identical UI refresh must not create a duplicate entry");
        require(history.size() == 1, "Expected one history entry after duplicate refresh");
    }

    private static void displayedRecommendationMustBeAuthoritative() {
        RecommendationHistory history = new RecommendationHistory();
        SessionMonitor monitor = new SessionMonitor();
        List<EventSummary> events = new ArrayList<EventSummary>();
        SessionReview review = SessionReview.build(events, monitor.snapshot());
        history.observe("Resolve missing MAP Predict channels", review, events,
                monitor.snapshot(), new EnumMap<ChannelRole, String>(ChannelRole.class), 1750L);
        require(history.toDisplayText().contains("CRITICAL_CHANNEL_RESOLUTION"),
                "Displayed missing-channel action must determine the history type");
        require(history.toDisplayText().contains("Resolve missing MAP Predict channels"),
                "History must retain the exact recommendation shown on the card");
        require(history.latestBadgeText().contains("WARNING / HIGH"),
                "Card badge must expose severity and confidence for the displayed action");
    }

    private static void channelResolutionChangeMustCreateEntry() {
        RecommendationHistory history = new RecommendationHistory();
        SessionMonitor monitor = new SessionMonitor();
        List<EventSummary> events = new ArrayList<EventSummary>();
        EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
        SessionReview review = SessionReview.build(events, monitor.snapshot());

        history.observe(review, events, monitor.snapshot(), selected, 2000L);
        selected.put(ChannelRole.ENGINE_RUNNING, "ready");
        require(history.observe(review, events, monitor.snapshot(), selected, 2500L),
                "Critical channel-resolution transition must be recorded");
        require(history.size() == 2, "Expected a second entry for channel-resolution change");
        require(history.toDisplayText().contains("running=ready"),
                "History must expose the selected running channel");
    }

    private static void runningTriggerFaultAndClearMustCreateTransitions() {
        RecommendationHistory history = new RecommendationHistory();
        List<EventSummary> events = new ArrayList<EventSummary>();
        EnumMap<ChannelRole, String> selected = exactCriticalChannels();

        SessionMonitor cleanMonitor = new SessionMonitor();
        SessionReview clean = SessionReview.build(events, cleanMonitor.snapshot());
        history.observe(clean, events, cleanMonitor.snapshot(), selected, 3000L);

        SessionMonitor faultMonitor = new SessionMonitor();
        faultMonitor.addSample(runningSample(1L, 1.0, 900.0, 0.0, 0.0));
        faultMonitor.addSample(runningSample(2L, 1.1, 880.0, 1.0, 2.0));
        SessionReview fault = SessionReview.build(events, faultMonitor.snapshot());
        require(history.observe(fault, events, faultMonitor.snapshot(), selected, 3500L),
                "Running trigger fault must create a transition");
        require(history.toDisplayText().contains("RUNNING_TRIGGER_SYNC"),
                "Trigger transition type must be visible");
        require(history.toDisplayText().contains("CRITICAL / HIGH"),
                "Trigger transition severity and confidence must be visible");

        SessionMonitor clearedMonitor = new SessionMonitor();
        clearedMonitor.addSample(runningSample(3L, 2.0, 900.0, 0.0, 0.0));
        SessionReview cleared = SessionReview.build(events, clearedMonitor.snapshot());
        require(history.observe(cleared, events, clearedMonitor.snapshot(), selected, 4000L),
                "Clearing a prior critical concern must create a transition");
        require(history.size() == 3, "Expected initial, fault and cleared entries");
    }

    private static void resetMustClearTemporaryHistory() {
        RecommendationHistory history = new RecommendationHistory();
        SessionMonitor monitor = new SessionMonitor();
        List<EventSummary> events = new ArrayList<EventSummary>();
        history.observe(SessionReview.build(events, monitor.snapshot()), events,
                monitor.snapshot(), exactCriticalChannels(), 5000L);
        require(history.size() == 1, "Precondition failed");
        history.reset();
        require(history.size() == 0, "Reset session must clear temporary history");
        require(history.toDisplayText().contains("No recommendation transition recorded yet"),
                "Empty history text must be explicit after reset");
    }

    private static EnumMap<ChannelRole, String> exactCriticalChannels() {
        EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
        selected.put(ChannelRole.ENGINE_RUNNING, "ready");
        selected.put(ChannelRole.ENGINE_CRANKING, "crank");
        selected.put(ChannelRole.IGNITION_ON, "ignitionOn");
        selected.put(ChannelRole.MAIN_RELAY_HAS_IGN, "Main relay: Has IGN voltage");
        selected.put(ChannelRole.TRIGGER_ERROR, "Error: Trigger");
        selected.put(ChannelRole.TRIGGER_ERROR_COUNT, "totalTriggerErrorCounter");
        selected.put(ChannelRole.IGN_CUT_CODE, "sparkCutReason");
        selected.put(ChannelRole.FUEL_CUT_CODE, "fuelCutReason");
        selected.put(ChannelRole.IGN_OVERDWELL, "overDwellNotScheduledCounter");
        selected.put(ChannelRole.IGN_OVERCHARGE_WARNINGS, "dwellOverChargeCounter");
        selected.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, "dwellUnderChargeCounter");
        selected.put(ChannelRole.IGN_SPARK_OUT_OF_ORDER, "sparkOutOfOrderCounter");
        return selected;
    }

    private static LiveSample runningSample(long nanoTime,
                                            double seconds,
                                            double rpm,
                                            double triggerError,
                                            double triggerCounter) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, 2.0);
        values.put(ChannelRole.MAP, 50.0);
        values.put(ChannelRole.BATTERY, 13.8);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.IGNITION_ON, 1.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, 1.0);
        values.put(ChannelRole.TRIGGER_ERROR, triggerError);
        values.put(ChannelRole.TRIGGER_ERROR_COUNT, triggerCounter);
        values.put(ChannelRole.IGNITION_FAULT, 0.0);
        values.put(ChannelRole.INJECTOR_FAULT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.IGN_CUT_CODE, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.FUEL_CUT_CODE, 0.0);
        values.put(ChannelRole.STOP_ENGINE_CODE, 0.0);
        values.put(ChannelRole.IGN_OVERDWELL, 0.0);
        values.put(ChannelRole.IGN_OVERCHARGE_WARNINGS, 0.0);
        values.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, 0.0);
        values.put(ChannelRole.IGN_SPARK_OUT_OF_ORDER, 0.0);
        return new LiveSample(nanoTime, seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
