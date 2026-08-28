package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.nio.file.Files;
import java.nio.file.Path;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

public final class MapEstimateGuidedControllerRegressionTest {
    public static void main(String[] args)throws Exception{
        finishPersistsOnceAndContinueCreatesNewDelta();
        resetPreservesStoredMemory();
        fineTuneScopeAndReviewedWritePlanStayExact();
        completedFocusUsesPendingNextCaptureConfiguration();
        currentCaptureOnlyExcludesEarlierStoredEvidence();
        proposalLimitChangesOnlyHighTpsCap();
        targetZoneMatchesDirectEvidenceTolerance();
        System.out.println("MapEstimateGuidedControllerRegressionTest passed");
    }

    private static void finishPersistsOnceAndContinueCreatesNewDelta()throws Exception{
        double[] t={0,10,20},r={1000,2000,3000};double[][] table=table(3,3,40);
        Path dir=Files.createTempDirectory("ae-map-controller-");
        MapEstimateGuidedController c=new MapEstimateGuidedController(new MapEstimateMemoryStore(dir));
        c.configure("cfg",t,r,table,20,115);c.start();
        for(int i=0;i<20;i++)c.acceptStable(0,1000,50,80,25);
        require(c.currentRunSamples()==20,"current delta missing");c.finish();require(c.storedSamples()==20,"finish did not persist delta");
        c.start();for(int i=0;i<10;i++)c.acceptStable(10,2000,60,80,25);c.finish();require(c.storedSamples()==30,"continue did not append exactly once");
        MapEstimateGuidedController reload=new MapEstimateGuidedController(new MapEstimateMemoryStore(dir));reload.configure("cfg",t,r,table,20,115);require(reload.storedSamples()==30,"persistent memory did not reload");
    }

    private static void resetPreservesStoredMemory()throws Exception{
        double[] t={0,10},r={1000,2000};double[][] table=table(2,2,40);
        Path dir=Files.createTempDirectory("ae-map-controller-reset-");MapEstimateGuidedController c=new MapEstimateGuidedController(new MapEstimateMemoryStore(dir));
        c.configure("cfg",t,r,table,10,115);c.start();for(int i=0;i<10;i++)c.acceptStable(0,1000,50,80,25);c.finish();
        c.start();for(int i=0;i<7;i++)c.acceptStable(10,2000,60,80,25);c.resetCurrentCapture();require(c.storedSamples()==10,"reset touched stored memory");
    }

    private static void fineTuneScopeAndReviewedWritePlanStayExact()throws Exception{
        double[] t={0,10,20,30},r={1000,2000,3000,4000};double[][] table=table(4,4,40);
        MapEstimateGuidedController c=new MapEstimateGuidedController(null);c.configure("cfg",t,r,table,20,115);
        c.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        c.setPendingScope(MapEstimateCellScope.none(4,4).withCell(1,2,true));c.start();
        for(int i=0;i<25;i++){require(c.acceptStable(10,3000,75,80,25),"selected stable sample rejected");require(!c.acceptStable(20,3000,90,80,25),"out-of-scope stable sample accepted");}
        require(c.reviewedWritePlan()==null,"write plan exposed before Finish/Review");c.finish();
        ProposalWritePlan plan=c.reviewedWritePlan();require(plan!=null&&plan.changeCount()==1,"fine tune should expose exactly one reviewed change");require(plan.getChanges().get(0).flatIndex==6,"fine-tune flat index changed");
        require(c.reviewText().contains("Cells outside selected scope are preserved unchanged"),"review omitted hard scope protection");
        String manifest=c.reviewedVerificationManifestJson();
        require(manifest.contains("\"parameter\": \"mapEstimateTable\"")&&manifest.contains("\"index\": 6"),
                "reviewed MAP Estimate manifest did not expose the exact write target");
        require(c.reviewText().contains("MAP ESTIMATE WRITE VERIFICATION MANIFEST")&&c.reviewText().contains(manifest.trim()),
                "MAP Estimate report/review does not carry the physical-write verification allowlist");
    }

