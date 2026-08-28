package se.anders.tunerstudio.aetuner.guided.mapestimate;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Physical-data regressions extracted from Archive25 + Archive26. */
public final class MapEstimateDev17RegressionTest {
    private static final double[] TPS={0,6.5,13.5,20,26.5,33.5,40,46.5,53.5,60,66.5,73.5,80,86.5,93.5,100};
    private static final double[] RPM={600,1000,1300,1700,2000,2500,2900,3200,3600,3900,4300,4700,5000,5400,5800,6200};
    private static final String CFG="MEGA144H7EPIC-Volvo940Turbo";

    public static void main(String[] args)throws Exception{
        archive26FalseDirectIsDemoted();
        archive25RawRangeOutlierDoesNotPoisonRepeatableCell();
        archive25And26CreateCrossSessionMaturity();
        repeatSessionDisagreementBecomesConflict();
        targetFrontierFollowsObservedCloudNotIndependentMaxima();
        v1MemoryMigratesToV2WithoutLosingEvidence();
        System.out.println("MapEstimateDev17RegressionTest passed");
    }

    private static void archive26FalseDirectIsDemoted()throws Exception{
        MapEstimateMemory a26=fixture("A26");
        MapEstimateSurface s=new MapEstimateSurface(a26,20);
        MapEstimateSurface.Cell falseDirect=s.cell(2,3); // 13.5% / 1700
        require(falseDirect.state==MapEstimateSurface.State.UNKNOWN,
                "Archive26 one-sided 11.5-12.1% TPS evidence still masquerades as Direct at 13.5%/1700");
        require(falseDirect.evidenceSamples>=40 && falseDirect.reason.contains("exact table coordinate"),
                "unsupported Archive26 node did not retain nearby-evidence/reason context");
        MapEstimateSurface.Cell physical=s.cell(1,4); // 6.5% / 2000
        require(physical.state==MapEstimateSurface.State.DIRECT,
                "Archive26 physically repeatable 6.5%/2000 anchor was lost");
        require(Math.abs(physical.valueKpa-44.12)<0.20,
                "Archive26 6.5%/2000 value drifted: "+physical.valueKpa);
        require(s.count(MapEstimateSurface.State.DIRECT)==4,
                "Archive26 geometry gate should retain four supported Direct anchors, got "+s.count(MapEstimateSurface.State.DIRECT));
        require(s.cell(1,2).state!=MapEstimateSurface.State.INTERPOLATED_STRONG,
                "Archive26 6.5%/1300 remained strong only by using the physically unsupported 13.5%/1700 anchor");
    }

    private static void archive25RawRangeOutlierDoesNotPoisonRepeatableCell()throws Exception{
        MapEstimateMemory a25=fixture("A25");
        MapEstimateSurface.Cell idle=new MapEstimateSurface(a25,20).cell(0,0);
        require(idle.rangeKpa>20.0,"fixture no longer contains Archive25's >20 kPa lifetime range");
        require(idle.state==MapEstimateSurface.State.DIRECT,
                "one Archive25 extreme permanently poisoned the otherwise repeatable 0%/600 region");
        require(Math.abs(idle.valueKpa-62.46)<0.40,
                "Archive25 0%/600 physical mean changed unexpectedly: "+idle.valueKpa);
    }

    private static void archive25And26CreateCrossSessionMaturity()throws Exception{
        MapEstimateMemory combined=new MapEstimateMemory(CFG,TPS,RPM);
        combined.mergeCompletedSession(fixture("A25"));
        combined.mergeCompletedSession(fixture("A26"));
        MapEstimateSurface s=new MapEstimateSurface(combined,20);
        require(s.cell(0,0).maturity==MapEstimateSurface.Maturity.CONFIRMED,
                "Archive25/26 agreement did not confirm 0%/600 evidence");
        require(s.cell(1,4).maturity==MapEstimateSurface.Maturity.CONFIRMED,
                "Archive25/26 agreement did not confirm 6.5%/2000 evidence");

        // Archive25 reaches 13.5%/1700, Archive26 does not: Archive26's
        // one-sided ~12% TPS evidence must neither contradict nor falsely
        // confirm the exact 13.5% node. It remains a one-session provisional
        // Direct until another completed session actually reaches the node.
        MapEstimateSurface.Cell oneSession=s.cell(2,3);
        require(oneSession.state==MapEstimateSurface.State.DIRECT
                        && oneSession.maturity==MapEstimateSurface.Maturity.PROVISIONAL
                        && oneSession.proposalEligible()
                        && oneSession.repeatSessionEvidenceSamples==0,
                "off-node Archive26 evidence incorrectly confirmed or contradicted the Archive25 13.5%/1700 node");
        require(s.count(MapEstimateSurface.Maturity.CONFIRMED)>=2,
                "physical cross-session fixture lost the known repeat-confirmed anchors");
    }

