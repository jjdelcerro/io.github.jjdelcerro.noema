package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckinOptions;

public class FilePatchTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "file_patch";
  
  /*
### Una nota sobre el formato del parche
Los LLM a veces envían el parche "desnudo" (solo el bloque `@@ ... @@`). Para que `UnifiedDiffUtils` no proteste, a veces es necesario que el parche incluya las líneas de cabecera:
```diff
--- a/archivo
+++ b/archivo
@@ -1,1 +1,1 @@
-viejo
+nuevo
```
Si vemos que Devstral o modelos "pequeñós" nos dan errores de parseo, podemos añadir una pequeña lógica 
en Java que le "pegue" unas cabeceras genéricas al principio del String si no las tiene. 
Pero por ahora, prueba así; los modelos de 2025 suelen ser bastante buenos siguiendo el estándar.
    
   */
  public FilePatchTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Aplica un parche en formato Unified Diff (@@ ... @@) a un archivo.\n"
                    + "Usa esta herramienta para refactorizaciones, añadir métodos o cambios en múltiples bloques.\n"
                    + "Proporciona suficiente contexto en las líneas del parche para que sea robusto."
            )
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo"))
            .addParameter("patch", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El parche en formato unified diff"))
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
      String relativePath = args.get("path");
      String patchString = args.get("patch");

      Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath, PATH_ACCESS_WRITE);
      if (filePath == null) {
        LOGGER.info("Archivo no encontrado a acceso deneagado '" + relativePath + "'");
        return gson.toJson(Map.of("status", "error", "message", "Archivo no encontrado o acceso denegado."));
      }

      // 1. Leer archivo original
      List<String> originalLines = Files.readAllLines(filePath);

      // 2. Parsear el parche usando UnifiedDiffUtils (IMPORTANTE: es esta clase, no DiffUtils)
      List<String> patchLines = Arrays.asList(patchString.split("\n"));
      Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);

      // 3. Aplicar el parche usando DiffUtils
      List<String> patchedLines = DiffUtils.patch(originalLines, patch);

      if( Files.exists(filePath) ) {
        RCSManager rcsmanager = RCSLocator.getRCSManager();
        CheckinOptions opciones = rcsmanager.createCheckinOptions(filePath);
        opciones.setAuthor(this.getReasoningService().getModelName());
        opciones.setInit(true);
        RCSCommand ci = rcsmanager.create(opciones);
        ci.execute(opciones);
      }
      // 4. Guardar resultado
      Files.write(filePath, patchedLines);

      return gson.toJson(Map.of("status", "success", "message", "Parche aplicado correctamente en " + relativePath));

    } catch (PatchFailedException e) {
      LOGGER.warn("El parche no encaja con el contenido actual del archivo. Revisa el contexto.", e);
      return gson.toJson(Map.of("status", "error", "message", "El parche no encaja con el contenido actual del archivo. Revisa el contexto."));
    } catch (Exception e) {
      LOGGER.warn("Error aplicando parche, args=" + jsonArguments, e);
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }
}
