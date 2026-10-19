package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.utils.FileSearchAndReplaceUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.FileSearchAndReplaceUtils.SearchAndReplaceResult;

/**
 * Herramienta de edicion de archivos mediante busqueda y sustitucion de
 * bloques. Delega la ejecucion, estrategias de coincidencia y salvaguarda en
 * FileSearchAndReplaceUtils.
 */
public class FileSearchAndReplaceTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_search_and_replace";

  public FileSearchAndReplaceTool(Agent agent) {
    super(agent);
  }

  @Override
  public boolean isAvailableByDefault() {
    return false;
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("""
Edita un archivo reemplazando un bloque de texto existente ('oldText') por uno nuevo ('newText').

CARACTERISTICAS Y REGLAS DE USO:
1. Exactitud y contexto: Incluye siempre 2 o 3 lineas de contexto circundante en 'oldText' para garantizar que el bloque sea unico dentro del archivo.
2. Tolerancia automatica: Si la sangria o los espacios no coinciden de forma exacta, el motor adaptara automaticamente el nuevo texto a la indentacion original del archivo.
3. Para eliminar codigo: Especifica el bloque a borrar en 'oldText' y envia 'newText' como cadena vacia.
4. Auto-recuperacion: Si el bloque no se localiza, la herramienta te devolvera las lineas reales del archivo mas parecidas para que puedas corregir tu peticion.
5. Seguridad: Todo cambio realiza una copia de seguridad automatica en el historial de JavaRCS antes de modificar el disco.
""")
            .addStringParameter("path", false, "Ruta absoluta del archivo a editar.")
            .addStringParameter("oldText", false, "Bloque de texto exacto a sustituir (debe ser unico en el archivo).")
            .addStringParameter("newText", false, "Nuevo texto que reemplazara a 'oldText'. Puede ser vacio para borrar.");
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      Args args = gson.fromJson(jsonArguments, Args.class);
      if (args == null) {
        return error("Argumentos invalidos.");
      }

      SearchAndReplaceResult result = FileSearchAndReplaceUtils.searchAndReplace(
              this.agent.getAccessControl(),
              args.path,
              args.oldText,
              args.newText
      );

      return this.result(result.toJson());

    } catch (Exception e) {
      LOGGER.warn("Error ejecutando file_search_and_replace sobre: " + jsonArguments, e);
      return error("Error interno al procesar edicion: " + e.getMessage());
    }
  }

  private static class Args {

    String path;
    String oldText;
    String newText;
  }
}
