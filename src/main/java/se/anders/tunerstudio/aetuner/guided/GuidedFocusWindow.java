package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;

/** Modeless driver-facing Guided Focus pop-out. */
public final class GuidedFocusWindow extends JDialog {
    private static final String CARD_BLEND_DURATION = "blend-duration";
    private static final String CARD_MAP_ESTIMATE = "map-estimate";
    private static final String CARD_ENGAGEMENT = "engagement";
    private static final String CARD_FOUNDATION_THRESHOLD = "foundation-threshold";
    private static final String CARD_COACH_PROPOSAL = "coach-proposal";

    private final JLabel method = new JLabel("Guided Focus", SwingConstants.LEFT);
    private final JCheckBox alwaysOnTop = new JCheckBox("Always on top");
    private final JCheckBox driverView = new JCheckBox("Driver view", true);
    private final CardLayout cardsLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardsLayout);
    private final BlendDurationGuidedFocusPanel blendDuration = new BlendDurationGuidedFocusPanel();
    private final MapEstimateGuidedFocusPanel mapEstimate = new MapEstimateGuidedFocusPanel();
    private final EngagementDetectionGuidedFocusPanel engagement = new EngagementDetectionGuidedFocusPanel();
    private final EngagementScrollHost engagementHost = new EngagementScrollHost();
    private final JScrollPane engagementScroll = new JScrollPane(
            engagementHost, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final FoundationThresholdGuidedFocusPanel foundationThreshold = new FoundationThresholdGuidedFocusPanel();
    private final GuidedCoachProposalPanel coachProposal = new GuidedCoachProposalPanel();
    private GuidedTuningRecipe currentRecipe = GuidedTuningRecipe.BLEND_DURATION;
    private String visibleCard = "";
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

        engagementHost.add(engagement, BorderLayout.CENTER);
        engagementScroll.setBorder(BorderFactory.createEmptyBorder());
        engagementScroll.getVerticalScrollBar().setUnitIncrement(24);
        engagementScroll.getViewport().setBackground(engagement.getBackground());

        cards.add(blendDuration, CARD_BLEND_DURATION);
        cards.add(mapEstimate, CARD_MAP_ESTIMATE);
        cards.add(engagementScroll, CARD_ENGAGEMENT);
        cards.add(foundationThreshold, CARD_FOUNDATION_THRESHOLD);
        cards.add(coachProposal, CARD_COACH_PROPOSAL);
        add(cards, BorderLayout.CENTER);

        alwaysOnTop.addActionListener(event -> setAlwaysOnTop(alwaysOnTop.isSelected()));
        driverView.addActionListener(event -> applyDriverView());
        applyDriverView();
    }

    public void update(GuidedTuningRecipe recipe, GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel, String fallbackGuidance) {
        update(recipe, state, mapEstimateModel, null, null, null, fallbackGuidance);
    }

    public void update(GuidedTuningRecipe recipe, GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel,
                       EngagementFocusModel engagementModel, String fallbackGuidance) {
        update(recipe, state, mapEstimateModel, engagementModel, null, null, fallbackGuidance);
    }

    public void update(GuidedTuningRecipe recipe, GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel,
                       EngagementFocusModel engagementModel,
                       FoundationThresholdFocusModel thresholdModel,
                       String fallbackGuidance) {
        update(recipe, state, mapEstimateModel, engagementModel, thresholdModel, null, fallbackGuidance);
    }

    public void update(GuidedTuningRecipe recipe, GuidedCaptureState state,
                       MapEstimateFocusModel mapEstimateModel,
                       EngagementFocusModel engagementModel,
                       FoundationThresholdFocusModel thresholdModel,
                       BlendDurationFocusModel blendDurationModel,
                       String fallbackGuidance) {
        GuidedTuningRecipe safeRecipe = recipe == null ? GuidedTuningRecipe.BLEND_DURATION : recipe;
        GuidedCaptureState safeState = state == null ? GuidedCaptureState.IDLE : state;
        currentRecipe = safeRecipe;
        method.setText(safeRecipe.displayName + " — " + safeState.name());
        if (safeRecipe == GuidedTuningRecipe.BLEND_DURATION) {
            blendDuration.updateModel(blendDurationModel, fallbackGuidance);
            showCard(CARD_BLEND_DURATION);
        } else if (safeRecipe == GuidedTuningRecipe.MAP_ESTIMATE) {
            mapEstimate.updateModel(mapEstimateModel);
            showCard(CARD_MAP_ESTIMATE);
        } else if (safeRecipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            engagement.updateModel(engagementModel);
            refreshEngagementCard();
        } else if (safeRecipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD) {
            foundationThreshold.updateModel(thresholdModel, fallbackGuidance);
            showCard(CARD_FOUNDATION_THRESHOLD);
        } else {
            coachProposal.updateRecipe(safeRecipe);
            showCard(CARD_COACH_PROPOSAL);
        }
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

    public BlendDurationGuidedFocusPanel blendDurationPanelForTest() { return blendDuration; }
    public MapEstimateGuidedFocusPanel mapEstimatePanelForTest() { return mapEstimate; }
    public EngagementDetectionGuidedFocusPanel engagementPanelForTest() { return engagement; }
    public FoundationThresholdGuidedFocusPanel foundationThresholdPanelForTest() { return foundationThreshold; }
    public GuidedCoachProposalPanel coachProposalPanelForTest() { return coachProposal; }
    public boolean driverViewForTest() { return driverView.isSelected(); }
    public boolean alwaysOnTopForTest() { return alwaysOnTop.isSelected(); }
    String visibleCardForTest() { return visibleCard; }

    boolean engagementDetailsScrollEnabledForTest() {
        return engagementScroll.getHorizontalScrollBarPolicy() == JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                && engagementScroll.getVerticalScrollBarPolicy() == JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                && !engagementHost.getScrollableTracksViewportHeight();
    }
    boolean engagementDetailsNotCompressedForTest() { return engagement.getHeight() + 1 >= engagement.getPreferredSize().height; }
    boolean engagementDetailsScrollbarVisibleForTest() { return engagementScroll.getVerticalScrollBar().isVisible(); }
    boolean engagementDetailsAlignmentNormalizedForTest() {
        return visiblePassiveDetailsFillWidth(engagement);
    }

    String taskGuideTextForTest() {
        return coachProposal.actionTextForTest() + "\n" + coachProposal.visualTextForTest() + "\n"
                + coachProposal.audioTextForTest() + "\n" + coachProposal.evidenceTextForTest() + "\n"
                + coachProposal.reviewTextForTest() + "\n" + coachProposal.experimentTextForTest() + "\n"
                + coachProposal.futureTextForTest();
    }

    private void applyDriverView() {
        boolean driver = driverView.isSelected();
        blendDuration.setDriverView(driver);
        mapEstimate.setDriverView(driver);
        engagement.setDriverView(driver);
        foundationThreshold.setDriverView(driver);
        engagementHost.setTrackViewportHeight(driver);
        engagementScroll.setVerticalScrollBarPolicy(driver
                ? JScrollPane.VERTICAL_SCROLLBAR_NEVER : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        engagementScroll.getVerticalScrollBar().setValue(0);
        engagementHost.revalidate();
        engagementScroll.revalidate();
        coachProposal.setDriverView(driver);
        refreshEngagementCard();
    }

    private void refreshEngagementCard() {
        if (currentRecipe != GuidedTuningRecipe.ENGAGEMENT_DETECTION) return;
        showCard(CARD_ENGAGEMENT);
    }

    private void showCard(String card) {
        if (card == null || card.equals(visibleCard)) return;
        cardsLayout.show(cards, card);
        visibleCard = card;
    }

    private static boolean visiblePassiveDetailsFillWidth(Container root) {
        if (root == null || root.getWidth() <= 0) return false;
        boolean found = false;
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JPanel && ((JPanel) child).getBorder() instanceof TitledBorder) {
                found = true;
                if (child.getWidth() + 8 < root.getWidth()) return false;
            }
            if (child instanceof Container && visiblePassiveDetailsFillWidth((Container) child)) {
                found = true;
            }
        }
        return found;
    }

    private static final class EngagementScrollHost extends JPanel implements Scrollable {
        private boolean trackViewportHeight = true;
        EngagementScrollHost() { super(new BorderLayout()); }
        void setTrackViewportHeight(boolean trackViewportHeight) {
            if (this.trackViewportHeight == trackViewportHeight) return;
            this.trackViewportHeight = trackViewportHeight;
            revalidate();
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(24, extent - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return trackViewportHeight; }
    }
}
