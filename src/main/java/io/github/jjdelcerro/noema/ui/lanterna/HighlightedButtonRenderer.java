package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Button.ButtonRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

public class HighlightedButtonRenderer implements ButtonRenderer {

    private final char targetChar;

    public HighlightedButtonRenderer(char targetChar) {
        this.targetChar = Character.toLowerCase(targetChar);
    }

    @Override
    public TerminalSize getPreferredSize(Button button) {
        String label = button.getLabel();
        if (label == null) {
            label = "";
        }
        // Ancho de la etiqueta + 4 posiciones para "< " y " >"
        return new TerminalSize(label.length() + 4, 1);
    }

    @Override
    public TerminalPosition getCursorLocation(Button button) {
        return null; // Los botones no muestran un cursor de texto intermitente
    }

    @Override
    public void drawComponent(TextGUIGraphics graphics, Button button) {
        // 1. Obtener estilo según el estado (Deshabilitado, Enfocado/Seleccionado, Normal)
        ThemeStyle style;
        if (!button.isEnabled()) {
            style = button.getThemeDefinition().getInsensitive();
        } else if (button.isFocused()) {
            style = button.getThemeDefinition().getSelected();
        } else {
            style = button.getThemeDefinition().getNormal();
        }

        graphics.applyThemeStyle(style);
        graphics.fill(' '); // Limpiar el fondo del botón

        // 2. Corchetes según si tiene el foco
        String openBracket  = button.isFocused() ? "[ " : "  ";
        String closeBracket = button.isFocused() ? " ]" : "  ";

        graphics.putString(0, 0, openBracket);

        // 3. Dibujar texto resaltando la letra clave
        String label = button.getLabel();
        if (label == null) {
            label = "";
        }

        int x = openBracket.length();
        boolean highlighted = false;

        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);

            if (!highlighted && Character.toLowerCase(ch) == targetChar) {
                graphics.enableModifiers(SGR.BOLD, SGR.UNDERLINE);
                graphics.putString(x, 0, String.valueOf(ch));
                graphics.disableModifiers(SGR.BOLD, SGR.UNDERLINE);
                highlighted = true; // Solo la primera ocurrencia
            } else {
                graphics.putString(x, 0, String.valueOf(ch));
            }
            x++;
        }

        graphics.putString(x, 0, closeBracket);
    }
}
