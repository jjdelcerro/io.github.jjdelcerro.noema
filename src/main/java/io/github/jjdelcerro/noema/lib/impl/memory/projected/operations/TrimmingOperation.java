package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.AgentTool.ToolResult;
import io.github.jjdelcerro.noema.lib.api.AgentTool.TrimResultType;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZoneType;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.impl.memory.projected.ContextZoningImpl;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolResultImpl;
import java.util.List;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ContextZoning.ContextZone;

public class TrimmingOperation implements ProjectedMemoryOperation {

  private static final int MINIMUM_SIZE_FOR_TRIM = 1024;
  private static final int PRIORITY = 10;

  private final int minimumSizeForTrim;

  public TrimmingOperation() {
    this(MINIMUM_SIZE_FOR_TRIM);
  }

  public TrimmingOperation(int minimumSizeForTrim) {
    this.minimumSizeForTrim = minimumSizeForTrim;
  }

  @Override
  public String getName() {
    return "trimming";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

//    @Override
//    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
//        int total = projectedMessages.size();
//        int safeLimit = total - this.messagesToKeep;
//
//        for (int i = 0; i < total; i++) {
//            if (i >= safeLimit) {
//                break;
//            }
//
//            ChatMessage message = projectedMessages.get(i);
//            if (message instanceof ToolExecutionResultMessage toolResult) {
//                AgentTool tool = memory.getTool(toolResult.toolName());
//
//                if (tool != null) {
//                    String text = toolResult.text();
//                    if (text != null && text.length() > this.minimumSizeForTrim) {
//                        ToolResult trimmedText = tool.trimResult(text, TrimResultType.Trim);
//                        if (trimmedText != null) {
//                          ToolExecutionResultMessage trimmedMessage = ToolResultImpl.toToolExecutionResultMessage(
//                                  toolResult.id(),
//                                  toolResult.toolName(),
//                                  trimmedText
//                          );
//                          projectedMessages.set(i, trimmedMessage);
//                        }
//                    }
//                }
//            }
//        }
//    }
  @Override
  public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
    int total = projectedMessages.size();

    ContextZoning zoning = memory.getContextZoning();
    ContextZone timmingZone = zoning.getZone(ContextZoneType.PRUNABLE, total);

    for (int i = 0; i < total; i++) {
      if (timmingZone.contains(i)) {
        ChatMessage message = projectedMessages.get(i);
        if (message instanceof ToolExecutionResultMessage toolResult) {
          AgentTool tool = memory.getTool(toolResult.toolName());

          if (tool != null) {
            String text = toolResult.text();
            if (text != null && text.length() > this.minimumSizeForTrim) {
              ToolResult trimmedText = tool.trimResult(text, TrimResultType.Trim);
              if (trimmedText != null) {
                ToolExecutionResultMessage trimmedMessage = ToolResultImpl.toToolExecutionResultMessage(
                        toolResult.id(),
                        toolResult.toolName(),
                        trimmedText
                );
                projectedMessages.set(i, trimmedMessage);
              }
            }
          }
        }
      }
    }
  }

  @Override
  public JsonObject getState() {
    return null;
  }

  @Override
  public void restoreState(JsonObject state) {
    // Operacion sin estado persistente
  }
}
