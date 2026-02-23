package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentAccessControl;
import io.github.jjdelcerro.noema.lib.impl.AgentImpl;
import java.nio.file.Path;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsService;

/**
 * Herramienta para indexar y procesar documentos nuevos. Activa el DocMapper
 * para transformar un archivo bruto en conocimiento estructurado.
 */
public class DocumentIndexTool implements AgentTool {

  private final AgentImpl agent;
  private final Gson gson = new Gson();

  public DocumentIndexTool(Agent agent) {
    this.agent = (AgentImpl) agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("document_index")
            .description("Inicia el procesamiento y mapeo de un documento (PDF, texto, etc.) para que pueda ser buscado y leído por secciones más tarde. Úsalo cuando encuentres un archivo nuevo que quieras 'aprender'.")
            .addParameter("path", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Ruta relativa del archivo a procesar."))
            .build();
  }

  @Override
  public int getMode() {
    // Marcamos como WRITE porque altera el estado del sistema de conocimiento
    return AgentTool.MODE_WRITE;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      DocumentsService service = (DocumentsService) this.agent.getService(DocumentsService.NAME);

      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String relativePath = args.get("path");

      // 1. Validar acceso al archivo
      Path docPath = agent.getAccessControl().resolvePathOrNull(relativePath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
      if (docPath == null) {
        return "{\"status\": \"error\", \"message\": \"Acceso denegado o archivo no encontrado.\"}";
      }

      service.indexDocument(docPath);

      return "{\"status\": \"success\", \"message\": \"Procesamiento de '" + relativePath + "' iniciado. Te notificaré cuando haya terminado de mapear su estructura.\"}";
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
