package io.github.jjdelcerro.noema.lib.impl.services.scheduler;

import io.github.jjdelcerro.noema.lib.AgentService;
import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public interface SchedulerService extends AgentService {

  public static final String NAME = "Scheduler";
  
  String schedule(LocalDateTime when, String reason);
  
}
