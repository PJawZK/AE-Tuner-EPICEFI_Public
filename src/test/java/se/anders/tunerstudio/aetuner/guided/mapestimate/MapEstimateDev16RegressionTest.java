package se.anders.tunerstudio.aetuner.guided.mapestimate;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MapEstimateDev16RegressionTest {
    private static final double[] TPS={0,6.5,13.5,20,26.5,33.5,40,46.5,53.5,60,66.5,73.5,80,86.5,93.5,100};
    private static final double[] RPM={600,1000,1300,1700,2000,2500,2900,3200,3600,3900,4300,4700,5100,5400,5800,6200};
    public static void main(String[] args)throws Exception{
        memoryPathUsesDedicatedFolderAndOverride();
        memoryRoundTripKeepsOneBackup();
        corruptCurrentRecoversPreviousWithoutLosingIt();
        noExtrapolationOutsideDirectTriangle();
        directFineTuneScopeIsHardBoundary();
        proposalMaskAndFlatIndicesAreExact();
        resetDoesNotEraseStoredMemoryAndFinishDoesNotDoubleCount();
        archive25ReplayCharacterization();
        System.out.println("MapEstimateDev16RegressionTest passed");
    }


    private static void memoryPathUsesDedicatedFolderAndOverride()throws Exception{
        String key="ae.tuner.memory.dir";String old=System.getProperty(key);
        Path override=Files.createTempDirectory("ae-map-memory-root-").resolve("custom-memory");
        try{System.setProperty(key,override.toString());require(MapEstimateMemoryPaths.memoryDirectory().equals(override),"memory override not honored");}
        finally{if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
        Path normal=MapEstimateMemoryPaths.memoryDirectory();
        require("Memory".equals(normal.getFileName().toString()),"default memory folder is not the dedicated Memory sibling: "+normal);
    }

    private static void memoryRoundTripKeepsOneBackup()throws Exception{
        Path dir=Files.createTempDirectory("ae-map-memory-");MapEstimateMemoryStore store=new MapEstimateMemoryStore(dir);
        MapEstimateMemory m=new MapEstimateMemory("cfg",TPS,RPM);m.add(10.1,2001,55,80,25);store.save(m);
        require(Files.exists(store.currentPath()),"current memory missing");require(!Files.exists(store.previousPath()),"backup should not exist after first save");
        m.add(11.2,2100,57,81,26);store.save(m);require(Files.exists(store.previousPath()),"single previous backup missing");
        MapEstimateMemory loaded=store.loadCurrent();require(loaded.sampleCount()==2,"roundtrip sample count mismatch");
        long files=Files.list(dir).filter(p->p.getFileName().toString().endsWith(".memory")).count();require(files==2,"memory directory accumulated unexpected history files: "+files);
    }


    private static void corruptCurrentRecoversPreviousWithoutLosingIt()throws Exception{
        Path dir=Files.createTempDirectory("ae-map-memory-recover-");MapEstimateMemoryStore store=new MapEstimateMemoryStore(dir);
        MapEstimateMemory m=new MapEstimateMemory("cfg",TPS,RPM);m.add(10,2000,55,80,25);store.save(m);
        m.add(11,2100,56,81,26);store.save(m); // previous now contains the one-sample valid memory.
        Files.write(store.currentPath(),"corrupt-current".getBytes(StandardCharsets.UTF_8));
        MapEstimateMemory template=new MapEstimateMemory("cfg",TPS,RPM);
        MapEstimateMemoryStore.LoadResult recovered=store.loadBest(template);
        require(recovered.recoveredFromPrevious,"corrupt current did not recover previous backup");
        require(recovered.memory.sampleCount()==1,"wrong backup recovered");
        require(store.loadCurrent().sampleCount()==1,"current file was not repaired from previous");
        require(store.load(store.previousPath()).sampleCount()==1,"good previous backup was damaged during recovery");
    }

    private static void noExtrapolationOutsideDirectTriangle(){
        MapEstimateMemory m=new MapEstimateMemory("cfg",new double[]{0,10,20,30},new double[]{1000,2000,3000,4000});
        repeat(m,0,1000,40,30);repeat(m,20,1000,70,30);repeat(m,0,3000,30,30);
        MapEstimateSurface s=new MapEstimateSurface(m,20);
        require(s.cell(1,1).state==MapEstimateSurface.State.INTERPOLATED_STRONG||s.cell(1,1).state==MapEstimateSurface.State.INTERPOLATED_WEAK,"inside triangle was not interpolated");
        require(s.cell(3,3).state==MapEstimateSurface.State.UNKNOWN,"outside triangle was extrapolated");
    }

    private static void directFineTuneScopeIsHardBoundary()throws Exception{
        Path dir=Files.createTempDirectory("ae-map-fine-");MapEstimateEvidenceSession session=new MapEstimateEvidenceSession(new MapEstimateMemoryStore(dir),"cfg",new double[]{0,10,20},new double[]{1000,2000,3000});
        MapEstimateCellScope scope=MapEstimateCellScope.none(3,3).withCell(1,1,true);
        session.start(MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,scope);
        for(int i=0;i<25;i++){require(session.acceptStable(10.1,1995,55+i*0.01,80,25),"selected evidence rejected");require(!session.acceptStable(20.0,3000,95,80,25),"out-of-scope evidence accepted");}
        session.finish();MapEstimateSurface surface=new MapEstimateSurface(session.stored(),20);
        require(surface.cell(1,1).state==MapEstimateSurface.State.DIRECT,"selected cell not direct");
        require(surface.cell(2,2).state==MapEstimateSurface.State.UNKNOWN,"out-of-scope cell learned unexpectedly");
    }

    private static void proposalMaskAndFlatIndicesAreExact(){
        double[] t={0,10,20,30},r={1000,2000,3000,4000};double[][] current=new double[4][4];for(int i=0;i<4;i++)for(int j=0;j<4;j++)current[i][j]=40+i*10+j;
        MapEstimateMemory m=new MapEstimateMemory("cfg",t,r);repeat(m,0,1000,50,30);repeat(m,10,3000,72,30);repeat(m,30,4000,100,30);
        MapEstimateSurface s=new MapEstimateSurface(m,20);MapEstimateCellScope scope=MapEstimateCellScope.none(4,4).withCell(0,0,true).withCell(1,2,true).withCell(3,3,true);
        MapEstimateProposal p=MapEstimateProposal.build("cfg",t,r,current,s,MapEstimateCoverageStrategy.DIRECT_FINE_TUNE,scope,115);
        ProposalWritePlan plan=p.writePlan();require(plan!=null&&plan.changeCount()==3,"expected three exact changes");
        require(plan.getChanges().get(0).flatIndex==0,"first flat index wrong");require(plan.getChanges().get(1).flatIndex==6,"middle flat index wrong");require(plan.getChanges().get(2).flatIndex==15,"last flat index wrong");
        for(int row=0;row<4;row++)for(int col=0;col<4;col++){boolean expected=(row==0&&col==0)||(row==1&&col==2)||(row==3&&col==3);require(p.changed(row,col)==expected,"proposal mask leaked at "+row+","+col);}
        String manifest=plan.verificationManifestJson();require(manifest.contains("\"parameter\": \"mapEstimateTable\"")&&manifest.contains("\"index\": 15"),"manifest missing MAP table indices");
    }

    private static void resetDoesNotEraseStoredMemoryAndFinishDoesNotDoubleCount()throws Exception{
        Path dir=Files.createTempDirectory("ae-map-session-");MapEstimateMemoryStore store=new MapEstimateMemoryStore(dir);MapEstimateEvidenceSession session=new MapEstimateEvidenceSession(store,"cfg",new double[]{0,10},new double[]{1000,2000});
        session.start(MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,MapEstimateCellScope.all(2,2));for(int i=0;i<20;i++)session.acceptStable(0,1000,40,70,20);session.finish();long first=session.stored().sampleCount();require(first==20,"first commit wrong");
        session.finish();require(session.stored().sampleCount()==20,"second finish double-counted evidence");
        session.start(MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,MapEstimateCellScope.all(2,2));for(int i=0;i<10;i++)session.acceptStable(10,2000,60,75,22);session.reset();require(session.stored().sampleCount()==20,"reset erased or committed unfinished delta");
        MapEstimateMemory reloaded=store.loadCurrent();require(reloaded.sampleCount()==20,"disk memory changed after reset");
    }

    private static void archive25ReplayCharacterization()throws Exception{
        Path csv=Paths.get("/mnt/data/archive25_stable.csv");if(!Files.exists(csv)){System.out.println("Archive25 fixture absent; replay skipped");return;}
        MapEstimateMemory m=new MapEstimateMemory("MEGA144H7EPIC-Volvo940Turbo",TPS,RPM);
        try(BufferedReader in=Files.newBufferedReader(csv,StandardCharsets.UTF_8)){String line=in.readLine();while((line=in.readLine())!=null){String[] p=line.split(",",-1); double clt=p[3].isEmpty()?Double.NaN:Double.parseDouble(p[3]); double mat=p[4].isEmpty()?Double.NaN:Double.parseDouble(p[4]); m.add(Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]),clt,mat);}}
        require(m.sampleCount()==4874,"Archive25 accepted sample count changed: "+m.sampleCount());
        MapEstimateSurface s=new MapEstimateSurface(m,20);
        MapEstimateTargetSelector.Target target=new MapEstimateTargetSelector().choose(s,m,MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,MapEstimateCellScope.all(16,16),Double.NaN,Double.NaN);
        System.out.println("Archive25 dev16: buckets="+m.bucketCount()+" direct="+s.count(MapEstimateSurface.State.DIRECT)+" strong="+s.count(MapEstimateSurface.State.INTERPOLATED_STRONG)+" weak="+s.count(MapEstimateSurface.State.INTERPOLATED_WEAK)+" conflict="+s.count(MapEstimateSurface.State.CONFLICT)+" unknown="+s.count(MapEstimateSurface.State.UNKNOWN)+" target="+(target.available()?TPS[target.row]+"%/"+RPM[target.col]+"rpm":"none")+" reason="+target.reason);
        require(m.bucketCount()<700,"persistent evidence did not compress raw samples enough");
        Path memoryDir=Files.createTempDirectory("archive25-map-memory-");
        MapEstimateMemoryStore archiveStore=new MapEstimateMemoryStore(memoryDir);
        archiveStore.save(m);
        long memoryBytes=Files.size(archiveStore.currentPath());
        System.out.println("Archive25 persistent memory size="+memoryBytes+" bytes");
        require(memoryBytes<131072,"Archive25 learned-state file grew beyond the 128 KiB compact-memory guard: "+memoryBytes);
        require(s.count(MapEstimateSurface.State.DIRECT)>=10,"Archive25 produced implausibly few direct cells");
        require(s.count(MapEstimateSurface.State.INTERPOLATED_STRONG)>=5,"Archive25 produced no useful bounded interpolation");
        require(s.count(MapEstimateSurface.State.CONFLICT)>=1,"Archive25 failed to surface any conflicting area");
        require(target.available()&&TPS[target.row]<33.5,"automatic target crossed high-TPS coaching boundary");
    }

    private static void repeat(MapEstimateMemory m,double tps,double rpm,double map,int n){for(int i=0;i<n;i++)m.add(tps+(i%3-1)*0.02,rpm+(i%5-2),map+(i%3-1)*0.05,80,25);}
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
