package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasePane;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Container;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

public class HistoryChatBox extends TextBox {

    private final ColoredHistoryRenderer historyRenderer;
    private WindowListener windowListener;

    public HistoryChatBox() {
        super(new TerminalSize(0, 0), Style.MULTI_LINE);
        this.setReadOnly(false);
        this.setTheme(LanternaUtils.getMainTheme());

        this.historyRenderer = new ColoredHistoryRenderer();
        this.setRenderer(this.historyRenderer);
    }

    public ColoredHistoryRenderer getHistoryRenderer() {
        return historyRenderer;
    }

    public boolean isShowSystemLogs() {
        return historyRenderer.isShowSystemLogs();
    }

    public void setShowSystemLogs(boolean show) {
        historyRenderer.setShowSystemLogs(show);
        invalidate();
    }

    public boolean isShowErrorLogs() {
        return historyRenderer.isShowErrorLogs();
    }

    public void setShowErrorLogs(boolean show) {
        historyRenderer.setShowErrorLogs(show);
        invalidate();
    }

    public void appendUserMessage(String text) {
        addHistoryLine("[USR] " + text);
    }

    public void appendModelResponse(String text) {
        addHistoryLine("[RES] " + text);
    }

    public void appendModelThinking(String text) {
        addHistoryLine("[THI] " + text);
    }

    public void appendSystemLog(String text) {
        addHistoryLine("[SIS] " + text);
    }

    public void appendSystemError(String text) {
        addHistoryLine("[ERR] " + text);
    }

    public void addHistoryLine(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        this.addLine(text);
        int totalLines = this.getLineCount();
        this.setCaretPosition(totalLines - 1, 0);
    }

    public void clearHistory() {
        this.setText("");
    }

    public String generateMarkdownContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Conversacion Noema Agent\n\n");
        sb.append("_Exportada el: ").append(LocalDateTime.now()).append("_\n\n");
        sb.append("---\n\n");

        String rawText = this.getText();
        if (rawText != null) {
            for (String line : rawText.split("\n")) {
                if (line.startsWith("[USR] ")) {
                    sb.append("### 👤 Usuario\n").append(line.substring(6)).append("\n\n");
                } else if (line.startsWith("[RES] ")) {
                    sb.append("### 🤖 Model\n").append(line.substring(6)).append("\n\n");
                } else if (line.startsWith("[THI] ")) {
                    sb.append("### 🤖 Model thinking\n").append(line.substring(6)).append("\n\n");
                } else if (line.startsWith("[SIS] ")) {
                    sb.append("> **[SIS]** ").append(line.substring(6)).append("\n\n");
                } else if (line.startsWith("[ERR] ")) {
                    sb.append("> **[ERR]** ").append(line.substring(6)).append("\n\n");
                } else {
                    sb.append(line).append("\n");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.UNHANDLED;
        }

        // 1. Manejo de entrada de raton
        if (keyStroke instanceof MouseAction mouseAction) {
            MouseActionType actionType = mouseAction.getActionType();
            if (actionType == MouseActionType.SCROLL_UP) {
                scrollBy(-3);
                return Result.HANDLED;
            }
            if (actionType == MouseActionType.SCROLL_DOWN) {
                scrollBy(3);
                return Result.HANDLED;
            }
            // Consumir clics y arrastres para evitar reposicionar el cursor
            return Result.HANDLED;
        }

        // 2. Whitelist para teclado: solo permitir teclas de navegacion
        KeyType keyType = keyStroke.getKeyType();
        switch (keyType) {
            case ArrowUp:
            case ArrowDown:
            case PageUp:
            case PageDown:
            case Home:
            case End:
                return super.handleKeyStroke(keyStroke);
            default:
                // Consumir cualquier otra tecla (letras, numeros, borrado, etc.)
                return Result.HANDLED;
        }
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
                int delta = (actionType == MouseActionType.SCROLL_UP) ? -3 : 3;
                scrollBy(delta);
                hasHandled.set(true);
            }
        }
    }

    private void scrollBy(int delta) {
        int totalLines = this.getLineCount();
        if (totalLines == 0) {
            return;
        }
        TerminalPosition caret = this.getCaretPosition();
        int currentRow = caret.getRow();
        int newRow = Math.max(0, Math.min(totalLines - 1, currentRow + delta));
        this.setCaretPosition(newRow, 0);
        invalidate();
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
}
