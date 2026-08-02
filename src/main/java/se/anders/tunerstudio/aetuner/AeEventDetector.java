package se.anders.tunerstudio.aetuner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class AeEventDetector {
    private static final double PRE_SECONDS = 0.60;
    private static final double POST_SECONDS = 0.55;
    private static final double SPLIT_QUIET_SECONDS = 0.35;
    private static final double MIN_RUNNING_RPM = 400.0;
    private static final double MIN_POSITIVE_TPS_DELTA = 0.50;
    private static final double MIN_EVENT_SECONDS = 0.16;
    private static final double MAX_ACTIVE_EVENT_SECONDS = 3.25;
    private static final double MAX_CAPTURE_SECONDS = 4.75;
    private static final int MAX_RING_SAMPLES = 400;

    private final Deque<LiveSample> ring = new ArrayDeque<LiveSample>();
    private final List<LiveSample> active = new ArrayList<LiveSample>();
    private boolean inEvent;
    private long eventStartNano;
    private long lastActivityNano;
    private int nextEventIndex = 1;

    synchronized void resetTracking() {
        ring.clear();
        active.clear();
        inEvent = false;
        eventStartNano = 0L;
        lastActivityNano = 0L;
    }

    synchronized void resetSession() {
        resetTracking();
        nextEventIndex = 1;
    }

    synchronized void addPassiveSample(LiveSample sample) {
        appendRing(sample);
    }

    synchronized int getRingSampleCount() {
        return ring.size();
    }

    synchronized int getActiveSampleCount() {
        return active.size();
    }

    synchronized EventSummary addSample(LiveSample sample, double startThreshold) {
        return addSample(sample, startThreshold, false);
    }

    synchronized EventSummary addSample(LiveSample sample, double startThreshold, boolean mapPredictWorkflow) {
        appendRing(sample);
        boolean activeNow = isEventActivity(sample, startThreshold, inEvent);

        if (inEvent) {
            double quietBefore = secondsBetween(sample.getNanoTime(), lastActivityNano);
            boolean newCleanOpening = quietBefore >= SPLIT_QUIET_SECONDS && isCleanStart(sample, startThreshold);
            if (newCleanOpening && active.size() >= 4) {
                EventSummary previous = buildSummary(trimToRecent(active, MAX_CAPTURE_SECONDS),
                        "closed before a new throttle opening after quiet period", false, mapPredictWorkflow);
                startEvent(sample);
                return previous;
            }
        }

        boolean startedNow = false;
        if (!inEvent && activeNow && isCleanStart(sample, startThreshold)) {
            startEvent(sample);
            startedNow = true;
        }

        if (inEvent) {
            if (!startedNow) {
                active.add(sample);
            }
            if (activeNow) {
                lastActivityNano = sample.getNanoTime();
            }

            double quietSeconds = secondsBetween(sample.getNanoTime(), lastActivityNano);
            double activeDuration = secondsBetween(sample.getNanoTime(), eventStartNano);
            double captureDuration = active.isEmpty() ? 0.0 : secondsBetween(sample, active.get(0));
            boolean timeout = activeDuration >= MAX_ACTIVE_EVENT_SECONDS || captureDuration >= MAX_CAPTURE_SECONDS;
            boolean quietDone = quietSeconds >= POST_SECONDS && activeDuration >= MIN_EVENT_SECONDS;
            if (timeout || quietDone) {
                String closeNote = timeout ? "duration timeout/truncated at max capture limit" : "post-event quiet window captured";
                EventSummary summary = buildSummary(trimToRecent(active, MAX_CAPTURE_SECONDS), closeNote, timeout, mapPredictWorkflow);
                active.clear();
                inEvent = false;
                eventStartNano = 0L;
                lastActivityNano = 0L;
                return summary;
            }
        }

        return null;
    }

    private void startEvent(LiveSample sample) {
        inEvent = true;
        active.clear();
        for (LiveSample buffered : ring) {
            if (buffered.getNanoTime() != sample.getNanoTime()
                    && secondsBetween(sample, buffered) <= PRE_SECONDS) {
                active.add(buffered);
            }
        }
        eventStartNano = sample.getNanoTime();
        lastActivityNano = sample.getNanoTime();
        active.add(sample);
    }

    private boolean isCleanStart(LiveSample sample, double startThreshold) {
        if (!isRunning(sample)) {
            return false;
        }
        if (sample.bool(ChannelRole.DFCO) || zeroIfNaN(sample.get(ChannelRole.FUEL_CUT)) > 0.01) {
            return false;
        }
        return hasPositiveOpening(sample, startThreshold);
    }

    private boolean isEventActivity(LiveSample sample, double startThreshold, boolean alreadyRunning) {
        if (alreadyRunning && !isRunning(sample)) {
            return false;
        }

        double threshold = Math.max(0.05, startThreshold);
        double continueThreshold = Math.max(0.03, threshold * 0.55);
        boolean dotActive = sample.getTpsDot() >= (alreadyRunning ? continueThreshold : threshold);
        boolean ecuStateActive = sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                || sample.bool(ChannelRole.AE_EXTRA_SHOT);
        boolean tpsFuelActive = isTpsAeFuelVisible(sample);
        boolean otherTransientActive = isOtherTransientVisible(sample);

        if (!alreadyRunning) {
            return isRunning(sample) && hasPositiveOpening(sample, threshold)
                    && (dotActive || ecuStateActive || tpsFuelActive || otherTransientActive);
        }
        return dotActive || ecuStateActive || tpsFuelActive || otherTransientActive;
    }

    private EventSummary buildSummary(List<LiveSample> samples, String closeNote, boolean closedByTimeout,
                                      boolean mapPredictWorkflow) {
        EventStats stats = new EventStats(samples);
        String rejection = hardRejectionReason(stats, closedByTimeout);
        if (rejection.length() > 0) {
            return new EventSummary(nextEventIndex++, false, "Rejected", rejection + noteSuffix(closeNote), samples);
        }

        if (mapPredictWorkflow) {
            if (stats.mapPredictionSeen) {
                String eventClass;
                if (stats.wallWettingSeen && stats.instantFuelSeen) {
                    eventClass = "MAP Predict + Wall Wetting + Instant Fuel event";
                } else if (stats.wallWettingSeen) {
                    eventClass = "MAP Predict + Wall Wetting event";
                } else if (stats.instantFuelSeen) {
                    eventClass = "MAP Predict + Instant Fuel event";
                } else {
                    eventClass = "MAP Predict event";
                }
                return new EventSummary(nextEventIndex++, true, eventClass,
                        "MAP Predict activity was visible; shared TPS-change detector state "
                                + (stats.tpsAeStateSeen ? "was seen" : "was not sampled")
                                + noteSuffix(closeNote), samples, true);
            }
            if (stats.wallWettingSeen) {
                return new EventSummary(nextEventIndex++, true, "Wall Wetting event",
                        "Wall Wetting contribution was visible without MAP Predict" + noteSuffix(closeNote), samples, true);
            }
            if (stats.instantFuelSeen) {
                return new EventSummary(nextEventIndex++, true, "Instant Fuel event",
                        "Instant Fuel contribution was visible without MAP Predict" + noteSuffix(closeNote), samples, true);
            }
            if (stats.tpsAeStateSeen) {
                return new EventSummary(nextEventIndex++, true, "TPS-change detector state",
                        "Fuel: TPS AE Active was seen as the shared detector state, but MAP Predict did not activate" + noteSuffix(closeNote), samples, true);
            }
            if (stats.triggerNearMiss) {
                return new EventSummary(nextEventIndex++, true, "MAP Predict trigger near miss",
                        "Pedal movement reached " + formatPercent(stats.maxTriggerRatio)
                                + " of AccelThreshold, but the shared detector did not activate MAP Predict" + noteSuffix(closeNote), samples, true);
            }
            return new EventSummary(nextEventIndex++, false, "Pedal movement only",
                    "Pedal movement captured, but no MAP Predict, Wall Wetting, Instant Fuel, or shared detector state was visible"
                            + noteSuffix(closeNote), samples, true);
        }

        if (stats.tpsAeFuelProved) {
            return new EventSummary(nextEventIndex++, true, "TPS AE fuel proved",
                    "Fuel: TPS AE add fuel ms and/or Fuel: TPS extraFuel was visible anywhere in this captured event; fuel bursts "
                            + stats.fuelBurstCount + noteSuffix(closeNote), samples, false);
        }
        if (stats.tpsAeStateSeen) {
            return new EventSummary(nextEventIndex++, true, "TPS AE state only",
                    "Fuel: TPS AE Active was seen, but Fuel: TPS AE add fuel ms and Fuel: TPS extraFuel stayed zero"
                            + noteSuffix(closeNote), samples, false);
        }
        if (stats.otherTransientPathSeen) {
            return new EventSummary(nextEventIndex++, true, "Other transient path",
                    "No TPS cycle-AE fuel was visible; MAP Predict, Wall Wetting, or Instant Fuel activity was present"
                            + noteSuffix(closeNote), samples, false);
        }
        if (stats.triggerNearMiss) {
            return new EventSummary(nextEventIndex++, true, "Trigger near miss",
                    "Pedal movement reached " + formatPercent(stats.maxTriggerRatio)
                            + " of AccelThreshold but Fuel: TPS AE Active and TPS AE fuel stayed off"
                            + noteSuffix(closeNote), samples, false);
        }
        return new EventSummary(nextEventIndex++, false, "Pedal movement only",
                "Pedal movement captured, but no EpicEFI TPS AE state or transient fuel output was visible"
                        + noteSuffix(closeNote), samples, false);
    }

    private String hardRejectionReason(EventStats stats, boolean closedByTimeout) {
        if (stats.sampleCount < 4) {
            return "too few samples";
        }
        if (stats.localDurationSeconds < MIN_EVENT_SECONDS) {
            return "event shorter than " + MIN_EVENT_SECONDS + " s";
        }
        if (stats.localDurationSeconds > MAX_CAPTURE_SECONDS + 0.05) {
            return "internal capture longer than max capture clamp";
        }
        if (stats.maxRpm < MIN_RUNNING_RPM) {
            return "RPM below " + (int) MIN_RUNNING_RPM + " during event";
        }
        if (stats.dfcoSeen) {
            return "fuel cut/DFCO visible in event";
        }
        if (!stats.hasLambdaOrAfr) {
            return "lambda/AFR channel missing";
        }
        if (!stats.hasTps || !stats.hasMap) {
            return "TPS or MAP channel missing";
        }
        if (!stats.tpsAeFuelProved && !stats.tpsAeStateSeen && !stats.otherTransientPathSeen && stats.maxTpsDot <= 0.0) {
            return "no positive TPSdot or transient-fuel activity visible";
        }
        // Timeout is a note, not a hard rejection, so a real fuel event cannot be thrown away only because the window was truncated.
        return "";
    }

    private static String formatPercent(double ratio) {
        if (!Double.isFinite(ratio)) {
            return "n/a";
        }
        return Integer.toString((int) Math.round(ratio * 100.0)) + "%";
    }

    private static String noteSuffix(String closeNote) {
        return closeNote == null || closeNote.length() == 0 ? "" : "; " + closeNote;
    }

    private static boolean isRunning(LiveSample sample) {
        double rpm = sample.get(ChannelRole.RPM);
        return Double.isFinite(rpm) && rpm >= MIN_RUNNING_RPM;
    }

    private static boolean hasPositiveOpening(LiveSample sample, double threshold) {
        if (sample.getTpsDot() >= Math.max(0.05, threshold)) {
            return true;
        }
        return openingAmount(sample) >= MIN_POSITIVE_TPS_DELTA;
    }

    private static double openingAmount(LiveSample sample) {
        double amount = 0.0;
        double delta = sample.get(ChannelRole.DELTA_TPS);
        if (Double.isFinite(delta)) {
            amount = Math.max(amount, delta);
        }
        double smooth = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        if (Double.isFinite(smooth)) {
            amount = Math.max(amount, smooth);
        }
        double from = sample.get(ChannelRole.TPS_FROM);
        double to = sample.get(ChannelRole.TPS_TO);
        if (Double.isFinite(from) && Double.isFinite(to)) {
            amount = Math.max(amount, to - from);
        }
        return amount;
    }

    private void appendRing(LiveSample sample) {
        ring.add(sample);
        while (ring.size() > MAX_RING_SAMPLES) {
            ring.removeFirst();
        }
        while (!ring.isEmpty() && secondsBetween(sample, ring.peekFirst()) > PRE_SECONDS + 0.25) {
            ring.removeFirst();
        }
    }

    private static List<LiveSample> trimToRecent(List<LiveSample> samples, double maxSeconds) {
        if (samples.size() < 2) {
            return new ArrayList<LiveSample>(samples);
        }
        LiveSample last = samples.get(samples.size() - 1);
        List<LiveSample> trimmed = new ArrayList<LiveSample>();
        for (LiveSample sample : samples) {
            if (secondsBetween(last, sample) <= maxSeconds) {
                trimmed.add(sample);
            }
        }
        return trimmed;
    }

    private static boolean isTpsAeFuelVisible(LiveSample sample) {
        return Math.abs(zeroIfNaN(sample.get(ChannelRole.AE_ADD_MS))) > 0.002
                || Math.abs(zeroIfNaN(sample.get(ChannelRole.EXTRA_FUEL))) > 0.0001;
    }

    private static boolean isMapPredictionVisible(LiveSample sample) {
        return TransientSignals.mapPredictionVisible(sample);
    }

    private static boolean isWallWettingVisible(LiveSample sample) {
        return TransientSignals.wallWettingVisible(sample);
    }

    private static boolean isInstantFuelVisible(LiveSample sample) {
        return TransientSignals.instantFuelVisible(sample);
    }

    private static boolean isOtherTransientVisible(LiveSample sample) {
        return isMapPredictionVisible(sample) || isWallWettingVisible(sample) || isInstantFuelVisible(sample);
    }

    private static double secondsBetween(LiveSample newer, LiveSample older) {
        return secondsBetween(newer.getNanoTime(), older.getNanoTime());
    }

    private static double secondsBetween(long newerNano, long olderNano) {
        if (newerNano <= 0L || olderNano <= 0L) {
            return 0.0;
        }
        return Math.max(0.0, (newerNano - olderNano) / 1000000000.0);
    }

    private static double zeroIfNaN(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static final class EventStats {
        final int sampleCount;
        final double localDurationSeconds;
        double maxRpm;
        double maxTpsDot;
        boolean dfcoSeen;
        boolean hasLambdaOrAfr;
        boolean hasTps;
        boolean hasMap;
        boolean tpsAeFuelProved;
        boolean tpsAeStateSeen;
        boolean otherTransientPathSeen;
        boolean mapPredictionSeen;
        boolean wallWettingSeen;
        boolean instantFuelSeen;
        boolean triggerNearMiss;
        double maxTriggerRatio;
        double maxTriggerMargin = Double.NEGATIVE_INFINITY;
        double minTps = Double.POSITIVE_INFINITY;
        double maxTps = Double.NEGATIVE_INFINITY;
        int fuelBurstCount;

        EventStats(List<LiveSample> samples) {
            this.sampleCount = samples.size();
            if (samples.size() >= 2) {
                this.localDurationSeconds = secondsBetween(samples.get(samples.size() - 1), samples.get(0));
            } else {
                this.localDurationSeconds = 0.0;
            }
            boolean previousFuelVisible = false;
            for (LiveSample sample : samples) {
                double rpm = sample.get(ChannelRole.RPM);
                if (Double.isFinite(rpm)) {
                    maxRpm = Math.max(maxRpm, rpm);
                }
                maxTpsDot = Math.max(maxTpsDot, sample.getTpsDot());
                double smooth = zeroIfNaN(sample.get(ChannelRole.SMOOTHED_DELTA_TPS));
                double threshold = zeroIfNaN(sample.get(ChannelRole.ACCEL_THRESHOLD));
                if (threshold > 0.0) {
                    maxTriggerRatio = Math.max(maxTriggerRatio, smooth / threshold);
                    maxTriggerMargin = Math.max(maxTriggerMargin, smooth - threshold);
                }
                double tps = sample.get(ChannelRole.TPS);
                if (Double.isFinite(tps)) {
                    minTps = Math.min(minTps, tps);
                    maxTps = Math.max(maxTps, tps);
                }
                if (Double.isFinite(sample.get(ChannelRole.LAMBDA)) || Double.isFinite(sample.get(ChannelRole.AFR))) {
                    hasLambdaOrAfr = true;
                }
                if (Double.isFinite(sample.get(ChannelRole.TPS))) {
                    hasTps = true;
                }
                if (Double.isFinite(sample.get(ChannelRole.MAP))) {
                    hasMap = true;
                }
                if (sample.bool(ChannelRole.DFCO) || zeroIfNaN(sample.get(ChannelRole.FUEL_CUT)) > 0.01) {
                    dfcoSeen = true;
                }
                boolean fuelVisible = isTpsAeFuelVisible(sample);
                if (fuelVisible) {
                    tpsAeFuelProved = true;
                }
                if (fuelVisible && !previousFuelVisible) {
                    fuelBurstCount++;
                }
                previousFuelVisible = fuelVisible;
                if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
                    tpsAeStateSeen = true;
                }
                if (isMapPredictionVisible(sample)) {
                    mapPredictionSeen = true;
                }
                if (isWallWettingVisible(sample)) {
                    wallWettingSeen = true;
                }
                if (isInstantFuelVisible(sample)) {
                    instantFuelSeen = true;
                }
                if (isOtherTransientVisible(sample)) {
                    otherTransientPathSeen = true;
                }
            }
            double tpsRise = Double.isInfinite(minTps) || Double.isInfinite(maxTps) ? 0.0 : Math.max(0.0, maxTps - minTps);
            triggerNearMiss = !tpsAeFuelProved
                    && !tpsAeStateSeen
                    && !otherTransientPathSeen
                    && tpsRise >= MIN_POSITIVE_TPS_DELTA
                    && maxTriggerRatio >= 0.80
                    && maxTriggerRatio < 1.0;
        }
    }
}
