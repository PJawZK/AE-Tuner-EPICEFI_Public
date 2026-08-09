package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;

/** Builds the read-only combined report without owning file chooser or panel state. */
public final class MapPredictReportBuilder {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");

    private MapPredictReportBuilder() {
    }

    public static String build(String configurationName,
                        List<TransientEvent> events,
                        double sampleRateHz,
                        int minimumMapSamples,
                        double mapCap,
                        SessionDiagnostics diagnostics,
                        MapEstimateSuggestion mapSuggestion,
                        MapBlendSuggestion blendSuggestion,
                        EnumMap<ChannelRole, String> selectedChannels,
                        EnumMap<ChannelRole, Double> latestChannelValues,
                        SessionReview review,
                        long preparationStartedNano) {
        StringBuilder text = new StringBuilder();
        text.append("AE Tuner (EPICEFI) MAP Predict report\n")
                .append("Plugin version: ").append(AeTunerPlugin.VERSION).append("\n")
                .append("Created: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n")
                .append("Project: ").append(configurationName == null ? "unknown" : configurationName).append("\n")
                .append("Captured events: ").append(events.size()).append("\n")
                .append("Live sample rate at save: ")
                .append(sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a").append("\n")
                .append("MAP draft minimum samples/cell: ").append(minimumMapSamples).append("\n")
                .append("Turbo MAP cap: ").append(F1.format(mapCap)).append(" kPa from 33.5% TPS\n")
                .append("Read-only report: no ECU values were changed.\n")
                .append("\n============================================================\n")
                .append("SESSION DIAGNOSTICS\n")
                .append("============================================================\n")
                .append(diagnostics.toReportText()).append("\n")
                .append("\n============================================================\n")
                .append("MAP ESTIMATE DRAFT\n")
                .append("============================================================\n")
                .append(mapSuggestion.getDisplayText()).append("\n\n")
                .append("TunerStudio copy/paste block (descending TPS row order):\n")
                .append(mapSuggestion.isAvailable() ? mapSuggestion.getCopyPasteBlock() : "Unavailable").append("\n")
                .append("\n============================================================\n")
                .append("PREDICTIVE MAP BLEND DURATION DRAFT\n")
                .append("============================================================\n")
                .append(blendSuggestion.getDisplayText()).append("\n\n")
                .append("TunerStudio copy/paste block (ascending RPM order):\n")
                .append(blendSuggestion.isAvailable() ? blendSuggestion.getCopyPasteBlock() : "Unavailable").append("\n")
                .append("\n============================================================\n")
                .append("CRITICAL OUTPUT-CHANNEL RESOLUTION\n")
                .append("============================================================\n")
                .append(ChannelResolutionEvidence.build(selectedChannels, latestChannelValues)).append("\n")
                .append("============================================================\n")
                .append("SESSION REVIEW\n")
                .append("============================================================\n")
                .append(review.toDisplayText()).append("\n")
                .append("\nReport preparation duration before file write: ")
                .append(F1.format(elapsedMillis(preparationStartedNano))).append(" ms\n");
        return text.toString();
    }

    private static double elapsedMillis(long startedNano) {
        return Math.max(0L, System.nanoTime() - startedNano) / 1000000.0;
    }
}
