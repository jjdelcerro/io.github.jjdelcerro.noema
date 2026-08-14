package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.BasePane;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Container;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.LayoutManager;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Panel contenedor con scroll vertical automático, viewport virtual,
 * auto-scroll guiado por foco de teclado y soporte para rueda de ratón vía WindowListener.
 */
public class ScrollPanel extends Panel {

    private static final TextColor COLOR_SCROLL_TRACK = TextColor.Factory.fromString("#292C34");
    private static final TextColor COLOR_SCROLL_THUMB = TextColor.Factory.fromString("#58A6FF");

    private int topLine = 0;
    private WindowListener windowListener;

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

    @Override
    public synchronized void onAdded(Container container) {
        super.onAdded(container);
        if (container != null && container.getBasePane() instanceof Window window) {
            registerWindowListener(window);
        }
    }

    private synchronized void registerWindowListener(Window window) {
        if (windowListener != null || window == null) {
            return;
        }
        windowListener = new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasHandled) {
                if (keyStroke instanceof MouseAction mouseAction) {
                    handleMouseScroll(mouseAction, hasHandled);
                }
            }
        };
        window.addWindowListener(windowListener);
    }

    private void handleMouseScroll(MouseAction mouseAction, AtomicBoolean hasHandled) {
        MouseActionType actionType = mouseAction.getActionType();
        if (actionType == MouseActionType.SCROLL_UP || actionType == MouseActionType.SCROLL_DOWN) {
            if (isMouseOverComponent(this, mouseAction.getPosition())) {
                int delta = (actionType == MouseActionType.SCROLL_UP) ? -2 : 2;
                setTopLine(topLine + delta);
                invalidate(); // Notificar a Lanterna para solicitar repintado
                hasHandled.set(true);
            }
        }
    }

    private boolean isMouseOverComponent(Component comp, TerminalPosition mousePos) {
        if (comp == null || !comp.isVisible() || mousePos == null) {
            return false;
        }
        BasePane basePane = comp.getBasePane();
        if (basePane == null) {
            return false;
        }
        TerminalPosition compAbsPos = comp.toBasePane(TerminalPosition.TOP_LEFT_CORNER);
        TerminalSize compSize = comp.getSize();

        int mouseCol = mousePos.getColumn();
        int mouseRow = mousePos.getRow();

        return mouseCol >= compAbsPos.getColumn()
                && mouseCol < compAbsPos.getColumn() + compSize.getColumns()
                && mouseRow >= compAbsPos.getRow()
                && mouseRow < compAbsPos.getRow() + compSize.getRows();
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
            // Garantizar el registro del listener en el primer renderizado por si onAdded se ejecutó antes de asociarse a la ventana
            if (windowListener == null && panel.getBasePane() instanceof Window window) {
                registerWindowListener(window);
            }

            ThemeStyle style = panel.getThemeDefinition().getNormal();
            graphics.applyThemeStyle(style);
            graphics.fill(' '); // Limpiar el fondo del panel

            int viewWidth = graphics.getSize().getColumns();
            int viewHeight = graphics.getSize().getRows();

            if (viewWidth <= 0 || viewHeight <= 0) {
                return;
            }

            // 1. Calcular el tamaño virtual preferido sumando los componentes hijos
            LayoutManager layoutManager = panel.getLayoutManager();
            List<Component> children = new ArrayList<>(panel.getChildren());
            TerminalSize preferredSize = layoutManager.getPreferredSize(children);

            int virtualHeight = Math.max(viewHeight, preferredSize.getRows());
            boolean needsScrollbar = virtualHeight > viewHeight;
            int textWidth = needsScrollbar ? Math.max(1, viewWidth - 1) : viewWidth;

            // 2. Ejecutar la maquetación virtual sobre el ancho útil del texto
            layoutManager.doLayout(new TerminalSize(textWidth, virtualHeight), children);

            // 3. Rastreo RECURSIVO del foco para auto-scroll por teclado (TAB / Flechas)
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
                for (Component child : new ArrayList<>(container.getChildren())) {
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
