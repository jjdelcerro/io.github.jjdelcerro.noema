package io.github.jjdelcerro.noema.lib.settings;

import java.util.List;

/**
 * Representa una lista de elementos donde cada uno tiene un estado
 * marcado/desmarcado. Útil para activación de herramientas o documentos de
 * gobierno.
 */
public interface AgentSettingsCheckedList extends AgentSettingsItem {

  List<CheckedItem> getItems();

  interface CheckedItem {

    String getValue();

    boolean isChecked();
  }
}
