package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared immutable transient evidence event used by the multi-surface Guided
 * recommendation engines.
 *
 * Operating-condition bins are latched at the owning method's event onset while
 * lambda is evaluated only in a later response window. This avoids assigning
 * the same instantaneous lambda sample to whichever table happens to be under
 * review and provides one common place to reject fuel-cut and obvious
 * cross-method contamination.
 *
 * This is deliberately a conservative first transport-alignment model rather
 * than a claimed exhaust-physics model. Future vehicle/.mlg work can replace
 * the fixed windows with an RPM/load-specific measured transport model without
 * changing callers.
 */
final class TransientEvidenceEvent {
    enum Owner { TPS_AE, WALL, INSTANT }

    static final class Event {
        final Owner owner;
        final double startSeconds;
        final double rpm;
        final double tps;
        final double map;
        final double clt;
        final double deltaTps;
        final double earlyError;
        final double lateError;
        final int earlySamples;
        final boolean fuelCut;
        final boolean tpsAeOverlap;
        final boolean wallOverlap;
        final boolean instantOverlap;
        final boolean mapPredictOverlap;

        Event(Owner owner, double startSeconds,
              double rpm, double tps, double map, double clt, double deltaTps,
              double earlyError, double lateError, int earlySamples,
              boolean fuelCut, boolean tpsAeOverlap, boolean wallOverlap,
              boolean instantOverlap, boolean mapPredictOverlap) {
            this.owner = owner;
            this.startSeconds = startSeconds;
            this.rpm = rpm;
            this.tps = tps;
            this.map = map;
            this.clt = clt;
            this.deltaTps = deltaTps;
            this.earlyError = earlyError;
            this.lateError = lateError;
            this.earlySamples = earlySamples;
            this.fuelCut = fuelCut;
            this.tpsAeOverlap = tpsAeOverlap;
            this.wallOverlap = wallOverlap;
            this.instantOverlap = instantOverlap;
            this.mapPredictOverlap = mapPredictOverlap;
        }

        boolean complete() {
            return !fuelCut && earlySamples >= 3 && Double.isFinite(earlyError);
        }

        boolean sustainedSameDirection(double limit) {
            return Double.isFinite(earlyError) && Double.isFinite(lateError)
                    && Math.abs(lateError) > limit
                    && Math.signum(earlyError) == Math.signum(lateError);
        }

        double value(ChannelRole role) {
            if (role == ChannelRole.RPM) return rpm;
            if (role == ChannelRole.TPS) return tps;
            if (role == ChannelRole.MAP) return map;
            if (role == ChannelRole.COOLANT) return clt;
            if (role == ChannelRole.DELTA_TPS) return deltaTps;
            return Double.NaN;
        }
    }

    private static final double TPS_GAP_SECONDS = 0.45;
    private static final double WALL_GAP_SECONDS = 0.55;
    private static final double INSTANT_GAP_SECONDS = 0.55;
    private static final double LATE_START_SECONDS = 1.00;
    private static final double WINDOW_END_SECONDS = 1.80;

    private TransientEvidenceEvent() { }

