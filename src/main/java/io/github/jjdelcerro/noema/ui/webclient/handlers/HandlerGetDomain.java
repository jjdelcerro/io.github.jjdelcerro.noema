package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetDomain extends AbstractHandler {

  public HandlerGetDomain(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String domainName = ctx.pathParam("domainName");
    Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");

    if (!Files.exists(uiPath)) {
      ctx.status(404).json(Map.of("error", "settingsui.json no disponible"));
      return;
    }

    String uiJson = Files.readString(uiPath, StandardCharsets.UTF_8);
    JsonObject uiRoot = JsonParser.parseString(uiJson).getAsJsonObject();
    JsonObject domains = uiRoot.getAsJsonObject("domains");

    if (domains == null || !domains.has(domainName)) {
      ctx.status(404).json(Map.of("error", "Dominio no registrado: " + domainName));
      return;
    }

    String fileName = domains.get(domainName).getAsString();
    Path propPath = this.agent.getPaths().getConfigPath(fileName);

    if (!Files.exists(propPath)) {
      ctx.status(404).json(Map.of("error", "Properties del dominio no encontrado: " + fileName));
      return;
    }

    Properties props = new Properties();
    try (Reader r = Files.newBufferedReader(propPath, StandardCharsets.UTF_8)) {
      props.load(r);
    }

    List<Map<String, String>> result = new ArrayList<>();
    List<String> sortedKeys = new ArrayList<>(props.stringPropertyNames());
    Collections.sort(sortedKeys);

    for (String key : sortedKeys) {
      Map<String, String> entry = new LinkedHashMap<>();
      entry.put("key", key);
      entry.put("label", key.replace('_', ' '));
      entry.put("value", props.getProperty(key));
      result.add(entry);
    }

    ctx.json(result);
  }
  
}
