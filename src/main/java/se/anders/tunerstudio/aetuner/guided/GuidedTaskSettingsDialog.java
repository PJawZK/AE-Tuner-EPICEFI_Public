package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets;
import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.GuidedControllerSettingInventory;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal Review/Edit surface for one Guided task's physically validated settings.
 * It stages a ProposalWritePlan only; Apply/Restore remain in GuidedCapturePanel.
 */
final class GuidedTaskSettingsDialog extends JDialog {
    private final GuidedTaskSettingsDraft draft;
    private final List<Binding> bindings = new ArrayList<Binding>();
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

    private GuidedTaskSettingsDialog(Window owner, GuidedTaskSettingsDraft draft) {
        super(owner, "Review Task Settings — " + draft.getTask().displayName,
                ModalityType.APPLICATION_MODAL);
        this.draft = draft;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        setPreferredSize(new Dimension(980, 720));
        pack();
    }

    private void buildUi() {
        GuidedControllerSettingInventory.TaskInventory inventory =
                GuidedControllerSettingInventory.find(draft.getTask());

        JPanel intro = new JPanel();
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("PHYSICALLY VALIDATED TASK SETTINGS — REVIEW BEFORE APPLY");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        intro.add(title);
        intro.add(new JLabel(
                "Editing here does not write the ECU. Stage the changed values, review the generated plan, then use Guided 'Apply Current Proposal'. No burn."));
        if (inventory != null) {
            intro.add(new JLabel("Production write support: "
                    + inventory.getProductionWriteSupport()
                    + " | recommendation maturity: "
                    + inventory.getRecommendationSupport()
                    + " | controller parameters: "
                    + draft.controllerNames().size()));
        }
        add(intro, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        for (String controllerName : draft.controllerNames()) {
            List<GuidedTaskSettingsDraft.Entry> entries =
                    draft.entriesForController(controllerName);
            if (entries.isEmpty()) continue;
            AeApplyRestoreValidationTargets.Target first =
                    entries.get(0).getTarget();
            String tabTitle = first.getParameter().getDisplayName();
            tabs.addTab(tabTitle, buildParameterPanel(controllerName, entries));
            tabs.setToolTipTextAt(tabs.getTabCount() - 1,
                    controllerName + " — " + first.getDefinition().getKind()
                            + " " + first.getDefinition().getValueType());
        }
        add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        JButton reset = new JButton("Reset Editors to Current");
        JButton cancel = new JButton("Cancel");
        JButton stage = new JButton("Stage Reviewed Changes");
        reset.addActionListener(event -> resetEditors());
        cancel.addActionListener(event -> dispose());
        stage.addActionListener(event -> stagePlan());
        buttons.add(reset);
        buttons.add(cancel);
        buttons.add(stage);
        add(buttons, BorderLayout.SOUTH);
    }

    private JComponent buildParameterPanel(
            String controllerName,
            List<GuidedTaskSettingsDraft.Entry> entries) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        GuidedTaskSettingsDraft.Entry first = entries.get(0);
        AeControllerDefinitionCatalog.Definition definition =
                first.getTarget().getDefinition();

        JLabel metadata = new JLabel(controllerName + " | "
                + definition.getKind() + " " + definition.getValueType()
                + " | range " + format(definition.getMinimum()) + " .. "
                + format(definition.getMaximum())
                + (definition.getUnit().length() == 0
                ? "" : " " + definition.getUnit()));
        metadata.setBorder(BorderFactory.createEmptyBorder(4, 6, 8, 6));
        list.add(metadata);

        for (GuidedTaskSettingsDraft.Entry entry : entries) {
            list.add(buildEntryRow(entry));
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    private JComponent buildEntryRow(GuidedTaskSettingsDraft.Entry entry) {
        AeApplyRestoreValidationTargets.Target target = entry.getTarget();
        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        JPanel row = new JPanel(new BorderLayout(10, 2));
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                row.getForeground()));

        String point = target.isIndexed()
                ? "[" + target.coordinateText() + "]  flat " + target.getFlatIndex()
                : "scalar";
        JLabel identity = new JLabel(point);
        identity.setPreferredSize(new Dimension(125, 28));
        row.add(identity, BorderLayout.WEST);

        JPanel values = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JLabel current = new JLabel("Current " + format(entry.getOriginalValue())
                + (definition.getUnit().length() == 0
                ? "" : " " + definition.getUnit()));
        current.setPreferredSize(new Dimension(200, 26));
        values.add(current);
        values.add(new JLabel("Proposed"));

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
            JComboBox<OptionChoice> combo =
                    new JComboBox<OptionChoice>(choices.toArray(new OptionChoice[0]));
            if (selectedIndex >= 0) combo.setSelectedIndex(selectedIndex);
            combo.setPreferredSize(new Dimension(260, combo.getPreferredSize().height));
            editor = combo;
        } else {
            double step = GuidedTaskSettingsDraft.editorStep(definition);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                    Double.valueOf(entry.getOriginalValue()),
                    Double.valueOf(definition.getMinimum()),
                    Double.valueOf(definition.getMaximum()),
                    Double.valueOf(step)));
            spinner.setPreferredSize(new Dimension(180, spinner.getPreferredSize().height));
            editor = spinner;
            values.add(new JLabel("step " + format(step)));
        }
        values.add(editor);
        row.add(values, BorderLayout.CENTER);
        bindings.add(new Binding(entry, editor));
        return row;
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
                JOptionPane.showMessageDialog(this,
                        "No values differ from the captured working tune. Nothing was staged.",
                        "Task Settings — No Changes",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    plan.reviewText()
                            + "\n\nStage this reviewed plan for the existing Guided Apply button?\n"
                            + "This dialog itself performs no controller write and no burn.",
                    "Stage " + plan.changeCount() + " Reviewed Change(s)",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            stagedPlan = plan;
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Cannot stage task settings: " + safeMessage(ex),
                    "Task Settings — Review Required",
                    JOptionPane.ERROR_MESSAGE);
        }
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
