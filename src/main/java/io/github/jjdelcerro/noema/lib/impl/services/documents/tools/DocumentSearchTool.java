package io.github.jjdelcerro.noema.lib.impl.services.documents.tools;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsService.DocumentResult;
import io.github.jjdelcerro.noema.lib.impl.services.documents.DocumentsServiceImpl;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

public class DocumentSearchTool extends AbstractAgentTool {
  public static final String TOOL_NAME = "document_search";

  public DocumentSearchTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Busca documentos combinando filtros por categorías y búsqueda semántica en los resúmenes. Devuelve el ID, título y resumen del documento.")
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El concepto o tema a buscar por significado."))
            .addParameter("categories", JsonSchemaProperty.type("array"),
                    JsonSchemaProperty.items(JsonSchemaProperty.STRING),
                    JsonSchemaProperty.description("Opcional: Lista de categorías exactas para filtrar (ej: ['Manual', 'Proyecto_X']).")
            )
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 5)."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      DocumentsServiceImpl service = (DocumentsServiceImpl) this.agent.getService(DocumentsServiceImpl.NAME);

      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String query = (String) args.get("query");
      List<String> categories = (List<String>) args.get("categories");
      int limit = args.containsKey("limit") ? ((Double) args.get("limit")).intValue() : 5;

      List<DocumentResult> results = service.search(categories, query, limit);
      return gson.toJson(Map.of("status", "success", "results", results));
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
