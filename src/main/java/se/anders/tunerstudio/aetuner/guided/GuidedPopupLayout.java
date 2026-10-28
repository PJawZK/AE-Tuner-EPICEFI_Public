package se.anders.tunerstudio.aetuner.guided;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;

/** Width-first text/report layout, confined to scrollable AE pop-outs. */
final class GuidedPopupLayout {
    private GuidedPopupLayout() { }
    static void prepare(Component component) {
        if(component instanceof JComponent) {
            JComponent c=(JComponent)component;
            if(c.getClientProperty("ae.popup.layout")==null) {
                c.putClientProperty("ae.popup.layout",Boolean.TRUE);
                if(c instanceof JLabel) {
                    c.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){wrap((JLabel)c);}});
                    c.addPropertyChangeListener(e -> {if("text".equals(e.getPropertyName())||"font".equals(e.getPropertyName())||"foreground".equals(e.getPropertyName()))wrap((JLabel)c);});
                    wrap((JLabel)c);
                }
                if(c instanceof JTextArea) {
                    ((JTextArea)c).setLineWrap(true);((JTextArea)c).setWrapStyleWord(true);
                }
                if(c instanceof JScrollPane)((JScrollPane)c).setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                if(c instanceof JTable)fitTable((JTable)c);
                c.addContainerListener(new ContainerAdapter(){public void componentAdded(ContainerEvent e){prepare(e.getChild());}});
            }
        }
        if(component instanceof Container)for(Component child:((Container)component).getComponents())prepare(child);
    }
    private static void fitTable(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for(int i=0;i<table.getColumnCount();i++) {
            javax.swing.table.TableColumn column=table.getColumnModel().getColumn(i);
            javax.swing.table.TableCellRenderer renderer=column.getCellRenderer();
            if(renderer==null)renderer=table.getDefaultRenderer(table.getColumnClass(i));
            column.setCellRenderer(new WrappedCell(renderer));
        }
        if(table.getTableHeader()!=null) {
            javax.swing.table.JTableHeader header=table.getTableHeader();
            WrappedCell renderer=new WrappedCell(header.getDefaultRenderer());
            header.setDefaultRenderer(renderer);
            table.addComponentListener(new ComponentAdapter(){
                private int width=-1;
                public void componentResized(ComponentEvent e) {
                    if(width==table.getWidth())return;width=table.getWidth();
                    table.setRowHeight(table.getRowHeight());
                    int height=20;
                    for(int i=0;i<table.getColumnCount();i++) {
                        Component c=renderer.getTableCellRendererComponent(table,table.getColumnName(i),false,false,-1,i);
                        height=Math.max(height,c.getPreferredSize().height);
                    }
                    header.setPreferredSize(new Dimension(width,height));header.revalidate();
                }
            });
        }
    }
    private static final class WrappedCell implements javax.swing.table.TableCellRenderer {
        private final javax.swing.table.TableCellRenderer delegate;
        private final JTextArea text=new JTextArea();
        WrappedCell(javax.swing.table.TableCellRenderer delegate) {
            this.delegate=delegate;text.setLineWrap(true);text.setWrapStyleWord(true);
            text.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
        }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col) {
            Component original=delegate.getTableCellRendererComponent(table,value,selected,focus,row,col);
            if(!(original instanceof JLabel))return original;
            String valueText=((JLabel)original).getText();
            if(BasicHTML.isHTMLString(valueText))return original;
            text.setText(valueText);text.setToolTipText(valueText);text.setFont(original.getFont());
            text.setForeground(original.getForeground());text.setBackground(original.getBackground());
            text.setSize(Math.max(12,table.getColumnModel().getColumn(col).getWidth()),Short.MAX_VALUE);
            if(row>=0 && text.getPreferredSize().height>table.getRowHeight(row))
                table.setRowHeight(row,text.getPreferredSize().height);
            return text;
        }
    }
    private static void wrap(JLabel label) {
        String text=label.getText();int width=label.getWidth();
        if(text==null||width<40)return;
        if(!BasicHTML.isHTMLString(text) && (text.indexOf(' ')<0
                || label.getFontMetrics(label.getFont()).stringWidth(text)<=width-label.getInsets().left-label.getInsets().right)) {
            if(Boolean.TRUE.equals(label.getClientProperty("ae.popup.wrapped"))) {
                label.putClientProperty("ae.popup.wrapped",Boolean.FALSE);
                BasicHTML.updateRenderer(label,text);label.setPreferredSize(null);label.revalidate();
            }
            return;
        }
        label.putClientProperty("ae.popup.wrapped",Boolean.TRUE);
        View view=(View)label.getClientProperty(BasicHTML.propertyKey);
        if(view==null) {
            view=BasicHTML.createHTMLView(label,"<html>"+text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</html>");
            label.putClientProperty(BasicHTML.propertyKey,view);
        }
        Insets in=label.getInsets();view.setSize(Math.max(1,width-in.left-in.right),0);
        Dimension size=new Dimension(width,(int)Math.ceil(view.getPreferredSpan(View.Y_AXIS))+in.top+in.bottom);
        if(!size.equals(label.getPreferredSize())){label.setPreferredSize(size);label.revalidate();}
    }
}
