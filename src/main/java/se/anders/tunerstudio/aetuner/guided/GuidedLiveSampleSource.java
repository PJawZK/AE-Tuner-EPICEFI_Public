package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.OutputChannelResolver;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.OutputChannelClient;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal TunerStudio live-channel source for the current Guided runtime.
 *
 * This is deliberately not an analysis subsystem. It owns only output-channel
 * resolution/subscription, coherent callback-batch assembly, derivative
 * calculation and bounded handoff to GuidedSampleDispatcher. The retired
 * Passive detector/event/report pipeline has no authority or work here.
 */
public final class GuidedLiveSampleSource implements OutputChannelClient {
    private static final long MIN_SAMPLE_GAP_NS = 8000000L;

    private final Object lock = new Object();
    private final Object samplingLock = new Object();
    private final GuidedSampleDispatcher dispatcher;
    private final EnumMap<ChannelRole, String> channelNames =
            new EnumMap<ChannelRole, String>(ChannelRole.class);
    private final EnumMap<ChannelRole, Double> latestValues =
            new EnumMap<ChannelRole, Double>(ChannelRole.class);
    private final Map<String, ChannelRole> subscribedChannels =
            new HashMap<String, ChannelRole>();
    private final Set<String> availableOutputChannels = new HashSet<String>();
    private final CoherentAssembler assembler = new CoherentAssembler();

    private ControllerAccess controllerAccess;
    private OutputChannelServer outputChannelServer;
    private String configurationName = "Main Controller";
    private LiveSample previousSample;
    private long lastSampleNano;
    private long lastRateWindowNano;
    private int samplesInWindow;
    private volatile double sampleRateHz;
    private volatile boolean enabled;
    private volatile String status = "Guided live stream disconnected.";

    public GuidedLiveSampleSource(GuidedSampleDispatcher dispatcher) {
        if (dispatcher == null) throw new IllegalArgumentException("dispatcher");
        this.dispatcher = dispatcher;
    }

    public void connectController(ControllerAccess access) {
        disconnectController();
        if (access == null) {
            status = "Guided live stream: no controller access.";
            return;
        }
        controllerAccess = access;
        try {
            outputChannelServer = access.getOutputChannelServer();
            if (outputChannelServer == null) {
                status = "Guided live stream: no TunerStudio output-channel server.";
                return;
            }
            configurationName = findConfigurationName(access);
            resolveOutputChannels();
            subscribeResolvedChannels();
            synchronized (samplingLock) {
                assembler.reset();
                previousSample = null;
                lastSampleNano = 0L;
                lastRateWindowNano = 0L;
                samplesInWindow = 0;
                sampleRateHz = 0.0;
                enabled = true;
            }
            status = "Guided live stream: " + configurationName + " | subscribed "
                    + subscribedCount() + " channel(s).";
        } catch (ControllerException ex) {
            enabled = false;
            status = "Guided live stream connect failed: " + safeMessage(ex);
        }
    }

    public void disconnectController() {
        OutputChannelServer server;
        synchronized (samplingLock) {
            enabled = false;
            server = outputChannelServer;
            outputChannelServer = null;
            controllerAccess = null;
            assembler.reset();
            previousSample = null;
            lastSampleNano = 0L;
            lastRateWindowNano = 0L;
            samplesInWindow = 0;
            sampleRateHz = 0.0;
        }
        synchronized (lock) {
            subscribedChannels.clear();
            latestValues.clear();
            channelNames.clear();
            availableOutputChannels.clear();
        }
        if (server != null) {
            try {
                server.unsubscribe(this);
            } catch (RuntimeException ignored) {
                // Host teardown may already have retired the server.
            }
        }
        status = "Guided live stream disconnected.";
    }

    @Override
    public void setCurrentOutputChannelValue(String outputChannelName, double value) {
        ChannelRole role;
        synchronized (lock) {
            role = subscribedChannels.get(outputChannelName);
            if (role != null) latestValues.put(role, value);
        }
        if (!enabled || role == null) return;
        synchronized (samplingLock) {
            CoherentAssembler.Frame frame = assembler.accept(role, value, System.nanoTime());
            if (frame != null) emit(frame);
        }
    }

