package se.anders.tunerstudio.aetuner;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

final class LiveSample {
    private static final ChannelRole[] ROLES = ChannelRole.values();

    private final long nanoTime;
    private final double seconds;
    private final double[] values;
    private final double tpsDot;
    private final double mapDot;

    LiveSample(long nanoTime,
               double seconds,
               EnumMap<ChannelRole, Double> sourceValues,
               double tpsDot,
               double mapDot) {
        this.nanoTime = nanoTime;
        this.seconds = seconds;
        this.values = new double[ROLES.length];
        Arrays.fill(this.values, Double.NaN);
        for (Map.Entry<ChannelRole, Double> entry : sourceValues.entrySet()) {
            Double value = entry.getValue();
            this.values[entry.getKey().ordinal()] = value == null ? Double.NaN : value.doubleValue();
        }
        this.tpsDot = tpsDot;
        this.mapDot = mapDot;
    }

    long getNanoTime() { return nanoTime; }
    double getSeconds() { return seconds; }
    double get(ChannelRole role) { return values[role.ordinal()]; }
    double getTpsDot() { return tpsDot; }
    double getMapDot() { return mapDot; }

    boolean bool(ChannelRole role) {
        double value = get(role);
        return Double.isFinite(value) && value >= 0.5;
    }
}
