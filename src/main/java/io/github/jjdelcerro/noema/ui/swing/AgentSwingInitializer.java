package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.ui.AgentUILocator;

/**
 *
 * @author jjdelcerro
 */
public class AgentSwingInitializer {

  public static void init(AgentConsole console) {
    AgentUILocator.registerAgentUIManager(new AgentSwingManagerImpl(console));
  }
}
