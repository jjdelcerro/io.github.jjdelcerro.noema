package io.github.jjdelcerro.noema.lib.impl.utils.search.chunkers;

import io.github.jjdelcerro.noema.lib.impl.utils.search.SearchChunker;
import java.io.IOException;
import java.io.Reader;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractSearchChunker implements SearchChunker {

  public static class SearchChunkerLocationBase implements SearchChunkerLocation {

    private final int end;
    private final int start;

    public SearchChunkerLocationBase(int start, int end) {
      this.start = start;
      this.end = end;
    }
    
    @Override
    public int start() {
      return start;
    }

    @Override
    public int end() {
      return end;
    }

    @Override
    public boolean isBlock() {
      return end > start+1;
    }

    @Override
    public boolean isEmpty() {
      return end == start;
    }
        
    @Override
    public String toString() {
      return String.format("%s:%d", start, end);
    }    
    
  }
  
  private final String type;
  
  public AbstractSearchChunker(String type) {
    this.type = type;
  }

  @Override
  public String getType() {
    return type;
  }
  
  
}
