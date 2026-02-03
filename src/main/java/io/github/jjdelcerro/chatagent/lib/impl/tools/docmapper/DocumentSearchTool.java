package io.github.jjdelcerro.chatagent.lib.impl.tools.docmapper;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.impl.AgentImpl;
import io.github.jjdelcerro.chatagent.lib.impl.docmapper.DocumentServices;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.util.List;
import java.util.Map;

public class DocumentSearchTool implements AgenteTool {

  private final AgentImpl agent;
  private final Gson gson = new Gson();

  public DocumentSearchTool(Agent agent) {
    this.agent = (AgentImpl) agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("document_search")
            .description("Busca documentos combinando filtros por categorías y búsqueda semántica en los resúmenes. Devuelve el ID, título y resumen del documento.")
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El concepto o tema a buscar por significado."))
            .addParameter("categories", JsonSchemaProperty.ARRAY, JsonSchemaProperty.description("Opcional: Lista de categorías exactas para filtrar (ej: ['Manual', 'Proyecto_X'])."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 5)."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String query = (String) args.get("query");
      List<String> categories = (List<String>) args.get("categories");
      int limit = args.containsKey("limit") ? ((Double) args.get("limit")).intValue() : 5;

      List<DocumentServices.DocumentResult> results = agent.getDocumentServices().search(categories, query, limit);
      return gson.toJson(Map.of("status", "success", "results", results));
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
