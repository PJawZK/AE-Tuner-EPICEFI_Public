package se.anders.tunerstudio.aetuner.guided.method;

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
 * Hardened Basic Wall Wetting recommendation.
 *
 * Beta remains the immediate deposited-film/amplitude authority and is evaluated
 * from method-owned delayed-response events. Tau remains visible and directly
 * review/apply-capable, but automatic Tau movement is withheld because wall
 * correction is an immediate ECU quantity while measured lambda is delayed by
 * combustion/exhaust/sensor transport. A raw persistence diagnostic is retained
 * for vehicle correlation, but it is not write authority until that delay is
 * measured/aligned.
 */
final class WallWettingRecommendation {
    private static final int MIN_EVENTS = 3;
    private static final double LAMBDA_ERROR_DEADBAND = 0.025;
    private static final double MIN_ACTIVE = 0.01;
    private static final double MAX_BETA = 1.0;
    private static final double MIN_BETA_STEP = 0.01;
    private static final double MAX_BETA_STEP = 0.05;

    static final class Result {
        final String reviewText;
        final ProposalWritePlan plan;
        final int candidateEvents;
        final int acceptedEvents;
        final int rejectedFuelCut;
        final int rejectedInstantOverlap;
        final int rejectedIncomplete;
        final int tpsAeOverlapEvents;
        final int mapPredictOverlapEvents;
        final double medianLambdaError;
        final double medianPersistenceDelta;

        Result(String reviewText, ProposalWritePlan plan,
               int candidateEvents, int acceptedEvents,
               int rejectedFuelCut, int rejectedInstantOverlap,
               int rejectedIncomplete, int tpsAeOverlapEvents,
               int mapPredictOverlapEvents, double medianLambdaError,
               double medianPersistenceDelta) {
            this.reviewText = nn(reviewText);
            this.plan = plan;
            this.candidateEvents = candidateEvents;
            this.acceptedEvents = acceptedEvents;
            this.rejectedFuelCut = rejectedFuelCut;
            this.rejectedInstantOverlap = rejectedInstantOverlap;
            this.rejectedIncomplete = rejectedIncomplete;
            this.tpsAeOverlapEvents = tpsAeOverlapEvents;
            this.mapPredictOverlapEvents = mapPredictOverlapEvents;
            this.medianLambdaError = medianLambdaError;
            this.medianPersistenceDelta = medianPersistenceDelta;
        }
    }

    private WallWettingRecommendation() { }

    static Result evaluate(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        if (snapshot == null) {
            return unavailable("Read Working Tune before Wall Wetting review.");
        }
        if (!snapshot.isWallWettingEnabled()) {
            return unavailable("Wall Wetting is disabled in the Working Tune. Automatic enabling is withheld; the full validated task settings remain explicitly reviewable.");
        }
        if (!basicModel(snapshot.getWallWettingModel())) {
            return unavailable("Working Tune uses the Advanced wall model. Use Advanced Tau/Beta Mapping for CLT/RPM/MAP surfaces; Basic scalar Tau/Beta are not the active firmware authority.");
        }
        if (!snapshot.hasWallTauBeta()) {
            return unavailable("Exact Working Tune wwaeTau / wwaeBeta baseline is unavailable. No stale-checkable proposal can be created.");
        }
        double tau = snapshot.getWallTau();
        double beta = snapshot.getWallBeta();
        if (tau < MIN_ACTIVE || beta < MIN_ACTIVE) {
            return unavailable("Basic Wall Wetting currently has Tau or Beta below 0.01. Firmware treats that as disabled; automatic enabling is withheld while explicit Task Settings remain available.");
        }
        if (evidence == null || evidence.isEmpty()) {
            return unavailable("No retained Wall Wetting evidence is available.");
        }

        List<TransientEvidenceEvent.Event> candidates = TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.WALL, evidence);
        List<Double> amplitudeErrors = new ArrayList<Double>();
        int fuelCut = 0;
        int instant = 0;
        int incomplete = 0;
        int tpsOverlap = 0;
        int mapOverlap = 0;
        int lean = 0;
        int rich = 0;

        for (TransientEvidenceEvent.Event event : candidates) {
            if (event.fuelCut) {
                fuelCut++;
                continue;
            }
            if (event.instantOverlap) {
                instant++;
                continue;
            }
            if (!event.complete()) {
                incomplete++;
                continue;
            }
            amplitudeErrors.add(event.earlyError);
            if (event.tpsAeOverlap) tpsOverlap++;
            if (event.mapPredictOverlap) mapOverlap++;
            if (event.earlyError > LAMBDA_ERROR_DEADBAND) lean++;
            if (event.earlyError < -LAMBDA_ERROR_DEADBAND) rich++;
        }

