package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Locale;

/** Method-owned post-Apply validation for Foundation 1 timing proposals. */
final class FoundationTimingValidation {
    private FoundationTimingValidation() { }

    static final class Baseline {
        final ProposalWritePlan plan;
        final EngagementPassiveCapture.TimingStatus before;

        Baseline(ProposalWritePlan plan, EngagementPassiveCapture.TimingStatus before) {
            this.plan = plan;
            this.before = before;
        }

        String instructions() {
            return "VALIDATION B — repeat the same passive TPS Movement / Timing method after Apply. "
                    + "Use comparable moderate openings across a useful pre-event operating RPM spread. "
                    + "KEEP requires the fresh operating-range-confirmed result to retain the applied timing pair.";
        }
    }

    static final class Result {
        final GuidedValidationVerdict verdict;
        final String text;

        Result(GuidedValidationVerdict verdict, String text) {
            this.verdict = verdict;
            this.text = text == null ? "" : text;
        }
    }

    static Baseline arm(AeProjectSnapshot snapshot, ProposalWritePlan plan) {
        if (snapshot == null || plan == null
                || !"engagement-detection-timing-pair".equals(plan.getRecipeId())) return null;
        EngagementPassiveCapture.TimingStatus before =
                EngagementPassiveCapture.timingStatus(snapshot);
        if (!before.applyReady) return null;
        return new Baseline(plan, before);
    }

    static Result compare(Baseline baseline, AeProjectSnapshot afterSnapshot) {
        if (baseline == null || afterSnapshot == null) {
            return new Result(GuidedValidationVerdict.INCONCLUSIVE,
                    "INCONCLUSIVE — fresh working-tune and timing evidence are required.");
        }
        return compareStatus(baseline, EngagementPassiveCapture.timingStatus(afterSnapshot),
                afterSnapshot.getEngagementDeltaWindowMs(),
                afterSnapshot.getEngagementSampleLengthSeconds());
    }

    static Result compareStatus(Baseline baseline,
                                EngagementPassiveCapture.TimingStatus after,
                                double currentDeltaMs,
                                double currentSampleSeconds) {
        if (baseline == null || after == null || !after.applyReady) {
            return new Result(GuidedValidationVerdict.INCONCLUSIVE,
                    "FOUNDATION 1 A/B VALIDATION\nVERDICT: INCONCLUSIVE\nFresh operating-range-confirmed timing evidence is not resolved yet.");
        }

        boolean restore = false;
        boolean inconclusive = false;
        StringBuilder out = new StringBuilder();
        out.append("FOUNDATION 1 METHOD-OWNED A/B VALIDATION\n")
                .append("Fresh timing result: Delta ").append(f0(after.deltaWindowMs))
                .append(" ms | Sample Length ").append(f0(after.sampleLengthMs))
                .append(" ms | pedal quality ").append(after.pedalQuality).append('\n');

        for (ProposalWritePlan.Change change : baseline.plan.getChanges()) {
            if (AeParameterNames.TPS_AE_DELTA_WINDOW_MS.equals(change.parameterName)) {
                if (Math.abs(currentDeltaMs - change.proposedValue) > 0.001) {
                    inconclusive = true;
                    out.append("  Delta Window: INCONCLUSIVE — fresh tune does not match applied value.\n");
                } else if (!after.deltaChangeNeeded
                        && Math.abs(after.deltaWindowMs - change.proposedValue) <= 0.001) {
                    out.append("  Delta Window: KEEP — fresh method retains applied value.\n");
                } else if (after.deltaChangeNeeded
                        && Math.abs(after.deltaWindowMs - change.expectedValue) <= 0.001) {
                    restore = true;
                    out.append("  Delta Window: RESTORE — fresh method resolves back to previous value.\n");
                } else {
                    inconclusive = true;
                    out.append("  Delta Window: INCONCLUSIVE — fresh method prefers a different third value.\n");
                }
            } else if (AeParameterNames.TPS_ACCEL_LOOKBACK.equals(change.parameterName)) {
                double appliedSeconds = change.proposedValue;
                double expectedSeconds = change.expectedValue;
                if (Math.abs(currentSampleSeconds - appliedSeconds) > 0.00001) {
                    inconclusive = true;
                    out.append("  Sample Length: INCONCLUSIVE — fresh tune does not match applied value.\n");
                } else if (!after.sampleChangeNeeded
                        && Math.abs(after.sampleLengthMs / 1000.0 - appliedSeconds) <= 0.00001) {
                    out.append("  Sample Length: KEEP — fresh method retains applied capacity.\n");
                } else if (after.sampleChangeNeeded
                        && Math.abs(after.sampleLengthMs / 1000.0 - expectedSeconds) <= 0.00001) {
                    restore = true;
                    out.append("  Sample Length: RESTORE — fresh method resolves back to previous value.\n");
                } else {
                    inconclusive = true;
                    out.append("  Sample Length: INCONCLUSIVE — fresh method prefers a different third value.\n");
                }
            } else {
                inconclusive = true;
                out.append("  ").append(change.displayLabel)
                        .append(": INCONCLUSIVE — target is outside Foundation 1 timing validation authority.\n");
            }
        }

        GuidedValidationVerdict verdict = restore ? GuidedValidationVerdict.RESTORE
                : (inconclusive ? GuidedValidationVerdict.INCONCLUSIVE : GuidedValidationVerdict.KEEP);
        out.append("\nVERDICT: ").append(verdict.name()).append('\n');
        if (verdict == GuidedValidationVerdict.KEEP) {
            out.append("The applied timing pair is retained by fresh physical evidence. KEEP records the result only; no Burn.");
        } else if (verdict == GuidedValidationVerdict.RESTORE) {
            out.append("Use verified Restore Previous Apply. No Burn.");
        } else {
            out.append("Collect another comparable independent passive set before deciding KEEP/RESTORE.");
        }
        return new Result(verdict, out.toString());
    }

    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a";
    }
}
