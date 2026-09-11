package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Task- and stage-specific Options / Info presentation for the v0.19 port.
 * Coaching text comes from the existing production recipe catalog; this class
 * performs no evidence qualification, recommendation math or controller writes.
 */
final class GuidedV019TaskInfoViews {
    private static final Font TITLE = new Font("Dialog", Font.BOLD, 22);
    private static final Font H2 = new Font("Dialog", Font.BOLD, 15);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);

    private GuidedV019TaskInfoViews() { }

    static JComponent create(GuidedProductionTask task,
                             GuidedV019WorkspacePanel.Stage stage,
                             AeProjectSnapshot tune,
                             GuidedV019ProductionBridge bridge) {
        GuidedCoachBlueprint coach = task != null && task.productionRecipe != null
                ? GuidedCoachCatalog.forRecipe(task.productionRecipe) : null;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(AeUiTheme.background());
        root.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        JLabel title = new JLabel((task == null ? "Guided task" : task.displayName)
                + " — " + stage.title);
        title.setFont(TITLE);
        title.setForeground(AeUiTheme.text());
        JLabel sub = new JLabel(stageSubtitle(task, stage, coach));
        sub.setFont(SMALL);
        sub.setForeground(AeUiTheme.muted());
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 2, 10, 0));
        cards.setOpaque(false);
        cards.add(card(leftTitle(stage), leftText(task, stage, tune, bridge, coach)));
        cards.add(card(rightTitle(stage), rightText(task, stage, tune, bridge, coach)));
        JScrollPane scroll = new JScrollPane(cards);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        JLabel authority = new JLabel(authorityText(stage, bridge));
        authority.setFont(SMALL);
        authority.setForeground(AeUiTheme.muted());
        footer.add(authority, BorderLayout.CENTER);
        if ((stage == GuidedV019WorkspacePanel.Stage.PREPARE
                || stage == GuidedV019WorkspacePanel.Stage.CAPTURE)
                && bridge != null) {
            JButton settings = new JButton("Edit / Review Task Settings…");
            styleButton(settings);
            settings.setEnabled(bridge.taskSettingsEnabled());
            settings.addActionListener(event -> bridge.editTaskSettings());
            footer.add(settings, BorderLayout.EAST);
        }
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private static String stageSubtitle(GuidedProductionTask task,
                                        GuidedV019WorkspacePanel.Stage stage,
                                        GuidedCoachBlueprint coach) {
        String recipe = task == null || task.productionRecipe == null
                ? "No production recipe" : task.productionRecipe.status;
        String archetype = coach == null ? "planned / unavailable"
                : coach.archetype.label;
        return recipe + "  •  " + archetype + "  •  " + stage.title + " details";
    }

    private static String leftTitle(GuidedV019WorkspacePanel.Stage stage) {
        switch (stage) {
            case PREPARE: return "What this task is proving";
            case CAPTURE: return "Do this during capture";
            case GUIDED_FOCUS: return "What the Focus view should make obvious";
            case REVIEW: return "What Review must decide";
            case APPLY: return "Reviewed write authority";
            default: return "What Result means";
        }
    }

    private static String rightTitle(GuidedV019WorkspacePanel.Stage stage) {
        switch (stage) {
            case PREPARE: return "Conditions / prerequisites";
            case CAPTURE: return "Evidence to retain";
            case GUIDED_FOCUS: return "Cues / interpretation";
            case REVIEW: return "Comparison / next decision";
            case APPLY: return "Safety boundary";
            default: return "Keep / Restore boundary";
        }
    }

    private static String leftText(GuidedProductionTask task,
                                   GuidedV019WorkspacePanel.Stage stage,
                                   AeProjectSnapshot tune,
                                   GuidedV019ProductionBridge bridge,
                                   GuidedCoachBlueprint coach) {
        if (coach == null) {
            return task == null ? "No task selected."
                    : task.description + "\n\nThis task has no implemented production recipe yet.";
        }
        switch (stage) {
            case PREPARE:
                return coach.question + "\n\nWorking tune: "
                        + (tune == null ? "not read" : tune.getConfigurationName())
                        + "\nTask availability: "
                        + GuidedTaskAvailabilityAdapter.evaluate(task, tune).status;
            case CAPTURE:
                return coach.driverCue + "\n\nAudio/state sequence:\n" + coach.audio;
            case GUIDED_FOCUS:
                return coach.primaryVisual + "\n\nThe Focus surface is presentation only; the same production session remains authoritative.";
            case REVIEW:
                return coach.review + "\n\nCurrent production decision context:\n"
                        + safe(bridge == null ? null : bridge.proposalText());
            case APPLY:
                if (bridge != null && bridge.applyEnabled()) {
                    return "A real reviewed ProposalWritePlan is available.\n\n"
                            + safe(bridge.proposalText());
                }
                return "No production ProposalWritePlan is currently legal to apply. Review must remain fail-closed.";
            default:
                return bridge != null && bridge.restoreEnabled()
                        ? "An AE Tuner Apply has a verified Restore snapshot. Choose KEEP only after validation, or Restore the exact prior values."
                        : "No controller write was performed. Evidence may be retained and the session can finish as a no-write result.";
        }
    }

    private static String rightText(GuidedProductionTask task,
                                    GuidedV019WorkspacePanel.Stage stage,
                                    AeProjectSnapshot tune,
                                    GuidedV019ProductionBridge bridge,
                                    GuidedCoachBlueprint coach) {
        if (coach == null) return "No additional production guidance is available for this planned task.";
        switch (stage) {
            case PREPARE:
                return coach.futureConditions + "\n\nProduction recipe:\n" + task.productionRecipe.guidance;
            case CAPTURE:
                return coach.evidence + "\n\nCapture writes: NONE.\nBurn: NEVER.";
            case GUIDED_FOCUS:
                return coach.audio + "\n\nReview evidence:\n" + coach.evidence;
            case REVIEW:
                return coach.experiment + "\n\nFuture/coverage conditions:\n" + coach.futureConditions;
            case APPLY:
                return "ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot\n\n"
                        + "No alternate writer. No automatic final Apply. No Burn.";
            default:
                return "KEEP records acceptance of the verified applied state. Restore is legal only while the working tune still matches the AE Tuner-applied state.\n\n"
                        + "After Apply or Restore, read Working Tune again before new capture.";
        }
    }

    private static String authorityText(GuidedV019WorkspacePanel.Stage stage,
                                        GuidedV019ProductionBridge bridge) {
        if (stage == GuidedV019WorkspacePanel.Stage.APPLY) {
            return bridge != null && bridge.applyEnabled()
                    ? "Production plan ready — guarded coordinator remains sole write authority."
                    : "No write authority — return to Review or collect more evidence.";
        }
        return "Production AE Tuner owns evidence, recommendation, proposal, readback and Restore; this window explains the selected task only.";
    }

    private static JPanel card(String title, String text) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(AeUiTheme.card());
        panel.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),
                new EmptyBorder(12, 14, 12, 14)));
        JLabel heading = new JLabel(title);
        heading.setFont(H2);
        heading.setForeground(AeUiTheme.text());
        JTextArea body = new JTextArea(text == null ? "" : text);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFocusable(false);
        body.setBackground(AeUiTheme.card());
        body.setForeground(AeUiTheme.text());
        body.setFont(BODY);
        body.setBorder(null);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static void styleButton(JButton button) {
        button.setFocusPainted(false);
        button.setFont(new Font("Dialog", Font.BOLD, 11));
        button.setOpaque(true);
        button.setBackground(AeUiTheme.button());
        button.setForeground(AeUiTheme.text());
        button.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.buttonBorder()),
                new EmptyBorder(6, 10, 6, 10)));
        button.setPreferredSize(new Dimension(210, 32));
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "No reviewed production output yet.";
        return value;
    }
}
