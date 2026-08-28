package io.github.jjdelcerro.noema.lib.memory.projected;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

public interface ProjectedMemoryOperation {
    String getName();
    int getPriority();
    void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications);
    
    JsonObject getState();
    void restoreState(JsonObject state);
}
