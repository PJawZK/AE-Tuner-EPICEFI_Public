package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Read-only export, clipboard, proposal and recovery actions for the passive
 * AE Tuner surface.
 *
 * The host panel still owns buttons and session/runtime state. This collaborator
 * owns only the expensive/report-oriented action implementation so the live
 * TunerStudio callback path remains visually and structurally separate from
 * file choosers, report assembly and clipboard work.
 */
final class PassiveAdvisoryActions {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");

    private final JComponent parent;
    private final JTextArea notes;
    private final JTabbedPane lowerTabs;
    private final JSpinner mapMinimumSamples;
    private final JTextField mapCapField;
    private final MapEstimateCollector mapEstimateCollector;
    private final SessionMonitor sessionMonitor;
    private final AeEventDetector eventDetector;
    private final RecommendationHistory recommendationHistory;

    PassiveAdvisoryActions(JComponent parent,
                           JTextArea notes,
                           JTabbedPane lowerTabs,
                           JSpinner mapMinimumSamples,
                           JTextField mapCapField,
                           MapEstimateCollector mapEstimateCollector,
                           SessionMonitor sessionMonitor,
                           AeEventDetector eventDetector,
                           RecommendationHistory recommendationHistory) {
        this.parent = parent;
        this.notes = notes;
        this.lowerTabs = lowerTabs;
        this.mapMinimumSamples = mapMinimumSamples;
        this.mapCapField = mapCapField;
        this.mapEstimateCollector = mapEstimateCollector;
        this.sessionMonitor = sessionMonitor;
        this.eventDetector = eventDetector;
        this.recommendationHistory = recommendationHistory;
    }

    double saveCsv(List<TransientEvent> events, double previousDurationMillis) {
        if (events == null || events.isEmpty()) {
            showNotes("No events captured yet, nothing to save.", true);
            return previousDurationMillis;
        }

        File file = AdvisoryExportCoordinator.chooseCsvTarget(parent);
        if (file == null) {
            return previousDurationMillis;
        }

        long exportStartedNano = System.nanoTime();
        try {
            AdvisoryExportCoordinator.writeCsv(file, events);
            double elapsed = AdvisoryExportCoordinator.elapsedMillis(exportStartedNano);
            showNotes("Saved " + events.size() + " event(s) to "
                    + file.getAbsolutePath() + "\nCSV write duration: "
                    + F1.format(elapsed) + " ms", true);
            return elapsed;
        } catch (IOException ex) {
            showNotes("CSV save failed: " + ex.getMessage(), true);
            return previousDurationMillis;
        }
    }

