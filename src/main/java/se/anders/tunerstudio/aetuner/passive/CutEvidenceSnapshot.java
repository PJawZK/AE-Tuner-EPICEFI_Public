package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/** Immutable separation of actual cut outputs from diagnostic reason codes. */
final class CutEvidenceSnapshot {
    final long actualSparkCutSamples;
    final long ignitionCutReasonSamples;
    final long actualFuelCutSamples;
    final long fuelCutReasonSamples;
    final long guardedIgnitionReasonSamples;
    final long guardedFuelReasonSamples;

    final boolean runningActualSparkCut;
    final boolean runningIgnitionCutReason;
    final boolean runningActualFuelCut;
    final boolean runningFuelCutReason;

    CutEvidenceSnapshot(long actualSparkCutSamples,
                        long ignitionCutReasonSamples,
                        long actualFuelCutSamples,
                        long fuelCutReasonSamples,
                        long guardedIgnitionReasonSamples,
                        long guardedFuelReasonSamples,
                        boolean runningActualSparkCut,
                        boolean runningIgnitionCutReason,
                        boolean runningActualFuelCut,
                        boolean runningFuelCutReason) {
        this.actualSparkCutSamples = actualSparkCutSamples;
        this.ignitionCutReasonSamples = ignitionCutReasonSamples;
        this.actualFuelCutSamples = actualFuelCutSamples;
        this.fuelCutReasonSamples = fuelCutReasonSamples;
        this.guardedIgnitionReasonSamples = guardedIgnitionReasonSamples;
        this.guardedFuelReasonSamples = guardedFuelReasonSamples;
        this.runningActualSparkCut = runningActualSparkCut;
        this.runningIgnitionCutReason = runningIgnitionCutReason;
        this.runningActualFuelCut = runningActualFuelCut;
        this.runningFuelCutReason = runningFuelCutReason;
    }
}