    private static void repeatSessionDisagreementBecomesConflict(){
        double[] t={0,10,20},r={1000,2000,3000};
        MapEstimateMemory stored=new MapEstimateMemory("cfg",t,r);
        MapEstimateMemory first=new MapEstimateMemory("cfg",t,r);
        MapEstimateMemory second=new MapEstimateMemory("cfg",t,r);
        repeat(first,10,2000,60,30);
        repeat(second,10,2000,68,30);
        stored.mergeCompletedSession(first);stored.mergeCompletedSession(second);
        MapEstimateSurface.Cell cell=new MapEstimateSurface(stored,20).cell(1,1);
        require(cell.state==MapEstimateSurface.State.CONFLICT
                        && cell.maturity==MapEstimateSurface.Maturity.RECHECK
                        && cell.betweenSessionRangeKpa>6.0,
                "clean but contradictory completed sessions did not become Conflict/Recheck");
    }

    private static void targetFrontierFollowsObservedCloudNotIndependentMaxima(){
        double[] t={0,10,20,30},r={1000,2000,3000,4000};
        MapEstimateMemory m=new MapEstimateMemory("cfg",t,r);
        repeat(m,30,1000,75,25); // high TPS only at low RPM
        repeat(m,0,4000,35,25);  // high RPM only at closed throttle
        MapEstimateSurface surface=new MapEstimateSurface(m,20);
        MapEstimateTargetSelector.Target target=new MapEstimateTargetSelector().choose(surface,m,
                MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,MapEstimateCellScope.all(4,4),Double.NaN,Double.NaN);
        require(target.available(),"local evidence-cloud frontier produced no target");
        require(!(target.row==3&&target.col==3),
                "independent max TPS/max RPM incorrectly authorized the unobserved 30%/4000 corner");
        require(adjacentTo(target.row,target.col,3,0)||adjacentTo(target.row,target.col,0,3),
                "target escaped more than one table step beyond the demonstrated operating cloud");
    }

    private static void v1MemoryMigratesToV2WithoutLosingEvidence()throws Exception{
        Path dir=Files.createTempDirectory("ae-map-dev17-v1-");
        Path current=dir.resolve(MapEstimateMemoryStore.FILE_NAME);
        String text="AE_TUNER_MAP_ESTIMATE_MEMORY,1\n"
                +"configuration=cfg\n"
                +"tps_axis=0;10\n"
                +"rpm_axis=1000;2000\n"
                +"bucket_tps,bucket_rpm,count,sum_tps,sum_rpm,sum_map,sum_map_sq,min_map,max_map,sum_clt,clt_count,sum_mat,mat_count\n"
                +"10,20,20,200,40000,1200,72000,60,60,1600,20,500,20\n";
        Files.write(current,text.getBytes(StandardCharsets.UTF_8));
        MapEstimateMemoryStore store=new MapEstimateMemoryStore(dir);
        MapEstimateMemoryStore.LoadResult loaded=store.loadBest(new MapEstimateMemory("cfg",new double[]{0,10},new double[]{1000,2000}));
        require(loaded.memory.sampleCount()==20,"v1 migration lost evidence");
        MapEstimateEvidenceBucket bucket=loaded.memory.buckets().get(0);
        require(bucket.sessionCount()==1,"v1 pooled evidence was not conservatively represented as one legacy session");
        store.save(loaded.memory);
        String magic=Files.readAllLines(current,StandardCharsets.UTF_8).get(0);
        require("AE_TUNER_MAP_ESTIMATE_MEMORY,2".equals(magic),"next save did not migrate memory to v2");
        require(store.loadCurrent().sampleCount()==20,"v2 roundtrip changed migrated sample count");
    }

    private static MapEstimateMemory fixture(String session)throws Exception{
        Path path=Paths.get("src/test/resources/physical/archive25_26_map_validation.csv");
        require(Files.exists(path),"physical Archive25/26 fixture missing");
        MapEstimateMemory memory=new MapEstimateMemory(CFG,TPS,RPM);
        try(BufferedReader in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String line=in.readLine();
            while((line=in.readLine())!=null){
                String[] p=line.split(",",-1);if(!session.equals(p[0]))continue;
                MapEstimateEvidenceBucket b=MapEstimateEvidenceBucket.restoredV1(
                        Integer.parseInt(p[1]),Integer.parseInt(p[2]),Long.parseLong(p[3]),
                        Double.parseDouble(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),
                        Double.parseDouble(p[8]),Double.parseDouble(p[9]),Double.parseDouble(p[10]),Long.parseLong(p[11]),
                        Double.parseDouble(p[12]),Long.parseLong(p[13]));
                memory.putRestored(b);
            }
        }
        return memory;
    }

    private static boolean adjacentTo(int r,int c,int rr,int cc){return Math.abs(r-rr)<=1&&Math.abs(c-cc)<=1;}
    private static void repeat(MapEstimateMemory m,double tps,double rpm,double map,int n){for(int i=0;i<n;i++)m.add(tps+(i%3-1)*0.02,rpm+(i%5-2),map+(i%3-1)*0.05,80,25);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
