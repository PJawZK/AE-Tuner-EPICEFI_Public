package se.anders.tunerstudio.aetuner.guided;

/**
 * User-requested TPS quiet-baseline recalibration lifecycle.
 *
 * Recalibration is intentionally coupled to the controlled Delta Window
 * lifecycle so evidence from different calibration revisions cannot be mixed.
 * Any temporary candidate is restored and verified before the old comparison
 * set is discarded. No ECU write is performed by this class itself.
 */
public final class EngagementQuietCalibrationControl {
    private EngagementQuietCalibrationControl() { }

    public static boolean canRecalibrate() {
        EngagementDeltaWindowSweepRuntime.Snapshot sweep =
                EngagementDeltaWindowSweepRuntime.snapshot();
        return sweep.phase != EngagementDeltaWindowSweepRuntime.Phase.MOVING
                && sweep.phase != EngagementDeltaWindowSweepRuntime.Phase.POST_STOP
                && sweep.phase != EngagementDeltaWindowSweepRuntime.Phase.ERROR;
    }

    public static Result recalibrate() {
        if (!canRecalibrate()) {
            EngagementDeltaWindowSweepRuntime.Snapshot sweep =
                    EngagementDeltaWindowSweepRuntime.snapshot();
            return Result.blocked("TPS quiet-baseline recalibration is unavailable during "
                    + sweep.phase + ". Finish the physical maneuver or recover the sweep first.");
        }

        EngagementDeltaWindowSweepRuntime.Config preserved =
                EngagementDeltaWindowSweepRuntime.pendingConfig();
        if (!EngagementDeltaWindowSweepRuntime.resetForLifecycle()) {
            return Result.blocked("TPS quiet-baseline recalibration blocked because the temporary "
                    + "Delta Window could not be restored and verified. Recover the sweep before continuing.");
        }
        EngagementDeltaWindowSweepRuntime.updatePendingConfig(preserved);
        FoundationTpsNoiseGate.beginCalibration();
        return Result.started("CALIBRATE TPS QUIET BASELINE — previous comparable sweep evidence "
                + "was cleared after verified baseline restore. Engine idling; pedal untouched until FROZEN.");
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        static Result started(String message) {
            return new Result(true, message);
        }

        static Result blocked(String message) {
            return new Result(false, message);
        }
    }
}
