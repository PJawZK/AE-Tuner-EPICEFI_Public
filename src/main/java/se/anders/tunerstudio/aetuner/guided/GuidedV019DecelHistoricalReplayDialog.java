package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalPoolService;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalProgress;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalReplayService;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultModel;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposal;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalContext;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.concurrent.ExecutionException;

/** Result-first file picker/surface for historical Decel replay and compatible pooling. */
final class GuidedV019DecelHistoricalReplayDialog {
    private GuidedV019DecelHistoricalReplayDialog() { }

    static void chooseAndAnalyze(Component parent, AeProjectSnapshot snapshot) {
        chooseAndAnalyze(parent, snapshot, null);
    }

    static void chooseAndAnalyze(Component parent, AeProjectSnapshot snapshot,
                                 Runnable startMissingRegionCapture) {
        if (snapshot == null || !snapshot.hasDecelDetectionSettings()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Analyze Existing Log(s) — Decel Detection");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "TunerStudio native binary logs (*.mlg)", "mlg"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "AE Tuner / TunerStudio text logs (*.csv, *.txt, *.tsv)",
                "csv", "txt", "tsv"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files == null || files.length == 0) {
            File single = chooser.getSelectedFile();
            if (single == null) return;
            files = new File[]{single};
        }
        open(parent, snapshot, files, startMissingRegionCapture);
    }

    static void open(Component parent, AeProjectSnapshot snapshot, File file) {
        open(parent, snapshot, file, null);
    }

    static void open(Component parent, AeProjectSnapshot snapshot, File file,
                     Runnable startMissingRegionCapture) {
        open(parent, snapshot, file == null ? new File[0] : new File[]{file},
                startMissingRegionCapture);
    }

    interface AnalysisOperation {
        DecelHistoricalReplayService.Result run(File[] files, AeProjectSnapshot snapshot,
                                                DecelHistoricalProgress progress) throws Exception;
    }

    static void open(Component parent, AeProjectSnapshot snapshot, File[] selectedFiles,
                     Runnable startMissingRegionCapture) {
        open(parent, snapshot, selectedFiles, startMissingRegionCapture,
                (files, tune, progress) -> files.length > 1
                        ? DecelHistoricalPoolService.analyze(files, tune, progress)
                        : DecelHistoricalReplayService.analyze(files[0], tune, progress));
    }

