package io.github.jjdelcerro.noema.lib.impl.memory.proyected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.AgentTool.TrimResultType;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemoryOperation;
import java.util.List;

public class TrimmingOperation implements ProjectedMemoryOperation {

    private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
    private static final int MINIMUM_SIZE_FOR_TRIM = 1024;
    private static final int PRIORITY = 10;

    private final int messagesToKeep;
    private final int minimumSizeForTrim;

    public TrimmingOperation() {
        this(DEFAULT_MESSAGES_TO_KEEP, MINIMUM_SIZE_FOR_TRIM);
    }

    public TrimmingOperation(int messagesToKeep, int minimumSizeForTrim) {
        this.messagesToKeep = messagesToKeep;
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

    @Override
    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
        int total = projectedMessages.size();
        int safeLimit = total - this.messagesToKeep;

        for (int i = 0; i < total; i++) {
            if (i >= safeLimit) {
                break;
            }

            ChatMessage message = projectedMessages.get(i);
            if (message instanceof ToolExecutionResultMessage toolResult) {
                AgentTool tool = memory.getTool(toolResult.toolName());

                if (tool != null) {
                    String text = toolResult.text();
                    if (text != null && text.length() > this.minimumSizeForTrim) {
                        String trimmedText = tool.trimResult(text, TrimResultType.Trim);
                        if (trimmedText != null) {
                            ToolExecutionResultMessage trimmedMessage = ToolExecutionResultMessage.from(
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

    @Override
    public JsonObject getState() {
        return null;
    }

    @Override
    public void restoreState(JsonObject state) {
        // Operacion sin estado persistente
    }
}
