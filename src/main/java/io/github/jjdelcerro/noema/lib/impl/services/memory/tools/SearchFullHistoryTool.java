package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.persistence.Turn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import org.apache.commons.lang3.StringUtils;

public class SearchFullHistoryTool extends AbstractAgentTool {

  public static final String NAME = "search_full_history";

  public SearchFullHistoryTool(Agent agent) {
    super(agent);
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
            .description(StringUtils.replace(
"""
Busca y recupera en toda la memoria histórica informacion relevantes basándose en significado.
Úsalo cuando:
1. Tengas la sensación de haber hablado de algo pero no recuerdes los detalles
2. Necesites encontrar información relacionada con un concepto o tema
3. El contexto inmediato sea insuficiente para responder con precisión
Parámetros:
- query: La consulta de búsqueda (describe lo que buscas)
- limit: Máximo de resultados (default: 10, max: 50)

Se recuperara toda la informacion disponible para cada uno de los turnos o citas recuperados.
Una vez ya has recuperado un turno o cita mediante esta herramienta no debes llamar a la
herramienta {LOOKUPTURN} para tratar de recuperar mas informacion sobre los turnos devueltos por esta 
herramienta.
""", "{LOOKUPTURN}", NAME))
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El concepto a buscar."))
            .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Máximo de resultados (Default 10, Max 50)."))
            .build();
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_MEMORY;
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
        map.put("code", StringUtils.trim(String.valueOf(t.getId()))); 
        map.put("timestamp", t.getTimestamp().toString());
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
