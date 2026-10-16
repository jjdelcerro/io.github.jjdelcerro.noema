package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.javalin.http.Context;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class HandlerPostAction extends AbstractHandler {

  public HandlerPostAction(Agent agent) {
    super(agent);
  }
  
  @Override
  public void handle(Context ctx) throws Exception {
    String actionName = ctx.pathParam("actionName");
    if (StringUtils.isBlank(actionName)) {
      ctx.status(400).json(Map.of("status", "error", "message", "Nombre de acción no especificado"));
      return;
    }

    try {
      boolean success = this.getAgent().getActions().call(actionName.trim(), this.getAgent().getSettings());
      if (success) {
        ctx.json(Map.of("status", "success", "action", actionName));
      } else {
        ctx.status(500).json(Map.of(
                "status", "error",
                "message", "La acción '" + actionName + "' devolvió un estado no exitoso o no se encontró"
        ));
      }
    } catch (Exception e) {
      LOGGER.error("Error ejecutando acción '{}' desde la API web", actionName, e);
      ctx.status(500).json(Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : "Error interno"));
    }
  }
  
}
