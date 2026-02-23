package io.github.jjdelcerro.noema.ui.common;

import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface AgentSettingsItem {
    String getType();
    String getLabel();
    String getVariableName();
    String getActionName();
    String getValue();
    List<AgentSettingsItem> getChilds();
    AgentSettingsItem getParent();
    public boolean isRequired();
     
}
