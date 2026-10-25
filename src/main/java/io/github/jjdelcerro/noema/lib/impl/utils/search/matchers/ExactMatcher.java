package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import org.apache.commons.lang3.Strings;

/**
 *
 * @author jjdelcerro
 */
public class ExactMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final String query;
  final boolean casesensitive;

  public ExactMatcher(String type, String query, boolean casesensitive) {
    super(type);
    this.query = query;
    this.casesensitive = casesensitive;
  }

  @Override
  public int matchScore(String text) {
    boolean n = this.casesensitive ? Strings.CS.contains(text, this.query) : Strings.CI.contains(text, this.query);
    return n ? 100 : 0;
  }
  
}
