package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import java.util.regex.Pattern;

/**
 *
 * @author jjdelcerro
 */
public class RegexpMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final Pattern pattern;

  public RegexpMatcher(String type, String pattern, boolean casesensitive) {
    super(type);
    this.pattern = Pattern.compile(pattern, casesensitive ? 0 : Pattern.CASE_INSENSITIVE);
  }

  @Override
  public int matchScore(String text) {
    boolean n = pattern.matcher(text).find();
    return n ? 100 : 0;
  }
  
}
