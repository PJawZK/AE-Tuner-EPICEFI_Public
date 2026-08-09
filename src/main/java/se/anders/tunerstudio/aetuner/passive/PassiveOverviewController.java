package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JSpinner;

/**
 * Presentation/evaluation controller for the passive AE Tuner status surface.
 *
 * It consumes immutable snapshots from AeTunerPanel and owns all Overview card,
 * technical-status, SessionReview-cache and Session Guidance calculations. The
 * TunerStudio callback and mutable capture state stay in the host panel.
 */
final class PassiveOverviewController {
    private final UiRefreshPresenter uiPresenter;
    private final StatusCard workflowCard;
    private final StatusCard tpsCycleCard;
    private final StatusCard mapPredictCard;
    private final StatusCard wallWettingCard;
    private final StatusCard instantFuelCard;
    private final StatusCard detectorCard;
    private final StatusCard predictionLiveCard;
    private final StatusCard mapValuesCard;
    private final StatusCard transientFuelCard;
    private final StatusCard eventProgressCard;
    private final StatusCard mapCoverageCard;
    private final StatusCard nextActionCard;
    private final StatusCard contributionReviewCard;
    private final StatusCard lowRpmReviewCard;
    private final StatusCard fullLoadSafetyCard;
    private final JSpinner mapMinimumSamples;
    private final MapEstimateCollector mapEstimateCollector;
    private final SessionMonitor sessionMonitor;
    private final RecommendationHistory recommendationHistory;
    private final TpsNoiseCalibration calibration;

    private long cachedEventReviewRevision = Long.MIN_VALUE;
    private SessionReview cachedEventReview;

    PassiveOverviewController(UiRefreshPresenter uiPresenter,
                              StatusCard workflowCard,
                              StatusCard tpsCycleCard,
                              StatusCard mapPredictCard,
                              StatusCard wallWettingCard,
                              StatusCard instantFuelCard,
                              StatusCard detectorCard,
                              StatusCard predictionLiveCard,
                              StatusCard mapValuesCard,
                              StatusCard transientFuelCard,
                              StatusCard eventProgressCard,
                              StatusCard mapCoverageCard,
                              StatusCard nextActionCard,
                              StatusCard contributionReviewCard,
                              StatusCard lowRpmReviewCard,
                              StatusCard fullLoadSafetyCard,
                              JSpinner mapMinimumSamples,
                              MapEstimateCollector mapEstimateCollector,
                              SessionMonitor sessionMonitor,
                              RecommendationHistory recommendationHistory,
                              TpsNoiseCalibration calibration) {
        this.uiPresenter = uiPresenter;
        this.workflowCard = workflowCard;
        this.tpsCycleCard = tpsCycleCard;
        this.mapPredictCard = mapPredictCard;
        this.wallWettingCard = wallWettingCard;
        this.instantFuelCard = instantFuelCard;
        this.detectorCard = detectorCard;
        this.predictionLiveCard = predictionLiveCard;
        this.mapValuesCard = mapValuesCard;
        this.transientFuelCard = transientFuelCard;
        this.eventProgressCard = eventProgressCard;
        this.mapCoverageCard = mapCoverageCard;
        this.nextActionCard = nextActionCard;
        this.contributionReviewCard = contributionReviewCard;
        this.lowRpmReviewCard = lowRpmReviewCard;
        this.fullLoadSafetyCard = fullLoadSafetyCard;
        this.mapMinimumSamples = mapMinimumSamples;
        this.mapEstimateCollector = mapEstimateCollector;
        this.sessionMonitor = sessionMonitor;
        this.recommendationHistory = recommendationHistory;
        this.calibration = calibration;
    }

    void refresh(AeProjectSnapshot projectSnapshot,
                 String configurationName,
                 int subscribedChannels,
                 double sampleRateHz,
                 long detectionArmedNano,
                 int acceptedEvents,
                 int tpsAeFuelProvedEvents,
                 int rejectedEvents,
                 long eventRevision,
                 EnumMap<ChannelRole, String> channelNames,
                 EnumMap<ChannelRole, Double> latestValues,
                 List<TransientEvent> events) {
        refreshOverview(projectSnapshot, configurationName, subscribedChannels,
                sampleRateHz, detectionArmedNano, eventRevision,
                channelNames, latestValues, events);

        String eventCountText;
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            int predictionEvents = countPredictionEvents(events);
            eventCountText = "Events: " + predictionEvents + " MAP Predict / "
                    + (acceptedEvents - predictionEvents)
                    + " other diagnostic / " + rejectedEvents + " rejected";
        } else {
            eventCountText = "Events: " + tpsAeFuelProvedEvents
                    + " TPS AE fuel proved / "
                    + (acceptedEvents - tpsAeFuelProvedEvents)
                    + " diagnostic / " + rejectedEvents + " rejected";
        }

