package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCellScope;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCoverageStrategy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateMemory;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposal;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposalLimitPolicy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateSurface;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.Arrays;

/**
 * Reproduces the physical MAP Estimate storage boundary: the controller stores
 * mapEstimateTable at 0.01 kPa resolution while ProposalApplyCoordinator keeps
 * strict 1e-6 readback verification.
 */
public final class MapEstimateApplyResolutionRegressionTest {
    public static void main(String[] args) {
        unquantizedMapWriteFailsStrictReadbackAndRollsBack();
        realMapEstimateProposalAppliesAndRestoresAtControllerResolution();
        System.out.println("MapEstimateApplyResolutionRegressionTest passed");
    }

    private static void unquantizedMapWriteFailsStrictReadbackAndRollsBack() {
        QuantizingMapBackend backend = new QuantizingMapBackend(
                new double[][]{{40.00,45.00},{50.00,60.00}});
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalWritePlan unsafePrecision = new ProposalWritePlan(
                "map-estimate-table", "MAP Estimate Table", "cfg", "precision regression",
                Arrays.asList(ProposalWritePlan.Change.arrayCell(
                        "mapEstimateTable", 3, 60.00, 145.377,
                        "40.0% TPS / 2000 RPM", "kPa")));

        ProposalApplyCoordinator.ApplyResult result = coordinator.apply(unsafePrecision);
        require(!result.success,
                "unrepresentable MAP proposal unexpectedly passed strict readback");
        require(result.message.contains("read-back verification failed"),
                "unrepresentable MAP proposal failed for the wrong reason: "+result.message);
        require(result.message.contains("rollback PASS"),
                "unrepresentable MAP proposal did not verify rollback: "+result.message);
        requireClose(60.00, backend.value(1,1),
                "failed unrepresentable MAP Apply did not restore exact baseline");
        require(coordinator.applyDepth()==0,
                "readback-failed MAP Apply entered restore history");
    }

    private static void realMapEstimateProposalAppliesAndRestoresAtControllerResolution() {
        double[] tps={0,40};
        double[] rpm={1000,2000};
        double[][] current={{40.00,45.00},{50.00,60.00}};
        MapEstimateMemory memory=new MapEstimateMemory("cfg",tps,rpm);
        for(int i=0;i<25;i++) memory.add(40.0,2000.0,145.377,80.0,25.0);
        MapEstimateSurface surface=new MapEstimateSurface(memory,20);
        MapEstimateProposal proposal=MapEstimateProposal.build(
                "cfg",tps,rpm,current,surface,
                MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                MapEstimateCellScope.none(2,2).withCell(1,1,true),
                115.0,MapEstimateProposalLimitPolicy.UNRESTRICTED_ELIGIBLE_MAP);
        ProposalWritePlan plan=proposal.writePlan();
        require(plan!=null&&plan.changeCount()==1,
                "real MAP Estimate fixture did not produce one guarded write");
        requireClose(145.38,plan.getChanges().get(0).proposedValue,
                "real MAP Estimate proposal was not quantized to 0.01 kPa");

        QuantizingMapBackend backend = new QuantizingMapBackend(current);
        ProposalApplyCoordinator coordinator = new ProposalApplyCoordinator(backend);
        ProposalApplyCoordinator.ApplyResult applied=coordinator.apply(plan);
        require(applied.success,
                "representable real MAP Estimate proposal failed strict Apply/readback: "+applied.message);
        requireClose(145.38,backend.value(1,1),
                "controller-resolution backend did not retain proposed MAP value");
        requireClose(40.00,backend.value(0,0),"undeclared MAP cell [0,0] changed");
        requireClose(45.00,backend.value(0,1),"undeclared MAP cell [0,1] changed");
        requireClose(50.00,backend.value(1,0),"undeclared MAP cell [1,0] changed");
        require(coordinator.applyDepth()==1,
                "successful MAP Estimate Apply did not create one restore record");

        ProposalApplyCoordinator.ApplyResult restored=coordinator.restorePreviousApply();
        require(restored.success&&restored.restore,
                "representable MAP Estimate proposal did not Restore/readback cleanly: "+restored.message);
        requireClose(60.00,backend.value(1,1),
                "MAP Estimate Restore did not recover exact baseline");
        require(coordinator.applyDepth()==0,
                "successful MAP Estimate Restore did not clear restore record");
    }

    private static final class QuantizingMapBackend implements ProposalApplyCoordinator.Backend {
        private double[][] map;
        QuantizingMapBackend(double[][] initial) { map=cloneTable(initial); }

        @Override public double readScalar(String configurationName,String parameterName) {
            throw new IllegalStateException("scalar access not expected");
        }
        @Override public double[][] readArray(String configurationName,String parameterName) {
            if(!"cfg".equals(configurationName)||!"mapEstimateTable".equals(parameterName))
                throw new IllegalStateException("unexpected MAP parameter read");
            return cloneTable(map);
        }
        @Override public void writeScalar(String configurationName,String parameterName,double value) {
            throw new IllegalStateException("scalar access not expected");
        }
        @Override public void writeArray(String configurationName,String parameterName,double[][] values) {
            if(!"cfg".equals(configurationName)||!"mapEstimateTable".equals(parameterName))
                throw new IllegalStateException("unexpected MAP parameter write");
            map=cloneTable(values);
            for(int r=0;r<map.length;r++) for(int c=0;c<map[r].length;c++)
                map[r][c]=Math.round(map[r][c]*100.0)/100.0;
        }
        double value(int row,int col){return map[row][col];}
    }

    private static double[][] cloneTable(double[][] values) {
        double[][] copy=new double[values.length][];
        for(int i=0;i<values.length;i++) copy[i]=values[i].clone();
        return copy;
    }
    private static void requireClose(double expected,double actual,String message) {
        if(Math.abs(expected-actual)>0.000001)
            throw new AssertionError(message+": expected "+expected+" but was "+actual);
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
