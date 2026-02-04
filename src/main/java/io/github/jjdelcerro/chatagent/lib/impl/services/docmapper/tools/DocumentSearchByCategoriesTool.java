package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.impl.AgentImpl;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DocumentResult;
import io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsServiceImpl;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class DocumentSearchByCategoriesTool implements AgentTool {

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
      DocumentsServiceImpl service = (DocumentsServiceImpl) this.agent.getService(DocumentsServiceImpl.NAME);

      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      List<String> categories = (List<String>) args.get("categories");
      int limit = args.containsKey("limit") ? ((Double) args.get("limit")).intValue() : 10;

      List<DocumentResult> results = service.searchByCategories(categories, limit);
      return gson.toJson(Map.of("status", "success", "results", results));
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
