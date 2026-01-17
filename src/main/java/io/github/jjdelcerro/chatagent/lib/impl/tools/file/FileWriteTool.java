package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public class FileWriteTool implements AgenteTool {
/*
    TODO: Para las herramientas de escritura deberia de preguntar antes de usarlas.
    TODO: Hay que tener en cuenta estas consideraciones.
    

### ¿Por qué esta herramienta es "peligrosa" en tu arquitectura?

Al integrar esta herramienta en tu sistema de **Memoria Híbrida Determinista**, debes tener en cuenta un par de cosas que justifican tu recelo:

1.  **El problema del "Estado Real" vs "Memoria":** Si el agente escribe un archivo y luego la compactación falla o hay un error de base de datos, el archivo en el disco habrá cambiado pero el agente podría "olvidar" que lo hizo (o cómo lo hizo). Esto rompe el determinismo.
2.  **Truncamiento en la persistencia:** Como el contenido escrito puede ser muy grande, tu `SourceOfTruthImpl` lo truncará en la BD (el límite de 2KB). El `MemoryManager` verá en el historial que se escribió algo, pero no sabrá exactamente *qué* se escribió a menos que el agente lo haya mencionado antes en su `text_model_thinking`.

### Mi sugerencia de "Arquitecto": El patrón de Pre-lectura

Para que esta herramienta no rompa la coherencia de tu experimento con **Devstral**, yo le añadiría una instrucción al `SystemPrompt` del `ConversationAgent`:

> *"Antes de usar `file_write`, debes usar `file_read` sobre el archivo (si existe) para tener una copia del estado anterior en el historial. Esto garantiza que la memoria consolidada pueda reconstruir la evolución del código."*

De esta forma, el `MemoryManager` (DeepSeek R1) podrá narrar en "El Viaje": *"El agente leyó el fichero X {cite: 200}, identificó un error en la línea 40, y procedió a sobrescribirlo con la versión corregida {cite: 201}"*.
*/      
        
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_write")
                .description("Escribe o sobrescribe un archivo con el contenido proporcionado. Crea carpetas automáticamente.")
                .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo (ej: 'src/main/resources/config.json')"))
                .addParameter("content", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Contenido completo que se escribirá en el archivo."))
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
            String content = args.get("content");

            if (relativePath == null || content == null) {
                return gson.toJson(Map.of("status", "error", "message", "Faltan parámetros: path o content"));
            }

            Path filePath = rootPath.resolve(relativePath).normalize();

            // SEGURIDAD: Impedir Path Traversal (escribir fuera del proyecto)
            if (!filePath.startsWith(rootPath)) {
                return gson.toJson(Map.of("status", "error", "message", "Acceso denegado: No se permite escribir fuera del directorio del proyecto."));
            }

            // Crear directorios padre si no existen (comportamiento mkdir -p)
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }

            // Escritura atómica (opcionalmente podrías usar StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            return gson.toJson(Map.of(
                    "status", "success",
                    "path", relativePath,
                    "bytes_written", content.getBytes(StandardCharsets.UTF_8).length
            ));

        } catch (AccessDeniedException e) {
            return gson.toJson(Map.of("status", "error", "message", "Permiso denegado por el Sistema Operativo."));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }
}
