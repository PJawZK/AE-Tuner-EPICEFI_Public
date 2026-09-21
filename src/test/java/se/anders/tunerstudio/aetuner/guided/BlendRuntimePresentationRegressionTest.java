package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for presentation-only Blend allocation controls. */
public final class BlendRuntimePresentationRegressionTest {
    private BlendRuntimePresentationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        maneuverWidgetsRemainPermanentAcrossRefreshes();
        runtimeWorkerReusesRichModelInsideUiCadence();
        directProjectionCallsRemainDeterministicAndUncached();
        System.out.println("BlendRuntimePresentationRegressionTest passed");
    }

    private static void maneuverWidgetsRemainPermanentAcrossRefreshes() {
        GuidedFocusHub.clear();
        GuidedV019BlendDurationFocus focus = new GuidedV019BlendDurationFocus(null, null);
        require(focus.driverStepComponentCountForTest() == 5,
                "Blend maneuver strip must construct exactly five permanent phase widgets");
        for (int i = 0; i < 200; i++) focus.refreshFromProduction();
        require(focus.driverStepComponentCountForTest() == 5,
                "Blend refresh rebuilt or leaked maneuver phase widgets");
        GuidedFocusHub.clear();
    }

    private static void runtimeWorkerReusesRichModelInsideUiCadence() throws Exception {
        BlendDurationFocusModel.resetRuntimePresentationCacheForTest();
        final BlendDurationCaptureConfig config = new BlendDurationCaptureConfig(
                1500.0, 20.0, 5, 0, false,
                new double[]{1500.0, 2600.0}, new double[]{0.30, 0.26});
        final BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        final LiveSample sample = sample(1500.0, 3.0, 45.0);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BlendDurationFocusModel first = build(config, groups, sample);
                    BlendDurationFocusModel second = build(config, groups, sample);
                    require(first == second,
                            "runtime worker should reuse the rich presentation snapshot inside the 50 ms UI window");
                } catch (Throwable ex) {
                    failure.set(ex);
                }
            }
        }, "AE-Tuner-Guided-worker");
        worker.start();
        worker.join();
        if (failure.get() != null) throw new AssertionError("worker regression failed", failure.get());
        require(BlendDurationFocusModel.runtimePresentationBuildsForTest() == 1L,
                "runtime worker built more than one rich model for back-to-back samples");
        require(BlendDurationFocusModel.runtimePresentationCacheHitsForTest() >= 1L,
                "runtime presentation cache was not exercised");
    }

    private static void directProjectionCallsRemainDeterministicAndUncached() {
        BlendDurationFocusModel.resetRuntimePresentationCacheForTest();
        BlendDurationCaptureConfig config = new BlendDurationCaptureConfig(1500.0, 20.0, 5, 0, false);
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        LiveSample sample = sample(1500.0, 3.0, 45.0);
        BlendDurationFocusModel first = build(config, groups, sample);
        BlendDurationFocusModel second = build(config, groups, sample);
        require(first != second,
                "non-runtime model projections must remain uncached for deterministic unit-test semantics");
        require(BlendDurationFocusModel.runtimePresentationBuildsForTest() == 0L
                        && BlendDurationFocusModel.runtimePresentationCacheHitsForTest() == 0L,
                "non-runtime calls polluted runtime presentation counters/cache");
    }

    private static BlendDurationFocusModel build(BlendDurationCaptureConfig config,
                                                 BlendDurationComparabilityGroups groups,
                                                 LiveSample sample) {
        return BlendDurationFocusModel.build(
                GuidedCaptureState.CAPTURING,
                config,
                sample,
                null,
                null,
                null,
                false,
                null,
                null,
                groups,
                new ArrayList<BlendDurationAttempt>(),
                0,
                0,
                "capture",
                "checks",
                "result",
                BlendDurationFocusTrace.empty(),
                BlendDurationFocusTrace.empty());
    }

    private static LiveSample sample(double rpm, double tps, double map) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        return new LiveSample(System.nanoTime(), 1.0, values, 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
