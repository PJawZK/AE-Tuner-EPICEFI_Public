package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsDraft;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.guided.GuidedWorkingTuneSurfaceCache;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Bounded recommendation layer for promoted TPS AE, Wall Wetting and Instant
 * Fuel subtasks.
 *
 * The original complete-baseline implementation binned instantaneous samples
 * whenever any transient-fuel source happened to be active. That made it
 * possible for TPS, Wall, Instant or MAP-Predict response to be attributed to
 * the wrong surface. This hardening pass instead consumes method-owned
 * TransientEvidenceEvent windows: conditions are latched at the owning event
 * onset and lambda is evaluated in a later response window.
 *
 * Completed recommendation results are memoized by Working Tune object and
 * retained-evidence revision. Swing Review/Proposal/Copy surfaces may therefore
 * read the same immutable result repeatedly without rebuilding event windows and
 * table-bin statistics every 150 ms.
 *
 * Every proposed value is still revalidated by GuidedTaskSettingsDraft and the
 * resulting ProposalWritePlan remains the only output authority. Capture never
 * writes and no Burn path exists here.
 */
final class BaselineSurfaceRecommendation {
    static final class Result {
        final String reviewText;
        final ProposalWritePlan plan;
        Result(String reviewText, ProposalWritePlan plan) {
            this.reviewText = reviewText == null ? "" : reviewText;
            this.plan = plan;
        }
    }

    private static final class CacheEntry {
        final AeProjectSnapshot snapshot;
        final int evidenceSize;
        final long lastNano;
        final Result result;

        CacheEntry(AeProjectSnapshot snapshot, int evidenceSize,
                   long lastNano, Result result) {
            this.snapshot = snapshot;
            this.evidenceSize = evidenceSize;
            this.lastNano = lastNano;
            this.result = result;
        }
    }

    private static final double ERROR_DEADBAND = 0.015;
    private static final int MIN_BIN_EVENTS = 3;
    private static final CacheEntry[] CACHE =
            new CacheEntry[GuidedTuningRecipe.values().length];
    private static long evaluationCount;

    private BaselineSurfaceRecommendation() { }

    static synchronized Result evaluate(GuidedTuningRecipe task,
                                        AeProjectSnapshot snapshot,
                                        List<LiveSample> evidence) {
        if (task == null) return evaluateFresh(null, snapshot, evidence);
        int size = evidence == null ? 0 : evidence.size();
        long lastNano = size <= 0 || evidence.get(size - 1) == null
                ? Long.MIN_VALUE : evidence.get(size - 1).getNanoTime();
        CacheEntry cached = CACHE[task.ordinal()];
        if (cached != null && cached.snapshot == snapshot
                && cached.evidenceSize == size && cached.lastNano == lastNano) {
            return cached.result;
        }
        Result result = evaluateFresh(task, snapshot, evidence);
        CACHE[task.ordinal()] = new CacheEntry(snapshot, size, lastNano, result);
        evaluationCount++;
        return result;
    }

    static synchronized void clearCache() {
        for (int i = 0; i < CACHE.length; i++) CACHE[i] = null;
    }

    static synchronized long evaluationCountForTest() { return evaluationCount; }

