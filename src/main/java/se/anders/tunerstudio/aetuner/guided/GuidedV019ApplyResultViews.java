package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Production binding of the validated v0.19 Apply / Result presentation. */
final class GuidedV019ApplyResultViews {
    enum ApplyAction { PERFORM, BACK, CLOSE }
    enum ResultAction { KEEP, RESTORE, CLOSE }
    interface ApplyListener { void action(ApplyAction action); }
    interface ResultListener { void action(ResultAction action); }

    private GuidedV019ApplyResultViews() { }

    static JComponent createApply(GuidedProductionTask task, AeProjectSnapshot tune,
                                  GuidedV019ProductionBridge bridge, final ApplyListener listener) {
        final boolean canApply = bridge.applyEnabled();
        JPanel root = shell();
        root.add(header(task.displayName + " — Guided Apply",
                "Exact reviewed plan → stale-baseline preflight → write → exact readback → Restore snapshot",
                canApply ? "PLAN READY" : "NO WRITE AUTHORITY", canApply ? AeUiTheme.amber() : AeUiTheme.muted()), BorderLayout.NORTH);
        if (!canApply) {
            JPanel center = new JPanel(new BorderLayout(8,8)); center.setOpaque(false);
            center.add(note("Apply is intentionally unavailable",
                    "This Review outcome has no real production ProposalWritePlan. The v0.19 presentation never fabricates a write merely to advance the workflow."), BorderLayout.NORTH);
            center.add(titled("No-write lifecycle", kv(new String[][]{{"Review","completed / evidence retained"},{"Write","NONE"},{"Readback","not required"},{"Restore","not required"},{"Burn","NEVER"}})), BorderLayout.CENTER);
            root.add(center, BorderLayout.CENTER); root.add(applyFooter(false, listener), BorderLayout.SOUTH); return root;
        }
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        JPanel summaries = new JPanel(new GridLayout(1,4,8,0)); summaries.setOpaque(false);
        summaries.add(summary("Reviewed plan", writeCount(task,state), "Immutable production Review output", AeUiTheme.blue()));
        summaries.add(summary("Baseline", tune == null ? "UNAVAILABLE" : "PREFLIGHT", tune == null ? "Read Working Tune" : tune.getConfigurationName(), tune == null ? AeUiTheme.red() : AeUiTheme.green()));
        summaries.add(summary("Readback", "REQUIRED", "Exact declared targets only", AeUiTheme.amber()));
        summaries.add(summary("Burn", "NEVER", "Restore snapshot retained", AeUiTheme.green()));
        JPanel content=new JPanel(new BorderLayout(8,8));content.setOpaque(false);content.add(summaries,BorderLayout.NORTH);
        JPanel mid=new JPanel(new GridLayout(1,3,8,0));mid.setOpaque(false);
        mid.add(titled("Exact reviewed targets", exactPlan(task,state,bridge)));
        mid.add(titled("Preflight / authority", kv(new String[][]{{"Working Tune",tune==null?"unavailable":tune.getConfigurationName()},{"Proposal baseline","production coordinator authority"},{"Stale baseline","validated before write"},{"Targets writable","validated before write"},{"Unreviewed targets","0 by plan contract"},{"Burn","NEVER"}})));
        JPanel lifecycle=new JPanel();lifecycle.setOpaque(false);lifecycle.setLayout(new BoxLayout(lifecycle,BoxLayout.Y_AXIS));lifecycle.add(step("1","Validate baseline","REQUIRED",AeUiTheme.green()));lifecycle.add(Box.createVerticalStrut(7));lifecycle.add(step("2","Write declared targets","READY",AeUiTheme.amber()));lifecycle.add(Box.createVerticalStrut(7));lifecycle.add(step("3","Read back exact targets","REQUIRED",AeUiTheme.blue()));lifecycle.add(Box.createVerticalStrut(7));lifecycle.add(step("4","Keep Restore snapshot","REQUIRED",AeUiTheme.blue()));lifecycle.add(Box.createVerticalStrut(12));lifecycle.add(note("Production binding","This view delegates the existing reviewed plan to ProposalApplyCoordinator. It owns no writer and performs no Burn."));mid.add(titled("Guarded write lifecycle",lifecycle));content.add(mid,BorderLayout.CENTER);
        content.add(note("PRODUCTION — GUARDED APPLY ONLY","Perform Guarded Apply delegates to the existing AE Tuner coordinator. Exact readback and Restore remain production-authoritative."),BorderLayout.SOUTH);
        root.add(content,BorderLayout.CENTER);root.add(applyFooter(true,listener),BorderLayout.SOUTH);return root;
    }

