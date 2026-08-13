package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;

public class LanternaUtils {

    // Paleta de colores centralizada
    public static final TextColor BG_DARK      = TextColor.Factory.fromString("#18191A"); // Fondo principal oscuro
    public static final TextColor BG_INPUT_BAR = TextColor.Factory.fromString("#33373B"); // Fondo gris destacado del input
    public static final TextColor TEXT_WHITE   = TextColor.Factory.fromString("#E1E4E8"); // Texto normal
    public static final TextColor TEXT_BRIGHT  = TextColor.Factory.fromString("#FFFFFF"); // Texto seleccionado brillante

    private static SimpleTheme mainTheme;
    private static SimpleTheme inputTheme;

    /**
     * Retorna el tema principal oscuro para ventanas, diálogos, historial y menús con resalte de selección
     */
    public static synchronized SimpleTheme getMainTheme() {
        if (mainTheme == null) {
            mainTheme = SimpleTheme.makeTheme(
                    true,         // activeIsBold (negrita en la opción seleccionada)
                    TEXT_WHITE,   // baseForeground
                    BG_DARK,      // baseBackground (#18191A)
                    TEXT_WHITE,   // editableForeground
                    BG_DARK,      // editableBackground (#18191A - Para que el historial use el fondo oscuro)
                    TEXT_BRIGHT,  // selectedForeground
                    BG_INPUT_BAR, // selectedBackground (#33373B - Resalte de selección en menús)
                    BG_DARK       // guiBackground
            );
        }
        return mainTheme;
    }

    /**
     * Tema para la caja de entrada de texto del usuario (prompt)
     */
    public static synchronized SimpleTheme getInputTheme() {
        if (inputTheme == null) {
            inputTheme = SimpleTheme.makeTheme(
                    true,
                    TEXT_WHITE,
                    BG_INPUT_BAR,
                    TEXT_WHITE,
                    BG_INPUT_BAR, // editableBackground (#33373B - Fondo gris destacado para el input)
                    TEXT_BRIGHT,
                    BG_INPUT_BAR,
                    BG_DARK
            );
        }
        return inputTheme;
    }
}