    private static Result evaluateFresh(GuidedTuningRecipe task,
                                        AeProjectSnapshot snapshot,
                                        List<LiveSample> evidence) {
        if (snapshot == null) return unavailable(task, "Read Working Tune first.");
        GuidedTaskSettingsDraft draft = GuidedWorkingTuneSurfaceCache.ensureBaseline(task, snapshot);
        if (draft == null) {
            return unavailable(task, GuidedWorkingTuneSurfaceCache.statusFor(task));
        }
        if (evidence == null || evidence.isEmpty()) {
            return unavailable(task, "No retained evidence is available yet. "
                    + GuidedWorkingTuneSurfaceCache.statusFor(task));
        }

        StringBuilder detail = new StringBuilder();
        int acceptedEvents = 0;

        if (task == GuidedTuningRecipe.TPS_AE_COMPENSATION) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.TPS_AE, evidence);
            acceptedEvents = events.size();
            applyCurve(draft, events, AeParameterNames.TPS_AE_RPM_CORRECTION_BINS,
                    AeParameterNames.TPS_AE_RPM_CORRECTION_VALUES,
                    ChannelRole.RPM, 0.35, detail, "RPM correction");
            applyCurve(draft, events, AeParameterNames.AE_CLT_CORR_BINS,
                    AeParameterNames.AE_CLT_CORR_VALUES,
                    ChannelRole.COOLANT, 0.35, detail, "AE-vs-CLT correction");
            applyTable(draft, events,
                    AeParameterNames.TPS_AE_SCALE_CLT_BINS,
                    AeParameterNames.TPS_AE_SCALE_TPS_BINS,
                    AeParameterNames.TPS_AE_SCALE_TABLE,
                    ChannelRole.COOLANT, ChannelRole.TPS,
                    0.20, detail, "TPS-vs-CLT scale");
            appendOverlapSummary(detail, events);
        } else if (task == GuidedTuningRecipe.TPS_AE_COMPLETION) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.TPS_AE, evidence);
            acceptedEvents = events.size();
            applyCompletionEvidenceOnly(draft, evidence, events, detail);
        } else if (task == GuidedTuningRecipe.WALL_WETTING_ADVANCED) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.WALL, evidence);
            acceptedEvents = events.size();
            applyCurve(draft, events, AeParameterNames.WALL_CLT_BINS,
                    AeParameterNames.WALL_BETA_CLT_VALUES,
                    ChannelRole.COOLANT, 0.35, detail, "Beta-vs-CLT");
            applyTable(draft, events,
                    AeParameterNames.WALL_RPM_BINS, AeParameterNames.WALL_MAP_BINS,
                    AeParameterNames.WALL_BETA_TABLE,
                    ChannelRole.RPM, ChannelRole.MAP,
                    0.25, detail, "Beta RPM/MAP table");
            detail.append("Tau-vs-CLT and Tau RPM/MAP values remain directly review/apply-capable. Automatic Tau movement is withheld until measured lambda transport delay is aligned with immediate wall-correction decay.\n");
            appendOverlapSummary(detail, events);
        } else if (task == GuidedTuningRecipe.INSTANT_FUEL_SETUP) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.INSTANT, evidence);
            acceptedEvents = events.size();
            applyInstantSetup(draft, snapshot, evidence, detail);
            appendOverlapSummary(detail, events);
        } else if (task == GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.INSTANT, evidence);
            acceptedEvents = events.size();
            applyCurve(draft, events,
                    AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_BINS,
                    AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_MULTIPLIER,
                    ChannelRole.DELTA_TPS, 0.70, detail, "Delta-TPS pulse strength");
            appendOverlapSummary(detail, events);
        } else if (task == GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS) {
            List<TransientEvidenceEvent.Event> events = TransientEvidenceEvent.acceptedForTask(
                    TransientEvidenceEvent.Owner.INSTANT, evidence);
            acceptedEvents = events.size();
            applyCurve(draft, events, AeParameterNames.TPS_AE_INSTANT_RPM_BINS,
                    AeParameterNames.TPS_AE_INSTANT_RPM_MULTIPLIER,
                    ChannelRole.RPM, 0.20, detail, "RPM multiplier");
            applyCurve(draft, events, AeParameterNames.TPS_AE_INSTANT_TPS_BINS,
                    AeParameterNames.TPS_AE_INSTANT_TPS_MULTIPLIER,
                    ChannelRole.TPS, 0.20, detail, "TPS multiplier");
            applyCurve(draft, events, AeParameterNames.TPS_AE_INSTANT_MAP_BINS,
                    AeParameterNames.TPS_AE_INSTANT_MAP_MULTIPLIER,
                    ChannelRole.MAP, 0.20, detail, "MAP multiplier");
            applyCurve(draft, events, AeParameterNames.TPS_AE_INSTANT_CLT_BINS,
                    AeParameterNames.TPS_AE_INSTANT_CLT_MULTIPLIER,
                    ChannelRole.COOLANT, 0.20, detail, "CLT multiplier");
            appendOverlapSummary(detail, events);
        } else {
            return unavailable(task, "This task is evidence-only in the complete baseline.");
        }

        ProposalWritePlan plan;
        try {
            plan = draft.buildPlan();
        } catch (RuntimeException ex) {
            return unavailable(task, "Hardened event evidence produced no safe plan: " + ex.getMessage());
        }

        StringBuilder review = new StringBuilder();
        review.append(task.displayName).append(" — EVENT-ALIGNED BASELINE REVIEW\n")
                .append(GuidedWorkingTuneSurfaceCache.statusFor(task)).append('\n')
                .append("Retained clean samples: ").append(cleanCount(evidence)).append('\n')
                .append("Method-owned accepted events: ").append(acceptedEvents).append('\n')
                .append(detail);
        if (plan == null) {
            review.append("Decision: KEEP/WITHHOLD automatic movement for this capture. Every validated setting in this task remains available through Edit/Review Task Settings.\n");
        } else {
            review.append("Decision: PROPOSE ").append(plan.changeCount())
                    .append(" event-backed value change(s). Review remains explicit; capture never writes; no Burn.\n");
        }
        review.append("Maturity: event ownership and delayed response windows are now production hardening boundaries; RPM/load-specific measured exhaust transport remains a later vehicle refinement.");
        return new Result(review.toString(), plan);
    }

    private static void applyCurve(GuidedTaskSettingsDraft draft,
                                   List<TransientEvidenceEvent.Event> events,
                                   String axisName, String valueName,
                                   ChannelRole xRole, double authority,
                                   StringBuilder detail, String label) {
        List<GuidedTaskSettingsDraft.Entry> axes = sorted(draft.entriesForController(axisName));
        List<GuidedTaskSettingsDraft.Entry> values = sorted(draft.entriesForController(valueName));
        if (axes.isEmpty() || values.size() != axes.size()) {
            detail.append(label).append(": baseline unavailable.\n");
            return;
        }
        double[] sum = new double[axes.size()];
        int[] count = new int[axes.size()];
        for (TransientEvidenceEvent.Event event : events) {
            double x = event.value(xRole);
            if (!Double.isFinite(x) || !Double.isFinite(event.earlyError)) continue;
            int bin = nearest(axes, x);
            sum[bin] += event.earlyError;
            count[bin]++;
        }
        int changed = 0;
        for (int i = 0; i < values.size(); i++) {
            if (count[i] < MIN_BIN_EVENTS) continue;
            double error = sum[i] / count[i];
            if (Math.abs(error) <= ERROR_DEADBAND) continue;
            double original = values.get(i).getOriginalValue();
            if (!Double.isFinite(original) || Math.abs(original) < 0.000001) continue;
            double factor = 1.0 + clamp(error, -0.15, 0.15) * authority;
            double proposed = quantize(values.get(i), original * factor);
            if (set(draft, values.get(i), proposed)) changed++;
        }
        detail.append(label).append(": ").append(changed)
                .append(" bin(s) proposed; minimum ").append(MIN_BIN_EVENTS)
                .append(" method-owned events/bin; distributed authority ")
                .append(fmt(authority)).append(".\n");
    }

    private static void applyTable(GuidedTaskSettingsDraft draft,
                                   List<TransientEvidenceEvent.Event> events,
                                   String rowAxisName, String colAxisName,
                                   String tableName,
                                   ChannelRole rowRole, ChannelRole colRole,
                                   double authority, StringBuilder detail,
                                   String label) {
        List<GuidedTaskSettingsDraft.Entry> rows = sorted(draft.entriesForController(rowAxisName));
        List<GuidedTaskSettingsDraft.Entry> cols = sorted(draft.entriesForController(colAxisName));
        List<GuidedTaskSettingsDraft.Entry> cells = sorted(draft.entriesForController(tableName));
        if (rows.isEmpty() || cols.isEmpty() || cells.size() != rows.size() * cols.size()) {
            detail.append(label).append(": complete 2D baseline unavailable.\n");
            return;
        }
        double[] sum = new double[cells.size()];
        int[] count = new int[cells.size()];
        for (TransientEvidenceEvent.Event event : events) {
            double rv = event.value(rowRole);
            double cv = event.value(colRole);
            if (!Double.isFinite(rv) || !Double.isFinite(cv)
                    || !Double.isFinite(event.earlyError)) continue;
            int r = nearest(rows, rv);
            int c = nearest(cols, cv);
            int flat = r * cols.size() + c;
            sum[flat] += event.earlyError;
            count[flat]++;
        }
        int changed = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (count[i] < MIN_BIN_EVENTS) continue;
            double error = sum[i] / count[i];
            if (Math.abs(error) <= ERROR_DEADBAND) continue;
            double original = cells.get(i).getOriginalValue();
            if (!Double.isFinite(original) || Math.abs(original) < 0.000001) continue;
            double proposed = quantize(cells.get(i),
                    original * (1.0 + clamp(error, -0.15, 0.15) * authority));
            if (set(draft, cells.get(i), proposed)) changed++;
        }
        detail.append(label).append(": ").append(changed)
                .append(" covered cell(s) proposed from event-onset operating conditions; uncovered cells stay at the frozen Working Tune value.\n");
    }

    private static void applyCompletionEvidenceOnly(GuidedTaskSettingsDraft draft,
                                                     List<LiveSample> evidence,
                                                     List<TransientEvidenceEvent.Event> events,
                                                     StringBuilder detail) {
        GuidedTaskSettingsDraft.Entry inhibit = first(draft,
                AeParameterNames.NO_FUEL_TRIM_AFTER_ACCEL_TIME);
        List<Double> recoveries = lambdaRecoveryDurations(evidence);
        if (inhibit == null) {
            detail.append("Closed-loop inhibit baseline unavailable.\n");
            return;
        }
        if (recoveries.isEmpty()) {
            detail.append("Closed-loop handoff: no complete lambda-recovery timing was measurable.\n");
        } else {
            Collections.sort(recoveries);
            double p75 = recoveries.get((int)Math.floor((recoveries.size() - 1) * 0.75));
            detail.append("Observed AE-end to lambda-recovery 75th percentile: ")
                    .append(fmt(p75)).append(" s across ").append(recoveries.size())
                    .append(" event(s).\n");
        }
        detail.append("Automatic noFuelTrimAfterAccelTime movement is WITHHELD: lambda recovery does not prove that EGO/closed-loop trim actually re-entered. The setting remains directly review/apply-capable until an authoritative EGO/trim-active transition channel is captured.\n")
                .append("Burn Skip and TPS-AE-resets-EGO likewise remain directly review/apply-capable; no automatic completion-control movement is produced from lambda alone.\n")
                .append("Method-owned TPS AE events available for review: ")
                .append(events.size()).append(".\n");
    }

    private static void applyInstantSetup(GuidedTaskSettingsDraft draft,
                                          AeProjectSnapshot snapshot,
                                          List<LiveSample> evidence,
                                          StringBuilder detail) {
        GuidedTaskSettingsDraft.Entry enable = first(draft, AeParameterNames.TPS_ACCEL_EXTRA_SHOT);
        GuidedTaskSettingsDraft.Entry timer = first(draft, AeParameterNames.TPS_EXTRA_SHOT_TIMER);
        if (enable == null || timer == null) {
            detail.append("Instant Setup baseline incomplete.\n");
            return;
        }

        detail.append("Enable state: retained. Automatic enabling is prohibited; an OFF Instant method is unavailable to capture and enabling remains an explicit Task Settings decision.\n");

        InstantFuelRecommendation.Result pulse = InstantFuelRecommendation.evaluate(snapshot, evidence);
        detail.append(pulse.reviewText).append('\n');
        if (pulse.plan != null) {
            for (ProposalWritePlan.Change change : pulse.plan.getChanges()) {
                List<GuidedTaskSettingsDraft.Entry> entries =
                        draft.entriesForController(change.parameterName);
                if (!entries.isEmpty()) set(draft, entries.get(0), change.proposedValue);
            }
        }

        int blockedLean = countBlockedLeanReapplies(evidence);
        if (blockedLean >= 2 && timer.getOriginalValue() > 0.0) {
            set(draft, timer, quantize(timer, Math.max(0.0,
                    timer.getOriginalValue() - 1.0)));
            detail.append("Inhibit cycles: ").append(blockedLean)
                    .append(" lean re-apply event(s) occurred without a new pulse-counter edge; propose one-cycle reduction.\n");
        } else {
            detail.append("Inhibit cycles: retained; no repeated blocked-lean re-apply pattern proved.\n");
        }
    }

    private static int countBlockedLeanReapplies(List<LiveSample> evidence) {
        int result = 0;
        double lastCounter = Double.NaN;
        boolean lastEvent = false;
        for (LiveSample sample : evidence) {
            if (!clean(sample)) continue;
            boolean event = sample.bool(ChannelRole.AE_EVENT_JUST_OCCURRED);
            double counter = sample.get(ChannelRole.INSTANT_PULSE_CNT);
            if (event && !lastEvent && Double.isFinite(lastCounter)
                    && Double.isFinite(counter)
                    && Math.abs(counter - lastCounter) < 0.5
                    && lambdaError(sample) > 0.03) {
                result++;
            }
            if (Double.isFinite(counter)) lastCounter = counter;
            lastEvent = event;
        }
        return result;
    }

    private static List<Double> lambdaRecoveryDurations(List<LiveSample> evidence) {
        List<Double> out = new ArrayList<Double>();
        boolean active = false;
        double end = Double.NaN;
        int stable = 0;
        for (LiveSample sample : evidence) {
            if (sample == null || !Double.isFinite(sample.getSeconds())) continue;
            boolean now = sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                    || positive(sample, ChannelRole.AE_ADD_MS);
            if (now) {
                active = true;
                end = sample.getSeconds();
                stable = 0;
                continue;
            }
            if (!active || !Double.isFinite(end) || !clean(sample)) continue;
            if (Math.abs(lambdaError(sample)) <= 0.02) stable++; else stable = 0;
            if (stable >= 3) {
                out.add(Math.max(0.0, sample.getSeconds() - end));
                active = false;
                stable = 0;
            } else if (sample.getSeconds() - end > 3.0) {
                active = false;
                stable = 0;
            }
        }
        return out;
    }

    private static void appendOverlapSummary(StringBuilder detail,
                                             List<TransientEvidenceEvent.Event> events) {
        int tps = 0;
        int wall = 0;
        int instant = 0;
        int map = 0;
        for (TransientEvidenceEvent.Event event : events) {
            if (event.tpsAeOverlap) tps++;
            if (event.wallOverlap) wall++;
            if (event.instantOverlap) instant++;
            if (event.mapPredictOverlap) map++;
        }
        detail.append("Accepted-event overlap context: TPS AE ").append(tps)
                .append(" | Wall ").append(wall)
                .append(" | Instant ").append(instant)
                .append(" | MAP Predict ").append(map).append(".\n");
    }

    private static int cleanCount(List<LiveSample> evidence) {
        int count = 0;
        if (evidence != null) {
            for (LiveSample sample : evidence) if (clean(sample)) count++;
        }
        return count;
    }

    private static boolean clean(LiveSample sample) {
        if (sample == null || sample.bool(ChannelRole.DFCO)
                || sample.bool(ChannelRole.FUEL_CUT)) return false;
        double lambda = sample.get(ChannelRole.LAMBDA);
        double target = sample.get(ChannelRole.TARGET_LAMBDA);
        return Double.isFinite(lambda) && Double.isFinite(target)
                && lambda > 0.55 && lambda < 1.65
                && target > 0.55 && target < 1.65;
    }

    private static double lambdaError(LiveSample sample) {
        return sample.get(ChannelRole.LAMBDA) - sample.get(ChannelRole.TARGET_LAMBDA);
    }

    private static List<GuidedTaskSettingsDraft.Entry> sorted(
            List<GuidedTaskSettingsDraft.Entry> input) {
        List<GuidedTaskSettingsDraft.Entry> out =
                new ArrayList<GuidedTaskSettingsDraft.Entry>(input);
        Collections.sort(out, new Comparator<GuidedTaskSettingsDraft.Entry>() {
            @Override public int compare(GuidedTaskSettingsDraft.Entry a,
                                         GuidedTaskSettingsDraft.Entry b) {
                return Integer.compare(a.getTarget().getFlatIndex(),
                        b.getTarget().getFlatIndex());
            }
        });
        return out;
    }

    private static int nearest(List<GuidedTaskSettingsDraft.Entry> axis,
                               double value) {
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < axis.size(); i++) {
            double d = Math.abs(axis.get(i).getOriginalValue() - value);
            if (d < distance) {
                distance = d;
                best = i;
            }
        }
        return best;
    }

    private static GuidedTaskSettingsDraft.Entry first(GuidedTaskSettingsDraft draft,
                                                        String controllerName) {
        List<GuidedTaskSettingsDraft.Entry> entries =
                draft.entriesForController(controllerName);
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static boolean set(GuidedTaskSettingsDraft draft,
                               GuidedTaskSettingsDraft.Entry entry,
                               double value) {
        try {
            draft.setProposedValue(entry.getTarget().identity(), value);
            return entry.isChanged();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static double quantize(GuidedTaskSettingsDraft.Entry entry,
                                   double value) {
        double step;
        switch (entry.getTarget().getDefinition().getValueType()) {
            case U08:
            case S08:
            case U16:
            case S16:
            case U32:
                step = entry.getTarget().getDefinition().getScale();
                break;
            default:
                step = Math.pow(10.0, -Math.max(0,
                        entry.getTarget().getDefinition().getDecimals()));
                break;
        }
        if (!Double.isFinite(step) || step <= 0.0) return value;
        return Math.rint(value / step) * step;
    }

    private static boolean positive(LiveSample sample, ChannelRole role) {
        double value = sample == null ? Double.NaN : sample.get(role);
        return Double.isFinite(value) && value > 0.000001;
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }

    private static Result unavailable(GuidedTuningRecipe task, String reason) {
        return new Result((task == null ? "Guided task" : task.displayName)
                + " — baseline recommendation withheld\n" + reason
                + "\nNo ProposalWritePlan. Validated Task Settings remain directly review/apply-capable; capture never writes; no Burn.", null);
    }
}
