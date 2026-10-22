package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.Note;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.util.Map;

/**
 * Devuelve la nota modificada mas recientemente.
 *
 * Se usa al abrir la pestana de notas para retomar el trabajo donde se dejo.
 * El arbol se carga de forma perezosa, asi que el cliente no puede deducir
 * cual es la nota mas reciente sin recorrer todas las carpetas: ese calculo
 * se hace en el servidor y se devuelve la nota completa, de modo que el
 * cliente pueda pintarla sin una segunda peticion.
 *
 * Responde 204 cuando todavia no hay ninguna nota.
 *
 * @author jjdelcerro
 */
public class HandlerGetLatestNote extends AbstractHandler {

  public HandlerGetLatestNote(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    NoteStore store = NotesSupport.store(this.agent);
    try {
      Note note = store.findLatest();
      if (note == null) {
        ctx.status(204);
        return;
      }
      ctx.json(note);
    } catch (Exception e) {
      LOGGER.warn("No se pudo localizar la nota mas reciente", e);
      ctx.status(500).json(Map.of("error", "No se pudo leer la nota mas reciente"));
    }
  }
}
