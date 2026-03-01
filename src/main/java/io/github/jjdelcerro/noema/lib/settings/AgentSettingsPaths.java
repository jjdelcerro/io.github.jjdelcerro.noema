package io.github.jjdelcerro.noema.lib.settings;

import java.nio.file.Path;
import java.util.List;

/**
 * Representa una lista de rutas de ficheros o directorios.
 */
public interface AgentSettingsPaths extends AgentSettingsItem {

  List<Path> getValues();
}