    public RuntimeStats runtimeStats() {
        int subscribed;
        EnumMap<ChannelRole, String> names;
        EnumMap<ChannelRole, Double> values;
        long emitted;
        long incomplete;
        long duplicate;
        long quiet;
        long maxBurst;
        String required;
        synchronized (lock) {
            subscribed = subscribedChannels.size();
            names = new EnumMap<ChannelRole, String>(channelNames);
            values = new EnumMap<ChannelRole, Double>(latestValues);
        }
        synchronized (samplingLock) {
            emitted = assembler.emittedFrames();
            incomplete = assembler.incompleteFrames();
            duplicate = assembler.duplicateBoundaries();
            quiet = assembler.quietGapBoundaries();
            maxBurst = assembler.maxBurstSpanNano();
            required = assembler.requiredRolesText();
        }
        return new RuntimeStats(enabled, configurationName, subscribed, sampleRateHz,
                emitted, incomplete, duplicate, quiet, maxBurst, required, names, values, status);
    }

    public String runtimeDiagnosticsText() {
        RuntimeStats stats = runtimeStats();
        StringBuilder out = new StringBuilder();
        out.append("GUIDED LIVE CHANNEL RUNTIME\n")
                .append("===========================\n")
                .append("Project: ").append(stats.configurationName).append('\n')
                .append("Live source: ").append(stats.enabled ? "ACTIVE" : "SUSPENDED").append('\n')
                .append("Delivered coherent sample rate: ")
                .append(stats.sampleRateHz > 0.0
                        ? String.format(java.util.Locale.ROOT, "%.1f Hz", stats.sampleRateHz)
                        : "n/a").append('\n')
                .append("Subscribed live channels: ").append(stats.subscribedChannels).append('\n')
                .append("Coherent frames: ").append(stats.emittedFrames)
                .append(" emitted / ").append(stats.incompleteFrames).append(" incomplete dropped\n")
                .append("Frame boundaries: ").append(stats.duplicateBoundaries)
                .append(" repeated-role / ").append(stats.quietGapBoundaries).append(" quiet-gap\n")
                .append("Max callback burst span: ")
                .append(String.format(java.util.Locale.ROOT, "%.3f ms",
                        stats.maxBurstSpanNano / 1000000.0)).append('\n')
                .append("Coherence-required resolved roles: ").append(stats.requiredRoles).append('\n')
                .append("Status: ").append(stats.status).append("\n\n")
                .append("RESOLVED OUTPUT CHANNELS\n")
                .append("========================\n");
        for (ChannelRole role : ChannelRole.values()) {
            String name = stats.channelNames.get(role);
            if (name == null) continue;
            Double value = stats.latestValues.get(role);
            out.append(role.name()).append(" -> ").append(name).append(" = ")
                    .append(value == null || !Double.isFinite(value.doubleValue())
                            ? "n/a" : String.format(java.util.Locale.ROOT, "%.3f", value.doubleValue()))
                    .append('\n');
        }
        return out.toString();
    }

    private void emit(CoherentAssembler.Frame frame) {
        long now = frame.nanoTime;
        if (now - lastSampleNano < MIN_SAMPLE_GAP_NS) return;
        lastSampleNano = now;

        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(frame.values);
        if (!values.containsKey(ChannelRole.TPS)) return;
        double seconds = now / 1000000000.0;
        double tpsDot = 0.0;
        double mapDot = 0.0;
        if (previousSample != null) {
            double dt = Math.max(0.001, seconds - previousSample.getSeconds());
            double tps = value(values, ChannelRole.TPS);
            double previousTps = previousSample.get(ChannelRole.TPS);
            if (Double.isFinite(tps) && Double.isFinite(previousTps)) {
                tpsDot = (tps - previousTps) / dt;
            }
            double map = value(values, ChannelRole.MAP);
            double previousMap = previousSample.get(ChannelRole.MAP);
            if (Double.isFinite(map) && Double.isFinite(previousMap)) {
                mapDot = (map - previousMap) / dt;
            }
        }
        LiveSample sample = new LiveSample(now, seconds, values, tpsDot, mapDot);
        previousSample = sample;

        samplesInWindow++;
        if (lastRateWindowNano == 0L) {
            lastRateWindowNano = now;
        } else if (now - lastRateWindowNano >= 1000000000L) {
            sampleRateHz = samplesInWindow / ((now - lastRateWindowNano) / 1000000000.0);
            samplesInWindow = 0;
            lastRateWindowNano = now;
        }
        dispatcher.offer(sample);
    }

