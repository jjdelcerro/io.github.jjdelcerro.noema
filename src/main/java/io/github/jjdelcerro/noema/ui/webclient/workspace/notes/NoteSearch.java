package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchChunker;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchUtils;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.chunkers.AbstractSearchChunker;
import java.io.BufferedReader;
import org.apache.commons.io.IOUtils;

/**
 * Motor de busqueda sobre el arbol de notas.
 *
 * Los tres modos comparten la misma forma de resultado para que el cliente web
 * y la cache no tengan que distinguirlos:
 *
 * - exact: el termino aparece en title, tags o content. score 1.0.
 * - tags: la nota declara todas las etiquetas pedidas. score 1.0.
 * - fuzzy: delegacion en FileFuzzySearchUtils. score es la similitud coseno.
 *
 * limit y minSimilarity son constantes de dominio: no se exponen en la UI y por
 * eso tampoco entran en la clave de cache.
 *
 * OJO: FileFuzzySearchUtils recorta internamente el limite a MAX_LIMIT (20) y
 * se detiene tras MAX_FILES_TO_SCAN (100) ficheros. El modo fuzzy, por tanto,
 * nunca devuelve mas de veinte resultados ni ve el corpus entero.
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class NoteSearch {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoteSearch.class);

  public static final String MODE_EXACT = "exact";
  public static final String MODE_TAGS = "tags";
  public static final String MODE_FUZZY = "fuzzy";
  public static final String MODE_GLOB = "glob";
  public static final String MODE_REGEXP = "regexp";
  public static final String MODE_SEMANTIC = "semantic";
  
  public static final String DEFAULT_MODE = MODE_EXACT;

  public static final int LIMIT = 10000;
  public static final double MIN_SIMILARITY = 0.5;

  private static final String NOTES_FILE_PATTERN = "**/*.xml";

  private static final Comparator<NoteSearchItem> UPDATED_DESC = Comparator.comparing(
          NoteSearchItem::getUpdated,
          Comparator.nullsLast(Comparator.reverseOrder()));

  private static final Comparator<NoteSearchItem> SCORE_DESC = Comparator.comparingDouble(
          NoteSearchItem::getScore).reversed();

  private final Agent agent;
  private final NoteStore store;

  public NoteSearch(Agent agent, NoteStore store) {
    this.agent = agent;
    this.store = store;
  }

  /**
   * Ejecuta una busqueda sobre el arbol de notas.
   *
   * @param mode modo de busqueda; si no se reconoce se usa exact
   * @param query termino buscado
   * @param root subarbol donde buscar, o null para todo el arbol
   * @return resultado con la forma comun a los tres modos
   * @throws IOException si falla el recorrido del arbol
   */
  public NoteSearchResult search(String mode, String query, String root) throws IOException {
    String normalizedMode = this.normalizeMode(mode);
    String normalizedQuery = (query == null) ? "" : query.trim();

    List<NoteSearchItem> items;
    if( normalizedMode.equals(MODE_TAGS) ) {
      items = this.searchByTags(normalizedQuery, root);       
    } else {
      SearchMatcher matcher;
      switch(normalizedMode) {
        case MODE_FUZZY:
          matcher = SearchMatcher.of(SearchMatcher.FUZZYWUZZY, query);
          break;
        case MODE_GLOB:
          matcher = SearchMatcher.of(SearchMatcher.GLOB, query, false);
          break;
        case MODE_SEMANTIC:
          matcher = SearchMatcher.of(SearchMatcher.COSENO_SIMILARITY, 
                  query, 
                  (EmbeddingsService) this.agent.getService(EmbeddingsService.NAME)
          );
          break;
        case MODE_REGEXP:
          matcher = SearchMatcher.of(SearchMatcher.REGEXP, query, false);
          break;
        case MODE_EXACT:
        default:
          matcher = SearchMatcher.of(SearchMatcher.EXACT, query, false);
          break;
      }
      items = this.searchGrep(matcher, root);
    }

    boolean truncated = false;
    int total = items.size();
    if (total < 1 ) {
      return null;
    }    
    if (total > LIMIT) {
      items = new ArrayList<>(items.subList(0, LIMIT));
      truncated = true;
    }

    NoteSearchResult result = createResults(normalizedMode, normalizedMode, root, truncated, items);
    return result;
  }

  public NoteSearchResult createResults(String mode, String query, String root) {
    return this.createResults(mode, query, root, false, null);
  }
  
  public NoteSearchResult createResults(String mode, String query, String root, boolean truncated, List<NoteSearchItem>items) {
    if( items == null ) {
      items = Collections.emptyList();
    }
    NoteSearchResult result = new NoteSearchResult();
    result.setQuery(query);
    result.setMode(mode);
    result.setRoot(root);
    result.setTimestamp(Instant.now().toString());
    result.setTotal(items.size());
    result.setTruncated(truncated);
    result.setItems(items);
    return result;
  }
  
  /**
   * Normaliza el nombre del modo. Un modo desconocido cae a exact en vez de
   * fallar: el cliente no debe romperse por un parametro de mas.
   *
   * @param mode modo pedido
   * @return modo valido
   */
  public String normalizeMode(String mode) {
    if (StringUtils.isBlank(mode)) {
      return DEFAULT_MODE;
    }
    String candidate = mode.trim().toLowerCase(Locale.ROOT);
    if (MODE_TAGS.equals(candidate) || 
            MODE_FUZZY.equals(candidate) || 
            MODE_SEMANTIC.equals(candidate) || 
            MODE_REGEXP.equals(candidate) || 
            MODE_GLOB.equals(candidate)) {
      return candidate;
    }
    return MODE_EXACT;
  }

  private List<NoteSearchItem> searchByTags(String query, String root) throws IOException {
    List<NoteSearchItem> items = new ArrayList<>();
    List<String> wanted = this.splitTags(query);
    if (wanted.isEmpty()) {
      return items;
    }
    for (Path path : this.store.findAll(root)) {
      Note note = this.readQuietly(path);
      if (note == null) {
        continue;
      }
      if (this.containsAllTags(note.getTags(), wanted)) {
        items.add(this.toItem(note, path, 100));
      }
    }
    items.sort(UPDATED_DESC);
    return items;
  }

  private List<NoteSearchItem> searchGrep(SearchMatcher query, String root) throws IOException {
    Map<String, NoteSearchItem> best = new LinkedHashMap<>();
    Path scope = this.resolveBase(root);
    SearchResult matches = SearchUtils.search(
            this.agent.getAccessControl(), 
            query, 
            new NoteSearchChunker(store),
            scope,
            NOTES_FILE_PATTERN
    );
    if( matches == null ) {
      return Collections.EMPTY_LIST;
    }
    
    for (SearchResult.SearchResultEntry match : matches.results()) {
      Path path = match.file();
      if (path == null) {
        continue;
      }
      String key = path.toAbsolutePath().normalize().toString();
      int score = match.score();
      NoteSearchItem previous = best.get(key);
      if (previous != null && previous.getScore() >= score) {
        continue;
      }
      Note note = this.readQuietly(path);
      if (note == null) {
        continue;
      }
      best.put(key, this.toItem(note, path, score));
    }
    List<NoteSearchItem> items = new ArrayList<>(best.values());
    items.sort(SCORE_DESC);
    return items;
  }

  private static class NoteSearchChunker extends AbstractSearchChunker {

    private final SearchChunker chunker;
    private BufferedReader reader;
    private final NoteStore store;
    private BufferedReader contentReader;
    private int line;

    public NoteSearchChunker(NoteStore store) {
      super(SearchChunker.LINE);
      this.chunker = SearchChunker.of(SearchChunker.LINE);      
      this.store = store;
      this.line = 0;
    }

    
    @Override
    public SearchChunker open(Reader reader) throws IOException {      
      this.reader = (reader instanceof BufferedReader)? (BufferedReader)reader :new BufferedReader(reader); 
      Note note = this.store.read(reader);
      String title = StringUtils.defaultString(note.getTitle());
      String content = StringUtils.defaultString(note.getContent());
      this.contentReader = new BufferedReader(new StringReader(title + "\n\n" + content));
      this.line = 0;
      return this;
    }

    @Override
    public String read() throws IOException {
      this.line++;
      return this.contentReader.readLine();
    }

    @Override
    public void close() throws Exception {
      IOUtils.close(contentReader);
      IOUtils.close(reader);
      this.contentReader = null;
      this.reader = null;
      this.line = 0;
    }

    @Override
    public SearchChunkerLocation getLocation() {
      return new SearchChunkerLocationBase(line, line+1);
    }
    
  }

  private Path resolveBase(String root) {
    Path base = this.store.getNotesFolder();
    if (StringUtils.isBlank(root)) {
      return base;
    }
    Path candidate = base.resolve(root).normalize();
    if (!candidate.startsWith(base)) {
      throw new IllegalArgumentException("Ruta de notas fuera del arbol: " + root);
    }
    return candidate;
  }

  private List<String> splitTags(String query) {
    List<String> tags = new ArrayList<>();
    if (StringUtils.isBlank(query)) {
      return tags;
    }
    for (String part : query.split(",")) {
      String tag = part.trim().toLowerCase(Locale.ROOT);
      if (!tag.isEmpty() && !tags.contains(tag)) {
        tags.add(tag);
      }
    }
    return tags;
  }

  private boolean containsAllTags(List<String> noteTags, List<String> wanted) {
    if (noteTags == null || noteTags.isEmpty()) {
      return false;
    }
    for (String wantedTag : wanted) {
      boolean found = false;
      for (String noteTag : noteTags) {
        if (noteTag != null && noteTag.trim().toLowerCase(Locale.ROOT).equals(wantedTag)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private Note readQuietly(Path path) {
    try {
      return this.store.read(path);
    } catch (Exception e) {
      LOGGER.warn("No se pudo leer la nota: {}", path, e);
      return null;
    }
  }

  private NoteSearchItem toItem(Note note, Path path, int score) {
    NoteSearchItem item = new NoteSearchItem();
    item.setId(note.getId());
    item.setTitle(note.getTitle());
    item.setTags(note.getTags());
    item.setPath(this.store.relativePath(path));
    item.setUpdated(note.getUpdated());
    item.setScore(score);
    return item;
  }
}
