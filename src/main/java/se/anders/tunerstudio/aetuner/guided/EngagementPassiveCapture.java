package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Passive Foundation 1 collector.
 *
 * Captures natural positive TPS movements, finds a robust repeatable step-size
 * cluster, and evaluates Dual Stride / Newest Delta Window candidates offline.
 * The live capture path is read-only. Driver guidance may show a visual
 * reference from the first usable movement, but that reference is presentation
 * only and is never a hard acceptance target.
 */
public final class EngagementPassiveCapture {
    private static final Object LOCK = new Object();
    private static final int MAX_EVENTS = 32;
    private static final int MAX_PRIOR_SETS = 8;
    private static final int MAX_REPEAT_MARKERS = 12;
    private static final int MAX_BASELINE_POINTS = 40;
    private static final int MIN_BASELINE_POINTS = 2;
    private static final int MAX_NOISE_RATES = 240;
    private static final double MIN_EVENT_STEP = 4.0;
    private static final double MAX_EVENT_STEP = 45.0;
    private static final double MIN_EVENT_SECONDS = 0.055;
    private static final double MAX_EVENT_SECONDS = 1.30;
    private static final double PEAK_DROP_CLOSE = 0.40;
    private static final double SETTLE_SECONDS = 0.45;
    private static final double SETTLE_TPS_TOLERANCE = 1.25;
    private static final double SETTLE_RPM_RATE_LIMIT = 250.0;
    private static final double SETTLE_RPM_ABOVE_BASE_LIMIT = 180.0;
    private static final double STATIONARY_VSS_KPH = 1.5;
    private static final double ROAD_VSS_KPH = 5.0;
    private static final int ROAD_CONFIRM_EVENTS = 3;
    private static final double ROAD_CONFIRM_RPM_SPAN = 400.0;
    private static final double MIN_ONSET_RATE = 3.0;
    private static final double MAX_NOISE_RATE_SAMPLE = 20.0;
    private static final double EPSILON = 0.000001;
    private static final double INTERNAL_SCORE_STEP_SECONDS = 0.005;
    private static final double COARSE_HOST_INTERVAL_MS = 20.0;
    private static final double[] DELTA_CANDIDATES_MS =
            new double[]{10, 15, 20, 25, 30, 35, 40, 45};

    private static final Deque<Point> baseline = new ArrayDeque<Point>();
    private static final Deque<Double> noiseRates = new ArrayDeque<Double>();
    private static final List<Event> events = new ArrayList<Event>();
    private static final List<SetSummary> completedSets = new ArrayList<SetSummary>();
    private static final List<Double> repeatPeakTps = new ArrayList<Double>();

    private static boolean moving;
    private static boolean settling;
    private static EventBuilder current;
    private static double settleBaselineTps = Double.NaN;
    private static double settleBaselineRpm = Double.NaN;
    private static double settleBaselineVss = Double.NaN;
    private static double settlePeakTps = Double.NaN;
    private static double settleAnchorTps = Double.NaN;
    private static double settleStableSince = Double.NaN;
    private static double lastSettleSeconds = Double.NaN;
    private static double lastSettleRpm = Double.NaN;
    private static boolean settleTpsReturned;
    private static boolean settleTpsQuiet;
    private static boolean settleRpmReady;
    private static double referencePeakTps = Double.NaN;
    private static int targetComparable = 6;
    private static int totalCompleted;
    private static int rejectedSmall;
    private static int rejectedLarge;
    private static int rejectedDuration;
    private static double clusterMedian = Double.NaN;
    private static double clusterMad = Double.NaN;
    private static double clusterTolerance = Double.NaN;
    private static int comparableCount;
    private static String lastEvent = "No pedal movement captured yet";
    private static long revision;

    private EngagementPassiveCapture() { }

    /** Full lifecycle reset, including previously completed capture-set summaries. */
    public static void reset() {
        synchronized (LOCK) {
            completedSets.clear();
            clearActiveSetLocked(true);
            lastEvent = "No pedal movement captured yet";
            revision++;
        }
    }

    /**
     * Start a fresh independent Foundation capture set without discarding the
     * previous completed set. The learned TPS-rate noise floor is retained
     * because the controller/tune baseline has not changed.
     */
    public static void startNewSet() {
        synchronized (LOCK) {
            archiveActiveSetLocked();
            clearActiveSetLocked(false);
            lastEvent = "New capture set — first usable movement will set the visual TPS reference";
            revision++;
        }
    }

    private static void archiveActiveSetLocked() {
        if (events.isEmpty()) return;
        if (completedSets.size() >= MAX_PRIOR_SETS) completedSets.remove(0);
        EvidenceContext context = evidenceContextLocked();
        List<Event> comparable = currentComparableEventsLocked();
        completedSets.add(new SetSummary(events.size(), comparableCount,
                clusterMedian, clusterMad, clusterTolerance,
                context.roadComparable, context.stationaryComparable,
                context.roadRpmSpan,
                Collections.unmodifiableList(new ArrayList<Event>(comparable))));
    }

    private static void clearActiveSetLocked(boolean clearNoise) {
        baseline.clear();
        if (clearNoise) noiseRates.clear();
        events.clear();
        repeatPeakTps.clear();
        moving = false;
        settling = false;
        current = null;
        settleBaselineTps = Double.NaN;
        settleBaselineRpm = Double.NaN;
        settleBaselineVss = Double.NaN;
        settlePeakTps = Double.NaN;
        settleAnchorTps = Double.NaN;
        settleStableSince = Double.NaN;
        lastSettleSeconds = Double.NaN;
        lastSettleRpm = Double.NaN;
        settleTpsReturned = false;
        settleTpsQuiet = false;
        settleRpmReady = false;
        referencePeakTps = Double.NaN;
        totalCompleted = 0;
        rejectedSmall = 0;
        rejectedLarge = 0;
        rejectedDuration = 0;
        clusterMedian = Double.NaN;
        clusterMad = Double.NaN;
        clusterTolerance = Double.NaN;
        comparableCount = 0;
    }

    /**
     * Called when a Foundation capture is (re)started. If the previous active
     * set had already completed, restart means a new independent set: archive
     * its summary, clear the active 5/N population and visual markers, and keep
     * only the learned TPS-rate noise floor.
     */
    public static void configureTarget(int target) {
        synchronized (LOCK) {
            if (!moving && !settling && !events.isEmpty()
                    && comparableCount >= targetComparable) {
                archiveActiveSetLocked();
                clearActiveSetLocked(false);
                lastEvent = "New capture set — first usable movement will set the visual TPS reference";
                revision++;
            }
            targetComparable = Math.max(3, Math.min(12, target));
            recomputeClusterLocked();
        }
    }

