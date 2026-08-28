package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Converts compact continuous-coordinate evidence into table-cell confidence.
 * dev18 keeps Direct authority tied to evidence that geometrically reaches the
 * exact table coordinate, separates completed-session repeatability from
 * within-session sample noise, and de-trends coherent local MAP gradients
 * before deciding that a tight Direct neighborhood is internally inconsistent.
 */
public final class MapEstimateSurface {
    public enum State { UNKNOWN, DIRECT, INTERPOLATED_STRONG, INTERPOLATED_WEAK, CONFLICT }
    public enum Maturity { PROVISIONAL, CONFIRMED, RECHECK }

    public static final class Cell {
        public final int row, col;
        public final double tps, rpm;
        public final State state;
        public final Maturity maturity;
        public final double valueKpa;
        public final long evidenceSamples;
        public final long repeatSessionEvidenceSamples;
        public final int sessionCount;
        public final double stddevKpa;
        public final double rangeKpa;
        public final double betweenSessionRangeKpa;
        public final double confidence;
        public final String reason;

        Cell(int row,int col,double tps,double rpm,State state,Maturity maturity,double valueKpa,long evidenceSamples,
             long repeatSessionEvidenceSamples,int sessionCount,double stddevKpa,double rangeKpa,double betweenSessionRangeKpa,
             double confidence,String reason){
            this.row=row;this.col=col;this.tps=tps;this.rpm=rpm;this.state=state;this.maturity=maturity;
            this.valueKpa=valueKpa;this.evidenceSamples=evidenceSamples;this.repeatSessionEvidenceSamples=repeatSessionEvidenceSamples;
            this.sessionCount=sessionCount;this.stddevKpa=stddevKpa;this.rangeKpa=rangeKpa;this.betweenSessionRangeKpa=betweenSessionRangeKpa;
            this.confidence=confidence;this.reason=reason==null?"":reason;
        }
        public boolean proposalEligible(){
            return (state==State.DIRECT || state==State.INTERPOLATED_STRONG) && maturity!=Maturity.RECHECK;
        }
        public boolean needsRecheck(){ return maturity==Maturity.RECHECK || state==State.CONFLICT; }
    }

    private static final double MAX_DIRECT_SD = 5.0;
    private static final double MAX_DIRECT_BUCKET_RESIDUAL = 6.0;
    private static final double CONFIRMED_SESSION_RANGE = 4.0;
    private static final double CONFLICT_SESSION_RANGE = 6.0;
    private static final double STRONG_TRIANGLE_DISTANCE = 4.25;
    private static final double WEAK_TRIANGLE_DISTANCE = 6.5;

    private final double[] tpsAxis, rpmAxis;
    private final Cell[][] cells;
    private final List<Cell> directAnchors;

    public MapEstimateSurface(MapEstimateMemory memory, int minimumSamples) {
        this.tpsAxis=memory.tpsAxis(); this.rpmAxis=memory.rpmAxis();
        this.cells=new Cell[tpsAxis.length][rpmAxis.length];
        int min=Math.max(3,minimumSamples);
        List<Cell> direct=new ArrayList<Cell>();
        for(int r=0;r<tpsAxis.length;r++) for(int c=0;c<rpmAxis.length;c++){
            Cell d=directCell(memory,r,c,min); cells[r][c]=d;
            if(d.state==State.DIRECT && d.maturity!=Maturity.RECHECK) direct.add(d);
        }
        this.directAnchors=Collections.unmodifiableList(direct);
        for(int r=0;r<tpsAxis.length;r++) for(int c=0;c<rpmAxis.length;c++){
            if(cells[r][c].state==State.UNKNOWN){
                Cell directContext=cells[r][c];
                Cell interpolated=interpolateCell(r,c,direct);
                cells[r][c]=(interpolated.state==State.UNKNOWN && directContext.evidenceSamples>0)
                        ? directContext : interpolated;
            }
        }
    }

    public Cell cell(int row,int col){ return cells[row][col]; }
    public int rows(){return cells.length;} public int cols(){return cells.length==0?0:cells[0].length;}
    public List<Cell> directAnchors(){return directAnchors;}
    public int count(State state){int n=0;for(Cell[] row:cells)for(Cell c:row)if(c.state==state)n++;return n;}
    public int count(Maturity maturity){int n=0;for(Cell[] row:cells)for(Cell c:row)if(c.state!=State.UNKNOWN&&c.maturity==maturity)n++;return n;}

