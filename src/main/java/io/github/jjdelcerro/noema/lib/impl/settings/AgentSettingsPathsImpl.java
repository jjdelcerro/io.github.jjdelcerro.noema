package io.github.jjdelcerro.noema.lib.impl.settings;

import io.github.jjdelcerro.noema.lib.settings.AgentSettingsPaths;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class AgentSettingsPathsImpl implements AgentSettingsPaths {

  private final List<String> rawValues;

  public AgentSettingsPathsImpl(List<String> rawValues) {
    this.rawValues = rawValues;
  }

  @Override
  public List<Path> getValues() {
    return rawValues.stream()
            .map(Path::of)
            .collect(Collectors.toList());
  }

  // Necesario para la serialización de GSON
  public List<String> getRawValues() {
    return rawValues;
  }
}
