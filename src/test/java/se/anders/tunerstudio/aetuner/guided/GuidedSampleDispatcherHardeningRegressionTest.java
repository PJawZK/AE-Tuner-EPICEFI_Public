package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Permanent gates for whole-plugin dispatcher lifecycle/Decel hardening. */
public final class GuidedSampleDispatcherHardeningRegressionTest {
    private GuidedSampleDispatcherHardeningRegressionTest() { }

    public static void main(String[] args) throws Exception {
        constructionDoesNotCreateWorkerGcRoot();
        negativeTpsTransientsSurviveQuietBacklog();
        decelActiveMarkerIsCriticalEvenWithSmallTpsDot();
        System.out.println("GuidedSampleDispatcherHardeningRegressionTest passed");
    }

    private static void constructionDoesNotCreateWorkerGcRoot() {
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample ->
                GuidedCaptureState.READY);
        require(!dispatcher.workerAliveForTest(),
                "constructing an inactive plugin dispatcher created a worker thread");
        require(!dispatcher.diagnostics().accepting,
                "new dispatcher accepted samples before lifecycle resume");
        dispatcher.resume();
        require(dispatcher.workerAliveForTest(),
                "resume did not lazily create the Guided worker");
        dispatcher.close();
    }

    private static void negativeTpsTransientsSurviveQuietBacklog() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<Integer> releases = Collections.synchronizedList(
                new ArrayList<Integer>());
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (sample.getTpsDot() <= -5.0) {
                releases.add((int)Math.round(sample.getSeconds()));
            }
            return GuidedCaptureState.CAPTURING;
        });
        dispatcher.resume();
        dispatcher.offer(sample(0.1, 0.0, false));
        require(entered.await(2, TimeUnit.SECONDS),
                "fixture did not block worker before backlog");

        for (int i = 0; i < 400; i++) {
            dispatcher.offer(sample(10.0 + i / 1000.0, 0.0, false));
        }
        for (int i = 1; i <= 8; i++) {
            dispatcher.offer(sample(i, -25.0, false));
        }
        GuidedSampleDispatcher.Diagnostics blocked = dispatcher.diagnostics();
        require(blocked.coalesced > 0,
                "quiet backlog was not coalesced");
        require(blocked.criticalDropped == 0,
                "negative TPS release samples were dropped while quiet entries existed");

        release.countDown();
        waitForEmpty(dispatcher, 3000L);
        require(releases.size() == 8,
                "not all protected negative TPS release samples reached Guided processing");
        dispatcher.close();
    }

    private static void decelActiveMarkerIsCriticalEvenWithSmallTpsDot() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch decelDelivered = new CountDownLatch(1);
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (sample.bool(ChannelRole.TPS_DECEL_ACTIVE)) decelDelivered.countDown();
            return GuidedCaptureState.CAPTURING;
        });
        dispatcher.resume();
        dispatcher.offer(sample(0.1, 0.0, false));
        require(entered.await(2, TimeUnit.SECONDS), "fixture worker did not block");
        for (int i = 0; i < 200; i++) {
            dispatcher.offer(sample(20.0 + i / 1000.0, 0.0, false));
        }
        require(dispatcher.offer(sample(3.0, -0.5, true)),
                "TPS Decel Active marker was rejected under coalescible backlog");
        require(dispatcher.diagnostics().criticalDropped == 0,
                "TPS Decel Active marker was not treated as critical");
        release.countDown();
        require(decelDelivered.await(3, TimeUnit.SECONDS),
                "TPS Decel Active marker never reached Guided processing");
        dispatcher.close();
    }

    private static LiveSample sample(double seconds, double tpsDot,
                                     boolean decelActive) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, 15.0);
        values.put(ChannelRole.MAP, 45.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, decelActive ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, tpsDot / 100.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        return new LiveSample(Math.round(seconds * 1.0e9), seconds,
                values, tpsDot, 0.0);
    }

    private static void waitForEmpty(GuidedSampleDispatcher dispatcher,
                                     long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + timeoutMillis * 1000000L;
        while (System.nanoTime() < deadline) {
            GuidedSampleDispatcher.Diagnostics d = dispatcher.diagnostics();
            if (d.queueDepth == 0
                    && d.delivered + d.coalesced + d.dropped >= d.offered) return;
            Thread.sleep(10L);
        }
        throw new AssertionError("dispatcher backlog did not drain: "
                + dispatcher.diagnostics().summary());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
