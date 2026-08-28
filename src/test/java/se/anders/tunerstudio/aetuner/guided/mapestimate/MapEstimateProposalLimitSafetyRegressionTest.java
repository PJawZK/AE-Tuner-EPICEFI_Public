package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Unrestricted proposal mode may remove the cap, never evidence authority. */
public final class MapEstimateProposalLimitSafetyRegressionTest {
    public static void main(String[] args) {
        unrestrictedDoesNotMakeConflictedEvidenceWritable();
        System.out.println("MapEstimateProposalLimitSafetyRegressionTest passed");
    }

    private static void unrestrictedDoesNotMakeConflictedEvidenceWritable() {
        double[] tps={0,40};
        double[] rpm={1000,2000};
        MapEstimateMemory memory=new MapEstimateMemory("cfg",tps,rpm);
        // Same exact operating coordinate, two incompatible MAP states. This
        // must remain Conflict/Recheck even though the requested proposal mode
        // is unrestricted and the cell is above the high-TPS cap boundary.
        for(int i=0;i<12;i++)memory.add(40.0,2000.0,82.0,80.0,25.0);
        for(int i=0;i<12;i++)memory.add(40.0,2000.0,142.0,80.0,25.0);
        MapEstimateSurface surface=new MapEstimateSurface(memory,20);
        MapEstimateSurface.Cell cell=surface.cell(1,1);
        require(cell.state==MapEstimateSurface.State.CONFLICT,
                "conflict fixture did not classify as Conflict: "+cell.state+" / "+cell.reason);
        require(cell.maturity==MapEstimateSurface.Maturity.RECHECK,
                "conflict fixture did not require Recheck: "+cell.maturity);

        double[][] current={{40.0,45.0},{50.0,60.0}};
        MapEstimateCellScope scope=MapEstimateCellScope.none(2,2).withCell(1,1,true);
        MapEstimateProposal unrestricted=MapEstimateProposal.build(
                "cfg",tps,rpm,current,surface,
                MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,scope,115.0,
                MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        require(unrestricted.changeCount()==0&&unrestricted.writePlan()==null,
                "unrestricted proposal mode bypassed Conflict/Recheck exclusion");
        require(!unrestricted.changed(1,1),
                "conflicted selected cell entered unrestricted proposal mask");
    }

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
