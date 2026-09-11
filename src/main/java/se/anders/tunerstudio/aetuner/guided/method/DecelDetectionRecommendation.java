package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Evidence-qualified recommendation policy for the shared falling-TPS detector.
 *
 * Firmware authority is deliberately preserved here:
 * - Fuel: TPS AE change is signed; closing movement is negative.
 * - tpsDecelThresholdValue stores a positive magnitude.
 * - firmware edge condition is decelTps < -DecelThreshold.
 * - a zero threshold disables detection at that RPM.
 * - tpsDecelHoldCycles is read-only in this first recommendation slice.
 */
final class DecelDetectionRecommendation {
    static final int QUIET_CALIBRATION_TARGET = 80;
    static final double QUIET_CALIBRATION_MIN_SECONDS = 1.50;
    static final int MIN_NORMAL_EVENTS_PER_BIN = 3;
    static final int MIN_RELEASE_EVENTS_PER_BIN = 3;
    static final String CLASS_NORMAL_CORRECTION = "NORMAL_CORRECTION";
    static final String CLASS_DECEL_RELEASE = "DECEL_RELEASE";
    static final String CLASS_LOCKED = "LOCKED";

    private static final double EVENT_GAP = 0.18;
    private static final double QUIET_GAP = 0.20;
    private static final double MIN_MOVE_RATE = 2.0;
    private static final double MIN_RELEASE_RATE = 6.0;
    private static final double RELEASE_QUIET_MULT = 5.0;
    private static final double MOVE_QUIET_MULT = 0.75;
    private static final double NOISE_GUARD = 1.25;
    private static final double RELEASE_GUARD = 0.80;
    private static final double MIN_SEPARATION = 0.02;
    private static final double MAX_MOVE_FRACTION = 0.25;
    private static final double MIN_MAX_MOVE = 0.05;
    private static final double NORMAL_MAX_EXCURSION = 1.25;
    private static final double NORMAL_RATE_FRACTION = 0.80;
    private static final double RELEASE_MIN_EXCURSION = 0.50;
    private static final double RELEASE_TINY_RATIO = 0.20;
    private static final double ZERO_THRESHOLD_QUIET_LIMIT = 0.05;

    static final class EventObservation {
        final String eventClass;
        final double peakMagnitude;
        final double peakRpm;
        final double peakThreshold;
        final double peakClosingRate;
        final double excursion;
        final boolean decelActiveObserved;

        EventObservation(String eventClass, double peakMagnitude, double peakRpm,
                         double peakThreshold, double peakClosingRate,
                         double excursion, boolean decelActiveObserved) {
            this.eventClass = nn(eventClass);
            this.peakMagnitude = peakMagnitude;
            this.peakRpm = peakRpm;
            this.peakThreshold = peakThreshold;
            this.peakClosingRate = peakClosingRate;
            this.excursion = excursion;
            this.decelActiveObserved = decelActiveObserved;
        }

        double ratio() {
            return finite(peakMagnitude) && finite(peakThreshold) && peakThreshold > 1.0e-6
                    ? peakMagnitude / peakThreshold : Double.NaN;
        }
    }

    static final class BinDecision {
        final double rpm;
        final String regionLabel;
        final double current;
        final int normalEvents;
        final int releaseEvents;
        final int rejectedEvents;
        final int normalCrossingEvents;
        final int releaseBelowThresholdEvents;
        final int nearThresholdReleaseEvents;
        final int normalActiveObservedEvents;
        final int releaseActiveObservedEvents;
        final double normalP95;
        final double releaseP25;
        final double low;
        final double high;
        final double separationGap;
        final double requiredSeparation;
        final boolean validated;
        final boolean currentDisabled;
        final boolean eligible;
        final double proposed;
        final boolean changed;
        final String requestedClass;
        final String status;
        final String lastRejectedReason;
        final List<EventObservation> normalObservations;
        final List<EventObservation> releaseObservations;

