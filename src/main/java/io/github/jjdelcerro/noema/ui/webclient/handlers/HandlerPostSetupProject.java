package io.github.jjdelcerro.noema.ui.webclient.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.mcp.McpServiceImpl;
import io.github.jjdelcerro.noema.ui.webclient.NoemaWebServer;
import io.javalin.http.Context;
import java.util.Map;

/**
 * Lanza 'project_setup' sobre el servidor MCP 'aide' en un hilo aparte y
 * notifica al cliente web cuando termina.
 *
 * Esta clase es un singleton: se instancia una sola vez en el start del
 * servidor y se reutiliza para todas las peticiones. El monitor del propio
 * objeto serializa las peticiones: solo puede haber un setup en curso en
 * todo el servidor, aunque los proyectos sean distintos.
 *
 * Flujo:
 *   1. El cliente hace POST con {path, terminalId}.
 *   2. El handler comprueba si hay un setup en curso. Si lo hay, devuelve
 *      409 sin lanzar nada.
 *   3. Si no lo hay, marca el estado y lanza un hilo. Devuelve 202 de
 *      inmediato.
 *   4. Cuando el hilo termina, limpia el estado y notifica al cliente por
 *      el canal SSE del subcanal indicado, con evento 'editor_setup_done'
 *      o 'editor_setup_error'.
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class HandlerPostSetupProject extends AbstractHandler {

  // Estado del setup en curso. Protegidos por el monitor de la instancia
  // (todos los accesos van dentro de bloques synchronized(this)).
  private boolean setupRunning = false;
  private String runningSubchannel = null;

  public HandlerPostSetupProject(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    JsonObject body;
    try {
      body = JsonParser.parseString(ctx.body()).getAsJsonObject();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "JSON invalido en el cuerpo de la peticion"));
      return;
    }

    String path = body.has("path") ? body.get("path").getAsString() : null;
    String terminalId = body.has("terminalId") ? body.get("terminalId").getAsString() : null;

    if (path == null || path.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'path' es obligatorio"));
      return;
    }
    if (terminalId == null || terminalId.isBlank()) {
      ctx.status(400).json(Map.of("error", "El campo 'terminalId' es obligatorio"));
      return;
    }

    synchronized (this) {
      if (this.setupRunning) {
        ctx.status(409).json(Map.of(
                "error", "Ya hay un setup-project en curso. Espere a que termine."
        ));
        return;
      }
      this.setupRunning = true;
      this.runningSubchannel = terminalId;
    }

    // Lanzamos el setup en un hilo separado. El handler responde ya.
    Thread worker = new Thread(() -> runSetup(path, terminalId), "noema-setup-project");
    worker.setDaemon(true);
    worker.start();

    ctx.status(202).json(Map.of("status", "accepted"));
  }

  /**
   * Cuerpo del hilo: ejecuta el setup, limpia el estado y notifica al
   * cliente. Cualquier fallo se traduce en un evento de error al cliente
   * y en log en servidor. La limpieza de estado va en finally para que un
   * fallo no deje el setup bloqueado.
   */
  private void runSetup(String path, String terminalId) {
    String status;
    String message;
    try {
      McpServiceImpl serviceMCP = (McpServiceImpl) this.agent.getService(McpServiceImpl.NAME);
      if (serviceMCP == null) {
        throw new IllegalStateException("Servicio MCP no disponible");
      }
      AgentTool tool = serviceMCP.getFirstTool("aide", "project_setup");
      if (tool == null) {
        throw new IllegalStateException("Herramienta project_setup no encontrada");
      }

      String projectRoot = findProjectFolder(path);
      if (projectRoot == null) {
          throw new IllegalStateException("No se encontró pom.xml subiendo desde " + path);
      }
      String toolArgs = String.format("{  \"path\": \"%s\" }", projectRoot);      
      String resp = tool.execute(toolArgs);

      status = "ok";
      message = resp != null ? resp : "";
      LOGGER.info("project_setup completado para path={}", path);
    } catch (Exception e) {
      LOGGER.warn("project_setup fallo para path={}", path, e);
      status = "error";
      message = e.getMessage() != null ? e.getMessage() : "Error desconocido";
    } finally {
      synchronized (this) {
        this.setupRunning = false;
        this.runningSubchannel = null;
      }
    }

    notifyClient(terminalId, status, message);
  }

  /**
   * Notifica al cliente el resultado del setup a traves del canal SSE.
   * Si no hay consola activa para el subcanal (el usuario cerro la
   * pestana durante el setup), se loguea y no se hace nada.
   */
  private void notifyClient(String terminalId, String status, String message) {
    NoemaWebServer webserver = this.getWebServer();
    if (webserver == null) {
      return;
    }
    webserver.notifyEditorSetupResult(terminalId, status, message);
  }
}
