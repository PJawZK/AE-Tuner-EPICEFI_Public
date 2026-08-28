package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class GuidedEventGearEvidenceRegressionTest {
    private GuidedEventGearEvidenceRegressionTest() { }

    public static void main(String[] args) {
        sustainedCleanDifferentGearIsMismatch();
        singleGearSpikeIsIgnored();
        impossibleVssDoesNotPoisonReference();
        System.out.println("GuidedEventGearEvidenceRegressionTest passed");
    }

    private static void sustainedCleanDifferentGearIsMismatch() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        for (int i = 0; i < 10; i++) {
            samples.add(sample(1.00 + i * 0.015, 3.0, 37.0 + i * 0.2));
        }
        GuidedEventGearEvidence.Result result =
                GuidedEventGearEvidence.evaluate(samples, 2);
        require(result.mismatch, "sustained clean gear 3 evidence did not flag session gear 2 mismatch");
        require(result.dominantGear == 3, "dominant event gear changed");
        require(result.dominantFraction >= 0.99, "clean dominant gear confidence changed");
    }

    private static void singleGearSpikeIsIgnored() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        for (int i = 0; i < 10; i++) {
            double gear = i == 4 ? 3.0 : 2.0;
            samples.add(sample(2.00 + i * 0.015, gear, 26.0 + i * 0.1));
        }
        GuidedEventGearEvidence.Result result =
                GuidedEventGearEvidence.evaluate(samples, 2);
        require(!result.mismatch, "single detected-gear spike became an event mismatch");
        require(result.dominantGear == 2, "session-matching gear stopped dominating");
    }

    private static void impossibleVssDoesNotPoisonReference() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(3.000, 2.0, 26.0));
        samples.add(sample(3.015, 0.0, 579.0));
        samples.add(sample(3.030, 2.0, 26.2));
        samples.add(sample(3.045, 2.0, 26.3));
        samples.add(sample(3.060, 2.0, 26.4));
        samples.add(sample(3.075, 2.0, 26.5));
        samples.add(sample(3.090, 2.0, 26.6));
        samples.add(sample(3.105, 2.0, 26.7));
        GuidedEventGearEvidence.Result result =
                GuidedEventGearEvidence.evaluate(samples, 2);
        require(!result.mismatch, "impossible VSS spike created a gear mismatch");
        require(result.rejectedVssSamples == 1, "impossible VSS was not rejected exactly once");
        require(result.dominantGear == 2, "clean post-spike gear evidence was poisoned");
    }

    private static LiveSample sample(double seconds, double gear, double vss) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        values.put(ChannelRole.RPM, 1500.0);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.MAP, 50.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
