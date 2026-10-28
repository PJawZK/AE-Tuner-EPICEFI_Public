package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/** Real window close/reopen and single-click task controls, including a modal pop-out. */
public final class GuidedTaskControlsSyntheticTest {
    public static void main(String[] args) throws Exception {
        java.util.concurrent.CompletableFuture<Void> completed = new java.util.concurrent.CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            GuidedCapturePanel panel = new GuidedCapturePanel();
            GuidedProductionWorkspacePanel root = new GuidedProductionWorkspacePanel(panel);
            JFrame frame = new JFrame("Workflow controls");
            try {
                AeUiTheme.set(AeUiTheme.Side.DARK_SIDE);
                frame.setContentPane(root); frame.setSize(1366,768);frame.setVisible(true);
                GuidedMethodProbeSession probe=(GuidedMethodProbeSession)get(panel,"probeSession");
                probe.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.INSTANT_FUEL_SETUP));
                probe.accept(GuidedReviewPerformanceCharacterization.sample(1,true));
                JDialog first = GuidedTaskControls.install(GuidedDialogLifecycle.create(frame,"Capture",false));
                first.setContentPane(new GuidedV019InstantFuelFocus(first,null));
                first.setSize(1100,660); first.setVisible(true);
                first.dispose(); // Closing the view must not cancel or clear capture.
                require(probe.state()==GuidedCaptureState.CAPTURING && probe.sampleCount()==1,
                        "closing Focus changed capture state");
                JDialog reopened = GuidedTaskControls.install(GuidedDialogLifecycle.create(frame,"Capture reopened",false));
                reopened.setContentPane(new GuidedV019InstantFuelFocus(reopened,null));
                reopened.setSize(1100,660);reopened.setVisible(true);
                // WindowOpened is queued even on EDT; test the installed controls after it is dispatched.
                SwingUtilities.invokeLater(() -> {
                    try {
                        JButton cancel=find(reopened,"Cancel Task");
                        require(cancel!=null && cancel.isShowing(),"reopened pop-out has no visible Cancel Task");
                        save(reopened,"workflow-controls-focus-dark.png");
                        cancel.doClick();
                        require(panel.taskCancelled() && !reopened.isDisplayable() && probe.sampleCount()==1,
                                "single-click cancel failed to stop/retain/close");
                        save(frame,"workflow-controls-cancelled-dark.png");
                        JButton reset=find(root,"Start Over");
                        require(reset!=null,"workspace lost Start Over after closing pop-out");
                        reset.doClick();
                        require(!panel.taskCancelled() && !probe.hasEvidence(),"single-click reset failed");
                        save(frame,"workflow-controls-workspace-dark.png");
                        JDialog modal=GuidedTaskControls.install(GuidedDialogLifecycle.create(frame,"Review",true));
                        modal.add(new JLabel("Review in progress"));modal.setSize(650,300);
                        Timer click=new Timer(150,e -> {
                            JButton button=find(modal,"Start Over");
                            require(button!=null && button.isShowing(),"modal workflow controls missing");
                            button.doClick();
                        });
                        click.setRepeats(false);click.start();modal.setVisible(true);
                        require(!modal.isDisplayable(),"modal Start Over did not close the task window");
                        System.out.println("GuidedTaskControlsSyntheticTest passed");
                        completed.complete(null);
                    } catch(Throwable ex) { completed.completeExceptionally(ex); }
                    finally { panel.disposePanel(); frame.dispose(); }
                });
            } catch(Exception ex) {
                panel.disposePanel();frame.dispose();throw new RuntimeException(ex);
            }
        });
        try { completed.get(20, java.util.concurrent.TimeUnit.SECONDS); }
        finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) window.dispose();
            });
        }
    }
    private static JButton find(Container root,String text) {
        for(Component c:root.getComponents()) {
            if(c instanceof JButton && ((JButton)c).getText().equals(text)) return (JButton)c;
            if(c instanceof Container) { JButton found=find((Container)c,text);if(found!=null)return found; }
        }return null;
    }
    private static Object get(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void save(Window window,String name) throws Exception {
        window.validate();
        BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();try { window.paint(g); } finally { g.dispose(); }
        File dir=new File(System.getenv("SYNTHETIC_INTEGRATION_OUT")==null?"target/ui-pass1":System.getenv("SYNTHETIC_INTEGRATION_OUT"));
        dir.mkdirs();ImageIO.write(image,"png",new File(dir,name));
    }
    private static void require(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
}
