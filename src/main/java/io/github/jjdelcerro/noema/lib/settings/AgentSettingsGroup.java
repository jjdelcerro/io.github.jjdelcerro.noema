package io.github.jjdelcerro.noema.lib.settings;

import java.nio.file.Path;
import java.util.List;

/**
 * Nodo del árbol de configuración que contiene otros elementos (grupos o
 * valores). Permite el acceso jerárquico mediante rutas separadas por '/'.
 */
public interface AgentSettingsGroup extends AgentSettingsItem {

  AgentSettingsItem getProperty(String path);

  String getPropertyAsString(String path);

  String getPropertyAsString(String path, String defaultValue);

  int getPropertyAsInt(String path, int defaultValue);

  long getPropertyAsLong(String path, long defaultValue);

  List<Path> getPropertyAsPaths(String path);

  AgentSettingsGroup getPropertyGroup(String path);

  AgentSettingsCheckedList getPropertyAsCheckedList(String path);

  void setProperty(String path, String value);

  void setProperty(String path, List<String> values);

  /**
   * Actualiza o añade el estado de un elemento dentro de una lista marcada.
   */
  void setChecked(String path, String value, boolean checked);
}
