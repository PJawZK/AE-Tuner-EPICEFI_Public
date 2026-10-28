package se.anders.tunerstudio.aetuner.guided;

import java.awt.*;

/** Flow controls report their wrapped height instead of clipping a second row. */
final class GuidedWrapLayout extends FlowLayout {
    GuidedWrapLayout(int align,int hgap,int vgap) { super(align,hgap,vgap); }
    @Override public Dimension preferredLayoutSize(Container parent) { return size(parent); }
    @Override public Dimension minimumLayoutSize(Container parent) { return size(parent); }
    private Dimension size(Container parent) {
        synchronized(parent.getTreeLock()) {
            Insets in=parent.getInsets();
            int width=parent.getWidth();
            if(width<=0 && parent.getParent()!=null)width=parent.getParent().getWidth();
            if(width<=0)width=1000;
            int available=Math.max(1,width-in.left-in.right-2*getHgap());
            int rowW=0,rowH=0,totalH=0,maxW=0;
            for(Component c:parent.getComponents())if(c.isVisible()) {
                Dimension d=c.getPreferredSize();int gap=rowW==0?0:getHgap();
                if(rowW>0 && rowW+gap+d.width>available) {
                    maxW=Math.max(maxW,rowW);totalH+=rowH+getVgap();rowW=0;rowH=0;gap=0;
                }
                rowW+=gap+d.width;rowH=Math.max(rowH,d.height);
            }
            return new Dimension(Math.max(maxW,rowW)+in.left+in.right+2*getHgap(),
                    totalH+rowH+in.top+in.bottom+2*getVgap());
        }
    }
}
