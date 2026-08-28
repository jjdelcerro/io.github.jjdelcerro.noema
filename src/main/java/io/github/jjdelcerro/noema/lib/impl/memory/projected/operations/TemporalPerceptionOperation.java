package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.impl.DateUtils;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.projected.ProjectedMemoryOperation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class TemporalPerceptionOperation implements ProjectedMemoryOperation {

    private static final long DEFAULT_HOURS_THRESHOLD = 1L;
    private static final int PRIORITY = 30;

    private final long hoursThreshold;

    public TemporalPerceptionOperation() {
        this(DEFAULT_HOURS_THRESHOLD);
    }

    public TemporalPerceptionOperation(long hoursThreshold) {
        this.hoursThreshold = hoursThreshold;
    }

    @Override
    public String getName() {
        return "temporal_perception";
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
        LocalDateTime lastTime = memory.getLastInteractionTime();
        if (lastTime == null || projectedMessages.isEmpty()) {
            return;
        }

        Duration delta = Duration.between(lastTime, LocalDateTime.now());
        if (delta.toHours() < this.hoursThreshold) {
            return;
        }

        String timeNotice = "Ha pasado " + DateUtils.timeAgo(lastTime) + " desde la ultima interaccion con el usuario.";
        notifications.add(timeNotice);
    }

    @Override
    public JsonObject getState() {
        return null;
    }

    @Override
    public void restoreState(JsonObject state) {
        // Operacion sin estado persistente propio
    }
}
