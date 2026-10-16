package io.github.jjdelcerro.noema.ui.webclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.jjdelcerro.noema.lib.api.AgentConsole;
import io.javalin.http.sse.SseClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class SseAgentConsole implements AgentConsole {

  private final String subchannel;
  private final List<SseClient> clients = new CopyOnWriteArrayList<>();
  private final BufferedOutput bufferedResponse = new BufferedOutput(
          (String t) -> {
            broadcast("response", t);
          },
          500
  );
  private final BufferedOutput bufferedReasoning = new BufferedOutput(
          (String t) -> {
            broadcast("thinking", t);
          },
          500
  );
  private boolean streamingUsed;

  @Override
  public boolean streamingUsed() {
    return this.streamingUsed;
  }

  @Override
  public void setStreamingUsed(boolean used) {
    this.streamingUsed = used;
  }

  private class BufferedOutput {

    private final Consumer<String> output;
    private final StringBuffer buffer;
    private boolean active;
    private final int delayms;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public BufferedOutput(Consumer<String> output, int delayms) {
      this.output = output;
      this.buffer = new StringBuffer();
      this.delayms = delayms;
      this.scheduler = Executors.newSingleThreadScheduledExecutor(
              Thread.ofPlatform()
                      .daemon(true)
                      .name("noema-buffer-stream-" + subchannel)
                      .factory()
      );
      this.task = null;
      this.active = false;
    }

    public synchronized void add(String s) {
      boolean startTimer = this.buffer.length() == 0;
      this.active = true;
      this.buffer.append(s);
      if (startTimer && this.task == null) {
        this.task = this.scheduler.schedule(this::flush, delayms, TimeUnit.MILLISECONDS);
      }
    }

    private synchronized void flush() {
      String s = buffer.toString();
      if (StringUtils.isNotBlank(s)) {
        try {
          output.accept(s);
        } catch (Exception ex) {
          // Do nothing
        }
      }
      buffer.setLength(0);
      this.task = null;
    }

    public synchronized boolean isActive() {
      return this.active;
    }

    public synchronized void endblock() {
      if (this.task != null) {
        this.task.cancel(false);
      }
      if (this.buffer.length() > 0) {
        String s = buffer.toString();
        if (StringUtils.isNotBlank(s)) {
          try {
            output.accept(s);
          } catch (Exception ex) {
            // Do nothing
          }
        }
        buffer.setLength(0);
      }
      this.active = false;
    }

  }

  public SseAgentConsole(String subchannel) {
    this.subchannel = subchannel;
  }

  public void addClient(SseClient client) {
    this.clients.add(client);
  }

  public void removeClient(SseClient client) {
    this.clients.remove(client);
  }

  public boolean isEmpty() {
    return this.clients.isEmpty();
  }

  /**
   * Envia un evento ad-hoc al cliente. El contenido se serializa como
   * JsonElement dentro del sobre estandar {content, timestamp}, igual que el
   * resto de eventos de este canal.
   *
   * Pensado para tools del agente que necesitan empujar ordenes al cliente web
   * (por ejemplo editor_open, editor_apply). El fallo al enviar a un cliente
   * concreto se silencia y el cliente se elimina de la lista, como en
   * broadcast().
   */
  public void sendEvent(String eventType, JsonObject payload) {
    broadcastJson(eventType, payload);
  }

  private void broadcast(String eventType, String content) {
    broadcastJson(eventType, new JsonPrimitive(content));
  }

  private void broadcastJson(String eventType, JsonElement content) {
    JsonObject json = new JsonObject();
    json.add("content", content);
    json.addProperty("timestamp", System.currentTimeMillis());
    String data = new Gson().toJson(json);

    for (SseClient client : clients) {
      try {
        client.sendEvent(eventType, data);
      } catch (Exception e) {
        clients.remove(client);
      }
    }
  }

  @Override
  public boolean confirm(String message) {
    return true;
  }

  @Override
  public void printSystemError(String message) {
    broadcast("error", message);
  }

  @Override
  public void printSystemLog(String message) {
    broadcast("log", message);
  }

  @Override
  public void printSystemLog(String message, AgentConsole.Format format) {
    broadcast("log", message);
  }

  @Override
  public void printUserMessage(String message) {
    // Los mensajes de usuario son manejados localmente en la UI
  }

  @Override
  public void printModelResponse(String message) {
    if (this.streamingUsed()) {
      bufferedResponse.endblock();
    } else {
      broadcast("response", message);
    }
  }

  @Override
  public void printModelReasoning(String message) {
    if (this.streamingUsed()) {
      bufferedReasoning.endblock();
    } else {
      broadcast("thinking", message);
    }
  }

  @Override
  public void streamingFinished() {
    if (this.streamingUsed()) {
      this.bufferedReasoning.endblock();
      this.bufferedResponse.endblock();
      broadcast("stream_end", "");
    }
  }

  @Override
  public void StreamReasoning(String s) {
    this.bufferedReasoning.add(s);
  }

  @Override
  public void StreamResponse(String s) {
    this.bufferedResponse.add(s);
  }

}
