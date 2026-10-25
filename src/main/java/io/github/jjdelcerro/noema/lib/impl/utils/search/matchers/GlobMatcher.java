package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

/**
 *
 * @author jjdelcerro
 */
public class GlobMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final String glob;
  final boolean casesensitive;

  public GlobMatcher(String type, String glob, boolean casesensitive) {
    super(type);
    this.glob = glob;
    this.casesensitive = casesensitive;
  }

  @Override
  public int matchScore(String text) {
    boolean n = FilenameUtils.wildcardMatch(text, glob, casesensitive ? IOCase.SENSITIVE : IOCase.INSENSITIVE);
    return n ? 100 : 0;
  }
  
}
