package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

/** Modeless driver-facing Guided Focus pop-out. */
public final class GuidedFocusWindow extends JDialog {
    private static final String CARD_MAP_ESTIMATE = "map-estimate";
    private static final String CARD_ENGAGEMENT = "engagement";
    private static final String CARD_COACH_PROPOSAL = "coach-proposal";

    private final JLabel method = new JLabel("Guided Focus", SwingConstants.LEFT);
    private final JCheckBox alwaysOnTop = new JCheckBox("Always on top");
    private final JCheckBox driverView = new JCheckBox("Driver view", true);
    private final CardLayout cardsLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardsLayout);
    private final MapEstimateGuidedFocusPanel mapEstimate = new MapEstimateGuidedFocusPanel();
    private final EngagementDetectionGuidedFocusPanel engagement =
            new EngagementDetectionGuidedFocusPanel();
    private final GuidedCoachProposalPanel coachProposal =
            new GuidedCoachProposalPanel();
    private boolean locatedOnce;

    public GuidedFocusWindow(Window owner) {
        this(owner, GuidedFocusHub.mapEstimateConfigurationListener());
    }

    public GuidedFocusWindow(Window owner,
            MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateListener) {
        super(owner, "AE Tuner Guided Focus", ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(760, 480));
        setPreferredSize(new Dimension(1180, 720));
        mapEstimate.setConfigurationListener(mapEstimateListener);
        buildUi();
        pack();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        method.setFont(method.getFont().deriveFont(Font.BOLD, 18f));
        header.add(method, BorderLayout.CENTER);
        JPanel controls = new JPanel();
        controls.add(driverView);
        controls.add(alwaysOnTop);
        header.add(controls, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(7, 8, 5, 8));
        add(header, BorderLayout.NORTH);

        cards.add(mapEstimate, CARD_MAP_ESTIMATE);
        cards.add(engagement, CARD_ENGAGEMENT);
        cards.add(coachProposal, CARD_COACH_PROPOSAL);
        add(cards, BorderLayout.CENTER);

        alwaysOnTop.addActionListener(event -> setAlwaysOnTop(alwaysOnTop.isSelected()));
        driverView.addActionListener(event -> applyDriverView());
        applyDriverView();
    }

    /** Compatibility overload for older callers/tests without an engagement model. */
    public void update(GuidedTuningRecipe recipe,
                       GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel,
                       String fallbackGuidance) {
        update(recipe, state, mapEstimateModel, null, fallbackGuidance);
    }

    public void update(GuidedTuningRecipe recipe,
                       GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel,
                       EngagementFocusModel engagementModel,
                       String fallbackGuidance) {
        GuidedTuningRecipe safeRecipe = recipe == null
                ? GuidedTuningRecipe.BLEND_DURATION : recipe;
        GuidedCaptureState safeState = state == null
                ? GuidedCaptureState.IDLE : state;
        method.setText(safeRecipe.displayName + " — " + safeState.name());
        if (safeRecipe == GuidedTuningRecipe.MAP_ESTIMATE) {
            mapEstimate.updateModel(mapEstimateModel);
            cardsLayout.show(cards, CARD_MAP_ESTIMATE);
        } else if (safeRecipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            engagement.updateModel(engagementModel);
            cardsLayout.show(cards, CARD_ENGAGEMENT);
        } else {
            coachProposal.updateRecipe(safeRecipe);
            cardsLayout.show(cards, CARD_COACH_PROPOSAL);
        }
        applyDriverView();
    }

    public void openWindow() {
        if (!locatedOnce) {
            setLocationRelativeTo(getOwner());
            locatedOnce = true;
        }
        setVisible(true);
        toFront();
    }

    public void disposeWindow() {
        setVisible(false);
        dispose();
    }

    public MapEstimateGuidedFocusPanel mapEstimatePanelForTest() { return mapEstimate; }
    public EngagementDetectionGuidedFocusPanel engagementPanelForTest() { return engagement; }
    public GuidedCoachProposalPanel coachProposalPanelForTest() { return coachProposal; }
    public boolean driverViewForTest() { return driverView.isSelected(); }
    public boolean alwaysOnTopForTest() { return alwaysOnTop.isSelected(); }

    /** Compatibility helper retained for older UI regressions. */
    String taskGuideTextForTest() {
        return coachProposal.actionTextForTest() + "\n"
                + coachProposal.visualTextForTest() + "\n"
                + coachProposal.audioTextForTest() + "\n"
                + coachProposal.evidenceTextForTest() + "\n"
                + coachProposal.reviewTextForTest() + "\n"
                + coachProposal.experimentTextForTest() + "\n"
                + coachProposal.futureTextForTest();
    }

    private void applyDriverView() {
        boolean driver = driverView.isSelected();
        mapEstimate.setDriverView(driver);
        engagement.setDriverView(driver);
        coachProposal.setDriverView(driver);
    }
}
