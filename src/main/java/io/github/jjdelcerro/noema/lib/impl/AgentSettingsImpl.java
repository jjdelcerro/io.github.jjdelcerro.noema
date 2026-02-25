package io.github.jjdelcerro.noema.lib.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.annotations.SerializedName;
import edu.emory.mathcs.backport.java.util.Collections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/**
 *
 * @author jjdelcerro
 */
public class AgentSettingsImpl implements AgentSettings {

  public class GlobalSettingsData {

    @SerializedName("lastWorkspacesUseds")
    private List<String> lastWorkspacesPaths;

    @SerializedName("lastWorkspacesUsed")
    private String lastWorkspacePath;

    public List<String> getLastWorkspacesPaths() {
      return lastWorkspacesPaths;
    }

    public String getLastWorkspacePath() {
      return lastWorkspacePath;
    }

    public void setLastWorkspacesPaths(List<String> lastWorkspacesPaths) {
      this.lastWorkspacesPaths = lastWorkspacesPaths;
    }

    public void setLastWorkspacePath(String lastWorkspacePath) {
      this.lastWorkspacePath = lastWorkspacePath;
    }
  }

  private final Map<String, String> values;
  private AgentPaths paths;
  private GlobalSettingsData globalSettings;

  public AgentSettingsImpl(AgentPaths paths) {
    this.values = new ConcurrentHashMap<>();
    this.paths = paths;
    this.globalSettings = null;
  }

  private GlobalSettingsData getGlobalSettings() {
    if (this.globalSettings == null) {
      try {
        Gson gson = new Gson();
        String json = Files.readString(this.paths.getGlobalConfigFolder().resolve("settings.json"), StandardCharsets.UTF_8);
        this.globalSettings = gson.fromJson(json, GlobalSettingsData.class);
        List<String> lastWorkspacesPaths = this.globalSettings.getLastWorkspacesPaths();
        if (lastWorkspacesPaths != null) {
          Collections.sort(lastWorkspacesPaths);
        }
      } catch (IOException ex) {
        // FIXME log error
        this.globalSettings = new GlobalSettingsData();
      }
    }
    return this.globalSettings;
  }

  private void saveGlobalSettings() {
    try {
      Gson gson = new GsonBuilder()
              .setPrettyPrinting()
              .disableHtmlEscaping()
              .serializeNulls()
              .create();

      String json = gson.toJson(this.getGlobalSettings());

      Files.writeString(
              this.paths.getGlobalConfigFolder().resolve("settings.json"),
              json,
              StandardCharsets.UTF_8,
              StandardOpenOption.WRITE,
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING
      );
    } catch (IOException ex) {
      throw new RuntimeException("Can't save global settings", ex);
    }
  }

  @Override
  public String getProperty(String name) {
    return values.get(name);
  }

  @Override
  public long getPropertyAsLong(String name, long defaultValue) {
    try {
      String s = values.get(name);
      if (StringUtils.isBlank(s)) {
        return defaultValue;
      }
      Long v = Long.getLong(s);
      return v;
    } catch (Exception ex) {
      return defaultValue; // TODO: enviar al log.
    }
  }

  @Override
  public String setProperty(String name, String value) {
    if (value == null) {
      return values.remove(name);
    }
    return values.put(name, value);
  }

  @Override
  public void load() {
    Path path = this.paths.getConfigFolder().resolve("settings.properties");
    if (!Files.exists(path)) {
      return;
    }
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(path.toFile())) {
      props.load(fis);
      for (String key : props.stringPropertyNames()) {
        values.put(key, props.getProperty(key));
      }
    } catch (Exception e) {
      throw new RuntimeException("Error loading settings from " + path.toString(), e);
    }
  }

  @Override
  public void save() {
    Path path = this.paths.getConfigFolder().resolve("settings.properties");
    if (!Files.exists(path)) {
      return;
    }
    Properties props = new Properties();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      props.setProperty(entry.getKey(), entry.getValue());
    }
    try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
      props.store(fos, "Agent Settings");
    } catch (Exception e) {
      throw new RuntimeException("Error saving settings to " + path.toString(), e);
    }
  }

  @Override
  public List<String> getLastWorkspacesPaths() {
    return this.getGlobalSettings().getLastWorkspacesPaths();
  }

  @Override
  public String getLastWorkspacePath() {
    return this.getGlobalSettings().getLastWorkspacePath();
  }

  public void setLastWorkspacePath(String lastWorkspacePath) {
    this.getGlobalSettings().setLastWorkspacePath(lastWorkspacePath);
    List<String> lastWorkspacesPaths = this.globalSettings.getLastWorkspacesPaths();
    if (lastWorkspacesPaths == null) {
      lastWorkspacesPaths = new ArrayList<>();
      lastWorkspacesPaths.add(lastWorkspacePath);
      this.globalSettings.setLastWorkspacesPaths(lastWorkspacesPaths);
    } else {
      if (!lastWorkspacesPaths.contains(lastWorkspacePath)) {
        lastWorkspacesPaths.add(lastWorkspacePath);
        Collections.sort(lastWorkspacesPaths);
        this.globalSettings.setLastWorkspacesPaths(lastWorkspacesPaths);
      }
    }
    this.saveGlobalSettings();
  }

  @Override
  public AgentPaths getPaths() {
    return this.paths;
  }

  @Override
  public void setupSettings(AgentPaths paths) {
    this.paths = paths;
    this.paths.setupHierarchy();
    this.setupSettings();
  }

  @Override
  public void setupSettings() {
    // Instala lo minimo para poder iniciar el agente.
    String[] resources = new String[]{
      "models.properties",
      //      "providers_apikeys.properties",
      "providers_urls.properties",
      "settings.properties",
      "settingsui.json"
    };
    for (String resPath : resources) {
      AgentUtils.installResource(this.paths, resPath);
    }
  }

}
