package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Shared visibility rules for the transient-fueling paths. */
public final class TransientSignals {
    private static final double FALLBACK_MAP_GAP_KPA = 1.0;
    private static final double WALL_WETTING_PW_VISIBLE_MS = 0.0002;
    private static final double WALL_CORRECTION_VISIBLE = 0.10;

    private TransientSignals() { }

    public static boolean mapPredictionVisible(LiveSample sample) {
        double active = sample.get(ChannelRole.MAP_PRED_ACTIVE);
        // When the dedicated displayed channel exists, it is the source of
        // truth. A small effectiveMap/MAP numerical offset after prediction
        // has ended must not create a new MAP Predict event.
        if (Double.isFinite(active)) {
            return active >= 0.5;
        }
        // Older definitions may not expose isMapPredictionActive. Only then
        // fall back to a meaningful effective-MAP gap.
        double map = sample.get(ChannelRole.MAP);
        double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
        return Double.isFinite(map) && Double.isFinite(effective)
                && effective - map > FALLBACK_MAP_GAP_KPA;
    }

    public static boolean wallWettingVisible(LiveSample sample) {
        double wallPw = sample.get(ChannelRole.WALL_WETTING_PW);
        // Prefer the displayed injector-time contribution when available. It
        // avoids treating tiny continuous film-model numerical movement as a
        // standalone event at idle or steady cruise.
        if (Double.isFinite(wallPw)) {
            return Math.abs(wallPw) > WALL_WETTING_PW_VISIBLE_MS;
        }
        return absGreater(sample.get(ChannelRole.WALL_CORRECTION), WALL_CORRECTION_VISIBLE);
    }

    public static boolean instantFuelVisible(LiveSample sample) {
        return sample.bool(ChannelRole.AE_EXTRA_SHOT)
                || absGreater(sample.get(ChannelRole.INSTANT_PULSE_PW), 0.0001);
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
    }
}
