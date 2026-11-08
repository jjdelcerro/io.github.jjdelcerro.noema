package io.github.jjdelcerro.noema.lib.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.jjdelcerro.noema.lib.api.AgentConsole;

/**
 * Fallback console used when no UI console is registered for a subchannel.
 * It never returns null and writes every message to the application log, so
 * a disconnected terminal does not abort the reasoning thread and the
 * information is not silently lost.
 *
 * Confirmation requests are denied on purpose: without an interactive
 * console there is no human to authorize the operation.
 *
 * @author jjdelcerro
 */
public final class LoggingAgentConsole implements AgentConsole {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAgentConsole.class);

  private static final LoggingAgentConsole INSTANCE = new LoggingAgentConsole();

  private volatile boolean streamingUsed;

  private LoggingAgentConsole() {
  }

  public static LoggingAgentConsole getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean confirm(String message) {
    LOGGER.warn("[CONSOLE-UNAVAILABLE] Confirmation denied, no interactive console: {}", message);
    return false;
  }

  @Override
  public void printSystemLog(String message) {
    LOGGER.info("[SYS] {}", message);
  }

  @Override
  public void printSystemLog(String message, Format format) {
    LOGGER.info("[SYS] {}", message);
  }

  @Override
  public void printSystemError(String message) {
    LOGGER.error("[SYS] {}", message);
  }

  @Override
  public void printUserMessage(String message) {
    LOGGER.info("[USR] {}", message);
  }

  @Override
  public void printModelResponse(String message) {
    LOGGER.info("[MODEL] {}", message);
  }

  @Override
  public void printModelReasoning(String message) {
    LOGGER.debug("[THINKING] {}", message);
  }

  @Override
  public void StreamReasoning(String s) {
    // Per-token streaming is intentionally not logged: it would flood the log.
  }

  @Override
  public void StreamResponse(String s) {
    // Per-token streaming is intentionally not logged: it would flood the log.
  }

  @Override
  public void streamingFinished() {
    // Nothing to flush: streaming tokens are not logged.
  }

  @Override
  public boolean streamingUsed() {
    return this.streamingUsed;
  }

  @Override
  public void setStreamingUsed(boolean used) {
    this.streamingUsed = used;
  }

}
