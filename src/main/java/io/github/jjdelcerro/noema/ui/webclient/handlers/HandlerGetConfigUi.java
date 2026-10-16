package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetConfigUi extends AbstractHandler {

  public HandlerGetConfigUi(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
    if (!Files.exists(uiPath)) {
      ctx.status(404).json(Map.of("error", "Archivo settingsui.json no encontrado"));
      return;
    }
    String json = Files.readString(uiPath, StandardCharsets.UTF_8);
    ctx.contentType("application/json").result(json);
  }
  
}
