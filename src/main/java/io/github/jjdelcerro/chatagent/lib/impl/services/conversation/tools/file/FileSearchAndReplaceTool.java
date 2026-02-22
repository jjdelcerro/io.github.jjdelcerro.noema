package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.chatagent.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;

public class FileSearchAndReplaceTool extends AbstractAgentTool {

  public FileSearchAndReplaceTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("file_search_and_replace")
            .description("Edita un archivo reemplazando un bloque de texto específico por otro.\n"
                    + "Usa esta herramienta para cambios simples (una línea, un valor, un nombre), es más rápido y seguro que "+FilePatchTool.TOOL_NAME+".\n" +
"\n"
                    + "Asegúrate de incluir al menos 2 o 3 líneas de contexto en el oldText para que sea único. Copia el texto exactamente como aparece en el archivo, respetando cada espacio y salto de línea."
            )
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo."))
            .addParameter("oldText", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El bloque de texto EXACTO que se desea cambiar (debe ser único en el archivo)."))
            .addParameter("newText", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El nuevo texto que reemplazará al anterior."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String oldText = args.get("oldText");
      String newText = args.get("newText");
      Path filePath = this.agent.getAccessControl().resolvePathOrNull(args.get("path"), PATH_ACCESS_WRITE);
      if (filePath == null) {
        return gson.toJson(Map.of("status", "error", "message", "Acceso denegado."));
      }

      String content = Files.readString(filePath, StandardCharsets.UTF_8);

      // Verificamos si el texto original existe
      if (!content.contains(oldText)) {
        return gson.toJson(Map.of("status", "error",
                "message", "No se encontró el bloque 'oldText' en el archivo. El texto debe ser idéntico, incluyendo espacios e indentación."));
      }

      // Verificamos si el texto original es único para evitar reemplazos accidentales en sitios que no tocan
      int firstIndex = content.indexOf(oldText);
      if (content.indexOf(oldText, firstIndex + 1) != -1) {
        return gson.toJson(Map.of("status", "error",
                "message", "El bloque 'oldText' no es único. Proporciona un bloque más largo o específico."));
      }

      // Realizamos el reemplazo
      String newContent = content.replace(oldText, newText);
      if (Files.exists(filePath)) {
        RCSManager rcsmanager = RCSLocator.getRCSManager();
        CheckinOptions opciones = rcsmanager.createCheckinOptions(filePath);
        opciones.setAuthor(this.getConversationService().getModelName());
        opciones.setInit(true);
        RCSCommand ci = rcsmanager.create(opciones);
        ci.execute(opciones);
      }
      Files.writeString(filePath, newContent, StandardCharsets.UTF_8);

      return gson.toJson(Map.of(
              "status", "success",
              "path", args.get("path"),
              "message", "Archivo editado correctamente."
      ));

    } catch (Exception e) {
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }
}
