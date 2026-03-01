package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.AbstractAgentAction;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import java.io.File;
import java.nio.file.Path;
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
    Path target = this.getAgent().getPaths().getConfigFolder().resolve(this.fname);
    SwingUtilities.invokeLater(() -> {
      SimpleTextEditor editor = new SimpleTextEditor();
      editor.load(target.toFile());
      editor.showWindow("Editor");
    });
    return true;
  }
}
