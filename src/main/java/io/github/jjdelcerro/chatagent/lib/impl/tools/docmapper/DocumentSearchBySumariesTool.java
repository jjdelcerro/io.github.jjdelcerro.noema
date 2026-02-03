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

public class DocumentSearchBySumariesTool implements AgenteTool {

  private final AgentImpl agent;
  private final Gson gson = new Gson();

  public DocumentSearchBySumariesTool(Agent agent) {
    this.agent = (AgentImpl) agent;
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("document_search_by_sumaries")
            .description("Busca en los resúmenes de todos los documentos por significado. Úsalo si no conoces la categoría pero sabes de qué trata el documento.")
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El tema a buscar en los resúmenes."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 5)."))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
      String query = (String) args.get("query");
      int limit = args.containsKey("limit") ? ((Double) args.get("limit")).intValue() : 5;

      List<DocumentServices.DocumentResult> results = agent.getDocumentServices().searchBySummaries(query, limit);
      return gson.toJson(Map.of("status", "success", "results", results));
    } catch (Exception e) {
      return "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
    }
  }
}
