package io.github.jjdelcerro.noema.ui.swing.settings;

import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import javax.swing.JComponent;

public interface AgentSettingsItemSwing extends AgentSettingsItemUI {

  JComponent getComponent();

  boolean isLeaf();
  
}