        double medianError = percentile(amplitudeErrors, 0.50);
        double rawPersistence = rawPersistenceDiagnostic(evidence);
        boolean enough = amplitudeErrors.size() >= MIN_EVENTS;
        int signNeed = enough
                ? (int)Math.ceil(amplitudeErrors.size() * 2.0 / 3.0)
                : Integer.MAX_VALUE;
        boolean leanRepeatable = lean >= signNeed;
        boolean richRepeatable = rich >= signNeed;

        List<ProposalWritePlan.Change> changes =
                new ArrayList<ProposalWritePlan.Change>();
        String betaDecision;
        if (!enough) {
            betaDecision = "WITHHOLD Beta — need at least " + MIN_EVENTS
                    + " clean method-owned tip-in events after rejection gates.";
        } else if (!leanRepeatable && !richRepeatable) {
            betaDecision = "WITHHOLD Beta — accepted tip-ins do not show a repeatable lean or rich direction.";
        } else if (!Double.isFinite(medianError)
                || Math.abs(medianError) <= LAMBDA_ERROR_DEADBAND) {
            betaDecision = "KEEP Beta — delayed transient lambda error is inside the baseline deadband.";
        } else {
            double step = Math.max(MIN_BETA_STEP,
                    Math.min(MAX_BETA_STEP, Math.abs(beta) * 0.10));
            double proposed = medianError > 0.0 ? beta + step : beta - step;
            proposed = round4(Math.max(MIN_ACTIVE, Math.min(MAX_BETA, proposed)));
            if (Math.abs(proposed - beta) < 0.00005) {
                betaDecision = "KEEP Beta — bounded movement resolves to the current value.";
            } else {
                changes.add(ProposalWritePlan.Change.scalar(
                        AeParameterNames.WALL_BETA, beta, proposed,
                        "Wall Wetting Beta", "fraction"));
                betaDecision = "PROPOSE Beta " + fmt(beta) + " -> " + fmt(proposed)
                        + (medianError > 0.0
                        ? " for repeatable lean method-owned tip-in amplitude."
                        : " for repeatable rich method-owned tip-in amplitude.");
            }
        }

        String tauDecision = "WITHHOLD Tau automatic movement — wall correction is immediate but measured lambda is transport-delayed. Tau remains fully review/apply-capable through Task Settings until an RPM/load-specific measured lambda-delay alignment is available.";

        ProposalWritePlan plan = changes.isEmpty() ? null : new ProposalWritePlan(
                "wall-wetting-basic-beta",
                "Wall Wetting — Basic Beta",
                snapshot.getConfigurationName(),
                "Event-owned Basic wall amplitude proposal. Tau persistence is evidence-visible but excluded from automatic writes until lambda transport timing is aligned. Exact stale check/readback and Restore remain mandatory. No Burn.",
                changes);

