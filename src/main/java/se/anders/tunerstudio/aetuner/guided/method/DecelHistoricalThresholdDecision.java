package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only decision layer over validated historical Decel separation evidence.
 *
 * The result is derived from the independent physical event populations rather
 * than pair-multiplying them. The largest observed Normal Correction defines
 * the noise-side boundary; the lowest credible Decel Release defines the
 * release-side boundary. A target is placed conservatively inside that empty
 * classification gap and quantized coarsely to avoid false precision.
 */
public final class DecelHistoricalThresholdDecision {
    private static final double RESULT_QUANTUM = 0.05;
    private static final double BASE_GAP_POSITION = 0.60;
    private static final double ISOLATED_WEAK_RELEASE_GAP_POSITION = 0.75;
    private static final double ISOLATED_WEAK_RELEASE_RATIO = 2.0;
    private static final int MIN_NORMAL_FOR_WEAK_RELEASE_RULE = 5;
    private static final int MIN_RELEASE_FOR_WEAK_RELEASE_RULE = 8;
    private static final int STRONG_NORMAL_EVENTS = 10;
    private static final int STRONG_RELEASE_EVENTS = 10;

    public enum Outcome {
        KEEP,
        PROPOSE,
        WITHHOLD
    }

    public static final class Compatibility {
        public final boolean actionable;
        public final String status;

        Compatibility(boolean actionable, String status) {
            this.actionable = actionable;
            this.status = status == null ? "" : status;
        }
    }

    public static final class BinDecision {
        public final double rpm;
        public final double current;
        public final double safeLow;
        public final double safeHigh;
        public final int normalEvents;
        public final int releaseEvents;
        public final int ambiguousEvents;
        public final Outcome outcome;
        /** Historical API name retained; for qualified bins this is the classification-boundary ECU target. */
        public final double preview;
        public final String reason;

        BinDecision(double rpm, double current, double safeLow, double safeHigh,
                    int normalEvents, int releaseEvents, int ambiguousEvents,
                    Outcome outcome, double preview, String reason) {
            this.rpm = rpm;
            this.current = current;
            this.safeLow = safeLow;
            this.safeHigh = safeHigh;
            this.normalEvents = normalEvents;
            this.releaseEvents = releaseEvents;
            this.ambiguousEvents = ambiguousEvents;
            this.outcome = outcome;
            this.preview = preview;
            this.reason = reason == null ? "" : reason;
        }

        public double ecuResultValue() {
            if (outcome == Outcome.KEEP || outcome == Outcome.PROPOSE) return preview;
            return Double.NaN;
        }
    }

    public static final class Result {
        public final Compatibility compatibility;
        public final List<BinDecision> bins;
        public final int keepCount;
        public final int proposeCount;
        public final int withholdCount;
        public final String reportText;

        Result(Compatibility compatibility, List<BinDecision> bins,
               int keepCount, int proposeCount, int withholdCount,
               String reportText) {
            this.compatibility = compatibility;
            this.bins = Collections.unmodifiableList(new ArrayList<BinDecision>(bins));
            this.keepCount = keepCount;
            this.proposeCount = proposeCount;
            this.withholdCount = withholdCount;
            this.reportText = reportText == null ? "" : reportText;
        }
    }

    private static final class ClassificationBoundaryTarget {
        final double normalCeiling;
        final double releaseFloor;
        final int ignoredWeakReleases;
        final double gapPosition;
        final double rawTarget;
        final double controllerTarget;

        ClassificationBoundaryTarget(double normalCeiling, double releaseFloor,
                                     int ignoredWeakReleases, double gapPosition,
                                     double rawTarget, double controllerTarget) {
            this.normalCeiling = normalCeiling;
            this.releaseFloor = releaseFloor;
            this.ignoredWeakReleases = ignoredWeakReleases;
            this.gapPosition = gapPosition;
            this.rawTarget = rawTarget;
            this.controllerTarget = controllerTarget;
        }
    }

    private DecelHistoricalThresholdDecision() { }

    public static Result evaluate(AeProjectSnapshot snapshot,
                                  DecelHistoricalEvidenceAnalyzer.Result analysis,
                                  MlgEmbeddedTuneMetadata embedded) {
        if (snapshot == null || !snapshot.hasDecelThresholdCurve()) {
            return unavailable("Working Tune Decel threshold curve is unavailable.");
        }

        double[] rpmBins = snapshot.getDecelThresholdRpmBins();
        double[] current = snapshot.getDecelThresholdValues();
        if (rpmBins.length == 0 || rpmBins.length != current.length) {
            return unavailable("Working Tune Decel threshold curve is invalid.");
        }

        Compatibility compatibility = compatibility(snapshot, embedded);
        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES);
        double scale = definition == null ? 0.001 : definition.getScale();
        double minimum = definition == null ? 0.0 : definition.getMinimum();
        double maximum = definition == null ? 60.0 : definition.getMaximum();

