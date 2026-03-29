package io.github.jjdelcerro.noema.lib.settings;

import io.github.jjdelcerro.noema.lib.AgentPaths;
import java.util.List;
import java.util.Map;

/**
 * Interfaz principal del subsistema de configuración. Actúa como el nodo raíz
 * del árbol de configuración.
 */
public interface AgentSettings extends AgentSettingsGroup {

  void load();

  void save();

  void setupSettings();

  void setupSettings(AgentPaths paths);

  AgentPaths getPaths();

  List<String> getLastWorkspacesPaths();

  String getLastWorkspacePath();

  void setLastWorkspacePath(String lastWorkspacePath);
  
  Object eval(String expression, Object defaultValue); 
  Object eval(String expression, Object defaultValue, Map<String,Object> vars); 
}
