package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import se.anders.tunerstudio.aetuner.guided.method.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.*;

/** Actual modal prompt and width-constrained report with pinned actions. */
public final class GuidedPopupLayoutSyntheticTest {
    private static GuidedProductionWorkspacePanel workspace;
    public static void main(String[] args)throws Exception {
        File fixture=GuidedDecelAnalysisProgressRegressionTest.fixture(3);
        se.anders.tunerstudio.aetuner.model.AeProjectSnapshot tune=GuidedDecelAnalysisProgressRegressionTest.tune();
        DecelHistoricalReplayService.Result replay=DecelHistoricalReplayService.analyze(fixture,tune);
        DecelHistoricalResultModel result=DecelHistoricalResultModel.build(fixture,tune,replay,null);
        GuidedCapturePanel capture=new GuidedCapturePanel();
        SwingUtilities.invokeAndWait(() -> workspace=new GuidedProductionWorkspacePanel(capture));
        try {
            for(AeUiTheme.Side side:AeUiTheme.Side.values()) {
                CompletableFuture<Void> prompt=new CompletableFuture<>();
                SwingUtilities.invokeLater(() -> {
                    AeUiTheme.set(side);
                    JDialog dialog=GuidedV019DecelEvidenceEntryDialog.createPrompt(null,true);
                    Timer check=new Timer(250,e -> {
                        try {
                            require(find(dialog,JScrollPane.class)==null,"short prompt unexpectedly scrolls");
                            JTextArea explanation=find(dialog,JTextArea.class);
                            require(explanation.isShowing()&&explanation.getHeight()>=explanation.getPreferredSize().height,
                                    "prompt explanation is clipped");
                            JButton analyze=button(dialog,"Analyze Existing Log…");
                            require(analyze!=null&&analyze.isShowing(),"Analyze action missing");
                            require(SwingUtilities.getAncestorOfClass(JViewport.class,analyze)==null,"prompt action scrolls");
                            save(dialog,"popup-prompt-"+side+".png");
                            button(dialog,"Start Optional Live Capture").doClick();
                            require(Integer.valueOf(1).equals(dialog.getRootPane().getClientProperty("ae.evidence.choice")),"wrong prompt choice");
                            prompt.complete(null);
                        }catch(Throwable ex){prompt.completeExceptionally(ex);dialog.dispose();}
                    });check.setRepeats(false);check.start();dialog.setVisible(true);
                });
                prompt.get(15,TimeUnit.SECONDS);
                CompletableFuture<Void> report=new CompletableFuture<>();
                SwingUtilities.invokeLater(() -> {
                    JDialog dialog=GuidedTaskControls.install(GuidedDialogLifecycle.create(null,"Result layout",true));
                    JPanel root=new JPanel(new BorderLayout());
                    root.add(GuidedV019DecelResultView.create(result),BorderLayout.CENTER);
                    JPanel metrics=new JPanel(new GridLayout(1,5));
                    JLabel strength=new JLabel("1 strong / 1 good / 0 minimum");
                    strength.setFont(new Font("Dialog",Font.BOLD,14));metrics.add(strength);
                    for(int i=0;i<4;i++)metrics.add(new JLabel("Summary"));
                    root.add(metrics,BorderLayout.NORTH);
                    JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    JButton close=new JButton("Close report");close.addActionListener(e -> dialog.dispose());actions.add(close);
                    root.add(actions,BorderLayout.SOUTH);dialog.setContentPane(root);
                    dialog.setSize(780,440);
                    Timer check=new Timer(500,e -> {
                        try {
                            save(dialog,"popup-result-"+side+".png");
                            noHorizontalScroll(dialog);
                            require(strength.getPreferredSize().height>strength.getFontMetrics(strength.getFont()).getHeight(),
                                    "short wide summary value did not wrap");
                            require(close.isShowing()&&SwingUtilities.getAncestorOfClass(JViewport.class,close)==null,"report action is in scrolling body");
                            JTable table=find(dialog,JTable.class);
                            Rectangle last=table.getCellRect(0,table.getColumnCount()-1,true);
                            require(last.x+last.width<=table.getWidth(),"last table column extends outside viewport");
                            require(find(dialog,JScrollPane.class).getVerticalScrollBar().isVisible(),"long report does not scroll vertically");
                            close.doClick();report.complete(null);
                        }catch(Throwable ex){report.completeExceptionally(ex);dialog.dispose();}
                    });check.setRepeats(false);check.start();dialog.setVisible(true);
                });
                report.get(15,TimeUnit.SECONDS);
            }
            System.out.println("GuidedPopupLayoutSyntheticTest passed");
        }finally{SwingUtilities.invokeAndWait(() -> {for(Window w:Window.getWindows())w.dispose();capture.disposePanel();workspace=null;});}
    }
    private static void noHorizontalScroll(Container c) {
        for(Component child:c.getComponents()) {
            if(child instanceof JScrollPane)require(!((JScrollPane)child).getHorizontalScrollBar().isVisible(),"horizontal scrollbar visible");
            if(child instanceof Container)noHorizontalScroll((Container)child);
        }
    }
    private static JButton button(Container c,String text) {
        for(Component child:c.getComponents()) {
            if(child instanceof JButton&&text.equals(((JButton)child).getText()))return (JButton)child;
            if(child instanceof Container){JButton b=button((Container)child,text);if(b!=null)return b;}
        }return null;
    }
    private static <T> T find(Container c,Class<T> type) {
        for(Component child:c.getComponents()) {
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container){T found=find((Container)child,type);if(found!=null)return found;}
        }return null;
    }
    private static void save(Window w,String name)throws Exception {
        BufferedImage b=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=b.createGraphics();w.paint(g);g.dispose();
        File out=new File(System.getenv("SYNTHETIC_INTEGRATION_OUT")==null?"target/popup-layout":System.getenv("SYNTHETIC_INTEGRATION_OUT"));out.mkdirs();ImageIO.write(b,"png",new File(out,name));
    }
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}