    static List<Event> collect(Owner owner, List<LiveSample> samples) {
        if (owner == null || samples == null || samples.isEmpty()) {
            return Collections.emptyList();
        }
        List<Event> result = new ArrayList<Event>();
        double lastStart = Double.NEGATIVE_INFINITY;
        double lastInstantCounter = Double.NaN;
        boolean lastInstantPulse = false;
        boolean lastTpsActive = false;

        for (int i = 0; i < samples.size(); i++) {
            LiveSample start = samples.get(i);
            if (start == null || !Double.isFinite(start.getSeconds())) continue;

            double counter = start.get(ChannelRole.INSTANT_PULSE_CNT);
            boolean pulseNow = positive(start, ChannelRole.INSTANT_PULSE_PW);
            boolean counterAvailable = Double.isFinite(counter);
            boolean hadCounterBaseline = Double.isFinite(lastInstantCounter);
            boolean counterEdge = counterAvailable && hadCounterBaseline
                    && counter > lastInstantCounter + 0.5;
            boolean pulseRise = pulseNow && !lastInstantPulse;
            if (counterAvailable) lastInstantCounter = counter;
            lastInstantPulse = pulseNow;

            boolean tpsNow = start.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                    || positive(start, ChannelRole.AE_ADD_MS);
            boolean tpsEdge = start.bool(ChannelRole.AE_EVENT_JUST_OCCURRED)
                    || (tpsNow && !lastTpsActive);
            lastTpsActive = tpsNow;

            boolean anchor;
            if (owner == Owner.TPS_AE) {
                anchor = tpsEdge;
            } else if (owner == Owner.WALL) {
                anchor = opening(start) && wallActive(start);
            } else {
                // Once a counter baseline exists, counter edges are authoritative.
                // If capture begins exactly on the first pulse there is no prior
                // counter sample, so allow one PW rising-edge fallback. A steady
                // non-zero PW can never create subsequent events.
                anchor = counterAvailable && hadCounterBaseline
                        ? counterEdge : pulseRise;
            }
            if (!anchor) continue;

            double gap = owner == Owner.TPS_AE ? TPS_GAP_SECONDS
                    : owner == Owner.WALL ? WALL_GAP_SECONDS : INSTANT_GAP_SECONDS;
            if (start.getSeconds() - lastStart < gap) continue;

            double earlyStart = owner == Owner.INSTANT ? 0.15 : 0.20;
            double earlyEnd = owner == Owner.INSTANT ? 0.85 : 0.95;
            List<Double> early = new ArrayList<Double>();
            List<Double> late = new ArrayList<Double>();
            boolean fuelCut = false;
            boolean tpsOverlap = false;
            boolean wallOverlap = false;
            boolean instantOverlap = false;
            boolean mapOverlap = false;
            double t0 = start.getSeconds();

            for (int j = i; j < samples.size(); j++) {
                LiveSample sample = samples.get(j);
                if (sample == null || !Double.isFinite(sample.getSeconds())) continue;
                double dt = sample.getSeconds() - t0;
                if (dt > WINDOW_END_SECONDS) break;
                if (sample.bool(ChannelRole.DFCO) || sample.bool(ChannelRole.FUEL_CUT)) {
                    fuelCut = true;
                }
                if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                        || positive(sample, ChannelRole.AE_ADD_MS)) tpsOverlap = true;
                if (wallActive(sample)) wallOverlap = true;
                if (positive(sample, ChannelRole.INSTANT_PULSE_PW)) instantOverlap = true;
                if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)) mapOverlap = true;

                double lambda = sample.get(ChannelRole.LAMBDA);
                double target = sample.get(ChannelRole.TARGET_LAMBDA);
                if (!validLambda(lambda, target)) continue;
                double error = lambda - target;
                if (dt >= earlyStart && dt <= earlyEnd) early.add(error);
                if (dt >= LATE_START_SECONDS && dt <= WINDOW_END_SECONDS) late.add(error);
            }

            double earlyError = robustSignedError(early);
            double lateError = percentile(late, 0.50);
            result.add(new Event(owner, t0,
                    start.get(ChannelRole.RPM), start.get(ChannelRole.TPS),
                    start.get(ChannelRole.MAP), start.get(ChannelRole.COOLANT),
                    start.get(ChannelRole.DELTA_TPS),
                    earlyError, lateError, early.size(), fuelCut,
                    tpsOverlap, wallOverlap, instantOverlap, mapOverlap));
            lastStart = t0;
        }
        return Collections.unmodifiableList(result);
    }

    static List<Event> acceptedForTask(Owner owner, List<LiveSample> samples) {
        List<Event> all = collect(owner, samples);
        if (all.isEmpty()) return all;
        List<Event> accepted = new ArrayList<Event>();
        for (Event event : all) {
            if (!event.complete()) continue;
            if (owner == Owner.INSTANT && event.sustainedSameDirection(0.040)) continue;
            if (owner == Owner.WALL && event.instantOverlap) continue;
            accepted.add(event);
        }
        return Collections.unmodifiableList(accepted);
    }

    private static boolean opening(LiveSample sample) {
        double smooth = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        return sample.getTpsDot() > 2.0
                || (Double.isFinite(smooth) && smooth > 0.02);
    }

    private static boolean wallActive(LiveSample sample) {
        return nonZero(sample, ChannelRole.WALL_CORRECTION)
                || nonZero(sample, ChannelRole.WALL_WETTING_PW);
    }

    private static boolean positive(LiveSample sample, ChannelRole role) {
        double value = sample == null ? Double.NaN : sample.get(role);
        return Double.isFinite(value) && value > 0.000001;
    }

    private static boolean nonZero(LiveSample sample, ChannelRole role) {
        double value = sample == null ? Double.NaN : sample.get(role);
        return Double.isFinite(value) && Math.abs(value) > 0.000001;
    }

    private static boolean validLambda(double lambda, double target) {
        return Double.isFinite(lambda) && Double.isFinite(target)
                && lambda > 0.55 && lambda < 1.65
                && target > 0.55 && target < 1.65;
    }

    private static double robustSignedError(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        double p20 = percentile(values, 0.20);
        double p80 = percentile(values, 0.80);
        if (!Double.isFinite(p20)) return p80;
        if (!Double.isFinite(p80)) return p20;
        return Math.abs(p80) >= Math.abs(p20) ? p80 : p20;
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
        double fraction = position - lo;
        return copy.get(lo).doubleValue()
                + fraction * (copy.get(hi).doubleValue() - copy.get(lo).doubleValue());
    }
}
