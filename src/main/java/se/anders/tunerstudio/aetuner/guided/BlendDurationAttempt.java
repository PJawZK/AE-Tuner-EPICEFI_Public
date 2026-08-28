package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Immutable retained measurement from one valid controlled Guided opening. */
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

    // The session latch remains immutable. eventDetectedGear is only populated
    // when one event has sustained, plausible evidence of a different gear and
    // is then used to keep that event out of the latched-gear comparison group.
    final int eventDetectedGear;
    final boolean eventGearMismatch;
    final String eventGearEvidenceText;

    // Legacy-shaped fields remain so older regressions/helpers keep compiling.
    // A latched detection is represented by gearMin == gearMax == detectedGear.
    final int gearMin;
    final int gearMax;
    final boolean gearOscillation;
    final boolean vssBad;
    final int detectedGear;
    final boolean detectedGearLatched;
    boolean warning;

    BlendDurationAttempt(int number, double duration, double baseRpm,
                         double baseMap, double baseTps, double heldTps,
                         double gap, String trend,
                         BlendDurationCaptureConfig settings,
                         int gearMin, int gearMax,
                         boolean gearOscillation, boolean vssBad) {
        this(number, duration, baseRpm, baseMap, baseTps, heldTps,
                gap, trend, settings,
                gearMin != Integer.MAX_VALUE && gearMin == gearMax ? gearMin : 0,
                gearMin != Integer.MAX_VALUE && gearMin == gearMax,
                gearOscillation, vssBad, 0, false, "");
    }

    private BlendDurationAttempt(int number, double duration, double baseRpm,
                                 double baseMap, double baseTps, double heldTps,
                                 double gap, String trend,
                                 BlendDurationCaptureConfig settings,
                                 int detectedGear, boolean detectedGearLatched,
                                 boolean gearOscillation, boolean vssBad,
                                 int eventDetectedGear,
                                 boolean eventGearMismatch,
                                 String eventGearEvidenceText) {
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
        this.detectedGear = detectedGear;
        this.detectedGearLatched = detectedGearLatched;
        this.gearMin = detectedGearLatched ? detectedGear : Integer.MAX_VALUE;
        this.gearMax = detectedGearLatched ? detectedGear : Integer.MIN_VALUE;
        this.gearOscillation = gearOscillation;
        this.vssBad = vssBad;
        this.eventDetectedGear = eventDetectedGear;
        this.eventGearMismatch = eventGearMismatch;
        this.eventGearEvidenceText = eventGearEvidenceText == null
                ? "" : eventGearEvidenceText;
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
                                      int detectedGear,
                                      int badVss,
                                      int vssSamples,
                                      int captureSamples) {
        return build(number, baseRpm, baseMap, baseTps,
                measurementAnchor, holdAnchor, end, duration, settings,
                detectedGear, badVss, vssSamples, captureSamples,
                GuidedEventGearEvidence.Result.unavailable(detectedGear));
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
                                      int detectedGear,
                                      int badVss,
                                      int vssSamples,
                                      int captureSamples,
                                      GuidedEventGearEvidence.Result eventGear) {
        double delta = end.get(ChannelRole.RPM)
                - measurementAnchor.get(ChannelRole.RPM);
        String trend = delta > 40.0 ? "RISING"
                : delta < -40.0 ? "FALLING" : "STABLE";
        boolean gearLatched = detectedGear >= 1 && detectedGear <= 8;
        boolean automaticReliability = settings != null && settings.automaticGear;
        boolean gearWarning = automaticReliability && !gearLatched;
        boolean vssWarning = automaticReliability && vssSamples > 0
                && badVss > vssSamples / 10;
        GuidedEventGearEvidence.Result evidence = eventGear == null
                ? GuidedEventGearEvidence.Result.unavailable(detectedGear)
                : eventGear;
        int eventGearValue = evidence.mismatch ? evidence.dominantGear : 0;
        return new BlendDurationAttempt(number, duration,
                baseRpm, baseMap, baseTps,
                holdAnchor.get(ChannelRole.TPS),
                measurementAnchor.get(ChannelRole.FALLBACK_MAP)
                        - measurementAnchor.get(ChannelRole.MAP),
                trend, settings, detectedGear, gearLatched,
                gearWarning, vssWarning,
                eventGearValue, evidence.mismatch, evidence.text());
    }

    boolean gearReliabilityWarning() {
        return settings != null && settings.automaticGear
                && (gearOscillation || vssBad);
    }

    int comparisonGear() {
        if (settings == null || !settings.automaticGear) return 0;
        if (eventGearMismatch && eventDetectedGear >= 1 && eventDetectedGear <= 8) {
            return eventDetectedGear;
        }
        return detectedGearLatched ? detectedGear : 0;
    }

    String gearText() {
        StringBuilder text = new StringBuilder();
        if (settings != null && settings.manualGear > 0) {
            text.append("manual ").append(settings.manualGear);
            if (detectedGearLatched) {
                text.append(" | ECU detected ").append(detectedGear);
                if (detectedGear != settings.manualGear) {
                    text.append(" (informational mismatch)");
                }
            } else {
                text.append(" | ECU detected unavailable");
            }
            return text.toString();
        }
        if (settings != null && settings.automaticGear) {
            text.append("automatic");
            if (detectedGearLatched) {
                text.append(" | session latched ").append(detectedGear);
            } else {
                text.append(" | session gear not latched");
            }
            if (eventGearEvidenceText.length() > 0) {
                text.append(" | ").append(eventGearEvidenceText);
            }
            if (eventGearMismatch) {
                text.append(" | excluded from session-gear ")
                        .append(detectedGear)
                        .append(" comparability; session latch unchanged");
            }
            return text.toString();
        }
        text.append("ignored");
        if (detectedGearLatched) {
            text.append(" | ECU detected ").append(detectedGear).append(" (informational)");
        }
        return text.toString();
    }
}
