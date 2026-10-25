package io.github.jjdelcerro.noema.lib.impl.utils.search;

import io.github.jjdelcerro.noema.lib.api.AgentAccessControl;
import static io.github.jjdelcerro.noema.lib.api.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import io.github.jjdelcerro.noema.lib.impl.utils.CloseableIterable;
import io.github.jjdelcerro.noema.lib.impl.utils.FsUtils;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.detect.AutoDetectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public class SearchUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchUtils.class);

  public static final int DEFAULT_MIN_SCORE = 50;

  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, Path scope) {
    return search(accessControl, matcher, null, scope, null, DEFAULT_MIN_SCORE, -1);
  }
  
  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, Path scope, int minscore) {
    return search(accessControl, matcher, null, scope, null, minscore, -1);
  }
  
  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, Path scope, String filePattern) {
    return search(accessControl, matcher, null, scope, filePattern, DEFAULT_MIN_SCORE, -1);
  }

  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, Path scope, String filePattern, int minscore) {
    return search(accessControl, matcher, null, scope, filePattern, minscore, -1);
  }
  
  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, SearchChunker chunker, Path scope, String filePattern) {
    return search(accessControl, matcher, chunker, scope, filePattern, DEFAULT_MIN_SCORE, -1);
  }
  
  public static SearchResult search(AgentAccessControl accessControl, SearchMatcher matcher, Path scope, String filePattern, int score, int limit) {
    return search(accessControl, matcher, null, scope, filePattern, score, limit);
  }
  
  public static SearchResult search(
          AgentAccessControl accessControl, 
          SearchMatcher matcher, 
          SearchChunker chunker, 
          Path scope, 
          String filePattern, 
          int minscore,
          int limit) {
    SearchResult results = new SearchResult();
    try (CloseableIterable<Path> entries = FsUtils.walk(
            accessControl, 
            scope, 
            FsUtils.normalizeGlob(filePattern), 
            PATH_ACCESS_READ
      )) {
      if( chunker == null ) {
        chunker = SearchChunker.of(SearchChunker.LINE);
      }
      for (Path entry : entries) {
        if (!Files.isRegularFile(entry)) {
          continue;
        }
        if (FsUtils.isBinaryResource(entry)) {
          continue;
        }
        searchSingleFile(matcher, chunker, entry, minscore, limit, results);
        if( limit>0 && results.size()>limit ) {
          results.setLimitReached(true);
          break;
        }
        results.incrementTotalFiles();
      }
    } catch (IOException e) {
      LOGGER.warn("Error walking directory for grep: {}", scope, e);
    }
    return results;
  }

  private static void searchSingleFile(
          SearchMatcher matcher, 
          SearchChunker chunker, 
          Path file, 
          int minscore, 
          int limit,
          SearchResult results
    ) {
    try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()));
      Reader reader = new AutoDetectReader(in)) { 
      try ( SearchChunker localChunker = chunker.open(reader) ) {
        String chunk;
        boolean found = false;
        while ((chunk = localChunker.read()) != null) {
          int score = matcher.matchScore(chunk);
          if ( score >= minscore ) {
            // TODO: habria que ver como gestionamos que hay chunkers que devuelven un bloque de lineas.
            results.add(file, localChunker.getLocation().start(), chunk, score);
            found = true;
            if( limit>0 && results.size()>limit ) {
              results.setLimitReached(true);
              break;
            }
          }
        }
        if( found ) {
          results.incrementFoundFiles();
        }
      }
    } catch (Exception ignored) {
      // Files that cannot be decoded as UTF-8 are skipped, like unreadable ones.
    }
  }
  
  
}
