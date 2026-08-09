package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.EnumMap;

public final class GuidedChannelValidityRegressionTest {
    private GuidedChannelValidityRegressionTest() { }

    public static void main(String[] args) {
        unavailableOptionalChannelsAreNeverShownAsConfirmedInactive();
        availableOptionalChannelsPreserveOriginalChecks();
        System.out.println("GuidedChannelValidityRegressionTest passed");
    }

    private static void unavailableOptionalChannelsAreNeverShownAsConfirmedInactive() {
        LiveSample sample = sample(false);
        String original = "✓ Engine running\n"
                + "✓ Not cranking\n"
                + "✓ No actual fuel cut\n"
                + "✓ No actual spark cut\n"
                + "✓ No trigger fault\n"
                + "✓ No active prediction or TPS detector burst\n";
        String decorated = GuidedChannelValidity.decorate(original, sample);

        require(!decorated.contains("✓ Not cranking"),
                "Unavailable cranking channel remained a confirmed green check");
        require(!decorated.contains("✓ No actual fuel cut"),
                "Unavailable fuel-cut channel remained a confirmed green check");
        require(decorated.contains("not confirmed"),
                "Unavailable optional state was not labelled unconfirmed");
        require(decorated.contains("advisory, not zero"),
                "Validity explanation did not state that missing channels are not zero");
    }

    private static void availableOptionalChannelsPreserveOriginalChecks() {
        LiveSample sample = sample(true);
        String original = "✓ Engine running\n"
                + "✓ Not cranking\n"
                + "✓ No actual fuel cut\n"
                + "✓ No actual spark cut\n"
                + "✓ No trigger fault\n"
                + "✓ No active prediction or TPS detector burst\n";
        String decorated = GuidedChannelValidity.decorate(original, sample);
        require(original.equals(decorated),
                "Available optional evidence unexpectedly changed the settle display");
    }

    private static LiveSample sample(boolean includeOptional) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2000.0);
        values.put(ChannelRole.MAP, 50.0);
        values.put(ChannelRole.TPS, 5.0);
        values.put(ChannelRole.FALLBACK_MAP, 50.0);
        if (includeOptional) {
            values.put(ChannelRole.ENGINE_RUNNING, 1.0);
            values.put(ChannelRole.ENGINE_CRANKING, 0.0);
            values.put(ChannelRole.FUEL_CUT, 0.0);
            values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
            values.put(ChannelRole.TRIGGER_ERROR, 0.0);
            values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
            values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        }
        return new LiveSample(1000000000L, 1.0, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
