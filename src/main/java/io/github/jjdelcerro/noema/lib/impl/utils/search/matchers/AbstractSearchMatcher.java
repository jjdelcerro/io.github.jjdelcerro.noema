package io.github.jjdelcerro.noema.lib.impl.utils.search.matchers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchMatcher;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractSearchMatcher implements SearchMatcher {

  private final String type;
  
  protected AbstractSearchMatcher(String type) {
    this.type = type;
  }

  @Override
  public String getType() {
    return type;
  }

}

