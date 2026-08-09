package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorates settle-check presentation so an unavailable optional channel is
 * never displayed as a confirmed inactive state.
 *
 * Core RPM, TPS, MAP, and fallbackMap validity remains owned by the guided
 * recipe. This helper is presentation-only and does not weaken or override a
 * failed check.
 */
final class GuidedChannelValidity {
    private GuidedChannelValidity() { }

    static String decorate(String checks, LiveSample sample) {
        if (checks == null || checks.length() == 0 || sample == null) {
            return checks == null ? "" : checks;
        }

        List<Replacement> replacements = new ArrayList<Replacement>();
        int unavailable = 0;

        if (!finite(sample, ChannelRole.ENGINE_RUNNING)) {
            unavailable++;
            replacements.add(new Replacement("Engine running",
                    "~ Engine-running state inferred from RPM; channel unavailable"));
        }
        if (!finite(sample, ChannelRole.ENGINE_CRANKING)) {
            unavailable++;
            replacements.add(new Replacement("Not cranking",
                    "? Cranking-state channel unavailable — not confirmed"));
        }
        if (!finite(sample, ChannelRole.FUEL_CUT)) {
            unavailable++;
            replacements.add(new Replacement("No actual fuel cut",
                    "? Actual fuel-cut channel unavailable — inactive state not confirmed"));
        }
        if (!finite(sample, ChannelRole.TOTAL_SPARK_CUT)) {
            unavailable++;
            replacements.add(new Replacement("No actual spark cut",
                    "? Actual spark-cut channel unavailable — inactive state not confirmed"));
        }
        if (!finite(sample, ChannelRole.TRIGGER_ERROR)) {
            unavailable++;
            replacements.add(new Replacement("No trigger fault",
                    "? Trigger-fault channel unavailable — fault-free state not confirmed"));
        }

        boolean predictionAvailable = finite(sample, ChannelRole.MAP_PRED_ACTIVE);
        boolean detectorAvailable = finite(sample, ChannelRole.AE_ABOVE_THRESHOLD)
                || (finite(sample, ChannelRole.SMOOTHED_DELTA_TPS)
                && finite(sample, ChannelRole.ACCEL_THRESHOLD));
        if (!predictionAvailable || !detectorAvailable) {
            unavailable++;
            String missing = !predictionAvailable && !detectorAvailable
                    ? "MAP Predict and TPS-detector channels unavailable"
                    : !predictionAvailable
                    ? "MAP Predict active channel unavailable"
                    : "TPS-detector channels unavailable";
            replacements.add(new Replacement(
                    "No active prediction or TPS detector burst",
                    "? " + missing + " — quiet state not fully confirmed"));
        }

        String[] lines = checks.split("\\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Replacement replacement : replacements) {
                if (line.endsWith(replacement.originalLabel)) {
                    line = replacement.newLine;
                    break;
                }
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }

        if (unavailable > 0) {
            out.append("\n\nOptional channel validity: ")
                    .append(unavailable)
                    .append(" evidence group(s) unavailable. Core RPM/TPS/MAP/fallbackMap ")
                    .append("checks remain authoritative; unavailable optional states are advisory, not zero.");
        }
        return out.toString();
    }

    private static boolean finite(LiveSample sample, ChannelRole role) {
        return Double.isFinite(sample.get(role));
    }

    private static final class Replacement {
        final String originalLabel;
        final String newLine;

        Replacement(String originalLabel, String newLine) {
            this.originalLabel = originalLabel;
            this.newLine = newLine;
        }
    }
}
