package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.modules;

import com.google.gson.Gson;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.AbstractScriptingModule;
import io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class AnnotationModule extends AbstractScriptingModule {
  
  final String subchannel;

  public AnnotationModule(ScriptContext context, Agent agent, String subchannel) {
    super(context, agent, "annotation", "Modulo de acceso a las anotaciones del agente");
    this.subchannel = subchannel != null ? subchannel : Agent.DEFAULT_SUBCHANNEL;
  }

  public void add(String source, String note) {
    add(source, note, null, null);
  }

  public void add(String source, String note, String resourceId) {
    add(source, note, resourceId, null);
  }

  /**
   * Persists an annotation turn directly into EpisodicMemory.
   * @param source
   * @param note
   * @param resourceId
   * @param type
   */
  public void add(String source, String note, String resourceId, String type) {
    if (StringUtils.isBlank(note)) {
      return;
    }
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("source", source != null ? source : "script");
    args.put("note", note);
    if (StringUtils.isNotBlank(resourceId)) {
      args.put("resource_id", resourceId);
    }
    if (StringUtils.isNotBlank(type)) {
      args.put("type", type);
    }
    String toolCallJson = new Gson().toJson(args);
    Turn turn = agent.getEpisodicMemory().createTurn(LocalDateTime.now(), "annotation", subchannel, null, null, null, toolCallJson, "{\"status\": \"success\"}", null);
    agent.getEpisodicMemory().add(turn);
    LOGGER.info("Knowledge note registered from script: [{}] {}", source, StringUtils.abbreviate(note, 60));
  }
  
}
