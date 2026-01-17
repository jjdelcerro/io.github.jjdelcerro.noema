package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class FileSearchAndReplaceTool implements AgenteTool {
/*
TODO: Para que el modelo no se frustre (porque a veces fallan por un espacio o un tabulador al copiar el oldText), es recomendable añadir una pequeña instrucción en el System Prompt:

"Cuando uses file_edit, asegúrate de incluir al menos 2 o 3 líneas de contexto en el oldText para que sea único. Copia el texto exactamente como aparece en el archivo, respetando cada espacio y salto de línea."   
*/
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_search_and_replace")
                .description("Edita un archivo reemplazando un bloque de texto específico por otro.")
                .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo."))
                .addParameter("oldText", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El bloque de texto EXACTO que se desea cambiar (debe ser único en el archivo)."))
                .addParameter("newText", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El nuevo texto que reemplazará al anterior."))
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
            Path filePath = rootPath.resolve(args.get("path")).normalize();
            String oldText = args.get("oldText");
            String newText = args.get("newText");

            if (!filePath.startsWith(rootPath)) {
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