        BinDecision(double rpm, String regionLabel, double current,
                    int normalEvents, int releaseEvents, int rejectedEvents,
                    int normalCrossingEvents, int releaseBelowThresholdEvents,
                    int nearThresholdReleaseEvents, int normalActiveObservedEvents,
                    int releaseActiveObservedEvents, double normalP95,
                    double releaseP25, double low, double high,
                    double separationGap, double requiredSeparation,
                    boolean validated, boolean currentDisabled, boolean eligible,
                    double proposed, boolean changed, String requestedClass,
                    String status, String lastRejectedReason,
                    List<EventObservation> normalObservations,
                    List<EventObservation> releaseObservations) {
            this.rpm = rpm;
            this.regionLabel = nn(regionLabel);
            this.current = current;
            this.normalEvents = normalEvents;
            this.releaseEvents = releaseEvents;
            this.rejectedEvents = rejectedEvents;
            this.normalCrossingEvents = normalCrossingEvents;
            this.releaseBelowThresholdEvents = releaseBelowThresholdEvents;
            this.nearThresholdReleaseEvents = nearThresholdReleaseEvents;
            this.normalActiveObservedEvents = normalActiveObservedEvents;
            this.releaseActiveObservedEvents = releaseActiveObservedEvents;
            this.normalP95 = normalP95;
            this.releaseP25 = releaseP25;
            this.low = low;
            this.high = high;
            this.separationGap = separationGap;
            this.requiredSeparation = requiredSeparation;
            this.validated = validated;
            this.currentDisabled = currentDisabled;
            this.eligible = eligible;
            this.proposed = proposed;
            this.changed = changed;
            this.requestedClass = nn(requestedClass);
            this.status = nn(status);
            this.lastRejectedReason = nn(lastRejectedReason);
            this.normalObservations = immutable(normalObservations);
            this.releaseObservations = immutable(releaseObservations);
        }
    }

    static final class EvidenceSummary {
        final int validSamples;
        final boolean calibrationFrozen;
        final int quietTarget;
        final int quietSamples;
        final double quietDurationSeconds;
        final double quietRateP95;
        final double quietRateP99;
        final double movementRateFloor;
        final double releaseRateFloor;
        final int rawCrossingSamples;
        final int movementEvents;
        final int rejectedEvents;
        final int validatedBins;
        final int normalCrossingEvents;
        final int releaseBelowThresholdEvents;
        final int decelActiveSamples;
        final List<BinDecision> bins;

        EvidenceSummary(int validSamples, boolean calibrationFrozen,
                        int quietTarget, int quietSamples,
                        double quietDurationSeconds, double quietRateP95,
                        double quietRateP99, double movementRateFloor,
                        double releaseRateFloor, int rawCrossingSamples,
                        int movementEvents, int rejectedEvents,
                        int validatedBins, int normalCrossingEvents,
                        int releaseBelowThresholdEvents, int decelActiveSamples,
                        List<BinDecision> bins) {
            this.validSamples = validSamples;
            this.calibrationFrozen = calibrationFrozen;
            this.quietTarget = quietTarget;
            this.quietSamples = quietSamples;
            this.quietDurationSeconds = quietDurationSeconds;
            this.quietRateP95 = quietRateP95;
            this.quietRateP99 = quietRateP99;
            this.movementRateFloor = movementRateFloor;
            this.releaseRateFloor = releaseRateFloor;
            this.rawCrossingSamples = rawCrossingSamples;
            this.movementEvents = movementEvents;
            this.rejectedEvents = rejectedEvents;
            this.validatedBins = validatedBins;
            this.normalCrossingEvents = normalCrossingEvents;
            this.releaseBelowThresholdEvents = releaseBelowThresholdEvents;
            this.decelActiveSamples = decelActiveSamples;
            this.bins = Collections.unmodifiableList(new ArrayList<BinDecision>(bins));
        }

        static EvidenceSummary empty() {
            return new EvidenceSummary(0, false, QUIET_CALIBRATION_TARGET, 0,
                    0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    0, 0, 0, 0, 0, 0, 0,
                    Collections.<BinDecision>emptyList());
        }
    }

