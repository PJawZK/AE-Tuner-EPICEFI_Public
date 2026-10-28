package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only replay/analyzer for historical falling-TPS evidence.
 *
 * This deliberately does not use the current Decel threshold to decide whether
 * an event is a normal correction or a clear release.  The current threshold is
 * replayed only after classification so historical analysis does not become
 * circular.  Ambiguous middle-ground events remain ambiguous instead of being
 * forced into either evidence population.
 *
 * No ProposalWritePlan is produced here.  Imported/replayed evidence must first
 * prove useful before it can be considered by the existing reviewed Decel
 * recommendation/write path.
 */
public final class DecelHistoricalEvidenceAnalyzer {
    static final int QUIET_TARGET = 80;
    static final double QUIET_MIN_SECONDS = 1.50;
    static final int MIN_EVENTS_PER_CLASS = 3;

    static final String CLASS_NORMAL_CORRECTION = "NORMAL_CORRECTION";
    static final String CLASS_DECEL_RELEASE = "DECEL_RELEASE";
    static final String CLASS_AMBIGUOUS = "AMBIGUOUS";

    private static final double QUIET_DELTA_LIMIT = 0.05;
    private static final double QUIET_GAP = 0.20;
    private static final double EVENT_GAP = 0.18;
    private static final double MIN_MOVE_RATE = 2.0;
    private static final double MIN_RELEASE_RATE = 6.0;
    private static final double MOVE_QUIET_MULT = 0.75;
    private static final double RELEASE_QUIET_MULT = 5.0;
    private static final double NORMAL_MAX_EXCURSION = 1.25;
    private static final double NORMAL_MAX_RELEASE_RATE_FRACTION = 0.80;
    private static final double RELEASE_MIN_EXCURSION = 0.50;
    private static final double NOISE_GUARD = 1.25;
    private static final double RELEASE_GUARD = 0.80;
    private static final double MIN_SEPARATION = 0.02;

    public static final class Event {
        public final String eventClass;
        public final double startSeconds;
        public final double endSeconds;
        public final double startRpm;
        public final double peakMagnitude;
        public final double peakClosingRate;
        public final double excursion;
        public final double currentThreshold;
        public final boolean currentThresholdCrossed;
        public final boolean decelActiveObserved;

        Event(String eventClass, double startSeconds, double endSeconds,
              double startRpm, double peakMagnitude, double peakClosingRate,
              double excursion, double currentThreshold,
              boolean currentThresholdCrossed, boolean decelActiveObserved) {
            this.eventClass = eventClass;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.startRpm = startRpm;
            this.peakMagnitude = peakMagnitude;
            this.peakClosingRate = peakClosingRate;
            this.excursion = excursion;
            this.currentThreshold = currentThreshold;
            this.currentThresholdCrossed = currentThresholdCrossed;
            this.decelActiveObserved = decelActiveObserved;
        }
    }

    public static final class BinSummary {
        public final double rpm;
        public final int normalCorrections;
        public final int decelReleases;
        public final int ambiguousEvents;
        public final int normalCrossings;
        public final int releaseMisses;
        public final double normalP95;
        public final double releaseP25;
        public final double safeLow;
        public final double safeHigh;
        public final boolean separated;
        public final List<Event> events;

        BinSummary(double rpm, int normalCorrections, int decelReleases,
                   int ambiguousEvents, int normalCrossings, int releaseMisses,
                   double normalP95, double releaseP25,
                   double safeLow, double safeHigh, boolean separated,
                   List<Event> events) {
            this.rpm = rpm;
            this.normalCorrections = normalCorrections;
            this.decelReleases = decelReleases;
            this.ambiguousEvents = ambiguousEvents;
            this.normalCrossings = normalCrossings;
            this.releaseMisses = releaseMisses;
            this.normalP95 = normalP95;
            this.releaseP25 = releaseP25;
            this.safeLow = safeLow;
            this.safeHigh = safeHigh;
            this.separated = separated;
            this.events = Collections.unmodifiableList(new ArrayList<Event>(events));
        }
    }

