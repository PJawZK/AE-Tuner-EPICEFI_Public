package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.util.EnumMap;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.passive.MapEstimateCollector;

public final class MapEstimateGuidedProbeRouteRegressionTest {
    public static void main(String[] args) throws Exception {
        dev15CollectorDecisionRemainsAuthoritative();
        directFineTuneScopeFiltersAfterCollectorAcceptance();
        resetPreservesPersistentLearnedState();
        setupChangesDoNotReloadOrLoseMemory();
        focusListenerCannotChangeFrozenCaptureScope();
        System.out.println("MapEstimateGuidedProbeRouteRegressionTest passed");
    }

    private static void dev15CollectorDecisionRemainsAuthoritative() throws Exception {
        MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        MapEstimateGuidedProbeRoute route=new MapEstimateGuidedProbeRoute(new MapEstimateCollector(),controller);
        route.prepare(snapshot(),3,115);route.start();
        require(!route.accept(sample(1,10,2000,60,3.0),true),"TPS-moving sample escaped dev15 gate");
        require(controller.currentRunSamples()==0,"rejected dev15 sample entered learned memory");
        require(!route.accept(sample(2,10,2000,60,0.0),false),"required-incomplete sample accepted");
        require(controller.currentRunSamples()==0,"required-incomplete sample entered learned memory");
        require(route.accept(sample(3,10,2000,60,0.0),true),"stable dev15-accepted sample was not forwarded");
        require(controller.currentRunSamples()==1,"stable accepted sample missing from learned memory");
    }

    private static void directFineTuneScopeFiltersAfterCollectorAcceptance() throws Exception {
        MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        MapEstimateGuidedProbeRoute route=new MapEstimateGuidedProbeRoute(new MapEstimateCollector(),controller);
        route.prepare(snapshot(),3,115);
        route.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        route.setPendingScope(MapEstimateCellScope.none(3,3).withCell(1,1,true));
        route.start();
        require(!route.accept(sample(1,20,2000,80,0),true),"out-of-scope stable sample entered Fine Tune memory");
        require(route.currentRunStableSamples()==1,"dev15 collector should still report the stable observation");
        require(controller.currentRunSamples()==0,"Fine Tune scope failed to protect learned delta");
        require(route.accept(sample(2,10,2000,60,0),true),"selected Fine Tune sample rejected");
        require(controller.currentRunSamples()==1,"selected Fine Tune sample missing");
    }

    private static void resetPreservesPersistentLearnedState() throws Exception {
        MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        MapEstimateGuidedProbeRoute route=new MapEstimateGuidedProbeRoute(new MapEstimateCollector(),controller);
        route.prepare(snapshot(),3,115);route.start();
        for(int i=0;i<3;i++)route.accept(sample(i+1,10,2000,60,0),true);
        route.finish();long stored=route.storedStableSamples();require(stored==3,"setup learned state not committed");
        route.start();route.accept(sample(10,20,3000,80,0),true);route.resetCurrentCapture();
        require(route.storedStableSamples()==stored,"Reset Session changed persistent learned state");
    }

    private static void setupChangesDoNotReloadOrLoseMemory() throws Exception {
        MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        MapEstimateGuidedProbeRoute route=new MapEstimateGuidedProbeRoute(new MapEstimateCollector(),controller);
        route.prepare(snapshot(),3,115);route.start();
        for(int i=0;i<3;i++)route.accept(sample(i+1,10,2000,60,0),true);
        route.finish();long before=route.storedStableSamples();
        route.updateReviewSettings(10,110);
        require(route.storedStableSamples()==before,"changing samples/cap reloaded or lost learned memory");
    }

    private static void focusListenerCannotChangeFrozenCaptureScope() throws Exception {
        MapEstimateGuidedController controller=new MapEstimateGuidedController(null);
        MapEstimateGuidedProbeRoute route=new MapEstimateGuidedProbeRoute(new MapEstimateCollector(),controller);
        route.prepare(snapshot(),3,115);
        MapEstimateCellScope original=MapEstimateCellScope.none(3,3).withCell(1,1,true);
        route.setPendingStrategy(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE);
        route.setPendingScope(original);route.start();
        route.onStrategyRequested(MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE);
        route.onScopeRequested(MapEstimateCellScope.all(3,3));
        require(route.pendingStrategy()==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,
                "active Focus listener changed frozen strategy");
        require(route.pendingScope().size()==1 && route.pendingScope().contains(1,1),
                "active Focus listener changed frozen scope");
    }

    private static AeProjectSnapshot snapshot(){
        double[] t={0,10,20},r={1000,2000,3000};
        return new AeProjectSnapshot(
                "cfg",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, true, false, false,
                new double[0][0], new double[0][0],
                r, t, new double[][]{{40,45,50},{50,55,60},{60,65,70}},
                new double[0], new double[0]);
    }

    private static LiveSample sample(double seconds,double tps,double rpm,double map,double tpsDot){
        EnumMap<ChannelRole,Double> v=new EnumMap<ChannelRole,Double>(ChannelRole.class);
        v.put(ChannelRole.TPS,tps);v.put(ChannelRole.RPM,rpm);v.put(ChannelRole.MAP,map);v.put(ChannelRole.COOLANT,80.0);v.put(ChannelRole.IAT,25.0);
        return new LiveSample((long)(seconds*1e9),seconds,v,tpsDot,0.0);
    }
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
