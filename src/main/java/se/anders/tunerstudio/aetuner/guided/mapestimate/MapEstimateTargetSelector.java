package se.anders.tunerstudio.aetuner.guided.mapestimate;

/** Chooses a useful next direct measurement from demonstrated evidence clouds. */
public final class MapEstimateTargetSelector {
    public static final double AUTO_TARGET_TPS_LIMIT = 33.49;
    private static final long CURRENT_RUN_FRONTIER_MIN_SAMPLES = 20;

    public static final class Target {
        public final int row,col; public final String reason; public final double score;
        Target(int row,int col,String reason,double score){this.row=row;this.col=col;this.reason=reason;this.score=score;}
        public boolean available(){return row>=0&&col>=0;}
    }

    public Target choose(MapEstimateSurface surface, MapEstimateMemory memory,
                         MapEstimateCoverageStrategy strategy, MapEstimateCellScope scope,
                         double liveTps,double liveRpm){
        return choose(surface,memory,null,strategy,scope,liveTps,liveRpm);
    }

    public Target choose(MapEstimateSurface surface, MapEstimateMemory memory,
                         MapEstimateMemory currentRun,
                         MapEstimateCoverageStrategy strategy, MapEstimateCellScope scope,
                         double liveTps,double liveRpm){
        if(surface==null||memory==null||scope==null)return new Target(-1,-1,"no surface/scope",Double.NaN);
        double[] ta=memory.tpsAxis(), ra=memory.rpmAxis();
        boolean[][] lifetimeObserved=observedCloud(memory,ta,ra,liveTps,liveRpm);
        boolean useCurrentRun=strategy!=MapEstimateCoverageStrategy.DIRECT_FINE_TUNE
                && currentRun!=null && currentRun.sampleCount()>=CURRENT_RUN_FRONTIER_MIN_SAMPLES;
        boolean[][] currentObserved=useCurrentRun
                ?observedCloud(currentRun,ta,ra,Double.NaN,Double.NaN):null;

        Target bestOverall=new Target(-1,-1,"no eligible target",Double.NEGATIVE_INFINITY);
        Target bestCurrent=new Target(-1,-1,"no current-run reachable target",Double.NEGATIVE_INFINITY);
        for(int r=0;r<surface.rows();r++)for(int c=0;c<surface.cols();c++){
            if(!scope.contains(r,c))continue;
            MapEstimateSurface.Cell cell=surface.cell(r,c);
            if(strategy==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE){
                if(cell.state==MapEstimateSurface.State.DIRECT && !cell.needsRecheck())continue;
            }else{
                if(ta[r]>=33.5)continue;
                if(!insideLocalFrontier(lifetimeObserved,r,c))continue;
                if(cell.state==MapEstimateSurface.State.DIRECT && !cell.needsRecheck())continue;
            }
            double score=informationScore(surface,r,c);
            if(cell.state==MapEstimateSurface.State.CONFLICT||cell.maturity==MapEstimateSurface.Maturity.RECHECK)score+=2.4;
            if(cell.state==MapEstimateSurface.State.INTERPOLATED_WEAK)score+=1.2;
            if(cell.state==MapEstimateSurface.State.UNKNOWN)score+=1.6;
            if(Double.isFinite(liveTps)&&Double.isFinite(liveRpm)){
                double d=Math.abs(ta[r]-liveTps)/6.5+Math.abs(ra[c]-liveRpm)/400.0;
                score-=0.07*d;
            }
            score+=0.10*c+0.04*r;
            Target candidate=new Target(r,c,reason(cell,strategy,false),score);
            if(score>bestOverall.score)bestOverall=candidate;

            if(useCurrentRun&&insideLocalFrontier(currentObserved,r,c)){
                double currentScore=score+0.35;
                Target reachable=new Target(r,c,reason(cell,strategy,true),currentScore);
                if(currentScore>bestCurrent.score)bestCurrent=reachable;
            }
        }
        return useCurrentRun&&bestCurrent.available()?bestCurrent:bestOverall;
    }

    private static boolean[][] observedCloud(MapEstimateMemory memory,double[] ta,double[] ra,double liveTps,double liveRpm){
        boolean[][] observed=new boolean[ta.length][ra.length];boolean any=false;
        for(MapEstimateEvidenceBucket b:memory.buckets()){
            if(b.count()<=0)continue;int r=nearest(ta,b.meanTps()),c=nearest(ra,b.meanRpm());observed[r][c]=true;any=true;
        }
        if(!any&&Double.isFinite(liveTps)&&Double.isFinite(liveRpm)){
            int r=nearest(ta,liveTps),c=nearest(ra,liveRpm);observed[r][c]=true;
        }
        return observed;
    }
    private static boolean insideLocalFrontier(boolean[][] observed,int row,int col){
        if(observed==null)return false;
        for(int dr=-1;dr<=1;dr++)for(int dc=-1;dc<=1;dc++){
            int r=row+dr,c=col+dc;if(r>=0&&c>=0&&r<observed.length&&c<observed[r].length&&observed[r][c])return true;
        }
        return false;
    }
    private static double informationScore(MapEstimateSurface s,int row,int col){
        double unknown=0,direct=0;
        for(int dr=-2;dr<=2;dr++)for(int dc=-2;dc<=2;dc++){
            int r=row+dr,c=col+dc;if(r<0||c<0||r>=s.rows()||c>=s.cols())continue;
            MapEstimateSurface.Cell cell=s.cell(r,c);MapEstimateSurface.State st=cell.state;
            double w=1.0/(1.0+Math.abs(dr)+Math.abs(dc));
            if(st==MapEstimateSurface.State.UNKNOWN||st==MapEstimateSurface.State.INTERPOLATED_WEAK||cell.needsRecheck())unknown+=w;
            if(st==MapEstimateSurface.State.DIRECT&&!cell.needsRecheck())direct+=w;
        }
        return unknown+Math.min(1.5,direct/3.0);
    }
    private static String reason(MapEstimateSurface.Cell c,MapEstimateCoverageStrategy strategy,boolean currentRunReachable){
        String base;
        if(c.state==MapEstimateSurface.State.CONFLICT)base="evidence conflicts and needs verification";
        else if(c.maturity==MapEstimateSurface.Maturity.RECHECK)base="repeat-session evidence needs recheck before proposal authority";
        else if(strategy==MapEstimateCoverageStrategy.DIRECT_FINE_TUNE)base="selected Fine Tune cell still lacks trustworthy direct evidence";
        else if(c.state==MapEstimateSurface.State.INTERPOLATED_WEAK)base="weak interpolation in a high-information local coverage gap";
        else if(c.state==MapEstimateSurface.State.INTERPOLATED_STRONG)base="replace provisional interpolation with direct evidence inside the demonstrated operating cloud";
        else base="high-information uncovered region one step beyond the demonstrated operating cloud";
        return currentRunReachable?base+"; prioritized because this run has reached the adjacent operating region":base;
    }
    private static int nearest(double[] a,double v){int best=0;double d=Double.POSITIVE_INFINITY;for(int i=0;i<a.length;i++){double x=Math.abs(a[i]-v);if(x<d){d=x;best=i;}}return best;}
}
