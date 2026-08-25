package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class LlmModule extends AbstractScriptingModule {
  
  private static final String DEFAULT_MODEL = "DOCMAPPER_BASIC";

  public LlmModule(ScriptContext context, Agent agent) {
    super(context, agent, "llm", "modulo de acceso al API del LLM");
  }

  /**
   * Performs a stateless semantic query on a text chunk.
   */
  public String query(String prompt, String chunk) {
    return queryWithModel(DEFAULT_MODEL, prompt, chunk);
  }

  public String queryWithModel(String modelId, String prompt, String chunk) {
    String result = agent.callChatModel(modelId, prompt, chunk != null ? chunk : "");
    return result != null ? result.trim() : "";
  }

  /**
   * Performs a structured extraction, returning a parsed JSON Map or List.
   */
  public Object extractJson(String prompt, String chunk) {
    JsonObject json = agent.callChatModelAsJson(DEFAULT_MODEL, prompt, chunk != null ? chunk : "");
    if (json == null) {
      return Collections.emptyMap();
    }
    return new Gson().fromJson(json, Object.class);
  }

  /**
   * Evaluates an iterable of chunks sequentially, applying a prompt to each
   * item.
   */
  public Iterable<String> map(String prompt, Iterable<String> chunks) {
    List<String> results = new ArrayList<>();
    for (String chunk : chunks) {
      results.add(query(prompt, chunk));
    }
    return results;
  }
  
}
