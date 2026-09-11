package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

/** v0.19 Blend Duration driving view backed only by measured production response state. */
final class GuidedV019BlendDurationFocus extends GuidedV019FocusBase {
    private final DriverCue cue=new DriverCue();
    private final ResponseChart response=new ResponseChart();
    private final ReferenceChart reference=new ReferenceChart();
    private final JLabel liveTps=valueLabel(),liveRpm=valueLabel(),liveMap=valueLabel();
    private final JLabel rpmRegion=smallValue(),currentBlend=smallValue(),accepted=smallValue(),authority=smallValue();

    GuidedV019BlendDurationFocus(JDialog owner,Runnable done){super(owner,done);build();}
    String taskTitle(){return "Blend Duration";}
    String taskSubtitle(){return "Driver-guided manifold response capture — visual + one-shot sound cues";}

    JComponent drivingContent(){
        JPanel root=new JPanel(new BorderLayout(8,8));root.setOpaque(false);cue.setPreferredSize(new Dimension(100,142));root.add(cue,BorderLayout.NORTH);
        JPanel main=new JPanel(new GridBagLayout());main.setOpaque(false);GridBagConstraints gc=new GridBagConstraints();gc.gridy=0;gc.fill=GridBagConstraints.BOTH;gc.weighty=1;
        gc.gridx=0;gc.weightx=.34;gc.insets=new Insets(0,0,0,8);main.add(chartCard("How the event is collected",new InstructionChart()),gc);
        gc.gridx=1;gc.weightx=.49;main.add(chartCard("Latest manifold response",response),gc);
        gc.gridx=2;gc.weightx=.17;gc.insets=new Insets(0,0,0,0);main.add(chartCard("ΔMAP reference / repeats",reference),gc);root.add(main,BorderLayout.CENTER);
        JPanel lower=new JPanel(new GridLayout(1,2,8,0));lower.setOpaque(false);lower.add(liveTriple("Live context","TPS",liveTps,"RPM",liveRpm,"MAP",liveMap));lower.add(summaryGrid("Capture context",new String[]{"RPM region","Current Blend","Accepted","Authority"},new JLabel[]{rpmRegion,currentBlend,accepted,authority}));lower.setPreferredSize(new Dimension(100,82));root.add(lower,BorderLayout.SOUTH);return root;
    }

    JComponent diagnosticsContent(){return new Diagnostics();}

    void refreshFromProduction(){BlendDurationFocusModel m=model();cue.update(m);response.update(m);reference.update(m);if(m==null){liveTps.setText("n/a");liveRpm.setText("n/a");liveMap.setText("n/a");rpmRegion.setText("waiting");currentBlend.setText("n/a");accepted.setText("0");authority.setText("Measurement only");review.setEnabled(false);return;}liveTps.setText(fmt(m.liveTps)+" %");liveRpm.setText(fmt0(m.liveRpm));liveMap.setText(fmt(m.liveMap)+" kPa");rpmRegion.setText(Double.isFinite(m.targetRpm)?"~"+fmt0(m.targetRpm)+" RPM":"automatic");currentBlend.setText(Double.isFinite(m.currentBlendDuration)?String.format(java.util.Locale.ROOT,"%.3f s",m.currentBlendDuration):"n/a");accepted.setText(m.matchingEvents+" / "+m.targetEvents);authority.setText("Measurement only");review.setEnabled(m.captureState==GuidedCaptureState.COMPLETE);repaint();}
    private static BlendDurationFocusModel model(){GuidedFocusHub.State s=GuidedFocusHub.snapshot();return s!=null&&s.recipe==GuidedTuningRecipe.BLEND_DURATION?s.blendDuration:null;}

