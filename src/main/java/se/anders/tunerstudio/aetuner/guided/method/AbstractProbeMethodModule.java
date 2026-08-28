package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.Arrays;

/** Shared helpers only; each AE method keeps its own role list and activity rule. */
abstract class AbstractProbeMethodModule implements GuidedAeMethodModule {
    @Override
    public final CaptureMode captureMode() {
        return CaptureMode.READ_ONLY_PROBE;
    }

    protected static boolean nonZero(LiveSample sample, ChannelRole role) {
        if (sample == null) return false;
        double value = sample.get(role);
        return Double.isFinite(value) && Math.abs(value) > 0.000001;
    }

    protected static boolean positive(LiveSample sample, ChannelRole role) {
        if (sample == null) return false;
        double value = sample.get(role);
        return Double.isFinite(value) && value > 0.000001;
    }

    protected static boolean gap(LiveSample sample, ChannelRole a,
                                 ChannelRole b, double minimum) {
        if (sample == null) return false;
        double first = sample.get(a);
        double second = sample.get(b);
        return Double.isFinite(first) && Double.isFinite(second)
                && Math.abs(first - second) >= minimum;
    }

    protected static String enabled(boolean value) {
        return value ? "ENABLED" : "DISABLED";
    }

    protected static String axis(double[] values) {
        if (values == null || values.length == 0) return "n/a";
        if (values.length <= 8) return Arrays.toString(values);
        return "[" + values[0] + ", " + values[1] + ", ... "
                + values[values.length - 2] + ", " + values[values.length - 1]
                + "] (" + values.length + " points)";
    }
}
