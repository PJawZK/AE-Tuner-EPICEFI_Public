package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Production modal analysis UI: busy heartbeat, cancel/late completion, failure and completion. */
public final class GuidedDecelAnalysisProgressSyntheticTest {
    private static String themeName;
    public static void main(String[] args) throws Exception {
        File fixture=GuidedDecelAnalysisProgressRegressionTest.fixture(3);
        AeProjectSnapshot tune=GuidedDecelAnalysisProgressRegressionTest.tune();
        DecelHistoricalReplayService.Result result=DecelHistoricalReplayService.analyze(fixture,tune);
        AtomicReference<JFrame> frame=new AtomicReference<>();
        AtomicReference<GuidedCapturePanel> panel=new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new GuidedCapturePanel());
            JFrame host=new JFrame("Decel analysis progress");
            host.setContentPane(new GuidedProductionWorkspacePanel(panel.get()));
            host.setSize(1260,760);host.setVisible(true);frame.set(host);
        });
        se.anders.tunerstudio.aetuner.ui.AeUiTheme.Side previousTheme=se.anders.tunerstudio.aetuner.ui.AeUiTheme.side();
        try {
            for (se.anders.tunerstudio.aetuner.ui.AeUiTheme.Side side : se.anders.tunerstudio.aetuner.ui.AeUiTheme.Side.values()) {
                themeName=side==se.anders.tunerstudio.aetuner.ui.AeUiTheme.Side.DARK_SIDE?"dark":"light";
                SwingUtilities.invokeAndWait(() -> se.anders.tunerstudio.aetuner.ui.AeUiTheme.set(side));
            blockedCase(frame.get(),fixture,tune,result,false);
            blockedCase(frame.get(),fixture,tune,result,true);
            SwingUtilities.invokeLater(() -> GuidedV019DecelHistoricalReplayDialog.open(frame.get(),tune,
                    new File[]{new File(fixture.getParentFile(),"missing-progress-log.csv")},null));
            await(() -> current()!=null && text(current()).contains("Analysis failed"));
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog=current();
                require(!DecelHistoricalResultContext.available(),"failure retained result authority");
                require(!find(dialog,"Review Proposed Threshold Changes").isEnabled(),"failed analysis enabled Review");
                save(dialog,"decel-analysis-failed.png");find(dialog,"Close").doClick();
            });
            SwingUtilities.invokeLater(() -> GuidedV019DecelHistoricalReplayDialog.open(frame.get(),tune,new File[]{fixture},null));
            await(() -> current()!=null && text(current()).contains("Analysis finished — no tuning result"));
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog=current();
                require(DecelHistoricalResultContext.available(),"completed analysis failed to publish its readonly result");
                require(find(dialog,"Full Technical Report…").isEnabled(),"completed report inaccessible");
                save(dialog,"decel-analysis-complete.png");find(dialog,"Close").doClick();
            });
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                se.anders.tunerstudio.aetuner.ui.AeUiTheme.set(previousTheme);
                panel.get().disposePanel();
                for(Window window:Window.getWindows())window.dispose();
                DecelHistoricalResultContext.clear();DecelHistoricalThresholdProposalContext.clear();
            });
            fixture.delete();
        }
        System.out.println("GuidedDecelAnalysisProgressSyntheticTest passed");
    }
    private static void blockedCase(JFrame host,File file,AeProjectSnapshot tune,
            DecelHistoricalReplayService.Result result,boolean reset) throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> GuidedV019DecelHistoricalReplayDialog.open(host,tune,new File[]{file},null,
                (files,snapshot,progress) -> {
                    require(!SwingUtilities.isEventDispatchThread(),"analysis executed on EDT");
                    progress.update(file,"Decoding native log",1234);
                    entered.countDown();
                    // Deliberately ignore cancellation until released to prove late-result suppression.
                    boolean done=false;
                    while(!done)try { release.await();done=true; }catch(InterruptedException ignored) { }
                    returned.countDown();return result;
                }));
        try {
            require(entered.await(5,TimeUnit.SECONDS),"analysis worker did not enter");
            await(() -> current()!=null && findType(current(),GuidedDecelAnalysisProgress.class)!=null);
            await(() -> current()!=null && text(current()).matches("(?s).*Elapsed: [1-9][0-9]* s.*"));
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog=current();
                GuidedDecelAnalysisProgress progress=findType(dialog,GuidedDecelAnalysisProgress.class);
                progress.render();
                require(progress.isShowing() && progress.statusText().contains("1234"),"busy file/row progress missing");
                require(findType(dialog,JProgressBar.class).isIndeterminate(),"busy activity bar is not animated");
                require(!find(dialog,"Review Proposed Threshold Changes").isEnabled(),"busy Review unlocked");
                if(reset) {
                    find(dialog,"Start Over").doClick();
                    require(!dialog.isDisplayable(),"Start Over left analysis open");
                } else {
                    save(dialog,"decel-analysis-busy.png");
                    find(dialog,"Cancel Analysis").doClick();
                    require(progress.statusText().contains("Analysis cancelled")&&!progress.active(),"cancel feedback missing");
                    require(!findType(dialog,JProgressBar.class).isIndeterminate(),"cancel kept busy indicator running");
                    save(dialog,"decel-analysis-cancelled.png");
                }
            });
            release.countDown();require(returned.await(5,TimeUnit.SECONDS),"worker did not return");
            CompletableFuture<Void> settled=new CompletableFuture<>();
            SwingUtilities.invokeLater(() -> {
                Timer timer=new Timer(150,e -> settled.complete(null));timer.setRepeats(false);timer.start();
            });
            settled.get(5,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> {
                require(!DecelHistoricalResultContext.available()&&!DecelHistoricalThresholdProposalContext.active(),
                        "late completion resurrected cancelled/reset authority");
                if(!reset)find(current(),"Close").doClick();
            });
        } finally { release.countDown(); }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        CompletableFuture<Void> ready=new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            Timer timer=new Timer(25,null);
            timer.addActionListener(e -> {
                try {
                    if(condition.getAsBoolean()) {timer.stop();ready.complete(null);}
                    else if(System.nanoTime()>deadline) {timer.stop();ready.completeExceptionally(new AssertionError("UI state timed out"));}
                } catch(Throwable ex) {timer.stop();ready.completeExceptionally(ex);}
            });timer.start();
        });ready.get(10,TimeUnit.SECONDS);
    }
    private static JDialog current() {
        for(Window window:Window.getWindows())if(window instanceof JDialog && window.isShowing()
                &&((JDialog)window).getTitle().equals("Decel Detection — Historical Result"))return (JDialog)window;
        return null;
    }
    private static String text(Container root) {
        StringBuilder out=new StringBuilder();
        for(Component child:root.getComponents()) {
            if(child instanceof JLabel)out.append(((JLabel)child).getText());
            if(child instanceof javax.swing.text.JTextComponent)out.append(((javax.swing.text.JTextComponent)child).getText());
            if(child instanceof Container)out.append(text((Container)child));
        }return out.toString();
    }
    private static JButton find(Container root,String label) {
        for(Component child:root.getComponents()) {
            if(child instanceof JButton&&((JButton)child).getText().equals(label))return (JButton)child;
            if(child instanceof Container){JButton found=find((Container)child,label);if(found!=null)return found;}
        }return null;
    }
    private static <T> T findType(Container root,Class<T> type) {
        if(type.isInstance(root))return type.cast(root);
        for(Component child:root.getComponents())if(child instanceof Container) {
            T found=findType((Container)child,type);if(found!=null)return found;
        }return null;
    }
    private static void save(Window window,String name) {
        try {
            BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();try{window.paint(g);}finally{g.dispose();}
            File out=new File(System.getenv("SYNTHETIC_INTEGRATION_OUT")==null?"target/ui-pass2":System.getenv("SYNTHETIC_INTEGRATION_OUT"));
            out.mkdirs();ImageIO.write(image,"png",new File(out,name.replace(".png","-"+themeName+".png")));
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
