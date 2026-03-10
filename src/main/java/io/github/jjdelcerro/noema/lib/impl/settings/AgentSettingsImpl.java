package io.github.jjdelcerro.noema.lib.impl.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.impl.AgentUtils;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AgentSettingsImpl extends AgentSettingsGroupImpl implements AgentSettings {

  // Estructura para los ajustes globales (fuera de cualquier workspace)
  private static class GlobalSettingsData {

    @SerializedName("lastWorkspacesUseds")
    private List<String> lastWorkspacesPaths = new ArrayList<>();

    @SerializedName("lastWorkspacesUsed")
    private String lastWorkspacePath;
  }

  private transient AgentPaths paths;
  private transient GlobalSettingsData globalSettings;

  public AgentSettingsImpl(AgentPaths paths) {
    this.paths = paths;
  }

  @Override
  public void load() {
    Path path = paths.getConfigFolder().resolve("settings.json");
    if (!Files.exists(path)) {
      return;
    }

    try {
      Gson gson = createGson();
      // IMPORTANTE: Leemos el JSON como un Mapa de Items
      Type type = new TypeToken<Map<String, AgentSettingsItem>>() {
      }.getType();
      Map<String, AgentSettingsItem> loadedItems = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);

      if (loadedItems != null) {
        this.items.clear();
        this.items.putAll(loadedItems);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error cargando configuración", e);
    }
  }

  @Override
  public void save() {
    Path path = paths.getConfigFolder().resolve("settings.json");
    try {
      Files.createDirectories(path.getParent());
      Gson gson = createGson();
      // IMPORTANTE: Guardamos el MAPA de items, no "this"
      String json = gson.toJson(this.items);
      Files.writeString(path, json, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Error guardando configuración", e);
    }
  }

  private Gson createGson() {
    Gson x = new GsonBuilder()
            .registerTypeHierarchyAdapter(AgentSettingsItem.class, new AgentSettingsItemDeserializer())
            .registerTypeHierarchyAdapter(java.nio.file.Path.class, new com.google.gson.TypeAdapter<java.nio.file.Path>() {
              @Override
              public void write(com.google.gson.stream.JsonWriter out, java.nio.file.Path value) throws IOException {
                out.value(value == null ? null : value.toString());
              }

              @Override
              public java.nio.file.Path read(com.google.gson.stream.JsonReader in) throws IOException {
                return java.nio.file.Paths.get(in.nextString());
              }
            })
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    return x;
  }

  private GlobalSettingsData getGlobalSettings() {
    if (this.globalSettings == null) {
      Path globalPath = this.paths.getGlobalConfigFolder().resolve("settings.json");
      if (Files.exists(globalPath)) {
        try {
          Gson gson = new Gson();
          this.globalSettings = gson.fromJson(
                  Files.readString(globalPath, StandardCharsets.UTF_8),
                  GlobalSettingsData.class
          );
        } catch (IOException e) {
          this.globalSettings = new GlobalSettingsData();
        }
      } else {
        this.globalSettings = new GlobalSettingsData();
      }
    }
    return this.globalSettings;
  }

  private void saveGlobalSettings() {
    try {
      Path globalPath = this.paths.getGlobalConfigFolder().resolve("settings.json");
      Files.createDirectories(globalPath.getParent());

      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      Files.writeString(globalPath, gson.toJson(getGlobalSettings()),
              StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      // Log de error pero no bloqueamos el flujo
      System.err.println("No se pudieron guardar los ajustes globales: " + e.getMessage());
    }
  }

  @Override
  public List<String> getLastWorkspacesPaths() {
    List<String> list = getGlobalSettings().lastWorkspacesPaths;
    if (list != null) {
      Collections.sort(list);
    }
    return list;
  }

  @Override
  public String getLastWorkspacePath() {
    return getGlobalSettings().lastWorkspacePath;
  }

  @Override
  public void setLastWorkspacePath(String lastWorkspacePath) {
    GlobalSettingsData gs = getGlobalSettings();
    gs.lastWorkspacePath = lastWorkspacePath;
    if (lastWorkspacePath != null && !gs.lastWorkspacesPaths.contains(lastWorkspacePath)) {
      gs.lastWorkspacesPaths.add(lastWorkspacePath);
    }
    saveGlobalSettings();
  }

  @Override
  public void setupSettings(AgentPaths paths) {
    this.paths = paths;
    this.paths.setupHierarchy();
    this.setupSettings();
  }

  @Override
  public void setupSettings() {
    // Desplegamos los ficheros base si no existen
    String[] resources = {
      "var/config/models.properties",
      "var/config/providers_urls.properties",
      "var/config/available_tools.properties",
      "var/config/settings.json",
      "var/config/settingsui.json",
      "var/skills/readme.md",
      "var/identity/core/readme.md",
      "var/identity/environ/readme.md"
    };
    for (String resPath : resources) {
      AgentUtils.installResource(this.paths, resPath);
    }
  }

  @Override
  public AgentPaths getPaths() {
    return this.paths;
  }
}
