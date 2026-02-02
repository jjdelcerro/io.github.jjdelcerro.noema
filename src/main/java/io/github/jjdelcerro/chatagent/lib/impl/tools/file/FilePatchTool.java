package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class FilePatchTool implements AgenteTool {
    /*

    TODO: ¿Cómo instruir al Agente para que elija entre FilePatchTool y FileSearchAndReplaceTool?

Para que tu PoC funcione bien, te sugiero añadir esto al `getBaseSystemPrompt()` de tu `ConversationAgent`:

> **Estrategia de Edición:**
> 1. Para cambios simples (una línea, un valor, un nombre): usa `file_search_replace`. Es más rápido y seguro.
> 2. Para refactorizaciones, añadir métodos o cambios en múltiples bloques: usa `file_patch`. Proporciona suficiente contexto en las líneas del parche para que sea robusto.

### Un pequeño "hack" de visualización:
Si quieres que tu consola en Java se vea como la de **Gemini CLI**, puedes interceptar la respuesta de la herramienta en tu `ConversationAgent` y, si es un `file_patch`, imprimir por consola el String del parche usando códigos de color ANSI (Rojo para `-`, Verde para `+`). 

¡Ver cómo el agente "parchea" su propio código fuente en tiempo real es la mejor forma de validar que tu PoC tiene "vida"! 

### Una nota sobre el formato del parche
Los LLM a veces envían el parche "desnudo" (solo el bloque `@@ ... @@`). Para que `UnifiedDiffUtils` no proteste, a veces es necesario que el parche incluya las líneas de cabecera:
```diff
--- a/archivo
+++ b/archivo
@@ -1,1 +1,1 @@
-viejo
+nuevo
```
Si ves que **Devstral** te da errores de parseo, podemos añadir una pequeña lógica en Java que le "pegue" unas cabeceras genéricas al principio del String si no las tiene. Pero por ahora, prueba así; los modelos de 2025 suelen ser bastante buenos siguiendo el estándar.
    
    */
    private final Gson gson = new Gson();

    private final Agent agent;
    
    public FilePatchTool(Agent agent) {
      this.agent = agent;
    }
    
    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_patch")
                .description("Aplica un parche en formato Unified Diff (@@ ... @@) a un archivo.")
                .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo"))
                .addParameter("patch", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El parche en formato unified diff"))
                .build();
    }

    @Override
    public int getMode() {
        return AgenteTool.MODE_WRITE;
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String relativePath = args.get("path");
            String patchString = args.get("patch");

            Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath,PATH_ACCESS_WRITE);
            if (filePath == null) {
                return gson.toJson(Map.of("status", "error", "message", "Archivo no encontrado o acceso denegado."));
            }            
            
            // 1. Leer archivo original
            List<String> originalLines = Files.readAllLines(filePath);

            // 2. Parsear el parche usando UnifiedDiffUtils (IMPORTANTE: es esta clase, no DiffUtils)
            List<String> patchLines = Arrays.asList(patchString.split("\n"));
            Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);

            // 3. Aplicar el parche usando DiffUtils
            List<String> patchedLines = DiffUtils.patch(originalLines, patch);

            // 4. Guardar resultado
            Files.write(filePath, patchedLines);

            return gson.toJson(Map.of("status", "success", "message", "Parche aplicado correctamente en " + relativePath));

        } catch (PatchFailedException e) {
            return gson.toJson(Map.of("status", "error", "message", "El parche no encaja con el contenido actual del archivo. Revisa el contexto."));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