    public static final class Result {
        public final String reportText;
        public final int validSamples;
        public final boolean quietCalibrationLocked;
        public final int quietSamples;
        public final double quietDurationSeconds;
        public final double quietRateP95;
        public final double quietRateP99;
        public final double movementRateFloor;
        public final double releaseRateFloor;
        public final int movementEvents;
        public final int normalCorrections;
        public final int decelReleases;
        public final int ambiguousEvents;
        public final int separatedBins;
        public final List<BinSummary> bins;

        Result(String reportText, int validSamples,
               boolean quietCalibrationLocked, int quietSamples,
               double quietDurationSeconds, double quietRateP95,
               double quietRateP99, double movementRateFloor,
               double releaseRateFloor, int movementEvents,
               int normalCorrections, int decelReleases,
               int ambiguousEvents, int separatedBins,
               List<BinSummary> bins) {
            this.reportText = reportText == null ? "" : reportText;
            this.validSamples = validSamples;
            this.quietCalibrationLocked = quietCalibrationLocked;
            this.quietSamples = quietSamples;
            this.quietDurationSeconds = quietDurationSeconds;
            this.quietRateP95 = quietRateP95;
            this.quietRateP99 = quietRateP99;
            this.movementRateFloor = movementRateFloor;
            this.releaseRateFloor = releaseRateFloor;
            this.movementEvents = movementEvents;
            this.normalCorrections = normalCorrections;
            this.decelReleases = decelReleases;
            this.ambiguousEvents = ambiguousEvents;
            this.separatedBins = separatedBins;
            this.bins = Collections.unmodifiableList(new ArrayList<BinSummary>(bins));
        }
    }

    private static final class MutableBin {
        final List<Double> normalPeaks = new ArrayList<Double>();
        final List<Double> releasePeaks = new ArrayList<Double>();
        final List<Event> events = new ArrayList<Event>();
        int normalCrossings;
        int releaseMisses;
        int ambiguous;
    }

    private static final class Movement {
        boolean active;
        double startSeconds = Double.NaN;
        double lastSeconds = Double.NaN;
        double startRpm = Double.NaN;
        double startTps = Double.NaN;
        double minTps = Double.NaN;
        double peakMagnitude = Double.NaN;
        double peakClosingRate = Double.NaN;
        double threshold = Double.NaN;
        boolean decelActive;

        void startOrExtend(LiveSample sample, double magnitude,
                           double closingRate, double threshold,
                           double previousTps) {
            if (!active) {
                active = true;
                startSeconds = sample.getSeconds();
                startRpm = sample.get(ChannelRole.RPM);
                startTps = finite(previousTps) ? previousTps : sample.get(ChannelRole.TPS);
                this.threshold = threshold;
            }
            lastSeconds = sample.getSeconds();
            peakMagnitude = !finite(peakMagnitude)
                    ? magnitude : Math.max(peakMagnitude, magnitude);
            peakClosingRate = !finite(peakClosingRate)
                    ? closingRate : Math.max(peakClosingRate, closingRate);
            double tps = sample.get(ChannelRole.TPS);
            if (finite(tps)) minTps = !finite(minTps) ? tps : Math.min(minTps, tps);
            if (finite(startTps)) minTps = !finite(minTps) ? startTps : Math.min(minTps, startTps);
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelActive = true;
        }

        void tail(LiveSample sample) {
            if (!active) return;
            double tps = sample.get(ChannelRole.TPS);
            if (finite(tps)) minTps = !finite(minTps) ? tps : Math.min(minTps, tps);
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelActive = true;
        }

        double excursion() {
            return finite(startTps) && finite(minTps)
                    ? Math.max(0.0, startTps - minTps) : Double.NaN;
        }

        void clear() {
            active = false;
            startSeconds = lastSeconds = startRpm = startTps = minTps = Double.NaN;
            peakMagnitude = peakClosingRate = threshold = Double.NaN;
            decelActive = false;
        }
    }

    private DecelHistoricalEvidenceAnalyzer() { }