    static JComponent createResult(GuidedProductionTask task, AeProjectSnapshot tune,
                                   GuidedV019ProductionBridge bridge, boolean applied,
                                   final ResultListener listener) {
        JPanel root=shell();
        boolean restore=bridge.restoreEnabled();
        boolean evidenceReady=bridge.evidenceReady(task);
        boolean applyReady=bridge.applyEnabled();
        String state=applied?"APPLIED / VERIFY":evidenceReady?"NO-WRITE RESULT":"EVIDENCE INCOMPLETE";
        Color stateColor=applied?AeUiTheme.blue():evidenceReady?(task==GuidedProductionTask.BLEND_DURATION?AeUiTheme.amber():AeUiTheme.green()):AeUiTheme.amber();
        String subtitle=applied
                ? "Task-specific final state — verify the guarded production lifecycle and choose Keep or Restore"
                : evidenceReady
                    ? "Task-specific final state — review-ready evidence retained; no controller write was required"
                    : "Capture stopped before the task evidence target was satisfied — continue Capture; Review/Apply authority is withheld";
        root.add(header(task.displayName+" — Result",subtitle,state,stateColor),BorderLayout.NORTH);
        JPanel summaries=new JPanel(new GridLayout(1,4,8,0));summaries.setOpaque(false);
        if(applied){summaries.add(summary("Apply",restore?"READBACK / SNAPSHOT READY":"COORDINATOR RETURNED","Existing production path",restore?AeUiTheme.green():AeUiTheme.amber()));summaries.add(summary("Validation","USER / VEHICLE","Evaluate the applied result",AeUiTheme.blue()));summaries.add(summary("Restore",restore?"AVAILABLE":"CHECK STATE","Verified pre-write snapshot",restore?AeUiTheme.blue():AeUiTheme.amber()));summaries.add(summary("Decision","KEEP / RESTORE","Explicit user choice",AeUiTheme.amber()));}
        else if(evidenceReady){summaries.add(summary("Review",task==GuidedProductionTask.BLEND_DURATION&&!applyReady?"EVIDENCE ONLY":"NO WRITE","Production Review outcome",stateColor));summaries.add(summary("Write","NONE","Apply was not required",AeUiTheme.muted()));summaries.add(summary("Evidence","RETAINED",tune==null?"production session":tune.getConfigurationName(),AeUiTheme.blue()));summaries.add(summary("Restore","NOT REQUIRED","No controller mutation",AeUiTheme.green()));}
        else {summaries.add(summary("Review","BLOCKED","Evidence target not satisfied",AeUiTheme.amber()));summaries.add(summary("Write","NONE","No review authority",AeUiTheme.muted()));summaries.add(summary("Evidence","INCOMPLETE","Continue the same capture",AeUiTheme.amber()));summaries.add(summary("Restore","NOT REQUIRED","No controller mutation",AeUiTheme.green()));}
        JPanel content=new JPanel(new BorderLayout(8,8));content.setOpaque(false);content.add(summaries,BorderLayout.NORTH);content.add(resultBody(task,applied,evidenceReady,bridge),BorderLayout.CENTER);root.add(content,BorderLayout.CENTER);root.add(resultFooter(applied&&restore,evidenceReady||applied,listener),BorderLayout.SOUTH);return root;
    }

    private static JComponent exactPlan(GuidedProductionTask task, GuidedFocusHub.State state, GuidedV019ProductionBridge bridge){
        if(task==GuidedProductionTask.THRESHOLD_SENSITIVITY&&state!=null&&state.foundationThreshold!=null){List<Object[]> rows=new ArrayList<Object[]>();for(FoundationThresholdFocusModel.Bin b:state.foundationThreshold.bins)if(b.changed)rows.add(new Object[]{b.regionLabel,fmt(b.currentThreshold),fmt(b.proposedThreshold),signed(b.proposedThreshold-b.currentThreshold)});return themedTable(new String[]{"RPM region","Current","Proposed","Δ"},rows.toArray(new Object[rows.size()][]));}
        if(task==GuidedProductionTask.MAP_ESTIMATE&&state!=null&&state.mapEstimate!=null){List<Object[]> rows=new ArrayList<Object[]>();MapEstimateFocusModel m=state.mapEstimate;for(int r=0;r<m.rows();r++)for(int c=0;c<m.cols();c++){MapEstimateFocusModel.Cell x=m.cell(r,c);if(x.proposalChange)rows.add(new Object[]{fmt(m.tpsAxis[r])+"%",fmt0(m.rpmAxis[c]),fmt(x.currentKpa),Double.isFinite(x.valueKpa)?fmt(x.valueKpa):"—",fmt(x.proposedKpa),signed(x.proposedKpa-x.currentKpa)});}return themedTable(new String[]{"TPS","RPM","Working","Learned","Proposed","Δ"},rows.toArray(new Object[rows.size()][]));}
        JTextArea area=wrapArea(bridge.proposalText(),11,AeUiTheme.text());return themedScroll(area);
    }

