package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

/** v0.19 Threshold / Sensitivity driving view over the production threshold learner. */
final class GuidedV019ThresholdFocus extends GuidedV019FocusBase {
    private final DriverCue cue = new DriverCue();
    private final ActivityChart activity = new ActivityChart();
    private final JLabel liveTps=valueLabel(), liveRpm=valueLabel(), liveDelta=valueLabel();
    private final JLabel effectiveThreshold=smallValue(), quiet=smallValue(), accel=smallValue(), normal=smallValue();

    GuidedV019ThresholdFocus(JDialog owner, Runnable done){super(owner,done);build();}
    String taskTitle(){return "Threshold / Sensitivity";}
    String taskSubtitle(){return "Driver-guided passive threshold learning — visual + one-shot sound cues";}

    JComponent drivingContent(){
        JPanel root=new JPanel(new BorderLayout(8,8));root.setOpaque(false);
        cue.setPreferredSize(new Dimension(100,132));root.add(cue,BorderLayout.NORTH);
        JPanel main=new JPanel(new GridBagLayout());main.setOpaque(false);
        GridBagConstraints gc=new GridBagConstraints();gc.gridy=0;gc.fill=GridBagConstraints.BOTH;gc.weighty=1;
        gc.gridx=0;gc.weightx=.40;gc.insets=new Insets(0,0,0,8);main.add(chartCard("What counts as useful evidence",new InstructionChart()),gc);
        gc.gridx=1;gc.weightx=.60;gc.insets=new Insets(0,0,0,0);main.add(chartCard("Latest detector activity",activity),gc);
        root.add(main,BorderLayout.CENTER);
        JPanel lower=new JPanel(new GridLayout(1,2,8,0));lower.setOpaque(false);
        lower.add(liveTriple("Live context","TPS",liveTps,"RPM",liveRpm,"TPS AE change",liveDelta));
        lower.add(summaryGrid("Evidence status",new String[]{"Effective AccelThreshold","Quiet calibration","Acceleration Openings","Normal Corrections"}, new JLabel[]{effectiveThreshold,quiet,accel,normal}));
        lower.setPreferredSize(new Dimension(100,82));root.add(lower,BorderLayout.SOUTH);return root;
    }

    JComponent diagnosticsContent(){return new Diagnostics();}

    void refreshFromProduction(){
        FoundationThresholdFocusModel m=model(); cue.update(m); activity.update(m);
        if(m==null){liveTps.setText("n/a");liveRpm.setText("n/a");liveDelta.setText("n/a");effectiveThreshold.setText("n/a");quiet.setText("waiting");accel.setText("0");normal.setText("0");review.setEnabled(false);return;}
        liveTps.setText(fmt(m.liveTps)+" %");liveRpm.setText(fmt0(m.liveRpm));liveDelta.setText(fmt(m.liveDelta));effectiveThreshold.setText(fmt(m.liveThreshold));
        quiet.setText(m.quietSamples+" / "+m.quietTarget+" • "+fmt(m.quietDurationSeconds)+" s");
        int a=0,n=0;for(FoundationThresholdFocusModel.Bin b:m.bins){a+=b.accelerationOpeningEvents;n+=b.normalCorrectionEvents;}accel.setText(String.valueOf(a));normal.setText(String.valueOf(n));
        review.setEnabled("COMPLETE".equals(m.captureState));repaint();
    }

    private static FoundationThresholdFocusModel model(){GuidedFocusHub.State s=GuidedFocusHub.snapshot();return s!=null&&s.recipe==GuidedTuningRecipe.FOUNDATION_THRESHOLD?s.foundationThreshold:null;}

