package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.impl.utils.ConversationJsonToXml;
import io.javalin.http.Context;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class HandlerGetContextXml extends AbstractHandler {

  public HandlerGetContextXml(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String terminalId = ctx.pathParam("terminalId");
    String json = this.agent.getProjectedMemoryAsJson(terminalId);

    if (StringUtils.isBlank(json)) {
      ctx.status(404).json(Map.of("error", "No hay contexto proyectado disponible para el terminal: " + terminalId));
      return;
    }

    try (StringReader reader = new StringReader(json); StringWriter writer = new StringWriter()) {
      // Usamos "/css/conversation.xsl" para que el navegador lo renderice nativamente
      // si alguien abre la URL del XML directamente en una pestaña
      ConversationJsonToXml.json2xml(reader, writer, "/css/conversation.xsl");

      byte[] payload = writer.toString().getBytes(StandardCharsets.UTF_8);
      ctx.contentType("application/xml; charset=UTF-8").result(payload);
    } catch (Exception e) {
      LOGGER.error("Error transformando contexto a XML para el terminal '{}'", terminalId, e);
      ctx.status(500).json(Map.of("error", "Fallo al transformar contexto a XML: " + e.getMessage()));
    }
  }
  
}
