package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the signed falling-TPS detector EpicEFI defines for Decel.
 *
 * Firmware assigns decelTps from the falling twin of tpsAeDetectMode. Therefore
 * the explicitly published mode twin is the strongest historical/live analysis
 * authority when it is present. Fuel: TPS decel change/decelTps remains an
 * important cross-check and fallback, but it must not override a known-mode
 * twin when the two disagree in a log.
 */
public final class DecelSignalSamples {
    private DecelSignalSamples() { }

    public static double value(LiveSample sample, AeProjectSnapshot snapshot) {
        return value(sample, snapshot == null ? null : snapshot.getEngagementModel());
    }

    /** Prefer the configured-mode falling twin; use direct decelTps only as fallback. */
    public static double value(LiveSample sample, String detectorMode) {
        if (sample == null) return Double.NaN;
        ChannelRole twin = modeTwin(detectorMode);
        if (twin != null) {
            double candidate = sample.get(twin);
            if (Double.isFinite(candidate)) return candidate;
        }
        double direct = sample.get(ChannelRole.DECEL_DELTA_TPS);
        return Double.isFinite(direct) ? direct : Double.NaN;
    }

    public static double directValue(LiveSample sample) {
        if (sample == null) return Double.NaN;
        double direct = sample.get(ChannelRole.DECEL_DELTA_TPS);
        return Double.isFinite(direct) ? direct : Double.NaN;
    }

    public static double modeTwinValue(LiveSample sample, String detectorMode) {
        if (sample == null) return Double.NaN;
        ChannelRole twin = modeTwin(detectorMode);
        if (twin == null) return Double.NaN;
        double candidate = sample.get(twin);
        return Double.isFinite(candidate) ? candidate : Double.NaN;
    }

    public static ChannelRole modeTwin(AeProjectSnapshot snapshot) {
        return modeTwin(snapshot == null ? null : snapshot.getEngagementModel());
    }

    public static ChannelRole modeTwin(String detectorMode) {
        String model = normalize(detectorMode);
        if (model.length() == 0 || "unknown".equals(model)) return null;
        if (model.contains("dual") && model.contains("stride") && model.contains("newest")) {
            return ChannelRole.AE_DELTA_NEWEST_FALL;
        }
        if (model.contains("max") && model.contains("step") && model.contains("timed")) {
            return ChannelRole.AE_DELTA_TIMED_FALL;
        }
        if (model.contains("window") && model.contains("span")) {
            return ChannelRole.AE_DELTA_SPAN;
        }
        if (model.contains("rise") && model.contains("floor")) {
            return ChannelRole.AE_DELTA_FALL;
        }
        if (model.contains("legacy") || (model.contains("max") && model.contains("step"))) {
            return ChannelRole.AE_DELTA_MIN_STEP;
        }
        return null;
    }

    public static String sourceLabel(List<LiveSample> samples, AeProjectSnapshot snapshot) {
        return sourceLabel(samples, snapshot == null ? null : snapshot.getEngagementModel());
    }

    public static String sourceLabel(List<LiveSample> samples, String detectorMode) {
        boolean direct = false;
        boolean twin = false;
        ChannelRole twinRole = modeTwin(detectorMode);
        if (samples != null) {
            for (LiveSample sample : samples) {
                if (sample == null) continue;
                if (Double.isFinite(sample.get(ChannelRole.DECEL_DELTA_TPS))) direct = true;
                if (twinRole != null && Double.isFinite(sample.get(twinRole))) twin = true;
                if (direct && twin) break;
            }
        }
        if (twin) {
            return direct
                    ? twinRole.getLabel() + " (mode-matched firmware twin; direct decelTps retained as cross-check)"
                    : twinRole.getLabel() + " (mode-matched firmware twin)";
        }
        if (direct) {
            return "Fuel: TPS decel change (direct decelTps fallback; configured-mode twin unavailable)";
        }
        if (twinRole != null) {
            return "UNAVAILABLE — need " + twinRole.getLabel()
                    + " or Fuel: TPS decel change for detector mode '" + detectorMode + "'";
        }
        return "UNAVAILABLE — detector mode unresolved and no direct decelTps available";
    }