    public static Result analyze(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        if (snapshot == null || !snapshot.hasDecelDetectionSettings()) {
            return unavailable("Read Working Tune before historical Decel analysis.");
        }
        if (evidence == null || evidence.isEmpty()) {
            return unavailable("No historical samples are available for Decel analysis.");
        }
        double[] rpmBins = snapshot.getDecelThresholdRpmBins();
        if (rpmBins.length == 0 || rpmBins.length != snapshot.getDecelThresholdValues().length) {
            return unavailable("Working-tune Decel threshold curve is unavailable or invalid.");
        }

        List<Double> quietRates = new ArrayList<Double>();
        double quietStart = Double.NaN;
        double quietLast = Double.NaN;
        double quietDuration = 0.0;
        boolean quietLocked = false;
        double quietP95 = Double.NaN;
        double quietP99 = Double.NaN;
        int validSamples = 0;

        for (LiveSample sample : evidence) {
            GuidedAnalysisCancellation.check();
            if (!valid(sample)) continue;
            validSamples++;
            if (quietLocked) continue;
            double seconds = sample.getSeconds();
            double delta = sample.get(ChannelRole.DELTA_TPS);
            if (Math.abs(delta) > QUIET_DELTA_LIMIT) {
                quietRates.clear();
                quietStart = quietLast = Double.NaN;
                quietDuration = 0.0;
                continue;
            }
            if (finite(quietLast) && seconds - quietLast > QUIET_GAP) {
                quietRates.clear();
                quietStart = Double.NaN;
            }
            if (!finite(quietStart)) quietStart = seconds;
            quietLast = seconds;
            quietRates.add(Math.abs(sample.getTpsDot()));
            quietDuration = Math.max(0.0, quietLast - quietStart);
            if (quietRates.size() >= QUIET_TARGET && quietDuration >= QUIET_MIN_SECONDS) {
                quietP95 = percentile(quietRates, 0.95);
                quietP99 = percentile(quietRates, 0.99);
                quietLocked = true;
            }
        }
        if (!quietLocked) {
            quietP95 = percentile(quietRates, 0.95);
            quietP99 = percentile(quietRates, 0.99);
        }

        double moveFloor = Math.max(MIN_MOVE_RATE, safe(quietP95) * MOVE_QUIET_MULT);
        double releaseFloor = Math.max(MIN_RELEASE_RATE, safe(quietP95) * RELEASE_QUIET_MULT);
        MutableBin[] mutable = new MutableBin[rpmBins.length];
        for (int i = 0; i < mutable.length; i++) mutable[i] = new MutableBin();

        Movement movement = new Movement();
        double previousTps = Double.NaN;
        int movementEvents = 0;
        int normalCount = 0;
        int releaseCount = 0;
        int ambiguousCount = 0;
        int engineStateExcludedSamples = 0;

        if (quietLocked) {
            for (LiveSample sample : evidence) {
            GuidedAnalysisCancellation.check();
                if (!valid(sample)) {
                    int[] added = finish(movement, mutable, rpmBins, releaseFloor);
                    movementEvents += added[0]; normalCount += added[1];
                    releaseCount += added[2]; ambiguousCount += added[3];
                    previousTps = Double.NaN;
                    continue;
                }
                if (!DecelSignalSamples.physicalEvidenceAllowed(sample)) {
                    int[] added = finish(movement, mutable, rpmBins, releaseFloor);
                    movementEvents += added[0]; normalCount += added[1];
                    releaseCount += added[2]; ambiguousCount += added[3];
                    previousTps = Double.NaN;
                    engineStateExcludedSamples++;
                    continue;
                }
                double seconds = sample.getSeconds();
                if (movement.active && finite(movement.lastSeconds)
                        && seconds - movement.lastSeconds >= EVENT_GAP) {
                    int[] added = finish(movement, mutable, rpmBins, releaseFloor);
                    movementEvents += added[0]; normalCount += added[1];
                    releaseCount += added[2]; ambiguousCount += added[3];
                }
                double delta = sample.get(ChannelRole.DELTA_TPS);
                double closingRate = -sample.getTpsDot();
                double rpm = sample.get(ChannelRole.RPM);
                double threshold = snapshot.decelThresholdForRpm(rpm);
                if (delta < 0.0 && closingRate > moveFloor) {
                    movement.startOrExtend(sample, -delta, closingRate, threshold, previousTps);
                } else if (movement.active && finite(movement.lastSeconds)
                        && seconds - movement.lastSeconds < EVENT_GAP) {
                    movement.tail(sample);
                }
                previousTps = sample.get(ChannelRole.TPS);
            }
        }
        int[] added = finish(movement, mutable, rpmBins, releaseFloor);
        movementEvents += added[0]; normalCount += added[1];
        releaseCount += added[2]; ambiguousCount += added[3];

        List<BinSummary> bins = new ArrayList<BinSummary>();
        int separatedBins = 0;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < mutable.length; i++) {
            MutableBin bin = mutable[i];
            double normalP95 = percentile(bin.normalPeaks, 0.95);
            double releaseP25 = percentile(bin.releasePeaks, 0.25);
            double low = finite(normalP95) ? normalP95 * NOISE_GUARD : Double.NaN;
            double high = finite(releaseP25) ? releaseP25 * RELEASE_GUARD : Double.NaN;
            boolean separated = bin.normalPeaks.size() >= MIN_EVENTS_PER_CLASS
                    && bin.releasePeaks.size() >= MIN_EVENTS_PER_CLASS
                    && separated(low, high);
            if (separated) separatedBins++;
            BinSummary summary = new BinSummary(rpmBins[i], bin.normalPeaks.size(),
                    bin.releasePeaks.size(), bin.ambiguous,
                    bin.normalCrossings, bin.releaseMisses,
                    normalP95, releaseP25, low, high, separated, bin.events);
            bins.add(summary);
            if (!bin.events.isEmpty()) {
                rows.append("  ").append(Math.round(rpmBins[i])).append(" RPM: normal ")
                        .append(bin.normalPeaks.size()).append(", release ")
                        .append(bin.releasePeaks.size()).append(", ambiguous ")
                        .append(bin.ambiguous).append(", normal crossings ")
                        .append(bin.normalCrossings).append(", release misses ")
                        .append(bin.releaseMisses).append(", normal p95 ")
                        .append(fmt(normalP95)).append(", release p25 ")
                        .append(fmt(releaseP25)).append(", safe window ")
                        .append(fmt(low)).append("..").append(fmt(high))
                        .append(" | ").append(separated ? "SEPARATED" : "WITHHOLD")
                        .append('\n');
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("DECEL HISTORICAL REPLAY — READ ONLY\n")
                .append("Classification is based on falling-event physical shape, not on the current Decel threshold. The current threshold is replayed only after classification.\n")
                .append("Valid samples: ").append(validSamples).append('\n')
                .append("Quiet baseline: ").append(quietRates.size()).append(" sample(s), ")
                .append(fmt(quietDuration)).append(" s | ")
                .append(quietLocked ? "LOCKED" : "INSUFFICIENT").append('\n')
                .append("Quiet TPS-rate p95/p99: ").append(fmt(quietP95)).append(" / ")
                .append(fmt(quietP99)).append(" %/s\n")
                .append("Movement/release rate floors: ").append(fmt(moveFloor)).append(" / ")
                .append(fmt(releaseFloor)).append(" %/s\n")
                .append("Engine-state event qualification excluded ")
                .append(engineStateExcludedSamples)
                .append(" valid sample(s) from physical movement classification; quiet-signal calibration remains unchanged.\n")
                .append("Falling movement events: ").append(movementEvents)
                .append(" | normal corrections: ").append(normalCount)
                .append(" | clear releases: ").append(releaseCount)
                .append(" | ambiguous: ").append(ambiguousCount).append('\n')
                .append("\nRPM-BIN REPLAY\n").append(rows)
                .append("Separated bins: ").append(separatedBins).append(" / ")
                .append(rpmBins.length).append(".\n")
                .append("Boundary: historical replay is evidence-only. It creates no ProposalWritePlan, changes no Working Tune value, does not tune hold cycles, and never Burns the ECU.");

        return new Result(report.toString(), validSamples, quietLocked,
                quietRates.size(), quietDuration, quietP95, quietP99,
                moveFloor, releaseFloor, movementEvents, normalCount,
                releaseCount, ambiguousCount, separatedBins, bins);
    }

    private static int[] finish(Movement movement, MutableBin[] bins,
                                double[] rpmBins, double releaseFloor) {
        if (movement == null || !movement.active) return new int[]{0,0,0,0};
        double excursion = movement.excursion();
        String eventClass = classify(excursion, movement.peakClosingRate, releaseFloor);
        int index = binForRpm(rpmBins, movement.startRpm);
        MutableBin bin = bins[index];
        boolean crossed = finite(movement.threshold) && movement.threshold > 0.0
                && finite(movement.peakMagnitude)
                && movement.peakMagnitude > movement.threshold;
        Event event = new Event(eventClass, movement.startSeconds,
                movement.lastSeconds, movement.startRpm,
                movement.peakMagnitude, movement.peakClosingRate,
                excursion, movement.threshold, crossed, movement.decelActive);
        bin.events.add(event);
        int normal = 0, release = 0, ambiguous = 0;
        if (CLASS_NORMAL_CORRECTION.equals(eventClass)) {
            normal = 1;
            bin.normalPeaks.add(movement.peakMagnitude);
            if (crossed) bin.normalCrossings++;
        } else if (CLASS_DECEL_RELEASE.equals(eventClass)) {
            release = 1;
            bin.releasePeaks.add(movement.peakMagnitude);
            if (finite(movement.threshold) && movement.threshold > 0.0 && !crossed) {
                bin.releaseMisses++;
            }
        } else {
            ambiguous = 1;
            bin.ambiguous++;
        }
        movement.clear();
        return new int[]{1, normal, release, ambiguous};
    }

    private static String classify(double excursion, double closingRate,
                                   double releaseFloor) {
        if (!finite(excursion) || !finite(closingRate)) return CLASS_AMBIGUOUS;
        if (excursion <= NORMAL_MAX_EXCURSION
                && closingRate < releaseFloor * NORMAL_MAX_RELEASE_RATE_FRACTION) {
            return CLASS_NORMAL_CORRECTION;
        }
        if (excursion >= RELEASE_MIN_EXCURSION && closingRate >= releaseFloor) {
            return CLASS_DECEL_RELEASE;
        }
        return CLASS_AMBIGUOUS;
    }

    private static boolean valid(LiveSample sample) {
        return sample != null
                && finite(sample.getSeconds())
                && finite(sample.get(ChannelRole.RPM))
                && finite(sample.get(ChannelRole.TPS))
                && finite(sample.get(ChannelRole.DELTA_TPS))
                && finite(sample.getTpsDot());
    }

    private static boolean separated(double low, double high) {
        if (!finite(low) || !finite(high)) return false;
        double required = Math.max(MIN_SEPARATION, Math.max(0.0, low) * 0.15);
        return high - low >= required;
    }

    private static int binForRpm(double[] bins, double rpm) {
        int best = 0;
        double distance = Math.abs(rpm - bins[0]);
        for (int i = 1; i < bins.length; i++) {
            double next = Math.abs(rpm - bins[i]);
            if (next < distance) { best = i; distance = next; }
        }
        return best;
    }

    private static double percentile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>();
        for (Double value : values) {
            if (value != null && finite(value.doubleValue())) copy.add(value);
        }
        if (copy.isEmpty()) return Double.NaN;
        Collections.sort(copy);
        if (copy.size() == 1) return copy.get(0).doubleValue();
        double position = Math.max(0.0, Math.min(1.0, q)) * (copy.size() - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        if (low == high) return copy.get(low).doubleValue();
        double fraction = position - low;
        return copy.get(low).doubleValue()
                + (copy.get(high).doubleValue() - copy.get(low).doubleValue()) * fraction;
    }

    private static Result unavailable(String reason) {
        String text = "DECEL HISTORICAL REPLAY — READ ONLY\n"
                + (reason == null ? "Historical Decel evidence is unavailable." : reason)
                + "\nNo ProposalWritePlan is produced and no Working Tune value is changed.";
        return new Result(text, 0, false, 0, 0.0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                0, 0, 0, 0, 0, Collections.<BinSummary>emptyList());
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
    private static double safe(double value) { return finite(value) ? value : 0.0; }
    private static String fmt(double value) {
        return finite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
