package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Regression for read-only AE Foundation threshold/noise classification. */
public final class FoundationThresholdEvidenceRegressionTest {
    private FoundationThresholdEvidenceRegressionTest() { }

    public static void main(String[] args) {
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        long nano = 0L;
        double seconds = 0.0;
        for (int i = 0; i < 30; i++) {
            evidence.add(sample(nano += 25000000L, seconds += 0.025,
                    0.10, 1.00, 1.0, 1800.0));
        }
        for (int i = 0; i < 3; i++) {
            evidence.add(sample(nano += 25000000L, seconds += 0.025,
                    1.20, 1.00, 1.0, 1800.0));
        }
        for (int i = 0; i < 3; i++) {
            evidence.add(sample(nano += 25000000L, seconds += 0.025,
                    1.20, 1.00, 12.0, 2800.0));
        }
        evidence.add(sample(nano += 25000000L, seconds += 0.025,
                0.80, 1.00, 12.0, 2800.0));

        FoundationThresholdMethodModule module = new FoundationThresholdMethodModule();
        require(module.reviewedWritePlan(null, evidence) == null,
                "Threshold review must remain read-only without ProposalWritePlan");
        String review = module.reviewOutputs();
        require(review.contains("Quiet/noise calibration samples: 30"), review);
        require(review.contains("False-trigger candidate samples: 3"), review);
        require(review.contains("Missed-intent candidate samples: 1"), review);
        require(review.contains("Near-threshold intentional samples (75-100%): 1"), review);
        require(review.contains("1500-2499: 3 / 0 / 3"), review);
        require(review.contains("2500-3499: 3 / 3 / 0"), review);
        require(review.contains("review classifier only; not an ECU setting"), review);
        require(review.contains("No automatic Apply and no burn"), review);
        System.out.println("FoundationThresholdEvidenceRegressionTest passed");
    }

    private static LiveSample sample(long nano, double seconds,
                                     double delta, double threshold,
                                     double tpsRate, double rpm) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(18.0));
        values.put(ChannelRole.DELTA_TPS, Double.valueOf(delta));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(threshold));
        return new LiveSample(nano, seconds, values, tpsRate, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