        StringBuilder review = new StringBuilder();
        review.append("WALL WETTING — HARDENED BASIC REVIEW\n")
                .append("Firmware attribution: Beta = deposited fraction/amplitude; Tau = persistence via alpha.\n")
                .append("Working Tune: Tau ").append(fmt(tau))
                .append(" s | Beta ").append(fmt(beta)).append(".\n")
                .append("Tip-in candidates: ").append(candidates.size())
                .append(" | accepted amplitude: ").append(amplitudeErrors.size())
                .append(" | fuel-cut rejected: ").append(fuelCut)
                .append(" | Instant overlap rejected: ").append(instant)
                .append(" | incomplete response: ").append(incomplete).append(".\n")
                .append("Accepted-event context: TPS AE overlap ").append(tpsOverlap)
                .append(" | MAP Predict overlap ").append(mapOverlap).append(".\n")
                .append("Amplitude: lean ").append(lean)
                .append(" | rich ").append(rich)
                .append(" | median delayed lambda-target ")
                .append(fmtSigned(medianError)).append(".\n")
                .append("Raw wall-decay minus lambda-recovery diagnostic: ")
                .append(fmtSigned(rawPersistence))
                .append(" s (diagnostic only; NOT Tau write authority).\n")
                .append("Beta decision: ").append(betaDecision).append('\n')
                .append("Tau decision: ").append(tauDecision).append('\n')
                .append("Boundary: Advanced CLT/RPM/MAP shaping belongs to Advanced Tau/Beta Mapping; near-zero Basic settings are never auto-enabled; capture never writes; no Burn.");
        return new Result(review.toString(), plan, candidates.size(),
                amplitudeErrors.size(), fuelCut, instant, incomplete,
                tpsOverlap, mapOverlap, medianError, rawPersistence);
    }

    /**
     * Keep the old timing relationship only as a visible diagnostic. It is not
     * permitted to create a Tau ProposalWritePlan because the two clocks have
     * different physical transport delays.
     */
    private static double rawPersistenceDiagnostic(List<LiveSample> samples) {
        List<Double> deltas = new ArrayList<Double>();
        double eventStart = Double.NaN;
        double peakWall = 0.0;
        double wallDecay = Double.NaN;
        double lambdaRecovery = Double.NaN;
        int lambdaStable = 0;

        for (LiveSample sample : samples) {
            if (sample == null || !Double.isFinite(sample.getSeconds())) continue;
            boolean opening = sample.getTpsDot() > 2.0;
            double wall = wallValue(sample);
            if (!Double.isFinite(eventStart) && opening && Math.abs(wall) > 0.000001) {
                eventStart = sample.getSeconds();
                peakWall = Math.abs(wall);
                wallDecay = Double.NaN;
                lambdaRecovery = Double.NaN;
                lambdaStable = 0;
            }
            if (!Double.isFinite(eventStart)) continue;
            double dt = sample.getSeconds() - eventStart;
            if (dt > 2.5) {
                if (Double.isFinite(wallDecay) && Double.isFinite(lambdaRecovery)) {
                    deltas.add(wallDecay - lambdaRecovery);
                }
                eventStart = Double.NaN;
                continue;
            }
            peakWall = Math.max(peakWall, Math.abs(wall));
            if (!Double.isFinite(wallDecay) && peakWall > 0.000001
                    && dt >= 0.20
                    && Math.abs(wall) <= Math.max(0.0005, peakWall * 0.20)) {
                wallDecay = dt;
            }
            double lambda = sample.get(ChannelRole.LAMBDA);
            double target = sample.get(ChannelRole.TARGET_LAMBDA);
            if (Double.isFinite(lambda) && Double.isFinite(target)
                    && Math.abs(lambda - target) <= 0.020) {
                lambdaStable++;
                if (!Double.isFinite(lambdaRecovery) && lambdaStable >= 3 && dt >= 0.20) {
                    lambdaRecovery = dt;
                }
            } else {
                lambdaStable = 0;
            }
            if (Double.isFinite(wallDecay) && Double.isFinite(lambdaRecovery)) {
                deltas.add(wallDecay - lambdaRecovery);
                eventStart = Double.NaN;
            }
        }
        return percentile(deltas, 0.50);
    }

    private static double wallValue(LiveSample sample) {
        double correction = sample.get(ChannelRole.WALL_CORRECTION);
        if (Double.isFinite(correction)) return correction;
        double pw = sample.get(ChannelRole.WALL_WETTING_PW);
        return Double.isFinite(pw) ? pw : 0.0;
    }

    private static boolean basicModel(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.contains("basic") || "0".equals(v) || "false".equals(v);
    }

    private static double percentile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>();
        for (Double value : values) {
            if (value != null && Double.isFinite(value.doubleValue())) copy.add(value);
        }
        if (copy.isEmpty()) return Double.NaN;
        Collections.sort(copy);
        if (copy.size() == 1) return copy.get(0).doubleValue();
        double position = Math.max(0.0, Math.min(1.0, q)) * (copy.size() - 1);
        int lo = (int)Math.floor(position);
        int hi = (int)Math.ceil(position);
        if (lo == hi) return copy.get(lo).doubleValue();
        double f = position - lo;
        return copy.get(lo).doubleValue()
                + f * (copy.get(hi).doubleValue() - copy.get(lo).doubleValue());
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static String fmtSigned(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%+.3f", value) : "n/a";
    }

    private static Result unavailable(String reason) {
        return new Result("WALL WETTING — recommendation withheld\n" + nn(reason)
                + "\nNo ProposalWritePlan. Validated Task Settings remain directly review/apply-capable; no automatic Apply and no Burn.",
                null, 0, 0, 0, 0, 0, 0, 0,
                Double.NaN, Double.NaN);
    }
}
