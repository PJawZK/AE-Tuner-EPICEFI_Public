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

/** Dedicated v0.19 production Focus for Instant Fuel global setup. */
final class GuidedV019InstantFuelFocus extends GuidedV019FocusBase {
    private final JLabel cue = label("● READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = textArea("Start Capture from the main workflow.", 16, Font.BOLD);
    private final JTextArea guidance = textArea("", 11, Font.PLAIN);
    private final JLabel stateValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel enableAuthority = smallValue();
    private final JLabel multAuthority = smallValue();
    private final JLabel timerAuthority = smallValue();

    GuidedV019InstantFuelFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    @Override String taskTitle() { return "Global Pulse / Inhibit"; }
    @Override String taskSubtitle() { return "Instant Fuel pulse magnitude and re-arm spacing"; }

    @Override JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(new String[][]{
                {"1", "BASELINE", "Instant Fuel enabled", "green"},
                {"2", "OPEN", "capture first moment", "blue"},
                {"3", "PULSE", "measure response", "amber"},
                {"4", "RE-APPLY", "test inhibit spacing", "blue"},
                {"5", "REVIEW", "enable + mult + timer", "gray"}
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
        coach.add(label("COMPLETE SETUP — ENABLE • GLOBAL MULTIPLIER • INHIBIT CYCLES",
                9, Font.BOLD, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        center.add(coach);
        center.add(notePanel("What belongs in Global Setup",
                "With Instant Fuel enabled, short repeatable first-moment response establishes its global pulse size. Controlled rapid re-applies establish the inhibit-cycle spacing. A sustained same-direction lambda error is still an upstream TPS AE / MAP Predict / Wall problem and is not hidden here. If Instant Fuel is OFF, this entire task group is unavailable and collects no Guided evidence.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Production session",
                new String[]{"State", "Review", "Capture writes"},
                new JLabel[]{stateValue, reviewValue, fixed("NONE")}));
        lower.add(summaryGrid("Recommendation authority",
                new String[]{"Enable", "Global multiplier", "Inhibit cycles"},
                new JLabel[]{enableAuthority, multAuthority, timerAuthority}));
        lower.setPreferredSize(new java.awt.Dimension(100, 94));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    @Override JComponent diagnosticsContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AeUiTheme.focusBackground());
        root.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        root.add(label("Instant Fuel Setup — Production Review", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Firmware authority",
                "EpicEFI calculates pulse width from actualLastInjection multiplied by the RPM, TPS, MAP, CLT and latched delta-TPS correction curves, then by tpsExtraShotMult. tpsExtraShotTimer is the engine-cycle re-arm spacing. This setup task owns enable, global multiplier and timer together; Event Strength and Operating Conditions own the five curves.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        body.add(notePanel("Current production review",
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL_SETUP).reviewOutputs(),
                AeUiTheme.neutralSoft(), AeUiTheme.navy()));
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    @Override void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        boolean matching = state != null && state.recipe == GuidedTuningRecipe.INSTANT_FUEL_SETUP;
        GuidedCaptureState capture = matching ? state.captureState : GuidedCaptureState.IDLE;
        String text = matching && state.guidance != null && state.guidance.trim().length() > 0
                ? state.guidance : GuidedTuningRecipe.INSTANT_FUEL_SETUP.guidance;
        guidance.setText(text);
        guidance.setCaretPosition(0);
        stateValue.setText(String.valueOf(capture));
        boolean complete = capture == GuidedCaptureState.COMPLETE;
        boolean reviewReady = complete && GuidedFocusHub.isActiveEvidenceReviewReady();
        reviewValue.setText(reviewReady ? "READY" : "LOCKED");
        enableAuthority.setText("enabled-method evidence");
        multAuthority.setText("enabled-method evidence");
        timerAuthority.setText("re-apply evidence");

        if (reviewReady) {
            cue.setText("● REVIEW READY");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("Review first-moment response, global pulse magnitude and re-apply spacing together.");
        } else if (complete) {
            cue.setText("● MORE EVIDENCE NEEDED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Capture stopped before the evidence gate was satisfied. Press Continue Capture and retain the current evidence window.");
        } else if (capture == GuidedCaptureState.PAUSED) {
            cue.setText("● PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume only when the same opening and controlled re-apply can be repeated safely.");
        } else if (capture == GuidedCaptureState.CAPTURING) {
            cue.setText("● CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Repeat clean first-moment events, then controlled rapid re-applies; sustained errors are rejected from Instant authority.");
        } else {
            cue.setText("● READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Read Working Tune with Instant Fuel enabled, then capture after the upstream transient strategy is credible. If Instant Fuel is OFF, this task group remains unavailable.");
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