    static final class Result {
        final String reviewText;
        final ProposalWritePlan plan;
        final int eligibleBins;
        final int changedBins;
        final EvidenceSummary summary;

        Result(String reviewText, ProposalWritePlan plan, int eligibleBins,
               int changedBins, EvidenceSummary summary) {
            this.reviewText = nn(reviewText);
            this.plan = plan;
            this.eligibleBins = eligibleBins;
            this.changedBins = changedBins;
            this.summary = summary == null ? EvidenceSummary.empty() : summary;
        }
    }

    private static final class BinEvidence {
        final List<Double> normalPeaks = new ArrayList<Double>();
        final List<Double> releasePeaks = new ArrayList<Double>();
        final List<EventObservation> normalObservations = new ArrayList<EventObservation>();
        final List<EventObservation> releaseObservations = new ArrayList<EventObservation>();
        int crossings;
        int misses;
        int near;
        int rejected;
        int normalActive;
        int releaseActive;
        boolean locked;
        String lastReject = "";
    }

    private static final class MovementEvent {
        boolean active;
        double last = Double.NaN;
        double magnitude = Double.NaN;
        double rpm = Double.NaN;
        double threshold = Double.NaN;
        double closingRate = Double.NaN;
        double startTps = Double.NaN;
        double minTps = Double.NaN;
        boolean decelActiveObserved;

        void move(LiveSample sample, double magnitude, double threshold,
                  double closingRate, double previousTps) {
            if (!active) {
                startTps = finite(previousTps) ? previousTps : sample.get(ChannelRole.TPS);
            }
            active = true;
            last = sample.getSeconds();
            if (!finite(this.closingRate) || closingRate > this.closingRate) {
                this.closingRate = closingRate;
            }
            if (!finite(this.magnitude) || magnitude > this.magnitude) {
                this.magnitude = magnitude;
                rpm = sample.get(ChannelRole.RPM);
                this.threshold = threshold;
            }
            tps(sample.get(ChannelRole.TPS));
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelActiveObserved = true;
        }

        void tail(LiveSample sample) {
            if (!active) return;
            tps(sample.get(ChannelRole.TPS));
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelActiveObserved = true;
        }

        private void tps(double value) {
            if (!finite(value)) return;
            if (!finite(minTps) || value < minTps) minTps = value;
            if (finite(startTps)) minTps = Math.min(minTps, startTps);
        }

        double excursion() {
            return finite(startTps) && finite(minTps)
                    ? Math.max(0.0, startTps - minTps) : Double.NaN;
        }

        void clear() {
            active = false;
            last = magnitude = rpm = threshold = closingRate = startTps = minTps = Double.NaN;
            decelActiveObserved = false;
        }
    }

    private static final class Counts {
        int moves;
        int rejected;
        int normalCrossings;
        int releaseMisses;
    }

    private DecelDetectionRecommendation() { }

    static Result evaluate(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        if (snapshot == null) return unavailable("Read Working Tune before Decel Detection recommendation.");
        if (!snapshot.hasDecelDetectionSettings()) {
            return unavailable("Working-tune decel threshold curve / hold-cycle baseline is unavailable or invalid.");
        }
        double[] rpmBins = snapshot.getDecelThresholdRpmBins();
        double[] current = snapshot.getDecelThresholdValues();
        if (rpmBins.length == 0 || rpmBins.length != current.length) {
            return unavailable("Working-tune decel threshold curve is unavailable or invalid.");
        }
        if (evidence == null || evidence.isEmpty()) {
            return unavailable("No retained Decel Detection samples are available.");
        }

        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES);
        double scale = definition == null ? 0.001 : definition.getScale();
        double minimum = definition == null ? 0.0 : definition.getMinimum();
        double maximum = definition == null ? 60.0 : definition.getMaximum();

        List<Double> quietRates = new ArrayList<Double>();
        boolean frozen = false;
        double freezeSeconds = Double.NaN;
        double quietStart = Double.NaN;
        double quietLast = Double.NaN;
        double quietDuration = 0.0;
        double quietP95 = Double.NaN;
        double quietP99 = Double.NaN;
        int validSamples = 0;
        int decelActiveSamples = 0;

