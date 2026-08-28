package se.anders.tunerstudio.aetuner.guided.mapestimate;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

public final class MapEstimateFocusUiRegressionTest {
    public static void main(String[] args) throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                try { runOnEdt(); } catch (Throwable t) { failure.set(t); }
            }
        });
        if (failure.get() != null) throw new RuntimeException(failure.get());
        System.out.println("MapEstimateFocusUiRegressionTest passed");
    }

    private static void runOnEdt() throws Exception {
        basicFocusRenderingAndSelection();
        completedControllerSetupSurvivesFocusRefresh();
    }

    private static void basicFocusRenderingAndSelection() throws Exception {
        double[] tps={0,10,20,30};
        double[] rpm={1000,2000,3000,4000};
        double[][] current=new double[4][4];
        for(int r=0;r<4;r++)for(int c=0;c<4;c++)current[r][c]=35+r*10+c;
        MapEstimateEvidenceSession session=new MapEstimateEvidenceSession(null,"cfg",tps,rpm);
        session.start(MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,MapEstimateCellScope.all(4,4));
        for(int i=0;i<30;i++){
            session.acceptStable(0,1000,45+(i%3-1)*0.03,80,25);
            session.acceptStable(20,1000,70+(i%3-1)*0.03,80,25);
            session.acceptStable(0,3000,32+(i%3-1)*0.03,80,25);
        }
        MapEstimateFocusModel model=MapEstimateFocusModel.build(session,current,20,115,10,2000,"accepted stable evidence");
        require(model.currentRunSamples==90,"current-run sample count missing");
        require(model.directCount>=3,"direct cells not visible in focus model");
        require(model.interpolatedStrongCount>=1,"bounded interpolation not visible in focus model");
        require(model.proposalChangeCount>=3,"proposal mask not visible in focus model");
        require(model.evidenceBasis==MapEstimateEvidenceBasis.LEARNED_MEMORY,"compatibility Focus basis default changed");
        require(model.proposalLimitPolicy==MapEstimateProposalLimitPolicy.HIGH_TPS_CAP,"compatibility proposal-limit default changed");

        final AtomicReference<MapEstimateCellScope> requestedScope=new AtomicReference<MapEstimateCellScope>();
        final AtomicReference<MapEstimateCoverageStrategy> requestedStrategy=new AtomicReference<MapEstimateCoverageStrategy>();
        MapEstimateGuidedFocusPanel panel=new MapEstimateGuidedFocusPanel();
        panel.setConfigurationListener(new MapEstimateGuidedFocusPanel.ConfigurationListener(){
            @Override public void onStrategyRequested(MapEstimateCoverageStrategy value){requestedStrategy.set(value);}
            @Override public void onScopeRequested(MapEstimateCellScope value){requestedScope.set(value);}
        });
        panel.updateModel(model);
        require(!panel.strategyForTest().isEnabled(),"strategy remained editable during capture");
        require(!panel.scopeModeForTest().isEnabled(),"scope remained editable during capture");
        require(!panel.evidenceBasisForTest().isEnabled(),"evidence basis remained editable during capture");
        require(!panel.proposalLimitForTest().isEnabled(),"proposal limit remained editable during capture");
        require(String.valueOf(panel.tableForTest().getValueAt(0,1)).contains("✓D"),"direct provenance text missing");
        require(String.valueOf(panel.tableForTest().getValueAt(1,2)).contains("≈I") || model.cell(1,1).state!=MapEstimateSurface.State.INTERPOLATED_STRONG,
                "interpolated provenance text missing");

        session.finish();
        session.start(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,MapEstimateCellScope.none(4,4).withCell(1,1,true));
        session.finish(); // zero-sample complete state; configuration is now editable.
        MapEstimateFocusModel fine=MapEstimateFocusModel.build(session,current,20,115,10,2000,"waiting");
        panel.updateModel(fine);
        require(panel.strategyForTest().isEnabled(),"strategy did not unlock after capture");
        require(panel.scopeModeForTest().isEnabled(),"scope did not unlock after capture");
        require(panel.evidenceBasisForTest().isEnabled(),"evidence basis did not unlock after capture");
        require(panel.proposalLimitForTest().isEnabled(),"proposal limit did not unlock after capture");
        panel.scopeModeForTest().setSelectedItem(MapEstimateGuidedFocusPanel.ScopeMode.SELECTED_CELLS);
        panel.selectRectangleForTest(1,1,2,2);
        MapEstimateCellScope rect=panel.workingScopeForTest();
        require(rect.size()==4 && rect.contains(1,1)&&rect.contains(2,2),"rectangle selection failed");
        panel.toggleCellForTest(2,2);
        require(panel.workingScopeForTest().size()==3&&!panel.workingScopeForTest().contains(2,2),"individual selection toggle failed");
        require(requestedScope.get()!=null,"scope listener did not receive selection changes");
        panel.strategyForTest().setSelectedItem(MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE);
        require(requestedStrategy.get()==MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,"strategy listener did not receive requested strategy");
        panel.setDriverView(false);
        require(panel.tableForTest().getRowHeight()==36,"driver-view sizing did not update");
    }

    private static void completedControllerSetupSurvivesFocusRefresh() throws Exception {
        final double[] tps={0,10,20,30};
        final double[] rpm={1000,2000,3000,4000};
        final double[][] current=new double[4][4];
        for(int r=0;r<4;r++)for(int c=0;c<4;c++)current[r][c]=40+r*5+c;

        final MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        controller.configure("cfg",tps,rpm,current,20,115);
        controller.start();
        for(int i=0;i<25;i++)controller.acceptStable(0,1000,50,80,25);
        controller.finish();

        final MapEstimateGuidedFocusPanel panel=new MapEstimateGuidedFocusPanel();
        panel.setConfigurationListener(new MapEstimateGuidedFocusPanel.ConfigurationListener(){
            @Override public void onStrategyRequested(MapEstimateCoverageStrategy value){controller.setPendingStrategy(value);}
            @Override public void onScopeRequested(MapEstimateCellScope value){controller.setPendingScope(value);}
            @Override public void onEvidenceBasisRequested(MapEstimateEvidenceBasis value){controller.setPendingEvidenceBasis(value);}
            @Override public void onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy value){controller.setPendingProposalLimitPolicy(value);}
        });
        panel.updateModel(controller.focus(20,3000,"complete; setup next capture"));
        require(panel.strategyForTest().isEnabled(),"completed controller did not unlock strategy setup");
        require(panel.evidenceBasisForTest().isEnabled(),"completed controller did not unlock evidence-basis setup");
        require(panel.proposalLimitForTest().isEnabled(),"completed controller did not unlock proposal-limit setup");

        panel.strategyForTest().setSelectedItem(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        panel.scopeModeForTest().setSelectedItem(MapEstimateGuidedFocusPanel.ScopeMode.SELECTED_CELLS);
        panel.selectRectangleForTest(2,2,2,2);
        panel.evidenceBasisForTest().setSelectedItem(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        panel.proposalLimitForTest().setSelectedItem(MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        require(controller.pendingStrategy()==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "Focus strategy request did not reach controller pending setup");
        require(controller.pendingScope().size()==1&&controller.pendingScope().contains(2,2),
                "Focus selected scope did not reach controller pending setup");
        require(controller.pendingEvidenceBasis()==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "Focus evidence-basis request did not reach controller pending setup");
        require(controller.pendingProposalLimitPolicy()==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "Focus proposal-limit request did not reach controller pending setup");

        // This refresh physically exposed the dev18 strategy/scope bug. All
        // four next-capture controls must now survive the same normal refresh.
        panel.updateModel(controller.focus(20,3000,"normal refresh"));
        require(panel.strategyForTest().getSelectedItem()==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "normal Focus refresh snapped strategy back to the completed session");
        require(panel.workingScopeForTest().size()==1&&panel.workingScopeForTest().contains(2,2),
                "normal Focus refresh snapped scope back to whole table");
        require(panel.evidenceBasisForTest().getSelectedItem()==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "normal Focus refresh snapped evidence basis back to learned memory");
        require(panel.proposalLimitForTest().getSelectedItem()==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "normal Focus refresh snapped proposal limit back to capped mode");

        controller.start();
        MapEstimateFocusModel active=controller.focus(20,3000,"capturing");
        panel.updateModel(active);
        require(!panel.strategyForTest().isEnabled()&&!panel.scopeModeForTest().isEnabled(),
                "Focus strategy/scope setup did not lock when the next capture started");
        require(!panel.evidenceBasisForTest().isEnabled()&&!panel.proposalLimitForTest().isEnabled(),
                "Focus experiment setup did not lock when the next capture started");
        require(panel.strategyForTest().getSelectedItem()==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "active session did not inherit the requested Direct Fine Tune strategy");
        require(panel.workingScopeForTest().size()==1&&panel.workingScopeForTest().contains(2,2),
                "active session did not inherit the requested selected-cell scope");
        require(panel.evidenceBasisForTest().getSelectedItem()==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY
                        && active.evidenceBasis==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "active session did not inherit current-capture-only evidence basis");
        require(panel.proposalLimitForTest().getSelectedItem()==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP
                        && active.proposalLimitPolicy==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "active session did not inherit unrestricted proposal limit");
    }

    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
