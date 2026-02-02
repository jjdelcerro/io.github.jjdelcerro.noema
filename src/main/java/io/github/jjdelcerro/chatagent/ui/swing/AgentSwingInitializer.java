package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.ui.AgentUILocator;

/**
 *
 * @author jjdelcerro
 */
public class AgentSwingInitializer {

  public static void init() {
    AgentUILocator.registerAgentUIManager(new AgentSwingManagerImpl());
  }
}
