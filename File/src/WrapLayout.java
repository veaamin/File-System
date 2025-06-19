
import javax.swing.*;
import java.awt.*;

public class WrapLayout extends FlowLayout {
    private Dimension preferredLayoutSize;
    public static int initialHGap, initialVGap;
    static private java.util.List<Component> components = new java.util.ArrayList<Component>();
    static private int backuprmembers = 0;

    public WrapLayout() {
        super();
        init(LEFT, 5, 5); // Call init here
    }


    public WrapLayout(int align) {
        super(align);
        init(align, 5, 5); 
    }

   
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
        init(align, hgap, vgap); 
    }

    private void init(int align, int hgap, int vgap) { 
        setAlignment(align);
        setHgap(hgap);
        setVgap(vgap);
        initialHGap = hgap;
        initialVGap = vgap;
    }


    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }


    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
          
            int targetWidth = target.getSize().width;
            Container container = target;

            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }

            targetWidth = container.getSize().width;

            if (targetWidth == 0)
                targetWidth = Integer.MAX_VALUE;

            int hgap = initialHGap;
            int vgap = initialVGap;
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;


            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();

            int rmembers = 0;


            int currentRowWidth = 0;
            boolean first = true;

            components.clear();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);

                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    if (i >= components.size()) {
                        components.add(i, m);
                    } else {
                        components.set(i, m);
                    }


                    if (rowWidth + d.width > maxWidth) {
                        if (first) {
                            rmembers = i;
                            backuprmembers = i;
                            currentRowWidth = rowWidth;
                            first = false;
                        }

                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    if (rowWidth != 0) {
                        rowWidth += hgap;
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            addRow(dim, rowWidth, rowHeight);

            if (rmembers == 0 || rmembers == 1)
                setHgap(initialHGap);
            else {

                setHgap((maxWidth - currentRowWidth) / (rmembers + 1) + initialHGap);
            }

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;


            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);

            if (scrollPane != null && target.isValid()) {
                dim.width -= (hgap + 1);
            }

            return dim;
        }
    }

    static public Component getComponent(int position) {
        if (components.size() == 0)
            return null;

        if (position >= components.size()) {
            return components.get(components.size() - 1);
        } else if (position < 0) {
            if (position == -1)
                return components.get(0);
            else
                return null;
        } else
            return components.get(position);
    }

    static public int getIndex(Component element) {
        return components.indexOf(element);
    }

    static public int getRowLength() {
        return backuprmembers;
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);

        if (dim.height > 0) {
            dim.height += getVgap();
        }

        dim.height += rowHeight;
    }
}


