package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets;
import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.GuidedControllerSettingInventory;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspacePanel;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal Review/Edit surface for one Guided task's physically validated settings.
 * It stages a ProposalWritePlan only; Apply/Restore remain in GuidedCapturePanel.
 */
final class GuidedTaskSettingsDialog extends JDialog {
    private static final Font TITLE = new Font("Dialog", Font.BOLD, 19);
    private static final Font H2 = new Font("Dialog", Font.BOLD, 14);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);
    private static final int ENTRY_ROW_HEIGHT = 78;

    private final GuidedTaskSettingsDraft draft;
    private final List<Binding> bindings = new ArrayList<Binding>();
    private final AeUtilityWorkspacePanel workspace;
    private final JLabel editState = new JLabel();
    private final JButton reset = new JButton("Reset to Working Tune");
    private final JButton cancel = new JButton("Cancel");
    private final JButton stage = new JButton("Stage Reviewed Changes");
    private ProposalWritePlan stagedPlan;

    static ProposalWritePlan showDialog(Component parent,
                                        ControllerAccess access,
                                        String configurationName,
                                        GuidedTuningRecipe task)
            throws Exception {
        GuidedTaskSettingsDraft draft =
                GuidedTaskSettingsDraft.capture(access, configurationName, task);
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        GuidedTaskSettingsDialog dialog =
                new GuidedTaskSettingsDialog(owner, draft);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.stagedPlan;
    }

    GuidedTaskSettingsDialog(Window owner, GuidedTaskSettingsDraft draft) {
        super(owner, "Review Task Settings — " + draft.getTask().displayName,
                ModalityType.APPLICATION_MODAL);
        if (draft == null) throw new IllegalArgumentException("draft");
        this.draft = draft;
        this.workspace = new AeUtilityWorkspacePanel(
                "Task Settings — " + draft.getTask().displayName,
                "Review Working Tune → Proposed values. Editing is draft-only until a real plan is staged for Guided Apply.");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildUi());
        setPreferredSize(new Dimension(1060, 720));
        pack();
    }

    private JComponent buildUi() {
        GuidedControllerSettingInventory.TaskInventory inventory =
                GuidedControllerSettingInventory.find(draft.getTask());

        workspace.setToolbar(buildToolbar(inventory));

        int section = 0;
        for (String controllerName : draft.controllerNames()) {
            List<GuidedTaskSettingsDraft.Entry> entries =
                    draft.entriesForController(controllerName);
            if (entries.isEmpty()) continue;
            AeApplyRestoreValidationTargets.Target first = entries.get(0).getTarget();
            AeControllerDefinitionCatalog.Definition definition = first.getDefinition();
            String title = first.getParameter().getDisplayName();
            String detail = controllerName + " • " + definition.getKind() + " "
                    + definition.getValueType() + " • " + entries.size()
                    + (entries.size() == 1 ? " value" : " values");
            workspace.addSection("parameter-" + section++, title, detail,
                    buildParameterPanel(controllerName, entries));
        }
        refreshEditState();
        workspace.applyTheme();
        return workspace;
    }

    private JComponent buildToolbar(GuidedControllerSettingInventory.TaskInventory inventory) {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);

        JPanel safety = new JPanel(new BorderLayout(0, 2));
        safety.setOpaque(false);
        JLabel boundary = new JLabel("DRAFT ONLY  •  NO ECU WRITE  •  NO BURN");
        boundary.setFont(H2);
        boundary.setForeground(AeUiTheme.blue());
        JLabel authority = new JLabel(inventory == null
                ? "Stage creates a ProposalWritePlan; existing Guided Apply/Restore remains the sole write authority."
                : "Write support: " + inventory.getProductionWriteSupport()
                + "  •  Recommendation: " + inventory.getRecommendationSupport()
                + "  •  " + draft.controllerNames().size() + " controller parameter(s)");
        authority.setFont(SMALL);
        authority.setForeground(AeUiTheme.muted());
        safety.add(boundary, BorderLayout.NORTH);
        safety.add(authority, BorderLayout.SOUTH);
        toolbar.add(safety, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        editState.setFont(new Font("Dialog", Font.BOLD, 10));
        editState.setBorder(new EmptyBorder(7, 0, 0, 5));
        reset.addActionListener(event -> resetEditors());
        cancel.addActionListener(event -> dispose());
        stage.addActionListener(event -> stagePlan());
        actions.add(editState);
        actions.add(reset);
        actions.add(cancel);
        actions.add(stage);
        toolbar.add(actions, BorderLayout.EAST);
        return toolbar;
    }

    private JComponent buildParameterPanel(
            String controllerName,
            List<GuidedTaskSettingsDraft.Entry> entries) {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        GuidedTaskSettingsDraft.Entry first = entries.get(0);
        AeControllerDefinitionCatalog.Definition definition =
                first.getTarget().getDefinition();

        JPanel metadata = new JPanel(new BorderLayout(8, 3));
        metadata.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                new EmptyBorder(9, 11, 9, 11)));
        JLabel controller = new JLabel(controllerName);
        controller.setFont(H2);
        JLabel range = new JLabel(definition.getKind() + " " + definition.getValueType()
                + "  •  range " + format(definition.getMinimum()) + " .. "
                + format(definition.getMaximum())
                + (definition.getUnit().length() == 0
                ? "" : " " + definition.getUnit())
                + "  •  captured from " + draft.getConfigurationName());
        range.setFont(SMALL);
        metadata.add(controller, BorderLayout.NORTH);
        metadata.add(range, BorderLayout.CENTER);
        root.add(metadata, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(3, 3, 3, 3));
        for (GuidedTaskSettingsDraft.Entry entry : entries) {
            list.add(buildEntryRow(entry));
            list.add(Box.createVerticalStrut(5));
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildEntryRow(GuidedTaskSettingsDraft.Entry entry) {
        AeApplyRestoreValidationTargets.Target target = entry.getTarget();
        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        JPanel row = new JPanel(new BorderLayout(12, 4));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(760, ENTRY_ROW_HEIGHT));
        row.setMinimumSize(new Dimension(0, ENTRY_ROW_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ENTRY_ROW_HEIGHT));
        row.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                new EmptyBorder(8, 10, 8, 10)));

        String point = target.isIndexed()
                ? "[" + target.coordinateText() + "]  flat " + target.getFlatIndex()
                : "Scalar value";
        JPanel identity = new JPanel(new BorderLayout(0, 2));
        identity.setOpaque(false);
        JLabel pointLabel = new JLabel(point);
        pointLabel.setFont(new Font("Dialog", Font.BOLD, 11));
        JLabel targetLabel = new JLabel(target.identity());
        targetLabel.setFont(new Font("Dialog", Font.PLAIN, 9));
        identity.add(pointLabel, BorderLayout.NORTH);
        identity.add(targetLabel, BorderLayout.SOUTH);
        identity.setPreferredSize(new Dimension(205, 44));
        row.add(identity, BorderLayout.WEST);

        JPanel values = new JPanel(new GridLayout(1, 2, 14, 0));
        values.setOpaque(false);

        JPanel currentPanel = valuePanel("WORKING TUNE",
                format(entry.getOriginalValue())
                        + (definition.getUnit().length() == 0
                        ? "" : " " + definition.getUnit()));
        values.add(currentPanel);

        JPanel proposed = new JPanel(new BorderLayout(8, 2));
        proposed.setOpaque(false);
        JLabel proposedTitle = new JLabel("PROPOSED");
        proposedTitle.setFont(new Font("Dialog", Font.BOLD, 9));
        proposed.add(proposedTitle, BorderLayout.NORTH);

        JPanel editorLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        editorLine.setOpaque(false);
        JComponent editor;
        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            List<OptionChoice> choices = new ArrayList<OptionChoice>();
            String[] labels = definition.getOptionLabels();
            int selectedIndex = -1;
            int original = (int) Math.rint(entry.getOriginalValue());
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i] == null ? "" : labels[i].trim();
                if (label.length() == 0 || "INVALID".equalsIgnoreCase(label)) continue;
                if (i == original) selectedIndex = choices.size();
                choices.add(new OptionChoice(i, label));
            }
            if (selectedIndex < 0) {
                choices.add(0, new OptionChoice(original, "Current / unmapped"));
                selectedIndex = 0;
            }
            JComboBox<OptionChoice> combo =
                    new JComboBox<OptionChoice>(choices.toArray(new OptionChoice[0]));
            combo.setSelectedIndex(selectedIndex);
            combo.setPreferredSize(new Dimension(290, combo.getPreferredSize().height));
            editor = combo;
            editorLine.add(editor);
        } else {
            double step = GuidedTaskSettingsDraft.editorStep(definition);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    Double.valueOf(entry.getOriginalValue()),
                    Double.valueOf(definition.getMinimum()),
                    Double.valueOf(definition.getMaximum()),
                    Double.valueOf(step)));
            spinner.setPreferredSize(new Dimension(190, spinner.getPreferredSize().height));
            editor = spinner;
            editorLine.add(editor);
            JLabel stepLabel = new JLabel("step " + format(step));
            stepLabel.setFont(new Font("Dialog", Font.PLAIN, 9));
            editorLine.add(stepLabel);
        }
        proposed.add(editorLine, BorderLayout.CENTER);
        values.add(proposed);
        row.add(values, BorderLayout.CENTER);

        Binding binding = new Binding(entry, editor);
        bindings.add(binding);
        if (editor instanceof JSpinner) {
            ((JSpinner) editor).addChangeListener(event -> refreshEditState());
        } else if (editor instanceof JComboBox) {
            ((JComboBox<?>) editor).addActionListener(event -> refreshEditState());
        }
        return row;
    }

    private JPanel valuePanel(String title, String value) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Dialog", Font.BOLD, 9));
        JLabel body = new JLabel(value);
        body.setFont(new Font("Dialog", Font.BOLD, 13));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void resetEditors() {
        for (Binding binding : bindings) {
            AeControllerDefinitionCatalog.Definition definition =
                    binding.entry.getTarget().getDefinition();
            if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
                @SuppressWarnings("unchecked")
                JComboBox<OptionChoice> combo =
                        (JComboBox<OptionChoice>) binding.editor;
                int original = (int) Math.rint(binding.entry.getOriginalValue());
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (combo.getItemAt(i).index == original) {
                        combo.setSelectedIndex(i);
                        break;
                    }
                }
            } else {
                ((JSpinner) binding.editor).setValue(
                        Double.valueOf(binding.entry.getOriginalValue()));
            }
        }
        refreshEditState();
    }

    private void refreshEditState() {
        int changed = 0;
        for (Binding binding : bindings) {
            try {
                if (Math.abs(requestedValue(binding)
                        - binding.entry.getOriginalValue()) > 0.000001) changed++;
            } catch (RuntimeException ignored) { }
        }
        editState.setText(changed == 0 ? "NO DRAFT EDITS"
                : changed + (changed == 1 ? " DRAFT EDIT" : " DRAFT EDITS"));
        editState.setForeground(changed == 0 ? AeUiTheme.muted() : AeUiTheme.amberDark());
    }

    private void stagePlan() {
        try {
            for (Binding binding : bindings) {
                double requested = requestedValue(binding);
                draft.setProposedValue(
                        binding.entry.getTarget().identity(), requested);
            }
            ProposalWritePlan plan = draft.buildPlan();
            if (plan == null) {
                showStatusMessage("Task Settings — No Changes",
                        "No values differ from the captured Working Tune. Nothing was staged.",
                        false);
                return;
            }
            if (!confirmStagePlan(plan)) return;
            stagedPlan = plan;
            dispose();
        } catch (Exception ex) {
            showStatusMessage("Task Settings — Review Required",
                    "Cannot stage task settings: " + safeMessage(ex), true);
        }
    }

    private boolean confirmStagePlan(ProposalWritePlan plan) {
        final boolean[] accepted = new boolean[]{false};
        final JDialog review = new JDialog(this,
                "Stage " + plan.changeCount() + " Reviewed Change(s)",
                ModalityType.APPLICATION_MODAL);
        review.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(AeUiTheme.background());
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        JLabel title = new JLabel("Review Staged Task Settings");
        title.setFont(TITLE);
        title.setForeground(AeUiTheme.text());
        JLabel sub = new JLabel("This confirms the ProposalWritePlan only. The ECU is not written here; Guided Apply remains the sole mutation path.");
        sub.setFont(SMALL);
        sub.setForeground(AeUiTheme.muted());
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JTextArea planText = new JTextArea(plan.reviewText()
                + "\n\nSTAGE ONLY — NO ECU WRITE — NO BURN\n"
                + "After staging, return to Guided Review / Apply for the guarded write and exact readback.");
        planText.setEditable(false);
        planText.setLineWrap(true);
        planText.setWrapStyleWord(true);
        planText.setFont(BODY);
        planText.setBackground(AeUiTheme.card());
        planText.setForeground(AeUiTheme.text());
        planText.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(planText);
        scroll.setBorder(new LineBorder(AeUiTheme.border()));
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton back = new JButton("Back to Editing");
        JButton accept = new JButton("Stage Reviewed Plan");
        back.addActionListener(event -> review.dispose());
        accept.addActionListener(event -> {
            accepted[0] = true;
            review.dispose();
        });
        buttons.add(back);
        buttons.add(accept);
        root.add(buttons, BorderLayout.SOUTH);
        AeUtilityWorkspacePanel.themeTree(root);

        review.setContentPane(root);
        review.setMinimumSize(new Dimension(720, 500));
        review.setSize(780, 560);
        review.setLocationRelativeTo(this);
        review.setVisible(true);
        return accepted[0];
    }

    private void showStatusMessage(String titleText, String message, boolean error) {
        final JDialog messageDialog = new JDialog(this, titleText,
                ModalityType.APPLICATION_MODAL);
        messageDialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(AeUiTheme.background());
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel(error ? "Review Required" : "No Changes to Stage");
        title.setFont(H2);
        title.setForeground(error ? AeUiTheme.amberDark() : AeUiTheme.text());
        JTextArea body = new JTextArea(message);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(BODY);
        body.setBackground(AeUiTheme.card());
        body.setForeground(AeUiTheme.text());
        body.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                new EmptyBorder(10, 12, 10, 12)));
        JButton close = new JButton("OK");
        close.addActionListener(event -> messageDialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(close);
        root.add(title, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        AeUtilityWorkspacePanel.themeTree(root);
        messageDialog.setContentPane(root);
        messageDialog.setSize(new Dimension(560, 260));
        messageDialog.setLocationRelativeTo(this);
        messageDialog.setVisible(true);
    }

    private static double requestedValue(Binding binding) {
        AeControllerDefinitionCatalog.Definition definition =
                binding.entry.getTarget().getDefinition();
        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            @SuppressWarnings("unchecked")
            JComboBox<OptionChoice> combo =
                    (JComboBox<OptionChoice>) binding.editor;
            OptionChoice choice = (OptionChoice) combo.getSelectedItem();
            if (choice == null) {
                throw new IllegalStateException("choose a valid controller option for "
                        + binding.entry.getTarget().identity());
            }
            return choice.index;
        }
        return ((Number) ((JSpinner) binding.editor).getValue()).doubleValue();
    }

    int parameterSectionCountForTest() { return workspace.sectionCount(); }
    String parameterSectionTitleForTest(int index) { return workspace.sectionTitleAt(index); }
    String selectedParameterSectionForTest() { return workspace.selectedSectionId(); }
    void selectParameterSectionForTest(String id) { workspace.selectSection(id); }
    int editorBindingCountForTest() { return bindings.size(); }
    String editStateForTest() { return editState.getText(); }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "unknown error";
        String message = ex.getMessage();
        return message == null || message.trim().length() == 0
                ? ex.getClass().getSimpleName() : message;
    }

    private static final class Binding {
        final GuidedTaskSettingsDraft.Entry entry;
        final JComponent editor;

        Binding(GuidedTaskSettingsDraft.Entry entry, JComponent editor) {
            this.entry = entry;
            this.editor = editor;
        }
    }

    private static final class OptionChoice {
        final int index;
        final String label;

        OptionChoice(int index, String label) {
            this.index = index;
            this.label = label;
        }

        @Override public String toString() {
            return index + " — " + label;
        }
    }
}