    private void resolveOutputChannels() throws ControllerException {
        String[] channels = outputChannelServer.getOutputChannels(configurationName);
        synchronized (lock) {
            channelNames.clear();
            availableOutputChannels.clear();
            if (channels != null) Collections.addAll(availableOutputChannels, channels);
            for (ChannelRole role : ChannelRole.values()) {
                String resolved = OutputChannelResolver.resolve(role, availableOutputChannels);
                if (resolved != null) channelNames.put(role, resolved);
            }
        }
    }

    private void subscribeResolvedChannels() throws ControllerException {
        EnumMap<ChannelRole, String> names;
        synchronized (lock) {
            subscribedChannels.clear();
            names = new EnumMap<ChannelRole, String>(channelNames);
        }
        synchronized (samplingLock) {
            assembler.configureResolvedRoles(names.keySet());
        }
        for (Map.Entry<ChannelRole, String> entry : names.entrySet()) {
            synchronized (lock) {
                subscribedChannels.put(entry.getValue(), entry.getKey());
            }
            outputChannelServer.subscribe(configurationName, entry.getValue(), this);
        }
    }

    private int subscribedCount() {
        synchronized (lock) { return subscribedChannels.size(); }
    }

    private static String findConfigurationName(ControllerAccess access) {
        String[] names = access.getEcuConfigurationNames();
        if (names != null) {
            for (String name : names) {
                if (name != null && name.length() > 0) return name;
            }
        }
        return "Main Controller";
    }

    private static double value(EnumMap<ChannelRole, Double> values, ChannelRole role) {
        Double value = values.get(role);
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().length() == 0
                ? throwable == null ? "unknown error" : throwable.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ');
    }

    public static final class RuntimeStats {
        public final boolean enabled;
        public final String configurationName;
        public final int subscribedChannels;
        public final double sampleRateHz;
        public final long emittedFrames;
        public final long incompleteFrames;
        public final long duplicateBoundaries;
        public final long quietGapBoundaries;
        public final long maxBurstSpanNano;
        public final String requiredRoles;
        public final EnumMap<ChannelRole, String> channelNames;
        public final EnumMap<ChannelRole, Double> latestValues;
        public final String status;

        RuntimeStats(boolean enabled, String configurationName, int subscribedChannels,
                     double sampleRateHz, long emittedFrames, long incompleteFrames,
                     long duplicateBoundaries, long quietGapBoundaries,
                     long maxBurstSpanNano, String requiredRoles,
                     EnumMap<ChannelRole, String> channelNames,
                     EnumMap<ChannelRole, Double> latestValues, String status) {
            this.enabled = enabled;
            this.configurationName = configurationName == null ? "unknown" : configurationName;
            this.subscribedChannels = subscribedChannels;
            this.sampleRateHz = sampleRateHz;
            this.emittedFrames = emittedFrames;
            this.incompleteFrames = incompleteFrames;
            this.duplicateBoundaries = duplicateBoundaries;
            this.quietGapBoundaries = quietGapBoundaries;
            this.maxBurstSpanNano = maxBurstSpanNano;
            this.requiredRoles = requiredRoles == null ? "[]" : requiredRoles;
            this.channelNames = channelNames;
            this.latestValues = latestValues;
            this.status = status == null ? "" : status;
        }
    }

