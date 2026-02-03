package io.github.jjdelcerro.chatagent.lib.impl.docmapper;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import io.github.jjdelcerro.chatagent.lib.impl.docmapper.TextContent.TextLine;
        
/**
 *
 * @author jjdelcerro
 */
class TextContent extends ArrayList<TextLine> {
  
  public static class TextLine {

    int lineNumber;
    int lineOffset;
    String text;

    public TextLine(int lineNumber, int lineOffset, String text) {
    }
  }

  public TextContent() {
  }

  public void load(Path document, Charset UTF_8) {
    // TODO: Carga el documento linea a linea calculando el numero de linea y el offset y creando TextLine y añadiendolos a TextContent
  }

  public String toCSV() {
    // TODO: genera un texto en formato csv con su cabecera incluida con dos columnas, "LineNumber" y "text". Cuidado con escapar las comillas dobles y para los saltos de linea escaparlos con "\n".
    return null;
  }
  
}