    private Cell directCell(MapEstimateMemory memory,int row,int col,int minimumSamples){
        double targetTps=tpsAxis[row], targetRpm=rpmAxis[col];
        double tpsTolerance=MapEstimateTargetZone.directTolerance(tpsAxis,row,2.25);
        double rpmTolerance=MapEstimateTargetZone.directTolerance(rpmAxis,col,150.0);
        long n=0; double sum=0,sumSq=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        double coordWeight=0, coordError=0;
        double minBucketTps=Double.POSITIVE_INFINITY,maxBucketTps=Double.NEGATIVE_INFINITY;
        double minBucketRpm=Double.POSITIVE_INFINITY,maxBucketRpm=Double.NEGATIVE_INFINITY;
        double maxBetweenRange=Double.NaN;
        long repeatedNeighborhoodEvidence=0;
        int maxSessionCount=0;
        boolean repeatedNodeTouched=false;
        List<MapEstimateEvidenceBucket> neighborhood=new ArrayList<MapEstimateEvidenceBucket>();
        for(MapEstimateEvidenceBucket b:memory.buckets()){
            double bt=b.meanTps(), br=b.meanRpm();
            if(Math.abs(bt-targetTps)>tpsTolerance || Math.abs(br-targetRpm)>rpmTolerance) continue;
            long bn=b.count(); if(bn<=0)continue;
            neighborhood.add(b);
            double bm=b.meanMap(), bsd=b.mapStdDev();
            n+=bn; sum+=bm*bn;
            double within=Double.isFinite(bsd)?bsd*bsd:0.0;
            sumSq+=(within+bm*bm)*bn;
            min=Math.min(min,b.minMap()); max=Math.max(max,b.maxMap());
            minBucketTps=Math.min(minBucketTps,bt);maxBucketTps=Math.max(maxBucketTps,bt);
            minBucketRpm=Math.min(minBucketRpm,br);maxBucketRpm=Math.max(maxBucketRpm,br);
            double nd=normDistance(targetTps,targetRpm,bt,br,row,col);
            coordError+=nd*bn;coordWeight+=bn;

            if(b.sessionCount()>=2){
                double bucketTps=b.tpsKey*MapEstimateMemory.TPS_BUCKET;
                double bucketRpm=b.rpmKey*MapEstimateMemory.RPM_BUCKET;
                boolean bucketTouchesNode = targetTps >= bucketTps-MapEstimateMemory.TPS_BUCKET*0.5
                        && targetTps <= bucketTps+MapEstimateMemory.TPS_BUCKET*0.5
                        && targetRpm >= bucketRpm-MapEstimateMemory.RPM_BUCKET*0.5
                        && targetRpm <= bucketRpm+MapEstimateMemory.RPM_BUCKET*0.5;
                repeatedNeighborhoodEvidence+=bn;
                maxSessionCount=Math.max(maxSessionCount,(int)Math.min(Integer.MAX_VALUE,b.sessionCount()));
                if(bucketTouchesNode){
                    repeatedNodeTouched=true;
                    double brange=b.betweenSessionRange();
                    if(Double.isFinite(brange)) maxBetweenRange=Double.isFinite(maxBetweenRange)?Math.max(maxBetweenRange,brange):brange;
                }
            }
        }
        if(n<minimumSamples) return unknown(row,col,n,"insufficient tight direct evidence");
        double mean=sum/n;
        double sd=Math.sqrt(Math.max(0.0,sumSq/n-mean*mean));
        double range=max-min;
        double avgCoord=coordWeight>0?coordError/coordWeight:99;

        boolean reachesTps = targetTps >= minBucketTps-MapEstimateMemory.TPS_BUCKET*0.5
                && targetTps <= maxBucketTps+MapEstimateMemory.TPS_BUCKET*0.5;
        boolean reachesRpm = targetRpm >= minBucketRpm-MapEstimateMemory.RPM_BUCKET*0.5
                && targetRpm <= maxBucketRpm+MapEstimateMemory.RPM_BUCKET*0.5;
        if(!reachesTps || !reachesRpm){
            return unknown(row,col,n,"nearby stable evidence does not yet reach the exact table coordinate");
        }

        boolean repeatSupportsNode = repeatedNodeTouched && repeatedNeighborhoodEvidence>=minimumSamples;
        long repeatedSessionEvidence = repeatSupportsNode ? repeatedNeighborhoodEvidence : 0;
        int nodeSessionCount = repeatSupportsNode ? maxSessionCount : 1;
        double nodeBetweenRange = repeatSupportsNode ? maxBetweenRange : Double.NaN;

        MapEstimateLocalQuality.Result localQuality=MapEstimateLocalQuality.evaluate(
                neighborhood,targetTps,targetRpm,minimumSamples,sd);
        boolean residualSpreadConflict=Double.isFinite(localQuality.residualStdDevKpa)
                && localQuality.residualStdDevKpa>MAX_DIRECT_SD;
        boolean supportedBucketConflict=Double.isFinite(localQuality.maxSupportedBucketResidualKpa)
                && localQuality.maxSupportedBucketResidualKpa>MAX_DIRECT_BUCKET_RESIDUAL;
        if(residualSpreadConflict || supportedBucketConflict){
            String why=localQuality.gradientUsed
                    ? "local-gradient residuals remain inconsistent inside the tight direct neighborhood"
                    : "tight direct evidence has excessive within-region spread";
            return new Cell(row,col,targetTps,targetRpm,State.CONFLICT,Maturity.RECHECK,mean,n,repeatedSessionEvidence,nodeSessionCount,
                    sd,range,nodeBetweenRange,0.15,why);
        }
        if(Double.isFinite(nodeBetweenRange) && nodeBetweenRange>CONFLICT_SESSION_RANGE){
            return new Cell(row,col,targetTps,targetRpm,State.CONFLICT,Maturity.RECHECK,mean,n,repeatedSessionEvidence,nodeSessionCount,
                    sd,range,nodeBetweenRange,0.15,"independent completed sessions disagree by more than 6 kPa at the exact node");
        }
        Maturity maturity;
        if(Double.isFinite(nodeBetweenRange) && nodeBetweenRange>CONFIRMED_SESSION_RANGE){
            maturity=Maturity.RECHECK;
        }else if(repeatSupportsNode){
            maturity=Maturity.CONFIRMED;
        }else{
            maturity=Maturity.PROVISIONAL;
        }
        double confidence=Math.max(0.35,Math.min(1.0,0.55+0.25*Math.min(1.0,n/(double)(minimumSamples*3))+0.20*Math.max(0,1-avgCoord)));
        if(maturity==Maturity.CONFIRMED)confidence=Math.min(1.0,confidence+0.10);
        if(maturity==Maturity.RECHECK)confidence=Math.min(confidence,0.45);
        String gradientNote=sd>MAX_DIRECT_SD && localQuality.gradientUsed
                ? "; coherent local TPS/RPM gradient explains the raw neighborhood spread" : "";
        String reason=maturity==Maturity.CONFIRMED?"direct evidence confirmed across completed sessions with exact-node repeat support"+gradientNote
                : maturity==Maturity.RECHECK?"direct evidence needs exact-node repeat-session verification"+gradientNote
                : "direct evidence has table-node geometry but not enough repeat-session support at the exact node"+gradientNote;
        return new Cell(row,col,targetTps,targetRpm,State.DIRECT,maturity,mean,n,repeatedSessionEvidence,nodeSessionCount,
                sd,range,nodeBetweenRange,confidence,reason);
    }