    private static final class DriverCue extends JPanel{
        final JLabel state=new JLabel(),instruction=new JLabel(),sub=new JLabel(),context=new JLabel();final JPanel steps=new JPanel(new GridLayout(1,4,6,0));
        DriverCue(){setLayout(new BorderLayout(12,8));setBackground(AeUiTheme.card());setBorder(cardBorder());JPanel left=new JPanel(new BorderLayout(0,2));left.setOpaque(false);state.setFont(new Font("Dialog",Font.BOLD,27));instruction.setFont(new Font("Dialog",Font.BOLD,17));instruction.setForeground(AeUiTheme.focusText());sub.setFont(BODY);sub.setForeground(AeUiTheme.focusMuted());left.add(state,BorderLayout.NORTH);left.add(instruction,BorderLayout.CENTER);left.add(sub,BorderLayout.SOUTH);add(left,BorderLayout.CENTER);context.setFont(new Font("Dialog",Font.BOLD,11));context.setForeground(AeUiTheme.focusMuted());context.setHorizontalAlignment(SwingConstants.RIGHT);context.setVerticalAlignment(SwingConstants.TOP);add(context,BorderLayout.EAST);steps.setOpaque(false);add(steps,BorderLayout.SOUTH);}
        void update(FoundationThresholdFocusModel m){
            String title="READ WORKING TUNE",action="Load the production threshold context before capture.",detail="No threshold evidence loaded.";Color color=AeUiTheme.focusAmber();int active=0;String region="RPM n/a";int ae=0,nc=0;
            if(m!=null){action=m.driverInstruction();detail=m.recommendationStatus();region=m.liveBin()==null?(Double.isFinite(m.liveRpm)?fmt0(m.liveRpm)+" RPM":"RPM n/a"):m.liveBin().regionLabel;for(FoundationThresholdFocusModel.Bin b:m.bins){ae+=b.accelerationOpeningEvents;nc+=b.normalCorrectionEvents;}
                if("PAUSED".equals(m.captureState)){title="PAUSED";color=AeUiTheme.focusAmber();active=-1;}
                else if(!m.calibrationFrozen){title="DRIVE NORMALLY";color=AeUiTheme.focusBlue();active=0;}
                else if(Double.isFinite(m.liveRatio)&&m.liveRatio>=1.0){title="ACCELERATION OPENING";color=AeUiTheme.focusGreen();active=2;}
                else {FoundationThresholdFocusModel.Bin b=m.liveBin();if(b!=null&&"ACCELERATION OPENING".equals(b.requestedClassLabel())){title="MAKE ONE NORMAL OPENING";color=AeUiTheme.focusAmber();active=1;}else{title="KEEP DRIVING";color=AeUiTheme.navy();active=3;}}
            }
            state.setText("● "+title);state.setForeground(color);instruction.setText("<html>"+escape(action)+"</html>");sub.setText("<html>"+escape(detail)+"</html>");context.setText("<html><div style='text-align:right'>"+region+"<br>Accel openings "+ae+"<br>Normal corrections "+nc+"<br>Capture writes NONE</div></html>");steps.removeAll();addStep("1","DRIVE",active==0,AeUiTheme.focusBlue());addStep("2","OPEN",active==1,AeUiTheme.focusAmber());addStep("3","DETECTED",active==2,AeUiTheme.focusGreen());addStep("4","BUILD",active==3,AeUiTheme.navy());revalidate();repaint();
        }
        void addStep(String n,String text,boolean on,Color accent){JLabel l=new JLabel(n+"  "+text,SwingConstants.CENTER);l.setFont(new Font("Dialog",Font.BOLD,10));l.setOpaque(true);l.setBackground(on?alpha(accent,35):AeUiTheme.neutralSoft());l.setForeground(on?accent:AeUiTheme.focusMuted());l.setBorder(new LineBorder(on?accent:AeUiTheme.border()));steps.add(l);}
    }

