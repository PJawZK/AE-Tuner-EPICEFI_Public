package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Road-friendly post-capture grouping for valid Guided Blend Duration events.
 *
 * Capture validity is intentionally separate from comparability. A clean event
 * is retained even if no existing group matches it. Only events assigned to the
 * same group are later combined for measurement repeatability review.
 *
 * Manual gear is authoritative operator metadata and never requires ECU gear
 * confirmation. Automatic gear uses the short latched ECU detection as an
 * additional comparability dimension; Ignore gear does not use it.
 */
final class BlendDurationComparabilityGroups {
    static final double RPM_LIMIT = 250.0;
    static final double MAP_LIMIT = 8.0;
    static final double TPS_STEP_LIMIT = 5.0;
    static final double GAP_MIN_LIMIT = 4.0;
    static final double GAP_FRACTION_LIMIT = 0.30;
    private static final double WARN_FRACTION = 0.80;

    static final class Assignment {
        final String groupId;
        final int groupCount;
        final boolean nearBoundary;
        final String description;

        Assignment(String groupId, int groupCount,
                   boolean nearBoundary, String description) {
            this.groupId = groupId;
            this.groupCount = groupCount;
            this.nearBoundary = nearBoundary;
            this.description = description;
        }
    }

    private final List<Group> groups = new ArrayList<Group>();

    synchronized Assignment assign(BlendDurationAttempt attempt) {
        Group best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Group group : groups) {
            Match match = group.match(attempt);
            if (match.accepted && match.score < bestScore) {
                best = group;
                bestScore = match.score;
            }
        }
        if (best == null) {
            best = new Group(groupName(groups.size()));
            groups.add(best);
        }
        Match before = best.count == 0 ? Match.accept(0.0, false) : best.match(attempt);
        best.add(attempt);

