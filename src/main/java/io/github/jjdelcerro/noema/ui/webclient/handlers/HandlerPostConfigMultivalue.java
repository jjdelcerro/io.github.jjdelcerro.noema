package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostConfigMultivalue extends AbstractHandler {

  public HandlerPostConfigMultivalue(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    JsonArray queryArray = JsonParser.parseString(ctx.body()).getAsJsonArray();
    JsonObject result = new JsonObject();

    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
    JsonObject uiRoot = Files.exists(uiPath)
            ? JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject()
            : new JsonObject();

    for (JsonElement el : queryArray) {
      JsonObject q = el.getAsJsonObject();
      String qPath = q.get("path").getAsString();
      JsonElement defaultValEl = q.get("defaultValue");

      Object defaultVal = defaultValEl == null ? ""
              : (defaultValEl.isJsonPrimitive() && defaultValEl.getAsJsonPrimitive().isBoolean()
              ? defaultValEl.getAsBoolean() : defaultValEl.getAsString());

      if (q.has("enabledExpression")) {
        Map<String, Object> contextVars = new HashMap<>();
        if (q.has("context")) {
          JsonObject contextJson = q.getAsJsonObject("context");
          for (String k : contextJson.keySet()) {
            contextVars.put(k, contextJson.get(k).getAsString());
          }
        }
        Object val = this.agent.getSettings().eval(
                q.get("enabledExpression").getAsString(), defaultVal, contextVars
        );
        result.addProperty(qPath, Boolean.valueOf(val.toString()));
      } else if (qPath.endsWith("/enabled")) {
        String parentPath = qPath.substring(0, qPath.length() - "/enabled".length());
        String expression = findChildEnabledExpression(uiRoot, parentPath);

        if (expression != null) {
          Map<String, Object> contextVars = new HashMap<>();
          if (q.has("context")) {
            JsonObject contextJson = q.getAsJsonObject("context");
            for (String k : contextJson.keySet()) {
              contextVars.put(k, contextJson.get(k).getAsString());
            }
          }
          Object val = this.agent.getSettings().eval(expression, defaultVal, contextVars);
          result.addProperty(qPath, Boolean.valueOf(val.toString()));
        } else {
          result.addProperty(qPath, true);
        }
      } else {
        String val = this.agent.getSettings().getPropertyAsString(qPath);
        if (val == null) {
          result.addProperty(qPath, defaultVal.toString());
        } else {
          result.addProperty(qPath, val);
        }
      }
    }

    ctx.contentType("application/json").result(new Gson().toJson(result));
  }
  
}
