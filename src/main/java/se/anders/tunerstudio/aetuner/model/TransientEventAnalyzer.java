package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.Arrays;
import java.util.List;

/** Pure retained-sample analysis for one passive transient event. */
final class TransientEventAnalyzer {
    private TransientEventAnalyzer() { }

    static Result analyze(List<LiveSample> samples) {
        LiveSample first = samples.isEmpty() ? null : samples.get(0);
        LiveSample last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        LiveSample firstRunning = firstRunning(samples);
        LiveSample lastRunning = lastRunning(samples);

        double startSeconds = first == null ? Double.NaN : first.getSeconds();
        double endSeconds = last == null ? Double.NaN : last.getSeconds();
        double medianRpm = median(samples, ChannelRole.RPM, 400.0);
        double startMap = firstRunning == null ? Double.NaN : firstRunning.get(ChannelRole.MAP);
        double endMap = lastRunning == null ? Double.NaN : lastRunning.get(ChannelRole.MAP);
        double startTps = firstRunning == null ? Double.NaN : firstRunning.get(ChannelRole.TPS);
        double endTps = lastRunning == null ? Double.NaN : lastRunning.get(ChannelRole.TPS);

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
            smoothedDeltaTpsMax = Math.max(smoothedDeltaTpsMax,
                    safePositive(sample.get(ChannelRole.SMOOTHED_DELTA_TPS)));
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
            cycleMultMax = Math.max(cycleMultMax,
                    safePositive(sample.get(ChannelRole.TPS_AE_CYCLE_MULT)));

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
            dfco = dfco || sample.bool(ChannelRole.DFCO)
                    || safePositive(sample.get(ChannelRole.FUEL_CUT)) > 0.01;
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
                    || (Double.isFinite(realMapForPrediction)
                    && Double.isFinite(effectiveMapForPrediction)
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

        double triggerMargin = triggerMarginMax == Double.NEGATIVE_INFINITY
                ? Double.NaN : triggerMarginMax;
        double tpsRise = Double.isInfinite(tpsMin) || Double.isInfinite(tpsMax)
                ? Double.NaN : Math.max(0.0, tpsMax - tpsMin);
        double mapRise = Double.isInfinite(mapMin) || Double.isInfinite(mapMax)
                ? Double.NaN : Math.max(0.0, mapMax - mapMin);
        double maxTps = Double.isInfinite(tpsMax) ? Double.NaN : tpsMax;
        double maxTpsAeTo = Double.isInfinite(tpsAeToMax) ? Double.NaN : tpsAeToMax;
        boolean triggerNearMiss = !tpsFuel && !aeState && !otherPath
                && triggerRatioMax >= 0.80 && triggerRatioMax < 1.0;
        boolean tinyTriggerCandidate = tpsFuel && Double.isFinite(tpsRise) && tpsRise < 6.0
                && Double.isFinite(mapRise) && mapRise < 12.0;

        MapPredictionMetrics predictionMetrics = MapPredictionMetrics.build(samples);
        CounterMath.Result predictionResetMetrics =
                CounterMath.analyze(samples, ChannelRole.MAP_PRED_RESET_CNT);
        int predictionTriggerBurstCount = PredictionBurstMath.countTriggerBursts(samples);

        return new Result(startSeconds, endSeconds, medianRpm,
                startMap, endMap, startTps, endTps,
                tpsDotMax, smoothedDeltaTpsMax, accelThresholdMax, mapDotMax,
                pwMax, aeMax, extraMax, cycleMultMax,
                wallMax, wallMin, wallPwMax, wallPwMin,
                wallCorrectionSeenAsChannel, wallWettingPwSeenAsChannel,
                extraShot, dfco, aeState, tpsFuel, otherPath, fuelBursts,
                triggerRatioMax, triggerMargin, tpsRise, mapRise, maxTps, maxTpsAeTo,
                triggerNearMiss, tinyTriggerCandidate,
                leanMax, richMax, leanSum, richSum,
                earlyLeanMax, earlyRichMax, midLeanMax, midRichMax,
                lateLeanMax, lateRichMax,
                predictionMetrics, predictionResetMetrics, predictionTriggerBurstCount);
    }

    private static double safePositive(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double zeroIfNaN(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static int classifyAeWindow(LiveSample sample, long firstNano) {
        double cycle = sample.get(ChannelRole.TPS_AE_CYCLE_CNT);
        if (Double.isFinite(cycle)) {
            if (cycle <= 4.0) return 0;
            if (cycle <= 10.0) return 1;
            return 2;
        }
        if (firstNano > 0L && sample.getNanoTime() >= firstNano) {
            double elapsed = (sample.getNanoTime() - firstNano) / 1000000000.0;
            if (elapsed <= 0.25) return 0;
            if (elapsed <= 0.85) return 1;
        }
        return 2;
    }

    private static LiveSample firstRunning(List<LiveSample> samples) {
        for (LiveSample sample : samples) {
            double rpm = sample.get(ChannelRole.RPM);
            if (Double.isFinite(rpm) && rpm >= 400.0) return sample;
        }
        return samples.isEmpty() ? null : samples.get(0);
    }

    private static LiveSample lastRunning(List<LiveSample> samples) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            LiveSample sample = samples.get(i);
            double rpm = sample.get(ChannelRole.RPM);
            if (Double.isFinite(rpm) && rpm >= 400.0) return sample;
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
        if (count == 0) return Double.NaN;
        values = Arrays.copyOf(values, count);
        Arrays.sort(values);
        int middle = count / 2;
        if ((count & 1) == 1) return values[middle];
        return (values[middle - 1] + values[middle]) / 2.0;
    }

    static final class Result {
        final double startSeconds;
        final double endSeconds;
        final double medianRpm;
        final double startMap;
        final double endMap;
        final double startTps;
        final double endTps;
        final double maxTpsDot;
        final double maxSmoothedDeltaTps;
        final double maxAccelThreshold;
        final double maxMapDot;
        final double maxPw;
        final double maxAeMs;
        final double maxExtraFuel;
        final double maxCycleMult;
        final double maxWallCorrection;
        final double minWallCorrection;
        final double maxWallWettingPw;
        final double minWallWettingPw;
        final boolean wallCorrectionAvailable;
        final boolean wallWettingPwAvailable;
        final boolean extraShotSeen;
        final boolean dfcoSeen;
        final boolean tpsAeStateSeen;
        final boolean tpsAeFuelProved;
        final boolean otherTransientPathSeen;
        final int fuelBurstCount;
        final double maxTriggerRatio;
        final double triggerMargin;
        final double tpsRise;
        final double mapRise;
        final double maxTps;
        final double maxTpsAeTo;
        final boolean triggerNearMiss;
        final boolean tinyTriggerCandidate;
        final double maxLeanLambdaError;
        final double maxRichLambdaError;
        final double leanArea;
        final double richArea;
        final double earlyLeanLambdaError;
        final double earlyRichLambdaError;
        final double midLeanLambdaError;
        final double midRichLambdaError;
        final double lateLeanLambdaError;
        final double lateRichLambdaError;
        final MapPredictionMetrics predictionMetrics;
        final CounterMath.Result predictionResetMetrics;
        final int predictionTriggerBurstCount;

        private Result(double startSeconds, double endSeconds, double medianRpm,
                       double startMap, double endMap, double startTps, double endTps,
                       double maxTpsDot, double maxSmoothedDeltaTps, double maxAccelThreshold,
                       double maxMapDot, double maxPw, double maxAeMs, double maxExtraFuel,
                       double maxCycleMult, double maxWallCorrection, double minWallCorrection,
                       double maxWallWettingPw, double minWallWettingPw,
                       boolean wallCorrectionAvailable, boolean wallWettingPwAvailable,
                       boolean extraShotSeen, boolean dfcoSeen, boolean tpsAeStateSeen,
                       boolean tpsAeFuelProved, boolean otherTransientPathSeen, int fuelBurstCount,
                       double maxTriggerRatio, double triggerMargin, double tpsRise, double mapRise,
                       double maxTps, double maxTpsAeTo, boolean triggerNearMiss,
                       boolean tinyTriggerCandidate, double maxLeanLambdaError,
                       double maxRichLambdaError, double leanArea, double richArea,
                       double earlyLeanLambdaError, double earlyRichLambdaError,
                       double midLeanLambdaError, double midRichLambdaError,
                       double lateLeanLambdaError, double lateRichLambdaError,
                       MapPredictionMetrics predictionMetrics,
                       CounterMath.Result predictionResetMetrics,
                       int predictionTriggerBurstCount) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.medianRpm = medianRpm;
            this.startMap = startMap;
            this.endMap = endMap;
            this.startTps = startTps;
            this.endTps = endTps;
            this.maxTpsDot = maxTpsDot;
            this.maxSmoothedDeltaTps = maxSmoothedDeltaTps;
            this.maxAccelThreshold = maxAccelThreshold;
            this.maxMapDot = maxMapDot;
            this.maxPw = maxPw;
            this.maxAeMs = maxAeMs;
            this.maxExtraFuel = maxExtraFuel;
            this.maxCycleMult = maxCycleMult;
            this.maxWallCorrection = maxWallCorrection;
            this.minWallCorrection = minWallCorrection;
            this.maxWallWettingPw = maxWallWettingPw;
            this.minWallWettingPw = minWallWettingPw;
            this.wallCorrectionAvailable = wallCorrectionAvailable;
            this.wallWettingPwAvailable = wallWettingPwAvailable;
            this.extraShotSeen = extraShotSeen;
            this.dfcoSeen = dfcoSeen;
            this.tpsAeStateSeen = tpsAeStateSeen;
            this.tpsAeFuelProved = tpsAeFuelProved;
            this.otherTransientPathSeen = otherTransientPathSeen;
            this.fuelBurstCount = fuelBurstCount;
            this.maxTriggerRatio = maxTriggerRatio;
            this.triggerMargin = triggerMargin;
            this.tpsRise = tpsRise;
            this.mapRise = mapRise;
            this.maxTps = maxTps;
            this.maxTpsAeTo = maxTpsAeTo;
            this.triggerNearMiss = triggerNearMiss;
            this.tinyTriggerCandidate = tinyTriggerCandidate;
            this.maxLeanLambdaError = maxLeanLambdaError;
            this.maxRichLambdaError = maxRichLambdaError;
            this.leanArea = leanArea;
            this.richArea = richArea;
            this.earlyLeanLambdaError = earlyLeanLambdaError;
            this.earlyRichLambdaError = earlyRichLambdaError;
            this.midLeanLambdaError = midLeanLambdaError;
            this.midRichLambdaError = midRichLambdaError;
            this.lateLeanLambdaError = lateLeanLambdaError;
            this.lateRichLambdaError = lateRichLambdaError;
            this.predictionMetrics = predictionMetrics;
            this.predictionResetMetrics = predictionResetMetrics;
            this.predictionTriggerBurstCount = predictionTriggerBurstCount;
        }
    }
}
