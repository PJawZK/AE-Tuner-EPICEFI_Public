package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class GuidedSampleDispatcherRegressionTest {
    private GuidedSampleDispatcherRegressionTest() { }

    public static void main(String[] args) throws Exception {
        producerNeverRunsGuidedListenerInline();
        backlogIsBoundedAndCriticalSamplesSurviveCoalescing();
        suspendClearsBacklogAndResumeWorks();
        listenerFailureDoesNotKillWorker();
        System.out.println("GuidedSampleDispatcherRegressionTest passed");
    }

    private static void producerNeverRunsGuidedListenerInline() throws Exception {
        final String caller = Thread.currentThread().getName();
        final String[] workerName = new String[1];
        final CountDownLatch delivered = new CountDownLatch(1);
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample -> {
            workerName[0] = Thread.currentThread().getName();
            delivered.countDown();
            return GuidedCaptureState.SETTLING;
        });
        dispatcher.resume();
        require(dispatcher.offer(sample(1.0, false, 0.0)),
                "dispatcher rejected an enabled sample");
        require(delivered.await(2, TimeUnit.SECONDS),
                "dispatcher worker did not deliver sample");
        require(workerName[0] != null && !caller.equals(workerName[0]),
                "Guided listener ran inline on the producer thread");
        dispatcher.close();
    }

    private static void backlogIsBoundedAndCriticalSamplesSurviveCoalescing()
            throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<Integer> criticalIds = Collections.synchronizedList(
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
            if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                criticalIds.add((int) Math.round(sample.getSeconds()));
            }
            return sample.bool(ChannelRole.MAP_PRED_ACTIVE)
                    ? GuidedCaptureState.CAPTURING : GuidedCaptureState.READY;
        });
        dispatcher.resume();
        dispatcher.offer(sample(0.1, false, 0.0));
        require(entered.await(2, TimeUnit.SECONDS),
                "slow-listener setup did not enter worker");

        long started = System.nanoTime();
        for (int i = 0; i < 400; i++) {
            dispatcher.offer(sample(10.0 + i / 1000.0, false, 0.0));
        }
        for (int i = 1; i <= 8; i++) {
            dispatcher.offer(sample(i, true, 30.0));
        }
        long producerMillis = (System.nanoTime() - started) / 1000000L;
        GuidedSampleDispatcher.Diagnostics blocked = dispatcher.diagnostics();
        require(blocked.queueDepth <= GuidedSampleDispatcher.CAPACITY,
                "dispatcher exceeded its bounded queue capacity");
        require(blocked.coalesced > 0,
                "steady backlog was not coalesced");
        require(blocked.criticalDropped == 0,
                "critical opening samples were dropped while coalescible entries existed");
        require(producerMillis < 500,
                "producer handoff blocked behind the slow Guided listener");

        release.countDown();
        waitForEmpty(dispatcher, 3000L);
        require(criticalIds.size() == 8,
                "not all protected prediction-active samples reached Guided worker");
        for (int i = 0; i < 8; i++) {
            require(criticalIds.get(i) == i + 1,
                    "critical sample order changed under backlog");
        }
        dispatcher.close();
    }

    private static void suspendClearsBacklogAndResumeWorks() throws Exception {
        final CountDownLatch first = new CountDownLatch(1);
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample -> {
            first.countDown();
            return GuidedCaptureState.SETTLING;
        });
        dispatcher.resume();
        for (int i = 0; i < 20; i++) {
            dispatcher.offer(sample(i * 0.01, false, 0.0));
        }
        dispatcher.suspend();
        GuidedSampleDispatcher.Diagnostics suspended = dispatcher.diagnostics();
        require(!suspended.accepting && suspended.queueDepth == 0,
                "suspend did not stop acceptance and clear pending samples");
        require(!dispatcher.offer(sample(2.0, true, 20.0)),
                "suspended dispatcher accepted another sample");

        dispatcher.resume();
        require(dispatcher.offer(sample(3.0, false, 0.0)),
                "dispatcher did not accept samples after resume");
        require(first.await(2, TimeUnit.SECONDS),
                "resumed dispatcher did not deliver a sample");
        dispatcher.close();
        require(dispatcher.diagnostics().closed,
                "dispatcher did not report closed lifecycle state");
    }

    private static void listenerFailureDoesNotKillWorker() throws Exception {
        final CountDownLatch good = new CountDownLatch(1);
        final int[] count = new int[1];
        GuidedSampleDispatcher dispatcher = new GuidedSampleDispatcher(sample -> {
            count[0]++;
            if (count[0] == 1) {
                throw new IllegalStateException("synthetic listener failure");
            }
            good.countDown();
            return GuidedCaptureState.READY;
        });
        dispatcher.resume();
        dispatcher.offer(sample(1.0, false, 0.0));
        dispatcher.offer(sample(1.1, false, 0.0));
        require(good.await(2, TimeUnit.SECONDS),
                "worker stopped after listener exception");
        require(dispatcher.diagnostics().listenerFailures == 1,
                "listener failure was not diagnosed exactly once");
        require(dispatcher.workerAliveForTest(),
                "worker thread died after isolated listener exception");
        dispatcher.close();
    }

    private static void waitForEmpty(GuidedSampleDispatcher dispatcher,
                                     long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + timeoutMillis * 1000000L;
        while (System.nanoTime() < deadline) {
            GuidedSampleDispatcher.Diagnostics diagnostics = dispatcher.diagnostics();
            if (diagnostics.queueDepth == 0
                    && diagnostics.delivered + diagnostics.coalesced
                    + diagnostics.dropped >= diagnostics.offered) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("dispatcher backlog did not drain before timeout: "
                + dispatcher.diagnostics().summary());
    }

    private static LiveSample sample(double seconds, boolean prediction,
                                     double tpsDot) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2000.0);
        values.put(ChannelRole.MAP, 50.0);
        values.put(ChannelRole.TPS, 10.0);
        values.put(ChannelRole.FALLBACK_MAP, prediction ? 75.0 : 50.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, tpsDot, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