    /**
     * Accept one live sample. Returns true only when this sample completed a
     * movement that belongs to the current comparable cluster.
     */
    public static boolean accept(LiveSample sample) {
        if (sample == null) return false;
        synchronized (LOCK) {
            double seconds = sample.getSeconds();
            double tps = sample.get(ChannelRole.TPS);
            double rpm = sample.get(ChannelRole.RPM);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            double rate = sample.getTpsDot();
            if (!finite(seconds, tps, rpm, threshold, rate)) return false;

            if (moving) {
                current.add(point(sample));
                if (tps > current.peakTps) {
                    current.peakTps = tps;
                    current.peakSeconds = seconds;
                }
                if (!Double.isFinite(current.firstDetectorSeconds)
                        && sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
                    current.firstDetectorSeconds = seconds;
                }
                double elapsed = seconds - current.onsetSeconds;
                boolean reversed = elapsed >= MIN_EVENT_SECONDS
                        && current.peakTps - tps >= PEAK_DROP_CLOSE;
                boolean timedOut = elapsed >= MAX_EVENT_SECONDS;
                if (reversed || timedOut) {
                    return closeEventLocked(seconds, reversed);
                }
                return false;
            }

            if (settling) {
                processSettlingLocked(sample);
                return false;
            }

            // Preserve a genuinely pre-event baseline. A rising TPS sample must
            // never be inserted into the baseline before the onset decision;
            // otherwise sparse/irregular delivery can move the baseline upward,
            // delay onset until the peak, and make SETTLING wait for a TPS value
            // the released pedal will never revisit.
            pruneBaselineLocked(seconds);
            double onsetRate = onsetRateLocked();
            if (rate >= onsetRate) {
                if (baseline.size() < MIN_BASELINE_POINTS) return false;

                List<Point> pre = new ArrayList<Point>(baseline);
                double baselineTps = medianPoint(pre, ValueKind.TPS);
                double baselineRpm = medianPoint(pre, ValueKind.RPM);
                double baselineVss = medianPoint(pre, ValueKind.VSS);
                if (!Double.isFinite(baselineTps) || !Double.isFinite(baselineRpm)) return false;

                current = new EventBuilder(baselineTps, baselineRpm, baselineVss, seconds, pre);
                current.add(point(sample));
                current.peakTps = tps;
                current.peakSeconds = seconds;
                if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
                    current.firstDetectorSeconds = seconds;
                }
                moving = true;
                baseline.clear();
                revision++;
                return false;
            }

            rememberBaselineLocked(sample, true);
            return false;
        }
    }

    private static void processSettlingLocked(LiveSample sample) {
        double seconds = sample.getSeconds();
        double tps = sample.get(ChannelRole.TPS);
        double rpm = sample.get(ChannelRole.RPM);
        double rate = sample.getTpsDot();
        rememberBaselineLocked(sample, false);

        double rpmRate = Double.NaN;
        if (Double.isFinite(lastSettleSeconds) && seconds > lastSettleSeconds + EPSILON
                && Double.isFinite(lastSettleRpm)) {
            rpmRate = (rpm - lastSettleRpm) / (seconds - lastSettleSeconds);
        }
        lastSettleSeconds = seconds;
        lastSettleRpm = rpm;

        // The previous positive opening must clearly end before re-arming, but
        // the next baseline does NOT have to be the same TPS/RPM operating point.
        // This is essential on-road: the driver may release the opening, then move
        // to a different steady cruise/load point before the next test event.
        if (!settleTpsReturned && Double.isFinite(settlePeakTps)
                && tps <= settlePeakTps - PEAK_DROP_CLOSE) {
            settleTpsReturned = true;
        }
        double quietRate = Math.max(MIN_ONSET_RATE, onsetRateLocked() * 0.75);
        boolean tpsQuiet = Math.abs(rate) <= quietRate;
        // Re-anchor on a steady RPM, not the old RPM. This prevents an idle-origin
        // event from forcing the driver back to idle before a road event can arm.
        boolean rpmSettled = !Double.isFinite(rpmRate)
                || Math.abs(rpmRate) <= SETTLE_RPM_RATE_LIMIT;
        settleTpsQuiet = tpsQuiet;
        settleRpmReady = rpmSettled;

        if (settleTpsReturned && tpsQuiet && rpmSettled) {
            if (!Double.isFinite(settleStableSince)) {
                settleStableSince = seconds;
                settleAnchorTps = tps;
            }
            if (seconds - settleStableSince >= SETTLE_SECONDS) {
                List<Point> recent = new ArrayList<Point>(baseline);
                double learnedAnchor = medianPoint(recent, ValueKind.TPS);
                if (Double.isFinite(learnedAnchor)) settleAnchorTps = learnedAnchor;
                settling = false;
                settleStableSince = Double.NaN;
                lastEvent = "New steady operating point acquired at ~"
                        + f1(settleAnchorTps) + "% TPS — ready for the next pedal movement";
                revision++;
            }
        } else {
            settleStableSince = Double.NaN;
            settleAnchorTps = Double.NaN;
        }
    }

    private static boolean closeEventLocked(double seconds, boolean alreadyReleased) {
        moving = false;
        EventBuilder builder = current;
        current = null;
        beginSettlingLocked(builder, seconds, alreadyReleased);
        totalCompleted++;

        double step = builder.peakTps - builder.baselineTps;
        double duration = builder.peakSeconds - builder.onsetSeconds;
        if (step < MIN_EVENT_STEP) {
            rejectedSmall++;
            lastEvent = "Ignored small movement: +" + f1(step) + " TPS — settling before re-arm";
            revision++;
            return false;
        }
        if (step > MAX_EVENT_STEP) {
            rejectedLarge++;
            lastEvent = "Ignored unusually large movement: +" + f1(step) + " TPS — settling before re-arm";
            revision++;
            return false;
        }
        if (duration < MIN_EVENT_SECONDS || duration > MAX_EVENT_SECONDS) {
            rejectedDuration++;
            lastEvent = "Ignored movement duration " + f3(duration) + " s — settling before re-arm";
            revision++;
            return false;
        }

        Event event = builder.build();
        if (events.size() >= MAX_EVENTS) events.remove(0);
        events.add(event);
        if (!Double.isFinite(referencePeakTps)) {
            referencePeakTps = event.peakTps;
        } else {
            if (repeatPeakTps.size() >= MAX_REPEAT_MARKERS) repeatPeakTps.remove(0);
            repeatPeakTps.add(Double.valueOf(event.peakTps));
        }
        recomputeClusterLocked();
        boolean comparable = isComparableLocked(event.step);
        if (events.size() == 1) {
            lastEvent = "Reference movement captured: +" + f1(event.step)
                    + " TPS — let TPS settle, then repeat approximately this movement";
        } else {
            lastEvent = (comparable ? "Captured comparable movement: +"
                    : "Captured movement outside current cluster: +")
                    + f1(event.step) + " TPS — settling before re-arm";
        }
        revision++;
        return comparable;
    }

    private static void beginSettlingLocked(EventBuilder builder, double seconds,
                                              boolean alreadyReleased) {
        settling = true;
        settleBaselineTps = builder.baselineTps;
        settleBaselineRpm = builder.baselineRpm;
        settleBaselineVss = builder.baselineVss;
        settlePeakTps = builder.peakTps;
        settleAnchorTps = Double.NaN;
        settleStableSince = Double.NaN;
        lastSettleSeconds = seconds;
        lastSettleRpm = Double.NaN;
        settleTpsReturned = alreadyReleased;
        settleTpsQuiet = false;
        settleRpmReady = false;
        baseline.clear();
    }

    private static void rememberBaselineLocked(LiveSample sample, boolean learnNoise) {
        baseline.addLast(point(sample));
        while (baseline.size() > MAX_BASELINE_POINTS) baseline.removeFirst();
        pruneBaselineLocked(sample.getSeconds());
        if (learnNoise) {
            double rate = Math.abs(sample.getTpsDot());
            if (Double.isFinite(rate) && rate <= MAX_NOISE_RATE_SAMPLE) {
                noiseRates.addLast(Double.valueOf(rate));
                while (noiseRates.size() > MAX_NOISE_RATES) noiseRates.removeFirst();
            }
        }
    }

    private static void pruneBaselineLocked(double now) {
        while (!baseline.isEmpty() && now - baseline.peekFirst().seconds > 0.35) {
            baseline.removeFirst();
        }
    }

    private static Point point(LiveSample sample) {
        return new Point(sample.getSeconds(),
                sample.get(ChannelRole.TPS),
                sample.get(ChannelRole.RPM),
                sample.get(ChannelRole.ACCEL_THRESHOLD),
                sample.bool(ChannelRole.AE_ABOVE_THRESHOLD),
                sample.get(ChannelRole.VSS));
    }

    private static double onsetRateLocked() {
        if (noiseRates.size() < 20) return MIN_ONSET_RATE;
        List<Double> copy = new ArrayList<Double>(noiseRates);
        Collections.sort(copy);
        int index = Math.min(copy.size() - 1,
                (int) Math.floor((copy.size() - 1) * 0.95));
        return Math.max(MIN_ONSET_RATE, copy.get(index).doubleValue() * 2.5);
    }

    private static void recomputeClusterLocked() {
        if (events.isEmpty()) {
            clusterMedian = clusterMad = clusterTolerance = Double.NaN;
            comparableCount = 0;
            return;
        }
        List<Double> steps = new ArrayList<Double>();
        for (Event event : events) steps.add(Double.valueOf(event.step));

        List<Double> best = new ArrayList<Double>();
        double bestSpread = Double.POSITIVE_INFINITY;
        for (Double centerValue : steps) {
            double center = centerValue.doubleValue();
            double initialTolerance = clamp(center * 0.25, 3.0, 6.0);
            List<Double> candidate = new ArrayList<Double>();
            for (Double value : steps) {
                if (Math.abs(value.doubleValue() - center) <= initialTolerance) {
                    candidate.add(value);
                }
            }
            double spread = mad(candidate);
            if (candidate.size() > best.size()
                    || (candidate.size() == best.size() && spread < bestSpread)) {
                best = candidate;
                bestSpread = spread;
            }
        }

        clusterMedian = median(best);
        clusterMad = mad(best);
        clusterTolerance = clamp(Math.max(clusterMedian * 0.20, clusterMad * 2.0), 3.0, 6.0);
        comparableCount = 0;
        for (Event event : events) {
            if (isComparableLocked(event.step)) comparableCount++;
        }
    }

    private static boolean isComparableLocked(double step) {
        return Double.isFinite(clusterMedian) && Double.isFinite(clusterTolerance)
                && Math.abs(step - clusterMedian) <= clusterTolerance + EPSILON;
    }

    private static List<Event> currentComparableEventsLocked() {
        List<Event> comparable = new ArrayList<Event>();
        for (Event event : events) {
            if (isComparableLocked(event.step)) comparable.add(event);
        }
        return comparable;
    }

    private static List<Event> accumulatedComparableEventsLocked() {
        List<Event> combined = new ArrayList<Event>();
        for (SetSummary set : completedSets) combined.addAll(set.comparableEvents);
        combined.addAll(currentComparableEventsLocked());
        return combined;
    }

    private static EvidenceContext evidenceContextLocked() {
        return evidenceContextForLocked(currentComparableEventsLocked());
    }

    private static EvidenceContext evidenceContextForLocked(List<Event> source) {
        int road = 0;
        int stationary = 0;
        int operating = 0;
        double minOperatingRpm = Double.POSITIVE_INFINITY;
        double maxOperatingRpm = Double.NEGATIVE_INFINITY;
        for (Event event : source) {
            if (Double.isFinite(event.baselineRpm)) {
                operating++;
                minOperatingRpm = Math.min(minOperatingRpm, event.baselineRpm);
                maxOperatingRpm = Math.max(maxOperatingRpm, event.baselineRpm);
            }
            if (Double.isFinite(event.baselineVss) && Math.abs(event.baselineVss) >= ROAD_VSS_KPH) {
                road++;
            } else if (Double.isFinite(event.baselineVss)
                    && Math.abs(event.baselineVss) <= STATIONARY_VSS_KPH) {
                stationary++;
            }
        }
        double span = operating >= 2 ? maxOperatingRpm - minOperatingRpm : 0.0;
        return new EvidenceContext(road, stationary, span);
    }

    private static double medianHostIntervalMs(List<Event> source) {
        List<Double> intervals = new ArrayList<Double>();
        for (Event event : source) {
            for (int i = 1; i < event.points.size(); i++) {
                double dt = event.points.get(i).seconds - event.points.get(i - 1).seconds;
                if (dt > EPSILON && dt < 0.50) intervals.add(Double.valueOf(dt * 1000.0));
            }
        }
        return median(intervals);
    }

    private static double medianRiseMs(List<Event> source) {
        List<Double> rises = new ArrayList<Double>();
        for (Event event : source) {
            double rise = event.peakSeconds - event.onsetSeconds;
            if (rise > EPSILON) rises.add(Double.valueOf(rise * 1000.0));
        }
        return median(rises);
    }

    private static String pedalQualityLocked() {
        if (!Double.isFinite(clusterMedian) || !Double.isFinite(clusterMad)) return "COLLECTING";
        double ratio = clusterMad / Math.max(1.0, Math.abs(clusterMedian));
        if (clusterMad <= 1.5 || ratio <= 0.08) return "GOOD";
        if (clusterMad <= 3.0 || ratio <= 0.15) return "FAIR";
        return "POOR";
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            EvidenceContext context = evidenceContextLocked();
            double[] markers = new double[repeatPeakTps.size()];
            for (int i = 0; i < markers.length; i++) markers[i] = repeatPeakTps.get(i).doubleValue();
            return new Snapshot(moving, settling, events.size(), comparableCount, targetComparable,
                    clusterMedian, clusterMad, clusterTolerance, onsetRateLocked(),
                    totalCompleted, rejectedSmall, rejectedLarge, rejectedDuration,
                    referencePeakTps, markers,
                    settleBaselineTps, settleAnchorTps,
                    settleTpsReturned, settleTpsQuiet, settleRpmReady,
                    context.roadComparable, context.stationaryComparable,
                    context.roadRpmSpan, completedSets.size(), lastEvent, revision);
        }
    }

    public static ProposalWritePlan recommendationPlan(AeProjectSnapshot snapshot) {
    synchronized (LOCK) {
        Recommendation recommendation = recommendationLocked(snapshot);
        if (!recommendation.ready || !recommendation.applyReady || snapshot == null) {
            return null;
        }
        return EngagementDetectionSettingProposal.timingPair(snapshot,
                recommendation.deltaWindowMs,
                recommendation.sampleLengthMs / 1000.0);
    }
}

    /** Structured timing result used by the Driver view. Delta Window may
     * remain unresolved while Sample Length already has a safe capacity answer
     * for every currently plausible Delta candidate. */
    public static TimingStatus timingStatus(AeProjectSnapshot snapshot) {
        synchronized (LOCK) {
            Recommendation r = recommendationLocked(snapshot);
            double currentSampleMs = snapshot == null ? Double.NaN
                    : snapshot.getEngagementSampleLengthSeconds() * 1000.0;
            boolean deltaChange = r.ready && Double.isFinite(r.currentDeltaWindowMs)
                    && Math.abs(r.deltaWindowMs - r.currentDeltaWindowMs) > EPSILON;
            return new TimingStatus(r.ready, r.sampleResolved, r.applyReady,
                    r.deltaWindowMs, r.currentDeltaWindowMs, deltaChange,
                    r.sampleLengthMs, currentSampleMs,
                    r.sampleResolved && r.sampleLengthChangeNeeded, r.reason,
                    r.bestDeltaMs, r.bestScore, r.secondDeltaMs, r.secondScore,
                    r.scoreGap, r.requiredScoreGap, r.currentScore,
                    r.plausibleMinDeltaMs, r.plausibleMaxDeltaMs,
                    r.evidenceSets, r.evidenceEvents,
                    r.medianHostIntervalMs, r.medianRiseMs,
                    r.temporalResolutionLimited, r.pedalQuality, r.coaching);
        }
    }

    public static String reviewText(AeProjectSnapshot snapshot) {
        synchronized (LOCK) {
            Recommendation recommendation = recommendationLocked(snapshot);
            EvidenceContext currentContext = evidenceContextLocked();
            StringBuilder out = new StringBuilder();
            out.append("PASSIVE TPS MOVEMENT ANALYSIS\n")
                    .append("Current set physical openings: ").append(events.size()).append('\n')
                    .append("Current comparable cluster: ").append(comparableCount).append('/')
                    .append(targetComparable).append('\n');
            if (Double.isFinite(clusterMedian)) {
                out.append("Pedal repeatability: ").append(recommendation.pedalQuality)
                        .append(" | median +").append(f1(clusterMedian))
                        .append(" TPS | MAD ").append(f1(clusterMad))
                        .append(" | accepted band +/-").append(f1(clusterTolerance)).append(" TPS\n");
            }
            if (Double.isFinite(referencePeakTps)) {
                out.append("Driver visual reference peak: ").append(f1(referencePeakTps))
                        .append("% TPS (presentation only; not an acceptance target)\n");
            }
            out.append("Current-set VSS-road context: ").append(currentContext.roadComparable)
                    .append(" | VSS-stationary: ").append(currentContext.stationaryComparable)
                    .append(" | pre-event RPM span: ").append(f0(currentContext.roadRpmSpan)).append(" RPM\n")
                    .append("Accumulated timing evidence: ").append(recommendation.evidenceSets)
                    .append(" set(s) / ").append(recommendation.evidenceEvents)
                    .append(" comparable opening(s) retained and combined\n")
                    .append("Accumulated pre-event RPM span: ").append(f0(recommendation.accumulatedRpmSpan)).append(" RPM\n")
                    .append("Host trace timing: median point interval ").append(f1(recommendation.medianHostIntervalMs))
                    .append(" ms | median opening rise ").append(f0(recommendation.medianRiseMs)).append(" ms")
                    .append(recommendation.temporalResolutionLimited
                            ? " | coarse for 5 ms candidate spacing\n"
                            : " | adequate for current candidate spacing\n")
                    .append("No controller settings were changed during capture.\n\n");

            if (!recommendation.scores.isEmpty()) {
                out.append("DELTA WINDOW DIAGNOSTICS (lower score is better)\n")
                        .append("Best: ").append(f0(recommendation.bestDeltaMs)).append(" ms = ")
                        .append(f3(recommendation.bestScore));
                if (Double.isFinite(recommendation.secondDeltaMs)) {
                    out.append(" | runner-up: ").append(f0(recommendation.secondDeltaMs)).append(" ms = ")
                            .append(f3(recommendation.secondScore));
                }
                out.append(" | gap ").append(f3(recommendation.scoreGap))
                        .append(" / required ").append(f3(recommendation.requiredScoreGap)).append('\n');
                if (Double.isFinite(recommendation.currentScore)) {
                    out.append("Current Delta Window score: ").append(f3(recommendation.currentScore)).append('\n');
                }
                out.append("Plausible Delta range: ").append(f0(recommendation.plausibleMinDeltaMs))
                        .append("-").append(f0(recommendation.plausibleMaxDeltaMs)).append(" ms\n");
                for (CandidateScore score : recommendation.scores) {
                    out.append("  ").append(f0(score.deltaMs)).append(" ms: ")
                            .append(f3(score.score)).append('\n');
                }
                out.append('\n');
            }

            if (!recommendation.ready) {
                out.append("1/2 DELTA WINDOW: UNRESOLVED\n")
                        .append(recommendation.reason.length() > 0 ? recommendation.reason
                                : "More timing evidence is required.").append('\n');
                if (recommendation.sampleResolved) {
                    out.append("2/2 SAMPLE LENGTH: CAPACITY RESOLVED — ")
                            .append(f0(recommendation.sampleLengthMs)).append(" ms — ")
                            .append(recommendation.sampleLengthChangeNeeded ? "INCREASE REQUIRED" : "RETAIN")
                            .append(". This covers the currently plausible Delta range ")
                            .append(f0(recommendation.plausibleMinDeltaMs)).append('-')
                            .append(f0(recommendation.plausibleMaxDeltaMs)).append(" ms without history clamp.\n");
                } else {
                    out.append("2/2 SAMPLE LENGTH: WAITING FOR ENOUGH DELTA EVIDENCE TO BOUND CAPACITY.\n");
                }
                out.append("WHAT TO DO NEXT: ").append(recommendation.coaching);
                return out.toString();
            }

            out.append(recommendation.applyReady
                    ? "1/2 DELTA WINDOW: OPERATING-RANGE CONFIRMED — "
                    : "1/2 DELTA WINDOW: PROVISIONAL — ")
                    .append(f0(recommendation.deltaWindowMs)).append(" ms")
                    .append(Math.abs(recommendation.deltaWindowMs - recommendation.currentDeltaWindowMs) <= EPSILON
                            ? " — RETAIN\n" : " — PROPOSE\n")
                    .append("2/2 SAMPLE LENGTH: ").append(f0(recommendation.sampleLengthMs)).append(" ms — ")
                    .append(recommendation.sampleLengthChangeNeeded ? "PROPOSE INCREASE\n" : "RETAIN\n");
            if (!recommendation.applyReady) {
                out.append("Apply withheld: final physical validation requires comparable pre-event operating points spanning about ")
                        .append(f0(ROAD_CONFIRM_RPM_SPAN)).append(" RPM or more. VSS is context, not a hard prerequisite.\n");
            }
            out.append("WHAT TO DO NEXT: ").append(recommendation.coaching)
                    .append("\n\nThe recommendation is calculated inside AE Tuner from retained captured TPS traces using the firmware-equivalent Dual Stride / Newest comparison shape. No LLM or external log interpretation is required.");
            return out.toString();
        }
    }

    private static Recommendation recommendationLocked(AeProjectSnapshot snapshot) {
        double currentDelta = snapshot == null ? Double.NaN : snapshot.getEngagementDeltaWindowMs();
        double currentSampleMs = snapshot == null ? Double.NaN
                : snapshot.getEngagementSampleLengthSeconds() * 1000.0;
        String quality = pedalQualityLocked();
        if (snapshot == null || !snapshot.hasEngagementDeltaWindow() || !snapshot.hasEngagementSampleLength()) {
            return Recommendation.noEvidence(currentDelta, currentSampleMs,
                    "Read Working Tune before a timing recommendation is released.", quality);
        }
        if (comparableCount < targetComparable) {
            int remaining = Math.max(0, targetComparable - comparableCount);
            return Recommendation.noEvidence(currentDelta, currentSampleMs,
                    "Need " + remaining + " more comparable natural opening(s) to complete this independent set.", quality);
        }

        List<Event> comparable = accumulatedComparableEventsLocked();
        EvidenceContext accumulatedContext = evidenceContextForLocked(comparable);
        int evidenceSets = completedSets.size() + 1;
        int evidenceEvents = comparable.size();
        double hostIntervalMs = medianHostIntervalMs(comparable);
        double riseMs = medianRiseMs(comparable);

        List<CandidateScore> scores = new ArrayList<CandidateScore>();
        for (double candidate : DELTA_CANDIDATES_MS) {
            List<Double> eventScores = new ArrayList<Double>();
            for (Event event : comparable) {
                eventScores.add(Double.valueOf(scoreCandidate(event, candidate)));
            }
            scores.add(new CandidateScore(candidate, median(eventScores)));
        }
        Collections.sort(scores, new Comparator<CandidateScore>() {
            @Override public int compare(CandidateScore a, CandidateScore b) {
                int byScore = Double.compare(a.score, b.score);
                if (byScore != 0) return byScore;
                return Double.compare(Math.abs(a.deltaMs - currentDelta),
                        Math.abs(b.deltaMs - currentDelta));
            }
        });

        CandidateScore best = scores.get(0);
        CandidateScore second = scores.size() > 1 ? scores.get(1) : null;
        CandidateScore currentScore = nearest(scores, currentDelta);
        double gap = second == null ? Double.POSITIVE_INFINITY : second.score - best.score;
        double requiredGap = Math.max(0.040, Math.abs(best.score) * 0.10);
        boolean temporalLimited = Double.isFinite(hostIntervalMs)
                && hostIntervalMs > COARSE_HOST_INTERVAL_MS
                && second != null && Math.abs(second.deltaMs - best.deltaMs) <= 5.1;

        double plausibleMin = best.deltaMs;
        double plausibleMax = best.deltaMs;
        for (CandidateScore score : scores) {
            if (score.score - best.score <= requiredGap + EPSILON) {
                plausibleMin = Math.min(plausibleMin, score.deltaMs);
                plausibleMax = Math.max(plausibleMax, score.deltaMs);
            }
        }
        double requiredSampleMs = Math.ceil((plausibleMax + 10.0) / 10.0) * 10.0;
        double suggestedSampleMs = Double.isFinite(currentSampleMs)
                ? Math.max(currentSampleMs, requiredSampleMs) : requiredSampleMs;
        boolean sampleChange = Double.isFinite(currentSampleMs)
                && currentSampleMs + EPSILON < requiredSampleMs;

        double selected = best.deltaMs;
        if (currentScore != null) {
            double improvement = currentScore.score - best.score;
            double requiredImprovement = Math.max(0.080, Math.abs(best.score) * 0.15);
            if (improvement <= requiredImprovement + EPSILON) selected = currentScore.deltaMs;
        }

        boolean changing = currentScore == null || Math.abs(selected - currentScore.deltaMs) > EPSILON;
        boolean ambiguous = changing && (gap + EPSILON < requiredGap || temporalLimited);
        String coaching = coachingLocked(quality, ambiguous, temporalLimited, hostIntervalMs,
                riseMs, evidenceSets, accumulatedContext.roadRpmSpan);

        if (ambiguous) {
            String reason = temporalLimited
                    ? "Adjacent Delta candidates remain too close while host samples are coarse relative to the 5 ms candidate spacing. Exact 5 ms precision is withheld."
                    : "Candidate scores remain too close to justify an exact Delta Window change. Prior independent sets are retained and combined rather than discarded.";
            return Recommendation.ambiguous(currentDelta, suggestedSampleMs, sampleChange,
                    scores, reason, best, second, currentScore, gap, requiredGap,
                    plausibleMin, plausibleMax, evidenceSets, evidenceEvents,
                    hostIntervalMs, riseMs, temporalLimited, quality, coaching,
                    accumulatedContext.roadRpmSpan);
        }

        requiredSampleMs = Math.ceil((selected + 10.0) / 10.0) * 10.0;
        suggestedSampleMs = Double.isFinite(currentSampleMs)
                ? Math.max(currentSampleMs, requiredSampleMs) : requiredSampleMs;
        sampleChange = Double.isFinite(currentSampleMs)
                && currentSampleMs + EPSILON < requiredSampleMs;
        boolean operatingConfirmed = evidenceEvents >= ROAD_CONFIRM_EVENTS
                && accumulatedContext.roadRpmSpan + EPSILON >= ROAD_CONFIRM_RPM_SPAN;
        return Recommendation.resolved(operatingConfirmed, selected, currentDelta,
                suggestedSampleMs, sampleChange, scores, best, second, currentScore,
                gap, requiredGap, selected, selected,
                evidenceSets, evidenceEvents, hostIntervalMs, riseMs,
                temporalLimited, quality, coaching, accumulatedContext.roadRpmSpan);
    }

    private static String coachingLocked(String quality, boolean ambiguous,
                                         boolean temporalLimited, double hostIntervalMs,
                                         double riseMs, int evidenceSets, double rpmSpan) {
        StringBuilder out = new StringBuilder();
        if ("POOR".equals(quality)) {
            out.append("Pedal amplitude repeatability is the main weakness. Use the full-height marker only as a memory aid and make the next openings more similar in size; do not chase an exact number.");
        } else if (ambiguous && temporalLimited) {
            out.append("Pedal repeatability is ").append(quality)
                    .append("; do not tighten the TPS target. Keep approximately the same opening size but make the opening slightly slower and smoother so the rising edge spans more captured points. ")
                    .append("Median host interval is ").append(f1(hostIntervalMs)).append(" ms")
                    .append(Double.isFinite(riseMs) ? " and median rise is " + f0(riseMs) + " ms. " : ". ")
                    .append("Start a new independent set when ready; its timing evidence will be added to the ")
                    .append(evidenceSets).append(" set(s) already represented rather than replacing them.");
        } else if (ambiguous) {
            out.append("Pedal repeatability is ").append(quality)
                    .append(". Keep the same approximate amplitude and collect another independent set; prior timing evidence is retained and combined, so the new set genuinely increases Delta discrimination.");
        } else {
            out.append("Pedal repeatability is ").append(quality)
                    .append(" and Delta discrimination is sufficient. Do not change the pedal technique just to make the marker more exact.");
        }
        if (rpmSpan + EPSILON < ROAD_CONFIRM_RPM_SPAN) {
            out.append(" For final Apply confidence, when safe, spread the pre-event operating points over about ")
                    .append(f0(ROAD_CONFIRM_RPM_SPAN)).append(" RPM; current accumulated span is ")
                    .append(f0(rpmSpan)).append(" RPM.");
        }
        return out.toString();
    }

    private static CandidateScore nearest(List<CandidateScore> scores, double value) {
        if (!Double.isFinite(value)) return null;
        CandidateScore best = null;
        double error = Double.POSITIVE_INFINITY;
        for (CandidateScore score : scores) {
            double e = Math.abs(score.deltaMs - value);
            if (e < error) { error = e; best = score; }
        }
        return error <= 2.6 ? best : null;
    }

    private static double scoreCandidate(Event event, double deltaMs) {
        List<Point> points = event.points;
        if (points.size() < 3) return 9.0;
        double start = points.get(0).seconds + INTERNAL_SCORE_STEP_SECONDS;
        double end = points.get(points.size() - 1).seconds;
        boolean above = false;
        int bursts = 0;
        int preFalse = 0;
        double firstCross = Double.NaN;
        double clearAfterPeak = Double.NaN;
        for (double seconds = start; seconds <= end + EPSILON;
             seconds += INTERNAL_SCORE_STEP_SECONDS) {
            double tps = interpolateTps(points, seconds);
            double previous = interpolateTps(points, seconds - INTERNAL_SCORE_STEP_SECONDS);
            double strided = interpolateTps(points, seconds - deltaMs / 1000.0);
            double threshold = interpolateThreshold(points, seconds);
            if (!finite(tps, previous, strided, threshold) || threshold <= 0.0) continue;
            double output = Math.max(tps - previous, tps - strided);
            boolean nowAbove = output > threshold;
            if (seconds < event.onsetSeconds) {
                if (nowAbove && !above) preFalse++;
            } else {
                if (nowAbove && !above) {
                    bursts++;
                    if (!Double.isFinite(firstCross)) firstCross = seconds;
                }
                if (seconds >= event.peakSeconds && !nowAbove
                        && !Double.isFinite(clearAfterPeak)) {
                    clearAfterPeak = seconds;
                }
            }
            above = nowAbove;
        }
        if (!Double.isFinite(firstCross)) return 5.0 + preFalse * 0.35;
        double onsetDelay = Math.max(0.0, firstCross - event.onsetSeconds);
        double clearDelay = Double.isFinite(clearAfterPeak)
                ? Math.max(0.0, clearAfterPeak - event.peakSeconds) : 0.50;
        int extraBursts = Math.max(0, bursts - 1);
        return onsetDelay * 6.0 + clearDelay * 0.8
                + extraBursts * 0.45 + preFalse * 0.35;
    }

    private static double interpolateThreshold(List<Point> points, double seconds) {
        if (points.isEmpty() || seconds < points.get(0).seconds) return Double.NaN;
        Point previous = points.get(0);
        if (Math.abs(previous.seconds - seconds) <= EPSILON) return previous.threshold;
        for (int i = 1; i < points.size(); i++) {
            Point next = points.get(i);
            if (next.seconds + EPSILON < seconds) {
                previous = next;
                continue;
            }
            double span = next.seconds - previous.seconds;
            if (span <= EPSILON) return next.threshold;
            double alpha = clamp((seconds - previous.seconds) / span, 0.0, 1.0);
            return previous.threshold + (next.threshold - previous.threshold) * alpha;
        }
        return Double.NaN;
    }

    private static double interpolateTps(List<Point> points, double seconds) {
        if (points.isEmpty() || seconds < points.get(0).seconds) return Double.NaN;
        Point previous = points.get(0);
        if (Math.abs(previous.seconds - seconds) <= EPSILON) return previous.tps;
        for (int i = 1; i < points.size(); i++) {
            Point next = points.get(i);
            if (next.seconds + EPSILON < seconds) {
                previous = next;
                continue;
            }
            double span = next.seconds - previous.seconds;
            if (span <= EPSILON) return next.tps;
            double alpha = clamp((seconds - previous.seconds) / span, 0.0, 1.0);
            return previous.tps + (next.tps - previous.tps) * alpha;
        }
        return Double.NaN;
    }

    private enum ValueKind { TPS, RPM, VSS }

    private static double medianPoint(List<Point> points, ValueKind kind) {
        List<Double> values = new ArrayList<Double>();
        for (Point point : points) {
            double value = kind == ValueKind.TPS ? point.tps
                    : kind == ValueKind.RPM ? point.rpm : point.vss;
            if (Double.isFinite(value)) values.add(Double.valueOf(value));
        }
        return median(values);
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>(values);
        Collections.sort(copy);
        int n = copy.size();
        if ((n & 1) != 0) return copy.get(n / 2).doubleValue();
        return (copy.get(n / 2 - 1).doubleValue() + copy.get(n / 2).doubleValue()) * 0.5;
    }

    private static double mad(List<Double> values) {
        double med = median(values);
        if (!Double.isFinite(med)) return Double.POSITIVE_INFINITY;
        List<Double> deviations = new ArrayList<Double>();
        for (Double value : values) deviations.add(Double.valueOf(Math.abs(value.doubleValue() - med)));
        return median(deviations);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a";
    }
    private static String f1(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "n/a";
    }
    private static String f3(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    public static final class TimingStatus {
        public final boolean deltaResolved;
        public final boolean sampleResolved;
        public final boolean applyReady;
        public final double deltaWindowMs;
        public final double currentDeltaWindowMs;
        public final boolean deltaChangeNeeded;
        public final double sampleLengthMs;
        public final double currentSampleLengthMs;
        public final boolean sampleChangeNeeded;
        public final String reason;
        public final double bestDeltaMs;
        public final double bestScore;
        public final double secondDeltaMs;
        public final double secondScore;
        public final double scoreGap;
        public final double requiredScoreGap;
        public final double currentScore;
        public final double plausibleMinDeltaMs;
        public final double plausibleMaxDeltaMs;
        public final int evidenceSets;
        public final int evidenceEvents;
        public final double medianHostIntervalMs;
        public final double medianRiseMs;
        public final boolean temporalResolutionLimited;
        public final String pedalQuality;
        public final String coaching;

        TimingStatus(boolean deltaResolved, boolean sampleResolved, boolean applyReady,
                     double deltaWindowMs, double currentDeltaWindowMs,
                     boolean deltaChangeNeeded, double sampleLengthMs,
                     double currentSampleLengthMs, boolean sampleChangeNeeded,
                     String reason, double bestDeltaMs, double bestScore,
                     double secondDeltaMs, double secondScore, double scoreGap,
                     double requiredScoreGap, double currentScore,
                     double plausibleMinDeltaMs, double plausibleMaxDeltaMs,
                     int evidenceSets, int evidenceEvents,
                     double medianHostIntervalMs, double medianRiseMs,
                     boolean temporalResolutionLimited, String pedalQuality,
                     String coaching) {
            this.deltaResolved = deltaResolved;
            this.sampleResolved = sampleResolved;
            this.applyReady = applyReady;
            this.deltaWindowMs = deltaWindowMs;
            this.currentDeltaWindowMs = currentDeltaWindowMs;
            this.deltaChangeNeeded = deltaChangeNeeded;
            this.sampleLengthMs = sampleLengthMs;
            this.currentSampleLengthMs = currentSampleLengthMs;
            this.sampleChangeNeeded = sampleChangeNeeded;
            this.reason = reason == null ? "" : reason;
            this.bestDeltaMs = bestDeltaMs;
            this.bestScore = bestScore;
            this.secondDeltaMs = secondDeltaMs;
            this.secondScore = secondScore;
            this.scoreGap = scoreGap;
            this.requiredScoreGap = requiredScoreGap;
            this.currentScore = currentScore;
            this.plausibleMinDeltaMs = plausibleMinDeltaMs;
            this.plausibleMaxDeltaMs = plausibleMaxDeltaMs;
            this.evidenceSets = evidenceSets;
            this.evidenceEvents = evidenceEvents;
            this.medianHostIntervalMs = medianHostIntervalMs;
            this.medianRiseMs = medianRiseMs;
            this.temporalResolutionLimited = temporalResolutionLimited;
            this.pedalQuality = pedalQuality == null ? "" : pedalQuality;
            this.coaching = coaching == null ? "" : coaching;
        }
    }

    public static final class Snapshot {
        public final boolean moving;
        public final boolean settling;
        public final int storedEvents;
        public final int comparableEvents;
        public final int targetComparable;
        public final double medianStep;
        public final double madStep;
        public final double tolerance;
        public final double onsetRateFloor;
        public final int totalCompleted;
        public final int rejectedSmall;
        public final int rejectedLarge;
        public final int rejectedDuration;
        public final double referencePeakTps;
        public final double[] repeatPeakTps;
        /** Pre-event TPS baseline retained only as diagnostic context. */
        public final double settleBaselineTps;
        /** Newly learned steady TPS operating point while re-arming. */
        public final double settleUpperTps;
        public final boolean settleTpsReturned;
        public final boolean settleTpsQuiet;
        public final boolean settleRpmReady;
        public final int roadComparableEvents;
        public final int stationaryComparableEvents;
        public final double roadRpmSpan;
        public final int completedSets;
        public final String lastEvent;
        public final long revision;

        Snapshot(boolean moving, boolean settling, int storedEvents, int comparableEvents,
                 int targetComparable, double medianStep, double madStep,
                 double tolerance, double onsetRateFloor, int totalCompleted,
                 int rejectedSmall, int rejectedLarge, int rejectedDuration,
                 double referencePeakTps, double[] repeatPeakTps,
                 double settleBaselineTps, double settleUpperTps,
                 boolean settleTpsReturned, boolean settleTpsQuiet,
                 boolean settleRpmReady,
                 int roadComparableEvents, int stationaryComparableEvents,
                 double roadRpmSpan, int completedSets,
                 String lastEvent, long revision) {
            this.moving = moving;
            this.settling = settling;
            this.storedEvents = storedEvents;
            this.comparableEvents = comparableEvents;
            this.targetComparable = targetComparable;
            this.medianStep = medianStep;
            this.madStep = madStep;
            this.tolerance = tolerance;
            this.onsetRateFloor = onsetRateFloor;
            this.totalCompleted = totalCompleted;
            this.rejectedSmall = rejectedSmall;
            this.rejectedLarge = rejectedLarge;
            this.rejectedDuration = rejectedDuration;
            this.referencePeakTps = referencePeakTps;
            this.repeatPeakTps = repeatPeakTps == null ? new double[0] : repeatPeakTps.clone();
            this.settleBaselineTps = settleBaselineTps;
            this.settleUpperTps = settleUpperTps;
            this.settleTpsReturned = settleTpsReturned;
            this.settleTpsQuiet = settleTpsQuiet;
            this.settleRpmReady = settleRpmReady;
            this.roadComparableEvents = roadComparableEvents;
            this.stationaryComparableEvents = stationaryComparableEvents;
            this.roadRpmSpan = roadRpmSpan;
            this.completedSets = completedSets;
            this.lastEvent = lastEvent == null ? "" : lastEvent;
            this.revision = revision;
        }

        public boolean complete() { return comparableEvents >= targetComparable; }
        public boolean readyForMovement() { return !moving && !settling; }
        public boolean roadConfirmed() {
            return comparableEvents >= ROAD_CONFIRM_EVENTS
                    && roadRpmSpan + EPSILON >= ROAD_CONFIRM_RPM_SPAN;
        }
    }

    private static final class Point {
        final double seconds;
        final double tps;
        final double rpm;
        final double threshold;
        final boolean detectorActive;
        final double vss;
        Point(double seconds, double tps, double rpm, double threshold,
              boolean detectorActive, double vss) {
            this.seconds = seconds;
            this.tps = tps;
            this.rpm = rpm;
            this.threshold = threshold;
            this.detectorActive = detectorActive;
            this.vss = vss;
        }
    }

    private static final class EventBuilder {
        final double baselineTps;
        final double baselineRpm;
        final double baselineVss;
        final double onsetSeconds;
        final List<Point> points = new ArrayList<Point>();
        double peakTps;
        double peakSeconds;
        double firstDetectorSeconds = Double.NaN;
        EventBuilder(double baselineTps, double baselineRpm, double baselineVss,
                     double onsetSeconds, List<Point> pre) {
            this.baselineTps = baselineTps;
            this.baselineRpm = baselineRpm;
            this.baselineVss = baselineVss;
            this.onsetSeconds = onsetSeconds;
            this.points.addAll(pre);
        }
        void add(Point point) { points.add(point); }
        Event build() {
            return new Event(baselineTps, baselineRpm, baselineVss, onsetSeconds,
                    peakTps, peakSeconds, firstDetectorSeconds,
                    Collections.unmodifiableList(new ArrayList<Point>(points)));
        }
    }

    private static final class Event {
        final double baselineTps;
        final double baselineRpm;
        final double baselineVss;
        final double onsetSeconds;
        final double peakTps;
        final double peakSeconds;
        final double firstDetectorSeconds;
        final double step;
        final List<Point> points;
        Event(double baselineTps, double baselineRpm, double baselineVss,
              double onsetSeconds, double peakTps, double peakSeconds,
              double firstDetectorSeconds, List<Point> points) {
            this.baselineTps = baselineTps;
            this.baselineRpm = baselineRpm;
            this.baselineVss = baselineVss;
            this.onsetSeconds = onsetSeconds;
            this.peakTps = peakTps;
            this.peakSeconds = peakSeconds;
            this.firstDetectorSeconds = firstDetectorSeconds;
            this.step = peakTps - baselineTps;
            this.points = points;
        }
    }

    private static final class EvidenceContext {
        final int roadComparable;
        final int stationaryComparable;
        final double roadRpmSpan;
        EvidenceContext(int roadComparable, int stationaryComparable, double roadRpmSpan) {
            this.roadComparable = roadComparable;
            this.stationaryComparable = stationaryComparable;
            this.roadRpmSpan = roadRpmSpan;
        }
    }

    private static final class SetSummary {
        final int events;
        final int comparable;
        final double median;
        final double mad;
        final double tolerance;
        final int roadComparable;
        final int stationaryComparable;
        final double roadRpmSpan;
        final List<Event> comparableEvents;
        SetSummary(int events, int comparable, double median, double mad,
                   double tolerance, int roadComparable,
                   int stationaryComparable, double roadRpmSpan,
                   List<Event> comparableEvents) {
            this.events = events;
            this.comparable = comparable;
            this.median = median;
            this.mad = mad;
            this.tolerance = tolerance;
            this.roadComparable = roadComparable;
            this.stationaryComparable = stationaryComparable;
            this.roadRpmSpan = roadRpmSpan;
            this.comparableEvents = comparableEvents == null
                    ? Collections.<Event>emptyList() : comparableEvents;
        }
    }

    private static final class CandidateScore {
        final double deltaMs;
        final double score;
        CandidateScore(double deltaMs, double score) {
            this.deltaMs = deltaMs;
            this.score = score;
        }
    }

    private static final class Recommendation {
        final boolean ready;
        final boolean sampleResolved;
        final boolean applyReady;
        final double deltaWindowMs;
        final double currentDeltaWindowMs;
        final double sampleLengthMs;
        final boolean sampleLengthChangeNeeded;
        final List<CandidateScore> scores;
        final String reason;
        final double bestDeltaMs;
        final double bestScore;
        final double secondDeltaMs;
        final double secondScore;
        final double scoreGap;
        final double requiredScoreGap;
        final double currentScore;
        final double plausibleMinDeltaMs;
        final double plausibleMaxDeltaMs;
        final int evidenceSets;
        final int evidenceEvents;
        final double medianHostIntervalMs;
        final double medianRiseMs;
        final boolean temporalResolutionLimited;
        final String pedalQuality;
        final String coaching;
        final double accumulatedRpmSpan;

        private Recommendation(boolean ready, boolean sampleResolved, boolean applyReady,
                               double deltaWindowMs, double currentDeltaWindowMs,
                               double sampleLengthMs, boolean sampleLengthChangeNeeded,
                               List<CandidateScore> scores, String reason,
                               double bestDeltaMs, double bestScore,
                               double secondDeltaMs, double secondScore,
                               double scoreGap, double requiredScoreGap, double currentScore,
                               double plausibleMinDeltaMs, double plausibleMaxDeltaMs,
                               int evidenceSets, int evidenceEvents,
                               double medianHostIntervalMs, double medianRiseMs,
                               boolean temporalResolutionLimited, String pedalQuality,
                               String coaching, double accumulatedRpmSpan) {
            this.ready = ready;
            this.sampleResolved = sampleResolved;
            this.applyReady = applyReady;
            this.deltaWindowMs = deltaWindowMs;
            this.currentDeltaWindowMs = currentDeltaWindowMs;
            this.sampleLengthMs = sampleLengthMs;
            this.sampleLengthChangeNeeded = sampleLengthChangeNeeded;
            this.scores = scores == null ? Collections.<CandidateScore>emptyList() : scores;
            this.reason = reason == null ? "" : reason;
            this.bestDeltaMs = bestDeltaMs;
            this.bestScore = bestScore;
            this.secondDeltaMs = secondDeltaMs;
            this.secondScore = secondScore;
            this.scoreGap = scoreGap;
            this.requiredScoreGap = requiredScoreGap;
            this.currentScore = currentScore;
            this.plausibleMinDeltaMs = plausibleMinDeltaMs;
            this.plausibleMaxDeltaMs = plausibleMaxDeltaMs;
            this.evidenceSets = evidenceSets;
            this.evidenceEvents = evidenceEvents;
            this.medianHostIntervalMs = medianHostIntervalMs;
            this.medianRiseMs = medianRiseMs;
            this.temporalResolutionLimited = temporalResolutionLimited;
            this.pedalQuality = pedalQuality == null ? "" : pedalQuality;
            this.coaching = coaching == null ? "" : coaching;
            this.accumulatedRpmSpan = accumulatedRpmSpan;
        }

        static Recommendation noEvidence(double currentDelta, double currentSampleMs,
                                         String reason, String pedalQuality) {
            return new Recommendation(false, false, false, Double.NaN, currentDelta,
                    currentSampleMs, false, Collections.<CandidateScore>emptyList(), reason,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    completedSets.size(), 0, Double.NaN, Double.NaN, false,
                    pedalQuality, reason, 0.0);
        }

        static Recommendation ambiguous(double currentDelta, double sampleMs,
                                        boolean sampleChange, List<CandidateScore> scores,
                                        String reason, CandidateScore best,
                                        CandidateScore second, CandidateScore current,
                                        double gap, double requiredGap,
                                        double plausibleMin, double plausibleMax,
                                        int evidenceSets, int evidenceEvents,
                                        double hostIntervalMs, double riseMs,
                                        boolean temporalLimited, String pedalQuality,
                                        String coaching, double rpmSpan) {
            return new Recommendation(false, true, false, Double.NaN, currentDelta,
                    sampleMs, sampleChange, scores, reason,
                    best.deltaMs, best.score,
                    second == null ? Double.NaN : second.deltaMs,
                    second == null ? Double.NaN : second.score,
                    gap, requiredGap, current == null ? Double.NaN : current.score,
                    plausibleMin, plausibleMax, evidenceSets, evidenceEvents,
                    hostIntervalMs, riseMs, temporalLimited, pedalQuality,
                    coaching, rpmSpan);
        }

        static Recommendation resolved(boolean applyReady, double selected,
                                       double currentDelta, double sampleMs,
                                       boolean sampleChange, List<CandidateScore> scores,
                                       CandidateScore best, CandidateScore second,
                                       CandidateScore current, double gap,
                                       double requiredGap, double plausibleMin,
                                       double plausibleMax, int evidenceSets,
                                       int evidenceEvents, double hostIntervalMs,
                                       double riseMs, boolean temporalLimited,
                                       String pedalQuality, String coaching,
                                       double rpmSpan) {
            return new Recommendation(true, true, applyReady, selected, currentDelta,
                    sampleMs, sampleChange, scores, "",
                    best.deltaMs, best.score,
                    second == null ? Double.NaN : second.deltaMs,
                    second == null ? Double.NaN : second.score,
                    gap, requiredGap, current == null ? Double.NaN : current.score,
                    plausibleMin, plausibleMax, evidenceSets, evidenceEvents,
                    hostIntervalMs, riseMs, temporalLimited, pedalQuality,
                    coaching, rpmSpan);
        }
    }
}
