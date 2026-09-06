package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationAutomation;
import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationEngine;
import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationLabModel;
import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationReport;
import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets;
import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.proposal.SessionExportSupport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary developer UI over the permanent 816-target physical validation
 * inventory and the production Apply/Restore engine.
 */
public final class AeApplyRestoreValidationLabPanel extends JPanel {
    private final AeApplyRestoreValidationLabModel model;
    private final AeApplyRestoreValidationAutomation automation;
    private final JTree targetTree = new JTree();
    private final JLabel summary = new JLabel();
    private final JLabel automationState = new JLabel("Automation idle.");
    private final JButton autoRun = new JButton("Auto Validate Remaining");
    private final JButton autoStop = new JButton("Stop After Current");
    private final JButton exportResults = new JButton("Export Validation Results...");
    private final JLabel targetName = new JLabel("Select a physical target.");
    private final JLabel representation = new JLabel("Representation: n/a");
    private final JLabel original = new JLabel("Original: n/a");
    private final JLabel applyReadback = new JLabel("Apply readback: n/a");
    private final JLabel restoreReadback = new JLabel("Restore readback: n/a");
    private final JLabel engineState = new JLabel("No target selected.");
    private final JPanel valueEditorHost = new JPanel(new BorderLayout());
    private final JButton apply = new JButton("Apply Temporary Value");
    private final JButton restore = new JButton("Restore Exact Original");
    private final JComboBox<AeApplyRestoreValidationLabModel.ValidationStatus> markStatus =
            new JComboBox<AeApplyRestoreValidationLabModel.ValidationStatus>(
                    AeApplyRestoreValidationLabModel.ValidationStatus.values());
    private final JTextField note = new JTextField(36);
    private final JButton record = new JButton("Record Status / Note");
    private final JTextArea message = new JTextArea(6, 60);

    private JSpinner numericEditor;
    private JComboBox<OptionChoice> optionEditor;
    private TreePath acceptedSelection;
    private boolean revertingSelection;
    private Timer automationTimer;
    private boolean automationRunning;
    private boolean automationStopRequested;
    private int automationProcessed;
    private int automationInitialRemaining;

    public AeApplyRestoreValidationLabPanel(AeApplyRestoreValidationLabModel model) {
        super(new BorderLayout(10, 10));
        if (model == null) throw new IllegalArgumentException("validation Lab model is required");
        this.model = model;
        this.automation = new AeApplyRestoreValidationAutomation(model);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        rebuildTree();
        refreshSummary();
        refreshControls();
    }

