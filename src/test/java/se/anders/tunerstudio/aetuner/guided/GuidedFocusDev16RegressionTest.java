package se.anders.tunerstudio.aetuner.guided;

import javax.swing.SwingUtilities;
import se.anders.tunerstudio.aetuner.guided.mapestimate.*;

public final class GuidedFocusDev16RegressionTest {
    public static void main(String[] args) throws Exception {
        hubCarriesNewSurfaceModel();
        windowReusesNewPanelAndDoesNotOwnCapture();
        System.out.println("GuidedFocusDev16RegressionTest passed");
    }

    private static void hubCarriesNewSurfaceModel() throws Exception {
        MapEstimateGuidedController c=new MapEstimateGuidedController(null);
        double[] t={0,10},r={1000,2000};double[][] table={{40,45},{50,55}};
        c.configure("cfg",t,r,table,3,115);
        MapEstimateFocusModel model=c.focus(Double.NaN,Double.NaN,"waiting");
        GuidedFocusHub.publishMapEstimateSetup(model,"ready");
        GuidedFocusHub.State s=GuidedFocusHub.snapshot();
        require(s.recipe==GuidedTuningRecipe.MAP_ESTIMATE,"hub recipe changed");
        require(s.captureState==GuidedCaptureState.IDLE,"setup state changed");
        require(s.mapEstimate==model,"hub did not retain immutable dev16 model");
    }

    private static void windowReusesNewPanelAndDoesNotOwnCapture() throws Exception {
        final GuidedFocusWindow[] box=new GuidedFocusWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            box[0]=new GuidedFocusWindow(null);
            box[0].update(GuidedTuningRecipe.MAP_ESTIMATE,GuidedCaptureState.IDLE,null,"ready");
            require(box[0].driverViewForTest(),"driver view should default on");
            require(box[0].mapEstimatePanelForTest()!=null,"dev16 MAP panel missing");
            box[0].disposeWindow();
        });
    }

    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
