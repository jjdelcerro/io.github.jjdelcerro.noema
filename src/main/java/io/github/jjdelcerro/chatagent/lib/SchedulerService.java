package io.github.jjdelcerro.chatagent.lib;

import java.time.LocalDateTime;

/**
 *
 * @author jjdelcerro
 */
public interface SchedulerService {

  void schedule(LocalDateTime when, String reason);

  void start();
  
}