    private Cell interpolateCell(int row,int col,List<Cell> anchors){
        double x=tpsAxis[row], y=rpmAxis[col];
        if(anchors.size()<3) return unknown(row,col,0,"fewer than three trustworthy direct anchors");
        List<Cell> nearest=new ArrayList<Cell>(anchors);
        Collections.sort(nearest,new Comparator<Cell>(){public int compare(Cell a,Cell b){return Double.compare(normDistance(x,y,a.tps,a.rpm,row,col),normDistance(x,y,b.tps,b.rpm,row,col));}});
        if(nearest.size()>9) nearest=new ArrayList<Cell>(nearest.subList(0,9));
        Triangle best=null;
        for(int i=0;i<nearest.size()-2;i++) for(int j=i+1;j<nearest.size()-1;j++) for(int k=j+1;k<nearest.size();k++){
            Triangle t=Triangle.of(nearest.get(i),nearest.get(j),nearest.get(k),x,y,row,col,this);
            if(t!=null && (best==null || t.score<best.score)) best=t;
        }
        if(best==null) return unknown(row,col,0,"outside direct-anchor triangles; extrapolation withheld");
        double value=best.a.valueKpa*best.wa+best.b.valueKpa*best.wb+best.c.valueKpa*best.wc;
        double maxDist=Math.max(normDistance(x,y,best.a.tps,best.a.rpm,row,col),Math.max(normDistance(x,y,best.b.tps,best.b.rpm,row,col),normDistance(x,y,best.c.tps,best.c.rpm,row,col)));
        double spread=Math.max(best.a.valueKpa,Math.max(best.b.valueKpa,best.c.valueKpa))-Math.min(best.a.valueKpa,Math.min(best.b.valueKpa,best.c.valueKpa));
        State state=maxDist<=STRONG_TRIANGLE_DISTANCE ? State.INTERPOLATED_STRONG : maxDist<=WEAK_TRIANGLE_DISTANCE ? State.INTERPOLATED_WEAK : State.UNKNOWN;
        if(state==State.UNKNOWN) return unknown(row,col,0,"anchor triangle too distant");
        Maturity maturity=best.a.maturity==Maturity.CONFIRMED&&best.b.maturity==Maturity.CONFIRMED&&best.c.maturity==Maturity.CONFIRMED
                ?Maturity.CONFIRMED:Maturity.PROVISIONAL;
        double conf=Math.max(0.2,Math.min(0.90,(state==State.INTERPOLATED_STRONG?0.82:0.52)-0.07*maxDist));
        if(maturity==Maturity.CONFIRMED)conf=Math.min(0.95,conf+0.08);
        int sessions=Math.min(best.a.sessionCount,Math.min(best.b.sessionCount,best.c.sessionCount));
        long repeated=Math.min(best.a.repeatSessionEvidenceSamples,Math.min(best.b.repeatSessionEvidenceSamples,best.c.repeatSessionEvidenceSamples));
        double between=maxFinite(best.a.betweenSessionRangeKpa,best.b.betweenSessionRangeKpa,best.c.betweenSessionRangeKpa);
        return new Cell(row,col,x,y,state,maturity,value,best.a.evidenceSamples+best.b.evidenceSamples+best.c.evidenceSamples,
                repeated,sessions,Double.NaN,spread,between,conf,
                maturity==Maturity.CONFIRMED?"bounded triangle interpolation from repeat-confirmed direct anchors":"bounded triangle interpolation from provisional/direct anchors");
    }

