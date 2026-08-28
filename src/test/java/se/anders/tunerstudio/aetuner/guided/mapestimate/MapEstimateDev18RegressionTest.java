package se.anders.tunerstudio.aetuner.guided.mapestimate;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/** Compact physical-data regressions extracted from Archive27. */
public final class MapEstimateDev18RegressionTest {
    private static final double[] TPS={0,6.5,13.5,20,26.5,33.5,40,46.5,53.5,60,66.5,73.5,80,86.5,93.5,100};
    private static final double[] RPM={600,1000,1300,1700,2000,2500,2900,3200,3600,3900,4300,4700,5000,5400,5800,6200};
    private static final String CFG="MEGA144H7EPIC-Volvo940Turbo";

    public static void main(String[] args) {
        archive27CoherentGradientDoesNotBecomeConflict();
        archive27BoostOnsetDisagreementRemainsConflict();
        currentRunReachabilityConstrainsAutomaticCoach();
        mapEstimateProposalDeclaresExactApplyCell();
        mapEstimateProposalUsesControllerResolution();
        System.out.println("MapEstimateDev18RegressionTest passed");
    }

    private static void archive27CoherentGradientDoesNotBecomeConflict() {
        MapEstimateMemory m=new MapEstimateMemory(CFG,TPS,RPM);
        putV1(m,18,24,4,72.5900000000,9623.00000000,236.700000000,14009.1686000,58.3200000000,60.2200000000);
        putV1(m,18,25,4,72.8500000000,10034.0000000,229.260000000,13142.0990000,56.4100000000,58.1100000000);
        putV1(m,18,26,4,73.1450000000,10384.0000000,223.180000000,12453.7150000,54.9200000000,56.5400000000);
        putV1(m,19,24,11,206.060000000,26077.0000000,704.220000000,45095.7794000,63.0700000000,66.8600000000);
        putV1(m,20,24,7,143.000000000,16628.0000000,504.440000000,36353.0666000,71.0200000000,72.6500000000);
        putV1(m,21,24,9,184.630000000,21533.0000000,650.570000000,47027.9231000,71.6300000000,72.8700000000);
        putV1(m,21,25,14,287.670000000,35102.0000000,988.700000000,69826.3742000,70.0100000000,71.3900000000);
        putV1(m,21,26,4,82.1650000000,10255.0000000,278.970000000,19456.4193000,69.4900000000,70.2400000000);
        putV1(m,22,24,15,330.665000000,35957.0000000,1201.60000000,96259.0838000,79.3900000000,80.8600000000);
        putV1(m,22,25,20,440.875000000,49968.0000000,1587.15000000,125956.977700,78.3500000000,80.3900000000);
        putV1(m,22,26,7,154.310000000,18031.0000000,545.740000000,42548.1286000,77.5600000000,78.4400000000);

        MapEstimateSurface.Cell cell=new MapEstimateSurface(m,20).cell(3,5); // 20% / 2500
        require(cell.stddevKpa>7.4,"Archive27 gradient fixture no longer reproduces the raw ~7.56 kPa pooled SD");
        require(cell.state==MapEstimateSurface.State.DIRECT,
                "coherent Archive27 20%/2500 gradient still becomes Conflict");
        require(cell.reason.contains("coherent local TPS/RPM gradient"),
                "Direct result does not explain why raw spread was accepted: "+cell.reason);
    }

    private static void archive27BoostOnsetDisagreementRemainsConflict() {
        MapEstimateMemory m=new MapEstimateMemory(CFG,TPS,RPM);
        putV1(m,28,20,3,83.2350000000,6100.00000000,286.510000000,27366.1701000,94.3300000000,96.9400000000);
        putV1(m,26,21,1,26.4900000000,2072.00000000,97.0100000000,9410.94010000,97.0100000000,97.0100000000);
        putV1(m,27,21,3,81.2650000000,6264.00000000,294.300000000,28873.1106000,97.0800000000,99.2100000000);
        putV1(m,26,19,20,528.385000000,38407.0000000,2266.27000000,256802.988500,112.370000000,114.020000000);
        putV1(m,26,20,14,369.805000000,27687.0000000,1587.67000000,180053.594700,112.430000000,114.160000000);
        putV1(m,28,21,2,56.4000000000,4194.00000000,216.460000000,23427.9460000,107.740000000,108.720000000);

        MapEstimateSurface.Cell cell=new MapEstimateSurface(m,20).cell(4,4); // 26.5% / 2000
        require(cell.stddevKpa>6.0,"Archive27 boost-onset fixture no longer reproduces the raw conflict");
        require(cell.state==MapEstimateSurface.State.CONFLICT
                        && cell.maturity==MapEstimateSurface.Maturity.RECHECK,
                "Archive27 26.5%/2000 disagreement was incorrectly explained away by local gradient fitting");
        require(cell.reason.contains("local-gradient residuals"),
                "boost-onset conflict lost residual-quality provenance: "+cell.reason);
    }

