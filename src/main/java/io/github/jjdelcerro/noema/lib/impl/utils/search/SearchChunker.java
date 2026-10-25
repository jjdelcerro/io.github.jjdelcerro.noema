package io.github.jjdelcerro.noema.lib.impl.utils.search;

import io.github.jjdelcerro.noema.lib.impl.utils.search.chunkers.ChunkerLines;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public interface SearchChunker extends AutoCloseable {

  public static final String LINE = "line";
  
  public static SearchChunker of(String type) {
    switch(StringUtils.defaultIfBlank(type, "line").toLowerCase()) {
      default:
      case LINE:
        return new ChunkerLines(type);
    }
  }
  
  public interface SearchChunkerLocation {
    
    public int start();
    public int end();
    public boolean isBlock();
    public boolean isEmpty();
  }
      
  public String getType();
  public SearchChunker open(Reader reader) throws IOException;
  public String read() throws IOException;
  public SearchChunkerLocation getLocation();
  @Override
  void close() throws Exception;
}