        for (LiveSample sample : evidence) {
            if (sample == null) continue;
            double rpm = sample.get(ChannelRole.RPM);
            double delta = sample.get(ChannelRole.DELTA_TPS);
            double rate = sample.getTpsDot();
            double seconds = sample.getSeconds();
            double threshold = snapshot.decelThresholdForRpm(rpm);
            if (!finite(rpm) || !finite(delta) || !finite(rate)
                    || !finite(seconds) || !finite(threshold)) continue;
            validSamples++;
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelActiveSamples++;
            if (frozen) continue;

            double quietLimit = Math.max(ZERO_THRESHOLD_QUIET_LIMIT,
                    Math.max(0.0, threshold) * 0.35);
            boolean quiet = Math.abs(delta) <= quietLimit;
            if (!quiet) {
                quietRates.clear();
                quietStart = quietLast = Double.NaN;
                continue;
            }
            if (finite(quietLast) && seconds - quietLast > QUIET_GAP) {
                quietRates.clear();
                quietStart = Double.NaN;
            }
            if (!finite(quietStart)) quietStart = seconds;
            quietLast = seconds;
            quietRates.add(Math.abs(rate));
            quietDuration = Math.max(0.0, quietLast - quietStart);
            if (quietRates.size() >= QUIET_CALIBRATION_TARGET
                    && quietDuration >= QUIET_CALIBRATION_MIN_SECONDS) {
                quietP95 = percentile(quietRates, 0.95);
                quietP99 = percentile(quietRates, 0.99);
                frozen = true;
                freezeSeconds = seconds;
            }
        }

        if (!frozen) {
            quietP95 = percentile(quietRates, 0.95);
            quietP99 = percentile(quietRates, 0.99);
            quietDuration = finite(quietStart) && finite(quietLast)
                    ? quietLast - quietStart : 0.0;
        }

        double releaseRateFloor = Math.max(MIN_RELEASE_RATE,
                safe(quietP95) * RELEASE_QUIET_MULT);
        double moveRateFloor = Math.max(MIN_MOVE_RATE,
                safe(quietP95) * MOVE_QUIET_MULT);

        BinEvidence[] bins = new BinEvidence[rpmBins.length];
        for (int i = 0; i < bins.length; i++) bins[i] = new BinEvidence();
        MovementEvent event = new MovementEvent();
        Counts counts = new Counts();
        int rawCrossingSamples = 0;
        double previousTps = Double.NaN;

        if (frozen) {
            for (LiveSample sample : evidence) {
                if (sample == null) continue;
                double rpm = sample.get(ChannelRole.RPM);
                double tps = sample.get(ChannelRole.TPS);
                double delta = sample.get(ChannelRole.DELTA_TPS);
                double rate = sample.getTpsDot();
                double seconds = sample.getSeconds();
                double threshold = snapshot.decelThresholdForRpm(rpm);
                if (!finite(rpm) || !finite(delta) || !finite(rate)
                        || !finite(seconds) || !finite(threshold)) {
                    finish(event, bins, rpmBins, releaseRateFloor, scale, counts);
                    previousTps = Double.NaN;
                    continue;
                }
                if (finite(freezeSeconds) && seconds <= freezeSeconds) {
                    previousTps = tps;
                    continue;
                }
                if (threshold > 0.0 && delta < -threshold) rawCrossingSamples++;
                if (event.active && finite(event.last) && seconds - event.last >= EVENT_GAP) {
                    finish(event, bins, rpmBins, releaseRateFloor, scale, counts);
                }
                double closingRate = -rate;
                if (delta < 0.0 && closingRate > moveRateFloor) {
                    event.move(sample, -delta, threshold, closingRate, previousTps);
                } else if (event.active && finite(event.last)
                        && seconds - event.last < EVENT_GAP) {
                    event.tail(sample);
                }
                previousTps = tps;
            }
        }
        finish(event, bins, rpmBins, releaseRateFloor, scale, counts);

