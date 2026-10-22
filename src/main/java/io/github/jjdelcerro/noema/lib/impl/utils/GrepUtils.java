package io.github.jjdelcerro.noema.lib.impl.utils;

import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang3.Strings;
import org.apache.tika.detect.AutoDetectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class GrepUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(GrepUtils.class);

  public static final int DEFAULT_MIN_SCORE = 50;
  
  public static record GrepMatch(Path file, int line, String content, int score) {

    @Override
    public String toString() {
      return String.format("%s:%d:%d %s", file, line, score, content);
    }
  }
  
  public enum GrepMatchers {
    EXACT,
    GLOB,
    REGEXP,
    COSENO_SIMILARITY,
    FUZZYWUZZY
  }
  
  /**
   * Transforma el flujo de lectura de un fichero antes de trocearlo.
   *
   * Permite que el llamador sustituya el texto crudo del fichero por otro
   * derivado de el. Si el transformador devuelve null, el fichero se descarta.
   */
  @FunctionalInterface
  public interface ReaderTransformer {
    Reader apply(Reader reader) throws IOException;
  }
  
  public interface GrepMatcher {
    /**
     * 
     * @param text
     * @return 0 not match, 100 exact match
     */
    public int matchScore(String text); 
  }

  private static class ExactMatcher implements GrepMatcher {

    private final String query;
    private final boolean casesensitive;

    public ExactMatcher(String query, boolean casesensitive) {
      this.query = query;
      this.casesensitive = casesensitive;
    }

    @Override
    public int matchScore(String text) {
      boolean n = this.casesensitive?
              Strings.CS.contains(text, this.query) :
              Strings.CI.contains(text, this.query)              
              ;
      return n? 100 : 0;
    }    
  }
  
  private static class RegexpMatcher implements GrepMatcher {

    private final Pattern pattern;

    public RegexpMatcher(String pattern, boolean casesensitive) {
      this.pattern = Pattern.compile(pattern, casesensitive? 0 : Pattern.CASE_INSENSITIVE);
      
    }
    @Override
    public int matchScore(String text) {
      boolean n = pattern.matcher(text).find();
      return n? 100 : 0;
    }
  }
    
  private static class GlobMatcher implements GrepMatcher {

    private final String glob;
    private final boolean casesensitive;

    public GlobMatcher(String glob, boolean casesensitive) {
      this.glob = glob;
      this.casesensitive = casesensitive;
    }
    @Override
    public int matchScore(String text) {
      boolean n = FilenameUtils.wildcardMatch(text, glob, casesensitive? IOCase.SENSITIVE:IOCase.INSENSITIVE);
      return n? 100 : 0;
    }
  }
  
  private static class CosenoSimilarityMatcher implements GrepMatcher {

    private final EmbeddingsService service;
    private final EmbeddingsService.Embedding query_embedding;
    
    public CosenoSimilarityMatcher(EmbeddingsService service, String query) {
      this.service = service;
      this.query_embedding = new EmbeddingsService.Embedding(query, service.getDefaultModel());
              
    }

    @Override
    public int matchScore(String text) {
      EmbeddingsService.Embedding text_embedding = new EmbeddingsService.Embedding(text, service.getDefaultModel());
      double n = this.query_embedding.cosineSimilarity(text_embedding);
      return (int) (n*100);
    }
    
  }

  private static class FuzzywuzzyMatcher implements GrepMatcher {

    private final String query;

    public FuzzywuzzyMatcher(String query) {
      this.query = query;
    }
    
    @Override
    public int matchScore(String text) {
      int n = FuzzySearch.partialRatio(query, text);
      return n;
    }
  }
  
  
  public static GrepMatcher matcherOf(GrepMatchers matcher, String query) {
    return matcherOf(matcher, query, null, true);
  }
  
  public static GrepMatcher matcherOf(GrepMatchers matcher, String query, boolean casesensitive) {
    return matcherOf(matcher, query, null, casesensitive);
  }
  
  public static GrepMatcher matcherOf(GrepMatchers matcher, String query, EmbeddingsService service) {
    return matcherOf(matcher, query, service, true);
  }
  
  public static GrepMatcher matcherOf(GrepMatchers matcher, String query, EmbeddingsService service, boolean casesensitive) {
    switch(matcher) {
      default:
      case GrepMatchers.EXACT:
        return new ExactMatcher(query, casesensitive);
      case GrepMatchers.FUZZYWUZZY:
        return new FuzzywuzzyMatcher(query);
      case GrepMatchers.GLOB:
        return new GlobMatcher(query, casesensitive);
      case GrepMatchers.REGEXP:
        return new RegexpMatcher(query, casesensitive);              
      case GrepMatchers.COSENO_SIMILARITY:
        if( service == null ) {
          throw new IllegalArgumentException("EmbeddingsService requiered for CosenoSimilarityMatcher");
        }
        return new CosenoSimilarityMatcher(service, query);              
    }
  }
  
  public static Iterable<GrepMatch> grep(AgentAccessControl accessControl, GrepMatcher matcher, Path scope) {
    return grep(accessControl, matcher, scope, DEFAULT_MIN_SCORE, null);
  }
  
  public static Iterable<GrepMatch> grep(AgentAccessControl accessControl, GrepMatcher matcher, Path scope, ReaderTransformer transformer) {
    return grep(accessControl, matcher, scope, DEFAULT_MIN_SCORE, transformer);
  }
  
  public static Iterable<GrepMatch> grep(AgentAccessControl accessControl, GrepMatcher matcher, Path scope, int minscore, ReaderTransformer transformer) {
    return grep(accessControl, matcher, scope, null, minscore, transformer);
  }
  
  public static Iterable<GrepMatch> grep(AgentAccessControl accessControl, GrepMatcher matcher, Path scope, String filePattern, ReaderTransformer transformer) {
    return grep(accessControl, matcher, scope, filePattern, DEFAULT_MIN_SCORE, transformer);
  }
  
  public static Iterable<GrepMatch> grep(AgentAccessControl accessControl, GrepMatcher matcher, Path scope, String filePattern, int minscore, ReaderTransformer transformer) {
    List<GrepMatch> results = new ArrayList<>();
    try (CloseableIterable<Path> entries = FsUtils.walk(
            accessControl, 
            scope, 
            FsUtils.normalizeGlob(filePattern), 
            PATH_ACCESS_READ
      )) {
      for (Path entry : entries) {
        if (!Files.isRegularFile(entry)) {
          continue;
        }
        grepSingleFile(matcher, minscore, entry, results, transformer);
      }
    } catch (IOException e) {
      LOGGER.warn("Error walking directory for grep: {}", scope, e);
    }
    return results;
  }

  private static void grepSingleFile(GrepMatcher matcher, int minscore, Path file, List<GrepMatch> results, ReaderTransformer transformer) {
    if (FsUtils.isBinaryResource(file)) {
      return;
    }
    try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()));
      Reader detected = new AutoDetectReader(in)) { 
      Reader transformed;
      if( transformer == null ) {
        transformed = detected;
      } else {
        transformed = transformer.apply(detected);
        if (transformed == null) {
          return;
        }
      }
      try (BufferedReader reader = new BufferedReader(transformed)) {
        String line;
        int lineNum = 1;
        while ((line = reader.readLine()) != null) {
          int score = matcher.matchScore(line);
          if ( score > minscore ) {
            results.add(new GrepMatch(file, lineNum, line, score));
          }
          lineNum++;
        }
      }
    } catch (Exception ignored) {
      // Files that cannot be decoded as UTF-8 are skipped, like unreadable ones.
    }
  }
  
  
}