        List<BinDecision> decisions = new ArrayList<BinDecision>();
        int keep = 0;
        int propose = 0;
        int withhold = 0;

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < rpmBins.length; i++) {
            DecelHistoricalEvidenceAnalyzer.BinSummary bin =
                    analysis != null && analysis.bins != null && i < analysis.bins.size()
                            ? analysis.bins.get(i) : null;
            double low = bin == null ? Double.NaN : bin.safeLow;
            double high = bin == null ? Double.NaN : bin.safeHigh;
            int normal = bin == null ? 0 : bin.normalCorrections;
            int release = bin == null ? 0 : bin.decelReleases;
            int ambiguous = bin == null ? 0 : bin.ambiguousEvents;
            Outcome outcome;
            double target = Double.NaN;
            ClassificationBoundaryTarget derived = null;
            String reason;

            if (!compatibility.actionable) {
                outcome = Outcome.WITHHOLD;
                reason = "captured/current detector compatibility is not proven";
            } else if (analysis == null || !analysis.quietCalibrationLocked) {
                outcome = Outcome.WITHHOLD;
                reason = "quiet calibration is not locked";
            } else if (bin == null || !bin.separated) {
                outcome = Outcome.WITHHOLD;
                reason = "historical Normal/Release evidence is insufficient or not safely separated";
            } else if (current[i] <= scale * 0.5) {
                outcome = Outcome.WITHHOLD;
                reason = "current threshold is firmware-disabled (0); historical evidence never auto-enables a disabled cell";
            } else if (!finite(low) || !finite(high) || low > high || high < scale) {
                outcome = Outcome.WITHHOLD;
                reason = "validated evidence does not yield a representable nonzero separation interval";
            } else {
                derived = classificationBoundaryTarget(bin, low, high, scale,
                        Math.max(scale, minimum), maximum);
                target = derived.controllerTarget;
                if (!finite(target) || target <= scale * 0.5) {
                    outcome = Outcome.WITHHOLD;
                    reason = "independent Normal/Release event populations do not yield a safely representable classification-boundary target";
                } else if (Math.abs(target - current[i]) <= scale * 0.5) {
                    outcome = Outcome.KEEP;
                    reason = "current threshold already equals the classification-boundary target derived from independent physical events";
                } else {
                    outcome = Outcome.PROPOSE;
                    reason = "historical physical events yield a conservative classification-boundary threshold target";
                }
            }

            if (outcome == Outcome.KEEP) keep++;
            else if (outcome == Outcome.PROPOSE) propose++;
            else withhold++;

            BinDecision decision = new BinDecision(rpmBins[i], current[i], low, high,
                    normal, release, ambiguous, outcome, target, reason);
            decisions.add(decision);
            rows.append("  ").append(Math.round(rpmBins[i])).append(" RPM | current ECU ")
                    .append(fmt(current[i])).append(" | result ECU ")
                    .append(fmtResult(decision.ecuResultValue()))
                    .append(" | evidence separation ")
                    .append(fmt(low)).append("..").append(fmt(high));
            if (derived != null && finite(derived.normalCeiling)) {
                rows.append(" | normal ceiling ").append(fmt(derived.normalCeiling))
                        .append(" | credible release floor ").append(fmt(derived.releaseFloor))
                        .append(" | ignored isolated weak releases ").append(derived.ignoredWeakReleases)
                        .append(" | gap position ").append(fmtPercent(derived.gapPosition))
                        .append(" | raw boundary target ").append(fmt(derived.rawTarget));
            }
            rows.append(" | normal/release/ambiguous ")
                    .append(normal).append('/').append(release).append('/').append(ambiguous)
                    .append(" | ").append(outcome.name())
                    .append(" | ").append(reason).append('\n');
        }

        StringBuilder report = new StringBuilder();
        report.append("HISTORICAL THRESHOLD DECISION PREVIEW — READ ONLY\n")
                .append("Compatibility: ").append(compatibility.status).append('\n')
                .append("ECU result semantics: each evidence-supported RPM bin uses the independent physical event distributions directly. The largest observed Normal Correction is the noise-side ceiling. The lowest credible Decel Release is the release-side floor. The default target is placed 60% across that empty classification gap toward the release side and quantized to 0.05 to avoid false precision. With at least 5 Normal and 8 Release events, one clearly isolated weakest Release may be treated as a weak outlier when its separation from the next Release is at least twice its absolute distance from the Normal ceiling; this permits an isolated weak Release on either side of that ceiling to be rejected. Strong 10+10 evidence keeps the default 60% gap position after that rejection; thinner qualified evidence uses a conservative 75% gap position. The target must remain inside the pre-existing validated evidence separation interval. KEEP means the ECU already equals that target; PROPOSE means it differs; WITHHOLD has no result value.\n")
                .append(rows)
                .append("Summary: KEEP ").append(keep)
                .append(" | PROPOSE ").append(propose)
                .append(" | WITHHOLD ").append(withhold).append(".\n")
                .append("Boundary: preview only. This stage creates no ProposalWritePlan, performs no Apply/readback, changes no Working Tune value, does not tune Hold/fuel multiplier, and never Burns the ECU.");

        return new Result(compatibility, decisions, keep, propose, withhold,
                report.toString());
    }

    static Compatibility compatibility(AeProjectSnapshot snapshot,
                                       MlgEmbeddedTuneMetadata embedded) {
        if (snapshot == null) return new Compatibility(false, "WITHHELD — Working Tune is unavailable.");
        if (embedded == null || !embedded.available || embedded.detectorModeRaw.length() == 0) {
            return new Compatibility(false,
                    "WITHHELD — captured detector mode is unavailable; historical magnitude compatibility cannot be proven.");
        }

        ChannelRole historicalTwin = DecelSignalSamples.modeTwin(embedded.detectorModeRaw);
        ChannelRole currentTwin = DecelSignalSamples.modeTwin(snapshot.getEngagementModel());
        if (historicalTwin == null || currentTwin == null) {
            return new Compatibility(false,
                    "WITHHELD — captured/current detector mode could not be resolved to a published falling twin.");
        }
        if (historicalTwin != currentTwin) {
            return new Compatibility(false,
                    "WITHHELD — captured detector mode and current Working Tune select different falling twins.");
        }

        List<String> checked = new ArrayList<String>();
        List<String> unavailable = new ArrayList<String>();
        checked.add("mode=" + historicalTwin.getLabel());

        if (finite(embedded.deltaWindowMs)) {
            if (!snapshot.hasEngagementDeltaWindow()) {
                return new Compatibility(false,
                        "WITHHELD — captured Delta Window is known but current Working Tune Delta Window is unavailable.");
            }
            if (!near(embedded.deltaWindowMs, snapshot.getEngagementDeltaWindowMs(), 0.001)) {
                return new Compatibility(false,
                        "WITHHELD — captured/current Delta Window differs ("
                                + fmt(embedded.deltaWindowMs) + " vs "
                                + fmt(snapshot.getEngagementDeltaWindowMs()) + " ms)." );
            }
            checked.add("Delta Window=" + fmt(embedded.deltaWindowMs) + " ms");
        } else {
            unavailable.add("Delta Window");
        }

        if (finite(embedded.sampleLengthSeconds)) {
            if (!snapshot.hasEngagementSampleLength()) {
                return new Compatibility(false,
                        "WITHHELD — captured TPS lookback is known but current Working Tune lookback is unavailable.");
            }
            if (!near(embedded.sampleLengthSeconds,
                    snapshot.getEngagementSampleLengthSeconds(), 0.000001)) {
                return new Compatibility(false,
                        "WITHHELD — captured/current TPS lookback differs ("
                                + fmt(embedded.sampleLengthSeconds) + " vs "
                                + fmt(snapshot.getEngagementSampleLengthSeconds()) + " s)." );
            }
            checked.add("lookback=" + fmt(embedded.sampleLengthSeconds) + " s");
        } else {
            unavailable.add("TPS lookback");
        }

        if (embedded.fastCallback != null) {
            if (!snapshot.hasEngagementFastCallback()) {
                return new Compatibility(false,
                        "WITHHELD — captured Fast Callback is known but current Working Tune value is unavailable.");
            }
            if (embedded.fastCallback.booleanValue() != snapshot.isEngagementFastCallback()) {
                return new Compatibility(false,
                        "WITHHELD — captured/current Fast Callback differs.");
            }
            checked.add("Fast Callback=" + embedded.fastCallback.toString());
        } else {
            unavailable.add("Fast Callback");
        }

        StringBuilder status = new StringBuilder("COMPATIBLE — ");
        for (int i = 0; i < checked.size(); i++) {
            if (i > 0) status.append(", ");
            status.append(checked.get(i));
        }
        if (!unavailable.isEmpty()) {
            status.append("; captured ");
            for (int i = 0; i < unavailable.size(); i++) {
                if (i > 0) status.append(", ");
                status.append(unavailable.get(i));
            }
            status.append(" unavailable and therefore not compared");
        }
        status.append('.');
        return new Compatibility(true, status.toString());
    }

    private static ClassificationBoundaryTarget classificationBoundaryTarget(
            DecelHistoricalEvidenceAnalyzer.BinSummary bin,
            double low, double high, double scale, double minimum, double maximum) {
        if (bin == null || bin.events == null || bin.events.isEmpty()
                || !finite(low) || !finite(high) || low > high
                || !finite(scale) || scale <= 0.0) {
            return new ClassificationBoundaryTarget(Double.NaN, Double.NaN, 0,
                    BASE_GAP_POSITION, Double.NaN, Double.NaN);
        }

        List<Double> normals = new ArrayList<Double>();
        List<Double> releases = new ArrayList<Double>();
        for (DecelHistoricalEvidenceAnalyzer.Event event : bin.events) {
            if (event == null || !finite(event.peakMagnitude)) continue;
            if (DecelHistoricalEvidenceAnalyzer.CLASS_NORMAL_CORRECTION.equals(event.eventClass)) {
                normals.add(Double.valueOf(event.peakMagnitude));
            } else if (DecelHistoricalEvidenceAnalyzer.CLASS_DECEL_RELEASE.equals(event.eventClass)) {
                releases.add(Double.valueOf(event.peakMagnitude));
            }
        }
        if (normals.isEmpty() || releases.isEmpty()) {
            return new ClassificationBoundaryTarget(Double.NaN, Double.NaN, 0,
                    BASE_GAP_POSITION, Double.NaN, Double.NaN);
        }

        Collections.sort(normals);
        Collections.sort(releases);
        double normalCeiling = normals.get(normals.size() - 1).doubleValue();
        double releaseFloor = releases.get(0).doubleValue();
        int ignoredWeakReleases = 0;
        double gapPosition = BASE_GAP_POSITION;

        if (normals.size() >= MIN_NORMAL_FOR_WEAK_RELEASE_RULE
                && releases.size() >= MIN_RELEASE_FOR_WEAK_RELEASE_RULE
                && releases.size() >= 2) {
            double weakestDistanceFromNormal = Math.abs(releaseFloor - normalCeiling);
            double nextRelease = releases.get(1).doubleValue();
            double isolatedGap = nextRelease - releaseFloor;
            if (weakestDistanceFromNormal > 0.0
                    && isolatedGap >= weakestDistanceFromNormal * ISOLATED_WEAK_RELEASE_RATIO) {
                releaseFloor = nextRelease;
                ignoredWeakReleases = 1;
                gapPosition = normals.size() >= STRONG_NORMAL_EVENTS
                        && releases.size() >= STRONG_RELEASE_EVENTS
                        ? BASE_GAP_POSITION : ISOLATED_WEAK_RELEASE_GAP_POSITION;
            }
        }

        if (!finite(normalCeiling) || !finite(releaseFloor)
                || releaseFloor <= normalCeiling) {
            return new ClassificationBoundaryTarget(normalCeiling, releaseFloor,
                    ignoredWeakReleases, gapPosition, Double.NaN, Double.NaN);
        }

        double rawTarget = normalCeiling
                + ((releaseFloor - normalCeiling) * gapPosition);
        double lower = Math.max(Math.max(low, minimum), normalCeiling + scale);
        double upper = Math.min(Math.min(high, maximum), releaseFloor - scale);
        if (lower > upper) {
            return new ClassificationBoundaryTarget(normalCeiling, releaseFloor,
                    ignoredWeakReleases, gapPosition, rawTarget, Double.NaN);
        }

        double quantum = Math.max(RESULT_QUANTUM, scale);
        double coarseLow = Math.ceil(lower / quantum) * quantum;
        double coarseHigh = Math.floor(upper / quantum) * quantum;
        double target;
        if (coarseLow <= coarseHigh) {
            target = Math.round(rawTarget / quantum) * quantum;
            target = Math.max(coarseLow, Math.min(coarseHigh, target));
        } else {
            target = Math.round(rawTarget / scale) * scale;
            target = Math.max(lower, Math.min(upper, target));
        }
        target = Math.round(target / scale) * scale;
        target = Math.max(minimum, Math.min(maximum, target));
        return new ClassificationBoundaryTarget(normalCeiling, releaseFloor,
                ignoredWeakReleases, gapPosition, rawTarget, target);
    }

    private static Result unavailable(String reason) {
        Compatibility compatibility = new Compatibility(false,
                "WITHHELD — " + (reason == null ? "historical decision inputs are unavailable." : reason));
        String report = "HISTORICAL THRESHOLD DECISION PREVIEW — READ ONLY\n"
                + compatibility.status + "\n"
                + "No ProposalWritePlan is created and no Working Tune value is changed.";
        return new Result(compatibility, Collections.<BinDecision>emptyList(),
                0, 0, 0, report);
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    private static boolean near(double a, double b, double tolerance) {
        return finite(a) && finite(b) && Math.abs(a - b) <= tolerance;
    }

    private static String fmt(double value) {
        return finite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static String fmtResult(double value) {
        return finite(value) ? String.format(Locale.ROOT, "%.3f", value) : "—";
    }

    private static String fmtPercent(double fraction) {
        return finite(fraction) ? String.format(Locale.ROOT, "%.0f%%", fraction * 100.0) : "n/a";
    }
}
