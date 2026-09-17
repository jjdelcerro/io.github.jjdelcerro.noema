package io.github.jjdelcerro.noema.lib.impl.scripting.modules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.SLMUtils;
import io.github.jjdelcerro.noema.lib.impl.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.scripting.ScriptContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class LlmModule extends AbstractScriptModule {

  private static final String DEFAULT_MODEL = "DOCMAPPER_BASIC";
  private static final int MAX_LLM_CALLS_PER_SCRIPT = 25;

  private int callCount;

  public LlmModule(ScriptContext context, Agent agent) {
    super(context, agent, "llm", "modulo de acceso al API del LLM");
    callCount = 0;
  }

  @Override
  public String help() {
    return """
[agent.llm API] (DISYUNTOR: máx. %d llamadas/script. Filtra primero con Groovy, NO iterar archivos masivos)
• query(systemPrompt, message): String (modelo por defecto)
  -> def res = agent.llm.query('Clasifica', texto)
• queryWithModel(modelId, systemPrompt, message): String
  -> def res = agent.llm.queryWithModel('deepseek-chat', 'Resume', texto)
• sml_query(prompt): String (modelo local ONNX Qwen3.5-0.8B, sin coste API ni red)
  -> def tag = agent.llm.sml_query('Detecta tipo: ' + linea)
• extractJson(systemPrompt, message): Map|List (parseo estructurado)
  -> def json = agent.llm.extractJson('Devuelve JSON {name, age}', bio)
• map(systemPrompt, Iterable<String>): Iterable<String> (solo lotes pequeños <20)
""".formatted(MAX_LLM_CALLS_PER_SCRIPT);
  }

  /**
   * Performs a stateless semantic query on a text chunk.
   */
  public String query(String systemPrompt, String message) {
    return queryWithModel(DEFAULT_MODEL, systemPrompt, message);
  }

  public String sml_query(String prompt) {
    return SLMUtils.generate(agent, prompt);
  }

  public String queryWithModel(String modelId, String systemPrompt, String message) {
    if (++callCount > MAX_LLM_CALLS_PER_SCRIPT) {
      throw new IllegalStateException(
              "Límite de seguridad alcanzado: se ha superado el máximo de " + MAX_LLM_CALLS_PER_SCRIPT
              + " subconsultas al LLM en este script. Está TERMINANTEMENTE PROHIBIDO iterar línea a línea "
              + "con agent.llm.query(). Filtra primero los datos con código (regex, text.contains) o agrupa "
              + "en bloques grandes antes de consultar al modelo."
      );
    }
    String result = agent.callChatModel(modelId, systemPrompt, message != null ? message : "");
    return result != null ? result.trim() : "";
  }

  /**
   * Performs a structured extraction, returning a parsed JSON Map or List.
   */
  public Object extractJson(String systemPrompt, String message) {
    if (++callCount > MAX_LLM_CALLS_PER_SCRIPT) {
      throw new IllegalStateException(
              "Límite de seguridad alcanzado: se ha superado el máximo de " + MAX_LLM_CALLS_PER_SCRIPT
              + " subconsultas al LLM en este script. Está TERMINANTEMENTE PROHIBIDO iterar línea a línea "
              + "con agent.llm.query(). Filtra primero los datos con código (regex, text.contains) o agrupa "
              + "en bloques grandes antes de consultar al modelo."
      );
    }
    JsonObject json = agent.callChatModelAsJson(DEFAULT_MODEL, systemPrompt, message != null ? message : "");
    if (json == null) {
      return Collections.emptyMap();
    }
    return new Gson().fromJson(json, Object.class);
  }

  /**
   * Evaluates an iterable of messages sequentially, applying a prompt to each
   * item.
   */
  public Iterable<String> map(String systemPrompt, Collection<String> messages) {
    if (messages.size() > MAX_LLM_CALLS_PER_SCRIPT) {
      throw new IllegalStateException(
              "Límite de seguridad alcanzado: se ha superado el máximo de " + MAX_LLM_CALLS_PER_SCRIPT
              + " subconsultas al LLM en este script. Está TERMINANTEMENTE PROHIBIDO iterar línea a línea "
              + "con agent.llm.query(). Filtra primero los datos con código (regex, text.contains) o agrupa "
              + "en bloques grandes antes de consultar al modelo."
      );
    }
    List<String> results = new ArrayList<>();
    for (String message : messages) {
      results.add(query(systemPrompt, message));
    }
    return results;
  }

}
