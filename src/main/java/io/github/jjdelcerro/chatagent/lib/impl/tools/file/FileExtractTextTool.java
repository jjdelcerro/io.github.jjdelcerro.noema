package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.tika.Tika;
//import org.apache.tika.Tika;
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Map;

public class FileExtractTextTool implements AgenteTool {
/*
    TODO: Implementar esto cuando se introduzca Tika (paginacion?)
*/
    
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();
    private final Tika tika = new Tika(); // Tika centraliza la lógica de extracción

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_extract_text")
                .description("Extrae el texto legible de archivos complejos (PDF, DOCX, XLSX, etc).")
                .addParameter("path", JsonSchemaProperty.STRING, 
                        JsonSchemaProperty.description("Ruta relativa del archivo binario del que extraer texto."))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String relativePath = args.get("path");
            Path filePath = rootPath.resolve(relativePath).normalize();

            // Seguridad
            if (!filePath.startsWith(rootPath) || !Files.exists(filePath)) {
                return gson.toJson(Map.of("status", "error", "message", "Archivo no encontrado o acceso denegado."));
            }

            // Tika hace la magia aquí
            // Abre el archivo, detecta el parser adecuado, extrae metadatos y contenido
            // y lo devuelve como un String plano.
            String extractedText = tika.parseToString(filePath);

            return gson.toJson(Map.of(
                    "status", "success",
                    "path", relativePath,
                    "content", extractedText
            ));

        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", "Error en la extracción: " + e.getMessage()));
        }
    }
}
