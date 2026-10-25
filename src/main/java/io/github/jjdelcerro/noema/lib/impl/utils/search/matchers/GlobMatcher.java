package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang3.Strings;

/**
 *
 * @author jjdelcerro
 */
public class GlobMatcher extends AbstractSearchMatcher implements SearchMatcher {
  
  final String glob;
  final boolean casesensitive;

  public GlobMatcher(String type, String glob, boolean casesensitive) {
    super(type);
    if( !Strings.CS.startsWith(glob, "*") ) {
      glob = "*" + glob;
    }
    if( !Strings.CS.endsWith(glob, "*") ) {
      glob = glob + "*";
    }
    this.glob = glob;
    this.casesensitive = casesensitive;
  }

  @Override
  public int matchScore(String text) {
    boolean n = FilenameUtils.wildcardMatch(text, glob, casesensitive ? IOCase.SENSITIVE : IOCase.INSENSITIVE);
    return n ? 100 : 0;
  }
  
}
