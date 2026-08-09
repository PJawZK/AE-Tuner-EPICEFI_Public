package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Immutable retained measurement from one valid adaptive Guided opening. */
final class BlendDurationAttempt {
    final int number;
    final double duration;
    final double baseRpm;
    final double baseMap;
    final double baseTps;
    final double heldTps;
    final double tpsStep;
    final double gap;
    final String trend;
    final BlendDurationCaptureConfig settings;
    final int gearMin;
    final int gearMax;
    final boolean gearOscillation;
    final boolean vssBad;
    boolean warning;

    BlendDurationAttempt(int number, double duration, double baseRpm,
                         double baseMap, double baseTps, double heldTps,
                         double gap, String trend,
                         BlendDurationCaptureConfig settings,
                         int gearMin, int gearMax,
                         boolean gearOscillation, boolean vssBad) {
        this.number = number;
        this.duration = duration;
        this.baseRpm = baseRpm;
        this.baseMap = baseMap;
        this.baseTps = baseTps;
        this.heldTps = heldTps;
        this.tpsStep = heldTps - baseTps;
        this.gap = gap;
        this.trend = trend;
        this.settings = settings;
        this.gearMin = gearMin;
        this.gearMax = gearMax;
        this.gearOscillation = gearOscillation;
        this.vssBad = vssBad;
    }

    static BlendDurationAttempt build(int number,
                                      double baseRpm,
                                      double baseMap,
                                      double baseTps,
                                      LiveSample measurementAnchor,
                                      LiveSample holdAnchor,
                                      LiveSample end,
                                      double duration,
                                      BlendDurationCaptureConfig settings,
                                      int gearMin,
                                      int gearMax,
                                      int gearSamples,
                                      int badVss,
                                      int captureSamples) {
        double delta = end.get(ChannelRole.RPM)
                - measurementAnchor.get(ChannelRole.RPM);
        String trend = delta > 40.0 ? "RISING"
                : delta < -40.0 ? "FALLING" : "STABLE";
        return new BlendDurationAttempt(number, duration,
                baseRpm, baseMap, baseTps,
                holdAnchor.get(ChannelRole.TPS),
                measurementAnchor.get(ChannelRole.FALLBACK_MAP)
                        - measurementAnchor.get(ChannelRole.MAP),
                trend, settings, gearMin, gearMax,
                gearSamples > 0 && gearMin != gearMax,
                captureSamples > 0 && badVss > captureSamples / 10);
    }

    String gearText() {
        StringBuilder text = new StringBuilder(settings.gearText());
        if (gearMin != Integer.MAX_VALUE) {
            text.append(" | detected ");
            text.append(gearMin == gearMax ? Integer.toString(gearMin)
                    : gearMin + "-" + gearMax);
        } else {
            text.append(" | detected unavailable");
        }
        if (vssBad) text.append(" | VSS unreliable");
        return text.toString();
    }
}
