package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Classification and tuning-policy result derived from immutable event metrics. */
final class TransientEventAssessment {
    final double wallWettingToTpsAeRatio;
    final String transientMixClass;
    final String aeFuelTableGuidance;
    final String fuelPathVerdict;
    final String lowRpmMapPredictReview;

    private final boolean tpsAeFuelProved;
    private final boolean dfcoSeen;
    private final boolean otherTransientPathSeen;
    private final boolean wallWettingPwAvailable;

    private TransientEventAssessment(double wallWettingToTpsAeRatio,
                                     String transientMixClass,
                                     String aeFuelTableGuidance,
                                     String fuelPathVerdict,
                                     String lowRpmMapPredictReview,
                                     boolean tpsAeFuelProved,
                                     boolean dfcoSeen,
                                     boolean otherTransientPathSeen,
                                     boolean wallWettingPwAvailable) {
        this.wallWettingToTpsAeRatio = wallWettingToTpsAeRatio;
        this.transientMixClass = transientMixClass;
        this.aeFuelTableGuidance = aeFuelTableGuidance;
        this.fuelPathVerdict = fuelPathVerdict;
        this.lowRpmMapPredictReview = lowRpmMapPredictReview;
        this.tpsAeFuelProved = tpsAeFuelProved;
        this.dfcoSeen = dfcoSeen;
        this.otherTransientPathSeen = otherTransientPathSeen;
        this.wallWettingPwAvailable = wallWettingPwAvailable;
    }

