package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Supplier;

/** Window-local theme/contrast repair. Never changes TunerStudio's UIManager defaults. */
final class GuidedUiTheme {
    private static final String INSTALLED="ae.theme.installed",BUSY="ae.theme.applying";
    private static final class Binding {
        final Supplier<Color> role; final Color original; Color applied;
        Binding(Supplier<Color> role,Color applied) {this.role=role;this.original=applied;this.applied=applied;}
    }
    @SuppressWarnings("unchecked")
    private static final Supplier<Color>[] BACKGROUNDS=new Supplier[]{
        (Supplier<Color>)AeUiTheme::card,(Supplier<Color>)AeUiTheme::background,(Supplier<Color>)AeUiTheme::panel,
        (Supplier<Color>)AeUiTheme::focusBackground,(Supplier<Color>)AeUiTheme::button,(Supplier<Color>)AeUiTheme::buttonAlt,
        (Supplier<Color>)AeUiTheme::softBlue,(Supplier<Color>)AeUiTheme::softGreen,(Supplier<Color>)AeUiTheme::softAmber,
        (Supplier<Color>)AeUiTheme::softRed,(Supplier<Color>)AeUiTheme::softPurple,(Supplier<Color>)AeUiTheme::neutralSoft,
        (Supplier<Color>)AeUiTheme::taskDisabledBg,(Supplier<Color>)AeUiTheme::taskSelectedBg,
        (Supplier<Color>)AeUiTheme::tableHeader,(Supplier<Color>)AeUiTheme::focusSoftBlue,(Supplier<Color>)AeUiTheme::focusSoftGreen};
    @SuppressWarnings("unchecked")
    private static final Supplier<Color>[] FOREGROUNDS=new Supplier[]{
        (Supplier<Color>)AeUiTheme::text,(Supplier<Color>)AeUiTheme::focusText,(Supplier<Color>)AeUiTheme::muted,
        (Supplier<Color>)AeUiTheme::focusMuted,(Supplier<Color>)AeUiTheme::navy,(Supplier<Color>)AeUiTheme::blue,
        (Supplier<Color>)AeUiTheme::focusBlue,(Supplier<Color>)AeUiTheme::green,(Supplier<Color>)AeUiTheme::focusGreen,
        (Supplier<Color>)AeUiTheme::amber,(Supplier<Color>)AeUiTheme::focusAmber,(Supplier<Color>)AeUiTheme::red};
    private GuidedUiTheme() { }
    static void install(JComponent root) {
        apply(root);
        Timer timer=new Timer(250,null);
        final AeUiTheme.Side[] side={AeUiTheme.side()};
        timer.addActionListener(e -> {if(side[0]!=AeUiTheme.side()){side[0]=AeUiTheme.side();apply(root);}});
        if(root.isShowing())timer.start();
        root.addHierarchyListener(e -> {
            if((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)==0)return;
            if(root.isShowing()){apply(root);timer.start();}else timer.stop();
        });
    }
    static void apply(Component component) {
        if(component instanceof JComponent) {
            JComponent c=(JComponent)component;
            if(c.getClientProperty(INSTALLED)==null) {
                c.putClientProperty(INSTALLED,Boolean.TRUE);
                if(c instanceof JLabel && c.getToolTipText()==null)c.putClientProperty("ae.theme.autoTooltip",Boolean.TRUE);
                c.addPropertyChangeListener(e -> {
                    if("foreground".equals(e.getPropertyName())||"background".equals(e.getPropertyName())
                            ||"text".equals(e.getPropertyName()))single(c);
                });
                if(c instanceof JButton)((JButton)c).setUI(new ContrastButtonUI());
                if(c instanceof JTable)tables((JTable)c);
                c.addContainerListener(new ContainerAdapter(){@Override public void componentAdded(ContainerEvent e){apply(e.getChild());}});
            }
            single(c);
        }
        if(component instanceof Container)for(Component child:((Container)component).getComponents())apply(child);
    }
    private static void single(JComponent c) {
        if(Boolean.TRUE.equals(c.getClientProperty(BUSY)))return;
        c.putClientProperty(BUSY,Boolean.TRUE);
        try {
            Color bg=bound(c,"ae.theme.background",c.getBackground(),BACKGROUNDS,
                    c instanceof AbstractButton?AeUiTheme::button:AeUiTheme::card);
            if(bg!=null) {
                // Swing may skip painting behind an opaque child. Flatten alpha
                // fills so both the rendered background and contrast are stable.
                if(c.isOpaque() && bg.getAlpha()<255)bg=composite(bg,background(c.getParent()));
                c.setBackground(bg);
                ((Binding)c.getClientProperty("ae.theme.background")).applied=bg;
            }
            Color effective=background(c);
            boolean text=c instanceof JLabel||c instanceof AbstractButton||c instanceof JTextComponent
                    ||c instanceof JComboBox||c instanceof JTable;
            if(text) {
                Color fg=bound(c,"ae.theme.foreground",c.getForeground(),FOREGROUNDS,AeUiTheme::text);
                Color readable=readable(fg,effective);c.setForeground(readable);
                Object binding=c.getClientProperty("ae.theme.foreground");
                if(binding instanceof Binding)((Binding)binding).applied=readable;
            }
            if(c instanceof JTextComponent) {
                JTextComponent t=(JTextComponent)c;t.setCaretColor(AeUiTheme.text());
                t.setDisabledTextColor(readable(AeUiTheme.muted(),effective));
                t.setSelectionColor(AeUiTheme.softBlue());t.setSelectedTextColor(readable(AeUiTheme.text(),AeUiTheme.softBlue()));
            }
            if(c instanceof JTable) {
                JTable table=(JTable)c;table.setGridColor(AeUiTheme.border());
                table.setSelectionBackground(AeUiTheme.softBlue());table.setSelectionForeground(readable(AeUiTheme.text(),AeUiTheme.softBlue()));
            }
            title(c.getBorder(),effective);
            if(c instanceof JLabel && Boolean.TRUE.equals(c.getClientProperty("ae.theme.autoTooltip"))) {
                String label=((JLabel)c).getText();if(label!=null&&!label.startsWith("<html>"))c.setToolTipText(label);
            }
        } finally {c.putClientProperty(BUSY,Boolean.FALSE);}
    }
    private static Color bound(JComponent c,String key,Color color,Supplier<Color>[] roles,Supplier<Color> fallback) {
        Binding binding=(Binding)c.getClientProperty(key);
        if(binding==null||color==null||!color.equals(binding.applied)) {
            Supplier<Color> role=null;
            for(Supplier<Color> candidate:roles)if(candidate.get().equals(color)){role=candidate;break;}
            if(role==null && (color==null||color instanceof UIResource))role=fallback;
            binding=new Binding(role,color);c.putClientProperty(key,binding);
        }
        Color result=binding.role==null?binding.original:binding.role.get();binding.applied=result;return result;
    }
    static Color background(Component c) {
        if(c==null)return AeUiTheme.card();
        Color under=background(c.getParent());
        if(c instanceof JComponent && !((JComponent)c).isOpaque())return under;
        Color bg=c.getBackground();
        if(bg==null)return under;
        if(bg.getAlpha()==255)return bg;
        return composite(bg,under);
    }
    private static Color composite(Color bg,Color under) {
        double alpha=bg.getAlpha()/255.0;
        return new Color((int)Math.round(bg.getRed()*alpha+under.getRed()*(1-alpha)),
                (int)Math.round(bg.getGreen()*alpha+under.getGreen()*(1-alpha)),
                (int)Math.round(bg.getBlue()*alpha+under.getBlue()*(1-alpha)));
    }
    static Color readable(Color fg,Color bg) {
        if(fg==null)fg=AeUiTheme.text();if(bg==null)bg=AeUiTheme.card();
        if(contrast(fg,bg)>=4.5)return fg;
        Color anchor=contrast(Color.BLACK,bg)>contrast(Color.WHITE,bg)?Color.BLACK:Color.WHITE;
        for(int i=1;i<=20;i++) {
            double f=i/20.;Color result=new Color((int)(fg.getRed()*(1-f)+anchor.getRed()*f),
                    (int)(fg.getGreen()*(1-f)+anchor.getGreen()*f),(int)(fg.getBlue()*(1-f)+anchor.getBlue()*f));
            if(contrast(result,bg)>=4.5)return result;
        }return anchor;
    }
    static double contrast(Color a,Color b) {
        double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static double luminance(Color c) {
        double[] values={c.getRed()/255.,c.getGreen()/255.,c.getBlue()/255.};
        for(int i=0;i<3;i++)values[i]=values[i]<=.04045?values[i]/12.92:Math.pow((values[i]+.055)/1.055,2.4);
        return .2126*values[0]+.7152*values[1]+.0722*values[2];
    }
    private static void title(Border b,Color bg) {
        if(b instanceof TitledBorder)((TitledBorder)b).setTitleColor(readable(AeUiTheme.text(),bg));
        if(b instanceof CompoundBorder){title(((CompoundBorder)b).getInsideBorder(),bg);title(((CompoundBorder)b).getOutsideBorder(),bg);}
    }
    private static void tables(JTable table) {
        if(table.getTableHeader()!=null) {
            TableCellRenderer original=table.getTableHeader().getDefaultRenderer();
            table.getTableHeader().setDefaultRenderer((t,v,selected,focus,row,col) -> {
                Component c=original.getTableCellRendererComponent(t,v,selected,focus,row,col);
                c.setBackground(AeUiTheme.tableHeader());c.setForeground(readable(AeUiTheme.text(),c.getBackground()));return c;
            });
        }
    }
    private static final class ContrastButtonUI extends BasicButtonUI {
        @Override protected void paintText(Graphics g,AbstractButton b,Rectangle rect,String text) {
            if(b.isEnabled()){super.paintText(g,b,rect,text);return;}
            g.setColor(readable(AeUiTheme.muted(),background(b)));
            BasicGraphicsUtils.drawStringUnderlineCharAt(g,text,-1,rect.x,rect.y+g.getFontMetrics().getAscent());
        }
    }
}
