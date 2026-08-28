package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.io.IOException;

/** Owns stored memory plus one uncommitted capture delta. */
public final class MapEstimateEvidenceSession {
    public enum State { IDLE, CAPTURING, PAUSED, COMPLETE }
    private final MapEstimateMemoryStore store;
    private MapEstimateMemory stored;
    private MapEstimateMemory delta;
    private MapEstimateCoverageStrategy strategy=MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE;
    private MapEstimateCellScope scope;
    private State state=State.IDLE;
    private boolean committed;
    private String loadStatus="Memory persistence disabled for this test/session.";

    public MapEstimateEvidenceSession(MapEstimateMemoryStore store,String configuration,double[] tps,double[] rpm) throws IOException {
        this.store=store;
        MapEstimateMemory empty=new MapEstimateMemory(configuration,tps,rpm);
        if(store==null){stored=empty;}else{MapEstimateMemoryStore.LoadResult loaded=store.loadBest(empty);stored=loaded.memory;loadStatus=loaded.status;}
        scope=MapEstimateCellScope.all(tps.length,rpm.length);
    }

    public void start(MapEstimateCoverageStrategy strategy,MapEstimateCellScope scope){
        if(state==State.CAPTURING||state==State.PAUSED)throw new IllegalStateException("capture already active");
        this.strategy=strategy==null?MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE:strategy;
        this.scope=scope==null?MapEstimateCellScope.all(stored.tpsAxis().length,stored.rpmAxis().length):scope;
        delta=new MapEstimateMemory(stored.configuration(),stored.tpsAxis(),stored.rpmAxis());committed=false;state=State.CAPTURING;
    }

    /** Called only after the existing dev15 stability/transient gates accept the sample. */
    public boolean acceptStable(double tps,double rpm,double map,double clt,double mat){
        if(state!=State.CAPTURING)return false;
        int row=nearest(stored.tpsAxis(),tps),col=nearest(stored.rpmAxis(),rpm);
        if(!scope.contains(row,col))return false;
        delta.add(tps,rpm,map,clt,mat);return true;
    }

    public void togglePause(){if(state==State.CAPTURING)state=State.PAUSED;else if(state==State.PAUSED)state=State.CAPTURING;}
    public void finish() throws IOException {
        if(state!=State.CAPTURING&&state!=State.PAUSED)return;
        IOException saveFailure=null;
        if(!committed&&delta!=null&&delta.sampleCount()>0){
            MapEstimateMemory merged=copyOf(stored);merged.mergeCompletedSession(delta);
            try{if(store!=null)store.save(merged);}catch(IOException ex){saveFailure=ex;}
            stored=merged;committed=true;
        }
        state=State.COMPLETE;
        if(saveFailure!=null)throw saveFailure;
    }
    public void reset(){delta=null;committed=false;state=State.IDLE;}
    public MapEstimateMemory combined(){MapEstimateMemory m=new MapEstimateMemory(stored.configuration(),stored.tpsAxis(),stored.rpmAxis());m.merge(stored);if(delta!=null&&!committed)m.merge(delta);return m;}
    public MapEstimateMemory stored(){return copyOf(stored);}
    public MapEstimateMemory currentRunMemory(){return delta==null?new MapEstimateMemory(stored.configuration(),stored.tpsAxis(),stored.rpmAxis()):copyOf(delta);}
    public long currentRunSamples(){return delta==null?0:delta.sampleCount();}
    public MapEstimateCoverageStrategy strategy(){return strategy;} public MapEstimateCellScope scope(){return scope;} public State state(){return state;}
    public String loadStatus(){return loadStatus;}
    private static MapEstimateMemory copyOf(MapEstimateMemory source){
        MapEstimateMemory copy=new MapEstimateMemory(source.configuration(),source.tpsAxis(),source.rpmAxis());
        copy.merge(source);
        return copy;
    }
    private static int nearest(double[] a,double v){int b=0;double d=Double.POSITIVE_INFINITY;for(int i=0;i<a.length;i++){double x=Math.abs(a[i]-v);if(x<d){d=x;b=i;}}return b;}
}