    private static void currentRunReachabilityConstrainsAutomaticCoach() {
        double[] t={0,10,20,30}, r={1000,2000,3000,4000};
        MapEstimateMemory lifetime=new MapEstimateMemory("cfg",t,r);
        repeat(lifetime,0,1000,40,30);
        repeat(lifetime,30,4000,80,30);
        MapEstimateMemory currentRun=new MapEstimateMemory("cfg",t,r);
        repeat(currentRun,0,1000,40,25);
        MapEstimateSurface surface=new MapEstimateSurface(lifetime,20);
        MapEstimateTargetSelector.Target target=new MapEstimateTargetSelector().choose(
                surface,lifetime,currentRun,MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,
                MapEstimateCellScope.all(4,4),Double.NaN,Double.NaN);
        require(target.available(),"current-run reachable frontier produced no target");
        require(target.row<=1&&target.col<=1,
                "automatic coach ignored the current run and jumped back to a remote lifetime-only region: "+target.row+","+target.col);
        require(target.reason.contains("this run has reached"),
                "current-run target did not expose reachability rationale");
    }

    private static void mapEstimateProposalDeclaresExactApplyCell() {
        double[] t={0,10}, r={1000,2000};
        MapEstimateMemory m=new MapEstimateMemory("cfg",t,r);
        repeat(m,10,2000,70,30);
        MapEstimateSurface surface=new MapEstimateSurface(m,20);
        double[][] current={{40,45},{50,55}};
        MapEstimateProposal proposal=MapEstimateProposal.build("cfg",t,r,current,surface,
                MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,MapEstimateCellScope.all(2,2),115);
        ProposalWritePlan plan=proposal.writePlan();
        require(plan!=null&&plan.changeCount()==1,"MAP Estimate did not produce exactly one guarded write target");
        ProposalWritePlan.Change change=plan.getChanges().get(0);
        require("map-estimate-table".equals(plan.getRecipeId()),"wrong MAP Estimate recipe id");
        require("mapEstimateTable".equals(change.parameterName)&&change.flatIndex==3,
                "MAP Estimate Apply plan did not declare the exact flattened table cell");
        require(Math.abs(change.expectedValue-55)<1e-9&&Math.abs(change.proposedValue-70)<0.2,
                "MAP Estimate Apply plan baseline/proposed value changed unexpectedly");
        require(plan.verificationManifestJson().contains("\"index\": 3"),
                "MAP Estimate write plan did not expose an MSQ verification manifest");
    }

    private static void mapEstimateProposalUsesControllerResolution() {
        double[] t={0,10}, r={1000,2000};
        MapEstimateMemory m=new MapEstimateMemory("cfg",t,r);
        repeat(m,10,2000,70.037,30);
        MapEstimateSurface surface=new MapEstimateSurface(m,20);
        double[][] current={{40,45},{50,55}};
        MapEstimateProposal proposal=MapEstimateProposal.build("cfg",t,r,current,surface,
                MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,MapEstimateCellScope.all(2,2),115);
        ProposalWritePlan plan=proposal.writePlan();
        require(plan!=null&&plan.changeCount()==1,"resolution fixture did not produce one MAP Estimate change");
        ProposalWritePlan.Change change=plan.getChanges().get(0);
        require(Math.abs(change.proposedValue-70.04)<1e-9,
                "MAP Estimate write plan contains a value the 0.01 kPa ECU table cannot represent: "+change.proposedValue);
        require(proposal.copyPasteBlock().contains("70.04"),
                "paste-ready MAP Estimate table disagrees with the quantized write plan");
        require(plan.verificationManifestJson().contains("70.04"),
                "verification manifest does not carry the representable MAP Estimate value");
    }

    private static void putV1(MapEstimateMemory m,int tk,int rk,long count,
                              double sumTps,double sumRpm,double sumMap,double sumMapSq,
                              double minMap,double maxMap) {
        MapEstimateEvidenceBucket b=MapEstimateEvidenceBucket.restoredV1(
                tk,rk,count,sumTps,sumRpm,sumMap,sumMapSq,minMap,maxMap,
                0.0,0,0.0,0);
        m.putRestored(b);
    }

    private static void repeat(MapEstimateMemory m,double tps,double rpm,double map,int n) {
        for(int i=0;i<n;i++)m.add(tps+(i%3-1)*0.02,rpm+(i%5-2),map+(i%3-1)*0.05,80,25);
    }
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