    private static void completedFocusUsesPendingNextCaptureConfiguration() throws Exception {
        double[] t={0,10,20,30},r={1000,2000,3000,4000};double[][] table=table(4,4,40);
        MapEstimateGuidedController c=new MapEstimateGuidedController(null);c.configure("cfg",t,r,table,20,115);
        c.start();for(int i=0;i<25;i++)c.acceptStable(0,1000,50,80,25);c.finish();

        MapEstimateCellScope nextScope=MapEstimateCellScope.none(4,4).withCell(2,3,true);
        c.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        c.setPendingScope(nextScope);
        c.setPendingEvidenceBasis(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        c.setPendingProposalLimitPolicy(MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        MapEstimateFocusModel setup=c.focus(20,4000,"next capture setup");
        require(setup.strategy==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "completed Focus snapped next-capture strategy back to completed-session strategy");
        require(setup.scope.size()==1&&setup.scope.contains(2,3),
                "completed Focus snapped next-capture scope back to completed-session scope");
        require(setup.evidenceBasis==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "completed Focus snapped next-capture evidence basis back to completed-session basis");
        require(setup.proposalLimitPolicy==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "completed Focus snapped next-capture proposal limit back to completed-session limit");
        require(!setup.captureActive,"completed Focus incorrectly reported active capture");
        String completedReview=c.reviewText();
        require(completedReview.contains("Strategy: Interpolated Coverage"),
                "changing next-capture setup rewrote completed-session strategy authority");
        require(completedReview.contains("Evidence basis: Learned memory + this capture"),
                "changing next-capture evidence basis rewrote completed-session review authority");
        require(completedReview.contains("Proposal limit: High-TPS cap"),
                "changing next-capture proposal limit rewrote completed-session review authority");

        c.start();
        MapEstimateFocusModel active=c.focus(20,4000,"capturing");
        require(active.strategy==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "pending next-capture strategy did not reach active evidence session");
        require(active.scope.size()==1&&active.scope.contains(2,3),
                "pending next-capture scope did not reach active evidence session");
        require(active.evidenceBasis==MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY,
                "pending evidence basis did not freeze into active capture");
        require(active.proposalLimitPolicy==MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP,
                "pending proposal limit did not freeze into active capture");
        for(int i=0;i<25;i++)require(c.acceptStable(20,4000,88,80,25),"selected next-run sample rejected");
        require(!c.acceptStable(10,4000,70,80,25),"next-run scope admitted an unselected cell");
        c.finish();
        ProposalWritePlan plan=c.reviewedWritePlan();
        require(plan!=null&&plan.changeCount()==1,"next-run Direct Fine Tune plan escaped exact scope");
        require(plan.getChanges().get(0).flatIndex==11,"next-run write plan targeted a cell outside selected scope");
        require(c.reviewText().contains("Evidence basis: Current capture only"),
                "completed review lost active current-capture-only authority");
        require(c.reviewText().contains("Proposal limit: Unrestricted eligible MAP"),
                "completed review lost active unrestricted proposal authority");
    }

    private static void currentCaptureOnlyExcludesEarlierStoredEvidence() throws Exception {
        double[] t={0,40},r={1000,2000};double[][] current={{40,45},{50,60}};
        Path dir=Files.createTempDirectory("ae-map-current-only-");
        MapEstimateGuidedController c=new MapEstimateGuidedController(new MapEstimateMemoryStore(dir));
        c.configure("cfg",t,r,current,20,115);
        MapEstimateCellScope exact=MapEstimateCellScope.none(2,2).withCell(1,1,true);
        c.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        c.setPendingScope(exact);
        c.setPendingProposalLimitPolicy(MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);

        c.start();
        for(int i=0;i<25;i++)require(c.acceptStable(40,2000,80.0,80,25),"first-session stable sample rejected");
        c.finish();
        require(c.storedSamples()==25,"first completed session was not retained");

        c.setPendingEvidenceBasis(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        c.start();
        for(int i=0;i<25;i++)require(c.acceptStable(40,2000,140.0,80,25),"isolated second-session stable sample rejected");
        MapEstimateFocusModel active=c.focus(40,2000,"isolated capture");
        require(active.evidenceSamplesUsed==25,"current-capture-only surface still counted earlier stored evidence");
        require(active.storedSamples==25,"current-capture-only mode unexpectedly deleted stored evidence before Finish");
        c.finish();

        require(c.storedSamples()==50,"current-capture-only Finish should archive the run without deleting history");
        ProposalWritePlan plan=c.reviewedWritePlan();
        require(plan!=null&&plan.changeCount()==1,"isolated current run did not produce its exact Direct proposal");
        require(Math.abs(plan.getChanges().get(0).proposedValue-140.0)<1e-9,
                "earlier 80 kPa memory contaminated the 140 kPa current-capture-only proposal: "+plan.getChanges().get(0).proposedValue);
        String review=c.reviewText();
        require(review.contains("Evidence basis: Current capture only"),"review omitted isolated evidence basis");
        require(review.contains("Evidence samples used by this surface: 25"),"review did not expose isolated evidence sample count");
        require(review.contains("Persistent stable samples retained: 50"),"review did not distinguish retained history from evidence authority");
    }

    private static void proposalLimitChangesOnlyHighTpsCap() throws Exception {
        double capped=proposalValueForLimit(MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
        double unrestricted=proposalValueForLimit(MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        require(Math.abs(capped-115.0)<1e-9,"high-TPS cap policy no longer clamps eligible 145.37 kPa evidence to 115 kPa: "+capped);
        require(Math.abs(unrestricted-145.37)<1e-9,"unrestricted policy did not preserve eligible high-TPS MAP at table resolution: "+unrestricted);
    }

    private static double proposalValueForLimit(MapEstimateProposalLimitPolicy policy) throws Exception {
        double[] t={0,40},r={1000,2000};double[][] current={{40,45},{50,60}};
        MapEstimateGuidedController c=new MapEstimateGuidedController(null);c.configure("cfg",t,r,current,20,115);
        c.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        c.setPendingScope(MapEstimateCellScope.none(2,2).withCell(1,1,true));
        c.setPendingEvidenceBasis(MapEstimateEvidenceBasis.CURRENT_CAPTURE_ONLY);
        c.setPendingProposalLimitPolicy(policy);
        c.start();
        for(int i=0;i<25;i++)require(c.acceptStable(40,2000,145.37,80,25),"high-TPS limit fixture rejected stable sample");
        require(!c.acceptStable(0,1000,90,80,25),"limit policy bypassed selected scope during capture");
        c.finish();
        ProposalWritePlan plan=c.reviewedWritePlan();
        require(plan!=null&&plan.changeCount()==1,"limit policy changed write eligibility/scope instead of only proposal value");
        require(plan.getChanges().get(0).flatIndex==3,"limit policy escaped the selected high-TPS cell");
        return plan.getChanges().get(0).proposedValue;
    }

    private static void targetZoneMatchesDirectEvidenceTolerance() throws Exception {
        double[] t={0,6.5,13.5,20,26.5,33.5};
        double[] r={1000,1300,1700,2000,2500,2900,3200,3600,3900};
        MapEstimateTargetZone zone=MapEstimateTargetZone.forCell(t,r,4,8);
        require(zone.available(),"target zone unavailable");
        require(Math.abs(zone.minTps-24.25)<0.0001 && Math.abs(zone.maxTps-28.75)<0.0001,
                "TPS target zone does not mirror tight direct-evidence tolerance");
        require(Math.abs(zone.minRpm-3795.0)<0.0001 && Math.abs(zone.maxRpm-4005.0)<0.0001,
                "RPM target zone does not mirror tight direct-evidence tolerance");
        require(zone.contains(26.0,3900.0),"target zone rejected a valid operating point");
        require(!zone.contains(30.0,3900.0),"target zone accepted an overly broad TPS point");
    }
    private static double[][] table(int rows,int cols,double base){double[][] v=new double[rows][cols];for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)v[r][c]=base+r*5+c;return v;}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
