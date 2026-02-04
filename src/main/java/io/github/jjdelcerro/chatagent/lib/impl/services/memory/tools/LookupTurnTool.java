package io.github.jjdelcerro.chatagent.lib.impl.services.memory.tools;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.persistence.Turn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.jjdelcerro.chatagent.lib.AgentTool;

public class LookupTurnTool implements AgentTool {

  public static final String NAME = "lookup_turn";

  private final Agent agent;
  private final Gson gson;

  public LookupTurnTool(Agent agent) {
    this.agent = agent;
    this.gson = new Gson();
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(NAME)
            .description("""
Recupera un evento espec\u00edfico de la memoria a largo plazo usando su ID \u00fanico. 
\u00dasalo cuando:
1. Veas una referencia como {cite:ID-123} en el Punto de Guardado
2. Necesites los detalles exactos de lo que ocurri\u00f3 en un momento espec\u00edfico
3. Requieras el contexto cronol\u00f3gico (qu\u00e9 pas\u00f3 justo antes/despu\u00e9s)
Par\u00e1metros:
- code: El ID del turno (ej: "ID-123" o solo "123")
- context_window: Cu\u00e1ntos turnos adicionales recuperar (default: 2)""")
            .addParameter("code", JsonSchemaProperty.STRING, JsonSchemaProperty.description("El ID único del turno (ej: 'ID-1001')."))
            .addParameter("context_window", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Turnos antes/después a recuperar (Max 5)."))
            .build();
  }

  @Override
  public int getType() {
    return AgentTool.TYPE_MEMORY;
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      LookupArgs args = gson.fromJson(jsonArguments, LookupArgs.class);
      int safeWindow = Math.min(args.context_window > 0 ? args.context_window : 1, 5);

      // Parseo del ID: "ID-100" -> 100
      int centerId = parseId(args.code);

      if (centerId < 0) {
        return gson.toJson(Map.of("status", "error", "message", "Formato de ID inválido. Use 'ID-Numero'"));
      }

      // Calculamos rango
      int first = centerId - safeWindow;
      int last = centerId + safeWindow;

      List<Turn> turns = agent.getSourceOfTruth().getTurnsByIds(first, last);

      List<Map<String, Object>> results = new ArrayList<>();
      for (Turn t : turns) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", "ID-" + t.getId());
        map.put("role", determineRole(t));
        map.put("text", t.getContentForEmbedding());
        results.add(map);
      }

      return gson.toJson(Map.of(
              "status", "success",
              "target_code", args.code,
              "retrieved_turns", results
      ));

    } catch (Exception e) {
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }

  // --- Helpers Internos ---
  private int parseId(String code) {
    try {
      if (code == null) {
        return -1;
      }
      if (code.toUpperCase().startsWith("ID-")) {
        return Integer.parseInt(code.substring(3));
      }
      return Integer.parseInt(code); // Intento directo
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private String determineRole(Turn t) {
    if ("chat".equals(t.getContenttype())) {
      return t.getTextUser() != null ? "user" : "assistant";
    }
    return t.getContenttype(); // tool, lookup, etc.
  }

  // Clase interna para argumentos
  private static class LookupArgs {

    String code;
    int context_window;
  }
}