    static TransientEventAssessment build(boolean mapPredictWorkflow,
                                          TransientEventAnalyzer.Result analysis) {
        double wallAbsPw = Math.max(Math.abs(analysis.maxWallWettingPw),
                Math.abs(analysis.minWallWettingPw));
        double tpsAeReference = Math.max(analysis.maxAeMs, analysis.maxExtraFuel);
        double ratio = tpsAeReference > 0.002 ? wallAbsPw / tpsAeReference : Double.NaN;
        String mix = classifyTransientMix(analysis.tpsAeFuelProved,
                analysis.otherTransientPathSeen, analysis.wallWettingPwAvailable,
                wallAbsPw, tpsAeReference, analysis.extraShotSeen);
        String guidance = buildAeFuelTableGuidance(mapPredictWorkflow, analysis, ratio);
        String pathVerdict = fuelPathVerdict(mapPredictWorkflow, analysis);
        String lowRpmReview = lowRpmMapPredictReview(analysis);
        return new TransientEventAssessment(ratio, mix, guidance, pathVerdict, lowRpmReview,
                analysis.tpsAeFuelProved, analysis.dfcoSeen,
                analysis.otherTransientPathSeen, analysis.wallWettingPwAvailable);
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
            return otherPath ? "TPS AE + unquantified other path"
                    : "TPS AE; Wall Wetting channel unavailable";
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

    private static String buildAeFuelTableGuidance(boolean mapPredictWorkflow,
                                                   TransientEventAnalyzer.Result analysis,
                                                   double wallWettingToTpsAeRatio) {
        if (mapPredictWorkflow) {
            return "TPS cycle enrichment is disabled. Tune MAP Estimate and Predictive Map Blend Duration first, then evaluate Wall Wetting and only later Instant Fuel.";
        }
        if (analysis.dfcoSeen) {
            return "Ignore for AE fuel-table tuning: dfcoActive/fuel cut was visible.";
        }
        if (analysis.otherTransientPathSeen && !analysis.tpsAeFuelProved) {
            return "Do not tune TPS AE fuel table from this event: another transient path was active instead.";
        }
        if (analysis.triggerNearMiss) {
            return "Trigger guidance: review TPS AE Rate of change vs RPM; fuel table was not exercised.";
        }
        if (!analysis.tpsAeFuelProved) {
            return "No TPS AE fuel-table guidance: Fuel: TPS AE add fuel ms and Fuel: TPS extraFuel stayed zero.";
        }
        if (analysis.wallWettingPwAvailable && Double.isFinite(wallWettingToTpsAeRatio)
                && wallWettingToTpsAeRatio > 0.40) {
            return "Combined guidance only: Wall Wetting contribution was substantial relative to TPS AE; do not tune the TPS AE multiplier table from this event alone.";
        }
        String combinedPrefix = analysis.wallWettingPwAvailable
                && Double.isFinite(wallWettingToTpsAeRatio)
                && wallWettingToTpsAeRatio > 0.10
                ? "Combined TPS AE + Wall Wetting evidence: " : "";

        boolean earlyLean = analysis.earlyLeanLambdaError > 0.08;
        boolean strongEarlyLean = analysis.earlyLeanLambdaError > 0.14;
        boolean midRich = analysis.midRichLambdaError < -0.08;
        boolean lateRich = analysis.lateRichLambdaError < -0.08;
        boolean strongLateRich = analysis.lateRichLambdaError < -0.14;
        boolean overallRich = analysis.maxRichLambdaError < -0.16
                && Math.abs(analysis.maxRichLambdaError) > analysis.maxLeanLambdaError * 1.2;
        boolean overallLean = analysis.maxLeanLambdaError > 0.16
                && analysis.maxLeanLambdaError > Math.abs(analysis.maxRichLambdaError) * 1.2;

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
        if (Math.abs(analysis.maxRichLambdaError) < 0.08
                && analysis.maxLeanLambdaError < 0.08) {
            return combinedPrefix + "Good shape candidate: Lambda stayed close to Target lambda; gather repeats before changing cells.";
        }
        return combinedPrefix + "Mixed result: gather repeats in the same TPS/RPM area before changing amount or decay.";
    }

    private static String fuelPathVerdict(boolean mapPredictWorkflow,
                                          TransientEventAnalyzer.Result analysis) {
        if (mapPredictWorkflow) {
            boolean prediction = analysis.predictionMetrics.predictionSeen;
            boolean wall = analysis.predictionMetrics.wallSeen;
            boolean instant = analysis.predictionMetrics.instantSeen;
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
            if (analysis.triggerNearMiss) {
                return "Transient path: shared TPS-change detector near miss; MAP Predict did not activate.";
            }
            if (analysis.tpsAeStateSeen) {
                return "Transient path: shared TPS-change detector state was seen, but no MAP Predict/fuel contribution followed.";
            }
            return "Transient path: no MAP Predict, Wall Wetting, or Instant Fuel contribution visible.";
        }

        if (analysis.tpsAeFuelProved) {
            if (analysis.tinyTriggerCandidate) {
                return "TPS AE fuel path: PROVED ACTIVE in this event. Note: small TPS/MAP movement, possible over-sensitive trigger candidate.";
            }
            return "TPS AE fuel path: PROVED ACTIVE in this event.";
        }
        if (analysis.triggerNearMiss) {
            return "TPS AE fuel path: near miss. Pedal movement reached 80–99% of AccelThreshold but no TPS AE fuel was visible.";
        }
        if (analysis.tpsAeStateSeen) {
            return "TPS AE fuel path: Fuel: TPS AE Active was seen, but TPS AE fuel stayed zero.";
        }
        if (analysis.otherTransientPathSeen) {
            return "TPS AE fuel path: no TPS AE fuel visible; other transient path(s) were active.";
        }
        return "TPS AE fuel path: no EpicEFI AE state or transient fuel contribution visible.";
    }

    private static String lowRpmMapPredictReview(TransientEventAnalyzer.Result analysis) {
        if (!analysis.predictionMetrics.predictionSeen
                || !Double.isFinite(analysis.medianRpm)
                || analysis.medianRpm >= 2200.0) {
            return "not a low-RPM MAP Predict event";
        }
        double gap = analysis.predictionMetrics.maxEffectiveGap;
        if (gap >= 18.0 && analysis.maxRichLambdaError <= -0.10) {
            return "review estimate/blend: large predicted-MAP gap with rich response";
        }
        if (gap <= 10.0 && analysis.maxLeanLambdaError >= 0.10) {
            return "review estimate/blend: small predicted-MAP gap with lean response";
        }
        if (analysis.predictionTriggerBurstCount > 1) {
            return "review detector/blend: multiple separate TPS-change bursts below 2200 RPM";
        }
        if (analysis.predictionResetMetrics.hasDiscontinuity()) {
            return "reset counter discontinuity; do not interpret raw jump as reset count";
        }
        return "low-RPM event captured; gather repeats before changing MAP Estimate or Blend Duration";
    }
}
