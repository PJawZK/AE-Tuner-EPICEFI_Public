package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Feedback must be bounded, off-EDT, and observational; terminal states cannot be overwritten. */
public final class GuidedDecelAnalysisProgressRegressionTest {
    public static void main(String[] args) throws Exception {
        File first=fixture(2050), second=fixture(3);
        AeProjectSnapshot tune=tune();
        List<String> trace=Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            DecelHistoricalReplayService.Result expected=DecelHistoricalReplayService.analyze(first,tune);
            DecelHistoricalReplayService.Result observed=executor.submit(() ->
                    DecelHistoricalReplayService.analyze(first,tune,(file,stage,rows) -> {
                        require(!SwingUtilities.isEventDispatchThread(),"analysis ran on EDT");
                        require(first.equals(file),"progress lost current source identity");
                        trace.add(stage+":"+rows);
                    })).get(10,TimeUnit.SECONDS);
            require(expected.reportText.equals(observed.reportText),"progress changed report bytes");
            require(trace.contains("Reading log rows:1") && trace.contains("Reading log rows:1025")
                    && trace.contains("Reading log rows:2049"),"row feedback missing or not batched");
            require(trace.stream().anyMatch(s -> s.startsWith("Classifying physical events"))
                    && trace.stream().anyMatch(s -> s.startsWith("Evaluating threshold evidence")),"analysis stages missing");
            List<File> files=new ArrayList<>();List<String> stages=new ArrayList<>();
            DecelHistoricalReplayService.Result pool=DecelHistoricalPoolService.analyze(new File[]{first,second},tune,
                    (file,stage,rows) -> {files.add(file);stages.add(stage);});
            require(files.contains(first)&&files.contains(second)
                    &&stages.contains("Checking detector compatibility across logs"),"pooled file/stage feedback missing");
            require(pool.reportText.equals(DecelHistoricalPoolService.analyze(new File[]{first,second},tune).reportText),
                    "progress changed pooled report bytes");
            Method nativeFixture=DecelHistoricalLogImporterRegressionTest.class.getDeclaredMethod(
                    "writeMlgFixture",int.class,boolean.class,boolean.class,boolean.class);
            nativeFixture.setAccessible(true);
            File nativeFile=(File)nativeFixture.invoke(null,1,false,false,false);
            List<Long> nativeRows=new ArrayList<>();
            DecelHistoricalLogImporter.load(nativeFile,(file,stage,rows) -> {
                if(stage.equals("Decoding native log"))nativeRows.add(rows);
            });
            require(nativeRows.contains(1L),"native decoder has no progress feedback");
            Thread.currentThread().interrupt();
            try { DecelHistoricalReplayService.analyze(first,tune,(f,s,r)->{});
                throw new AssertionError("interrupted analysis continued");
            } catch(CancellationException expectedCancel) { }
            finally { Thread.interrupted(); }
        } finally { executor.shutdownNow();first.delete();second.delete(); }
        AtomicReference<GuidedDecelAnalysisProgress> panel=new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new GuidedDecelAnalysisProgress(new File[]{first,second})));
        for(int i=0;i<10000;i++)panel.get().update(second,"Decoding",i);
        SwingUtilities.invokeAndWait(() -> {
            GuidedDecelAnalysisProgress progress=panel.get();progress.render();
            require(progress.statusText().contains("File 2 of 2")&&progress.statusText().contains("9999"),"latest bounded update lost");
            progress.cancelled();progress.update(first,"late completion",999999);progress.render();
            require(progress.statusText().contains("Analysis cancelled")&&!progress.statusText().contains("late completion"),
                    "late progress replaced cancelled feedback");
            GuidedDecelAnalysisProgress failure=new GuidedDecelAnalysisProgress(new File[]{first});
            failure.failed(new java.io.IOException("unreadable fixture"));
            require(failure.statusText().contains("unreadable fixture")&&!failure.active(),"failure state unclear");
        });
        System.out.println("GuidedDecelAnalysisProgressRegressionTest passed");
    }
    static File fixture(int rows) throws Exception {
        File file=File.createTempFile("decel-progress-", ".csv");file.deleteOnExit();
        StringBuilder csv=new StringBuilder("Time,RPM,TPS,TPSdot,DECEL_TPS,Fuel: TPS decel change,Fuel: AE delta newest fall,running,cranking\n");
        for(int i=0;i<rows;i++)csv.append(i*.01).append(",1600,20,-10,1,-0.8,-0.8,1,0\n");
        Files.write(file.toPath(),csv.toString().getBytes(StandardCharsets.UTF_8));return file;
    }
    static AeProjectSnapshot tune() throws Exception {
        Method m=DecelHistoricalThresholdProposalRegressionTest.class.getDeclaredMethod("snapshot",double.class,double[].class,String.class,double.class);
        m.setAccessible(true);return (AeProjectSnapshot)m.invoke(null,.05,new double[]{1000,1500,2000,2500,3000,3500,4000,4500},"Dual stride, newest",30.);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
