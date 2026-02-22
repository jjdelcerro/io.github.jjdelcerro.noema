package io.github.jjdelcerro.chatagent.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.chatagent.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentTool;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.LogOptions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static io.github.jjdelcerro.chatagent.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;

/**
 * Herramienta que conecta el Agente con el sistema JavaRCS.
 * Permite al LLM consultar el historial de revisiones (autores, fechas y mensajes de commit)
 * de cualquier archivo bajo control de versiones.
 */
@SuppressWarnings("UseSpecificCatch")
public class FileHistoryTool extends AbstractAgentTool {

    public static final String TOOL_NAME = "file_history";
    
    public FileHistoryTool(Agent agent) {
        super(agent);
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Muestra el historial de revisiones de un archivo (RCS rlog). " +
                             "Úsalo para entender la evolución de un fichero, ver que versiones tiene, quién hizo cambios y leer los mensajes de commit.")
                .addParameter("path", JsonSchemaProperty.STRING, 
                        JsonSchemaProperty.description("Ruta del archivo (ej: 'src/Main.java')"))
                .build();
    }

    @Override
    public int getMode() {
        return AgentTool.MODE_READ;
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String relativePath = args.get("path");

            if (relativePath == null || relativePath.isBlank()) {
                return error("El parámetro 'path' es obligatorio.");
            }

            // 1. Validar sandbox
            Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath, PATH_ACCESS_READ);
            if (filePath == null) {
                return error("Acceso denegado o archivo fuera del sandbox.");
            }

            // 2. Configurar JavaRCS
            RCSManager rcsManager = RCSLocator.getRCSManager();
            LogOptions options = rcsManager.createLogOptions(filePath);
            
            // TODO: Opcional. podría añadir lógica para filtrar por revisión si el LLM lo pide, 
            // pero por defecto rlog muestra todo el árbol.
            
            // 3. Capturar la salida de LogCommand
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream newOut = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
                options.setOutputStream(newOut);
                RCSCommand<LogOptions> logCommand = rcsManager.create(options);            
                logCommand.execute(options);
                return baos.toString(StandardCharsets.UTF_8);
            }

        } catch (Exception e) {
            LOGGER.warn("Error ejecutando file_history sobre " + jsonArguments, e);
            return error("Error consultando historial RCS: " + e.getMessage());
        }
    }
}
