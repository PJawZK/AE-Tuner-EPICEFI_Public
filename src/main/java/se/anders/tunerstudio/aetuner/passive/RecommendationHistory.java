package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Temporary, in-memory timeline of meaningful recommendation transitions.
 *
 * Nothing in this class is persisted and it does not write to the ECU. The
 * timeline changes only when the displayed recommendation state or critical
 * channel resolution changes; repeated UI refreshes do not create duplicates.
 */
final class RecommendationHistory {
    private static final int MAX_ENTRIES = 100;

    private final List<Entry> entries = new ArrayList<Entry>();
    private String lastSignature;

    boolean observe(SessionReview review,
                    List<TransientEvent> events,
                    SessionMonitor.Snapshot snapshot,
                    Map<ChannelRole, String> selectedChannels,
                    long timestampMillis) {
        return observe(review == null ? null : review.recommendedNextStep(),
                review, events, snapshot, selectedChannels, timestampMillis);
    }

    boolean observe(String displayedRecommendation,
                    SessionReview review,
                    List<TransientEvent> events,
                    SessionMonitor.Snapshot snapshot,
                    Map<ChannelRole, String> selectedChannels,
                    long timestampMillis) {
        if (review == null) {
            return false;
        }
        State state = State.build(displayedRecommendation, review, events, snapshot, selectedChannels);
        if (state.signature.equals(lastSignature)) {
            return false;
        }

        entries.add(new Entry(timestampMillis,
                state.type,
                state.text,
                state.severity,
                state.confidence,
                state.eventIds,
                state.supportSummary,
                state.channelSummary));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        lastSignature = state.signature;
        return true;
    }

    void reset() {
        entries.clear();
        lastSignature = null;
    }

    int size() {
        return entries.size();
    }

    Entry latest() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    String latestBadgeText() {
        Entry latest = latest();
        if (latest == null) {
            return "";
        }
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
        return latest.severity + " / " + latest.confidence + " • "
                + time.format(new Date(latest.timestampMillis));
    }

