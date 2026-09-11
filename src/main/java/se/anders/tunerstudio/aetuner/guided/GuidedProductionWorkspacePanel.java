package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Production host for the structurally ported v0.19 UI.
 *
 * The legacy surface is deliberately not inserted into this component tree.
 * AeTunerPlugin retains it separately and selects it only when
 * -Dae.tuner.guided.ui.v019=false is explicitly supplied.
 */
public final class GuidedProductionWorkspacePanel extends JPanel {
    private static final String FEATURE_PROPERTY = "ae.tuner.guided.ui.v019";

    /** Permanent structural contract mirrored by the v0.19 workspace stepper. */
    private enum Stage {
        PREPARE("Prepare"),
        CAPTURE("Capture"),
        GUIDED_FOCUS("Guided Focus"),
        REVIEW("Review"),
        APPLY("Apply"),
        RESULT("Result");

        final String title;
        Stage(String title) { this.title = title; }
    }

    private final GuidedCapturePanel productionPanel;
    private final GuidedV019WorkspacePanel v019;

    /** v0.19 is the default; explicit false preserves the exact legacy UI. */
    public static boolean featureEnabled() {
        return Boolean.parseBoolean(System.getProperty(FEATURE_PROPERTY, "true"));
    }

    public GuidedProductionWorkspacePanel(JComponent legacySurface,
                                          GuidedCapturePanel productionPanel) {
        this(legacySurface, productionPanel, GuidedV019UtilityActions.NONE);
    }

    public GuidedProductionWorkspacePanel(JComponent legacySurface,
                                          GuidedCapturePanel productionPanel,
                                          GuidedV019UtilityActions utilities) {
        super(new BorderLayout());
        if (legacySurface == null) throw new IllegalArgumentException("legacySurface");
        if (productionPanel == null) throw new IllegalArgumentException("productionPanel");
        this.productionPanel = productionPanel;
        this.v019 = new GuidedV019WorkspacePanel(productionPanel, utilities);
        add(v019, BorderLayout.CENTER);
    }

    public void setCurrentTune(AeProjectSnapshot snapshot) {
        v019.setCurrentTune(snapshot);
    }

    int taskCountForTest() { return v019.taskCountForTest(); }
    String taskNameForTest(int index) { return v019.taskNameForTest(index); }
    boolean taskAvailableForTest(GuidedProductionTask task) { return v019.taskAvailableForTest(task); }
    String taskStatusForTest(GuidedProductionTask task) { return v019.taskStatusForTest(task); }
    String groupStateForTest(GuidedProductionTask.Group group) { return v019.groupStateForTest(group); }
    String visibleStageForTest() { return v019.visibleStageForTest(); }
    JButton mapValidationToggleForTest() { return v019.mapValidationToggleForTest(); }
    boolean mapValidationActiveForTest() { return v019.mapValidationActiveForTest(); }
    boolean legacySurfaceRenderedForTest() { return v019.legacySurfaceRenderedForTest(); }
    int stageCardCountForTest() { return v019.stageCardCountForTest(); }
    boolean mainUsesTwoByThreeCardsForTest() { return v019.mainUsesTwoByThreeCardsForTest(); }
    JButton prepareButtonForTest() { return v019.prepareButtonForTest(); }
    JButton captureButtonForTest() { return v019.captureButtonForTest(); }
    JButton passiveAnalysisButtonForTest() { return v019.passiveAnalysisButtonForTest(); }
    JButton diagnosticsButtonForTest() { return v019.diagnosticsButtonForTest(); }
    JComponent prepareInfoViewForTest() {
        return v019.stepInfoViewForTest(GuidedV019WorkspacePanel.Stage.PREPARE);
    }
    JComponent captureInfoViewForTest() {
        return v019.stepInfoViewForTest(GuidedV019WorkspacePanel.Stage.CAPTURE);
    }
    JComponent focusViewForTest(GuidedProductionTask task) {
        return GuidedV019FocusViews.create(task, null, null);
    }
    JComponent tpsFocusViewForTest() {
        return focusViewForTest(GuidedProductionTask.TPS_MOVEMENT_TIMING);
    }
    JComponent reviewViewForTest() { return v019.reviewViewForTest(); }
    JComponent applyViewForTest() { return v019.applyViewForTest(); }
    JComponent resultViewForTest(boolean applied) { return v019.resultViewForTest(applied); }

    @SuppressWarnings("unused")
    private void selectTask(GuidedProductionTask task) {
        v019.selectTaskForTest(task);
    }
}