    private void buildUi() {
        JPanel intro = new JPanel(new BorderLayout(4, 4));
        JLabel warning = new JLabel(
                "APPLY/RESTORE VALIDATION LAB — TEMPORARY DEVELOPER TOOL — WORKING TUNE ONLY — NO BURN");
        warning.setFont(warning.getFont().deriveFont(Font.BOLD));
        intro.add(warning, BorderLayout.NORTH);
        JLabel instruction = new JLabel(
                "Manual or automatic: exactly one physical target at a time → Apply/readback → Restore/readback → final result.");
        intro.add(instruction, BorderLayout.CENTER);

        JPanel progress = new JPanel(new BorderLayout(8, 2));
        progress.add(summary, BorderLayout.NORTH);
        JPanel progressButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        progressButtons.add(autoRun);
        progressButtons.add(autoStop);
        progressButtons.add(exportResults);
        progress.add(progressButtons, BorderLayout.CENTER);
        progress.add(automationState, BorderLayout.SOUTH);
        intro.add(progress, BorderLayout.SOUTH);
        add(intro, BorderLayout.NORTH);

        targetTree.setRootVisible(false);
        targetTree.setShowsRootHandles(true);
        targetTree.addTreeSelectionListener(this::onTreeSelection);
        JScrollPane treeScroll = new JScrollPane(targetTree);
        treeScroll.setPreferredSize(new Dimension(410, 560));
        treeScroll.setBorder(BorderFactory.createTitledBorder(
                "Guided area → task → physical controller target"));
        add(treeScroll, BorderLayout.WEST);

        JPanel detail = new JPanel(new BorderLayout(8, 8));
        JPanel identity = new JPanel();
        identity.setLayout(new javax.swing.BoxLayout(identity, javax.swing.BoxLayout.Y_AXIS));
        targetName.setFont(targetName.getFont().deriveFont(Font.BOLD, 17f));
        identity.add(targetName);
        identity.add(representation);
        identity.add(original);
        identity.add(applyReadback);
        identity.add(restoreReadback);
        identity.add(engineState);
        detail.add(identity, BorderLayout.NORTH);

        JPanel workflow = new JPanel();
        workflow.setLayout(new javax.swing.BoxLayout(workflow, javax.swing.BoxLayout.Y_AXIS));

        JPanel temp = new JPanel(new BorderLayout(8, 4));
        temp.setBorder(BorderFactory.createTitledBorder("Temporary test value"));
        valueEditorHost.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        temp.add(valueEditorHost, BorderLayout.CENTER);
        JPanel applyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        applyButtons.add(apply);
        applyButtons.add(restore);
        temp.add(applyButtons, BorderLayout.SOUTH);
        workflow.add(temp);

        JPanel outcome = new JPanel(new BorderLayout(8, 4));
        outcome.setBorder(BorderFactory.createTitledBorder("Validation result"));
        JPanel mark = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        mark.add(new JLabel("Status"));
        mark.add(markStatus);
        mark.add(new JLabel("Note"));
        mark.add(note);
        mark.add(record);
        outcome.add(mark, BorderLayout.NORTH);
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setFocusable(false);
        outcome.add(new JScrollPane(message), BorderLayout.CENTER);
        workflow.add(outcome);

        detail.add(workflow, BorderLayout.CENTER);
        add(detail, BorderLayout.CENTER);

        apply.addActionListener(event -> applyTemporary());
        restore.addActionListener(event -> restoreOriginal());
        record.addActionListener(event -> recordOutcome());
        autoRun.addActionListener(event -> startAutomaticValidation());
        autoStop.addActionListener(event -> requestAutomaticStop());
        exportResults.addActionListener(event -> exportResults());
        autoRun.setToolTipText(
                "Automatically validate every NOT TESTED physical target using a deterministic nearby valid value. One Apply/Restore round trip at a time; no burn.");
        autoStop.setToolTipText(
                "Stop automatic validation after the current target has reached a safe restored state.");
        exportResults.setToolTipText(
                "Export one row per unique physical target plus a session summary. Disabled while automation or a temporary value is active.");
    }

    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Physical validation inventory");
        for (GuidedTuningArea area : model.areasWithTargets()) {
            DefaultMutableTreeNode areaNode = new DefaultMutableTreeNode(area);
            root.add(areaNode);
            for (GuidedTuningRecipe task : area.tasks()) {
                List<AeApplyRestoreValidationTargets.Target> targets = model.targetsForTask(task);
                if (targets.isEmpty()) continue;
                DefaultMutableTreeNode taskNode = new DefaultMutableTreeNode(task);
                areaNode.add(taskNode);
                for (AeApplyRestoreValidationTargets.Target target : targets) {
                    taskNode.add(new DefaultMutableTreeNode(new TargetTreeItem(target)));
                }
            }
        }
        targetTree.setModel(new DefaultTreeModel(root));
    }

    private void onTreeSelection(TreeSelectionEvent event) {
        if (revertingSelection || automationRunning) return;
        TreePath path = targetTree.getSelectionPath();
        if (path == null) return;
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(userObject instanceof TargetTreeItem)) return;
        TargetTreeItem item = (TargetTreeItem) userObject;

        AeApplyRestoreValidationLabModel.Record current = model.getSelected();
        if (model.isTemporaryApplied() && current != null
                && !current.getTarget().identity().equals(item.target.identity())) {
            message.setText("Navigation blocked: restore the currently applied temporary value first.");
            revertTreeSelection();
            return;
        }

        try {
            AeApplyRestoreValidationLabModel.Record selected = model.select(item.target);
            acceptedSelection = path;
            loadRecord(selected);
            rebuildTreeLabels();
        } catch (Exception ex) {
            message.setText("Target selection failed: " + safeMessage(ex));
            revertTreeSelection();
        }
    }

    private void revertTreeSelection() {
        if (acceptedSelection == null) return;
        revertingSelection = true;
        SwingUtilities.invokeLater(() -> {
            try {
                targetTree.setSelectionPath(acceptedSelection);
            } finally {
                revertingSelection = false;
            }
        });
    }

    private void loadRecord(AeApplyRestoreValidationLabModel.Record record) {
        if (record == null) return;
        AeApplyRestoreValidationTargets.Target target = record.getTarget();
        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        targetName.setText(target.getParameter().getDisplayName()
                + (target.isIndexed() ? " [" + target.coordinateText() + "]" : ""));
        representation.setText("Controller: " + target.identity()
                + " | " + definition.getKind() + " " + definition.getValueType()
                + " | range " + format(definition.getMinimum()) + ".."
                + format(definition.getMaximum())
                + (definition.getUnit().length() == 0 ? "" : " " + definition.getUnit()));
        original.setText("Recorded original: " + format(record.getOriginalValue()));
        applyReadback.setText("Apply readback: " + format(record.getApplyReadback()));
        restoreReadback.setText("Restore readback: " + format(record.getRestoreReadback()));
        markStatus.setSelectedItem(record.getStatus());
        note.setText(record.getNote());
        message.setText(record.getMessage());

        AeApplyRestoreValidationLabModel.Record selected = model.getSelected();
        double currentLive = selected != null
                && selected.getTarget().identity().equals(target.identity())
                ? model.getCurrentCapturedOriginalValue() : Double.NaN;
        buildValueEditor(target, currentLive);
        refreshControls();
    }

    private void buildValueEditor(AeApplyRestoreValidationTargets.Target target,
                                  double currentLiveValue) {
        valueEditorHost.removeAll();
        numericEditor = null;
        optionEditor = null;
        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            List<OptionChoice> choices = new ArrayList<OptionChoice>();
            String[] labels = definition.getOptionLabels();
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i] == null ? "" : labels[i].trim();
                if (label.length() == 0 || "INVALID".equalsIgnoreCase(label)) continue;
                choices.add(new OptionChoice(i, label));
            }
            optionEditor = new JComboBox<OptionChoice>(choices.toArray(new OptionChoice[0]));
            if (Double.isFinite(currentLiveValue)) {
                int liveIndex = (int) Math.rint(currentLiveValue);
                for (int i = 0; i < choices.size(); i++) {
                    if (choices.get(i).index == liveIndex) {
                        optionEditor.setSelectedIndex(i);
                        break;
                    }
                }
            }
            valueEditorHost.add(optionEditor, BorderLayout.WEST);
        } else {
            double step = numericStep(definition);
            double editorValue = currentLiveValue;
            if (!Double.isFinite(editorValue)
                    || editorValue < definition.getMinimum()
                    || editorValue > definition.getMaximum()) {
                editorValue = definition.getMinimum();
            }
            numericEditor = new JSpinner(new SpinnerNumberModel(
                    editorValue,
                    definition.getMinimum(), definition.getMaximum(), step));
            numericEditor.setPreferredSize(new Dimension(180, numericEditor.getPreferredSize().height));
            valueEditorHost.add(numericEditor, BorderLayout.WEST);
            valueEditorHost.add(new JLabel("Current live baseline " + format(currentLiveValue)
                    + " | step " + format(step)
                    + (definition.getUnit().length() == 0 ? "" : " " + definition.getUnit())),
                    BorderLayout.CENTER);
        }
        valueEditorHost.revalidate();
        valueEditorHost.repaint();
    }

    private void applyTemporary() {
        AeApplyRestoreValidationLabModel.Record selected = model.getSelected();
        if (selected == null || automationRunning) return;
        try {
            double requested = selectedEditorValue();
            model.setTemporaryValue(requested);
            AeApplyRestoreValidationEngine.OperationResult result = model.applyTemporary();
            loadRecord(model.getSelected());
            message.setText(result.message);
            rebuildTreeLabels();
        } catch (Exception ex) {
            message.setText("Apply blocked: " + safeMessage(ex));
            refreshControls();
        }
    }

    private void restoreOriginal() {
        if (model.getSelected() == null || automationRunning) return;
        try {
            AeApplyRestoreValidationEngine.OperationResult result = model.restoreOriginal();
            loadRecord(model.getSelected());
            message.setText(result.message);
            rebuildTreeLabels();
        } catch (Exception ex) {
            message.setText("Restore failed: " + safeMessage(ex));
            refreshControls();
        }
    }

    private void recordOutcome() {
        if (model.getSelected() == null || automationRunning) return;
        try {
            model.markSelected(
                    (AeApplyRestoreValidationLabModel.ValidationStatus) markStatus.getSelectedItem(),
                    note.getText());
            loadRecord(model.getSelected());
            rebuildTreeLabels();
            refreshSummary();
        } catch (Exception ex) {
            message.setText("Result not recorded: " + safeMessage(ex));
        }
    }

    private void startAutomaticValidation() {
        if (automationRunning) return;
        if (model.isTemporaryApplied()) {
            message.setText("Automation blocked: restore the active temporary value first.");
            return;
        }
        List<AeApplyRestoreValidationTargets.Target> remaining = automation.remainingTargets();
        if (remaining.isEmpty()) {
            automationState.setText("Automation complete: no NOT TESTED physical targets remain.");
            return;
        }

        automationRunning = true;
        automationStopRequested = false;
        automationProcessed = 0;
        automationInitialRemaining = remaining.size();
        automationState.setText("Automatic validation starting: "
                + automationInitialRemaining + " NOT TESTED target(s).");
        automationTimer = new Timer(75, event -> runAutomaticStep());
        automationTimer.setInitialDelay(0);
        automationTimer.start();
        refreshControls();
    }

    private void requestAutomaticStop() {
        if (!automationRunning) return;
        automationStopRequested = true;
        autoStop.setEnabled(false);
        automationState.setText("Stop requested; current target will finish Restore/readback first.");
    }

    private void runAutomaticStep() {
        if (!automationRunning) return;
        if (automationStopRequested) {
            finishAutomation("Automatic validation stopped by operator after "
                    + automationProcessed + " completed target(s). Working tune is restored.");
            return;
        }

        AeApplyRestoreValidationTargets.Target target = automation.nextUntestedTarget();
        if (target == null) {
            finishAutomation("Automatic validation complete. "
                    + automationProcessed + " target(s) processed in this run.");
            return;
        }

        AeApplyRestoreValidationAutomation.StepResult result =
                automation.validateOne(target);
        automationProcessed++;
        AeApplyRestoreValidationLabModel.Record selected = model.getSelected();
        if (selected != null
                && selected.getTarget().identity().equals(target.identity())) {
            loadRecord(selected);
        } else {
            targetName.setText(target.getParameter().getDisplayName()
                    + (target.isIndexed() ? " [" + target.coordinateText() + "]" : ""));
            message.setText(result.message);
        }
        rebuildTreeLabels();
        refreshSummary();
        automationState.setText("Automatic validation " + automationProcessed + "/"
                + automationInitialRemaining + " | last " + result.status
                + " | " + target.identity());
        message.setText(result.message);

        if (!result.safeToContinue
                || result.status == AeApplyRestoreValidationAutomation.StepStatus.HALT) {
            finishAutomation("AUTOMATION HALTED after " + automationProcessed
                    + " target(s): " + result.message);
        }
    }

    private void finishAutomation(String status) {
        if (automationTimer != null) {
            automationTimer.stop();
            automationTimer = null;
        }
        automationRunning = false;
        automationStopRequested = false;
        automationState.setText(status);
        refreshControls();
        rebuildTreeLabels();
    }

    private void stopAutomationForLifecycle() {
        if (automationTimer != null) {
            automationTimer.stop();
            automationTimer = null;
        }
        automationRunning = false;
        automationStopRequested = false;
    }

    private void exportResults() {
        if (automationRunning) {
            message.setText("Export blocked: stop automatic validation first.");
            return;
        }
        if (model.isTemporaryApplied()) {
            message.setText("Export blocked: restore the active temporary working-tune value first.");
            return;
        }
        File parent = SessionExportSupport.chooseParent(this, "validation");
        if (parent == null) {
            message.setText("Validation evidence export cancelled or export folder unavailable.");
            return;
        }

        SessionExportSupport.StagedFolder staged = null;
        try {
            staged = SessionExportSupport.stageSessionFolder(parent, "validation");
            SessionExportSupport.writeTextAtomic(
                    staged.file("validation-results.csv"),
                    AeApplyRestoreValidationReport.csv(model));
            SessionExportSupport.writeTextAtomic(
                    staged.file("summary.txt"),
                    AeApplyRestoreValidationReport.summary(model));
            File published = staged.finish();
            message.setText("Validation evidence exported: " + published.getAbsolutePath());
        } catch (IOException ex) {
            if (staged != null) staged.cleanup();
            message.setText("Validation evidence export failed: " + safeMessage(ex));
        }
    }

    private double selectedEditorValue() {
        if (optionEditor != null) {
            OptionChoice choice = (OptionChoice) optionEditor.getSelectedItem();
            if (choice == null) throw new IllegalStateException("choose a valid controller option");
            return choice.index;
        }
        if (numericEditor == null) throw new IllegalStateException("temporary value editor unavailable");
        return ((Number) numericEditor.getValue()).doubleValue();
    }

    private void refreshControls() {
        boolean selected = model.getSelected() != null;
        boolean applied = model.isTemporaryApplied();
        boolean manual = !automationRunning;
        apply.setEnabled(manual && selected && !applied);
        restore.setEnabled(manual && selected && applied);
        markStatus.setEnabled(manual && selected && !applied);
        note.setEnabled(manual && selected && !applied);
        record.setEnabled(manual && selected && !applied);
        autoRun.setEnabled(!automationRunning && !applied
                && !automation.remainingTargets().isEmpty());
        autoStop.setEnabled(automationRunning && !automationStopRequested);
        exportResults.setEnabled(!automationRunning && !applied);
        targetTree.setEnabled(!automationRunning && !applied);
        targetTree.setToolTipText(automationRunning
                ? "Automatic validation is running. Use Stop After Current before manual navigation."
                : applied
                ? "Restore the temporary value before selecting another physical target."
                : "Select one physical controller target to validate.");
        engineState.setText(model.engineStatusText());
        refreshSummary();
    }

    private void refreshSummary() {
        summary.setText(model.summaryText());
    }

    private void rebuildTreeLabels() {
        targetTree.repaint();
        refreshSummary();
    }

    public boolean canCloseSafely() {
        return !automationRunning && !model.isTemporaryApplied();
    }

    public boolean restoreBeforeLifecycleClose() {
        stopAutomationForLifecycle();
        if (!model.isTemporaryApplied()) return true;
        try {
            AeApplyRestoreValidationEngine.OperationResult result = model.restoreOriginal();
            if (model.getSelected() != null) loadRecord(model.getSelected());
            message.setText(result.message);
            return result.success;
        } catch (Exception ex) {
            message.setText("Lifecycle safety Restore failed: " + safeMessage(ex));
            return false;
        }
    }

    public void showCloseBlockedMessage() {
        if (automationRunning) {
            JOptionPane.showMessageDialog(this,
                    "Automatic validation is still running.\nUse Stop After Current before closing this Lab.",
                    "Validation Lab — Automation Running",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this,
                "A temporary working-tune value is still applied.\nRestore Exact Original before closing this Lab.",
                "Validation Lab — Restore Required",
                JOptionPane.WARNING_MESSAGE);
    }

    public int physicalTargetCountForTest() { return model.totalCount(); }
    public String summaryTextForTest() { return model.summaryText(); }
    public boolean temporaryAppliedForTest() { return model.isTemporaryApplied(); }
    public String exportButtonTextForTest() { return exportResults.getText(); }
    public String automationButtonTextForTest() { return autoRun.getText(); }
    public String automationStopButtonTextForTest() { return autoStop.getText(); }
    public boolean automationRunningForTest() { return automationRunning; }

    private final class TargetTreeItem {
        final AeApplyRestoreValidationTargets.Target target;

        TargetTreeItem(AeApplyRestoreValidationTargets.Target target) {
            this.target = target;
        }

        @Override public String toString() {
            AeApplyRestoreValidationLabModel.Record record = model.recordFor(target);
            String status = record == null ? "NOT TESTED" : record.getStatus().label;
            String label = target.getParameter().getDisplayName();
            if (target.isIndexed()) label += " [" + target.coordinateText() + "]";
            return "[" + status + "] " + label + " — " + target.identity();
        }
    }

    private static final class OptionChoice {
        final int index;
        final String label;

        OptionChoice(int index, String label) {
            this.index = index;
            this.label = label;
        }

        @Override public String toString() { return index + " — " + label; }
    }

    private static double numericStep(AeControllerDefinitionCatalog.Definition definition) {
        switch (definition.getValueType()) {
            case U08:
            case S08:
            case U16:
            case S16:
            case U32:
                return definition.getScale();
            default:
                int decimals = Math.max(0, definition.getDecimals());
                return Math.pow(10.0, -decimals);
        }
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "unknown error";
        String value = ex.getMessage();
        return value == null || value.length() == 0 ? ex.getClass().getSimpleName() : value;
    }
}
