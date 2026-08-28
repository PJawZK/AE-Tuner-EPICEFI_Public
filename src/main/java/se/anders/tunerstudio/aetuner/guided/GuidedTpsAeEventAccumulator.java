package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.model.TransientEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Guided-only TPS AE event segmentation used to feed the existing conservative
 * AeTableSuggestion math. This class does not create write plans and does not
 * write ECU parameters.
 *
 * Unlike the Passive detector, Guided capture starts from the ECU's TPS AE
 * activity/fuel evidence rather than a separately configured TPSdot threshold.
 * A short pre-window and post-activity quiet window are retained so the shared
 * TransientEvent analysis can evaluate early/mid/late lambda response and
 * attribution to Wall Wetting, MAP Predict and Instant Fuel.
 */
final class GuidedTpsAeEventAccumulator {
    private static final double PRE_SECONDS = 0.60;
    private static final double POST_SECONDS = 0.55;
    private static final double SPLIT_QUIET_SECONDS = 0.35;
    private static final double MAX_EVENT_SECONDS = 4.75;
    private static final double MIN_RUNNING_RPM = 400.0;
    private static final int MIN_EVENT_SAMPLES = 4;
    private static final int MAX_RING_SAMPLES = 400;

    private final Deque<LiveSample> ring = new ArrayDeque<LiveSample>();
    private final List<LiveSample> active = new ArrayList<LiveSample>();
    private final List<TransientEvent> events = new ArrayList<TransientEvent>();

    private boolean inEvent;
    private double eventStartSeconds = Double.NaN;
    private double lastActivitySeconds = Double.NaN;
    private int nextEventIndex = 1;

    synchronized void reset() {
        events.clear();
        nextEventIndex = 1;
        resetTracking();
    }

    /** Preserve completed evidence while starting a same-method continuation. */
    synchronized void resume() {
        resetTracking();
    }

    synchronized void accept(LiveSample sample) {
        if (sample == null) return;
        appendRing(sample);
        boolean activeNow = tpsAeActivity(sample);

        if (inEvent && activeNow && Double.isFinite(lastActivitySeconds)
                && sample.getSeconds() - lastActivitySeconds >= SPLIT_QUIET_SECONDS) {
            closeEvent("closed before a new TPS AE burst after quiet period");
            startEvent(sample);
            return;
        }

        if (!inEvent && activeNow && canStart(sample)) {
            startEvent(sample);
            return;
        }

        if (!inEvent) return;

        active.add(sample);
        if (activeNow) lastActivitySeconds = sample.getSeconds();

        double quiet = Double.isFinite(lastActivitySeconds)
                ? sample.getSeconds() - lastActivitySeconds : 0.0;
        double duration = Double.isFinite(eventStartSeconds)
                ? sample.getSeconds() - eventStartSeconds : 0.0;
        if (duration >= MAX_EVENT_SECONDS) {
            closeEvent("duration timeout/truncated at Guided max capture limit");
        } else if (quiet >= POST_SECONDS) {
            closeEvent("post-event quiet window captured");
        }
    }

    synchronized void finish() {
        if (inEvent) closeEvent("capture finished while TPS AE event was active/recovering");
        resetTracking();
    }

    synchronized int eventCount() { return events.size(); }

    synchronized int fuelProvedEventCount() {
        int count = 0;
        for (TransientEvent event : events) {
            if (event.isTpsAeFuelProved()) count++;
        }
        return count;
    }

    synchronized List<TransientEvent> eventsSnapshot() {
        return new ArrayList<TransientEvent>(events);
    }

    private void startEvent(LiveSample sample) {
        inEvent = true;
        active.clear();
        for (LiveSample buffered : ring) {
            if (buffered.getNanoTime() == sample.getNanoTime()) continue;
            if (sample.getSeconds() - buffered.getSeconds() <= PRE_SECONDS) {
                active.add(buffered);
            }
        }
        eventStartSeconds = sample.getSeconds();
        lastActivitySeconds = sample.getSeconds();
        active.add(sample);
    }

    private void closeEvent(String note) {
        if (active.size() >= MIN_EVENT_SAMPLES) {
            events.add(new TransientEvent(nextEventIndex++, true,
                    "Guided TPS AE event", note, active, false));
        }
        resetTracking();
    }

    private void resetTracking() {
        ring.clear();
        active.clear();
        inEvent = false;
        eventStartSeconds = Double.NaN;
        lastActivitySeconds = Double.NaN;
    }

    private void appendRing(LiveSample sample) {
        ring.addLast(sample);
        while (ring.size() > MAX_RING_SAMPLES) ring.removeFirst();
        while (!ring.isEmpty()
                && sample.getSeconds() - ring.peekFirst().getSeconds() > PRE_SECONDS + 0.25) {
            ring.removeFirst();
        }
    }

    private static boolean canStart(LiveSample sample) {
        double rpm = sample.get(ChannelRole.RPM);
        if (!Double.isFinite(rpm) || rpm < MIN_RUNNING_RPM) return false;
        if (sample.bool(ChannelRole.DFCO)) return false;
        double cut = sample.get(ChannelRole.FUEL_CUT);
        return !Double.isFinite(cut) || cut <= 0.01;
    }

    private static boolean tpsAeActivity(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double add = sample.get(ChannelRole.AE_ADD_MS);
        if (Double.isFinite(add) && Math.abs(add) > 0.000001) return true;
        double extra = sample.get(ChannelRole.EXTRA_FUEL);
        return Double.isFinite(extra) && Math.abs(extra) > 0.000001;
    }
}
