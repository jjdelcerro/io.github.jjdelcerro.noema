package io.github.jjdelcerro.noema.lib.impl.settings;

import io.github.jjdelcerro.noema.lib.settings.AgentSettingsString;

public class AgentSettingsStringImpl implements AgentSettingsString {

  private final String value;

  public AgentSettingsStringImpl(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }
}
