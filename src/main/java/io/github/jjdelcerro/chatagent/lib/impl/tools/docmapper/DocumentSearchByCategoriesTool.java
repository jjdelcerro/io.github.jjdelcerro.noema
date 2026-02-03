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

public class DocumentSearchByCategoriesTool implements AgenteTool {

  private final AgentImpl agent;
  private final Gson gson = new Gson();

  public DocumentSearchByCategoriesTool(Agent agent) {
    this.agent = (AgentImpl) agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("document_search_by_categories")
            .description("Lista documentos que pertenecen a categorías específicas. Úsalo para ver qué manuales o archivos hay disponibles de un tipo concreto.")
            .addParameter("categories", JsonSchemaProperty.ARRAY, JsonSchemaProperty.description("Lista de categorías (ej: ['Hardware', 'PDF'])."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 10)."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      List<String> categories = (List<String>) args.get("categories");
      int limit = args.containsKey("limit") ? ((Double) args.get("limit")).intValue() : 10;

      List<DocumentServices.DocumentResult> results = agent.getDocumentServices().searchByCategories(categories, limit);
      return gson.toJson(Map.of("status", "success", "results", results));
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
