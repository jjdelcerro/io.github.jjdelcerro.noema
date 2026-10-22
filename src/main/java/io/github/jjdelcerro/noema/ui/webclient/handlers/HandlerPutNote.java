package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.Note;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actualiza una nota existente.
 *
 * Solo se aceptan title, tags y content. El identificador y la fecha de
 * creacion los manda el fichero, no el cliente. Como el guardado puede
 * trasladar la nota al mes corriente, la respuesta devuelve la ruta nueva.
 *
 * @author jjdelcerro
 */
public class HandlerPutNote extends AbstractHandler {

  public HandlerPutNote(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String id = ctx.pathParam("id");
    if (!NoteStore.isValidId(id)) {
      ctx.status(400).json(Map.of("status", "error", "message", "Identificador de nota invalido"));
      return;
    }
    Note body = ctx.bodyAsClass(Note.class);
    if (body == null) {
      ctx.status(400).json(Map.of("status", "error", "message", "Falta el cuerpo de la nota"));
      return;
    }
    body.setId(id);
    body.setCreated(null);
    body.setTitle(body.getTitle() == null ? "" : body.getTitle());
    body.setContent(body.getContent() == null ? "" : body.getContent());
    body.setTags(body.getTags() == null ? new ArrayList<>() : body.getTags());

    NoteStore store = NotesSupport.store(this.agent);
    try {
      if (store.find(id) == null) {
        ctx.status(404).json(Map.of("status", "error", "message", "La nota no existe"));
        return;
      }
      store.save(body);
      Path path = store.find(id);
      if (path == null) {
        ctx.status(500).json(Map.of("status", "error", "message", "La nota desaparecio al guardarla"));
        return;
      }
      Note stored = store.read(path);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("id", stored.getId());
      response.put("title", stored.getTitle());
      response.put("tags", stored.getTags());
      response.put("created", stored.getCreated());
      response.put("updated", stored.getUpdated());
      response.put("content", stored.getContent());
      response.put("path", store.relativePath(path));
      ctx.json(response);
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("status", "error", "message", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo guardar la nota {}", id, e);
      ctx.status(500).json(Map.of("status", "error", "message", "No se pudo guardar la nota"));
    }
  }
}
