package se.anders.tunerstudio.aetuner.guided;

import javax.swing.*;
import java.awt.*;

/** Window-local sizing/state tools; never iconify or resize the TunerStudio owner. */
public final class GuidedWindowTools {
    private GuidedWindowTools() { }
    static Rectangle usable(Window window) {
        GraphicsConfiguration gc=window.getGraphicsConfiguration();
        if(gc==null)gc=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds=new Rectangle(gc.getBounds());
        Insets in=Toolkit.getDefaultToolkit().getScreenInsets(gc);
        bounds.x+=in.left;bounds.y+=in.top;bounds.width-=in.left+in.right;bounds.height-=in.top+in.bottom;
        return bounds;
    }
    static void fit(Window window) { fit(window,usable(window)); }
    static void fit(Window window,Rectangle area) {
        Dimension min=window.getMinimumSize();
        window.setMinimumSize(new Dimension(Math.min(min.width,area.width),Math.min(min.height,area.height)));
        int w=Math.min(window.getWidth(),area.width),h=Math.min(window.getHeight(),area.height);
        window.setBounds(Math.max(area.x,Math.min(window.getX(),area.x+area.width-w)),
                Math.max(area.y,Math.min(window.getY(),area.y+area.height-h)),w,h);
    }
    public static JFrame frame(Window host,String title) {
        return frame(host,title,true);
    }
    static JFrame frame(Window host,String title,boolean scrollBody) {
        JFrame frame=host==null?new JFrame(title):new JFrame(title,host.getGraphicsConfiguration());frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        // Request OS decorations even when the host enables Swing-decorated frames.
        frame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        frame.setUndecorated(false);
        frame.setType(Window.Type.NORMAL);
        frame.setResizable(true);GuidedDialogLifecycle.ownFrame(frame);
        GuidedTaskControls.installFrame(frame,scrollBody);return frame;
    }
}