    /** Reassembles per-channel callbacks into coherent ECU update batches. */
    private static final class CoherentAssembler {
        private static final long QUIET_GAP_NS = 4000000L;
        private static final ChannelRole[] CRITICAL_IF_RESOLVED = {
                ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP,
                ChannelRole.FALLBACK_MAP, ChannelRole.EFFECTIVE_MAP,
                ChannelRole.MAP_PRED_ACTIVE, ChannelRole.MAP_PRED_RESET_CNT,
                ChannelRole.MAP_PRED_EVENT_OVER, ChannelRole.SMOOTHED_DELTA_TPS,
                ChannelRole.ACCEL_THRESHOLD, ChannelRole.GEAR, ChannelRole.VSS
        };

        private final EnumMap<ChannelRole, Double> batchValues =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        private final EnumSet<ChannelRole> seen = EnumSet.noneOf(ChannelRole.class);
        private EnumSet<ChannelRole> required = EnumSet.of(
                ChannelRole.RPM, ChannelRole.TPS, ChannelRole.MAP);
        private long firstCallbackNano;
        private long lastCallbackNano;
        private long emittedFrames;
        private long incompleteFrames;
        private long duplicateBoundaries;
        private long quietGapBoundaries;
        private long maxBurstSpanNano;

        void configureResolvedRoles(Set<ChannelRole> resolved) {
            EnumSet<ChannelRole> next = EnumSet.noneOf(ChannelRole.class);
            if (resolved != null) {
                for (ChannelRole role : CRITICAL_IF_RESOLVED) {
                    if (resolved.contains(role)) next.add(role);
                }
            }
            next.add(ChannelRole.RPM);
            next.add(ChannelRole.TPS);
            next.add(ChannelRole.MAP);
            required = next;
            clearBatch();
        }

        Frame accept(ChannelRole role, double value, long nowNano) {
            if (role == null) return null;
            Frame completed = null;
            if (!seen.isEmpty()) {
                boolean duplicate = seen.contains(role);
                boolean quietGap = lastCallbackNano > 0L
                        && nowNano - lastCallbackNano > QUIET_GAP_NS;
                if (duplicate || quietGap) {
                    if (duplicate) duplicateBoundaries++;
                    else quietGapBoundaries++;
                    completed = finishBatch();
                }
            }
            if (seen.isEmpty()) firstCallbackNano = nowNano;
            batchValues.put(role, value);
            seen.add(role);
            lastCallbackNano = nowNano;
            return completed;
        }

        void reset() {
            clearBatch();
            emittedFrames = 0L;
            incompleteFrames = 0L;
            duplicateBoundaries = 0L;
            quietGapBoundaries = 0L;
            maxBurstSpanNano = 0L;
        }

        long emittedFrames() { return emittedFrames; }
        long incompleteFrames() { return incompleteFrames; }
        long duplicateBoundaries() { return duplicateBoundaries; }
        long quietGapBoundaries() { return quietGapBoundaries; }
        long maxBurstSpanNano() { return maxBurstSpanNano; }
        String requiredRolesText() { return required.toString(); }

        private Frame finishBatch() {
            long first = firstCallbackNano;
            long last = lastCallbackNano;
            long span = Math.max(0L, last - first);
            if (span > maxBurstSpanNano) maxBurstSpanNano = span;
            Frame frame = null;
            if (seen.containsAll(required)) {
                emittedFrames++;
                frame = new Frame(last, new EnumMap<ChannelRole, Double>(batchValues));
            } else {
                incompleteFrames++;
            }
            clearBatch();
            return frame;
        }

        private void clearBatch() {
            batchValues.clear();
            seen.clear();
            firstCallbackNano = 0L;
            lastCallbackNano = 0L;
        }

        static final class Frame {
            final long nanoTime;
            final EnumMap<ChannelRole, Double> values;
            Frame(long nanoTime, EnumMap<ChannelRole, Double> values) {
                this.nanoTime = nanoTime;
                this.values = values;
            }
        }
    }
}
