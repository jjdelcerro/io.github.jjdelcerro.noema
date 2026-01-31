package io.github.jjdelcerro.chatagent.lib;

import io.github.jjdelcerro.chatagent.lib.persistence.SourceOfTruth;
import java.io.File;

/**
 *
 * @author jjdelcerro
 */
public interface Agent {

  public File getDataFolder();
  
  public AgentActions getActions();

  public AgentSettings getSettings();

  public AgentConsole getConsole();

  public SourceOfTruth getSourceOfTruth();

  public String processTurn(String input);
  
  public void putEvent(String channel, String priority, String eventText);

}
