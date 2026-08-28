package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Non-authoritative, coaching-first preview for Guided tasks that do not yet
 * have a dedicated visual implementation.
 *
 * Driver View intentionally has no root scroll pane. Live implementations must
 * preserve this contract: frequent ECU updates may repaint values, but may not
 * resize the root layout or steal the user's viewport position.
 */
public final class GuidedCoachProposalPanel extends JPanel {
    private final JLabel title = new JLabel("Guided coach proposal");
    private final JLabel archetype = new JLabel("Interaction: n/a");
    private final JTextArea action = area("Choose a Guided task.", 25f, Font.BOLD);
    private final JTextArea visual = area("Primary visual proposal unavailable.", 14f, Font.PLAIN);
    private final JTextArea audio = area("Audio proposal unavailable.", 14f, Font.PLAIN);
    private final JTextArea evidence = area("Evidence proposal unavailable.", 13f, Font.PLAIN);
    private final JTextArea review = area("Review proposal unavailable.", 13f, Font.PLAIN);
    private final JTextArea experiment = area("A/B proposal unavailable.", 13f, Font.PLAIN);
    private final JTextArea future = area("Future-condition proposal unavailable.", 13f, Font.PLAIN);
    private final JPanel reviewRow = new JPanel(new GridLayout(1, 2, 8, 8));
    private final JPanel futureRow = new JPanel(new GridLayout(1, 2, 8, 8));
    private boolean driverView = true;

    public GuidedCoachProposalPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new BorderLayout(8, 2));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        archetype.setFont(archetype.getFont().deriveFont(Font.PLAIN, 13f));
        header.add(title, BorderLayout.CENTER);
        header.add(archetype, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(3, 1, 8, 8));
        body.add(wrap("WHAT TO DO / TUNING QUESTION", action));

        JPanel liveRow = new JPanel(new GridLayout(1, 2, 8, 8));
        liveRow.add(wrap("PRIMARY VISUAL", visual));
        liveRow.add(wrap("AUDIO / EYES-UP CUES", audio));
        body.add(liveRow);

        reviewRow.add(wrap("EVIDENCE TO OBTAIN", evidence));
        reviewRow.add(wrap("POST-EVENT / REVIEW", review));
        body.add(reviewRow);
        add(body, BorderLayout.CENTER);

        futureRow.add(wrap("A/B OR COVERAGE PLAN", experiment));
        futureRow.add(wrap("PREREQUISITES / FUTURE CONDITIONS", future));
        add(futureRow, BorderLayout.SOUTH);
        setDriverView(true);
    }

    public void updateRecipe(GuidedTuningRecipe recipe) {
        GuidedCoachBlueprint blueprint = GuidedCoachCatalog.forRecipe(recipe);
        title.setText(blueprint.recipe.displayName + " — COACH PROPOSAL");
        archetype.setText("Interaction: " + blueprint.archetype.label);
        action.setText(blueprint.question + "\n\n" + blueprint.driverCue);
        visual.setText(blueprint.primaryVisual);
        audio.setText(blueprint.audio);
        evidence.setText(blueprint.evidence);
        review.setText(blueprint.review);
        experiment.setText(blueprint.experiment);
        future.setText(blueprint.futureConditions);
        resetCarets();
    }

    public void setDriverView(boolean driver) {
        driverView = driver;
        reviewRow.setVisible(!driver);
        futureRow.setVisible(!driver);
        action.setFont(action.getFont().deriveFont(Font.BOLD, driver ? 28f : 22f));
        visual.setFont(visual.getFont().deriveFont(driver ? 15f : 14f));
        audio.setFont(audio.getFont().deriveFont(driver ? 15f : 14f));
        revalidate();
        repaint();
    }

    public boolean driverViewForTest() { return driverView; }
    public String actionTextForTest() { return action.getText(); }
    public String visualTextForTest() { return visual.getText(); }
    public String audioTextForTest() { return audio.getText(); }
    public String evidenceTextForTest() { return evidence.getText(); }
    public String reviewTextForTest() { return review.getText(); }
    public String experimentTextForTest() { return experiment.getText(); }
    public String futureTextForTest() { return future.getText(); }
    public boolean reviewVisibleForTest() { return reviewRow.isVisible(); }
    public boolean futureVisibleForTest() { return futureRow.isVisible(); }

    private void resetCarets() {
        action.setCaretPosition(0);
        visual.setCaretPosition(0);
        audio.setCaretPosition(0);
        evidence.setCaretPosition(0);
        review.setCaretPosition(0);
        experiment.setCaretPosition(0);
        future.setCaretPosition(0);
    }

    private static JPanel wrap(String name, JTextArea content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(name));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea area(String value, float size, int style) {
        JTextArea text = new JTextArea(value);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setFont(text.getFont().deriveFont(style, size));
        text.setMargin(new java.awt.Insets(6, 7, 6, 7));
        return text;
    }
}