        List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
        List<BinDecision> decisions = new ArrayList<BinDecision>();
        int eligibleBins = 0;
        int validatedBins = 0;
        StringBuilder rows = new StringBuilder();

        for (int i = 0; i < bins.length; i++) {
            BinEvidence bin = bins[i];
            double normalP95 = percentile(bin.normalPeaks, 0.95);
            double releaseP25 = percentile(bin.releasePeaks, 0.25);
            double low = finite(normalP95) ? normalP95 * NOISE_GUARD + scale : Double.NaN;
            double high = finite(releaseP25) ? releaseP25 * RELEASE_GUARD : Double.NaN;
            double gap = finite(low) && finite(high) ? high - low : Double.NaN;
            double required = finite(low)
                    ? Math.max(MIN_SEPARATION, Math.max(0.0, low) * 0.15)
                    : MIN_SEPARATION;
            boolean locked = bin.locked;
            if (locked) validatedBins++;
            boolean currentDisabled = current[i] <= scale * 0.5;
            boolean nonzeroWindow = finite(high) && high >= scale;
            boolean eligible = frozen && locked && !currentDisabled && nonzeroWindow;
            double proposed = current[i];
            boolean changed = false;
            String status;

            if (!frozen) {
                status = "calibrating quiet TPS-rate baseline — maneuver evidence is not accepted yet";
            } else if (locked && currentDisabled) {
                status = "event separation validated and locked; current threshold is 0 (firmware-disabled) so automatic enabling is withheld";
            } else if (locked && !nonzeroWindow) {
                status = "event separation validated but the safe window falls below the smallest nonzero controller step; automatic proposal withheld";
            } else if (eligible) {
                eligibleBins++;
                proposed = boundedProposal(current[i], low, high, scale,
                        Math.max(scale, minimum), maximum);
                if (Math.abs(proposed - current[i]) <= scale * 0.5) {
                    proposed = current[i];
                    status = "event separation validated and locked; current value already sits inside the event-backed window";
                } else {
                    changed = true;
                    status = "event separation validated and locked; proposal "
                            + fmt(current[i]) + " -> " + fmt(proposed);
                    changes.add(ProposalWritePlan.Change.arrayCell(
                            AeParameterNames.TPS_DECEL_THRESHOLD_VALUES,
                            i, current[i], proposed,
                            "TPS decel threshold @ " + Math.round(rpmBins[i]) + " RPM", "#"));
                }
            } else if (bin.normalPeaks.size() < MIN_NORMAL_EVENTS_PER_BIN) {
                status = "need " + (MIN_NORMAL_EVENTS_PER_BIN - bin.normalPeaks.size())
                        + " more Normal Correction event(s) in this broad RPM region";
            } else if (bin.releasePeaks.size() < MIN_RELEASE_EVENTS_PER_BIN) {
                status = "need " + (MIN_RELEASE_EVENTS_PER_BIN - bin.releasePeaks.size())
                        + " more Decel Release event(s) in this broad RPM region";
            } else {
                status = "Normal Corrections and Decel Releases still overlap in falling-TPS magnitude space — repeat the requested maneuver class";
            }

            String requested = requestedClass(bin);
            String region = regionLabel(rpmBins, i);
            decisions.add(new BinDecision(
                    rpmBins[i], region, current[i],
                    bin.normalPeaks.size(), bin.releasePeaks.size(), bin.rejected,
                    bin.crossings, bin.misses, bin.near,
                    bin.normalActive, bin.releaseActive,
                    normalP95, releaseP25, low, high, gap, required,
                    locked, currentDisabled, eligible, proposed, changed,
                    requested, status, bin.lastReject,
                    bin.normalObservations, bin.releaseObservations));

            rows.append("  ").append(region)
                    .append(": Normal Corrections ").append(bin.normalPeaks.size())
                    .append(" event(s), Decel Releases ").append(bin.releasePeaks.size())
                    .append(" event(s), rejected ").append(bin.rejected)
                    .append(", Normal p95 ").append(fmt(normalP95))
                    .append(", Release p25 ").append(fmt(releaseP25))
                    .append(", gap ").append(fmtSigned(gap))
                    .append(" / required +").append(fmt(required))
                    .append(", separation ").append(locked ? "LOCKED" : "OPEN")
                    .append(", requested ").append(classLabel(requested))
                    .append(" | ").append(status).append('\n');
        }

