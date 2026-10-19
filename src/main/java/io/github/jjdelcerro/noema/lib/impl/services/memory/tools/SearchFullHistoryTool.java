package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.utils.DateUtils;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import io.github.jjdelcerro.noema.lib.api.memory.episodic.Turn;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UseSpecificCatch")
public class SearchFullHistoryTool extends AbstractAgentTool {

  public static final String NAME = "search_full_history";

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 50;
  private static final double DEFAULT_SIMILARITY = 0.5;

  public SearchFullHistoryTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(NAME)
            .description(StringUtils.replace("""
Busca y recupera informacion en toda la memoria historica basandose en similitud de significado.
Usalo cuando:
1. Tengas la sensacion de haber hablado de un tema en el pasado pero no recuerdes los detalles ni el momento.
2. Necesites encontrar antecedentes, decisiones o analisis previos sobre un concepto.
3. El contexto inmediato y el relato actual sean insuficientes para responder con precision.

**Instrucciones de uso:**
- Cada turno devuelto incluye su contenido completo y su marca temporal.
- Si el resultado se ha podado por antiguedad, solo conservara las citas de los turnos encontrados:
  usa '{LOOKUPTURN}' con el codigo indicado para recuperar el turno completo.
- El parametro 'type' esta reservado para filtrar por categoria de anotacion; omitelo a menos que tengas una instruccion explicita.
""", "{LOOKUPTURN}", LookupTurnTool.NAME))
            .addStringParameter("query", false, "El concepto, pregunta o tema a buscar por significado.")
            .addIntegerParameter("limit", true, "Maximo de resultados a devolver (Default: 10, Max: 50).")
            .addNumberParameter("minsimilarity", true, "Umbral minimo de similitud coseno (Default: 0.5, Rango: 0.0 a 1.0). Valores mas altos hacen la busqueda mas estricta.")
            .addStringParameter("type", true, "USO RESTRINGIDO. Omitir o dejar vacio por defecto. Rellenar UNICAMENTE si necesitas filtrar exclusivamente anotaciones registradas bajo un tipo concreto.");
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_MEMORY;
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    try {
      SearchArgs args = gson.fromJson(jsonArguments, SearchArgs.class);

      if (args == null || StringUtils.isBlank(args.query)) {
        return error("El parametro 'query' es obligatorio.");
      }

      int safeLimit = (args.limit != null && args.limit > 0) ? Math.min(args.limit, MAX_LIMIT) : DEFAULT_LIMIT;
      double safeSimilarity = (args.similarity != null && !Double.isNaN(args.similarity))
              ? Math.max(0.0, Math.min(1.0, args.similarity))
              : DEFAULT_SIMILARITY;
      String safeType = StringUtils.trimToNull(args.type);

      List<Turn> turns = this.agent.getEpisodicMemory().getTurnsByText(
              null,
              args.query.trim(),
              safeLimit,
              safeSimilarity,
              safeType
      );

      List<Map<String, Object>> results = new ArrayList<>();
      for (Turn t : turns) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", StringUtils.trim(String.valueOf(t.getId())));
        map.put("timestamp", DateUtils.toString(t.getTimestamp()));
        map.put("role", determineRole(t));
        map.put("subchannel", t.getSubchannel());
        map.put("contenttype", t.getContenttype());
        if (t.getAnnotationType() != null) {
          map.put("annotation_type", t.getAnnotationType());
        }
        map.put("text", t.getContentForEmbedding());
        results.add(map);
      }

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("status", "success");
      response.put("query", args.query);
      response.put("count", results.size());
      response.put("results", results);

      return result(gson.toJson(response));

    } catch (Exception e) {
      LOGGER.warn("Error ejecutando search_full_history sobre: " + jsonArguments, e);
      return error("Error en busqueda historica: " + e.getMessage());
    }
  }

  /**
   * Degrada el resultado de la busqueda a un indice de citas cuando el turno
   * sale de la zona segura de la memoria proyectada. Se conservan los metadatos
   * de cada turno para poder elegir cual recuperar y el campo 'text' se sustituye
   * por su cita. El contenido completo sigue siendo recuperable con
   * 'fetch_citation' a partir del codigo.
   */
  @Override
  public ToolResult trimResult(ToolResult toolResult, TrimResultType trimResultType) {
    if (trimResultType != TrimResultType.Trim) {
      return null;
    }
    if (toolResult == null || toolResult.getType() != ToolResultType.Text) {
      return null;
    }
    String text = toolResult.text();
    if (StringUtils.isBlank(text)) {
      return null;
    }
    try {
      Map<String, Object> source = gson.fromJson(text, Map.class);
      if (source == null || !"success".equals(source.get("status"))) {
        return null;
      }
      Object rawResults = source.get("results");
      if (!(rawResults instanceof List)) {
        return null;
      }
      List<?> sourceResults = (List<?>) rawResults;
      if (sourceResults.isEmpty()) {
        return null;
      }

      List<Map<String, Object>> trimmedResults = new ArrayList<>();
      for (Object item : sourceResults) {
        if (!(item instanceof Map)) {
          continue;
        }
        Map<?, ?> sourceEntry = (Map<?, ?>) item;
        Object code = sourceEntry.get("code");
        if (code == null) {
          continue;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", code);
        entry.put("timestamp", sourceEntry.get("timestamp"));
        entry.put("role", sourceEntry.get("role"));
        entry.put("subchannel", sourceEntry.get("subchannel"));
        entry.put("citation", "{cite:" + code + "}");
        trimmedResults.add(entry);
      }

      if (trimmedResults.isEmpty()) {
        return null;
      }

      Map<String, Object> trimmed = new LinkedHashMap<>();
      trimmed.put("status", "success");
      trimmed.put("query", source.get("query"));
      trimmed.put("count", trimmedResults.size());
      trimmed.put("trimmed", true);
      trimmed.put("results", trimmedResults);

      return result(gson.toJson(trimmed));

    } catch (Exception e) {
      LOGGER.debug("No se pudo recortar el resultado de " + NAME, e);
      return null;
    }
  }

  private String determineRole(Turn t) {
    if ("chat".equals(t.getContenttype())) {
      return t.getTextUser() != null ? "user" : "assistant";
    }
    return t.getContenttype(); // tool, lookup, etc.
  }
  
  private static class SearchArgs {

    String query;
    Integer limit;
    Double similarity;
    String type;
  }
}
