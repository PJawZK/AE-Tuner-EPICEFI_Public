package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.EngagementDetectionMethodModule;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.EnumMap;

/** Regression for the passive Foundation 1 road-capture contract. */
public final class EngagementPassiveCaptureRegressionTest {
    private EngagementPassiveCaptureRegressionTest() { }

    public static void main(String[] args) {
        naturalClusterUsesVisualReferenceWithoutExactTarget();
        passiveRecommendationAutoAdvancesToSampleLength();
        independentSetsAccumulateTimingEvidence();
        completedSetExplainsHowToImproveEvidence();
        vssZeroCanConfirmRepresentativeOperatingRange();
        idleBaselineRpmSpreadStillStaysProvisional();
        settlingPreventsOverlappingPedalEvents();
        roadRepositionAutomaticallyLearnsNewBaseline();
        archive37SparseOnsetKeepsTheTruePreEventBaseline();
        releaseBelowHigherBaselineStillRearms();
        newCaptureSetPreservesPriorSetSummary();
        completedSetRestartAutomaticallyCreatesFreshActiveSet();
        methodRouteIsReadOnlyDuringCapture();
        System.out.println("EngagementPassiveCaptureRegressionTest passed");
    }

    private static void naturalClusterUsesVisualReferenceWithoutExactTarget() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(5);
        Feed feed = new Feed();

        // Natural road-like variation centered around ~16 TPS and spread over
        // ordinary RPM. The first event is the visual reference only.
        feed.event(15.0, 1400.0, 45.0);
        feed.event(17.2, 1600.0, 45.0);
        feed.event(14.4, 1800.0, 45.0);
        feed.event(16.3, 2000.0, 45.0);
        feed.event(18.0, 2200.0, 45.0);
        // Outliers remain recorded but must not stretch the comparable set.
        feed.event(8.0, 2400.0, 45.0);
        feed.event(28.0, 2600.0, 45.0);

        EngagementPassiveCapture.Snapshot s = EngagementPassiveCapture.snapshot();
        require(s.storedEvents >= 7, "passive collector lost physical movements");
        require(s.comparableEvents >= 5,
                "natural 14-18 TPS cluster did not complete the comparable set");
        require(s.complete(), "passive set did not complete after five clustered movements");
        require(s.medianStep > 14.0 && s.medianStep < 18.5,
                "learned cluster median did not follow the natural road movements: " + s.medianStep);
        require(s.tolerance >= 3.0 && s.tolerance <= 6.0,
                "adaptive tolerance escaped its road-safe bounds: " + s.tolerance);
        require(Double.isFinite(s.referencePeakTps),
                "first usable movement did not establish a visual TPS reference");
        require(s.repeatPeakTps.length >= 4,
                "later completed movements did not retain short repeat markers");
        require(s.roadComparableEvents >= 5 && s.roadRpmSpan >= 700.0,
                "road evidence context did not retain useful RPM spread");

