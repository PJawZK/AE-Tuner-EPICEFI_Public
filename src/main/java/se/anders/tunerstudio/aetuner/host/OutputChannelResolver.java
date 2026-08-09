package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.Locale;
import java.util.Set;

/** Resolves one canonical role against TunerStudio's available output-channel names. */
public final class OutputChannelResolver {
    private OutputChannelResolver() { }

    public static String resolve(ChannelRole role, Set<String> availableOutputChannels) {
        if (role == null || availableOutputChannels == null || availableOutputChannels.isEmpty()) {
            return null;
        }

        for (String candidate : role.getCandidates()) {
            if (availableOutputChannels.contains(candidate)) {
                return candidate;
            }
        }

        for (String candidate : role.getCandidates()) {
            String normalizedCandidate = normalize(candidate);
            for (String available : availableOutputChannels) {
                if (normalize(available).equals(normalizedCandidate)) {
                    return available;
                }
            }
        }

        // EPICEFI/rusEFI has used both generated structure-field names and
        // output-channel aliases for Wall Wetting. Keep these fallbacks
        // deliberately role-specific to avoid resolving an unrelated channel.
        if (role == ChannelRole.WALL_CORRECTION) {
            for (String available : availableOutputChannels) {
                String value = normalize(available);
                if (value.contains("wallfuelcorrectionvalue")
                        || value.contains("fuelwallcorrectionvalue")) {
                    return available;
                }
            }
        } else if (role == ChannelRole.WALL_WETTING_PW) {
            for (String available : availableOutputChannels) {
                String value = normalize(available);
                if ((value.equals("wallfuelcorrection")
                        || value.endsWith("wallfuelcorrection")
                        || value.contains("wallwettinginjectiontime"))
                        && !value.contains("correctionvalue")) {
                    return available;
                }
            }
        }
        return null;
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
