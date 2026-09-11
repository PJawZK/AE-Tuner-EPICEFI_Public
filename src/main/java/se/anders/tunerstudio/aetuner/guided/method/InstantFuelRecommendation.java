package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Event-owned Instant Fuel multiplier recommendation.
 *
 * Firmware computes pulse width from the previous/base injection PW and the
 * correction multipliers, then scales it by tpsExtraShotMult. Only that final
 * scalar is automatic here. The enable state is never auto-enabled and the
 * timer/condition curves remain separate task authorities.
 *
 * Pulse identity comes from TransientEvidenceEvent: counter edges are
 * authoritative when available and pulse PW is only a rising-edge fallback.
 * This avoids treating a retained non-zero commanded PW as a stream of new
 * Instant events.
 */
final class InstantFuelRecommendation {
    private static final int MIN_EVENTS = 3;
    private static final double LAMBDA_ERROR_DEADBAND = 0.025;
    private static final double SUSTAINED_ERROR_LIMIT = 0.040;
    private static final double MIN_MULT = 0.01;
    private static final double MAX_MULT = 2.50;
    private static final double MIN_STEP = 0.02;
    private static final double MAX_STEP = 0.10;

    static final class Result {
        final String reviewText;
        final ProposalWritePlan plan;
        final int candidateEvents;
        final int acceptedEvents;
        final int sustainedRejected;
        final int fuelCutRejected;
        final int incompleteRejected;
        final int tpsAeOverlapEvents;
        final int wallOverlapEvents;
        final int mapPredictOverlapEvents;
        final double medianEarlyError;

        Result(String reviewText, ProposalWritePlan plan,
               int candidateEvents, int acceptedEvents,
               int sustainedRejected, int fuelCutRejected,
               int incompleteRejected, int tpsAeOverlapEvents,
               int wallOverlapEvents, int mapPredictOverlapEvents,
               double medianEarlyError) {
            this.reviewText = nn(reviewText);
            this.plan = plan;
            this.candidateEvents = candidateEvents;
            this.acceptedEvents = acceptedEvents;
            this.sustainedRejected = sustainedRejected;
            this.fuelCutRejected = fuelCutRejected;
            this.incompleteRejected = incompleteRejected;
            this.tpsAeOverlapEvents = tpsAeOverlapEvents;
            this.wallOverlapEvents = wallOverlapEvents;
            this.mapPredictOverlapEvents = mapPredictOverlapEvents;
            this.medianEarlyError = medianEarlyError;
        }
    }

    private InstantFuelRecommendation() { }

