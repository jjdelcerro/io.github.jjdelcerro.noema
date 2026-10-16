package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public abstract class AbstractHandler implements Handler {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractHandler.class);

  protected final Agent agent;

  protected AbstractHandler(Agent agent) {
    this.agent = agent;
  }

  public Agent getAgent() {
    return agent;
  }
  
  public NoemaWebServer getWebServer() {
      NoemaWebServer webserver = (NoemaWebServer) this.agent.get("webserver");
      return webserver;
  }

  protected Path resolveFilePath(String rawFile) {
    if (rawFile == null || rawFile.isBlank()) {
      return null;
    }
    String trimmed = rawFile.trim();
    if (trimmed.startsWith("var:/")) {
      String sub = trimmed.substring("var:/".length());
      while (sub.startsWith("/")) {
        sub = sub.substring(1);
      }
      if (this.agent.getPaths() != null) {
        // getAgentPath comprueba primero en el workspace local (.noema-agent)
        // y si no existe, busca en la configuración global (~/.config/noema-agent)
        Path resolved = this.agent.getPaths().getAgentPath(sub);
        if (resolved != null) {
          return resolved.normalize().toAbsolutePath();
        }
      }
      return Path.of(sub).normalize().toAbsolutePath();
    } else {
      return Path.of(trimmed).normalize().toAbsolutePath();
    }
  }
  
  protected void triggerAction(String variablePath) {
    try {
      Path uiPath = this.agent.getPaths().getConfigFolder().resolve("settingsui.json");
      if (Files.exists(uiPath)) {
        JsonObject uiRoot = JsonParser.parseString(Files.readString(uiPath, StandardCharsets.UTF_8)).getAsJsonObject();
        String actionName = findActionName(uiRoot, variablePath);
        if (actionName != null && !actionName.trim().isEmpty()) {
          LOGGER.debug("Disparando acción lifecycle de Noema: {}", actionName);
          this.agent.getActions().call(actionName, this.agent.getSettings());
        }
      }
    } catch (Exception e) {
      LOGGER.warn("No se pudo ejecutar la acción asociada al path: " + variablePath, e);
    }
  }
  
  protected String findActionName(JsonElement element, String variableName) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
        if (obj.has("actionName")) {
          return obj.get("actionName").getAsString();
        }
      }
      if (obj.has("childs")) {
        String action = findActionName(obj.get("childs"), variableName);
        if (action != null) {
          return action;
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        String action = findActionName(child, variableName);
        if (action != null) {
          return action;
        }
      }
    }
    return null;
  }

  protected String findChildEnabledExpression(JsonElement element, String variableName) {
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("variableName") && obj.get("variableName").getAsString().equals(variableName)) {
        if (obj.has("childEnabled")) {
          return obj.get("childEnabled").getAsString();
        }
      }
      if (obj.has("childs")) {
        String exp = findChildEnabledExpression(obj.get("childs"), variableName);
        if (exp != null) {
          return exp;
        }
      }
    } else if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        String exp = findChildEnabledExpression(child, variableName);
        if (exp != null) {
          return exp;
        }
      }
    }
    return null;
  }

  protected String findProjectFolder(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }

    // Normalizamos la ruta para trabajar con una ruta absoluta y sin . o ..
    Path path = Paths.get(filename).toAbsolutePath().normalize();

    // Si es un directorio, empezamos por él mismo.
    // Si es un archivo (o no existe), empezamos por su carpeta padre.
    Path dir = Files.isDirectory(path) ? path : path.getParent();

    // Subimos por el árbol de directorios
    while (dir != null) {
      Path pom = dir.resolve("pom.xml");
      if (Files.isRegularFile(pom)) {
        return dir.toString(); // Carpeta donde se encontró pom.xml
      }
      dir = dir.getParent();
    }

    return null; // No se encontró ningún pom.xml en los ancestros
  }
  
}