    static void open(Component parent, AeProjectSnapshot snapshot, File[] selectedFiles,
                     Runnable startMissingRegionCapture, AnalysisOperation operation) {
        final File[] files = selectedFiles == null ? new File[0] : selectedFiles.clone();
        if (files.length == 0) return;
        DecelHistoricalResultContext.clear();
        DecelHistoricalThresholdProposalContext.clear();
        final File analysisSource = files.length == 1
                ? files[0] : new File(DecelHistoricalPoolService.sourceLabel(files));
        final boolean pooled = files.length > 1;

        final Window host = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog = createDialog(host, "Decel Detection — Historical Result");
        dialog.setLayout(new BorderLayout(0, 8));
        dialog.getContentPane().setBackground(AeUiTheme.background());

        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(10, 12, 0, 12));
        JLabel title = new JLabel(pooled
                ? "Decel Detection — compatible multi-log result"
                : "Decel Detection — existing-log result");
        title.setFont(new Font("Dialog", Font.BOLD, 20));
        title.setForeground(AeUiTheme.text());
        JLabel sub = new JLabel(pooled
                ? "Selected logs are pooled only after detector compatibility is proven. Physical events stay session-classified; no automatic Apply and no Burn."
                : "Result first: keep, change, or collect missing evidence. Full replay diagnostics remain available separately; no automatic Apply and no Burn.");
        sub.setFont(new Font("Dialog", Font.PLAIN, 11));
        sub.setForeground(AeUiTheme.muted());
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);
        dialog.add(head, BorderLayout.NORTH);

        final JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        final GuidedDecelAnalysisProgress progress = new GuidedDecelAnalysisProgress(files);
        content.add(progress, BorderLayout.CENTER);
        content.setBorder(new EmptyBorder(0, 12, 0, 12));
        dialog.add(content, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(0, 12, 10, 12));
        final JButton reviewProposal = new JButton("Review Proposed Threshold Changes");
        reviewProposal.setEnabled(false);
        final JButton captureMissing = new JButton("Capture Missing Regions");
        captureMissing.setEnabled(false);
        captureMissing.setVisible(false);
        final JButton technical = new JButton("Full Technical Report…");
        technical.setEnabled(false);
        JButton close = new JButton("Cancel Analysis");
        close.setToolTipText("Stop the background analysis without changing the Working Tune.");
        final boolean[] cancelRequested = {false};
        final DecelHistoricalThresholdProposal.Eligibility[] eligibility =
                new DecelHistoricalThresholdProposal.Eligibility[1];
        final DecelHistoricalResultModel[] resultModel = new DecelHistoricalResultModel[1];

        reviewProposal.addActionListener(e -> {
            DecelHistoricalThresholdProposal.Eligibility reviewed = eligibility[0];
            if (reviewed == null || !reviewed.promotable || reviewed.evidence == null) return;

            String reviewText = DecelHistoricalThresholdProposal.reviewChangesText(
                    snapshot, reviewed.evidence);
            JTextArea reviewArea = new JTextArea(reviewText, 24, 84);
            reviewArea.setEditable(false);
            reviewArea.setLineWrap(true);
            reviewArea.setWrapStyleWord(true);
            reviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            reviewArea.setCaretPosition(0);
            JScrollPane reviewScroll = new JScrollPane(reviewArea);
            reviewScroll.setPreferredSize(new Dimension(860, 560));

            int confirm = JOptionPane.showConfirmDialog(dialog, reviewScroll,
                    "Review Historical Decel Threshold Proposal — No Write Yet",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.OK_OPTION) return;

            DecelHistoricalThresholdProposal.Result staged =
                    DecelHistoricalThresholdProposalContext.stage(reviewed, snapshot);
            if (staged.plan == null) {
                JOptionPane.showMessageDialog(dialog, staged.reviewText,
                        "Historical Proposal Withheld", JOptionPane.INFORMATION_MESSAGE);
                reviewProposal.setEnabled(false);
                return;
            }
            JOptionPane.showMessageDialog(dialog,
                    "Historical threshold changes are staged for the existing Guided Review/Apply path.\n\n"
                            + "No controller write occurred here. Apply still requires the normal stale check, exact readback and Restore snapshot.",
                    "Historical Proposal Staged — Not Applied",
                    JOptionPane.INFORMATION_MESSAGE);
            reviewProposal.setEnabled(false);
            GuidedV019WorkflowNavigation.decelHistoricalProposalStaged();
            dialog.dispose();
        });

        captureMissing.addActionListener(e -> {
            DecelHistoricalResultModel model = resultModel[0];
            if (model == null || !model.targetedCaptureUseful
                    || startMissingRegionCapture == null) return;
            dialog.dispose();
            startMissingRegionCapture.run();
            GuidedV019WorkflowNavigation.decelTargetedCaptureStarted();
        });

        technical.addActionListener(e -> {
            DecelHistoricalResultModel model = resultModel[0];
            if (model != null) showTechnicalReport(dialog, model.technicalReport);
        });
        buttons.add(reviewProposal);
        buttons.add(captureMissing);
        buttons.add(technical);
        buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);

        dialog.setMinimumSize(new Dimension(900, 560));
        dialog.setSize(1100, 720);
        dialog.setLocationRelativeTo(parent);

        SwingWorker<AnalysisOutcome, Void> worker =
                new SwingWorker<AnalysisOutcome, Void>() {
                    @Override protected AnalysisOutcome doInBackground()
                            throws Exception {
                        DecelHistoricalReplayService.Result result = operation.run(files, snapshot, progress);
                        DecelHistoricalProgress.report(progress, null, "Preparing result", -1);
                        DecelHistoricalThresholdProposal.Eligibility reviewed = result == null ? null
                                : DecelHistoricalThresholdProposal.reviewEligibility(analysisSource, snapshot, result);
                        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(
                                analysisSource, snapshot, result, reviewed);
                        DecelHistoricalProgress.report(progress, null, "Result ready", -1);
                        return new AnalysisOutcome(reviewed, model);
                    }

                    @Override protected void done() {
                        if (!dialog.isDisplayable()) { progress.close(); return; }
                        close.setText("Close");
                        close.setToolTipText("Close this result window.");
                        if (isCancelled() || cancelRequested[0]) { progress.cancelled(); return; }
                        try {
                            AnalysisOutcome outcome = get();
                            DecelHistoricalThresholdProposal.Eligibility reviewed = outcome.eligibility;
                            eligibility[0] = reviewed;
                            DecelHistoricalResultModel model = outcome.model;
                            resultModel[0] = model;
                            DecelHistoricalResultContext.publish(model);
                            GuidedV019WorkflowNavigation.decelHistoricalResult();

                            content.removeAll();
                            content.add(progress.completed(model.validatedBins > 0), BorderLayout.NORTH);
                            content.add(GuidedV019DecelResultView.create(model), BorderLayout.CENTER);
                            content.revalidate();
                            content.repaint();

                            reviewProposal.setEnabled(model.proposalAvailable
                                    && reviewed != null && reviewed.promotable);
                            captureMissing.setVisible(model.targetedCaptureUseful);
                            captureMissing.setEnabled(model.targetedCaptureUseful
                                    && startMissingRegionCapture != null);
                            if (model.targetedCaptureUseful && startMissingRegionCapture == null) {
                                captureMissing.setToolTipText("Return to the main Decel Capture stage to start a targeted live follow-up for "
                                        + model.missingBinsText() + " RPM.");
                            }
                            technical.setEnabled(model.technicalReport.length() > 0);
                        } catch (java.util.concurrent.CancellationException ex) {
                            progress.cancelled();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            progress.failed(ex);
                        } catch (ExecutionException ex) {
                            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                            if (cause instanceof java.util.concurrent.CancellationException) progress.cancelled();
                            else progress.failed(cause);
                        }
                    }
                };
        close.addActionListener(e -> {
            if (progress.active()) {
                cancelRequested[0] = true;
                worker.cancel(true);
                progress.cancelled();
                close.setText("Close");
                close.setToolTipText("Close this cancelled analysis.");
            } else dialog.dispose();
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { progress.close(); worker.cancel(true); }
        });
        worker.execute();
        dialog.setVisible(true);
    }

    private static final class AnalysisOutcome {
        final DecelHistoricalThresholdProposal.Eligibility eligibility;
        final DecelHistoricalResultModel model;
        AnalysisOutcome(DecelHistoricalThresholdProposal.Eligibility eligibility,
                        DecelHistoricalResultModel model) { this.eligibility=eligibility; this.model=model; }
    }

    private static void showTechnicalReport(Component parent, String text) {
        final JDialog reportDialog = createDialog(
                javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Decel Detection — Full Technical Report");
        reportDialog.setLayout(new BorderLayout(0, 8));
        reportDialog.getContentPane().setBackground(AeUiTheme.background());
        final JTextArea report = new JTextArea(text == null ? "" : text);
        report.setEditable(false);
        report.setLineWrap(false);
        report.setFont(new Font("Monospaced", Font.PLAIN, 12));
        report.setBackground(AeUiTheme.card());
        report.setForeground(AeUiTheme.text());
        report.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(report);
        scroll.setBorder(new EmptyBorder(10, 10, 0, 10));
        reportDialog.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 10, 10, 10));
        JButton copy = new JButton("Copy Report");
        JButton close = new JButton("Close");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(report.getText()), null));
        close.addActionListener(e -> reportDialog.dispose());
        footer.add(copy);
        footer.add(close);
        reportDialog.add(footer, BorderLayout.SOUTH);
        reportDialog.setMinimumSize(new Dimension(820, 520));
        reportDialog.setSize(1060, 720);
        reportDialog.setLocationRelativeTo(parent);
        reportDialog.setVisible(true);
    }

    private static JDialog createDialog(Window host, String title) {
        return GuidedTaskControls.install(GuidedDialogLifecycle.create(host, title, true));
    }
}
