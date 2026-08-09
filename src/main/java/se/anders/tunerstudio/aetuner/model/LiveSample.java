package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class LiveSample {
    private static final ChannelRole[] ROLES = ChannelRole.values();

    private final long nanoTime;
    private final double seconds;
    private final double[] values;
    private final double tpsDot;
    private final double mapDot;

    public LiveSample(long nanoTime,
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

    public long getNanoTime() {
        return nanoTime;
    }

    public double getSeconds() {
        return seconds;
    }

    public double get(ChannelRole role) {
        return values[role.ordinal()];
    }

    public double getTpsDot() {
        return tpsDot;
    }

    public double getMapDot() {
        return mapDot;
    }

    public boolean bool(ChannelRole role) {
        double value = get(role);
        return Double.isFinite(value) && value >= 0.5;
    }
}
