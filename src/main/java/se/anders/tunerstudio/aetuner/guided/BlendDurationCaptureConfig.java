package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Immutable configuration for one adaptive Guided Blend Duration capture series. */
final class BlendDurationCaptureConfig {
    final double startRpm;
    final double desiredTpsStep;
    final int targetCount;
    final int manualGear;
    final boolean automaticGear;

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear) {
        this.startRpm = startRpm;
        this.desiredTpsStep = desiredTpsStep;
        this.targetCount = targetCount;
        this.manualGear = manualGear;
        this.automaticGear = automaticGear;
    }

    String gearText() {
        if (manualGear > 0) return "manual " + manualGear;
        return automaticGear ? "automatic advisory" : "ignored";
    }
}