    private static final class DriverCue extends JPanel{
        private final JLabel state=new JLabel(),instruction=new JLabel(),sub=new JLabel(),context=new JLabel();private final JPanel steps=new JPanel(new GridLayout(1,5,6,0));
        DriverCue(){setLayout(new BorderLayout(12,8));setBackground(AeUiTheme.card());setBorder(cardBorder());JPanel left=new JPanel(new BorderLayout(0,2));left.setOpaque(false);state.setFont(new Font("Dialog",Font.BOLD,27));instruction.setFont(new Font("Dialog",Font.BOLD,17));instruction.setForeground(AeUiTheme.focusText());sub.setFont(BODY);sub.setForeground(AeUiTheme.focusMuted());left.add(state,BorderLayout.NORTH);left.add(instruction,BorderLayout.CENTER);left.add(sub,BorderLayout.SOUTH);add(left,BorderLayout.CENTER);context.setFont(new Font("Dialog",Font.BOLD,11));context.setForeground(AeUiTheme.focusMuted());context.setHorizontalAlignment(SwingConstants.RIGHT);context.setVerticalAlignment(SwingConstants.TOP);add(context,BorderLayout.EAST);steps.setOpaque(false);add(steps,BorderLayout.SOUTH);}
        void update(BlendDurationFocusModel m){String title="WAIT FOR BASELINE",inst="Acquire a steady local starting point before the opening.",subtext="No exact MAP, TPS or RPM target is required.";Color color=AeUiTheme.focusAmber();int active=0;String ctx="Waiting for production Blend state";if(m!=null){inst=m.instruction;subtext=m.status;switch(m.phase){case OPEN_AND_SETTLE:title=Double.isFinite(m.predictionTarget)?"MAP RESPONSE":"OPEN NOW";color=Double.isFinite(m.predictionTarget)?AeUiTheme.purple():AeUiTheme.focusBlue();active=Double.isFinite(m.predictionTarget)?2:1;break;case HOLD_FOR_MAP:title="SOFT PLATEAU";color=AeUiTheme.focusGreen();active=3;break;case RESULT:case COMPLETE:title="RE-ARM / REVIEW";color=AeUiTheme.navy();active=4;break;case PAUSED:title="PAUSED";color=AeUiTheme.focusAmber();active=-1;break;case GET_STEADY:title=m.allValidEvents>0?"RE-ARM":"WAIT FOR BASELINE";color=m.allValidEvents>0?AeUiTheme.navy():AeUiTheme.focusAmber();active=m.allValidEvents>0?4:0;break;default:break;}ctx="<html><div style='text-align:right'>"+(Double.isFinite(m.liveRpm)?fmt0(m.liveRpm)+" RPM":"RPM n/a")+"<br>Accepted "+m.matchingEvents+" / "+m.targetEvents+"<br>Latest "+(m.lastResult.length()==0?"waiting":m.lastResult)+"<br>Capture writes NONE</div></html>";}state.setText("● "+title);state.setForeground(color);instruction.setText("<html>"+escape(inst)+"</html>");sub.setText("<html>"+escape(subtext)+"</html>");context.setText(ctx);steps.removeAll();addStep("1","BASELINE",active==0,AeUiTheme.focusAmber());addStep("2","OPEN",active==1,AeUiTheme.focusBlue());addStep("3","MAP RESPONSE",active==2,AeUiTheme.purple());addStep("4","PLATEAU",active==3,AeUiTheme.focusGreen());addStep("5","RE-ARM",active==4,AeUiTheme.navy());revalidate();repaint();}
        void addStep(String n,String text,boolean on,Color accent){JLabel l=new JLabel(n+"  "+text,SwingConstants.CENTER);l.setFont(new Font("Dialog",Font.BOLD,9));l.setOpaque(true);l.setBackground(on?alpha(accent,35):AeUiTheme.neutralSoft());l.setForeground(on?accent:AeUiTheme.focusMuted());l.setBorder(new LineBorder(on?accent:AeUiTheme.border()));steps.add(l);}
    }

