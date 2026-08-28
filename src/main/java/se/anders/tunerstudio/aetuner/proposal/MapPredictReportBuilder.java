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

/** Builds the non-writing Passive session report without owning Swing state. */
public final class MapPredictReportBuilder {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    public static final String BLEND_DURATION_WITHHELD_TEXT =
            "Numerical Predictive MAP Blend Duration drafting is intentionally WITHHELD.\n"
            + "The legacy passive measured-MAP catch-up/T90 conversion was retired after "
            + "firmware-faithful review showed that fallbackMap can rise repeatedly and reset "
            + "the active blend origin during one opening.\n"
            + "Use Guided Tuning model-validation evidence (fallbackMap, Effective MAP, current-RPM "
            + "curve interpolation and prediction lifecycle evidence) before any new numerical "
            + "Blend Duration proposal is allowed.\n"
            + "Current working Blend Duration values are not changed by this Passive report.";

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
        text.append("AE Tuner (EPICEFI) Passive Session report\n")
                .append("Plugin version: ").append(AeTunerPlugin.VERSION).append("\n")
                .append("Created: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n")
                .append("Project: ").append(configurationName == null ? "unknown" : configurationName).append("\n")
                .append("Passive report/export action is non-writing. Read-only report: no ECU values were changed.\n")
                .append("\n============================================================\n")
                .append("SESSION SUMMARY\n")
                .append("============================================================\n")
                .append("Captured events: ").append(events.size()).append("\n")
                .append("Live sample rate at export: ")
                .append(sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a").append("\n")
                .append("MAP Estimate minimum samples/cell: ").append(minimumMapSamples).append("\n")
                .append("Turbo MAP cap: ").append(F1.format(mapCap)).append(" kPa from 33.5% TPS\n")
                .append("\n============================================================\n")
                .append("TUNING RESULTS / COPY-PASTE DATA\n")
                .append("============================================================\n")
                .append("\nMAP ESTIMATE DRAFT\n")
                .append("------------------\n")
                .append(mapSuggestion.getDisplayText()).append("\n\n")
                .append("TunerStudio copy/paste block (descending TPS row order):\n")
                .append(mapSuggestion.isAvailable() ? mapSuggestion.getCopyPasteBlock() : "Unavailable").append("\n")
                .append("\nPREDICTIVE MAP BLEND DURATION MODEL STATUS\n")
                .append("------------------------------------------\n")
                .append(BLEND_DURATION_WITHHELD_TEXT).append("\n\n")
                .append("Blend Duration clipboard block: Unavailable while model validation is in progress.\n")
                .append("\n============================================================\n")
                .append("SESSION REVIEW\n")
                .append("============================================================\n")
                .append(review.toDisplayText()).append("\n")
                .append("\nReport preparation duration before file write: ")
                .append(F1.format(elapsedMillis(preparationStartedNano))).append(" ms\n")
                .append("\n============================================================\n")
                .append("DIAGNOSTICS\n")
                .append("============================================================\n")
                .append("\nSESSION DIAGNOSTICS\n")
                .append("-------------------\n")
                .append(diagnostics.toReportText()).append("\n")
                .append("\nCRITICAL OUTPUT-CHANNEL RESOLUTION\n")
                .append("----------------------------------\n")
                .append(ChannelResolutionEvidence.build(selectedChannels, latestChannelValues)).append("\n");
        return text.toString();
    }

    private static double elapsedMillis(long startedNano) {
        return Math.max(0L, System.nanoTime() - startedNano) / 1000000.0;
    }
}
