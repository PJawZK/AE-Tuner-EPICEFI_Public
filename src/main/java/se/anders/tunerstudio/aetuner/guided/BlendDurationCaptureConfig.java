package se.anders.tunerstudio.aetuner.guided;

/** Immutable configuration for one controlled Guided Blend Duration capture series. */
final class BlendDurationCaptureConfig {
    static final double TPS_STEP_TOLERANCE = 10.0;

    final double startRpm;
    final double desiredTpsStep;
    final int targetCount;
    final int manualGear;
    final boolean automaticGear;
    final double[] blendRpmBins;
    final double[] blendDurationValues;

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear) {
        this(startRpm, desiredTpsStep, targetCount, manualGear, automaticGear,
                new double[0], new double[0]);
    }

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear,
                               double[] blendRpmBins,
                               double[] blendDurationValues) {
        this.startRpm = startRpm;
        this.desiredTpsStep = desiredTpsStep;
        this.targetCount = targetCount;
        this.manualGear = manualGear;
        this.automaticGear = automaticGear;
        this.blendRpmBins = blendRpmBins == null ? new double[0] : blendRpmBins.clone();
        this.blendDurationValues = blendDurationValues == null
                ? new double[0] : blendDurationValues.clone();
    }

    double targetStepLow() {
        return targetStepLow(desiredTpsStep);
    }

    double targetStepHigh() {
        return targetStepHigh(desiredTpsStep);
    }

    boolean acceptsTpsStep(double step) {
        return Double.isFinite(step)
                && step >= targetStepLow() - 1.0e-9
                && step <= targetStepHigh() + 1.0e-9;
    }

    boolean hasBlendCurve() {
        return blendRpmBins.length >= 1
                && blendRpmBins.length == blendDurationValues.length;
    }

    double blendDurationAt(double rpm) {
        if (!hasBlendCurve() || !Double.isFinite(rpm)) return Double.NaN;
        if (blendRpmBins.length == 1 || rpm <= blendRpmBins[0]) {
            return blendDurationValues[0];
        }
        int last = blendRpmBins.length - 1;
        if (rpm >= blendRpmBins[last]) return blendDurationValues[last];
        for (int i = 1; i < blendRpmBins.length; i++) {
            if (rpm <= blendRpmBins[i]) {
                double lowRpm = blendRpmBins[i - 1];
                double highRpm = blendRpmBins[i];
                double span = highRpm - lowRpm;
                if (!(span > 0.0)) return Double.NaN;
                double fraction = (rpm - lowRpm) / span;
                return blendDurationValues[i - 1]
                        + (blendDurationValues[i] - blendDurationValues[i - 1]) * fraction;
            }
        }
        return blendDurationValues[last];
    }

    static double targetStepLow(double desiredStep) {
        return Math.max(PedalPlateauDetector.MIN_USABLE_STEP,
                desiredStep - TPS_STEP_TOLERANCE);
    }

    static double targetStepHigh(double desiredStep) {
        return Math.min(PedalPlateauDetector.MAX_USABLE_STEP,
                desiredStep + TPS_STEP_TOLERANCE);
    }

    String gearText() {
        if (manualGear > 0) return "manual " + manualGear;
        return automaticGear ? "automatic detected" : "ignored";
    }
}
