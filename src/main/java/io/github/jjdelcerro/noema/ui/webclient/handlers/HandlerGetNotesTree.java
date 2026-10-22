package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteEntry;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;

/**
 * Devuelve los hijos directos del arbol de notas.
 *
 * Sin parametro 'path' devuelve el primer nivel (anyos). Con 'path' devuelve
 * el contenido de esa carpeta relativa a la carpeta de notas. La carga es
 * perezosa: cada llamada resuelve un solo nivel.
 *
 * @author jjdelcerro
 */
public class HandlerGetNotesTree extends AbstractHandler {

  public HandlerGetNotesTree(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String folder = ctx.queryParam("path");
    NoteStore store = NotesSupport.store(this.agent);
    try {
      List<NoteEntry> entries = store.list(folder);
      ctx.json(entries);
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("error", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo listar el arbol de notas: {}", folder, e);
      ctx.status(500).json(Map.of("error", "No se pudo leer el arbol de notas"));
    }
  }
}
