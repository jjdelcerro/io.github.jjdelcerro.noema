package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;

public class LanternaUtils {

  // --- PALETA BASE ---
  public static final TextColor COLOR_BASE_BG = TextColor.Factory.fromString("#171A21"); 
  public static final TextColor COLOR_BASE_BG_INPUT = TextColor.Factory.fromString("#1D2029"); 
  public static final TextColor COLOR_BASE_TEXT = TextColor.Factory.fromString("#9296A5"); 
  public static final TextColor COLOR_BASE_TEXT_BRIGHT = TextColor.Factory.fromString("#E9EAF0");
  public static final TextColor COLOR_BASE_TEXT_MUTED = TextColor.Factory.fromString("#9296A5"); 

  // --- FONDO ESPECIFICO DEL HISTORIAL DE CHAT ---
  public static final TextColor COLOR_CHATHISTORY_BG = TextColor.Factory.fromString("#0D0F13"); 

  // --- ROLES DE CONVERSACION ---
  public static final TextColor COLOR_ROLE_USER = TextColor.Factory.fromString("#B8A0FF"); 
  public static final TextColor COLOR_ROLE_MODEL = TextColor.Factory.fromString("#E9EAF0");
  public static final TextColor COLOR_ROLE_LOG = TextColor.Factory.fromString("#9296A5"); 
  public static final TextColor COLOR_ROLE_ERR = TextColor.Factory.fromString("#DA6575");

  // --- SINTAXIS Y MARKDOWN ---
  public static final TextColor COLOR_MARKDOWN_HEADING = TextColor.Factory.fromString("#B8A0FF");
  public static final TextColor COLOR_MARKDOWN_CODE_INLINE = TextColor.Factory.fromString("#79C0FF");
  public static final TextColor COLOR_MARKDOWN_CODE_BLOCK = TextColor.Factory.fromString("#A5D6FF");

  // --- BARRAS DE SCROLL ---
  public static final TextColor COLOR_SCROLL_TRACK = TextColor.Factory.fromString("#9296A5");
  public static final TextColor COLOR_SCROLL_THUMB = TextColor.Factory.fromString("#9296A5");

  private static SimpleTheme mainTheme;
  private static SimpleTheme inputTheme;

  public static synchronized SimpleTheme getMainTheme() {
    if (mainTheme == null) {
      mainTheme = SimpleTheme.makeTheme(true,
              COLOR_BASE_TEXT,
              COLOR_BASE_BG,
              COLOR_BASE_TEXT,
              COLOR_BASE_BG,
              COLOR_BASE_TEXT_BRIGHT,
              COLOR_BASE_BG_INPUT,
              COLOR_BASE_BG
      );
    }
    return mainTheme;
  }

  public static synchronized SimpleTheme getInputTheme() {
    if (inputTheme == null) {
      inputTheme = SimpleTheme.makeTheme(true,
              COLOR_BASE_TEXT,
              COLOR_BASE_BG_INPUT,
              COLOR_BASE_TEXT,
              COLOR_BASE_BG_INPUT,
              COLOR_BASE_TEXT_BRIGHT,
              COLOR_BASE_BG_INPUT,
              COLOR_BASE_BG
      );
    }
    return inputTheme;
  }
}
