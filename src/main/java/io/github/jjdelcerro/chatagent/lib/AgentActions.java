package io.github.jjdelcerro.chatagent.lib;

/**
 *
 * @author jjdelcerro
 */
public interface AgentActions {

  public static final String CHANGE_MEMORY_PROVIDER = "CHANGE_MEMORY_PROVIDER";
  public static final String CHANGE_MEMORY_MODEL = "CHANGE_MEMORY_MODEL";
  public static final String CHANGE_CONVERSATION_PROVIDER = "CHANGE_CONVERSATION_PROVIDER";
  public static final String CHANGE_CONVERSATION_MODEL = "CHANGE_CONVERSATION_MODEL";

  public interface AgentAction {

    public boolean perform(AgentSettings settings);
  }

  public void addAction(String name, AgentAction action);

  public boolean call(String name, AgentSettings settings);
}
