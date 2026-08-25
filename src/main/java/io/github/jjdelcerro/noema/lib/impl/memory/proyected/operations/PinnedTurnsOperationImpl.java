package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;

import io.github.jjdelcerro.noema.lib.memory.proyected.operations.PinnedTurnsOperation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.memory.GsonUtils;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;

/**
 * Operation that manages turn pinning for tools declaring
 * {@link AgentTool#shouldPin()}. Retains pinned request-response pairs at the
 * beginning of the projected context after recent memory compactions, and emits
 * periodic reminders.
 */
public class PinnedTurnsOperationImpl implements PinnedTurnsOperation {

  private static final int PRIORITY = 5; // Executes before TrimmingOperation
  private static final long NOTIFICATION_TURN_INTERVAL = 5L;

  private final List<PinnedTurnStateImpl> pinnedTurns;
  private long lastNotifiedTurn;
  private final Gson gson;

  public static class PinnedTurnStateImpl implements PinnedTurnState {

    private final AiMessage requestMessage;
    private final ToolExecutionResultMessage resultMessage;
    private transient AgentTool tool;

    public PinnedTurnStateImpl(AgentTool tool, AiMessage requestMessage, ToolExecutionResultMessage resultMessage) {
      this.tool = tool;
      this.requestMessage = requestMessage;
      this.resultMessage = resultMessage;
    }

    @Override
    public AgentTool getTool() {
      return tool;
    }

    public void setTool(AgentTool tool) {
      this.tool = tool;
    }

    @Override
    public AiMessage getRequestMessage() {
      return requestMessage;
    }

    @Override
    public ToolExecutionResultMessage getResultMessage() {
      return resultMessage;
    }
  }

  public PinnedTurnsOperationImpl() {
    this.pinnedTurns = new ArrayList<>();
    this.lastNotifiedTurn = 0L;
    this.gson = createGson();
  }