    private static final class InstructionChart extends JPanel{
        InstructionChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(390,260));}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=35,r=getWidth()-18,t=28,b=getHeight()-34;g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<5;i++){int y=t+(b-t)*i/4;g2.drawLine(l,y,r,y);}int th=t+(b-t)*42/100;g2.setColor(AeUiTheme.thresholdFill());g2.fillRect(l,th+22,r-l,b-th-22);g2.setColor(AeUiTheme.red());g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{7,5},0));g2.drawLine(l,th,r,th);int[]xs={l,l+45,l+80,l+115,l+150,l+185,l+220,l+255,l+290,r};int[]ys={b-22,b-28,b-18,b-26,b-22,t+42,b-24,b-20,b-29,b-21};draw(g2,xs,ys,AeUiTheme.focusBlue(),3f,false);g2.setFont(new Font("Dialog",Font.BOLD,10));g2.setColor(AeUiTheme.focusMuted());g2.drawString("NORMAL CORRECTION — useful",l+12,b-45);g2.setColor(AeUiTheme.red());g2.drawString("EFFECTIVE AccelThreshold",Math.max(l+10,r-130),th-7);g2.setColor(AeUiTheme.focusGreen());g2.drawString("ACCELERATION OPENING",l+165,t+28);g2.setFont(SMALL);g2.setColor(AeUiTheme.focusMuted());g2.drawString("stays below threshold",l+12,b-31);g2.drawString("crosses clearly",l+165,t+42);g2.dispose();}
    }

    private static final class ActivityChart extends JPanel{
        private FoundationThresholdFocusModel m;ActivityChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(560,260));}void update(FoundationThresholdFocusModel m){this.m=m;repaint();}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=42,r=getWidth()-16,t=28,b=getHeight()-30;g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<=5;i++){int y=t+(b-t)*i/5;g2.drawLine(l,y,r,y);int x=l+(r-l)*i/5;g2.drawLine(x,t,x,b);}if(m==null||!Double.isFinite(m.liveThreshold)){g2.setColor(AeUiTheme.focusMuted());g2.drawString("Waiting for production detector data",l+15,(t+b)/2);g2.dispose();return;}double sig=Double.isFinite(m.liveDelta)?Math.max(0,m.liveDelta):0,thr=Math.max(.001,m.liveThreshold),max=Math.max(thr,sig)*1.45;int yt=b-(int)(thr/max*(b-t));int ys=b-(int)(sig/max*(b-t));g2.setColor(AeUiTheme.thresholdFill());g2.fillRect(l,yt+12,r-l,b-yt-12);g2.setColor(AeUiTheme.red());g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{6,5},0));g2.drawLine(l,yt,r,yt);g2.setColor(sig>=thr?AeUiTheme.focusGreen():AeUiTheme.focusBlue());g2.setStroke(new BasicStroke(7f));g2.drawLine(l+(r-l)/2,b,l+(r-l)/2,ys);g2.fillOval(l+(r-l)/2-6,ys-6,12,12);g2.setFont(new Font("Dialog",Font.BOLD,10));g2.drawString("Fuel: TPS AE change "+fmt(m.liveDelta),l+10,t+18);g2.setColor(AeUiTheme.red());g2.drawString("AccelThreshold "+fmt(m.liveThreshold),r-150,yt-7);FoundationThresholdFocusModel.Bin bin=m.liveBin();if(bin!=null){g2.setColor(AeUiTheme.focusMuted());g2.drawString(bin.regionLabel+" • "+bin.requestedClassLabel(),l+10,b-8);}g2.dispose();}
    }

    private static final class Diagnostics extends JPanel{
        private final JTable table=new JTable();private final JTextArea reviewArea=new JTextArea();private final JLabel status=label("Waiting",17,Font.BOLD,AeUiTheme.focusBlue());private final Timer timer;
        Diagnostics(){setLayout(new BorderLayout(0,8));setBackground(AeUiTheme.focusBackground());setBorder(new EmptyBorder(8,10,8,10));JPanel h=new JPanel(new BorderLayout());h.setOpaque(false);h.add(label("Threshold / Sensitivity Diagnostics",22,Font.BOLD,AeUiTheme.focusText()),BorderLayout.NORTH);h.add(label("Normal Correction / Acceleration Opening separation and real production semantics.",11,Font.PLAIN,AeUiTheme.focusMuted()),BorderLayout.SOUTH);add(h,BorderLayout.NORTH);JPanel center=new JPanel(new BorderLayout(8,8));center.setOpaque(false);JPanel top=new JPanel(new GridLayout(1,2,8,0));top.setOpaque(false);top.add(chartCard("Current detector activity",new ActivityChartProxy()));top.add(infoCard());center.add(top,BorderLayout.NORTH);center.add(themedTableScroll(table),BorderLayout.CENTER);reviewArea.setEditable(false);reviewArea.setLineWrap(true);reviewArea.setWrapStyleWord(true);reviewArea.setBackground(AeUiTheme.card());reviewArea.setForeground(AeUiTheme.focusText());reviewArea.setFont(new Font("Dialog",Font.PLAIN,10));JScrollPane rs=new JScrollPane(reviewArea);rs.setPreferredSize(new Dimension(100,110));center.add(rs,BorderLayout.SOUTH);add(center,BorderLayout.CENTER);timer=new Timer(200,e->refresh());addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)==0)return;if(isShowing()){refresh();timer.start();}else timer.stop();});refresh();}
        private JComponent infoCard(){JPanel p=card(new BorderLayout(0,5));p.setBorder(cardBorder());p.add(label("Algorithm / Evidence Details",13,Font.BOLD,AeUiTheme.focusText()),BorderLayout.NORTH);p.add(status,BorderLayout.CENTER);return p;}
        private void refresh(){FoundationThresholdFocusModel m=model();if(m==null){table.setModel(new DefaultTableModel(new Object[0][0],new String[]{"RPM","State"}));status.setText("Waiting for production threshold state");return;}status.setText(m.recommendationStatus());String[]cols={"RPM region","Current","Normal Corr.","Accel Opening","P95","P25","Gap","Proposed","State"};Object[][]rows=new Object[m.bins.size()][cols.length];for(int i=0;i<m.bins.size();i++){FoundationThresholdFocusModel.Bin b=m.bins.get(i);rows[i]=new Object[]{b.regionLabel,fmt(b.currentThreshold),b.normalCorrectionEvents,b.accelerationOpeningEvents,fmt(b.normalCorrectionP95),fmt(b.accelerationOpeningP25),fmt(b.separationGap),b.changed?fmt(b.proposedThreshold):"—",b.status};}table.setModel(new DefaultTableModel(rows,cols){public boolean isCellEditable(int r,int c){return false;}});styleTable(table);String tx=m.reviewText==null?"":m.reviewText;if(!reviewArea.getText().equals(tx)){reviewArea.setText(tx);reviewArea.setCaretPosition(0);}}
        private static final class ActivityChartProxy extends JPanel{ActivityChartProxy(){setBackground(AeUiTheme.card());}protected void paintComponent(Graphics g){super.paintComponent(g);FoundationThresholdFocusModel m=model();Graphics2D g2=(Graphics2D)g.create();int l=30,r=getWidth()-15,t=20,b=getHeight()-24;g2.setColor(AeUiTheme.chartGridSoft());g2.drawRect(l,t,r-l,b-t);if(m!=null&&Double.isFinite(m.liveThreshold)){double sig=Double.isFinite(m.liveDelta)?Math.max(0,m.liveDelta):0,thr=Math.max(.001,m.liveThreshold),max=Math.max(sig,thr)*1.5;int yt=b-(int)(thr/max*(b-t));int ys=b-(int)(sig/max*(b-t));g2.setColor(AeUiTheme.red());g2.drawLine(l,yt,r,yt);g2.setColor(sig>=thr?AeUiTheme.focusGreen():AeUiTheme.focusBlue());g2.fillRect(l+40,ys,20,b-ys);}g2.dispose();}}
    }

    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}