        ProposalWritePlan plan = changes.isEmpty() ? null : new ProposalWritePlan(
                "decel-detection-threshold-curve",
                "Decel Detection — falling-TPS threshold curve",
                snapshot.getConfigurationName(),
                "Evidence-backed falling-TPS threshold changes only. Zero means disabled and is never auto-enabled. Hold cycles remain unchanged. No automatic Apply or Burn.",
                changes);

        StringBuilder review = new StringBuilder("DECEL DETECTION — FALLING-TPS THRESHOLD REVIEW\n");
        review.append("Firmware semantics: signed closing movement must satisfy decelTps < -DecelThreshold; tune values are positive magnitudes; threshold 0 disables detection at that RPM.\n")
                .append("Valid retained analysis samples: ").append(validSamples).append('\n')
                .append("Quiet calibration: ").append(quietRates.size()).append(" / ")
                .append(QUIET_CALIBRATION_TARGET).append(" samples; continuous ")
                .append(fmt(quietDuration)).append(" / ")
                .append(fmt(QUIET_CALIBRATION_MIN_SECONDS)).append(" s | ")
                .append(frozen ? "LOCKED" : "CALIBRATING").append('\n')
                .append("Frozen quiet TPS-rate p95 / p99: ")
                .append(fmt(quietP95)).append(" / ").append(fmt(quietP99)).append(" %/s\n")
                .append("Closing movement / Decel Release rate floors: ")
                .append(fmt(moveRateFloor)).append(" / ").append(fmt(releaseRateFloor)).append(" %/s\n")
                .append("Raw firmware-threshold crossing samples: ").append(rawCrossingSamples).append('\n')
                .append("Fuel: TPS Decel Active samples observed: ").append(decelActiveSamples)
                .append(" (diagnostic only; hold cycles can extend this state beyond the triggering edge).\n")
                .append("Movement events formed after calibration: ").append(counts.moves)
                .append(" | rejected physical mismatches: ").append(counts.rejected).append('\n')
                .append("Accepted Normal Corrections crossing current threshold: ").append(counts.normalCrossings)
                .append(" | accepted Decel Releases below current threshold: ").append(counts.releaseMisses).append('\n')
                .append("\nRPM-BIN EVENT DECISIONS\n").append(rows)
                .append("\nRegions with locked Normal Correction / Decel Release separation: ")
                .append(validatedBins).append(" / ").append(rpmBins.length).append(".\n")
                .append("Automatic nonzero threshold eligibility: ").append(eligibleBins)
                .append(" / ").append(rpmBins.length).append(".\n")
                .append("Proposed threshold changes: ").append(changes.size()).append(".\n")
                .append("Decel hold: ").append(fmt(snapshot.getDecelHoldCycles()))
                .append(" engine cycles — READ ONLY in this pass; threshold and hold are intentionally not tuned together.\n")
                .append("\nWHAT TO DO NEXT: ").append(nextAction(decisions)).append('\n')
                .append("\nBoundary: a zero threshold is a firmware disable command and is never auto-enabled by this recommendation. Only evidence-backed tpsDecelThresholdValue cells may be proposed. tpsDecelHoldCycles remains unchanged. No automatic Apply and no burn.");

