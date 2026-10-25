package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 *
 * @author jjdelcerro
 */
public class FuzzywuzzyMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final String query;

  public FuzzywuzzyMatcher(String type, String query) {
    super(type);
    this.query = query;
  }

  @Override
  public int matchScore(String text) {
    int n = FuzzySearch.partialRatio(query, text);
    return n;
  }
  
}