    void copySuggestedAeTable(AeProjectSnapshot projectSnapshot,
                              List<TransientEvent> events) {
        AeTableSuggestion suggestion = AeTableSuggestion.build(projectSnapshot, events);
        showNotes(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    void copySuggestedMapEstimate(AeProjectSnapshot projectSnapshot) {
        MapEstimateSuggestion suggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector,
                minimumSamples(), mapCap());
        showNotes(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    void copySuggestedBlendDuration(AeProjectSnapshot projectSnapshot,
                                    List<TransientEvent> events) {
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(
                projectSnapshot, events);
        showNotes(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    double saveMapPredictReport(String configurationName,
                                AeProjectSnapshot projectSnapshot,
                                List<TransientEvent> events,
                                EnumMap<ChannelRole, String> selectedChannels,
                                EnumMap<ChannelRole, Double> latestChannelValues,
                                double sampleRateHz,
                                long sessionStartedNano,
                                double lastCsvExportMillis,
                                double previousReportDurationMillis) {
        File file = AdvisoryExportCoordinator.chooseReportTarget(parent);
        if (file == null) {
            return previousReportDurationMillis;
        }

        long exportStartedNano = System.nanoTime();
        SessionDiagnostics diagnostics = SessionDiagnostics.build(
                sessionStartedNano, System.nanoTime(), events,
                eventDetector.getRingSampleCount(), eventDetector.getActiveSampleCount(),
                mapEstimateCollector.getAcceptedSamples(), recommendationHistory.size(),
                lastCsvExportMillis, previousReportDurationMillis);
        int minimum = minimumSamples();
        double cap = mapCap();
        MapEstimateSuggestion mapSuggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        MapBlendSuggestion blendSuggestion = MapBlendSuggestion.build(
                projectSnapshot, events);
        SessionReview review = SessionReview.build(events, sessionMonitor.snapshot());

        String text = MapPredictReportBuilder.build(
                configurationName, events, sampleRateHz, minimum, cap, diagnostics,
                mapSuggestion, blendSuggestion, selectedChannels, latestChannelValues,
                review, exportStartedNano);

        try {
            AdvisoryExportCoordinator.writeReport(file, text);
            double elapsed = AdvisoryExportCoordinator.elapsedMillis(exportStartedNano);
            showNotes("Saved combined MAP Estimate, Blend Duration, and session review report to\n"
                    + file.getAbsolutePath()
                    + "\nReport generation and write duration: "
                    + F1.format(elapsed) + " ms", true);
            return elapsed;
        } catch (IOException ex) {
            showNotes("MAP Predict report save failed: " + ex.getMessage(), true);
            return previousReportDurationMillis;
        }
    }

    EvidenceRecoverySnapshot.Passive recoverySnapshot(
            String configurationName,
            AeProjectSnapshot projectSnapshot,
            List<TransientEvent> events,
            EnumMap<ChannelRole, String> selectedChannels,
            EnumMap<ChannelRole, Double> latestChannelValues,
            long eventRevision,
            double sampleRateHz,
            long sessionStartedNano,
            double lastCsvExportMillis,
            double lastReportExportMillis) {
        long mapSamples = mapEstimateCollector.getAcceptedSamples();
        if ((events == null || events.isEmpty()) && mapSamples <= 0
                && eventDetector.getRingSampleCount() <= 0) {
            return null;
        }
        long exportStartedNano = System.nanoTime();
        SessionDiagnostics diagnostics = SessionDiagnostics.build(
                sessionStartedNano, System.nanoTime(), events,
                eventDetector.getRingSampleCount(), eventDetector.getActiveSampleCount(),
                mapSamples, recommendationHistory.size(),
                lastCsvExportMillis, lastReportExportMillis);
        int minimum = minimumSamples();
        double cap = mapCap();
        MapEstimateSuggestion mapSuggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        MapBlendSuggestion blendSuggestion = MapBlendSuggestion.build(
                projectSnapshot, events);
        SessionReview review = SessionReview.build(events, sessionMonitor.snapshot());
        String report = MapPredictReportBuilder.build(
                configurationName, events, sampleRateHz, minimum, cap, diagnostics,
                mapSuggestion, blendSuggestion, selectedChannels, latestChannelValues,
                review, exportStartedNano);
        String key = EvidenceRecoverySnapshot.safeKey(
                (configurationName == null ? "controller" : configurationName)
                        + "-" + Long.toHexString(sessionStartedNano),
                "passive-session");
        return new EvidenceRecoverySnapshot.Passive(
                key, eventRevision, events, report);
    }

    int minimumSamples() {
        return ((Number) mapMinimumSamples.getValue()).intValue();
    }

    double mapCap() {
        try {
            double value = Double.parseDouble(
                    mapCapField.getText().trim().replace(',', '.'));
            return Math.max(90.0, Math.min(180.0, value));
        } catch (NumberFormatException ex) {
            return 115.0;
        }
    }

    private void copyToClipboard(String text) {
        String error = AdvisoryExportCoordinator.copyToClipboard(text);
        if (error != null) {
            notes.append("\n\nCould not copy to clipboard: " + error);
        }
    }

    private void showNotes(String text, boolean showNotesTab) {
        notes.setText(text == null ? "" : text);
        notes.setCaretPosition(0);
        if (showNotesTab && lowerTabs.getTabCount() > 1) {
            lowerTabs.setSelectedIndex(1);
        }
    }
}
