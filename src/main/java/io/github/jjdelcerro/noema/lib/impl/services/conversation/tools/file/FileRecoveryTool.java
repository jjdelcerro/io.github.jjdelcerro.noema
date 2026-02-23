package io.github.jjdelcerro.noema.lib.impl.services.conversation.tools.file;

import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.javarcs.lib.RCSCommand;
import io.github.jjdelcerro.javarcs.lib.RCSLocator;
import io.github.jjdelcerro.javarcs.lib.RCSManager;
import io.github.jjdelcerro.javarcs.lib.commands.CheckoutOptions;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_WRITE;

/**
 * Herramienta para recuperar versiones antiguas de un archivo usando JavaRCS.
 * Ejecuta el comando 'co' (checkout) para sobrescribir el archivo de trabajo actual
 * con una revisión específica del historial.
 */
@SuppressWarnings("UseSpecificCatch")
public class FileRecoveryTool extends AbstractAgentTool {

    public FileRecoveryTool(Agent agent) {
        super(agent);
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_recovery")
                .description("Recupera una versión específica de un archivo desde el historial (RCS co).\n" 
                        + "¡CUIDADO! Esta acción sobrescribirá el contenido actual del archivo con la versión solicitada.\n"
                        + "Utiliza la herramienta "+FileHistoryTool.TOOL_NAME+" para consultar las versiones de un archivo."
                        
                )
                .addParameter("path", JsonSchemaProperty.STRING, 
                        JsonSchemaProperty.description("Ruta relativa del archivo a recuperar."))
                .addParameter("revision", JsonSchemaProperty.STRING, 
                        JsonSchemaProperty.description("Número de revisión a extraer (ej: '1.1', '1.2')."))
                .build();
    }

    @Override
    public int getMode() {
        // Marcamos como WRITE porque sobrescribe un archivo existente
        return AgentTool.MODE_WRITE;
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String relativePath = args.get("path");
            String revision = args.get("revision");

            if (relativePath == null || revision == null) {
                return error("Los parámetros 'path' y 'revision' son obligatorios.");
            }

            // 1. Validar sandbox (con permiso de escritura)
            Path filePath = this.agent.getAccessControl().resolvePathOrNull(relativePath, PATH_ACCESS_WRITE);
            if (filePath == null) {
                return error("Acceso denegado o archivo fuera del sandbox.");
            }


            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream newOut = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
                // 2. Configurar JavaRCS
                RCSManager rcsManager = RCSLocator.getRCSManager();
                CheckoutOptions options = rcsManager.createCheckoutOptions(filePath);

                options.setRevision(revision);
                options.setForce(true); // Forzamos la sobrescritura porque el Agente ya pidió confirmación previa
                options.setQuiet(false);
                options.setPipeOut(false);
                options.setOutputStream(newOut);
                
                RCSCommand<CheckoutOptions> checkoutCommand = rcsManager.create(options);
                checkoutCommand.execute(options);
                
                return gson.toJson(Map.of(
                    "status", "success",
                    "message", "Archivo recuperado exitosamente.",
                    "revision", revision,
                    "rcs_output", baos.toString(StandardCharsets.UTF_8).trim()
                ));
            }

        } catch (Exception e) {
            LOGGER.warn("Error recuperando archivo con JavaRCS, args=" + jsonArguments, e);
            return error("Error en recuperación RCS: " + e.getMessage());
        }
    }

}
