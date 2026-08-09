package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lightweight, read-only diagnostics for understanding long-session retention. */
public final class SessionDiagnostics {
    final double elapsedSeconds;
    final int totalEvents;
    final int acceptedEvents;
    final int rejectedEvents;
    final long retainedSamples;
    final int minimumSamplesPerEvent;
    final int maximumSamplesPerEvent;
    final double meanSamplesPerEvent;
    final int detectorRingSamples;
    final int activeEventSamples;
    final long mapEstimateAcceptedSamples;
    final int guidanceEntries;
    final double previousCsvExportMillis;
    final double previousReportExportMillis;
    final String eventTypes;

    private SessionDiagnostics(double elapsedSeconds,
                               int totalEvents,
                               int acceptedEvents,
                               int rejectedEvents,
                               long retainedSamples,
                               int minimumSamplesPerEvent,
                               int maximumSamplesPerEvent,
                               double meanSamplesPerEvent,
                               int detectorRingSamples,
                               int activeEventSamples,
                               long mapEstimateAcceptedSamples,
                               int guidanceEntries,
                               double previousCsvExportMillis,
                               double previousReportExportMillis,
                               String eventTypes) {
        this.elapsedSeconds = elapsedSeconds;
        this.totalEvents = totalEvents;
        this.acceptedEvents = acceptedEvents;
        this.rejectedEvents = rejectedEvents;
        this.retainedSamples = retainedSamples;
        this.minimumSamplesPerEvent = minimumSamplesPerEvent;
        this.maximumSamplesPerEvent = maximumSamplesPerEvent;
        this.meanSamplesPerEvent = meanSamplesPerEvent;
        this.detectorRingSamples = detectorRingSamples;
        this.activeEventSamples = activeEventSamples;
        this.mapEstimateAcceptedSamples = mapEstimateAcceptedSamples;
        this.guidanceEntries = guidanceEntries;
        this.previousCsvExportMillis = previousCsvExportMillis;
        this.previousReportExportMillis = previousReportExportMillis;
        this.eventTypes = eventTypes;
    }

    static SessionDiagnostics build(long sessionStartedNano,
                                    long nowNano,
                                    List<TransientEvent> events,
                                    int detectorRingSamples,
                                    int activeEventSamples,
                                    long mapEstimateAcceptedSamples,
                                    int guidanceEntries,
                                    double previousCsvExportMillis,
                                    double previousReportExportMillis) {
        int accepted = 0;
        int rejected = 0;
        long retained = 0L;
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        Map<String, Integer> types = new TreeMap<String, Integer>();
        if (events != null) {
            for (TransientEvent event : events) {
                if (event == null) {
                    continue;
                }
                if (event.isAccepted()) {
                    accepted++;
                } else {
                    rejected++;
                }
                int samples = event.getSamples().size();
                retained += samples;
                minimum = Math.min(minimum, samples);
                maximum = Math.max(maximum, samples);
                String type = event.getEventClass();
                Integer count = types.get(type);
                types.put(type, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        int total = accepted + rejected;
        if (total == 0) {
            minimum = 0;
        }
        double elapsed = sessionStartedNano > 0L && nowNano >= sessionStartedNano
                ? (nowNano - sessionStartedNano) / 1000000000.0 : 0.0;
        double mean = total == 0 ? 0.0 : retained / (double) total;
        return new SessionDiagnostics(elapsed, total, accepted, rejected, retained,
                minimum, maximum, mean, detectorRingSamples, activeEventSamples,
                mapEstimateAcceptedSamples, guidanceEntries,
                previousCsvExportMillis, previousReportExportMillis, types.toString());
    }

    public String toReportText() {
        StringBuilder text = new StringBuilder();
        text.append("Session elapsed: ").append(format(elapsedSeconds)).append(" s\n")
                .append("Events: ").append(totalEvents).append(" total; ")
                .append(acceptedEvents).append(" accepted; ")
                .append(rejectedEvents).append(" rejected\n")
                .append("Event types: ").append(eventTypes).append("\n")
                .append("Retained event samples: ").append(retainedSamples)
                .append(" total; min/mean/max per event ")
                .append(minimumSamplesPerEvent).append("/")
                .append(format(meanSamplesPerEvent)).append("/")
                .append(maximumSamplesPerEvent).append("\n")
                .append("Detector buffers now: ring ").append(detectorRingSamples)
                .append("; active event ").append(activeEventSamples).append(" sample(s)\n")
                .append("MAP Estimate accepted samples: ").append(mapEstimateAcceptedSamples).append("\n")
                .append("Session Guidance entries: ").append(guidanceEntries).append("\n")
                .append("Previous completed CSV export: ").append(formatMillis(previousCsvExportMillis)).append("\n")
                .append("Previous completed report export: ").append(formatMillis(previousReportExportMillis)).append("\n")
                .append("Plugin version: ").append(AeTunerPlugin.VERSION).append("\n")
                .append("Java runtime: ").append(System.getProperty("java.runtime.version", "unknown"))
                .append("; VM ").append(System.getProperty("java.vm.name", "unknown"));
        return text.toString();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String formatMillis(double value) {
        return Double.isFinite(value) ? format(value) + " ms" : "n/a";
    }
}
