package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.Note;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Devuelve una nota completa por su identificador.
 *
 * Incluye la ruta relativa del fichero, porque la nota pudo moverse de mes y
 * el cliente no puede deducirla a partir del id.
 *
 * @author jjdelcerro
 */
public class HandlerGetNote extends AbstractHandler {

  public HandlerGetNote(Agent agent) {
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
      Note note = store.read(path);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("id", note.getId());
      response.put("title", note.getTitle());
      response.put("tags", note.getTags());
      response.put("created", note.getCreated());
      response.put("updated", note.getUpdated());
      response.put("content", note.getContent());
      response.put("path", store.relativePath(path));
      ctx.json(response);
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("status", "error", "message", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo leer la nota {}", id, e);
      ctx.status(500).json(Map.of("status", "error", "message", "No se pudo leer la nota"));
    }
  }
}
