package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdValidation;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;

/**
 * Lifecycle state for one method-owned post-Apply A/B validation.
 *
 * This class never writes the controller. GuidedCapturePanel keeps using the
 * existing ProposalApplyCoordinator for Apply/readback/Restore.
 */
final class GuidedProposalValidationSession {
    private enum Phase {
        IDLE,
        AWAITING_WORKING_TUNE_READ,
        READY_FOR_POST_CAPTURE,
        INCONCLUSIVE,
        KEEP_RECOMMENDED,
        RESTORE_RECOMMENDED,
        RESOLVED_KEEP,
        RESOLVED_RESTORE,
        RESOLVED_OPERATOR_RESTORE
    }

    private Phase phase = Phase.IDLE;
    private GuidedTuningRecipe recipe;
    private ProposalWritePlan plan;
    private FoundationThresholdValidation.Baseline thresholdBaseline;
    private FoundationTimingValidation.Baseline timingBaseline;
    private GuidedValidationVerdict lastVerdict;
    private String lastResult = "";
    private String baselineReport = "";
    private String baselineCsv = "";
    private String lastNote = "";
    private boolean evaluationPerformed;

    GuidedTuningRecipe recipe() { return recipe; }

    boolean unresolved() {
        return phase != Phase.IDLE
                && phase != Phase.RESOLVED_KEEP
                && phase != Phase.RESOLVED_RESTORE
                && phase != Phase.RESOLVED_OPERATOR_RESTORE;
    }

    boolean hasArtifactFor(GuidedTuningRecipe candidate) {
        return recipe != null && recipe == candidate && phase != Phase.IDLE;
    }

    boolean canEvaluate(GuidedTuningRecipe candidate) {
        return recipe == candidate
                && (phase == Phase.READY_FOR_POST_CAPTURE || phase == Phase.INCONCLUSIVE);
    }

    boolean canContinueCapture(GuidedTuningRecipe candidate) {
        return canEvaluate(candidate);
    }

    boolean canAcceptKeep() {
        return phase == Phase.KEEP_RECOMMENDED
                && lastVerdict == GuidedValidationVerdict.KEEP;
    }

    String restoreActionLabel() {
        if (phase == Phase.RESTORE_RECOMMENDED && lastVerdict == GuidedValidationVerdict.RESTORE) {
            return "Restore Previous Apply";
        }
        if (unresolved()) return "Abort A/B Validation & Restore";
        return "Restore Previous Apply";
    }

    void armAfterVerifiedApply(GuidedTuningRecipe selected,
                               AeProjectSnapshot beforeSnapshot,
                               List<LiveSample> baselineEvidence,
                               ProposalWritePlan appliedPlan,
                               String beforeReport,
                               String beforeCsv) {
        reset();
        if (selected == null || beforeSnapshot == null || appliedPlan == null) return;

        if (selected == GuidedTuningRecipe.FOUNDATION_THRESHOLD) {
            thresholdBaseline = FoundationThresholdValidation.arm(
                    beforeSnapshot, baselineEvidence, appliedPlan);
            if (thresholdBaseline == null) return;
        } else if (selected == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            timingBaseline = FoundationTimingValidation.arm(beforeSnapshot, appliedPlan);
            if (timingBaseline == null) return;
        } else {
            return;
        }

        recipe = selected;
        plan = appliedPlan;
        baselineReport = beforeReport == null ? "" : beforeReport;
        baselineCsv = beforeCsv == null ? "" : beforeCsv;
        phase = Phase.AWAITING_WORKING_TUNE_READ;
        lastNote = "APPLIED / VERIFIED — A/B validation armed. NEXT: Read Working Tune. Do not Restore unless you intend to abort validation.";
    }