    private static JComponent resultBody(GuidedProductionTask task,boolean applied,boolean evidenceReady,GuidedV019ProductionBridge bridge){
        GuidedFocusHub.State s=GuidedFocusHub.snapshot();
        if(!applied&&task==GuidedProductionTask.TPS_MOVEMENT_TIMING){EngagementPassiveCapture.Snapshot p=EngagementPassiveCapture.snapshot();return titled(evidenceReady?"Retained timing evidence":"Incomplete timing evidence",kv(new String[][]{{"Comparable movements",p.comparableEvents+" / "+p.targetComparable},{"Rejected",String.valueOf(p.rejectedSmall+p.rejectedLarge+p.rejectedDuration)},{"Pre-event RPM span",fmt0(p.roadRpmSpan)+" RPM"},{"Write","NONE"},{"Evidence",evidenceReady?"retained":"incomplete — continue capture"}}));}
        if(!applied&&task==GuidedProductionTask.BLEND_DURATION&&s!=null&&s.blendDuration!=null){
            BlendDurationFocusModel m=s.blendDuration;
            JPanel p=new JPanel(new BorderLayout(8,0));
            p.setOpaque(false);

            JPanel evidenceColumn=new JPanel(new BorderLayout());
            evidenceColumn.setOpaque(false);
            evidenceColumn.setPreferredSize(new Dimension(390,0));
            evidenceColumn.add(titled(evidenceReady?"Retained response evidence":"Incomplete response evidence",
                    compactKv(new String[][]{{"Current Blend",fmtMillis(m.currentBlendDuration)},
                            {"Final prediction target",fmt(m.predictionTarget)+" kPa"},
                            {"Target gap",fmt(m.targetGap)+" kPa"},
                            {"Physical MAP catch-up",fmtMillis(m.physicalCatchupSeconds)},
                            {"Comparable events",m.matchingEvents+" / "+m.targetEvents}})),BorderLayout.NORTH);
            p.add(evidenceColumn,BorderLayout.WEST);

            JPanel outcomeColumn=new JPanel(new BorderLayout());
            outcomeColumn.setOpaque(false);
            JPanel outcomeStack=new JPanel();
            outcomeStack.setOpaque(false);
            outcomeStack.setLayout(new BoxLayout(outcomeStack,BoxLayout.Y_AXIS));
            outcomeStack.add(bigText(evidenceReady?"MEASUREMENT ACCEPTED — PROPOSAL WITHHELD":"MORE EVIDENCE NEEDED",AeUiTheme.amber()));
            outcomeStack.add(Box.createVerticalStrut(10));
            outcomeStack.add(resultNote(evidenceReady?"Evidence retained":"Evidence incomplete",
                    evidenceReady?m.comparabilityHint:"Continue Capture until the comparable-event target is satisfied. No Review/Apply authority is granted by stopping early."));
            if(evidenceReady&&!bridge.applyEnabled()){
                outcomeStack.add(Box.createVerticalStrut(8));
                outcomeStack.add(resultNote("Why no Apply",
                        "Production authority does not yet convert the measured catch-up relationship into a numerical Blend Duration change."));
            }
            outcomeColumn.add(outcomeStack,BorderLayout.NORTH);
            p.add(titled("Outcome",outcomeColumn),BorderLayout.CENTER);
            return p;
        }
        if(applied)return titled("Applied production result",kv(new String[][]{{"Coordinator","existing ProposalApplyCoordinator"},{"Exact readback","production authority"},{"Restore snapshot",bridge.restoreEnabled()?"AVAILABLE":"check coordinator state"},{"Burn","NEVER"},{"Next","validate then Keep / Restore"}}));
        return titled(evidenceReady?"Result summary":"Incomplete capture summary",kv(new String[][]{{"Review",evidenceReady?"complete":"blocked"},{"Write","NONE"},{"Evidence",evidenceReady?"retained":"incomplete — continue capture"},{"Restore","not required"}}));
    }