        EvidenceSummary summary = new EvidenceSummary(
                validSamples, frozen, QUIET_CALIBRATION_TARGET, quietRates.size(),
                quietDuration, quietP95, quietP99, moveRateFloor, releaseRateFloor,
                rawCrossingSamples, counts.moves, counts.rejected, validatedBins,
                counts.normalCrossings, counts.releaseMisses, decelActiveSamples,
                decisions);
        return new Result(review.toString(), plan, eligibleBins, changes.size(), summary);
    }

    private static void finish(MovementEvent event, BinEvidence[] bins,
                               double[] rpmBins, double releaseRateFloor,
                               double scale, Counts counts) {
        if (event == null || !event.active) return;
        if (finite(event.magnitude) && event.magnitude >= 0.0
                && finite(event.rpm) && finite(event.closingRate)) {
            counts.moves++;
            BinEvidence bin = bins[binForRpm(rpmBins, event.rpm)];
            if (!bin.locked) {
                String expected = requestedClass(bin);
                double ratio = finite(event.threshold) && event.threshold > 1.0e-6
                        ? event.magnitude / event.threshold : Double.NaN;
                double excursion = event.excursion();
                String reject = "";
                if (CLASS_NORMAL_CORRECTION.equals(expected)
                        && event.closingRate >= releaseRateFloor * NORMAL_RATE_FRACTION
                        && finite(excursion) && excursion > NORMAL_MAX_EXCURSION) {
                    reject = "Normal Correction was too large/fast and looked like an intentional throttle release";
                } else if (CLASS_DECEL_RELEASE.equals(expected)
                        && event.closingRate < releaseRateFloor) {
                    reject = "Decel Release was too slow; make a clear normal lift instead of easing out gradually";
                } else if (CLASS_DECEL_RELEASE.equals(expected)
                        && (!finite(excursion) || excursion < RELEASE_MIN_EXCURSION)
                        && (!finite(ratio) || ratio < RELEASE_TINY_RATIO)) {
                    reject = "Decel Release was too small/weak to represent an intentional closing event";
                }

                if (reject.length() > 0) {
                    bin.rejected++;
                    counts.rejected++;
                    bin.lastReject = reject;
                } else if (CLASS_NORMAL_CORRECTION.equals(expected)) {
                    bin.normalPeaks.add(event.magnitude);
                    bin.normalObservations.add(new EventObservation(expected,
                            event.magnitude, event.rpm, event.threshold,
                            event.closingRate, excursion, event.decelActiveObserved));
                    if (event.decelActiveObserved) bin.normalActive++;
                    if (finite(ratio) && ratio > 1.0) {
                        bin.crossings++;
                        counts.normalCrossings++;
                    }
                } else if (CLASS_DECEL_RELEASE.equals(expected)) {
                    bin.releasePeaks.add(event.magnitude);
                    bin.releaseObservations.add(new EventObservation(expected,
                            event.magnitude, event.rpm, event.threshold,
                            event.closingRate, excursion, event.decelActiveObserved));
                    if (event.decelActiveObserved) bin.releaseActive++;
                    if (finite(ratio) && ratio < 1.0) {
                        bin.misses++;
                        counts.releaseMisses++;
                    }
                    if (finite(ratio) && ratio >= 0.75 && ratio < 1.0) bin.near++;
                }

                if (!bin.locked
                        && bin.normalPeaks.size() >= MIN_NORMAL_EVENTS_PER_BIN
                        && bin.releasePeaks.size() >= MIN_RELEASE_EVENTS_PER_BIN) {
                    double normal = percentile(bin.normalPeaks, 0.95);
                    double release = percentile(bin.releasePeaks, 0.25);
                    double low = finite(normal) ? normal * NOISE_GUARD + scale : Double.NaN;
                    double high = finite(release) ? release * RELEASE_GUARD : Double.NaN;
                    if (separated(low, high)) bin.locked = true;
                }
            }
        }
        event.clear();
    }

    private static boolean separated(double low, double high) {
        return finite(low) && finite(high)
                && high - low >= Math.max(MIN_SEPARATION, Math.max(0.0, low) * 0.15);
    }

    private static String requestedClass(BinEvidence bin) {
        if (bin == null || bin.locked) return CLASS_LOCKED;
        if (bin.normalPeaks.size() < MIN_NORMAL_EVENTS_PER_BIN) return CLASS_NORMAL_CORRECTION;
        if (bin.releasePeaks.size() < MIN_RELEASE_EVENTS_PER_BIN) return CLASS_DECEL_RELEASE;
        return bin.normalPeaks.size() <= bin.releasePeaks.size()
                ? CLASS_NORMAL_CORRECTION : CLASS_DECEL_RELEASE;
    }

    private static String nextAction(List<BinDecision> decisions) {
        for (BinDecision decision : decisions) {
            if (decision.validated || (decision.normalEvents == 0 && decision.releaseEvents == 0)) continue;
            if (CLASS_NORMAL_CORRECTION.equals(decision.requestedClass)) {
                return "In the " + decision.regionLabel
                        + ", make a small natural pedal reduction without intentionally entering overrun.";
            }
            if (CLASS_DECEL_RELEASE.equals(decision.requestedClass)) {
                return "In the " + decision.regionLabel
                        + ", make a clear normal throttle lift; do not ease out slowly.";
            }
        }
        for (BinDecision decision : decisions) {
            if (decision.validated && decision.currentDisabled) {
                return "Evidence is valid in " + decision.regionLabel
                        + " but the current threshold is 0/disabled. Automatic enabling is withheld; use explicit reviewed Task Settings only if enabling this region is intentional.";
            }
        }
        return "If the representative RPM regions are validated, review the threshold-only proposal. Leave Decel hold unchanged for this pass.";
    }

    private static double boundedProposal(double current, double low, double high,
                                          double scale, double minimum, double maximum) {
        if (!finite(current) || !finite(low) || !finite(high) || low > high) return current;
        if (current >= low && current <= high) return current;
        double target = current < low ? low : high;
        double maxMove = Math.max(MIN_MAX_MOVE, Math.abs(current) * MAX_MOVE_FRACTION);
        double delta = target - current;
        if (delta > maxMove) delta = maxMove;
        if (delta < -maxMove) delta = -maxMove;
        double value = current + delta;
        value = Math.max(minimum, Math.min(maximum, value));
        value = Math.round(value / scale) * scale;
        if (value < minimum) value = minimum;
        if (value > maximum) value = maximum;
        return value;
    }

    private static int binForRpm(double[] bins, double rpm) {
        if (bins == null || bins.length == 0) return 0;
        int best = 0;
        double bestDistance = Math.abs(rpm - bins[0]);
        for (int i = 1; i < bins.length; i++) {
            double distance = Math.abs(rpm - bins[i]);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String regionLabel(double[] bins, int index) {
        if (bins == null || bins.length == 0 || index < 0 || index >= bins.length) return "unknown RPM region";
        double low = index == 0 ? 0.0 : (bins[index - 1] + bins[index]) / 2.0;
        double high = index == bins.length - 1 ? Double.POSITIVE_INFINITY
                : (bins[index] + bins[index + 1]) / 2.0;
        if (!finite(high)) return Math.round(low) + "+ RPM region";
        return Math.round(low) + "–" + Math.round(high) + " RPM region";
    }

    private static String classLabel(String value) {
        if (CLASS_NORMAL_CORRECTION.equals(value)) return "NORMAL CORRECTION";
        if (CLASS_DECEL_RELEASE.equals(value)) return "DECEL RELEASE";
        return nn(value).replace('_', ' ');
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
                + fraction * (copy.get(high).doubleValue() - copy.get(low).doubleValue());
    }

    private static double safe(double value) { return finite(value) ? Math.max(0.0, value) : 0.0; }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static String nn(String value) { return value == null ? "" : value; }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(values));
    }

    private static String fmt(double value) {
        return finite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static String fmtSigned(double value) {
        return finite(value) ? String.format(Locale.ROOT, "%+.3f", value) : "n/a";
    }

    private static Result unavailable(String reason) {
        return new Result("DECEL DETECTION — recommendation withheld\n" + nn(reason)
                + "\nNo ProposalWritePlan. No automatic Apply and no burn.",
                null, 0, 0, EvidenceSummary.empty());
    }
}