    private static final class InstructionChart extends JPanel{
        InstructionChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(390,260));}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=42,r=getWidth()-18,t=30,b=getHeight()-34;int laneTop=t,laneBottom=t+28,graphTop=laneBottom+18,mid=(graphTop+b)/2;int x1=l+(r-l)*22/100,x2=l+(r-l)*40/100,x3=l+(r-l)*72/100;Color[]fills={AeUiTheme.softAmber(),AeUiTheme.softBlue(),AeUiTheme.softPurple(),AeUiTheme.softGreen()};Color[]acc={AeUiTheme.focusAmber(),AeUiTheme.focusBlue(),AeUiTheme.purple(),AeUiTheme.focusGreen()};String[]names={"1  BASELINE","2  OPEN","3  MAP RESPONSE","4  PLATEAU"};int[]ls={l,x1,x2,x3},rs={x1,x2,x3,r};g2.setFont(new Font("Dialog",Font.BOLD,8));for(int i=0;i<4;i++){g2.setColor(fills[i]);g2.fillRect(ls[i],laneTop,rs[i]-ls[i],laneBottom-laneTop);g2.setColor(acc[i]);g2.drawRect(ls[i],laneTop,rs[i]-ls[i],laneBottom-laneTop);g2.drawString(names[i],ls[i]+5,laneTop+18);}g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<=5;i++){int x=l+(r-l)*i/5;g2.drawLine(x,graphTop,x,b);}g2.drawLine(l,mid,r,mid);g2.setFont(new Font("Dialog",Font.BOLD,9));g2.setColor(AeUiTheme.focusMuted());g2.drawString("PEDAL / TPS",l,graphTop+12);g2.drawString("MAP",l,mid+13);int[]tx={l,x1-8,x1+8,x2,x3,r};int[]ty={mid-18,mid-18,graphTop+35,graphTop+35,graphTop+36,graphTop+36};draw(g2,tx,ty,AeUiTheme.focusBlue(),3f,false);int[]mx={l,x1,x2,x2+35,x3,r};int[]my={b-18,b-18,b-16,mid+35,mid+23,mid+22};draw(g2,mx,my,AeUiTheme.focusGreen(),3f,false);g2.setColor(AeUiTheme.purple());g2.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{5,4},0));g2.drawLine(x2,graphTop,x2,b);g2.setFont(new Font("Dialog",Font.BOLD,9));g2.drawString("PEDAL STOPS",x2+5,graphTop+52);g2.setFont(SMALL);g2.setColor(AeUiTheme.focusMuted());g2.drawString("MAP keeps moving after pedal motion ends",Math.max(l+5,x2-70),b-6);g2.dispose();}
    }

    private static final class ResponseChart extends JPanel{
        private BlendDurationFocusModel m;ResponseChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(560,260));}void update(BlendDurationFocusModel m){this.m=m;repaint();}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=48,r=getWidth()-18,t=28,b=getHeight()-38;g2.setColor(AeUiTheme.chartGridSoft());for(int i=0;i<=5;i++){int y=t+(b-t)*i/5;g2.drawLine(l,y,r,y);}g2.setFont(new Font("Dialog",Font.BOLD,10));g2.setColor(AeUiTheme.focusMuted());g2.drawString("CURRENT MAP STATE",l,t-8);if(m==null){g2.drawString("Waiting for production manifold response",l+20,(t+b)/2);g2.dispose();return;}double[]vals={m.liveMap,m.liveFallbackMap,m.liveEffectiveMap,m.predictionTarget};String[]names={"Measured","Fallback / predicted","Effective","Final target"};Color[]cs={AeUiTheme.focusBlue(),AeUiTheme.red(),AeUiTheme.focusGreen(),AeUiTheme.purple()};double max=1;for(double v:vals)if(Double.isFinite(v))max=Math.max(max,v);max*=1.12;int usable=(r-l)-60;for(int i=0;i<vals.length;i++){int x=l+20+i*usable/Math.max(1,vals.length-1);if(Double.isFinite(vals[i])){int y=b-(int)(vals[i]/max*(b-t));g2.setColor(cs[i]);g2.setStroke(new BasicStroke(i==1?2f:4f));if(i==1)g2.drawLine(x-16,y,x+16,y);else{g2.drawLine(x,b,x,y);g2.fillOval(x-6,y-6,12,12);}g2.setFont(new Font("Dialog",Font.BOLD,9));g2.drawString(fmt(vals[i])+" kPa",x-22,Math.max(t+12,y-10));}g2.setColor(AeUiTheme.focusMuted());g2.setFont(new Font("Dialog",Font.PLAIN,9));g2.drawString(names[i],x-30,b+18);}g2.setFont(new Font("Dialog",Font.BOLD,9));g2.setColor(AeUiTheme.focusMuted());g2.drawString("Physical catch-up "+fmtMillis(m.physicalCatchupSeconds)+" • gap "+fmt(m.targetGap)+" kPa",l,b+34);g2.dispose();}
    }

    private static final class ReferenceChart extends JPanel{
        private BlendDurationFocusModel m;ReferenceChart(){setBackground(AeUiTheme.card());setPreferredSize(new Dimension(190,260));}void update(BlendDurationFocusModel m){this.m=m;repaint();}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int l=24,r=getWidth()-14,t=24,b=getHeight()-32;g2.setColor(AeUiTheme.chartGridSoft());g2.drawLine(l,b,r,b);if(m==null||!Double.isFinite(m.lastEventBaseMap)){g2.setColor(AeUiTheme.focusMuted());g2.setFont(SMALL);g2.drawString("retained event",l+8,(t+b)/2);g2.drawString("reference appears",l+8,(t+b)/2+14);g2.drawString("after capture",l+8,(t+b)/2+28);g2.dispose();return;}double delta=Double.isFinite(m.lastEventGap)?Math.abs(m.lastEventGap):Math.max(0,m.predictionTarget-m.lastEventBaseMap);double max=Math.max(10,delta*1.4);int y=b-(int)(delta/max*(b-t));g2.setColor(AeUiTheme.focusGreen());g2.setStroke(new BasicStroke(4f));g2.drawLine(l+24,b,l+24,y);g2.setFont(new Font("Dialog",Font.BOLD,9));g2.drawString("latest Δ",l+8,Math.max(t+10,y-5));g2.setColor(AeUiTheme.focusText());g2.drawString(fmt(delta)+" kPa",l+8,b+18);g2.setColor(AeUiTheme.focusMuted());g2.drawString("catch-up "+fmtMillis(m.lastEventDuration),l+8,b+31);g2.dispose();}
    }

    private static final class Diagnostics extends JPanel{
        private final JLabel status=label("Waiting",17,Font.BOLD,AeUiTheme.focusBlue());private final JTextArea details=new JTextArea();private final Timer timer;
        Diagnostics(){setLayout(new BorderLayout(0,8));setBackground(AeUiTheme.focusBackground());setBorder(new EmptyBorder(8,10,8,10));JPanel h=new JPanel(new BorderLayout());h.setOpaque(false);h.add(label("Blend Duration Diagnostics",22,Font.BOLD,AeUiTheme.focusText()),BorderLayout.NORTH);h.add(label("Detailed timing, firmware replay, event quality and current RPM-region evidence.",11,Font.PLAIN,AeUiTheme.focusMuted()),BorderLayout.SOUTH);add(h,BorderLayout.NORTH);JPanel top=new JPanel(new GridLayout(1,4,8,0));top.setOpaque(false);top.add(infoTable("Current Status",new String[][]{{"State","CAPTURING / REVIEW"}}));top.add(infoTable("Current RPM Region",new String[][]{{"RPM","automatic"}}));top.add(infoTable("Current Blend Setting",new String[][]{{"Curve","n/a"}}));top.add(infoTable("Authority",new String[][]{{"Proposal","WITHHELD"}}));JPanel center=new JPanel(new BorderLayout(0,8));center.setOpaque(false);center.add(top,BorderLayout.NORTH);details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);details.setFont(new Font("Dialog",Font.PLAIN,11));details.setForeground(AeUiTheme.focusText());details.setBackground(AeUiTheme.card());JScrollPane s=new JScrollPane(details);s.setBorder(new LineBorder(AeUiTheme.border()));center.add(s,BorderLayout.CENTER);add(center,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);timer=new Timer(200,e->refresh());addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)==0)return;if(isShowing()){refresh();timer.start();}else timer.stop();});refresh();}
        private void refresh(){BlendDurationFocusModel m=model();if(m==null){status.setText("Waiting for production Blend Focus state");details.setText("");return;}status.setText(m.status);String text="LIVE / CURRENT MEASUREMENT\nRPM target / live: "+fmt0(m.targetRpm)+" / "+fmt0(m.liveRpm)+" rpm\nMeasured MAP: "+fmt(m.liveMap)+" kPa\nPredicted / fallback MAP: "+fmt(m.liveFallbackMap)+" kPa\nEffective MAP: "+fmt(m.liveEffectiveMap)+" kPa\nFinal upward-latched target: "+fmt(m.predictionTarget)+" kPa\nTarget gap: "+fmt(m.targetGap)+" kPa\nSoft plateau: "+(m.softPlateauAcquired?"ACQUIRED":"WAITING")+"\nCurrent RPM-interpolated Blend Duration: "+fmtMillis(m.currentBlendDuration)+"\nPhysical MAP catch-up: "+fmtMillis(m.physicalCatchupSeconds)+"\nPrediction counters: "+m.predictionCounterEvidence+"\n\nFIRMWARE REPLAY\nSamples: "+m.effectiveMapReplaySamples+"\nMean |error|: "+fmt(m.effectiveMapMeanAbsoluteError)+" kPa\nMax |error|: "+fmt(m.effectiveMapMaxAbsoluteError)+" kPa\nConsistent: "+m.effectiveMapReplayConsistent+"\n\nCOMPARABILITY\nMatching "+m.matchingEvents+" / "+m.targetEvents+" | valid "+m.allValidEvents+" | excluded "+m.excludedEvents+" | returned "+m.returnedEvents+"\n"+m.repeatability+"\n"+m.comparabilityHint+"\n\n"+m.detail;if(!details.getText().equals(text)){details.setText(text);details.setCaretPosition(0);}}
    }

    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}