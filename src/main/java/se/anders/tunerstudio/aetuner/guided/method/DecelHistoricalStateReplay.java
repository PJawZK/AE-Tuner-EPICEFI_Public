package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.List;
import java.util.Locale;

/**
 * Read-only audit of the JZ EpicEFI Decel state machine.
 *
 * Instantaneous edge replay is independent: detector signal + threshold. Hold
 * state cannot be independently reconstructed from row timestamps because the
 * firmware advances tpsDecelCnt in engine-cycle space. When that logged counter
 * is present we perform a state-consistency check, not an independent replay.
 */
public final class DecelHistoricalStateReplay {
    public static final class Result {
        public final String reportText;
        public final int usableSamples;
        public final int historicalEdgeSamples;
        public final int accelCancelledEdgeSamples;
        public final int holdOnlyExpectedActiveSamples;
        public final int observedActiveSamples;
        public final int expectedActiveSamples;
        public final int activeStateMismatches;
        public final int fuelEnleanmentSamples;
        /** Kept for compatibility: independent full-state replay is never claimed. */
        public final boolean exactStateReplay;
        public final boolean counterConsistencyAvailable;
        public final String historicalThresholdSource;
        public final String historicalHoldSource;
        public final int currentCounterfactualEdgeSamples;

        Result(String reportText, int usableSamples, int historicalEdgeSamples,
               int accelCancelledEdgeSamples, int holdOnlyExpectedActiveSamples,
               int observedActiveSamples, int expectedActiveSamples,
               int activeStateMismatches, int fuelEnleanmentSamples,
               boolean exactStateReplay, boolean counterConsistencyAvailable,
               String historicalThresholdSource, String historicalHoldSource,
               int currentCounterfactualEdgeSamples) {
            this.reportText = reportText == null ? "" : reportText;
            this.usableSamples = usableSamples;
            this.historicalEdgeSamples = historicalEdgeSamples;
            this.accelCancelledEdgeSamples = accelCancelledEdgeSamples;
            this.holdOnlyExpectedActiveSamples = holdOnlyExpectedActiveSamples;
            this.observedActiveSamples = observedActiveSamples;
            this.expectedActiveSamples = expectedActiveSamples;
            this.activeStateMismatches = activeStateMismatches;
            this.fuelEnleanmentSamples = fuelEnleanmentSamples;
            this.exactStateReplay = exactStateReplay;
            this.counterConsistencyAvailable = counterConsistencyAvailable;
            this.historicalThresholdSource = historicalThresholdSource == null ? "" : historicalThresholdSource;
            this.historicalHoldSource = historicalHoldSource == null ? "" : historicalHoldSource;
            this.currentCounterfactualEdgeSamples = currentCounterfactualEdgeSamples;
        }
    }

    private DecelHistoricalStateReplay() { }

