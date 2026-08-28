package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceBasis;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposalLimitPolicy;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Proves the real GuidedMethodProbeSession owns MAP experiment controls and leg boundaries. */
public final class GuidedMapEstimateExperimentRoutingRegressionTest {
    public static void main(String[] args) {
        realGuidedFocusListenerRoutesAndLocksExperimentControls();
        continueStartsFreshCollectorWindowWithoutDeletingMemory();
        System.out.println("GuidedMapEstimateExperimentRoutingRegressionTest passed");
    }

    private static void realGuidedFocusListenerRoutesAndLocksExperimentControls() {
        GuidedMethodProbeSession session=new GuidedMethodProbeSession();
        AeProjectSnapshot snapshot=mapSnapshot();
        session.configureMapEstimateFocus(snapshot,3,115.0);
        MapEstimateGuidedFocusPanel.ConfigurationListener listener=
                GuidedFocusHub.mapEstimateConfigurationListener();
        require(listener!=null,"production Guided Focus listener was not installed");

        listener.onEvidenceBasisRequested(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        listener.onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        require(session.mapEstimatePendingEvidenceBasisForTest()==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "production Focus listener ignored Current capture only request");
        require(session.mapEstimatePendingProposalLimitForTest()==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "production Focus listener ignored Unrestricted eligible MAP request");

        GuidedAeMethodModule mapEstimate=GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE);
        session.start(mapEstimate,snapshot,5,3,115.0);
        String active=session.resultText();
        require(active.contains("Evidence basis: Current capture only"),
                "active production metrics did not report current-capture-only authority: "+active);
        require(active.contains("proposal limit: Unrestricted eligible MAP"),
                "active production metrics did not report unrestricted proposal mode: "+active);
        require(!active.contains("high-TPS MAP cap"),
                "unrestricted production metrics still claimed a high-TPS cap");

        // Controls are locked by the real production listener while capture is active.
        listener.onEvidenceBasisRequested(MapEstimateEvidenceBasis.LEARNED_MEMORY);
        listener.onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
        require(session.mapEstimatePendingEvidenceBasisForTest()==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "active production capture allowed evidence-basis mutation");
        require(session.mapEstimatePendingProposalLimitForTest()==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "active production capture allowed proposal-limit mutation");

        session.finish();
        String completed=session.reportText("0.4.2-dev.test");
        require(completed.contains("Evidence basis: Current capture only"),
                "completed production export lost actual evidence basis");
        require(completed.contains("Proposal limit: Unrestricted eligible MAP"),
                "completed production export lost actual proposal limit");

        // After Finish the next capture may be configured, but the completed
        // report/review must remain historical authority for the run just made.
        listener.onEvidenceBasisRequested(MapEstimateEvidenceBasis.LEARNED_MEMORY);
        listener.onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
        require(session.mapEstimatePendingEvidenceBasisForTest()==MapEstimateEvidenceBasis.LEARNED_MEMORY,
                "completed production session did not unlock next evidence basis");
        require(session.mapEstimatePendingProposalLimitForTest()==MapEstimateProposalLimitPolicy.HIGH_TPS_CAP,
                "completed production session did not unlock next proposal limit");
        String historical=session.reportText("0.4.2-dev.test");
        require(historical.contains("Evidence basis: Current capture only")
                        && historical.contains("Proposal limit: Unrestricted eligible MAP"),
                "next-capture setup rewrote completed production export authority");
    }

    private static void continueStartsFreshCollectorWindowWithoutDeletingMemory() {
        GuidedMethodProbeSession session=new GuidedMethodProbeSession();
        AeProjectSnapshot snapshot=mapSnapshot();
        session.configureMapEstimateFocus(snapshot,3,115.0);
        MapEstimateGuidedFocusPanel.ConfigurationListener listener=GuidedFocusHub.mapEstimateConfigurationListener();
        listener.onEvidenceBasisRequested(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        GuidedAeMethodModule mapEstimate=GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_ESTIMATE);

        session.start(mapEstimate,snapshot,5,3,115.0);
        for(int i=0;i<5;i++)session.accept(stableMapSample(1.0+i*0.05,1500.0,10.0,70.0));
        require(session.mapEstimateCollectorAcceptedForTest()==5,
                "first experiment leg did not collect five stable samples");
        session.finish();

        session.start(mapEstimate,snapshot,5,3,115.0);
        require(session.mapEstimateCollectorAcceptedForTest()==0,
                "Continue/Start leaked prior leg's visible stable-cell collector counts");
        String fresh=session.resultText();
        require(fresh.contains("MAP Estimate collection: 0 stable sample(s)"),
                "new experiment leg did not present a clean collector window: "+fresh);
        for(int i=0;i<3;i++)session.accept(stableMapSample(2.0+i*0.05,1500.0,10.0,72.0));
        require(session.mapEstimateCollectorAcceptedForTest()==3,
                "second experiment leg collector did not count only its own samples");
    }

    private static LiveSample stableMapSample(double seconds,double rpm,double tps,double map) {
        EnumMap<ChannelRole,Double> values=new EnumMap<ChannelRole,Double>(ChannelRole.class);
        values.put(ChannelRole.RPM,rpm);
        values.put(ChannelRole.TPS,tps);
        values.put(ChannelRole.MAP,map);
        values.put(ChannelRole.MAP_PRED_ACTIVE,0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD,0.0);
        values.put(ChannelRole.AE_EXTRA_SHOT,0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW,0.0);
        values.put(ChannelRole.DFCO,0.0);
        values.put(ChannelRole.FUEL_CUT,0.0);
        values.put(ChannelRole.COOLANT,80.0);
        values.put(ChannelRole.IAT,25.0);
        return new LiveSample(Math.round(seconds*1000000000.0),seconds,values,0.0,0.0);
    }

    private static AeProjectSnapshot mapSnapshot() {
        return new AeProjectSnapshot(
                "experiment-routing-test",
                new double[]{1.0}, new double[]{20.0}, new double[][]{{0.0}},
                new double[]{1000.0}, new double[]{1.0},
                0.0, 0.0, new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0],
                new double[]{1500.0,3000.0},
                new double[]{10.0,40.0},
                new double[][]{{45.0,50.0},{70.0,115.0}},
                new double[]{1500.0,3000.0}, new double[]{0.10,0.20});
    }

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