    boolean noteWorkingTuneRead(AeProjectSnapshot snapshot) {
        if (phase != Phase.AWAITING_WORKING_TUNE_READ || snapshot == null || plan == null) return false;
        if (!plan.getConfigurationName().equals(snapshot.getConfigurationName())) {
            lastNote = "A/B validation is waiting: fresh working tune belongs to a different controller configuration.";
            return false;
        }
        if (!planMatchesSnapshot(snapshot, plan)) {
            lastNote = "A/B validation is waiting: fresh working tune does not contain every verified applied value.";
            return false;
        }
        if (thresholdBaseline != null) {
            String mismatch = FoundationThresholdValidation.contextMismatch(thresholdBaseline, snapshot);
            if (mismatch.length() > 0) {
                lastNote = "A/B validation blocked before B capture: " + mismatch
                        + ". The fresh working tune changed outside the reviewed Foundation 2 Apply; establish a fresh A baseline.";
                return false;
            }
        }
        phase = Phase.READY_FOR_POST_CAPTURE;
        lastNote = "Fresh working tune matches the verified Apply and the complete relevant A context. Baseline A is frozen; start a clean Validation B capture.";
        return true;
    }

    void evaluateAfter(AeProjectSnapshot afterSnapshot, List<LiveSample> afterEvidence) {
        if (!canEvaluate(recipe)) return;
        evaluationPerformed = true;
        if (recipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD) {
            FoundationThresholdValidation.Result result =
                    FoundationThresholdValidation.compare(thresholdBaseline, afterSnapshot, afterEvidence);
            lastVerdict = result.verdict;
            lastResult = result.text;
        } else if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            FoundationTimingValidation.Result result =
                    FoundationTimingValidation.compare(timingBaseline, afterSnapshot);
            lastVerdict = result.verdict;
            lastResult = result.text;
        } else {
            lastVerdict = GuidedValidationVerdict.INCONCLUSIVE;
            lastResult = "INCONCLUSIVE — this method has no post-Apply validation evaluator.";
        }

