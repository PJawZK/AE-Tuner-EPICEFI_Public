package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * v0.19 production Focus for implemented evidence tasks which do not yet have a
 * dedicated numerical Focus model. Maneuver coaching comes from the task's
 * GuidedCoachBlueprint while production Guided remains the sole evidence and
 * recommendation authority.
 */
final class GuidedV019BoundEvidenceFocus extends GuidedV019FocusBase {
    private final GuidedProductionTask task;
    private final GuidedCoachBlueprint coach;
    private final JLabel cue = label("READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = actionArea("Start the production evidence session.");
    private final JLabel stateValue = smallValue();
    private final JLabel authorityValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel recipeValue = smallValue();
    private final JTextArea guidance = new JTextArea();

    GuidedV019BoundEvidenceFocus(GuidedProductionTask task, JDialog owner, Runnable done) {
        super(owner, done);
        if (task == null || task.productionRecipe == null) throw new IllegalArgumentException("task");
        this.task = task;
        this.coach = GuidedCoachCatalog.forRecipe(task.productionRecipe);
        build();
    }

    String taskTitle() { return task.displayName; }
    String taskSubtitle() { return coach.archetype.label + " — production evidence capture"; }

    JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(sequenceFor(task)), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));
        center.setOpaque(false);

        JPanel instructions = new JPanel();
        instructions.setOpaque(false);
        instructions.setLayout(new BoxLayout(instructions, BoxLayout.Y_AXIS));
        JComponent doNow = notePanel("DO THIS NOW", coach.driverCue,
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue());
        doNow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JComponent evidence = notePanel("WHAT COUNTS AS USEFUL EVIDENCE", coach.evidence,
                AeUiTheme.focusSoftGreen(), AeUiTheme.focusGreen());
        evidence.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        instructions.add(doNow);
        instructions.add(Box.createVerticalStrut(8));
        instructions.add(evidence);
        center.add(instructions);

        JPanel productionCoach = card(new BorderLayout(0, 8));
        productionCoach.setBorder(cardBorder());
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        heading.add(cue, BorderLayout.NORTH);
        heading.add(action, BorderLayout.SOUTH);
        productionCoach.add(heading, BorderLayout.NORTH);
        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setFocusable(false);
        guidance.setOpaque(false);
        guidance.setFont(BODY);
        guidance.setForeground(AeUiTheme.focusText());
        productionCoach.add(guidance, BorderLayout.CENTER);
        JLabel boundary = label(
                "INSTRUCTIONAL VIEW — production capture/evidence remains authoritative; no synthetic event data",
                9, Font.BOLD, AeUiTheme.focusMuted());
        boundary.setHorizontalAlignment(SwingConstants.LEFT);
        productionCoach.add(boundary, BorderLayout.SOUTH);
        center.add(productionCoach);
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Production session", new String[]{"State", "Recipe", "Review"},
                new JLabel[]{stateValue, recipeValue, reviewValue}));
        lower.add(summaryGrid("Authority", new String[]{"Evidence", "Proposal", "Burn"},
                new JLabel[]{authorityValue, fixed("existing production engine"), fixed("NEVER")}));
        lower.setPreferredSize(new java.awt.Dimension(100, 94));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    JComponent diagnosticsContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AeUiTheme.focusBackground());
        root.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label(task.displayName + " — Diagnostics", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
        head.add(label("Production recipe boundary and current Guided session state.", 11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Task experiment", coach.question + "\n\n" + task.productionRecipe.guidance,
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        GuidedFocusHub.State state = matchingState();
        body.add(infoTable("Current production state", new String[][]{
                {"Capture", String.valueOf(state == null ? GuidedCaptureState.IDLE : state.captureState)},
                {"Recipe status", task.productionRecipe.status},
                {"Guidance source", state != null && state.guidance.length() > 0 ? "active production session" : "production recipe"},
                {"Review gate", state != null && state.captureState == GuidedCaptureState.COMPLETE ? "READY" : "LOCKED"},
                {"Write path", "Review → guarded ProposalWritePlan only"},
                {"Burn", "NEVER"}
        }));
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    void refreshFromProduction() {
        GuidedFocusHub.State state = matchingState();
        GuidedCaptureState captureState = state == null
                ? GuidedFocusHub.activeCaptureState() : state.captureState;
        String activeGuidance = state != null && state.guidance != null
                && state.guidance.trim().length() > 0
                ? state.guidance : task.productionRecipe.guidance;
        guidance.setText("WHAT THIS TASK IS PROVING\n" + coach.question
                + "\n\nPRODUCTION CAPTURE CONTEXT\n" + activeGuidance);
        guidance.setCaretPosition(0);
        stateValue.setText(String.valueOf(captureState));
        recipeValue.setText(trim(task.productionRecipe.displayName, 32));
        recipeValue.setToolTipText(task.productionRecipe.status);
        authorityValue.setText("production evidence only");
        boolean complete = captureState == GuidedCaptureState.COMPLETE;
        reviewValue.setText(complete ? "READY" : "LOCKED");
        review.setEnabled(complete);

        if (captureState == GuidedCaptureState.COMPLETE) {
            cue.setText("● CAPTURE COMPLETE");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("Export Evidence for this task, then open Review Results.");
        } else if (captureState == GuidedCaptureState.PAUSED) {
            cue.setText("● PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume when it is safe to continue this maneuver: " + coach.driverCue);
        } else if (captureState == GuidedCaptureState.CAPTURING) {
            cue.setText("● CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("DO NOW — " + coach.driverCue);
        } else {
            cue.setText("● READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Start Capture from the main workflow. Then follow the DO THIS NOW instruction on this screen.");
        }
        repaint();
    }

    private GuidedFocusHub.State matchingState() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        return state != null && state.recipe == task.productionRecipe ? state : null;
    }

    private static JTextArea actionArea(String text) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(3);
        area.setFont(new Font("Dialog", Font.BOLD, 15));
        area.setForeground(AeUiTheme.focusText());
        return area;
    }

    private static JLabel fixed(String text) {
        JLabel l = smallValue();
        l.setText(text);
        return l;
    }

    private static String[][] sequenceFor(GuidedProductionTask task) {
        switch (task) {
            case TPS_SCALING:
                return seq("BASELINE", "steady / in band", "OPEN", "standardized step",
                        "HOLD", "complete AE event", "RECOVER", "lambda settles", "REPEAT", "other RPM / CLT band");
            case TPS_COMPLETION:
                return seq("BASELINE", "steady", "OPEN", "clean event",
                        "HOLD", "through AE tail", "RECOVER", "closed loop returns", "REPEAT", "include re-apply");
            case TPS_VALIDATION:
                return seq("SMALL", "clean opening", "MEDIUM", "clean opening",
                        "REAPPLY", "lift then reopen", "STACK", "short repeated stabs", "REVIEW", "classify failures");
            case WALL_ADVANCED:
                return seq("IN BAND", "RPM / MAP / CLT", "TIP-IN", "hold / recover",
                        "TIP-OUT", "hold / recover", "REPEAT", "same condition", "NEXT BAND", "coverage");
            case WALL_VALIDATION:
                return seq("BASELINE", "steady", "TIP-IN", "hold / recover",
                        "TIP-OUT", "hold / recover", "REPEAT", "paired transition", "REVIEW", "balance / decay");
            case INSTANT_EVENT_STRENGTH:
                return seq("BASELINE", "steady", "OPEN", "small accepted change",
                        "RECOVER", "full settle", "OPEN", "larger accepted change", "REPEAT", "cover ΔTPS bins");
            case INSTANT_CONDITIONS:
                return seq("IN BAND", "RPM / TPS / MAP / CLT", "OPEN", "same event severity",
                        "RECOVER", "full settle", "REPEAT", "same band", "NEXT BAND", "coverage");
            case INSTANT_VALIDATION:
                return seq("BASELINE", "steady", "OPEN", "sharp event",
                        "REAPPLY", "controlled rapid repeat", "RECOVER", "full settle", "REVIEW", "early residual only");
            default:
                return seq("BASELINE", "steady", "EVENT", "requested maneuver",
                        "HOLD", "complete response", "RECOVER", "full settle", "REPEAT", "comparable event");
        }
    }

    private static String[][] seq(String a, String ad, String b, String bd,
                                  String c, String cd, String d, String dd,
                                  String e, String ed) {
        return new String[][]{
                {"1", a, ad, "green"},
                {"2", b, bd, "blue"},
                {"3", c, cd, "blue"},
                {"4", d, dd, "amber"},
                {"5", e, ed, "gray"}
        };
    }
}