    private Cell unknown(int row,int col,long evidence,String reason){return new Cell(row,col,tpsAxis[row],rpmAxis[col],State.UNKNOWN,Maturity.PROVISIONAL,Double.NaN,evidence,0,0,Double.NaN,Double.NaN,Double.NaN,0,reason);}

    private double normDistance(double tps,double rpm,double otherTps,double otherRpm,int row,int col){
        double ts=localStep(tpsAxis,row,6.5), rs=localStep(rpmAxis,col,400);
        double dx=(otherTps-tps)/ts, dy=(otherRpm-rpm)/rs; return Math.sqrt(dx*dx+dy*dy);
    }
    private static double localStep(double[] a,int i,double fallback){
        double s=Double.POSITIVE_INFINITY;if(i>0)s=Math.min(s,Math.abs(a[i]-a[i-1]));if(i+1<a.length)s=Math.min(s,Math.abs(a[i+1]-a[i]));return Double.isFinite(s)&&s>1e-9?s:fallback;
    }
    private static double maxFinite(double a,double b,double c){double m=Double.NaN;if(Double.isFinite(a))m=a;if(Double.isFinite(b))m=Double.isFinite(m)?Math.max(m,b):b;if(Double.isFinite(c))m=Double.isFinite(m)?Math.max(m,c):c;return m;}

    private static final class Triangle {
        final Cell a,b,c; final double wa,wb,wc,score;
        Triangle(Cell a,Cell b,Cell c,double wa,double wb,double wc,double score){this.a=a;this.b=b;this.c=c;this.wa=wa;this.wb=wb;this.wc=wc;this.score=score;}
        static Triangle of(Cell a,Cell b,Cell c,double x,double y,int row,int col,MapEstimateSurface s){
            double x1=a.tps,y1=a.rpm,x2=b.tps,y2=b.rpm,x3=c.tps,y3=c.rpm;
            double den=(y2-y3)*(x1-x3)+(x3-x2)*(y1-y3);if(Math.abs(den)<1e-9)return null;
            double wa=((y2-y3)*(x-x3)+(x3-x2)*(y-y3))/den;
            double wb=((y3-y1)*(x-x3)+(x1-x3)*(y-y3))/den; double wc=1-wa-wb;
            double eps=1e-8;if(wa<-eps||wb<-eps||wc<-eps)return null;
            double score=s.normDistance(x,y,a.tps,a.rpm,row,col)+s.normDistance(x,y,b.tps,b.rpm,row,col)+s.normDistance(x,y,c.tps,c.rpm,row,col);
            return new Triangle(a,b,c,wa,wb,wc,score);
        }
    }
}
