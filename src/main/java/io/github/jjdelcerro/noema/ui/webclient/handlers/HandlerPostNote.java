package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.Note;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.Map;

/**
 * Crea una nota nueva.
 *
 * El identificador y la fecha de creacion los genera el servidor: si el
 * cliente los envia se descartan. Se aceptan title, tags y content.
 *
 * @author jjdelcerro
 */
public class HandlerPostNote extends AbstractHandler {

  public HandlerPostNote(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    Note body = ctx.bodyAsClass(Note.class);
    if (body == null) {
      ctx.status(400).json(Map.of("status", "error", "message", "Falta el cuerpo de la nota"));
      return;
    }
    body.setId(null);
    body.setCreated(null);
    body.setTitle(body.getTitle() == null ? "" : body.getTitle());
    body.setContent(body.getContent() == null ? "" : body.getContent());
    body.setTags(body.getTags() == null ? new ArrayList<>() : body.getTags());

    NoteStore store = NotesSupport.store(this.agent);
    try {
      Note note = store.create(body);
      ctx.status(201).json(note);
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("status", "error", "message", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo crear la nota", e);
      ctx.status(500).json(Map.of("status", "error", "message", "No se pudo crear la nota"));
    }
  }
}
