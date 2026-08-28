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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

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
    private volatile double lastSessionExportMillis = Double.NaN;
    private volatile boolean exportInProgress;

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
        showNotes(MapPredictReportBuilder.BLEND_DURATION_WITHHELD_TEXT
                + "\n\nNo Blend Duration values were copied to the clipboard.", true);
    }

    double saveMapPredictReport(final String configurationName,
                                final AeProjectSnapshot projectSnapshot,
                                final List<TransientEvent> events,
                                final EnumMap<ChannelRole, String> selectedChannels,
                                final EnumMap<ChannelRole, Double> latestChannelValues,
                                final double sampleRateHz,
                                final long sessionStartedNano,
                                final double lastCsvExportMillis,
                                final double previousReportDurationMillis) {
        if (exportInProgress) {
            showNotes("Passive Session export is already in progress.\n"
                    + "Wait for the completion or failure message before starting another export.",
                    true);
            return previousReportDurationMillis;
        }

        final File parentFolder = SessionExportSupport.chooseParent(parent, "Passive");
        if (parentFolder == null) {
            return previousReportDurationMillis;
        }

        final int minimum = minimumSamples();
        final double cap = mapCap();

        // The visible chooser is DIRECTORIES_ONLY. A non-directory selection can
        // therefore only come from a programmatic/legacy caller. Preserve that
        // non-UI path so existing integrations can still request one report file,
        // while normal users always get the new session-folder export below.
        if (!parentFolder.isDirectory()) {
            long started = System.nanoTime();
            try {
                String report = buildReport(configurationName, projectSnapshot,
                        events, selectedChannels, latestChannelValues,
                        sampleRateHz, sessionStartedNano, lastCsvExportMillis,
                        previousReportDurationMillis, minimum, cap, started);
                SessionExportSupport.writeTextAtomic(parentFolder, report);
                double elapsed = SessionExportSupport.elapsedMillis(started);
                lastSessionExportMillis = elapsed;
                showNotes("Passive report compatibility export complete.\n"
                        + parentFolder.getAbsolutePath() + "\n"
                        + F1.format(elapsed) + " ms", true);
                return elapsed;
            } catch (IOException ex) {
                showNotes("Passive report compatibility export failed: "
                        + ex.getMessage(), true);
                return previousReportDurationMillis;
            }
        }

        // Freeze all user-visible export evidence before the background worker
        // starts. Live capture may continue, but this export remains one coherent
        // point-in-time Passive session snapshot.
        final List<TransientEvent> eventSnapshot = events == null
                ? new ArrayList<TransientEvent>()
                : new ArrayList<TransientEvent>(events);
        final EnumMap<ChannelRole, String> selectedChannelSnapshot =
                new EnumMap<ChannelRole, String>(ChannelRole.class);
        if (selectedChannels != null) selectedChannelSnapshot.putAll(selectedChannels);
        final EnumMap<ChannelRole, Double> latestChannelSnapshot =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        if (latestChannelValues != null) latestChannelSnapshot.putAll(latestChannelValues);
        final double priorReportDuration = Double.isFinite(lastSessionExportMillis)
                ? lastSessionExportMillis : previousReportDurationMillis;
        final SessionDiagnostics diagnosticsSnapshot = SessionDiagnostics.build(
                sessionStartedNano, System.nanoTime(), eventSnapshot,
                eventDetector.getRingSampleCount(), eventDetector.getActiveSampleCount(),
                mapEstimateCollector.getAcceptedSamples(), recommendationHistory.size(),
                lastCsvExportMillis, priorReportDuration);
        final MapEstimateSuggestion mapSuggestionSnapshot = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        final MapBlendSuggestion blendSuggestionSnapshot = MapBlendSuggestion.build(
                projectSnapshot, eventSnapshot);
        final SessionReview reviewSnapshot = SessionReview.build(
                eventSnapshot, sessionMonitor.snapshot());

        exportInProgress = true;
        showNotes("Exporting Passive Session...\n"
                + "A fixed session snapshot has been captured.\n"
                + "Writing report and structured evidence in the background.\n"
                + "Do not close TunerStudio until the export reports completion.", true);

        new SwingWorker<PassiveExportResult, Void>() {
            @Override
            protected PassiveExportResult doInBackground() throws Exception {
                long exportStartedNano = System.nanoTime();
                SessionExportSupport.StagedFolder staged = null;
                try {
                    String report = MapPredictReportBuilder.build(
                            configurationName, eventSnapshot, sampleRateHz,
                            minimum, cap, diagnosticsSnapshot,
                            mapSuggestionSnapshot, blendSuggestionSnapshot,
                            selectedChannelSnapshot, latestChannelSnapshot,
                            reviewSnapshot, exportStartedNano);

                    staged = SessionExportSupport.stageSessionFolder(
                            parentFolder, "passive");
                    SessionExportSupport.writeTextAtomic(
                            staged.file("passive-report.txt"), report);
                    int fileCount = 1;
                    if (!eventSnapshot.isEmpty()) {
                        SessionExportSupport.writeEventsCsvAtomic(
                                staged.file("passive-events.csv"), eventSnapshot);
                        fileCount++;
                    }
                    File folder = staged.finish();
                    double elapsed = SessionExportSupport.elapsedMillis(exportStartedNano);
                    lastSessionExportMillis = elapsed;
                    return new PassiveExportResult(folder, fileCount, elapsed,
                            eventSnapshot.size());
                } catch (Exception ex) {
                    if (staged != null) staged.cleanup();
                    throw ex;
                }
            }

            @Override
            protected void done() {
                exportInProgress = false;
                try {
                    PassiveExportResult result = get();
                    showNotes("Passive Session export complete.\n"
                            + result.eventCount + " captured event(s) | "
                            + result.fileCount + " file(s) | "
                            + F1.format(result.elapsedMillis) + " ms\n"
                            + result.folder.getAbsolutePath(), true);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showNotes("Passive Session export failed: "
                            + cause.getMessage()
                            + "\nIncomplete data was not promoted to a final session folder.",
                            true);
                }
            }
        }.execute();
        return previousReportDurationMillis;
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
        int minimum = minimumSamples();
        double cap = mapCap();
        String report = buildReport(configurationName, projectSnapshot, events,
                selectedChannels, latestChannelValues, sampleRateHz,
                sessionStartedNano, lastCsvExportMillis, lastReportExportMillis,
                minimum, cap, exportStartedNano);
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

    private String buildReport(String configurationName,
                               AeProjectSnapshot projectSnapshot,
                               List<TransientEvent> events,
                               EnumMap<ChannelRole, String> selectedChannels,
                               EnumMap<ChannelRole, Double> latestChannelValues,
                               double sampleRateHz,
                               long sessionStartedNano,
                               double lastCsvExportMillis,
                               double previousReportDurationMillis,
                               int minimum,
                               double cap,
                               long exportStartedNano) {
        double priorReportDuration = Double.isFinite(lastSessionExportMillis)
                ? lastSessionExportMillis : previousReportDurationMillis;
        SessionDiagnostics diagnostics = SessionDiagnostics.build(
                sessionStartedNano, System.nanoTime(), events,
                eventDetector.getRingSampleCount(), eventDetector.getActiveSampleCount(),
                mapEstimateCollector.getAcceptedSamples(), recommendationHistory.size(),
                lastCsvExportMillis, priorReportDuration);
        MapEstimateSuggestion mapSuggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        MapBlendSuggestion blendSuggestion = MapBlendSuggestion.build(
                projectSnapshot, events);
        SessionReview review = SessionReview.build(events, sessionMonitor.snapshot());
        return MapPredictReportBuilder.build(
                configurationName, events, sampleRateHz, minimum, cap,
                diagnostics, mapSuggestion, blendSuggestion,
                selectedChannels, latestChannelValues, review,
                exportStartedNano);
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

    private static final class PassiveExportResult {
        final File folder;
        final int fileCount;
        final double elapsedMillis;
        final int eventCount;

        PassiveExportResult(File folder, int fileCount,
                            double elapsedMillis, int eventCount) {
            this.folder = folder;
            this.fileCount = fileCount;
            this.elapsedMillis = elapsedMillis;
            this.eventCount = eventCount;
        }
    }
}
