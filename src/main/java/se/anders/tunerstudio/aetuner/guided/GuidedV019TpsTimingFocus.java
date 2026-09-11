package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;

/** v0.19 TPS Movement / Timing driving view bound to real production evidence. */
final class GuidedV019TpsTimingFocus extends GuidedV019FocusBase {
    private final DriverCue cue = new DriverCue();
    private final LatestMovement latest = new LatestMovement();
    private final ReferenceMarkers markers = new ReferenceMarkers();
    private final JLabel liveTps = valueLabel();
    private final JLabel liveRpm = valueLabel();
    private final JLabel liveDelta = valueLabel();
    private final JLabel threshold = smallValue();
    private final JLabel timing = smallValue();
    private final JLabel comparable = smallValue();
    private final JLabel sound = smallValue();

    GuidedV019TpsTimingFocus(JDialog owner, Runnable done) { super(owner, done); build(); }
    String taskTitle() { return "TPS Movement / Timing"; }
    String taskSubtitle() { return "Driver-guided passive capture — visual + one-shot sound cues"; }

    JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8)); root.setOpaque(false);
        cue.setPreferredSize(new Dimension(100, 132)); root.add(cue, BorderLayout.NORTH);
        JPanel main = new JPanel(new GridBagLayout()); main.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints(); gc.gridy=0; gc.fill=GridBagConstraints.BOTH; gc.weighty=1;
        gc.gridx=0; gc.weightx=.34; gc.insets=new Insets(0,0,0,8); main.add(chartCard("How to move the pedal", new InstructionChart()), gc);
        gc.gridx=1; gc.weightx=.49; main.add(chartCard("Latest pedal movement", latest), gc);
        gc.gridx=2; gc.weightx=.17; gc.insets=new Insets(0,0,0,0); main.add(chartCard("Reference / repeats — 1st full, later half", markers), gc);
        root.add(main, BorderLayout.CENTER);
        JPanel lower = new JPanel(new GridLayout(1,2,8,0)); lower.setOpaque(false);
        lower.add(liveTriple("Live context", "TPS", liveTps, "RPM", liveRpm, "TPS AE change", liveDelta));
        lower.add(summaryGrid("Capture context", new String[]{"AccelThreshold","Timing pair","Comparable","Sound cue"}, new JLabel[]{threshold,timing,comparable,sound}));
        lower.setPreferredSize(new Dimension(100,82)); root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    JComponent diagnosticsContent() { return new Diagnostics(); }

    void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        EngagementFocusModel model = state != null && state.recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION ? state.engagement : null;
        if (model == null) model = EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE);
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        cue.update(model, passive); latest.update(model, passive); markers.update(passive);
        liveTps.setText(fmt(model.tps) + " %"); liveRpm.setText(fmt0(model.rpm)); liveDelta.setText(fmt(model.productionDeltaTps));
        threshold.setText(fmt(model.threshold));
        EngagementPassiveCapture.TimingStatus ts = model.timingStatus;
        timing.setText(ts.deltaResolved ? fmt0(ts.deltaWindowMs) + " / " + fmt0(ts.sampleLengthMs) + " ms" : "collecting");
        comparable.setText(passive.comparableEvents + " / " + passive.targetComparable); sound.setText("production cues");
        review.setEnabled(model.captureState == GuidedCaptureState.COMPLETE || passive.complete()); repaint();
    }

    private static final class DriverCue extends JPanel {
        private final JLabel state=new JLabel(), instruction=new JLabel(), sub=new JLabel(), context=new JLabel();
        private final JPanel steps=new JPanel(new GridLayout(1,4,6,0));
        DriverCue(){
            setLayout(new BorderLayout(12,8)); setBackground(AeUiTheme.card()); setBorder(cardBorder());
            JPanel left=new JPanel(new BorderLayout(0,2));left.setOpaque(false);
            state.setFont(new Font("Dialog",Font.BOLD,27)); instruction.setFont(new Font("Dialog",Font.BOLD,17)); instruction.setForeground(AeUiTheme.focusText());
            sub.setFont(BODY);sub.setForeground(AeUiTheme.focusMuted()); left.add(state,BorderLayout.NORTH);left.add(instruction,BorderLayout.CENTER);left.add(sub,BorderLayout.SOUTH);add(left,BorderLayout.CENTER);
            context.setFont(new Font("Dialog",Font.BOLD,11));context.setForeground(AeUiTheme.focusMuted());context.setHorizontalAlignment(SwingConstants.RIGHT);context.setVerticalAlignment(SwingConstants.TOP);add(context,BorderLayout.EAST);
            steps.setOpaque(false); add(steps,BorderLayout.SOUTH);
        }
        void update(EngagementFocusModel m, EngagementPassiveCapture.Snapshot p){
            String title; Color color; int active;
            if(m.captureState==GuidedCaptureState.PAUSED){title="PAUSED";color=AeUiTheme.focusAmber();active=-1;}
            else if(p.moving){title="OPEN NOW";color=AeUiTheme.focusBlue();active=1;}
            else if(p.settling){title="SETTLE";color=AeUiTheme.focusGreen();active=2;}
            else if(p.comparableEvents>0&&!p.complete()){title="RE-ARM / READY";color=AeUiTheme.navy();active=3;}
            else {title="READY";color=AeUiTheme.focusAmber();active=0;}
            state.setText("● "+title); state.setForeground(color);
            String action=m.nextActionText(); String[] lines=action.split("\\n",2); instruction.setText(lines.length>0?lines[0]:action); sub.setText(lines.length>1?lines[1].replace('\n',' '):m.detectorStatusText());
            context.setText("<html><div style='text-align:right'>"+(Double.isFinite(m.rpm)?fmt0(m.rpm)+" RPM":"RPM n/a")+"<br>Evidence "+p.comparableEvents+" / "+p.targetComparable+"<br>Capture writes NONE</div></html>");
            steps.removeAll(); addStep("1","QUIET",active==0,AeUiTheme.focusGreen()); addStep("2","OPEN",active==1,AeUiTheme.focusBlue()); addStep("3","SETTLE",active==2,AeUiTheme.focusGreen()); addStep("4","RE-ARM",active==3,AeUiTheme.navy()); revalidate();repaint();
        }
        private void addStep(String n,String text,boolean active,Color accent){JLabel l=new JLabel(n+"  "+text,SwingConstants.CENTER);l.setFont(new Font("Dialog",Font.BOLD,10));l.setOpaque(true);l.setBackground(active?alpha(accent,35):AeUiTheme.neutralSoft());l.setForeground(active?accent:AeUiTheme.focusMuted());l.setBorder(new LineBorder(active?accent:AeUiTheme.border()));steps.add(l);}
    }

    private static final class InstructionChart extends JPanel {
        InstructionChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(320,260));}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=28,r=getWidth()-18,t=30,b=getHeight()-36,w=r-l;g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<4;i++){int x=l+w*i/3;g2.drawLine(x,t,x,b);}g2.drawLine(l,b,r,b);int[]xs={l,l+w*18/100,l+w*28/100,l+w*38/100,l+w*50/100,l+w*62/100,r};int[]ys={b-18,b-18,b-34,b-95,t+72,t+58,t+58};draw(g2,xs,ys,AeUiTheme.focusBlue(),4f,false);g2.setFont(new Font("Dialog",Font.BOLD,10));g2.setColor(AeUiTheme.focusGreen());g2.drawString("1  QUIET",l,t+6);g2.setColor(AeUiTheme.focusBlue());g2.drawString("2  OPEN",l+w*34/100,t+6);g2.setColor(AeUiTheme.focusGreen());g2.drawString("3  SETTLE",l+w*73/100,t+6);g2.setFont(SMALL);g2.setColor(AeUiTheme.focusMuted());g2.drawString("baseline",l,b-4);g2.drawString("moderate opening",l+w*31/100,b-4);g2.drawString("soft plateau",l+w*72/100,b-4);g2.dispose();}
    }

    private static final class LatestMovement extends JPanel {
        private EngagementFocusModel model; private EngagementPassiveCapture.Snapshot passive;
        LatestMovement(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(500,260));}
        void update(EngagementFocusModel m,EngagementPassiveCapture.Snapshot p){model=m;passive=p;repaint();}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=42,r=getWidth()-16,t=28,b=getHeight()-30;g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<=5;i++){int y=t+(b-t)*i/5;g2.drawLine(l,y,r,y);int x=l+(r-l)*i/5;g2.drawLine(x,t,x,b);}if(model!=null&&Double.isFinite(model.productionDeltaTps)&&Double.isFinite(model.threshold)){double max=Math.max(1.0,Math.max(model.threshold,model.productionDeltaTps)*1.35);int yt=b-(int)(model.threshold/max*(b-t));int ys=b-(int)(Math.max(0,model.productionDeltaTps)/max*(b-t));g2.setColor(AeUiTheme.red());g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{6f,4f},0f));g2.drawLine(l,yt,r,yt);g2.setColor(AeUiTheme.focusBlue());g2.setStroke(new BasicStroke(4f));g2.drawLine(l+20,b,l+20,ys);g2.fillOval(l+14,ys-6,12,12);g2.setFont(new Font("Dialog",Font.BOLD,10));g2.drawString("Fuel: TPS AE change "+fmt(model.productionDeltaTps),l+36,ys+4);g2.setColor(AeUiTheme.red());g2.drawString("AccelThreshold "+fmt(model.threshold),Math.max(l+36,r-155),yt-7);}g2.setColor(AeUiTheme.focusMuted());g2.setFont(SMALL);g2.drawString(trim(passive==null?"Waiting for production movement evidence":passive.lastEvent,70),l,b+20);g2.dispose();}
    }

    private static final class ReferenceMarkers extends JPanel {
        private EngagementPassiveCapture.Snapshot passive;
        ReferenceMarkers(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(190,260));}
        void update(EngagementPassiveCapture.Snapshot p){passive=p;repaint();}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=24,r=getWidth()-14,t=24,b=getHeight()-30;g2.setColor(AeUiTheme.chartGridSoft());g2.drawLine(l,b,r,b);if(passive!=null&&Double.isFinite(passive.referencePeakTps)){double max=Math.max(10.0,passive.referencePeakTps*1.15);int ry=b-(int)(passive.referencePeakTps/max*(b-t));g2.setColor(AeUiTheme.focusGreen());g2.setStroke(new BasicStroke(3f));g2.drawLine(l+18,b,l+18,ry);g2.setFont(new Font("Dialog",Font.BOLD,9));g2.drawString("Ref 1",l+4,Math.max(t+10,ry-4));double[]rs=passive.repeatPeakTps;for(int i=0;i<rs.length&&i<8;i++){int x=l+42+i*Math.max(12,(r-l-50)/8);int y=b-(int)(Math.max(0,rs[i])/max*(b-t));int half=(b+y)/2;g2.setStroke(new BasicStroke(2f));g2.drawLine(x,b,x,half);g2.drawString(String.valueOf(i+2),x-3,Math.max(t+10,half-3));}}else{g2.setColor(AeUiTheme.focusMuted());g2.setFont(SMALL);g2.drawString("first accepted",l+6,(t+b)/2);g2.drawString("movement sets",l+6,(t+b)/2+14);g2.drawString("the reference",l+6,(t+b)/2+28);}g2.dispose();}
    }

    private static final class Diagnostics extends JPanel {
        private final JLabel status=new JLabel(), details=new JLabel(); private final JTextArea reviewArea=new JTextArea(); private final Timer timer;
        Diagnostics(){setLayout(new BorderLayout(0,8));setBackground(AeUiTheme.focusBackground());setBorder(new EmptyBorder(8,10,8,10));JPanel h=new JPanel(new BorderLayout());h.setOpaque(false);h.add(label("TPS Movement / Timing Diagnostics",22,Font.BOLD,AeUiTheme.focusText()),BorderLayout.NORTH);h.add(label("Production timing evidence and driver-cue state for the current run.",11,Font.PLAIN,AeUiTheme.focusMuted()),BorderLayout.SOUTH);add(h,BorderLayout.NORTH);JPanel body=new JPanel(new GridLayout(1,2,8,0));body.setOpaque(false);JPanel left=card(new BorderLayout(0,8));left.setBorder(cardBorder());status.setFont(new Font("Dialog",Font.BOLD,17));status.setForeground(AeUiTheme.focusBlue());left.add(status,BorderLayout.NORTH);left.add(details,BorderLayout.CENTER);body.add(left);reviewArea.setEditable(false);reviewArea.setLineWrap(true);reviewArea.setWrapStyleWord(true);reviewArea.setFont(new Font("Dialog",Font.PLAIN,11));reviewArea.setForeground(AeUiTheme.focusText());reviewArea.setBackground(AeUiTheme.card());JScrollPane rs=new JScrollPane(reviewArea);rs.setBorder(new LineBorder(AeUiTheme.border()));body.add(rs);add(body,BorderLayout.CENTER);JButton close=button("Close");close.addActionListener(e->{Window w=SwingUtilities.getWindowAncestor(this);if(w!=null)w.dispose();});JPanel f=new JPanel(new FlowLayout(FlowLayout.RIGHT));f.setOpaque(false);f.add(close);add(f,BorderLayout.SOUTH);timer=new Timer(200,e->refresh());addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)==0)return;if(isShowing()){refresh();timer.start();}else timer.stop();});refresh();}
        private void refresh(){GuidedFocusHub.State s=GuidedFocusHub.snapshot();EngagementFocusModel m=s!=null&&s.engagement!=null?s.engagement:EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE);EngagementPassiveCapture.Snapshot p=EngagementPassiveCapture.snapshot();status.setText(m.detectorStatusText());details.setText("<html><table cellpadding='4'><tr><td>Detector</td><td><b>"+(m.workingModel==null?"unknown":m.workingModel.displayName())+"</b></td></tr><tr><td>Fuel: TPS AE change</td><td><b>"+fmt(m.productionDeltaTps)+"</b></td></tr><tr><td>AccelThreshold</td><td><b>"+fmt(m.threshold)+"</b></td></tr><tr><td>Delta Window</td><td><b>"+fmt0(m.timingStatus.currentDeltaWindowMs)+" ms</b></td></tr><tr><td>Sample Length</td><td><b>"+fmt0(m.timingStatus.currentSampleLengthMs)+" ms</b></td></tr><tr><td>Comparable</td><td><b>"+p.comparableEvents+" / "+p.targetComparable+"</b></td></tr><tr><td>Rejected</td><td><b>"+(p.rejectedSmall+p.rejectedLarge+p.rejectedDuration)+"</b></td></tr><tr><td>RPM span</td><td><b>"+fmt0(p.roadRpmSpan)+" RPM</b></td></tr></table></html>");String text=EngagementPassiveCapture.reviewText(null);if(!reviewArea.getText().equals(text)){reviewArea.setText(text);reviewArea.setCaretPosition(0);}}
    }
}