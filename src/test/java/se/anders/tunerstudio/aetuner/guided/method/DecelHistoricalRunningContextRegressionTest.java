package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Permanent regression for the retained real log's 0-200 RPM cranking closures. */
public final class DecelHistoricalRunningContextRegressionTest {
    private DecelHistoricalRunningContextRegressionTest() { }

    public static void main(String[] args) {
        nonRunningClosuresDoNotEnterPhysicalEventBins();
        System.out.println("DecelHistoricalRunningContextRegressionTest passed");
    }

    private static void nonRunningClosuresDoNotEnterPhysicalEventBins() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = 0.0;
        for (int i = 0; i < 90; i++) {
            samples.add(sample(seconds, 0.0, 0.22, 0.0, 0.2, false, false));
            seconds += 0.02;
        }

        seconds += 0.25;
        samples.add(sample(seconds, 195.0, 20.0, 0.0, 0.0, false, true));
        seconds += 0.04;
        samples.add(sample(seconds, 195.0, 18.0, -1.20, -20.0, false, true));
        seconds += 0.04;
        samples.add(sample(seconds, 195.0, 17.5, 0.0, -2.0, false, true));

        seconds += 0.25;
        samples.add(sample(seconds, 1600.0, 20.0, 0.0, 0.0, true, false));
        seconds += 0.04;
        samples.add(sample(seconds, 1600.0, 18.0, -1.20, -20.0, true, false));
        seconds += 0.04;
        samples.add(sample(seconds, 1600.0, 17.5, 0.0, -2.0, true, false));

        DecelHistoricalEvidenceAnalyzer.Result result =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(), samples);
        require(result.quietCalibrationLocked,
                "engine-off quiet sensor evidence no longer locks the unchanged quiet calibration");
        require(result.movementEvents == 1 && result.decelReleases == 1,
                "cranking/non-running closing event reached physical Decel classification");
        require(result.reportText.contains("Engine-state event qualification excluded")
                        && result.reportText.contains("quiet-signal calibration remains unchanged"),
                "historical report did not expose the engine-state event boundary");
        require(result.bins.get(0).events.isEmpty(),
                "195 RPM cranking closure was assigned to the lowest threshold bin");
        require(result.bins.get(1).decelReleases == 1,
                "valid running 1600 RPM release was not retained");
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double delta, double tpsDot,
                                     boolean running, boolean cranking) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ENGINE_RUNNING, running ? 1.0 : 0.0);
        values.put(ChannelRole.ENGINE_CRANKING, cranking ? 1.0 : 0.0);
        return new LiveSample((long) (seconds * 1.0e9), seconds, values, tpsDot, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        double[] rpm = new double[]{800, 1600, 2400, 3200};
        double[] threshold = new double[]{0.57, 0.60, 0.83, 0.87};
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 40.0, 0.06, true, true, 1.0,
                rpm, threshold, 4.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