  private Gson createGson() {
    return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ChatMessage.class, new GsonUtils.ChatMessageAdapter())
            .registerTypeAdapter(Content.class, new GsonUtils.ContentAdapter())
            .enableComplexMapKeySerialization()
            .create();
  }

  @Override
  public String getName() {
    return OPERATION_NAME;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  public List<PinnedTurnState> getPinnedTurns() {
    return Collections.unmodifiableList(this.pinnedTurns);
  }

  /**
   * Removes pinned turns matching the given predicate.
   *
   * @param predicate condition to test each pinned turn
   * @return true if any turn was removed
   */
  @Override
  public boolean removePinnedTurn(Predicate<PinnedTurnState> predicate) {
    if (predicate == null) {
      return false;
    }
    return this.pinnedTurns.removeIf(predicate);
  }

  @Override
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    if (projectedMessages == null) {
      return;
    }

    // 1. Re-link transient tool references if necessary
    for (PinnedTurnStateImpl state : this.pinnedTurns) {
      if (state.getTool() == null && state.getResultMessage() != null) {
        state.setTool(memory.getTool(state.getResultMessage().toolName()));
      }
    }

    // 2. Capture newly executed tools requiring pinning
    captureNewPinnedTurns(memory, projectedMessages);

    // 3. Re-inject pinned turns that have been purged by compaction
    reinjectCompactedPinnedTurns(projectedMessages);

    // 4. Emit periodic reminder notifications every 5 turns
    processPeriodicNotifications(memory, notifications);
  }

  private void captureNewPinnedTurns(ProjectedMemory memory, List<ChatMessage> messages) {
    for (int i = 0; i < messages.size(); i++) {
      ChatMessage msg = messages.get(i);
      if (msg instanceof ToolExecutionResultMessage resultMsg) {
        AgentTool tool = memory.getTool(resultMsg.toolName());
        if (tool != null && tool.shouldPin()) {
          boolean alreadyPinned = this.pinnedTurns.stream()
                  .anyMatch(p -> p.getResultMessage().id().equals(resultMsg.id()));

          if (!alreadyPinned) {
            AiMessage requestMsg = findPrecedingAiMessage(messages, i, resultMsg.id());
            if (requestMsg != null) {
              this.pinnedTurns.add(new PinnedTurnStateImpl(tool, requestMsg, resultMsg));
            }
          }
        }
      }
    }
  }

  private AiMessage findPrecedingAiMessage(List<ChatMessage> messages, int resultIndex, String toolCallId) {
    for (int i = resultIndex - 1; i >= 0; i--) {
      ChatMessage candidate = messages.get(i);
      if (candidate instanceof AiMessage aiMsg) {
        if (aiMsg.hasToolExecutionRequests()) {
          for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
            if (req.id().equals(toolCallId)) {
              return aiMsg;
            }
          }
        }
      }
    }
    return null;
  }

  private void reinjectCompactedPinnedTurns(List<ChatMessage> projectedMessages) {
    if (this.pinnedTurns.isEmpty()) {
      return;
    }

    // Locate insertion point right after leading SystemMessage headers
    int insertIndex = 0;
    while (insertIndex < projectedMessages.size() && projectedMessages.get(insertIndex) instanceof SystemMessage) {
      insertIndex++;
    }

    for (PinnedTurnStateImpl pinnedTurn : this.pinnedTurns) {
      String callId = pinnedTurn.getResultMessage().id();
      boolean isPresentInRecent = false;

      for (ChatMessage msg : projectedMessages) {
        if (msg instanceof ToolExecutionResultMessage toolResult && toolResult.id().equals(callId)) {
          isPresentInRecent = true;
          break;
        }
      }

      if (!isPresentInRecent) {
        projectedMessages.add(insertIndex++, pinnedTurn.getRequestMessage());
        projectedMessages.add(insertIndex++, pinnedTurn.getResultMessage());
      }
    }
  }

  private void processPeriodicNotifications(ProjectedMemory memory, List<String> notifications) {
    if (this.pinnedTurns.isEmpty() || notifications == null) {
      return;
    }

    long currentTurn = memory.getLastInteractionTurn();
    if (currentTurn <= 0) {
      return;
    }

    if ((currentTurn - this.lastNotifiedTurn) >= NOTIFICATION_TURN_INTERVAL) {
      for (PinnedTurnStateImpl state : this.pinnedTurns) {
        AgentTool tool = state.getTool();
        if (tool != null && state.getRequestMessage().hasToolExecutionRequests()) {
          for (ToolExecutionRequest req : state.getRequestMessage().toolExecutionRequests()) {
            if (req.id().equals(state.getResultMessage().id())) {
              String message = tool.getPinnedNotificationMessage(req, state.getResultMessage());
              if (StringUtils.isNotBlank(message)) {
                notifications.add(message.trim());
              }
            }
          }
        }
      }
      this.lastNotifiedTurn = currentTurn;
    }
  }

  @Override
  public JsonObject getState() {
    JsonObject state = new JsonObject();
    state.addProperty("lastNotifiedTurn", this.lastNotifiedTurn);

    JsonArray array = new JsonArray();
    for (PinnedTurnStateImpl item : this.pinnedTurns) {
      JsonObject itemObj = new JsonObject();
      itemObj.add("requestMessage", this.gson.toJsonTree(item.getRequestMessage(), ChatMessage.class));
      itemObj.add("resultMessage", this.gson.toJsonTree(item.getResultMessage(), ChatMessage.class));
      array.add(itemObj);
    }
    state.add("pinnedTurns", array);
    return state;
  }

  @Override
  public void restoreState(JsonObject state) {
    if (state == null || state.isEmpty()) {
      return;
    }

    if (state.has("lastNotifiedTurn")) {
      this.lastNotifiedTurn = state.get("lastNotifiedTurn").getAsLong();
    }

    this.pinnedTurns.clear();
    if (state.has("pinnedTurns") && state.get("pinnedTurns").isJsonArray()) {
      JsonArray array = state.getAsJsonArray("pinnedTurns");
      for (JsonElement elem : array) {
        if (elem.isJsonObject()) {
          JsonObject itemObj = elem.getAsJsonObject();
          AiMessage req = (AiMessage) this.gson.fromJson(itemObj.get("requestMessage"), ChatMessage.class);
          ToolExecutionResultMessage res = (ToolExecutionResultMessage) this.gson.fromJson(itemObj.get("resultMessage"), ChatMessage.class);
          if (req != null && res != null) {
            this.pinnedTurns.add(new PinnedTurnStateImpl(null, req, res));
          }
        }
      }
    }
  }
}
