package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostConfigList extends AbstractHandler {

  public HandlerPostConfigList(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    JsonArray array = JsonParser.parseString(ctx.body()).getAsJsonArray();
    List<String> values = new ArrayList<>();

    for (JsonElement el : array) {
      values.add(el.getAsString());
    }

    this.agent.getSettings().setProperty(path, values);
    this.agent.getSettings().save();
    triggerAction(path);

    ctx.json(Map.of("status", "success"));
  }
  
}
