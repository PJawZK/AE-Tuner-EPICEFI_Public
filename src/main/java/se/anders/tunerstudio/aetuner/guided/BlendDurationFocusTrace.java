package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.List;

/**
 * Bounded presentation-only trace for the Blend Duration Driver view.
 *
 * Capture/evidence authority remains in BlendDurationGuidedSession. This class
 * only projects already-retained attempt samples into at most 96 display
 * points so Swing can draw a time-domain trace cheaply. Physical 20/80 markers
 * are presentation of authority already decided by PhysicalMapResponseMeasurement;
 * fallback/prediction remains diagnostic context only.
 */
public final class BlendDurationFocusTrace {
    private static final int MAX_POINTS = 96;
    private static final BlendDurationFocusTrace EMPTY =
            new BlendDurationFocusTrace(new double[0], new double[0],
                    new double[0], new double[0], new double[0],
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, false, "");

    public final double[] seconds;
    public final double[] tps;
    public final double[] map;
    public final double[] fallbackMap;
    public final double[] effectiveMap;
    public final double pedalSettledSeconds;
    public final double responseLowSeconds;
    public final double responseHighSeconds;
    /** Compatibility alias: physical-response high crossing marker. */
    public final double catchupSeconds;
    public final double physicalLateMapKpa;
    public final double responseLowMapKpa;
    public final double responseHighMapKpa;
    /** Diagnostic-only predicted/fallback target. */
    public final double predictionTargetKpa;
    public final boolean frozen;
    public final String outcome;

    private BlendDurationFocusTrace(double[] seconds,
                                    double[] tps,
                                    double[] map,
                                    double[] fallbackMap,
                                    double[] effectiveMap,
                                    double pedalSettledSeconds,
                                    double responseLowSeconds,
                                    double responseHighSeconds,
                                    double physicalLateMapKpa,
                                    double responseLowMapKpa,
                                    double responseHighMapKpa,
                                    double predictionTargetKpa,
                                    boolean frozen,
                                    String outcome) {
        this.seconds = seconds;
        this.tps = tps;
        this.map = map;
        this.fallbackMap = fallbackMap;
        this.effectiveMap = effectiveMap;
        this.pedalSettledSeconds = pedalSettledSeconds;
        this.responseLowSeconds = responseLowSeconds;
        this.responseHighSeconds = responseHighSeconds;
        this.catchupSeconds = responseHighSeconds;
        this.physicalLateMapKpa = physicalLateMapKpa;
        this.responseLowMapKpa = responseLowMapKpa;
        this.responseHighMapKpa = responseHighMapKpa;
        this.predictionTargetKpa = predictionTargetKpa;
        this.frozen = frozen;
        this.outcome = outcome == null ? "" : outcome;
    }

    public static BlendDurationFocusTrace empty() {
        return EMPTY;
    }

    public boolean hasData() {
        return seconds.length > 1;
    }

    public double durationSeconds() {
        return seconds.length == 0 ? Double.NaN : seconds[seconds.length - 1];
    }

    static BlendDurationFocusTrace build(List<LiveSample> samples,
                                         LiveSample holdAnchor,
                                         LiveSample responseLowSample,
                                         LiveSample responseHighSample,
                                         double physicalLateMapKpa,
                                         double responseLowMapKpa,
                                         double responseHighMapKpa,
                                         double predictionTargetKpa,
                                         boolean frozen,
                                         String outcome) {
        if (samples == null || samples.isEmpty()) return EMPTY;

        int sourceCount = samples.size();
        int count = Math.min(MAX_POINTS, sourceCount);
        double[] time = new double[count];
        double[] tps = new double[count];
        double[] map = new double[count];
        double[] fallback = new double[count];
        double[] effective = new double[count];

        LiveSample first = samples.get(0);
        long firstNano = first == null ? 0L : first.getNanoTime();
        for (int i = 0; i < count; i++) {
            int sourceIndex = count == 1 ? 0
                    : (int)Math.round((double)i * (sourceCount - 1) / (count - 1));
            LiveSample sample = samples.get(sourceIndex);
            if (sample == null) {
                time[i] = i == 0 ? 0.0 : time[i - 1];
                tps[i] = Double.NaN;
                map[i] = Double.NaN;
                fallback[i] = Double.NaN;
                effective[i] = Double.NaN;
                continue;
            }
            time[i] = firstNano == 0L
                    ? Math.max(0.0, sample.getSeconds() - first.getSeconds())
                    : Math.max(0.0, (sample.getNanoTime() - firstNano) / 1000000000.0);
            tps[i] = sample.get(ChannelRole.TPS);
            map[i] = sample.get(ChannelRole.MAP);
            fallback[i] = sample.get(ChannelRole.FALLBACK_MAP);
            effective[i] = sample.get(ChannelRole.EFFECTIVE_MAP);
        }

        return new BlendDurationFocusTrace(
                time, tps, map, fallback, effective,
                relativeSeconds(firstNano, first, holdAnchor),
                relativeSeconds(firstNano, first, responseLowSample),
                relativeSeconds(firstNano, first, responseHighSample),
                physicalLateMapKpa, responseLowMapKpa, responseHighMapKpa,
                predictionTargetKpa, frozen, outcome);
    }

    /** Legacy-shaped overload retained for older presentation regressions. */
    static BlendDurationFocusTrace build(List<LiveSample> samples,
                                         LiveSample holdAnchor,
                                         LiveSample physicalCatchSample,
                                         double predictionTargetKpa,
                                         boolean frozen,
                                         String outcome) {
        return build(samples, holdAnchor, null, physicalCatchSample,
                Double.NaN, Double.NaN, Double.NaN,
                predictionTargetKpa, frozen, outcome);
    }

    private static double relativeSeconds(long firstNano,
                                          LiveSample first,
                                          LiveSample marker) {
        if (marker == null || first == null) return Double.NaN;
        if (firstNano != 0L) {
            return Math.max(0.0,
                    (marker.getNanoTime() - firstNano) / 1000000000.0);
        }
        return Math.max(0.0, marker.getSeconds() - first.getSeconds());
    }
}