        uiPresenter.refreshTechnicalStatus(sampleRateHz, eventCountText,
                fuelPathStatus(projectSnapshot != null
                        && projectSnapshot.isMapPredictWorkflow(), latestValues),
                sessionModeText(projectSnapshot, channelNames),
                sessionGuidanceText(projectSnapshot, events),
                mapEstimateCollector.statusText(minimumSamples()));
    }

    private void refreshOverview(AeProjectSnapshot projectSnapshot,
                                 String configurationName,
                                 int subscribedChannels,
                                 double sampleRateHz,
                                 long detectionArmedNano,
                                 long eventRevision,
                                 EnumMap<ChannelRole, String> channelNames,
                                 EnumMap<ChannelRole, Double> latestValues,
                                 List<TransientEvent> events) {
        uiPresenter.refreshCalibration(calibration.isRunning(),
                calibration.secondsRemaining(), detectionArmedNano,
                System.nanoTime(), calibration.getLastResult());
        uiPresenter.refreshOverviewHeader(
                configurationName, subscribedChannels, sampleRateHz);

        if (projectSnapshot == null) {
            workflowCard.setValue("Read project data", CardState.WAITING);
            tpsCycleCard.setValue("Unknown", CardState.WAITING);
            mapPredictCard.setValue("Unknown", CardState.WAITING);
            wallWettingCard.setValue("Unknown", CardState.WAITING);
            instantFuelCard.setValue("Unknown", CardState.WAITING);
            detectorCard.setValue("Unknown", CardState.WAITING);
            predictionLiveCard.setValue("Waiting for project data", CardState.WAITING);
            mapValuesCard.setValue("MAP data not ready", CardState.WAITING);
            transientFuelCard.setValue("Fuel paths not ready", CardState.WAITING);
            eventProgressCard.setValue("No session data", CardState.WAITING);
            mapCoverageCard.setValue("No table axes", CardState.WAITING);
            contributionReviewCard.setValue("No transient data", CardState.WAITING);
            lowRpmReviewCard.setValue("No low-RPM data", CardState.WAITING);
            fullLoadSafetyCard.setValue("No full-load data", CardState.WAITING);
            nextActionCard.setValue("Press Read AE project data", CardState.INFO);
            return;
        }

        boolean mapMode = projectSnapshot.isMapPredictWorkflow();
        workflowCard.setValue(OverviewTextRenderer.stage(projectSnapshot), CardState.GOOD);

        if (mapMode) {
            tpsCycleCard.setValue(projectSnapshot.isTpsAeEnabled()
                            ? "ON — unexpected" : "OFF — correct",
                    projectSnapshot.isTpsAeEnabled()
                            ? CardState.WARNING : CardState.OFF);
        } else {
            tpsCycleCard.setValue(projectSnapshot.isTpsAeEnabled() ? "ON" : "OFF",
                    projectSnapshot.isTpsAeEnabled()
                            ? CardState.GOOD : CardState.OFF);
        }
        mapPredictCard.setValue(projectSnapshot.isMapEstimateEnabled() ? "ON" : "OFF",
                projectSnapshot.isMapEstimateEnabled()
                        ? CardState.GOOD : CardState.OFF);
        wallWettingCard.setValue(projectSnapshot.isWallWettingEnabled()
                        ? "ON — " + projectSnapshot.getWallWettingModel()
                        : "OFF — later stage",
                projectSnapshot.isWallWettingEnabled()
                        ? CardState.GOOD : CardState.OFF);
        instantFuelCard.setValue(projectSnapshot.isExtraShotEnabled()
                        ? "ON" : "OFF — later stage",
                projectSnapshot.isExtraShotEnabled()
                        ? CardState.WARNING : CardState.OFF);
        if (projectSnapshot.isDynamicThresholdEnabled()) {
            detectorCard.setValue(projectSnapshot.isDynamicThresholdAverageStatic()
                            ? "Dynamic + static average" : "Dynamic threshold",
                    CardState.GOOD);
        } else {
            detectorCard.setValue("Static threshold only", CardState.INFO);
        }

        boolean activeChannel = channelNames.containsKey(ChannelRole.MAP_PRED_ACTIVE);
        boolean fallbackChannel = channelNames.containsKey(ChannelRole.FALLBACK_MAP);
        boolean effectiveChannel = channelNames.containsKey(ChannelRole.EFFECTIVE_MAP);
        boolean resetChannel = channelNames.containsKey(ChannelRole.MAP_PRED_RESET_CNT);
        boolean predictionActive = valueOn(value(latestValues, ChannelRole.MAP_PRED_ACTIVE));
        if (!activeChannel || !fallbackChannel || !effectiveChannel || !resetChannel) {
            predictionLiveCard.setValue("Missing prediction channel(s)", CardState.ERROR);
        } else {
            predictionLiveCard.setValue(predictionActive ? "ACTIVE" : "Idle",
                    predictionActive ? CardState.ACTIVE : CardState.OFF);
        }

        double realMap = value(latestValues, ChannelRole.MAP);
        double fallback = value(latestValues, ChannelRole.FALLBACK_MAP);
        double effective = value(latestValues, ChannelRole.EFFECTIVE_MAP);
        mapValuesCard.setValue(
                OverviewTextRenderer.mapValues(realMap, fallback, effective),
                predictionActive ? CardState.ACTIVE : CardState.INFO);

        double wallPw = value(latestValues, ChannelRole.WALL_WETTING_PW);
        double instantPw = value(latestValues, ChannelRole.INSTANT_PULSE_PW);
        transientFuelCard.setValue(
                OverviewTextRenderer.transientFuel(wallPw, instantPw),
                absGreater(wallPw, 0.0001) || absGreater(instantPw, 0.0001)
                        ? CardState.ACTIVE : CardState.OFF);

        int predictionEvents = countPredictionEvents(events);
        int repeatedResets = countRepeatedResetEvents(events);
        eventProgressCard.setValue(
                OverviewTextRenderer.eventProgress(predictionEvents, repeatedResets),
                predictionEvents > 0
                        ? (repeatedResets > 0 ? CardState.WARNING : CardState.GOOD)
                        : CardState.WAITING);

        int minimum = minimumSamples();
        int covered = mapEstimateCollector.getCoveredCells(minimum);
        int total = projectSnapshot.getMapEstimateRpmBins().length
                * projectSnapshot.getMapEstimateTpsBins().length;
        mapCoverageCard.setValue(OverviewTextRenderer.mapCoverage(
                        mapEstimateCollector.getAcceptedSamples(), covered, total),
                covered > 0 ? CardState.GOOD : CardState.WAITING);

        SessionMonitor.Snapshot reviewSnapshot = sessionMonitor.snapshot();
        SessionReview review;
        if (cachedEventReview == null || cachedEventReviewRevision != eventRevision) {
            cachedEventReview = SessionReview.build(events, reviewSnapshot);
            cachedEventReviewRevision = eventRevision;
            review = cachedEventReview;
        } else {
            review = cachedEventReview.withFullLoad(reviewSnapshot);
        }
        contributionReviewCard.setValue(review.contributionCardText(),
                predictionEvents > 0 ? CardState.INFO : CardState.WAITING);
        lowRpmReviewCard.setValue(review.lowRpmCardText(),
                review.lowRpmNeedsReview() ? CardState.WARNING
                        : (predictionEvents > 0 ? CardState.GOOD : CardState.WAITING));
        fullLoadSafetyCard.setValue(review.fullLoadCardText(),
                review.fullLoadNeedsReview() ? CardState.WARNING
                        : (reviewSnapshot.hasData() ? CardState.GOOD : CardState.WAITING));
        uiPresenter.refreshSessionReview(review.toDisplayText());

        String action;
        CardState actionState;
        if (!mapMode) {
            action = "Use the TPS cycle-AE workflow";
            actionState = CardState.INFO;
        } else if (!activeChannel || !fallbackChannel || !effectiveChannel || !resetChannel) {
            action = "Resolve missing MAP Predict channels";
            actionState = CardState.ERROR;
        } else if (review.triggerSyncNeedsReview()) {
            action = "Review running trigger/sync loss before more transient testing";
            actionState = CardState.WARNING;
        } else if (review.sessionFaultNeedsReview()) {
            action = "Review running fault/cut activity";
            actionState = CardState.WARNING;
        } else if (review.fullLoadNeedsReview()) {
            action = "Review full-load safety before more WOT testing";
            actionState = CardState.WARNING;
        } else if (review.lowRpmNeedsReview()) {
            action = "Review MAP Estimate / blend below 2200 RPM";
            actionState = CardState.WARNING;
        } else if (predictionEvents < 4) {
            action = "Collect deliberate loaded tip-ins";
            actionState = CardState.INFO;
        } else if (covered < 4) {
            action = "Add stable RPM/TPS/MAP coverage";
            actionState = CardState.INFO;
        } else {
            action = "Review MAP Estimate and Blend Duration drafts";
            actionState = CardState.GOOD;
        }
        if (recommendationHistory.observe(action, review, events, reviewSnapshot,
                new EnumMap<ChannelRole, String>(channelNames),
                System.currentTimeMillis())) {
            uiPresenter.refreshRecommendationHistory(
                    recommendationHistory.toDisplayText());
        }
        String historyBadge = recommendationHistory.latestBadgeText();
        nextActionCard.setValue(historyBadge.length() > 0
                ? action + "\n" + historyBadge : action, actionState);
    }

    private String sessionModeText(AeProjectSnapshot projectSnapshot,
                                   EnumMap<ChannelRole, String> channelNames) {
        Set<ChannelRole> resolvedRoles =
                new HashSet<ChannelRole>(channelNames.keySet());
        return TechnicalDetailsRenderer.sessionMode(projectSnapshot, resolvedRoles);
    }

    private String sessionGuidanceText(AeProjectSnapshot projectSnapshot,
                                       List<TransientEvent> events) {
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            int predictionEvents = countPredictionEvents(events);
            int repeatedResetEvents = 0;
            int wallActiveEvents = 0;
            int resetDiscontinuities = 0;
            for (TransientEvent event : events) {
                CounterMath.Result resets = event.getPredictionResetMetrics();
                if (resets.hasRepeatedResets()) repeatedResetEvents++;
                if (resets.hasDiscontinuity()) resetDiscontinuities++;
                if (event.hasWallWettingContribution()) wallActiveEvents++;
            }
            SessionReview review = SessionReview.build(
                    events, sessionMonitor.snapshot());
            String nextStep;
            if (review.triggerSyncNeedsReview()) {
                nextStep = "Running trigger/sync loss review has priority before further transient testing.";
            } else if (review.sessionFaultNeedsReview()) {
                nextStep = "Running fault/cut review has priority before further transient testing.";
            } else if (review.fullLoadNeedsReview()) {
                nextStep = "Full-load safety review has priority before further WOT testing.";
            } else if (review.lowRpmNeedsReview()) {
                nextStep = "Low-RPM events indicate the exercised MAP Estimate cells and Blend Duration below 2200 RPM need review.";
            } else if (predictionEvents == 0) {
                nextStep = "Collect deliberate loaded tip-ins and confirm fallbackMap/effectiveMap/isMapPredictionActive are subscribed.";
            } else if (repeatedResetEvents > 0) {
                nextStep = "Repeated resets deserve review for drift-style pedal stabs: keep MAP Estimate conservative and avoid an unnecessarily long Predictive Map Blend Duration.";
            } else {
                nextStep = "Use Copy MAP Estimate draft for table coverage and Copy Blend Duration draft after several prediction events across the RPM range.";
            }
            return TechnicalDetailsRenderer.mapPredictGuidance(
                    predictionEvents, repeatedResetEvents, resetDiscontinuities,
                    wallActiveEvents, mapEstimateCollector.statusText(minimumSamples()),
                    nextStep);
        }

        int proved = 0;
        int nearMiss = 0;
        for (TransientEvent summary : events) {
            if (summary.isTpsAeFuelProved()) proved++;
            else if (summary.isTriggerNearMiss()) nearMiss++;
        }
        return TechnicalDetailsRenderer.tpsCycleGuidance(
                events.size(), proved, nearMiss);
    }

    private int minimumSamples() {
        return ((Number) mapMinimumSamples.getValue()).intValue();
    }

    static String fuelPathStatus(boolean mapPredictWorkflow,
                                 EnumMap<ChannelRole, Double> values) {
        return TechnicalDetailsRenderer.fuelPathStatus(mapPredictWorkflow, values);
    }

    private static int countPredictionEvents(List<TransientEvent> events) {
        int count = 0;
        for (TransientEvent event : events) {
            if (event.hasMapPrediction()) count++;
        }
        return count;
    }

    private static int countRepeatedResetEvents(List<TransientEvent> events) {
        int count = 0;
        for (TransientEvent event : events) {
            if (event.getPredictionResetMetrics().hasRepeatedResets()) count++;
        }
        return count;
    }

    private static boolean valueOn(double value) {
        return Double.isFinite(value) && value >= 0.5;
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
    }

    private static double value(EnumMap<ChannelRole, Double> values,
                                ChannelRole role) {
        Double value = values.get(role);
        return value == null ? Double.NaN : value.doubleValue();
    }
}
