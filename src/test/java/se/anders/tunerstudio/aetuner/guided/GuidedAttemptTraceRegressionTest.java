package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class GuidedAttemptTraceRegressionTest {
    private GuidedAttemptTraceRegressionTest() { }

    public static void main(String[] args) {
        preservesCompactTraceShapeAndAnchors();
        boundsLargeAttemptTrace();
        System.out.println("GuidedAttemptTraceRegressionTest passed");
    }

    private static void preservesCompactTraceShapeAndAnchors() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, 2000.0, 50.0, 8.0, 50.0, false, false));
        LiveSample measurement = sample(1.10, 2020.0, 55.0, 20.0, 78.0, true, true);
        LiveSample hold = sample(1.20, 2040.0, 65.0, 29.5, 80.0, false, true);
        LiveSample outcome = sample(1.55, 2100.0, 78.0, 30.0, 80.0, false, false);
        samples.add(measurement);
        samples.add(hold);
        samples.add(outcome);

        String trace = GuidedAttemptTrace.build(
                "VALID", samples,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                GuidedVehicleTestLimits.defaults(false),
                0.75, 0.20, 5.0, 8.0,
                measurement, 23.0, hold, outcome);

        require(trace.startsWith("Compact attempt trace (VALID)\n"),
                "trace disposition header changed");
        require(trace.contains("adaptive_baseline_s,0.75\n"),
                "baseline metadata changed");
        require(trace.contains("desired_tps_step,22.0\n"),
                "TPS-step metadata changed");
        require(trace.contains("dt_s,rpm,tps,map,fallbackMap,gap,tpsdot,detector,prediction\n"),
                "trace columns changed");
        require(trace.contains("measurement_anchor_dt_s=0.100,gap_kpa=23.00\n"),
                "measurement anchor metadata changed");
        require(trace.contains("natural_hold_dt_s=0.200,tps=29.5\n"),
                "hold anchor metadata changed");
        require(trace.contains("outcome_dt_s=0.550\n"),
                "outcome metadata changed");
    }

    private static void boundsLargeAttemptTrace() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        for (int i = 0; i < 320; i++) {
            double seconds = i * 0.01;
            samples.add(sample(seconds, 2000.0 + i,
                    50.0 + i * 0.1, 8.0 + i * 0.02,
                    60.0 + i * 0.1, false, i > 3));
        }
        String trace = GuidedAttemptTrace.build(
                "EXCLUDED", samples,
                new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, false),
                GuidedVehicleTestLimits.defaults(false),
                0.75, 0.20, 5.0, 8.0,
                null, Double.NaN, null, samples.get(samples.size() - 1));
        String[] lines = trace.split("\\n");
        require(lines.length < 60,
                "bounded trace expanded unexpectedly: " + lines.length + " lines");
        require(trace.contains("outcome_dt_s=3.190"),
                "final outcome timing missing from bounded trace");
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps, double fallback,
                                     boolean detector, boolean prediction) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
