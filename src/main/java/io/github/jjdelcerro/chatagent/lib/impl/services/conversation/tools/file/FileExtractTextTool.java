package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.nio.file.Path;
import java.util.Map;
import org.apache.tika.Tika;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
//import org.apache.tika.Tika;
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Map;

public class FileExtractTextTool implements AgentTool {
/*
    TODO: Implementar esto cuando se introduzca Tika (paginacion?)
*/
    
    private final Gson gson = new Gson();
    private final Tika tika = new Tika(); // Tika centraliza la lógica de extracción

    private final Agent agent;
    
    public FileExtractTextTool(Agent agent) {
      this.agent = agent;
    }
    
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

            Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath,PATH_ACCESS_READ);
            if (filePath == null) {
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
