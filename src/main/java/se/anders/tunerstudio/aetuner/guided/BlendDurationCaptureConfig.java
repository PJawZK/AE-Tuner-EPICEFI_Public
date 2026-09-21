package se.anders.tunerstudio.aetuner.guided;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable configuration for one controlled Guided Blend Duration capture series.
 *
 * desiredTpsStep is presentation/coaching context only. Physical event validity
 * uses the broad natural-pedal usability bounds, while repeatability authority is
 * established after capture by BlendDurationComparabilityGroups.
 *
 * armedRpmBins is the operator-selected 1..4 set of actual Blend Duration table
 * bins eligible for this session. startRpm remains the immutable per-event bin
 * once an automatic RPM latch is made; before a latch it may be NaN in a
 * presentation projection. Legacy constructors arm only their single startRpm.
 */
final class BlendDurationCaptureConfig {
    static final int MAX_ARMED_RPM_BINS = 4;

    final double startRpm;
    final double desiredTpsStep;
    final int targetCount;
    final int manualGear;
    final boolean automaticGear;
    final double[] blendRpmBins;
    final double[] blendDurationValues;
    final double[] armedRpmBins;

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear) {
        this(startRpm, desiredTpsStep, targetCount, manualGear, automaticGear,
                new double[0], new double[0], single(startRpm));
    }

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear,
                               double[] blendRpmBins,
                               double[] blendDurationValues) {
        this(startRpm, desiredTpsStep, targetCount, manualGear, automaticGear,
                blendRpmBins, blendDurationValues, single(startRpm));
    }

    BlendDurationCaptureConfig(double startRpm, double desiredTpsStep,
                               int targetCount, int manualGear,
                               boolean automaticGear,
                               double[] blendRpmBins,
                               double[] blendDurationValues,
                               double[] armedRpmBins) {
        this.startRpm = startRpm;
        this.desiredTpsStep = desiredTpsStep;
        this.targetCount = targetCount;
        this.manualGear = manualGear;
        this.automaticGear = automaticGear;
        this.blendRpmBins = blendRpmBins == null ? new double[0] : blendRpmBins.clone();
        this.blendDurationValues = blendDurationValues == null
                ? new double[0] : blendDurationValues.clone();
        this.armedRpmBins = sanitizeArmed(armedRpmBins, startRpm);
    }

    BlendDurationCaptureConfig withStartRpm(double rpm) {
        return new BlendDurationCaptureConfig(rpm, desiredTpsStep, targetCount,
                manualGear, automaticGear, blendRpmBins, blendDurationValues,
                armedRpmBins);
    }

    double[] armedRpmBins() { return armedRpmBins.clone(); }
    int armedRpmBinCount() { return armedRpmBins.length; }

    String armedRpmText() {
        if (armedRpmBins.length == 0) return "none";
        StringBuilder out = new StringBuilder();
        for (double rpm : armedRpmBins) {
            if (out.length() > 0) out.append(" / ");
            out.append(String.format(Locale.US, "%.0f", rpm));
        }
        return out.toString();
    }

    boolean isArmedRpm(double rpm) {
        if (!Double.isFinite(rpm)) return false;
        for (double armed : armedRpmBins) {
            if (Math.abs(armed - rpm) <= 0.5) return true;
        }
        return false;
    }

    double usableStepLow() {
        return PedalPlateauDetector.MIN_USABLE_STEP;
    }

    double usableStepHigh() {
        return PedalPlateauDetector.MAX_USABLE_STEP;
    }

    boolean acceptsTpsStep(double step) {
        return Double.isFinite(step)
                && step >= usableStepLow() - 1.0e-9
                && step <= usableStepHigh() + 1.0e-9;
    }

    // Compatibility accessors for older presentation/tests. These are now broad
    // physical usability bounds, not a desired-step acceptance window.
    double targetStepLow() { return usableStepLow(); }
    double targetStepHigh() { return usableStepHigh(); }

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
        return PedalPlateauDetector.MIN_USABLE_STEP;
    }

    static double targetStepHigh(double desiredStep) {
        return PedalPlateauDetector.MAX_USABLE_STEP;
    }

    String gearText() {
        if (manualGear > 0) return "manual " + manualGear;
        return automaticGear ? "automatic detected" : "ignored";
    }

    private static double[] single(double rpm) {
        return Double.isFinite(rpm) ? new double[]{rpm} : new double[0];
    }

    private static double[] sanitizeArmed(double[] requested, double fallbackRpm) {
        List<Double> clean = new ArrayList<Double>();
        if (requested != null) {
            for (double rpm : requested) {
                if (!Double.isFinite(rpm)) continue;
                boolean duplicate = false;
                for (Double existing : clean) {
                    if (Math.abs(existing.doubleValue() - rpm) <= 0.5) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) clean.add(Double.valueOf(rpm));
            }
        }
        if (clean.isEmpty() && Double.isFinite(fallbackRpm)) {
            clean.add(Double.valueOf(fallbackRpm));
        }
        if (clean.size() > MAX_ARMED_RPM_BINS) {
            throw new IllegalArgumentException("Blend Duration supports 1-"
                    + MAX_ARMED_RPM_BINS + " armed RPM bins per session");
        }
        double[] result = new double[clean.size()];
        for (int i = 0; i < clean.size(); i++) result[i] = clean.get(i).doubleValue();
        return result;
    }
}