    public static int usableCount(List<LiveSample> samples, AeProjectSnapshot snapshot) {
        return usableCount(samples, snapshot == null ? null : snapshot.getEngagementModel());
    }

    public static int usableCount(List<LiveSample> samples, String detectorMode) {
        if (samples == null) return 0;
        int count = 0;
        for (LiveSample sample : samples) if (Double.isFinite(value(sample, detectorMode))) count++;
        return count;
    }

    public static List<LiveSample> asPrimaryDelta(List<LiveSample> samples,
                                                   AeProjectSnapshot snapshot) {
        return asPrimaryDelta(samples,
                snapshot == null ? null : snapshot.getEngagementModel());
    }

    /** Adapt the existing generic analyzer boundary to the firmware-correct falling signal. */
    public static List<LiveSample> asPrimaryDelta(List<LiveSample> samples,
                                                   String detectorMode) {
        List<LiveSample> out = new ArrayList<LiveSample>();
        if (samples == null) return out;
        ChannelRole[] roles = ChannelRole.values();
        for (LiveSample sample : samples) {
            if (sample == null) continue;
            double decel = value(sample, detectorMode);
            EnumMap<ChannelRole, Double> values =
                    new EnumMap<ChannelRole, Double>(ChannelRole.class);
            for (ChannelRole role : roles) {
                double v = sample.get(role);
                if (Double.isFinite(v)) values.put(role, v);
            }
            if (Double.isFinite(decel)) values.put(ChannelRole.DELTA_TPS, decel);
            else values.remove(ChannelRole.DELTA_TPS);
            out.add(new LiveSample(sample.getNanoTime(), sample.getSeconds(), values,
                    sample.getTpsDot(), sample.getMapDot()));
        }
        return out;
    }

    /**
     * When firmware engine-state channels are available, physical closing events
     * require running and reject cranking. If those channels are absent, legacy
     * text evidence remains usable and the analyzer falls back to its prior rules.
     */
    static boolean physicalEvidenceAllowed(LiveSample sample) {
        if (sample == null) return false;
        double running = sample.get(ChannelRole.ENGINE_RUNNING);
        if (Double.isFinite(running) && running < 0.5) return false;
        double cranking = sample.get(ChannelRole.ENGINE_CRANKING);
        return !Double.isFinite(cranking) || cranking < 0.5;
    }

    public static Agreement agreement(List<LiveSample> samples, AeProjectSnapshot snapshot,
                                      double tolerance) {
        return agreement(samples,
                snapshot == null ? null : snapshot.getEngagementModel(), tolerance);
    }

    /** Audit direct decelTps against the exact falling twin selected by detector mode. */
    public static Agreement agreement(List<LiveSample> samples, String detectorMode,
                                      double tolerance) {
        ChannelRole twin = modeTwin(detectorMode);
        if (samples == null || twin == null) return new Agreement(twin, 0, 0, Double.NaN);
        int compared = 0;
        int mismatched = 0;
        double maxDifference = 0.0;
        for (LiveSample sample : samples) {
            if (sample == null) continue;
            double direct = sample.get(ChannelRole.DECEL_DELTA_TPS);
            double candidate = sample.get(twin);
            if (!Double.isFinite(direct) || !Double.isFinite(candidate)) continue;
            compared++;
            double difference = Math.abs(direct - candidate);
            if (difference > maxDifference) maxDifference = difference;
            if (difference > tolerance) mismatched++;
        }
        return new Agreement(twin, compared, mismatched,
                compared == 0 ? Double.NaN : maxDifference);
    }

    public static final class Agreement {
        public final ChannelRole twinRole;
        public final int comparedSamples;
        public final int mismatchedSamples;
        public final double maxDifference;

        Agreement(ChannelRole twinRole, int comparedSamples,
                  int mismatchedSamples, double maxDifference) {
            this.twinRole = twinRole;
            this.comparedSamples = comparedSamples;
            this.mismatchedSamples = mismatchedSamples;
            this.maxDifference = maxDifference;
        }

        public boolean verified() {
            return twinRole != null && comparedSamples > 0 && mismatchedSamples == 0;
        }

        public boolean directUntrusted() {
            return twinRole != null && comparedSamples > 0 && mismatchedSamples > 0;
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }
}
