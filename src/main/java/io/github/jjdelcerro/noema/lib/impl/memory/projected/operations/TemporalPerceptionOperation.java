package io.github.jjdelcerro.noema.lib.impl.memory.projected.operations;

import dev.langchain4j.data.message.ChatMessage;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.impl.utils.DateUtils;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemoryOperation;
import io.github.jjdelcerro.noema.lib.spi.memory.projected.AbstractProjectedMemoryOperation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class TemporalPerceptionOperation extends AbstractProjectedMemoryOperation implements ProjectedMemoryOperation {

    private static final long DEFAULT_HOURS_THRESHOLD = 1L;
    private static final int PRIORITY = 30;

    private final long hoursThreshold;

    public TemporalPerceptionOperation(Agent agent) {
        this(agent,DEFAULT_HOURS_THRESHOLD);
    }

    public TemporalPerceptionOperation(Agent agent, long hoursThreshold) {
      super(agent, "temporal_perception", PRIORITY);
        this.hoursThreshold = hoursThreshold;
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

        String timeNotice = "[CONSCIENCIA TEMPORAL] Ha pasado " + DateUtils.timeAgo(lastTime) + " desde la ultima interaccion con el usuario.";
        notifications.add(timeNotice);
    }
}