    private static String writeCount(GuidedProductionTask task,GuidedFocusHub.State s){if(task==GuidedProductionTask.THRESHOLD_SENSITIVITY&&s!=null&&s.foundationThreshold!=null)return s.foundationThreshold.changedBins+" indexed value(s)";if(task==GuidedProductionTask.MAP_ESTIMATE&&s!=null&&s.mapEstimate!=null)return s.mapEstimate.proposalChangeCount+" indexed cell(s)";return "reviewed ProposalWritePlan";}

    private static JComponent applyFooter(boolean canApply,final ApplyListener listener){JPanel p=card(new BorderLayout(8,0));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(7,9,7,9)));p.add(label(canApply?"Next: explicit guarded production Apply":"Next: return to Review / Result",11,Font.BOLD,AeUiTheme.text()),BorderLayout.WEST);JPanel b=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));b.setOpaque(false);JButton back=button("Back to Review"),close=button("Close");back.addActionListener(e->{if(listener!=null)listener.action(ApplyAction.BACK);});close.addActionListener(e->{if(listener!=null)listener.action(ApplyAction.CLOSE);});b.add(back);if(canApply){JButton apply=button("Perform Guarded Apply");highlight(apply);apply.addActionListener(e->{if(listener!=null)listener.action(ApplyAction.PERFORM);});b.add(apply);}b.add(close);p.add(b,BorderLayout.EAST);return p;}
    private static JComponent resultFooter(boolean canRestore,boolean canFinish,final ResultListener listener){JPanel p=card(new BorderLayout(8,0));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(7,9,7,9)));p.add(label(canRestore?"Next: keep applied result or restore previous values":canFinish?"Next: finish and retain the reviewed evidence":"Next: close this view and continue Capture",11,Font.BOLD,AeUiTheme.text()),BorderLayout.WEST);JPanel b=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));b.setOpaque(false);if(canRestore){JButton restore=button("Restore Previous");restore.addActionListener(e->{if(listener!=null)listener.action(ResultAction.RESTORE);});b.add(restore);}if(canFinish){JButton keep=button(canRestore?"Keep Applied":"Finish Session");highlight(keep);keep.addActionListener(e->{if(listener!=null)listener.action(ResultAction.KEEP);});b.add(keep);}JButton close=button("Close");close.addActionListener(e->{if(listener!=null)listener.action(ResultAction.CLOSE);});b.add(close);p.add(b,BorderLayout.EAST);return p;}

    private static JPanel shell(){JPanel p=new JPanel(new BorderLayout(0,8));p.setBackground(AeUiTheme.background());p.setBorder(new EmptyBorder(10,12,10,12));return p;}
    private static JComponent header(String title,String sub,String state,Color c){JPanel p=new JPanel(new BorderLayout(8,4));p.setOpaque(false);JPanel l=new JPanel(new BorderLayout());l.setOpaque(false);l.add(label(title,21,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);l.add(label(sub,11,Font.PLAIN,AeUiTheme.muted()),BorderLayout.SOUTH);p.add(l,BorderLayout.WEST);JPanel r=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));r.setOpaque(false);r.add(pill("PRODUCTION — GUARDED",AeUiTheme.softBlue(),AeUiTheme.blue()));r.add(pill(state,alpha(c,AeUiTheme.isDark()?55:28),c));p.add(r,BorderLayout.EAST);return p;}
    private static JPanel titled(String title,JComponent body){JPanel p=card(new BorderLayout(0,6));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(8,9,8,9)));p.add(label(title,13,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);p.add(body,BorderLayout.CENTER);return p;}
    private static JPanel summary(String title,String main,String sub,Color accent){JPanel p=card(new BorderLayout(4,4));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(7,9,7,9)));p.add(label(title,11,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);p.add(label("<html>"+main+"</html>",14,Font.BOLD,accent),BorderLayout.CENTER);p.add(wrapArea(sub,9,AeUiTheme.muted()),BorderLayout.SOUTH);return p;}
    private static JPanel note(String title,String text){JPanel p=card(new BorderLayout(0,4));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(7,8,7,8)));p.add(label(title,11,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);p.add(wrapArea(text,10,AeUiTheme.muted()),BorderLayout.CENTER);return p;}
    private static JPanel resultNote(String title,String text){JPanel p=card(new BorderLayout(0,5));p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(9,10,9,10)));p.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(label(title,13,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);p.add(wrapArea(text,13,AeUiTheme.text()),BorderLayout.CENTER);return p;}
    private static JPanel compactKv(String[][] rows){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));for(int i=0;i<rows.length;i++){String[]r=rows[i];JPanel row=new JPanel(new BorderLayout(12,0));row.setOpaque(false);row.setAlignmentX(Component.LEFT_ALIGNMENT);JLabel key=label(r[0],12,Font.PLAIN,AeUiTheme.muted());key.setPreferredSize(new Dimension(170,key.getPreferredSize().height));row.add(key,BorderLayout.WEST);row.add(label(r[1],12,Font.BOLD,AeUiTheme.text()),BorderLayout.CENTER);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));p.add(row);if(i+1<rows.length)p.add(Box.createVerticalStrut(6));}return p;}
    private static JTextArea bigText(String t,Color c){JTextArea a=wrapArea(t,16,c);a.setFont(new Font("Dialog",Font.BOLD,16));a.setAlignmentX(Component.LEFT_ALIGNMENT);return a;}
    private static JPanel step(String n,String title,String status,Color c){JPanel p=card(new BorderLayout(7,0));JLabel no=label(n,14,Font.BOLD,c);no.setHorizontalAlignment(SwingConstants.CENTER);no.setPreferredSize(new Dimension(28,28));JPanel text=new JPanel(new BorderLayout());text.setOpaque(false);text.add(label(title,11,Font.BOLD,AeUiTheme.text()),BorderLayout.NORTH);text.add(label(status,9,Font.BOLD,c),BorderLayout.SOUTH);p.add(no,BorderLayout.WEST);p.add(text,BorderLayout.CENTER);return p;}
    private static JLabel big(String t,Color c){JLabel l=label(t,16,Font.BOLD,c);l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
    private static JPanel card(LayoutManager lm){JPanel p=new JPanel(lm);p.setBackground(AeUiTheme.card());return p;}
    private static JLabel label(String t,int size,int style,Color c){JLabel l=new JLabel(t);l.setFont(new Font("Dialog",style,size));l.setForeground(c);return l;}
    private static JTextArea wrapArea(String t,int size,Color c){JTextArea a=new JTextArea(t==null?"":t);a.setFont(new Font("Dialog",Font.PLAIN,size));a.setForeground(c);a.setOpaque(false);a.setEditable(false);a.setFocusable(false);a.setLineWrap(true);a.setWrapStyleWord(true);a.setBorder(null);a.setAlignmentX(Component.LEFT_ALIGNMENT);return a;}
    private static JLabel pill(String t,Color bg,Color fg){JLabel l=label(t,9,Font.BOLD,fg);l.setOpaque(true);l.setBackground(bg);l.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()),new EmptyBorder(4,7,4,7)));return l;}
    private static JButton button(String t){JButton b=new JButton(t);b.setFont(new Font("Dialog",Font.PLAIN,10));b.setFocusPainted(false);b.setOpaque(true);b.setBackground(AeUiTheme.button());b.setForeground(AeUiTheme.text());b.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.buttonBorder()),new EmptyBorder(5,8,5,8)));return b;}
    private static void highlight(JButton b){b.setBackground(AeUiTheme.amber());b.setForeground(new Color(42,31,4));b.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.amberDark(),2),new EmptyBorder(4,7,4,7)));}
    private static JPanel kv(String[][] rows){JPanel p=new JPanel(new GridLayout(rows.length,2,6,4));p.setOpaque(false);for(String[]r:rows){p.add(label(r[0],10,Font.PLAIN,AeUiTheme.muted()));p.add(label(r[1],10,Font.BOLD,AeUiTheme.text()));}return p;}
    private static JComponent themedTable(String[] cols,Object[][] rows){JTable t=new JTable(new DefaultTableModel(rows,cols){public boolean isCellEditable(int r,int c){return false;}});GuidedV019FocusBase.styleTable(t);t.setRowHeight(25);return themedScroll(t);}
    private static JScrollPane themedScroll(Component c){JScrollPane s=new JScrollPane(c);s.setBorder(new LineBorder(AeUiTheme.border()));s.getViewport().setBackground(AeUiTheme.card());return s;}
    private static Color alpha(Color c,int a){return new Color(c.getRed(),c.getGreen(),c.getBlue(),a);}
    private static String fmt(double v){return Double.isFinite(v)?String.format(Locale.ROOT,"%.2f",v):"n/a";}
    private static String fmt0(double v){return Double.isFinite(v)?String.format(Locale.ROOT,"%.0f",v):"n/a";}
    private static String fmtMillis(double sec){return Double.isFinite(sec)?String.format(Locale.ROOT,"%.0f ms",sec*1000.0):"n/a";}
    private static String signed(double v){return Double.isFinite(v)?String.format(Locale.ROOT,"%+.2f",v):"n/a";}
}
