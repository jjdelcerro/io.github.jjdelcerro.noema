package io.github.jjdelcerro.noema.lib.impl.settings;

import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsGroup;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsItem;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsPaths;
import io.github.jjdelcerro.noema.lib.settings.AgentSettingsString;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.BooleanUtils;

public class AgentSettingsGroupImpl implements AgentSettingsGroup {

  protected transient final Map<String, AgentSettingsItem> items = new ConcurrentHashMap<>();

  @Override
  public AgentSettingsItem getProperty(String path) {
    return findNode(path, false);
  }

  @Override
  public String getPropertyAsString(String path) {
    return getPropertyAsString(path, null);
  }

  @Override
  public String getPropertyAsString(String path, String defaultValue) {
    AgentSettingsItem item = getProperty(path);
    if (item instanceof AgentSettingsString s) {
      return s.getValue();
    }
    return defaultValue;
  }

  @Override
  public int getPropertyAsInt(String path, int defaultValue) {
    String val = getPropertyAsString(path);
    try {
      return val != null ? Integer.parseInt(val) : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public boolean getPropertyAsBoolean(String path, boolean defaultValue) {
    String val = getPropertyAsString(path);
    try {
      return BooleanUtils.toBoolean(val);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public long getPropertyAsLong(String path, long defaultValue) {
    String val = getPropertyAsString(path);
    try {
      return val != null ? Long.parseLong(val) : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  @Override
  public List<Path> getPropertyAsPaths(String path) {
    AgentSettingsItem item = getProperty(path);
    if (item instanceof AgentSettingsPaths p) {
      return p.getValues();
    }
    return Collections.emptyList();
  }

  @Override
  public AgentSettingsGroup getPropertyGroup(String path) {
    AgentSettingsItem item = getProperty(path);
    if (item instanceof AgentSettingsGroup g) {
      return g;
    }
    return null;
  }

  @Override
  public AgentSettingsCheckedList getPropertyAsCheckedList(String path) {
    AgentSettingsItem item = getProperty(path);
    if (item instanceof AgentSettingsCheckedList cl) {
      return cl;
    }
    return null;
  }

  @Override
  public void setProperty(String path, String value) {
    NodeInfo parent = findParentNode(path, true);
    parent.group.items.put(parent.targetName, new AgentSettingsStringImpl(value));
  }

  @Override
  public void setProperty(String path, List<String> values) {
    NodeInfo parent = findParentNode(path, true);
    parent.group.items.put(parent.targetName, new AgentSettingsPathsImpl(values));
  }

  @Override
  public void setChecked(String path, String value, boolean checked) {
    AgentSettingsCheckedList list = getPropertyAsCheckedList(path);
    if (list == null) {
      list = new AgentSettingsCheckedListImpl(new ArrayList<>());
      NodeInfo parent = findParentNode(path, true);
      parent.group.items.put(parent.targetName, list);
    }

    List<AgentSettingsCheckedListImpl.CheckedItemImpl> internal
            = ((AgentSettingsCheckedListImpl) list).getInternalItems();

    internal.stream()
            .filter(i -> i.getValue().equals(value))
            .findFirst()
            .ifPresentOrElse(
                    i -> i.setChecked(checked),
                    () -> internal.add(new AgentSettingsCheckedListImpl.CheckedItemImpl(checked, value))
            );
  }

  // --- Helper para navegación ---
  private record NodeInfo(AgentSettingsGroupImpl group, String targetName) {

  }

  private NodeInfo findParentNode(String path, boolean create) {
    String[] parts = path.split("/");
    AgentSettingsGroupImpl current = this;
    for (int i = 0; i < parts.length - 1; i++) {
      AgentSettingsItem next = current.items.get(parts[i]);
      if (next == null && create) {
        next = new AgentSettingsGroupImpl();
        current.items.put(parts[i], next);
      }
      if (!(next instanceof AgentSettingsGroupImpl)) {
        return null;
//        throw new IllegalStateException("Camino bloqueado por nodo no-grupo en: " + parts[i]);
      }
      current = (AgentSettingsGroupImpl) next;
    }
    return new NodeInfo(current, parts[parts.length - 1]);
  }

  private AgentSettingsItem findNode(String path, boolean create) {
    if (path.isEmpty()) {
      return this;
    }
    NodeInfo info = findParentNode(path, create);
    if (info == null) {
      return null;
    }
    return info.group.items.get(info.targetName);
  }

  // Usado por el serializador
  public Map<String, AgentSettingsItem> getItems() {
    return items;
  }
}