    List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    String toDisplayText() {
        StringBuilder text = new StringBuilder();
        text.append("Session Guidance\n")
                .append("Temporary session memory only — cleared by Reset session or plugin restart.\n")
                .append("Only meaningful recommendation or critical-channel transitions are recorded.\n\n");
        if (entries.isEmpty()) {
            text.append("No recommendation transition recorded yet.");
            return text.toString();
        }

        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            text.append(i + 1).append(". ")
                    .append(time.format(new Date(entry.timestampMillis)))
                    .append("  [").append(entry.severity).append(" / ")
                    .append(entry.confidence).append("]\n")
                    .append("   ").append(entry.text).append("\n")
                    .append("   Type: ").append(entry.type).append("\n")
                    .append("   Evidence: ").append(entry.supportSummary).append("\n")
                    .append("   Channels: ").append(entry.channelSummary).append("\n")
                    .append("   Event IDs: ").append(entry.eventIds.isEmpty() ? "none" : entry.eventIds)
                    .append("\n");
            if (i + 1 < entries.size()) {
                text.append("\n");
            }
        }
        return text.toString();
    }

    static final class Entry {
        final long timestampMillis;
        final String type;
        final String text;
        final Severity severity;
        final Confidence confidence;
        final List<Integer> eventIds;
        final String supportSummary;
        final String channelSummary;

        Entry(long timestampMillis,
              String type,
              String text,
              Severity severity,
              Confidence confidence,
              List<Integer> eventIds,
              String supportSummary,
              String channelSummary) {
            this.timestampMillis = timestampMillis;
            this.type = type;
            this.text = text;
            this.severity = severity;
            this.confidence = confidence;
            this.eventIds = Collections.unmodifiableList(new ArrayList<Integer>(eventIds));
            this.supportSummary = supportSummary;
            this.channelSummary = channelSummary;
        }
    }

    enum Severity { CRITICAL, WARNING, REVIEW, INFO, READY }
    enum Confidence { HIGH, MEDIUM, LOW }

    private static final class State {
        final String type;
        final String text;
        final Severity severity;
        final Confidence confidence;
        final List<Integer> eventIds;
        final String supportSummary;
        final String channelSummary;
        final String signature;

        State(String type,
              String text,
              Severity severity,
              Confidence confidence,
              List<Integer> eventIds,
              String supportSummary,
              String channelSummary) {
            this.type = type;
            this.text = text;
            this.severity = severity;
            this.confidence = confidence;
            this.eventIds = eventIds;
            this.supportSummary = supportSummary;
            this.channelSummary = channelSummary;
            this.signature = type + "|" + text + "|" + severity + "|" + confidence + "|" + channelSummary;
        }

        static State build(String displayedRecommendation,
                           SessionReview review,
                           List<TransientEvent> events,
                           SessionMonitor.Snapshot snapshot,
                           Map<ChannelRole, String> selectedChannels) {
            String text = displayedRecommendation == null || displayedRecommendation.trim().length() == 0
                    ? review.recommendedNextStep() : displayedRecommendation.trim();
            String type;
            Severity severity;
            Confidence confidence;
            if (text.startsWith("Resolve missing MAP Predict channels")) {
                type = "CRITICAL_CHANNEL_RESOLUTION";
                severity = Severity.WARNING;
                confidence = Confidence.HIGH;
            } else if (text.startsWith("Use the TPS cycle-AE workflow")) {
                type = "WORKFLOW_SELECTION";
                severity = Severity.INFO;
                confidence = Confidence.HIGH;
            } else if (review.triggerSyncNeedsReview()) {
                type = "RUNNING_TRIGGER_SYNC";
                severity = Severity.CRITICAL;
                confidence = snapshot != null && snapshot.triggerErrorCountSamples > 0L
                        ? Confidence.HIGH : Confidence.MEDIUM;
            } else if (review.sessionFaultNeedsReview()) {
                type = "RUNNING_FAULT_OR_CUT";
                severity = Severity.CRITICAL;
                confidence = Confidence.HIGH;
            } else if (review.fullLoadNeedsReview()) {
                type = "FULL_LOAD_SAFETY";
                severity = Severity.WARNING;
                confidence = Confidence.MEDIUM;
            } else if (review.lowRpmNeedsReview()) {
                type = "LOW_RPM_MAP_PREDICT";
                severity = Severity.REVIEW;
                confidence = Confidence.MEDIUM;
            } else if (events == null || countPredictionEvents(events) < 4) {
                type = "COLLECT_EVIDENCE";
                severity = Severity.INFO;
                confidence = Confidence.LOW;
            } else {
                type = "REVIEW_DRAFTS";
                severity = Severity.READY;
                confidence = Confidence.MEDIUM;
            }

            List<Integer> ids = latestAcceptedEventIds(events, 6);
            String support = buildSupportSummary(review, events, snapshot);
            String channels = buildChannelSummary(selectedChannels);
            return new State(type, text, severity, confidence, ids, support, channels);
        }

        private static int countPredictionEvents(List<TransientEvent> events) {
            int count = 0;
            if (events != null) {
                for (TransientEvent event : events) {
                    if (event != null && event.hasMapPrediction()) {
                        count++;
                    }
                }
            }
            return count;
        }

        private static List<Integer> latestAcceptedEventIds(List<TransientEvent> events, int limit) {
            List<Integer> ids = new ArrayList<Integer>();
            if (events == null) {
                return ids;
            }
            for (int i = events.size() - 1; i >= 0 && ids.size() < limit; i--) {
                TransientEvent event = events.get(i);
                if (event != null && event.isAccepted()) {
                    ids.add(0, Integer.valueOf(event.getIndex()));
                }
            }
            return ids;
        }

        private static String buildSupportSummary(SessionReview review,
                                                  List<TransientEvent> events,
                                                  SessionMonitor.Snapshot snapshot) {
            int eventCount = events == null ? 0 : events.size();
            if (snapshot == null) {
                return "events " + eventCount + "; session monitor unavailable";
            }
            return "events " + eventCount
                    + "; " + review.contributionCardText()
                    + "; " + review.lowRpmCardText()
                    + "; running samples " + snapshot.runningSamples
                    + "; trigger counter +" + format(snapshot.triggerErrorCountDelta)
                    + "; full-load segments " + snapshot.segments;
        }

        private static String buildChannelSummary(Map<ChannelRole, String> selectedChannels) {
            EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
            if (selectedChannels != null) {
                selected.putAll(selectedChannels);
            }
            ChannelRole[] critical = new ChannelRole[]{
                    ChannelRole.ENGINE_RUNNING,
                    ChannelRole.ENGINE_CRANKING,
                    ChannelRole.IGNITION_ON,
                    ChannelRole.MAIN_RELAY_HAS_IGN,
                    ChannelRole.TRIGGER_ERROR,
                    ChannelRole.TRIGGER_ERROR_COUNT,
                    ChannelRole.IGN_CUT_CODE,
                    ChannelRole.FUEL_CUT_CODE,
                    ChannelRole.IGN_OVERDWELL,
                    ChannelRole.IGN_OVERCHARGE_WARNINGS,
                    ChannelRole.IGN_UNDERCHARGE_WARNINGS,
                    ChannelRole.IGN_SPARK_OUT_OF_ORDER
            };
            int resolved = 0;
            for (ChannelRole role : critical) {
                if (selected.containsKey(role)) {
                    resolved++;
                }
            }
            return resolved + "/" + critical.length + " critical roles resolved"
                    + "; running=" + selectedName(selected, ChannelRole.ENGINE_RUNNING)
                    + "; trigger=" + selectedName(selected, ChannelRole.TRIGGER_ERROR)
                    + "; trigger counter=" + selectedName(selected, ChannelRole.TRIGGER_ERROR_COUNT)
                    + "; ignition cut=" + selectedName(selected, ChannelRole.IGN_CUT_CODE)
                    + "; fuel cut=" + selectedName(selected, ChannelRole.FUEL_CUT_CODE);
        }

        private static String selectedName(Map<ChannelRole, String> selected, ChannelRole role) {
            String value = selected.get(role);
            return value == null ? "unresolved" : value;
        }

        private static String format(double value) {
            if (!Double.isFinite(value)) {
                return "n/a";
            }
            if (Math.abs(value - Math.rint(value)) < 0.000001) {
                return Long.toString(Math.round(value));
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }
}
