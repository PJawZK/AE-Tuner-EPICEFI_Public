package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.EnumMap;
import java.util.Locale;

/** Session-local generated-tone profile. No audio files are bundled. */
final class GuidedAudioProfile {
    enum Pattern {
        SINGLE("Single tone"),
        DOUBLE("Double tone"),
        RISING_CHIRP("Rising chirp"),
        FALLING_CHIRP("Falling chirp"),
        THREE_ASCENDING("Three ascending"),
        SILENT("Silent");

        private final String label;

        Pattern(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static final class Setting {
        final boolean enabled;
        final Pattern pattern;
        final double startHz;
        final double endHz;
        final int durationMs;
        final int repeats;
        final int gapMs;
        final double volume;

        Setting(boolean enabled, Pattern pattern,
                double startHz, double endHz,
                int durationMs, int repeats, int gapMs,
                double volume) {
            this.enabled = enabled;
            this.pattern = pattern == null ? Pattern.SINGLE : pattern;
            this.startHz = clamp(startHz, 250.0, 2000.0);
            this.endHz = clamp(endHz, 250.0, 2000.0);
            this.durationMs = clamp(durationMs, 50, 500);
            this.repeats = clamp(repeats, 1, 3);
            this.gapMs = clamp(gapMs, 40, 300);
            this.volume = clamp(volume, 0.05, 1.0);
        }

        Setting copy() {
            return new Setting(enabled, pattern, startHz, endHz,
                    durationMs, repeats, gapMs, volume);
        }

        int estimatedDurationMs() {
            if (!enabled || pattern == Pattern.SILENT) {
                return 0;
            }
            int pulses = pattern == Pattern.THREE_ASCENDING
                    ? 3 : pattern == Pattern.DOUBLE
                    ? Math.max(2, repeats) : repeats;
            return pulses * durationMs + Math.max(0, pulses - 1) * gapMs;
        }

        String summary() {
            return (enabled ? "ON" : "OFF") + " | " + pattern
                    + " | " + Math.round(startHz) + "→"
                    + Math.round(endHz) + " Hz | " + durationMs
                    + " ms ×" + repeats + " | gap " + gapMs
                    + " ms | volume "
                    + String.format(Locale.US, "%.0f%%", volume * 100.0);
        }
    }

    private final EnumMap<GuidedAudioCueController.Cue, Setting> values =
            new EnumMap<GuidedAudioCueController.Cue, Setting>(
                    GuidedAudioCueController.Cue.class);

    static GuidedAudioProfile defaults() {
        GuidedAudioProfile profile = new GuidedAudioProfile();
        profile.put(GuidedAudioCueController.Cue.SESSION_STARTED,
                new Setting(true, Pattern.SINGLE,
                        520, 520, 120, 1, 70, 0.45));
        profile.put(GuidedAudioCueController.Cue.READY,
                new Setting(true, Pattern.RISING_CHIRP,
                        750, 1050, 180, 1, 70, 0.55));
        profile.put(GuidedAudioCueController.Cue.OPENING_PENDING,
                new Setting(false, Pattern.SINGLE,
                        1100, 1100, 70, 1, 60, 0.45));
        profile.put(GuidedAudioCueController.Cue.TARGET_ACQUIRED,
                new Setting(true, Pattern.DOUBLE,
                        1250, 1250, 90, 2, 70, 0.55));
        profile.put(GuidedAudioCueController.Cue.ACCEPTED,
                new Setting(true, Pattern.DOUBLE,
                        900, 900, 130, 2, 80, 0.55));
        profile.put(GuidedAudioCueController.Cue.EXCLUDED,
                new Setting(true, Pattern.FALLING_CHIRP,
                        650, 380, 150, 2, 70, 0.60));
        profile.put(GuidedAudioCueController.Cue.RETURN_TO_BASELINE,
                new Setting(true, Pattern.FALLING_CHIRP,
                        700, 450, 180, 1, 70, 0.55));
        profile.put(GuidedAudioCueController.Cue.COMPLETE,
                new Setting(true, Pattern.THREE_ASCENDING,
                        650, 1200, 130, 3, 60, 0.55));
        return profile;
    }

    GuidedAudioProfile copy() {
        GuidedAudioProfile copy = new GuidedAudioProfile();
        for (GuidedAudioCueController.Cue cue
                : GuidedAudioCueController.Cue.values()) {
            copy.put(cue, get(cue).copy());
        }
        return copy;
    }

    Setting get(GuidedAudioCueController.Cue cue) {
        Setting setting = values.get(cue);
        if (setting == null) {
            setting = new Setting(false, Pattern.SILENT,
                    800, 800, 100, 1, 60, 0.40);
        }
        return setting;
    }

    void put(GuidedAudioCueController.Cue cue, Setting setting) {
        if (cue != null && setting != null) {
            values.put(cue, setting.copy());
        }
    }

    String summary() {
        StringBuilder out = new StringBuilder();
        for (GuidedAudioCueController.Cue cue
                : GuidedAudioCueController.Cue.values()) {
            if (out.length() > 0) out.append("; ");
            out.append(cue.name()).append('=').append(get(cue).summary());
        }
        return out.toString();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
