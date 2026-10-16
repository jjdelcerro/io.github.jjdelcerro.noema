package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostEditorFormat extends AbstractHandler {

  public HandlerPostEditorFormat(Agent agent) {
    super(agent);
  }

  /**
   * Formatea el contenido recibido en el cuerpo de la peticion.
   *
   * Request: POST /api/editor/format?ext=java Content-Type: text/plain;
   * charset=UTF-8 Body: contenido crudo del buffer del editor. Response:
   * Content-Type: text/plain; charset=UTF-8 Body: contenido formateado.
   *
   */
  @Override
  public void handle(Context ctx) throws Exception {
    String content = ctx.body();
    content = this.formatJavaCode(content);
    ctx.contentType("text/plain; charset=UTF-8").result(content);
  }
//   private String formatJavaCode(String sourceCode) throws FormatterException {
//        // Cargar la implementación del servicio
//        FormatterService formatterService = ServiceLoader.load(FormatterService.class)
//          .findFirst()
//          .orElseThrow(
//              () -> new IllegalStateException(
//                "No se encontró una implementación de FormatterService en el classpath."
//              )
//          );
//
//        return formatterService.formatSourceReflowStringsAndFixImports(sourceCode);
//    }  

  private String formatJavaCode(String sourceCode) throws FormatterException {
    // Crear el formateador con el estilo de Palantir
    Formatter formatter = Formatter.createFormatter(
            JavaFormatterOptions.builder()
                    .style(JavaFormatterOptions.Style.PALANTIR)
                    .build()
    );

    // Definir el rango a formatear: todo el String
    ImmutableList<Range<Integer>> ranges = ImmutableList.of(
            Range.closedOpen(0, sourceCode.length())
    );

    // Formatear y devolver el resultado
    return formatter.formatSource(sourceCode, ranges);
  }

}
