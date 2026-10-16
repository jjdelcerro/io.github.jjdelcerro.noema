package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsItem;
import io.javalin.http.Context;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostConfigPath extends AbstractHandler {

  public HandlerPostConfigPath(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();

    if (!body.has("value")) {
      ctx.status(400).json(Map.of("error", "Falta el campo 'value'"));
      return;
    }

    JsonElement valEl = body.get("value");

    int lastSlash = path.lastIndexOf('/');
    if (lastSlash != -1) {
      String parentPath = path.substring(0, lastSlash);
      String childKey = path.substring(lastSlash + 1);
      AgentSettingsItem parentItem = this.agent.getSettings().getProperty(parentPath);

      if (parentItem instanceof AgentSettingsCheckedList) {
        boolean isChecked = valEl.getAsBoolean();
        this.agent.getSettings().setChecked(parentPath, childKey, isChecked);
        this.agent.getSettings().save();
        triggerAction(parentPath);
        ctx.json(Map.of("status", "success"));
        return;
      }
    }

    this.agent.getSettings().setProperty(path, valEl.getAsString());
    this.agent.getSettings().save();
    triggerAction(path);

    ctx.json(Map.of("status", "success"));
  }
  
}
