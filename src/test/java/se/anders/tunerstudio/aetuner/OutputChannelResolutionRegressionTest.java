package se.anders.tunerstudio.aetuner;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/** Deterministic checks for exact EPICEFI output-channel aliases and evidence text. */
public final class OutputChannelResolutionRegressionTest {
    private OutputChannelResolutionRegressionTest() { }

    public static void main(String[] args) {
        exactGeneratedNamesMustResolve();
        evidenceMustExposeSelectedNamesAndRawValues();
        System.out.println("OutputChannelResolutionRegressionTest passed");
    }

    private static void exactGeneratedNamesMustResolve() {
        Set<String> available = new HashSet<String>(Arrays.asList(
                "ready",
                "crank",
                "ignitionOn",
                "Main relay: Has IGN voltage",
                "sparkCutReason",
                "fuelCutReason",
                "overDwellNotScheduledCounter",
                "dwellOverChargeCounter",
                "dwellUnderChargeCounter",
                "sparkOutOfOrderCounter"));

        requireEquals("ready", OutputChannelResolver.resolve(ChannelRole.ENGINE_RUNNING, available));
        requireEquals("crank", OutputChannelResolver.resolve(ChannelRole.ENGINE_CRANKING, available));
        requireEquals("sparkCutReason", OutputChannelResolver.resolve(ChannelRole.IGN_CUT_CODE, available));
        requireEquals("fuelCutReason", OutputChannelResolver.resolve(ChannelRole.FUEL_CUT_CODE, available));
        requireEquals("overDwellNotScheduledCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_OVERDWELL, available));
        requireEquals("dwellOverChargeCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_OVERCHARGE_WARNINGS, available));
        requireEquals("dwellUnderChargeCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_UNDERCHARGE_WARNINGS, available));
        requireEquals("sparkOutOfOrderCounter",
                OutputChannelResolver.resolve(ChannelRole.IGN_SPARK_OUT_OF_ORDER, available));
    }

    private static void evidenceMustExposeSelectedNamesAndRawValues() {
        EnumMap<ChannelRole, String> selected = new EnumMap<ChannelRole, String>(ChannelRole.class);
        selected.put(ChannelRole.ENGINE_RUNNING, "ready");
        selected.put(ChannelRole.ENGINE_CRANKING, "crank");
        selected.put(ChannelRole.IGN_CUT_CODE, "sparkCutReason");

        EnumMap<ChannelRole, Double> latest = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        latest.put(ChannelRole.ENGINE_RUNNING, 1.0);
        latest.put(ChannelRole.ENGINE_CRANKING, 0.0);
        latest.put(ChannelRole.IGN_CUT_CODE, 14.0);

        String evidence = ChannelResolutionEvidence.build(selected, latest);
        require(evidence.contains("running: selected `ready`; latest raw 1.0"),
                "Running evidence must expose selected internal name and raw value");
        require(evidence.contains("cranking: selected `crank`; latest raw 0.0"),
                "Cranking evidence must preserve a received zero value");
        require(evidence.contains("Ign: Cut Code: selected `sparkCutReason`; latest raw 14.0"),
                "Cut-code evidence must expose selected internal name and raw value");
        require(evidence.contains("Ignition: overcharge warnings: unresolved; tried"),
                "Unresolved evidence must list attempted candidates");
    }

    private static void requireEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
