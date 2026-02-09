package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import io.github.jjdelcerro.chatagent.ui.AgentUILocator;

/**
 *
 * @author jjdelcerro
 */
public class AgentSwingInitializer {

  public static void init(AgentConsole console) {
    AgentUILocator.registerAgentUIManager(new AgentSwingManagerImpl(console));
  }
}
