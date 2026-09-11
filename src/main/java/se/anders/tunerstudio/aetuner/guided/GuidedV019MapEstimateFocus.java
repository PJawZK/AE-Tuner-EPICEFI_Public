package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCellScope;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCoverageStrategy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** v0.19 table-first MAP Estimate driving view using the cached production model. */
final class GuidedV019MapEstimateFocus extends GuidedV019FocusBase {
    private final CoverageTableModel tableModel = new CoverageTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel selectedTitle=label("Selected Cell",13,Font.BOLD,AeUiTheme.focusText());
    private final JLabel selectedDetails=label("",11,Font.PLAIN,AeUiTheme.focusText());
    private final JPanel selectedPanel=card(new BorderLayout(0,8));
    private final JComboBox<MapEstimateCoverageStrategy> coverage=new JComboBox<MapEstimateCoverageStrategy>(MapEstimateCoverageStrategy.values());
    private final JComboBox<String> scope=new JComboBox<String>(new String[]{"Whole table","Selected cells"});
    private final JLabel stateLine=label("Waiting for MAP Estimate state",10,Font.PLAIN,AeUiTheme.focusMuted());
    private boolean updating;
    private int selectedRow=-1,selectedCol=-1;

    GuidedV019MapEstimateFocus(JDialog owner,Runnable done){super(owner,done);build();}
    String taskTitle(){return "MAP Estimate";}
    String taskSubtitle(){return "Steady-state guided capture — table-first driving view";}

    JComponent drivingContent(){
        JPanel root=new JPanel(new BorderLayout(8,6));root.setOpaque(false);
        root.add(sequence(new String[][]{{"✓","Qualifying","conditions met","green"},{"●","Stable samples","collecting data","blue"},{"○","Cluster forming","analyzing samples","gray"},{"○","Confidence","evaluating stability","gray"},{"○","Review ready","proposed value","gray"}}),BorderLayout.NORTH);
        JPanel main=new JPanel(new BorderLayout(8,0));main.setOpaque(false);main.add(tableCard(),BorderLayout.CENTER);main.add(selectedCard(),BorderLayout.EAST);root.add(main,BorderLayout.CENTER);return root;
    }

