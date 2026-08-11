package io.github.jjdelcerro.noema.lib.impl.settings;

import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AgentSettingsListImpl implements AgentSettingsList {

    private final List<AgentSettingsItem> items;

    public AgentSettingsListImpl(List<AgentSettingsItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    @Override
    public AgentSettingsItem get(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public Iterator<AgentSettingsItem> iterator() {
        return items.iterator();
    }
}
