package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentAccessControl;
import io.github.jjdelcerro.chatagent.lib.impl.AgentImpl;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentMapper;
import java.nio.file.Path;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

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
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String relativePath = args.get("path");

      // 1. Validar acceso al archivo
      Path docPath = agent.getAccessControl().resolvePathOrNull(relativePath, AgentAccessControl.AccessMode.PATH_ACCESS_READ);
      if (docPath == null) {
        return "{\"status\": \"error\", \"message\": \"Acceso denegado o archivo no encontrado.\"}";
      }

      // 2. Disparar el DocMapper
      // Nota: DocMapper debería gestionar su propio hilo interno para no bloquear al agente
      DocumentMapper mapper = new DocumentMapper(agent);
      mapper.processDocument(docPath);

      return "{\"status\": \"success\", \"message\": \"Procesamiento de '" + relativePath + "' iniciado. Te notificaré cuando haya terminado de mapear su estructura.\"}";
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
