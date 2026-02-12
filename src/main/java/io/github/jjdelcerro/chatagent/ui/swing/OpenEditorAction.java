package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AbstractAgentAction;
import io.github.jjdelcerro.chatagent.lib.AgentLocator;
import io.github.jjdelcerro.chatagent.lib.AgentManager;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import java.io.File;
import javax.swing.SwingUtilities;

/**
 *
 * @author jjdelcerro
 */
public class OpenEditorAction extends AbstractAgentAction {

  public static final void registerAction(String actionName, String fname) {
      AgentManager agentManager = AgentLocator.getAgentManager();     
      agentManager.registerAction(() -> new OpenEditorAction(actionName,fname));
  }
  
  private final String fname;

  public OpenEditorAction(String actionName, String fname) {
    super(actionName);
    this.fname = fname;
  }

  @Override
  public boolean perform(AgentSettings settings) {
    File target = this.getAgent().getDataFolder(this.fname);
    SwingUtilities.invokeLater(() -> {
      SimpleTextEditor editor = new SimpleTextEditor();
      editor.load(target);
      editor.showWindow("Editor");
    });
    return true;
  }
}
