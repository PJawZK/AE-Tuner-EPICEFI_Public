package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** Dedicated v0.19 production Focus for TPS AE fuel response / decay. */
final class GuidedV019TpsAeFocus extends GuidedV019FocusBase {
    private final JLabel cue = label("● READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = textArea("Start Capture from the main workflow.", 16, Font.BOLD);
    private final JTextArea guidance = textArea("", 11, Font.PLAIN);
    private final JLabel stateValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel tableAuthority = smallValue();
    private final JLabel otherAuthority = smallValue();

    GuidedV019TpsAeFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    @Override String taskTitle() { return "Fuel Response / Decay"; }
    @Override String taskSubtitle() { return "TPS AE table evidence and guarded multi-cell proposal"; }

    @Override JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(new String[][]{
                {"1", "STEADY", "clean pre-event baseline", "green"},
                {"2", "OPEN", "repeat comparable tip-in", "blue"},
                {"3", "RESPOND", "observe lambda + AE", "amber"},
                {"4", "REPEAT", "prove TPS-to rows", "blue"},
                {"5", "REVIEW", "table cells only", "gray"}
        }), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));
        center.setOpaque(false);
        JPanel coach = card(new BorderLayout(0, 8));
        coach.setBorder(cardBorder());
        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        head.add(cue, BorderLayout.NORTH);
        head.add(action, BorderLayout.SOUTH);
        coach.add(head, BorderLayout.NORTH);
        guidance.setForeground(AeUiTheme.focusText());
        coach.add(guidance, BorderLayout.CENTER);
        coach.add(label("PRODUCTION TABLE ENGINE — no presentation-side tuning math",
                9, Font.BOLD, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        center.add(coach);
        center.add(notePanel("What this pass owns",
                "The existing production TPS AE event accumulator and AeTableSuggestion engine remain authoritative. Repeated fuel-proved events may propose only evidence-backed tpsAeCycleValues cells. RPM/CLT scaling and detector settings are deliberately outside this task.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Production session",
                new String[]{"State", "Review", "Capture writes"},
                new JLabel[]{stateValue, reviewValue, fixed("NONE")}));
        lower.add(summaryGrid("Recommendation authority",
                new String[]{"TPS AE table", "Scaling / compensation", "Apply path"},
                new JLabel[]{tableAuthority, otherAuthority, fixed("guarded existing path")}));
        lower.setPreferredSize(new java.awt.Dimension(100, 94));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    @Override JComponent diagnosticsContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AeUiTheme.focusBackground());
        root.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        root.add(label("TPS AE — Production Review", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Method boundary",
                "Foundation owns event detection. Fuel Response / Decay owns the TPS-to × cycle response table. Existing AeTableSuggestion evidence/recommendation math is preserved unchanged. Scaling / Compensation remains a separate future task.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        body.add(notePanel("Current production review",
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.TPS_AE).reviewOutputs(),
                AeUiTheme.neutralSoft(), AeUiTheme.navy()));
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    @Override void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        boolean matching = state != null && state.recipe == GuidedTuningRecipe.TPS_AE;
        GuidedCaptureState capture = matching ? state.captureState : GuidedCaptureState.IDLE;
        String text = matching && state.guidance != null && state.guidance.trim().length() > 0
                ? state.guidance : GuidedTuningRecipe.TPS_AE.guidance;
        guidance.setText(text);
        guidance.setCaretPosition(0);
        stateValue.setText(String.valueOf(capture));
        boolean complete = capture == GuidedCaptureState.COMPLETE;
        reviewValue.setText(complete ? "READY" : "LOCKED");
        review.setEnabled(complete);
        tableAuthority.setText("evidence-backed cells");
        otherAuthority.setText("outside this task");

        if (complete) {
            cue.setText("● REVIEW READY");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("Review the fuel-proved event set and the existing TPS AE multi-cell proposal.");
        } else if (capture == GuidedCaptureState.PAUSED) {
            cue.setText("● PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume when the next comparable clean tip-in can be performed safely.");
        } else if (capture == GuidedCaptureState.CAPTURING) {
            cue.setText("● CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Repeat comparable tip-ins and let the production table accumulator prove the same TPS-to rows.");
        } else {
            cue.setText("● READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Read Working Tune, then Start Capture for TPS AE Fuel Response / Decay.");
        }
        repaint();
    }

    private static JLabel fixed(String text) {
        JLabel value = smallValue();
        value.setText(text);
        return value;
    }

    private static JTextArea textArea(String text, int size, int style) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(new Font("Dialog", style, size));
        area.setForeground(AeUiTheme.focusText());
        return area;
    }
}