    static Result evaluate(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        if (snapshot == null) {
            return unavailable("Read Working Tune before Instant Fuel review.");
        }
        if (!snapshot.isExtraShotEnabled()) {
            return unavailable("Instant Fuel Pulse is disabled in the Working Tune. Automatic enabling is prohibited; enable intentionally through reviewed Task Settings first.");
        }
        if (!snapshot.hasInstantFuelSettings()) {
            return unavailable("Exact Working Tune tpsExtraShotMult / tpsExtraShotTimer baseline is unavailable.");
        }
        double current = snapshot.getExtraShotMultiplier();
        double timer = snapshot.getExtraShotTimer();
        if (current < MIN_MULT) {
            return unavailable("Current tpsExtraShotMult is zero/near-zero. Automatic enabling/seeding is prohibited; use reviewed Task Settings if enabling is intentional.");
        }
        if (evidence == null || evidence.isEmpty()) {
            return unavailable("No retained Instant Fuel evidence is available.");
        }

        List<TransientEvidenceEvent.Event> candidates = TransientEvidenceEvent.collect(
                TransientEvidenceEvent.Owner.INSTANT, evidence);
        List<Double> accepted = new ArrayList<Double>();
        int sustained = 0;
        int fuelCut = 0;
        int incomplete = 0;
        int tpsOverlap = 0;
        int wallOverlap = 0;
        int mapOverlap = 0;
        int lean = 0;
        int rich = 0;

        for (TransientEvidenceEvent.Event event : candidates) {
            if (event.fuelCut) {
                fuelCut++;
                continue;
            }
            if (!event.complete()) {
                incomplete++;
                continue;
            }
            if (event.sustainedSameDirection(SUSTAINED_ERROR_LIMIT)) {
                sustained++;
                continue;
            }
            accepted.add(event.earlyError);
            if (event.tpsAeOverlap) tpsOverlap++;
            if (event.wallOverlap) wallOverlap++;
            if (event.mapPredictOverlap) mapOverlap++;
            if (event.earlyError > LAMBDA_ERROR_DEADBAND) lean++;
            if (event.earlyError < -LAMBDA_ERROR_DEADBAND) rich++;
        }

        double median = percentile(accepted, 0.50);
        boolean enough = accepted.size() >= MIN_EVENTS;
        int signNeed = enough
                ? (int)Math.ceil(accepted.size() * 2.0 / 3.0)
                : Integer.MAX_VALUE;
        boolean leanRepeatable = lean >= signNeed;
        boolean richRepeatable = rich >= signNeed;
        ProposalWritePlan plan = null;
        String decision;

        if (!enough) {
            decision = "WITHHOLD — need at least " + MIN_EVENTS
                    + " clean pulse-counter/rising-edge events after residual/sustained-error gates.";
        } else if (!leanRepeatable && !richRepeatable) {
            decision = "WITHHOLD — early residual direction is not repeatable enough.";
        } else if (!Double.isFinite(median)
                || Math.abs(median) <= LAMBDA_ERROR_DEADBAND) {
            decision = "KEEP — early residual is inside the first-pass deadband.";
        } else {
            double step = Math.max(MIN_STEP,
                    Math.min(MAX_STEP, Math.abs(current) * 0.10));
            double proposed = median > 0.0 ? current + step : current - step;
            proposed = round2(Math.max(MIN_MULT, Math.min(MAX_MULT, proposed)));
            if (Math.abs(proposed - current) < 0.005) {
                decision = "KEEP — bounded multiplier movement resolves to the current value.";
            } else {
                List<ProposalWritePlan.Change> changes =
                        new ArrayList<ProposalWritePlan.Change>();
                changes.add(ProposalWritePlan.Change.scalar(
                        AeParameterNames.TPS_EXTRA_SHOT_MULT,
                        current, proposed, "Instant Fuel multiplier", "x"));
                plan = new ProposalWritePlan(
                        "instant-fuel-multiplier",
                        "Instant Fuel — Pulse Multiplier",
                        snapshot.getConfigurationName(),
                        "Repeatable method-owned first-moment lambda evidence only. Enable state, timer and RPM/TPS/MAP/CLT/delta-TPS curves remain separate authorities. No automatic Apply or Burn.",
                        changes);
                decision = "PROPOSE multiplier " + fmt(current) + " -> " + fmt(proposed)
                        + (median > 0.0
                        ? " for repeatable residual early lean."
                        : " for repeatable early rich response.");
            }
        }

        StringBuilder review = new StringBuilder();
        review.append("INSTANT FUEL — EVENT-OWNED MULTIPLIER REVIEW\n")
                .append("Firmware authority: pulse PW = base injection PW × correction curves × tpsExtraShotMult. This pass changes only the final multiplier.\n")
                .append("Event authority: pulse-counter edge when available; PW rising edge only as fallback. A retained non-zero PW cannot create repeated events.\n")
                .append("Working Tune: multiplier ").append(fmt(current))
                .append(" | timer ").append(fmt(timer))
                .append(" engine cycle(s) (timer handled by the setup/inhibit task).\n")
                .append("Pulse candidates: ").append(candidates.size())
                .append(" | accepted residual events: ").append(accepted.size())
                .append(" | sustained-error rejected: ").append(sustained)
                .append(" | fuel-cut rejected: ").append(fuelCut)
                .append(" | incomplete: ").append(incomplete).append(".\n")
                .append("Accepted-event context: TPS AE overlap ").append(tpsOverlap)
                .append(" | Wall overlap ").append(wallOverlap)
                .append(" | MAP Predict overlap ").append(mapOverlap).append(".\n")
                .append("Repeatable early direction: lean ").append(lean)
                .append(" | rich ").append(rich)
                .append(" | median lambda-target ").append(fmtSigned(median)).append(".\n")
                .append("Decision: ").append(decision).append("\n")
                .append("Boundary: Instant OFF/near-zero is never auto-enabled; sustained same-direction lambda error is rejected as an upstream/non-Instant problem; capture never writes; no Burn.");
        return new Result(review.toString(), plan, candidates.size(), accepted.size(),
                sustained, fuelCut, incomplete, tpsOverlap, wallOverlap,
                mapOverlap, median);
    }

    private static double percentile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>();
        for (Double value : values) {
            if (value != null && Double.isFinite(value.doubleValue())) {
                copy.add(value);
            }
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.2f", value) : "n/a";
    }

    private static String fmtSigned(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%+.4f", value) : "n/a";
    }

    private static Result unavailable(String reason) {
        return new Result("INSTANT FUEL — recommendation withheld\n" + nn(reason)
                + "\nNo ProposalWritePlan. Validated Task Settings remain available; no automatic Apply and no Burn.",
                null, 0, 0, 0, 0, 0, 0, 0, 0, Double.NaN);
    }
}