    private JComponent tableCard(){
        JPanel p=card(new BorderLayout(0,6));p.setBorder(cardBorder());JPanel head=new JPanel(new BorderLayout());head.setOpaque(false);head.add(label("MAP Estimate Table — Driving (Active)",16,Font.BOLD,AeUiTheme.focusText()),BorderLayout.WEST);JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));controls.setOpaque(false);controls.add(label("Coverage",10,Font.PLAIN,AeUiTheme.focusMuted()));controls.add(coverage);controls.add(label("Scope",10,Font.PLAIN,AeUiTheme.focusMuted()));controls.add(scope);head.add(controls,BorderLayout.EAST);p.add(head,BorderLayout.NORTH);
        styleTable(table);table.setRowHeight(48);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setDefaultRenderer(Object.class,new CellRenderer());table.getSelectionModel().addListSelectionListener(e->selectionChanged());table.getColumnModel().getSelectionModel().addListSelectionListener(e->selectionChanged());p.add(themedTableScroll(table),BorderLayout.CENTER);p.add(legend(),BorderLayout.SOUTH);
        coverage.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){if(updating)return;MapEstimateGuidedFocusPanel.ConfigurationListener l=GuidedFocusHub.mapEstimateConfigurationListener();if(l!=null)l.onStrategyRequested((MapEstimateCoverageStrategy)coverage.getSelectedItem());}});
        scope.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){if(updating)return;MapEstimateFocusModel m=model();MapEstimateGuidedFocusPanel.ConfigurationListener l=GuidedFocusHub.mapEstimateConfigurationListener();if(m==null||l==null)return;if(scope.getSelectedIndex()==0)l.onScopeRequested(MapEstimateCellScope.all(m.rows(),m.cols()));else{MapEstimateCellScope s=MapEstimateCellScope.none(m.rows(),m.cols());if(selectedRow>=0&&selectedCol>=0)s=s.withCell(selectedRow,selectedCol,true);l.onScopeRequested(s);}}});
        return p;
    }

    private JComponent selectedCard(){selectedPanel.setPreferredSize(new Dimension(225,0));selectedPanel.setBorder(cardBorder());selectedPanel.add(selectedTitle,BorderLayout.NORTH);selectedDetails.setVerticalAlignment(SwingConstants.TOP);selectedPanel.add(selectedDetails,BorderLayout.CENTER);selectedPanel.setVisible(false);return selectedPanel;}

    private JComponent legend(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));p.setOpaque(false);p.add(key("Direct",AeUiTheme.focusGreen()));p.add(key("Interpolated",AeUiTheme.focusBlue()));p.add(key("Weak",AeUiTheme.focusAmber()));p.add(key("Recheck / Conflict",AeUiTheme.red()));p.add(key("This run",AeUiTheme.navy()));p.add(key("Target",AeUiTheme.purple()));p.add(key("Proposal",AeUiTheme.blue()));p.add(stateLine);return p;}
    private JLabel key(String text,Color c){return label("■ "+text,9,Font.BOLD,c);}

    JComponent diagnosticsContent(){return new Diagnostics();}

    void refreshFromProduction(){
        MapEstimateFocusModel m=model();tableModel.set(m);updating=true;try{coverage.setSelectedItem(m==null?MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE:m.strategy);scope.setSelectedIndex(m==null||m.scope.isWholeTable()?0:1);}finally{updating=false;}boolean editable=m==null||!m.captureActive;coverage.setEnabled(editable);scope.setEnabled(editable);stateLine.setText(m==null?"No MAP Estimate evidence loaded.":m.evidenceSamplesUsed+" evidence samples • "+m.directCount+" Direct • "+m.conflictCount+" Conflict • "+m.proposalChangeCount+" proposal cell(s)");refreshSelected(m);review.setEnabled(m!=null&&GuidedFocusHub.snapshot().captureState==GuidedCaptureState.COMPLETE);table.repaint();
    }

    private void selectionChanged(){if(table.getSelectedRow()>=0&&table.getSelectedColumn()>0){selectedRow=table.getSelectedRow();selectedCol=table.getSelectedColumn()-1;}refreshSelected(model());}
    private void refreshSelected(MapEstimateFocusModel m){if(m==null||selectedRow<0||selectedCol<0||selectedRow>=m.rows()||selectedCol>=m.cols()){selectedPanel.setVisible(false);return;}MapEstimateFocusModel.Cell c=m.cell(selectedRow,selectedCol);selectedTitle.setText("Selected Cell (Live)");selectedDetails.setText("<html>TPS row <b>"+fmt(m.tpsAxis[selectedRow])+"%</b><br>RPM column <b>"+fmt0(m.rpmAxis[selectedCol])+"</b><br><br>Working Tune <b>"+fmt(c.currentKpa)+" kPa</b><br>Learned estimate <b>"+(Double.isFinite(c.valueKpa)?fmt(c.valueKpa)+" kPa":"—")+"</b><br>Proposed <b>"+(c.proposalChange?fmt(c.proposedKpa)+" kPa":"unchanged")+"</b><br><br>Samples <b>"+c.evidenceSamples+"</b><br>Confidence <b>"+fmt(c.confidence)+"</b><br>State <b>"+c.state+" / "+c.maturity+"</b><br>Evidence <b>"+(c.currentRun?"this run + retained":"retained")+"</b></html>");selectedPanel.setVisible(true);selectedPanel.revalidate();}

    private static MapEstimateFocusModel model(){GuidedFocusHub.State s=GuidedFocusHub.snapshot();return s!=null&&s.recipe==GuidedTuningRecipe.MAP_ESTIMATE?s.mapEstimate:null;}

    private final class CoverageTableModel extends AbstractTableModel{
        private MapEstimateFocusModel m;void set(MapEstimateFocusModel next){boolean shape=m==null||next==null||m.rows()!=next.rows()||m.cols()!=next.cols();m=next;if(shape)fireTableStructureChanged();else fireTableDataChanged();configureWidths();}
        public int getRowCount(){return m==null?0:m.rows();}public int getColumnCount(){return m==null?1:m.cols()+1;}public String getColumnName(int c){return c==0?"TPS / RPM":m==null?"":fmt0(m.rpmAxis[c-1]);}
        public Object getValueAt(int r,int c){if(m==null)return "";if(c==0)return fmt0(m.tpsAxis[r])+"%";MapEstimateFocusModel.Cell cell=m.cell(r,c-1);String learned=Double.isFinite(cell.valueKpa)?fmt(cell.valueKpa):"—";return "<html><b>W</b> "+fmt(cell.currentKpa)+" &nbsp; <b>L</b> "+learned+"<br><b>P</b> "+fmt(cell.proposedKpa)+(cell.proposalChange?" &nbsp; Δ":"")+"</html>";}
        private void configureWidths(){if(table.getColumnModel().getColumnCount()==0)return;table.getColumnModel().getColumn(0).setPreferredWidth(82);for(int c=1;c<table.getColumnModel().getColumnCount();c++)table.getColumnModel().getColumn(c).setPreferredWidth(142);}
    }

    private final class CellRenderer extends DefaultTableCellRenderer{
        public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){JLabel l=(JLabel)super.getTableCellRendererComponent(t,value,selected,focus,row,col);l.setOpaque(true);l.setHorizontalAlignment(col==0?SwingConstants.CENTER:SwingConstants.LEFT);l.setForeground(AeUiTheme.focusText());l.setBorder(new MatteBorder(0,0,1,1,AeUiTheme.border()));MapEstimateFocusModel m=model();if(col==0||m==null){l.setBackground(AeUiTheme.tableHeader());return l;}MapEstimateFocusModel.Cell c=m.cell(row,col-1);Color bg=AeUiTheme.card();if(c.proposalChange)bg=AeUiTheme.softBlue();else if(c.currentRun)bg=AeUiTheme.focusSoftBlue();else if(c.state!=null&&c.state.toString().contains("CONFLICT"))bg=AeUiTheme.softRed();else if(c.state!=null&&c.state.toString().contains("WEAK"))bg=AeUiTheme.softAmber();else if(c.state!=null&&c.state.toString().contains("DIRECT"))bg=AeUiTheme.softGreen();l.setBackground(selected?AeUiTheme.softBlue():bg);if(m.isTarget(row,col-1))l.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.purple(),2),new EmptyBorder(0,2,0,2)));return l;}
    }

    private static final class Diagnostics extends JPanel{
        private final JTable table=new JTable();private final JLabel totals=label("Waiting",11,Font.BOLD,AeUiTheme.focusText());private final Timer timer;
        Diagnostics(){setLayout(new BorderLayout(0,8));setBackground(AeUiTheme.focusBackground());setBorder(new EmptyBorder(8,10,8,10));JPanel h=new JPanel(new BorderLayout());h.setOpaque(false);h.add(label("MAP Estimate Diagnostics",22,Font.BOLD,AeUiTheme.focusText()),BorderLayout.NORTH);h.add(label("Detailed production evidence state, proposal mask and target context.",11,Font.PLAIN,AeUiTheme.focusMuted()),BorderLayout.SOUTH);add(h,BorderLayout.NORTH);JPanel center=card(new BorderLayout(0,6));center.setBorder(cardBorder());center.add(totals,BorderLayout.NORTH);center.add(themedTableScroll(table),BorderLayout.CENTER);add(center,BorderLayout.CENTER);timer=new Timer(200,e->refresh());addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)==0)return;if(isShowing()){refresh();timer.start();}else timer.stop();});refresh();}
        private void refresh(){MapEstimateFocusModel m=model();if(m==null){table.setModel(new javax.swing.table.DefaultTableModel(new Object[0][0],new String[]{"TPS/RPM"}));totals.setText("No MAP Estimate Focus model");return;}String[]cols=new String[m.cols()+1];cols[0]="TPS/RPM";for(int c=0;c<m.cols();c++)cols[c+1]=fmt0(m.rpmAxis[c]);Object[][]rows=new Object[m.rows()][cols.length];for(int r=0;r<m.rows();r++){rows[r][0]=fmt0(m.tpsAxis[r])+"%";for(int c=0;c<m.cols();c++){MapEstimateFocusModel.Cell x=m.cell(r,c);rows[r][c+1]=(x.state==null?"":x.state.toString())+" • "+x.evidenceSamples+" • "+(x.proposalChange?"PROPOSE":"keep");}}table.setModel(new javax.swing.table.DefaultTableModel(rows,cols){public boolean isCellEditable(int r,int c){return false;}});styleTable(table);totals.setText(m.evidenceSamplesUsed+" evidence samples • Direct "+m.directCount+" • Interpolated "+m.interpolatedStrongCount+" • Weak "+m.weakCount+" • Conflict "+m.conflictCount+" • Recheck "+m.recheckCount+" • Proposal "+m.proposalChangeCount);}
    }
}