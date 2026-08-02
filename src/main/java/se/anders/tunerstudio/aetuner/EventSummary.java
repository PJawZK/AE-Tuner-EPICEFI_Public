package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class EventSummary {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private final int index;
    private final boolean accepted;
    private final String eventClass;
    private final String reason;
    private final List<LiveSample> samples;
    private final double startSeconds;
    private final double endSeconds;
    private final double medianRpm;
    private final double startMap;
    private final double endMap;
    private final double startTps;
    private final double endTps;
    private final double maxTpsDot;
    private final double maxSmoothedDeltaTps;
    private final double maxAccelThreshold;
    private final double maxMapDot;
    private final double maxPw;
    private final double maxAeMs;
    private final double maxExtraFuel;
    private final double maxCycleMult;
    private final double maxWallCorrection;
    private final double minWallCorrection;
    private final double maxWallWettingPw;
    private final double minWallWettingPw;
    private final boolean wallCorrectionAvailable;
    private final boolean wallWettingPwAvailable;
    private final double wallWettingToTpsAeRatio;
    private final String transientMixClass;
    private final boolean extraShotSeen;
    private final boolean dfcoSeen;
    private final boolean tpsAeStateSeen;
    private final boolean tpsAeFuelProved;
    private final boolean otherTransientPathSeen;
    private final int fuelBurstCount;
    private final double maxTriggerRatio;
    private final double triggerMargin;
    private final double tpsRise;
    private final double mapRise;
    private final double maxTps;
    private final double maxTpsAeTo;
    private final boolean triggerNearMiss;
    private final boolean tinyTriggerCandidate;
    private final double maxLeanLambdaError;
    private final double maxRichLambdaError;
    private final double leanArea;
    private final double richArea;
    private final double earlyLeanLambdaError;
    private final double earlyRichLambdaError;
    private final double midLeanLambdaError;
    private final double midRichLambdaError;
    private final double lateLeanLambdaError;
    private final double lateRichLambdaError;
    private final String aeFuelTableGuidance;
    private final boolean mapPredictWorkflow;
    private final boolean mapPredictionSeenCached;
    private final boolean wallWettingSeenCached;
    private final boolean instantFuelSeenCached;
    private final double maxEffectiveMapGapCached;
    private final double maxFallbackMapGapCached;
    private final double predictionActiveSecondsCached;
    private final CounterMath.Result predictionResetMetricsCached;
    private final int predictionTriggerBurstCountCached;
    private final double medianPredictionRpmCached;

    EventSummary(int index, boolean accepted, String eventClass, String reason, List<LiveSample> samples) {
        this(index, accepted, eventClass, reason, samples, false);
    }

    EventSummary(int index, boolean accepted, String eventClass, String reason, List<LiveSample> samples,
                 boolean mapPredictWorkflow) {
        this.index = index;
        this.accepted = accepted;
        this.eventClass = eventClass == null ? "Unclassified" : eventClass;
        this.reason = reason == null ? "" : reason;
        this.mapPredictWorkflow = mapPredictWorkflow;
        this.samples = Collections.unmodifiableList(new ArrayList<LiveSample>(samples));

        LiveSample first = samples.isEmpty() ? null : samples.get(0);
        LiveSample last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        LiveSample firstRunning = firstRunning(samples);
        LiveSample lastRunning = lastRunning(samples);
        this.startSeconds = first == null ? Double.NaN : first.getSeconds();
        this.endSeconds = last == null ? Double.NaN : last.getSeconds();
        this.medianRpm = median(samples, ChannelRole.RPM, 400.0);
        this.startMap = firstRunning == null ? Double.NaN : firstRunning.get(ChannelRole.MAP);
        this.endMap = lastRunning == null ? Double.NaN : lastRunning.get(ChannelRole.MAP);
        this.startTps = firstRunning == null ? Double.NaN : firstRunning.get(ChannelRole.TPS);
        this.endTps = lastRunning == null ? Double.NaN : lastRunning.get(ChannelRole.TPS);

        double tpsDotMax = 0.0;
        double smoothedDeltaTpsMax = 0.0;
        double accelThresholdMax = 0.0;
        double mapDotMax = 0.0;
        double pwMax = 0.0;
        double aeMax = 0.0;
        double extraMax = 0.0;
        double cycleMultMax = 0.0;
        double wallMax = 0.0;
        double wallMin = 0.0;
        double wallPwMax = 0.0;
        double wallPwMin = 0.0;
        boolean wallCorrectionSeenAsChannel = false;
        boolean wallWettingPwSeenAsChannel = false;
        boolean extraShot = false;
        boolean dfco = false;
        boolean aeState = false;
        boolean tpsFuel = false;
        boolean otherPath = false;
        int fuelBursts = 0;
        double triggerRatioMax = 0.0;
        double triggerMarginMax = Double.NEGATIVE_INFINITY;
        double tpsMin = Double.POSITIVE_INFINITY;
        double tpsMax = Double.NEGATIVE_INFINITY;
        double mapMin = Double.POSITIVE_INFINITY;
        double mapMax = Double.NEGATIVE_INFINITY;
        double tpsAeToMax = Double.NEGATIVE_INFINITY;
        boolean previousTpsFuelVisible = false;
        double leanMax = 0.0;
        double richMax = 0.0;
        double leanSum = 0.0;
        double richSum = 0.0;
        double earlyLeanMax = 0.0;
        double earlyRichMax = 0.0;
        double midLeanMax = 0.0;
        double midRichMax = 0.0;
        double lateLeanMax = 0.0;
        double lateRichMax = 0.0;
        long firstNano = first == null ? 0L : first.getNanoTime();

        LiveSample previous = null;
        for (LiveSample sample : samples) {
            tpsDotMax = Math.max(tpsDotMax, sample.getTpsDot());
            smoothedDeltaTpsMax = Math.max(smoothedDeltaTpsMax, safePositive(sample.get(ChannelRole.SMOOTHED_DELTA_TPS)));
            double smoothed = safePositive(sample.get(ChannelRole.SMOOTHED_DELTA_TPS));
            double threshold = safePositive(sample.get(ChannelRole.ACCEL_THRESHOLD));
            accelThresholdMax = Math.max(accelThresholdMax, threshold);
            if (threshold > 0.0) {
                triggerRatioMax = Math.max(triggerRatioMax, smoothed / threshold);
                triggerMarginMax = Math.max(triggerMarginMax, smoothed - threshold);
            }
            double tps = sample.get(ChannelRole.TPS);
            if (Double.isFinite(tps)) {
                tpsMin = Math.min(tpsMin, tps);
                tpsMax = Math.max(tpsMax, tps);
            }
            double map = sample.get(ChannelRole.MAP);
            if (Double.isFinite(map)) {
                mapMin = Math.min(mapMin, map);
                mapMax = Math.max(mapMax, map);
            }
            double tpsAeTo = sample.get(ChannelRole.TPS_TO);
            if (Double.isFinite(tpsAeTo)) {
                tpsAeToMax = Math.max(tpsAeToMax, tpsAeTo);
            }
            mapDotMax = Math.max(mapDotMax, sample.getMapDot());
            pwMax = Math.max(pwMax, safePositive(sample.get(ChannelRole.PW)));
            aeMax = Math.max(aeMax, safePositive(sample.get(ChannelRole.AE_ADD_MS)));
            extraMax = Math.max(extraMax, safePositive(sample.get(ChannelRole.EXTRA_FUEL)));
            cycleMultMax = Math.max(cycleMultMax, safePositive(sample.get(ChannelRole.TPS_AE_CYCLE_MULT)));

            double wall = sample.get(ChannelRole.WALL_CORRECTION);
            if (Double.isFinite(wall)) {
                wallCorrectionSeenAsChannel = true;
                wallMax = Math.max(wallMax, wall);
                wallMin = Math.min(wallMin, wall);
            }
            double wallPw = sample.get(ChannelRole.WALL_WETTING_PW);
            if (Double.isFinite(wallPw)) {
                wallWettingPwSeenAsChannel = true;
                wallPwMax = Math.max(wallPwMax, wallPw);
                wallPwMin = Math.min(wallPwMin, wallPw);
            }
            extraShot = extraShot || sample.bool(ChannelRole.AE_EXTRA_SHOT);
            dfco = dfco || sample.bool(ChannelRole.DFCO) || safePositive(sample.get(ChannelRole.FUEL_CUT)) > 0.01;
            aeState = aeState || sample.bool(ChannelRole.AE_ABOVE_THRESHOLD);
            boolean tpsFuelVisibleNow = safePositive(sample.get(ChannelRole.AE_ADD_MS)) > 0.002
                    || safePositive(sample.get(ChannelRole.EXTRA_FUEL)) > 0.0001;
            tpsFuel = tpsFuel || tpsFuelVisibleNow;
            if (tpsFuelVisibleNow && !previousTpsFuelVisible) {
                fuelBursts++;
            }
            previousTpsFuelVisible = tpsFuelVisibleNow;
            double realMapForPrediction = sample.get(ChannelRole.MAP);
            double effectiveMapForPrediction = sample.get(ChannelRole.EFFECTIVE_MAP);
            boolean mapPredictionVisible = sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                    || (Double.isFinite(realMapForPrediction) && Double.isFinite(effectiveMapForPrediction)
                    && effectiveMapForPrediction - realMapForPrediction > 0.10);
            otherPath = otherPath || mapPredictionVisible
                    || Math.abs(zeroIfNaN(sample.get(ChannelRole.WALL_CORRECTION))) > 0.0001
                    || Math.abs(zeroIfNaN(sample.get(ChannelRole.WALL_WETTING_PW))) > 0.0001
                    || sample.bool(ChannelRole.AE_EXTRA_SHOT)
                    || Math.abs(zeroIfNaN(sample.get(ChannelRole.INSTANT_PULSE_PW))) > 0.0001;

            double lambda = sample.get(ChannelRole.LAMBDA);
            double target = sample.get(ChannelRole.TARGET_LAMBDA);
            if (!Double.isFinite(target) || target <= 0.0) {
                double afr = sample.get(ChannelRole.AFR);
                double targetAfr = sample.get(ChannelRole.TARGET_AFR);
                if (Double.isFinite(afr) && Double.isFinite(targetAfr) && targetAfr > 0.1) {
                    lambda = afr / targetAfr;
                    target = 1.0;
                }
            }

            if (Double.isFinite(lambda) && Double.isFinite(target) && target > 0.0) {
                double error = lambda - target;
                leanMax = Math.max(leanMax, error);
                richMax = Math.min(richMax, error);
                int aeWindow = classifyAeWindow(sample, firstNano);
                if (aeWindow == 0) {
                    earlyLeanMax = Math.max(earlyLeanMax, error);
                    earlyRichMax = Math.min(earlyRichMax, error);
                } else if (aeWindow == 1) {
                    midLeanMax = Math.max(midLeanMax, error);
                    midRichMax = Math.min(midRichMax, error);
                } else {
                    lateLeanMax = Math.max(lateLeanMax, error);
                    lateRichMax = Math.min(lateRichMax, error);
                }

                if (previous != null) {
                    double dt = Math.max(0.0, sample.getSeconds() - previous.getSeconds());
                    if (error > 0.0) {
                        leanSum += error * dt;
                    } else if (error < 0.0) {
                        richSum += -error * dt;
                    }
                }
            }
            previous = sample;
        }

        this.maxTpsDot = tpsDotMax;
        this.maxSmoothedDeltaTps = smoothedDeltaTpsMax;
        this.maxAccelThreshold = accelThresholdMax;
        this.maxMapDot = mapDotMax;
        this.maxPw = pwMax;
        this.maxAeMs = aeMax;
        this.maxExtraFuel = extraMax;
        this.maxCycleMult = cycleMultMax;
        this.maxWallCorrection = wallMax;
        this.minWallCorrection = wallMin;
        this.maxWallWettingPw = wallPwMax;
        this.minWallWettingPw = wallPwMin;
        this.wallCorrectionAvailable = wallCorrectionSeenAsChannel;
        this.wallWettingPwAvailable = wallWettingPwSeenAsChannel;
        double wallAbsPw = Math.max(Math.abs(wallPwMax), Math.abs(wallPwMin));
        double tpsAeReference = Math.max(aeMax, extraMax);
        this.wallWettingToTpsAeRatio = tpsAeReference > 0.002 ? wallAbsPw / tpsAeReference : Double.NaN;
        this.transientMixClass = classifyTransientMix(tpsFuel, otherPath, wallWettingPwSeenAsChannel,
                wallAbsPw, tpsAeReference, extraShot);
        this.extraShotSeen = extraShot;
        this.dfcoSeen = dfco;
        this.tpsAeStateSeen = aeState;
        this.tpsAeFuelProved = tpsFuel;
        this.otherTransientPathSeen = otherPath;
        this.fuelBurstCount = fuelBursts;
        this.maxTriggerRatio = triggerRatioMax;
        this.triggerMargin = triggerMarginMax == Double.NEGATIVE_INFINITY ? Double.NaN : triggerMarginMax;
        this.tpsRise = Double.isInfinite(tpsMin) || Double.isInfinite(tpsMax) ? Double.NaN : Math.max(0.0, tpsMax - tpsMin);
        this.mapRise = Double.isInfinite(mapMin) || Double.isInfinite(mapMax) ? Double.NaN : Math.max(0.0, mapMax - mapMin);
        this.maxTps = Double.isInfinite(tpsMax) ? Double.NaN : tpsMax;
        this.maxTpsAeTo = Double.isInfinite(tpsAeToMax) ? Double.NaN : tpsAeToMax;
        this.triggerNearMiss = !tpsFuel && !aeState && !otherPath && triggerRatioMax >= 0.80 && triggerRatioMax < 1.0;
        this.tinyTriggerCandidate = tpsFuel && Double.isFinite(this.tpsRise) && this.tpsRise < 6.0 && Double.isFinite(this.mapRise) && this.mapRise < 12.0;
        this.maxLeanLambdaError = leanMax;
        this.maxRichLambdaError = richMax;
        this.leanArea = leanSum;
        this.richArea = richSum;
        this.earlyLeanLambdaError = earlyLeanMax;
        this.earlyRichLambdaError = earlyRichMax;
        this.midLeanLambdaError = midLeanMax;
        this.midRichLambdaError = midRichMax;
        this.lateLeanLambdaError = lateLeanMax;
        this.lateRichLambdaError = lateRichMax;
        PredictionMetrics predictionMetrics = PredictionMetrics.build(samples);
        this.mapPredictionSeenCached = predictionMetrics.predictionSeen;
        this.wallWettingSeenCached = predictionMetrics.wallSeen;
        this.instantFuelSeenCached = predictionMetrics.instantSeen;
        this.maxEffectiveMapGapCached = predictionMetrics.maxEffectiveGap;
        this.maxFallbackMapGapCached = predictionMetrics.maxFallbackGap;
        this.predictionActiveSecondsCached = predictionMetrics.activeSeconds;
        this.predictionResetMetricsCached = CounterMath.analyze(samples, ChannelRole.MAP_PRED_RESET_CNT);
        this.predictionTriggerBurstCountCached = PredictionBurstMath.countTriggerBursts(samples);
        this.medianPredictionRpmCached = predictionMetrics.medianPredictionRpm;
        this.aeFuelTableGuidance = buildAeFuelTableGuidance();
    }

    private static double safePositive(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double zeroIfNaN(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    int getIndex() {
        return index;
    }

    boolean isAccepted() {
        return accepted;
    }

    boolean isTpsAeFuelProved() {
        return tpsAeFuelProved;
    }

    String getEventClass() {
        return eventClass;
    }

    String getReason() {
        return reason;
    }

    List<LiveSample> getSamples() {
        return samples;
    }

    boolean hasMapPrediction() {
        return mapPredictionSeenCached;
    }

    boolean hasWallWettingContribution() {
        return wallWettingSeenCached;
    }

    boolean hasInstantFuelContribution() {
        return instantFuelSeenCached;
    }

    double getMaxEffectiveMapGap() {
        return maxEffectiveMapGapCached;
    }

    double getMaxFallbackMapGap() {
        return maxFallbackMapGapCached;
    }

    double getPredictionActiveSeconds() {
        return predictionActiveSecondsCached;
    }

    CounterMath.Result getPredictionResetMetrics() {
        return predictionResetMetricsCached;
    }

    int getPredictionTriggerBurstCount() {
        return predictionTriggerBurstCountCached;
    }

    double getMedianPredictionRpm() {
        return medianPredictionRpmCached;
    }

    double getMaxTriggerRatio() {
        return maxTriggerRatio;
    }

    boolean isTriggerNearMiss() {
        return triggerNearMiss;
    }

    boolean isTinyTriggerCandidate() {
        return tinyTriggerCandidate;
    }

    String getAeFuelTableGuidance() {
        return aeFuelTableGuidance;
    }

    String getTransientMixClass() {
        return transientMixClass;
    }

    double getWallWettingToTpsAeRatio() {
        return wallWettingToTpsAeRatio;
    }

    boolean isWallCorrectionAvailable() {
        return wallCorrectionAvailable;
    }

    boolean isWallWettingPwAvailable() {
        return wallWettingPwAvailable;
    }

    boolean isDfcoSeen() {
        return dfcoSeen;
    }

    double multiplierSuggestionWeight(AeProjectSnapshot snapshot) {
        if (!tpsAeFuelProved || dfcoSeen) {
            return 0.0;
        }
        if (snapshot == null || !snapshot.isWallWettingEnabled()) {
            return otherTransientPathSeen ? 0.0 : 1.0;
        }
        if (!wallWettingPwAvailable) {
            return 0.0;
        }
        if (!Double.isFinite(wallWettingToTpsAeRatio)) {
            return 0.0;
        }
        if (wallWettingToTpsAeRatio <= 0.10) {
            return 1.0;
        }
        if (wallWettingToTpsAeRatio <= 0.40) {
            return 0.50;
        }
        return 0.0;
    }

    double getTpsRise() {
        return tpsRise;
    }

    double getMapRise() {
        return mapRise;
    }

    double getMaxTps() {
        return maxTps;
    }

    double getMaxTpsAeTo() {
        return maxTpsAeTo;
    }

    double getMaxLeanLambdaError() {
        return maxLeanLambdaError;
    }

    double getMaxRichLambdaError() {
        return maxRichLambdaError;
    }

    double getEarlyLeanLambdaError() {
        return earlyLeanLambdaError;
    }

    double getLateRichLambdaError() {
        return lateRichLambdaError;
    }

    String toDisplayText() {
        String status = accepted ? eventClass : "Rejected / " + eventClass;
        return status + " event #" + index
                + " | " + F1.format(durationSeconds()) + " s"
                + " | TPS " + fmt1(startTps) + " -> " + fmt1(endTps) + " %"
                + " | MAP " + fmt1(startMap) + " -> " + fmt1(endMap) + " kPa"
                + " | RPM med " + fmt1(medianRpm)
                + " | max TPSdot " + fmt1(maxTpsDot) + " %/s"
                + " | smoothedDeltaTps " + fmt1(maxSmoothedDeltaTps)
                + " | AccelThreshold " + fmt1(maxAccelThreshold) + "\n"
                + "Trigger check: smoothedDeltaTps/AccelThreshold " + fmt1(maxTriggerRatio * 100.0) + "%"
                + " | margin " + fmt2(triggerMargin)
                + " | TPS rise " + fmt1(tpsRise) + "%"
                + " | MAP rise " + fmt1(mapRise) + " kPa\n"
                + mapPredictVerdict() + "\n"
                + (mapPredictWorkflow ? "Low-RPM review: " + lowRpmMapPredictReview() + "\n" : "")
                + fuelPathVerdict() + "\n"
                + "Peaks: Fuel: TPS AE add fuel ms " + F3.format(maxAeMs)
                + " | Fuel: TPS extraFuel " + F3.format(maxExtraFuel)
                + " | tpsAeCycleMult " + F3.format(maxCycleMult)
                + " | Fuel: Last inj pulse width " + F3.format(maxPw) + " ms"
                + " | fuel bursts " + fuelBurstCount + "\n"
                + "States: Fuel: TPS AE Active " + (tpsAeStateSeen ? "seen" : "not seen")
                + " | Fuel: TPSAE ExtraShot " + (extraShotSeen ? "seen" : "not seen")
                + " | dfcoActive " + (dfcoSeen ? "seen" : "not seen") + "\n"
                + "Other transient paths: Fuel: wall correction " + availableRange(wallCorrectionAvailable, minWallCorrection, maxWallCorrection)
                + " | fuel wallwetting injection time " + availableRange(wallWettingPwAvailable, minWallWettingPw, maxWallWettingPw) + " ms\n"
                + "Transient contribution: " + transientMixClass
                + " | Wall Wetting/TPS AE ratio " + ratioText(wallWettingToTpsAeRatio) + "\n"
                + "Lambda error: lean max +" + F2.format(maxLeanLambdaError)
                + " λ, rich max " + F2.format(maxRichLambdaError)
                + " λ | lean area " + F2.format(leanArea)
                + " | rich area " + F2.format(richArea) + " | " + reason + "\n"
                + (mapPredictWorkflow ? "Workflow guidance: " : "AE fuel table guidance: ") + aeFuelTableGuidance;
    }

    private String mapPredictVerdict() {
        boolean active = false;
        double maxGap = 0.0;
        double maxFallbackGap = 0.0;
        double activeSeconds = 0.0;
        LiveSample previous = null;
        for (LiveSample sample : samples) {
            boolean activeNow = sample.bool(ChannelRole.MAP_PRED_ACTIVE);
            active = active || activeNow;
            double map = sample.get(ChannelRole.MAP);
            double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
            double fallback = sample.get(ChannelRole.FALLBACK_MAP);
            if (Double.isFinite(map) && Double.isFinite(effective)) {
                maxGap = Math.max(maxGap, effective - map);
            }
            if (Double.isFinite(map) && Double.isFinite(fallback)) {
                maxFallbackGap = Math.max(maxFallbackGap, fallback - map);
            }
            if (previous != null && activeNow) {
                activeSeconds += Math.max(0.0, sample.getSeconds() - previous.getSeconds());
            }
            previous = sample;
        }
        CounterMath.Result resets = predictionResetMetricsCached;
        return "MAP Predict: " + (active ? "active" : "not active")
                + " | active " + F2.format(activeSeconds) + " s"
                + " | max effectiveMap-MAP gap " + F2.format(maxGap) + " kPa"
                + " | max fallbackMap-MAP gap " + F2.format(maxFallbackGap) + " kPa"
                + " | trigger bursts " + predictionTriggerBurstCountCached
                + " | timer counter increments " + resets.shortText();
    }

    private String fuelPathVerdict() {
        if (mapPredictWorkflow) {
            boolean prediction = false;
            boolean wall = false;
            boolean instant = false;
            for (LiveSample sample : samples) {
                prediction = prediction || TransientSignals.mapPredictionVisible(sample);
                wall = wall || TransientSignals.wallWettingVisible(sample);
                instant = instant || TransientSignals.instantFuelVisible(sample);
            }
            if (prediction && wall && instant) {
                return "Transient path: MAP Predict + Wall Wetting + Instant Fuel were visible.";
            }
            if (prediction && wall) {
                return "Transient path: MAP Predict + Wall Wetting were visible.";
            }
            if (prediction) {
                return "Transient path: MAP Predict was visible; TPS cycle fuel is not required in this workflow.";
            }
            if (wall) {
                return "Transient path: Wall Wetting was visible without MAP Predict.";
            }
            if (instant) {
                return "Transient path: Instant Fuel was visible without MAP Predict.";
            }
            if (triggerNearMiss) {
                return "Transient path: shared TPS-change detector near miss; MAP Predict did not activate.";
            }
            if (tpsAeStateSeen) {
                return "Transient path: shared TPS-change detector state was seen, but no MAP Predict/fuel contribution followed.";
            }
            return "Transient path: no MAP Predict, Wall Wetting, or Instant Fuel contribution visible.";
        }

        if (tpsAeFuelProved) {
            if (tinyTriggerCandidate) {
                return "TPS AE fuel path: PROVED ACTIVE in this event. Note: small TPS/MAP movement, possible over-sensitive trigger candidate.";
            }
            return "TPS AE fuel path: PROVED ACTIVE in this event.";
        }
        if (triggerNearMiss) {
            return "TPS AE fuel path: near miss. Pedal movement reached 80–99% of AccelThreshold but no TPS AE fuel was visible.";
        }
        if (tpsAeStateSeen) {
            return "TPS AE fuel path: Fuel: TPS AE Active was seen, but TPS AE fuel stayed zero.";
        }
        if (otherTransientPathSeen) {
            return "TPS AE fuel path: no TPS AE fuel visible; other transient path(s) were active.";
        }
        return "TPS AE fuel path: no EpicEFI AE state or transient fuel contribution visible.";
    }

    private boolean mapPredictionSeen() {
        return mapPredictionSeenCached;
    }

    private double maxEffectiveMapGap() {
        return maxEffectiveMapGapCached;
    }

    private double maxFallbackMapGap() {
        return maxFallbackMapGapCached;
    }

    private double predictionActiveSeconds() {
        return predictionActiveSecondsCached;
    }

    private String lowRpmMapPredictReview() {
        if (!mapPredictionSeen() || !Double.isFinite(medianRpm) || medianRpm >= 2200.0) {
            return "not a low-RPM MAP Predict event";
        }
        double gap = maxEffectiveMapGap();
        CounterMath.Result resets = predictionResetMetricsCached;
        if (gap >= 18.0 && maxRichLambdaError <= -0.10) {
            return "review estimate/blend: large predicted-MAP gap with rich response";
        }
        if (gap <= 10.0 && maxLeanLambdaError >= 0.10) {
            return "review estimate/blend: small predicted-MAP gap with lean response";
        }
        if (predictionTriggerBurstCountCached > 1) {
            return "review detector/blend: multiple separate TPS-change bursts below 2200 RPM";
        }
        if (resets.hasDiscontinuity()) {
            return "reset counter discontinuity; do not interpret raw jump as reset count";
        }
        return "low-RPM event captured; gather repeats before changing MAP Estimate or Blend Duration";
    }

    String toCsvHeader() {
        return quote("event_index") + "," + quote("event_status") + "," + quote("event_class") + "," + quote("event_reason")
                + "," + quote("event_duration_seconds")
                + "," + quote("event_sample_count")
                + "," + quote("event_start_seconds")
                + "," + quote("event_end_seconds")
                + "," + quote("tps_ae_fuel_proved")
                + "," + quote("tps_ae_state_seen")
                + "," + quote("other_transient_path_seen")
                + "," + quote("fuel_burst_count")
                + "," + quote("event_trigger_ratio_pct")
                + "," + quote("event_trigger_margin")
                + "," + quote("event_tps_rise")
                + "," + quote("event_map_rise")
                + "," + quote("event_ae_fuel_table_guidance")
                + "," + quote("event_early_lean_lambda_error")
                + "," + quote("event_early_rich_lambda_error")
                + "," + quote("event_mid_lean_lambda_error")
                + "," + quote("event_mid_rich_lambda_error")
                + "," + quote("event_late_lean_lambda_error")
                + "," + quote("event_late_rich_lambda_error")
                + "," + quote("event_transient_mix_class")
                + "," + quote("event_wall_wetting_to_tps_ae_ratio_pct")
                + "," + quote("event_wall_correction_channel_available")
                + "," + quote("event_wall_wetting_pw_channel_available")
                + "," + quote("event_workflow")
                + "," + quote("event_map_predict_seen")
                + "," + quote("event_prediction_active_seconds")
                + "," + quote("event_max_effective_map_gap_kpa")
                + "," + quote("event_max_fallback_map_gap_kpa")
                + "," + quote("event_prediction_timer_resets")
                + "," + quote("event_prediction_trigger_bursts")
                + "," + quote("event_prediction_counter_wraps")
                + "," + quote("event_prediction_counter_discontinuities")
                + "," + quote("event_low_rpm_map_predict_review")
                + "," + quote("sample_seconds")
                + "," + quote("RPM")
                + "," + quote("TPS")
                + "," + quote("TPSdot")
                + "," + quote("smoothedDeltaTps")
                + "," + quote("AccelThreshold")
                + "," + quote("MAP")
                + "," + quote("baroPressure")
                + "," + quote("fallbackMap")
                + "," + quote("effectiveMap")
                + "," + quote("isMapPredictionActive")
                + "," + quote("predTimerResetCnt")
                + "," + quote("mapPredEventOver")
                + "," + quote("MAPdot")
                + "," + quote("Lambda")
                + "," + quote("Target lambda")
                + "," + quote("AFR")
                + "," + quote("Target AFR")
                + "," + quote("Fuel: Last inj pulse width")
                + "," + quote("Injector duty cycle")
                + "," + quote("Timing: ignition")
                + "," + quote("Boost: Target")
                + "," + quote("Fuel: TPS AE add fuel ms")
                + "," + quote("Fuel: TPS extraFuel")
                + "," + quote("Engine cycles AE duration")
                + "," + quote("tpsAeCycleMult")
                + "," + quote("Fuel: TPS AE Active")
                + "," + quote("Fuel: TPSAE ExtraShot")
                + "," + quote("Fuel: wall correction")
                + "," + quote("fuel wallwetting injection time")
                + "," + quote("aeInstantPulsePw")
                + "," + quote("aeInstantPulseCnt")
                + "," + quote("m_tpsExtraShotTimer")
                + "," + quote("dfcoActive")
                + "," + quote("Total fuel cut")
                + "," + quote("totalSparkCut")
                + "," + quote("Ign: Cut Code")
                + "," + quote("Fuel: Cut Code")
                + "," + quote("stopEngineCode")
                + "," + quote("ignitionFault")
                + "," + quote("injectorFault")
                + "," + quote("Error: Trigger")
                + "," + quote("Trigger Error Counter")
                + "," + quote("Ignition: overDwellNotScheduled")
                + "," + quote("Ignition: overcharge warnings")
                + "," + quote("Ignition: undecharge warnings")
                + "," + quote("Ignition: sparkOutOfOrder")
                + "," + quote("Fuel pressure _high")
                + "," + quote("Fuel pressure _low")
                + "," + quote("STFT: Bank 1")
                + "," + quote("CLT")
                + "," + quote("MAT")
                + "," + quote("Batt V");
    }

    List<String> toCsvRows() {
        List<String> rows = new ArrayList<String>();
        CounterMath.Result resetMetrics = predictionResetMetricsCached;
        boolean predictionSeen = mapPredictionSeen();
        double predictionSeconds = predictionActiveSeconds();
        double effectiveGap = maxEffectiveMapGap();
        double fallbackGap = maxFallbackMapGap();
        String lowRpmReview = lowRpmMapPredictReview();
        for (LiveSample sample : samples) {
            rows.add(num(index)
                    + "," + quote(accepted ? "accepted" : "rejected")
                    + "," + quote(eventClass)
                    + "," + quote(reason)
                    + "," + num(durationSeconds())
                    + "," + num(samples.size())
                    + "," + num(startSeconds)
                    + "," + num(endSeconds)
                    + "," + (tpsAeFuelProved ? "1" : "0")
                    + "," + (tpsAeStateSeen ? "1" : "0")
                    + "," + (otherTransientPathSeen ? "1" : "0")
                    + "," + num(fuelBurstCount)
                    + "," + num(maxTriggerRatio * 100.0)
                    + "," + num(triggerMargin)
                    + "," + num(tpsRise)
                    + "," + num(mapRise)
                    + "," + quote(aeFuelTableGuidance)
                    + "," + num(earlyLeanLambdaError)
                    + "," + num(earlyRichLambdaError)
                    + "," + num(midLeanLambdaError)
                    + "," + num(midRichLambdaError)
                    + "," + num(lateLeanLambdaError)
                    + "," + num(lateRichLambdaError)
                    + "," + quote(transientMixClass)
                    + "," + num(Double.isFinite(wallWettingToTpsAeRatio) ? wallWettingToTpsAeRatio * 100.0 : Double.NaN)
                    + "," + (wallCorrectionAvailable ? "1" : "0")
                    + "," + (wallWettingPwAvailable ? "1" : "0")
                    + "," + quote(mapPredictWorkflow ? "MAP Predict workflow" : "TPS cycle AE workflow")
                    + "," + (predictionSeen ? "1" : "0")
                    + "," + num(predictionSeconds)
                    + "," + num(effectiveGap)
                    + "," + num(fallbackGap)
                    + "," + num(resetMetrics.getIncrements())
                    + "," + num(predictionTriggerBurstCountCached)
                    + "," + num(resetMetrics.getWraps())
                    + "," + num(resetMetrics.getAnomalies())
                    + "," + quote(lowRpmReview)
                    + "," + num(sample.getSeconds())
                    + "," + num(sample.get(ChannelRole.RPM))
                    + "," + num(sample.get(ChannelRole.TPS))
                    + "," + num(sample.getTpsDot())
                    + "," + num(sample.get(ChannelRole.SMOOTHED_DELTA_TPS))
                    + "," + num(sample.get(ChannelRole.ACCEL_THRESHOLD))
                    + "," + num(sample.get(ChannelRole.MAP))
                    + "," + num(sample.get(ChannelRole.BARO))
                    + "," + num(sample.get(ChannelRole.FALLBACK_MAP))
                    + "," + num(sample.get(ChannelRole.EFFECTIVE_MAP))
                    + "," + (sample.bool(ChannelRole.MAP_PRED_ACTIVE) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.MAP_PRED_RESET_CNT))
                    + "," + (sample.bool(ChannelRole.MAP_PRED_EVENT_OVER) ? "1" : "0")
                    + "," + num(sample.getMapDot())
                    + "," + num(sample.get(ChannelRole.LAMBDA))
                    + "," + num(sample.get(ChannelRole.TARGET_LAMBDA))
                    + "," + num(sample.get(ChannelRole.AFR))
                    + "," + num(sample.get(ChannelRole.TARGET_AFR))
                    + "," + num(sample.get(ChannelRole.PW))
                    + "," + num(sample.get(ChannelRole.INJ_DUTY))
                    + "," + num(sample.get(ChannelRole.IGNITION_TIMING))
                    + "," + num(sample.get(ChannelRole.BOOST_TARGET))
                    + "," + num(sample.get(ChannelRole.AE_ADD_MS))
                    + "," + num(sample.get(ChannelRole.EXTRA_FUEL))
                    + "," + num(sample.get(ChannelRole.TPS_AE_CYCLE_CNT))
                    + "," + num(sample.get(ChannelRole.TPS_AE_CYCLE_MULT))
                    + "," + (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.AE_EXTRA_SHOT) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.WALL_CORRECTION))
                    + "," + num(sample.get(ChannelRole.WALL_WETTING_PW))
                    + "," + num(sample.get(ChannelRole.INSTANT_PULSE_PW))
                    + "," + num(sample.get(ChannelRole.INSTANT_PULSE_CNT))
                    + "," + num(sample.get(ChannelRole.EXTRA_SHOT_TIMER))
                    + "," + (sample.bool(ChannelRole.DFCO) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.FUEL_CUT) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.TOTAL_SPARK_CUT))
                    + "," + num(sample.get(ChannelRole.IGN_CUT_CODE))
                    + "," + num(sample.get(ChannelRole.FUEL_CUT_CODE))
                    + "," + num(sample.get(ChannelRole.STOP_ENGINE_CODE))
                    + "," + (sample.bool(ChannelRole.IGNITION_FAULT) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.INJECTOR_FAULT) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.TRIGGER_ERROR) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.TRIGGER_ERROR_COUNT))
                    + "," + num(sample.get(ChannelRole.IGN_OVERDWELL))
                    + "," + num(sample.get(ChannelRole.IGN_OVERCHARGE_WARNINGS))
                    + "," + num(sample.get(ChannelRole.IGN_UNDERCHARGE_WARNINGS))
                    + "," + num(sample.get(ChannelRole.IGN_SPARK_OUT_OF_ORDER))
                    + "," + num(sample.get(ChannelRole.FUEL_PRESSURE_HIGH))
                    + "," + num(sample.get(ChannelRole.FUEL_PRESSURE_LOW))
                    + "," + num(sample.get(ChannelRole.STFT1))
                    + "," + num(sample.get(ChannelRole.COOLANT))
                    + "," + num(sample.get(ChannelRole.IAT))
                    + "," + num(sample.get(ChannelRole.BATTERY)));
        }
        return rows;
    }

    private static String classifyTransientMix(boolean tpsFuel,
                                               boolean otherPath,
                                               boolean wallPwAvailable,
                                               double wallAbsPw,
                                               double tpsAeReference,
                                               boolean extraShot) {
        if (extraShot) {
            return tpsFuel ? "TPS AE + Instant Fuel Pulse" : "Instant Fuel Pulse only";
        }
        if (!tpsFuel) {
            return otherPath ? "Wall Wetting/other transient path only" : "No transient fuel visible";
        }
        if (!wallPwAvailable) {
            return otherPath ? "TPS AE + unquantified other path" : "TPS AE; Wall Wetting channel unavailable";
        }
        if (wallAbsPw <= 0.0001) {
            return "TPS AE isolated";
        }
        if (tpsAeReference <= 0.002) {
            return "Wall Wetting dominant";
        }
        double ratio = wallAbsPw / tpsAeReference;
        if (ratio <= 0.10) {
            return "TPS AE dominant (Wall Wetting <=10%)";
        }
        if (ratio <= 0.40) {
            return "Combined, TPS AE dominant (Wall Wetting 10-40%)";
        }
        if (ratio <= 1.00) {
            return "Combined mixed contribution";
        }
        return "Wall Wetting dominant";
    }

    private static String availableRange(boolean available, double min, double max) {
        return available ? F3.format(min) + ".." + F3.format(max) : "channel unavailable";
    }

    private static String ratioText(double ratio) {
        return Double.isFinite(ratio) ? F1.format(ratio * 100.0) + "%" : "n/a";
    }

    private static int classifyAeWindow(LiveSample sample, long firstNano) {
        double cycle = sample.get(ChannelRole.TPS_AE_CYCLE_CNT);
        if (Double.isFinite(cycle)) {
            if (cycle <= 4.0) {
                return 0;
            }
            if (cycle <= 10.0) {
                return 1;
            }
            return 2;
        }
        if (firstNano > 0L && sample.getNanoTime() >= firstNano) {
            double elapsed = (sample.getNanoTime() - firstNano) / 1000000000.0;
            if (elapsed <= 0.25) {
                return 0;
            }
            if (elapsed <= 0.85) {
                return 1;
            }
        }
        return 2;
    }

    private String buildAeFuelTableGuidance() {
        if (mapPredictWorkflow) {
            return "TPS cycle enrichment is disabled. Tune MAP Estimate and Predictive Map Blend Duration first, then evaluate Wall Wetting and only later Instant Fuel.";
        }
        if (dfcoSeen) {
            return "Ignore for AE fuel-table tuning: dfcoActive/fuel cut was visible.";
        }
        if (otherTransientPathSeen && !tpsAeFuelProved) {
            return "Do not tune TPS AE fuel table from this event: another transient path was active instead.";
        }
        if (triggerNearMiss) {
            return "Trigger guidance: review TPS AE Rate of change vs RPM; fuel table was not exercised.";
        }
        if (!tpsAeFuelProved) {
            return "No TPS AE fuel-table guidance: Fuel: TPS AE add fuel ms and Fuel: TPS extraFuel stayed zero.";
        }
        if (wallWettingPwAvailable && Double.isFinite(wallWettingToTpsAeRatio)
                && wallWettingToTpsAeRatio > 0.40) {
            return "Combined guidance only: Wall Wetting contribution was substantial relative to TPS AE; do not tune the TPS AE multiplier table from this event alone.";
        }
        String combinedPrefix = wallWettingPwAvailable && Double.isFinite(wallWettingToTpsAeRatio)
                && wallWettingToTpsAeRatio > 0.10
                ? "Combined TPS AE + Wall Wetting evidence: " : "";

        boolean earlyLean = earlyLeanLambdaError > 0.08;
        boolean strongEarlyLean = earlyLeanLambdaError > 0.14;
        boolean midRich = midRichLambdaError < -0.08;
        boolean lateRich = lateRichLambdaError < -0.08;
        boolean strongLateRich = lateRichLambdaError < -0.14;
        boolean overallRich = maxRichLambdaError < -0.16 && Math.abs(maxRichLambdaError) > maxLeanLambdaError * 1.2;
        boolean overallLean = maxLeanLambdaError > 0.16 && maxLeanLambdaError > Math.abs(maxRichLambdaError) * 1.2;

        if (earlyLean && (lateRich || midRich)) {
            return combinedPrefix + "Shape guidance: early lean with later rich; keep or slightly increase cycles 0-4 only after repeats, and reduce/shorten cycles 6+ first.";
        }
        if (strongEarlyLean && !midRich && !lateRich) {
            return combinedPrefix + "Amount guidance: add a little early AE fuel in the exercised TPS-to row, mainly cycles 0-4.";
        }
        if (strongLateRich || (overallRich && lateRich)) {
            return combinedPrefix + "Rate guidance: AE hangs rich late; reduce cycles 6-12 or shorten the nonzero tail before reducing cycle 0-4.";
        }
        if (overallLean) {
            return combinedPrefix + "Amount guidance: event remains lean overall; add fuel to exercised cells, but confirm lambda delay and base VE first.";
        }
        if (Math.abs(maxRichLambdaError) < 0.08 && maxLeanLambdaError < 0.08) {
            return combinedPrefix + "Good shape candidate: Lambda stayed close to Target lambda; gather repeats before changing cells.";
        }
        return combinedPrefix + "Mixed result: gather repeats in the same TPS/RPM area before changing amount or decay.";
    }

    private static LiveSample firstRunning(List<LiveSample> samples) {
        for (LiveSample sample : samples) {
            double rpm = sample.get(ChannelRole.RPM);
            if (Double.isFinite(rpm) && rpm >= 400.0) {
                return sample;
            }
        }
        return samples.isEmpty() ? null : samples.get(0);
    }

    private static LiveSample lastRunning(List<LiveSample> samples) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            LiveSample sample = samples.get(i);
            double rpm = sample.get(ChannelRole.RPM);
            if (Double.isFinite(rpm) && rpm >= 400.0) {
                return sample;
            }
        }
        return samples.isEmpty() ? null : samples.get(samples.size() - 1);
    }

    private static double median(List<LiveSample> samples, ChannelRole role, double minimum) {
        double[] values = new double[samples.size()];
        int count = 0;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (Double.isFinite(value) && value >= minimum) {
                values[count++] = value;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        values = Arrays.copyOf(values, count);
        Arrays.sort(values);
        int middle = count / 2;
        if ((count & 1) == 1) {
            return values[middle];
        }
        return (values[middle - 1] + values[middle]) / 2.0;
    }

    private double durationSeconds() {
        if (!Double.isFinite(startSeconds) || !Double.isFinite(endSeconds)) {
            return Double.NaN;
        }
        if (samples.size() >= 2) {
            long firstNano = samples.get(0).getNanoTime();
            long lastNano = samples.get(samples.size() - 1).getNanoTime();
            if (firstNano > 0L && lastNano >= firstNano) {
                return (lastNano - firstNano) / 1000000000.0;
            }
        }
        return Math.max(0.0, endSeconds - startSeconds);
    }

    private static String fmt1(double value) {
        return Double.isFinite(value) ? F1.format(value) : "n/a";
    }

    private static String fmt2(double value) {
        return Double.isFinite(value) ? F2.format(value) : "n/a";
    }

    private static String num(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "";
    }

    private static String num(int value) {
        return Integer.toString(value);
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value.replace("\"", "'");
        return "\"" + safe + "\"";
    }

    private static final class PredictionMetrics {
        final boolean predictionSeen;
        final boolean wallSeen;
        final boolean instantSeen;
        final double maxEffectiveGap;
        final double maxFallbackGap;
        final double activeSeconds;
        final double medianPredictionRpm;

        private PredictionMetrics(boolean predictionSeen, boolean wallSeen, boolean instantSeen,
                                  double maxEffectiveGap, double maxFallbackGap,
                                  double activeSeconds, double medianPredictionRpm) {
            this.predictionSeen = predictionSeen;
            this.wallSeen = wallSeen;
            this.instantSeen = instantSeen;
            this.maxEffectiveGap = maxEffectiveGap;
            this.maxFallbackGap = maxFallbackGap;
            this.activeSeconds = activeSeconds;
            this.medianPredictionRpm = medianPredictionRpm;
        }

        static PredictionMetrics build(List<LiveSample> samples) {
            boolean prediction = false;
            boolean wall = false;
            boolean instant = false;
            double maxEffectiveGap = 0.0;
            double maxFallbackGap = 0.0;
            double activeSeconds = 0.0;
            double[] rpmValues = new double[samples.size()];
            int rpmCount = 0;
            LiveSample previous = null;
            for (LiveSample sample : samples) {
                boolean activeNow = TransientSignals.mapPredictionVisible(sample);
                prediction = prediction || activeNow;
                wall = wall || TransientSignals.wallWettingVisible(sample);
                instant = instant || TransientSignals.instantFuelVisible(sample);
                double map = sample.get(ChannelRole.MAP);
                double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
                double fallback = sample.get(ChannelRole.FALLBACK_MAP);
                if (Double.isFinite(map) && Double.isFinite(effective)) {
                    maxEffectiveGap = Math.max(maxEffectiveGap, effective - map);
                }
                if (Double.isFinite(map) && Double.isFinite(fallback)) {
                    maxFallbackGap = Math.max(maxFallbackGap, fallback - map);
                }
                if (previous != null && sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                    activeSeconds += Math.max(0.0, sample.getSeconds() - previous.getSeconds());
                }
                if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                    double rpm = sample.get(ChannelRole.RPM);
                    if (Double.isFinite(rpm) && rpm >= 400.0) {
                        rpmValues[rpmCount++] = rpm;
                    }
                }
                previous = sample;
            }
            double medianRpm = Double.NaN;
            if (rpmCount > 0) {
                Arrays.sort(rpmValues, 0, rpmCount);
                medianRpm = (rpmCount & 1) == 1
                        ? rpmValues[rpmCount / 2]
                        : (rpmValues[rpmCount / 2 - 1] + rpmValues[rpmCount / 2]) / 2.0;
            }
            return new PredictionMetrics(prediction, wall, instant, maxEffectiveGap,
                    maxFallbackGap, activeSeconds, medianRpm);
        }
    }

}
