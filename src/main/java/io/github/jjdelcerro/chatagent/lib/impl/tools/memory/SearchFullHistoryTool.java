package io.github.jjdelcerro.chatagent.lib.impl.tools.memory;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchFullHistoryTool implements AgenteTool {

  public static final String NAME = "search_full_history";

  private final Agent agent;
  private final Gson gson;

  public SearchFullHistoryTool(Agent agent) {
    this.agent = agent;
    this.gson = new Gson();
  }

  @Override
  public ToolSpecification getSpecification() {
    /*
FIXME: Hay algun lio con el parametro de limit. Deberian ser dos, 
- uno para limitar los documentos que se recorre durante la busqueda.
- Otro para limitar el numero de documentos maximo a devolver.
FIXME: le faltaria un par de parametros para indicar rango de fechas en el que se desea realizar la busqueda.
     */
    return ToolSpecification.builder()
            .name("search_full_history")
            .description(
                    """
Busca en toda la memoria hist\u00f3rica eventos relevantes bas\u00e1ndose en significado.
\u00dasalo cuando:
1. Tengas la sensaci\u00f3n de haber hablado de algo pero no recuerdes los detalles
2. Necesites encontrar informaci\u00f3n relacionada con un concepto o tema
3. El contexto inmediato sea insuficiente para responder con precisi\u00f3n
Par\u00e1metros:
- query: La consulta de b\u00fasqueda (describe lo que buscas)
- limit: M\u00e1ximo de resultados (default: 10, max: 50)"""
            )
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El concepto a buscar."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 10, Max 50)."))
            .build();
  }

  @Override
  public int getType() {
    return AgenteTool.TYPE_MEMORY;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      SearchArgs args = gson.fromJson(jsonArguments, SearchArgs.class);
      int safeLimit = Math.min(args.limit > 0 ? args.limit : 10, 50);

      List<Turn> turns = this.agent.getSourceOfTruth().getTurnsByText(args.query, safeLimit);

      // Mapeamos a una estructura ligera para devolver al LLM
      List<Map<String, Object>> results = new ArrayList<>();
      for (Turn t : turns) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", "ID-" + t.getId()); // Formato String para el LLM
        map.put("timestamp", t.getTimestamp().toString());
        // Usamos el contenido concatenado para dar contexto
        map.put("content", t.getContentForEmbedding());
        results.add(map);
      }

      return gson.toJson(Map.of("status", "success", "results", results));

    } catch (Exception e) {
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }

  // Clase interna para argumentos
  private static class SearchArgs {

    String query;
    int limit;
  }
}
