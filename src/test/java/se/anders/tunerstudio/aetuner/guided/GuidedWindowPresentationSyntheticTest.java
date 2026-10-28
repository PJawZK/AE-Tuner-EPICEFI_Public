package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Actual window chrome, theme changes and small-screen reachability. */
public final class GuidedWindowPresentationSyntheticTest {
    private static JFrame frame;
    private static JFrame diagnostics;
    private static GuidedProductionWorkspacePanel workspace;
    public static void main(String[] args) throws Exception {
        GuidedCapturePanel capture=new GuidedCapturePanel();
        SwingUtilities.invokeAndWait(() -> workspace=new GuidedProductionWorkspacePanel(capture));
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Method open=GuidedV019WorkspacePanel.class.getDeclaredMethod("openFocus");
                open.setAccessible(true);
                open.invoke(GuidedV019WorkflowNavigation.workspace());
                open.invoke(GuidedV019WorkflowNavigation.workspace());
                int count=0;
                for(Window w:Window.getWindows())if(w.isDisplayable() && w instanceof JFrame)count++;
                require(count==1,"reopening Focus created duplicate independent windows");
                GuidedDialogLifecycle.disposeAll();
            } catch(Exception ex){throw new RuntimeException(ex);}
        });
        Color hostLabel=UIManager.getColor("Label.foreground");
        try {
            for(AeUiTheme.Side side:AeUiTheme.Side.values()) {
                SwingUtilities.invokeAndWait(() -> {
                    AeUiTheme.set(side);
                    require(GuidedUiTheme.contrast(AeUiTheme.focusGreen(),AeUiTheme.card())>=4.5,
                            "painted green captions lack contrast");
                    require(GuidedUiTheme.contrast(AeUiTheme.focusAmber(),AeUiTheme.card())>=4.5,
                            "painted amber captions lack contrast");
                    frame=GuidedWindowTools.frame(null,"Presentation",false);
                    frame.setContentPane(new GuidedV019TpsTimingFocus(null,null));
                    frame.setMinimumSize(new Dimension(950,560));frame.setSize(1260,680);frame.setVisible(true);
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(frame.isResizable(),"Focus is not resizable");
                    require(find(frame,"Start Over")!=null,"Focus lacks session reset");
                    require(!frame.isUndecorated() && frame.getType()==Window.Type.NORMAL,
                            "Focus lacks a normal native title bar");
                    require(frame.getRootPane().getWindowDecorationStyle()==JRootPane.NONE,
                            "Focus uses Swing title-bar decorations");
                    for(String name:new String[]{"Minimize","Maximize","Restore Window","Close Window"})
                        require(find(frame,name)==null,"redundant content control remains: "+name);
                    Rectangle area=GuidedWindowTools.usable(frame);
                    require(area.contains(frame.getBounds()),"Focus extends beyond screen");
                    checkText(frame);
                    save(frame,"presentation-focus-"+side.name()+".png");
                    find(frame,"Options / Info").doClick();
                    for(Window w:Window.getWindows())if(w instanceof JFrame && w.isVisible()
                            && ((JFrame)w).getTitle().equals("TPS Movement / Timing — Diagnostics"))diagnostics=(JFrame)w;
                    require(diagnostics!=null,"real TPS diagnostics did not open");
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(!findScroll(diagnostics).getHorizontalScrollBar().isVisible(),
                            "diagnostic report forced off screen horizontally");
                    checkText(diagnostics);
                    save(diagnostics,"presentation-tps-diagnostics-"+side.name()+".png");
                    frame.dispose();
                });
                SwingUtilities.invokeAndWait(() -> {
                    frame=GuidedWindowTools.frame(null,"Diagnostics");
                    JPanel body=new JPanel();body.setPreferredSize(new Dimension(1200,800));
                    JLabel label=new JLabel("<html>Diagnostic values must remain readable</html>");
                    label.setForeground(Color.BLACK);body.add(label);
                    body.add(new JButton("Disabled action"){{setEnabled(false);}});
                    frame.setContentPane(body);frame.setMinimumSize(new Dimension(1100,800));
                    frame.setSize(1300,900);frame.setVisible(true);
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(GuidedWindowTools.usable(frame).contains(frame.getBounds()),"diagnostics not screen bounded");
                    JScrollPane scroll=findScroll(frame);
                    require(scroll!=null && scroll.getVerticalScrollBar().isVisible(),"oversized diagnostics unreachable");
                    checkText(frame);save(frame,"presentation-diagnostics-"+side.name()+".png");
                    AeUiTheme.set(side==AeUiTheme.Side.DARK_SIDE?AeUiTheme.Side.LIGHT_SIDE:AeUiTheme.Side.DARK_SIDE);
                    GuidedUiTheme.apply(frame.getRootPane());checkText(frame);
                    require(hostLabel.equals(UIManager.getColor("Label.foreground")),"host defaults changed");
                    GuidedDialogLifecycle.disposeAll();
                    require(!frame.isDisplayable(),"independent frame escaped plugin cleanup");
                });
            }
            for(AeUiTheme.Side side:AeUiTheme.Side.values()) {
                for(boolean threshold:new boolean[]{true,false}) {
                    SwingUtilities.invokeAndWait(() -> {
                        AeUiTheme.set(side);
                        frame=GuidedWindowTools.frame(null,"Audit fixes",false);
                        frame.setContentPane(threshold?new GuidedV019ThresholdFocus(null,null):new GuidedV019BlendDurationFocus(null,null));
                        frame.setSize(1260,680);frame.setVisible(true);
                    });
                    SwingUtilities.invokeAndWait(() -> {
                        checkText(frame);
                        save(frame,(threshold?"presentation-threshold-":"presentation-blend-")+side.name()+".png");
                        frame.dispose();
                    });
                }
            }
            System.out.println("GuidedWindowPresentationSyntheticTest passed");
        } finally {SwingUtilities.invokeAndWait(() -> {for(Window w:Window.getWindows())w.dispose();capture.disposePanel();workspace=null;});}
    }
    private static void checkText(Component c) {
        if(c instanceof JLabel || c instanceof JButton || c instanceof javax.swing.text.JTextComponent) {
            require(GuidedUiTheme.contrast(c.getForeground(),GuidedUiTheme.background(c))>=4.49,
                    "low contrast "+c.getClass().getSimpleName()+" "+c.getForeground()+" / "+GuidedUiTheme.background(c));
        }
        if(c instanceof Container)for(Component child:((Container)c).getComponents())checkText(child);
    }
    private static JButton find(Container c,String text) {
        for(Component child:c.getComponents()) {
            if(child instanceof JButton && text.equals(((JButton)child).getText()))return (JButton)child;
            if(child instanceof Container){JButton b=find((Container)child,text);if(b!=null)return b;}
        }return null;
    }
    private static JScrollPane findScroll(Container c) {
        for(Component child:c.getComponents()) {
            if(child instanceof JScrollPane)return (JScrollPane)child;
            if(child instanceof Container){JScrollPane s=findScroll((Container)child);if(s!=null)return s;}
        }return null;
    }
    private static void save(Window w,String name) {
        try {
            BufferedImage image=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();w.paint(g);g.dispose();
            File dir=new File(System.getenv("SYNTHETIC_INTEGRATION_OUT")==null?"target/ui-pass3":System.getenv("SYNTHETIC_INTEGRATION_OUT"));
            dir.mkdirs();ImageIO.write(image,"png",new File(dir,name));
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
