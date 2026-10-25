package io.github.jjdelcerro.noema.lib.impl.utils.search;

import io.github.jjdelcerro.noema.lib.impl.services.embeddings.EmbeddingsService;
import io.github.jjdelcerro.noema.lib.impl.utils.search.matchers.CosenoSimilarityMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.matchers.ExactMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.matchers.FuzzywuzzyMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.matchers.GlobMatcher;
import io.github.jjdelcerro.noema.lib.impl.utils.search.matchers.RegexpMatcher;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public interface SearchMatcher {

  public static final String EXACT = "EXACT";
  public static final String GLOB = "GLOB";
  public static final String REGEXP = "REGEXP";
  public static final String COSENO_SIMILARITY = "COSENO_SIMILARITY";
  public static final String FUZZYWUZZY = "FUZZYWUZZY";
  
  
  public static SearchMatcher of(String type, String query) {
    return of(type, query, null, true);
  }
  
  public static SearchMatcher of(String type, String query, boolean casesensitive) {
    return of(type, query, null, casesensitive);
  }
  
  public static SearchMatcher of(String type, String query, EmbeddingsService service) {
    return of(type, query, service, true);
  }
  
  public static SearchMatcher of(String type, String query, EmbeddingsService service, boolean casesensitive) {
    switch(StringUtils.defaultIfBlank(type, EXACT).toUpperCase()) {
      default:
      case EXACT:
        return new ExactMatcher(type, query, casesensitive);
      case FUZZYWUZZY:
        return new FuzzywuzzyMatcher(type, query);
      case GLOB:
        return new GlobMatcher(type, query, casesensitive);
      case REGEXP:
        return new RegexpMatcher(type, query, casesensitive);              
      case COSENO_SIMILARITY:
        if( service == null ) {
          throw new IllegalArgumentException("EmbeddingsService requiered for CosenoSimilarityMatcher");
        }
        return new CosenoSimilarityMatcher(type, service, query);              
    }
  }
    
  
  public String getType();
  
  /**
   *
   * @param text
   * @return 0 not match, 100 exact match
   */
  public int matchScore(String text);
    
}
