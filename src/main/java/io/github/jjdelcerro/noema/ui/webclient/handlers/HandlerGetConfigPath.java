package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.Gson;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.api.settings.AgentSettingsString;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetConfigPath extends AbstractHandler {

  public HandlerGetConfigPath(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String path = ctx.pathParam("path");
    AgentSettingsItem item = this.agent.getSettings().getProperty(path);

    if (item == null) {
      ctx.status(404).json(Map.of("error", "Propiedad no encontrada: " + path));
      return;
    }

    if (item instanceof AgentSettingsGroup) {
      ctx.contentType("application/json").result(new Gson().toJson(item));
    } else if (item instanceof AgentSettingsString stringItem) {
      ctx.json(Map.of("value", stringItem.getValue()));
    } else if (item instanceof AgentSettingsPaths pathsItem) {
      List<String> rawPaths = pathsItem.getValues().stream().map(Path::toString).toList();
      ctx.json(rawPaths);
    } else if (item instanceof AgentSettingsCheckedList checkedList) {
      ctx.json(checkedList.getItems());
    } else {
      ctx.json(Map.of("value", item.toString()));
    }
  }
  
}