        phase = lastVerdict == GuidedValidationVerdict.KEEP
                ? Phase.KEEP_RECOMMENDED
                : (lastVerdict == GuidedValidationVerdict.RESTORE
                    ? Phase.RESTORE_RECOMMENDED : Phase.INCONCLUSIVE);
    }

    void acceptKeep() {
        if (!canAcceptKeep()) return;
        phase = Phase.RESOLVED_KEEP;
        lastNote = "KEEP ACCEPTED — the tested incremental working-tune step is retained in RAM. This does not declare the region converged; any new proposal generated from Validation B is a separate next incremental proposal and has not been applied. No controller write and no Burn occurred.";
    }

    void markRestored(ProposalWritePlan restoredPlan) {
        if (!unresolved() || restoredPlan == null || plan == null) return;
        if (!restoredPlan.getRecipeId().equals(plan.getRecipeId())) return;
        if (phase == Phase.RESTORE_RECOMMENDED
                && lastVerdict == GuidedValidationVerdict.RESTORE
                && evaluationPerformed) {
            phase = Phase.RESOLVED_RESTORE;
            lastNote = "RESTORED / VERIFIED — algorithmic A/B RESTORE verdict resolved through the existing verified restore path. No Burn.";
            return;
        }
        phase = Phase.RESOLVED_OPERATOR_RESTORE;
        lastNote = evaluationPerformed
                ? "OPERATOR RESTORE / VERIFIED — the applied value was restored after validation, but this was not an algorithmic RESTORE verdict. No Burn."
                : "VALIDATION ABORTED / RESTORED — the applied value was restored before the A/B evaluator ran. No method verdict was produced and no Burn occurred.";
    }

    void onGuidedReset() {
        if (phase == Phase.RESOLVED_KEEP
                || phase == Phase.RESOLVED_RESTORE
                || phase == Phase.RESOLVED_OPERATOR_RESTORE) {
            reset();
        } else if (phase == Phase.INCONCLUSIVE) {
            phase = Phase.READY_FOR_POST_CAPTURE;
            lastResult = "";
            lastVerdict = null;
            evaluationPerformed = false;
            lastNote = "Validation B evidence reset; baseline A and verified controller context are retained. Start a fresh B capture in only the changed target region(s).";
        }
    }

    void reset() {
        phase = Phase.IDLE;
        recipe = null;
        plan = null;
        thresholdBaseline = null;
        timingBaseline = null;
        lastVerdict = null;
        lastResult = "";
        baselineReport = "";
        baselineCsv = "";
        lastNote = "";
        evaluationPerformed = false;
    }

    String baselineReportFor(GuidedTuningRecipe candidate) {
        return hasArtifactFor(candidate) ? baselineReport : "";
    }

    String baselineCsvFor(GuidedTuningRecipe candidate) {
        return hasArtifactFor(candidate) ? baselineCsv : "";
    }

    String reportFor(GuidedTuningRecipe candidate) {
        if (!hasArtifactFor(candidate)) return "";
        StringBuilder out = new StringBuilder();
        out.append("AE Tuner Guided method-owned A/B validation\n")
                .append("Method: ").append(recipe.displayName).append('\n')
                .append("State: ").append(phase.name()).append('\n')
                .append("Evaluation performed: ").append(evaluationPerformed ? "YES" : "NO").append('\n')
                .append("Method verdict: ").append(lastVerdict == null ? "NOT_EVALUATED" : lastVerdict.name()).append('\n')
                .append("Resolution: ").append(resolutionText()).append('\n')
                .append("Controller write boundary: existing guarded Apply/Restore only; validation performs no writes and no Burn.\n\n")
                .append(plan.reviewText("APPLIED PLAN")).append("\n\n")
                .append("VALIDATION STATUS\n").append(statusText());
        return out.toString();
    }

    String statusText() {
        if (phase == Phase.IDLE) return "No method-owned A/B validation is armed.";
        StringBuilder out = new StringBuilder();
        out.append(recipe.displayName).append(" A/B validation — ").append(phase.name()).append('\n');
        if (lastNote.length() > 0) out.append(lastNote).append('\n');
        if (phase == Phase.AWAITING_WORKING_TUNE_READ) {
            out.append("NEXT: Read Working Tune. Validation B is blocked until every applied value and the complete relevant controller context are verified. Restore now only if you intend to abort A/B validation.");
        } else if (phase == Phase.READY_FOR_POST_CAPTURE) {
            out.append(instructions());
        } else if (phase == Phase.INCONCLUSIVE) {
            out.append(lastResult).append("\n\nNEXT: Follow the specific local validation reason above; do not Apply another proposal.");
        } else if (phase == Phase.KEEP_RECOMMENDED) {
            out.append(lastResult).append("\n\nNEXT: Accept Validation KEEP to retain the applied RAM value, or Restore if you reject the result. No Burn.");
        } else if (phase == Phase.RESTORE_RECOMMENDED) {
            out.append(lastResult).append("\n\nNEXT: Restore Previous Apply through the existing verified restore path. No Burn.");
        } else {
            if (lastResult.length() > 0) out.append(lastResult).append('\n');
            out.append("Validation resolved. ").append(lastNote);
        }
        return out.toString().trim();
    }

    private String resolutionText() {
        if (phase == Phase.RESOLVED_KEEP) return "KEEP_ACCEPTED";
        if (phase == Phase.RESOLVED_RESTORE) return "ALGORITHM_RESTORE";
        if (phase == Phase.RESOLVED_OPERATOR_RESTORE) return "OPERATOR_ABORT_OR_OVERRIDE_RESTORE";
        return "UNRESOLVED";
    }

    private String instructions() {
        if (thresholdBaseline != null) return thresholdBaseline.instructions();
        if (timingBaseline != null) return timingBaseline.instructions();
        return "Repeat the same method after Apply.";
    }

    private static boolean planMatchesSnapshot(AeProjectSnapshot snapshot,
                                               ProposalWritePlan plan) {
        double[] thresholds = snapshot.getThresholdValues();
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            double actual;
            if (AeParameterNames.TPS_AE_THRESHOLD_VALUES.equals(change.parameterName)
                    && change.kind == ProposalWritePlan.Kind.ARRAY_CELL
                    && change.flatIndex >= 0 && change.flatIndex < thresholds.length) {
                actual = thresholds[change.flatIndex];
            } else if (AeParameterNames.TPS_AE_DELTA_WINDOW_MS.equals(change.parameterName)) {
                actual = snapshot.getEngagementDeltaWindowMs();
            } else if (AeParameterNames.TPS_ACCEL_LOOKBACK.equals(change.parameterName)) {
                actual = snapshot.getEngagementSampleLengthSeconds();
            } else {
                return false;
            }
            double tolerance = Math.max(0.00001, Math.abs(change.proposedValue) * 0.0001);
            if (!Double.isFinite(actual)
                    || Math.abs(actual - change.proposedValue) > tolerance) return false;
        }
        return true;
    }
}
