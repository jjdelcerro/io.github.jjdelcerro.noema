package io.github.jjdelcerro.noema.ui.common;

import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface AgentSettingsItemUI {
    String getType();
    String getLabel();
    String getVariableName();
    String getActionName();
    String getValue();
    List<AgentSettingsItemUI> getChilds();
    AgentSettingsItemUI getParent();
    public boolean isRequired();
    public boolean isEnabled();
     
}
