package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsService.DocumentResult;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsService;

public class DocumentSearchByCategoriesTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "document_search_by_categories";

  public DocumentSearchByCategoriesTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description("Lista documentos que pertenecen a categorías específicas. Úsalo para ver qué manuales o archivos hay disponibles de un tipo concreto.")
            .addStringArrayParameter(
                    "categories",
                    true,
                    "Opcional: Lista de categorías exactas para filtrar (ej: ['Manual', 'Proyecto_X'])."
            );
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      DocumentsService service = (DocumentsService) this.agent.getService(DocumentsService.NAME);

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
