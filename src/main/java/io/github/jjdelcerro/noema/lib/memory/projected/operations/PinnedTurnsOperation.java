package io.github.jjdelcerro.noema.lib.memory.projected.operations;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperation;
import java.util.List;
import java.util.function.Predicate;

/**
 *
 * @author jjdelcerro
 */
public interface PinnedTurnsOperation extends ProjectedMemoryOperation {

  public interface PinnedTurnState {

    AiMessage getRequestMessage();

    ToolExecutionResultMessage getResultMessage();

    AgentTool getTool();

  }

  String OPERATION_NAME = "pinned_turns";

  /**
   * Removes pinned turns matching the given predicate.
   *
   * @param predicate condition to test each pinned turn
   * @return true if any turn was removed
   */
  boolean removePinnedTurn(Predicate<PinnedTurnState> predicate);
  
  public List<PinnedTurnState> getPinnedTurns();

}
