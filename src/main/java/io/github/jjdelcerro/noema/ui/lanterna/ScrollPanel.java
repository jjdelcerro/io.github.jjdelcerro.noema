package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Container;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.LayoutManager;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel contenedor con scroll vertical automático y viewport virtual.
 * Soporta componentes compuestos anidados (paneles dentro de paneles).
 */
public class ScrollPanel extends Panel {

    private static final TextColor COLOR_SCROLL_TRACK = TextColor.Factory.fromString("#292C34");
    private static final TextColor COLOR_SCROLL_THUMB = TextColor.Factory.fromString("#58A6FF");

    private int topLine = 0;

    public ScrollPanel() {
        this(new LinearLayout(Direction.VERTICAL));
    }

    public ScrollPanel(LayoutManager layoutManager) {
        super(layoutManager);
        setRenderer(new ScrollPanelRenderer());
    }

    public int getTopLine() {
        return topLine;
    }

    public void setTopLine(int topLine) {
        this.topLine = Math.max(0, topLine);
    }

    private class ScrollPanelRenderer implements ComponentRenderer<Panel> {

        @Override
        public TerminalSize getPreferredSize(Panel panel) {
            LayoutManager layoutManager = panel.getLayoutManager();
            List<Component> children = new ArrayList<>(panel.getChildren());
            return layoutManager.getPreferredSize(children);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Panel panel) {
            ThemeStyle style = panel.getThemeDefinition().getNormal();
            graphics.applyThemeStyle(style);
            graphics.fill(' '); // Limpiar fondo

            int viewWidth = graphics.getSize().getColumns();
            int viewHeight = graphics.getSize().getRows();

            if (viewWidth <= 0 || viewHeight <= 0) {
                return;
            }

            // 1. Calcular tamaño virtual sumando los componentes hijos
            LayoutManager layoutManager = panel.getLayoutManager();
            List<Component> children = new ArrayList<>(panel.getChildren());
            TerminalSize preferredSize = layoutManager.getPreferredSize(children);

            int virtualHeight = Math.max(viewHeight, preferredSize.getRows());
            boolean needsScrollbar = virtualHeight > viewHeight;
            int textWidth = needsScrollbar ? Math.max(1, viewWidth - 1) : viewWidth;

            // 2. Ejecutar la maquetación virtual
            layoutManager.doLayout(new TerminalSize(textWidth, virtualHeight), children);

            // 3. Rastreo RECURSIVO del foco para auto-scroll
            int focusedY = -1;
            int focusedHeight = 1;

            for (Component child : children) {
                if (isFocusedOrHasFocusedChild(child)) {
                    focusedY = child.getPosition().getRow();
                    focusedHeight = child.getSize().getRows();
                    break;
                }
            }

            if (focusedY >= 0) {
                if (focusedY < topLine) {
                    topLine = focusedY;
                } else if (focusedY + focusedHeight > topLine + viewHeight) {
                    topLine = focusedY + focusedHeight - viewHeight;
                }
            }

            int maxTopLine = Math.max(0, virtualHeight - viewHeight);
            topLine = Math.max(0, Math.min(topLine, maxTopLine));

            // 4. Dibujar componentes directamente en la pantalla desplazando la coordenada Y
            for (Component child : children) {
                if (!child.isVisible()) {
                    continue;
                }

                TerminalPosition pos = child.getPosition();
                TerminalSize size = child.getSize();

                int screenY = pos.getRow() - topLine;

                // Dibujar solo si el componente entra total o parcialmente en la pantalla
                if (screenY + size.getRows() > 0 && screenY < viewHeight) {
                    TextGUIGraphics childGraphics = graphics.newTextGraphics(
                            new TerminalPosition(pos.getColumn(), screenY),
                            size
                    );
                    child.draw(childGraphics);
                }
            }

            // 5. Dibujar la barra de scroll en la última columna si desborda
            if (needsScrollbar) {
                drawScrollbar(graphics, viewWidth - 1, viewHeight, virtualHeight, topLine);
            }
        }

        /**
         * Comprueba recursivamente si un componente o cualquiera de sus hijos anidados tiene el foco.
         */
        private boolean isFocusedOrHasFocusedChild(Component comp) {
            if (comp == null || !comp.isVisible()) {
                return false;
            }
            if (comp instanceof Interactable interactable && interactable.isFocused()) {
                return true;
            }
            if (comp instanceof Container container) {
                for (Component child : container.getChildren()) {
                    if (isFocusedOrHasFocusedChild(child)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void drawScrollbar(TextGUIGraphics graphics, int scrollCol, int viewHeight, int virtualHeight, int topLine) {
            int thumbHeight = Math.max(1, (int) Math.round((double) viewHeight * viewHeight / virtualHeight));
            if (thumbHeight > viewHeight) {
                thumbHeight = viewHeight;
            }

            int maxTopLine = virtualHeight - viewHeight;
            double scrollRatio = (maxTopLine > 0) ? (double) topLine / maxTopLine : 0.0;

            int maxThumbTop = viewHeight - thumbHeight;
            int thumbTop = (int) Math.round(scrollRatio * maxThumbTop);
            thumbTop = Math.max(0, Math.min(thumbTop, maxThumbTop));

            for (int row = 0; row < viewHeight; row++) {
                boolean isThumb = (row >= thumbTop && row < thumbTop + thumbHeight);
                if (isThumb) {
                    graphics.setForegroundColor(COLOR_SCROLL_THUMB);
                    graphics.putString(scrollCol, row, "█");
                } else {
                    graphics.setForegroundColor(COLOR_SCROLL_TRACK);
                    graphics.putString(scrollCol, row, "│");
                }
            }
        }
    }
}
