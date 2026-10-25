package io.github.jjdelcerro.noema.ui.webclient.handlers;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteSearch;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteSearchCache;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteSearchItem;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteSearchResult;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Busca en el arbol de notas y devuelve una pagina de resultados.
 *
 * Parametros de consulta:
 *
 * - q: termino buscado
 * - mode: exact, tags o fuzzy (por defecto exact)
 * - root: subarbol donde buscar, opcional
 * - page: pagina, base cero
 * - pageSize: tamano de pagina, 50 por defecto
 *
 * La cache se consulta siempre antes de calcular. Si no hay entrada, se
 * calcula, se escribe y se pagina en memoria; no se relee del disco.
 *
 * @author jjdelcerro
 */
public class HandlerGetNoteSearch extends AbstractHandler {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_PAGE_SIZE = 50;

  public HandlerGetNoteSearch(Agent agent) {
    super(agent);
  }

  @Override
  public void handle(Context ctx) throws Exception {
    String query = ctx.queryParam("q");
    String mode = ctx.queryParam("mode");
    String root = ctx.queryParam("root");
    int page = this.intParam(ctx.queryParam("page"), DEFAULT_PAGE);
    int pageSize = this.intParam(ctx.queryParam("pageSize"), DEFAULT_PAGE_SIZE);

    NoteStore store = NotesSupport.store(this.agent);
    NoteSearch search = new NoteSearch(this.agent, store);
    NoteSearchCache cache = new NoteSearchCache(NotesSupport.cacheFolder(this.agent));

    String normalizedMode = search.normalizeMode(mode);
    String key = cache.keyFor(normalizedMode, root, query);

    try {
      NoteSearchResult result = cache.read(key, page, pageSize);
      if (result == null) {
        NoteSearchResult fresh = search.search(normalizedMode, query, root);
        cache.write(key, fresh);
        result = this.paginate(fresh, page, pageSize);
      }
      if( result == null ) {
        result = search.createResults(mode, query, root);
      }
      ctx.json(result);
    } catch (IllegalArgumentException e) {
      ctx.status(400).json(Map.of("error", String.valueOf(e.getMessage())));
    } catch (Exception e) {
      LOGGER.warn("No se pudo ejecutar la busqueda de notas: {}", query, e);
      ctx.status(500).json(Map.of("error", "No se pudo ejecutar la busqueda"));
    }
  }

  private NoteSearchResult paginate(NoteSearchResult source, int page, int pageSize) {
    if (source==null || pageSize <= 0 || source.getItems() == null) {
      return source;
    }
    List<NoteSearchItem> items = source.getItems();
    int from = Math.max(0, page) * pageSize;
    List<NoteSearchItem> pageItems;
    if (from >= items.size()) {
      pageItems = new ArrayList<>();
    } else {
      int to = Math.min(items.size(), from + pageSize);
      pageItems = new ArrayList<>(items.subList(from, to));
    }
    source.setItems(pageItems);
    return source;
  }

  private int intParam(String text, int fallback) {
    if (text == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
