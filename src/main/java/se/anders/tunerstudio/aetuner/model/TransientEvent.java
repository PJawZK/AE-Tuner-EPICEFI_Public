package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable passive transient event model backed by analyzed result objects. */
public class TransientEvent {
    private final int index;
    private final boolean accepted;
    private final String eventClass;
    private final String reason;
    private final List<LiveSample> samples;
    private final boolean mapPredictWorkflow;
    private final TransientEventAnalyzer.Result analysis;
    private final TransientEventAssessment assessment;

    public TransientEvent(int index,
                   boolean accepted,
                   String eventClass,
                   String reason,
                   List<LiveSample> samples) {
        this(index, accepted, eventClass, reason, samples, false);
    }

    public TransientEvent(int index,
                   boolean accepted,
                   String eventClass,
                   String reason,
                   List<LiveSample> samples,
                   boolean mapPredictWorkflow) {
        this.index = index;
        this.accepted = accepted;
        this.eventClass = eventClass == null ? "Unclassified" : eventClass;
        this.reason = reason == null ? "" : reason;
        this.mapPredictWorkflow = mapPredictWorkflow;
        this.samples = Collections.unmodifiableList(new ArrayList<LiveSample>(samples));
        this.analysis = TransientEventAnalyzer.analyze(this.samples);
        this.assessment = TransientEventAssessment.build(mapPredictWorkflow, analysis);
    }

    public int getIndex() { return index; }
    public boolean isAccepted() { return accepted; }
    public boolean isTpsAeFuelProved() { return analysis.tpsAeFuelProved; }
    public String getEventClass() { return eventClass; }
    String getReason() { return reason; }
    public List<LiveSample> getSamples() { return samples; }

    public boolean hasMapPrediction() { return analysis.predictionMetrics.predictionSeen; }
    public boolean hasWallWettingContribution() { return analysis.predictionMetrics.wallSeen; }
    public boolean hasInstantFuelContribution() { return analysis.predictionMetrics.instantSeen; }
    public double getMaxEffectiveMapGap() { return analysis.predictionMetrics.maxEffectiveGap; }
    double getMaxFallbackMapGap() { return analysis.predictionMetrics.maxFallbackGap; }
    double getPredictionActiveSeconds() { return analysis.predictionMetrics.activeSeconds; }
    public CounterMath.Result getPredictionResetMetrics() { return analysis.predictionResetMetrics; }
    public int getPredictionTriggerBurstCount() { return analysis.predictionTriggerBurstCount; }
    public double getMedianPredictionRpm() { return analysis.predictionMetrics.medianPredictionRpm; }

    double getMaxTriggerRatio() { return analysis.maxTriggerRatio; }
    public boolean isTriggerNearMiss() { return analysis.triggerNearMiss; }
    boolean isTinyTriggerCandidate() { return analysis.tinyTriggerCandidate; }
    public String getAeFuelTableGuidance() { return assessment.aeFuelTableGuidance; }
    String getTransientMixClass() { return assessment.transientMixClass; }
    double getWallWettingToTpsAeRatio() { return assessment.wallWettingToTpsAeRatio; }
    boolean isWallCorrectionAvailable() { return analysis.wallCorrectionAvailable; }
    public boolean isWallWettingPwAvailable() { return analysis.wallWettingPwAvailable; }
    boolean isDfcoSeen() { return analysis.dfcoSeen; }
    public double multiplierSuggestionWeight(AeProjectSnapshot snapshot) {
        return assessment.multiplierSuggestionWeight(snapshot);
    }

    public double getTpsRise() { return analysis.tpsRise; }
    double getMapRise() { return analysis.mapRise; }
    public double getMaxTps() { return analysis.maxTps; }
    public double getMaxTpsAeTo() { return analysis.maxTpsAeTo; }
    public double getMaxLeanLambdaError() { return analysis.maxLeanLambdaError; }
    public double getMaxRichLambdaError() { return analysis.maxRichLambdaError; }
    double getEarlyLeanLambdaError() { return analysis.earlyLeanLambdaError; }
    double getLateRichLambdaError() { return analysis.lateRichLambdaError; }

    public String toDisplayText() {
        return TransientEventFormatter.displayText(index, accepted, eventClass, reason,
                samples, mapPredictWorkflow, analysis, assessment);
    }

    public String toCsvHeader() {
        return TransientEventFormatter.csvHeader();
    }

    public List<String> toCsvRows() {
        return TransientEventFormatter.csvRows(index, accepted, eventClass, reason,
                samples, mapPredictWorkflow, analysis, assessment);
    }
}
