package io.github.jjdelcerro.noema.lib.settings;

import java.util.Iterator;

public interface AgentSettingsList extends AgentSettingsItem, Iterable<AgentSettingsItem> {

    AgentSettingsItem get(int index);

    int size();

    @Override
    Iterator<AgentSettingsItem> iterator();
}
