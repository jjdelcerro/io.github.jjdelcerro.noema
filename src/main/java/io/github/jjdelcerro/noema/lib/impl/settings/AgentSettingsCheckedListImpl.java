package io.github.jjdelcerro.noema.lib.impl.settings;

import io.github.jjdelcerro.noema.lib.settings.AgentSettingsCheckedList;
import java.util.ArrayList;
import java.util.List;

public class AgentSettingsCheckedListImpl implements AgentSettingsCheckedList {

  public static class CheckedItemImpl implements CheckedItem {

    private boolean checked;
    private String value;

    public CheckedItemImpl(boolean checked, String value) {
      this.checked = checked;
      this.value = value;
    }

    @Override
    public boolean isChecked() {
      return checked;
    }

    @Override
    public String getValue() {
      return value;
    }

    public void setChecked(boolean checked) {
      this.checked = checked;
    }
  }

  private final List<CheckedItemImpl> items;

  public AgentSettingsCheckedListImpl(List<CheckedItemImpl> items) {
    this.items = items != null ? items : new ArrayList<>();
  }

  @Override
  public List<CheckedItem> getItems() {
    return new ArrayList<>(items);
  }

  public List<CheckedItemImpl> getInternalItems() {
    return items;
  }
}
