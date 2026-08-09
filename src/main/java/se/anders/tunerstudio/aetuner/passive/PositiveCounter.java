package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Accumulates positive counter movement while recording resets and state attribution. */
final class PositiveCounter {
    static final int RUNNING = 0;
    static final int CRANKING = 1;
    static final int KEY_OFF = 2;
    static final int UNKNOWN = 3;

    private long samples;
    private long resets;
    private double last = Double.NaN;
    private double increase;
    private final double[] stateIncrease = new double[4];

    void reset() {
        samples = 0L;
        resets = 0L;
        last = Double.NaN;
        increase = 0.0;
        for (int i = 0; i < stateIncrease.length; i++) stateIncrease[i] = 0.0;
    }

    void add(double value) { add(value, UNKNOWN); }

    void add(double value, int stateBucket) {
        if (!Double.isFinite(value)) return;
        samples++;
        if (Double.isFinite(last)) {
            if (value >= last) {
                double delta = value - last;
                increase += delta;
                if (stateBucket >= 0 && stateBucket < stateIncrease.length) {
                    stateIncrease[stateBucket] += delta;
                } else {
                    stateIncrease[UNKNOWN] += delta;
                }
            } else if (last - value > 0.000001) {
                resets++;
            }
        }
        last = value;
    }

    Snapshot snapshot() {
        return new Snapshot(samples, increase, resets,
                stateIncrease[RUNNING], stateIncrease[CRANKING],
                stateIncrease[KEY_OFF], stateIncrease[UNKNOWN]);
    }

    static final class Snapshot {
        final long samples;
        final double increase;
        final long resets;
        final double runningIncrease;
        final double crankingIncrease;
        final double keyOffIncrease;
        final double unknownIncrease;

        Snapshot(long samples, double increase, long resets) {
            this(samples, increase, resets, 0.0, 0.0, 0.0, increase);
        }

        Snapshot(long samples, double increase, long resets,
                 double runningIncrease, double crankingIncrease,
                 double keyOffIncrease, double unknownIncrease) {
            this.samples = samples;
            this.increase = increase;
            this.resets = resets;
            this.runningIncrease = runningIncrease;
            this.crankingIncrease = crankingIncrease;
            this.keyOffIncrease = keyOffIncrease;
            this.unknownIncrease = unknownIncrease;
        }
    }
}
