package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.Map;

/**
 * Borra una nota.
 *
 * La nota se localiza por su identificador: el cliente no necesita conocer la
 * ruta, que cambia cada vez que el editor la traslada al mes corriente.
 *
 * @author jjdelcerro
 */
public class HandlerDeleteNote extends AbstractHandler {

  public HandlerDeleteNote(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String id = ctx.pathParam("id");
    if (!NoteStore.isValidId(id)) {
      ctx.status(400).json(Map.of("status", "error", "message", "Identificador de nota invalido"));
      return;
    }
    NoteStore store = NotesSupport.store(this.agent);
    try {
      Path path = store.find(id);
      if (path == null) {
        ctx.status(404).json(Map.of("status", "error", "message", "La nota no existe"));
        return;
      }
      store.delete(id);
      ctx.json(Map.of("status", "ok", "id", id));
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("status", "error", "message", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo borrar la nota {}", id, e);
      ctx.status(500).json(Map.of("status", "error", "message", "No se pudo borrar la nota"));
    }
  }
}