    public static Result replay(List<LiveSample> samples, AeProjectSnapshot current,
                                MlgEmbeddedTuneMetadata embedded) {
        if (samples == null || samples.isEmpty()) {
            return unavailable("No imported Decel samples are available for state audit.");
        }

        boolean hasLoggedThreshold = anyFinite(samples, ChannelRole.DECEL_THRESHOLD);
        boolean hasAccelState = anyFinite(samples, ChannelRole.AE_ABOVE_THRESHOLD);
        boolean hasDecelState = anyFinite(samples, ChannelRole.TPS_DECEL_ACTIVE);
        boolean hasCycleCounter = anyFinite(samples, ChannelRole.TPS_DECEL_CYCLE_CNT);
        boolean embeddedCurve = embedded != null && embedded.hasDecelThresholdCurve();
        boolean embeddedHold = embedded != null && embedded.hasHoldCycles();
        String historicalDetectorMode =
                DecelHistoricalReplayService.historicalDetectorMode(embedded, current);

        String thresholdSource = hasLoggedThreshold
                ? "logged Fuel: TPS decel threshold (as-recorded firmware interpolation)"
                : embeddedCurve
                ? "embedded historical tpsDecelThresholdValue curve"
                : "unavailable for as-recorded edge replay";
        String holdSource = embeddedHold
                ? "embedded historical tpsDecelHoldCycles=" + fmt(embedded.decelHoldCycles)
                : "unavailable for logged-counter Hold consistency check";

        boolean counterConsistency = (hasLoggedThreshold || embeddedCurve)
                && embeddedHold && hasAccelState && hasDecelState && hasCycleCounter;

        int usable = 0;
        int historicalEdges = 0;
        int cancelledEdges = 0;
        int holdOnly = 0;
        int observedActive = 0;
        int expectedActive = 0;
        int mismatches = 0;
        int fuelEnleanment = 0;
        int currentEdges = 0;
        int historicalThresholdRows = 0;
        int currentThresholdRows = 0;

        for (LiveSample sample : samples) {
            GuidedAnalysisCancellation.check();
            if (sample == null) continue;
            double historicalDecel = DecelSignalSamples.value(sample, historicalDetectorMode);
            double rpm = sample.get(ChannelRole.RPM);
            if (!Double.isFinite(historicalDecel) || !Double.isFinite(rpm)) continue;
            usable++;

            // Current-tune counterfactual may select a different detector mode.
            double currentDecel = DecelSignalSamples.value(sample, current);
            double currentThreshold = current == null ? Double.NaN : current.decelThresholdForRpm(rpm);
            if (Double.isFinite(currentDecel) && Double.isFinite(currentThreshold)) {
                currentThresholdRows++;
                if (currentThreshold > 0.0 && currentDecel < -currentThreshold) currentEdges++;
            }

            double historicalThreshold = sample.get(ChannelRole.DECEL_THRESHOLD);
            if (!Double.isFinite(historicalThreshold) && embeddedCurve) {
                historicalThreshold = embedded.decelThresholdForRpm(rpm);
            }
            if (!Double.isFinite(historicalThreshold)) continue;
            historicalThresholdRows++;
            boolean armed = historicalThreshold > 0.0;
            boolean edge = armed && historicalDecel < -historicalThreshold;
            if (edge) historicalEdges++;

            double accelRaw = sample.get(ChannelRole.AE_ABOVE_THRESHOLD);
            boolean accelKnown = Double.isFinite(accelRaw);
            boolean accelActive = accelKnown && accelRaw >= 0.5;
            if (edge && accelActive) cancelledEdges++;

            double fuelMult = sample.get(ChannelRole.DECEL_FUEL_MULT);
            if (Double.isFinite(fuelMult) && Math.abs(fuelMult - 1.0) > 0.0005) fuelEnleanment++;

            double observedRaw = sample.get(ChannelRole.TPS_DECEL_ACTIVE);
            if (Double.isFinite(observedRaw) && observedRaw >= 0.5) observedActive++;

            if (!counterConsistency) continue;
            double cycle = sample.get(ChannelRole.TPS_DECEL_CYCLE_CNT);
            if (!Double.isFinite(cycle) || !Double.isFinite(observedRaw) || !accelKnown) continue;
            boolean counterConsistent = !accelActive
                    && (edge || (armed && cycle < embedded.decelHoldCycles));
            if (counterConsistent) expectedActive++;
            if (counterConsistent && !edge) holdOnly++;
            boolean observed = observedRaw >= 0.5;
            if (counterConsistent != observed) mismatches++;
        }

        StringBuilder report = new StringBuilder();
        report.append("INSTANTANEOUS EDGE / STATE-OUTPUT AUDIT\n")
                .append("Historical Decel signal: ")
                .append(DecelSignalSamples.sourceLabel(samples, historicalDetectorMode)).append('\n')
                .append("As-recorded threshold source: ").append(thresholdSource).append('\n')
                .append("As-recorded Hold source: ").append(holdSource).append('\n')
                .append("Usable Decel-signal samples: ").append(usable).append('\n')
                .append("Historical threshold rows: ").append(historicalThresholdRows).append('\n')
                .append("Independently replayed as-recorded instantaneous edge samples: ")
                .append(historicalEdges).append('\n')
                .append("Edge samples cancelled by simultaneous Accel Active: ").append(cancelledEdges).append('\n');
        if (counterConsistency) {
            report.append("State check: LOGGED-COUNTER CONSISTENCY INPUTS AVAILABLE — NOT AN INDEPENDENT HOLD REPLAY\n")
                    .append("Counter/rule-consistent Decel Active samples: ").append(expectedActive).append('\n')
                    .append("  of which Hold-only under logged counter (no instantaneous edge): ").append(holdOnly).append('\n')
                    .append("Observed Fuel: TPS Decel Active samples: ").append(observedActive).append('\n')
                    .append("Counter/rule vs observed active-state mismatches: ").append(mismatches).append('\n')
                    .append("The logged Engine cycles decel duration counter is firmware state input; it validates Hold/state consistency but does not reconstruct the preceding engine cycles independently.\n");
            if (historicalEdges == 0 && holdOnly > 0) {
                report.append("WARNING: Hold-active rows exist without any independently replayed edge in the imported interval. Treat this as detector/state inconsistency or unresolved carry-in, not proof of a valid Hold replay.\n");
            }
        } else {
            report.append("State check: EDGE-ONLY / WITHHELD. Logged-counter consistency requires as-recorded threshold + embedded Hold + Fuel: TPS AE Active + Engine cycles decel duration + Fuel: TPS Decel Active.\n")
                    .append("Observed Fuel: TPS Decel Active samples (context only): ").append(observedActive).append('\n');
        }
        report.append("Fuel multiplier != 1.000 samples: ").append(fuelEnleanment)
                .append(" (diagnostic; threshold analysis does not tune the enleanment table).\n")
                .append("\nCURRENT WORKING TUNE COUNTERFACTUAL\n")
                .append("Rows with current detector+threshold available: ").append(currentThresholdRows).append('\n')
                .append("Instantaneous edge samples under current Working Tune detector/threshold: ")
                .append(currentEdges).append('\n')
                .append("This is threshold-edge counterfactual evidence only; historical Hold/state is not rewritten with current settings.\n");

        return new Result(report.toString(), usable, historicalEdges, cancelledEdges,
                holdOnly, observedActive, expectedActive, mismatches, fuelEnleanment,
                false, counterConsistency, thresholdSource, holdSource, currentEdges);
    }

    private static boolean anyFinite(List<LiveSample> samples, ChannelRole role) {
        if (samples == null) return false;
        for (LiveSample sample : samples) {
            GuidedAnalysisCancellation.check();
            if (sample != null && Double.isFinite(sample.get(role))) return true;
        }
        return false;
    }

    private static Result unavailable(String reason) {
        return new Result("INSTANTANEOUS EDGE / STATE-OUTPUT AUDIT\n"
                + reason + "\nNo state conclusion was manufactured.",
                0, 0, 0, 0, 0, 0, 0, 0, false, false,
                "unavailable", "unavailable", 0);
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
