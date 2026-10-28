package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusBridge;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.Collections;

/** Reproductions of the four runtime findings from the general UI audit. */
public final class GuidedAuditFindingsRegressionTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                for(AeUiTheme.Side side:AeUiTheme.Side.values()) {
                    AeUiTheme.set(side);
                    translucentStepIsReadable();
                    missingLiveDataOverridesThresholdCoach();
                    for(Dimension size:new Dimension[]{new Dimension(280,90),new Dimension(420,140),new Dimension(680,260)}) {
                        thresholdExampleCrosses(size);
                        blendMapLaneIsVisible(size);
                    }
                }
            } catch(Exception e){throw new RuntimeException(e);}
            finally {GuidedFocusHub.clear();GuidedFocusHub.setCaptureControl(null);AeUiTheme.set(AeUiTheme.Side.LIGHT_SIDE);}
        });
        System.out.println("GuidedAuditFindingsRegressionTest passed");
    }
    private static void translucentStepIsReadable() {
        JPanel parent=new JPanel(null);parent.setBackground(AeUiTheme.card());parent.setSize(160,40);
        Color accent=AeUiTheme.focusBlue();
        JLabel label=new JLabel("1 DRIVE");label.setOpaque(true);
        label.setBackground(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),35));
        label.setForeground(accent);label.setBounds(0,0,160,40);parent.add(label);
        GuidedUiTheme.apply(parent);
        // Read a rendered background pixel, independently of the contrast helper's background calculation.
        Color pixel=new Color(paint(parent).getRGB(150,35));
        require(GuidedUiTheme.contrast(label.getForeground(),pixel)>=4.5,"active step unreadable over composited paint: side="+AeUiTheme.side()+" fg="+label.getForeground()+" pixel="+pixel+" expected="+GuidedUiTheme.background(label)+" contrast="+GuidedUiTheme.contrast(label.getForeground(),pixel));
        AeUiTheme.toggle();GuidedUiTheme.apply(parent);
        pixel=new Color(paint(parent).getRGB(150,35));
        require(GuidedUiTheme.contrast(label.getForeground(),pixel)>=4.5,"theme change lost translucent step contrast");
        AeUiTheme.toggle();
    }
    private static void missingLiveDataOverridesThresholdCoach() {
        Control control=new Control();GuidedFocusHub.setCaptureControl(control);
        FoundationThresholdFocusBridge.replaceEvidence(null,Collections.emptyList());
        publish(control.state);
        GuidedV019ThresholdFocus view=new GuidedV019ThresholdFocus(null,null);
        String text=text(view);
        require(text.contains("● WAITING FOR LIVE DATA")&&!text.contains("DRIVE NORMALLY")
                &&!text.contains("HOLD THE PEDAL"),"missing data still requests a maneuver");
        control.live=true;view.refreshFromProduction();
        require(text(view).contains("● DRIVE NORMALLY"),"coach did not recover when live data returned");
        control.live=false;view.refreshFromProduction();
        require(text(view).contains("● WAITING FOR LIVE DATA"),"lost data did not suspend coach");
        control.state=GuidedCaptureState.PAUSED;publish(control.state);view.refreshFromProduction();
        require(text(view).contains("● PAUSED"),"live-data wait overrode explicit pause");
        GuidedFocusHub.setCaptureControl(null);
    }
    private static void publish(GuidedCaptureState state) {
        GuidedFocusHub.publish(GuidedTuningRecipe.FOUNDATION_THRESHOLD,state,
                (se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel)null,"UI regression");
    }
    private static void thresholdExampleCrosses(Dimension size)throws Exception {
        JComponent chart=chart("GuidedV019ThresholdFocus$InstructionChart");chart.setSize(size);
        BufferedImage image=paint(chart);
        int line=0,maxRed=0;
        for(int y=18;y<size.height-34;y++) {
            int red=colored(image,AeUiTheme.red(),y,y+1);
            if(red>maxRed){maxRed=red;line=y;}
        }
        require(maxRed>size.width/5,"threshold line missing");
        require(colored(image,AeUiTheme.focusBlue(),18,line,size.width/2,size.width*3/4)>0,
                "opening example never crosses threshold at "+size);
        require(colored(image,AeUiTheme.focusBlue(),line+3,size.height-34,6,size.width/4)>0,
                "normal correction is not below threshold at "+size);
    }
    private static void blendMapLaneIsVisible(Dimension size)throws Exception {
        JComponent chart=chart("GuidedV019BlendDurationFocus$InstructionChart");chart.setSize(size);
        Class<?> area=Class.forName("se.anders.tunerstudio.aetuner.guided.GuidedV019BlendDurationFocus$PlotArea");
        Constructor<?> ctor=area.getDeclaredConstructor(int.class,int.class);ctor.setAccessible(true);
        Object bounds=ctor.newInstance(size.width,size.height);
        int top=number(bounds,"mapTop"),bottom=number(bounds,"mapBottom");
        require(top<bottom&&bottom<size.height-20,"MAP lane/legend extends beyond chart at "+size);
        require(colored(paint(chart),AeUiTheme.focusGreen(),top,bottom+1)>0,"physical MAP trace not visible at "+size);
    }
    private static int number(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
    private static JComponent chart(String name)throws Exception {
        Constructor<?> c=Class.forName("se.anders.tunerstudio.aetuner.guided."+name).getDeclaredConstructor();
        c.setAccessible(true);return (JComponent)c.newInstance();
    }
    private static BufferedImage paint(JComponent c) {
        BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();c.paint(g);g.dispose();return image;
    }
    private static int colored(BufferedImage image,Color color,int top,int bottom) {
        return colored(image,color,top,bottom,6,image.getWidth()-6);
    }
    private static int colored(BufferedImage image,Color color,int top,int bottom,int left,int right) {
        int count=0;
        for(int y=Math.max(0,top);y<Math.min(image.getHeight(),bottom);y++)for(int x=left;x<right;x++) {
            Color p=new Color(image.getRGB(x,y));
            if(Math.abs(p.getRed()-color.getRed())+Math.abs(p.getGreen()-color.getGreen())+Math.abs(p.getBlue()-color.getBlue())<35)count++;
        }return count;
    }
    private static String text(Container c) {
        StringBuilder s=new StringBuilder();for(Component child:c.getComponents()) {
            if(child instanceof JLabel)s.append(((JLabel)child).getText()).append('\n');
            if(child instanceof Container)s.append(text((Container)child));
        }return s.toString();
    }
    private static final class Control implements GuidedFocusHub.CaptureControl {
        GuidedCaptureState state=GuidedCaptureState.CAPTURING;boolean live;
        public GuidedCaptureState captureState(){return state;}
        public GuidedTuningRecipe activeRecipe(){return GuidedTuningRecipe.FOUNDATION_THRESHOLD;}
        public boolean liveDataReady(){return live;}
        public boolean reviewReady(){return false;}
        public boolean canContinueCapture(){return false;}
        public boolean canExportEvidence(){return false;}
        public void exportEvidence(){}
        public GuidedCaptureState finishCapture(){return state;}
        public GuidedCaptureState togglePause(){return state;}
        public GuidedCaptureState continueCapture(){return state;}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