        Group leading = bestGroup();
        boolean advancesLeading = leading != null && best.id.equals(leading.id);
        String progress = advancesLeading
                ? " | VALID — ADVANCES LEADING GROUP " + best.id
                    + " (" + best.count + " comparable event"
                    + (best.count == 1 ? "" : "s") + ")"
                : " | VALID — RETAINED IN DIFFERENT GROUP " + best.id
                    + " (" + best.count + " event"
                    + (best.count == 1 ? "" : "s") + "); leading group "
                    + (leading == null ? "n/a" : leading.id)
                    + " remains at " + (leading == null ? 0 : leading.count)
                    + " comparable event"
                    + (leading != null && leading.count == 1 ? "" : "s");
        return new Assignment(best.id, best.count, before.nearBoundary,
                best.description(attempt, before.nearBoundary) + progress);
    }

    synchronized void rebuild(List<BlendDurationAttempt> attempts) {
        groups.clear();
        if (attempts == null) return;
        for (BlendDurationAttempt attempt : attempts) {
            assign(attempt);
        }
    }

    synchronized String bestGroupId() {
        Group group = bestGroup();
        return group == null ? "" : group.id;
    }

    synchronized int bestGroupCount() {
        Group group = bestGroup();
        return group == null ? 0 : group.count;
    }

    synchronized List<BlendDurationAttempt> bestAttempts() {
        Group group = bestGroup();
        return group == null
                ? Collections.<BlendDurationAttempt>emptyList()
                : new ArrayList<BlendDurationAttempt>(group.attempts);
    }

    synchronized int groupCount() {
        return groups.size();
    }

    synchronized String summary() {
        if (groups.isEmpty()) return "No valid event groups yet.";
        List<Group> ordered = new ArrayList<Group>(groups);
        Collections.sort(ordered, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                int byCount = Integer.compare(b.count, a.count);
                return byCount != 0 ? byCount : a.id.compareTo(b.id);
            }
        });
        StringBuilder out = new StringBuilder();
        for (Group group : ordered) {
            if (out.length() > 0) out.append(" | ");
            out.append("Group ").append(group.id)
                    .append(": ").append(group.count)
                    .append(" event").append(group.count == 1 ? "" : "s")
                    .append(" @ ").append(f0(group.meanRpm)).append(" RPM / ")
                    .append(f1(group.meanMap)).append(" kPa / +")
                    .append(f1(group.meanStep)).append(" TPS / ")
                    .append(group.gearSummary());
        }
        return out.toString();
    }

    private Group bestGroup() {
        Group best = null;
        for (Group group : groups) {
            if (best == null || group.count > best.count) {
                best = group;
            }
        }
        return best;
    }

    private static String groupName(int index) {
        if (index < 26) {
            return Character.toString((char) ('A' + index));
        }
        return "G" + (index + 1);
    }

    private static boolean trendCompatible(String a, String b) {
        if (a == null || b == null) return true;
        if ("STABLE".equals(a) || "STABLE".equals(b)) return true;
        return a.equals(b);
    }

    private static final class Group {
        final String id;
        final List<BlendDurationAttempt> attempts =
                new ArrayList<BlendDurationAttempt>();
        int count;
        double meanRpm;
        double meanMap;
        double meanStep;
        double meanGap;
        String trend = "STABLE";
        boolean automaticGear;
        int manualGear;
        int comparisonGear;

        Group(String id) {
            this.id = id;
        }

        Match match(BlendDurationAttempt attempt) {
            if (count == 0) return Match.accept(0.0, false);
            if (!finite(attempt)) return Match.reject();
            if (!gearCompatible(attempt)) return Match.reject();
            double rpmDiff = Math.abs(attempt.baseRpm - meanRpm);
            double mapDiff = Math.abs(attempt.baseMap - meanMap);
            double stepDiff = Math.abs(attempt.tpsStep - meanStep);
            double gapLimit = Math.max(GAP_MIN_LIMIT,
                    Math.abs(meanGap) * GAP_FRACTION_LIMIT);
            double gapDiff = Math.abs(attempt.gap - meanGap);
            if (rpmDiff > RPM_LIMIT || mapDiff > MAP_LIMIT
                    || stepDiff > TPS_STEP_LIMIT || gapDiff > gapLimit
                    || !trendCompatible(trend, attempt.trend)) {
                return Match.reject();
            }
            double score = rpmDiff / RPM_LIMIT
                    + mapDiff / MAP_LIMIT
                    + stepDiff / TPS_STEP_LIMIT
                    + gapDiff / gapLimit;
            boolean near = rpmDiff >= RPM_LIMIT * WARN_FRACTION
                    || mapDiff >= MAP_LIMIT * WARN_FRACTION
                    || stepDiff >= TPS_STEP_LIMIT * WARN_FRACTION
                    || gapDiff >= gapLimit * WARN_FRACTION;
            return Match.accept(score, near);
        }

        private boolean gearCompatible(BlendDurationAttempt attempt) {
            if (attempt.settings == null) return true;
            if (automaticGear != attempt.settings.automaticGear) return false;
            if (manualGear != attempt.settings.manualGear) return false;
            if (!automaticGear) return true;
            int next = attempt.comparisonGear();
            if (comparisonGear == 0 && next == 0) return true;
            return comparisonGear > 0 && next > 0 && comparisonGear == next;
        }

        void add(BlendDurationAttempt attempt) {
            int next = count + 1;
            meanRpm = weighted(meanRpm, attempt.baseRpm, count, next);
            meanMap = weighted(meanMap, attempt.baseMap, count, next);
            meanStep = weighted(meanStep, attempt.tpsStep, count, next);
            meanGap = weighted(meanGap, attempt.gap, count, next);
            if (count == 0 || "STABLE".equals(trend)) {
                trend = attempt.trend;
            }
            if (count == 0 && attempt.settings != null) {
                automaticGear = attempt.settings.automaticGear;
                manualGear = attempt.settings.manualGear;
                comparisonGear = attempt.comparisonGear();
            }
            count = next;
            attempts.add(attempt);
        }

        String description(BlendDurationAttempt attempt,
                           boolean nearBoundary) {
            StringBuilder out = new StringBuilder();
            out.append("stored in adaptive group ").append(id)
                    .append(" (group now ").append(count).append(" event")
                    .append(count == 1 ? "" : "s").append(")")
                    .append(" | baseline ").append(f0(attempt.baseRpm))
                    .append(" RPM / ").append(f1(attempt.baseMap)).append(" kPa")
                    .append(" | TPS step +").append(f1(attempt.tpsStep))
                    .append(" | final target-anchor gap ").append(f1(attempt.gap)).append(" kPa")
                    .append(" | ").append(attempt.gearText());
            if (nearBoundary) {
                out.append(" | near group boundary; retained but not discarded");
            }
            return out.toString();
        }

        String gearSummary() {
            if (automaticGear) {
                return comparisonGear > 0
                        ? "detected gear " + comparisonGear
                        : "detected gear unknown";
            }
            if (manualGear > 0) return "manual " + manualGear;
            return "gear ignored";
        }

        private static double weighted(double current, double next,
                                       int oldCount, int newCount) {
            return oldCount == 0 ? next
                    : (current * oldCount + next) / newCount;
        }
    }

    private static final class Match {
        final boolean accepted;
        final double score;
        final boolean nearBoundary;

        private Match(boolean accepted, double score, boolean nearBoundary) {
            this.accepted = accepted;
            this.score = score;
            this.nearBoundary = nearBoundary;
        }

        static Match accept(double score, boolean nearBoundary) {
            return new Match(true, score, nearBoundary);
        }

        static Match reject() {
            return new Match(false, Double.POSITIVE_INFINITY, false);
        }
    }

    private static boolean finite(BlendDurationAttempt attempt) {
        return attempt != null
                && Double.isFinite(attempt.baseRpm)
                && Double.isFinite(attempt.baseMap)
                && Double.isFinite(attempt.tpsStep)
                && Double.isFinite(attempt.gap);
    }

    private static String f0(double value) {
        return String.format(Locale.US, "%.0f", value);
    }

    private static String f1(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