        String review = EngagementPassiveCapture.reviewText(snapshot());
        require(review.contains("DELTA WINDOW") || review.contains("Candidate scores are too close"),
                "plugin-internal passive timing review did not evaluate Delta Window confidence");
        require(review.contains("presentation only")
                        && review.contains("No controller settings were changed during capture")
                        && review.contains("No LLM"),
                "passive timing review lost reference/no-write/self-contained semantics");
    }

    private static void passiveRecommendationAutoAdvancesToSampleLength() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(5);
        Feed feed = new Feed();
        feed.event(15.0, 1400.0, 45.0);
        feed.event(17.0, 1600.0, 45.0);
        feed.event(14.5, 1800.0, 45.0);
        feed.event(16.0, 2000.0, 45.0);
        feed.event(18.0, 2200.0, 45.0);

        AeProjectSnapshot shortHistory = snapshot(25.0, 0.010);
        ProposalWritePlan plan = EngagementPassiveCapture.recommendationPlan(shortHistory);
        require(plan != null,
                "road-confirmed passive recommendation did not produce a timing proposal");
        boolean hasSampleLength = false;
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            if (AeParameterNames.TPS_ACCEL_LOOKBACK.equals(change.parameterName)) {
                hasSampleLength = true;
                require(change.proposedValue > change.expectedValue,
                        "Sample Length capacity check did not increase insufficient history");
            }
        }
        require(hasSampleLength,
                "passive recommendation stopped after Delta Window instead of automatically advancing to Sample Length");
        require(plan.getContext().contains("1/2 Delta Window")
                        && plan.getContext().contains("2/2 Sample Length"),
                "timing proposal lost the automatic Delta Window -> Sample Length handoff");
        EngagementPassiveCapture.TimingStatus timing =
                EngagementPassiveCapture.timingStatus(shortHistory);
        require(timing.deltaResolved && timing.sampleResolved,
                "structured timing state did not visibly resolve 1/2 Delta Window then 2/2 Sample Length");
        require(timing.sampleChangeNeeded,
                "structured timing state lost the Sample Length capacity increase");
    }

    private static void independentSetsAccumulateTimingEvidence() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();
        feed.event(15.0, 1500.0, 0.0);
        feed.event(16.0, 1700.0, 0.0);
        feed.event(15.5, 1900.0, 0.0);
        EngagementPassiveCapture.TimingStatus first =
                EngagementPassiveCapture.timingStatus(snapshot());
        require(first.evidenceEvents >= 3,
                "first independent set did not enter timing evidence");
        EngagementPassiveCapture.startNewSet();
        feed.event(16.0, 2100.0, 0.0);
        feed.event(15.0, 2300.0, 0.0);
        feed.event(15.5, 2500.0, 0.0);
        EngagementPassiveCapture.TimingStatus combined =
                EngagementPassiveCapture.timingStatus(snapshot());
        require(combined.evidenceSets >= 2 && combined.evidenceEvents >= 6,
                "second independent set replaced rather than accumulated prior timing evidence");
        require(EngagementPassiveCapture.reviewText(snapshot()).contains("retained and combined"),
                "review does not tell the operator that independent-set timing evidence accumulates");
    }

    private static void completedSetExplainsHowToImproveEvidence() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();
        feed.event(15.0, 1500.0, 0.0);
        feed.event(15.5, 1600.0, 0.0);
        feed.event(16.0, 1650.0, 0.0);
        EngagementPassiveCapture.TimingStatus timing =
                EngagementPassiveCapture.timingStatus(snapshot());
        String review = EngagementPassiveCapture.reviewText(snapshot());
        require(timing.evidenceEvents >= 3 && timing.pedalQuality.length() > 0,
                "completed set did not expose evidence quality diagnostics");
        require(review.contains("Pedal repeatability")
                        && review.contains("Host trace timing")
                        && review.contains("WHAT TO DO NEXT"),
                "completed set still gives only a generic more-evidence prompt");
        if (!timing.deltaResolved) {
            require(timing.sampleResolved
                            && review.contains("2/2 SAMPLE LENGTH: CAPACITY RESOLVED"),
                    "ambiguous Delta Window still hid Sample Length capacity status");
        }
    }

    private static void vssZeroCanConfirmRepresentativeOperatingRange() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(5);
        Feed feed = new Feed();
        feed.event(15.0, 1400.0, 0.0);
        feed.event(16.0, 1650.0, 0.0);
        feed.event(15.5, 1900.0, 0.0);
        feed.event(16.5, 2150.0, 0.0);
        feed.event(15.8, 2400.0, 0.0);
        EngagementPassiveCapture.Snapshot s = EngagementPassiveCapture.snapshot();
        require(s.roadComparableEvents == 0,
                "Archive39-shaped stuck-zero VSS fixture unexpectedly became VSS-road evidence");
        require(s.roadRpmSpan >= 900.0,
                "pre-event operating RPM diversity was not retained independently of VSS");
        require(s.roadConfirmed(),
                "stuck-zero VSS still hard-blocked representative Foundation 1 operating coverage");
    }

    private static void idleBaselineRpmSpreadStillStaysProvisional() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(5);
        Feed feed = new Feed();
        feed.event(15.0, 900.0, 0.0);
        feed.event(16.0, 950.0, 0.0);
        feed.event(15.5, 1000.0, 0.0);
        feed.event(16.5, 1020.0, 0.0);
        feed.event(15.8, 980.0, 0.0);
        EngagementPassiveCapture.Snapshot s = EngagementPassiveCapture.snapshot();
        require(s.complete(), "idle-only fixture did not complete repeatability evidence");
        require(s.roadRpmSpan < 400.0 && !s.roadConfirmed(),
                "idle-only baseline RPM cluster incorrectly became final operating-range confirmation");
    }

    private static void settlingPreventsOverlappingPedalEvents() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(5);
        Feed feed = new Feed();
        feed.baseline(10, 1600.0, 0.0);
        feed.openOnly(16.0, 1600.0, 0.0);
        EngagementPassiveCapture.Snapshot after = EngagementPassiveCapture.snapshot();
        require(after.storedEvents == 1 && after.settling,
                "completed event did not enter SETTLING");
        require(Double.isFinite(after.settleBaselineTps)
                        && Math.abs(after.settleBaselineTps - feed.baseTps) < 0.25,
                "SETTLING did not expose the actual pre-event TPS return reference");
        require(!Double.isFinite(after.settleUpperTps),
                "re-anchor marker should not exist before a new steady operating point is learned");

        // Try to start another physical movement immediately while the first
        // event is still unwinding. It must not become a second event.
        feed.sample(feed.baseTps + 10.0, 60.0, true, 0.05, 1750.0, 0.0);
        feed.sample(feed.baseTps + 15.0, 60.0, true, 0.05, 1700.0, 0.0);
        require(EngagementPassiveCapture.snapshot().storedEvents == 1,
                "SETTLING accepted an overlapping second pedal event");
        require(EngagementPassiveCapture.snapshot().settleTpsReturned,
                "completed/reversed opening did not latch the release needed for re-anchoring");

        feed.settle(20, 1600.0, 0.0);
        require(EngagementPassiveCapture.snapshot().readyForMovement(),
                "TPS did not re-arm after a stable settling interval");
    }

    private static void roadRepositionAutomaticallyLearnsNewBaseline() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();

        // First event begins from an idle/light-throttle point.
        for (int i = 0; i < 12; i++) {
            feed.sample(1.2, 0.1, false, 0.030, 1000.0, 0.0);
        }
        feed.sample(5.0, 55.0, true, 0.040, 1050.0, 0.0);
        feed.sample(10.0, 70.0, true, 0.040, 1100.0, 0.0);
        feed.sample(15.0, 55.0, true, 0.040, 1150.0, 0.0);
        feed.sample(14.2, -20.0, false, 0.040, 1180.0, 0.0);
        require(EngagementPassiveCapture.snapshot().settling,
                "road re-anchor fixture did not enter re-arm after first opening");

        // Now drive/reposition to a realistic road operating point. Old logic
        // required returning near 1.2% TPS and therefore made this impossible.
        feed.sample(5.0, -25.0, false, 0.080, 1350.0, 0.0);
        feed.sample(7.0, 18.0, false, 0.100, 1600.0, 0.0);
        feed.sample(9.0, 14.0, false, 0.100, 1850.0, 0.0);
        for (int i = 0; i < 8; i++) {
            feed.sample(9.0, 0.2, false, 0.100, 2000.0, 0.0);
        }
        EngagementPassiveCapture.Snapshot rearmed = EngagementPassiveCapture.snapshot();
        require(rearmed.readyForMovement(),
                "higher road TPS/RPM operating point could not automatically re-anchor");
        require(Double.isFinite(rearmed.settleUpperTps)
                        && Math.abs(rearmed.settleUpperTps - 9.0) < 0.5,
                "new road TPS operating point was not learned as the next baseline");

        // Next opening must be measured from ~9% TPS, not the original ~1.2% idle baseline.
        feed.sample(12.0, 45.0, true, 0.040, 2000.0, 0.0);
        feed.sample(18.0, 65.0, true, 0.040, 2020.0, 0.0);
        feed.sample(25.0, 60.0, true, 0.040, 2040.0, 0.0);
        feed.sample(24.2, -20.0, false, 0.040, 2030.0, 0.0);
        EngagementPassiveCapture.Snapshot second = EngagementPassiveCapture.snapshot();
        require(second.storedEvents >= 2,
                "next event was not captured after automatic road re-anchor");
        require(second.medianStep < 20.0,
                "second event still referenced the stale idle TPS baseline instead of the road baseline");
    }

    /**
     * Archive37 physical reproduction: sparse/irregular delivery left only two
     * genuinely pre-event samples inside the short baseline window. vehicle-test.5
     * inserted the rising pedal samples before checking onset, eventually starting
     * at the peak with a false ~5.3% TPS baseline and a 0.000 s event. The released
     * pedal then could never satisfy the symmetric settling band.
     */
    private static void archive37SparseOnsetKeepsTheTruePreEventBaseline() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();

        feed.sample(1.525, 0.0, false, 0.030, 1034.0, 0.0);
        feed.sample(1.540, 0.40, false, 0.038, 1034.0, 0.0);
        feed.sample(1.530, -0.09, false, 0.108, 1032.0, 0.0);
        feed.sample(1.530, 0.0, false, 0.154, 1036.0, 0.0);

        // Physical opening shaped from Archive37 around the stuck SETTLING case.
        feed.sample(2.430, 6.22, false, 0.145, 1026.0, 0.0);
        feed.sample(5.310, 79.44, true, 0.036, 1018.0, 0.0);
        feed.sample(10.190, 62.46, true, 0.078, 1018.0, 0.0);
        feed.sample(13.495, 37.44, false, 0.088, 1021.0, 0.0);
        feed.sample(13.490, -0.15, false, 0.033, 1021.0, 0.0);
        feed.sample(13.000, -13.30, false, 0.030, 1071.0, 0.0);

        EngagementPassiveCapture.Snapshot captured = EngagementPassiveCapture.snapshot();
        require(captured.storedEvents == 1,
                "Archive37-shaped opening was lost instead of being captured from the pre-event baseline");
        require(captured.rejectedDuration == 0,
                "Archive37-shaped opening still started at its peak and became a zero-duration rejection");
        require(captured.medianStep > 11.5 && captured.medianStep < 12.5,
                "Archive37-shaped opening used a contaminated TPS baseline: step=" + captured.medianStep);
        require(captured.settling,
                "Archive37-shaped completed opening did not enter SETTLING");
        require(captured.settleBaselineTps > 1.45 && captured.settleBaselineTps < 1.60,
                "Archive37-shaped SETTLING marker did not preserve the true pre-event baseline: "
                        + captured.settleBaselineTps);

        // Release below the original ~1.53% baseline, then wait out the stationary
        // RPM flare. This must return to READY instead of waiting forever near 5% TPS.
        feed.sample(1.480, -150.0, false, 0.077, 1071.0, 0.0);
        for (int i = 0; i < 6; i++) {
            feed.sample(0.75, 0.2, false, 0.120, 1180.0 - i * 24.0, 0.0);
        }
        for (int i = 0; i < 6; i++) {
            feed.sample(0.72, 0.1, false, 0.120, 1030.0, 0.0);
        }
        require(EngagementPassiveCapture.snapshot().readyForMovement(),
                "Archive37-shaped release remained stuck in SETTLING");
    }

    private static void releaseBelowHigherBaselineStillRearms() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();
        for (int i = 0; i < 12; i++) {
            feed.sample(10.0, 0.1, false, 0.030, 1800.0, 40.0);
        }
        feed.sample(12.0, 40.0, true, 0.035, 1800.0, 40.0);
        feed.sample(16.0, 60.0, true, 0.035, 1800.0, 40.0);
        feed.sample(20.0, 60.0, true, 0.035, 1800.0, 40.0);
        feed.sample(22.0, 40.0, true, 0.035, 1800.0, 40.0);
        feed.sample(21.0, -20.0, false, 0.040, 1800.0, 40.0);
        require(EngagementPassiveCapture.snapshot().settling,
                "higher-baseline positive opening did not enter SETTLING");

        for (int i = 0; i < 20; i++) {
            feed.sample(2.0, 0.1, false, 0.030, 1800.0, 40.0);
        }
        require(EngagementPassiveCapture.snapshot().readyForMovement(),
                "full pedal release below a higher pre-event TPS baseline failed to re-arm");
    }

    private static void newCaptureSetPreservesPriorSetSummary() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();
        feed.event(15.0, 1500.0, 40.0);
        feed.event(16.0, 1800.0, 40.0);
        feed.event(17.0, 2100.0, 40.0);
        require(EngagementPassiveCapture.snapshot().complete(),
                "first independent set did not complete");
        EngagementPassiveCapture.startNewSet();
        EngagementPassiveCapture.Snapshot fresh = EngagementPassiveCapture.snapshot();
        require(fresh.completedSets == 1,
                "new capture set discarded the previous set summary");
        require(fresh.storedEvents == 0 && fresh.comparableEvents == 0,
                "new capture set inherited the old active event counter");
        require(!Double.isFinite(fresh.referencePeakTps) && fresh.repeatPeakTps.length == 0,
                "new capture set inherited old visual TPS markers");
    }

    private static void completedSetRestartAutomaticallyCreatesFreshActiveSet() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(3);
        Feed feed = new Feed();
        feed.event(14.0, 1500.0, 40.0);
        feed.event(15.0, 1750.0, 40.0);
        feed.event(16.0, 2000.0, 40.0);
        EngagementPassiveCapture.Snapshot complete = EngagementPassiveCapture.snapshot();
        require(complete.complete() && complete.completedSets == 0,
                "precondition failed: active set did not complete before restart");

        // GuidedMethodProbeSession calls configureTarget when capture starts.
        // Re-starting a completed Foundation capture must therefore create a
        // new independent active population rather than resuming the old 3/3.
        EngagementPassiveCapture.configureTarget(3);
        EngagementPassiveCapture.Snapshot restarted = EngagementPassiveCapture.snapshot();
        require(restarted.completedSets == 1,
                "completed Foundation restart did not archive the previous set");
        require(restarted.storedEvents == 0 && restarted.comparableEvents == 0,
                "completed Foundation restart retained the old passed-event counter");
        require(!Double.isFinite(restarted.referencePeakTps)
                        && restarted.repeatPeakTps.length == 0,
                "completed Foundation restart retained the old reference/repeat markers");
    }

    private static void methodRouteIsReadOnlyDuringCapture() {
        EngagementDetectionMethodModule module = new EngagementDetectionMethodModule();
        AeProjectSnapshot snapshot = snapshot();
        module.currentTuneContext(snapshot);
        require(module.explicitSettingWritePlan(snapshot) == null,
                "passive Foundation 1 exposed a live/manual road write plan");
        require(module.setupGuidance().contains("no exact TPS target")
                        || module.setupGuidance().contains("There is no exact TPS target"),
                "passive Foundation 1 still instructs an exact pedal target");
        require(module.accumulationPlan().contains("No controller writes occur during capture"),
                "passive Foundation 1 lost the no-write capture boundary");
        require(module.operatorInputs(snapshot).contains("Garage/idle testing")
                        && module.operatorInputs(snapshot).contains("operating RPM"),
                "Foundation 1 no longer distinguishes provisional idle evidence from road confirmation");
    }

    private static final class Feed {
        double seconds;
        long index;
        final double baseTps = 7.0;

        void event(double step, double rpm, double vss) {
            baseline(12, rpm, vss);
            openOnly(step, rpm, vss);
            settle(20, rpm, vss);
        }

        void baseline(int count, double rpm, double vss) {
            for (int i = 0; i < count; i++) sample(baseTps, 0.15, false, 0.030, rpm, vss);
        }

        void openOnly(double step, double rpm, double vss) {
            sample(baseTps + step * 0.20, 45.0, true, 0.035, rpm, vss);
            sample(baseTps + step * 0.50, 65.0, true, 0.035, rpm, vss);
            sample(baseTps + step * 0.80, 65.0, true, 0.035, rpm, vss);
            sample(baseTps + step, 45.0, true, 0.035, rpm, vss);
            sample(baseTps + step - 0.8, -20.0, false, 0.040, rpm, vss);
        }

        void settle(int count, double rpm, double vss) {
            for (int i = 0; i < count; i++) sample(baseTps, 0.10, false, 0.030, rpm, vss);
        }

        void sample(double tps, double tpsDot, boolean detector, double dt,
                    double rpm, double vss) {
            seconds += dt;
            EnumMap<ChannelRole, Double> values =
                    new EnumMap<ChannelRole, Double>(ChannelRole.class);
            values.put(ChannelRole.RPM, rpm);
            values.put(ChannelRole.TPS, tps);
            values.put(ChannelRole.VSS, vss);
            values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
            values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
            values.put(ChannelRole.DELTA_TPS, detector ? 1.4 : 0.2);
            values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, detector ? 1.4 : 0.2);
            values.put(ChannelRole.AE_WINDOW_MS, 50.0);
            values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
            values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
            EngagementPassiveCapture.accept(new LiveSample(++index, seconds,
                    values, tpsDot, 0.0));
        }
    }

    private static AeProjectSnapshot snapshot() {
        return snapshot(25.0, 0.050);
    }

    private static AeProjectSnapshot snapshot(double deltaWindowMs, double sampleLengthSeconds) {
        return new AeProjectSnapshot(
                "passive-foundation1",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", deltaWindowMs, sampleLengthSeconds, true, 0.10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
