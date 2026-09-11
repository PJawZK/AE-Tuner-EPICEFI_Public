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

/** Dedicated v0.19 production Focus for Wall Wetting. */
final class GuidedV019WallWettingFocus extends GuidedV019FocusBase {
    private final JLabel cue = label("● READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = textArea("Start Capture from the main workflow.", 16, Font.BOLD);
    private final JTextArea guidance = textArea("", 11, Font.PLAIN);
    private final JLabel stateValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel betaAuthority = smallValue();
    private final JLabel tauAuthority = smallValue();

    GuidedV019WallWettingFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    @Override String taskTitle() { return "Wall Film / Tau & Beta"; }
    @Override String taskSubtitle() { return "Wall-film transient evidence with Basic Beta authority"; }

    @Override JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(new String[][]{
                {"1", "BASELINE", "steady warm condition", "green"},
                {"2", "TIP-IN", "clean wall-active opening", "blue"},
                {"3", "SETTLE", "observe lambda response", "amber"},
                {"4", "TIP-OUT", "retain persistence evidence", "blue"},
                {"5", "REVIEW", "Beta first", "gray"}
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
        coach.add(label("BASIC MODEL: BETA AUTOMATIC  •  TAU CONTEXT  •  ADVANCED TABLES WITHHELD",
                9, Font.BOLD, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        center.add(coach);
        center.add(notePanel("Why Beta is first",
                "Firmware uses Beta as the deposited fuel fraction while Tau controls how long existing film persists through alpha. This first pass isolates amplitude from duration: clean tip-in response may move Beta; tip-out/persistence is retained for the later Tau pass.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Production session",
                new String[]{"State", "Review", "Capture writes"},
                new JLabel[]{stateValue, reviewValue, fixed("NONE")}));
        lower.add(summaryGrid("Recommendation authority",
                new String[]{"Basic Beta", "Tau", "Advanced wall tables"},
                new JLabel[]{betaAuthority, tauAuthority, fixed("evidence only")}));
        lower.setPreferredSize(new java.awt.Dimension(100, 94));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    @Override JComponent diagnosticsContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AeUiTheme.focusBackground());
        root.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        root.add(label("Wall Wetting — Production Review", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Firmware ownership",
                "Basic mode reads wwaeTau and wwaeBeta directly. Tau shapes alpha = exp(-120/(RPM×Tau)); Beta is the deposited fraction and is clamped below alpha. Firmware disables the wall model when Tau or Beta is below 0.01. This pass never auto-enables that state.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        body.add(notePanel("Current production review",
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.WALL_WETTING).reviewOutputs(),
                AeUiTheme.neutralSoft(), AeUiTheme.navy()));
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    @Override void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        boolean matching = state != null && state.recipe == GuidedTuningRecipe.WALL_WETTING;
        GuidedCaptureState capture = matching ? state.captureState : GuidedCaptureState.IDLE;
        String text = matching && state.guidance != null && state.guidance.trim().length() > 0
                ? state.guidance : GuidedTuningRecipe.WALL_WETTING.guidance;
        guidance.setText(text);
        guidance.setCaretPosition(0);
        stateValue.setText(String.valueOf(capture));
        boolean complete = capture == GuidedCaptureState.COMPLETE;
        reviewValue.setText(complete ? "READY" : "LOCKED");
        review.setEnabled(complete);
        betaAuthority.setText("bounded automatic");
        tauAuthority.setText("context / read only");

        if (complete) {
            cue.setText("● REVIEW READY");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("Review clean tip-in evidence and the bounded Basic Beta proposal. Keep Tau unchanged.");
        } else if (capture == GuidedCaptureState.PAUSED) {
            cue.setText("● PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume when another comparable wall-active transient can be performed safely.");
        } else if (capture == GuidedCaptureState.CAPTURING) {
            cue.setText("● CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Repeat clean tip-ins at similar warm RPM/load; include tip-outs for later Tau/persistence diagnosis.");
        } else {
            cue.setText("● READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Read Working Tune, then Start Capture. Basic Tau/Beta must be available for numerical review.");
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
